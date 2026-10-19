# SPDX-License-Identifier: GPL-3.0-or-later
"""Tests for our own Pd objects (dcs/, built with `make -C dcs`), each in a small patch of its own.

    tools/.venv/bin/python tools/test_dcs.py tools/.libpd/libpd.so
"""
import ctypes, os, sys, tempfile
import numpy as np
from pdtest import Engine, SR

LIB = sys.argv[1] if len(sys.argv) > 1 else "tools/.libpd/libpd.so"
TMP = tempfile.mkdtemp(prefix="dcs_test_")
fails = []


def check(ok, what):
    print(("ok:   " if ok else "FAIL: ") + what)
    if not ok:
        fails.append(what)


def patch(name, objs, conns):
    """A patch from a list of object texts and (from, outlet, to, inlet) connections."""
    lines = ["#N canvas 0 0 600 400 12;", "#X declare -lib dcs;", "#X obj 10 10 declare -lib dcs;"]
    lines += [f"#X obj 10 {40 + 25 * i} {t};" for i, t in enumerate(objs)]
    lines += [f"#X connect {a + 1} {o} {b + 1} {i};" for a, o, b, i in conns]
    path = os.path.join(TMP, name + ".pd")
    with open(path, "w") as f:
        f.write("\n".join(lines) + "\n")
    return path


def peak_hz(x, lo, hi):
    """The strongest frequency between lo and hi, finely (Hann window, zero-padded FFT, parabolic peak)."""
    n = 1 << 20
    X = np.abs(np.fft.rfft(x * np.hanning(len(x)), n))
    f = np.fft.rfftfreq(n, 1 / SR)
    band = np.where((f >= lo) & (f <= hi))[0]
    k = band[np.argmax(X[band])]
    a, b, c = np.log(X[k - 1:k + 2] + 1e-30)
    return f[k] + 0.5 * (a - c) / (a - 2 * b + c) * (f[1] - f[0])


def rms(x):
    return float(np.sqrt(np.mean(np.asarray(x, dtype=float) ** 2)))


