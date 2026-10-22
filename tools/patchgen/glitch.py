# SPDX-License-Identifier: GPL-3.0-or-later
"""The glitch cloud, the onset detector switch and reverse reverb."""
from .constants import *  # noqa: F401,F403 (the tuning numbers)
from .core import *  # noqa: F401,F403


def add_glitch_cloud(P):
    """Glitch cloud (after dblue Glitch): the hushed mic goes into a ring buffer (memory only, GC_BUF_MS). A sudden
    sound (and now and then a free clock) grabs a bit of it and plays it back mangled: a stutter (a slice repeated,
    while walking a quarter step long), reversed, a tape stop (slowing to a halt), or a shuffled older slice, each one
    somewhere else between the ears. Amount = how often; walking makes glitches wait for the next step or half-step.
    Returns the left and right output signals."""
    x, y = 6600, 20
    def ex(name, text, xx, yy):
        return P.obj(name, "expr " + text.replace(",", BS + ",").replace("$", BS + "$"), xx, yy)
    # The voices only compute while they play (most of the time nothing does): on at each glitch, off after it.
    def voice_switch(name, src_list, tail_ms, xx, yy):
        P.msg(f"gcsw_{name}_on", f"{BS}; sw_{name} 1", xx, yy)
        P.obj(f"gcsw_{name}_t", "t l b", xx, yy + 20)
        P.obj(f"gcsw_{name}_L", "list split 1", xx + 80, yy + 40)
        P.obj(f"gcsw_{name}_tail", f"+ {tail_ms}", xx + 80, yy + 60)
        P.obj(f"gcsw_{name}_del", "del", xx + 80, yy + 80)
        P.msg(f"gcsw_{name}_off", f"{BS}; sw_{name} 0", xx + 80, yy + 100)
        P.c(src_list, f"gcsw_{name}_t"); P.c(f"gcsw_{name}_t", f"gcsw_{name}_on", 1, 0)
        P.c(f"gcsw_{name}_t", f"gcsw_{name}_L", 0, 0); P.c(f"gcsw_{name}_L", f"gcsw_{name}_tail")
        P.c(f"gcsw_{name}_tail", f"gcsw_{name}_del"); P.c(f"gcsw_{name}_del", f"gcsw_{name}_off")
        return f"gcsw_{name}_t"
    P.sig2("gc_src", "*~", "mic_gated", "hush_ln", x, y)                  # quieter in a pocket, like the rest
    P.obj("gc_wr", f"delwrite~ gc_buf {GC_BUF_MS}", x, y + 30)
    P.c("gc_src", "gc_wr")
    P.obj("gc_r_amt", "r gc_amount", x + 300, y)
    P.obj("gc_amt", "f", x + 300, y + 30)
    P.c("gc_r_amt", "gc_amt", 0, 1)
    # ---- sudden sounds: the 5 ms level against its own recent average ----
    P.obj("gc_m_t", "t f f f", x, y + 80)
    P.c("meter", "gc_m_t")
    # the found-sound catcher's own detector: the same jump, but in a loud room (a steady roar) a smaller one counts,
    # and with the room in stereo its least loudness is the room's own (fs_min)
    ex("fs_det", f"($f1-$f2>if($f2>{FS_LOUD_DB},{FS_ONSET_LOUD_DB},{GC_ONSET_DB}))*($f1>{fs_min(GC_MIN_DB)})", x + 250, y + 110)
    P.obj("fs_det_chg", "change", x + 250, y + 140); P.obj("fs_det_sel", "sel 1", x + 250, y + 170)
    P.c("gc_m_t", "fs_det", 2, 0); P.c("fs_det", "fs_det_chg"); P.c("fs_det_chg", "fs_det_sel")
    ex("gc_det", f"($f1-$f2>{GC_ONSET_DB})*($f1>{GC_MIN_DB})", x, y + 110)
    ex("gc_slow", "$f1*0.04+$f2*0.96", x + 120, y + 110)                # about a quarter second
    P.obj("gc_slow_t", "t f f", x + 120, y + 140)
    P.c("gc_m_t", "gc_det", 1, 0); P.c("gc_m_t", "gc_slow", 0, 0)
    P.c("gc_slow", "gc_slow_t"); P.c("gc_slow_t", "gc_slow", 1, 1); P.c("gc_slow_t", "gc_det", 0, 1); P.c("gc_slow_t", "fs_det", 0, 1)
    P.obj("gc_det_chg", "change", x, y + 140)
    P.obj("gc_det_sel", "sel 1", x, y + 170)
    P.c("gc_det", "gc_det_chg"); P.c("gc_det_chg", "gc_det_sel")
    # Resting: after a glitch nothing new starts for a while (shorter at a high Amount).
    P.obj("gc_rest", "spigot 1", x, y + 200)
    P.c(handling_gate(P, "gc_hg", "gc_det_sel", x + 60, y + 170), "gc_rest")   # not handling noise
    P.obj("gc_roll_t", "t b b", x, y + 230)
    P.obj("gc_roll", "random 1000", x + 60, y + 260)
    P.obj("gc_amt_rd", "f", x + 140, y + 260)
    ex("gc_pass", "$f1<$f2*1000", x, y + 290)
    P.c("gc_rest", "gc_roll_t"); P.c("gc_roll_t", "gc_amt_rd", 1, 0); P.c("gc_amt_rd", "gc_pass", 0, 1)
    P.c("gc_r_amt", "gc_amt_rd", 0, 1)
    P.c("gc_roll_t", "gc_roll", 0, 0); P.c("gc_roll", "gc_pass")
    P.obj("gc_pass_sel", "sel 1", x, y + 320)
    P.c("gc_pass", "gc_pass_sel")
    # an onset that passes: remember when it began (the timer) and that it was one, then ask for a glitch
    P.obj("gc_ons_t", "t b b b", x, y + 350)
    P.c("gc_pass_sel", "gc_ons_t")
    P.obj("gc_timer", "timer", x + 200, y + 380)
    P.msg("gc_flag1", "1", x + 120, y + 380)
    P.obj("gc_flag", "f", x + 120, y + 410)
    P.c("gc_ons_t", "gc_timer", 2, 0); P.c("gc_ons_t", "gc_flag1", 1, 0); P.c("gc_flag1", "gc_flag", 0, 1)
    P.obj("gc_trig", "t b b", x, y + 440)                               # right: start resting; left: the glitch
    P.c("gc_ons_t", "gc_trig", 0, 0)
    P.msg("gc_rest0", "0", x + 300, y + 200)
    P.msg("gc_rest1", "1", x + 300, y + 260)
    P.obj("gc_rest_del", "del 1000", x + 300, y + 230)
    ex("gc_cool", f"{GC_COOL_MS[1]}-({GC_COOL_MS[1]}-{GC_COOL_MS[0]})*$f1", x + 400, y + 200)
    P.c("gc_r_amt", "gc_cool"); P.c("gc_cool", "gc_rest_del", 0, 1)
    P.obj("gc_rest_t", "t b b", x + 300, y + 170)
    P.c("gc_trig", "gc_rest_t", 1, 0)
    P.c("gc_rest_t", "gc_rest_del", 0, 0); P.c("gc_rest_t", "gc_rest0", 1, 0); P.c("gc_rest0", "gc_rest", 0, 1)
    P.c("gc_rest_del", "gc_rest1"); P.c("gc_rest1", "gc_rest", 0, 1)
    # ---- free clock: when nothing sudden happens, now and then a random older bit (sooner at a high Amount) ----
    P.obj("gc_clk_lb", "loadbang", x + 500, y + 260)
    P.obj("gc_clk_t", "t b b", x + 500, y + 320)
    P.obj("gc_clk_rnd", "random 6000", x + 560, y + 350)
    P.obj("gc_clk_amt", "f", x + 660, y + 350)
    P.c("gc_r_amt", "gc_clk_amt", 0, 1)
    ex("gc_clk_ms", "(2000+$f1)*0.6/(0.2+$f2)", x + 560, y + 380)
    P.obj("gc_clk_del", "del 4000", x + 500, y + 410)
    P.c("gc_clk_lb", "gc_clk_t"); P.c("gc_r_amt", "gc_clk_t")                 # Amount changed: pick a new time
    P.c("gc_clk_t", "gc_clk_amt", 1, 0); P.c("gc_clk_amt", "gc_clk_ms", 0, 1)
    P.c("gc_clk_t", "gc_clk_rnd", 0, 0); P.c("gc_clk_rnd", "gc_clk_ms"); P.c("gc_clk_ms", "gc_clk_del")
    P.c("gc_ons_t", "gc_clk_t", 0, 0)                                   # a sudden sound puts the clock back
    P.obj("gc_clk_fire", "t b b", x + 500, y + 440)
    P.c("gc_clk_del", "gc_clk_fire"); P.c("gc_clk_fire", "gc_clk_t", 1, 0)
    P.obj("gc_clk_on", "spigot", x + 500, y + 470)                       # only while Amount > 0, and not resting
    ex("gc_clk_ok", "$f1>0", x + 600, y + 440)
    P.c("gc_r_amt", "gc_clk_ok"); P.c("gc_clk_ok", "gc_clk_on", 0, 1)
    P.obj("gc_clk_rest", "spigot 1", x + 500, y + 500)
    P.c("gc_rest0", "gc_clk_rest", 0, 1); P.c("gc_rest1", "gc_clk_rest", 0, 1)
    P.c("gc_clk_fire", "gc_clk_on", 0, 0); P.c("gc_clk_on", "gc_clk_rest")
    P.obj("gc_clk_go", "t b b", x + 500, y + 530)
    P.msg("gc_flag0", "0", x + 560, y + 560)
    P.c("gc_clk_rest", "gc_clk_go"); P.c("gc_clk_go", "gc_flag0", 1, 0); P.c("gc_flag0", "gc_flag", 0, 1)
    P.c("gc_clk_go", "gc_ask", 0, 0)                                   # (only sudden sounds start the rest)
    # ---- walking: a glitch waits for the next step or half-step; stutters are a quarter step long ----
    P.obj("gc_r_step", "r pulse", x + 800, y)
    P.obj("gc_step_t", "t b b f b", x + 800, y + 30)
    P.c("gc_r_step", "gc_step_t")
    P.msg("gc_walk1", "1", x + 800, y + 90); P.msg("gc_walk0", "0", x + 860, y + 120)
    P.obj("gc_walk_del", f"del {GC_STEP_WAIT_MS}", x + 860, y + 90)
    P.obj("gc_walk", "f", x + 800, y + 150)
    P.c("gc_step_t", "gc_walk1", 3, 0); P.c("gc_walk1", "gc_walk", 0, 1)
    P.c("gc_step_t", "gc_walk_del", 3, 0); P.c("gc_walk_del", "gc_walk0"); P.c("gc_walk0", "gc_walk", 0, 1)
    P.obj("gc_step_ms", "* 1000", x + 950, y + 60)
    P.obj("gc_stepms_v", "f", x + 950, y + 90)
    P.c("gc_step_t", "gc_step_ms", 2, 0); P.c("gc_step_ms", "gc_stepms_v", 0, 1)
    P.obj("gc_half", "* 500", x + 1050, y + 60)
    P.obj("gc_half_del", "del 300", x + 1050, y + 90)
    P.c("gc_step_t", "gc_half", 2, 0); P.c("gc_half", "gc_half_del", 0, 1); P.c("gc_step_t", "gc_half_del", 1, 0)
    P.obj("gc_pend", "f", x + 800, y + 200)                              # a glitch waiting for the beat
    P.obj("gc_pend_sel", "sel 1", x + 800, y + 230)
    P.obj("gc_beat_t", "t b b", x + 800, y + 170)
    P.c("gc_step_t", "gc_beat_t", 0, 0); P.c("gc_half_del", "gc_beat_t")
    P.c("gc_beat_t", "gc_pend", 1, 0); P.c("gc_pend", "gc_pend_sel")
    P.msg("gc_pend0", "0", x + 900, y + 230)
    P.c("gc_beat_t", "gc_pend0", 0, 0); P.c("gc_pend0", "gc_pend", 0, 1)
    P.obj("gc_ask", "t b b", x, y + 470)                                # walking? wait. still? now.
    P.c("gc_trig", "gc_ask", 0, 0)
    P.obj("gc_walk_rd", "f", x + 100, y + 500)
    P.c("gc_ask", "gc_walk_rd", 0, 0); P.c("gc_walk1", "gc_walk_rd", 0, 1); P.c("gc_walk0", "gc_walk_rd", 0, 1)
    P.obj("gc_walk_sel", "sel 0 1", x + 100, y + 530)
    P.c("gc_walk_rd", "gc_walk_sel")
    P.msg("gc_pend1", "1", x + 200, y + 560)
    P.c("gc_walk_sel", "gc_pend1", 1, 0); P.c("gc_pend1", "gc_pend", 0, 1)
    P.obj("gc_fire", "t b b b b b b b", x, y + 600)
    P.c("gc_walk_sel", "gc_fire", 0, 0); P.c("gc_pend_sel", "gc_fire")
    # ---- what kind of glitch: the glitch matrix (DadCraft): each kind's share in %, together at most 100; the share
    # left over is the chance that nothing happens. Past 100 (a preset, the dice) they count as proportions.
    # (tape stop and a turntable scratch were tried and dropped, DadCraft, Oct 10)
    # (Oct 19, more kinds for DadCraft to try, each from 0%: 4 ratchet, 5 pitch jump, 6 gate, 7 spray)
    P.obj("gc_mode_rnd", "random 1000", x, y + 640)
    w = "(" + "+".join(f"$f{k + 2}" for k in range(len(GC_KINDS))) + ")"
    c = ["+".join(f"$f{j + 2}" for j in range(k + 1)) for k in range(len(GC_KINDS))]
    pick = "-1"
    for k in range(len(GC_KINDS) - 1, -1, -1):
        pick = f"if($f1*max({w},100)/1000<{c[k]},{k},{pick})"
    ex("gc_mode", pick, x, y + 670)
    for k, name in enumerate(GC_KINDS):
        if name not in GC_POCKET:                                          # (a pocketed kind's inlet stays 0)
            P.obj(f"gc_en{k}", f"r {name}", x + 200 + 80 * k, y + 640)
            P.c(f"gc_en{k}", "gc_mode", 0, k + 1)
    P.c("gc_fire", "gc_mode_rnd", 0, 0); P.c("gc_mode_rnd", "gc_mode")
    P.obj("gc_try_s", "s gc_try", x + 100, y + 655); P.c("gc_mode_rnd", "gc_try_s")   # every chance, for the tests
    P.obj("gc_mode_ok", "moses 0", x, y + 700)
    P.c("gc_mode", "gc_mode_ok")
    # randoms, how long ago the sound began (-1 = from the free clock) and the step length (0 = standing)
    for k in range(4):
        P.obj(f"gc_rn{k}", "random 1000", x + 200 + 80 * k, y + 700)
    P.obj("gc_el", "f", x + 520, y + 700)
    ex("gc_el2", "if($f2,$f1,-1)", x + 520, y + 730)
    P.c("gc_timer", "gc_el2"); P.c("gc_flag1", "gc_el2", 0, 1); P.c("gc_flag0", "gc_el2", 0, 1)
    P.obj("gc_st_rd", "f", x + 620, y + 700)
    ex("gc_st2", "$f1*$f2", x + 620, y + 730)
    P.c("gc_stepms_v", "gc_st_rd", 0, 1); P.c("gc_st_rd", "gc_st2"); P.c("gc_walk1", "gc_st2", 0, 1); P.c("gc_walk0", "gc_st2", 0, 1)
    # $f1 mode (0 stutter, 1 reverse, 2 shuffle, 4 ratchet, 5 pitch jump, 6 gate, 7 spray), $f2 ms since the onset
    # (-1 free), $f3..$f5, $f7 randoms 0..999, $f6 step ms (0 = standing).
    # Out: L, d0, c1, c2, S, place, shrink, gate, spray, wait (dcs_glitch~'s slice; Legacy reads the first 6).
    S = "if($f6>0,min(max($f6/4,60),250),60+40*int($f4/250))"
    Sr = f"min(2*({S}),300)"                                             # ratchet: the first repeat, then 0.7x each
    S5 = f"min(1.5*({S}),250)"                                           # pitch jump: the slice, read faster or slower
    r5 = "if($f5<400,2,if($f5<700,0.5,1.5))"                             # an octave up, an octave down, a fifth up
    G = "if($f6>0,max($f6/2,40),50+30*int($f4/333))"                     # gate: on and off each half of this
    W = "(150+0.25*$f4)"                                                  # spray: the window the grains come from
    L = (f"if($f1==0,({S})*(4+int($f5/250)),if($f1==1,300+0.4*$f3,if($f1==4,3.3*({Sr}),if($f1==5,({S5})*(3+int($f3/333)),"
         f"if($f1==6,400+0.4*$f3,if($f1==7,600+0.6*$f3,150+0.35*$f3))))))")
    base = f"if($f1==2||$f2<0,250+3*$f4,$f2+{GC_PREROLL_MS})"            # from just before the sound began
    d0 = f"if($f1==1,10,if($f1==7,max(({base})-{W}/2,20),{base}))"      # (spray: the cloud from the sound on)
    outs = [L, d0, "if($f1==1,-1,if($f1==0||$f1==4||$f1==5,0,1))", f"if($f1==0||$f1==4,1,if($f1==5,{r5},0))",
            f"if($f1==0,{S},if($f1==4,{Sr},if($f1==5,{S5},10000000)))",
            "if($f7<500,-(0.45+0.55*$f7/500),0.45+0.55*($f7-500)/500)",
            "if($f1==4,0.7,0)", f"if($f1==6,{G},0)", f"if($f1==7,{W},0)",
            f"if($f1==1&&$f2>=0,max(({L})-$f2,0),0)"]                      # reverse: wait until it has all been heard
    ex("gc_par", " ; ".join(outs).replace(" ; ", " " + BS + "; "), x, y + 770)
    P.obj("gc_kind", "sel 3", x, y + 740)                                # 3 = stretch: its own voice
    P.c("gc_mode_ok", "gc_kind", 1, 0); P.c("gc_kind", "gc_par", 1, 0)
    P.msg("gc_kind4", "3", x + 60, y + 755); P.c("gc_kind", "gc_kind4", 0, 0)
    # stretch (Ghost capture, Ableton warp pushed far): the sound smeared 4-12x slower in 100 ms grains, pitch
    # unchanged, fading out slowly. $f2 ms since the onset (-1 free), $f3-$f5, $f7 randoms. Out: L, d0, rate, place.
    sd0 = f"if($f2>=0,$f2+{GC_PREROLL_MS},300+2.5*$f4)"
    srate = "(0.08+0.17*$f5/1000)"
    souts = [f"min(1500+2000*$f3/1000,(3850-({sd0}))/(1-{srate}))", sd0, srate,
             "if($f7<500,-(0.45+0.55*$f7/500),0.45+0.55*($f7-500)/500)"]
    ex("gc_spar", " ; ".join(souts).replace(" ; ", " " + BS + "; "), x + 700, y + 770)
    P.c("gc_kind4", "gc_spar")
    P.c("gc_rn0", "gc_spar", 0, 6); P.c("gc_rn1", "gc_spar", 0, 4); P.c("gc_rn2", "gc_spar", 0, 3); P.c("gc_rn3", "gc_spar", 0, 2)
    P.c("gc_el2", "gc_spar", 0, 1)
    P.obj("gc_spack", "pack f f f f", x + 700, y + 800)
    for k in range(3, 0, -1):
        P.c("gc_spar", "gc_spack", k, k)
    P.c("gc_spar", "gc_spack", 0, 0)
    voice_switch("gcs", "gc_vc_scls", 700, x + 900, y + 820)
    P.c("gc_fire", "gc_rn0", 6, 0); P.c("gc_rn0", "gc_par", 0, 6)
    P.c("gc_fire", "gc_rn1", 5, 0); P.c("gc_rn1", "gc_par", 0, 4)
    P.c("gc_fire", "gc_rn2", 4, 0); P.c("gc_rn2", "gc_par", 0, 3)
    P.c("gc_fire", "gc_rn3", 3, 0); P.c("gc_rn3", "gc_par", 0, 2)
    P.c("gc_fire", "gc_timer", 2, 1); P.c("gc_el2", "gc_par", 0, 1)
    P.c("gc_fire", "gc_st_rd", 1, 0); P.c("gc_st2", "gc_par", 0, 5)
    P.obj("gc_pack", "pack " + " ".join(["f"] * len(outs)), x, y + 810)
    for k in range(len(outs) - 1, 0, -1):
        P.c("gc_par", "gc_pack", k, k)
    P.c("gc_par", "gc_pack", 0, 0)
    # a reverse waits (the last number) until the sound has been heard; then the voice plays it. One voice: a glitch
    # arriving while another plays fades that one out over 4 ms before jumping, so there is no click.
    P.obj("gc_wait", f"list split {len(outs) - 1}", x + 100, y + 825)
    P.obj("gc_wait_del", "del", x + 200, y + 840)
    P.obj("gc_wait_l", "list", x + 100, y + 855)
    P.c("gc_pack", "gc_wait"); P.c("gc_wait", "gc_wait_del", 1, 0); P.c("gc_wait", "gc_wait_l", 0, 1)
    P.c("gc_wait_del", "gc_wait_l")
    # Glitch voice (`gc_voice`): Legacy (0) = the voices below, one at a time; New (1, the default) = dcs_glitch~ (our own C object:
    # several at once, each starting on its exact sample, and a stretch whose grains line up with each other)
    P.obj("gc_vc_vr", "r gc_voice", x + 300, y + 780)
    ex("gc_vc_ex", "$f1==0 ; $f1>=1".replace(" ; ", " " + BS + "; "), x + 300, y + 800)
    P.c("gc_vc_vr", "gc_vc_ex")
    for nm, init, src, sel in (("gc_vc_cls", 0, "gc_wait_l", 0), ("gc_vc_new", 1, "gc_wait_l", 1),
                               ("gc_vc_scls", 0, "gc_spack", 0), ("gc_vc_snew", 1, "gc_spack", 1)):
        P.obj(nm, f"spigot {init}", x + 400 + 60 * len(nm), y + 830)
        P.c(src, nm); P.c("gc_vc_ex", nm, sel, 1)
    P.obj("gc_vc_pre", "list prepend slice", x + 500, y + 860); P.obj("gc_vc_trim", "list trim", x + 500, y + 880)
    P.c("gc_vc_new", "gc_vc_pre"); P.c("gc_vc_pre", "gc_vc_trim"); P.c("gc_vc_trim", "gc_dcs")
    P.obj("gc_vc_spre", "list prepend stretch", x + 650, y + 860); P.obj("gc_vc_strim", "list trim", x + 650, y + 880)
    P.c("gc_vc_snew", "gc_vc_spre"); P.c("gc_vc_spre", "gc_vc_strim"); P.c("gc_vc_strim", "gc_dcs")
    P.obj("gc_dcs", f"dcs_glitch~ {GC_BUF_MS} {GC_NEW_VOICES}", x + 500, y + 900)
    P.c("gc_src", "gc_dcs")
    if os.environ.get("GC_DEBUG"):
        P.obj("gc_dbg1", "s gc_packed", x + 400, y + 920); P.c("gc_pack", "gc_dbg1")
        P.obj("gc_dbg2", "s gc_parL", x + 500, y + 920); P.c("gc_par", "gc_dbg2")
        P.obj("gc_dbg3", "s gc_event", x + 600, y + 920); P.c("gc_wait_l", "gc_dbg3")
        P._probes = ["gc0_d", "gc0_rd", "gc0_ew", "gc0_scm", "gc0_s1", "gc0_win", "gc0_env"]
    P.obj("gc_vc_t", "t l b", x, y + 870)
    P.msg("gc_vc_fade", "0 4", x + 100, y + 890)
    P.obj("gc_vc_l", "list", x, y + 910)
    P.obj("gc_vc_del", "del 5", x + 100, y + 910)
    P.obj("gc_vc_6", "list split 6", x, y + 860)                          # (Legacy: the first 6, no extras)
    P.c("gc_vc_cls", "gc_vc_6"); P.c("gc_vc_6", "gc_vc_t"); P.c("gc_vc_t", "gc_vc_fade", 1, 0); P.c("gc_vc_t", "gc_vc_del", 1, 0)
    P.c("gc_vc_t", "gc_vc_l", 0, 1); P.c("gc_vc_del", "gc_vc_l")
    voice_switch("gcv", "gc_vc_l", 100, x + 300, y + 900)
    P.obj("gc_dbg_mode", "s gc_mode", x + 200, y + 700); P.c("gc_mode_ok", "gc_dbg_mode", 1, 0)
    outs_l, outs_r = [], []
    for k in range(1):
        vx, vy = x + 1200, y + 600
        P.obj(f"gc{k}_up", "unpack f f f f f f", vx, vy)
        P.c("gc_vc_l", f"gc{k}_up")
        P.obj("grp_gcv_r", "r sw_gcv", 10, 10); P.obj("grp_gcv_sw", "switch~", 10, 40); P.c("grp_gcv_r", "grp_gcv_sw")
        P.obj(f"gc{k}_t", "vline~", vx, vy + 90)                           # ms into the glitch
        # read position (ms back): d0 + a*t - c*fmod(t,S), built from plain objects (expr~ costs far more per
        # sample). Stutter: a=1 c=1 (back to the slice start every S ms); reverse: a=2; shuffle: both 0.
        # The unpack gives L, d0, c1, c2, S, place: a = 1-c1, c = c2.
        P.obj(f"gc{k}_a", "expr 1-$f1".replace("$", BS + "$"), vx + 60, vy + 40)
        P.c(f"gc{k}_up", f"gc{k}_a", 2, 0)
        P.obj(f"gc{k}_at", "*~ 0", vx, vy + 120); P.c(f"gc{k}_t", f"gc{k}_at"); P.c(f"gc{k}_a", f"gc{k}_at", 0, 1)
        P.obj(f"gc{k}_invS", "expr 1/$f1".replace("$", BS + "$"), vx + 180, vy + 40)
        P.c(f"gc{k}_up", f"gc{k}_invS", 4, 0)
        P.obj(f"gc{k}_ts", "*~ 0", vx + 120, vy + 120); P.c(f"gc{k}_t", f"gc{k}_ts"); P.c(f"gc{k}_invS", f"gc{k}_ts", 0, 1)
        P.obj(f"gc{k}_wr", "wrap~", vx + 120, vy + 140); P.c(f"gc{k}_ts", f"gc{k}_wr")
        P.obj(f"gc{k}_f", "*~ 0", vx + 120, vy + 160); P.c(f"gc{k}_wr", f"gc{k}_f"); P.c(f"gc{k}_up", f"gc{k}_f", 4, 1)   # fmod(t,S)
        P.obj(f"gc{k}_cn", "* -1", vx + 240, vy + 40); P.c(f"gc{k}_up", f"gc{k}_cn", 3, 0)
        P.obj(f"gc{k}_ctf", "*~ 0", vx + 180, vy + 180); P.c(f"gc{k}_f", f"gc{k}_ctf"); P.c(f"gc{k}_cn", f"gc{k}_ctf", 0, 1)
        P.sig2(f"gc{k}_s2", "+~", f"gc{k}_at", f"gc{k}_ctf", vx, vy + 200)
        P.obj(f"gc{k}_dd", "+~ 0", vx, vy + 210); P.c(f"gc{k}_s2", f"gc{k}_dd"); P.c(f"gc{k}_up", f"gc{k}_dd", 1, 1)
        P.obj(f"gc{k}_d", "clip~ 1 3990", vx, vy + 220); P.c(f"gc{k}_dd", f"gc{k}_d")
        P.obj(f"gc{k}_rd", "delread4~ gc_buf", vx, vy + 230)
        P.c(f"gc{k}_d", f"gc{k}_rd")
        # each stutter slice fades in and out over 3 ms, so the jumps back don't click
        P.obj(f"gc{k}_w1", "*~ 0.333", vx + 250, vy + 170); P.c(f"gc{k}_f", f"gc{k}_w1")
        P.obj(f"gc{k}_w1c", "clip~ 0 1", vx + 250, vy + 180); P.c(f"gc{k}_w1", f"gc{k}_w1c")
        P.obj(f"gc{k}_w2", "*~ -0.333", vx + 320, vy + 170); P.c(f"gc{k}_f", f"gc{k}_w2")
        P.obj(f"gc{k}_S3", "/ 3", vx + 320, vy + 40); P.c(f"gc{k}_up", f"gc{k}_S3", 4, 0)
        P.obj(f"gc{k}_w2o", "+~ 0", vx + 320, vy + 180); P.c(f"gc{k}_w2", f"gc{k}_w2o"); P.c(f"gc{k}_S3", f"gc{k}_w2o", 0, 1)
        P.obj(f"gc{k}_w2c", "clip~ 0 1", vx + 320, vy + 190); P.c(f"gc{k}_w2o", f"gc{k}_w2c")
        P.sig2(f"gc{k}_win", "*~", f"gc{k}_w1c", f"gc{k}_w2c", vx + 250, vy + 200)
        P.obj(f"gc{k}_L", "t f f", vx, vy + 30)
        P.c(f"gc{k}_up", f"gc{k}_L", 0, 0)
        P.msg(f"gc{k}_tmsg", f"0 {BS}, {BS}$1 {BS}$1".replace(f"{BS},", f" {BS},").replace("  ", " "), vx, vy + 60)
        P.obj(f"gc{k}_Lm", "- 25", vx + 100, vy + 45)
        P.msg(f"gc{k}_emsg", f"1 5 {BS}, 0 20 {BS}$1", vx + 100, vy + 60)
        P.obj(f"gc{k}_env", "vline~", vx + 100, vy + 90)
        P.c(f"gc{k}_L", f"gc{k}_Lm", 1, 0); P.c(f"gc{k}_Lm", f"gc{k}_emsg"); P.c(f"gc{k}_emsg", f"gc{k}_env")
        P.c(f"gc{k}_L", f"gc{k}_tmsg", 0, 0); P.c(f"gc{k}_tmsg", f"gc{k}_t")
        P.c("gc_vc_fade", f"gc{k}_env")
        P.sig2(f"gc{k}_ew", "*~", f"gc{k}_env", f"gc{k}_win", vx + 100, vy + 150)
        P.sig2(f"gc{k}_s", "*~", f"gc{k}_rd", f"gc{k}_ew", vx, vy + 180)
        # where it sits (set while the voice is silent; the place comes out of unpack first): always at least
        # halfway out to one side, the near ear at full level, the far ear quieter and up to 0.6 ms later (the
        # time difference is what makes a sound seem really off to the side)
        ex(f"gc{k}_pan", "min(1,1-$f1) ; min(1,1+$f1) ; 1.5+0.6*max($f1,0) ; 1.5+0.6*max(-$f1,0)".replace(" ; ", " " + BS + "; "), vx + 250, vy + 30)
        P.c(f"gc{k}_up", f"gc{k}_pan", 5, 0)
        P.obj(f"gc{k}_gl", "sig~", vx + 250, vy + 60); P.obj(f"gc{k}_gr", "sig~", vx + 320, vy + 60)
        P.obj(f"gc{k}_itd", "delwrite~ gc_itd 10", vx, vy + 215); P.c(f"gc{k}_s", f"gc{k}_itd")
        P.obj(f"gc{k}_rl", "delread~ gc_itd 1.5", vx, vy + 225)            # whole samples are fine for this
        P.obj(f"gc{k}_rr", "delread~ gc_itd 1.5", vx + 100, vy + 225)
        for o, n in enumerate(("gl", "gr", "rl", "rr")):
            P.c(f"gc{k}_pan", f"gc{k}_{n}", o, 0)
        outs_l.append(P.sig2(f"gc{k}_l", "*~", f"gc{k}_rl", f"gc{k}_gl", vx, vy + 240))
        outs_r.append(P.sig2(f"gc{k}_r", "*~", f"gc{k}_rr", f"gc{k}_gr", vx + 100, vy + 240))
    # the stretch voice: two overlapping Hann-windowed grains; the grain start creeps forward at `rate`, each grain
    # plays at normal speed, so time slows and pitch stays. Its own place between the ears (no time offset).
    vx, vy = x + 1800, y + 600
    P.obj("gc2_up", "unpack f f f f", vx, vy); P.c("gc_vc_scls", "gc2_up")
    P.obj("grp_gcs_r", "r sw_gcs", 10, 10); P.obj("grp_gcs_sw", "switch~", 10, 40); P.c("grp_gcs_r", "grp_gcs_sw")
    P.obj("gc2_L", "t f f", vx, vy + 30); P.c("gc2_up", "gc2_L", 0, 0)
    P.msg("gc2_tmsg", f"0 {BS}, {BS}$1 {BS}$1", vx, vy + 60)
    P.obj("gc2_t", "vline~", vx, vy + 90)
    P.obj("gc2_Lm", "- 650", vx + 100, vy + 45)
    P.msg("gc2_emsg", f"1 40 {BS}, 0 600 {BS}$1", vx + 100, vy + 60)
    P.obj("gc2_env", "vline~", vx + 100, vy + 90)
    P.c("gc2_L", "gc2_Lm", 1, 0); P.c("gc2_Lm", "gc2_emsg"); P.c("gc2_emsg", "gc2_env")
    P.c("gc2_L", "gc2_tmsg", 0, 0); P.c("gc2_tmsg", "gc2_t")
    P.obj("gc2_k", "expr 1-$f1 ; (1-$f1)*100".replace("$", BS + "$").replace(" ; ", " " + BS + "; "), vx + 200, vy + 30)
    P.c("gc2_up", "gc2_k", 2, 0)
    P.obj("gc2_Dt", "*~ 0", vx, vy + 120); P.c("gc2_t", "gc2_Dt"); P.c("gc2_k", "gc2_Dt", 0, 1)
    P.obj("gc2_D", "+~ 0", vx, vy + 140); P.c("gc2_Dt", "gc2_D"); P.c("gc2_up", "gc2_D", 1, 1)
    P.obj("gc2_ph", "phasor~ 10", vx + 200, vy + 120)
    P.obj("gc2_ph2o", "+~ 0.5", vx + 300, vy + 120); P.obj("gc2_ph2", "wrap~", vx + 300, vy + 140)
    P.c("gc2_ph", "gc2_ph2o"); P.c("gc2_ph2o", "gc2_ph2")
    taps = []
    for n, ph in (("a", "gc2_ph"), ("b", "gc2_ph2")):
        tx = vx + (0 if n == "a" else 200)
        P.obj(f"gc2_{n}k", "*~ 0", tx, vy + 170); P.c(ph, f"gc2_{n}k"); P.c("gc2_k", f"gc2_{n}k", 1, 1)
        P.sig2(f"gc2_{n}d0", "-~", "gc2_D", f"gc2_{n}k", tx, vy + 190)
        P.obj(f"gc2_{n}d", "clip~ 1.5 3990", tx, vy + 210); P.c(f"gc2_{n}d0", f"gc2_{n}d")
        P.obj(f"gc2_{n}rd", "delread4~ gc_buf", tx, vy + 230); P.c(f"gc2_{n}d", f"gc2_{n}rd")
        P.obj(f"gc2_{n}cos", "cos~", tx + 100, vy + 170); P.c(ph, f"gc2_{n}cos")
        P.obj(f"gc2_{n}cn", "*~ -0.5", tx + 100, vy + 190); P.c(f"gc2_{n}cos", f"gc2_{n}cn")
        P.obj(f"gc2_{n}w", "+~ 0.5", tx + 100, vy + 210); P.c(f"gc2_{n}cn", f"gc2_{n}w")
        taps.append(P.sig2(f"gc2_{n}g", "*~", f"gc2_{n}rd", f"gc2_{n}w", tx, vy + 250))
    P.sig2("gc2_sum", "+~", taps[0], taps[1], vx, vy + 270)
    P.sig2("gc2_s", "*~", "gc2_sum", "gc2_env", vx, vy + 290)
    ex("gc2_pan", "min(1,1-$f1) ; min(1,1+$f1)".replace(" ; ", " " + BS + "; "), vx + 300, vy + 30)
    P.c("gc2_up", "gc2_pan", 3, 0)
    P.obj("gc2_gl", "sig~", vx + 300, vy + 60); P.obj("gc2_gr", "sig~", vx + 370, vy + 60)
    P.c("gc2_pan", "gc2_gl", 0, 0); P.c("gc2_pan", "gc2_gr", 1, 0)
    P.sig2("gc2_l", "*~", "gc2_s", "gc2_gl", vx, vy + 310); P.sig2("gc2_r", "*~", "gc2_s", "gc2_gr", vx + 100, vy + 310)
    P.sig2("gc_mix_l0", "+~", outs_l[0], "gc2_l", vx, vy + 320); P.sig2("gc_mix_r0", "+~", outs_r[0], "gc2_r", vx + 100, vy + 320)
    P.obj("gc_mix_l", "+~", vx, vy + 330); P.c("gc_mix_l0", "gc_mix_l"); P.c("gc_dcs", "gc_mix_l", 0, 1)
    P.obj("gc_mix_r", "+~", vx + 100, vy + 330); P.c("gc_mix_r0", "gc_mix_r"); P.c("gc_dcs", "gc_mix_r", 1, 1)
    outs_l[0], outs_r[0] = "gc_mix_l", "gc_mix_r"
    P.sig2("gc_pre_mono", "+~", "gc_mix_l", "gc_mix_r", vx, vy + 340)    # (for the glitch loops: before the level)
    for i, sig in enumerate(getattr(P, "_probes", [])):
        P.obj(f"gcdbg_E{i}", "env~ 4096", x + 300 + 60 * i, y + 1000); P.obj(f"gcdbg_S{i}", f"s dbg_{sig}", x + 300 + 60 * i, y + 1030)
        P.c(sig, f"gcdbg_E{i}"); P.c(f"gcdbg_E{i}", f"gcdbg_S{i}")
    lvl = smoothed(P, "gc_level", "gc_level", x + 1200, y + 900)
    P.sig2("gcx_out_l", "*~", outs_l[0], lvl, x + 1200, y + 950)
    P.sig2("gcx_out_r", "*~", outs_r[0], lvl, x + 1300, y + 950)
    return "gcx_out_l", "gcx_out_r"

