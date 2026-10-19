#!/usr/bin/env python3
"""The dcs effect ports sound exactly as the Pd objects they replaced: the whole built-in patch, built both ways
(make_patch.py and make_patch.py --legacy-fx), hears the same input and the same setting changes (an effect coming
on, gliding, going off again), and the two outputs must match sample for sample. Also prints what each costs.

Usage: tools/.venv/bin/python tools/test_fx_port.py tools/.libpd/libpd.so [effect words...]"""
import os, subprocess, sys, tempfile, time
import numpy as np
sys.path.insert(0, os.path.dirname(__file__))
from pdtest import Engine, SR

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PATCH = os.path.join(ROOT, "app/src/main/assets/patches/test_scene.pd")
LIB = sys.argv[1] if len(sys.argv) > 1 else "libpd.so"
ONLY = sys.argv[2:]
fails = 0

def check(ok, what):
    global fails
    print(("ok:   " if ok else "FAIL: ") + what)
    fails += 0 if ok else 1

REST = dict(gain=1, dry=1, smooth=30, echo_on=1, wet=0, ring_on=1, ring_mix=0, ring_freq=80, pitch_on=1, pitch_mix=0,
            crush_on=1, crush_mix=0, dist_on=1, dist_mix=0, dist_quality=0, filter_on=1, filter_mix=0, chorus_on=1, chorus_mix=0,
            flange_on=1, flange_mix=0, phaser_on=1, phaser_mix=0, trem_on=1, trem_depth=0, trem_rate=5, rvs_on=1,
            rvs_mix=0, glitch_on=1, glitch_hold=0, reverb_on=1, reverb_mix=0, space_on=1, space_shift=0, space_pan=0,
            music_on=0, beat_level=0, gc_amount=0, gate_thresh=-80, comp_thresh=0,
            echo_scatter=0)   # (the scatter's [random] draws differ from run to run in one process)
# each case: (seconds, settings sent at that moment), in order; the run lasts until the last moment + 1 s
CASES = {
    # the switched effects: off long enough for the switch to stop them (3 x smooth + 300 ms), then on again
    "chorus": [(0.3, dict(chorus_mix=0.6, chorus_rate=0.8, chorus_depth=3)), (0.9, dict(chorus_rate=3, chorus_depth=6)),
               (1.3, dict(chorus_mix=0)), (2.2, dict(chorus_mix=1, chorus_depth=1)), (3.0, dict(chorus_on=0))],
    "flanger": [(0.3, dict(flange_mix=0.6, flange_rate=0.3, flange_depth=4, flange_fb=0.5)), (0.9, dict(flange_fb=-0.8, flange_rate=2)),
                (1.3, dict(flange_mix=0)), (2.2, dict(flange_mix=1, flange_depth=8)), (3.0, dict(flange_on=0))],
    "phaser": [(0.3, dict(phaser_mix=0.6, phaser_rate=0.4, phaser_depth=0.7, phaser_fb=0.4)), (0.9, dict(phaser_depth=1, phaser_rate=3)),
               (1.3, dict(phaser_mix=0)), (2.2, dict(phaser_mix=1, phaser_fb=-0.6)), (3.0, dict(phaser_on=0))],
    "reverse": [(0.3, dict(rvs_mix=0.7, rvs_time=400)), (0.9, dict(rvs_time=150)), (1.3, dict(rvs_mix=0)),
                (2.2, dict(rvs_mix=1, rvs_time=700)), (3.0, dict(rvs_on=0))],
    "pitch": [(0.3, dict(pitch_mix=0.7, pitch_semi=7, pitch_window=60)), (0.9, dict(pitch_semi=-12, pitch_window=90)),
              (1.3, dict(pitch_mix=0)), (2.2, dict(pitch_mix=1, pitch_semi=4)), (3.0, dict(pitch_on=0))],
    "freeze": [(0.3, dict(glitch_hold=1, glitch_len=120)), (0.9, dict(glitch_len=300)), (1.4, dict(glitch_hold=0.5)),
               (1.9, dict(glitch_hold=0)), (2.6, dict(glitch_hold=1, glitch_len=60, smooth=100)), (3.2, dict(glitch_on=0))],
    "spatial": [(0.3, dict(space_shift=8, space_pan=0.5)), (0.9, dict(space_shift=-20)), (1.4, dict(space_pan=-1, smooth=200)),
                (2.0, dict(space_shift=25, space_pan=1)), (2.6, dict(space_on=0)), (3.1, dict(space_on=1, space_shift=0))],
    "echo": [(0.3, dict(wet=0.5, echo_time=300, feedback=0.5)), (1.0, dict(echo_time=120)), (1.5, dict(feedback=0.8, dry=0.4)),
             (2.0, dict(wet=0, feedback=0)), (2.8, dict(wet=0.7, echo_time=500, feedback=0.3)), (3.4, dict(echo_on=0))],
    "distortion": [(0.3, dict(dist_mix=0.6, dist_drive=6)), (0.9, dict(dist_type=1)), (1.4, dict(dist_type=2, dist_drive=12)),
                   (2.0, dict(dist_mix=0, smooth=120)), (2.6, dict(dist_mix=1, dist_type=0, dist_drive=2)), (3.2, dict(dist_on=0))],
    "filter": [(0.3, dict(filter_mix=1, filter_freq=800, filter_q=2)), (0.8, dict(filter_freq=4000)), (1.2, dict(filter_type=1)),
               (1.6, dict(filter_type=2, filter_q=8, smooth=200)), (2.1, dict(filter_freq=300)), (2.3, dict(filter_freq=2500)),
               (2.6, dict(filter_mix=0)), (3.0, dict(filter_mix=0.5, filter_type=0, smooth=0)), (3.2, dict(filter_freq=900)),
               (3.5, dict(filter_on=0))],
    "crush": [(0.3, dict(crush_mix=0.7, crush_bits=6, crush_rate=6000)), (1.0, dict(crush_rate=15000, crush_bits=3)),
              (1.6, dict(crush_mix=0, smooth=150)), (2.4, dict(crush_mix=1, crush_bits=12, crush_rate=900)),
              (3.1, dict(crush_on=0))],
    "ring": [(0.3, dict(ring_mix=0.5, ring_freq=120)), (1.0, dict(ring_freq=400)), (1.5, dict(ring_mix=1, smooth=200)),
             (2.2, dict(ring_mix=0)), (2.8, dict(ring_mix=0.7, ring_on=0)), (3.3, dict(ring_on=1))],
    "tremolo": [(0.3, dict(trem_depth=0.6)), (1.0, dict(trem_rate=11)), (1.7, dict(trem_depth=0, smooth=90)),
                (2.4, dict(trem_depth=1, trem_rate=2)), (3.0, dict(trem_on=0))],
}

