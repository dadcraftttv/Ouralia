#!/usr/bin/env python3
"""tools/replay_kinds.py (Nov 5) on a made-up session log: it runs the app's own SoundKinds, reads the settings and
the silence flag, and --set changes only the tuned run. Prints FAIL lines and ALL PASSED (both test tiers)."""
import json, os, subprocess, sys, tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
fails = 0


def check(ok, what, out=""):
    global fails
    print(("ok:   " if ok else "FAIL: ") + what)
    if not ok:
        fails += 1
        print("\n".join("      " + l for l in out.splitlines()[:40]))


def kinds(t, v, silent=False):
    row = {"t": round(t, 3), "ev": "kinds", "v": v}
    if silent:
        row["s"] = 1
    return row


def replay(path, *opts):
    r = subprocess.run([sys.executable, os.path.join(HERE, "replay_kinds.py"), path, *opts], capture_output=True, text=True)
    return r.returncode, r.stdout + r.stderr


with tempfile.TemporaryDirectory() as d:
    # 0-10 s YAMNet hears silence; 10-30 s a faint sink (water 0.15, under the 0.2 presence minimum); a mark at 20 s;
    # 30-50 s a clear sink (0.4). Mixed Surroundings on in the settings.
    rows = [{"t": 0.0, "ev": "start", "settings": {"sk_mixed": 1.0}}]
    t = 0.0
    while t < 50:
        if t < 10:
            rows.append(kinds(t, [0, 0, 0, 0, 0, 0, 0.9], silent=True))
        else:
            rows.append(kinds(t, [0, 0, 0, 0, 0.15 if t < 30 else 0.4, 0, 0.02]))
        if abs(t - 20) < 1e-6:
            rows.append({"t": 20.0, "ev": "mark", "text": "flat"})
        t += 0.25
    rows.append({"t": 50.0, "ev": "stop"})
    path = os.path.join(d, "20990101-000000.jsonl")
    with open(path, "w") as f:
        f.write("".join(json.dumps(r, separators=(",", ":")) + "\n" for r in rows))

    code, out = replay(path)
    check(code == 0 and "200 judgements, Mixed Surroundings on (from the log)" in out, "reads the judgements and the setting", out)
    check("older log" not in out, "the silence flag is read, not guessed", out)
    check("0:33  water on" in out and "0:20  mark: flat" in out,
          "the faint sink stays under the presence minimum, the clear one turns water on after 3.25 s; marks shown", out)
    code, out = replay(path, "--set", "present_min.water=0.12")
    check(code == 0 and "0:13  water on" in out and "tuned: present_min.water=0.12" in out,
          "--set present_min.water=0.12: the faint sink counts too (the timeline is the tuned run's)", out)
    check(any(l.split()[:3] == ["water", "0:17", "1"] and l.split()[3:5] == ["0:37", "1"] for l in out.splitlines()),
          "the states table: the app's 17 s of water beside the tuned 37 s", out)
    code, out = replay(path, "--mixed", "off")
    check("Mixed Surroundings off (--mixed)" in out and "0:13  water on" in out,
          "--mixed off: water counts while it is the kind, faint or not", out)
    code, out = replay(path, "--set", "nonsense=1")
    check(code != 0 and "unknown --set" in out, "an unknown --set is an error", out)

print("ALL PASSED" if not fails else "%d FAILED" % fails)
sys.exit(1 if fails else 0)
