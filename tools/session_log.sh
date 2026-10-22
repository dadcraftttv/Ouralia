#!/usr/bin/env bash
# A long listening session, logged on the phone (ROADMAP 22: dropouts, CPU, battery, heat). No root: a shell script in
# /data/local/tmp samples every 30 s, and logcat keeps the app's own lines (AudioService: its event log, dropouts
# included). PLUGGED IN ONLY: Android ends adb's processes when the cable comes out (Oct 22, setsid or not); for an
# unplugged session use `adb shell dumpsys batterystats --history` and the app's events.log (DESIGN-NOTES Diagnostics).
#   tools/session_log.sh start     start logging
#   tools/session_log.sh stop      stop it (plugged in again)
#   tools/session_log.sh report    copy the logs here (tools/.session/) and summarise them
set -euo pipefail
cd "$(dirname "$0")/.."
ADB="$HOME/Android/Sdk/platform-tools/adb -s ${ANDROID_SERIAL:-ZD222T44PP}"
D=/data/local/tmp/ouralia_session
case "${1:-}" in
start)
    $ADB shell "mkdir -p $D && rm -f $D/*"
    $ADB shell "cat > $D/sample.sh" <<'EOF'
D=/data/local/tmp/ouralia_session
echo $$ > $D/sampler.pid
logcat -c
logcat -v time -s AudioService:* AndroidRuntime:E libc:F DEBUG:F > $D/app.log 2>&1 &
echo $! > $D/logcat.pid
while true; do
  pid=$(pidof io.github.dadcraftttv.ouralia)
  cpu=-
  [ -n "$pid" ] && cpu=$(top -b -n 1 -p $pid -o %CPU | tail -1 | tr -d ' ')
  bat=$(dumpsys battery)
  lvl=$(echo "$bat" | sed -n 's/^ *level: //p')
  tmp=$(echo "$bat" | sed -n 's/^ *temperature: //p')
  ac=$(echo "$bat" | grep -c "powered: true")
  th=$(dumpsys thermalservice | sed -n 's/^Thermal Status: //p' | head -1)
  echo "$(date +%s) level=$lvl temp=$tmp plugged=$ac cpu=${cpu:--} thermal=$th app=${pid:-none}" >> $D/samples.txt
  sleep 30
done
EOF
    # (setsid: the script lives on after this adb command ends; nohup alone didn't. Not after unplugging.)
    $ADB shell "setsid sh $D/sample.sh < /dev/null > /dev/null 2>&1 &"
    for _ in 1 2 3 4 5; do
        $ADB shell "cat $D/samples.txt 2>/dev/null" && break
        sleep 1
    done
    echo "Logging (stay plugged in: unplugging ends it). Then: tools/session_log.sh stop, then report"
    ;;
stop)
    # (-9: the shell only takes a plain kill once its 30 s sleep ends)
    $ADB shell "kill -9 \$(cat $D/sampler.pid) \$(cat $D/logcat.pid) 2>/dev/null; echo stopped"
    ;;
report)
    mkdir -p tools/.session
    $ADB pull -q $D/samples.txt tools/.session/samples.txt
    $ADB pull -q $D/app.log tools/.session/app.log
    python3 - <<'EOF'
import re
rows = []
for line in open("tools/.session/samples.txt"):
    f = dict(kv.split("=", 1) for kv in line.split()[1:] if "=" in kv)
    rows.append((int(line.split()[0]), f))
if not rows:
    raise SystemExit("no samples")
t0, t1 = rows[0][0], rows[-1][0]
mins = (t1 - t0) / 60
lvl = [int(f["level"]) for _, f in rows if f.get("level", "").isdigit()]
tmp = [int(f["temp"]) / 10 for _, f in rows if f.get("temp", "").lstrip("-").isdigit()]
cpu = [float(f["cpu"]) for _, f in rows if re.match(r"^[\d.]+$", f.get("cpu", ""))]
unplugged = [f for _, f in rows if f.get("plugged") == "0"]
th = sorted({f.get("thermal", "?") for _, f in rows})
print(f"Session: {mins:.0f} min, {len(rows)} samples ({len(unplugged)} unplugged)")
if lvl and mins:
    drop = lvl[0] - lvl[-1]
    print(f"Battery: {lvl[0]}% -> {lvl[-1]}% ({drop} points, {60 * drop / mins:.1f} per hour)")
if tmp:
    print(f"Battery temperature: {tmp[0]:.1f} -> {tmp[-1]:.1f} C (highest {max(tmp):.1f})")
if cpu:
    print(f"App CPU (% of one core): average {sum(cpu) / len(cpu):.0f}, highest {max(cpu):.0f}")
print("Thermal status seen:", ", ".join(th), "(0 = none, 1 light, 2 moderate, 3 severe...)")
log = open("tools/.session/app.log", errors="replace").read().splitlines()
pick = lambda w: [l for l in log if w in l]
drops = pick("Audio dropouts")
print(f"Dropouts reported: {len(drops)} times" + (f", last: {drops[-1].split(': ', 1)[-1]}" if drops else ""))
for label, w in (("Starts", "Audio started"), ("Mic overloads", "Mic overload"), ("Headphones lost", "eadphones"),
                 ("Errors", "Failed to start"), ("Crashes", "FATAL")):
    n = len(pick(w))
    if n:
        print(f"{label}: {n}")
print("Full logs: tools/.session/samples.txt, tools/.session/app.log")
EOF
    ;;
*)
    sed -n '2,8p' "$0"; exit 1
    ;;
esac
