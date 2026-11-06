#!/usr/bin/env python3
"""Accuracy step 4b (Nov 6): real quiet rooms and real far sounds from Freesound, CC0 and CC-BY only.

DadCraft picked Freesound for room tones (Commons has two usable ones). Needs his API key in
~/.config/ouralia/freesound_key (never in the repo). Writes tools/sound_kinds/step4_clips.json (each clip's id, name,
uploader, licence and page: the attribution CC-BY asks for) and downloads the previews (128 kb/s MP3, enough for
YAMNet's 16 kHz) into tools/.sound_kinds_cache/step4/ (gitignored). Plan: docs/design/sound-kinds.md
"Accuracy step 4 plan".

    tools/.venv/bin/python tools/sound_kinds/step4_fetch.py            search, list, download what's missing
    tools/.venv/bin/python tools/sound_kinds/step4_fetch.py --list     search and list only
"""
import json, os, re, sys, time, urllib.error, urllib.parse, urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
CACHE = os.path.join(HERE, "..", ".sound_kinds_cache", "step4")
OUT = os.path.join(HERE, "step4_clips.json")
KEY = os.path.expanduser("~/.config/ouralia/freesound_key")
API = "https://freesound.org/apiv2/search/text/"
UA = "OuraliaBench/0.1 (https://github.com/dadcraftttv/Ouralia)"
LICENCES = '("Creative Commons 0" OR "Attribution")'
PER_USER = 3          # (a few uploaders made hundreds: spread the rooms over many people and mics)
PER_QUERY = 40

# label, query, words that rule a clip out (in its name or tags); a far clip must also say it was heard from inside.
# The far clips only check the layer: water and steps from another room are rare here, so training mixes close
# clips into the room tones at set levels instead (step4_train.py).
PEOPLE = "talk|voice|speech|people|crowd|man |woman|child|kid|conversation|laugh|cough|walla|chatter|sneez|pant|" \
         "breath|snor|purr|cat |class"
ODD = "sci fi|sci-fi|scifi|microgrind|panda|interweb|clock|strike|fx|soundscape|distort|darkness|mist|drone|vlf|" \
      "rain|thunder|wedding|nightingale|cricket|helicopter|driving|passby|pass by|dishes|sink|radio"
FAR_MUST = "indoor|window|distant|inside|interior|apartment|room|through"
LOUD = ODD + "|music|radio|tv|television|song|traffic|car|road|street|rain|thunder|wind|bird|dog|water|tap|faucet|shower|" \
       "train|plane|aircraft|construction|drill|machine gun|siren|alarm|party|bar |restaurant|cafe|kitchen sink"
QUERIES = [
    ("quiet", "room tone", PEOPLE + "|" + LOUD),
    ("quiet", "roomtone", PEOPLE + "|" + LOUD),
    ("quiet", "quiet room ambience", PEOPLE + "|" + LOUD),
    ("quiet", "empty room ambience", PEOPLE + "|" + LOUD),
    ("quiet", "bedroom night quiet", PEOPLE + "|" + LOUD),
    ("quiet", "office room tone", PEOPLE + "|" + LOUD),
    ("quiet", "living room ambience quiet", PEOPLE + "|" + LOUD),
    ("quiet", "fridge hum room", PEOPLE + "|" + LOUD),
    ("quiet", "hvac air conditioning room", PEOPLE + "|" + LOUD),
    ("quiet", "computer fan room", PEOPLE + "|" + LOUD),
    ("far", "distant traffic indoors", PEOPLE + "|" + ODD + "|music"),
    ("far", "traffic through window", PEOPLE + "|" + ODD + "|music"),
    ("far", "distant road hum", PEOPLE + "|" + ODD + "|music"),
    ("far", "traffic heard inside apartment", PEOPLE + "|" + ODD + "|music"),
]


def key():
    k = open(KEY).read().strip() if os.path.exists(KEY) else ""
    if not k:
        sys.exit(f"No Freesound key in {KEY}")
    return k


HEAD_BYTES = 1_200_000   # (~75 s of a 128 kb/s preview: the bench reads the first minute)


def get(url, k, params=None, head=False):
    if params:
        url += "?" + urllib.parse.urlencode(params)
    for wait in (5, 30, 0):
        try:
            h = {"Authorization": "Token " + k, "User-Agent": UA}
            if head:
                h["Range"] = f"bytes=0-{HEAD_BYTES - 1}"
            req = urllib.request.Request(url, headers=h)
            with urllib.request.urlopen(req, timeout=120) as r:
                return r.read()
        except urllib.error.HTTPError as e:
            if e.code != 429 or not wait:
                raise
            time.sleep(wait)


def search():
    k, clips, users, seen = key(), [], {}, set()
    for label, q, bad in QUERIES:
        d = json.loads(get(API, k, {"query": q, "page_size": PER_QUERY, "sort": "score",
                                    "filter": f"license:{LICENCES} duration:[20 TO 600]",
                                    "fields": "id,name,username,license,duration,tags,previews,url"}))
        took = 0
        for r in d.get("results", []):
            text = (r["name"] + " " + " ".join(r.get("tags", []))).lower()
            if r["id"] in seen or re.search(bad, text) or users.get(r["username"], 0) >= PER_USER or (
                    label == "far" and not re.search(FAR_MUST, text)):
                continue
            seen.add(r["id"])
            users[r["username"]] = users.get(r["username"], 0) + 1
            lic = "CC0" if "zero" in r["license"] else "CC BY " + r["license"].rstrip("/").split("/")[-1]
            clips.append({"label": label, "query": q, "id": r["id"], "name": r["name"], "by": r["username"],
                          "licence": lic, "page": r["url"], "secs": round(r["duration"], 1),
                          "preview": r["previews"]["preview-hq-mp3"]})
            took += 1
        print(f"  {label:5} {q!r}: {took} of {len(d.get('results', []))}")
        time.sleep(1)
    return clips


def main():
    clips = search()
    with open(OUT, "w") as f:
        json.dump(clips, f, indent=0)
    n = {l: sum(c["label"] == l for c in clips) for l in ("quiet", "far")}
    print(f"{len(clips)} clips ({n}), {len({c['by'] for c in clips})} uploaders -> {os.path.relpath(OUT)}")
    if "--list" in sys.argv:
        return
    os.makedirs(CACHE, exist_ok=True)
    k = key()
    todo = [c for c in clips if not os.path.exists(os.path.join(CACHE, f"{c['id']}.mp3"))]

    def one(c):
        data = get(c["preview"], k, head=True)
        path = os.path.join(CACHE, f"{c['id']}.mp3")
        with open(path + ".part", "wb") as f:
            f.write(data)
        os.replace(path + ".part", path)
    from concurrent.futures import ThreadPoolExecutor
    with ThreadPoolExecutor(4) as ex:
        list(ex.map(one, todo))
    print(f"downloaded {len(todo)}, cached {len(clips)}")


if __name__ == "__main__":
    main()
