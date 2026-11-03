# SPDX-License-Identifier: GPL-3.0-or-later
"""Fixed music places as mid corrections and a side, with the old mono sum kept."""
from .constants import *  # noqa: F401,F403
from .core import *  # noqa: F401,F403


def add_width(P):
    P.obj("ms_r", "r music_stereo", 20, 850)
    P.obj("ms_clip", "clip 0 1", 20, 865); P.c("ms_r", "ms_clip")
    P.obj("ms_pk", f"pack f {MUSIC_STEREO_MS}", 20, 880); P.c("ms_clip", "ms_pk")
    P.obj("ms_width", "line~", 20, 895); P.c("ms_pk", "ms_width")


def image(P, name, src, pan=0, trigger=None, voice=None):
    """Return (mid minus mono, side). sqrt(2) equal-power: centre = old dual mono.
    A note's independent quasi-random sequence avoids changing Pd's global random seeds.
    music_pan<voice> overrides the latched place until the next note (traffic's hook).
    """
    x, y = 15000, 20 + len(P.index) % 1500
    def ex(n, text):
        return P.obj(n, "expr " + text.replace(",", BS + ",").replace("$", BS + "$"), x, y)
    if trigger:
        P.obj(name + "_phase", f"f {(voice + 1) * 0.173 % 1:.6f}", x, y)
        P.c(trigger, name + "_phase", 4)
        ex(name + "_next", "fmod($f1+0.618034,1)")
        P.c(name + "_phase", name + "_next"); P.c(name + "_next", name + "_phase", 0, 1)
        ex(name + "_pick", f"(2*$f1-1)*{MUSIC_CHIME_PAN}")
        P.c(name + "_next", name + "_pick")
        P.obj(name + "_recv", f"r music_pan{voice}", x, y)
        P.obj(name + "_clip", f"clip {-MUSIC_PAN_CAP} {MUSIC_PAN_CAP}", x, y)
        P.c(name + "_pick", name + "_clip"); P.c(name + "_recv", name + "_clip")
        P.obj(name + "_report", f"s music_place{voice}", x, y); P.c(name + "_clip", name + "_report")
        # Coefficients are control-rate, held throughout each note. 5 ms smooths voice reuse / future glide.
        for suffix, formula in (("m", "(cos(($f1+1)*0.7853981634)+sin(($f1+1)*0.7853981634))/sqrt(2)-1"),
                                ("s", "(cos(($f1+1)*0.7853981634)-sin(($f1+1)*0.7853981634))/sqrt(2)")):
            ex(name + "_" + suffix + "g", formula); P.c(name + "_clip", name + "_" + suffix + "g")
            P.obj(name + "_" + suffix + "pk", "pack f 5", x, y)
            P.obj(name + "_" + suffix + "ln", "line~", x, y)
            P.c(name + "_" + suffix + "g", name + "_" + suffix + "pk")
            P.c(name + "_" + suffix + "pk", name + "_" + suffix + "ln")
            P.sig2(name + "_" + suffix, "*~", src, name + "_" + suffix + "ln", x, y)
    else:
        assert abs(pan) <= MUSIC_PAN_CAP
        a = (pan + 1) * math.pi / 4
        for suffix, gain in (("m", (math.cos(a) + math.sin(a)) / math.sqrt(2) - 1),
                             ("s", (math.cos(a) - math.sin(a)) / math.sqrt(2))):
            P.obj(name + "_" + suffix, f"*~ {gain:.10g}", x, y); P.c(src, name + "_" + suffix)
    return name + "_m", name + "_s"


def sum_image(P, name, sources):
    acc = sources[0]
    for i, src in enumerate(sources[1:]):
        acc = P.sig2(f"{name}{i}", "+~", acc, src, 15000, 1600 + i * 15)
    return acc


def apply_image(P, name, mono, mid_delta, side):
    P.sig2(name + "_delta", "*~", mid_delta, "ms_width", 15000, 1700)
    P.sig2(name + "_mid", "+~", mono, name + "_delta", 15000, 1720)
    P.sig2(name + "_side", "*~", side, "ms_width", 15000, 1740)
    return name + "_mid", name + "_side"
