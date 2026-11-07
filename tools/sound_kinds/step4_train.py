#!/usr/bin/env python3
"""Accuracy step 4c (Nov 6): can a small layer on YAMNet's embedding tell a quiet room from a sound at a distance,
and name the sound?

Quiet: the real room tones from step4_fetch.py (Freesound, CC0/CC-BY), each set to a phone room level (-65 to -45 dB).
Sound: the bench's close clips (clips.json: voices, music, traffic, birds, water, steps), moved away (DISTANCES: the
room's echo and dulled highs, "Go ahead with the realistic distance in the mixes", Nov 6), mixed into a room tone at
0-20 dB over it, then the Moto's path (bench.phone: mic high-pass and the app's compressor). A mix counts as a sound
from MIN_SNR dB up; 0 and 3 dB are only reported (his far tap sat ~1 dB over his office: below anything to answer).
The layer: a softmax over quiet and the six kinds (classes weighted equal), a window is a sound when 1 - P(quiet)
passes a cutoff set on the training folds so quiet rooms are named a sound no more often than QUIET_MAX.
Checked by folds: rooms split by uploader, sounds by clip, so every result is on recordings the layer never saw.
Today's rules beside it: YAMNet names a kind (Silence not on top, best kind >= QUIET_FLOOR 0.05, the Now line), and
Mixed Surroundings' "clearly there" (best kind >= PRESENT_MIN 0.2), named by YAMNet's top kind.
Also checks his own recordings (~/Downloads/step4-recordings, never trained on), the real far clips, and the bench's
real quiet room. Embeddings are kept in tools/.sound_kinds_cache/step4_emb.npz (--fresh remakes them).
Prints only the summary. Plan and results: docs/design/sound-kinds.md "Accuracy step 4 plan" and after.

    tools/.venv/bin/python tools/sound_kinds/step4_train.py [--fresh] [--l2 3000] [--ship] [--sa4] [--cut X] [--export PATH]

--ship trains only on clips whose licence lets the weights ship in the public repo and app (SHIP: CC0, public domain,
CC BY; nothing NC, Nov 6 plan); --sa4 adds CC BY-SA 4.0 (one-way compatible with GPLv3). Held-out results are still
on every clip. --export (implies --ship) writes the final layer for the app (4d, Nov 6): see export().
"""
import glob, json, os, sys
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import bench  # noqa: E402  (phone, at, db, clip_audio, windows, model, SR, WIN, QUIET_FLOOR)
from embed_model import add_output  # noqa: E402

CACHE = os.path.join(HERE, "..", ".sound_kinds_cache")
EMB = os.path.join(CACHE, "step4_emb.npz")
MINE = os.path.expanduser("~/Downloads/step4-recordings")
KINDS = ["voices", "music", "traffic", "birds", "water", "steps"]
SNRS = [0, 3, 6, 10, 15, 20]
MIN_SNR = 6
ROOM_DB = (-65, -45)
ROOM_WINDOWS = 20      # per room tone, spread over it
SOUND_WINDOWS = 6      # per sound clip and distance
FOLDS = 5
PRESENT_MIN = 0.2
QUIET_MAX = 0.02
SHIP = {"CC0", "Public domain", "CC BY 3.0", "CC BY 4.0"} | ({"CC BY-SA 4.0"} if "--sa4" in sys.argv else set())
EXPORT = sys.argv[sys.argv.index("--export") + 1] if "--export" in sys.argv else None
SHIP_ONLY = "--ship" in sys.argv or EXPORT is not None
L2 = float(sys.argv[sys.argv.index("--l2") + 1]) if "--l2" in sys.argv else 3000.0
# --cut X: a fixed cutoff on 1 - P(quiet) instead of the QUIET_MAX quantile (Nov 6: his sink, 0.75 vs 0.829)
CUT = float(sys.argv[sys.argv.index("--cut") + 1]) if "--cut" in sys.argv else None
# distance: (direct-to-echo ratio in dB, echo time RT60 in s, low-pass Hz and slope in dB/oct), each a (lo, hi) range.
# Across the room the direct sound and the room's echo are about even; far, the echo leads and the highs dull; in the
# next room the wall or door takes the highs and nearly all of it is echo.
DISTANCES = {
    "near": None,
    "room": ((0, 6), (0.3, 0.8), (7000, 10000), 6),
    "far": ((-6, -3), (0.3, 0.8), (3000, 5000), 6),
    "next room": ((-12, -8), (0.4, 0.9), (1000, 2000), 18),
}
VERSION = "v3 " + json.dumps([SNRS, ROOM_DB, ROOM_WINDOWS, SOUND_WINDOWS, FOLDS, DISTANCES])
WIN, SR = bench.WIN, bench.SR
rng = np.random.default_rng(4)


