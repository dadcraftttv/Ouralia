#!/usr/bin/env bash
# Session Log (DadCraft, Oct 29): copies the phone's kept sessions to this computer, next to the desktop's own, without
# deleting them on the phone. They describe your room, so they stay here: not in git, not sent anywhere.
#   tools/pull_sessions.sh            copy the new ones to ~/.local/share/ouralia/phone-sessions/
#   tools/pull_sessions.sh --clear    then delete the phone's copies (only those now safely here)
#   tools/pull_sessions.sh --dev      from Ouralia Dev (adb can read only a debuggable app: the release builds aren't)
# Read them with tools/session_summary.py.
set -euo pipefail
ADB="$HOME/Android/Sdk/platform-tools/adb -s ${ANDROID_SERIAL:-ZD222T44PP}"
PKG=io.github.dadcraftttv.ouralia
CLEAR=0
for a in "$@"; do
    case "$a" in
        --dev) PKG=io.github.dadcraftttv.ouralia.dev ;;
        --clear) CLEAR=1 ;;
        *) echo "unknown option $a"; exit 1 ;;
    esac
done
DEST="${XDG_DATA_HOME:-$HOME/.local/share}/ouralia/phone-sessions"
mkdir -p "$DEST"
# (the Moto's USB link drops now and then: wait for it, and say so rather than "No sessions" when it is gone,
#  Nov 6: a dropped link read as Session Log being off)
if ! timeout 30 $ADB wait-for-device 2>/dev/null || [ "$($ADB get-state 2>/dev/null)" != device ]; then
    echo "The phone isn't connected (adb: $($ADB get-state 2>&1 | head -1)). Plug it in or replug, then try again."
    exit 1
fi
names=$($ADB shell run-as $PKG ls files/sessions 2>/dev/null | tr -d '\r' | grep '\.jsonl$' || true)
if [ -z "$names" ]; then
    echo "No sessions on the phone (Session Log is in Setup, Behavior)."
    exit 0
fi
new=0
failed=""
for n in $names; do
    # each file: retried over a dropping link, kept only when its size matches the phone's; a whole copy is skipped
    ok=0
    for try in 1 2 3 4 5; do
        timeout 30 $ADB wait-for-device 2>/dev/null || true
        size=$($ADB shell run-as $PKG stat -c %s "files/sessions/$n" 2>/dev/null | tr -d '\r' || true)
        [ -n "$size" ] || { sleep 2; continue; }
        if [ -f "$DEST/$n" ] && [ "$(stat -c %s "$DEST/$n")" = "$size" ]; then
            ok=2
            break
        fi
        if $ADB exec-out run-as $PKG cat "files/sessions/$n" > "$DEST/$n.part" 2>/dev/null \
                && [ "$(stat -c %s "$DEST/$n.part")" = "$size" ]; then
            mv "$DEST/$n.part" "$DEST/$n"
            ok=1
            break
        fi
        sleep 2
    done
    rm -f "$DEST/$n.part"
    if [ $ok = 1 ]; then
        new=$((new + 1))
    elif [ $ok = 0 ]; then
        failed="$failed $n"
    fi
done
echo "Copied $new new session(s) to $DEST"
if [ -n "$failed" ]; then
    echo "Couldn't copy (the link kept dropping, or a session still being written):$failed"
    names=$(for n in $names; do case " $failed " in *" $n "*) ;; *) echo "$n" ;; esac; done)
fi
if [ "$CLEAR" = 1 ]; then
    for n in $names; do
        [ -s "$DEST/$n" ] && $ADB shell run-as $PKG rm "files/sessions/$n"
    done
    echo "Deleted them on the phone."
fi
