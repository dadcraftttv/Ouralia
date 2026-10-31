# SPDX-License-Identifier: GPL-3.0-or-later
"""The Rhythm: the tempo clock, the beat and its patterns, the found-sound kit, glitch loops."""
from .constants import *  # noqa: F401,F403 (the tuning numbers)
from .core import *  # noqa: F401,F403


def add_tempo_clock(P):
    """The tempo clock (roadmap 16 part 1): a steady beat that follows the walking like a band follows its drummer.
    Each step nudges its timing (CLOCK_PHASE_GAIN of the error) and tempo (CLOCK_FREQ_GAIN, plus a little of the step
    interval itself), so one odd step barely moves it and a change of pace is followed within a few steps. One step =
    one beat, folded into CLOCK_MIN_MS..CLOCK_MAX_MS (80-160 BPM; a slow amble plays at double time). It sends
    [pulse <beat s>( while you walk (until 1.25 step intervals after the last step, so stopping still stops the
    drums at once) and [tempo <bpm>( on each beat; when you stop it keeps the tempo. beat_follow 0 sends the steps
    themselves as pulses instead (the old behaviour)."""
    x, y = 9000, 20
    def ex(name, text, xx, yy):
        return P.obj(name, "expr " + text.replace(",", BS + ",").replace("$", BS + "$"), xx, yy)
    P.obj("tc_lb", "loadbang", x, y)
    P.obj("tc_timer", "timer", x, y + 60)                                 # ms since load (logical time)
    P.c("tc_lb", "tc_timer")
    P.obj("tc_r_step", "r step", x + 200, y)
    P.obj("tc_step_t", "t b f", x + 200, y + 30)                          # the interval first, then the time
    P.c("tc_r_step", "tc_step_t"); P.c("tc_step_t", "tc_timer", 0, 1)
    # state: P beat ms, N next beat (ms since load), B last beat, L last step, I last step interval ms
    st = {k: P.obj(f"tc_v{k}", f"value tc_{k}", x + 500 + 70 * i, y + 20) for i, k in enumerate("PNBLI")}
    for k in "PNBLI":
        P.obj(f"tc_w{k}", f"value tc_{k}", x + 500 + 70 * "PNBLI".index(k), y + 300)
    P.msg("tc_init_msg", f"{CLOCK_DEFAULT_MS}", x + 60, y + 20)
    P.c("tc_lb", "tc_init_msg"); P.c("tc_init_msg", "tc_wP")
    P.obj("tc_start", "del 50", x + 60, y + 40)                         # ticking from the start (a beat, then every P)
    P.c("tc_lb", "tc_start")
    # on a step: now, its interval -> new P, new N (fresh walk: a beat right now)
    P.obj("tc_now_t", "t f f b b b b", x + 200, y + 90)                   # read P N B L, store now, then calculate
    P.c("tc_timer", "tc_now_t")
    for i, k in enumerate("PNBL"):
        P.c("tc_now_t", f"tc_v{k}", 5 - i, 0)
    iv = "max($f2*1000,100)"                                              # (never 0: a beat 0 ms long would loop forever)
    fold = f"({iv})*pow(2,floor(ln({CLOCK_MAX_MS}/({iv}))/ln(2)))"
    e = "if(abs($f1-$f5)<abs($f1-$f4),$f1-$f5,$f1-$f4)"                     # error to the nearest beat
    fresh = f"($f6<=0||$f1-$f6>{CLOCK_FRESH_MS}+$f3)"
    newP = (f"if({fresh},{fold},min(max((1-{CLOCK_PACE_GAIN})*($f3+{CLOCK_FREQ_GAIN}*{e})+{CLOCK_PACE_GAIN}*{fold},"
            f"{CLOCK_MIN_MS}),{CLOCK_MAX_MS}))")
    newN = f"if({fresh},$f1,max($f4+{CLOCK_PHASE_GAIN}*{e},$f1+1))"
    # Tempo "Set" (tempo_mode 1): your BPM (tc_Pu ms), each step only nudging it toward the step rate in the octave nearest
    # it; the beat is not pulled into phase with the steps (two tempos would make it wobble)
    near = f"({iv})*pow(2,rint(ln(tc_Pu/({iv}))/ln(2)))"
    newP = f"if(tc_mode==1,$f3+{TEMPO_NUDGE}*((1-{TEMPO_WALK_PULL})*tc_Pu+{TEMPO_WALK_PULL}*{near}-$f3),{newP})"
    newN = f"if(tc_mode==1,max($f4,$f1+1),{newN})"
    ex("tc_calc", f"{newP} ; {newN} ; {iv} ; $f1".replace(" ; ", " " + BS + "; "), x + 200, y + 150)
    # inlets: $f1 now (hot, last), $f2 interval s, $f3 P, $f4 N, $f5 B, $f6 L
    P.c("tc_step_t", "tc_calc", 1, 1)
    P.c("tc_vP", "tc_calc", 0, 2); P.c("tc_vN", "tc_calc", 0, 3); P.c("tc_vB", "tc_calc", 0, 4); P.c("tc_vL", "tc_calc", 0, 5)
    P.c("tc_now_t", "tc_calc", 0, 0)
    # outputs right to left: L (now), I (interval ms), N, P -> written; then the next beat is (re)scheduled
    P.c("tc_calc", "tc_wL", 3, 0); P.c("tc_calc", "tc_wI", 2, 0)
    P.obj("tc_n_t", "t f b f", x + 280, y + 190)                         # store N, read now, then the wait
    P.c("tc_calc", "tc_n_t", 1, 0); P.c("tc_n_t", "tc_wN", 2, 0)
    P.c("tc_calc", "tc_wP", 0, 0)
    if os.environ.get("CLOCK_DEBUG"):
        P.obj("tc_dbg_pk", "pack f f f f", x + 700, y + 150); P.obj("tc_dbg_s", "s tc_dbg", x + 700, y + 170)
        P.c("tc_calc", "tc_dbg_pk", 0, 0); P.c("tc_calc", "tc_dbg_pk", 1, 1); P.c("tc_calc", "tc_dbg_pk", 2, 2); P.c("tc_calc", "tc_dbg_pk", 3, 3)
        P.c("tc_dbg_pk", "tc_dbg_s")
    P.obj("tc_now2", "f", x + 360, y + 210)                               # now, for the delay to N
    P.c("tc_now_t", "tc_now2", 1, 1)
    ex("tc_wait", "max($f1-$f2,0)", x + 280, y + 230)
    P.c("tc_n_t", "tc_now2", 1, 0); P.c("tc_now2", "tc_wait", 0, 1); P.c("tc_n_t", "tc_wait", 0, 0)
    P.obj("tc_del", "del", x + 280, y + 260)
    P.c("tc_wait", "tc_del")
    # a beat: B = N, N = N + P, the pulse (while walking) and the tempo, then the next beat
    P.obj("tc_beat", "t b b b b", x, y + 330)
    P.c("tc_del", "tc_beat"); P.c("tc_start", "tc_beat")
    P.obj("tc_bN", "value tc_N", x + 80, y + 350); P.obj("tc_bP", "value tc_P", x + 150, y + 350)
    P.obj("tc_bL", "value tc_L", x + 220, y + 350); P.obj("tc_bI", "value tc_I", x + 290, y + 350)
    P.obj("tc_timer2", "timer", x + 360, y + 370)                         # (its own timer: the step path has the other)
    P.c("tc_lb", "tc_timer2")
    P.c("tc_beat", "tc_timer2", 3, 1)
    P.c("tc_beat", "tc_bI", 2, 0); P.c("tc_beat", "tc_bL", 2, 0); P.c("tc_beat", "tc_bP", 1, 0); P.c("tc_beat", "tc_bN", 0, 0)
    walk = "(($f3-$f4<1.25*$f5)*($f5>0))"
    pe = f"if(tc_mode==2,tc_Pu,if(tc_mode==1&&{walk}==0,$f2+(tc_Pu-$f2)*{TEMPO_RETURN},$f2))"   # (Set, standing: back toward your BPM; locked: at once)
    ex("tc_next", f"$f1 ; $f1+{pe} ; max({pe},{CLOCK_MIN_MS}) ; {walk} ; 60000/max({pe},1) ; {pe}".replace(" ; ", " " + BS + "; "), x, y + 380)
    P.obj("tc_wP2", "value tc_P", x + 400, y + 400); P.c("tc_next", "tc_wP2", 5, 0)
    P.obj("tc_r_tm", "r tempo_mode", x + 900, y); P.obj("tc_v_tm", "value tc_mode", x + 900, y + 20); P.c("tc_r_tm", "tc_v_tm")
    P.obj("tc_r_bpm", "r tempo_bpm", x + 980, y); ex("tc_bpm_ms", "60000/max($f1,30)", x + 980, y + 20)
    P.obj("tc_v_pu", "value tc_Pu", x + 980, y + 40); P.c("tc_r_bpm", "tc_bpm_ms"); P.c("tc_bpm_ms", "tc_v_pu")
    # inlets: $f1 N (hot, last), $f2 P, $f3 now, $f4 L, $f5 I
    P.c("tc_timer2", "tc_next", 0, 2)                                      # (the time of the beat)
    P.c("tc_bL", "tc_next", 0, 3); P.c("tc_bI", "tc_next", 0, 4); P.c("tc_bP", "tc_next", 0, 1); P.c("tc_bN", "tc_next")
    # out: 4 bpm, 3 walking?, 2 P (the delay to the next beat), 1 new N, 0 B
    P.obj("tc_tempo_s", "s tempo", x + 300, y + 420); P.c("tc_next", "tc_tempo_s", 4, 0)
    P.obj("tc_walk", "f", x + 220, y + 420); P.c("tc_next", "tc_walk", 3, 1)
    P.c("tc_next", "tc_wN", 1, 0); P.c("tc_next", "tc_wB", 0, 0)
    P.obj("tc_p_t", "t f b", x + 150, y + 420)
    P.c("tc_next", "tc_p_t", 2, 0)
    P.c("tc_p_t", "tc_walk", 1, 0)                                         # walking? -> the pulse gate
    P.c("tc_p_t", "tc_del", 0, 0)                                          # next beat in P ms
    P.obj("tc_walk_s", "s clock_walking", x + 300, y + 450); P.c("tc_walk", "tc_walk_s")
    P.obj("tc_walk_sel", "sel 1", x + 220, y + 450)
    P.c("tc_walk", "tc_walk_sel")
    P.obj("tc_bP2", "value tc_P", x + 220, y + 480); P.obj("tc_sec", "/ 1000", x + 220, y + 510)
    P.c("tc_walk_sel", "tc_bP2"); P.c("tc_bP2", "tc_sec")
    # 16th-note ticks: each beat restarts a metro at a quarter of the beat; tick16 = position in the bar, 0-15
    P.obj("tc_q_t", "t b b b", x + 700, y + 330)                         # read, reset the count, start the metro
    P.c("tc_beat", "tc_q_t", 0, 0)                                          # (after the beat's own work)
    P.obj("tc_q_P", "value tc_P", x + 760, y + 350); P.obj("tc_q_div", "/ 4", x + 760, y + 370)
    P.obj("tc_q_metro", "metro 150", x + 700, y + 400)
    P.c("tc_q_t", "tc_q_P", 2, 0); P.c("tc_q_P", "tc_q_div"); P.c("tc_q_div", "tc_q_metro", 0, 1)
    P.obj("tc_beat_n", "f", x + 860, y + 330); P.obj("tc_beat_add", "+ 1", x + 900, y + 330)
    P.obj("tc_beat_mod", "mod 4", x + 940, y + 330)
    P.c("tc_q_t", "tc_beat_n", 2, 0); P.c("tc_beat_n", "tc_beat_add"); P.c("tc_beat_add", "tc_beat_mod")
    P.c("tc_beat_mod", "tc_beat_n", 0, 1)
    P.obj("tc_sub", "f", x + 700, y + 430); P.obj("tc_sub_add", "+ 1", x + 760, y + 430)
    P.msg("tc_sub0", "0", x + 760, y + 400)
    P.c("tc_q_t", "tc_sub0", 1, 0); P.c("tc_sub0", "tc_sub", 0, 1); P.c("tc_q_t", "tc_q_metro", 0, 0)
    P.c("tc_q_metro", "tc_sub"); P.c("tc_sub", "tc_sub_add"); P.c("tc_sub_add", "tc_sub", 0, 1)
    P.obj("tc_sub_ok", "moses 4", x + 700, y + 460)                     # a slowing walk: no 5th tick before the beat
    P.c("tc_sub", "tc_sub_ok")
    P.obj("tc_b4", "value tc_beatpos", x + 860, y + 360); P.c("tc_beat_mod", "tc_b4")   # (already the next beat)
    P.obj("tc_pos_t", "t f b", x + 700, y + 490)
    P.obj("tc_b4r", "value tc_beatpos", x + 780, y + 500)
    ex("tc_pos", "($f2+3)%4*4+$f1", x + 700, y + 520)                     # (the beat counter already moved on)
    P.c("tc_sub_ok", "tc_pos_t", 0, 0); P.c("tc_pos_t", "tc_b4r", 1, 0); P.c("tc_b4r", "tc_pos", 0, 1)
    P.c("tc_pos_t", "tc_pos", 0, 0)
    P.obj("tc_tick_s", "s tick16", x + 700, y + 550); P.c("tc_pos", "tc_tick_s")
    # the router: beat_follow 1 = the clock's beats, 0 = the steps themselves
    P.obj("tc_r_mode", "r beat_follow", x + 500, y + 450)
    P.obj("tc_clock_on", "spigot 1", x + 220, y + 540); P.obj("tc_steps_on", "spigot 0", x + 600, y + 540)
    ex("tc_inv", "$f1==0", x + 600, y + 480)
    P.c("tc_r_mode", "tc_clock_on", 0, 1); P.c("tc_r_mode", "tc_inv"); P.c("tc_inv", "tc_steps_on", 0, 1)
    P.c("tc_sec", "tc_clock_on")
    P.obj("tc_raw", "r step", x + 600, y + 510); P.c("tc_raw", "tc_steps_on")
    P.obj("tc_pulse_s", "s pulse", x + 400, y + 570)
    P.c("tc_clock_on", "tc_pulse_s"); P.c("tc_steps_on", "tc_pulse_s")

