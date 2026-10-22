# SPDX-License-Identifier: GPL-3.0-or-later
"""Room in Stereo: the two mics in (mid and side) and the widened dry room out."""
from .constants import *  # noqa: F401,F403 (the tuning numbers)
from .core import *  # noqa: F401,F403


def add_stereo_in(P):
    """The room in stereo (Oct 22, DadCraft): with the switch on, the app opens the phone's two mics as left and
    right (the input preset that gives two mics, found per phone: StereoCalibration.java). The mid (their average)
    feeds everything that follows, exactly as the one mic did; the side (half their difference) only widens the dry
    room (add_stereo_out). The app sends st_r (1 on, 0 off: the right channel's share), st_mid (the trim / 2 on, 1
    off) and st_side (the trim / 2 on, 0 off). Off, the mid is the left channel times 1 and the side is 0, so the
    sound is exactly as before; with one input channel open the right one is silence anyway. Returns the mid."""
    P.obj("st_rw", "*~ 0", 120, 20); P.c("adc", "st_rw", 1, 0)
    P.obj("st_r_r", "r st_r", 180, 0); P.c("st_r_r", "st_rw", 0, 1)
    P.sig2("st_sum", "+~", "adc", "st_rw", 20, 40)
    P.obj("st_mid", "*~ 1", 20, 50); P.c("st_sum", "st_mid")
    P.obj("st_mid_r", "r st_mid", 80, 30); P.c("st_mid_r", "st_mid", 0, 1)
    P.obj("st_diff", "-~", 200, 40); P.c("adc", "st_diff", 0, 0); P.c("adc", "st_diff", 1, 1)
    P.obj("st_side0", "*~ 0", 200, 50); P.c("st_diff", "st_side0")
    P.obj("st_side_r", "r st_side", 260, 30); P.c("st_side_r", "st_side0", 0, 1)
    # Wind Guard (Oct 22, `wind_guard`, 0 = off: mid and side pass untouched): dcs_dewind~ pulls wind rumble down in
    # both, judged together, before the gate and compressor so gusts can't pump them
    P.obj("dw", "dcs_dewind~", 20, 60); P.c("st_mid", "dw", 0, 0); P.c("st_side0", "dw", 0, 1)
    P.obj("dw_r", "r wind_guard", 140, 45); P.msg("dw_amt", f"amount {BS}$1", 140, 55)
    P.c("dw_r", "dw_amt"); P.c("dw_amt", "dw")
    # the room listener (Oct 22, dcs_listen~; only listens for now, DadCraft: "show it first"): talking, music, traffic,
    # wind, twice a second to the app (room_kind), from the mic as it comes in and the dewinder's windiness
    P.obj("ls", "dcs_listen~", 20, 75); P.c("st_mid", "ls", 0, 0); P.c("dw", "ls", 2, 1)
    P.obj("ls_s", "s room_kind", 20, 90); P.c("ls", "ls_s")
    return "dw"

def add_stereo_out(P, gain, dry):
    """The side joins the ears: left = mid + side, right = mid - side. It takes the gains the mid takes on its way
    (input gain, gate, compressor, pocket hush, Dry) and the share of the room still plain: stereo_plain from the
    app, 1 with no tone-changing effect on, falling as they take over (a pitch-shifted room with a plain stereo edge
    would sound wrong). Delayed like the mid (the spatial stage's EAR_BASE_MS, over the one 64-sample block a delay
    line read before its write can't go under; Smooth distortion turned up adds 16 samples, sent by the app as st_extra),
    so the two cancel in the far ear: a sound only the left mic heard stays in the left ear (test_patch "stereo")."""
    chain = "dw"   # (dcs_dewind~'s second outlet: the side, wind pulled down like the mid's)
    for k, g in enumerate((gain, "ln_gate", "ln_comp", "hush_ln", dry)):
        chain = P.sig2(f"st_side{k + 1}", "*~", chain, g, 200, 400 + 15 * k)
        if k == 0:
            P.conns[-2] = ("dw", 1, f"st_side1", 0)
    P.obj("st_plain_r", "r stereo_plain", 300, 380)
    P.obj("st_plain_pk", "pack f 50", 300, 395); P.c("st_plain_r", "st_plain_pk")
    P.obj("st_plain", "line~", 300, 410); P.c("st_plain_pk", "st_plain")
    chain = P.sig2("st_side_plain", "*~", chain, "st_plain", 200, 480)
    P.obj("st_dw", "delwrite~ st_side_dl 20", 200, 495); P.c(chain, "st_dw")
    P.obj("st_dq_r", "r st_extra", 400, 465)
    P.obj("st_dq_init", "loadbang", 500, 450); P.msg("st_dq_0", "0", 500, 465)
    P.obj("st_dq_t", "t f b", 400, 480)                                  # the rate first (cold), then the delay
    P.obj("st_sr", "samplerate~", 460, 487)
    P.obj("st_dq_ms", f"expr {EAR_BASE_MS}+$f1*1000/$f2".replace("$", BS + "$"), 400, 495)
    P.c("st_dq_init", "st_dq_0"); P.c("st_dq_0", "st_dq_t"); P.c("st_dq_r", "st_dq_t")
    P.c("st_dq_t", "st_sr", 1, 0); P.c("st_sr", "st_dq_ms", 0, 1); P.c("st_dq_t", "st_dq_ms", 0, 0)
    P.obj("st_dr", "delread~ st_side_dl", 200, 510); P.c("st_dq_ms", "st_dr")
    P.obj("st_neg", "*~ -1", 260, 525); P.c("st_dr", "st_neg")
    P.c("st_dr", "out_l"); P.c("st_neg", "out_r")
