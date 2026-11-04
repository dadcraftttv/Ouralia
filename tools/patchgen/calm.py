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


def add_calm_bass(P, ex, degree_to_hz, mixb, pad_lvl, syn, x, y):
    """The quiet tune's bass (Nov 4): once the tune passes CALM_BASS_FROM, a plucked low note on the chord's root at
    each bar's downbeat and its fifth (4 steps of 7, 3 of a pentatonic) on the second bar's 16th CALM_BASS_TICK,
    joining downbeat first. Its own voice (the held bass sits out in a quiet room while Pad Only With a Steady Sound
    is on), shaped like the held bass
    (2nd and 3rd harmonics for earbuds, 65-131 Hz). While it plays the held bass makes way for it. Names start
    ba_cb_ so it sits in the music's DSP group. Bass Voice Glass Bowl (Nov 4): each note a bowl instead (ba_cbw_, in the
    bbowl group), easing in and ringing into the next; syn is the synth's share (1 - the voice). Returns its signal."""
    P.obj('ba_cb_root', 'value ba_cb_root', x, y); P.c('mu_pad_root_v', 'ba_cb_root', 1, 0)   # the chord's root
    # The chord changes on the same tick16 0: read the grid 5 ms later, so the downbeat has the new root.
    P.obj('ba_cb_tick', 'r tick16', x, y + 20); P.obj('ba_cb_late', 'pipe 5', x, y + 35); P.c('ba_cb_tick', 'ba_cb_late')
    lvl = f'max(0,min(1,(ct_lvl-{CALM_BASS_FROM})/{1 - CALM_BASS_FROM}))'
    # rank: bar 0's downbeat 0, bar 1's downbeat 1, bar 1's pickup 2; a note joins when the level passes rank / 3
    rank = f'($f1==0)*ct_bar+($f1=={CALM_BASS_TICK})*2'
    play = f'ct_drums>0&&{lvl}>0&&($f1==0||($f1=={CALM_BASS_TICK}&&ct_bar==1))&&{lvl}>({rank})/3'
    _ex(P, 'ba_cb_play', f'if({play},ba_cb_root+int(scale_n*4/7+0.5)*($f1=={CALM_BASS_TICK}),-1);'
        f'{CALM_BASS_VEL_MIN}+{CALM_BASS_VEL_MAX - CALM_BASS_VEL_MIN}*{lvl}', x, y + 50)
    P.c('ba_cb_late', 'ba_cb_play')
    P.obj('ba_cb_vel', 'f', x + 160, y + 80); P.c('ba_cb_play', 'ba_cb_vel', 1, 1)
    P.obj('ba_cb_ok', 'moses 0', x, y + 65); P.c('ba_cb_play', 'ba_cb_ok')
    P.obj('ba_cb_t', 't b f', x, y + 80); P.c('ba_cb_ok', 'ba_cb_t', 1)
    P.obj('ba_cb_deg_s', 's calm_bass', x + 240, y + 80); P.c('ba_cb_ok', 'ba_cb_deg_s', 1)   # (for the tests)
    deg, hz = degree_to_hz('ba_cb_d', x, y + 100); P.c('ba_cb_t', deg, 1)
    ex('ba_cb_fold', 'if($f1/2>131,$f1/4,$f1/2)', x, y + 260); P.c(hz, 'ba_cb_fold')
    P.obj('ba_cb_f2', '* 2', x + 60, y + 280); P.obj('ba_cb_f3', '* 3', x + 120, y + 280)
    P.c('ba_cb_fold', 'ba_cb_f2'); P.c('ba_cb_fold', 'ba_cb_f3')
    for k, src in ((1, 'ba_cb_fold'), (2, 'ba_cb_f2'), (3, 'ba_cb_f3')):
        P.obj(f'ba_cb_o{k}', 'osc~', x + 60 * (k - 1), y + 300); P.c(src, f'ba_cb_o{k}')
    P.obj('ba_cb_g2', '*~ 0.35', x + 60, y + 315); P.obj('ba_cb_g3', '*~ 0.15', x + 120, y + 315)
    P.c('ba_cb_o2', 'ba_cb_g2'); P.c('ba_cb_o3', 'ba_cb_g3')
    P.sig2('ba_cb_s12', '+~', 'ba_cb_o1', 'ba_cb_g2', x, y + 330)
    P.sig2('ba_cb_s', '+~', 'ba_cb_s12', 'ba_cb_g3', x, y + 345)
    # the pluck: after the pitch is set, 10 ms up to the velocity, then down over CALM_BASS_DECAY_MS; squared
    P.c('ba_cb_t', 'ba_cb_vel')
    P.msg('ba_cb_pl', f'{BS}$1 10 {BS}, 0 {CALM_BASS_DECAY_MS} 10', x + 160, y + 100); P.c('ba_cb_vel', 'ba_cb_pl')
    P.obj('ba_cb_env', 'vline~', x + 160, y + 120); P.c('ba_cb_pl', 'ba_cb_env')
    P.sig2('ba_cb_env2', '*~', 'ba_cb_env', 'ba_cb_env', x + 160, y + 135)
    P.sig2('ba_cb_e', '*~', 'ba_cb_s', 'ba_cb_env2', x, y + 360)
    P.sig2('ba_cb_es', '*~', 'ba_cb_e', syn, x, y + 365)
    # the bowl: BASS_BOWL_UP x the note, its twin and a whisper of the octave; eases in, rings into the next note
    P.obj('ba_cbw_f1', f'* {BASS_BOWL_UP}', x + 200, y + 280); P.c('ba_cb_fold', 'ba_cbw_f1')
    P.obj('ba_cbw_f2', f'+ {BASS_BOWL_BEAT_HZ}', x + 260, y + 280); P.c('ba_cbw_f1', 'ba_cbw_f2')
    P.obj('ba_cbw_f3', '* 2', x + 320, y + 280); P.c('ba_cbw_f1', 'ba_cbw_f3')
    for o, src in (('o1', 'ba_cbw_f1'), ('o2', 'ba_cbw_f2'), ('o3', 'ba_cbw_f3')):
        P.obj(f'ba_cbw_{o}', 'osc~', x + 200, y + 300); P.c(src, f'ba_cbw_{o}')
    P.obj('ba_cbw_g2', f'*~ {PAD_BOWL_TWIN_LEVEL}', x + 260, y + 315); P.c('ba_cbw_o2', 'ba_cbw_g2')
    P.obj('ba_cbw_g3', f'*~ {PAD_BOWL_OCTAVE}', x + 320, y + 315); P.c('ba_cbw_o3', 'ba_cbw_g3')
    P.sig2('ba_cbw_s12', '+~', 'ba_cbw_o1', 'ba_cbw_g2', x + 200, y + 330)
    P.sig2('ba_cbw_s', '+~', 'ba_cbw_s12', 'ba_cbw_g3', x + 200, y + 345)
    P.msg('ba_cbw_pl', f'{BS}$1 {CALM_BOWL_RISE_MS} {BS}, 0 {CALM_BOWL_RING_MS} {CALM_BOWL_RISE_MS}', x + 260, y + 100)
    P.c('ba_cb_vel', 'ba_cbw_pl')
    P.obj('ba_cbw_env', 'vline~', x + 260, y + 120); P.c('ba_cbw_pl', 'ba_cbw_env')
    P.sig2('ba_cbw_e', '*~', 'ba_cbw_s', 'ba_cbw_env', x + 200, y + 360)
    P.obj('ba_cbw_lv', f'*~ {CALM_BOWL_LEVEL}', x + 200, y + 362); P.c('ba_cbw_e', 'ba_cbw_lv')
    P.obj('ba_cbw_v', 'line~', x + 260, y + 362); P.c('ba_bw_pk', 'ba_cbw_v')      # (the bowl's own copy of the voice)
    P.sig2('ba_cbw_out', '*~', 'ba_cbw_lv', 'ba_cbw_v', x + 200, y + 364)
    P.sig2('ba_cb_mix', '+~', 'ba_cb_es', 'ba_cbw_out', x, y + 368)
    P.obj('ba_cb_lp', 'lop~ 700', x, y + 375); P.c('ba_cb_mix', 'ba_cb_lp')
    P.obj('ba_cb_g', f'*~ {CALM_BASS_GAIN}', x, y + 390); P.c('ba_cb_lp', 'ba_cb_g')
    P.sig2('ba_cb_m', '*~', 'ba_cb_g', mixb, x, y + 405)                    # the Mix's bass trim...
    P.sig2('ba_cb_out', '*~', 'ba_cb_m', pad_lvl, x, y + 420)              # ...and the Pad level, like the held bass
    # The held bass makes way: 1 - level (while the drums sound), ten times a second, smoothed.
    P.obj('ba_cb_init', 'loadbang', x + 300, y); P.obj('ba_cb_mt', 'metro 100', x + 300, y + 15)
    P.c('ba_cb_init', 'ba_cb_mt')
    _ex(P, 'ba_cb_way', f'1-(ct_drums>0)*{lvl}', x + 300, y + 30); P.c('ba_cb_mt', 'ba_cb_way')
    P.obj('ba_cb_way_sig', 'sig~ 1', x + 300, y + 45); P.c('ba_cb_way', 'ba_cb_way_sig')
    P.obj('ba_cb_way_s', 's calm_way', x + 380, y + 45); P.c('ba_cb_way', 'ba_cb_way_s')   # (for the tests)
    P.obj('ba_cb_way_lp', 'lop~ 1', x + 300, y + 60); P.c('ba_cb_way_sig', 'ba_cb_way_lp')
    return 'ba_cb_out', 'ba_cb_way_lp'
