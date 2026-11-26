#!/usr/bin/env python3
"""Native debug symbols for Play: crash reports from our C code (libpd, pd-core's objects, dcs) readable on Play.

The .so files we ship are stripped. Their builds also leave unstripped copies; we keep those with only the symbol
table (debug info dropped, Play's "SYMBOL_TABLE" level) in third_party/native-symbols/<component>.zip, each entry
<abi>/<lib>.so. A stored copy belongs to a shipped library when their GNU build IDs are equal.

  native_symbols.py store <component> <obj/local dir>   build scripts: refresh <component>.zip from a fresh build
  native_symbols.py bundle <app.aab> <out.zip>          release_apk.sh: the zip to upload to Play beside the .aab
  native_symbols.py check                               test_release.py: the stored symbols match what we ship

Libraries that come prebuilt from Maven (Oboe, TensorFlow Lite) have no symbols of ours; bundle names them.
"""
import io, os, struct, subprocess, sys, zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
STORE = os.path.join(ROOT, "third_party", "native-symbols")
NDK = os.environ.get("ANDROID_NDK_HOME", os.path.expanduser("~/Android/Sdk/ndk/29.0.14206865"))
STRIP = os.path.join(NDK, "toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip")
SHIPPED = {  # what we ship from our own builds -> the component whose symbols it needs
    "third_party/maven/io/github/libpd/android/pd-core/1.4.2-pdscenes.2/pd-core-1.4.2-pdscenes.2.aar": "pd-core",
    "app/src/main/jniLibs": "dcs",
}
FIXED_TIME = (2020, 1, 1, 0, 0, 0)  # zip entries dated the same every run, so a rebuild of the same code is no change


def sections(data):
    """(type, offset, size) of each section of an ELF file (none if it isn't one)."""
    if data[:4] != b"\x7fELF":
        return
    is64, end = data[4] == 2, "<" if data[5] == 1 else ">"
    if is64:
        shoff, = struct.unpack_from(end + "Q", data, 0x28)
        shentsize, shnum = struct.unpack_from(end + "HH", data, 0x3A)
    else:
        shoff, = struct.unpack_from(end + "I", data, 0x20)
        shentsize, shnum = struct.unpack_from(end + "HH", data, 0x2E)
    for i in range(shnum):
        sh = shoff + i * shentsize
        sh_type, = struct.unpack_from(end + "I", data, sh + 4)
        off, size = struct.unpack_from(end + ("QQ" if is64 else "II"), data, sh + (0x18 if is64 else 0x10))
        yield sh_type, off, size


def has_symtab(data):
    return any(t == 2 for t, _, _ in sections(data))  # SHT_SYMTAB: gone from a stripped library


def build_id(data):
    """The GNU build ID of an ELF file (hex), or None."""
    end = "<" if data[5:6] == b"\x01" else ">"
    for sh_type, off, size in sections(data):
        if sh_type != 7:  # SHT_NOTE
            continue
        p = off
        while p + 12 <= off + size:
            namesz, descsz, ntype = struct.unpack_from(end + "III", data, p)
            name_end = p + 12 + ((namesz + 3) & ~3)
            if ntype == 3 and data[p + 12:p + 12 + namesz].rstrip(b"\0") == b"GNU":  # NT_GNU_BUILD_ID
                return data[name_end:name_end + descsz].hex()
            p = name_end + ((descsz + 3) & ~3)
    return None


def stored():
    """{(abi, lib): (component, bytes)} for every stored symbol file."""
    out = {}
    if os.path.isdir(STORE):
        for z in sorted(os.listdir(STORE)):
            if z.endswith(".zip"):
                with zipfile.ZipFile(os.path.join(STORE, z)) as zf:
                    for n in zf.namelist():
                        abi, lib = n.split("/", 1)
                        out[(abi, lib)] = (z[:-4], zf.read(n))
    return out


