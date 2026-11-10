#!/usr/bin/env python3
"""Sound-kinds replay (Nov 5, the 2026-11-03 review; DadCraft: "go ahead with the replay tool and the time-based
windows"): runs a session log's logged scores back through the app's own SoundKinds (tools/replay/ReplayKinds.java),
so the cutoffs can be tuned on real places instead of guessed. Session Log keeps each judgement's seven scores since
Nov 4 (never audio). Pull the phone's sessions first: tools/pull_sessions.sh (--dev for Ouralia Dev).

    tools/replay_kinds.py                      the newest session with scores
    tools/replay_kinds.py --last 3             the newest three
    tools/replay_kinds.py FILE...              just these
  options (after the sessions or before):
    --from S --to S          only this stretch, in seconds from the session's start
    --scores                 each second's mean score per kind (narrow it with --from/--to)
    --heard                  the timeline also shows each change of the heard kind (the Now line) and of unsure
    --mixed on|off           replay as if Mixed Surroundings were on/off (default: as the log says)
    --layer on|off           ... and Hear Far Sounds (step 4d; logs since Nov 6 keep the layer's answer, "far layer heard")
    --set NAME=VALUE         try another value beside the app's (repeatable); the report shows both. Names:
        present_min, present_min.<kind>     a kind counts as clearly there from this score (0.2; kinds: voices
                                            music traffic birds water steps)
        present_rel                         ... and at least this share of the strongest kind's (0.5)
        quiet_floor, strong, lead_ratio     (0.05, 0.5, 1.25)
        layer_cut                           Hear Far Sounds' cutoff on 1 - P(quiet) (layer.bin's own)
        <window>.on, <window>.off, <window>.s   a scene window's on/off share and length in seconds
                                            (windows: talk room water birds traffic_scene)
    --machines               instead of the replay: YAMNet's own top classes and its strongest machine class (fan,
                             air conditioning, hum, white noise...; in no kind, only logged) over time, with the marks
                             (logs since Nov 9 keep them; honours --from/--to)
  e.g.  tools/replay_kinds.py --last 1 --from 85 --to 135 --set present_min.water=0.12
"""
import os, shutil, struct, subprocess, sys, tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
SRC = os.path.join(REPO, "app/src/main/java/io/github/dadcraftttv/ouralia/SoundKinds.java")
KINDS = os.path.join(REPO, "app/src/main/assets/sound_kinds/kinds.txt")
LAYER = os.path.join(REPO, "app/src/main/assets/sound_kinds/layer.bin")
BASE = os.path.join(os.environ.get("XDG_DATA_HOME") or os.path.expanduser("~/.local/share"), "ouralia")
# SessionLog.MACHINES (read from the source, so the two can't drift)
MACH = set(map(int, __import__("re").search(r"MACHINES = \{([^}]*)\}", open(SRC.replace("SoundKinds.java", "SessionLog.java"), encoding="utf-8").read()).group(1).split(",")))
WITH_VALUE = {"--from", "--to", "--mixed", "--layer", "--set", "--last"}


def with_scores(path):
    with open(path, encoding="utf-8") as f:
        return any('"ev":"kinds"' in line for line in f)


def find(last):
    found = []
    for sub in ("sessions", "phone-sessions"):
        d = os.path.join(BASE, sub)
        if os.path.isdir(d):
            found += [os.path.join(d, n) for n in os.listdir(d) if n.endswith(".jsonl")]
    found.sort(key=lambda p: os.path.basename(p)[:-6])
    found = [p for p in found if with_scores(p)]
    return found[-last:]


def labels():
    import zipfile
    with zipfile.ZipFile(os.path.join(REPO, "app/src/main/assets/sound_kinds/yamnet.tflite")) as z:
        return [l for l in z.read("yamnet_label_list.txt").decode().split("\n") if l]