def signal():
    """A sweep, noise bursts, claps and silence: every effect has something to work on."""
    t = np.arange(int(5 * SR)) / SR
    rng = np.random.default_rng(3)
    x = 0.3 * np.sin(2 * np.pi * (110 * t + 60 * t * t))
    x += np.where((t % 0.7) < 0.15, rng.standard_normal(len(t)) * 0.2, 0)
    x[(t > 2.0) & (t < 2.3)] = 0
    return x.astype(np.float32)

def run(patch, events, x):
    e = Engine(LIB, patch)
    for k, v in REST.items():
        e.send(k, v)
    e.run(e.silence(0.5))
    outs, at, spent = [], 0, 0.0
    for when, settings in events + [(events[-1][0] + 1.0, {})]:
        upto = int(when * SR) // 64 * 64
        t0 = time.process_time(); outs.append(e.run(x[at:upto])); spent += time.process_time() - t0
        at = upto
        for k, v in settings.items():
            e.send(k, v)
    e.close()
    return np.concatenate(outs), spent

with tempfile.TemporaryDirectory() as d:
    legacy = os.path.join(os.path.dirname(PATCH), ".legacy_fx_test.pd")   # (next to the real one: same search paths)
    subprocess.run([sys.executable, os.path.join(ROOT, "tools/make_patch.py"), "--legacy-fx", legacy],
                   check=True, stdout=subprocess.DEVNULL)
    try:
        x = signal()
        for name, events in CASES.items():
            if ONLY and not any(w in name for w in ONLY):
                continue
            old, c_old = run(legacy, events, x)
            new, c_new = run(PATCH, events, x)
            peak = np.abs(old).max()
            diff = np.abs(old - new).max()
            # within -80 dB: most ports match to the bit; libpd is built with -ffast-math, so where Pd's own objects
            # round as the compiler chose (expr's maths and biquad~ in the phaser) a port drifts a few float steps
            # (worst 4.8e-5); a real mistake (timing, a formula) shows as 1e-3 or more
            check(peak > 0.01 and diff <= 1e-4 * max(peak, 1),
                  f"{name}: the dcs port matches the Pd objects (worst {diff:.1e}, peak {peak:.2f}; whole patch "
                  f"{100 * c_new / c_old:.0f}% of the time)")
    finally:
        os.remove(legacy)
print("ALL PASSED" if fails == 0 else f"{fails} FAILED")