def shipped():
    """{(abi, lib): (component, bytes)} for the stripped libraries our own builds put in the app."""
    out = {}
    for rel, comp in SHIPPED.items():
        path = os.path.join(ROOT, rel)
        if path.endswith(".aar"):
            with zipfile.ZipFile(path) as zf:
                for n in zf.namelist():
                    if n.startswith("jni/") and n.endswith(".so"):
                        _, abi, lib = n.split("/")
                        out[(abi, lib)] = (comp, zf.read(n))
        else:
            for abi in sorted(os.listdir(path)):
                for lib in sorted(os.listdir(os.path.join(path, abi))):
                    if lib.endswith(".so"):
                        with open(os.path.join(path, abi, lib), "rb") as f:
                            out[(abi, lib)] = (comp, f.read())
    return out


def store(component, obj_local):
    if not os.access(STRIP, os.X_OK):
        sys.exit("native_symbols: no llvm-strip at %s (the NDK)" % STRIP)
    os.makedirs(STORE, exist_ok=True)
    dest = os.path.join(STORE, component + ".zip")
    tmp = dest + ".tmp"
    with zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as zf:
        for abi in sorted(os.listdir(obj_local)):
            d = os.path.join(obj_local, abi)
            for lib in sorted(os.listdir(d)) if os.path.isdir(d) else []:
                if lib.endswith(".so"):
                    data = subprocess.run([STRIP, "--strip-debug", "-o", "-", os.path.join(d, lib)],
                                          check=True, capture_output=True).stdout
                    if not has_symtab(data):
                        continue  # a stripped prebuilt the build only links against (dcs: pd-core's libpd.so)
                    zi = zipfile.ZipInfo("%s/%s" % (abi, lib), FIXED_TIME)
                    zi.compress_type = zipfile.ZIP_DEFLATED
                    zf.writestr(zi, data)
    os.replace(tmp, dest)
    print("native symbols: %s (%d KB)" % (os.path.relpath(dest, ROOT), os.path.getsize(dest) // 1024))


def mismatches():
    """Stored symbols that don't belong to the library we ship (or ship no library), and components with none."""
    syms, ships, bad = stored(), shipped(), []
    for key, (comp, data) in sorted(syms.items()):
        lib = ships.get(key)
        if lib is None or lib[0] != comp or build_id(lib[1]) != build_id(data):
            bad.append("%s/%s in %s.zip (%s)" % (key[0], key[1], comp,
                                                 "not shipped by it" if lib is None or lib[0] != comp else "another build"))
    for comp in sorted(set(SHIPPED.values()) - {c for c, _ in syms.values()}):
        bad.append("%s: no stored symbols (third_party/native-symbols/%s.zip)" % (comp, comp))
    return bad


def bundle(aab, out):
    syms = stored()
    got, none, stale = 0, [], []
    with zipfile.ZipFile(aab) as zf, zipfile.ZipFile(out + ".tmp", "w", zipfile.ZIP_DEFLATED) as oz:
        for n in sorted(zf.namelist()):
            parts = n.split("/")
            if len(parts) != 4 or parts[1] != "lib" or not n.endswith(".so"):
                continue
            abi, lib = parts[2], parts[3]
            s = syms.get((abi, lib))
            if s is not None and build_id(s[1]) == build_id(zf.read(n)):
                zi = zipfile.ZipInfo("%s/%s" % (abi, lib), FIXED_TIME)
                zi.compress_type = zipfile.ZIP_DEFLATED
                oz.writestr(zi, s[1])
                got += 1
            elif s is not None:
                stale.append("%s/%s" % (abi, lib))
            else:
                none.append(lib)
    if stale:
        os.remove(out + ".tmp")
        sys.exit("native_symbols: stored symbols are from another build for %s: rerun the build script that makes "
                 "them (tools/build_pdcore.sh, tools/build_dcs_android.sh)" % ", ".join(stale))
    os.replace(out + ".tmp", out)
    print("native symbols: %d libraries%s" % (got, "; none for %s (prebuilt from Maven)" % ", ".join(sorted(set(none)))
                                               if none else ""))


if __name__ == "__main__":
    a = sys.argv[1:]
    if a[:1] == ["store"] and len(a) == 3:
        store(a[1], a[2])
    elif a[:1] == ["bundle"] and len(a) == 3:
        bundle(a[1], a[2])
    elif a == ["check"]:
        bad = mismatches()
        print("\n".join(bad) if bad else "native symbols match every shipped library of ours")
        sys.exit(1 if bad else 0)
    else:
        sys.exit(__doc__)