def add_onset_switch(P, x=2600, y=20):
    """Sudden-sound detector (`onset_mode`, Oct 17): Classic = the two level-jump detectors (gc_det_sel for glitches
    and reverse reverb, fs_det_sel for the found kit and found room notes); New = dcs_onset~ (spectral flux: fires on
    new sounds, not on swells or vibrato, and catches claps in a loud room) for both. Everything that listened to
    either detector now listens to on_gc / on_fs, fed by whichever is chosen."""
    for old, new in (("gc_det_sel", "on_gc"), ("fs_det_sel", "on_fs")):
        P.conns = [(new if (a == old and o == 0) else a, o, b, i) for a, o, b, i in P.conns]
    P.obj("on_gc", "t b", x, y + 100); P.obj("on_fs", "t b", x + 100, y + 100)
    P.msg("on_one", "1", x, y + 120); P.obj("on_heard", "s onset_heard", x, y + 140)    # (each one, for the tests)
    P.c("on_gc", "on_one"); P.c("on_one", "on_heard")
    P.obj("on_gc_c", "spigot 1", x, y + 70); P.obj("on_fs_c", "spigot 1", x + 100, y + 70)
    P.c("gc_det_sel", "on_gc_c"); P.c("on_gc_c", "on_gc"); P.c("fs_det_sel", "on_fs_c"); P.c("on_fs_c", "on_fs")
    P.obj("on_dcs", "dcs_onset~", x + 200, y + 40); P.c("mic", "on_dcs")
    P.group("onsetn", r"^on_dcs$")                                       # (only runs when chosen)
    add_switch(P, "onsetn", ("onset_mode",), 100)
    P.obj("on_new", "spigot 0", x + 200, y + 70); P.c("on_dcs", "on_new"); P.c("on_new", "on_gc"); P.c("on_new", "on_fs")
    P.obj("on_r", "r onset_mode", x, y)
    P.obj("on_sel", "expr $f1==0 ; $f1>=1".replace("$", BS + "$").replace(" ; ", " " + BS + "; "), x, y + 20)
    P.c("on_r", "on_sel"); P.c("on_sel", "on_gc_c", 0, 1); P.c("on_sel", "on_fs_c", 0, 1); P.c("on_sel", "on_new", 1, 1)

