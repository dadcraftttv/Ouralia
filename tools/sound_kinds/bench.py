#!/usr/bin/env python3
"""Sound kinds, step 4b: can YAMNet tell the kinds apart? A desktop benchmark, never shipped.

Plan and results: docs/design/sound-kinds.md. Run from the repo root:
    tools/.venv/bin/python tools/sound_kinds/bench.py
    tools/.venv/bin/python tools/sound_kinds/bench.py --export   (the Dev app's model and kinds: step 4c)
Needs ffmpeg and ai-edge-litert in tools/.venv. The model and the clips download into tools/.sound_kinds_cache
(gitignored): they are not ours to commit. clips.json lists every clip with its source and licence (Wikimedia
Commons, ESC-50 or made here); groups.json maps each kind to YAMNet's class names (the AudioSet ontology's
subtrees, e.g. "birds" = everything under Bird).

Each clip is cut into 0.975 s windows (YAMNet's input), scored in three conditions (clean; phone: the Moto's level
and mic shape plus the app's default compressor; far: the same 15 dB quieter), and named by its windows' majority.
The no-ML fallback scores the same windows from simple features. Prints only the summary.
"""
import io, json, os, subprocess, sys, tarfile, time, urllib.error, urllib.request, zipfile
from collections import Counter
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
CACHE = os.path.join(HERE, "..", ".sound_kinds_cache")
MODEL_URL = "https://www.kaggle.com/api/v1/models/google/yamnet/tfLite/classification-tflite/1/download"
UA = {"User-Agent": "OuraliaBench/0.1 (https://github.com/dadcraftttv/Ouralia)"}
SR, WIN = 16000, 15600                # YAMNet: 16 kHz mono, 0.975 s
KINDS = ["voices", "singing", "music", "traffic", "birds", "wind", "water", "steps", "quiet"]
MAX_WINDOWS = 12                      # per clip, spread over its first 40 s
KEEP_DB = 20                          # windows this far under the clip's loudest are gaps, not the sound
QUIET_FLOOR = 0.05                    # no kind scores this much: the room is quiet (0.1 hid quiet traffic)
GATE = 0.8                            # 8 of 10 clips named right
# --norm CAP (Nov 5, accuracy step 7): each window raised toward NORM_TARGET before YAMNet, by at most CAP dB, never
# lowered; adds the conditions phone+n and far+n, so what it wins at a distance and what it costs (quiet rooms heard
# as sounds) are both counted
NORM_TARGET = -35
NORM_CAP = float(sys.argv[sys.argv.index("--norm") + 1]) if "--norm" in sys.argv else None
NORM_GATE = float(sys.argv[sys.argv.index("--gate") + 1]) if "--gate" in sys.argv else -999   # (windows under it stay)
CONDS = ("clean", "phone", "far") + (("phone+n", "far+n") if NORM_CAP is not None else ())
# --pass2 (Nov 6, step 7's second pass): phone+2/far+2 raise as +n does, but quiet comes from a second YAMNet pass on the
# un-raised window: quiet when Silence is its top label (or its Silence score >= --sil T); else the raised window names it
PASS2 = NORM_CAP is not None and "--pass2" in sys.argv
SIL_T = float(sys.argv[sys.argv.index("--sil") + 1]) if "--sil" in sys.argv else None
KEEP_R = float(sys.argv[sys.argv.index("--keep") + 1]) if "--keep" in sys.argv else None   # (un-raised weak: raised needs R)
CONDS += ("phone+2", "far+2") if PASS2 else ()

def fetch(url, path):
    if not os.path.exists(path):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        for wait in (5, 20, 60, 0):                            # Commons asks bots to go slowly (HTTP 429)
            try:
                with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=120) as r:
                    data = r.read()
                break
            except urllib.error.HTTPError as e:
                if e.code != 429 or not wait:
                    raise
                time.sleep(wait)
        time.sleep(1)
        with open(path, "wb") as f:
            f.write(data)
    return path

def model():
    path = os.path.join(CACHE, "yamnet.tflite")
    if not os.path.exists(path):
        tgz = fetch(MODEL_URL, os.path.join(CACHE, "yamnet.tgz"))
        with tarfile.open(tgz) as t:
            m = [x for x in t.getmembers() if x.name.endswith(".tflite")][0]
            with open(path, "wb") as f:
                f.write(t.extractfile(m).read())
    labels = zipfile.ZipFile(path).read("yamnet_label_list.txt").decode().split("\n")
    return path, [l for l in labels if l]

