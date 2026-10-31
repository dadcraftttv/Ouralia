#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Compare kit-free audio to Kit Classic at a reference commit, in isolated seeded processes.

Run with tools/.venv/bin/python tools/check_classic_parity.py [0515f8d].
Only synthetic input is used. Temporary patches/audio are removed when the check ends.
"""
import itertools
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
from concurrent.futures import ThreadPoolExecutor

import numpy as np

ROOT = Path(__file__).resolve().parents[1]


def seeded_patch(source, target):
    # Stable node names survive graph deletion; Pd's automatic seeds depend on object creation order.
    sys.path.insert(0, str(source))
    from patchgen.build import build
    import zlib
    p = build()
    for name, index in list(p.index.items()):
        if p.lines[index].split()[4] in ('random', 'noise~'):
            seed = zlib.crc32(name.encode()) % 1000000 + 1
            p.msg('seed_' + name, 'seed ' + str(seed), 0, 0)
            p.c('loadbang', 'seed_' + name)
            p.c('seed_' + name, name)
    Path(target).write_text(p.text())


def render(patch, part, src, switch, output):
    from pdtest import Engine, SR
    src = int(src)
    e = Engine(str(ROOT / 'tools/.libpd/libpd.so'), patch)
    base = dict(gain=1, dry=0, wet=0, comp_thresh=0, pocket_hush=0, pocket_guard=0,
                music_on=1, music_pad=0, music_arp=0, music_res=0, music_harm=0, music_answer=0,
                beat_level=0, beat_follow=1, beat_when=1, tempo_mode=1, tempo_bpm=100,
                music_pad_reg=2, melody_src=src, pad_src=src, bass_src=src, rhythm_src=src)
    if part == 'melody':
        base['music_arp'] = 0.4
    if part in ('pad', 'bass'):
        base.update(music_pad=0.5, music_pad_reg=1 if part == 'pad' else 0)
    if part == 'drums':
        base['beat_level'] = 0.6
    if part == 'composer':
        base.update(music_arp=0.4, music_pad=0.5, beat_level=0.6, music_var=1, mel_dev=1,
                    new_pieces=1, pat_gen=1, mel_rhythm=1, mel_phrase=1, beat_break=1,
                    band_plays=1, chord_color=1)
    if part == 'vocoder':
        base.update(music_arp=0.4, music_pad=0.5, dry=1, voc_mix=1, voc_carrier=src,
                    melody_src=0, pad_src=0, bass_src=0, rhythm_src=0,
                    voc_wave=1)  # Chords, own pitch, whisper and melody; Chip carrier retained.
    if switch != 'off':
        base[switch] = 20.5 if switch == 'note_big' else 1
    for key, value in base.items():
        e.send(key, value)
    e.send('comp_seed', 12345)
    e.run(e.silence(0.5))
    rng = np.random.default_rng(7)
    x = rng.normal(0, 0.0004, SR * 16).astype('float32')
    for at in (1, 1.7, 3.1, 12.5):
        t = np.arange(4800) / SR
        x[int(at * SR):int(at * SR) + 4800] += (rng.normal(0, 0.25, 4800) * np.exp(-t * 50)).astype('float32')
    t = np.arange(SR * 5) / SR
    x[5 * SR:10 * SR] += (0.12 * (np.sin(2 * np.pi * 220 * t) + 0.3 * np.sin(2 * np.pi * 440 * t))).astype('float32')
    np.save(output, e.run(x))
    # Keep the original's diagnostic messages visible to the comparison too.
    errors = [line for line in e.log if 'error' in line.lower() or "couldn't" in line.lower()]
    Path(output + '.json').write_text(json.dumps(errors))
    e.close()


def run(args):
    result = subprocess.run([sys.executable, str(Path(__file__).resolve()), *map(str, args)],
                            cwd=ROOT, capture_output=True, text=True)
    if result.returncode:
        raise RuntimeError(result.stderr[-2000:])


def compare(reference):
    cases = list(itertools.product(('melody', 'pad', 'bass', 'drums'), range(3),
                                   ('off', 'chime_answer', 'pad_room', 'note_big')))
    cases += [('composer', i, 'off') for i in range(3)]
    cases += [('vocoder', i, 'off') for i in range(4)]
    with tempfile.TemporaryDirectory(prefix='ouralia-classic-') as folder:
        scratch = Path(folder)
        package = scratch / 'reference/patchgen'
        package.mkdir(parents=True)
        names = subprocess.check_output(['git', 'ls-tree', '-r', '--name-only', reference,
                                         'tools/patchgen'], cwd=ROOT, text=True).splitlines()
        for name in names:
            if name.endswith('.py'):
                (package / Path(name).name).write_bytes(subprocess.check_output(
                    ['git', 'show', reference + ':' + name], cwd=ROOT))
        run(['--seed', package.parent, scratch / 'before.pd'])
        run(['--seed', ROOT / 'tools', scratch / 'after.pd'])
        def pair(case):
            part, src, switch = case
            files = []
            for stage in ('before', 'after'):
                output = str(scratch / (stage + '_' + part + str(src) + '_' + switch + '.npy'))
                run(['--render', scratch / (stage + '.pd'), part, src, switch, output])
                files.append(output)
            a, b = (np.load(f) for f in files)
            errors = [json.loads(Path(f + '.json').read_text()) for f in files]
            same = np.array_equal(a, b) and errors[0] == errors[1]
            audible = bool(np.max(np.abs(a)) > 1e-8)
            expected_silent = part == 'bass' and switch == 'pad_room'
            return case, same and (audible or expected_silent), float(np.max(np.abs(a - b))), len(errors[0])
        with ThreadPoolExecutor(min(4, os.cpu_count() or 1)) as pool:
            results = list(pool.map(pair, cases))
        failed = [r for r in results if not r[1]]
        for case, _, peak, _ in failed:
            print('FAIL:', case, 'peak difference', peak)
        print('%d/%d sample-identical, max difference %.9g; diagnostics identical (%d baseline messages)'
              % (len(cases) - len(failed), len(cases), max(r[2] for r in results), sum(r[3] for r in results)))
        return bool(failed)


if __name__ == '__main__':
    if sys.argv[1:2] == ['--seed']:
        seeded_patch(*sys.argv[2:])
    elif sys.argv[1:2] == ['--render']:
        render(*sys.argv[2:])
    else:
        sys.exit(compare(sys.argv[1] if len(sys.argv) > 1 else '0515f8d'))
