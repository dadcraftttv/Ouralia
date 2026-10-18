#!/usr/bin/env python3
"""What each effect costs (process time for 20 s of audio through the whole built-in patch): everything at rest,
then each effect switched on alone at a typical setting, then DadCraft-like settings. The best of 3 runs each,
interleaved (other processes' load sways them all alike). Usage: tools/.venv/bin/python tools/bench_effects.py
tools/.libpd/libpd.so [seconds]"""
import os, sys, time
import numpy as np
sys.path.insert(0, os.path.dirname(__file__))
from pdtest import Engine, SR

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PATCH = os.path.join(ROOT, "app/src/main/assets/patches/test_scene.pd")
LIB = sys.argv[1] if len(sys.argv) > 1 else "libpd.so"
SECONDS = float(sys.argv[2]) if len(sys.argv) > 2 else 20.0
REST = dict(gain=1, dry=1, smooth=30, echo_on=1, wet=0, ring_on=1, ring_mix=0, pitch_on=1, pitch_mix=0,
            crush_on=1, crush_mix=0, dist_on=1, dist_mix=0, filter_on=1, filter_mix=0, chorus_on=1, chorus_mix=0,
            flange_on=1, flange_mix=0, phaser_on=1, phaser_mix=0, trem_on=1, trem_depth=0, rvs_on=1, rvs_mix=0,
            glitch_on=1, glitch_hold=0, reverb_on=1, reverb_mix=0, space_on=1, space_shift=0, space_pan=0,
            music_on=0, beat_level=0, gc_amount=0, gate_thresh=-80, comp_thresh=0)
EFFECTS = {
    "ring":    dict(ring_mix=0.5, ring_freq=120),
    "pitch":   dict(pitch_mix=0.5, pitch_semi=7, pitch_window=60),
    "crush":   dict(crush_mix=0.7, crush_bits=8, crush_rate=8000),
    "dist":    dict(dist_mix=0.6, dist_drive=6, dist_type=0),
    "filter":  dict(filter_mix=1, filter_type=0, filter_freq=1200, filter_q=2),
    "chorus":  dict(chorus_mix=0.5, chorus_rate=0.8, chorus_depth=3),
    "flanger": dict(flange_mix=0.5, flange_rate=0.3, flange_depth=4, flange_fb=0.5),
    "phaser":  dict(phaser_mix=0.5, phaser_rate=0.4, phaser_depth=0.7, phaser_fb=0.4),
    "tremolo": dict(trem_depth=0.6, trem_rate=5),
    "reverse": dict(rvs_mix=0.5, rvs_time=400),
    "echo":    dict(wet=0.4, echo_time=350, feedback=0.4),
    "reverb":  dict(reverb_mix=0.6, reverb_size=0.6),
    "spatial": dict(space_shift=8, space_pan=0.3),
}
DADCRAFT = dict(reverb_mix=0.6, phaser_mix=0.4, chorus_mix=0.5, pitch_mix=0.25, wet=0.4)

x = (np.random.default_rng(1).standard_normal(int(SECONDS * SR)) * 0.1).astype(np.float32)
def cost(over):
    e = Engine(LIB, PATCH)
    for k, v in {**REST, **over}.items():
        e.send(k, v)
    e.run(e.silence(1.0))
    t0 = time.process_time(); e.run(x); t = time.process_time() - t0
    e.close(); return t
cases = {"at rest": {}, **EFFECTS, "DadCraft-like": DADCRAFT}
best = {k: float("inf") for k in cases}
for _ in range(3):
    for k, over in cases.items():
        best[k] = min(best[k], cost(over))
base = best["at rest"]
print(f"at rest: {base:.3f} s for {SECONDS:.0f} s of audio ({100 * base / SECONDS:.2f}% of a core)")
for k in list(EFFECTS) + ["DadCraft-like"]:
    print(f"  {k:12s} +{best[k] - base:6.3f} s  ({100 * (best[k] / base - 1):+5.1f}%)")
