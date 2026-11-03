# SPDX-License-Identifier: GPL-3.0-or-later
"""The linear effects after music joins, shared by its mid and side paths."""
from .constants import *  # noqa: F401,F403
from .core import *  # noqa: F401,F403


def add_music_fx(P, src, prefix=""):
    # Build once in local names, then merge. Controls keep the same receivers and
    # phases on both paths; named delay buffers must be private to each path.
    Q = P
    P = Patch()
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

    names = {n: prefix + n for n in P.index}
    names.update(filtered_mix=src, r_smooth="r_smooth", smooth_x3="smooth_x3")
    for n, i in P.index.items():
        line = P.lines[i]
        if prefix:
            for buf in ("chorus_buf", "flange_buf", "ph_fb", "rvs_buf", "glitch_buf"):
                line = line.replace(" " + buf + " ", " " + prefix + buf + " ")
                line = line.replace(" " + buf + ";", " " + prefix + buf + ";")
        Q._add(names[n], line)
    for a, o, b, i in P.conns:
        Q.c(names.get(a, a), names.get(b, b), o, i)
    return names["voice"]