def add_found_kit(P, x):
    """C2, the hit catcher: on a sudden sound (the glitch cloud's detector) a FS_SLICE_S slice of the hushed mic,
    from FS_PREROLL_MS before it was noticed, is recorded into a staging table while its lows, highs and level are
    measured; then it is filed as kick (mostly low), hat (mostly high and little low) or snare, copied into that role's
    next slot (FS_SLOTS per role, round robin) with a gain that levels it. Memory only. C3, the found voices: one
    voice per role plays a random filled slot of its role at its rate through its filter, when the pattern engine
    asks (rh_* below decides synth or found). Reports catches on fs_caught (0 kick, 1 snare, 2 hat)."""
    y = 1100
    def ex(name, text, xx, yy):
        return P.obj(name, "expr " + text.replace(",", BS + ",").replace("$", BS + "$"), xx, yy)
    n = int(FS_SLICE_S * 48000) + 2000                                  # samples (room for 48 kHz)
    P.obj("fs_stage_t", f"table fs_stage {n}", x, y)
    for k in range(len(FS_ROLE_SPEC)):                                     # fs_<role><slot>: fs_00 .. fs_22
        for j in range(FS_SLOTS):
            P.obj(f"fs_tab_{k}{j}", f"table fs_{k}{j} {n}", x + 100 + 60 * j, y + 20 * k)
    P.obj("fs_gains_t", f"table fs_gains {3 * FS_SLOTS}", x + 300, y)
    P.obj("fs_filled_t", "table fs_filled 3", x + 300, y + 20)
    # ---- recording: the mic from FS_PREROLL_MS ago into the staging table ----
    P.sig2("fs_src", "*~", "mic_gated", "hush_ln", x, y + 80)
    P.obj("fs_wr", "delwrite~ fs_buf 100", x, y + 100); P.c("fs_src", "fs_wr")
    P.obj("fs_rd", f"delread~ fs_buf {FS_PREROLL_MS}", x, y + 120)
    P.obj("fs_rec", "tabwrite~ fs_stage", x, y + 140); P.c("fs_rd", "fs_rec")
    # measuring as it records: overall, lows and highs, each's loudest moment (dB, 100 = full scale)
    P.obj("fs_lo", "lop~ 150", x + 100, y + 120); P.obj("fs_hi", "hip~ 4000", x + 200, y + 120)
    P.c("fs_rd", "fs_lo"); P.c("fs_rd", "fs_hi")
    for k, (nm, src) in enumerate((("all", "fs_rd"), ("lo", "fs_lo"), ("hi", "fs_hi"))):
        P.obj(f"fs_env_{nm}", "env~ 512 128", x + 100 * k, y + 160); P.c(src, f"fs_env_{nm}")
        P.obj(f"fs_max_{nm}", "f", x + 100 * k, y + 200)
        ex(f"fs_mx_{nm}", "max($f1,$f2)", x + 100 * k, y + 180)
        P.c(f"fs_env_{nm}", f"fs_mx_{nm}")
        P.c(f"fs_mx_{nm}", f"fs_max_{nm}", 0, 1); P.c(f"fs_mx_{nm}", f"fs_mx_{nm}", 0, 1)   # (the running maximum)
    P.obj("fs_rmsdb", "f", x + 300, y + 200); P.c("fs_mx_all", "fs_rmsdb", 0, 1)          # (for the gain, read later)
    # ---- a sudden sound: if not already recording and the found kit is in use, record ----
    P.obj("fs_on", "spigot 1", x + 400, y + 80)                          # (closed while recording)
    P.obj("fs_use", "spigot 0", x + 400, y + 60)                         # (only when found sounds are used)
    # (needed by the found beat, or by the found melody: fs_need, which also switches the recording's audio)
    P.obj("fs_go_need", "t b b b b b", x + 500, y - 20)
    for k, nm in enumerate(("rhythm_src", "beat_level", "music_on", "melody_src", "res_src")):
        P.obj(f"fs_nd_r{k}", f"r {nm}", x + 500 + 70 * k, y - 80); P.obj(f"fs_nd_t{k}", "t b f", x + 500 + 70 * k, y - 65)
        P.obj(f"fs_nd_w{k}", f"value fsnd{k}", x + 500 + 70 * k, y - 50); P.obj(f"fs_nd_v{k}", f"value fsnd{k}", x + 500 + 70 * k, y - 5)
        P.c(f"fs_nd_r{k}", f"fs_nd_t{k}"); P.c(f"fs_nd_t{k}", f"fs_nd_w{k}", 1, 0); P.c(f"fs_nd_t{k}", "fs_go_need", 0, 0)
        P.c("fs_go_need", f"fs_nd_v{k}", k, 0)
    ex("fs_need_e", "($f1>0&&$f2>0)||($f3>0&&($f4>0||$f5>0))", x + 500, y + 15)   # (beat, melody or room notes found)
    for k in range(5):
        P.c(f"fs_nd_v{k}", "fs_need_e", 0, k)                            # (outlet 0 fires last: the hot inlet)
    P.obj("fs_need_chg", "change -1", x + 500, y + 30); P.obj("fs_need_s", "s fs_need", x + 560, y + 45)
    P.c("fs_need_e", "fs_need_chg"); P.c("fs_need_chg", "fs_need_s"); P.c("fs_need_chg", "fs_use", 0, 1)
    P.obj("fs_pocket", "spigot 1", x + 400, y + 70)                      # nothing is caught in a pocket (DadCraft)
    P.obj("fs_r_pocket", "r pocket", x + 600, y + 40); P.obj("fs_not_pocket", "== 0", x + 600, y + 60)
    P.c("fs_r_pocket", "fs_not_pocket"); P.c("fs_not_pocket", "fs_pocket", 0, 1)
    P.c("fs_det_sel", "fs_use"); P.c("fs_use", "fs_pocket"); P.c("fs_pocket", "fs_on")
    P.obj("fs_go", "t b b b b", x + 400, y + 100)
    P.c("fs_on", "fs_go")
    P.msg("fs_closed", "0", x + 480, y + 120); P.c("fs_go", "fs_closed", 3, 0); P.c("fs_closed", "fs_on", 0, 1)
    # handling: a jolt just before it starts (hjolt) or while it records (fs_jv set by the jolt) drops the slice
    P.obj("fs_jv_rd", "value hjolt", x + 600, y + 100); P.obj("fs_jv_wr", "value fs_jv", x + 600, y + 115)
    P.c("fs_go", "fs_jv_rd", 3, 0); P.c("fs_jv_rd", "fs_jv_wr")
    P.msg("fs_reset", "0", x + 540, y + 120)
    for nm in ("all", "lo", "hi"):
        P.c("fs_reset", f"fs_max_{nm}", 0, 1); P.c("fs_reset", f"fs_mx_{nm}", 0, 1)
    P.c("fs_go", "fs_reset", 2, 0)
    P.c("fs_go", "fs_rec", 1, 0)                                          # start recording
    # C4, the learned groove: where in the bar the sound fell, to the nearest 16th
    P.obj("fs_tick_r", "r tick16", x + 900, y + 40); P.obj("fs_tick_t", "t f b", x + 900, y + 60)
    P.obj("fs_tick_v", "f", x + 900, y + 80); P.obj("fs_tick_tm", "timer", x + 980, y + 80)
    P.c("fs_tick_r", "fs_tick_t"); P.c("fs_tick_t", "fs_tick_tm", 1, 0); P.c("fs_tick_t", "fs_tick_v", 0, 1)
    P.obj("fs_pos_t", "t b b b", x + 900, y + 110)
    P.c("fs_go", "fs_pos_t", 0, 0)
    P.obj("fs_pos_P", "value tc_P", x + 1060, y + 130)
    ex("fs_pos", "($f2+($f1>$f3/8))%16", x + 900, y + 150)
    P.c("fs_pos_t", "fs_pos_P", 2, 0); P.c("fs_pos_P", "fs_pos", 0, 2)
    P.c("fs_pos_t", "fs_tick_v", 1, 0); P.c("fs_tick_v", "fs_pos", 0, 1)
    P.c("fs_pos_t", "fs_tick_tm", 0, 1); P.c("fs_tick_tm", "fs_pos")
    P.obj("fs_pos_v", "f", x + 900, y + 170); P.c("fs_pos", "fs_pos_v", 0, 1)
    if os.environ.get("FS_DEBUG"):
        P.obj("fsdbg_pk", "pack f f f", x + 1100, y + 150); P.obj("fsdbg_ps", "s fsdbg_pos", x + 1100, y + 170)
        P.c("fs_pos_P", "fsdbg_pk", 0, 2); P.c("fs_tick_v", "fsdbg_pk", 0, 1); P.c("fs_tick_tm", "fsdbg_pk", 0, 0); P.c("fsdbg_pk", "fsdbg_ps")
    P.obj("fs_done", f"del {int(FS_SLICE_S * 1000) + 10}", x + 400, y + 140); P.c("fs_go", "fs_done", 0, 0)
    # ---- done: which role, worth keeping? ----
    P.obj("fs_d_t", "t b b b b", x + 400, y + 170)
    P.c("fs_done", "fs_d_t")
    P.msg("fs_open", "1", x + 600, y + 170); P.c("fs_d_t", "fs_open", 0, 0); P.c("fs_open", "fs_on", 0, 1)
    # kick: the lows carry it; hat: nearly all of it bright (within 3 dB) and little low; anything else is a snare
    # (hats loosened to 6 dB after the first phone test caught no hats at all from real taps)
    # a mic pop: louder than FS_POP_DB and carried by its lows (a snap at the mic overloads it into a low thump; on
    # the phone +8 dBFS with all its weight below 150 Hz, where real knocks were -16 to -42) is not kept
    # (and a slice spoilt by handling, a jolt near its start, is dropped and reported as 4)
    P.obj("fs_clean_r", "r fs_clean", x + 560, y + 190); P.obj("fs_clean_v", "value fsclean", x + 560, y + 205)
    P.c("fs_clean_r", "fs_clean_v")
    # the room's background for Clear Catches: the mic level's quietest lately (down at once, up FS_FLOOR_RISE dB a
    # reading, 30 readings a second); readings under FS_FLOOR_CUT (no sound yet, as at the start) don't count, so it
    # can't sink to nothing and let every catch through (the music's own floor, mfloor, does that for minutes; the
    # cut was -80 dBFS until Oct 22, but a stereo room's background reads -80..-85 on the Moto)
    P.obj("fs_bg_r", "r mic_level", x + 660, y + 160)
    ex("fs_bg_new", f"if($f1<{FS_FLOOR_CUT},fsfloor,min($f1,fsfloor+{FS_FLOOR_RISE}))", x + 660, y + 175)
    P.obj("fs_bg_wr", "value fsfloor", x + 660, y + 190); P.c("fs_bg_r", "fs_bg_new"); P.c("fs_bg_new", "fs_bg_wr")
    P.obj("fs_bg_lb", "loadbang", x + 760, y + 160); P.msg("fs_bg_40", "40", x + 760, y + 175)
    P.obj("fs_bg_init", "value fsfloor", x + 760, y + 190); P.c("fs_bg_lb", "fs_bg_40"); P.c("fs_bg_40", "fs_bg_init")
    P.obj("fs_st_r", "r st_side", x + 860, y + 160); P.obj("fs_st_v", "value fsstereo", x + 860, y + 175)
    P.c("fs_st_r", "fs_st_v")   # (st_side: above 0 with the room in stereo)
    ex("fs_role", f"if($f1<{fs_min(FS_MIN_DB)}||(fsclean>0&&$f1<fsfloor+fsclean),-1,if(fs_jv>0,-3,if($f1>{FS_POP_DB}&&$f2-$f1>-6,-2,if($f2-$f1>-6,0,if($f3-$f1>-6&&$f2-$f1<-15,2,1)))))", x + 400, y + 220)
    # inlets: $f1 overall (hot, last), $f2 lows, $f3 highs
    P.c("fs_d_t", "fs_max_hi", 2, 0); P.c("fs_max_hi", "fs_role", 0, 2)
    P.c("fs_d_t", "fs_max_lo", 2, 0); P.c("fs_max_lo", "fs_role", 0, 1)
    P.c("fs_d_t", "fs_max_all", 1, 0)
    # (the slice's levels go to the app first, for the event log: all, lows, highs)
    P.obj("fs_all_t", "t f f", x + 300, y + 230); P.c("fs_max_all", "fs_all_t"); P.c("fs_all_t", "fs_role", 0, 0)
    P.obj("fs_lv_pk", "pack f f f", x + 300, y + 250); P.obj("fs_lv_s", "s fs_levels", x + 300, y + 270)
    P.c("fs_all_t", "fs_lv_pk", 1, 0); P.c("fs_max_lo", "fs_lv_pk", 0, 1); P.c("fs_max_hi", "fs_lv_pk", 0, 2); P.c("fs_lv_pk", "fs_lv_s")
    P.obj("fs_keep", "moses 0", x + 400, y + 250); P.c("fs_role", "fs_keep")
    P.obj("fs_pop", "sel -2 -3", x + 300, y + 290); P.c("fs_keep", "fs_pop", 0, 0)   # (a dropped pop is reported as 3)
    P.msg("fs_pop3", "3", x + 300, y + 310); P.obj("fs_pop_s", "s fs_caught", x + 300, y + 330)
    P.c("fs_pop", "fs_pop3"); P.c("fs_pop3", "fs_pop_s")
    P.msg("fs_hnd4", "4", x + 340, y + 310); P.c("fs_pop", "fs_hnd4", 1, 0); P.c("fs_hnd4", "fs_pop_s")
    if os.environ.get("FS_DEBUG"):
        for nm, src, o in (("go", "fs_go", 0), ("onset", "fs_det_sel", 0), ("role", "fs_role", 0), ("all", "fs_max_all", 0),
                           ("lo", "fs_max_lo", 0), ("hi", "fs_max_hi", 0), ("use", "fs_use", 0)):
            P.obj(f"fsdbg_{nm}", f"s fsdbg_{nm}", x + 900, y + 200 + 20 * len(nm)); P.c(src, f"fsdbg_{nm}", o, 0)
    # ---- file it: the role's next slot, the level-matching gain, the copy ----
    P.obj("fs_k_t", "t f f f f", x + 400, y + 280)
    P.c("fs_keep", "fs_k_t", 1, 0)
    P.obj("fs_s_r", "s fs_caught", x + 700, y + 280); P.c("fs_k_t", "fs_s_r", 3, 0)
    P.obj("fs_heard_t", "table rh_heard 48", x + 1100, y)
    P.obj("fs_hd_t", "t b f", x + 1000, y + 280); P.c("fs_k_t", "fs_hd_t", 3, 0)
    ex("fs_hd_i", "$f2*16+$f1", x + 1000, y + 300)                       # (the role first, cold; then the spot)
    P.c("fs_hd_t", "fs_hd_i", 1, 1); P.c("fs_hd_t", "fs_pos_v", 0, 0); P.c("fs_pos_v", "fs_hd_i", 0, 0)
    P.obj("fs_hd_it", "t f f", x + 1000, y + 320); P.c("fs_hd_i", "fs_hd_it")
    P.obj("fs_hd_rd", "tabread rh_heard", x + 1000, y + 340)
    ex("fs_hd_new", f"min($f1+{FS_HEARD_ADD},1)", x + 1000, y + 360)
    P.obj("fs_hd_wr", "tabwrite rh_heard", x + 1000, y + 380)
    P.c("fs_hd_it", "fs_hd_wr", 1, 1); P.c("fs_hd_it", "fs_hd_rd", 0, 0); P.c("fs_hd_rd", "fs_hd_new"); P.c("fs_hd_new", "fs_hd_wr")
    for k in range(3):
        P.obj(f"fs_rr{k}", "f", x + 500 + 60 * k, y + 330)
        P.obj(f"fs_rr{k}_add", "+ 1", x + 500 + 60 * k, y + 350)
        P.obj(f"fs_rr{k}_mod", f"mod {FS_SLOTS}", x + 500 + 60 * k, y + 370)
        P.c(f"fs_rr{k}", f"fs_rr{k}_add"); P.c(f"fs_rr{k}_add", f"fs_rr{k}_mod"); P.c(f"fs_rr{k}_mod", f"fs_rr{k}", 0, 1)
    P.obj("fs_role_sel", "sel 0 1 2", x + 500, y + 300); P.c("fs_k_t", "fs_role_sel", 2, 0)
    for k in range(3):
        P.c("fs_role_sel", f"fs_rr{k}", k, 0)
        P.obj(f"fs_slot{k}", f"+ {k * FS_SLOTS}", x + 500 + 60 * k, y + 390); P.c(f"fs_rr{k}", f"fs_slot{k}")
    P.obj("fs_slot", "t f b b b f", x + 500, y + 420)                     # slot for the gain, loudness, min, max, copy
    for k in range(3):
        P.c(f"fs_slot{k}", "fs_slot")
    # the slice's loudest sample either way -> a gain that brings it to 0.5
    P.obj("fs_peak", "array max fs_stage", x + 700, y + 420)
    P.obj("fs_low", "array min fs_stage", x + 800, y + 420)
    # gain: the peak to 0.5, but the loudest 10 ms (dB, 100 = full scale) no higher than FS_RMS_TARGET
    ex("fs_gain", f"min(0.5/max(max(abs($f1),abs($f2)),0.01),{FS_RMS_TARGET}/max(pow(10,($f3-100)/20),0.001))", x + 700, y + 440)
    P.obj("fs_g_wr", "tabwrite fs_gains", x + 700, y + 460)
    P.c("fs_slot", "fs_g_wr", 4, 1)
    P.c("fs_slot", "fs_rmsdb", 3, 0); P.c("fs_rmsdb", "fs_gain", 0, 2)
    P.c("fs_slot", "fs_low", 2, 0); P.c("fs_low", "fs_gain", 0, 1)
    P.c("fs_slot", "fs_peak", 1, 0); P.c("fs_peak", "fs_gain"); P.c("fs_gain", "fs_g_wr")
    # copy the staging table into the slot's table
    ex("fs_name_i", f"int($f1/{FS_SLOTS})*10+$f1%{FS_SLOTS}", x + 500, y + 450)
    P.obj("fs_name", "makefilename fs_%02d", x + 500, y + 470)
    P.c("fs_slot", "fs_name_i", 0, 0); P.c("fs_name_i", "fs_name")
    P.obj("fs_get", "array get fs_stage", x + 500, y + 510)
    P.obj("fs_set", "array set fs_00", x + 500, y + 540)
    P.obj("fs_name_t", "t b s", x + 500, y + 490)
    P.c("fs_name", "fs_name_t"); P.c("fs_name_t", "fs_set", 1, 2); P.c("fs_name_t", "fs_get", 0, 0)
    P.c("fs_get", "fs_set")
    # how many slots each role has filled (for the fallback and the screen)
    P.obj("fs_fill_rd", "tabread fs_filled", x + 700, y + 300)
    ex("fs_fill_add", f"min($f1+1,{FS_SLOTS})", x + 700, y + 320)
    P.obj("fs_fill_wr", "tabwrite fs_filled", x + 700, y + 340)
    P.obj("fs_fill_t", "t f f", x + 640, y + 280)
    P.c("fs_k_t", "fs_fill_t", 1, 0); P.c("fs_fill_t", "fs_fill_wr", 1, 1); P.c("fs_fill_t", "fs_fill_rd", 0, 0)
    P.c("fs_fill_rd", "fs_fill_add"); P.c("fs_fill_add", "fs_fill_wr")
    # ---- the found voices: a hit (velocity, decay ms) plays a random filled slot of the role ----
    P.obj("fs_sr", "value fs_srv", x + 900, y); P.c("sr", "fs_sr")
    # the flourish chances (per 1000 hits), as values the voices read; fs_flourish_set (tests) sets both
    P.obj("fs_fl_lb", "loadbang", x + 1300, y); P.msg("fs_fl_init", f"{FS_FLOURISH[0]} {FS_FLOURISH[1]}", x + 1300, y + 15)
    P.obj("fs_fl_set", "r fs_flourish_set", x + 1400, y); P.obj("fs_fl_dup", "t f f", x + 1400, y + 15)
    P.obj("fs_fl_pk", "pack f f", x + 1400, y + 30); P.c("fs_fl_set", "fs_fl_dup"); P.c("fs_fl_dup", "fs_fl_pk", 1, 1); P.c("fs_fl_dup", "fs_fl_pk", 0, 0)
    P.obj("fs_fl_up", "unpack f f", x + 1300, y + 45); P.c("fs_fl_lb", "fs_fl_init"); P.c("fs_fl_init", "fs_fl_up"); P.c("fs_fl_pk", "fs_fl_up")
    P.obj("fs_fl_rv", "value fs_rv", x + 1300, y + 60); P.obj("fs_fl_hf", "value fs_hf", x + 1360, y + 60)
    P.c("fs_fl_up", "fs_fl_rv", 0, 0); P.c("fs_fl_up", "fs_fl_hf", 1, 0)
    outs = []
    for k, (r, rate, filt, lvl, decay) in enumerate(FS_ROLE_SPEC):
        vx, vy = x + 1000 + 400 * k, y + 600
        P.obj(f"fsv_{r}_in", "t f b b", vx, vy)                            # velocity in (decay set beforehand)
        P.obj(f"fsv_{r}_rnd", "random 1000", vx + 180, vy + 20)
        P.c(f"fsv_{r}_in", f"fsv_{r}_rnd", 1, 0)
        # its own role's slices; while it has none, any caught slice (played through this role's pitch and filter),
        # so one catch already makes the whole kit found
        own, S = f"fs_filled[{k}]", FS_SLOTS
        i = "int($f1*max(fs_filled[0]+fs_filled[1]+fs_filled[2],1)/1000)"
        ex(f"fsv_{r}_pick", f"if({own}>=1,{k * S}+int($f1*{own}/1000),if({i}<fs_filled[0],{i},"
                            f"if({i}<fs_filled[0]+fs_filled[1],{S}+{i}-fs_filled[0],{2 * S}+{i}-fs_filled[0]-fs_filled[1])))",
           vx + 180, vy + 40)
        P.c(f"fsv_{r}_rnd", f"fsv_{r}_pick")
        if os.environ.get("FS_DEBUG"):
            P.obj(f"fsdbg_pick_{r}", f"s fsdbg_pick_{r}", vx + 300, vy + 40); P.c(f"fsv_{r}_pick", f"fsdbg_pick_{r}")
            P.obj(f"fsdbg_amp_{r}", f"s fsdbg_amp_{r}", vx + 300, vy + 160); P.c(f"fsv_{r}_amp", f"fsdbg_amp_{r}")
        P.obj(f"fsv_{r}_slot", "t f f", vx + 180, vy + 60); P.c(f"fsv_{r}_pick", f"fsv_{r}_slot")
        P.obj(f"fsv_{r}_g", "tabread fs_gains", vx + 260, vy + 80); P.c(f"fsv_{r}_slot", f"fsv_{r}_g", 1, 0)
        P.obj(f"fsv_{r}_gv", "f 1", vx + 260, vy + 100); P.c(f"fsv_{r}_g", f"fsv_{r}_gv", 0, 1)
        ex(f"fsv_{r}_ni", f"int($f1/{FS_SLOTS})*10+$f1%{FS_SLOTS}", vx + 180, vy + 80)
        P.obj(f"fsv_{r}_name", "makefilename fs_%02d", vx + 180, vy + 100)
        P.msg(f"fsv_{r}_set", f"set {BS}$1", vx + 180, vy + 120)            # (a symbol "set fs_NN" would be ignored)
        P.c(f"fsv_{r}_slot", f"fsv_{r}_ni", 0, 0); P.c(f"fsv_{r}_ni", f"fsv_{r}_name"); P.c(f"fsv_{r}_name", f"fsv_{r}_set")
        P.obj(f"fsv_{r}_rd", "tabread4~ fs_00", vx, vy + 160)
        P.c(f"fsv_{r}_set", f"fsv_{r}_rd")
        # the read position: from the slice's start (or a few ms in) to its end, over its length / this hit's rate.
        # Each hit gets its own small pitch, start and loudness differences (random draws: $f2 pitch, $f3 start)
        P.obj(f"fsv_{r}_srv", "value fs_srv", vx + 100, vy + 120)
        P.obj(f"fsv_{r}_jt", "t f b b b b", vx + 100, vy + 125)
        P.obj(f"fsv_{r}_jf", "random 1000", vx + 340, vy + 125)              # (a flourish now and then)
        P.obj(f"fsv_{r}_jp", "random 1000", vx + 160, vy + 125); P.obj(f"fsv_{r}_js", "random 1000", vx + 220, vy + 125)
        P.obj(f"fsv_{r}_jv", "random 1000", vx + 280, vy + 125)
        rt = f"({rate}*pow(2,($f2-500)/500*{FS_VAR_SEMI}/12))"
        off = f"if($f3<{FS_VAR_START[0] * 1000},$f3/{FS_VAR_START[0] * 1000}*{FS_VAR_START[1] / 1000}*$f1,0)"
        rev, half = "($f4<fs_rv)", "($f4>=fs_rv&&$f4<fs_rv+fs_hf)"
        a, b = f"({off}+1)", f"($f1*{FS_SLICE_S})"
        ex(f"fsv_{r}_ix", f"if({rev},{b},{a}) ; if({rev},{a},{b}) ; ($f1*{FS_SLICE_S}-{off})/$f1*1000/{rt}*if({half},2,1)"
           .replace(" ; ", " " + BS + "; "), vx + 100, vy + 140)
        P.msg(f"fsv_{r}_ixmsg", f"{BS}$1 {BS}, {BS}$2 {BS}$3", vx, vy + 130)
        P.obj(f"fsv_{r}_ixpk", "pack f f f", vx, vy + 120)
        P.obj(f"fsv_{r}_ixl", "vline~", vx, vy + 145)
        P.c(f"fsv_{r}_ix", f"fsv_{r}_ixpk", 2, 2); P.c(f"fsv_{r}_ix", f"fsv_{r}_ixpk", 1, 1); P.c(f"fsv_{r}_ix", f"fsv_{r}_ixpk", 0, 0)
        P.c(f"fsv_{r}_ixpk", f"fsv_{r}_ixmsg"); P.c(f"fsv_{r}_ixmsg", f"fsv_{r}_ixl"); P.c(f"fsv_{r}_ixl", f"fsv_{r}_rd")
        P.c(f"fsv_{r}_srv", f"fsv_{r}_jt")
        P.c(f"fsv_{r}_jt", f"fsv_{r}_jf", 4, 0); P.c(f"fsv_{r}_jf", f"fsv_{r}_ix", 0, 3)
        P.c(f"fsv_{r}_jt", f"fsv_{r}_jv", 3, 0); P.c(f"fsv_{r}_jt", f"fsv_{r}_jp", 2, 0); P.c(f"fsv_{r}_jt", f"fsv_{r}_js", 1, 0)
        P.c(f"fsv_{r}_jp", f"fsv_{r}_ix", 0, 1); P.c(f"fsv_{r}_js", f"fsv_{r}_ix", 0, 2); P.c(f"fsv_{r}_jt", f"fsv_{r}_ix", 0, 0)
        # loudness: velocity x the slot's gain x the role's level x this hit's variation; decay from the hit (hats) or the role
        P.obj(f"fsv_{r}_dec", f"f {decay}", vx + 300, vy + 120)
        # (a backwards hit swells over its length and stops; a half-speed one fades twice as long: $f5 roll, $f6 length)
        rv5, hf5 = "($f5<fs_rv)", "($f5>=fs_rv&&$f5<fs_rv+fs_hf)"
        ex(f"fsv_{r}_amp", f"$f1*$f2*{lvl}*(1-{FS_VAR_VEL / 2}+{FS_VAR_VEL}*$f4/1000) ; if({rv5},$f6*0.9,2) ; "
                           f"if({rv5},15,$f3*if({hf5},2,1))".replace(" ; ", " " + BS + "; "), vx + 200, vy + 140)
        P.c(f"fsv_{r}_jv", f"fsv_{r}_amp", 0, 3); P.c(f"fsv_{r}_jf", f"fsv_{r}_amp", 0, 4); P.c(f"fsv_{r}_ix", f"fsv_{r}_amp", 2, 5)
        P.obj(f"fsv_{r}_ampk", "pack f f f", vx + 200, vy + 160)
        P.c(f"fsv_{r}_amp", f"fsv_{r}_ampk", 2, 2); P.c(f"fsv_{r}_amp", f"fsv_{r}_ampk", 1, 1); P.c(f"fsv_{r}_amp", f"fsv_{r}_ampk", 0, 0)
        P.msg(f"fsv_{r}_envm", f"0 {BS}, {BS}$1 {BS}$2 {BS}, 0 {BS}$3 {BS}$2", vx + 200, vy + 180)
        P.obj(f"fsv_{r}_env", "vline~", vx + 200, vy + 200)
        P.c(f"fsv_{r}_ampk", f"fsv_{r}_envm"); P.c(f"fsv_{r}_envm", f"fsv_{r}_env")
        P.c(f"fsv_{r}_gv", f"fsv_{r}_amp", 0, 1); P.c(f"fsv_{r}_dec", f"fsv_{r}_amp", 0, 2)
        # order on a hit: count + pick the slot (sets table and gain), then position, then loudness
        P.obj(f"fsv_{r}_go", "t f b b b", vx, vy + 80)
        P.c(f"fsv_{r}_in", f"fsv_{r}_go", 0, 0)
        P.c(f"fsv_{r}_go", f"fsv_{r}_dec", 3, 0)                             # (bang: this hit's decay)
        P.c(f"fsv_{r}_go", f"fsv_{r}_gv", 2, 0)                              # (bang: the gain read just above)
        P.c(f"fsv_{r}_go", f"fsv_{r}_srv", 1, 0)                             # (bang: the read position starts)
        P.c(f"fsv_{r}_go", f"fsv_{r}_amp", 0, 0)
        P.obj(f"fsv_{r}_f", filt, vx, vy + 220)
        P.sig2(f"fsv_{r}_s", "*~", f"fsv_{r}_rd", f"fsv_{r}_env", vx, vy + 190)
        P.c(f"fsv_{r}_s", f"fsv_{r}_f")
        outs.append(f"fsv_{r}_f")
    # (the Mix's kick trim; named fs_ks_* so it sits in the fsound group: outside it, a DSP loop between the two)
    P.sig2("fs_ks_kick", "*~", outs[0], "ln_mix_kick", x + 1000, y + 890)
    P.sig2("fs_ks0", "+~", "fs_ks_kick", outs[1], x + 1000, y + 900)
    # a soft limit, so no caught sound can jump out of the beat (tanh: gentle below, never above FS_LIMIT)
    soft_limit(P, "fs_ks", "fs_ks0", FS_LIMIT, FS_MAKEUP, x + 1000, y + 920)
    soft_limit(P, "fs_hat_lim", outs[2], FS_LIMIT, FS_MAKEUP, x + 1100, y + 920)
    return "fs_ks", "fs_hat_lim"

