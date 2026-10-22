#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Guards the app's promises and identity (ROADMAP 22, from ChatGPT's review): no internet or other permission
beyond the six it needs, backup off, and no "RjDj" in the package or the name. Reads the source manifest, the
Gradle file and the dependencies' manifests, and every APK already built (debug, release), as Android sees it.

    python3 tools/test_release.py
"""
import glob, os, re, subprocess, sys, zipfile

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
SDK = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or os.path.expanduser("~/Android/Sdk")
# What the app asks for, and why, is in PRIVACY.md: a new permission means updating it (and this list).
ALLOWED = {"RECORD_AUDIO", "MODIFY_AUDIO_SETTINGS", "POST_NOTIFICATIONS", "FOREGROUND_SERVICE",
           "FOREGROUND_SERVICE_MICROPHONE", "FOREGROUND_SERVICE_MEDIA_PLAYBACK"}
PACKAGE = "io.github.dadcraftttv.ouralia"

fails = 0
def check(cond, msg):
    global fails
    print(("ok:   " if cond else "FAIL: ") + msg)
    if not cond:
        fails += 1

def permissions(manifest_xml):
    return {p.split(".")[-1] for p in re.findall(r'uses-permission[^>]*android:name="([^"]+)"', manifest_xml)}

manifest = open(os.path.join(ROOT, "app/src/main/AndroidManifest.xml")).read()
extra = permissions(manifest) - ALLOWED
check(not extra, "the manifest asks only for the six permissions in PRIVACY.md" + (": also %s" % sorted(extra) if extra else ""))
check('android:allowBackup="false"' in manifest, "backup off (allowBackup false)")

gradle = open(os.path.join(ROOT, "app/build.gradle.kts")).read()
ids = re.findall(r'(?:applicationId|namespace)\s*=\s*"([^"]+)"', gradle)
check(ids and all(i == PACKAGE for i in ids), "applicationId and namespace are %s (%s)" % (PACKAGE, ids))
check(not any("rjdj" in i.lower() for i in ids), "no RjDj in the applicationId")

for aar in glob.glob(os.path.join(ROOT, "third_party/maven/**/*.aar"), recursive=True):
    with zipfile.ZipFile(aar) as z:
        dep = z.read("AndroidManifest.xml").decode("utf-8", "replace")
    extra = permissions(dep) - ALLOWED
    check(not extra, "%s adds no permission%s" % (os.path.basename(aar), ": %s" % sorted(extra) if extra else ""))

tools = sorted(glob.glob(os.path.join(SDK, "build-tools/*/aapt2")))
apks = glob.glob(os.path.join(ROOT, "app/build/outputs/apk/*/*.apk"))
if not tools or not apks:
    print("ok:   (no aapt2 or no built APK: the built-APK checks skipped)")
for apk in apks if tools else []:
    name = os.path.relpath(apk, ROOT)
    perms = subprocess.run([tools[-1], "dump", "permissions", apk], capture_output=True, text=True).stdout
    got = {p.split(".")[-1] for p in re.findall(r"uses-permission: name='([^']+)'", perms)}
    check(got and got <= ALLOWED, "%s: only the six permissions%s" % (name, ": also %s" % sorted(got - ALLOWED) if got - ALLOWED else ""))
    check("INTERNET" not in got, "%s: no INTERNET" % name)
    badging = subprocess.run([tools[-1], "dump", "badging", apk], capture_output=True, text=True).stdout
    pkg = re.search(r"package: name='([^']+)'", badging)
    label = re.search(r"application-label:'([^']*)'", badging)
    check(pkg and pkg.group(1) == PACKAGE, "%s: package %s" % (name, pkg.group(1) if pkg else "?"))
    check(label and label.group(1) == "Ouralia", "%s: named Ouralia (%s)" % (name, label.group(1) if label else "?"))

print("ALL PASSED" if not fails else "%d FAILED" % fails)
sys.exit(1 if fails else 0)
