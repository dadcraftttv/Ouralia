# SPDX-License-Identifier: GPL-3.0-or-later
"""Found sounds played as notes: the found melody (struck resonators) and the found pad."""
from .constants import *  # noqa: F401,F403 (the tuning numbers)
from .core import *  # noqa: F401,F403


def add_found_note(P, k, vx, vy, ex, caught, prefix="fm", gate="mel_src[0]>=1"):
    """Found melody, one voice (the twin of chime voice k): a velocity in (frequency, fade and brightness already
    stored by the chime) plays a random caught slice at note / FM_REF_HZ and strikes a resonator tuned to the note,
    ringing for the chime's fade. Silent when Melody sounds is Synth or nothing is caught yet. Its audio runs only
    while a note sounds (its own switch~ group fmv<k>, like the glitch voices)."""
    n = f"{prefix}{k}"
    S = FS_SLOTS
    ex(f"{n}_ok", f"$f1*({gate})*{caught}", vx, vy)
    P.obj(f"{n}_in", "t f", vx, vy - 20); P.c(f"{n}_in", f"{n}_ok")
    P.obj(f"{n}_gate", "moses 0.0001", vx, vy + 20); P.c(f"{n}_ok", f"{n}_gate")
    P.obj(f"{n}_go", "t f b b b b", vx, vy + 40); P.c(f"{n}_gate", f"{n}_go", 1, 0)
    P.msg(f"{n}_swon", f"{BS}; sw_{prefix}v{k} 1", vx + 100, vy + 20); P.c(f"{n}_go", f"{n}_swon", 4, 0)
    P.obj(f"{n}_swdc", "value cdec", vx + 160, vy + 20); P.obj(f"{n}_swt", "+ 600", vx + 160, vy + 35)
    P.obj(f"{n}_swdl", "del", vx + 160, vy + 50); P.msg(f"{n}_swoff", f"{BS}; sw_{prefix}v{k} 0", vx + 160, vy + 65)
    P.c(f"{n}_go", f"{n}_swdc", 4, 0); P.c(f"{n}_swdc", f"{n}_swt"); P.c(f"{n}_swt", f"{n}_swdl"); P.c(f"{n}_swdl", f"{n}_swoff")
    P.obj(f"grp_{prefix}v{k}_r", f"r sw_{prefix}v{k}", 10, 10); P.obj(f"grp_{prefix}v{k}_sw", "switch~", 10, 40)
    P.c(f"grp_{prefix}v{k}_r", f"grp_{prefix}v{k}_sw")
    # 1. which slice: a bright one (snare or hat: energy at every note) if any, else a kick
    P.obj(f"{n}_rnd", "random 1000", vx + 200, vy + 60); P.c(f"{n}_go", f"{n}_rnd", 3, 0)
    j = "int($f1*max(fs_filled[1]+fs_filled[2],1)/1000)"
    ex(f"{n}_pick", f"if(fs_filled[1]+fs_filled[2]>0,if({j}<fs_filled[1],{S}+{j},{2 * S}+{j}-fs_filled[1]),"
                    f"int($f1*max(fs_filled[0],1)/1000))", vx + 200, vy + 80)
    P.c(f"{n}_rnd", f"{n}_pick")
    P.obj(f"{n}_slot", "t f f", vx + 200, vy + 100); P.c(f"{n}_pick", f"{n}_slot")
    P.obj(f"{n}_gt", "t f f", vx + 300, vy + 105); P.obj(f"{n}_g", "tabread fs_gains", vx + 300, vy + 120)
    ex(f"{n}_gk", f"$f1*if($f2<{S},{FM_KICK_GAIN},1)", vx + 300, vy + 130); P.obj(f"{n}_gv", "f 1", vx + 300, vy + 140)
    P.c(f"{n}_slot", f"{n}_gt", 1, 0); P.c(f"{n}_gt", f"{n}_gk", 1, 1); P.c(f"{n}_gt", f"{n}_g", 0, 0)
    P.c(f"{n}_g", f"{n}_gk"); P.c(f"{n}_gk", f"{n}_gv", 0, 1)
    ex(f"{n}_ni", f"int($f1/{S})*10+$f1%{S}", vx + 200, vy + 120)
    P.obj(f"{n}_name", "makefilename fs_%02d", vx + 200, vy + 140); P.msg(f"{n}_set", f"set {BS}$1", vx + 200, vy + 160)
    P.c(f"{n}_slot", f"{n}_ni", 0, 0); P.c(f"{n}_ni", f"{n}_name"); P.c(f"{n}_name", f"{n}_set")
    ex(f"{n}_ref", f"if($f1<{S},{FM_KICK_REF_HZ},{FM_REF_HZ})", vx + 300, vy + 100)
    P.c(f"{n}_slot", f"{n}_ref", 0, 0); P.c(f"{n}_ref", f"{n}_rate", 0, 1)
    P.obj(f"{n}_rd", "tabread4~ fs_00", vx, vy + 260); P.c(f"{n}_set", f"{n}_rd")
    # 2. the note: rate, resonator frequencies, ring length (Q from the fade: 60 dB over cdec ms), octave level
    P.obj(f"{n}_nt", "t b b b", vx + 400, vy + 60); P.c(f"{n}_go", f"{n}_nt", 2, 0)
    P.obj(f"{n}_fq", "value cfreq", vx + 400, vy + 80); P.obj(f"{n}_dc", "value cdec", vx + 480, vy + 80)
    P.c(f"{n}_nt", f"{n}_dc", 1, 0); P.c(f"{n}_nt", f"{n}_fq", 0, 0)
    ex(f"{n}_q", f"max(3.1416*$f1*$f2/6900*(1-{FM_CHAR_SHORT}*fm_cc),5)", vx + 480, vy + 140)
    P.c(f"{n}_dc", f"{n}_q", 0, 1)
    P.obj(f"{n}_ft", "t f f f f", vx + 400, vy + 100); P.c(f"{n}_fq", f"{n}_ft")
    P.c(f"{n}_ft", f"{n}_q", 3, 0)
    P.obj(f"{n}_fs1", "sig~", vx + 400, vy + 240)
    P.c(f"{n}_ft", f"{n}_fs1", 2, 0)
    ex(f"{n}_rate", f"min(max($f1/$f2,{FM_RATE[0]}),{FM_RATE[1]})", vx + 400, vy + 140)
    P.obj(f"{n}_rv", "t f f", vx + 400, vy + 160); P.c(f"{n}_ft", f"{n}_rate", 0, 0); P.c(f"{n}_rate", f"{n}_rv")
    ex(f"{n}_qg", f"{FM_RES}*$f1*(1-{FM_CHAR_RING}*fm_cc)*(1-fmring)", vx + 560, vy + 160)       # (a narrower ring catches less: feed it harder)
    P.c(f"{n}_q", f"{n}_qg")
    # 3. play: the read ramp over the slice's length / rate, and the strike's loudness = velocity x the slice's gain
    # (the rate arrived with the note, into the cold inlets; then: the slice's gain, the read ramp, the loudness)
    P.obj(f"{n}_pl", "t f b b", vx, vy + 80); P.c(f"{n}_go", f"{n}_pl", 0, 0)
    P.obj(f"{n}_srv", "value fs_srv", vx + 100, vy + 100)
    ex(f"{n}_ixe", f"$f1*{FS_SLICE_S} ; {FS_SLICE_S * 1000}/$f2".replace(" ; ", " " + BS + "; "), vx + 100, vy + 120)
    P.c(f"{n}_rv", f"{n}_ixe", 1, 1)
    P.c(f"{n}_pl", f"{n}_srv", 1, 0); P.c(f"{n}_srv", f"{n}_ixe")
    P.obj(f"{n}_ixpk", "pack f f", vx, vy + 140)
    P.c(f"{n}_ixe", f"{n}_ixpk", 1, 1); P.c(f"{n}_ixe", f"{n}_ixpk", 0, 0)
    P.msg(f"{n}_ixmsg", f"0 {BS}, {BS}$1 {BS}$2", vx, vy + 160); P.obj(f"{n}_ix", "vline~", vx, vy + 180)
    P.c(f"{n}_ixpk", f"{n}_ixmsg"); P.c(f"{n}_ixmsg", f"{n}_ix"); P.c(f"{n}_ix", f"{n}_rd")
    ex(f"{n}_amp", f"$f1*$f2 ; {FS_SLICE_S * 1000}/$f3".replace(" ; ", " " + BS + "; "), vx + 60, vy + 120)
    P.c(f"{n}_gv", f"{n}_amp", 0, 1); P.c(f"{n}_rv", f"{n}_amp", 0, 2)
    P.c(f"{n}_pl", f"{n}_gv", 2, 0)
    P.c(f"{n}_pl", f"{n}_amp", 0, 0)
    P.obj(f"{n}_apk", "pack f f", vx + 60, vy + 160)
    P.c(f"{n}_amp", f"{n}_apk", 1, 1); P.c(f"{n}_amp", f"{n}_apk", 0, 0)
    P.msg(f"{n}_emsg", f"0 {BS}, {BS}$1 {FM_ATTACK_MS} {BS}, 0 {BS}$2 {FM_ATTACK_MS}", vx + 60, vy + 180); P.obj(f"{n}_env", "vline~", vx + 60, vy + 200)
    P.c(f"{n}_apk", f"{n}_emsg"); P.c(f"{n}_emsg", f"{n}_env")
    P.sig2(f"{n}_raw", "*~", f"{n}_rd", f"{n}_env", vx, vy + 290)
    # (the order inside go: slice first, then the note, then play; set from right to left)
    P.obj(f"{n}_r1", "vcf~", vx + 100, vy + 320)
    P.c(f"{n}_raw", f"{n}_r1"); P.c(f"{n}_fs1", f"{n}_r1", 0, 1); P.c(f"{n}_q", f"{n}_r1", 0, 2)
    P.obj(f"{n}_res", "*~ 1", vx + 100, vy + 390); P.c(f"{n}_r1", f"{n}_res"); P.c(f"{n}_qg", f"{n}_res", 0, 1)
    # ...or a plucked string (Found note ring = String, `fm_ring`; dcs_string~): the strike, band-passed around
    # the note below, plucks a string on the note that rings the chime's fade (shorter and brighter toward
    # "your sound"). (The raw strike rang a clap's 1-3 kHz harmonics: thin, not round.)
    P.obj(f"{n}_str", "dcs_string~ 440 1500 0.5", vx + 200, vy + 360)
    P.c(f"{n}_ft", f"{n}_str", 1, 1)
    ex(f"{n}_t60", f"$f1*(1-{FM_CHAR_SHORT}*fm_cc)", vx + 560, vy + 100); P.c(f"{n}_dc", f"{n}_t60"); P.c(f"{n}_t60", f"{n}_str", 0, 2)
    ex(f"{n}_sbr", f"{FM_STR_BRIGHT}+{FM_STR_CHAR_BRIGHT}*fm_cc+$f1*0", vx + 560, vy + 120); P.c(f"{n}_nt", f"{n}_sbr", 2, 0); P.c(f"{n}_sbr", f"{n}_str", 0, 3)
    ex(f"{n}_sg", f"{FM_STR_GAIN}*(1-{FM_CHAR_RING}*fm_cc)*fmring+$f1*0", vx + 560, vy + 180); P.c(f"{n}_nt", f"{n}_sg", 2, 0)
    P.obj(f"{n}_strg", "*~ 0", vx + 200, vy + 390); P.c(f"{n}_str", f"{n}_strg"); P.c(f"{n}_sg", f"{n}_strg", 0, 1)
    # each note plucks it (a clap has nothing down at the note itself: fed in alone, it rang the overtones, thin)
    ex(f"{n}_plk", f"$f1*{FM_PLUCK}", vx + 300, vy + 345); P.msg(f"{n}_plm", f"pluck {BS}$1", vx + 300, vy + 355)
    P.c(f"{n}_amp", f"{n}_plk"); P.c(f"{n}_plk", f"{n}_plm"); P.c(f"{n}_plm", f"{n}_str")
    # the strike itself keeps its character but not its own low pitch (that would fight the note) nor its harsh top:
    # a soft band around the note
    P.obj(f"{n}_bp", "vcf~ 1", vx, vy + 360); P.c(f"{n}_raw", f"{n}_bp"); P.c(f"{n}_fs1", f"{n}_bp", 0, 1)
    P.c(f"{n}_bp", f"{n}_str")                  # (the caught sound runs into the string too: the pluck's texture)
    ex(f"{n}_bpq", f"{FM_STRIKE_Q}*(1-fm_cc)+0.3*fm_cc+$f1*0", vx + 60, vy + 340); P.c(f"{n}_nt", f"{n}_bpq", 2, 0)
    P.c(f"{n}_bpq", f"{n}_bp", 0, 2)
    P.obj(f"{n}_mix", f"*~ {FM_RAW}", vx, vy + 390); P.c(f"{n}_bp", f"{n}_mix")
    ex(f"{n}_mixg", f"{FM_RAW}*(1+{FM_CHAR_RAW}*fm_cc)+$f1*0", vx + 60, vy + 375); P.c(f"{n}_nt", f"{n}_mixg", 2, 0)
    P.c(f"{n}_mixg", f"{n}_mix", 0, 1)
    P.sig2(f"{n}_ring", "+~", f"{n}_res", f"{n}_strg", vx + 100, vy + 400)
    P.sig2(f"{n}_out", "+~", f"{n}_mix", f"{n}_ring", vx, vy + 410)

