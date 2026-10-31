#!/usr/bin/env bash
# The desktop's logic tests (no sound, no window). Must end ALL PASSED.
set -e
H="$(cd "$(dirname "$0")" && pwd)"
[ -x "$JAVA_HOME/bin/javac" ] || JAVA_HOME="$HOME/.jdks/jbr-21.0.11"; export JAVA_HOME PATH="$JAVA_HOME/bin:$PATH"; unset JAVA_TOOL_OPTIONS
[ -f "$H/lib/json-20240303.jar" ] || "$H/fetch_libs.sh"
CP="$H/lib/libpd.jar:$H/lib/json-20240303.jar"
A="$H/../app/src/main/java/io/github/dadcraftttv/ouralia"
SHARED="Param Params ParamStore PresetManager Modulation PhoneContext Intensity CalmBuild Worlds WorldSelector MotifMemory NoteRegister CallResponse SessionLog"
OUT="$(mktemp -d)"
javac -d "$OUT" -cp "$CP" $(for c in $SHARED; do echo "$A/$c.java"; done) $(find "$H/shim" "$H/test" -name '*.java')
java -cp "$OUT:$CP" io.github.dadcraftttv.ouralia.DesktopTest
