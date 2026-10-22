# SPDX-License-Identifier: GPL-3.0-or-later
"""What the phone senses: pocket rustle, handling noise, the clip watch for Diagnostics."""
from .constants import *  # noqa: F401,F403 (the tuning numbers)
from .core import *  # noqa: F401,F403


def add_pocket_rustle(P, x=1300, y=170):
    """Pocket only (both 0 = as before; Oct 17, DadCraft: hush cut the world with the rustle): Rustle squash
    (pocket_rustle) = dcs_derustle~ on the compressed mic pulls sudden bursts back toward the room's floor; Step duck
    (pocket_steps) dips the mic around each footfall (now, and around the next one due). Returns the gain signal
    (1 = untouched) that joins hush_ln."""
    def ex(name, text, xx, yy):
        return P.obj(name, "expr " + text.replace(",", BS + ",").replace("$", BS + "$").replace(" ; ", " " + BS + "; "), xx, yy)
    P.obj("pr_rp", "r pocket", x, y); P.obj("pr_rr", "r pocket_rustle", x + 80, y); P.obj("pr_rs", "r pocket_steps", x + 200, y)
    P.obj("pr_vp", "value prpk", x, y + 15); P.obj("pr_vs", "value prst", x + 200, y + 15)
    P.c("pr_rp", "pr_vp"); P.c("pr_rs", "pr_vs")
    # rustle squash: amount = pocket x slider
    ex("pr_amt", "$f1*$f2", x, y + 40)
    P.obj("pr_rt", "t b f", x + 80, y + 20); P.c("pr_rr", "pr_rt"); P.c("pr_rt", "pr_amt", 1, 1); P.c("pr_rt", "pr_amt", 0, 0)
    P.c("pr_rp", "pr_amt")
    P.msg("pr_am", f"amount {BS}$1", x, y + 60); P.c("pr_amt", "pr_am")
    P.obj("pr_dr", f"dcs_derustle~ 0 {RUSTLE_ABOVE_DB} {RUSTLE_RELEASE_MS}", x, y + 80)
    P.c("mic_comp1", "pr_dr"); P.c("pr_am", "pr_dr")
    # step duck: each step (its interval, s) dips now and books a dip around the next one (none past 1.5 s)
    P.obj("pr_st", "r step", x + 300, y)
    ex("pr_sd", f"pow(10,-prpk*prst*{SD_DB}/20) ; if($f1<1.5,$f1*1000-{SD_LEAD_MS},100000) ; "
                f"if($f1<1.5,$f1*1000-{SD_LEAD_MS}+{SD_HOLD_MS},100000)", x + 300, y + 20)
    P.c("pr_st", "pr_sd")
    P.obj("pr_sp", "pack f f f", x + 300, y + 40)
    for k in (2, 1, 0):
        P.c("pr_sd", "pr_sp", k, k)
    P.msg("pr_sm", f"{BS}$1 10 {BS}, 1 {SD_RELEASE_MS} {SD_NOW_MS} {BS}, {BS}$1 15 {BS}$2 {BS}, 1 {SD_RELEASE_MS} {BS}$3", x + 300, y + 60)
    P.obj("pr_sl", "vline~", x + 300, y + 80)
    P.c("pr_sp", "pr_sm"); P.c("pr_sm", "pr_sl")
    P.obj("pr_lb", "loadbang", x + 450, y + 40); P.msg("pr_one", "1", x + 450, y + 60)
    P.c("pr_lb", "pr_one"); P.c("pr_one", "pr_sl")
    P.obj("pr_g", "*~", x, y + 100); P.c("pr_dr", "pr_g", 1, 0); P.c("pr_sl", "pr_g", 0, 1)
    return "pr_g"

def add_handling(P, x=2200, y=300):
    """Handling noise (taps, grips, rubbing; the app's HandlingDetector): [handling 0..1( turns the mic down by up to
    handling_hush x HND_HUSH_DB (fast down, slower up), and [jolt( marks the next HND_JOLT_MS as handling for the
    gates (value hjolt). value hlev = the level. Returns the gain signal."""
    # the app's handling level (happ): the mic goes down by it times Handling hush, and value hlev feeds the gates
    P.obj("hnd_r", "r handling", x, y); P.obj("hnd_av", "value happ", x + 100, y + 15); P.c("hnd_r", "hnd_av")
    P.obj("hnd_hr", "r handling_hush", x + 200, y); P.obj("hnd_hv", "value hhush", x + 200, y + 15); P.c("hnd_hr", "hnd_hv")
    P.obj("hnd_go", "t b", x, y + 15); P.c("hnd_r", "hnd_go")
    P.obj("hnd_calc", ("expr pow(10, -happ*hhush*" + str(HND_HUSH_DB) + "/20) ; happ")
          .replace(",", BS + ",").replace(" ; ", " " + BS + "; "), x, y + 30)
    P.c("hnd_go", "hnd_calc")
    P.obj("hnd_v", "value hlev", x + 100, y + 45); P.c("hnd_calc", "hnd_v", 1, 0)
    P.obj("hnd_g", "f", x, y + 38); P.c("hnd_calc", "hnd_g", 0, 0)
    P.obj("hnd_t", "t f f f", x, y + 45); P.c("hnd_g", "hnd_t")
    P.obj("hnd_tm", f"expr if($f1<$f2, {HND_ATTACK_MS}, {HND_RELEASE_MS})".replace("$", BS + "$").replace(",", BS + ","), x + 100, y + 60)
    P.obj("hnd_pk", "pack f 0", x, y + 75); P.obj("hnd_ln", "line~", x, y + 90)
    P.c("hnd_t", "hnd_tm", 2, 0); P.c("hnd_tm", "hnd_pk", 0, 1)           # down or up? (with the gain before)
    P.c("hnd_t", "hnd_tm", 1, 1)                                          # then this is the gain before
    P.c("hnd_t", "hnd_pk", 0, 0); P.c("hnd_pk", "hnd_ln")
    P.obj("hnd_lb", "loadbang", x + 300, y); P.msg("hnd_one", "1", x + 300, y + 15)
    P.c("hnd_lb", "hnd_one"); P.c("hnd_one", "hnd_ln"); P.c("hnd_one", "hnd_tm", 0, 1)
    # a jolt: handling for the next HND_JOLT_MS (restarted by each one), and it spoils a catch being recorded
    P.obj("hnd_jr", "r jolt", x + 400, y); P.obj("hnd_jt", "t b b b", x + 400, y + 15); P.c("hnd_jr", "hnd_jt")
    P.msg("hnd_j1", "1", x + 400, y + 30); P.obj("hnd_jv", "value hjolt", x + 400, y + 45)
    P.c("hnd_jt", "hnd_j1", 2, 0); P.c("hnd_j1", "hnd_jv")
    P.msg("hnd_f1", "1", x + 480, y + 30); P.obj("hnd_fv", "value fs_jv", x + 480, y + 45)
    P.c("hnd_jt", "hnd_f1", 1, 0); P.c("hnd_f1", "hnd_fv")
    P.obj("hnd_jd", f"del {HND_JOLT_MS}", x + 560, y + 30); P.c("hnd_jt", "hnd_jd", 0, 0)
    P.msg("hnd_j0", "0", x + 560, y + 45); P.obj("hnd_jv0", "value hjolt", x + 560, y + 60)
    P.c("hnd_jd", "hnd_j0"); P.c("hnd_j0", "hnd_jv0")
    return "hnd_ln"