def add_reverse_reverb(P, x=2600, y=1300):
    """Reverse reverb (roadmap item 7, DadCraft Oct 13; for the Ghost realm): a sudden sound books one swell: its own
    reverb tail, L ms of it, played backwards so it rises into the moment of the sound. A tail can only be reversed
    once it has been heard, so the swell comes later and peaks at time B >= now + 2L: while the beat runs on the tempo
    clock (msync, DadCraft: "time the swells to the beat") L is one beat and B the first beat that far away, so every
    swell lands on a beat; otherwise L = RR_MIN_MS + RR_SIZE_MS x room size and B = now + 2L. The read position runs
    backwards at normal speed: delay 2t - B - c, from B - c - 2L up to B - c. One swell at a time (a sound while one is
    booked or playing is ignored); handling noise books nothing. reverb_rev 0..1 (Reverb section); it needs the reverb.
    Returns the output (both ears, in the middle)."""
    ex = lambda n, t, xx, yy: P.obj(n, "expr " + t.replace(",", BS + ",").replace("$", BS + "$"), xx, yy)
    P.sig2("rr_src0", "+~", "rev_l", "rev_r", x, y)
    P.obj("rr_src", "*~ 0.5", x, y + 15); P.c("rr_src0", "rr_src")
    P.obj("rr_wr", f"delwrite~ rr_buf {RR_BUF_MS}", x, y + 30); P.c("rr_src", "rr_wr")
    # what it needs to know: room size, synced?, the beat length, the time since the last beat
    P.obj("rr_r_size", "r reverb_size", x + 300, y); P.obj("rr_size_v", "value rr_size", x + 300, y + 15); P.c("rr_r_size", "rr_size_v")
    P.obj("rr_r_sync", "r msync", x + 400, y); P.obj("rr_sync_v", "value rr_sync", x + 400, y + 15); P.c("rr_r_sync", "rr_sync_v")
    P.obj("rr_r_amt", "r reverb_rev", x + 500, y); P.obj("rr_amt_v", "value rr_amt", x + 500, y + 15); P.c("rr_r_amt", "rr_amt_v")
    P.obj("rr_r_beat", "r tempo", x + 600, y); P.obj("rr_beat_tm", "timer", x + 600, y + 30)
    P.obj("rr_beat_b", "t b", x + 600, y + 15); P.c("rr_r_beat", "rr_beat_b"); P.c("rr_beat_b", "rr_beat_tm", 0, 0)
    # a sudden sound (not handling noise), while on and not busy
    on = handling_gate(P, "rr_hg", "gc_det_sel", x, y + 60)
    P.obj("rr_free", "spigot 1", x, y + 110); P.c(on, "rr_free")
    ex("rr_want", "(rr_amt>0)", x, y + 125); P.c("rr_free", "rr_want")
    P.obj("rr_want_s", "sel 1", x, y + 140); P.c("rr_want", "rr_want_s")
    P.obj("rr_go", "t b b b", x, y + 155); P.c("rr_want_s", "rr_go")
    P.msg("rr_busy", "0", x + 100, y + 170); P.c("rr_go", "rr_busy", 2, 0); P.c("rr_busy", "rr_free", 0, 1)
    P.c("rr_go", "rr_beat_tm", 1, 1)                                     # how long since the last beat
    # out: L ; the wait until the swell starts (B - L) ; the delay at its start (B - c - 2L)
    Pb, Ls = "max(tc_P,100)", f"min(max(tc_P,100),{RR_MAX_MS})"             # (a beat, guarded; L while synced)
    nb = f"({Pb}-fmod($f1,{Pb}))"                                          # time to the next beat
    ex("rr_plan", f"if(rr_sync>0,{Ls},{RR_MIN_MS}+{RR_SIZE_MS}*rr_size) ; "
                  f"if(rr_sync>0,{nb}+{Pb}*ceil(max(0,2*{Ls}-{nb})/{Pb}),2*({RR_MIN_MS}+{RR_SIZE_MS}*rr_size))"
                  .replace(" ; ", " " + BS + "; "), x, y + 190)
    P.c("rr_beat_tm", "rr_plan")
    # (B is the plan's second outlet; the wait is B - L, the delay ramp B - 2L -> B over L)
    P.obj("rr_B", "f", x + 150, y + 210); P.obj("rr_L", "f", x + 250, y + 210)
    P.c("rr_plan", "rr_B", 1, 1); P.c("rr_plan", "rr_L", 0, 1)
    P.c("rr_go", "rr_plan_t", 0, 0)
    P.obj("rr_plan_t", "t b b", x, y + 225)                              # (after the plan is in)
    P.c("rr_plan_t", "rr_B", 0, 0)
    # (the read ends RR_LEAD_MS before the sound was noticed: detection and the handling check take that long, so the
    # swell rises right into the sound's own start)
    ex("rr_sched", f"$f1-rr_L_v ; $f1-2*rr_L_v+{RR_LEAD_MS} ; $f1+{RR_LEAD_MS} ; rr_L_v".replace(" ; ", " " + BS + "; "), x + 150, y + 240)
    P.obj("rr_Lw", "value rr_L_v", x + 250, y + 230); P.c("rr_L", "rr_Lw")
    P.c("rr_plan_t", "rr_L", 1, 0)                                       # (L first: written to rr_L_v, then B)
    P.c("rr_B", "rr_sched")
    # start after the wait: the delay ramp and the envelope; free again when it ends
    P.obj("rr_d0", "f", x + 150, y + 270); P.obj("rr_d1", "f", x + 250, y + 270); P.obj("rr_Ld", "f", x + 350, y + 270)
    P.c("rr_sched", "rr_Ld", 3, 1); P.c("rr_sched", "rr_d1", 2, 1); P.c("rr_sched", "rr_d0", 1, 1)
    P.obj("rr_wait", "del", x + 50, y + 270); P.c("rr_sched", "rr_wait", 0, 0)
    P.obj("rr_start", "t b b b", x + 50, y + 290); P.c("rr_wait", "rr_start")
    P.c("rr_start", "rr_Ld", 0, 0); P.c("rr_start", "rr_d1", 1, 0); P.c("rr_start", "rr_d0", 2, 0)
    P.obj("rr_pk", "pack f f f", x + 150, y + 310)
    P.c("rr_Ld", "rr_pk", 0, 0); P.c("rr_d1", "rr_pk", 0, 2); P.c("rr_d0", "rr_pk", 0, 1)   # (L last: the hot inlet)
    P.msg("rr_dmsg", f"{BS}$2 {BS}, {BS}$3 {BS}$1", x + 150, y + 330)
    P.msg("rr_emsg", f"0 {BS}, 1 {RR_FADE_IN_MS} {BS}, 0 {RR_FADE_OUT_MS} {BS}$1", x + 300, y + 330)
    P.obj("rr_emt", f"- {RR_FADE_OUT_MS}", x + 300, y + 315)
    P.c("rr_pk", "rr_dmsg"); P.c("rr_Ld", "rr_emt"); P.c("rr_emt", "rr_emsg")
    P.obj("rr_dl", "vline~", x + 150, y + 350); P.c("rr_dmsg", "rr_dl")
    P.obj("rr_env", "vline~", x + 300, y + 350); P.c("rr_emsg", "rr_env")
    P.obj("rr_end", "del", x + 450, y + 330); P.c("rr_Ld", "rr_end")
    P.msg("rr_free1", "1", x + 450, y + 350); P.c("rr_end", "rr_free1"); P.c("rr_free1", "rr_free", 0, 1)
    P.obj("rr_rd", "delread4~ rr_buf", x + 150, y + 370); P.c("rr_dl", "rr_rd")
    P.sig2("rr_sound", "*~", "rr_rd", "rr_env", x + 150, y + 390)
    amt = smoothed(P, "reverb_rev", "reverb_rev", x + 700, y)
    P.obj("rr_gain", f"*~ {RR_GAIN}", x + 700, y + 30); P.c(amt, "rr_gain")
    return P.sig2("rr_out", "*~", "rr_sound", "rr_gain", x + 150, y + 410)
