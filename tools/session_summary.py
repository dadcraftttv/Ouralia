#!/usr/bin/env python3
"""Session Log (DadCraft, Oct 29): a few lines per kept session, and each of DadCraft's marks with what was playing
and heard just before it. Reads the desktop's sessions and the phone's (tools/pull_sessions.sh) by default.

    tools/session_summary.py                 every session, oldest first
    tools/session_summary.py FILE...         just these
    tools/session_summary.py --last 3        the newest three
"""
import json, os, statistics, sys
from collections import Counter

BASE = os.path.join(os.environ.get("XDG_DATA_HOME") or os.path.expanduser("~/.local/share"), "ouralia")
SECTIONS = ["home", "lift", "calm", "turn"]
WINDOW_S = 10.0   # what a mark looks back over


def sessions(args):
    if args and args[0] != "--last":
        return args
    found = []
    for sub in ("sessions", "phone-sessions"):
        d = os.path.join(BASE, sub)
        if os.path.isdir(d):
            found += [os.path.join(d, n) for n in os.listdir(d) if n.endswith(".jsonl")]
    found.sort(key=lambda p: os.path.basename(p)[:-6])
    return found[-int(args[1]):] if args[:1] == ["--last"] else found


def read(path):
    rows = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            try:
                rows.append(json.loads(line))
            except ValueError:
                pass   # (a session cut short mid-line)
    return rows


def mean(xs):
    xs = [x for x in xs if x is not None]
    return f"{statistics.fmean(xs):.2f}" if xs else "-"


def summary(path):
    rows = read(path)
    if not rows:
        return [f"{os.path.basename(path)}: empty"]
    start = rows[0] if rows[0].get("ev") == "start" else {}
    end = rows[-1]["t"]
    pd = Counter(r["src"] for r in rows if r["ev"] == "pd")
    hear = [r for r in rows if r["ev"] == "hear"]
    sect = [SECTIONS[int(r["v"][0])] if r["v"] and 0 <= r["v"][0] < 4 else "?" for r in rows
            if r["ev"] == "pd" and r["src"] == "comp_section"]
    tempos = [r["v"][0] for r in rows if r["ev"] == "pd" and r["src"] == "tempo" and r["v"]]
    minutes = max(end / 60, 1e-9)
    s = start.get("settings", {})
    out = [f"{os.path.basename(path)} ({start.get('host', '?')} {start.get('version', '?')}, {start.get('wall', '?')}): "
           f"{end / 60:.1f} min, {sum(r['ev'] == 'bar' for r in rows)} bars, tempo "
           f"{min(tempos):.0f}-{max(tempos):.0f}" if tempos else
           f"{os.path.basename(path)} ({start.get('host', '?')}): {end / 60:.1f} min, no tempo reported"]
    out.append(f"  settings: Evolving {s.get('music_var', '-')}, scale {s.get('music_scale', '-')}, "
               f"key {s.get('music_key', '-')}, Intensity {s.get('intensity', '-')}; Realms playing "
               f"{dict(Counter(r.get('world') for r in hear).most_common(3))}")
    out.append(f"  played per minute: notes {pd['chime_deg'] / minutes:.1f}, chords {pd['comp_chord'] / minutes:.1f}, "
               f"kicks {pd['drum_kick'] / minutes:.1f}, snares {pd['drum_snare'] / minutes:.1f}; "
               f"sections {' '.join(sect) if sect else '-'}")
    out.append(f"  heard: mic {mean(r.get('mic_db') for r in hear)} dB, room {mean(r.get('room_db') for r in hear)} dB, "
               f"Intensity {mean(r.get('intensity') for r in hear)}, sung notes {pd['heard_deg']}, "
               f"found sounds {pd['fs_caught']}, walking {mean(float(r.get('walking', False)) for r in hear)}, "
               f"calm {Counter(r.get('calm') for r in hear).most_common(2)}")
    changed = Counter(r["id"] for r in rows if r["ev"] == "set")
    if changed:
        out.append(f"  settings changed: {', '.join(f'{k} x{n}' for k, n in changed.most_common(8))}")
    for m in (r for r in rows if r["ev"] == "mark"):
        t = m["t"]
        near = [r for r in rows if t - WINDOW_S <= r["t"] <= t]
        last_sect = next((SECTIONS[int(r["v"][0])] for r in reversed(rows) if r["t"] <= t and r["ev"] == "pd"
                          and r["src"] == "comp_section" and r["v"] and 0 <= r["v"][0] < 4), "-")
        h = [r for r in near if r["ev"] == "hear"]
        notes = [r["v"][0] for r in near if r["ev"] == "pd" and r["src"] == "chime_deg" and r["v"]]
        out.append(f"  mark {t / 60:5.1f} min \"{m['text']}\": section {last_sect}, last {WINDOW_S:.0f} s: "
                   f"{len(notes)} notes (steps {min(notes):.0f}..{max(notes):.0f})" if notes else
                   f"  mark {t / 60:5.1f} min \"{m['text']}\": section {last_sect}, last {WINDOW_S:.0f} s: no notes")
        out[-1] += (f", {sum(r['ev'] == 'pd' and r['src'] == 'comp_chord' for r in near)} chords, "
                    f"Intensity {mean(r.get('intensity') for r in h)}, mic {mean(r.get('mic_db') for r in h)} dB")
    return out


def main(argv):
    paths = sessions(argv)
    if not paths:
        print(f"No sessions in {BASE}/sessions or phone-sessions.")
        return
    for p in paths:
        print("\n".join(summary(p)))


if __name__ == "__main__":
    main(sys.argv[1:])