def interpreter():
    from ai_edge_litert.interpreter import Interpreter
    src, labels = bench.model()
    path = os.path.join(CACHE, "yamnet_emb.tflite")
    if not os.path.exists(path):
        add_output(src, path)
    it = Interpreter(model_path=path, num_threads=4)
    it.allocate_tensors()
    od = it.get_output_details()
    groups = json.load(open(os.path.join(HERE, "groups.json")))
    cols = {k: [labels.index(n) for n in groups[k]] for k in KINDS}
    return it, it.get_input_details()[0]["index"], od[0]["index"], od[1], cols, labels.index("Silence")


IT, INP, SC, EO, COLS, SILENCE = interpreter()
ESCALE, EZERO = EO["quantization"]


def run(w):
    """(embedding, today: [names a kind at 0.05, clearly there at 0.2, best kind score, YAMNet's top kind index])"""
    IT.set_tensor(INP, w.astype(np.float32))
    IT.invoke()
    s = IT.get_tensor(SC)[0]
    e = (IT.get_tensor(EO["index"]).reshape(-1).astype(np.float32) - EZERO) * ESCALE
    g = [s[COLS[k]].max() for k in KINDS]
    on = s.argmax() != SILENCE
    best = max(g)
    return e, [float(on and best >= bench.QUIET_FLOOR), float(on and best >= PRESENT_MIN), float(best * on),
               float(int(np.argmax(g)))]


def spread(x, n):
    k = len(x) // WIN
    idx = np.unique(np.linspace(0, k - 1, min(k, n)).astype(int)) if k else []
    return [x[i * WIN:(i + 1) * WIN] for i in idx]


def decode(path, secs=60):
    import subprocess
    raw = subprocess.run(["ffmpeg", "-v", "error", "-i", path, "-map", "0:a", "-t", str(secs), "-ac", "1", "-ar",
                          str(SR), "-f", "f32le", "-"], capture_output=True, check=True).stdout
    return np.frombuffer(raw, np.float32).copy()


def lowpass(x, hz, slope):
    f = np.fft.rfft(x)
    r = np.fft.rfftfreq(len(x), 1 / SR) / hz
    return np.fft.irfft(f / np.sqrt(1 + r ** (2 * slope / 6)), len(x))


def rms(x):
    return np.sqrt(np.mean(x ** 2)) + 1e-12


def distance(x, how):
    """The clip as heard from further away: its direct sound (dulled) plus the room's echo at the given ratio."""
    if DISTANCES[how] is None:
        return x
    drr, rt, lp, slope = DISTANCES[how]
    drr, rt, lp = rng.uniform(*drr), rng.uniform(*rt), rng.uniform(*lp)
    n = int(rt * SR)
    t = np.arange(n) / SR
    tail = rng.standard_normal(n) * 10 ** (-3 * t / rt)           # -60 dB at RT60
    tail = lowpass(tail, 4000, 6) * np.minimum(1, t / 0.005)      # (walls take the highs first; no echo before 5 ms)
    m = 1 << int(np.ceil(np.log2(len(x) + n)))
    echo = np.fft.irfft(np.fft.rfft(x, m) * np.fft.rfft(tail, m), m)[:len(x)]
    direct = lowpass(x, lp, slope)
    echo = echo * rms(direct) / rms(echo) * 10 ** (-drr / 20)
    return lowpass(direct + echo, lp, slope) if how == "next room" else direct + echo


def load():
    fetched = json.load(open(os.path.join(HERE, "step4_clips.json")))
    rooms, far = [], []
    for c in fetched:
        p = os.path.join(CACHE, "step4", f"{c['id']}.mp3")
        if os.path.exists(p):
            (rooms if c["label"] == "quiet" else far).append((c, decode(p)))
    sounds = []
    for c in json.load(open(os.path.join(HERE, "clips.json"))):
        if c["kind"] in KINDS:
            try:
                sounds.append((c, bench.at(bench.clip_audio(c), -20)))
            except Exception:
                continue
    return rooms, far, sounds