def decode(path):
    raw = subprocess.run(["ffmpeg", "-v", "error", "-i", path, "-t", "40", "-ac", "1", "-ar", str(SR), "-f", "f32le", "-"],
                         capture_output=True, check=True).stdout
    return np.frombuffer(raw, np.float32).copy()

def db(x):
    return 20 * np.log10(np.sqrt(np.mean(x ** 2)) + 1e-12)

def noise(n, colour, rng):
    w = rng.standard_normal(n)
    if colour == "white":
        return w
    f = np.fft.rfft(w); fr = np.fft.rfftfreq(n, 1 / SR); fr[0] = fr[1]
    return np.fft.irfft(f / (np.sqrt(fr) if colour == "pink" else fr), n)

def at(x, level):
    return x * 10 ** ((level - db(x)) / 20)

def bandpass(x, lo, hi):
    f = np.fft.rfft(x); fr = np.fft.rfftfreq(len(x), 1 / SR)
    f[(fr < lo) | (fr > hi)] = 0
    return np.fft.irfft(f, len(x))

def synth(name, secs=10):
    """Made here: quiet rooms at set levels, wind on the mic, and the app's own test signals (as tools/test_patch.py)."""
    rng = np.random.default_rng(abs(hash(name)) % 2 ** 32)
    p = name.split(); n = secs * SR; t = np.arange(n) / SR
    if p[0] in ("pink", "brown"):
        x = at(noise(n, p[0], rng), float(p[1]))
        if len(p) > 2:
            x += at(np.sin(2 * np.pi * 50 * t) + 0.5 * np.sin(2 * np.pi * 150 * t), float(p[3]))
    elif p[0] == "fan":
        x = at(bandpass(noise(n, "white", rng), 100, 400), float(p[1]))
    elif p[0] == "fridge":
        x = at(sum(np.sin(2 * np.pi * 120 * k * t) / k for k in range(1, 5)) + 0.3 * noise(n, "pink", rng), float(p[1]))
    elif p[0] == "dither":
        x = at(noise(n, "white", rng), float(p[1]))
    elif p[0] == "micwind":                        # buffeting: low rumble in gusts, the loudest hits clipping
        knots = np.arange(0, secs + 1, 0.4)
        gust = np.interp(t, knots, rng.uniform(0.1, 1, len(knots)))
        x = np.clip(at(bandpass(noise(n, "white", rng), 20, 250) * gust ** 2, float(p[1])) * 3, -0.5, 0.5) / 3
    elif p[0] == "buzz150":                        # test_patch.buzz: a voice-like 150 Hz buzz
        x = at(sum(np.sin(2 * np.pi * 150 * k * t) / k for k in range(1, 30) if 150 * k < 8000), float(p[1]))
    elif p[0] == "clap":                           # noise bursts around 1-3 kHz, twice a second
        env = np.exp(-((t % 0.5) / 0.02))
        x = at(bandpass(noise(n, "white", rng), 1000, 3000) * env, float(p[1]))
    return x.astype(np.float32)

def clip_audio(c):
    if c["src"] == "synth":
        return synth(c["name"])
    ext = os.path.splitext(c["url"])[1]
    safe = "".join(ch if ch.isalnum() else "_" for ch in c["name"])[:60]
    return decode(fetch(c["url"], os.path.join(CACHE, c["kind"], safe + ext)))

def phone(x, level):
    """The Moto's path: set to a room level, a ~100 Hz mic high-pass, then the app's default compressor
    (-30 dB, 4:1, 5 ms attack, 40 ms release, gain 1, no make-up)."""
    x = at(x, level)
    f = np.fft.rfft(x); r = (np.fft.rfftfreq(len(x), 1 / SR) / 100) ** 2
    x = np.fft.irfft(f * r / (1 + r), len(x))                 # 12 dB/oct high-pass at 100 Hz (magnitude)
    B = 80                                                    # gain per 5 ms block, then interpolated
    nb = len(x) // B
    ldb = 10 * np.log10((x[: nb * B].reshape(nb, B) ** 2).mean(1) + 1e-18)
    want = -np.maximum(ldb + 30, 0) * (1 - 1 / 4)
    att, rel = np.exp(-1 / (0.005 * SR / B)), np.exp(-1 / (0.040 * SR / B))
    g = np.empty_like(want); s = 0.0
    for i, v in enumerate(want):
        s = (att if v < s else rel) * (s - v) + v; g[i] = s
    gain = np.interp(np.arange(len(x)), np.arange(nb) * B + B / 2, g) if nb else np.zeros(len(x))
    return (x * 10 ** (gain / 20)).astype(np.float32)

