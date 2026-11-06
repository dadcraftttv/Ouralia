#!/usr/bin/env python3
"""Accuracy step 4c (Nov 6): can a small layer on YAMNet's embedding tell a quiet room from a sound at a distance?

Quiet: the real room tones from step4_fetch.py (Freesound, CC0/CC-BY), each set to a phone room level (-65 to -45 dB).
Sound: the bench's close clips (clips.json: voices, music, traffic, birds, water, steps) mixed into a room tone at
0-20 dB over it, then the Moto's path (bench.phone: mic high-pass and the app's compressor). A mix counts as a sound
from MIN_SNR dB up; 0 and 3 dB are only reported (his far tap sat ~1 dB over his office: below anything to answer).
Checked by folds: rooms split by uploader, sounds by clip, so every result is on recordings the layer never saw.
Today's rules beside it: YAMNet names a kind (Silence not on top, best kind >= QUIET_FLOOR 0.05, the Now line), and
Mixed Surroundings' "clearly there" (best kind >= PRESENT_MIN 0.2). The layer's classes are weighted equal, and its
cutoff is set on the training folds so quiet rooms are named a sound no more often than QUIET_MAX.
Also checks his own recordings (~/Downloads/step4-recordings, never trained on), the real far clips, and the bench's
two real quiet rooms. Prints only the summary. Plan: docs/design/sound-kinds.md "Accuracy step 4 plan".

    tools/.venv/bin/python tools/sound_kinds/step4_train.py [--l2 10,100,1000]   (regularization strengths to compare)
"""
import glob, json, os, sys
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import bench  # noqa: E402  (decode, phone, at, db, clip_audio, windows, model)
from embed_model import add_output  # noqa: E402

CACHE = os.path.join(HERE, "..", ".sound_kinds_cache")
MINE = os.path.expanduser("~/Downloads/step4-recordings")
KINDS = ["voices", "music", "traffic", "birds", "water", "steps"]
SNRS = [0, 3, 6, 10, 15, 20]
MIN_SNR = 6
ROOM_DB = (-65, -45)
ROOM_WINDOWS = 20      # per room tone, spread over it
FOLDS = 5
PRESENT_MIN = 0.2
QUIET_MAX = 0.02
L2S = [float(v) for v in sys.argv[sys.argv.index("--l2") + 1].split(",")] if "--l2" in sys.argv else [10.0]
WIN = bench.WIN
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
    """(embedding, today's verdicts: (names a kind at 0.05, a kind clearly there at 0.2))"""
    IT.set_tensor(INP, w.astype(np.float32))
    IT.invoke()
    s = IT.get_tensor(SC)[0]
    e = (IT.get_tensor(EO["index"]).reshape(-1).astype(np.float32) - EZERO) * ESCALE
    best = max(s[COLS[k]].max() for k in KINDS)
    on = s.argmax() != SILENCE
    top = max(range(len(KINDS)), key=lambda i: s[COLS[KINDS[i]]].max())
    return e, (bool(on and best >= bench.QUIET_FLOOR), bool(on and best >= PRESENT_MIN), float(best * on), top)


def spread(x, n):
    k = len(x) // WIN
    idx = np.unique(np.linspace(0, k - 1, min(k, n)).astype(int)) if k else []
    return [x[i * WIN:(i + 1) * WIN] for i in idx]


def decode(path, secs=60):
    import subprocess
    raw = subprocess.run(["ffmpeg", "-v", "error", "-i", path, "-map", "0:a", "-t", str(secs), "-ac", "1", "-ar",
                          str(bench.SR), "-f", "f32le", "-"], capture_output=True, check=True).stdout
    return np.frombuffer(raw, np.float32).copy()


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
                x = bench.clip_audio(c)
            except Exception:
                continue
            ws = bench.windows(bench.at(x, -20), False)   # (the windows the sound is in, not its gaps)
            sounds.append((c, ws))
    return rooms, far, sounds


