#!/usr/bin/env bash
# Builds a release APK and lays out what a release needs (docs/RELEASING.md): the APK named for its version, its
# SHA-256, and R8's mapping file (to read crash reports from the shrunk build). Publishes nothing.
#   tools/release_apk.sh [--replace]  -> ~/Downloads/Ouralia-<version>.apk, .sha256 and -mapping.txt
# With the Play release key in ~/.gradle/gradle.properties, also Ouralia-<version>.aab for Play (Play refuses a bundle
# signed with the debug key, so without the key there is no .aab).
# (--replace: overwrite that version's files, e.g. an APK swapped before anyone downloaded it)
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-$HOME/.jdks/jbr-21.0.11}"; export PATH="$JAVA_HOME/bin:$PATH"
VERSION=$(sed -n 's/.*versionName = "\(.*\)".*/\1/p' app/build.gradle.kts)
CODE=$(sed -n 's/.*versionCode = \([0-9]*\).*/\1/p' app/build.gradle.kts)
if [ -n "$(git status --porcelain)" ]; then
    echo "Uncommitted changes: commit first, so the release is exactly a commit."; exit 1
fi
OUT="${OURALIA_OUT_DIR:-$HOME/Downloads}/Ouralia-$VERSION"
if [ -e "$OUT.apk" ] && [ "${1:-}" != "--replace" ]; then
    echo "$OUT.apk already exists (a published release?). Raise versionName/versionCode, or pass --replace."; exit 1
fi
HAS_KEY=$(grep -qs '^ouraliaKeystore=' "${GRADLE_USER_HOME:-$HOME/.gradle}/gradle.properties" && echo 1 || true)
if [ -n "$HAS_KEY" ]; then ./gradlew -q assembleRelease bundleRelease; else ./gradlew -q assembleRelease; fi
python3 tools/test_release.py | tail -1
cp app/build/outputs/apk/release/app-release.apk "$OUT.apk"
cp app/build/outputs/mapping/release/mapping.txt "$OUT-mapping.txt"
(cd "$(dirname "$OUT")" && sha256sum "$(basename "$OUT").apk" > "$(basename "$OUT").sha256")
[ -n "$HAS_KEY" ] && cp app/build/outputs/bundle/release/app-release.aab "$OUT.aab"
SIGNER=$("$(ls -d "${ANDROID_HOME:-$HOME/Android/Sdk}"/build-tools/* | tail -1)/apksigner" verify --print-certs "$OUT.apk" \
    | sed -n 's/.*certificate DN: //p' | head -1)
echo "Ouralia $VERSION (versionCode $CODE), commit $(git rev-parse --short HEAD)"
echo "  $OUT.apk"
echo "  SHA-256 $(cut -d' ' -f1 "$OUT.sha256")"
echo "  signed by: $SIGNER"
echo "  mapping: $OUT-mapping.txt (keep it with the APK; not for the release page)"
if [ -n "$HAS_KEY" ]; then
    echo "  $OUT.aab (for Play; upload the mapping file with it)"
    echo "  aab signed by: $(keytool -printcert -jarfile "$OUT.aab" | sed -n 's/^Owner: //p' | head -1)"
else
    echo "  no .aab: the Play release key isn't set up (docs/RELEASING.md)"
fi
