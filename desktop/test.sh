#!/usr/bin/env bash
# The desktop's logic tests (no sound, no window). Must end ALL PASSED.
set -e
H="$(cd "$(dirname "$0")" && pwd)"
[ -x "$JAVA_HOME/bin/javac" ] || JAVA_HOME="$HOME/.jdks/jbr-21.0.11"; export JAVA_HOME PATH="$JAVA_HOME/bin:$PATH"; unset JAVA_TOOL_OPTIONS
[ -f "$H/lib/json-20240303.jar" ] || "$H/fetch_libs.sh"
CP="$H/lib/libpd.jar:$H/lib/json-20240303.jar"
A="$H/../app/src/main/java/io/github/dadcraftttv/ouralia"
SHARED="Param Params ParamStore PresetManager Modulation PhoneContext Intensity CalmBuild Worlds WorldSelector NoteRegister CallResponse SessionLog"
OUT="$(mktemp -d)"
javac -d "$OUT" -cp "$CP" $(for c in $SHARED; do echo "$A/$c.java"; done) $(find "$H/shim" "$H/test" -name '*.java')
java -cp "$OUT:$CP" io.github.dadcraftttv.ouralia.DesktopTest
# The desktop app itself must still compile against the shared logic (review finding 6, Nov 4); jars from fetch_libs.sh.
APP_CP="$CP:$H/lib/jnajack-1.4.0.jar:$H/lib/jna-5.14.0.jar:$H/lib/flatlaf-3.4.1.jar"
APP_OUT="$(mktemp -d)"
javac -d "$APP_OUT" -cp "$APP_CP" $(for c in $SHARED; do echo "$A/$c.java"; done) $(find "$H/shim" "$H/src" -name '*.java')
echo "desktop app sources: compile OK"
rm -rf "$OUT" "$APP_OUT"
