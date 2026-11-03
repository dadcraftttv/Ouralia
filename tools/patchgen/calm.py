# SPDX-License-Identifier: GPL-3.0-or-later
"""Calm builds, the quiet tune (Nov 3): the app's level (calm_tune 0..1) and phrase (calm_phrase, six chime steps)
become a two-bar chime phrase on the drum grid, only while the drums are sounding. No new voice: the notes go
through the motif path (a chime at a scale step, music.py), so key, chord register, chime voice, Mix level and
Music in Stereo places all apply. No [random]: the app picks the phrase."""
from .constants import *  # noqa: F401,F403
from .core import *  # noqa: F401,F403


def _ex(P, name, formula, x, y):
    return P.obj(name, 'expr ' + formula.replace('$', BS + '$').replace(',', BS + ',').replace(';', BS + ';'), x, y)


def add_calm_tune(P):
    x, y = 16200, 100
    P.obj('ct_r_lvl', 'r calm_tune', x, y); P.obj('ct_lvl', 'value ct_lvl', x, y + 15); P.c('ct_r_lvl', 'ct_lvl')
    P.obj('ct_tab', f'table ct_phrase {CALM_NOTES}', x + 100, y)
    P.obj('ct_r_ph', 'r calm_phrase', x + 100, y + 15); P.obj('ct_set', 'array set ct_phrase', x + 100, y + 30)
    P.c('ct_r_ph', 'ct_set')
    # The drums are sounding: the beat's own output above CALM_DRUM_DB within the last CALM_DRUM_HOLD_MS.
    P.obj('ct_env', 'env~ 1024 512', x + 200, y); P.c('beat_out', 'ct_env')
    P.obj('ct_hit', f'> {100 + CALM_DRUM_DB}', x + 200, y + 15); P.c('ct_env', 'ct_hit')
    P.obj('ct_hit1', 'sel 1', x + 200, y + 30); P.c('ct_hit', 'ct_hit1')
    P.obj('ct_hit_t', 't b b', x + 200, y + 45); P.c('ct_hit1', 'ct_hit_t')
    P.msg('ct_one', '1', x + 200, y + 60); P.c('ct_hit_t', 'ct_one', 1)
    P.obj('ct_drums', 'value ct_drums', x + 200, y + 75); P.c('ct_one', 'ct_drums')
    P.obj('ct_hold', f'delay {CALM_DRUM_HOLD_MS}', x + 260, y + 60); P.c('ct_hit_t', 'ct_hold')
    P.msg('ct_zero', '0', x + 260, y + 75); P.c('ct_hold', 'ct_zero')
    P.obj('ct_drums0', 'value ct_drums', x + 260, y + 90); P.c('ct_zero', 'ct_drums0')
    # The grid: tick16 is the 16th in the bar (0..15); the phrase is two bars long.
    P.obj('ct_tick', 'r tick16', x, y + 120); P.obj('ct_tt', 't f f', x, y + 135); P.c('ct_tick', 'ct_tt')
    P.c('ct_tt', _ex(P, 'ct_bar', 'if($f1==0,1-ct_bar,ct_bar)', x + 100, y + 150), 1)
    P.obj('ct_bar_v', 'value ct_bar', x + 100, y + 165); P.c('ct_bar', 'ct_bar_v')
    # Notes at 16ths 0, 6, 12 of each bar (k = 0..2), note i = bar*3 + k in time order. They join downbeats first:
    # rank 2k + bar, note joins when the level passes rank / CALM_NOTES. Out: velocity (right), then step or -1.
    play = (f'ct_lvl>0&&ct_drums>0&&fmod($f1,6)==0&&$f1<=12&&ct_lvl>(2*int($f1/6)+ct_bar)/{CALM_NOTES}')
    _ex(P, 'ct_play', f'if({play},ct_phrase[int(ct_bar*3+int($f1/6))],-1);'
        f'{CALM_VEL_MIN}+{CALM_VEL_MAX - CALM_VEL_MIN}*ct_lvl', x, y + 180)
    P.c('ct_tt', 'ct_play')
    P.obj('ct_pk', 'pack f f', x, y + 210); P.c('ct_play', 'ct_pk', 1, 1)
    P.obj('ct_ok', 'moses 0', x, y + 195); P.c('ct_play', 'ct_ok'); P.c('ct_ok', 'ct_pk', 1, 0)
    P.obj('ct_send', 's motif', x, y + 225); P.c('ct_pk', 'ct_send')
    P.obj('ct_note_s', 's calm_note', x + 100, y + 225); P.c('ct_ok', 'ct_note_s', 1)   # (for the tests: step...
    P.obj('ct_vel_s', 's calm_vel', x + 200, y + 225); P.c('ct_play', 'ct_vel_s', 1)    # ...and every tick's velocity)