def add_glitch_loop(P, x):
    """C5, loop layering: when the glitch cloud fires, one bar of its own sound (gcx_mono: the glitch voices, not the
    room or the beat) is recorded, noting the 16th it began on. Every bar, at that same 16th, the loop plays again
    inside the beat, fading GL_KEEP per bar; the next glitch replaces it. Level: beat_glitch, then the beat's own
    level (it joins the drum mix). Returns the loop's signal."""
    y = 1700
    def ex(name, text, xx, yy):
        return P.obj(name, "expr " + text.replace(",", BS + ",").replace("$", BS + "$"), xx, yy)
    n = int(GL_MAX_S * 48000) + 1000
    P.obj("gl_tab", f"table gl_loop {n}", x, y)
    P.obj("gl_stab", f"table gl_stage {n}", x + 100, y)
    P.obj("gl_rec", "tabwrite~ gl_stage", x, y + 40)
    P.obj("gl_rec_in", "*~ 0.5", x, y + 30); P.c("gc_pre_mono", "gl_rec_in"); P.c("gl_rec_in", "gl_rec")
    # a glitch fired (and nothing is being recorded): record a bar of it into the scratch table, remembering its 16th
    P.obj("gl_r_mode", "r gc_mode", x, y + 60)
    P.obj("gl_free", "spigot 1", x, y + 70); P.c("gl_r_mode", "gl_free")
    P.obj("gl_t", "t b b b b", x, y + 80); P.c("gl_free", "gl_t")
    P.msg("gl_busy", "0", x + 60, y + 95); P.c("gl_t", "gl_busy", 3, 0); P.c("gl_busy", "gl_free", 0, 1)
    P.obj("gl_tick_r", "r tick16", x + 200, y + 60); P.obj("gl_tick_v", "f", x + 200, y + 80)
    P.c("gl_tick_r", "gl_tick_v", 0, 1)
    P.obj("gl_pend", "f", x + 200, y + 110)                                 # the 16th of the take being recorded
    P.c("gl_t", "gl_tick_v", 2, 0); P.c("gl_tick_v", "gl_pend", 0, 1)
    P.c("gl_t", "gl_rec", 1, 0)
    P.obj("gl_rP", "value tc_P", x + 120, y + 110); P.obj("gl_rdur", "* 4", x + 120, y + 125)
    P.obj("gl_rdur2", "min " + str(int(GL_MAX_S * 1000)), x + 120, y + 135)
    P.obj("gl_rdone", "del 2400", x + 60, y + 150)
    P.c("gl_t", "gl_rP", 0, 0); P.c("gl_rP", "gl_rdur"); P.c("gl_rdur", "gl_rdur2"); P.c("gl_rdur2", "gl_rdone")
    # the take is done: loud enough? then it becomes the loop, at its 16th, at full level; either way, free again
    P.obj("gl_d_t", "t b b b", x + 60, y + 170); P.c("gl_rdone", "gl_d_t")
    P.msg("gl_free1", "1", x + 140, y + 170); P.c("gl_d_t", "gl_free1", 0, 0); P.c("gl_free1", "gl_free", 0, 1)
    P.obj("gl_mx", "array max gl_stage", x + 60, y + 190); P.obj("gl_mn", "array min gl_stage", x + 140, y + 190)
    ex("gl_loud", f"max(abs($f1),abs($f2))>={GL_MIN_PEAK}", x + 60, y + 210)
    P.c("gl_d_t", "gl_mn", 2, 0); P.c("gl_mn", "gl_loud", 0, 1); P.c("gl_d_t", "gl_mx", 1, 0); P.c("gl_mx", "gl_loud")
    P.obj("gl_keep", "sel 1", x + 60, y + 230); P.c("gl_loud", "gl_keep")
    P.obj("gl_k_t", "t b b b", x + 60, y + 250); P.c("gl_keep", "gl_k_t")
    P.obj("gl_get", "array get gl_stage", x + 200, y + 250); P.obj("gl_set", "array set gl_loop", x + 200, y + 270)
    P.c("gl_k_t", "gl_get", 2, 0); P.c("gl_get", "gl_set")
    P.obj("gl_start", "f -1", x + 200, y + 110 + 180)                       # -1 = no loop yet
    P.c("gl_k_t", "gl_pend", 1, 0); P.c("gl_pend", "gl_start", 0, 1)
    P.msg("gl_full", "1", x + 300, y + 110)                                  # a fresh loop starts at full level
    P.obj("gl_lvl", "f 1", x + 300, y + 140); P.c("gl_k_t", "gl_full", 0, 0); P.c("gl_full", "gl_lvl", 0, 1)
    # every 16th: is it the loop's spot? then play one bar of it from the start, a little quieter each time
    P.obj("gl_tick_t", "t f b b", x, y + 170); P.c("gl_tick_r", "gl_tick_t")
    P.c("gl_tick_t", "gl_start", 2, 0)
    ex("gl_here", "$f1==$f2", x, y + 200); P.c("gl_start", "gl_here", 0, 1); P.c("gl_tick_t", "gl_here", 0, 0)
    P.obj("gl_here_sel", "sel 1", x, y + 220); P.c("gl_here", "gl_here_sel")
    # order: the sample rate, the beat (-> the bar's length, the read ramp starts), the bar for the fades, the level
    P.obj("gl_play", "t b b b b", x, y + 240); P.c("gl_here_sel", "gl_play")
    P.obj("gl_lvl_rd", "t f f", x + 300, y + 270)
    P.c("gl_play", "gl_lvl", 0, 0); P.c("gl_lvl", "gl_lvl_rd")
    P.obj("gl_lvl_dec", f"* {GL_KEEP}", x + 380, y + 290); P.c("gl_lvl_rd", "gl_lvl_dec", 1, 0); P.c("gl_lvl_dec", "gl_lvl", 0, 1)
    P.obj("gl_s", "s gloop_play", x + 380, y + 310); P.c("gl_lvl_rd", "gl_s", 0, 0)     # (for the tests)
    # the bar's length now: 4 beats of the clock
    P.obj("gl_P", "value tc_P", x + 100, y + 270); P.c("gl_play", "gl_P", 2, 0)
    P.obj("gl_srv", "value fs_srv", x + 160, y + 270); P.c("gl_play", "gl_srv", 3, 0)
    # out: samples to read (hot for the ramp, last) ; the bar in ms (first)
    ex("gl_bar", f"min($f1*4,{GL_MAX_S * 1000})*$f2/1000 ; min($f1*4,{GL_MAX_S * 1000})".replace(" ; ", " " + BS + "; "), x + 100, y + 300)
    P.c("gl_srv", "gl_bar", 0, 1); P.c("gl_P", "gl_bar")
    P.obj("gl_ix_pk", "pack f f", x + 100, y + 330)
    P.c("gl_bar", "gl_ix_pk", 1, 1); P.c("gl_bar", "gl_ix_pk", 0, 0)
    P.msg("gl_ix_msg", f"0 {BS}, {BS}$1 {BS}$2", x + 100, y + 350)
    P.obj("gl_ix", "vline~", x + 100, y + 370)
    P.obj("gl_rd", "tabread4~ gl_loop", x + 100, y + 390)
    P.c("gl_ix_pk", "gl_ix_msg"); P.c("gl_ix_msg", "gl_ix"); P.c("gl_ix", "gl_rd")
    # its loudness: the level now, with 5 ms fades at the loop's ends
    ex("gl_env_v", "$f1 ; max($f2-10,0)".replace(" ; ", " " + BS + "; "), x + 300, y + 330)
    P.obj("gl_bar_ms", "f", x + 360, y + 310); P.c("gl_bar", "gl_bar_ms", 1, 1)
    P.c("gl_lvl_rd", "gl_env_v", 0, 0); P.c("gl_play", "gl_bar_ms", 1, 0); P.c("gl_bar_ms", "gl_env_v", 0, 1)
    P.obj("gl_env_pk", "pack f f", x + 300, y + 350)
    P.c("gl_env_v", "gl_env_pk", 1, 1); P.c("gl_env_v", "gl_env_pk", 0, 0)
    P.msg("gl_env_msg", f"0 {BS}, {BS}$1 5 {BS}, 0 5 {BS}$2", x + 300, y + 370)
    P.obj("gl_env", "vline~", x + 300, y + 390)
    P.c("gl_env_pk", "gl_env_msg"); P.c("gl_env_msg", "gl_env")
    P.sig2("gl_s0", "*~", "gl_rd", "gl_env", x + 100, y + 420)
    lvl = smoothed(P, "beat_glitch", "beat_glitch", x + 500, y + 300)
    return P.sig2("gl_out", "*~", "gl_s0", lvl, x + 100, y + 440)