def embed(rooms, sounds):
    if "--fresh" not in sys.argv and os.path.exists(EMB):
        d = np.load(EMB, allow_pickle=False)
        if str(d["version"]) == VERSION:
            return {k: d[k] for k in d.files}
    room_w = []   # (room index, window at its level, level)
    for ri, (c, x) in enumerate(rooms):
        lvl = rng.uniform(*ROOM_DB)
        for w in spread(x, ROOM_WINDOWS):
            if bench.db(w) > -100:
                room_w.append((ri, bench.at(w, lvl), lvl))
    ufold = {u: i % FOLDS for i, u in enumerate(sorted({c["by"] for c, _ in rooms}))}
    rfold = [ufold[c["by"]] for c, _ in rooms]
    sfold = [i % FOLDS for i in range(len(sounds))]
    rng.shuffle(sfold)
    Q = [(rfold[ri],) + run(bench.phone(w, bench.db(w))) for ri, w, _ in room_w]
    by_fold = {f: [r for r in room_w if rfold[r[0]] == f] for f in range(FOLDS)}
    M = []   # (fold, kind, distance, snr, licence, emb, today)
    for si, (c, x) in enumerate(sounds):
        f = sfold[si]
        for how in DISTANCES:
            ws = bench.windows(distance(x, how), False)          # (the windows the sound is in, not its gaps)
            ws = [ws[i] for i in np.unique(np.linspace(0, len(ws) - 1, min(len(ws), SOUND_WINDOWS)).astype(int))]
            for w in ws:
                ri, rw, lvl = by_fold[f][rng.integers(len(by_fold[f]))]
                for snr in SNRS:
                    y = rw + bench.at(w, lvl + snr)
                    M.append((f, c["kind"], how, snr, c.get("licence") or "") + run(bench.phone(y, bench.db(y))))
    d = {"version": np.array(VERSION),
         "qf": np.array([q[0] for q in Q]), "QE": np.array([q[1] for q in Q]), "Qt": np.array([q[2] for q in Q]),
         "mf": np.array([m[0] for m in M]), "MK": np.array([m[1] for m in M]), "MD": np.array([m[2] for m in M]),
         "MS": np.array([m[3] for m in M]), "ML": np.array([m[4] for m in M]),
         "ME": np.array([m[5] for m in M]), "Mt": np.array([m[6] for m in M])}
    np.savez(EMB, **d)
    return d


def fit_kinds(X, y, l2=L2, steps=1500, lr=0.2):
    """Softmax over quiet (0) and the six kinds (1..6), classes weighted equal; returns (probabilities, quiet cutoff):
    a window is a sound when 1 - P(quiet) passes the cutoff (at most QUIET_MAX of training quiet over it)."""
    mu, sd = X.mean(0), X.std(0) + 1e-6
    Z = (X - mu) / sd
    n, c = len(y), len(KINDS) + 1
    Y = np.eye(c)[y]
    cw = (1.0 / c) / np.maximum(Y.mean(0), 1e-9)
    W, b = np.zeros((X.shape[1], c)), np.zeros(c)
    for _ in range(steps):
        L = Z @ W + b
        P = np.exp(L - L.max(1, keepdims=True))
        P /= P.sum(1, keepdims=True)
        G = (P - Y) * cw[y][:, None]
        W -= lr * (Z.T @ G / n + l2 * W / n)
        b -= lr * G.mean(0)

    Wf, bf = W / sd[:, None], b - (mu / sd) @ W   # (the standardisation folded in: what the app runs)

    def f(E):
        L = E @ Wf + bf
        P = np.exp(L - L.max(1, keepdims=True))
        return P / P.sum(1, keepdims=True)
    f.weights = (Wf, bf)
    return f, CUT if CUT is not None else float(np.quantile(1 - f(X[y == 0])[:, 0], 1 - QUIET_MAX))


def export(path, pr, cut):
    """The app's layer (sound_kinds/layer.bin, read by Layer.java): little-endian; int32 inputs (1024) and outputs
    (7: quiet, then KINDS), float32 cutoff on 1 - P(quiet), then the weights row by row (inputs x outputs) and the
    bias. The embedding goes in dequantized (YAMNet's own scale and zero point)."""
    Wf, bf = pr.weights
    with open(path, "wb") as g:
        g.write(np.array(Wf.shape, "<i4").tobytes())
        g.write(np.array([cut], "<f4").tobytes())
        g.write(Wf.astype("<f4").tobytes())
        g.write(bf.astype("<f4").tobytes())
    print(f"\nexported {path}: {os.path.getsize(path)} bytes, cutoff {cut:.4f}")


