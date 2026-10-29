#!/usr/bin/env bash
# Session Log (DadCraft, Oct 29): copies the phone's kept sessions to this computer, next to the desktop's own, without
# deleting them on the phone. They describe your room, so they stay here: not in git, not sent anywhere.
#   tools/pull_sessions.sh            copy the new ones to ~/.local/share/ouralia/phone-sessions/
#   tools/pull_sessions.sh --clear    then delete the phone's copies (only those now safely here)
# Read them with tools/session_summary.py.
set -euo pipefail
ADB="$HOME/Android/Sdk/platform-tools/adb -s ${ANDROID_SERIAL:-ZD222T44PP}"
PKG=io.github.dadcraftttv.ouralia
DEST="${XDG_DATA_HOME:-$HOME/.local/share}/ouralia/phone-sessions"
mkdir -p "$DEST"
names=$($ADB shell run-as $PKG ls files/sessions 2>/dev/null | tr -d '\r' | grep '\.jsonl$' || true)
if [ -z "$names" ]; then
    echo "No sessions on the phone (Session Log is in Setup, Behavior)."
    exit 0
fi
new=0
for n in $names; do
    $ADB exec-out run-as $PKG cat "files/sessions/$n" > "$DEST/$n.part"
    mv "$DEST/$n.part" "$DEST/$n"
    new=$((new + 1))
done
echo "Copied $new session(s) to $DEST"
if [ "${1:-}" = "--clear" ]; then
    for n in $names; do
        [ -s "$DEST/$n" ] && $ADB shell run-as $PKG rm "files/sessions/$n"
    done
    echo "Deleted them on the phone."
fi