def add_rhythm(P, x):
    """The pattern engine (roadmap 16, step C1): on the tempo clock's 16th ticks it plays bars from KICK/SNARE/HAT_PATS
    on the drum voices. Each bar picks per lane a pattern near the drive (or a neighbour), the kick never the same
    twice; each hit may drop out, the kick gets an extra 8th now and then, the snare a ghost note, and every 4th bar
    ends in a snare fill when the drive is up. Drive: walking 0.55 + 0.45 x pace; standing 0.1 + 0.5 x the room's
    liveliness (sparse and soft in a quiet room). It plays while the walking beat follows the tempo clock, and with
    beat_when 1 also standing still, softer (rh_gain). The old one-hit-per-step drums stay for "your steps"."""
    y = 600
    def ex(name, text, xx, yy):
        return P.obj(name, "expr " + text.replace(",", BS + ",").replace("$", BS + "$"), xx, yy)
    nk = len(KICK_PATS)
    assert len(BREAK_KICK_PATS) == len(BREAK_SNARE_PATS) == len(SNARE_PATS) == nk   # (break pool = index + nk)
    # (each lane's last row is Patterns made by rules': dcs_composer rewrites it every bar; it starts as a middle one)
    tabs = (("rh_kpat", KICK_PATS + BREAK_KICK_PATS + [KICK_PATS[2]]), ("rh_spat", SNARE_PATS + BREAK_SNARE_PATS + [SNARE_PATS[2]]),
            ("rh_hpat", HAT_PATS + [HAT_PATS[1]]), ("rh_fpat", (FILL_PAT,)), ("rh_flong", (FILL_LONG,)), ("rh_cmap", CHOP_MAPS), ("rh_rvel", (ROLL_VELS,)),
            ("rh_rst", (ROLL_STARTS,)))
    for k, (nm, pats) in enumerate(tabs):
        P.obj(nm, f"table {nm} {len(pats) * len(pats[0])}", x + 1500, y + 20 * k)
    P.obj("rh_lb", "loadbang", x + 1500, y - 40)
    vals = lambda pats: " ".join(str(v) for pat in pats for v in pat)
    P.msg("rh_tabs", " ".join(f"{BS}; {nm} 0 {vals(pats)}" for nm, pats in tabs), x + 1500, y - 20)
    P.c("rh_lb", "rh_tabs")
    P.obj("rh_r_bb", "r beat_break", x + 1400, y - 40); P.obj("rh_bb_v", "value rh_bbv", x + 1400, y - 20)
    P.c("rh_r_bb", "rh_bb_v")
    # ---- walking? pace? the room? -> drive (0..1) and the drums' level ----
    P.obj("rh_r_walk", "r clock_walking", x + 1700, y)                   # the clock knows: a step within 1.25 intervals
    P.obj("rh_walk", "value rh_walking", x + 1700, y + 80)
    P.c("rh_r_walk", "rh_walk")
    # stopping: in "only while walking" the drums fade at once (150 ms) instead of at the next room reading
    P.obj("rh_stop_sel", "sel 0", x + 1780, y + 20); P.c("rh_r_walk", "rh_stop_sel")
    P.obj("rh_stop_when", "f 1", x + 1780, y + 40); P.c("rh_stop_sel", "rh_stop_when")
    P.obj("rh_stop_sel2", "sel 0", x + 1780, y + 60); P.c("rh_stop_when", "rh_stop_sel2")
    P.msg("rh_stop_fade", "0 150", x + 1780, y + 80)
    P.c("rh_stop_sel2", "rh_stop_fade")
    P.obj("rh_r_step", "r step", x + 1900, y)
    ex("rh_pace", "min(max((1/max($f1,0.25)-1)/1.6,0),1)", x + 1900, y + 20)
    P.obj("rh_pace_v", "value rh_pace", x + 1900, y + 40)
    P.c("rh_r_step", "rh_pace"); P.c("rh_pace", "rh_pace_v")
    # the room: a slow level against its own quiet floor (follows down at once, creeps up 0.5 dB a second)
    P.obj("rh_r_lvl", "r mic_level", x + 2100, y)
    P.obj("rh_lvl_v", "f", x + 2100, y + 20); P.c("rh_r_lvl", "rh_lvl_v", 0, 1)
    P.obj("rh_tick", "metro 100", x + 2000, y + 60); P.c("rh_lb", "rh_tick")
    P.obj("rh_room_t", "t b b b b", x + 2000, y + 80)
    P.c("rh_tick", "rh_room_t")
    P.obj("rh_slow", "f", x + 2100, y + 100); P.obj("rh_floor", "f 50", x + 2200, y + 100)
    ex("rh_room", "$f1*0.05+$f2*0.95 ; min($f1,$f3+0.05) ; min(max((($f1*0.05+$f2*0.95)-$f3-3)/25,0),1)"
       .replace(" ; ", " " + BS + "; "), x + 2100, y + 130)
    P.c("rh_room_t", "rh_slow", 3, 0); P.c("rh_slow", "rh_room", 0, 1)
    P.c("rh_room_t", "rh_floor", 3, 0); P.c("rh_floor", "rh_room", 0, 2)
    P.c("rh_room_t", "rh_lvl_v", 2, 0); P.c("rh_lvl_v", "rh_room", 0, 0)
    P.c("rh_room", "rh_slow", 0, 1); P.c("rh_room", "rh_floor", 1, 1)
    P.obj("rh_loud", "f", x + 2100, y + 160); P.c("rh_room", "rh_loud", 2, 1)
    P.obj("rh_walk_rd", "value rh_walking", x + 1700, y + 180); P.obj("rh_pace_rd", "value rh_pace", x + 1800, y + 180)
    P.obj("rh_when", "f 1", x + 1900, y + 180); P.obj("rh_r_when", "r beat_when", x + 1900, y + 160)
    P.c("rh_r_when", "rh_when", 0, 1); P.c("rh_r_when", "rh_stop_when", 0, 1)
    P.obj("rh_mode", "f 1", x + 1960, y + 180); P.obj("rh_r_mode", "r beat_follow", x + 1960, y + 160)
    P.c("rh_r_mode", "rh_mode", 0, 1)
    # out: drive ; gain (0 when the pattern engine shouldn't play)
    ex("rh_dg", "if($f2,0.4+0.45*$f3,0.1+0.5*$f1) ; if($f5,if($f2,1,$f4*(0.35+0.4*$f1)),1)".replace(" ; ", " " + BS + "; "),
       x + 1700, y + 210)
    P.c("rh_room_t", "rh_walk_rd", 1, 0); P.c("rh_walk_rd", "rh_dg", 0, 1)
    P.c("rh_room_t", "rh_pace_rd", 1, 0); P.c("rh_pace_rd", "rh_dg", 0, 2)
    P.c("rh_room_t", "rh_when", 1, 0); P.c("rh_when", "rh_dg", 0, 3)
    P.c("rh_room_t", "rh_mode", 1, 0); P.c("rh_mode", "rh_dg", 0, 4)
    P.c("rh_room_t", "rh_loud", 0, 0); P.c("rh_loud", "rh_dg", 0, 0)
    # Intensity (the app's macro, 0.5 = as set) makes the patterns calmer or busier: +-INT_DRIVE/2 on the drive
    P.obj("rh_r_int", "r intensity", x + 1600, y + 200); ex("rh_int_o", f"($f1-0.5)*{INT_DRIVE}", x + 1600, y + 215)
    P.obj("rh_int_v", "value rh_intoff", x + 1600, y + 230); P.c("rh_r_int", "rh_int_o"); P.c("rh_int_o", "rh_int_v")
    ex("rh_dg_i", f"min(max(max($f1,bandp*{BAND_FLOOR})+rh_intoff+rh_band,0),1)", x + 1700, y + 228); P.c("rh_dg", "rh_dg_i", 0, 0)
    P.obj("rh_drive_v", "value rh_drive", x + 1700, y + 240); P.c("rh_dg_i", "rh_drive_v", 0, 0)
    # only real changes move the level (a ramp restarted every 100 ms never quite arrives), and to 0 always quickly
    P.obj("rh_gain_chg", "change", x + 1800, y + 225)
    ex("rh_gain_ms", "$f1 ; if($f1<=0,150,400)".replace(" ; ", " " + BS + "; "), x + 1800, y + 235)
    P.obj("rh_gain_pk", "pack f 400", x + 1800, y + 245); P.obj("rh_gain", "line~", x + 1800, y + 260)
    P.c("rh_dg", "rh_gain_chg", 1, 0); P.c("rh_gain_chg", "rh_gain_ms")
    P.c("rh_gain_ms", "rh_gain_pk", 1, 1); P.c("rh_gain_ms", "rh_gain_pk", 0, 0)
    P.c("rh_gain_pk", "rh_gain"); P.c("rh_stop_fade", "rh_gain")
    # in "your steps" mode the old drums play at full level (and only on steps)
    # ---- each 16th tick: a new bar picks patterns; every tick reads them, with the bar's variations ----
    P.obj("rh_r_tick", "r tick16", x, y)
    P.obj("rh_on", "spigot 1", x, y + 20)
    P.c("rh_r_mode", "rh_on", 0, 1)                                       # (only in tempo clock mode)
    # swing: an off-beat 16th reaches the patterns rh_sw of a 16th late (the clock's own ticks stay straight: chimes,
    # the learned groove and glitch loops keep to them). Every SWING_EVERY_BARS bars a new amount is picked and glided to.
    sx = x - 400
    P.obj("rh_sw_t", "t f f", sx, y + 5); P.c("rh_r_tick", "rh_sw_t")
    ex("rh_sw_dl", "if($f1%2,rh_sw*tc_P/4,0)", sx + 80, y + 10)
    P.obj("rh_sw_pk", "pack f f", sx, y + 15); P.c("rh_sw_t", "rh_sw_dl", 1, 0); P.c("rh_sw_dl", "rh_sw_pk", 0, 1)
    P.c("rh_sw_t", "rh_sw_pk", 0, 0)
    P.obj("rh_sw_pipe", "pipe 0", sx, y + 20); P.c("rh_sw_pk", "rh_sw_pipe"); P.c("rh_sw_pipe", "rh_on")
    P.obj("rh_sw_v", "value rh_sw", sx + 200, y); P.obj("rh_sw_tv", "value rh_swt", sx + 280, y)
    P.obj("rh_sw_bar", "sel 0", sx + 160, y + 25); P.c("rh_sw_t", "rh_sw_bar", 1, 0)
    P.obj("rh_sw_bt", "t b b", sx + 160, y + 35); P.c("rh_sw_bar", "rh_sw_bt")
    P.obj("rh_sw_n", "f", sx + 240, y + 45); P.obj("rh_sw_n1", "+ 1", sx + 280, y + 45); P.obj("rh_sw_nm", f"mod {SWING_EVERY_BARS}", sx + 320, y + 45)
    P.c("rh_sw_bt", "rh_sw_n", 1, 0); P.c("rh_sw_n", "rh_sw_n1"); P.c("rh_sw_n1", "rh_sw_nm"); P.c("rh_sw_nm", "rh_sw_n", 0, 1)
    P.obj("rh_sw_new", "sel 0", sx + 240, y + 60); P.c("rh_sw_n", "rh_sw_new")
    P.obj("rh_sw_rnd", f"random {len(SWING_CHOICES)}", sx + 240, y + 75); P.c("rh_sw_new", "rh_sw_rnd")
    P.obj("rh_sw_ch", f"table rh_swing_ch {len(SWING_CHOICES)}", sx + 400, y)
    P.msg("rh_sw_chi", f"{BS}; rh_swing_ch 0 {' '.join(str(v) for v in SWING_CHOICES)}", sx + 400, y + 15)
    P.obj("rh_sw_lb", "loadbang", sx + 400, y - 15); P.c("rh_sw_lb", "rh_sw_chi")
    P.obj("rh_sw_rd", "tabread rh_swing_ch", sx + 240, y + 90); P.c("rh_sw_rnd", "rh_sw_rd"); P.c("rh_sw_rd", "rh_sw_tv")
    ex("rh_sw_gl", f"rh_sw+(rh_swt-rh_sw)*{SWING_GLIDE}+$f1*0", sx + 160, y + 105)
    P.c("rh_sw_bt", "rh_sw_gl", 0, 0)
    P.obj("rh_sw_gt", "t f f", sx + 160, y + 120); P.c("rh_sw_gl", "rh_sw_gt"); P.c("rh_sw_gt", "rh_sw_v", 1, 0)
    P.obj("rh_sw_s", "s rh_swing", sx + 160, y + 135); P.c("rh_sw_gt", "rh_sw_s", 0, 0)
    P.obj("rh_sw_set", "r rh_swing_set", sx + 400, y + 40); P.obj("rh_sw_set_t", "t f f", sx + 400, y + 55)   # (tests)
    P.c("rh_sw_set", "rh_sw_set_t"); P.c("rh_sw_set_t", "rh_sw_v", 1, 0); P.c("rh_sw_set_t", "rh_sw_tv", 0, 0)
    P.obj("rh_t", "t f f b b b b", x, y + 40)
    P.c("rh_on", "rh_t")
    P.obj("rh_bar_sel", "sel 15", x + 300, y + 60)                      # (after tick 15's hits: the next bar's patterns)
    P.c("rh_t", "rh_bar_sel", 1, 0)
    # (patterns are held for RH_HOLD_BARS bars, DadCraft: "slightly too busy/random"; the bar count runs every bar)
    P.obj("rh_hold", "spigot 1", x + 300, y + 70)
    # new bar: bar count, the patterns, the fill
    P.obj("rh_bar_t", "t b b b b b", x + 300, y + 80)
    P.obj("rh_bar_st", "t b b b b", x + 300, y + 65); P.c("rh_bar_sel", "rh_bar_st")
    P.c("rh_bar_st", "rh_bar_cnt_t", 3, 0)
    # arrangement moments: after the patterns are picked, maybe the next bar is a sparse one (rh_brk: 0 none, 1 hats
    # only, 2 no kick, 3 the downbeat kick and the hats), at least BREAK_MIN_BARS apart and never on a fill bar
    P.obj("rh_brk_t", "t b b", x - 300, y + 130); P.c("rh_bar_st", "rh_brk_t", 1, 0)
    P.obj("rh_brk_r", "random 1000", x - 300, y + 145); P.obj("rh_brk_k", "random 3", x - 200, y + 145)
    ex("rh_brk_e", f"if(rh_bsince>={BREAK_MIN_BARS}&&$f1<{int(BREAK_CHANCE * 1000)}&&rh_fill==0,1+$f2,0)", x - 300, y + 160)
    P.c("rh_brk_t", "rh_brk_k", 1, 0); P.c("rh_brk_k", "rh_brk_e", 0, 1); P.c("rh_brk_t", "rh_brk_r", 0, 0); P.c("rh_brk_r", "rh_brk_e")
    P.obj("rh_brk_o", "t f f f", x - 300, y + 175); P.c("rh_brk_e", "rh_brk_o")
    P.obj("rh_brk_v", "value rh_brk_next", x - 300, y + 190); P.c("rh_brk_o", "rh_brk_v", 2, 0)
    # (the next bar's patterns are picked before tick 15's own hits: the break starts exactly at tick 0 instead)
    P.obj("rh_brk_t0", "sel 0", x - 400, y + 60); P.c("rh_t", "rh_brk_t0", 1, 0)
    P.obj("rh_brk_nx", "value rh_brk_next", x - 400, y + 75); P.obj("rh_brk_now", "value rh_brk", x - 400, y + 90)
    P.c("rh_brk_t0", "rh_brk_nx"); P.c("rh_brk_nx", "rh_brk_now")
    ex("rh_bs_e", "if($f1>0,0,rh_bsince+1)", x - 200, y + 190); P.c("rh_brk_o", "rh_bs_e", 1, 0)
    P.obj("rh_bs_v", "value rh_bsince", x - 200, y + 205); P.c("rh_bs_e", "rh_bs_v")
    P.obj("rh_brk_s", "s rh_break", x - 300, y + 205); P.c("rh_brk_o", "rh_brk_s", 0, 0)    # (for the tests)
    # breakbeat (beat_break): after the sparse-bar choice, the next bar may get a long fill, a chop or a roll (never in
    # a sparse bar, a roll never inside a fill); like rh_brk they take effect from tick 0
    bx = x - 700
    P.obj("rh_bb_t", "t b b b b", bx, y + 130); P.c("rh_bar_st", "rh_bb_t", 0, 0)
    ex("rh_bfl_e", f"(rh_brk_next==0)*(random(0,1000)<rh_bbv*{int(BB_FILL * 1000)}||(rh_fill>0)*(random(0,1000)<rh_bbv*{int(BB_FILL_LONGER * 1000)}))",
       bx + 150, y + 145)
    P.obj("rh_bfl_nv", "value rh_bfl_next", bx + 150, y + 160); P.c("rh_bb_t", "rh_bfl_e", 3, 0); P.c("rh_bfl_e", "rh_bfl_nv")
    ex("rh_chop_e", f"if(rh_brk_next==0&&random(0,1000)<rh_bbv*{int(BB_CHOP * 1000)},random(1,{len(CHOP_MAPS)}),0)", bx + 100, y + 175)
    P.obj("rh_chop_nv", "value rh_chop_next", bx + 100, y + 190); P.c("rh_bb_t", "rh_chop_e", 2, 0); P.c("rh_chop_e", "rh_chop_nv")
    ex("rh_rl_e", "random(3,5)", bx + 50, y + 205)
    P.obj("rh_rl_nv", "value rh_rlen_next", bx + 50, y + 220); P.c("rh_bb_t", "rh_rl_e", 1, 0); P.c("rh_rl_e", "rh_rl_nv")
    ex("rh_roll_e", f"if(rh_brk_next==0&&random(0,1000)<rh_bbv*{int(BB_ROLL * 1000)},rh_rst[random(0,{len(ROLL_STARTS)})],0)",
       bx, y + 235)
    ex("rh_roll_x", "if((rh_bfl_next>0&&$f1+1>=8)||(rh_fill>0&&$f1+1>=12),0,$f1)", bx, y + 250)
    P.obj("rh_roll_nv", "value rh_roll_next", bx, y + 265)
    P.c("rh_bb_t", "rh_roll_e", 0, 0); P.c("rh_roll_e", "rh_roll_x"); P.c("rh_roll_x", "rh_roll_nv")
    P.obj("rh_bb_sp", "pack f f f", bx + 200, y + 265)                   # (for the tests: roll, chop, long fill;
    P.obj("rh_bb_s", "s rh_bb_bar", bx + 200, y + 280)                   #  the roll comes last, into the hot inlet)
    P.c("rh_roll_x", "rh_bb_sp", 0, 0); P.c("rh_chop_e", "rh_bb_sp", 0, 1); P.c("rh_bfl_e", "rh_bb_sp", 0, 2)
    P.c("rh_bb_sp", "rh_bb_s")
    P.obj("rh_bb_t0", "t b b b b", bx, y + 60); P.c("rh_brk_t0", "rh_bb_t0")
    for k, nm in enumerate(("bfl", "chop", "rlen", "roll")):
        P.obj(f"rh_{nm}_nx", f"value rh_{nm}_next", bx + 70 * k, y + 75); P.obj(f"rh_{nm}_now", f"value rh_{nm}", bx + 70 * k, y + 90)
        P.c("rh_bb_t0", f"rh_{nm}_nx", 3 - k, 0); P.c(f"rh_{nm}_nx", f"rh_{nm}_now")
    # the learned groove fades every bar (48 spots x FS_HEARD_KEEP)
    P.obj("rh_hd_fade", "t b", x + 600, y + 40); P.c("rh_bar_st", "rh_hd_fade", 2, 0)
    P.msg("rh_hd_48", "48", x + 600, y + 55); P.obj("rh_hd_until", "until", x + 600, y + 70)
    P.obj("rh_hd_i", "f", x + 600, y + 85); P.obj("rh_hd_inc", "+ 1", x + 640, y + 85)
    P.msg("rh_hd_0", "0", x + 680, y + 55)
    P.c("rh_hd_fade", "rh_hd_0"); P.c("rh_hd_0", "rh_hd_i", 0, 1)
    P.c("rh_hd_fade", "rh_hd_48"); P.c("rh_hd_48", "rh_hd_until"); P.c("rh_hd_until", "rh_hd_i")
    P.c("rh_hd_i", "rh_hd_inc"); P.c("rh_hd_inc", "rh_hd_i", 0, 1)
    P.obj("rh_hd_it", "t f f", x + 600, y + 100); P.c("rh_hd_i", "rh_hd_it")
    P.obj("rh_hd_rd", "tabread rh_heard", x + 600, y + 115); P.obj("rh_hd_mul", f"* {FS_HEARD_KEEP}", x + 600, y + 130)
    P.obj("rh_hd_wr", "tabwrite rh_heard", x + 600, y + 145)
    P.c("rh_hd_it", "rh_hd_wr", 1, 1); P.c("rh_hd_it", "rh_hd_rd", 0, 0); P.c("rh_hd_rd", "rh_hd_mul"); P.c("rh_hd_mul", "rh_hd_wr")
    P.obj("rh_bar_cnt_t", "t b b", x + 380, y + 70)
    P.obj("rh_bar_cnt", "f", x + 460, y + 70); P.obj("rh_bar_cnt_add", "+ 1", x + 500, y + 70)
    P.c("rh_bar_cnt_t", "rh_bar_cnt", 1, 0); P.c("rh_bar_cnt", "rh_bar_cnt_add"); P.c("rh_bar_cnt_add", "rh_bar_cnt", 0, 1)
    ex("rh_bar_due", f"$f1%{RH_HOLD_BARS}=={RH_HOLD_BARS - 1}", x + 460, y + 85)
    P.c("rh_bar_cnt", "rh_bar_due"); P.c("rh_bar_due", "rh_hold", 0, 1)
    P.c("rh_bar_cnt_t", "rh_hold", 0, 0); P.c("rh_hold", "rh_bar_t")
    # The Band Plays (band_plays, DadCraft Oct 21: the band could "dynamically get busier within measures and scale back
    # down, like a natural build and flow of a song"): each BAND_PHRASE-bar phrase starts pulled back and builds bar by
    # bar (rh_band on the drive: more ghost notes, extra kicks, longer hats, busier picks), its last bar ends in a snare
    # fill (rh_bfill), then the next phrase drops back. Each phrase is a little bigger, smaller or the same (bplift).
    # bandch: the standing-still chimes' own chance, rising over the phrase. (rh_bar_cnt here is the coming bar.)
    P.obj("bp_r", "r band_plays", x + 1300, y - 40); P.obj("bp_v", "value bandp", x + 1300, y - 25); P.c("bp_r", "bp_v")
    P.obj("bp_t", "t f f", x + 1300, y + 70); P.c("rh_bar_cnt", "bp_t")
    ex("bp_first", f"$f1%{BAND_PHRASE}==1", x + 1400, y + 85); P.obj("bp_first_s", "sel 1", x + 1400, y + 95)
    # (expr's random: a new [random] would take the next seed and reshuffle every later [random]'s sequence)
    ex("bp_lift", f"(random(0,3)-1)*{BAND_LIFT}", x + 1400, y + 115)
    P.obj("bp_lift_v", "value bplift", x + 1400, y + 125)
    P.c("bp_t", "bp_first", 1, 0); P.c("bp_first", "bp_first_s"); P.c("bp_first_s", "bp_lift")
    P.c("bp_lift", "bp_lift_v")
    # (a phrase starts on a bar that picks new patterns, which are held RH_HOLD_BARS: bars 1, 3, 5... of the count)
    b = f"(($f1+{BAND_PHRASE - 1})%{BAND_PHRASE})"
    ex("bp_arc", f"bandp*({BAND_ARC[0]}+{BAND_ARC[1]}*pow({b}/{BAND_PHRASE - 1},{BAND_ARC[2]})+bplift) ; "
                 f"bandp*({b}=={BAND_PHRASE - 1}) ; bandp*(0.5+{b}/{BAND_PHRASE - 1})".replace(" ; ", " " + BS + "; "),
       x + 1300, y + 140)
    P.c("bp_t", "bp_arc", 0, 0)
    ex("bp_ph", f"{b}", x + 1500, y + 140); P.obj("bp_ph_s", "s band_phase", x + 1500, y + 155)   # (for the tests)
    P.c("bp_t", "bp_ph", 0, 0); P.c("bp_ph", "bp_ph_s")
    # (the drive takes the new bar's place at once: the bar's patterns are picked right after this, rh_bar_cnt_t)
    P.obj("bp_redrive", "t b f", x + 1300, y + 150); P.c("bp_arc", "bp_redrive", 0, 0); P.c("bp_redrive", "rh_dg_i", 0, 0)
    for k, nm in enumerate(("rh_band", "rh_bfill", "bandch")):
        P.obj(f"bp_out{k}", f"value {nm}", x + 1300 + 70 * k, y + 160)
        P.c("bp_redrive" if k == 0 else "bp_arc", f"bp_out{k}", 1 if k == 0 else k, 0)
    P.obj("rh_bar", "f", x + 300, y + 100); P.obj("rh_bar_add", "+ 1", x + 340, y + 100)
    P.c("rh_bar_t", "rh_bar", 4, 0); P.c("rh_bar", "rh_bar_add"); P.c("rh_bar_add", "rh_bar", 0, 1)
    P.obj("rh_dr_rd", "value rh_drive", x + 420, y + 100)
    for k, lane in enumerate("ksh"):
        P.obj(f"rh_rp{lane}", "random 3", x + 500 + 60 * k, y + 100)
    P.obj("rh_rf", "random 100", x + 680, y + 100)
    top = len(KICK_PATS) - 1
    pick = lambda r: f"min(max(int($f1*{top}+0.5)+{r}-1,0),{top})"
    ex("rh_pick", f"if({pick('$f2')}==$f6,if($f6<{top},$f6+1,$f6-1),{pick('$f2')}) ; {pick('$f3')} ; {pick('$f4')} ; "
                  f"($f7%{RH_FILL_EVERY // RH_HOLD_BARS}=={RH_FILL_EVERY // RH_HOLD_BARS - 1})*($f1>0.35)*($f5<70)"
                  .replace(" ; ", " " + BS + "; "), x + 300, y + 130)
    # inlets: $f1 drive (hot, last), $f2-$f4 the lanes' randoms, $f5 fill roll, $f6 last kick pattern, $f7 bar
    P.c("rh_bar_t", "rh_rpk", 3, 0); P.c("rh_rpk", "rh_pick", 0, 1)
    P.c("rh_bar_t", "rh_rps", 3, 0); P.c("rh_rps", "rh_pick", 0, 2)
    P.c("rh_bar_t", "rh_rph", 2, 0); P.c("rh_rph", "rh_pick", 0, 3)
    P.c("rh_bar_t", "rh_rf", 1, 0); P.c("rh_rf", "rh_pick", 0, 4)
    ex("rh_bbf_e", f"random(0,1000)<rh_bbv*{int(BB_POOL * 1000)}", x + 740, y + 115)
    P.obj("rh_bbf_v", "value rh_bbf", x + 740, y + 130); P.c("rh_bar_t", "rh_bbf_e", 1, 0); P.c("rh_bbf_e", "rh_bbf_v")
    P.c("rh_bar", "rh_pick", 0, 6)
    P.c("rh_bar_t", "rh_dr_rd", 0, 0); P.c("rh_dr_rd", "rh_pick", 0, 0)
    for k, nm in enumerate(("kp", "sp", "hp", "fill")):
        P.obj(f"rh_{nm}", f"value rh_{nm}", x + 300 + 70 * k, y + 170)
        if nm in ("kp", "sp"):                                             # (the breakbeat pool sits after the usual one)
            ex(f"rh_{nm}_bb", f"if(rgen,{2 * nk},$f1+{nk}*rh_bbf)", x + 300 + 70 * k, y + 160)
            P.c("rh_pick", f"rh_{nm}_bb", k, 0); P.c(f"rh_{nm}_bb", f"rh_{nm}")
        elif nm == "hp":
            ex("rh_hp_g", f"if(rgen,{len(HAT_PATS)},$f1)", x + 300 + 70 * k, y + 160)
            P.c("rh_pick", "rh_hp_g", k, 0); P.c("rh_hp_g", "rh_hp")
        else:
            P.c("rh_pick", f"rh_{nm}", k, 0)
    # Patterns made by rules (pat_gen): after each bar's last tick dcs_composer writes the next bar into those rows
    P.obj("rh_gen_g", "spigot 0", x + 900, y + 60); P.obj("rh_gen_r", "r pat_gen", x + 960, y + 40)
    P.c("rh_gen_r", "rh_gen_g", 0, 1); P.c("rh_bar_sel", "rh_gen_g")
    P.obj("rh_gen_t", "t b b", x + 900, y + 80); P.c("rh_gen_g", "rh_gen_t")
    P.obj("rh_gen_d", "value rh_drive", x + 900, y + 100); P.obj("rh_gen_b", "value rh_bbv", x + 960, y + 100)
    P.obj("rh_gen_pk", "pack f f", x + 900, y + 120)
    P.c("rh_gen_t", "rh_gen_b", 1, 0); P.c("rh_gen_b", "rh_gen_pk", 0, 1); P.c("rh_gen_t", "rh_gen_d", 0, 0); P.c("rh_gen_d", "rh_gen_pk")
    P.msg("rh_gen_m", f"drums {BS}$1 {BS}$2", x + 900, y + 140); P.c("rh_gen_pk", "rh_gen_m"); P.c("rh_gen_m", "cmp")
    P.obj("rh_gen_rt", "route drum", x + 900, y + 160); P.c("cmp", "rh_gen_rt", 4, 0)
    P.obj("rh_gen_lane", "route 0 1 2", x + 900, y + 180); P.c("rh_gen_rt", "rh_gen_lane")
    for k, (tab, row) in enumerate((("rh_kpat", 2 * nk), ("rh_spat", 2 * nk), ("rh_hpat", len(HAT_PATS)))):
        P.obj(f"rh_gen_at{k}", f"list prepend {row * 16}", x + 900 + 90 * k, y + 200); P.c("rh_gen_lane", f"rh_gen_at{k}", k, 0)
        P.obj(f"rh_gen_s{k}", f"s {tab}", x + 900 + 90 * k, y + 220); P.c(f"rh_gen_at{k}", f"rh_gen_s{k}")
    P.obj("rh_gen_snd", "s rh_gen_bar", x + 1200, y + 180); P.c("rh_gen_rt", "rh_gen_snd")   # (for the tests)
    P.obj("rh_kp_last", "f", x + 300, y + 150); P.c("rh_pick", "rh_kp_last", 0, 0); P.c("rh_kp_last", "rh_pick", 0, 5)
    P.obj("rh_bar_s", "s rh_bar_patterns", x + 600, y + 170)              # (for the tests: which kick pattern)
    P.c("rh_pick", "rh_bar_s", 0, 0)
    # every tick: read the bar's choices and three randoms, then the hits
    for k, nm in enumerate(("kp", "sp", "hp", "fill")):
        P.obj(f"rh_rd_{nm}", f"value rh_{nm}", x + 60 * k, y + 220)
    P.obj("rh_rd_dr", "value rh_drive", x + 240, y + 220)
    for k in range(3):
        P.obj(f"rh_r{k}", "random 1000", x + 300 + 60 * k, y + 220)
    # $f1 tick (hot), $f2 kp, $f3 sp, $f4 hp, $f5 fill, $f6 drive, $f7-$f9 randoms 0..999
    st = "rh_cmap[rh_chop*16+$f1]"                                      # (a chopped bar reads its steps reordered)
    kv = f"rh_kpat[$f2*16+{st}]"
    sv = f"rh_spat[$f3*16+{st}]"
    hv = f"rh_hpat[$f4*16+{st}]"
    kick = f"if({kv}>0,{kv}*($f7>=40),($f1%2==0)*($f1>0)*($f7<10+20*$f6)*0.55)"
    snare = f"if(($f5||(rh_bfill&&$f6>0.2))&&$f1>=12,rh_fpat[$f1-12]*(0.75+0.25*$f8/1000),if({sv}>0,{sv},($f8<8+17*$f6)*0.22))"
    # breakbeat: a long fill over the last two beats; a roll's hits on the 16ths (its in-between 32nds come below)
    snare = f"if(rh_bfl>0&&$f1>=8,rh_flong[$f1-8]*(0.75+0.25*$f8/1000),{snare})"
    in_roll = "(rh_roll>0&&$f1>=rh_roll&&$f1<rh_roll+2)"
    snare = f"max({snare},if({in_roll},rh_rvel[min(max(($f1-rh_roll)*2,0),2)],0))"
    roll2 = "if(rh_roll>0&&($f1==rh_roll||($f1==rh_roll+1&&rh_rlen>=4)),rh_rvel[min(max(($f1-rh_roll)*2+1,1),3)],0)"
    hat = f"if({hv}>=2,{hv}-2,{hv})*($f9>=30)"
    # the learned groove (C4): a spot where a sound was caught lately plays that role too, as loud as it's remembered
    learn = lambda k: f"(rh_heard[{16 * k}+$f1]>={FS_HEARD_MIN})*rh_heard[{16 * k}+$f1]*0.9"
    kick, snare = f"max({kick},{learn(0)})", f"max({snare},{learn(1)})"
    # an arrangement moment (rh_brk) thins this bar out
    kick = f"({kick})*if(rh_brk==1||rh_brk==2,0,if(rh_brk==3,$f1==0,1))"
    snare = f"({snare})*if(rh_brk==1||rh_brk==3,0,1)"
    roll2 = f"({roll2})*if(rh_brk==1||rh_brk==3,0,1)"
    hat = f"max({hat},{learn(2)}) ; if({hv}>=2,240,30+60*$f6)"
    ex("rh_hits", f"{kick} ; {snare} ; {hat} ; {roll2}".replace(" ; ", " " + BS + "; "), x, y + 260)
    # a roll's in-between hits: a 32nd after the tick (tc_P = the beat in ms)
    ex("rh_r2_dl", "$f1 " + BS + "; tc_P/8", x + 100, y + 275)
    P.obj("rh_r2_pk", "pack f f", x + 100, y + 285); P.c("rh_hits", "rh_r2_dl", 4, 0)
    P.c("rh_r2_dl", "rh_r2_pk", 1, 1); P.c("rh_r2_dl", "rh_r2_pk", 0, 0)
    P.obj("rh_r2_pipe", "pipe 0", x + 100, y + 295); P.c("rh_r2_pk", "rh_r2_pipe")
    P.obj("rh_r2_ok", "moses 0.001", x + 100, y + 310); P.c("rh_r2_pipe", "rh_r2_ok")
    P.c("rh_t", "rh_r2", 2, 0); P.c("rh_r2", "rh_hits", 0, 8)
    P.c("rh_t", "rh_r1", 3, 0); P.c("rh_r1", "rh_hits", 0, 7)
    P.c("rh_t", "rh_r0", 4, 0); P.c("rh_r0", "rh_hits", 0, 6)
    P.c("rh_t", "rh_rd_dr", 5, 0); P.c("rh_rd_dr", "rh_hits", 0, 5)
    for k, nm in enumerate(("kp", "sp", "hp", "fill")):
        P.c("rh_t", f"rh_rd_{nm}", 5, 0); P.c(f"rh_rd_{nm}", "rh_hits", 0, k + 1)
    P.c("rh_t", "rh_hits", 0, 0)
    # hits -> voices: per role the synth drum, the found sound, or both ("Beat sounds", rhythm_src 0 synth, 1 found,
    # 2 both); with found sounds, until the first catch the synth plays faintly (FS_PLACEHOLDER), after it never
    P.obj("rh_src", "f 1", x + 600, y + 280); P.obj("rh_r_src", "r rhythm_src", x + 600, y + 260)
    P.c("rh_r_src", "rh_src", 0, 1)
    P.obj("rh_k_ok", "moses 0.001", x, y + 300); P.c("rh_hits", "rh_k_ok", 0, 0)
    P.obj("rh_s_ok", "moses 0.001", x + 100, y + 300); P.c("rh_hits", "rh_s_ok", 1, 0)
    P.obj("rh_h_pk", "pack f f", x + 200, y + 300)
    P.c("rh_hits", "rh_h_pk", 3, 1); P.c("rh_hits", "rh_h_pk", 2, 0)
    P.obj("rh_h_ok", "route 0", x + 200, y + 320); P.c("rh_h_pk", "rh_h_ok")
    P.obj("rh_h_v", "unpack f f", x + 260, y + 340); P.c("rh_h_ok", "rh_h_v", 1, 0)
    for k, (lane, src, synth_in, found_in) in enumerate((("k", "rh_k_ok", "kick_in", "fsv_k_in"),
                                                          ("s", "rh_s_ok", "snare_in", "fsv_s_in"),
                                                          ("h", "rh_h_v", None, "fsv_h_in"))):
        rx = x + 700 + 250 * k
        P.obj(f"rh_{lane}r_t", "t f b b", rx, y + 380)
        P.c(src, f"rh_{lane}r_t", 1 if lane != "h" else 0, 0)
        P.msg(f"rh_{lane}r_k", "0", rx + 80, y + 380)
        ex(f"rh_{lane}r_n", "$f1+fs_filled[0]+fs_filled[1]+fs_filled[2]", rx + 80, y + 400)   # (anything caught yet?)
        P.obj(f"rh_{lane}r_src", "f", rx + 160, y + 400); P.c("rh_r_src", f"rh_{lane}r_src", 0, 1)
        P.obj(f"rh_{lane}r_init", "loadbang", rx + 160, y + 360); P.msg(f"rh_{lane}r_init1", "1", rx + 160, y + 370)
        P.c(f"rh_{lane}r_init", f"rh_{lane}r_init1"); P.c(f"rh_{lane}r_init1", f"rh_{lane}r_src", 0, 1)
        P.c(f"rh_{lane}r_t", f"rh_{lane}r_k", 2, 0); P.c(f"rh_{lane}r_k", f"rh_{lane}r_n")
        P.c(f"rh_{lane}r_t", f"rh_{lane}r_src", 1, 0)
        ex(f"rh_{lane}r", f"if($f2==0||$f2==2,$f1,if($f3<1,$f1*{FS_PLACEHOLDER},0)) ; if($f2>=1&&$f3>=1,$f1,0)".replace(" ; ", " " + BS + "; "),
           rx, y + 420)
        P.c(f"rh_{lane}r_n", f"rh_{lane}r", 0, 2); P.c(f"rh_{lane}r_src", f"rh_{lane}r", 0, 1)
        P.c(f"rh_{lane}r_t", f"rh_{lane}r", 0, 0)
        P.obj(f"rh_{lane}r_syn", "moses 0.001", rx, y + 450); P.obj(f"rh_{lane}r_fnd", "moses 0.001", rx + 100, y + 450)
        P.c(f"rh_{lane}r", f"rh_{lane}r_syn", 0, 0); P.c(f"rh_{lane}r", f"rh_{lane}r_fnd", 1, 0)
        if lane == "h":
            P.obj("rh_h_pk2", "pack f f", rx, y + 480)
            P.c("rh_h_v", "rh_h_pk2", 1, 1); P.c("rh_h_v", "fsv_h_dec", 1, 1)   # (the decay comes out of unpack first)
            P.c("rh_hr_syn", "rh_h_pk2", 1, 0); P.c("rh_h_pk2", "h_amp_msg")
            P.c("rh_hr_fnd", "fsv_h_in", 1, 0)
        else:
            P.c(f"rh_{lane}r_syn", synth_in, 1, 0); P.c(f"rh_{lane}r_fnd", found_in, 1, 0)
    for k, lane in enumerate(("kick", "snare", "hat")):
        P.obj(f"rh_s_{lane}", f"s drum_{lane}", x + 400 + 70 * k, y + 330)
    P.c("rh_k_ok", "rh_s_kick", 1, 0); P.c("rh_s_ok", "rh_s_snare", 1, 0); P.c("rh_h_v", "rh_s_hat", 0, 0)
    P.c("rh_r2_ok", "rh_s_snare", 1, 0); P.c("rh_r2_ok", "rh_sr_t", 1, 0)