def add_clip_watch(P, x=2200, y=20):
    """On-screen diagnostics: once a second, how many ~12 ms stretches (CLIP_WINDOW samples) held a sample at or
    over full scale, for three places: the mic as it arrives (the phone's own mic overloading, harsh, and Input gain
    can't help), after Input gain (over 1.0, soaked up by the compressor) and the output limiter's input (over 1.0,
    where it squashes hard). Sent as floats diag_clip_in, diag_clip_gain, diag_clip_out (in that order, out last).
    Cheap: abs, a threshold made from plain objects (x 1e9 then clipped to 0..1) and one env~ per place."""
    places = (("in", ["adc"], CLIP_IN), ("gain", ["mic"], 1.0), ("out", ["out_l", "out_r"], 1.0))
    for i, (n, srcs, thr) in enumerate(places):
        cx = x + 200 * i
        overs = []
        for j, src in enumerate(srcs):
            p = f"cw_{n}{j}"
            P.obj(f"{p}_abs", "abs~", cx + 60 * j, y); P.c(src, f"{p}_abs")
            P.obj(f"{p}_thr", f"-~ {thr}", cx + 60 * j, y + 15); P.c(f"{p}_abs", f"{p}_thr")
            P.obj(f"{p}_big", "*~ 1e+09", cx + 60 * j, y + 30); P.c(f"{p}_thr", f"{p}_big")
            P.obj(f"{p}_one", "clip~ 0 1", cx + 60 * j, y + 45); P.c(f"{p}_big", f"{p}_one")
            overs.append(f"{p}_one")
        if len(overs) == 2:
            P.sig2(f"cw_{n}_sum", "+~", overs[0], overs[1], cx, y + 60); overs = [f"cw_{n}_sum"]
        P.obj(f"cw_{n}_env", f"env~ {CLIP_WINDOW} {CLIP_WINDOW}", cx, y + 75); P.c(overs[0], f"cw_{n}_env")
        P.obj(f"cw_{n}_hit", "> 0", cx, y + 90); P.c(f"cw_{n}_env", f"cw_{n}_hit")   # (silence reads 0 dB)
        P.obj(f"cw_{n}_sel", "sel 1", cx, y + 105); P.c(f"cw_{n}_hit", f"cw_{n}_sel")
        P.obj(f"cw_{n}_cnt", "f", cx, y + 120); P.obj(f"cw_{n}_inc", "+ 1", cx + 40, y + 120)
        P.c(f"cw_{n}_sel", f"cw_{n}_cnt"); P.c(f"cw_{n}_cnt", f"cw_{n}_inc"); P.c(f"cw_{n}_inc", f"cw_{n}_cnt", 0, 1)
        P.obj(f"cw_{n}_now", "f", cx + 100, y + 135); P.c(f"cw_{n}_inc", f"cw_{n}_now", 0, 1)   # the count so far
        P.obj(f"cw_{n}_s", f"s diag_clip_{n}", cx + 100, y + 150); P.c(f"cw_{n}_now", f"cw_{n}_s")
        P.msg(f"cw_{n}_z", "0", cx, y + 150)
        P.c(f"cw_{n}_z", f"cw_{n}_cnt", 0, 1); P.c(f"cw_{n}_z", f"cw_{n}_now", 0, 1)
    # every second: report in, gain, out (right to left), then start counting again
    P.obj("cw_lb", "loadbang", x, y + 170); P.obj("cw_clock", "metro 1000", x, y + 185); P.c("cw_lb", "cw_clock")
    P.obj("cw_t", "t b b b b", x, y + 200); P.c("cw_clock", "cw_t")
    P.c("cw_t", "cw_in_now", 3, 0); P.c("cw_t", "cw_gain_now", 2, 0); P.c("cw_t", "cw_out_now", 1, 0)
    for n, _, _ in places:
        P.c("cw_t", f"cw_{n}_z", 0, 0)