def main():
    rooms, far, sounds = load()
    print(f"rooms {len(rooms)} (from {len({c['by'] for c, _ in rooms})} uploaders), sounds {len(sounds)} clips, "
          f"far {len(far)}")
    d = embed(rooms, sounds)
    qf, QE, Qt = d["qf"], d["QE"], d["Qt"]
    mf, MK, MD, MS, ME, Mt = d["mf"], d["MK"], d["MD"], d["MS"], d["ME"], d["Mt"]
    ok = np.isin(d["ML"], list(SHIP)) if SHIP_ONLY else np.ones(len(ME), bool)
    print(f"windows: quiet {len(QE)}, mixes {len(ME)}" + (f", trained on {ok.mean()*100:.0f}% (licences: "
          f"{', '.join(sorted(SHIP))})" if SHIP_ONLY else ""))
    MKi = np.array([KINDS.index(k) + 1 for k in MK])
    heard, kind = np.zeros(len(ME), bool), np.zeros(len(ME), int)
    qheard = np.zeros(len(QE), bool)
    for f in range(FOLDS):
        tr_q, tr_m = qf != f, (mf != f) & (MS >= MIN_SNR) & ok
        pr, cut = fit_kinds(np.vstack([QE[tr_q], ME[tr_m]]), np.r_[np.zeros(tr_q.sum(), int), MKi[tr_m]])
        P = pr(ME[mf == f])
        heard[mf == f] = 1 - P[:, 0] >= cut
        kind[mf == f] = P[:, 1:].argmax(1) + 1
        qheard[qf == f] = 1 - pr(QE[qf == f])[:, 0] >= cut
    top = Mt[:, 3].astype(int) + 1
    right_l, right_y = heard & (kind == MKi), (Mt[:, 1] > 0) & (top == MKi)

    def pc(a):
        return f"{a.mean()*100:4.0f}%" if a.size else "    -"
    print(f"\nheld out (folds); 'right' = heard and named the right kind      0.05   0.2 right | layer right | 0.2 or layer (the app's switch)")
    print(f"  quiet rooms named a sound                                {pc(Qt[:, 0])} {pc(Qt[:, 1])}       | {pc(qheard)}       |  {pc((Qt[:, 1] > 0) | qheard)}"
          "  (lower is better)")
    for how in DISTANCES:
        print(f"  {how}:")
        for snr in SNRS:
            s = (MD == how) & (MS == snr)
            print(f"    {snr:2d} dB over the room                                 {pc(Mt[s, 0])} {pc(Mt[s, 1])}"
                  f" {pc(right_y[s])} | {pc(heard[s])} {pc(right_l[s])} |  {pc(right_y[s] | right_l[s])}" + ("  (not trained on)" if snr < MIN_SNR else ""))
    mid = (MS >= 6) & (MS <= 10)
    print("  by kind at 6-10 dB, all distances (0.2 right | layer right | 0.2 or layer)")
    for k in KINDS:
        s = mid & (MK == k)
        print(f"    {k:8} {pc(right_y[s])} | {pc(right_l[s])} | {pc(right_y[s] | right_l[s])}")
    # final layer on everything, for the checks
    tr = (MS >= MIN_SNR) & ok
    pr, cut = fit_kinds(np.vstack([QE, ME[tr]]), np.r_[np.zeros(len(QE), int), MKi[tr]])
    if EXPORT:
        export(EXPORT, pr, cut)
    print(f"\nchecks (whole recordings, final layer; windows named a sound, and the layer's most named kind)"
          f"   0.05   0.2  layer")

    def check(name, x):
        rs = [run(bench.phone(w, bench.db(w))) for w in spread(x, 60) if bench.db(w) > -100]
        if not rs:
            return
        E = np.array([r[0] for r in rs]); t = np.array([r[1] for r in rs])
        P = pr(E); on = 1 - P[:, 0] >= cut
        ks = np.bincount(P[on, 1:].argmax(1), minlength=len(KINDS)) if on.any() else np.zeros(len(KINDS), int)
        most = f"{KINDS[int(ks.argmax())]} {ks.max()}/{on.sum()}" if on.any() else ""
        yon = t[:, 1] > 0
        yk = np.bincount(t[yon, 3].astype(int), minlength=len(KINDS))
        ymost = f"{KINDS[int(yk.argmax())]} {yk.max()}/{yon.sum()}" if yon.any() else ""
        print(f"  {name[:56]:56} {bench.db(x):4.0f} dB {pc(t[:, 0])} {pc(t[:, 1])} {pc(on)}  {most:18} (0.2: {ymost})")
    for p in sorted(glob.glob(os.path.join(MINE, "*2.wav"))):
        check("his " + os.path.basename(p), decode(p))
    for c in json.load(open(os.path.join(HERE, "clips.json"))):
        if c["kind"] == "quiet" and c["src"] == "commons":
            check("bench quiet: " + c["name"], bench.clip_audio(c))
    for c, x in far:
        check("far: " + c["name"], x)


if __name__ == "__main__":
    main()
