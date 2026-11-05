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
    --set NAME=VALUE         try another value beside the app's (repeatable); the report shows both. Names:
        present_min, present_min.<kind>     a kind counts as clearly there from this score (0.2; kinds: voices
                                            music traffic birds water steps)
        present_rel                         ... and at least this share of the strongest kind's (0.5)
        quiet_floor, strong, lead_ratio     (0.05, 0.5, 1.25)
        <window>.on, <window>.off, <window>.s   a scene window's on/off share and length in seconds
                                            (windows: talk room water birds traffic_scene)
  e.g.  tools/replay_kinds.py --last 1 --from 85 --to 135 --set present_min.water=0.12
"""
import os, shutil, subprocess, sys, tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
SRC = os.path.join(REPO, "app/src/main/java/io/github/dadcraftttv/ouralia/SoundKinds.java")
KINDS = os.path.join(REPO, "app/src/main/assets/sound_kinds/kinds.txt")
BASE = os.path.join(os.environ.get("XDG_DATA_HOME") or os.path.expanduser("~/.local/share"), "ouralia")
WITH_VALUE = {"--from", "--to", "--mixed", "--set", "--last"}


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
        r = subprocess.run([java, "-cp", out, "io.github.dadcraftttv.ouralia.ReplayKinds", "--kinds", KINDS] + opts + files,
                           env=env, stderr=subprocess.PIPE, text=True)
        if r.returncode:
            sys.stderr.write("\n".join(l for l in r.stderr.splitlines() if "JAVA_TOOL_OPTIONS" not in l)[-3000:] + "\n")
        return r.returncode


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