def add_found_pad(P, px, bx, ex):
    """Found pad (and, if chosen, bass): a held pitched sound (the Harmony tracker's steady note: a hum, a voice, a
    whistle, a machine hum, a bird) is kept with its pitch (FP_SLOTS takes, memory only, never in a pocket) and
    stretched into the pad's chord: each chord note is two grains wandering through the take, pitched from the caught
    note to the chord note (folded within a tritone, so the sound stays natural), their starts a whole number of the
    caught note's periods apart. Each crossfade set has its own switch group (fpad<k>); the synth set (pads<k>) runs
    only when it is heard. "Pad sounds" pad_src 0 synth / 1 found / 2 both, "Bass sound" bass_src 0 synth / 1 found.
    Until the first take, found means synth."""
    n_take = int(FP_TAKE_S * 48000) + 2000
    # ---- what is needed: catching (and the tracker) when found pad or bass is chosen ----
    P.obj("fpc_go_need", "t b b b b", px, 1150)
    for k, nm in enumerate(("pad_src", "bass_src", "music_harm", "music_answer")):
        P.obj(f"fpc_r{k}", f"r {nm}", px + 70 * k, 1100); P.obj(f"fpc_t{k}", "t b f", px + 70 * k, 1115)
        P.obj(f"fpc_w{k}", f"value fpn{k}", px + 70 * k, 1130); P.obj(f"fpc_v{k}", f"value fpn{k}", px + 70 * k, 1170)
        P.c(f"fpc_r{k}", f"fpc_t{k}"); P.c(f"fpc_t{k}", f"fpc_w{k}", 1, 0); P.c(f"fpc_t{k}", "fpc_go_need", 0, 0)
        P.c("fpc_go_need", f"fpc_v{k}", k, 0)
    ex("fpc_need", "($f1>=1)||($f2==1)", px, 1190); P.c("fpc_v1", "fpc_need", 0, 1); P.c("fpc_v0", "fpc_need", 0, 0)
    # (the tracker also runs for call and response, music_answer)
    ex("fpc_run", "($f2>0)||$f1||($f3>0)", px + 140, 1190); P.c("fpc_v2", "fpc_run", 0, 1)   # (the levels first: they fire first)
    P.c("fpc_v3", "fpc_run", 0, 2)
    P.obj("fpc_need_t", "t f f", px, 1205); P.c("fpc_need", "fpc_need_t")
    P.c("fpc_need_t", "fpc_run", 0, 0)
    P.obj("fpc_need_chg", "change -1", px + 60, 1215); P.c("fpc_need_t", "fpc_need_chg", 1, 0)
    P.obj("fpc_need_s", "s fpad_need", px + 60, 1230); P.c("fpc_need_chg", "fpc_need_s")
    P.obj("fpc_run_chg", "change -1", px + 140, 1215); P.c("fpc_run", "fpc_run_chg")
    P.obj("fpc_run_s", "s harm_run", px + 140, 1230); P.c("fpc_run_chg", "fpc_run_s")
    # (a take needs the tracker: the Harmony groups run while Harmony is up or a found pad/bass wants takes)
    # ---- catching: a steady pitch starts a take; if it is still held when the take ends, it is kept ----
    P.obj("fpc_stage_t", f"table fpad_stage {n_take}", px + 300, 1100)
    for j in range(FP_SLOTS):
        P.obj(f"fpc_tab{j}", f"table fpad_{j} {n_take}", px + 300, 1120 + 20 * j)
    P.obj("fpc_f0_t", f"table fpad_f0 {FP_SLOTS}", px + 400, 1100); P.obj("fpc_g_t", f"table fpad_g {FP_SLOTS}", px + 400, 1120)
    P.obj("fpc_n_t", "table fpad_n 2", px + 400, 1140)                     # [0] how many kept (max FP_SLOTS), [1] the newest
    P.obj("fpr_rd", f"delread~ hv_buf {FP_PREROLL_MS}", px + 500, 1100)
    P.obj("fpr_rec", "tabwrite~ fpad_stage", px + 500, 1120); P.c("fpr_rd", "fpr_rec")
    P.obj("fpc_open", "spigot 1", px, 1260)                                # (closed while a take records)
    P.obj("fpc_use", "spigot 0", px, 1250); P.c("fpc_need_chg", "fpc_use", 0, 1)
    P.obj("fpc_pk_r", "r pocket", px + 80, 1240); P.obj("fpc_pk_n", "== 0", px + 80, 1255)
    P.obj("fpc_pk", "spigot 1", px, 1270); P.c("fpc_pk_r", "fpc_pk_n"); P.c("fpc_pk_n", "fpc_pk", 0, 1)
    P.c("mu_hm_on", "fpc_use", 0, 0); P.c("fpc_use", "fpc_pk"); P.c("fpc_pk", "fpc_open")
    P.obj("fpc_p", "f", px + 160, 1260); P.c("mu_hm_t", "fpc_p", 1, 1)       # the heard pitch (MIDI), set before the on
    P.obj("fpc_held", "f", px + 220, 1260)
    P.msg("fpc_h1", "1", px + 220, 1240); P.msg("fpc_h0", "0", px + 280, 1240)
    P.c("mu_hm_on", "fpc_h1", 0, 0); P.c("mu_hm_off", "fpc_h0"); P.c("fpc_h1", "fpc_held", 0, 1); P.c("fpc_h0", "fpc_held", 0, 1)
    P.obj("fpc_start", "t b b b", px, 1280); P.c("fpc_open", "fpc_start")
    P.msg("fpc_close", "0", px + 60, 1295); P.c("fpc_start", "fpc_close", 2, 0); P.c("fpc_close", "fpc_open", 0, 1)
    P.obj("fpc_pv", "f", px + 120, 1295); P.c("fpc_start", "fpc_p", 1, 0); P.c("fpc_p", "fpc_pv", 0, 1)   # (keep this take's pitch)
    P.c("fpc_start", "fpr_rec", 0, 0)
    P.obj("fpc_done", f"del {int(FP_TAKE_S * 1000 - FP_PREROLL_MS) + 10}", px, 1310); P.c("fpc_start", "fpc_done", 0, 0)
    P.obj("fpc_end", "t b b", px, 1325); P.c("fpc_done", "fpc_end")
    P.msg("fpc_reopen", "1", px + 60, 1340); P.c("fpc_end", "fpc_reopen", 0, 0); P.c("fpc_reopen", "fpc_open", 0, 1)
    P.c("fpc_end", "fpc_held", 1, 0)
    P.obj("fpc_keep", "sel 1", px, 1355); P.c("fpc_held", "fpc_keep")
    # kept: the next slot, its pitch (Hz) and gain (peak to 0.5), the copy, the count, then report it
    P.obj("fpc_rr", "f", px, 1370); P.obj("fpc_rr1", "+ 1", px + 50, 1370); P.obj("fpc_rrm", f"mod {FP_SLOTS}", px + 100, 1370)
    P.c("fpc_keep", "fpc_rr"); P.c("fpc_rr", "fpc_rr1"); P.c("fpc_rr1", "fpc_rrm"); P.c("fpc_rrm", "fpc_rr", 0, 1)
    P.obj("fpc_slot", "t f f f b f", px, 1385); P.c("fpc_rr", "fpc_slot")
    P.obj("fpc_f0", "mtof", px + 300, 1400); P.obj("fpc_f0w", "tabwrite fpad_f0", px + 300, 1420)
    P.c("fpc_slot", "fpc_f0w", 4, 1); P.c("fpc_slot", "fpc_pv", 3, 0); P.c("fpc_pv", "fpc_f0"); P.c("fpc_f0", "fpc_f0w")
    P.obj("fpc_pkx", "array max fpad_stage", px + 450, 1400); P.obj("fpc_pkn", "array min fpad_stage", px + 550, 1400)
    ex("fpc_gain", "0.5/max(max(abs($f1),abs($f2)),0.01)", px + 450, 1420)
    P.obj("fpc_gw", "tabwrite fpad_g", px + 450, 1440)
    P.obj("fpc_gt", "t b b f", px + 450, 1385); P.c("fpc_slot", "fpc_gt", 2, 0)
    P.c("fpc_gt", "fpc_gw", 2, 1); P.c("fpc_gt", "fpc_pkn", 1, 0); P.c("fpc_pkn", "fpc_gain", 0, 1)
    P.c("fpc_gt", "fpc_pkx", 0, 0); P.c("fpc_pkx", "fpc_gain"); P.c("fpc_gain", "fpc_gw")
    P.obj("fpc_name", "makefilename fpad_%d", px + 150, 1400); P.obj("fpc_nt", "t b s", px + 150, 1415)
    P.obj("fpc_get", "array get fpad_stage", px + 150, 1430); P.obj("fpc_set", "array set fpad_0", px + 150, 1450)
    P.c("fpc_slot", "fpc_name", 1, 0); P.c("fpc_name", "fpc_nt"); P.c("fpc_nt", "fpc_set", 1, 2); P.c("fpc_nt", "fpc_get", 0, 0)
    P.c("fpc_get", "fpc_set")
    ex("fpc_cnt", f"min(fpad_n[0]+1,{FP_SLOTS})+$f1*0", px + 600, 1440)
    P.obj("fpc_cw", "tabwrite fpad_n", px + 600, 1460); P.msg("fpc_c0", "0", px + 660, 1440); P.c("fpc_c0", "fpc_cw", 0, 1)
    P.obj("fpc_ct", "t b f b", px + 600, 1420); P.c("fpc_slot", "fpc_ct", 0, 0)
    P.c("fpc_ct", "fpc_c0", 2, 0); P.c("fpc_ct", "fpc_cnt", 2, 0)
    P.c("fpc_cnt", "fpc_cw")
    P.obj("fpc_lw", "tabwrite fpad_n", px + 700, 1460); P.msg("fpc_l1", "1", px + 760, 1440); P.c("fpc_l1", "fpc_lw", 0, 1)
    P.obj("fpc_lt", "t f b", px + 700, 1440); P.c("fpc_ct", "fpc_lt", 1, 0); P.c("fpc_lt", "fpc_l1", 1, 0); P.c("fpc_lt", "fpc_lw", 0, 0)
    P.obj("fpc_pv3", "f", px + 600, 1470); P.c("fpc_ct", "fpc_pv3", 0, 0); P.c("fpc_pv", "fpc_pv3", 0, 1)
    P.obj("fpc_s", "s fpad_caught", px + 600, 1480); P.c("fpc_pv3", "fpc_s")
    # (fpc_ct: count first, then the newest slot, then the report; the report re-checks the pad's switches)
    # ---- which pad audio runs: synth set k while heard and wanted, found set k while heard and found ----
    P.obj("fpw_go", "t b b", px + 800, 1100)
    for j, nm in enumerate(("fpad_kick", "pad_src", "music_pad_reg")):
        P.obj(f"fpw_r_{nm}", f"r {nm}", px + 800 + 90 * j, 1080)
        P.obj(f"fpw_b_{nm}", "t b f", px + 800 + 90 * j, 1090)
        P.c(f"fpw_r_{nm}", f"fpw_b_{nm}")
        if nm != "pad_src":
            P.c(f"fpw_b_{nm}", "fpw_go", 0, 0)   # (the value first, then the check)
    P.obj("fpw_src", "value fpw_src", px + 1100, 1080); P.c("fpw_b_pad_src", "fpw_src", 1, 0)
    for k in range(2):
        ex(f"fpw_ls{k}", "((fpw_src!=1)||(fpad_n[0]<1))+$f1*0", px + 1300, 1120 + 30 * k)
        ex(f"fpw_lf{k}", "(fpw_src>=1)*(fpad_n[0]>=1)+$f1*0", px + 1400, 1120 + 30 * k)
        P.obj(f"fpw_lt{k}", "t b b", px + 1300, 1110 + 30 * k)
        P.c(f"fpw_lt{k}", f"fpw_ls{k}", 1, 0); P.c(f"fpw_lt{k}", f"fpw_lf{k}", 0, 0)
        P.obj(f"fpw_lsw{k}", "tabwrite fpw_m", px + 1300, 1130 + 30 * k); P.msg(f"fpw_lsi{k}", str(2 * k), px + 1350, 1130 + 30 * k)
        P.obj(f"fpw_lfw{k}", "tabwrite fpw_m", px + 1400, 1130 + 30 * k); P.msg(f"fpw_lfi{k}", str(2 * k + 1), px + 1450, 1130 + 30 * k)
        P.obj(f"fpw_lst{k}", "t f b", px + 1300, 1125 + 30 * k); P.c(f"fpw_ls{k}", f"fpw_lst{k}")
        P.c(f"fpw_lst{k}", f"fpw_lsi{k}", 1, 0); P.c(f"fpw_lsi{k}", f"fpw_lsw{k}", 0, 1); P.c(f"fpw_lst{k}", f"fpw_lsw{k}", 0, 0)
        P.obj(f"fpw_lft{k}", "t f b", px + 1400, 1125 + 30 * k); P.c(f"fpw_lf{k}", f"fpw_lft{k}")
        P.c(f"fpw_lft{k}", f"fpw_lfi{k}", 1, 0); P.c(f"fpw_lfi{k}", f"fpw_lfw{k}", 0, 1); P.c(f"fpw_lft{k}", f"fpw_lfw{k}", 0, 0)
    P.obj("fpw_srcl", "t b b b", px + 1200, 1095); P.c("fpw_b_pad_src", "fpw_srcl", 0, 0)
    P.c("fpw_srcl", "fpw_lt0", 2, 0); P.c("fpw_srcl", "fpw_lt1", 1, 0); P.c("fpw_srcl", "fpw_go", 0, 0)
    P.obj("fpw_reg", "value fpw_reg", px + 1190, 1080); P.c("fpw_b_music_pad_reg", "fpw_reg", 1, 0)
    # each set's sound is decided when it takes its chord (fpw_m: synth, found per set) and kept for that chord, so a
    # set fading out is never cut and a new take joins at the next chord change; a Pad sounds change applies at once
    P.obj("fpw_m_t", "table fpw_m 4", px + 1100, 1120)
    for k in range(2):
        ex(f"fpw_syn{k}", f"fpw_act[{k}]*(fpw_reg!=0)*fpw_m[{2 * k}]+$f1*0", px + 800 + 150 * k, 1130)
        ex(f"fpw_fnd{k}", f"fpw_act[{k}]*(fpw_reg!=0)*fpw_m[{2 * k + 1}]+$f1*0", px + 870 + 150 * k, 1130)
        P.obj(f"fpw_bk{k}", "t b b", px + 800 + 150 * k, 1115); P.c("fpw_go", f"fpw_bk{k}", k, 0)
        P.c(f"fpw_bk{k}", f"fpw_syn{k}", 1, 0); P.c(f"fpw_bk{k}", f"fpw_fnd{k}", 0, 0)
        P.obj(f"fpw_sc{k}", "change -1", px + 800 + 150 * k, 1140); P.obj(f"fpw_fc{k}", "change -1", px + 870 + 150 * k, 1140)
        P.obj(f"fpw_ss{k}", f"s sw_pads{k}", px + 800 + 150 * k, 1150); P.c(f"fpw_syn{k}", f"fpw_sc{k}"); P.c(f"fpw_sc{k}", f"fpw_ss{k}")
        P.obj(f"fpw_fs{k}", f"s sw_fpad{k}", px + 870 + 150 * k, 1150); P.c(f"fpw_fnd{k}", f"fpw_fc{k}"); P.c(f"fpw_fc{k}", f"fpw_fs{k}")
    P.obj("fpw_act_t", "table fpw_act 2", px + 1100, 1100)
    P.obj("fpw_vsrc_init", "loadbang", px + 1300, 1080); P.msg("fpw_vsrc1", "1", px + 1300, 1095)
    P.c("fpw_vsrc_init", "fpw_vsrc1"); P.c("fpw_vsrc1", "fpw_src"); P.msg("fpw_vreg2", "2", px + 1350, 1095)
    P.c("fpw_vsrc_init", "fpw_vreg2"); P.c("fpw_vreg2", "fpw_reg")
    # ---- the found voices: per set, the take chosen when the set takes its chord ----
    sr = "fs_srv"                                                           # (the sample rate, kept by the found kit)
    outs = []
    for k in range(2):
        cx = px + 1400 + 900 * k
        # set k takes the chord: active, the newest take, the switches re-checked; set k faded out: inactive
        P.obj(f"fps{k}_on", "t b b b b", cx, 1100); P.c(f"mu_xf{k}_ont", f"fps{k}_on", 0, 0)
        P.msg(f"fps{k}_a1", f"{BS}; fpw_act {k} 1", cx + 60, 1115); P.c(f"fps{k}_on", f"fps{k}_a1", 3, 0)
        P.c(f"fps{k}_on", f"fpw_lt{k}", 2, 0)
        P.c(f"fps{k}_on", "fpw_go", 0, 0)
        P.obj(f"fps{k}_off", "t b b", cx + 200, 1100); P.c(f"mu_xf{k}_dl", f"fps{k}_off")
        P.msg(f"fps{k}_a0", f"{BS}; fpw_act {k} 0", cx + 260, 1115); P.c(f"fps{k}_off", f"fps{k}_a0", 1, 0); P.c(f"fps{k}_off", "fpw_go", 0, 0)
        ex(f"fps{k}_slot", "fpad_n[1]+$f1*0", cx + 120, 1130); P.c(f"fps{k}_on", f"fps{k}_slot", 1, 0)
        P.obj(f"fps{k}_slt", "t f f", cx + 120, 1145); P.c(f"fps{k}_slot", f"fps{k}_slt")
        P.obj(f"fps{k}_sv", f"value fps{k}", cx + 200, 1160); P.c(f"fps{k}_slt", f"fps{k}_sv", 1, 0)
        P.obj(f"fps{k}_nm", "makefilename fpad_%d", cx + 120, 1160)
        P.msg(f"fps{k}_nmm", f"set {BS}$1", cx + 120, 1170); P.c(f"fps{k}_nm", f"fps{k}_nmm")
        P.c(f"fps{k}_slt", f"fps{k}_nm", 0, 0)
        # the crossfade for this set (a copy of mu_xf<k>, inside its own group)
        P.obj(f"fp_xf{k}", "line~", cx + 300, 1100); P.c(f"mu_xf{k}_pk", f"fp_xf{k}")
        # the grain clock: every half grain, the other grain starts
        P.obj(f"fps{k}_met", f"metro {FP_GRAIN_MS / 2}", cx, 1200)
        P.obj(f"fps{k}_mr", f"r sw_fpad{k}", cx, 1180); P.c(f"fps{k}_mr", f"fps{k}_met")
        P.obj(f"fps{k}_ab", "f", cx, 1220); P.obj(f"fps{k}_ab1", "+ 1", cx + 40, 1220); P.obj(f"fps{k}_abm", "mod 2", cx + 80, 1220)
        P.c(f"fps{k}_met", f"fps{k}_ab"); P.c(f"fps{k}_ab", f"fps{k}_ab1"); P.c(f"fps{k}_ab1", f"fps{k}_abm"); P.c(f"fps{k}_abm", f"fps{k}_ab", 0, 1)
        P.obj(f"fps{k}_abt", "t f f", cx, 1235); P.c(f"fps{k}_ab", f"fps{k}_abt")
        P.obj(f"fps{k}_abs", "sel 0 1", cx, 1250); P.c(f"fps{k}_abt", f"fps{k}_abs", 1, 0)
        P.obj(f"fps{k}_rnd", "random 1000", cx + 100, 1250); P.c(f"fps{k}_abt", f"fps{k}_rnd", 1, 0)
        P.obj(f"fps{k}_tgt", f"value fpt{k}", cx + 100, 1265)
        ex(f"fps{k}_tg", f"({FP_LOOP_S[0]}+{FP_LOOP_S[1] - FP_LOOP_S[0]}*$f1/1000)*{sr}", cx + 100, 1258)
        P.c(f"fps{k}_rnd", f"fps{k}_tg"); P.c(f"fps{k}_tg", f"fps{k}_tgt")
        for g, gn in enumerate("ab"):
            P.msg(f"fps{k}_w{gn}m", f"0 {BS}, 1 {FP_GRAIN_MS}", cx + 60 * g, 1280)
            P.c(f"fps{k}_abs", f"fps{k}_w{gn}m", g, 0)
            P.obj(f"fp{k}{gn}_wl", "vline~", cx + 60 * g, 1300); P.c(f"fps{k}_w{gn}m", f"fp{k}{gn}_wl")
            P.obj(f"fp{k}{gn}_wc", "cos~", cx + 60 * g, 1315); P.c(f"fp{k}{gn}_wl", f"fp{k}{gn}_wc")
            P.obj(f"fp{k}{gn}_wn", "*~ -0.5", cx + 60 * g, 1330); P.c(f"fp{k}{gn}_wc", f"fp{k}{gn}_wn")
            P.obj(f"fp{k}{gn}_w", "+~ 0.5", cx + 60 * g, 1345); P.c(f"fp{k}{gn}_wn", f"fp{k}{gn}_w")
        acc = None
        for i in range(4):
            vx = cx + 160 * i
            n = f"fp{k}{i}"
            # the rate: chord note / caught note, folded within a tritone; the source period in samples
            f0 = f"max(fpad_f0[fps{k}],20)"
            ex(f"{n}_rate", f"$f1/{f0}*pow(2,-rint(log(max($f1,20)/{f0})/log(2)))", vx, 1380)
            P.c(f"mu_pad{i}_{k}_t", f"{n}_rate", 0, 0)
            P.obj(f"{n}_rv", f"value {n}r", vx, 1395); P.c(f"{n}_rate", f"{n}_rv")
            # each half grain: the next start, a whole number of periods from where the playing grain is now
            P.obj(f"{n}_st", f"value {n}s", vx, 1440)
            adv = f"{n}s+{FP_GRAIN_MS / 2}*{n}r*{sr}/1000"
            per = f"({sr}/{f0})"
            ex(f"{n}_nx", f"{adv}-rint(({adv}-fpt{k})/{per})*{per}+$f1*0", vx, 1420)
            P.c(f"fps{k}_abt", f"{n}_nx", 0, 0)
            P.obj(f"{n}_nt", "t f f", vx, 1430); P.c(f"{n}_nx", f"{n}_nt"); P.c(f"{n}_nt", f"{n}_st", 1, 0)
            P.obj(f"{n}_nd", f"expr $f1+{FP_GRAIN_MS}*{n}r*{sr}/1000".replace("$", BS + "$"), vx + 60, 1445)
            P.obj(f"{n}_pk", "pack f f", vx, 1460); P.c(f"{n}_nt", f"{n}_nd", 0, 0); P.c(f"{n}_nd", f"{n}_pk", 0, 1); P.c(f"{n}_nt", f"{n}_pk", 0, 0)
            P.obj(f"{n}_ab", "list prepend", vx, 1470); P.c(f"fps{k}_abt", f"{n}_ab", 1, 1); P.c(f"{n}_pk", f"{n}_ab")
            P.obj(f"{n}_rt", "route 0 1", vx, 1480); P.c(f"{n}_ab", f"{n}_rt")
            for g, gn in enumerate("ab"):
                P.msg(f"{n}{gn}_m", f"{BS}$1 {BS}, {BS}$2 {FP_GRAIN_MS}", vx + 60 * g, 1495); P.c(f"{n}_rt", f"{n}{gn}_m", g, 0)
                P.obj(f"{n}{gn}_ix", "vline~", vx + 60 * g, 1510); P.c(f"{n}{gn}_m", f"{n}{gn}_ix")
                P.obj(f"{n}{gn}_rd", "tabread4~ fpad_0", vx + 60 * g, 1525); P.c(f"{n}{gn}_ix", f"{n}{gn}_rd")
                P.c(f"fps{k}_nmm", f"{n}{gn}_rd", 0, 0)
                P.sig2(f"{n}{gn}_s", "*~", f"{n}{gn}_rd", f"fp{k}{gn}_w", vx + 60 * g, 1540)
            P.sig2(f"{n}_s", "+~", f"{n}a_s", f"{n}b_s", vx, 1560)
            acc = f"{n}_s" if acc is None else P.sig2(f"fp{k}_acc{i}", "+~", acc, f"{n}_s", vx, 1575)
        # the take's gain and the set's crossfade
        ex(f"fps{k}_g", "fpad_g[fps" + str(k) + "]*" + str(FP_GAIN) + "+$f1*0", cx + 400, 1130)
        P.c(f"fps{k}_on", f"fps{k}_g", 0, 0)
        P.obj(f"fp{k}_gm", "*~ 0", cx, 1590); P.c(acc, f"fp{k}_gm"); P.c(f"fps{k}_g", f"fp{k}_gm", 0, 1)
        outs.append(P.sig2(f"fp{k}_out", "*~", f"fp{k}_gm", f"fp_xf{k}", cx, 1605))
    P.sig2("fp_sum", "+~", outs[0], outs[1], px + 1400, 1630)
    # ---- the found bass: one voice like the pad's, on the bass note (not folded: it must sit low) ----
    P.obj("fb_r_src", "r bass_src", bx + 600, 1100); P.obj("fb_v_src", "value fb_src", bx + 600, 1115)
    P.obj("fb_src_t", "t b f", bx + 600, 1108); P.c("fb_r_src", "fb_src_t"); P.c("fb_src_t", "fb_v_src", 1, 0)
    ex("fb_want", "(fb_src==1)*(fpad_n[0]>=1)*(fb_mon>0)+$f1*0", bx + 600, 1140)
    P.obj("fb_r_mon", "r music_on", bx + 760, 1100); P.obj("fb_mon_t", "t b f", bx + 760, 1112)
    P.obj("fb_v_mon", "value fb_mon", bx + 760, 1125); P.c("fb_r_mon", "fb_mon_t"); P.c("fb_mon_t", "fb_v_mon", 1, 0); P.c("fb_mon_t", "fb_go", 0, 0)
    P.obj("fb_go", "t b", bx + 600, 1125)
    P.obj("fb_rc", "r fpad_caught", bx + 680, 1100); P.c("fb_src_t", "fb_go", 0, 0); P.c("fb_rc", "fb_go")
    P.c("fb_go", "fb_want")
    P.obj("fb_want_t", "t f f", bx + 600, 1155); P.c("fb_want", "fb_want_t")
    P.obj("fb_sw", "s sw_fbass", bx + 600, 1170); P.c("fb_want_t", "fb_sw", 1, 0)
    P.obj("fb_pk", "pack f 300", bx + 650, 1170); P.obj("fb_ln", "line~", bx + 650, 1185)
    P.c("fb_want_t", "fb_pk", 0, 0); P.c("fb_pk", "fb_ln")
    P.obj("fb_ml", "t f b", bx + 700, 1200); P.c("ba_ft", "fb_ml", 1, 0)       # a bass note: the newest take, its rate
    P.obj("fb_lastf", "f 65", bx + 760, 1185); P.c("ba_ft", "fb_lastf", 1, 1)   # (switched on: the current note again)
    P.obj("fb_on1", "sel 1", bx + 760, 1170); P.c("fb_want_t", "fb_on1", 0, 0); P.c("fb_on1", "fb_lastf"); P.c("fb_lastf", "fb_ml")
    ex("fb_slot", "fpad_n[1]+$f1*0", bx + 700, 1215); P.c("fb_ml", "fb_slot", 1, 0)
    P.obj("fb_sv", "value fbs", bx + 700, 1230); P.obj("fb_slt", "t f f", bx + 700, 1222); P.c("fb_slot", "fb_slt"); P.c("fb_slt", "fb_sv", 1, 0)
    P.obj("fb_nm", "makefilename fpad_%d", bx + 760, 1230); P.msg("fb_nmm", f"set {BS}$1", bx + 760, 1240)
    P.c("fb_slt", "fb_nm", 0, 0); P.c("fb_nm", "fb_nmm")
    ex("fb_rate", "$f1/max(fpad_f0[fbs],20)", bx + 800, 1215); P.c("fb_ml", "fb_rate", 0, 0)
    P.obj("fb_rv", "value fbr", bx + 800, 1230); P.c("fb_rate", "fb_rv")
    P.obj("fb_met", f"metro {FP_GRAIN_MS / 2}", bx + 600, 1260); P.obj("fb_mr", "r sw_fbass", bx + 600, 1245); P.c("fb_mr", "fb_met")
    P.obj("fb_ab", "f", bx + 600, 1275); P.obj("fb_ab1", "+ 1", bx + 640, 1275); P.obj("fb_abm", "mod 2", bx + 680, 1275)
    P.c("fb_met", "fb_ab"); P.c("fb_ab", "fb_ab1"); P.c("fb_ab1", "fb_abm"); P.c("fb_abm", "fb_ab", 0, 1)
    P.obj("fb_abt", "t f f", bx + 600, 1290); P.c("fb_ab", "fb_abt")
    P.obj("fb_rnd", "random 1000", bx + 700, 1290); P.c("fb_abt", "fb_rnd", 1, 0)
    P.obj("fb_tgt", "value fbt", bx + 700, 1310)
    ex("fb_tg", f"({FP_LOOP_S[0]}+{FP_LOOP_S[1] - FP_LOOP_S[0]}*$f1/1000)*{sr}", bx + 700, 1300); P.c("fb_rnd", "fb_tg"); P.c("fb_tg", "fb_tgt")
    P.obj("fb_st", "value fbst", bx + 600, 1330)
    adv = f"fbst+{FP_GRAIN_MS / 2}*fbr*{sr}/1000"; per = f"({sr}/max(fpad_f0[fbs],20))"
    ex("fb_nx", f"{adv}-rint(({adv}-fbt)/{per})*{per}+$f1*0", bx + 600, 1320); P.c("fb_abt", "fb_nx", 0, 0)
    P.obj("fb_nt", "t f f", bx + 600, 1335); P.c("fb_nx", "fb_nt"); P.c("fb_nt", "fb_st", 1, 0)
    P.obj("fb_nd", f"expr $f1+{FP_GRAIN_MS}*fbr*{sr}/1000".replace("$", BS + "$"), bx + 660, 1345)
    P.obj("fb_pk2", "pack f f", bx + 600, 1360); P.c("fb_nt", "fb_nd", 0, 0); P.c("fb_nd", "fb_pk2", 0, 1); P.c("fb_nt", "fb_pk2", 0, 0)
    P.obj("fb_abp", "list prepend", bx + 600, 1370); P.c("fb_abt", "fb_abp", 1, 1); P.c("fb_pk2", "fb_abp")
    P.obj("fb_rt", "route 0 1", bx + 600, 1380); P.c("fb_abp", "fb_rt")
    P.obj("fb_abs", "sel 0 1", bx + 750, 1290); P.c("fb_abt", "fb_abs", 1, 0)
    for g, gn in enumerate("ab"):
        P.msg(f"fb_w{gn}m", f"0 {BS}, 1 {FP_GRAIN_MS}", bx + 750 + 60 * g, 1305); P.c("fb_abs", f"fb_w{gn}m", g, 0)
        P.obj(f"fb{gn}_wl", "vline~", bx + 750 + 60 * g, 1320); P.c(f"fb_w{gn}m", f"fb{gn}_wl")
        P.obj(f"fb{gn}_wc", "cos~", bx + 750 + 60 * g, 1335); P.c(f"fb{gn}_wl", f"fb{gn}_wc")
        P.obj(f"fb{gn}_wn", "*~ -0.5", bx + 750 + 60 * g, 1350); P.c(f"fb{gn}_wc", f"fb{gn}_wn")
        P.obj(f"fb{gn}_w", "+~ 0.5", bx + 750 + 60 * g, 1365); P.c(f"fb{gn}_wn", f"fb{gn}_w")
        P.msg(f"fb{gn}_m", f"{BS}$1 {BS}, {BS}$2 {FP_GRAIN_MS}", bx + 600 + 60 * g, 1395); P.c("fb_rt", f"fb{gn}_m", g, 0)
        P.obj(f"fb{gn}_ix", "vline~", bx + 600 + 60 * g, 1410); P.c(f"fb{gn}_m", f"fb{gn}_ix")
        P.obj(f"fb{gn}_rd", "tabread4~ fpad_0", bx + 600 + 60 * g, 1425); P.c(f"fb{gn}_ix", f"fb{gn}_rd"); P.c("fb_nmm", f"fb{gn}_rd", 0, 0)
        P.sig2(f"fb{gn}_s", "*~", f"fb{gn}_rd", f"fb{gn}_w", bx + 600 + 60 * g, 1440)
    P.sig2("fb_s", "+~", "fba_s", "fbb_s", bx + 600, 1460)
    ex("fb_g", f"fpad_g[fbs]*{FB_GAIN}+$f1*0", bx + 800, 1250); P.c("fb_slt", "fb_g", 0, 0)
    P.obj("fb_gm", "*~ 0", bx + 600, 1475); P.c("fb_s", "fb_gm"); P.c("fb_g", "fb_gm", 0, 1)
    P.sig2("fb_out", "*~", "fb_gm", "fb_ln", bx + 600, 1490)
    return "fp_sum", "fb_out", "fb_ln"
