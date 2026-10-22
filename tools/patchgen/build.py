# SPDX-License-Identifier: GPL-3.0-or-later
"""The whole patch: input, effects chain, output, and every part wired in."""
from .constants import *  # noqa: F401,F403 (the tuning numbers)
from .core import *  # noqa: F401,F403
from .stereo import add_stereo_in, add_stereo_out
from .rhythm import add_tempo_clock, add_beat, add_kits, add_kit_drums
from .music import add_music
from .glitch import add_glitch_cloud, add_onset_switch, add_reverse_reverb
from .sensing import add_pocket_rustle, add_handling, add_clip_watch


def build():
    P = Patch()

    # --- global smoothing time (ms) feeds every ramp ---
    P.obj("r_smooth", "r smooth", 700, 20)
    P.obj("smooth_x3", "* 3", 700, 50)   # echo time glides 3x slower than other controls
    P.c("r_smooth", "smooth_x3")

    # --- input and gain ---
    P.obj("adc", "adc~ 1 2", 20, 20)
    gain = smoothed(P, "gain", "gain", 20, 60)
    P.sig2("mic", "*~", add_stereo_in(P), gain, 20, 140)

    # --- level meter: RMS in dB (100 = full scale) every ~5 ms, shared by gate and compressor ---
    P.obj("meter", "env~ 1024 256", 20, 170)
    P.c("mic", "meter")
    P.obj("meter_fs", "- 100", 20, 200)                          # dB relative to full scale
    P.c("meter", "meter_fs")
    # The app follows the room's loudness for its "Louder = higher/lower" movers (about 30 a second).
    P.obj("level_clock", "metro 33", 120, 170)
    P.obj("level_hold", "f", 120, 200)                           # latest meter reading
    P.obj("level_send", "s mic_level", 120, 230)
    P.obj("level_start", "loadbang", 120, 140)
    P.c("level_start", "level_clock"); P.c("level_clock", "level_hold")
    P.c("meter", "level_hold", 0, 1); P.c("level_hold", "level_send")

    # --- noise gate: closes when the level is under the threshold (-80 = never closes) ---
    # Its own fast meter (5 ms window, every 1.3 ms) so it opens on time without delaying what you hear (you hear the
    # room live through the headphones). It opens at the threshold but only starts closing GATE_HYST_DB below it (no
    # flutter at the edge), stays open at least gate_hold ms, then fades shut over gate_release; opens over gate_attack.
    P.obj("gate_meter", "env~ 256 64", 250, 140)
    P.c("mic", "gate_meter")
    P.obj("gate_m_t", "t f b", 250, 155)
    P.obj("r_gate_thresh", "r gate_thresh", 330, 140)
    P.obj("gate_thr_v", "f -80", 330, 160)
    P.c("r_gate_thresh", "gate_thr_v", 0, 1)
    P.obj("gate_act", ("expr if($f2<=-79.9||$f1-100>=$f2,1,if($f1-100<$f2-" + str(GATE_HYST_DB) + ",2,0))")
          .replace("$", BS + "$").replace(",", BS + ","), 250, 175)
    P.c("gate_meter", "gate_m_t"); P.c("gate_m_t", "gate_thr_v", 1, 0); P.c("gate_thr_v", "gate_act", 0, 1)
    P.c("gate_m_t", "gate_act", 0, 0)
    P.obj("gate_sel", "sel 1 2", 250, 190)
    P.c("gate_act", "gate_sel")
    # loud enough: cancel any pending close, open
    P.obj("gate_on_t", "t b b b", 250, 205)
    P.msg("gate_stop", "stop", 330, 220); P.msg("gate_pend0", "0", 380, 220); P.msg("gate_one", "1", 250, 220)
    P.obj("gate_hold", "del 0", 330, 245)
    P.obj("gate_pend", "f", 450, 245)
    P.c("gate_sel", "gate_on_t", 0, 0)
    P.c("gate_on_t", "gate_stop", 2, 0); P.c("gate_stop", "gate_hold")
    P.c("gate_on_t", "gate_pend0", 1, 0); P.c("gate_pend0", "gate_pend", 0, 1)
    P.c("gate_on_t", "gate_one", 0, 0)
    # clearly under: start the hold once (not again every reading, or it would never run out), then close
    P.obj("gate_pend_rd", "t b", 450, 205)
    P.c("gate_sel", "gate_pend_rd", 1, 0); P.c("gate_pend_rd", "gate_pend")
    P.obj("gate_pend_sel", "sel 0", 450, 225)
    P.c("gate_pend", "gate_pend_sel")
    P.obj("gate_hold_go", "t b b", 450, 235)
    P.msg("gate_pend1", "1", 520, 235)
    P.obj("gate_r_hold", "r gate_hold", 520, 140); P.obj("gate_hold_v", "f 0", 520, 160)
    P.c("gate_r_hold", "gate_hold_v", 0, 1)
    P.c("gate_pend_sel", "gate_hold_go"); P.c("gate_hold_go", "gate_pend1", 1, 0); P.c("gate_pend1", "gate_pend", 0, 1)
    P.c("gate_hold_go", "gate_hold_v", 0, 0); P.c("gate_hold_v", "gate_hold")
    P.msg("gate_zero", "0", 330, 265)
    P.c("gate_hold", "gate_zero"); P.c("gate_zero", "gate_pend", 0, 1)
    # the gate's state: 1 open, 0 shut; only changes move it, each at its own speed
    P.obj("gate_change", "change -1", 250, 280)
    P.c("gate_one", "gate_change"); P.c("gate_zero", "gate_change")
    P.obj("gate_state_sel", "sel 0 1", 250, 290)
    P.c("gate_change", "gate_state_sel")
    P.obj("gate_r_att", "r gate_attack", 600, 140); P.obj("gate_att_v", "f 5", 600, 160)
    P.obj("gate_r_rel", "r gate_release", 680, 140); P.obj("gate_rel_v", "f 80", 680, 160)
    P.c("gate_r_att", "gate_att_v", 0, 1); P.c("gate_r_rel", "gate_rel_v", 0, 1)
    P.c("gate_state_sel", "gate_rel_v", 0, 0); P.c("gate_state_sel", "gate_att_v", 1, 0)
    P.msg("gate_close", "0 " + BS + "$1", 250, 300)                    # fade out over the release
    P.msg("gate_open_msg", "1 " + BS + "$1", 330, 300)                 # open over the attack
    P.c("gate_rel_v", "gate_close"); P.c("gate_att_v", "gate_open_msg")
    P.obj("ln_gate", "line~", 250, 320)
    P.c("gate_close", "ln_gate"); P.c("gate_open_msg", "ln_gate")
    P.sig2("mic_gated", "*~", "mic", "ln_gate", 20, 350)

    # --- compressor: gain reduction = (1 - 1/ratio) * dB over threshold, plus make-up gain ---
    P.obj("r_comp_thresh", "r comp_thresh", 450, 170)
    P.obj("r_comp_ratio", "r comp_ratio", 550, 170)
    P.obj("r_comp_makeup", "r comp_makeup", 650, 170)
    P.obj("comp_calc", "expr 100-(1-1/max($f2{c}1))*max($f1-$f3{c}0)+$f4".replace("{c}", BS + ",").replace("$", BS + "$"), 450, 200)
    P.c("meter_fs", "comp_calc"); P.c("r_comp_ratio", "comp_calc", 0, 1)
    P.c("r_comp_thresh", "comp_calc", 0, 2); P.c("r_comp_makeup", "comp_calc", 0, 3)
    P.obj("comp_lin", "dbtorms", 450, 230)                       # 100 + dB -> linear gain
    # Squeezing (the gain going down) takes comp_attack ms, letting go comp_release ms.
    P.obj("comp_t", "t f f f", 450, 240)
    # (an unchanged target keeps the speed it had, so a squeeze that has settled still finishes at the attack speed)
    # (the meter ripples a little even on a steady sound: changes under ~0.1 dB don't count as a direction)
    P.obj("comp_time", "expr if($f1<$f2*0.99,$f3,if($f1>$f2*1.01,$f4,$f5))".replace("$", BS + "$").replace(",", BS + ","), 520, 250)
    P.obj("r_comp_att", "r comp_attack", 600, 230); P.obj("r_comp_rel", "r comp_release", 680, 230)
    P.c("r_comp_att", "comp_time", 0, 2); P.c("r_comp_rel", "comp_time", 0, 3)
    P.obj("comp_time_init", "loadbang", 760, 230); P.msg("comp_time_def", "40", 760, 250)
    P.c("comp_time_init", "comp_time_def"); P.c("comp_time_def", "comp_time", 0, 2); P.c("comp_time_def", "comp_time", 0, 3)
    P.c("comp_time_def", "comp_time", 0, 4)
    P.obj("pk_comp", "pack f 40", 450, 260)
    P.obj("ln_comp", "line~", 450, 290)
    P.c("comp_calc", "comp_lin"); P.c("comp_lin", "comp_t")
    P.c("comp_t", "comp_time", 2, 0); P.c("comp_time", "pk_comp", 0, 1)   # with the old gain: down or up?
    P.c("comp_time", "comp_time", 0, 4)                                     # (remembered for an unchanged target)
    P.c("comp_t", "comp_time", 1, 1)                                        # then this one is the old gain
    P.c("comp_t", "pk_comp", 0, 0); P.c("pk_comp", "ln_comp")
    P.sig2("mic_comp0", "*~", "mic_gated", "ln_comp", 20, 380)

    # --- pocket hush: in a pocket the mic hears cloth and rustle, so turn it down after the compressor
    # (which would otherwise lift the rustle back up). "pocket" (0/1) comes from the app. Mic-down protection adds
    # more, only while the phone sits mic end first ("pocket_down" 0/1): a further cut, and the low cloth thump
    # filtered out. At protection 0 the sound is exactly as without it. ---
    ex = lambda name, text, xx, yy: P.obj(name, "expr " + text.replace(",", BS + ",").replace("$", BS + "$"), xx, yy)
    ex("ph_calc", f"pow(10,-($f1*$f2*{POCKET_HUSH_DB}+$f3*$f4*{POCKET_GUARD_DB})/20)", 800, 260)
    for k, recv in enumerate(("pocket", "pocket_hush", "pocket_down", "pocket_guard")):
        P.obj(f"ph_r{k}", f"r {recv}", 800 + 80 * k, 170)
        if k == 0:
            P.c("ph_r0", "ph_calc")
        else:
            P.obj(f"ph_t{k}", "t b f", 800 + 80 * k, 200)
            P.c(f"ph_r{k}", f"ph_t{k}"); P.c(f"ph_t{k}", "ph_calc", 1, k); P.c(f"ph_t{k}", "ph_calc", 0, 0)
    P.obj("ph_t", "t f f", 800, 290)
    P.obj("ph_v", "value phush", 880, 320)                        # the music reads it too (chimes from rustle)
    P.obj("ph_pk", f"pack f {POCKET_HUSH_MS}", 800, 320)
    P.obj("ph_ln", "line~", 800, 350)
    P.c("ph_calc", "ph_t"); P.c("ph_t", "ph_v", 1, 0); P.c("ph_t", "ph_pk", 0, 0); P.c("ph_pk", "ph_ln")
    P.obj("ph_init", "loadbang", 1160, 230); P.msg("ph_init_msg", "1", 1160, 260)
    P.c("ph_init", "ph_init_msg"); P.c("ph_init_msg", "ph_v"); P.c("ph_init_msg", "ph_ln")
    # handling noise (taps, grips, rubbing) is turned down the same way: hush_ln = everything that hears the hushed mic
    P.sig2("hush0", "*~", "ph_ln", add_handling(P), 800, 365)
    P.sig2("hush_ln", "*~", "hush0", add_pocket_rustle(P), 800, 372)
    # the thump filter, crossfaded in by (mic down) x (protection)
    ex("pg_amt", "$f1*$f2", 1100, 290)
    P.obj("pg_down", "f", 1040, 260)
    P.c("ph_t2", "pg_down", 1, 1); P.c("ph_t3", "pg_amt", 1, 1)       # values first, then the bangs below
    P.c("ph_t2", "pg_down", 0, 0); P.c("ph_t3", "pg_down", 0, 0); P.c("pg_down", "pg_amt")
    P.obj("pg_pk", f"pack f {POCKET_HUSH_MS}", 1100, 320)
    P.obj("pg_ln", "line~", 1100, 350)
    P.c("pg_amt", "pg_pk"); P.c("pg_pk", "pg_ln")
    P.obj("pg_hp1", f"hip~ {POCKET_GUARD_HP_HZ}", 120, 380)
    P.obj("pg_hp2", f"hip~ {POCKET_GUARD_HP_HZ}", 120, 395)
    P.c("mic_comp0", "pg_hp1"); P.c("pg_hp1", "pg_hp2")
    P.sig2("pg_diff", "-~", "pg_hp2", "mic_comp0", 120, 410)       # filtered minus plain, times the amount, plus plain
    P.sig2("pg_part", "*~", "pg_diff", "pg_ln", 120, 425)
    P.sig2("mic_comp1", "+~", "mic_comp0", "pg_part", 20, 390)
    P.sig2("mic_comp", "*~", "mic_comp1", "hush_ln", 20, 395)

    # --- sing in key (Oct 18): dcs_shift~ moves each sung note to the nearest note of the music's key and scale.
    # Amount = how far (0 = off: the plain voice, nothing computed); glide = how long a correction takes (0 = the
    # hard robotic jump). On, the voice is 30 ms late (the grains need what follows), so it replaces the plain
    # voice instead of mixing with it (a late copy beside it would comb).
    P.obj("tune_dcs", "dcs_shift~ 0", 120, 440); P.c("mic_comp", "tune_dcs")
    P.obj("tune_r_amt", "r tune_amount", 200, 400)
    P.msg("tune_snap", f"snap {BS}$1", 200, 420); P.c("tune_r_amt", "tune_snap"); P.c("tune_snap", "tune_dcs")
    P.obj("tune_r_gl", "r tune_glide", 280, 400)
    P.msg("tune_gl", f"glide {BS}$1", 280, 420); P.c("tune_r_gl", "tune_gl"); P.c("tune_gl", "tune_dcs")
    P.obj("tune_r_sw", "r sw_tune", 360, 400)
    P.msg("tune_run", f"run {BS}$1", 360, 420); P.c("tune_r_sw", "tune_run"); P.c("tune_run", "tune_dcs")
    P.obj("tune_r_key", "r music_key", 440, 400)
    P.msg("tune_key", f"key {BS}$1", 440, 420); P.c("tune_r_key", "tune_key"); P.c("tune_key", "tune_dcs")
    P.obj("tune_r_sc", "r music_scale", 520, 400)
    tsel = P.obj("tune_sc_sel", "sel " + " ".join(str(i) for i in range(len(SCALES))), 520, 420)
    P.c("tune_r_sc", tsel)
    for i, iv in enumerate(SCALES):
        P.msg(f"tune_sc{i}", "scale " + " ".join(str(v) for v in iv), 520 + 40 * i, 440)
        P.c(tsel, f"tune_sc{i}", i, 0); P.c(f"tune_sc{i}", "tune_dcs")
    P.obj("tune_on", "> 0", 200, 450); P.c("tune_r_amt", "tune_on")
    P.obj("tune_pk", "pack f 30", 200, 465); P.c("tune_on", "tune_pk"); P.c("r_smooth", "tune_pk", 0, 1)
    P.obj("tune_xf", "line~", 200, 480); P.c("tune_pk", "tune_xf")
    P.sig2("tune_wet", "*~", "tune_dcs", "tune_xf", 120, 495)
    P.obj("tune_keep0", "-~ 1", 200, 495); P.c("tune_xf", "tune_keep0")
    P.obj("tune_keep", "*~ -1", 200, 505); P.c("tune_keep0", "tune_keep")     # 1 - on
    P.sig2("tune_dry", "*~", "mic_comp", "tune_keep", 20, 495)
    P.sig2("mic_tuned", "+~", "tune_dry", "tune_wet", 20, 510)

    # --- vocoder (Oct 18): dcs_vocoder~ lays the voice's words onto a carrier: the music's chords (the pad's notes,
    # sent as cnote from the music; before any, the key's triad), a synth on your own pitch (snapped to the key by
    # voc_snap), noise, or the melody (each chime's note, sent as mnote). Style 1 (Oct 19) is a talk box: the mouth's
    # shape filters the synth instead of the bands. No delay; passes the voice through untouched at amount 0.
    fx(P, "voc_out", "dcs_vocoder~", "mic_tuned", ("voc_mix", "mix"), ("voc_on", "on"), ("voc_carrier", "carrier"), ("voc_wave", "wave"),
       ("voc_style", "style"),
       ("voc_sib", "sib"), ("voc_octave", "octave"), ("voc_snap", "snap"), x=120, y=560)
    P.obj("voc_ctl", "r voc_ctl", 600, 520); P.c("voc_ctl", "voc_out")
    P.obj("voc_r_key", "r music_key", 680, 520)
    P.msg("voc_key", f"key {BS}$1", 680, 540); P.c("voc_r_key", "voc_key"); P.c("voc_key", "voc_out")
    P.obj("voc_r_sc", "r music_scale", 760, 520)
    vsel = P.obj("voc_sc_sel", "sel " + " ".join(str(i) for i in range(len(SCALES))), 760, 540)
    P.c("voc_r_sc", vsel)
    for i, iv in enumerate(SCALES):
        P.msg(f"voc_sc{i}", "scale " + " ".join(str(v) for v in iv), 760 + 40 * i, 560)
        P.c(vsel, f"voc_sc{i}", i, 0); P.c(f"voc_sc{i}", "voc_out")

    # --- ring modulator with bypass ---
    if LEGACY_FX:
        P.obj("r_ring_freq", "r ring_freq", 250, 20)
        P.obj("osc", "osc~ 80", 250, 50)
        P.c("r_ring_freq", "osc")
        mix = smoothed(P, "ring_mix", "ring_mix", 340, 20)
        on = smoothed(P, "ring_on", "ring_on", 450, 20)
        P.sig2("mix_eff", "*~", mix, on, 340, 100)            # amount, forced to 0 when bypassed
        P.sig2("osc_amt", "*~", "osc", "mix_eff", 250, 140)
        P.obj("mix_minus1", "-~ 1", 340, 140)
        P.c("mix_eff", "mix_minus1")
        P.obj("one_minus_mix", "*~ -1", 340, 170)             # 1 - amount
        P.c("mix_minus1", "one_minus_mix")
        P.sig2("carrier", "+~", "osc_amt", "one_minus_mix", 250, 200)
        P.sig2("ring_out", "*~", "voc_out", "carrier", 20, 230)   # amount 0 -> carrier is 1 -> unchanged
    else:   # dcs_ring~ (Oct 17): the same, sample for sample, and nothing to compute while the amount is 0
        fx(P, "ring_out", "dcs_ring~ 80", "voc_out", ("ring_freq", "freq"), ("ring_mix", "mix"), ("ring_on", "on"),
           x=250, y=50)

    # --- pitch shifter: two delay taps sweeping through a buffer, crossfaded with Hann windows ---
    # Reading a delay line whose delay changes at rate (1 - ratio) shifts pitch by 'ratio'.
    # The phasor rate (Hz) is (1 - ratio) * 1000 / window_ms; two taps half a cycle apart
    # with windows that sum to 1 hide the wrap-around.
    P.obj("r_pitch_semi", "r pitch_semi", 20, 280)
    P.obj("semi_to_ln", "* 0.0577623", 20, 310)                # semitones * ln(2)/12
    P.obj("ratio", "exp", 20, 340)                              # pitch ratio = 2^(semi/12)
    P.obj("ratio_m1", "- 1", 20, 370)
    P.obj("one_minus_ratio", "* -1", 20, 400)                   # 1 - ratio
    P.c("r_pitch_semi", "semi_to_ln"); P.c("semi_to_ln", "ratio"); P.c("ratio", "ratio_m1")
    P.c("ratio_m1", "one_minus_ratio")
    P.obj("r_pitch_window", "r pitch_window", 150, 280)         # grain/window length in ms
    P.obj("win_inv", "pow -1", 250, 310)                        # 1 / window
    P.obj("win_inv_k", "* 1000", 250, 340)                      # 1000 / window_ms
    P.c("r_pitch_window", "win_inv"); P.c("win_inv", "win_inv_k")
    if LEGACY_FX:
        P.obj("pk_rate_a", "pack f 30", 20, 430)
        P.obj("ln_rate_a", "line~", 20, 460)
        P.c("one_minus_ratio", "pk_rate_a"); P.c("pk_rate_a", "ln_rate_a")
        P.c("r_smooth", "pk_rate_a", 0, 1)
        P.obj("pk_win", "pack f 30", 150, 310)
        P.obj("ln_win", "line~", 150, 340)                          # window length, smoothed
        P.c("r_pitch_window", "pk_win"); P.c("pk_win", "ln_win"); P.c("r_smooth", "pk_win", 0, 1)
        P.obj("pk_rate_b", "pack f 30", 250, 370)
        P.obj("ln_rate_b", "line~", 250, 400)
        P.c("win_inv_k", "pk_rate_b")
        P.c("pk_rate_b", "ln_rate_b"); P.c("r_smooth", "pk_rate_b", 0, 1)

        P.sig2("rate_hz", "*~", "ln_rate_a", "ln_rate_b", 150, 460)
        P.obj("ph1", "phasor~", 150, 490)
        P.c("rate_hz", "ph1")
        P.obj("ph2_off", "+~ 0.5", 250, 490)
        P.obj("ph2", "wrap~", 250, 520)
        P.c("ph1", "ph2_off"); P.c("ph2_off", "ph2")

        P.obj("delwrite_p", "delwrite~ pitch_buf 400", 20, 500)
        P.c("ring_out", "delwrite_p")
        for n, ph, x in (("1", "ph1", 150), ("2", "ph2", 250)):
            P.sig2(f"tap{n}_ms", "*~", ph, "ln_win", x, 560)
            P.obj(f"tap{n}_off", "+~ 4", x, 590)                    # keep clear of the write head
            P.obj(f"rd{n}", "delread4~ pitch_buf", x, 620)
            P.obj(f"cos{n}", "cos~", x + 90, 560)
            P.obj(f"cos{n}_neg", "*~ -0.5", x + 90, 590)
            P.obj(f"win{n}", "+~ 0.5", x + 90, 620)                 # Hann: 0.5 - 0.5*cos(2*pi*phase)
            P.c(f"tap{n}_ms", f"tap{n}_off"); P.c(f"tap{n}_off", f"rd{n}")
            P.c(ph, f"cos{n}"); P.c(f"cos{n}", f"cos{n}_neg"); P.c(f"cos{n}_neg", f"win{n}")
            P.sig2(f"grain{n}", "*~", f"rd{n}", f"win{n}", x, 650)
        P.sig2("shifted", "+~", "grain1", "grain2", 200, 680)
    else:   # dcs_taps~ (Oct 17): the same two taps, sample for sample; stopped with the pitch switch
        fx(P, "shifted", "dcs_taps~ 400", "ring_out", ("sw_pitch", "run"), x=200, y=680)
        for name, sel, src in (("ra", "ra", "one_minus_ratio"), ("rb", "rb", "win_inv_k"), ("win", "win", "r_pitch_window")):
            P.msg(f"pt_{name}", f"{sel} {BS}$1", 200 + 60 * len(name), 640); P.c(src, f"pt_{name}"); P.c(f"pt_{name}", "shifted")

    pm = smoothed(P, "pitch_mix", "pitch_mix", 350, 280)
    pon = smoothed(P, "pitch_on", "pitch_on", 450, 280)
    P.sig2("pmix_eff", "*~", pm, pon, 350, 360)                 # amount, forced to 0 when bypassed
    # Pitch shifter (`shift_voice`): New = dcs_shift~ (keeps a voice's tone colour; Pitch grain size is unused then)
    P.obj("pshift_dcs", "dcs_shift~ 0", 300, 640); P.c("ring_out", "pshift_dcs")
    P.msg("pshift_semi", f"semi {BS}$1", 300, 620); P.c("r_pitch_semi", "pshift_semi"); P.c("pshift_semi", "pshift_dcs")
    P.obj("pshift_sv_r", "r shift_voice", 420, 600)
    P.obj("pshift_sv", "expr 1-$f1 ; $f1".replace("$", BS + "$").replace(" ; ", " " + BS + "; "), 420, 620)
    P.c("pshift_sv_r", "pshift_sv")
    P.obj("shifted_c", "*~ 1", 200, 690); P.obj("shifted_n", "*~ 0", 300, 690)
    P.c("shifted", "shifted_c"); P.c("pshift_dcs", "shifted_n"); P.c("pshift_sv", "shifted_c", 0, 1); P.c("pshift_sv", "shifted_n", 1, 1)
    P.sig2("shifted_sel", "+~", "shifted_c", "shifted_n", 200, 695)
    P.sig2("shifted_amt", "*~", "shifted_sel", "pmix_eff", 350, 700)
    P.obj("pmix_minus1", "-~ 1", 450, 360)
    P.c("pmix_eff", "pmix_minus1")
    P.obj("pmix_keep", "*~ -1", 450, 390)                       # 1 - amount
    P.c("pmix_minus1", "pmix_keep")
    P.sig2("kept_amt", "*~", "ring_out", "pmix_keep", 450, 700)
    P.sig2("pitched", "+~", "kept_amt", "shifted_amt", 400, 740)

    # --- bit crusher: sample-rate reduction (sample & hold), then bit-depth reduction ---
    if LEGACY_FX:
        # A phasor at the target rate clocks a sample-and-hold: [samphold~] grabs a new input
        # value each time the phasor wraps, so the signal only changes 'crush_rate' times a second.
        # Bit depth rounds to the nearest of 2^(bits-1) steps per polarity, using
        # round(x) = (x + 0.5) - wrap(x + 0.5).
        P.obj("crush_in", "clip~ -1 1", 700, 560)                   # bit depth assumes -1..1
        P.c("pitched", "crush_in")
        crate = smoothed(P, "crush_rate", "crush_rate", 800, 560)
        P.obj("crush_clock", "phasor~", 800, 650)
        P.c(crate, "crush_clock")
        P.obj("crush_hold", "samphold~", 700, 690)
        P.c("crush_in", "crush_hold", 0, 0)
        P.c("crush_clock", "crush_hold", 0, 1)

        P.obj("r_crush_bits", "r crush_bits", 900, 560)
        P.obj("bits_m1", "- 1", 900, 590)
        P.obj("bits_ln", "* 0.693147", 900, 620)                     # times ln(2)
        P.obj("levels", "exp", 900, 650)                             # levels = 2^(bits-1)
        P.obj("pk_levels", "pack f 30", 900, 680)
        P.obj("ln_levels", "line~", 900, 710)
        P.c("r_crush_bits", "bits_m1"); P.c("bits_m1", "bits_ln"); P.c("bits_ln", "levels")
        P.c("levels", "pk_levels"); P.c("pk_levels", "ln_levels"); P.c("r_smooth", "pk_levels", 0, 1)

        P.sig2("scaled", "*~", "crush_hold", "ln_levels", 700, 740)
        P.obj("scaled_half", "+~ 0.5", 700, 770)
        P.c("scaled", "scaled_half")
        P.obj("scaled_frac", "wrap~", 800, 770)
        P.c("scaled_half", "scaled_frac")
        P.sig2("rounded", "-~", "scaled_half", "scaled_frac", 700, 800)
        P.sig2("crushed", "/~", "rounded", "ln_levels", 700, 830)

        cmix = smoothed(P, "crush_mix", "crush_mix", 1000, 560)
        con = smoothed(P, "crush_on", "crush_on", 1100, 560)
        P.sig2("cmix_eff", "*~", cmix, con, 1000, 650)               # amount, forced to 0 when bypassed
        P.sig2("crush_diff", "-~", "crushed", "pitched", 700, 860)
        P.sig2("crush_amt", "*~", "crush_diff", "cmix_eff", 700, 890)
        P.sig2("crushed_mix", "+~", "pitched", "crush_amt", 700, 920)
    else:   # dcs_crush~ (Oct 17): Classic the same, sample for sample, nothing but its clock while the amount is 0;
            # Style picks a machine's own sound (8-bit sampler, SP-1200, NES, Game Boy, SNES, Genesis)
        fx(P, "crushed_mix", "dcs_crush~", "pitched", ("crush_rate", "rate"), ("crush_bits", "bits"),
           ("crush_mix", "mix"), ("crush_on", "on"), ("crush_style", "style"), x=700, y=600)

    # --- distortion: drive the signal into a clipper (three shapes), crossfaded with the clean sound ---
    # Each shape has a small-signal gain of 1, so drive 1 is close to clean and louder only gets
    # grittier. soft = x / (1 + |x|) (never quite reaches 1, so it stays smooth at any drive),
    # hard = x limited to +-1, fold = sin(drive * x * pi/2) * 2/pi (a cosine lookup shifted a
    # quarter cycle).
    if LEGACY_FX:
        drive = smoothed(P, "dist_drive", "dist_drive", 1000, 1000)
        P.sig2("dist_u", "*~", "crushed_mix", drive, 700, 1000)
        P.obj("dist_hard", "clip~ -1 1", 700, 1030)
        P.c("dist_u", "dist_hard")
        P.obj("dist_abs", "abs~", 800, 1030)
        P.obj("dist_den", "+~ 1", 800, 1060)
        P.c("dist_u", "dist_abs"); P.c("dist_abs", "dist_den")
        P.sig2("dist_soft", "/~", "dist_u", "dist_den", 800, 1090)
        P.obj("dist_f1", "*~ 0.25", 900, 1030)
        P.obj("dist_f2", "-~ 0.25", 900, 1060)
        P.obj("dist_f3", "wrap~", 900, 1090)
        P.obj("dist_f4", "cos~", 900, 1120)
        P.obj("dist_fold", "*~ 0.63662", 900, 1150)
        P.c("dist_u", "dist_f1"); P.c("dist_f1", "dist_f2"); P.c("dist_f2", "dist_f3")
        P.c("dist_f3", "dist_f4"); P.c("dist_f4", "dist_fold")
        # shape menu -> three smoothed weights (one is 1, the others 0)
        P.obj("r_dist_type", "r dist_type", 1000, 1030)
        P.obj("dist_sel", "sel 0 1 2", 1000, 1060)
        P.msg("dist_m0", "1 0 0", 1000, 1090); P.msg("dist_m1", "0 1 0", 1060, 1090); P.msg("dist_m2", "0 0 1", 1120, 1090)
        P.obj("dist_unpack", "unpack f f f", 1000, 1120)
        P.c("r_dist_type", "dist_sel")
        for k in range(3):
            P.c("dist_sel", f"dist_m{k}", k)
            P.c(f"dist_m{k}", "dist_unpack")
        shapes = []
        for k, src in enumerate(("dist_soft", "dist_hard", "dist_fold")):
            P.obj(f"dist_pk{k}", "pack f 30", 1000 + 100 * k, 1150)
            P.obj(f"dist_ln{k}", "line~", 1000 + 100 * k, 1180)
            P.c("dist_unpack", f"dist_pk{k}", k)
            P.c(f"dist_pk{k}", f"dist_ln{k}")
            P.c("r_smooth", f"dist_pk{k}", 0, 1)
            shapes.append(P.sig2(f"dist_w{k}", "*~", src, f"dist_ln{k}", 1000 + 100 * k, 1210))
        P.sig2("dist_s01", "+~", shapes[0], shapes[1], 1000, 1240)
        P.sig2("dist_shaped", "+~", "dist_s01", shapes[2], 1000, 1270)
        dmix = smoothed(P, "dist_mix", "dist_mix", 1300, 1000)
        don = smoothed(P, "dist_on", "dist_on", 1400, 1000)
        P.sig2("dmix_eff", "*~", dmix, don, 1300, 1060)              # amount, forced to 0 when bypassed
        P.sig2("dist_diff", "-~", "dist_shaped", "crushed_mix", 1000, 1300)
        P.sig2("dist_amt", "*~", "dist_diff", "dmix_eff", 1000, 1330)
        P.sig2("dist_out", "+~", "crushed_mix", "dist_amt", 1000, 1360)
    else:   # dcs_dist~ (Oct 17): the same, sample for sample, and nothing to compute while the amount is 0
        fx(P, "dist_out", "dcs_dist~", "crushed_mix", ("dist_drive", "drive"), ("dist_type", "type"),
           ("dist_mix", "mix"), ("dist_on", "on"), ("dist_quality", "quality"), x=700, y=1000)

    # --- filter: one biquad (low-pass / high-pass / band-pass) with coefficients from [expr] ---
    # Standard "audio EQ cookbook" formulas. Frequency changes glide (via [line]); the sample
    # rate comes from [samplerate~]. The max()/min() guards keep the maths finite before the
    # first values arrive, because a NaN would lock the filter up.
    P.obj("loadbang_sr", "loadbang", 2150, 20)                # the sample rate (other parts use it too)
    P.obj("sr", "samplerate~", 2150, 50)
    P.c("loadbang_sr", "sr")
    if LEGACY_FX:
        S = "max($f4\\, 8000)"
        W = "6.283185*min(max(exp($f1)\\, 20)\\, 0.45*%s)/%s" % (S, S)   # $f1 is ln(frequency)
        A = "sin(%s)/(2*max($f2\\, 0.1))" % W
        C = "cos(%s)" % W
        a0 = "(1+%s)" % A
        lp, hp, bp = "($f3<0.5)", "(($f3>0.5)*($f3<1.5))", "($f3>1.5)"
        coefs = [
            "2*%s/%s" % (C, a0),                                                      # feedback 1
            "-(1-%s)/%s" % (A, a0),                                                   # feedback 2
            "(%s*(1-%s)/2+%s*(1+%s)/2+%s*%s)/%s" % (lp, C, hp, C, bp, A, a0),         # feedforward 1
            "(%s*(1-%s)-%s*(1+%s))/%s" % (lp, C, hp, C, a0),                          # feedforward 2
            "(%s*(1-%s)/2+%s*(1+%s)/2-%s*%s)/%s" % (lp, C, hp, C, bp, A, a0),         # feedforward 3
        ]
        P.obj("coef", "expr " + (" " + BS + "; ").join(coefs).replace("$", BS + "$"), 1600, 20)
        P.obj("coef_pack", "pack f f f f f", 1600, 70)
        for i in range(5):
            P.c("coef", "coef_pack", i, i)
        P.obj("filt", "biquad~", 1600, 100)
        P.c("coef_pack", "filt")
        P.c("dist_out", "filt")

        P.obj("r_filter_freq", "r filter_freq", 1750, 20)
        P.obj("filter_freq_log", "log", 1750, 35)                    # glide in log frequency: equal-sounding steps
        P.obj("pk_filter_freq", "pack f 30", 1750, 50)
        P.obj("ln_filter_freq", "line 0 3", 1750, 80)                # control-rate glide in 3 ms steps
        P.c("r_filter_freq", "filter_freq_log"); P.c("filter_freq_log", "pk_filter_freq")
        P.c("pk_filter_freq", "ln_filter_freq")
        P.c("r_smooth", "pk_filter_freq", 0, 1)
        P.c("ln_filter_freq", "coef", 0, 0)
        for name, inlet in (("filter_q", 1), ("filter_type", 2)):
            P.obj(f"r_{name}", f"r {name}", 1850 + 100 * inlet, 20)
            P.obj(f"t_{name}", "t b f", 1850 + 100 * inlet, 50)
            P.c(f"r_{name}", f"t_{name}")
            P.c(f"t_{name}", "coef", 1, inlet)                      # value into the cold inlet...
            P.c(f"t_{name}", "coef", 0, 0)                          # ...then re-evaluate
        P.obj("t_sr", "t b f", 2150, 80)
        P.c("sr", "t_sr")
        P.c("t_sr", "coef", 1, 3); P.c("t_sr", "coef", 0, 0)

        fmix = smoothed(P, "filter_mix", "filter_mix", 1600, 160)
        fon = smoothed(P, "filter_on", "filter_on", 1700, 160)
        P.sig2("fmix_eff", "*~", fmix, fon, 1600, 220)               # amount, forced to 0 when bypassed
        P.sig2("filt_diff", "-~", "filt", "dist_out", 1600, 250)
        P.sig2("filt_amt", "*~", "filt_diff", "fmix_eff", 1600, 280)
        P.sig2("filtered_pre", "+~", "dist_out", "filt_amt", 1600, 310)
    else:   # dcs_filter~ (Oct 17): the same biquad, coefficients and 3 ms glide steps, sample for sample
        fx(P, "filtered_pre", "dcs_filter~", "dist_out", ("filter_freq", "freq"), ("filter_q", "q"),
           ("filter_type", "type"), ("filter_mix", "mix"), ("filter_on", "on"), x=1600, y=100)

    # The music joins here, after the tone-changing effects (so pitch shifter and crusher cannot push it
    # out of key) but before chorus, flanger, phaser, tremolo, echo, reverb and the spatial stage.
    # The Dry level scales the mic here, before the music joins, so the music keeps its own level whatever Dry is
    # (DadCraft: lowering Dry used to silence the music too). Everything from here to "voice" (chorus, flanger, phaser,
    # tremolo, reverse echo, freeze) is linear, so the mic sounds the same as when Dry scaled the end of the chain.
    dry = smoothed(P, "dry", "dry", 1500, 300)
    P.sig2("filtered_dry", "*~", "filtered_pre", dry, 1600, 325)
    music = add_music(P)
    P.sig2("filtered_mix", "+~", "filtered_dry", music, 1600, 340)

    # --- chorus: the signal mixed with two copies whose delay wobbles, which detunes them slightly ---
    if LEGACY_FX:
        P.obj("chorus_wr", "delwrite~ chorus_buf 60", 2300, 20)
        P.c("filtered_mix", "chorus_wr")
        crate = smoothed(P, "chorus_rate", "chorus_rate", 2450, 20)
        P.obj("chorus_ph", "phasor~", 2450, 110)
        P.c(crate, "chorus_ph")
        cdepth = smoothed(P, "chorus_depth", "chorus_depth", 2600, 20)
        for k, (base, offset) in enumerate(zip(CHORUS_BASE_MS, (0, 0.25))):
            n = f"ch{k}"
            P.obj(f"{n}_off", f"+~ {offset}", 2300 + 150 * k, 150)   # quarter-cycle apart
            P.obj(f"{n}_wrap", "wrap~", 2300 + 150 * k, 180)
            P.obj(f"{n}_lfo", "cos~", 2300 + 150 * k, 210)
            P.sig2(f"{n}_sweep", "*~", f"{n}_lfo", cdepth, 2300 + 150 * k, 240)   # +-depth ms
            P.obj(f"{n}_ms", f"+~ {base}", 2300 + 150 * k, 270)
            P.obj(f"{n}_rd", "delread4~ chorus_buf", 2300 + 150 * k, 300)
            P.c("chorus_ph", f"{n}_off"); P.c(f"{n}_off", f"{n}_wrap"); P.c(f"{n}_wrap", f"{n}_lfo")
            P.c(f"{n}_sweep", f"{n}_ms"); P.c(f"{n}_ms", f"{n}_rd")
        P.sig2("ch_sum", "+~", "ch0_rd", "ch1_rd", 2300, 340)
        P.obj("ch_wet", "*~ 0.5", 2300, 370)
        P.c("ch_sum", "ch_wet")
        cmix = smoothed(P, "chorus_mix", "chorus_mix", 2750, 20)
        con = smoothed(P, "chorus_on", "chorus_on", 2850, 20)
        P.sig2("cho_eff", "*~", cmix, con, 2750, 110)                # amount, forced to 0 when bypassed
        P.sig2("cho_diff", "-~", "ch_wet", "filtered_mix", 2300, 400)
        P.sig2("cho_amt", "*~", "cho_diff", "cho_eff", 2300, 430)
        P.sig2("chorused", "+~", "filtered_mix", "cho_amt", 2300, 460)
    else:   # dcs_chorus~ (Oct 17): the same, sample for sample; stopped with the chorus switch
        fx(P, "chorused", f"dcs_chorus~ {CHORUS_BASE_MS[0]} {CHORUS_BASE_MS[1]} 60", "filtered_mix",
           ("chorus_rate", "rate"), ("chorus_depth", "depth"), ("chorus_mix", "mix"), ("chorus_on", "on"),
           ("sw_chorus", "run"), x=2300, y=460)

    # --- flanger: a short delay swept slowly, with feedback, mixed 50/50 with the original at amount 1 ---
    if LEGACY_FX:
        P.obj("fl_wr", "delwrite~ flange_buf 30", 2300, 1000)
        frate = smoothed(P, "flange_rate", "flange_rate", 2450, 1000)
        P.obj("fl_ph", "phasor~", 2450, 1090)
        P.c(frate, "fl_ph")
        fdepth = smoothed(P, "flange_depth", "flange_depth", 2600, 1000)
        P.obj("fl_cos", "cos~", 2450, 1120)
        P.obj("fl_half", "*~ -0.5", 2450, 1150)
        P.obj("fl_unit", "+~ 0.5", 2450, 1180)                       # (1 - cos) / 2, from 0 to 1
        P.sig2("fl_sweep", "*~", "fl_unit", fdepth, 2450, 1210)      # 0 .. depth ms
        P.obj("fl_ms", f"+~ {FLANGE_MIN_MS}", 2450, 1240)
        P.obj("fl_rd", "delread4~ flange_buf", 2450, 1270)
        P.c("fl_ph", "fl_cos"); P.c("fl_cos", "fl_half"); P.c("fl_half", "fl_unit")
        P.c("fl_sweep", "fl_ms"); P.c("fl_ms", "fl_rd")
        ffb = smoothed(P, "flange_fb", "flange_fb", 2750, 1000)
        P.sig2("fl_fb_sig", "*~", "fl_rd", ffb, 2700, 1300)
        P.sig2("fl_in", "+~", "chorused", "fl_fb_sig", 2300, 1330)
        P.c("fl_in", "fl_wr")
        # resonant peaks grow as 1 / (1 - |feedback|); take part of that back out
        P.obj("fl_abs", "abs", 2750, 1050)
        P.obj("fl_k", "* -0.5", 2750, 1080)
        P.obj("fl_k1", "+ 1", 2750, 1110)
        P.obj("pk_fl_k", "pack f 30", 2750, 1140); P.obj("ln_fl_k", "line~", 2750, 1170)
        P.c("r_flange_fb", "fl_abs"); P.c("fl_abs", "fl_k"); P.c("fl_k", "fl_k1"); P.c("fl_k1", "pk_fl_k")
        P.c("pk_fl_k", "ln_fl_k"); P.c("r_smooth", "pk_fl_k", 0, 1)
        P.sig2("fl_wet", "*~", "fl_rd", "ln_fl_k", 2450, 1360)
        fmx = smoothed(P, "flange_mix", "flange_mix", 2900, 1000)
        fon2 = smoothed(P, "flange_on", "flange_on", 3000, 1000)
        P.sig2("fl_eff", "*~", fmx, fon2, 2900, 1060)                # amount, forced to 0 when bypassed
        P.obj("fl_wet_k", "*~ 0.5", 2900, 1090)                      # wet share: 0 .. 0.5
        P.c("fl_eff", "fl_wet_k")
        P.obj("fl_dry_k0", "*~ -1", 3000, 1090)
        P.obj("fl_dry_k", "+~ 1", 3000, 1120)                        # dry share: 1 .. 0.5
        P.c("fl_wet_k", "fl_dry_k0"); P.c("fl_dry_k0", "fl_dry_k")
        P.sig2("fl_wet_part", "*~", "fl_wet", "fl_wet_k", 2450, 1390)
        P.sig2("fl_dry_part", "*~", "chorused", "fl_dry_k", 2800, 1390)
        P.sig2("flanged", "+~", "fl_wet_part", "fl_dry_part", 2450, 1420)
    else:   # dcs_flanger~ (Oct 17): the same, sample for sample; stopped with the flanger switch
        fx(P, "flanged", f"dcs_flanger~ {FLANGE_MIN_MS} 30", "chorused", ("flange_rate", "rate"), ("flange_depth", "depth"),
           ("flange_fb", "fb"), ("flange_mix", "mix"), ("flange_on", "on"), ("sw_flanger", "run"), x=2450, y=1420)

    # --- phaser: four swept allpass filters (biquads), mixed 50/50 with the original at amount 1 ---
    # A control-rate LFO (every 4 ms) sets the sweep frequency; each stage computes its own
    # allpass coefficients from it with [expr] (audio-EQ-cookbook formulas).
    if LEGACY_FX:
        P.obj("ph_start", "loadbang", 3300, 1000)
        P.obj("ph_clock", "metro 4", 3300, 1030)
        P.obj("ph_snap", "snapshot~", 3300, 1060)
        P.c("ph_start", "ph_clock"); P.c("ph_clock", "ph_snap")
        prate = smoothed(P, "phaser_rate", "phaser_rate", 3450, 1000)
        P.obj("ph_osc", "osc~", 3450, 1090)
        P.c(prate, "ph_osc"); P.c("ph_osc", "ph_snap")
        P.obj("r_phaser_depth", "r phaser_depth", 3600, 1000)
        P.obj("ph_sweep", ("expr %d*pow(2%s 2*$f1*$f2)" % (PHASER_CENTRE_HZ, BS + ",")).replace("$", BS + "$"), 3300, 1120)
        P.c("ph_snap", "ph_sweep"); P.c("r_phaser_depth", "ph_sweep", 0, 1)
        P.obj("ph_sr_start", "loadbang", 3750, 1000)
        P.obj("ph_sr", "samplerate~", 3750, 1030)
        P.c("ph_sr_start", "ph_sr")
        S2 = "max($f2" + BS + ", 8000)"
        pfb = smoothed(P, "phaser_fb", "phaser_fb", 3900, 1030)
        P.obj("ph_fb_rd", "delread~ ph_fb 1.4", 3300, 1250)
        P.sig2("ph_fb_sig", "*~", "ph_fb_rd", pfb, 3300, 1280)
        for k, mult in enumerate(PHASER_STAGES):
            W2 = "6.283185*min(max($f1*%s%s 20)%s 0.45*%s)/%s" % (mult, BS + ",", BS + ",", S2, S2)
            A2 = "sin(%s)/(2*%s)" % (W2, PHASER_Q)
            C2 = "cos(%s)" % W2
            a02 = "(1+%s)" % A2
            coefs2 = ["2*%s/%s" % (C2, a02), "-(1-%s)/%s" % (A2, a02),
                      "(1-%s)/%s" % (A2, a02), "-2*%s/%s" % (C2, a02), "(1+%s)/%s" % (A2, a02)]
            P.obj(f"ph_coef{k}", ("expr " + (" " + BS + "; ").join(coefs2)).replace("$", BS + "$"), 3300 + 150 * k, 1160)
            P.obj(f"ph_pack{k}", "pack f f f f f", 3300 + 150 * k, 1190)
            P.obj(f"ph_ap{k}", "biquad~", 3300 + 150 * k, 1220)
            for i in range(5):
                P.c(f"ph_coef{k}", f"ph_pack{k}", i, i)
            P.c(f"ph_pack{k}", f"ph_ap{k}")
            P.c("ph_sweep", f"ph_coef{k}")
            P.c("ph_sr", f"ph_coef{k}", 0, 1)
        P.sig2("ph_in", "+~", "flanged", "ph_fb_sig", 3300, 1310)
        prev = "ph_in"
        for k in range(len(PHASER_STAGES)):
            P.c(prev, f"ph_ap{k}")
            prev = f"ph_ap{k}"
        P.obj("ph_fb_wr", "delwrite~ ph_fb 20", 3600, 1310)
        P.c(prev, "ph_fb_wr")
        pmx = smoothed(P, "phaser_mix", "phaser_mix", 4100, 1000)
        pon2 = smoothed(P, "phaser_on", "phaser_on", 4200, 1000)
        P.sig2("ph_eff", "*~", pmx, pon2, 4100, 1060)                # amount, forced to 0 when bypassed
        P.obj("ph_wet_k", "*~ 0.5", 4100, 1090)
        P.c("ph_eff", "ph_wet_k")
        P.obj("ph_dry_k0", "*~ -1", 4200, 1090)
        P.obj("ph_dry_k", "+~ 1", 4200, 1120)
        P.c("ph_wet_k", "ph_dry_k0"); P.c("ph_dry_k0", "ph_dry_k")
        P.sig2("ph_wet_part", "*~", prev, "ph_wet_k", 3300, 1400)
        P.sig2("ph_dry_part", "*~", "flanged", "ph_dry_k", 3700, 1400)
        P.sig2("phased", "+~", "ph_wet_part", "ph_dry_part", 3300, 1430)
    else:   # dcs_phaser~ (Oct 17): the same sweep steps and stages, sample for sample; stopped with the phaser switch
        fx(P, "phased", f"dcs_phaser~ {PHASER_CENTRE_HZ} {PHASER_Q} 1.4 " + " ".join(str(m) for m in PHASER_STAGES),
           "flanged", ("phaser_rate", "rate"), ("phaser_depth", "depth"), ("phaser_fb", "fb"), ("phaser_mix", "mix"),
           ("phaser_on", "on"), ("sw_phaser", "run"), x=3300, y=1430)

    # --- tremolo: the level swings between full and (1 - depth) at the LFO rate ---
    if LEGACY_FX:
        trate = smoothed(P, "trem_rate", "trem_rate", 2950, 20)
        P.obj("trem_osc", "osc~", 2950, 110)
        P.c(trate, "trem_osc")
        tdepth = smoothed(P, "trem_depth", "trem_depth", 3100, 20)
        ton = smoothed(P, "trem_on", "trem_on", 3200, 20)
        P.sig2("trem_depth_eff", "*~", tdepth, ton, 3100, 110)       # depth, forced to 0 when bypassed
        P.obj("trem_half", "*~ -0.5", 2950, 150)
        P.obj("trem_unit", "+~ 0.5", 2950, 180)                      # (1 - sine) / 2, from 0 to 1
        P.c("trem_osc", "trem_half"); P.c("trem_half", "trem_unit")
        P.sig2("trem_dip", "*~", "trem_unit", "trem_depth_eff", 2950, 210)
        P.obj("trem_neg", "*~ -1", 2950, 240)
        P.obj("trem_gain", "+~ 1", 2950, 270)                        # 1 - dip
        P.c("trem_dip", "trem_neg"); P.c("trem_neg", "trem_gain")
        P.sig2("trem_out", "*~", "phased", "trem_gain", 2950, 310)
    else:   # dcs_trem~ (Oct 17): the same, sample for sample, and nothing to compute while the depth is 0
        fx(P, "trem_out", "dcs_trem~", "phased", ("trem_rate", "rate"), ("trem_depth", "depth"), ("trem_on", "on"),
           x=2950, y=110)

    # --- reverse echo: plays the sound backwards in short chunks, mixed in behind the original ---
    # The same two-tap crossfading delay line as the pitch shifter, with playback speed -1: the
    # delay grows by 2 ms per ms, so each tap reads backwards. For chunks of C ms the buffer
    # window is W = 2C ms and the sweep runs at 1000/C Hz.
    P.obj("r_rvs_time", "r rvs_time", 3400, 20)
    P.obj("rvs_w", "* 2", 3400, 50)
    P.obj("rvs_inv", "pow -1", 3500, 50)
    P.obj("rvs_hz", "* 1000", 3500, 80)
    P.c("r_rvs_time", "rvs_w"); P.c("r_rvs_time", "rvs_inv"); P.c("rvs_inv", "rvs_hz")
    if LEGACY_FX:
        P.obj("pk_rvs_w", "pack f 30", 3400, 80); P.obj("ln_rvs_w", "line~", 3400, 110)
        P.c("rvs_w", "pk_rvs_w"); P.c("pk_rvs_w", "ln_rvs_w"); P.c("r_smooth", "pk_rvs_w", 0, 1)
        P.obj("pk_rvs_hz", "pack f 30", 3500, 110); P.obj("ln_rvs_hz", "line~", 3500, 140)
        P.c("rvs_hz", "pk_rvs_hz")
        P.c("pk_rvs_hz", "ln_rvs_hz"); P.c("r_smooth", "pk_rvs_hz", 0, 1)
        P.obj("rvs_ph1", "phasor~", 3500, 180)
        P.c("ln_rvs_hz", "rvs_ph1")
        P.obj("rvs_ph2_off", "+~ 0.5", 3600, 180); P.obj("rvs_ph2", "wrap~", 3600, 210)
        P.c("rvs_ph1", "rvs_ph2_off"); P.c("rvs_ph2_off", "rvs_ph2")
        P.obj("rvs_wr", "delwrite~ rvs_buf 2100", 3400, 250)
        P.c("trem_out", "rvs_wr")
        for n, ph, x in (("1", "rvs_ph1", 3400), ("2", "rvs_ph2", 3550)):
            P.sig2(f"rvs_tap{n}_ms", "*~", ph, "ln_rvs_w", x, 290)
            P.obj(f"rvs_tap{n}_off", "+~ 4", x, 320)
            P.obj(f"rvs_rd{n}", "delread4~ rvs_buf", x, 350)
            P.obj(f"rvs_cos{n}", "cos~", x + 80, 290)
            P.obj(f"rvs_cos{n}_neg", "*~ -0.5", x + 80, 320)
            P.obj(f"rvs_win{n}", "+~ 0.5", x + 80, 350)              # Hann window: 0.5 - 0.5*cos(2*pi*phase)
            P.c(f"rvs_tap{n}_ms", f"rvs_tap{n}_off"); P.c(f"rvs_tap{n}_off", f"rvs_rd{n}")
            P.c(ph, f"rvs_cos{n}"); P.c(f"rvs_cos{n}", f"rvs_cos{n}_neg"); P.c(f"rvs_cos{n}_neg", f"rvs_win{n}")
            P.sig2(f"rvs_grain{n}", "*~", f"rvs_rd{n}", f"rvs_win{n}", x, 380)
        P.sig2("rvs_sound", "+~", "rvs_grain1", "rvs_grain2", 3400, 410)
    else:   # dcs_taps~ (Oct 17): the same two taps, reading backwards, sample for sample; stopped with its switch
        fx(P, "rvs_sound", "dcs_taps~ 2100", "trem_out", ("sw_reverse", "run"), x=3400, y=410)
        P.msg("rvs_m_win", f"win {BS}$1", 3400, 380); P.c("rvs_w", "rvs_m_win"); P.c("rvs_m_win", "rvs_sound")
        P.msg("rvs_m_ra", f"ra {BS}$1", 3500, 380); P.c("rvs_hz", "rvs_m_ra"); P.c("rvs_m_ra", "rvs_sound")
    rmx = smoothed(P, "rvs_mix", "rvs_mix", 3700, 20)
    ron = smoothed(P, "rvs_on", "rvs_on", 3800, 20)
    P.sig2("rvs_eff", "*~", rmx, ron, 3700, 110)                 # level, forced to 0 when bypassed
    P.sig2("rvs_amt", "*~", "rvs_sound", "rvs_eff", 3400, 440)
    P.sig2("rvs_out", "+~", "trem_out", "rvs_amt", 3400, 470)

    # --- glitch / freeze: a feedback loop of 'glitch_len' ms. At hold = 1 the input is replaced by
    # the loop's own output, so the last glitch_len ms repeats until hold comes back down. ---
    if LEGACY_FX:
        hold = smoothed(P, "glitch_hold", "glitch_hold", 3900, 20)
        gon = smoothed(P, "glitch_on", "glitch_on", 4000, 20)
        P.sig2("hold_eff", "*~", hold, gon, 3900, 110)               # forced to 0 when bypassed
        P.obj("hold_inv", "*~ -1", 4000, 110)
        P.obj("live_gain", "+~ 1", 4000, 140)                        # 1 - hold
        P.c("hold_eff", "hold_inv"); P.c("hold_inv", "live_gain")
        glen = smoothed(P, "glitch_len", "glitch_len", 4100, 20)
        P.obj("glitch_rd", "delread4~ glitch_buf", 4100, 110)
        P.c(glen, "glitch_rd")
        P.sig2("glitch_live", "*~", "rvs_out", "live_gain", 3900, 200)
        P.sig2("glitch_loop", "*~", "glitch_rd", "hold_eff", 4100, 200)
        P.sig2("voice", "+~", "glitch_live", "glitch_loop", 4000, 260)   # what the rest of the chain hears
        P.obj("glitch_wr", "delwrite~ glitch_buf 600", 4000, 300)
        P.c("voice", "glitch_wr")
    else:   # dcs_freeze~ (Oct 17): the same loop, sample for sample; while hold is 0 it only writes the line
        fx(P, "voice", "dcs_freeze~ 600", "rvs_out", ("glitch_hold", "hold"), ("glitch_on", "on"), ("glitch_len", "len"),
           x=3900, y=200)

    # --- echo with feedback and bypass ---
    P.obj("r_echo_time", "r echo_time", 560, 20)
    P.obj("pk_echo_time", "pack f 100", 560, 50)
    P.obj("ln_echo_time", "line~", 560, 80)
    # While walking, the echo can lock to the step rhythm (echo_sync: 0 off, 1 = a step, 2 = half, 3 = quarter).
    # The step interval is averaged and moved in 20 ms jumps, so ordinary step-to-step wobble doesn't make the
    # echo warble; 2.5 s after the last step the slider's own time comes back.
    P.obj("ec_r_step", "r pulse", 700, 20)
    P.obj("ec_step_t", "t b f", 700, 50)
    P.c("ec_r_step", "ec_step_t")
    P.obj("ec_clip", "clip 0.25 1.2", 760, 80)
    ex = lambda n, t, x, y: P.obj(n, "expr " + t.replace(",", BS + ",").replace("$", BS + "$"), x, y)
    ex("ec_avg", "if($f2>0, $f1*0.3+$f2*0.7, $f1)", 760, 110)
    P.obj("ec_avg_t", "t f f", 760, 140)
    ex("ec_grid", "int($f1*50+0.5)/50", 760, 170)
    P.obj("ec_chg", "change", 760, 200)
    P.c("ec_step_t", "ec_clip", 1, 0); P.c("ec_clip", "ec_avg"); P.c("ec_avg", "ec_avg_t")
    P.c("ec_avg_t", "ec_avg", 1, 1); P.c("ec_avg_t", "ec_grid", 0, 0); P.c("ec_grid", "ec_chg")
    P.msg("ec_walk1", "1", 840, 80)
    P.obj("ec_walk_del", f"del {ECHO_WALK_HOLD_MS}", 900, 80)
    P.msg("ec_walk0", "0", 900, 110)
    P.c("ec_step_t", "ec_walk1", 0, 0); P.c("ec_step_t", "ec_walk_del", 0, 0); P.c("ec_walk_del", "ec_walk0")
    P.obj("ec_r_sync", "r echo_sync", 980, 20)
    # gather: slider time, step interval, sync choice, walking -> the echo time to use
    ex("ec_calc", "if($f3>0 && $f4>0.5, min($f2*1000/pow(2, $f3-1), 1900), $f1)", 560, 230)
    P.obj("ec_s0", "f", 560, 200)
    P.c("ec_s0", "ec_calc")
    for k, src in enumerate(("r_echo_time", "ec_chg", "ec_r_sync", None)):
        P.obj(f"ec_in{k}", "t b f", 600 + 60 * k, 170)
        if src:
            P.c(src, f"ec_in{k}")
        if k == 0:
            P.c(f"ec_in{k}", "ec_s0", 1, 1)
        else:
            P.c(f"ec_in{k}", "ec_calc", 1, k)
        P.c(f"ec_in{k}", "ec_s0", 0, 0)
    P.c("ec_walk1", "ec_in3"); P.c("ec_walk0", "ec_in3")
    P.c("ec_calc", "pk_echo_time")
    P.c("pk_echo_time", "ln_echo_time")
    P.c("smooth_x3", "pk_echo_time", 0, 1)
    # the echo hears the mic at full level whatever Dry is: the voice (at the Dry level) plus the part Dry took away
    P.obj("dry_rest_n", "*~ -1", 460, 230); P.c("ln_dry", "dry_rest_n")
    P.obj("dry_rest", "+~ 1", 460, 245); P.c("dry_rest_n", "dry_rest")
    P.sig2("echo_mic_rest", "*~", "filtered_pre", "dry_rest", 460, 260)
    if LEGACY_FX:
        P.obj("delread", "delread4~ echo_buf", 560, 110)
        P.c("ln_echo_time", "delread")
        fb = smoothed(P, "feedback", "feedback", 560, 150)
        P.sig2("fb_sig", "*~", "delread", fb, 560, 240)
        P.sig2("echo_in0", "+~", "voice", "echo_mic_rest", 560, 262)
        P.sig2("echo_in", "+~", "echo_in0", "fb_sig", 560, 270)
        P.obj("delwrite", "delwrite~ echo_buf 2000", 560, 300)
        P.c("echo_in", "delwrite")
        wet = smoothed(P, "wet", "wet", 700, 150)
        eon = smoothed(P, "echo_on", "echo_on", 800, 150)
        P.sig2("wet_eff", "*~", wet, eon, 700, 240)           # level, forced to 0 when bypassed
        P.sig2("wet_sig", "*~", "delread", "wet_eff", 700, 270)
    else:   # dcs_echo~ (Oct 17): the same line, feedback and level, sample for sample (the time still glides here)
        fx(P, "wet_sig", "dcs_echo~ 2000", "voice", ("feedback", "fb"), ("wet", "wet"), ("echo_on", "on"), x=560, y=270)
        P.c("echo_mic_rest", "wet_sig", 0, 1); P.c("ln_echo_time", "wet_sig", 0, 2)
    # Echo scatter: each repeat lands somewhere else between the ears. The echo comes back every T ms (the delay time),
    # so a new place is picked every T ms: each repeat of a sound falls in its own window. A new place is at least
    # half the width away from the last (else the other side), and moves there in 15 ms. 0 = in the middle as before.
    P.obj("es_lb", "loadbang", 900, 160)
    P.obj("es_tick", "t b b", 900, 190)
    P.obj("es_T", "snapshot~", 960, 220); P.c("ln_echo_time", "es_T")
    P.obj("es_Tc", "max 20", 960, 250)
    P.obj("es_del", "del 350", 900, 280)
    P.c("es_lb", "es_tick"); P.c("es_tick", "es_T", 0, 0); P.c("es_T", "es_Tc"); P.c("es_Tc", "es_del"); P.c("es_del", "es_tick")
    P.obj("es_rnd", "random 2001", 1040, 220)
    ex("es_pick", "if(abs(($f1/1000-1)-$f2)<0.5, -($f1/1000-1), $f1/1000-1)", 1040, 250)
    P.obj("es_prev", "t f f", 1040, 280)
    P.c("es_tick", "es_rnd", 1, 0); P.c("es_rnd", "es_pick"); P.c("es_pick", "es_prev"); P.c("es_prev", "es_pick", 1, 1)
    P.obj("es_r_amt", "r echo_scatter", 1160, 220)
    ex("es_gains", "min(1,1-$f1*$f2) ; min(1,1+$f1*$f2)".replace(" ; ", " " + BS + "; "), 1040, 310)
    P.c("es_prev", "es_gains", 0, 0); P.c("es_r_amt", "es_gains", 0, 1)
    for k, side in enumerate(("l", "r")):
        P.obj(f"es_pk_{side}", "pack f 15", 1040 + 80 * k, 340); P.obj(f"es_g_{side}", "line~", 1040 + 80 * k, 370)
        P.c("es_gains", f"es_pk_{side}", k, 0); P.c(f"es_pk_{side}", f"es_g_{side}")
        P.sig2(f"wet_{side}0", "*~", "wet_sig", f"es_g_{side}", 700 + 80 * k, 400)
    P.obj("es_init", "loadbang", 1200, 340); P.msg("es_one", "1", 1200, 360)
    P.c("es_init", "es_one"); P.c("es_one", "es_g_l"); P.c("es_one", "es_g_r")
    # Scatter style (`echo_scatter_style`, Oct 20): Classic (0) = the places above, by level only; New (1) =
    # dcs_scatter~: each repeat from a direction (ear delay, the head's shadow), thrown across while it plays, and
    # near or far (quieter, darker, more reverb). The Classic gains fade out as New fades in (20 ms).
    P.obj("es_r_style", "r echo_scatter_style", 1300, 160)
    ex("es_cl_v", "1-($f1>=0.5)", 1300, 190)
    P.obj("es_cl_pk", "pack f 20", 1300, 220); P.obj("es_cl", "line~", 1300, 250)
    P.c("es_r_style", "es_cl_v"); P.c("es_cl_v", "es_cl_pk"); P.c("es_cl_pk", "es_cl")
    P.obj("es_cl_lb", "loadbang", 1400, 190); P.msg("es_cl_one", "1", 1400, 220)
    P.c("es_cl_lb", "es_cl_one"); P.c("es_cl_one", "es_cl")
    fx(P, "es_new", "dcs_scatter~", "wet_sig", ("echo_scatter", "amount"), ("echo_throw", "throw"),
       ("echo_depth", "depth"), ("echo_scatter_style", "on"), x=1300, y=440, smooth=None)
    P.c("ln_echo_time", "es_new", 0, 1)
    for k, side in enumerate(("l", "r")):
        P.sig2(f"wet_{side}1", "*~", f"wet_{side}0", "es_cl", 700 + 80 * k, 430)
        P.sig2(f"wet_{side}", "+~", f"wet_{side}1", "es_new", 700 + 80 * k, 460)
        P.conns[-1] = ("es_new", k, f"wet_{side}", 1)

    # --- dry level and output ---
    P.obj("dry_sig", "*~ 1", 250, 380); P.c("voice", "dry_sig")     # (already at the Dry level, see the music join)
    P.obj("mix_pre", "+~", 250, 420); P.c("dry_sig", "mix_pre")      # the echo joins both ears later, scattered
    add_tempo_clock(P)
    beat = add_beat(P)
    P.sig2("mix_out", "+~", "mix_pre", beat, 250, 450)       # the beat joins here, so reverb and pan apply to it

    # --- reverb: stereo, added to the mix before the spatial stage ---
    rfb = "0.7+0.28*$f1"                                         # feedback: room size 0..1 -> 0.7..0.98
    P.obj("r_reverb_size", "r reverb_size", 1600, 400)
    P.obj("size_calc", "expr %s %s 0.28*sqrt(1-(%s)*(%s))" % (rfb.replace("$", BS + "$"), BS + ";",
          rfb.replace("$", BS + "$"), rfb.replace("$", BS + "$")), 1600, 430)
    P.c("r_reverb_size", "size_calc")
    P.obj("pk_rfb", "pack f 30", 1600, 470); P.obj("ln_rfb", "line~", 1600, 500)
    P.obj("pk_rin", "pack f 30", 1750, 470); P.obj("ln_rin", "line~", 1750, 500)
    P.c("size_calc", "pk_rfb", 0, 0); P.c("pk_rfb", "ln_rfb"); P.c("r_smooth", "pk_rfb", 0, 1)
    P.c("size_calc", "pk_rin", 1, 0); P.c("pk_rin", "ln_rin"); P.c("r_smooth", "pk_rin", 0, 1)
    P.obj("r_reverb_tone", "r reverb_tone", 1900, 400)           # damping: low-pass cutoff in the loops
    # The input level follows the room size so the reverb stays about equally loud at any size.
    gc_l, gc_r = add_glitch_cloud(P)
    P.sig2("gcx_mono", "+~", gc_l, gc_r, 1700, 520)
    P.obj("gcx_mono_h", "*~ 0.5", 1700, 530)
    P.c("gcx_mono", "gcx_mono_h")
    P.sig2("rev_src0", "+~", "mix_out", "gcx_mono_h", 1600, 525)       # the glitch cloud echoes in the reverb too
    P.sig2("rev_src1", "+~", "wet_sig", "es_new", 1600, 515)            # (a far repeat sends more: New scatter)
    P.conns[-1] = ("es_new", 2, "rev_src1", 1)
    P.sig2("rev_src", "+~", "rev_src0", "rev_src1", 1600, 530)            # and the echo, as before
    P.sig2("rev_in", "*~", "rev_src", "ln_rin", 1600, 540)

    rmix = smoothed(P, "reverb_mix", "reverb_mix", 2000, 400)
    ron = smoothed(P, "reverb_on", "reverb_on", 2100, 400)
    P.sig2("rev_gain", "*~", rmix, ron, 2000, 480)               # level, forced to 0 when bypassed

    # the reverb itself: dcs_reverb~ (Oct 17) = the same combs and all-passes these Pd objects used to make, sample for
    # sample (tools/test_dcs.py compares the two), at ~60% of the cost
    rargs = " ".join(str(v) for v in [RIGHT_EXTRA_MS, len(COMB_MS)] + COMB_MS + ALLPASS_MS)
    P.obj("rev_dcs", f"dcs_reverb~ {rargs}", 1600, 600)
    P.c("rev_in", "rev_dcs"); P.c("ln_rfb", "rev_dcs", 0, 1)
    P.msg("rev_tone", f"tone {BS}$1", 1900, 430); P.c("r_reverb_tone", "rev_tone"); P.c("rev_tone", "rev_dcs")
    for side, extra in (("l", 0.0), ("r", RIGHT_EXTRA_MS)):
        x0 = 1600 if side == "l" else 2000
        P.obj(f"rev_{side}", "*~", x0 + 400, 1150)
        P.c("rev_dcs", f"rev_{side}", 0 if side == "l" else 1, 0); P.c("rev_gain", f"rev_{side}", 0, 1)
        P.sig2(f"in_{side}0", "+~", "mix_out", f"rev_{side}", x0 + 400, 1200)
        P.sig2(f"in_{side}1", "+~", f"in_{side}0", gc_l if side == "l" else gc_r, x0 + 400, 1210)
        P.sig2(f"in_{side}2", "+~", f"in_{side}1", "rr_out", x0 + 400, 1215)   # (reverse reverb, below)
        P.sig2(f"in_{side}", "+~", f"in_{side}2", f"wet_{side}", x0 + 400, 1220)
    add_reverse_reverb(P)

    # --- spatial: ear delay (time difference between the ears), then pan, then output ---
    # Positive 'space_shift' delays the right ear, so the sound seems to come from the left
    # (negative: from the right). Larger values (10-25 ms) widen the sound instead of moving it.
    if LEGACY_FX:
        for side in ("l", "r"):
            P.obj(f"space_wr_{side}", f"delwrite~ space_buf_{side} 100", 250, 440)
            P.c(f"in_{side}", f"space_wr_{side}")
        shift = smoothed(P, "space_shift", "space_shift", 1200, 20)
        son = smoothed(P, "space_on", "space_on", 1300, 20)
        P.sig2("shift_eff", "*~", shift, son, 1200, 110)            # forced to 0 when bypassed
        P.obj("shift_neg", "*~ -1", 1300, 110)
        P.c("shift_eff", "shift_neg")
        for side, src, x in (("l", "shift_neg", 1300), ("r", "shift_eff", 1200)):
            P.obj(f"d_{side}_pos", "clip~ 0 25", x, 150)            # only one ear is delayed at a time
            P.obj(f"d_{side}_ms", f"+~ {EAR_BASE_MS}", x, 180)
            P.obj(f"rd_{side}", f"delread4~ space_buf_{side}", x, 210)
            P.c(src, f"d_{side}_pos"); P.c(f"d_{side}_pos", f"d_{side}_ms"); P.c(f"d_{side}_ms", f"rd_{side}")

        # Balance-style pan: centre leaves both ears at full level, hard pan silences the far ear.
        pan = smoothed(P, "space_pan", "space_pan", 1400, 20)
        P.sig2("pan_eff", "*~", pan, son, 1400, 110)                # forced to 0 when bypassed
        P.obj("pan_up", "clip~ 0 1", 1400, 150)                     # how far right of centre
        P.obj("pan_up_neg", "*~ -1", 1400, 180)
        P.obj("gain_l", "+~ 1", 1400, 210)                          # left gain = 1 - right-of-centre
        P.c("pan_eff", "pan_up"); P.c("pan_up", "pan_up_neg"); P.c("pan_up_neg", "gain_l")
        P.obj("pan_neg", "*~ -1", 1500, 110)
        P.obj("pan_down", "clip~ 0 1", 1500, 150)                   # how far left of centre
        P.obj("pan_down_neg", "*~ -1", 1500, 180)
        P.obj("gain_r", "+~ 1", 1500, 210)                          # right gain = 1 - left-of-centre
        P.c("pan_eff", "pan_neg"); P.c("pan_neg", "pan_down"); P.c("pan_down", "pan_down_neg")
        P.c("pan_down_neg", "gain_r")
        P.sig2("out_l", "*~", "rd_l", "gain_l", 1400, 260)
        P.sig2("out_r", "*~", "rd_r", "gain_r", 1500, 260)
    else:   # dcs_space~ (Oct 17): the same ear delays and balance pan, sample for sample
        fx(P, "space", f"dcs_space~ {EAR_BASE_MS} 100", "in_l", ("space_shift", "shift"), ("space_pan", "pan"),
           ("space_on", "on"), x=1300, y=210)
        P.c("in_r", "space", 0, 1)
        P.obj("out_l", "*~ 1", 1400, 260); P.obj("out_r", "*~ 1", 1500, 260)   # (the names the rest of the patch uses)
        P.c("space", "out_l", 0, 0); P.c("space", "out_r", 1, 0)
    add_stereo_out(P, gain, dry)

    # Duck: while another app plays (music, navigation), the app sends "duck <cut 0..1>" and the whole output dips a
    # little, so both can be heard. 0 = full level (also before anything arrives).
    P.obj("r_duck", "r duck", 1600, 290)
    P.msg("duck_msg", BS + "$1 " + str(DUCK_MS), 1600, 320)
    P.obj("duck_ln", "line~", 1600, 350)
    P.obj("duck_neg", "*~ -1", 1600, 370)
    P.obj("duck_gain", "+~ 1", 1600, 390)
    P.c("r_duck", "duck_msg"); P.c("duck_msg", "duck_ln"); P.c("duck_ln", "duck_neg"); P.c("duck_neg", "duck_gain")

    # Soft limiter: untouched up to 0.7, then the part above is eased in with a smooth cubic
    # so peaks never pass 1.0 and loud audio rounds off instead of hard-clipping into fuzz.
    for side, x in (("l", 1400), ("r", 1500)):
        o = lambda n, t, y: P.obj(f"lim_{side}_{n}", t, x, y)
        o("base", "clip~ -0.7 0.7", 290)             # the transparent part
        o("neg", "*~ -1", 300)
        o("exc", "+~", 310)                          # excess = input - base
        o("scale", "*~ 3.33333", 320)                # excess in units of the 0.3 headroom
        o("cl", "clip~ -1.5 1.5", 330)
        o("sq", "*~", 340)
        o("k", "*~ -0.148148", 350)
        o("one", "+~ 1", 360)
        o("cub", "*~", 370)
        o("back", "*~ 0.3", 380)
        o("sum", "+~", 390)
        P.c(f"out_{side}", f"lim_{side}_base"); P.c(f"out_{side}", f"lim_{side}_exc", 0, 0)
        P.c(f"lim_{side}_base", f"lim_{side}_neg"); P.c(f"lim_{side}_neg", f"lim_{side}_exc", 0, 1)
        P.c(f"lim_{side}_exc", f"lim_{side}_scale"); P.c(f"lim_{side}_scale", f"lim_{side}_cl")
        P.c(f"lim_{side}_cl", f"lim_{side}_sq", 0, 0); P.c(f"lim_{side}_cl", f"lim_{side}_sq", 0, 1)
        P.c(f"lim_{side}_sq", f"lim_{side}_k"); P.c(f"lim_{side}_k", f"lim_{side}_one")
        P.c(f"lim_{side}_cl", f"lim_{side}_cub", 0, 0); P.c(f"lim_{side}_one", f"lim_{side}_cub", 0, 1)
        P.c(f"lim_{side}_cub", f"lim_{side}_back")
        P.c(f"lim_{side}_back", f"lim_{side}_sum", 0, 0); P.c(f"lim_{side}_base", f"lim_{side}_sum", 0, 1)
        P.sig2(f"limit_{side}", "*~", f"lim_{side}_sum", "duck_gain", x, 400)
    # Realm cue: a dreamy warp (DadCraft) when a Realm starts (gliding up an octave) or your own settings come back
    # (gliding down): a soft tone with a detuned twin beating against it and a quiet fifth for a halo, swelling in and
    # melting away, after the limiter so it is heard whatever plays. From one Realm straight to another (DadCraft: "a
    # lateral, sideways warp") it swerves around one note (a dip, a lift past it, back) while sweeping from one ear to
    # the other. [realm_cue 1|0|2( from the app (into a Realm, home, Realm to Realm); cue_sound (Setup) switches it off.
    cx, cy = 1700, 300
    P.obj("cue_r", "r realm_cue", cx, cy); P.obj("cue_snd_r", "r cue_sound", cx + 120, cy)
    P.obj("cue_snd_v", "value cue_onv", cx + 120, cy + 15); P.c("cue_snd_r", "cue_snd_v")
    P.obj("cue_ex", ("expr ($f1+1)*(cue_onv>0)").replace("$", BS + "$"), cx, cy + 15); P.c("cue_r", "cue_ex")
    P.obj("cue_sel", "sel 2 1 3", cx, cy + 30); P.c("cue_ex", "cue_sel")
    m = CUE_SIDE_HZ
    glides = (f"{CUE_LOW_HZ} {BS}, {CUE_HIGH_HZ} {CUE_GLIDE_MS}",            # into a Realm: up an octave
              f"{CUE_HIGH_HZ} {BS}, {CUE_LOW_HZ} {CUE_GLIDE_MS}",            # home: down an octave
              f"{m} {BS}, {m * 0.94:.1f} 250 {BS}, {m * 1.06:.1f} 350 250 {BS}, {m} 300 600")   # Realm to Realm: a swerve
    for k, gl in enumerate(glides):
        P.obj(f"cue_t{k}", "t b b b b", cx + 160 * k, cy + 45); P.c("cue_sel", f"cue_t{k}", k, 0)
        P.msg(f"cue_sw{k}", f"{BS}; sw_cue 1", cx + 160 * k, cy + 60); P.c(f"cue_t{k}", f"cue_sw{k}", 3, 0)
        P.msg(f"cue_gl{k}", gl, cx + 160 * k + 60, cy + 60); P.c(f"cue_t{k}", f"cue_gl{k}", 2, 0)
        P.c(f"cue_gl{k}", "cue_fq")
        if k < 2:
            P.msg(f"cue_pc{k}", "0.5", cx + 160 * k + 100, cy + 70); P.c(f"cue_t{k}", f"cue_pc{k}", 1, 0); P.c(f"cue_pc{k}", "cue_pan")
        else:                                                                  # the sweep, one way or the other
            P.obj("cue_side", "random 2", cx + 420, cy + 70); P.c(f"cue_t{k}", "cue_side", 1, 0)
            P.obj("cue_side_sel", "sel 0 1", cx + 420, cy + 80); P.c("cue_side", "cue_side_sel")
            P.msg("cue_lr", f"0.1 {BS}, 0.9 {CUE_GLIDE_MS}", cx + 420, cy + 90); P.msg("cue_rl", f"0.9 {BS}, 0.1 {CUE_GLIDE_MS}", cx + 480, cy + 90)
            P.c("cue_side_sel", "cue_lr", 0, 0); P.c("cue_side_sel", "cue_rl", 1, 0); P.c("cue_lr", "cue_pan"); P.c("cue_rl", "cue_pan")
        P.c(f"cue_t{k}", "cue_go", 0, 0)
    P.obj("cue_go", "t b b", cx, cy + 80)
    P.msg("cue_env", f"0 {BS}, {CUE_LEVEL} {CUE_SWELL_MS} {BS}, 0 {CUE_FADE_MS} {CUE_SWELL_MS}", cx, cy + 95)
    P.c("cue_go", "cue_env", 1, 0)
    P.obj("cue_off", f"del {CUE_SWELL_MS + CUE_FADE_MS + CUE_ECHO_TAIL_MS}", cx + 300, cy + 95); P.c("cue_go", "cue_off", 0, 0)
    P.msg("cue_sw0ff", f"{BS}; sw_cue 0", cx + 300, cy + 110); P.c("cue_off", "cue_sw0ff")
    P.obj("cue_fq", "vline~", cx + 300, cy + 130)
    P.obj("cue_fd", f"*~ {CUE_DETUNE}", cx + 360, cy + 145); P.obj("cue_f5", "*~ 1.5", cx + 420, cy + 145)
    P.c("cue_fq", "cue_fd"); P.c("cue_fq", "cue_f5")
    P.obj("cue_oa", "osc~", cx + 300, cy + 165); P.obj("cue_ob", "osc~", cx + 360, cy + 165); P.obj("cue_oc", "osc~", cx + 420, cy + 165)
    P.c("cue_fq", "cue_oa"); P.c("cue_fd", "cue_ob"); P.c("cue_f5", "cue_oc")
    P.obj("cue_gc", "*~ 0.35", cx + 420, cy + 180); P.c("cue_oc", "cue_gc")
    P.sig2("cue_ab", "+~", "cue_oa", "cue_ob", cx + 300, cy + 185)
    P.sig2("cue_abc", "+~", "cue_ab", "cue_gc", cx + 300, cy + 200)
    P.obj("cue_ea", "vline~", cx, cy + 130); P.c("cue_env", "cue_ea")
    P.sig2("cue_dry", "*~", "cue_abc", "cue_ea", cx, cy + 220)
    # a touch of echo (DadCraft): repeats every CUE_ECHO_MS, each CUE_ECHO_FB of the last
    P.sig2("cue_ein", "+~", "cue_dry", "cue_efb", cx + 100, cy + 235)
    P.obj("cue_ew", f"delwrite~ cue_echo {CUE_ECHO_MS + 100}", cx + 100, cy + 250); P.c("cue_ein", "cue_ew")
    P.obj("cue_er", f"delread~ cue_echo {CUE_ECHO_MS}", cx + 200, cy + 235)
    P.obj("cue_efb", f"*~ {CUE_ECHO_FB}", cx + 200, cy + 250); P.c("cue_er", "cue_efb")
    P.obj("cue_ewet", f"*~ {CUE_ECHO_WET}", cx + 200, cy + 265); P.c("cue_er", "cue_ewet")
    P.sig2("cue_mix", "+~", "cue_dry", "cue_ewet", cx, cy + 280)
    P.obj("grp_cue_r", "r sw_cue", 10, 10); P.obj("grp_cue_sw", "switch~", 10, 40); P.c("grp_cue_r", "grp_cue_sw")
    # where it sits: 0 left .. 0.5 centre (both ears full, as before) .. 1 right
    P.obj("cue_pan", "vline~", cx + 300, cy + 280)
    P.obj("cue_pq", "*~ 0.25", cx + 300, cy + 295); P.c("cue_pan", "cue_pq")
    P.obj("cue_pcl", "cos~", cx + 300, cy + 310); P.c("cue_pq", "cue_pcl")
    P.obj("cue_pqr", "-~ 0.25", cx + 360, cy + 310); P.c("cue_pq", "cue_pqr")
    P.obj("cue_pcr", "cos~", cx + 360, cy + 325); P.c("cue_pqr", "cue_pcr")
    P.obj("cue_gl_l", "*~ 1.41421", cx + 300, cy + 340); P.c("cue_pcl", "cue_gl_l")
    P.obj("cue_gl_r", "*~ 1.41421", cx + 360, cy + 340); P.c("cue_pcr", "cue_gl_r")
    P.sig2("cue_l", "*~", "cue_mix", "cue_gl_l", cx, cy + 360); P.sig2("cue_rr", "*~", "cue_mix", "cue_gl_r", cx + 60, cy + 360)
    P.sig2("out_cl", "+~", "limit_l", "cue_l", 1400, 415); P.sig2("out_cr", "+~", "limit_r", "cue_rr", 1500, 415)
    P.obj("dac", "dac~ 1 2", 1400, 430)
    P.c("out_cl", "dac", 0, 0)
    P.c("out_cr", "dac", 0, 1)
    add_clip_watch(P)

    # --- switchable blocks: each one's DSP stops while it can't be heard ---
    P.group("cue", r"^cue_(fq|fd|f5|oa|ob|oc|gc|ab|abc|ea|dry|ein|ew|er|efb|ewet|mix|pan|pq|pcl|pqr|pcr|gl_l|gl_r|l|rr)$")                    # the Realm cue: on only while it sounds
    P.group("reverb", r"^(rev_dcs$|rev_in$|rev_[lr]$|pk_rfb$|ln_rfb$|pk_rin$|ln_rin$)")
    add_switch(P, "reverb", ("reverb_on", "reverb_mix"), 300)
    P.group("revrev", r"^rr_(src0|src|wr|rd|sound)$")                     # reverse reverb
    add_switch(P, "revrev", ("reverb_on", "reverb_mix", "reverb_rev"), 300)
    # (the prototype switches' twins: only the chosen one runs, Oct 17. A "<name>_c" receiver = 1 - <name>.)
    for name in ("shift_voice", "harm_tracker"):
        P.obj(f"inv_{name}_r", f"r {name}", 4400, 1500 + 40 * len(P.groups))
        P.obj(f"inv_{name}", "expr 1-$f1".replace("$", BS + "$"), 4400, 1515 + 40 * len(P.groups))
        P.obj(f"inv_{name}_s", f"s {name}_c", 4400, 1530 + 40 * len(P.groups))
        P.c(f"inv_{name}_r", f"inv_{name}"); P.c(f"inv_{name}", f"inv_{name}_s")
    if LEGACY_FX:
        P.group("pitch", r"^(rate_hz|ph1|ph2|ph2_off|delwrite_p|tap[12]_\w+|rd[12]|cos[12]|cos[12]_neg|win[12]|grain[12]|shifted)$")
    add_switch(P, "pitch", ("pitch_on", "pitch_mix", "shift_voice_c"), 300, dsp=LEGACY_FX)
    add_switch(P, "tune", ("tune_amount",), 300, dsp=False)    # (dcs_shift~ hears "run")
    P.group("pshiftn", r"^pshift_dcs$")
    add_switch(P, "pshiftn", ("pitch_on", "pitch_mix", "shift_voice"), 300)
    if LEGACY_FX:
        P.group("chorus", r"^(chorus_wr|chorus_ph|ch[01]_\w+|ch_sum|ch_wet)$")
    add_switch(P, "chorus", ("chorus_on", "chorus_mix"), 300, dsp=LEGACY_FX)
    if LEGACY_FX:
        P.group("flanger", r"^(fl_wr|fl_ph|fl_cos|fl_half|fl_unit|fl_sweep|fl_ms|fl_rd|fl_fb_sig|fl_in|fl_wet)$")
    add_switch(P, "flanger", ("flange_on", "flange_mix"), 300, dsp=LEGACY_FX)
    if LEGACY_FX:
        P.group("phaser", r"^(ph_start|ph_clock|ph_snap|ph_osc|ph_sweep|ph_sr_start|ph_sr|ph_fb_rd|ph_fb_sig|ph_coef\d|ph_pack\d|ph_ap\d|ph_in|ph_fb_wr)$")
    add_switch(P, "phaser", ("phaser_on", "phaser_mix"), 300, also=("ph_clock",) if LEGACY_FX else (), dsp=LEGACY_FX)   # the 4 ms clock stops too
    if LEGACY_FX:
        P.group("reverse", r"^(rvs_ph1|rvs_ph2_off|rvs_ph2|rvs_wr|rvs_tap\d_\w+|rvs_rd\d|rvs_cos\d\w*|rvs_win\d|rvs_grain\d|rvs_sound)$")
    add_switch(P, "reverse", ("rvs_on", "rvs_mix"), 300, dsp=LEGACY_FX)
    P.group("roomnotes", r"^rn\d*_")
    add_switch(P, "roomnotes", ("music_on", "music_res", "rn_ring"), 2500)
    for k in range(2):                                                  # Room notes, found: each voice on while it rings
        P.group(f"rfv{k}", rf"^rf{k}_(rd|ix|env|raw|bp|fs1|r1|res|str|strg|ring|mix|out)$")
    P.group("rnfound", r"^(rnf_(sum|env|agc_ln|lvl|lim(_\w+)?))$")
    add_switch(P, "rnfound", ("music_on", "music_res", "rnf_found"), 3000)       # rings run up to ~2 s
    for k in range(4):                                                  # each synth chime voice: on only while it rings
        P.group(f"chv{k}", rf"^mu_v{k}_(o1|o2|o3|g2|g3|s12|s|env|out)$")
    for k in range(2):                                                  # the found pad's sets: on while heard and found
        P.group(f"fpad{k}", rf"^(fp{k}\d?[ab]?_(wl|wc|wn|w|ix|rd|s|acc\d|gm|out)|fp_xf{k})$")
        P.obj(f"grp_fpad{k}_r", f"r sw_fpad{k}", 10, 10); P.obj(f"grp_fpad{k}_sw", "switch~", 10, 40); P.c(f"grp_fpad{k}_r", f"grp_fpad{k}_sw")
    P.group("fbass", r"^fb[ab]?_(wl|wc|wn|w|ix|rd|s|gm)$")                # the found bass: on while chosen and kept
    P.obj("grp_fbass_r", "r sw_fbass", 10, 10); P.obj("grp_fbass_sw", "switch~", 10, 40); P.c("grp_fbass_r", "grp_fbass_sw")
    P.group("fprec", r"^fpr_")                                            # recording takes: while a found pad/bass wants them
    add_switch(P, "fprec", ("music_on", "fpad_need"), 100)
    for k in range(2):                                                  # the pad's two crossfade sets: on only while heard
        P.group(f"pads{k}", rf"^(mu_pad\d_{k}_(o1|o2|s|x)|mu_xf{k})$")
    P.group("horgan", r"^mu_hm\d_")                                      # the organ voices: off for "Your voice"
    add_switch(P, "horgan", ("music_on", "music_harm", "harm_organ"), 2500)
    P.group("hvnew", r"^hv_(din|dcs)$")                                 # (Pitch shifter New: only while it sings)
    add_switch(P, "hvnew", ("sw_hvoice", "shift_voice"), 100)
    P.group("hvoice", r"^hv_")                                           # your voice as harmony: on only while it sings
    P.group("harmsig", r"^mu_hm_sig$")                                  # the pitch tracker, at half the sample rate
    add_switch(P, "harmsig", ("music_on", "harm_run", "harm_tracker_c"), 100, block="64 1 0.5")
    P.group("harmsign", r"^mu_hm_sig2$")                                # (Pitch tracker New: dcs_pitch~)
    add_switch(P, "harmsign", ("music_on", "harm_run", "harm_tracker"), 100, block="64 1 0.5")
    P.group("harm", r"^mu_hm")                                          # Harmony's tracker and voices: off at 0
    add_switch(P, "harm", ("music_on", "harm_run"), 2500)            # (its notes fade over 1.8 s)
    P.group("kpad", r"^mu_kv_pad2?$")                                    # a kit's pad voices: only while heard
    add_switch(P, "kpad", ("music_on", "kpad_run"), 3000)               # (a kit pad's release: up to 2.5 s)
    P.group("kbass", r"^ba_kv2?$")                                        # a kit's bass voices: the same
    add_switch(P, "kbass", ("music_on", "kbass_run"), 3000)
    P.group("music", r"^(mu_|ba_)")
    add_switch(P, "music", ("music_on",), 3000)                       # chimes fade over up to 2.6 s
    P.group("gloop", r"^gl_(rec_in|rec|ix|rd|env|s0|out)$")                    # the glitch loop's audio
    add_switch(P, "gloop", ("beat_glitch", "beat_level", "gc_amount"), 3500)
    for k in range(4):                                                  # each found melody voice: on only while it rings
        P.group(f"fmv{k}", rf"^fm{k}_(rd|ix|env|raw|bp|fs1|r1|res|str|strg|ring|mix|out)$")
    P.group("fmelody", r"^fm_(acc\d|lim(_\w+)?|env|agc_ln|lvl)$")             # the found melody's mix
    add_switch(P, "fmelody", ("music_on", "melody_src"), 3000)
    P.group("fsound", r"^(fs_(src|wr|rd|rec|lo|hi|env_\w+|ks0|ks(_\w+)?|hat_lim(_\w+)?)|fsv_\w+_(rd|ixl|env|s|f))$")    # the found-sound kit's audio
    add_switch(P, "fsound", ("fs_need",), 500)
    P.group("gcv", r"^gc0_")                                           # the glitch voices: on only while playing
    P.group("gcs", r"^gc2_")
    P.group("gcloud", r"^gc\d*_(?!r_amt$|amt$|amt_rd$|clk_|rest|cool|m_t$|det|slow|roll|pass|ons_t$|timer$|flag|trig$|ask$|walk|step|half|pend|beat_t$|fire$|mode|en\d$|rn\d$|el2?$|st2$|st_rd$|stepms|par$|pack$|wait|vc|route$|dbg|kind$|spar$|spack$)")
    add_switch(P, "gcloud", ("gc_amount", "gc_level"), 1500)           # a glitch runs up to ~1.3 s

    add_onset_switch(P)
    add_kits(P)
    add_kit_drums(P)
    # Our own objects loaded (Oct 19): the app warns when `dcs_ok` doesn't come at Start (without them, nothing
    # plays: the effects are dcs objects in every sound's path). Where the library is missing this box can't be made.
    P.obj("dcs_loaded", "dcs_loaded", 4300, 2100); P.obj("dcs_ok", "s dcs_ok", 4300, 2120); P.c("dcs_loaded", "dcs_ok")

    # --- defaults on load (desktop Pd / standalone) ---
    P.obj("loadbang", "loadbang", 20, 780)
    body = " ".join(f"{BS}; {k} {v}" for k, v in DEFAULTS)
    P.msg("defaults", body, 20, 810)
    P.c("loadbang", "defaults")
    return P