def db_frames(x, ms=20):
    m = int(SR * ms / 1000)
    fr = x[:len(x) // m * m].reshape(-1, m)
    return 20 * np.log10(np.sqrt((fr ** 2).mean(axis=1)) + 1e-12)


# ---------------------------------------------------------------- dcs_string~
print("dcs_string~:")
p = patch("string", ["adc~", "dcs_string~ 220 1500 0.5", "r sfreq", "r sdecay", "r sbright", "dac~"],
          [(0, 0, 1, 0), (2, 0, 1, 1), (3, 0, 1, 2), (4, 0, 1, 3), (1, 0, 5, 0), (1, 0, 5, 1)])
rng = np.random.default_rng(1)
burst = np.zeros(int(SR * 1.6), dtype=np.float32)
burst[:int(SR * 0.004)] = rng.uniform(-1, 1, int(SR * 0.004))
worst_cents = 0
for bright in (0.0, 0.5, 1.0):
    for f0 in (65.4, 220, 440, 1046.5, 2093):
        e = Engine(LIB, p)
        check(not any("couldn't create" in m for m in e.log), "dcs loads") if (bright, f0) == (0.0, 65.4) else None
        e.send("sbright", bright); e.send("sdecay", 3000); e.send("sfreq", f0)
        out = e.run(burst)[:, 0]
        e.close()
        got = peak_hz(out[int(SR * 0.1):], f0 * 0.9, f0 * 1.1)
        cents = 1200 * np.log2(got / f0) if np.abs(out).max() > 1e-3 else 1e9
        worst_cents = max(worst_cents, abs(cents))
        if abs(cents) > 3:
            print(f"      {f0} Hz at brightness {bright}: {got:.2f} Hz ({cents:+.1f} cents)")
check(worst_cents < 3, f"in tune from C2 to C7 at every brightness (worst {worst_cents:.2f} cents)")
def fall_t60(x, f0):
    """How long the f0 partial takes to fall 60 dB: its level in 50 ms frames, fitted from 0.2 s on."""
    m = int(SR * 0.05)
    k = np.arange(m)
    ref = np.exp(-2j * np.pi * f0 * k / SR) * np.hanning(m)
    lv = np.array([20 * np.log10(abs((x[i:i + m] * ref).sum()) + 1e-12) for i in range(0, len(x) - m, m)])
    t = np.arange(len(lv)) * 0.05
    use = (t >= 0.2) & (lv > lv.max() - 50)
    slope = np.polyfit(t[use], lv[use], 1)[0]
    return -60 / slope

for decay in (500, 1500, 3000):
    for bright in (0.1, 0.9):
        e = Engine(LIB, p)
        e.send("sbright", bright); e.send("sdecay", decay); e.send("sfreq", 220)
        out = e.run(np.concatenate([burst, np.zeros(SR * 3, dtype=np.float32)]))[:, 0]
        e.close()
        t60 = fall_t60(out, 220)
        check(0.9 < t60 / (decay / 1000) < 1.1,
              f"decay {decay} ms at brightness {bright}: the note rings that long ({t60 * 1000:.0f} ms to fall 60 dB)")
e = Engine(LIB, p)
e.send("sbright", 0); e.send("sdecay", 1500); e.send("sfreq", 2093)
out = e.run(np.concatenate([burst, np.zeros(SR * 2, dtype=np.float32)]))[:, 0]
e.close()
t60 = fall_t60(out, 2093)
check(0.9 < t60 / 1.5 < 1.1, f"a dark high note still rings ({t60 * 1000:.0f} ms at C7, brightness 0)")
e = Engine(LIB, p)
e.send("sbright", 1); e.send("sdecay", 60000); e.send("sfreq", 2000)
out = e.run(np.concatenate([burst, np.zeros(SR * 5, dtype=np.float32)]))[:, 0]
e.close()
check(np.isfinite(out).all() and 1e-3 < np.abs(out).max() < 4, f"bright, high and endless stays stable (peak {np.abs(out).max():.2f})")
noise = rng.uniform(-0.3, 0.3, SR * 2).astype(np.float32)
levels = []
for decay in (300, 3000):
    e = Engine(LIB, p)
    e.send("sbright", 0.5); e.send("sdecay", decay); e.send("sfreq", 330)
    out = e.run(noise)[:, 0]
    e.close()
    levels.append(20 * np.log10(np.sqrt((out[SR:] ** 2).mean())))
check(abs(levels[0] - levels[1]) < 8, f"steady noise comes out about as loud for a short and a long ring ({levels[0]:.1f} / {levels[1]:.1f} dB)")

p2 = patch("pluck", ["dcs_string~ 220 1500 0.3", "r spl", "dac~", "msg pluck 0.5"],
           [(1, 0, 3, 0), (3, 0, 0, 0), (0, 0, 2, 0), (0, 0, 2, 1)])
src = open(p2).read().replace("#X obj 10 115 msg pluck", "#X msg 10 115 pluck"); open(p2, "w").write(src)
e = Engine(LIB, p2)
e.run(np.zeros(640, dtype=np.float32)); e.lib.libpd_bang(b"spl")
out = e.run(np.zeros(SR, dtype=np.float32))[:, 0]
e.close()
w = out[int(SR * 0.05):int(SR * 0.5)]
h = [abs(np.mean(w * np.exp(-2j * np.pi * 220 * m * np.arange(len(w)) / SR))) for m in (1, 2, 3)]
check(0.2 < np.abs(out).max() < 1 and h[0] > 3 * max(h[1], h[2]) and abs(peak_hz(w, 200, 240) / 220 - 1) < 0.002,
      f"pluck: the note itself leads (harmonics 1-3: {h[0]:.3f} {h[1]:.3f} {h[2]:.3f}), in tune, peak {np.abs(out).max():.2f}")

# ---------------------------------------------------------------- dcs_glitch~
print("dcs_glitch~:")
g = patch("glitch", ["adc~", "dcs_glitch~ 4000 4", "r g", "dac~", "r gdel", "del 1.3", "msg slice 50 10 1 0 1e+07 -1",
                     "msg 1 5", "vline~", "+~"],
          [(0, 0, 1, 0), (2, 0, 1, 0), (1, 0, 3, 0), (1, 1, 9, 0), (8, 0, 9, 1), (9, 0, 3, 1),
           (4, 0, 5, 0), (5, 0, 6, 0), (6, 0, 1, 0), (5, 0, 7, 0), (7, 0, 8, 0)])
# the patch file needs a message box, not an object, for the two messages
src = open(g).read().replace("#X obj 10 190 msg slice", "#X msg 10 190 slice").replace("#X obj 10 215 msg 1 5", "#X msg 10 215 1 5")
open(g, "w").write(src)

e = Engine(LIB, g)
check(not any("couldn't create" in m for m in e.log), "loads")
ones = np.ones(SR, dtype=np.float32)
e.run(ones[:SR // 2])
e.lib.libpd_bang(b"gdel")
out = e.run(ones[:6400])
e.close()
gl = np.argmax(out[:, 0] > 1e-6)
vl = np.argmax(out[:, 1] > 1e-6)
check(abs(gl - vl) <= 2 and gl % 64 not in (0, 1, 2),
      f"a glitch from a [del 1.3] starts on its sample, with vline~ (sample {gl}, vline~ {vl}, not a block edge)")

noise = np.random.default_rng(2).uniform(-1, 1, SR * 2).astype(np.float32)
ms = SR // 1000
# stutter: a 100 ms slice repeated
e = Engine(LIB, g)
e.run(noise[:SR])
e.send_msg("g", "slice", 600, 200, 0, 1, 100, -1)
out = e.run(noise[SR:SR + 700 * ms])[:, 0]
e.close()
a1, a2 = out[110 * ms:190 * ms], out[210 * ms:290 * ms]
check(np.abs(a1 - a2).max() < 1e-4 and np.abs(a1).max() > 0.5, "stutter: each 100 ms slice repeats exactly")
# reverse: the last bit backwards
e = Engine(LIB, g)
e.run(noise[:SR])
e.send_msg("g", "slice", 400, 10, -1, 0, 1e7, -1)
out = e.run(noise[SR:SR + 500 * ms])[:, 0]
e.close()
t = np.arange(50 * ms, 350 * ms)
want = noise[SR + t - (10 * ms + 2 * t)]          # read back d0 + 2t: time runs backwards
check(np.abs(out[t] - want).max() < 1e-3, "reverse: plays what came before it backwards, sample for sample")
# several at once, on both sides
e = Engine(LIB, g)
e.run(ones[:SR // 2])
e.send_msg("g", "slice", 300, 50, 1, 0, 1e7, -1)
e.send_msg("g", "slice", 300, 50, 1, 0, 1e7, 1)
out = e.run(ones[:200 * ms])
e.close()
check(abs(out[100 * ms, 0] - 1) < 1e-3 and abs(out[100 * ms, 1] - 1) < 1e-3,
      "two glitches play at once, one in each ear")
e = Engine(LIB, g)
e.run(ones[:SR // 2])
for _ in range(7):
    e.send_msg("g", "slice", 300, 50, 1, 0, 1e7, -1)
out = e.run(ones[:200 * ms])
e.close()
check(3.9 < out[100 * ms, 0] < 4.1, f"at most 4 voices: extra glitches take the oldest's place ({out[100 * ms, 0]:.2f})")
# stretch: slower, same pitch, even level
sine = (0.5 * np.sin(2 * np.pi * 440 * np.arange(SR * 3) / SR)).astype(np.float32)
sweep = (0.5 * np.sin(2 * np.pi * np.cumsum(np.linspace(300, 900, SR * 3)) / SR)).astype(np.float32)
e = Engine(LIB, g)
e.run(sine[:SR])
e.send_msg("g", "stretch", 2500, 600, 0.1, -1)
out = e.run(np.zeros(SR * 3, dtype=np.float32))[:, 0]
e.close()
mid = out[300 * ms:1800 * ms]
check(abs(peak_hz(mid, 300, 600) / 440 - 1) < 0.005, f"stretch keeps the pitch ({peak_hz(mid, 300, 600):.1f} Hz from 440)")
d = db_frames(mid)
check(mid.std() > 0.05 and d.max() - d.min() < 3, f"stretch holds an even level on a steady tone (swing {d.max() - d.min():.1f} dB)")
e = Engine(LIB, g)
e.run(sweep[:SR * 2])                     # 300 -> 700 Hz over the 2 s so far
e.send_msg("g", "stretch", 2500, 1000, 0.1, -1)   # from 1 s back (~500 Hz), moving on at a tenth of the speed
out = e.run(np.zeros(SR * 3, dtype=np.float32))[:, 0]
e.close()
f_a = peak_hz(out[100 * ms:600 * ms], 300, 900)
f_b = peak_hz(out[1500 * ms:2000 * ms], 300, 900)
check(0 < f_b - f_a < 60, f"stretch moves on slowly: a rising sweep rises 10x slower ({f_a:.0f} -> {f_b:.0f} Hz over 1.4 s)")

# ---------------------------------------------------------------- dcs_derustle~
print("dcs_derustle~:")
d = patch("derustle", ["adc~", "dcs_derustle~", "r dr", "dac~"], [(0, 0, 1, 0), (2, 0, 1, 0), (1, 0, 3, 0), (1, 1, 3, 1)])
rng = np.random.default_rng(4)
def rustle_room(secs=8.0, room_db=-45, burst_db=-22, every=0.6, burst_ms=80):
    """A steady room (noise) with bursts of rustle every `every` s from 2 s on; returns the signal and a burst mask."""
    n = int(secs * SR); x = rng.standard_normal(n) * 10 ** (room_db / 20)
    mask = np.zeros(n, bool)
    for t in np.arange(2.0, secs - 0.2, every):
        i = int(t * SR); k = int(burst_ms * SR / 1000)
        x[i:i + k] += rng.standard_normal(k) * 10 ** (burst_db / 20) * np.hanning(k) * 1.6; mask[i:i + k] = True
    return x.astype(np.float32), mask
def derustle(x, amount):
    e = Engine(LIB, d); e.send_msg("dr", "amount", amount); y = e.run(x); e.close(); return y
x, mask = rustle_room()
y1, y0 = derustle(x, 1), derustle(x, 0)
lv = lambda v: 20 * np.log10(np.sqrt(np.mean(v ** 2)) + 1e-12)
late = np.arange(len(x)) > 3 * SR
gap = late & ~np.convolve(mask, np.ones(int(0.12 * SR)), "same").astype(bool)     # well clear of a burst
cut = lv(x[late & mask]) - lv(y1[late & mask, 0]); room = lv(x[gap]) - lv(y1[gap, 0])
check(cut > 15 and abs(room) < 1, f"bursts 23 dB over the room come down {cut:.1f} dB, the room between them {room:+.2f} dB")
check(np.allclose(y0[:, 0], x[:len(y0)], atol=1e-7), "amount 0 leaves the sound untouched")
tone = (0.2 * np.sin(2 * np.pi * 220 * np.arange(4 * SR) / SR)).astype(np.float32)
g = derustle(tone, 1)[SR:, 1]
check(g.min() > 0.97, f"a steady tone isn't pumped (lowest gain {g.min():.3f})")
step = np.concatenate([rng.standard_normal(3 * SR) * 0.003, rng.standard_normal(5 * SR) * 0.05]).astype(np.float32)
g = derustle(step, 1)[:, 1]
check(g[int(5.5 * SR):].min() > 0.9, f"a room that gets 24 dB louder and stays is let through within ~2.5 s (gain after {g[int(5.5 * SR):].min():.2f})")

# ---------------------------------------------------------------- dcs_pitch~
print("dcs_pitch~:")
pp = patch("pitch", ["adc~", "dcs_pitch~ 512", "s pt_p", "s pt_l", "s pt_s"], [(0, 0, 1, 0), (1, 0, 2, 0), (1, 1, 3, 0), (1, 2, 4, 0)])
def track(x):
    e = Engine(LIB, pp); e.listen("pt_p", "pt_l", "pt_s"); e.run(x); h = dict(e.heard); e.close(); return h
t = np.arange(2 * SR) / SR
worst = 0
for f in (80, 220, 440, 1000, 3000):
    h = track((0.1 * np.sin(2 * np.pi * f * t)).astype(np.float32))
    p = np.array(h["pt_p"][20:]); want = 69 + 12 * np.log2(f / 440)
    worst = max(worst, np.max(np.abs(p - want)) * 100 if len(p) and (p > 0).all() else 1e9)
check(worst < 5, f"sines from 80 Hz to 3 kHz within {worst:.1f} cents")
saw = (0.1 * (2 * ((150 * t) % 1) - 1)).astype(np.float32)
p = np.array(track(saw)["pt_p"][20:]); want = 69 + 12 * np.log2(150 / 440)
check((np.abs(p - want) < 0.1).mean() > 0.97, f"a bright 150 Hz sawtooth: no octave mistakes ({(np.abs(p - want) < 0.1).mean() * 100:.0f}% right)")
h = track((rng.standard_normal(2 * SR) * 0.05).astype(np.float32))
check(np.mean(np.array(h["pt_p"][20:]) < 0) > 0.9, f"noise has no pitch ({np.mean(np.array(h['pt_p'][20:]) < 0) * 100:.0f}% of frames)")
h = track((0.1 * np.sin(2 * np.pi * 440 * t)).astype(np.float32))
check(abs(np.median(h["pt_l"][20:]) - (100 + 20 * np.log10(0.1 / np.sqrt(2)))) < 0.5, f"level in Pd dB ({np.median(h['pt_l'][20:]):.1f})")
import time
def cost(obj):
    q = patch("cost", ["adc~", obj], [(0, 0, 1, 0)])
    e = Engine(LIB, q); x = (0.1 * np.sin(2 * np.pi * 220 * np.arange(20 * SR) / SR)).astype(np.float32)
    t0 = time.process_time(); e.run(x); c = time.process_time() - t0; e.close(); return c
cs, cd = min(cost("sigmund~ -hop 512 -npts 1024 pitch env") for _ in range(2)), min(cost("dcs_pitch~ 512") for _ in range(2))
check(cd < cs, f"costs {100 * cd / cs:.0f}% of sigmund~ with the same hop (both ~0.1% of real time: the win is accuracy)")

# ---------------------------------------------------------------- dcs_shift~
print("dcs_shift~:")
sp = patch("shift", ["adc~", "dcs_shift~ 7", "dac~"], [(0, 0, 1, 0), (1, 0, 2, 0), (1, 0, 2, 1)])
def vowel(f0=150, secs=3.0, formant=700):
    """A synthetic voice: a pulse train at f0 through a resonance at `formant` (its tone colour)."""
    n = int(secs * SR); x = np.zeros(n); x[(np.arange(0, n, SR / f0)).astype(int)] = 1.0
    r, th = 0.995, 2 * np.pi * formant / SR; y = np.zeros(n)
    for i in range(2, n):
        y[i] = x[i] + 2 * r * np.cos(th) * y[i - 1] - r * r * y[i - 2]
    return (0.1 * y / np.abs(y).max()).astype(np.float32)
v = vowel()
y = Engine(LIB, sp).run(v)[:, 0][SR:]
f0 = peak_hz(y, 180, 270)
check(abs(1200 * np.log2(f0 / (150 * 2 ** (7 / 12)))) < 5, f"a voice up a fifth lands on the fifth ({f0:.1f} Hz for {150 * 2 ** (7 / 12):.1f})")
S_ = np.abs(np.fft.rfft(y * np.hanning(len(y)))); fr = np.fft.rfftfreq(len(y), 1 / SR)
harm = [(k * f0, S_[np.argmin(np.abs(fr - k * f0))]) for k in range(1, 12)]
form = max(harm, key=lambda h: h[1])[0]
check(abs(form - 700) < f0 * 0.75, f"its tone colour stays: the strongest harmonic at {form:.0f} Hz (formant 700; a plain shifter: ~1050)")
d = db_frames(y)
check(d.max() - d.min() < 2, f"no warble: the level holds within {d.max() - d.min():.1f} dB")

# sing in key: C major; a voice sung between notes lands on the nearest one of the scale
tp = patch("tune", ["adc~", "dcs_shift~ 0", "dac~", "r tune"], [(0, 0, 1, 0), (1, 0, 2, 0), (1, 0, 2, 1), (3, 0, 1, 0)])
def sung(midi, snap=1, glide=0, key=0, scale=(0, 2, 4, 5, 7, 9, 11)):
    e = Engine(LIB, tp)
    e.send_msg("tune", "key", key); e.send_msg("tune", "scale", *scale)
    e.send_msg("tune", "snap", snap); e.send_msg("tune", "glide", glide)
    f = 440 * 2 ** ((midi - 69) / 12)
    y = e.run(vowel(f0=f))[:, 0][SR:]; e.close()
    return 69 + 12 * np.log2(peak_hz(y, f * 0.8, f * 1.25) / 440), y
for m, want, what in ((50.4, 50, "40 cents sharp of D"), (51.7, 52, "between D# and E (not in the scale)"),
                      (53.35, 53, "35 cents sharp of F")):
    got, _ = sung(m)
    check(abs(got - want) < 0.06, f"sung {what}: lands on {got:.2f} (MIDI {want})")
got, _ = sung(50.4, snap=0)
check(abs(got - 50.4) < 0.06, f"snap 0 leaves the voice alone ({got:.2f})")
got, _ = sung(50.4, snap=0.5)
check(abs(got - 50.2) < 0.06, f"snap 0.5 goes half way ({got:.2f})")
got, _ = sung(61.4, key=2, scale=(0, 2, 4, 5, 7, 9, 11))
check(abs(got - 61) < 0.06, f"key D major: C# is in it ({got:.2f})")
got, y = sung(50.4, glide=60)
d = db_frames(y)
check(abs(got - 50) < 0.06 and d.max() - d.min() < 2, f"with a 60 ms glide it settles too ({got:.2f}), no warble ({d.max() - d.min():.1f} dB)")

# ---------------------------------------------------------------- dcs_vocoder~
print("dcs_vocoder~:")
vcp = patch("vocoder", ["adc~", "dcs_vocoder~", "dac~", "r v"], [(0, 0, 1, 0), (1, 0, 2, 0), (1, 0, 2, 1), (3, 0, 1, 0)])
def vocode(x, *msgs):
    e = Engine(LIB, vcp)
    for m in (("smooth", 0), ("mix", 1)) + msgs:
        e.send_msg("v", m[0], *m[1:])
    y = e.run(x)[:, 0]; e.close(); return y
def spec(y):
    S_ = np.abs(np.fft.rfft(y * np.hanning(len(y)))); return S_, np.fft.rfftfreq(len(y), 1 / SR)
def near(S_, fr, f, w=3): return S_[(fr > f - w) & (fr < f + w)].max()
v = vowel(f0=150, secs=2.0)
chord = (130.81, 164.81, 196.0)
y = vocode(v, ("carrier", 0), *[("cnote", k, f) for k, f in enumerate(chord)])[SR // 2:]
S_, fr = spec(y)
check(min(near(S_, fr, f) for f in chord) > 5 * near(S_, fr, 150) and 0.4 < rms(y) / rms(v[SR // 2:]) < 2.5,
      f"chords: the voice speaks on the chord's notes, not its own pitch (level x{rms(y) / rms(v[SR // 2:]):.2f})")
cen = lambda y: float((spec(y)[0] * spec(y)[1]).sum() / spec(y)[0].sum())
lo, hi = (vocode(vowel(f0=150, secs=2.0, formant=f), ("carrier", 0), *[("cnote", k, c) for k, c in enumerate(chord)])[SR // 2:] for f in (500, 2000))
check(cen(hi) > cen(lo) * 1.4, f"the words come through: a brighter vowel stays brighter ({cen(lo):.0f} vs {cen(hi):.0f} Hz)")
y = vocode(np.zeros(SR, np.float32), ("carrier", 0))
check(np.abs(y).max() < 1e-3, f"silence in, silence out (peak {np.abs(y).max():.1e}): the carrier never leaks")
y = vocode(v, ("carrier", 1), ("snap", 0))[SR // 2:]; S_, fr = spec(y)
check(abs(peak_hz(y, 135, 165) - 150) < 1.5, f"your pitch: the synth follows the voice ({peak_hz(y, 135, 165):.1f} Hz for 150)")
y = vocode(v, ("carrier", 1), ("snap", 1), ("key", 0), ("scale", 0, 2, 4, 5, 7, 9, 11))[SR // 2:]; S_, fr = spec(y)
check(near(S_, fr, 146.83, 1.5) > 3 * near(S_, fr, 150, 1.5), "your pitch, snapped: 150 Hz sung, D (146.8) played (C major)")
y = vocode(v, ("carrier", 2))[SR // 2:]
check(0.3 < rms(y) / rms(v[SR // 2:]) < 3, f"whisper: noise speaks (level x{rms(y) / rms(v[SR // 2:]):.2f})")
y = vocode(v, ("mix", 0))
check(np.array_equal(y, v), "mix 0: the voice passes through untouched")
y = vocode(v, ("carrier", 0), ("key", 2), ("scale", 0, 2, 4, 5, 7, 9, 11))[SR // 2:]; S_, fr = spec(y)
check(near(S_, fr, 146.83) > 3 * near(S_, fr, 130.81), "no chord sent yet: it plays the key's own triad (D major)")
y = vocode(v, ("carrier", 3), ("mnote", 392))[SR // 2:]
check(abs(peak_hz(y, 370, 415) - 392) < 1.5, f"melody: the synth plays the melody's note ({peak_hz(y, 370, 415):.1f} Hz for 392)")
# the talk box: the mouth's shape (the vowel's resonance) filters the synth
y = vocode(v, ("style", 1), ("carrier", 0), *[("cnote", k, f) for k, f in enumerate(chord)])[SR // 2:]
S_, fr = spec(y)
check(min(near(S_, fr, f) for f in chord) > 5 * near(S_, fr, 150) and 0.4 < rms(y) / rms(v[SR // 2:]) < 2.5,
      f"talk box: the voice speaks on the chord's notes (level x{rms(y) / rms(v[SR // 2:]):.2f})")
def env_peak(y):
    S_, fr = spec(y); k = np.ones(int(150 / (fr[1] - fr[0]))); sm = np.convolve(S_, k / len(k), "same")
    band = (fr > 250) & (fr < 4000); return fr[band][np.argmax(sm[band])]
for f in (500, 1200, 2400):
    y = vocode(vowel(f0=150, secs=2.0, formant=f), ("style", 1), ("carrier", 0), *[("cnote", k, c) for k, c in enumerate(chord)])[SR // 2:]
    check(abs(env_peak(y) - f) < 0.15 * f, f"talk box: a vowel's resonance at {f} Hz comes out at {env_peak(y):.0f} Hz")
y = vocode(np.zeros(SR, np.float32), ("style", 1), ("carrier", 0))
check(np.abs(y).max() < 1e-3, f"talk box: silence in, silence out (peak {np.abs(y).max():.1e})")
y = vocode(v, ("style", 1), ("carrier", 3), ("mnote", 392))[SR // 2:]
check(abs(peak_hz(y, 370, 415) - 392) < 1.5 and np.isfinite(y).all(), f"talk box on the melody ({peak_hz(y, 370, 415):.1f} Hz for 392)")
y1 = vocode(v, ("style", 1), ("carrier", 3), ("mnote", 220), ("octave", 1.82))[SR // 2:]
check(abs(peak_hz(y1, 700, 950) - 880) < 10, f"octave: whole octaves only (1.82 plays 2 octaves up: {peak_hz(y1, 700, 950):.1f} Hz for 880, not 776)")
# chip carriers (Oct 19, `wave`): one pulse per note. A noise "voice" leaves the carrier's own harmonics: the 50%
# square has no even ones, the 25% pulse no 4th, the saws all of them
hiss = (np.random.default_rng(5).standard_normal(2 * SR) * 0.1).astype(np.float32)
def harmonics(wave):
    y = vocode(hiss, ("style", 1), ("carrier", 3), ("mnote", 220), ("wave", wave))[SR // 2:]; S_, fr = spec(y)
    return [near(S_, fr, 220 * h) for h in range(1, 6)], y
(hs, ys), (hq, yq), (hp, yp) = harmonics(0), harmonics(3), harmonics(1)
db = lambda a, b: 20 * np.log10(a / max(b, 1e-12))
check(db(hs[2], hs[1]) < 6 and db(hq[2], hq[1]) > 20 and db(hp[2], hp[3]) > 15 and db(hp[4], hp[3]) > 15
      and np.isfinite(yq).all() and 0.3 < rms(yq) / rms(ys) < 3,
      f"chip carriers: square: 3rd over 2nd {db(hq[2], hq[1]):.0f} dB (saws {db(hs[2], hs[1]):.0f}), 25% pulse: no 4th "
      f"({db(hp[2], hp[3]):.0f} / {db(hp[4], hp[3]):.0f} dB), level x{rms(yq) / rms(ys):.2f}")

# ---------------------------------------------------------------- dcs_onset~
print("dcs_onset~:")
op = patch("onset", ["adc~", "dcs_onset~", "f 1", "s on_b"], [(0, 0, 1, 0), (1, 0, 2, 0), (2, 0, 3, 0)])   # (the test hears numbers, not bangs)
def onsets(x):
    e = Engine(LIB, op); e.listen("on_b"); out, n, k = [], 64, 0
    for i in range(0, len(x) - n + 1, n):                  # block by block, so each onset has a time
        e.run(x[i:i + n]); m = len(e.heard["on_b"])
        out += [i / SR] * (m - k); k = m
    e.close(); return out
def claps_over(room_db, clap_db, secs=8.0, seed=11):
    r = np.random.default_rng(seed); n = int(secs * SR); x = r.standard_normal(n) * 10 ** (room_db / 20); at = []
    for t in np.arange(1.0, secs - 0.5, 0.7):
        i = int(t * SR); k = int(0.05 * SR); x[i:i + k] += r.standard_normal(k) * 10 ** (clap_db / 20) * np.exp(-np.arange(k) / (0.01 * SR)); at.append(t)
    return x.astype(np.float32), at
def score(found, at, tol=0.03):
    hit = [min((f - a for f in found if 0 <= f - a < tol), default=None) for a in at]
    late = [h for h in hit if h is not None]
    false = sum(1 for f in found if not any(0 <= f - a < tol for a in at))
    return len(late), false, (max(late) if late else 1)
x, at = claps_over(-60, -15)
h, fa, lat = score(onsets(x), at)
check(h == len(at) and fa == 0 and lat < 0.012, f"claps in a quiet room: {h}/{len(at)} found, {fa} false, at most {lat * 1000:.0f} ms late")
x, at = claps_over(-25, -17)
h, fa, lat = score(onsets(x), at)
check(h >= len(at) - 1 and fa <= 1, f"claps 8 dB over a loud roar: {h}/{len(at)} found, {fa} false")
t = np.arange(4 * SR) / SR
vib = (0.1 * np.sin(2 * np.pi * np.cumsum(440 * 2 ** (0.05 * np.sin(2 * np.pi * 6 * t))) / SR)).astype(np.float32)
f = [v for v in onsets(vib) if v > 0.5]
check(len(f) == 0, f"a held note with vibrato: {len(f)} onsets after its start")
env = np.clip(t / 0.4, 0, 1) ** 2
swell = (rng.standard_normal(len(t)) * 0.1 * env + rng.standard_normal(len(t)) * 0.001).astype(np.float32)
f = onsets(swell)
check(len(f) <= 1, f"a sound swelling up over 0.4 s: {len(f)} onsets (a level detector fires on swells)")

# ---------------------------------------------------------------- dcs_reverb~
print("dcs_reverb~:")
COMB, ALLP, EXTRA = [25.3, 26.9, 29.0, 30.7, 32.2, 33.8, 35.3, 36.7], [12.6, 10.0, 7.7, 5.1], 0.52   # (make_patch.py's)
def reverb_reference():
    """The reverb as make_patch.py builds it from Pd objects (both ears), fed by adc~, feedback 0.9, tone 4000."""
    objs, conns = ["adc~", "sig~ 0.9", "r tone", "dac~"], []
    def add(t):
        objs.append(t); return len(objs) - 1
    for side, extra, outlet in (("l", 0.0, 0), ("r", EXTRA, 1)):
        combs = []
        for i, ms in enumerate(COMB):
            rd = add(f"delread~ c{side}{i} {ms + extra:.2f}"); lp = add("lop~ 6000"); fbm = add("*~"); sm = add("+~")
            wr = add(f"delwrite~ c{side}{i} 100")
            conns += [(rd, 0, lp, 0), (2, 0, lp, 1), (lp, 0, fbm, 0), (1, 0, fbm, 1), (0, 0, sm, 0), (fbm, 0, sm, 1), (sm, 0, wr, 0)]
            combs.append(rd)
        acc = combs[0]
        for c in combs[1:]:
            a = add("+~"); conns += [(acc, 0, a, 0), (c, 0, a, 1)]; acc = a
        for k, ms in enumerate(ALLP):
            rd = add(f"delread~ a{side}{k} {ms + extra:.2f}"); g1 = add("*~ 0.5"); wv = add("+~"); wr = add(f"delwrite~ a{side}{k} 100")
            g2 = add("*~ 0.5"); y = add("-~")
            conns += [(rd, 0, g1, 0), (acc, 0, wv, 0), (g1, 0, wv, 1), (wv, 0, wr, 0), (wv, 0, g2, 0), (rd, 0, y, 0), (g2, 0, y, 1)]
            acc = y
        conns.append((acc, 0, 3, outlet))
    return patch("rev_ref", objs, conns)
def reverb_dcs():
    args = " ".join(str(v) for v in [EXTRA, len(COMB)] + COMB + ALLP)
    return patch("rev_dcs", ["adc~", "sig~ 0.9", "r tone", "dac~", f"dcs_reverb~ {args}", "msg tone \\$1"],
                 [(0, 0, 4, 0), (1, 0, 4, 1), (2, 0, 5, 0), (5, 0, 4, 0), (4, 0, 3, 0), (4, 1, 3, 1)])
rd_ = reverb_dcs(); src = open(rd_).read().replace("#X obj 10 165 msg tone", "#X msg 10 165 tone"); open(rd_, "w").write(src)
xin = np.zeros(4 * SR, np.float32); xin[100] = 0.5; xin[SR:SR + 2000] = rng.standard_normal(2000) * 0.2
outs, costs = [], []
for p in (reverb_reference(), rd_):
    e = Engine(LIB, p); e.send("tone", 4000); e.run(np.zeros(640, np.float32))
    t0 = time.process_time(); outs.append(e.run(xin)); costs.append(time.process_time() - t0); e.close()
diff = np.abs(outs[0] - outs[1]).max(); peak = np.abs(outs[0]).max()
check(peak > 0.05 and diff < 1e-5 * max(peak, 1), f"matches the Pd-object reverb sample for sample (worst {diff:.1e}, peak {peak:.2f}), both ears")
check(costs[1] < 0.75 * costs[0], f"costs {100 * costs[1] / costs[0]:.0f}% of the Pd-object version (the maths is the same: it saves the overhead of ~100 objects)")

# ---------------------------------------------------------------- dcs_crush~ styles
print("dcs_crush~ styles:")
cp = patch("crush", ["adc~", "r c", "dcs_crush~", "dac~"], [(0, 0, 2, 0), (1, 0, 2, 0), (2, 0, 3, 0), (2, 0, 3, 1)])
t = np.arange(SR) / SR
sine = (0.5 * np.sin(2 * np.pi * 440 * t)).astype(np.float32)
def crushed(style):
    e = Engine(LIB, cp)
    for m in (("mix", 1), ("on", 1), ("style", style), ("smooth", 0)):
        e.send_msg("c", *m)
    y = e.run(sine)[int(0.2 * SR):, 0]; e.close(); return y
def above(y, hz):
    Y = np.abs(np.fft.rfft(y * np.hanning(len(y)))) ** 2; f = np.fft.rfftfreq(len(y), 1 / SR)
    return Y[f > hz].sum() / Y.sum()
names = ["", "8-bit sampler", "SP-1200", "NES", "Game Boy", "SNES", "Genesis"]
hi = {}
for k in range(1, 7):
    y = crushed(k)
    gain = 20 * np.log10(rms(y) / rms(sine[int(0.2 * SR):]))
    hi[k] = above(y, 7000)
    check(np.isfinite(y).all() and abs(peak_hz(y, 300, 600) / 440 - 1) < 0.01 and -7 < gain < 3,
          f"{names[k]}: in tune, level {gain:+.1f} dB, {100 * hi[k]:.2f}% of the energy over 7 kHz")
check(hi[1] < 0.01 and hi[5] < 0.01 and hi[4] > 10 * hi[1],
      "the filtered machines (sampler, SNES) are smooth up top, the Game Boy buzzes (aliasing images)")
loud = (0.9 * np.sin(2 * np.pi * 3000 * t)).astype(np.float32)
e = Engine(LIB, cp)
for m in (("mix", 1), ("on", 1), ("style", 3), ("smooth", 0)):
    e.send_msg("c", *m)
y = e.run(loud)[int(0.2 * SR):, 0]; e.close()
g = 20 * np.log10(rms(y) / rms(loud))
check(g < -10, f"NES: the DPCM channel's 1-bit steps can't follow a loud 3 kHz tone ({g:+.1f} dB), as on the console")

# ---------------------------------------------------------------- dcs_voice~
print("dcs_voice~:")
vp = patch("voice", ["r vc", "dcs_voice~", "dac~"], [(0, 0, 1, 0), (1, 0, 2, 0), (1, 0, 2, 1)])
def play(msgs, secs, then=(), then_at=None):
    e = Engine(LIB, vp)
    for m in msgs:
        e.send_msg("vc", m[0], *m[1:])
    if then_at is None:
        y = e.run(np.zeros(int(secs * SR), np.float32))[:, 0]
    else:
        a = e.run(np.zeros(int(then_at * SR), np.float32))[:, 0]
        for m in then:
            e.send_msg("vc", m[0], *m[1:])
        y = np.concatenate([a, e.run(np.zeros(int((secs - then_at) * SR), np.float32))[:, 0]])
    e.close(); return y
def harm(y, f0, k):
    t = np.arange(len(y)) / SR; return abs(np.mean(y * np.exp(-2j * np.pi * f0 * k * t)))
y = play([("engine", 1), ("bright", 0.3), ("note", 220, 0.5, 1500)], 2.0)
w = y[int(0.05 * SR):int(0.5 * SR)]
check(abs(peak_hz(w, 200, 240) / 220 - 1) < 0.002 and harm(w, 220, 1) > 3 * harm(w, 220, 2) and 0.05 < np.abs(y).max() < 1
      and db_frames(y)[-5:].mean() < db_frames(y)[:5].mean() - 30,
      f"pluck: in tune, the note leads its overtones, dies away (peak {np.abs(y).max():.2f})")
y = play([("engine", 2), ("attack", 300), ("release", 600), ("pnote", 0, 110)], 4.0, [("pnote", 0, 165)], 2.5)
held = db_frames(y[int(1.0 * SR):int(2.4 * SR)], 100)   # (100 ms frames: 20 ms hold ~2 periods of 110 Hz and ripple by themselves)
late = y[int(3.4 * SR):]
check(held.max() - held.min() < 4 and held.mean() > -20 and abs(peak_hz(y[int(1.0 * SR):int(2.4 * SR)], 100, 120) / 110 - 1) < 0.003
      and harm(late, 165, 1) > 5 * harm(late, 110, 1),
      f"bowed: a held note stays level ({held.max() - held.min():.1f} dB swing), then the slot moves to the new note")
y = play([("engine", 2), ("release", 300), ("pnote", 0, 110)], 3.0, [("poff", 0)], 1.5)
check(np.abs(y[int(2.4 * SR):]).max() < 1e-3, "bowed: poff lets it go")
y = play([("engine", 3), ("modal", 1), ("note", 440, 0.5, 2000)], 2.5)
w = y[int(0.02 * SR):int(0.6 * SR)]
check(abs(peak_hz(w, 400, 480) / 440 - 1) < 0.002 and harm(w, 440 * 2.76, 1) > 0.1 * harm(w, 440, 1) and 0.02 < np.abs(y).max() < 1,
      f"modal bell: the note and its 2.76x partial, struck once (peak {np.abs(y).max():.2f})")
check(np.abs(play([("engine", 0), ("note", 440, 0.5, 1000)], 1.0)).max() == 0, "engine 0 plays nothing")
def cent(y):
    S_ = np.abs(np.fft.rfft(y * np.hanning(len(y)))); f = np.fft.rfftfreq(len(y), 1 / SR); return (S_ * f).sum() / S_.sum()
an = [("engine", 4), ("fenv", 0), ("reso", 0), ("attack", 5), ("detune", 0)]   # (detuned saws beat on purpose: off here)
y = play(an + [("cutoff", 4), ("pnote", 0, 220)], 1.5); w = y[int(0.3 * SR):]
dark = play(an + [("cutoff", 1.5), ("pnote", 0, 220)], 1.5)[int(0.3 * SR):]
bright = play(an + [("cutoff", 16), ("pnote", 0, 220)], 1.5)[int(0.3 * SR):]
# (0.75: each voice's random start comes from its memory address, which moves from run to run: dark/middle measured 0.51-0.72 over 11 runs)
check(abs(peak_hz(w, 200, 240) / 220 - 1) < 0.003 and cent(dark) < 0.75 * cent(w) < 0.75 * cent(bright) and 0.05 < np.abs(w).max() < 1,
      f"analog: in tune, the cutoff darkens and brightens it (brightness {cent(dark):.0f} / {cent(w):.0f} / {cent(bright):.0f} Hz)")
res = play([("engine", 4), ("fenv", 0), ("attack", 5), ("sub", 0), ("cutoff", 6), ("reso", 0.9), ("pnote", 0, 110)], 1.5)[int(0.3 * SR):]
flat = play([("engine", 4), ("fenv", 0), ("attack", 5), ("sub", 0), ("cutoff", 6), ("reso", 0), ("pnote", 0, 110)], 1.5)[int(0.3 * SR):]
check(harm(res, 110, 6) / harm(res, 110, 1) > 3 * harm(flat, 110, 6) / harm(flat, 110, 1),
      f"analog: resonance lifts the harmonics at the cutoff ({harm(res, 110, 6) / harm(res, 110, 1):.2f} vs {harm(flat, 110, 6) / harm(flat, 110, 1):.2f})")
hot = play([("engine", 4), ("fenv", 8), ("attack", 5), ("cutoff", 8), ("reso", 1), ("pnote", 0, 55)], 3.0)
check(np.isfinite(hot).all() and np.abs(hot).max() < 2, f"analog: full resonance stays stable (peak {np.abs(hot).max():.2f})")
hi = play(an + [("cutoff", 16), ("sub", 0), ("pnote", 0, 3000)], 1.0)[int(0.3 * SR):]
S_ = np.abs(np.fft.rfft(hi * np.hanning(len(hi)))) ** 2; f = np.fft.rfftfreq(len(hi), 1 / SR)
on_harm = sum(S_[np.abs(f - 3000 * k) < 15].sum() for k in range(1, 9))
check(on_harm / S_.sum() > 0.9, f"analog: a 3 kHz saw is band-limited ({100 * on_harm / S_.sum():.0f}% of its energy on its harmonics)")
dr = [("engine", 5)]
k = play(dr + [("hit", 0, 1)], 1.0)
check(abs(peak_hz(k[int(0.15 * SR):int(0.45 * SR)], 30, 120) - 50) < 4 and 0.3 < np.abs(k).max() <= 1.05
      and np.abs(k[int(0.9 * SR):]).max() < 0.25 * np.abs(k).max(),
      f"drums: the kick dives to its 50 Hz tune and dies away (peak {np.abs(k).max():.2f})")
sn = play(dr + [("hit", 1, 1)], 0.5)[:int(0.2 * SR)]
S_ = np.abs(np.fft.rfft(sn)) ** 2; fr = np.fft.rfftfreq(len(sn), 1 / SR)
check(S_[fr > 2000].sum() > 0.3 * S_.sum() and harm(sn, 185, 1) > 0.01, f"drums: the snare has its tone and its crack ({100 * S_[fr > 2000].sum() / S_.sum():.0f}% above 2 kHz)")
hc = play(dr + [("hit", 2, 1, 45)], 0.6); ho = play(dr + [("hit", 2, 1, 300)], 0.6)
S_ = np.abs(np.fft.rfft(hc)) ** 2; fr = np.fft.rfftfreq(len(hc), 1 / SR)
check(S_[fr > 5000].sum() > 0.8 * S_.sum() and rms(ho[int(0.15 * SR):]) > 10 * rms(hc[int(0.15 * SR):]),
      f"drums: hats are high ({100 * S_[fr > 5000].sum() / S_.sum():.0f}% above 5 kHz), an open one rings on")
check(np.abs(play([("engine", 4), ("hit", 0, 1)], 0.3)).max() == 0, "drums: hits only play on the drums engine")
one = play(an + [("cutoff", 16), ("saws", 1), ("pnote", 0, 220)], 1.5)[int(0.3 * SR):]
sev = play(an + [("cutoff", 16), ("saws", 7), ("detune", 25), ("pnote", 0, 220)], 1.5)[int(0.3 * SR):]
lift = 20 * np.log10(rms(sev) / rms(one))
check(abs(lift) < 3 and abs(peak_hz(sev, 210, 230) / 220 - 1) < 0.01, f"supersaw: 7 saws across 25 cents, about as loud as one ({lift:+.1f} dB), around the note")
def env_rate(y):
    """The strongest rate (Hz) at which y's level moves (2-20 Hz)."""
    e = np.sqrt(np.convolve(y ** 2, np.ones(240) / 240, "same"))[::48]; e = e - e.mean()
    S_ = np.abs(np.fft.rfft(e * np.hanning(len(e)), 1 << 16)); f = np.fft.rfftfreq(1 << 16, 48 / SR)
    b = (f > 2) & (f < 20); return float(f[b][np.argmax(S_[b])]), float(S_[b].max() / (np.abs(e).sum() + 1e-9))
wob = an + [("cutoff", 3), ("wrate", 2), ("tempo", 120)]
yw = play(wob + [("wobble", 1), ("pnote", 0, 220)], 3.0)[SR:]; y0 = play(wob + [("wobble", 0), ("pnote", 0, 220)], 3.0)[SR:]
rw, sw_ = env_rate(yw); _, s0_ = env_rate(y0)
check(abs(rw - 4) < 0.2 and sw_ > 5 * s0_, f"wobble: 2 a beat at 120 BPM pulses at {rw:.2f} Hz (vs no wobble: {sw_ / max(s0_, 1e-9):.0f}x stronger)")
yb = play(an + [("cutoff", 8), ("bend", -2), ("bendms", 150), ("pnote", 0, 220)], 1.0)
check(peak_hz(yb[int(0.005 * SR):int(0.04 * SR)], 150, 260) < 210 and abs(peak_hz(yb[int(0.5 * SR):], 200, 240) / 220 - 1) < 0.003,
      f"bend: a note starts low ({peak_hz(yb[int(0.005 * SR):int(0.04 * SR)], 150, 260):.0f} Hz) and glides to 220")
# chip: a console's sound chip
def chipnote(chip, wave, hz, secs=0.6, extra=()):
    return play([("chip", chip), ("wave", wave), ("sus", 1), ("level", 1)] + list(extra) + [("engine", 8), ("note", hz, 0.8, secs * 1000)], secs + 0.1)
y = chipnote(1, 0, 440)[int(0.05 * SR):int(0.5 * SR)]
nes = 1789773 / (16 * (round(1789773 / (16 * 440) - 1) + 1))
check(abs(peak_hz(y, 400, 480) - nes) < 1.5 and harm(y, nes, 2) < 0.1 * harm(y, nes, 1), f"chip NES pulse: in tune on the NES's own period ({peak_hz(y, 400, 480):.1f} Hz, chip {nes:.1f}), a square has no 2nd harmonic")
y = chipnote(1, 0, 440, extra=[("duty", 0.125)])[int(0.05 * SR):int(0.5 * SR)]
check(harm(y, nes, 2) > 0.5 * harm(y, nes, 1), "chip NES pulse at 12.5%: a thin, nasal tone (a strong 2nd harmonic)")
y = chipnote(1, 0, 3520)[int(0.05 * SR):int(0.5 * SR)]
f = peak_hz(y, 3300, 3700)
check(abs(1200 * np.log2(f / 3520) + 12.2) < 3, f"chip NES: a high note lands where the chip's period can ({f:.0f} Hz, {1200 * np.log2(f / 3520):+.0f} cents, as the real one)")
for chip, wave, name in ((1, 1, "NES triangle"), (2, 0, "Game Boy pulse"), (2, 2, "Game Boy wave"), (3, 3, "Genesis FM")):
    y = chipnote(chip, wave, 220)[int(0.05 * SR):int(0.5 * SR)]
    f = peak_hz(y, 200, 240)
    check(abs(1200 * np.log2(f / 220)) < 8 and 0.02 < np.abs(y).max() < 1, f"chip {name}: in tune ({f:.1f} Hz), level {np.abs(y).max():.2f}")
y = play([("chip", 1), ("engine", 8), ("note", 440, 0.8, 1000)], 1.0, then=[("note", 660, 0.8, 500)], then_at=0.4)
w = y[int(0.5 * SR):int(0.85 * SR)]
check(harm(w, 440, 1) < 0.05 * harm(w, 660, 1), "chip: one note at a time on a channel (the new one cuts the old)")
y = play([("chip", 1), ("arp", 40), ("engine", 8), ("pnote", 0, 262), ("pnote", 1, 330), ("pnote", 2, 392)], 1.2)[int(0.2 * SR):]
win = int(0.02 * SR); doms = []
for i in range(0, len(y) - win, win):
    seg = y[i:i + win]; doms.append(max((262, 330, 392), key=lambda f: harm(seg, f, 1)))
check(len(set(doms)) == 3 and sum(a != b for a, b in zip(doms, doms[1:])) >= 15,
      f"chip arp: a chord's notes take turns on the one channel ({sum(a != b for a, b in zip(doms, doms[1:]))} changes in 1 s)")
kick, snare, hat = (play([("chip", 1), ("engine", 8), ("hit", r, 1)], 0.4) for r in (0, 1, 2))
cen = lambda y: float((np.abs(np.fft.rfft(y)) * np.fft.rfftfreq(len(y), 1 / SR)).sum() / np.abs(np.fft.rfft(y)).sum())
check(min(rms(kick), rms(snare), rms(hat)) > 0.005 and cen(kick) < cen(snare) < cen(hat),
      f"chip drums: kick, snare, hat, each brighter ({cen(kick):.0f}, {cen(snare):.0f}, {cen(hat):.0f} Hz)")

# sample: a kit's own WAV files
import wave, struct
def wav(name, x, sr=SR, bits=16, chans=1, flt=False):
    path = os.path.join(TMP, name); x = np.asarray(x, np.float64)
    data = np.repeat(x[:, None], chans, axis=1).ravel()
    if flt:                                                  # (wave can't write float: a hand-made header)
        raw = data.astype("<f4").tobytes()
        fmt = struct.pack("<HHIIHH", 3, chans, sr, sr * 4 * chans, 4 * chans, 32)
        with open(path, "wb") as f:
            f.write(b"RIFF" + struct.pack("<I", 4 + 8 + len(fmt) + 8 + len(raw)) + b"WAVE" + b"fmt " + struct.pack("<I", len(fmt)) + fmt
                    + b"data" + struct.pack("<I", len(raw)) + raw)
        return path
    with wave.open(path, "wb") as w:
        w.setnchannels(chans); w.setsampwidth(bits // 8); w.setframerate(sr)
        q = np.round(data * (2 ** (bits - 1) - 1)).astype(np.int64)
        w.writeframes(b"".join(int(v).to_bytes(bits // 8, "little", signed=True) for v in q) if bits == 24 else q.astype("<i2").tobytes())
    return path
tt = np.arange(SR) / SR
c4 = wav("c4.wav", 0.5 * np.sin(2 * np.pi * 261.63 * tt) * np.exp(-tt * 0.5))
t44 = np.arange(44100) / 44100
c5 = wav("c5.wav", 0.5 * (np.sin(2 * np.pi * 523.25 * t44) + 0.8 * np.sin(2 * np.pi * 523.25 * 3 * t44)) / 1.8, sr=44100)
y = play([("sample", c4, 60), ("attack", 2), ("engine", 9), ("note", 392, 0.8, 500)], 0.6)[int(0.05 * SR):int(0.5 * SR)]
check(abs(1200 * np.log2(peak_hz(y, 350, 430) / 392)) < 5 and np.abs(y).max() > 0.02, f"sample: a C4 recording plays G4 ({peak_hz(y, 350, 430):.1f} Hz)")
y = play([("sample", c4, 60), ("sample", c5, 72), ("attack", 2), ("engine", 9), ("note", 466.16, 0.8, 500)], 0.6)[int(0.05 * SR):int(0.5 * SR)]
check(abs(peak_hz(y, 430, 500) - 466.16) < 2 and harm(y, 466.16, 3) > 0.3 * harm(y, 466.16, 1),
      "sample: a note takes the nearest recording (A#4 from the C5 one, at 44.1 kHz, its own tone)")
y = play([("sample", c4, 60), ("attack", 2), ("release", 30), ("oneshot", 0), ("engine", 9), ("note", 261.63, 0.8, 200)], 0.8)
y1 = play([("sample", c4, 60), ("attack", 2), ("engine", 9), ("note", 261.63, 0.8, 200)], 0.8)
check(rms(y[int(0.35 * SR):]) < 1e-4 and rms(y1[int(0.35 * SR):]) > 0.03, "sample: oneshot 0 stops at the note's length, oneshot 1 plays to the end")
y = play([("sample", c4, 60), ("attack", 2), ("loop", 1), ("loopstart", 200), ("engine", 9), ("pnote", 0, 261.63)], 3.0)
d = np.abs(np.diff(y[int(0.1 * SR):]))
check(rms(y[int(2.0 * SR):int(2.9 * SR)]) > 0.02 and d.max() < 0.05, f"sample: a held note loops past the recording's end, smoothly (max step {d.max():.3f})")
c4b = wav("c4_24.wav", 0.5 * np.sin(2 * np.pi * 261.63 * tt) * np.exp(-tt * 0.5), bits=24, chans=2)
c4f = wav("c4_f.wav", 0.5 * np.sin(2 * np.pi * 261.63 * tt) * np.exp(-tt * 0.5), chans=2, flt=True)
ys = [play([("sample", f, 60), ("attack", 2), ("engine", 9), ("note", 261.63, 0.8, 500)], 0.5) for f in (c4, c4b, c4f)]
check(all(np.abs(a - ys[0]).max() < 1e-3 for a in ys[1:]), "sample: 16-bit, 24-bit stereo and float files read alike")
kick = wav("kick.wav", np.sin(2 * np.pi * 60 * tt[:SR // 4]) * np.exp(-tt[:SR // 4] * 20))
ohat = wav("ohat.wav", np.random.default_rng(1).standard_normal(SR // 2) * 0.2)
yk = play([("dsample", 0, kick), ("engine", 9), ("hit", 0, 1)], 0.4)
yh = play([("dsample", 2, kick), ("dsample", 3, ohat), ("engine", 9), ("hit", 2, 1, 300)], 0.4)
check(rms(yk) > 0.01 and abs(peak_hz(yk, 40, 100) - 60) < 3 and rms(yh) > 0.01 and peak_hz(yh, 40, 100) != 60,
      "sample drums: a kick plays its file, a long hat its open hat")
y = play([("sample", os.path.join(TMP, "nothing.wav"), 60), ("engine", 9), ("note", 261.63, 0.8, 500)], 0.3)
check(np.abs(y).max() == 0, "sample: a missing file is skipped (silence, no crash)")
y = play([("sample", c4, 60), ("attack", 2), ("engine", 9), ("pnote", 0, 261.63)], 1.0, then=[("clear",)], then_at=0.3)
check(rms(y[int(0.35 * SR):]) == 0, "sample: clear stops and forgets them")

cp = patch("chop", ["r vc", "dcs_voice~", "dac~", "table fpad_0 48000", "table fpad_f0 2", "table fpad_g 2", "table fpad_n 2"],
           [(0, 0, 1, 0), (1, 0, 2, 0), (1, 0, 2, 1)])
def chop(kept, secs=0.6):
    e = Engine(LIB, cp); L = e.lib
    L.libpd_write_array.argtypes = [ctypes.c_char_p, ctypes.c_int, ctypes.c_void_p, ctypes.c_int]
    def put(name, vals):
        a = np.asarray(vals, np.float32); L.libpd_write_array(name.encode(), 0, a.ctypes.data, len(a))
    put("fpad_0", 0.5 * np.sin(2 * np.pi * 220 * np.arange(48000) / SR)); put("fpad_f0", [220, 0]); put("fpad_g", [1, 1])
    put("fpad_n", [kept, 0])
    for m in [("engine", 6), ("chop", 180), ("note", 330, 0.8, 1000)]:
        e.send_msg("vc", m[0], *m[1:])
    y = e.run(np.zeros(int(secs * SR), np.float32))[:, 0]; e.close(); return y
yc = chop(1); yf = chop(0)
on = np.nonzero(np.abs(yc) > 0.01)[0]
check(len(on) and abs(peak_hz(yc[on[0]:on[-1]], 280, 380) / 330 - 1) < 0.005 and 0.15 < (on[-1] - on[0]) / SR < 0.21
      and rms(yf) > 0.01 and peak_hz(yf, 280, 380) > 320,
      f"chop: a slice of the held voice at the note ({peak_hz(yc[on[0]:on[-1]], 280, 380):.1f} Hz, {(on[-1] - on[0]) / SR * 1000:.0f} ms); nothing kept: the analog voice plays it")
sb = [("engine", 7), ("decay", 2000), ("drive", 0.3)]
y8 = play(sb + [("glide", 120), ("pnote", 0, 55)], 3.0, [("pnote", 0, 82.4)], 1.5)
mid = [peak_hz(y8[int(t * SR):int((t + 0.06) * SR)], 40, 100) for t in (1.50, 1.56, 1.62)]
check(abs(peak_hz(y8[int(0.3 * SR):int(1.4 * SR)], 40, 100) - 55) < 1 and abs(peak_hz(y8[int(1.9 * SR):], 40, 100) - 82.4) < 1
      and mid[0] < mid[1] < 82 and rms(y8[int(1.55 * SR):int(1.7 * SR)]) > rms(y8[int(1.3 * SR):int(1.45 * SR)]),
      f"808: a held 55 Hz slides to 82.4 over ~120 ms ({[round(m) for m in mid]} Hz), its level starting again")
check(db_frames(y8[:int(1.5 * SR)], 100)[1] > db_frames(y8[:int(1.5 * SR)], 100)[13] + 4, "808: each note dies away over its decay")
oc = play([("engine", 4), ("fenv", 0), ("attack", 5), ("detune", 0), ("octave", -1), ("pnote", 0, 220)], 1.0)[int(0.3 * SR):]
check(abs(peak_hz(oc, 90, 130) - 110) < 1, "octave -1 plays a 220 Hz note at 110")
e = Engine(LIB, vp); e.send_msg("vc", "engine", 1); e.send_msg("vc", "chance", 0.3)
heard = 0
for k in range(200):                       # (each note dies within its 0.25 s window, so none is counted twice)
    e.send_msg("vc", "note", 440, 0.5, 120); heard += np.abs(e.run(np.zeros(int(0.25 * SR), np.float32))[:, 0]).max() > 0.01
e.close()
check(40 < heard < 80, f"chance 0.3 plays about a third of the notes ({heard} of 200)")
held = db_frames(play(an + [("cutoff", 4), ("pnote", 0, 110)], 3.0)[int(0.5 * SR):], 100)
check(held.max() - held.min() < 1, f"analog: a held note stands steady ({held.max() - held.min():.2f} dB)")

# ---------------------------------------------------------------- dcs_dist~ quality (Oct 19)
print("dcs_dist~ quality:")
dp = patch("dist", ["adc~", "dcs_dist~", "dac~", "r d"], [(0, 0, 1, 0), (1, 0, 2, 0), (1, 0, 2, 1), (3, 0, 1, 0)])
def distort(x, *msgs):
    e = Engine(LIB, dp)
    for m in (("smooth", 0), ("on", 1)) + msgs:
        e.send_msg("d", m[0], *m[1:])
    y = e.run(x)[:, 0]; e.close(); return y
tt = np.arange(SR) / SR
quiet = (0.15 * (np.sin(2 * np.pi * 440 * tt) + np.sin(2 * np.pi * 3100 * tt) + np.sin(2 * np.pi * 9000 * tt))).astype(np.float32)
for mixv in (1, 0.5):
    y = distort(quiet, ("type", 1), ("drive", 1), ("mix", mixv), ("quality", 1))
    err = np.abs(y[2000:] - quiet[2000 - 16:-16]).max()
    check(err < 2e-3, f"clean, mix {mixv}: below the clip it passes the sound 16 samples late, dry and wet in step (error {err:.1e})")
def alias_share(y, f0):
    S_ = np.abs(np.fft.rfft(y * np.hanning(len(y)))) ** 2; fr = np.fft.rfftfreq(len(y), 1 / SR)
    harm = np.zeros(len(fr), bool)
    for h in range(1, int(SR / 2 / f0) + 1):
        harm |= np.abs(fr - h * f0) < 15
    return S_[~harm & (fr > 30)].sum() / S_.sum()
loud = (0.8 * np.sin(2 * np.pi * 4500 * tt)).astype(np.float32)
# (fold makes most of its sound above hearing: on this high tone Classic's fold was a quarter fizz at drive 5 and
# nearly all fizz at 20, so Clean is that much quieter: x0.74 and x0.27. The level lost is the fizz.)
for typ, name, drv in ((1, "hard", 20), (0, "soft", 20), (2, "fold", 5)):
    y0 = distort(loud, ("type", typ), ("drive", drv), ("mix", 1))[SR // 4:]
    y1 = distort(loud, ("type", typ), ("drive", drv), ("mix", 1), ("quality", 1))[SR // 4:]
    a0, a1 = alias_share(y0, 4500), alias_share(y1, 4500)
    check(a1 < a0 / 30 and (0.5 if typ == 2 else 0.85) < rms(y1) / rms(y0) < 1.15,
          f"clean, {name} at drive {drv} on 4.5 kHz: folded-back fizz {10 * np.log10(a0):.0f} -> {10 * np.log10(a1):.0f} dB of the sound, level x{rms(y1) / rms(y0):.2f}")

# ---------------------------------------------------------------- dcs_loaded
print("dcs_loaded:")
d = patch("loaded", ["dcs_loaded", "f", "r dl_ping", "t b", "s dl_out", "r dl_bang", "t b", "dcs_loaded", "s dl_out2"],
          [(0, 0, 1, 1), (2, 0, 3, 0), (3, 0, 1, 0), (1, 0, 4, 0), (5, 0, 6, 0), (6, 0, 7, 0), (7, 0, 8, 0)])
e = Engine(LIB, d); e.listen("dl_out", "dl_out2")
e.send("dl_ping", 0); e.send("dl_bang", 0); e.run(np.zeros(64, dtype=np.float32))
check(e.heard["dl_out"] == [1.0], f"sends 1 as its patch loads ({e.heard['dl_out']})")
check(e.heard["dl_out2"] == [1.0], f"and on a bang ({e.heard['dl_out2']})")

print("ALL PASSED" if not fails else f"{len(fails)} FAILED")
sys.exit(1 if fails else 0)
