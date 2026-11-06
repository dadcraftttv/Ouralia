#!/usr/bin/env python3
"""Accuracy step 4d (Nov 6): the attributions for Hear Far Sounds' layer (assets/sound_kinds/layer.bin).

The layer is trained (step4_train.py --export) on the Freesound room tones in step4_clips.json and the bench's close
clips in clips.json whose licence is in step4_train.SHIP. CC BY asks for the creator, the title, the licence and a
link; this lists every CC BY clip the layer learned from (CC0 and public domain need none; they are counted). The
creators of the bench clips are fetched once from Wikimedia Commons and Freesound and kept in clips.json ("by").

    tools/.venv/bin/python tools/sound_kinds/step4_notice.py [--sa4] [--write]
--write replaces the section after MARK in assets/sound_kinds/NOTICE.txt (otherwise it prints it).
"""
import html, json, os, re, sys, urllib.parse, urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
NOTICE = os.path.join(HERE, "..", "..", "app", "src", "main", "assets", "sound_kinds", "NOTICE.txt")
MARK = "layer.bin (Hear Far Sounds"
KINDS = ["voices", "music", "traffic", "birds", "water", "steps"]
SHIP = {"CC0", "Public domain", "CC BY 3.0", "CC BY 4.0"} | ({"CC BY-SA 4.0"} if "--sa4" in sys.argv else set())
UA = {"User-Agent": "Ouralia-notice-tool/1.0 (https://github.com/dadcraftttv/Ouralia)"}


def get(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=30) as r:
        return json.load(r)


def creator(c):
    if c["src"] == "commons":
        title = urllib.parse.unquote(c["page"].rsplit("/wiki/", 1)[1])
        q = get("https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo&iiprop=extmetadata"
                "&titles=" + urllib.parse.quote(title))
        meta = next(iter(q["query"]["pages"].values()))["imageinfo"][0]["extmetadata"]
        artist = meta.get("Artist", {}).get("value", "")
        return re.sub(r"\s+", " ", html.unescape(re.sub(r"<[^>]+>", "", artist))).strip() or "unknown"
    if c["src"] == "esc50":
        key = open(os.path.expanduser("~/.config/ouralia/freesound_key")).read().strip()
        sid = c["page"].rstrip("/").rsplit("/", 1)[1]
        return get(f"https://freesound.org/apiv2/sounds/{sid}/?fields=username&token={key}")["username"]
    return ""


def main():
    path = os.path.join(HERE, "clips.json")
    clips = json.load(open(path))
    used = [c for c in clips if c["kind"] in KINDS and (c.get("licence") or "") in SHIP]
    fetched = 0
    for c in used:
        if c["licence"].startswith("CC BY") and not c.get("by"):
            c["by"] = creator(c)
            fetched += 1
    if fetched:
        with open(path, "w") as f:
            json.dump(clips, f, indent=0, ensure_ascii=False)
            f.write("\n")
    rooms = [c for c in json.load(open(os.path.join(HERE, "step4_clips.json"))) if c["label"] == "quiet"]
    lines = [f"{MARK}, accuracy step 4d; docs/design/sound-kinds.md \"Accuracy step 4\")", "",
             "A small layer on YAMNet's embedding, trained by tools/sound_kinds/step4_train.py on public recordings:",
             f"{len(rooms)} room tones from Freesound (freesound.org) and {len(used)} sound clips from Wikimedia Commons",
             "and Freesound (by way of the ESC-50 set), each kept under its own licence: CC0, public domain or CC BY"
             + (" or CC BY-SA 4.0" if "--sa4" in sys.argv else "") + ".",
             "The layer holds no audio, only numbers learned from it. The clips were cut, levelled, mixed and given room",
             "echo for training. The CC BY recordings, with their creators:", ""]
    for c in sorted(rooms, key=lambda c: c["by"].lower()):
        if c["licence"].startswith("CC BY"):
            lines.append(f"- \"{c['name']}\" by {c['by']}, {c['licence']}, {c['page']}")
    for c in sorted(used, key=lambda c: (c["kind"], c["name"])):
        if c["licence"].startswith("CC BY"):
            lines.append(f"- \"{c['name']}\" by {c['by']}, {c['licence']}, {c['page']}")
    free = sum(1 for c in rooms + used if not c["licence"].startswith("CC BY"))
    lines += ["", f"The other {free} are CC0 or public domain (listed in tools/sound_kinds/step4_clips.json and clips.json).",
              "Licences: https://creativecommons.org/licenses/by/3.0/ , https://creativecommons.org/licenses/by/4.0/"
              + (" , https://creativecommons.org/licenses/by-sa/4.0/" if "--sa4" in sys.argv else "")]
    text = "\n".join(lines) + "\n"
    if "--write" not in sys.argv:
        print(text)
        return
    old = open(NOTICE).read()
    head = old.split("\n" + MARK)[0].rstrip("\n")
    open(NOTICE, "w").write(head + "\n\n" + text)
    print(f"NOTICE.txt: {len(lines)} lines for the layer ({fetched} creators fetched)")


if __name__ == "__main__":
    main()