def fit(X, y, l2=10.0, steps=1500, lr=0.2):
    """Logistic regression, the two classes weighted equal; returns (score function, cutoff) with the cutoff set so
    at most QUIET_MAX of the training quiet windows score over it."""
    mu, sd = X.mean(0), X.std(0) + 1e-6
    Z = (X - mu) / sd
    cw = np.where(y == 1, 0.5 / y.mean(), 0.5 / (1 - y.mean()))
    w, b = np.zeros(X.shape[1]), 0.0
    for _ in range(steps):
        p = 1 / (1 + np.exp(-(Z @ w + b)))
        g = (p - y) * cw
        w -= lr * (Z.T @ g / len(y) + l2 * w / len(y))
        b -= lr * g.mean()
    f = lambda E: 1 / (1 + np.exp(-(((E - mu) / sd) @ w + b)))
    return f, float(np.quantile(f(X[y == 0]), 1 - QUIET_MAX))


def fit_kinds(X, y, l2=3000.0, steps=1500, lr=0.2):
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
        P = np.exp(L - L.max(1, keepdims=True)); P /= P.sum(1, keepdims=True)
        G = (P - Y) * cw[y][:, None]
        W -= lr * (Z.T @ G / n + l2 * W / n)
        b -= lr * G.mean(0)

    def f(E):
        L = ((E - mu) / sd) @ W + b
        P = np.exp(L - L.max(1, keepdims=True))
        return P / P.sum(1, keepdims=True)
    return f, float(np.quantile(1 - f(X[y == 0])[:, 0], 1 - QUIET_MAX))