def machines(path, lo, hi):
    """The machine report (--machines): per 10 s, the strongest machine class and YAMNet's commonest top class."""
    import json
    from collections import Counter
    names, rows, marks = labels(), [], []
    with open(path, encoding="utf-8") as f:
        for line in f:
            if '"ev":"mark"' in line or '"m":' in line:
                r = json.loads(line)
                if lo <= r["t"] <= hi:
                    (marks if r["ev"] == "mark" else rows).append(r)
    print(os.path.basename(path) + ":", len(rows), "judgements with YAMNet's classes")
    if not rows:
        print("  (none: logged since Nov 9, Session Log on, Mixed Surroundings or sound kinds running)")
        return
    ms = sorted(r["m"][1] for r in rows)
    top1 = Counter(names[r["y"][0][0]] for r in rows)
    print(f"  machine score: median {ms[len(ms) // 2]:.2f}, p90 {ms[int(len(ms) * 0.9)]:.2f}, max {ms[-1]:.2f};"
          f" a machine class on top in {sum(r['y'][0][0] in MACH for r in rows) / len(rows):.0%}")
    print("  YAMNet's top class most often: " + ", ".join(f"{n} {c / len(rows):.0%}" for n, c in top1.most_common(5)))
    print("  each 10 s: strongest machine class (its max, how often over 0.1) | YAMNet's commonest top class")
    events = [(m["t"], "    mark: " + m["text"]) for m in marks]
    start = int(rows[0]["t"] // 10 * 10)
    for b in range(start, int(rows[-1]["t"]) + 1, 10):
        w = [r for r in rows if b <= r["t"] < b + 10]
        if not w:
            continue
        best = max(w, key=lambda r: r["m"][1])["m"]
        over = sum(r["m"][1] >= 0.1 for r in w) / len(w)
        t1 = Counter(names[r["y"][0][0]] for r in w).most_common(1)[0]
        events.append((b, f"  {b // 60}:{b % 60:02d}  {names[best[0]][:20]:20} {best[1]:.2f} {over:4.0%}  | "
                          f"{t1[0]} ({t1[1] / len(w):.0%})"))
    for _, line in sorted(events, key=lambda e: e[0]):
        print(line)


def main(argv):
    if "-h" in argv or "--help" in argv:
        print(__doc__)
        return 0
    files, opts, last, i = [], [], 1, 0
    while i < len(argv):
        a = argv[i]
        if a in WITH_VALUE:
            if i + 1 >= len(argv):
                sys.exit(f"{a} needs a value")
            if a == "--last":
                last = int(argv[i + 1])
            else:
                opts += [a, argv[i + 1]]
            i += 2
        elif a == "--machines":
            opts.append(a)
            i += 1
        elif a in ("--scores", "--heard"):
            opts.append(a)
            i += 1
        elif a.startswith("--"):
            sys.exit(f"unknown option {a} (see --help)")
        else:
            files.append(a)
            i += 1
    if not files:
        files = find(last)
        if not files:
            sys.exit(f"No session with sound-kinds scores under {BASE} (tools/pull_sessions.sh copies the phone's)")
    if "--machines" in opts:
        lo = float(opts[opts.index("--from") + 1]) if "--from" in opts else 0
        hi = float(opts[opts.index("--to") + 1]) if "--to" in opts else float("inf")
        for f in files:
            machines(f, lo, hi)
        return 0
    javac, java = shutil.which("javac"), shutil.which("java")
    home = os.environ.get("JAVA_HOME") or os.path.expanduser("~/.jdks/jbr-21.0.11")
    if os.path.exists(os.path.join(home, "bin/javac")):
        javac, java = os.path.join(home, "bin/javac"), os.path.join(home, "bin/java")
    env = dict(os.environ, JAVA_TOOL_OPTIONS="")
    with tempfile.TemporaryDirectory() as out:
        c = subprocess.run([javac, "-d", out, "-proc:none", SRC, os.path.join(HERE, "replay/ReplayKinds.java")],
                           capture_output=True, text=True, env=env)
        if c.returncode:
            sys.stderr.write(c.stderr[-3000:])
            return 1
        cut = []
        if os.path.exists(LAYER):   # (the layer's cutoff: little-endian float32 after its two int32 sizes)
            with open(LAYER, "rb") as f:
                cut = ["--cut", repr(struct.unpack("<2if", f.read(12))[2])]
        r = subprocess.run([java, "-cp", out, "io.github.dadcraftttv.ouralia.ReplayKinds", "--kinds", KINDS] + cut + opts + files,
                           env=env, stderr=subprocess.PIPE, text=True)
        if r.returncode:
            sys.stderr.write("\n".join(l for l in r.stderr.splitlines() if "JAVA_TOOL_OPTIONS" not in l)[-3000:] + "\n")
        return r.returncode


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