def add_beat(P):
    """Footstep drums. The app sends [step( with the time since the previous step (seconds) at every
    footstep. Steps alternate kick and snare, and a hi-hat falls half a step after each one. Returns the
    name of the mono output signal."""
    x = 2300
    P.obj("r_step", "r pulse", x, 20)                                  # steps or the tempo clock (add_tempo_clock)
    P.obj("step_t", "t b b f", x, 50)
    P.obj("step_old", "spigot 1", x, 35)                                # the old one-hit-per-step drums: "your steps" only
    P.obj("step_old_r", "r beat_follow", x + 80, 20); P.obj("step_old_i", "== 0", x + 80, 35)
    P.c("step_old_r", "step_old_i"); P.c("step_old_i", "step_old", 0, 1)
    P.c("r_step", "step_old"); P.c("step_old", "step_t")
    # Half the interval, in ms, sets the hi-hat delay (right inlet of [del]: sets without starting it).
    P.obj("step_clip", "clip 0.25 1.2", x + 250, 80)
    P.obj("step_half_ms", "* 500", x + 250, 110)
    P.obj("hat_del", "del 270", x + 250, 140)
    P.c("step_t", "step_clip", 2, 0)
    P.c("step_clip", "step_half_ms")
    P.c("step_half_ms", "hat_del", 0, 1)
    P.c("step_t", "hat_del", 1, 0)
    # Even steps kick, odd steps snare.
    P.obj("cnt_f", "f", x, 80)
    P.obj("cnt_add", "+ 1", x, 110)
    P.obj("cnt_mod", "mod 2", x, 140)
    P.obj("cnt_sel", "sel 0 1", x, 170)
    P.c("step_t", "cnt_f", 0, 0)
    P.c("cnt_f", "cnt_add"); P.c("cnt_add", "cnt_mod"); P.c("cnt_mod", "cnt_f", 0, 1)
    P.c("cnt_f", "cnt_sel")

    comma = " " + BS + ","   # Pd writes a message-box comma as a separate escaped token
    def env(name, text, trig, trig_outlet, ex, ey):
        P.msg(name + "_msg", text.replace(",", comma), ex, ey)
        P.obj(name, "vline~", ex, ey + 30)
        P.c(trig, name + "_msg", trig_outlet, 0)
        P.c(name + "_msg", name)
        return name

    # Each voice takes a velocity (0..1): the old step path sends 1, the pattern engine its pattern's value.
    P.obj("kick_in", "t b f", x, 200); P.obj("snare_in", "t f f", x + 300, 200)
    P.msg("cnt_k1", "1", x, 185); P.msg("cnt_s1", "1", x + 300, 185)
    P.c("cnt_sel", "cnt_k1", 0, 0); P.c("cnt_k1", "kick_in"); P.c("cnt_sel", "cnt_s1", 1, 0); P.c("cnt_s1", "snare_in")
    # Kick: a sine whose pitch falls from 3.5x to 1x of the "pitch" setting in 70 ms.
    pitch = smoothed(P, "beat_pitch", "beat_pitch", x + 500, 20)
    k_amp = env("k_amp", "0, $1 2, 0 260 2".replace("$", BS + "$"), "kick_in", 1, x, 220)
    k_pit = env("k_pit", "3.5, 1 70", "kick_in", 0, x + 150, 220)
    P.sig2("k_freq", "*~", k_pit, pitch, x + 150, 290)
    P.obj("k_osc", "osc~", x + 150, 320)
    P.c("k_freq", "k_osc")
    P.sig2("kick", "*~", "k_osc", k_amp, x, 350)

    # Snare: noise and a short tone.
    s_amp = env("s_amp", "0, $1 1, 0 150 1".replace("$", BS + "$"), "snare_in", 1, x + 300, 220)
    s_ton = env("s_ton", "0, $1 1, 0 90 1".replace("$", BS + "$"), "snare_in", 0, x + 450, 220)
    P.obj("noise", "noise~", x + 600, 220)
    P.obj("s_hp", "hip~ 1800", x + 600, 250)
    P.c("noise", "s_hp")
    P.sig2("s_noise", "*~", "s_hp", s_amp, x + 300, 350)
    P.obj("s_osc", "osc~ 185", x + 450, 290)
    P.sig2("s_tone", "*~", "s_osc", s_ton, x + 450, 350)
    P.obj("s_tone_half", "*~ 0.5", x + 450, 380)
    P.c("s_tone", "s_tone_half")
    P.sig2("snare_raw", "+~", "s_noise", "s_tone_half", x + 300, 410)
    snl = smoothed(P, "beat_snare", "beat_snare", x + 700, 20)
    P.sig2("snare", "*~", "snare_raw", snl, x + 300, 440)

    # Hi-hat: bright noise, very short.
    # A harder step (step_strength, 0.5 = as usual) opens the hat: louder and longer. A soft step leaves it faint.
    # step_strength comes with the step (it is the step before's); step_hit follows once this step's bump is over,
    # before the hat plays, so the hat answers the stomp itself.
    P.obj("st_init", "loadbang", x + 1200, 20)
    P.msg("st_init_msg", BS + "; step_strength 0.5 " + BS + "; beat_sure 1", x + 1200, 50)
    P.c("st_init", "st_init_msg")
    P.obj("h_r_str", "r step_strength", x + 900, 140)
    P.obj("h_str", "f 0.5", x + 750, 170)
    P.obj("h_r_hit", "r step_hit", x + 1000, 140)
    P.c("hat_del", "h_str"); P.c("h_r_str", "h_str", 0, 1); P.c("h_r_hit", "h_str", 0, 1)
    P.obj("h_calc", ("expr 0.1+1.2*$f1*$f1" + " " + BS + "; 20+200*$f1*$f1").replace("$", BS + "$"), x + 750, 190)
    P.obj("h_pack", "pack f f", x + 750, 205)
    P.c("h_str", "h_calc"); P.c("h_calc", "h_pack", 1, 1); P.c("h_calc", "h_pack", 0, 0)
    P.msg("h_amp_msg", ("0, " + BS + "$1 1, 0 " + BS + "$2 1").replace(",", comma), x + 750, 220)
    P.obj("h_amp", "vline~", x + 750, 250)
    P.c("h_pack", "h_amp_msg"); P.c("h_amp_msg", "h_amp")
    add_rhythm(P, x)                                                    # the pattern engine plays these voices too
    h_amp = "h_amp"
    P.obj("h_hp", "hip~ 7000", x + 750, 250)
    P.c("noise", "h_hp")
    P.sig2("hat_raw", "*~", "h_hp", h_amp, x + 750, 350)
    hats = smoothed(P, "beat_hats", "beat_hats", x + 900, 20)
    P.sig2("hat", "*~", "hat_raw", hats, x + 750, 380)

    # Mix, master level and on/off switch (a little headroom so the drums never clip the output).
    mixk = smoothed(P, "mix_kick", "mix_kick", x + 1300, 20)            # the Mix's kick trim (synth and found)
    P.sig2("kick_m", "*~", "kick", mixk, x, 470)
    P.sig2("beat_ks0", "+~", "kick_m", "snare", x, 480)
    P.obj("ks_r_str", "r step_strength", x + 1200, 140)
    P.obj("ks_calc", ("expr 0.8+0.4*$f1").replace("$", BS + "$"), x + 1200, 170)
    P.obj("ks_pk", "pack f 5", x + 1200, 200)
    P.obj("ks_gain", "line~", x + 1200, 230)
    P.c("ks_r_str", "ks_calc"); P.c("ks_calc", "ks_pk"); P.c("ks_pk", "ks_gain")
    fs_ks, fs_hat = add_found_kit(P, x + 3000)
    P.sig2("beat_ks1", "+~", "beat_ks0", fs_ks, x, 490)                 # the found kick and snare
    P.sig2("beat_kick_snare", "*~", "beat_ks1", "ks_gain", x, 500)   # a harder step lifts kick and snare a little
    P.sig2("fs_hat_l", "*~", fs_hat, hats, x + 900, 400)
    P.sig2("beat_hats_all", "+~", "hat", "fs_hat_l", x + 750, 410)
    P.sig2("beat_sum00", "+~", "beat_kick_snare", "beat_hats_all", x, 525)
    P.sig2("beat_sum0", "+~", "beat_sum00", add_glitch_loop(P, x + 3000), x, 530)   # (C5: glitch loops in the beat)
    P.sig2("beat_sum", "*~", "beat_sum0", "rh_gain", x, 535)            # softer standing still (the pattern engine)
    lvl = smoothed(P, "beat_level", "beat_level", x + 1050, 20)
    bon = smoothed(P, "beat_on", "beat_on", x + 1150, 20)
    P.sig2("beat_gain0", "*~", lvl, bon, x + 1050, 120)
    # Uneven steps (beat_sure 0.4..1 from StepDetector) play the drums softer until the rhythm settles again.
    P.obj("bs_r", "r beat_sure", x + 1350, 140)
    P.obj("bs_pk", "pack f 300", x + 1350, 170)
    P.obj("bs_ln", "line~", x + 1350, 200)
    P.c("bs_r", "bs_pk"); P.c("bs_pk", "bs_ln")
    P.sig2("beat_gain", "*~", "beat_gain0", "bs_ln", x + 1050, 150)
    P.sig2("beat_lvl", "*~", "beat_sum", "beat_gain", x, 560)
    P.obj("beat_out", "*~ 0.6", x, 590)
    P.c("beat_lvl", "beat_out")
    return "beat_out"