def windows(x, keep_all):
    n = len(x) // WIN
    idx = np.unique(np.linspace(0, n - 1, min(n, MAX_WINDOWS)).astype(int)) if n else []
    ws = [x[i * WIN:(i + 1) * WIN] for i in idx]
    if not ws:
        ws = [np.pad(x, (0, WIN - len(x)))]
    if keep_all:
        return ws
    top = max(db(w) for w in ws)
    return [w for w in ws if db(w) > top - KEEP_DB]

def features_kind(w):
    """The no-ML fallback: wind, birds, traffic and steps from features the patch could measure cheaply."""
    if db(w) < -60:
        return "quiet"
    fr = np.lib.stride_tricks.sliding_window_view(w, 512)[::256] * np.hanning(512)
    S = np.abs(np.fft.rfft(fr, axis=1)) ** 2 + 1e-12; f = np.fft.rfftfreq(512, 1 / SR)
    tot = S.sum(1)
    low = S[:, f < 300].sum() / tot.sum()
    high = S[:, f > 2000].sum() / tot.sum()
    band = S[:, (f > 2000) & (f < 8000)]
    peaky = np.median(band.max(1) / band.mean(1))              # a whistle-like line above 2 kHz
    edb = 10 * np.log10(tot)
    onsets = sum(edb[i] - np.median(edb[max(0, i - 4):i]) > 10 for i in range(4, len(edb)))
    if onsets >= 1 and edb.max() - np.median(edb) > 15:
        return "steps"
    if high > 0.3 and peaky > 8:
        return "birds"
    if low > 0.5:
        return "wind" if np.std(edb) > 3 else "traffic"
    return "other"

PHONE_KINDS = [("voices", ["voices"]), ("music", ["music", "singing"]), ("traffic", ["traffic"]), ("birds", ["birds"]),
               ("water", ["water"]), ("steps", ["steps"]), ("quiet", ["quiet"])]   # step 4c: singing folded into music

def export(path, labels):
    """The app's files (app/src/main/assets/sound_kinds, in every build since Nov 5): the model, and each kind's class
    numbers, one line each."""
    dest = os.path.join(HERE, "..", "..", "app", "src", "main", "assets", "sound_kinds")
    os.makedirs(dest, exist_ok=True)
    with open(path, "rb") as f, open(os.path.join(dest, "yamnet.tflite"), "wb") as g:
        g.write(f.read())
    groups = json.load(open(os.path.join(HERE, "groups.json")))
    with open(os.path.join(dest, "kinds.txt"), "w") as f:
        f.write("# Made by tools/sound_kinds/bench.py --export from groups.json: a kind, then YAMNet's class numbers\n")
        for k, parts in PHONE_KINDS:
            f.write(k + " " + " ".join(str(i) for i in sorted(labels.index(n) for p in parts for n in groups[p])) + "\n")