def main():
    rooms, far, sounds = load()
    print(f"rooms {len(rooms)} (from {len({c['by'] for c, _ in rooms})} uploaders), sounds {len(sounds)} clips, "
          f"far {len(far)}")
    # room windows at a phone room level
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
    # embeddings: quiet windows, then mixes (each sound window into a room window of the same fold, each SNR)
    Q = [(rfold[ri],) + run(bench.phone(w, bench.db(w))) for ri, w, _ in room_w]
    M = []   # (fold, kind, snr, emb, today)
    by_fold = {f: [r for r in room_w if rfold[r[0]] == f] for f in range(FOLDS)}
    for si, (c, ws) in enumerate(sounds):
        f = sfold[si]
        for w in ws:
            ri, rw, lvl = by_fold[f][rng.integers(len(by_fold[f]))]
            for snr in SNRS:
                y = rw + bench.at(w, lvl + snr)
                M.append((f, c["kind"], snr) + run(bench.phone(y, bench.db(y))))
    print(f"windows: quiet {len(Q)}, mixes {len(M)}")
    # folds
    qf = np.array([q[0] for q in Q]); QE = np.array([q[1] for q in Q]); Qt = np.array([q[2] for q in Q])  # (n, 2)
    mf = np.array([m[0] for m in M]); MK = np.array([m[1] for m in M]); MS = np.array([m[2] for m in M])
    ME = np.array([m[3] for m in M]); Mt = np.array([m[4] for m in M])
    QP, MP = [], []
    for l2 in L2S:
        qp, mp = np.zeros(len(Q), bool), np.zeros(len(M), bool)
        for f in range(FOLDS):
            tr_q, tr_m = qf != f, (mf != f) & (MS >= MIN_SNR)
            X = np.vstack([QE[tr_q], ME[tr_m]])
            y = np.r_[np.zeros(tr_q.sum()), np.ones(tr_m.sum())]
            pred, cut = fit(X, y, l2=l2)
            qp[qf == f] = pred(QE[qf == f]) >= cut
            mp[mf == f] = pred(ME[mf == f]) >= cut
        QP.append(qp); MP.append(mp)

    def row(label, t, ls, note=""):
        print(f"  {label:30} {t[:, 0].mean()*100:4.0f}%  {t[:, 1].mean()*100:4.0f}%"
              + "".join(f"  {l.mean()*100:5.0f}%" for l in ls) + note)
    print("\nheld out (folds)               0.05   0.2" + "".join(f"  l2={v:<4g}" for v in L2S))
    row("quiet rooms named a sound", Qt, QP, "   (lower is better)")
    for snr in SNRS:
        s = MS == snr
        row(f"sound {snr:2d} dB over the room", Mt[s], [m[s] for m in MP], "   (not trained on)" if snr < MIN_SNR else "")
    print("  by kind at 6-10 dB")
    for k in KINDS:
        s = (MK == k) & (MS >= 6) & (MS <= 10)
        row("  " + k, Mt[s], [m[s] for m in MP])
    # when the layer hears a sound, does YAMNet's strongest kind (however weak) name it right?
    # the seven-answer layer: names the kind too
    kp, kk = np.zeros(len(M), bool), np.zeros(len(M), int)
    qk = np.zeros(len(Q), bool)
    MKi = np.array([KINDS.index(k) + 1 for k in MK])
    for f in range(FOLDS):
        tr_q, tr_m = qf != f, (mf != f) & (MS >= MIN_SNR)
        X = np.vstack([QE[tr_q], ME[tr_m]])
        y = np.r_[np.zeros(tr_q.sum(), int), MKi[tr_m]]
        pr, cut = fit_kinds(X, y, l2=L2S[-1])
        P = pr(ME[mf == f])
        kp[mf == f] = 1 - P[:, 0] >= cut
        kk[mf == f] = P[:, 1:].argmax(1) + 1
        qk[qf == f] = 1 - pr(QE[qf == f])[:, 0] >= cut
    print(f"  seven-answer layer: quiet rooms named a sound {qk.mean()*100:.0f}%")
    for snr in SNRS:
        s = MS == snr
        miss = s & (Mt[:, 1] == 0) & kp
        print(f"    {snr:2d} dB: heard {kp[s].mean()*100:3.0f}%, kind right {(kk[s & kp] == MKi[s & kp]).mean()*100:3.0f}%"
              f"; where 0.2 misses: kind right {(kk[miss] == MKi[miss]).mean()*100 if miss.any() else 0:3.0f}%"
              f" (YAMNet's top {(Mt[miss][:, 3] + 1 == MKi[miss]).mean()*100 if miss.any() else 0:3.0f}%)")
    print("  YAMNet's top kind right, where the layer hears a sound (last l2):")
    for snr in SNRS:
        s = (MS == snr) & MP[-1]
        right = np.array([KINDS[int(t)] for t in Mt[s][:, 3]]) == MK[s]
        weak = s & (Mt[:, 1] == 0)
        rw = np.array([KINDS[int(t)] for t in Mt[weak][:, 3]]) == MK[weak]
        print(f"    {snr:2d} dB: {right.mean()*100:3.0f}% (where 0.2 misses: {rw.mean()*100 if len(rw) else 0:3.0f}% of {len(rw)})")
    # the simple alternative: today's rule with a lower cutoff, at the same quiet cost as the layer
    qb, mb = Qt[:, 2], Mt[:, 2]
    for l2, qp in zip(L2S, QP):
        t = float(np.quantile(qb, 1 - qp.mean()))
        mid = (MS >= 6) & (MS <= 10)
        print(f"  same quiet cost as l2={l2:g} ({qp.mean()*100:.0f}%): a cutoff of {t:.3f} finds 6-10 dB sounds "
              f"{(mb[mid] >= t).mean()*100:.0f}% (layer {MP[L2S.index(l2)][mid].mean()*100:.0f}%), traffic "
              f"{(mb[mid & (MK == 'traffic')] >= t).mean()*100:.0f}%, water {(mb[mid & (MK == 'water')] >= t).mean()*100:.0f}%")
    # final layer on everything, for the checks
    X = np.vstack([QE, ME[MS >= MIN_SNR]])
    y = np.r_[np.zeros(len(QE)), np.ones((MS >= MIN_SNR).sum())]
    pred, cut = fit(X, y, l2=L2S[-1])
    print(f"\nchecks (l2={L2S[-1]:g}) (whole recordings, final layer, cutoff {cut:.2f}; windows named a sound)       0.05   0.2  layer")

    def check(name, x):
        rs = [run(bench.phone(w, bench.db(w))) for w in spread(x, 60) if bench.db(w) > -100]
        if not rs:
            return
        E = np.array([r[0] for r in rs]); t = np.array([r[1] for r in rs])
        print(f"  {name[:58]:58} {bench.db(x):4.0f} dB {t[:, 0].mean()*100:4.0f}%  {t[:, 1].mean()*100:4.0f}%"
              f"  {(pred(E) >= cut).mean()*100:4.0f}%")
    for p in sorted(glob.glob(os.path.join(MINE, "*2.wav"))):
        check("his " + os.path.basename(p), decode(p))
    for c in json.load(open(os.path.join(HERE, "clips.json"))):
        if c["kind"] == "quiet" and c["src"] == "commons":
            check("bench quiet: " + c["name"], bench.clip_audio(c))
    for c, x in far:
        check("far: " + c["name"], x)


if __name__ == "__main__":
    main()