def main():
    path, labels = model()
    if "--export" in sys.argv:
        return export(path, labels)
    from ai_edge_litert.interpreter import Interpreter
    it = Interpreter(model_path=path, num_threads=1); it.allocate_tensors()
    inp, out = it.get_input_details()[0]["index"], it.get_output_details()[0]["index"]
    groups = json.load(open(os.path.join(HERE, "groups.json")))
    cols = {k: [labels.index(n) for n in groups[k]] for k in KINDS}
    silence = labels.index("Silence")
    clips = json.load(open(os.path.join(HERE, "clips.json")))
    times = []

    def yam(w):
        it.set_tensor(inp, w.astype(np.float32)); t = time.perf_counter(); it.invoke(); times.append(time.perf_counter() - t)
        return it.get_tensor(out)[0]

    def name(s, how):
        g = {k: (s[cols[k]].max() if how == "max" else s[cols[k]].sum()) for k in KINDS if k != "quiet"}
        best = max(g, key=g.get)
        return "quiet" if s.argmax() == silence or s[cols[best]].max() < QUIET_FLOOR else best

    res = []                                              # (clip, condition, how, verdict, window share right)
    fb = []                                               # (clip, verdict) for the fallback, phone condition
    info = []
    for c in clips:
        try:
            x = clip_audio(c)
        except Exception as e:                            # a dead link or a decode failure: report, carry on
            print("skip %s: %s" % (c["name"][:50], str(e)[:80])); continue
        absolute = c["kind"] in ("quiet", "info")         # quiet rooms keep their own level
        for cond, level in (("clean", -20), ("phone", -35), ("far", -50), ("phone+n", -35), ("far+n", -50),
                            ("phone+2", -35), ("far+2", -50)):
            if cond not in CONDS:
                continue
            far = cond.startswith("far")
            y = (x if absolute else at(x, -20)) if cond == "clean" else phone(x, db(x) - 15 * far if absolute else level)
            ws = windows(y, absolute)
            raw = [yam(w) for w in ws] if cond.endswith("+2") else None
            if cond.endswith("+n") or cond.endswith("+2"):
                ws = [w * 10 ** (min(NORM_CAP, max(0.0, NORM_TARGET - db(w))) / 20 * (db(w) >= NORM_GATE)) for w in ws]
            scores = [yam(w) for w in ws]
            if raw:                                       # the un-raised pass says quiet: a Silence-only window
                q = [(r.argmax() == silence) if SIL_T is None else (r[silence] >= SIL_T) for r in raw]
                if KEEP_R is not None:                    # un-raised quiet only from weak scores: raised must be clear
                    q = [qq or (name(r, "max") == "quiet" and max(sc[cols[k]].max() for k in KINDS if k != "quiet") < KEEP_R)
                         for qq, r, sc in zip(q, raw, scores)]
                scores = [np.where(np.arange(len(sc)) == silence, 1.0, 0.0) if qq else sc for sc, qq in zip(scores, q)]
            if c["kind"] == "info" and cond == "phone":
                top = Counter(labels[s.argmax()] for s in scores).most_common(1)[0][0]
                info.append("%s -> %s" % (c["name"], top))
            if os.environ.get("BENCH_QUIET_DEBUG") and c["kind"] == "quiet" and cond in ("phone+2", "phone"):
                top = sorted(((sc[cols[k]].max(), k) for sc in scores for k in KINDS if k != "quiet"), reverse=True)[:3]
                print("DBG %-7s %-30s %s" % (cond, c["name"][:30], " ".join("%s %.2f" % (k, v) for v, k in top)))
            for how in ("max", "sum"):
                names = [name(s, how) for s in scores]
                res.append((c, cond, how, Counter(names).most_common(1)[0][0], names.count(c["kind"]) / len(names)))
            if cond == "phone":
                fn = [features_kind(w) for w in ws]
                fb.append((c, Counter(fn).most_common(1)[0][0]))

    print("YAMNet on this desktop: %.1f ms per window (median, one thread)" % (1000 * np.median(times)))
    for how in ("max", "sum"):
        for cond in CONDS:
            rows = [r for r in res if r[1] == cond and r[2] == how and r[0]["kind"] in KINDS]
            line = []
            for k in KINDS:
                real = [r for r in rows if r[0]["kind"] == k and r[0]["src"] != "synth"]
                syn = [r for r in rows if r[0]["kind"] == k and r[0]["src"] == "synth"]
                rr = real or syn
                hit = sum(r[3] == k for r in rr)
                line.append("%s %d/%d%s" % (k, hit, len(rr), "" if hit >= GATE * len(rr) else "!"))
                if real and syn:
                    line[-1] += "(+synth %d/%d)" % (sum(r[3] == k for r in syn), len(syn))
            print("%-5s %-7s %s" % (how, cond, "  ".join(line)))
    rows = [r for r in res if r[1] == "phone" and r[2] == "max" and r[0]["kind"] in KINDS]
    conf = Counter((r[0]["kind"], r[3]) for r in rows if r[3] != r[0]["kind"])
    print("phone/max wrong as:", ", ".join("%s->%s %d" % (a, b, n) for (a, b), n in conf.most_common()))
    for a, b in (("voices", "singing"), ("singing", "voices"), ("wind", "traffic"), ("traffic", "wind")):
        n = [r for r in rows if r[0]["kind"] == a]
        print("  %s named %s: %d of %d clips" % (a, b, sum(r[3] == b for r in n), len(n)))
    for k in ("wind", "birds", "traffic", "steps", "quiet"):
        mine = [v for c, v in fb if c["kind"] == k]
        false = [v for c, v in fb if c["kind"] != k and c["kind"] in KINDS]
        print("fallback %-7s %d/%d right, fires on %d of %d other clips" % (k, mine.count(k), len(mine), false.count(k), len(false)))
    both = ("singing", "music")                         # YAMNet hears choirs as Music: one kind "music or singing"?
    n = [r for r in rows if r[0]["kind"] in both]
    print("phone/max with singing and music as one kind: %d of %d clips" % (sum(r[3] in both for r in n), len(n)))
    print("app test signals (phone):", "; ".join(info))

if __name__ == "__main__":
    main()
