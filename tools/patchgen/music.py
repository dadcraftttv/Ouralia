# SPDX-License-Identifier: GPL-3.0-or-later
"""The Music: melody rhythm, phrases, the composer, the pad/bass/melody voices, Harmony and Room notes."""
from .constants import *  # noqa: F401,F403 (the tuning numbers)
from .core import *  # noqa: F401,F403
from .found import add_found_note, add_found_pad


def add_melody_rhythm(P, x, ex):
    """Melody rhythm (mel_rhythm 1 straight, 2 dotted, 3 mixed; 0 = as before): each bar (tick 0) picks a one-bar
    cell near the drive (rh_drive: pace walking, the room's liveliness standing; never the same twice) from the
    chosen pool. On each of its onsets (the clock's 16ths) the note's ring is set from its length (mlen, MEL_RING),
    then, walking, the arpeggio plays its next note (instead of one per beat), or, standing, the still pattern rolls
    its chance (scaled by the cell's onsets per bar, so the density stays as it was). Reports mel_cell <row> and
    mel_note <tick> <length in 16ths> <ring ms> for the tests."""
    def rows(cells):
        out = []
        for c in cells:
            assert sum(abs(v) for v in c) == 16, c
            r = [0] * 16; t = 0
            for v in c:
                if v > 0:
                    r[t] = v
                t += abs(v)
            out.append(r)
        return out
    mixed = sorted(MEL_CELLS_STRAIGHT + MEL_CELLS_DOTTED, key=lambda c: sum(1 for v in c if v > 0))
    pools = [rows(MEL_CELLS_STRAIGHT), rows(MEL_CELLS_DOTTED), rows(mixed)]
    allrows = [r for p in pools for r in p]
    allrows.append(rows(MEL_CELLS_STRAIGHT)[3])   # (the last row: Patterns made by rules', rewritten each bar: add_composer)
    offs = [0, len(pools[0]), len(pools[0]) + len(pools[1])]
    ns = [len(p) for p in pools]
    y = 1400; mx = x + 1700
    P.obj("mr_cells", f"table mel_cells {16 * len(allrows)}", mx, y)
    P.obj("mr_cn", f"table mel_cn {len(allrows)}", mx + 150, y)
    P.obj("mr_lo", f"table mel_lo {len(allrows)}", mx + 150, y + 15)        # each cell's last onset (tick)
    P.obj("mr_po", "table mel_po 6", mx + 300, y)
    P.obj("mr_lb", "loadbang", mx, y - 30)
    P.msg("mr_init", f"{BS}; mel_cells 0 {' '.join(str(v) for r in allrows for v in r)}"
                     f" {BS}; mel_cn 0 {' '.join(str(sum(1 for v in r if v > 0)) for r in allrows)}"
                     f" {BS}; mel_po 0 {' '.join(str(v) for v in offs + ns)}"
                     f" {BS}; mel_lo 0 {' '.join(str(max(i for i, v in enumerate(r) if v > 0)) for r in allrows)}", mx, y - 15)
    P.c("mr_lb", "mr_init")
    # the setting: 0 opens the old paths (and puts the still pattern's roll length back), 1-3 closes them
    P.obj("mr_r", "r mel_rhythm", mx + 450, y - 30); P.obj("mr_v", "value mrhy", mx + 450, y - 15)
    P.obj("mr_t", "t f f", mx + 450, y); P.c("mr_r", "mr_t"); P.c("mr_t", "mr_v", 1, 0)
    P.obj("mr_off", "== 0", mx + 450, y + 15); P.c("mr_t", "mr_off", 0, 0)
    for g in ("mu_mr_arp", "mu_mr_stf", "mu_mr_sts"):
        P.c("mr_off", g, 0, 1)
    P.obj("mr_off_sel", "sel 1", mx + 520, y + 30); P.c("mr_off", "mr_off_sel")
    P.msg("mr_p250", "250", mx + 520, y + 45); P.c("mr_off_sel", "mr_p250"); P.c("mr_p250", "mu_p", 0, 2)
    P.obj("mr_lv", "value mlen", mx + 600, y)                            # (declared; 0 = no cell note)
    # each 16th: a new bar picks its cell (right outlet first), then the tick's onset
    P.obj("mr_tick", "r tick16", mx, y + 40); P.obj("mr_tt", "t f f", mx, y + 55); P.c("mr_tick", "mr_tt")
    P.obj("mr_bar0", "sel 0", mx + 150, y + 70); P.c("mr_tt", "mr_bar0", 1, 0)
    # (phrases: the bar's place in A A' B A first, -1 while off, so switching on starts at A)
    P.obj("mr_bar", "t b b", mx + 150, y + 75); P.c("mr_bar0", "mr_bar")
    ex("phr_cnt", "if(mphr>0&&mrhy>0,(mph_bar+1)%4,-1)", mx + 300, y + 75); P.c("mr_bar", "phr_cnt", 1, 0)
    P.obj("phr_cnt_v", "value mph_bar", mx + 300, y + 90); P.c("phr_cnt", "phr_cnt_v")
    P.msg("phr_init", "-1", mx + 380, y + 90); P.c("mr_lb", "phr_init"); P.c("phr_init", "phr_cnt_v")
    P.obj("phr_r", "r mel_phrase", mx + 450, y + 60); P.obj("phr_rv", "value mphr", mx + 450, y + 75); P.c("phr_r", "phr_rv")
    pool = "min(max(mrhy,1),3)-1"
    near = f"min(max(int(rh_drive*(mel_po[{pool}+3]-1)+0.5)+random(0,3)-1,0),mel_po[{pool}+3]-1)"
    ex("mr_pick", f"if(rgen,{len(allrows) - 1},mel_po[{pool}]+{near})", mx + 150, y + 85); P.c("mr_bar", "mr_pick")
    # (never the same cell twice in a row: a repeat moves one along, within the pool)
    ex("mr_pick2", f"if(rgen,$f1,if($f1==mel_row,if($f1-mel_po[{pool}]<mel_po[{pool}+3]-1,$f1+1,$f1-1),$f1))", mx + 150, y + 100)
    P.c("mr_pick", "mr_pick2")
    # (phrases: A' and the last A take A's cell again; A keeps its own)
    ex("phr_rowx", "if(mph_bar==1||mph_bar==3,ph_row,$f1)", mx + 150, y + 105); P.c("mr_pick2", "phr_rowx")
    P.obj("mr_row_t", "t f f", mx + 150, y + 115); P.c("phr_rowx", "mr_row_t")
    ex("phr_keep", "if(mph_bar==0,$f1,ph_row)", mx + 300, y + 115); P.c("phr_rowx", "phr_keep")
    P.obj("phr_row_v", "value ph_row", mx + 300, y + 130); P.c("phr_keep", "phr_row_v")
    P.obj("mr_row", "value mel_row", mx + 150, y + 130); P.c("mr_row_t", "mr_row", 1, 0)
    P.obj("mr_row_s", "s mel_cell", mx + 230, y + 130); P.c("mr_row_t", "mr_row_s", 0, 0)
    ex("mr_len", "if(mrhy>0,mel_cells[mel_row*16+$f1],0) ; $f1".replace(" ; ", " " + BS + "; "), mx, y + 150)
    P.c("mr_tt", "mr_len", 0, 0)
    P.obj("mr_tk", "f", mx + 100, y + 165); P.c("mr_len", "mr_tk", 1, 1)
    P.obj("mr_tkv", "value mtick", mx + 140, y + 165); P.c("mr_len", "mr_tkv", 1, 0)
    P.obj("mr_has", "moses 0.5", mx, y + 165); P.c("mr_len", "mr_has", 0, 0)
    P.obj("mr_lt", "t f f", mx, y + 180); P.c("mr_has", "mr_lt", 1, 0)
    ex("mr_ring", f"min(max($f1*tc_P/4*{MEL_RING[0]}+{MEL_RING[1]},{MEL_RING[2]}),{MEL_RING[3]})", mx, y + 195)
    P.c("mr_lt", "mr_ring", 0, 0)
    P.obj("mr_rep", "pack f f f", mx + 100, y + 210)                     # (tick, length, ring) for the tests
    P.c("mr_lt", "mr_rep", 1, 1)
    P.obj("mr_go", "t b b b f", mx, y + 225); P.c("mr_ring", "mr_go"); P.c("mr_go", "mr_rep", 3, 2)
    P.obj("mr_rep_s", "s mel_note", mx + 100, y + 240)
    P.obj("mr_set", "value mlen", mx + 60, y + 240); P.c("mr_go", "mr_set", 3, 0)   # first: this note's ring
    P.obj("mr_route", "t b", mx, y + 240); P.c("mr_go", "mr_route", 1, 0)
    P.msg("mr_clr", "0", mx + 160, y + 255); P.obj("mr_clr_v", "value mlen", mx + 160, y + 270)
    P.c("mr_go", "mr_clr", 0, 0); P.c("mr_clr", "mr_clr_v")                     # last: back to random rings
    P.obj("phr_off_v", "value mphon", mx + 220, y + 270); P.c("mr_clr", "phr_off_v")
    add_phrases(P, ex, "mr_go", 2, mx + 700, y + 150)
    # walking -> the arpeggio's next note; standing -> the still pattern's roll (its chance scaled to the cell)
    P.obj("mr_wk", "value mwalk", mx, y + 255); P.c("mr_route", "mr_wk")
    P.obj("mr_wsel", "sel 0", mx, y + 270); P.c("mr_wk", "mr_wsel")
    P.c("mr_route", "mr_tk"); P.c("mr_tk", "mr_rep", 0, 0); P.c("mr_rep", "mr_rep_s")
    P.obj("mr_st", "t b b", mx, y + 285); P.c("mr_wsel", "mr_st", 0, 0)
    ex("mr_plen", "4*tc_P/max(mel_cn[mel_row],1)+$f1*0", mx + 60, y + 300)
    P.c("mr_st", "mr_plen", 1, 0); P.c("mr_plen", "mu_p", 0, 2)
    P.c("mr_st", "mu_still_gate", 0, 0)
    P.c("mr_wsel", "mu_arp_gate", 1, 0)

def add_phrases(P, ex, src, outlet, px, py):
    """Melody phrases: before a cell note plays, its scale step is chosen here (mphdeg, mphon 1 while it plays):
    made up in A and B (one step, sometimes two, from the last note, kept within CHIME_SPAN of the register's middle:
    the arpeggio's walking, the brightness note's standing; on beats 1 and 3 moved to the nearest chord tone),
    replayed from A's notes (ph_a) in A' and the last A, A' ending on the chord's root. Reports ph_note
    <phase> <tick> <step> <chord root> <scale size> (tests)."""
    S = CHIME_SPAN
    m = lambda v: f"((({v})-mchord+scale_n*20)%scale_n)"
    root = lambda v: "".join(f"if({m(f'{v}+{d}')}==0,{v}+{d}," for d in (0, -1, 1, -2, 2, -3)) + f"{v}+3" + ")" * 6
    P.obj("mph_a", "table ph_a 17", px + 300, py)                          # A's notes by tick (16: a spare slot)
    P.obj("mph_vd", "value mphdeg", px + 300, py + 15); P.obj("mph_vl", "value mph_last", px + 300, py + 30)
    P.obj("mph_go", "t b", px, py); P.c(src, "mph_go", outlet, 0)
    P.obj("mph_on", "value mphr", px, py + 15); P.c("mph_go", "mph_on")
    P.obj("mph_sel", "sel 1", px, py + 30); P.c("mph_on", "mph_sel")
    ex("mph_d", f"if(random(0,100)<{PH_STEP2_PCT},2,1)*if(random(0,2),1,-1)", px, py + 45); P.c("mph_sel", "mph_d")
    C = "if(mwalk,scale_n+mchord+3,int(mbright*2*scale_n+0.5)+scale_n)"
    L = f"min(max(mph_last,{C}-{S}),{C}+{S})"
    ex("mph_cand", f"if(abs({L}+$f1-({C}))>{S},{L}-$f1,{L}+$f1)", px, py + 60); P.c("mph_d", "mph_cand")
    ct = lambda v: f"({m(v)}==0||{m(v)}==2||{m(v)}==4)"
    ex("mph_snap", f"if(mtick%8==0,if({ct('$f1')},$f1,if({ct('$f1-1')},$f1-1,$f1+1)),$f1)", px, py + 75)
    P.c("mph_cand", "mph_snap")
    ex("mph_fin", f"if(mph_bar==3||(mph_bar==1&&mtick!=mel_lo[mel_row]),ph_a[mtick],if(mph_bar==1,{root('ph_a[mtick]')},$f1))",
       px, py + 90)
    P.c("mph_snap", "mph_fin")
    P.obj("mph_t", "t f f f f", px, py + 105); P.c("mph_fin", "mph_t")
    ex("mph_wi", "if(mph_bar==0,mtick,16)+$f1*0", px + 150, py + 120); P.obj("mph_wr", "tabwrite ph_a", px + 100, py + 135)
    P.c("mph_t", "mph_wi", 3, 0); P.c("mph_wi", "mph_wr", 0, 1); P.c("mph_t", "mph_wr", 2, 0)
    P.c("mph_t", "mph_vl", 1, 0)
    P.obj("mph_o", "t b f", px, py + 120); P.c("mph_t", "mph_o", 0, 0)
    P.obj("mph_vd2", "value mphdeg", px + 60, py + 135); P.c("mph_o", "mph_vd2", 1, 0)
    P.msg("mph_one", "1", px, py + 135); P.obj("mph_onv", "value mphon", px, py + 150); P.c("mph_o", "mph_one", 0, 0); P.c("mph_one", "mph_onv")
    ex("mph_rep", "mph_bar ; mtick ; $f1 ; mchord ; scale_n".replace(" ; ", " " + BS + "; "), px + 150, py + 150)
    P.c("mph_o", "mph_rep", 1, 0)
    P.obj("mph_pk", "pack f f f f f", px + 150, py + 165); P.obj("mph_s", "s ph_note", px + 150, py + 180)
    for k in range(5):
        P.c("mph_rep", "mph_pk", k, k)
    P.c("mph_pk", "mph_s")

def add_composer(P, x, y):
    """Music variety (music_var, Music: 0 Classic = as before, 1 Evolving; DadCraft, Oct 21: with a key and scale he
    likes, "the band keeps playing much of the same things"): [dcs_composer] plays chord progressions in sections
    (Home, Lift, Calm, Turn) instead of the Classic pick near the room's brightness every 7 s. Its chord comes in
    where Classic's does (mu_pad_root_v: the anchor scale step for everything that thinks in steps, the slot flip),
    but the four pad notes and the bass come as real notes, so the pentatonic scales can borrow the full scale's
    chords while the melody stays pentatonic. The section moves the melody's register. Classic's picker, pad notes
    and bass are switched off (spigots) while it plays."""
    BSd = BS + "$"
    P.obj("cmp", "dcs_composer", x, y + 200)
    # Classic or Evolving: the switches, then (Evolving) the chord now or (Classic) Classic's chord now
    P.obj("cmp_r_var", "r music_var", x, y)
    P.obj("cmp_var_ch", "change 0", x, y + 10); P.c("cmp_r_var", "cmp_var_ch")   # (Classic at load: nothing to do)
    P.obj("cmp_var_t", "t f f f", x, y + 20); P.c("cmp_var_ch", "cmp_var_t")
    P.obj("cmp_is", ">= 0.5", x, y + 40); P.obj("cmp_not", "< 0.5", x + 60, y + 40)
    P.c("cmp_var_t", "cmp_is", 2, 0); P.c("cmp_var_t", "cmp_not", 2, 0)
    for i in range(4):
        P.c("cmp_not", f"mu_pad{i}_hcl", 0, 1)
    P.c("cmp_not", "ba_hcl", 0, 1); P.c("cmp_not", "mu_chord_go", 0, 1)
    P.obj("cmp_gate", "spigot 0", x, y + 230); P.c("cmp_is", "cmp_gate", 0, 1)
    P.obj("cmp_now", "sel 0 1", x, y + 60); P.c("cmp_var_t", "cmp_now", 0, 0)
    P.c("cmp_now", "mu_chord_go", 0, 0)
    P.msg("cmp_start", "start", x + 60, y + 80); P.c("cmp_now", "cmp_start", 1, 0); P.c("cmp_start", "cmp")
    # what it listens to: the scale (after its tables), the key (the same chord, new notes), the music starting,
    # the bars of the clock (the room's brightness first), the tempo and Intensity
    P.obj("cmp_sc", "f", x + 140, y); P.c("mu_scale_t", "cmp_sc", 1, 1); P.c("mu_scale_t", "cmp_sc", 0, 0)
    P.msg("cmp_sc_m", f"scale {BSd}1", x + 140, y + 20); P.c("cmp_sc", "cmp_sc_m"); P.c("cmp_sc_m", "cmp")
    P.msg("cmp_again", "again", x + 220, y + 20); P.c("mu_key_t", "cmp_again", 0, 0); P.c("cmp_again", "cmp")
    P.c("mu_on_t", "cmp_start", 1, 0)
    P.obj("cmp_r_tick", "r tick16", x + 300, y); P.obj("cmp_bar_sel", "sel 0", x + 300, y + 20)
    P.obj("cmp_bar_t", "t b b", x + 300, y + 40); P.c("cmp_r_tick", "cmp_bar_sel"); P.c("cmp_bar_sel", "cmp_bar_t")
    P.obj("cmp_br", "value mbright", x + 360, y + 60); P.msg("cmp_br_m", f"bright {BSd}1", x + 360, y + 80)
    P.c("cmp_bar_t", "cmp_br", 1, 0); P.c("cmp_br", "cmp_br_m"); P.c("cmp_br_m", "cmp")
    P.msg("cmp_bar", "bar", x + 300, y + 100); P.c("cmp_bar_t", "cmp_bar", 0, 0); P.c("cmp_bar", "cmp")
    for k, (rcv, sel) in enumerate((("tempo", "tempo"), ("intensity", "energy"), ("comp_seed", "seed"), ("chord_color", "color"), ("new_pieces", "renew"))):
        P.obj(f"cmp_r{k}", f"r {rcv}", x + 460 + 80 * k, y); P.msg(f"cmp_m{k}", f"{sel} {BSd}1", x + 460 + 80 * k, y + 20)
        P.c(f"cmp_r{k}", f"cmp_m{k}"); P.c(f"cmp_m{k}", "cmp")
    # a chord: the four pad notes and the bass held first, then the anchor goes where Classic's root goes; that
    # flips the crossfade slot and then asks for the notes (pad: outlet 0, bass: outlet 1, after the slot flip)
    P.c("cmp", "cmp_gate", 0, 0)
    P.obj("cmp_s_chord", "s comp_chord", x + 100, y + 250); P.c("cmp_gate", "cmp_s_chord")   # (for the tests)
    P.obj("cmp_up", "unpack f f f f f f", x, y + 260); P.c("cmp_gate", "cmp_up")
    P.c("cmp_up", "mu_pad_root_v", 0, 0)
    P.obj("cmp_ask_pad", "t b", x, y + 300); P.c("mu_pad_root_v", "cmp_ask_pad", 0, 0)
    P.obj("cmp_ask_bass", "t b", x + 60, y + 300); P.c("mu_pad_root_v", "cmp_ask_bass", 1, 0)
    for j, (nm, dest, ask) in enumerate([("bass", "ba_fold", "cmp_ask_bass")] + [(f"v{i}", f"mu_pad{i}_hz", "cmp_ask_pad") for i in range(4)]):
        xx = x + 120 + 70 * j
        P.obj(f"cmp_{nm}", "f", xx, y + 320); P.c("cmp_up", f"cmp_{nm}", j + 1, 1); P.c(ask, f"cmp_{nm}")
        P.obj(f"cmp_{nm}_k", "+ 0", xx, y + 340); P.c(f"cmp_{nm}", f"cmp_{nm}_k"); P.c("mu_key_root", f"cmp_{nm}_k", 0, 1)
        P.obj(f"cmp_{nm}_hz", "mtof", xx, y + 360); P.c(f"cmp_{nm}_k", f"cmp_{nm}_hz")
        P.obj(f"cmp_{nm}_g", "spigot 0", xx, y + 380); P.c("cmp_is", f"cmp_{nm}_g", 0, 1)
        P.c(f"cmp_{nm}_hz", f"cmp_{nm}_g"); P.c(f"cmp_{nm}_g", dest)
    # the section: its register for the melody (0 in Classic), and a report
    P.obj("cmp_su", "unpack f f", x + 500, y + 230); P.c("cmp", "cmp_su", 1, 0)
    P.obj("cmp_s_sect", "s comp_section", x + 600, y + 250); P.c("cmp_su", "cmp_s_sect", 0, 0)
    P.obj("cmp_reg", "f", x + 560, y + 260); P.c("cmp_su", "cmp_reg", 1, 0)
    P.obj("cmp_reg_on", "*", x + 560, y + 280); P.c("cmp_reg", "cmp_reg_on")
    P.c("cmp_var_t", "cmp_reg_on", 1, 1)                              # (set before the bang below re-sends)
    P.obj("cmp_reg_b", "t b", x + 640, y + 60); P.c("cmp_var_t", "cmp_reg_b", 0, 0); P.c("cmp_reg_b", "cmp_reg")
    P.c("cmp_reg_on", "mu_sreg_add", 0, 1)
    # Melodies that develop (mel_dev, stage 2): instead of a random step from the last chime, each chime asks the
    # composer for the next note of its motif's statements, around the same middle (the brightness note) and on the
    # chord sounding now; the sensors' and the section's registers and Melody phrases still apply after it
    P.obj("cmp_r_dev", "r mel_dev", x + 700, y + 400); P.obj("cmp_dev_t", "t f f", x + 700, y + 420)
    P.obj("cmp_dev_is", ">= 0.5", x + 700, y + 440); P.obj("cmp_dev_not", "< 0.5", x + 760, y + 440)
    P.c("cmp_r_dev", "cmp_dev_t"); P.c("cmp_dev_t", "cmp_dev_is", 1, 0); P.c("cmp_dev_t", "cmp_dev_not", 0, 0)
    P.c("cmp_dev_not", "mu_dev_cl", 0, 1)
    P.obj("cmp_dev_g", "spigot 0", x + 700, y + 460); P.c("cmp_dev_is", "cmp_dev_g", 0, 1); P.c("mu_pick", "cmp_dev_g")
    P.obj("cmp_dev_b", "t b", x + 700, y + 480); P.c("cmp_dev_g", "cmp_dev_b")
    P.obj("cmp_dev_q", "expr int(mbright*2*scale_n+0.5)+scale_n" + BS + "; mchord" + BS + "; scale_n", x + 700, y + 500)
    P.c("cmp_dev_b", "cmp_dev_q")
    P.obj("cmp_dev_pk", "pack f f f", x + 700, y + 520)
    for k in (2, 1, 0):
        P.c("cmp_dev_q", "cmp_dev_pk", k, k)
    P.msg("cmp_dev_m", f"note {BSd}1 {BSd}2 {BSd}3", x + 700, y + 540); P.c("cmp_dev_pk", "cmp_dev_m"); P.c("cmp_dev_m", "cmp")
    P.c("cmp", "mu_pick_ph", 2, 0)
    P.obj("cmp_s_note", "s comp_note", x + 700, y + 560); P.c("cmp", "cmp_s_note", 2, 0)   # (for the tests)
    P.obj("cmp_s_form", "s comp_form", x + 800, y + 560); P.c("cmp", "cmp_s_form", 3, 0)   # (for the tests)
    # Patterns made by rules (pat_gen, stage 3; the drums' side is in add_rhythm): rgen points the walking notes and
    # the drums at their generated rows; at each walking bar's first step the composer writes the next figure
    P.obj("cmp_r_gen", "r pat_gen", x + 1000, y + 400); P.obj("cmp_gen_v", "value rgen", x + 1000, y + 420)
    P.c("cmp_r_gen", "cmp_gen_v")
    P.obj("cmp_arp_g", "spigot 0", x + 1000, y + 440); P.c("cmp_r_gen", "cmp_arp_g", 0, 1); P.c("mu_pat_bar", "cmp_arp_g")
    P.obj("cmp_arp_t", "t b b", x + 1000, y + 460); P.c("cmp_arp_g", "cmp_arp_t")
    P.obj("cmp_arp_pace", "f", x + 1000, y + 480); P.c("mu_pace_f", "cmp_arp_pace", 0, 1)
    P.obj("cmp_arp_n", "value scale_n", x + 1060, y + 480)
    P.obj("cmp_arp_pk", "pack f f", x + 1000, y + 500)
    P.c("cmp_arp_t", "cmp_arp_n", 1, 0); P.c("cmp_arp_n", "cmp_arp_pk", 0, 1); P.c("cmp_arp_t", "cmp_arp_pace", 0, 0); P.c("cmp_arp_pace", "cmp_arp_pk")
    P.msg("cmp_arp_m", f"arp {BSd}1 {BSd}2", x + 1000, y + 520); P.c("cmp_arp_pk", "cmp_arp_m"); P.c("cmp_arp_m", "cmp")
    P.obj("cmp_arp_rt", "route arp", x + 1000, y + 540); P.c("cmp", "cmp_arp_rt", 4, 0)
    P.obj("cmp_arp_at", f"list prepend {len(ARP_PATTERNS) * len(ARP_PATTERNS[0])}", x + 1000, y + 560); P.c("cmp_arp_rt", "cmp_arp_at")
    P.obj("cmp_arp_s", "s arp_pat", x + 1000, y + 580); P.c("cmp_arp_at", "cmp_arp_s")
    P.obj("cmp_arp_snd", "s arp_gen_bar", x + 1100, y + 580); P.c("cmp_arp_rt", "cmp_arp_snd")   # (for the tests)
    # ...and Melody rhythm's bar: at each bar's tick 0 (before its first onset is read) the composer writes the
    # next cell into mel_cells' last row, its note count into mel_cn and its last onset into mel_lo
    G = 2 * (len(MEL_CELLS_STRAIGHT) + len(MEL_CELLS_DOTTED))   # (add_melody_rhythm's rows: straight, dotted, mixed = both)
    P.obj("cmp_cell_g", "spigot 0", x + 1200, y + 440); P.c("cmp_r_gen", "cmp_cell_g", 0, 1); P.c("mr_bar0", "cmp_cell_g")
    P.obj("cmp_cell_t", "t b b", x + 1200, y + 460); P.c("cmp_cell_g", "cmp_cell_t")
    P.obj("cmp_cell_d", "value rh_drive", x + 1200, y + 480); P.obj("cmp_cell_s", "value mrhy", x + 1260, y + 480)
    P.obj("cmp_cell_pk", "pack f f", x + 1200, y + 500)
    P.c("cmp_cell_t", "cmp_cell_s", 1, 0); P.c("cmp_cell_s", "cmp_cell_pk", 0, 1); P.c("cmp_cell_t", "cmp_cell_d", 0, 0); P.c("cmp_cell_d", "cmp_cell_pk")
    P.msg("cmp_cell_m", f"cell {BSd}1 {BSd}2", x + 1200, y + 520); P.c("cmp_cell_pk", "cmp_cell_m"); P.c("cmp_cell_m", "cmp")
    P.obj("cmp_cell_rt", "route cell", x + 1200, y + 540); P.c("cmp", "cmp_cell_rt", 4, 0)
    P.obj("cmp_cell_sp", "list split 16", x + 1200, y + 560); P.c("cmp_cell_rt", "cmp_cell_sp")
    P.obj("cmp_cell_at", f"list prepend {G * 16}", x + 1200, y + 580); P.c("cmp_cell_sp", "cmp_cell_at", 0, 0)
    P.obj("cmp_cell_tab", "s mel_cells", x + 1200, y + 600); P.c("cmp_cell_at", "cmp_cell_tab")
    P.obj("cmp_cell_up", "unpack f f", x + 1300, y + 580); P.c("cmp_cell_sp", "cmp_cell_up", 1, 0)
    for k, tab in enumerate(("mel_cn", "mel_lo")):
        P.obj(f"cmp_cell_p{k}", f"list prepend {G}", x + 1300 + 80 * k, y + 600); P.c("cmp_cell_up", f"cmp_cell_p{k}", k, 0)
        P.obj(f"cmp_cell_w{k}", f"s {tab}", x + 1300 + 80 * k, y + 620); P.c(f"cmp_cell_p{k}", f"cmp_cell_w{k}")
    P.obj("cmp_cell_snd", "s cell_gen_bar", x + 1400, y + 560); P.c("cmp_cell_rt", "cmp_cell_snd")   # (for the tests)

def add_music(P):
    """A reactive synth that plays only notes of the chosen key and scale. It listens to the mic:
    loudness sets how busy and loud it is, the brightness of the sound (zero-crossing rate) sets the
    register and the chords, sudden sounds trigger a chime, and footsteps play an arpeggio that gets
    louder as the pace rises. Returns the name of the mono output signal."""
    x = 4600
    comma = " " + BS + ","
    d = BS + "$"

    def ex(name, text, xx, yy):
        return P.obj(name, "expr " + text.replace(",", BS + ",").replace("$", d), xx, yy)

    # ---- scale and key: the scale lives in a table; a degree (0, 1, 2...) becomes a pitch ----
    P.obj("mu_scale_tab", "table scale_iv 8", x, 20)
    P.obj("mu_pcdeg_tab", "table pc_deg 12", x + 100, 20)
    P.obj("mu_dr_tab", "table drift_opts 8", x + 200, 40)
    P.obj("mu_r_scale", "r music_scale", x, 50)
    P.obj("mu_scale_t", "t b f", x, 65)                                  # table first, then re-pick the chord
    sel = P.obj("mu_scale_sel", "sel " + " ".join(str(i) for i in range(len(SCALES))), x, 80)
    P.c("mu_r_scale", "mu_scale_t"); P.c("mu_scale_t", sel, 1, 0)
    for i, iv in enumerate(SCALES):
        pool, contrast = CHORD_POOLS[i]
        opts = drift_options(iv, pool)
        text = (f"{BS}; drift_opts 0 {' '.join(str(v) for v in opts)} {BS}; drift_n {len(opts)} {BS}; drift_set 0"
                f" {BS}; scale_iv 0 {' '.join(str(v) for v in iv)} {BS}; scale_n {len(iv)}"
                f" {BS}; pc_deg 0 {' '.join(str(v) for v in pc_to_degree(iv))}"
                f" {BS}; chord_pool 0 {' '.join(str(v) for v in pool)} {BS}; pool_n {len(pool)}"
                f" {BS}; pool_x {-1 if contrast is None else contrast}")
        P.msg(f"mu_scale_msg{i}", text, x + 60 * i, 110)
        P.c(sel, f"mu_scale_msg{i}", i, 0)
    P.obj("mu_pool_tab", "table chord_pool 8", x + 200, 20)
    P.obj("mu_r_n", "r scale_n", x + 520, 50)
    P.obj("mu_v_n", "value scale_n", x + 520, 80)
    P.c("mu_r_n", "mu_v_n")
    P.obj("mu_r_key", "r music_key", x + 640, 50)
    P.obj("mu_key_t", "t b f", x + 640, 65)
    P.obj("mu_key_root", "+ 48", x + 640, 80)                    # key note in the octave from C3
    P.c("mu_r_key", "mu_key_t"); P.c("mu_key_t", "mu_key_root", 1, 0)

    def degree_to_hz(tag, xx, yy):
        """Float in: scale degree (0 = the key note). Float out: frequency in Hz."""
        P.obj(f"{tag}_t", "t f f", xx, yy)
        P.obj(f"{tag}_mod", "mod 7", xx + 60, yy + 30)
        P.obj(f"{tag}_iv", "tabread scale_iv", xx + 60, yy + 60)
        P.obj(f"{tag}_div", "div 7", xx, yy + 30)
        P.obj(f"{tag}_oct", "* 12", xx, yy + 60)
        P.obj(f"{tag}_a", "+ 0", xx, yy + 90)
        P.obj(f"{tag}_b", "+ 48", xx, yy + 120)
        P.obj(f"{tag}_hz", "mtof", xx, yy + 150)
        P.c(f"{tag}_t", f"{tag}_mod", 1, 0); P.c(f"{tag}_t", f"{tag}_div", 0, 0)
        P.c(f"{tag}_mod", f"{tag}_iv"); P.c(f"{tag}_iv", f"{tag}_a", 0, 1)
        P.c(f"{tag}_div", f"{tag}_oct"); P.c(f"{tag}_oct", f"{tag}_a")
        P.c(f"{tag}_a", f"{tag}_b"); P.c(f"{tag}_b", f"{tag}_hz")
        P.c("mu_r_n", f"{tag}_mod", 0, 1); P.c("mu_r_n", f"{tag}_div", 0, 1)
        P.c("mu_key_root", f"{tag}_b", 0, 1)
        return f"{tag}_t", f"{tag}_hz"

    # ---- listening: loudness, brightness, onsets ----
    P.obj("mu_r_react", "r music_react", x, 300)
    P.obj("mu_r_level", "r mic_level", x + 150, 300)
    P.obj("mu_raw_sig", "sig~", x + 300, 360)                           # the same level, averaged over ~1 s
    P.obj("mu_slow_lp", "lop~ 0.6", x + 300, 390)
    P.c("mu_r_level", "mu_raw_sig"); P.c("mu_raw_sig", "mu_slow_lp")
    # Brightness: how often the mic signal crosses zero, as a frequency.
    P.obj("mu_zc_amp", "*~ 1e+09", x + 450, 285)                       # x > 0 -> 1, else 0 (an [expr~] cost more)
    P.obj("mu_zc_sign", "clip~ 0 1", x + 450, 300); P.c("mu_zc_amp", "mu_zc_sign")
    P.obj("mu_zc_diff", "rzero~ 1", x + 450, 330)
    P.obj("mu_zc_abs", "abs~", x + 450, 360)
    P.obj("mu_zc_lp", "lop~ 1.5", x + 450, 390)
    P.c("mic_gated", "mu_zc_amp"); P.c("mu_zc_sign", "mu_zc_diff")
    P.c("mu_zc_diff", "mu_zc_abs"); P.c("mu_zc_abs", "mu_zc_lp")
    P.obj("mu_half_sr", "/ 2", x + 620, 330)
    P.c("sr", "mu_half_sr")
    # Ten times a second: store the loudness (shaped by "react"), the brightness and the slow level.
    P.obj("mu_load", "loadbang", x + 700, 270)
    P.obj("mu_tick", "metro 100", x + 700, 300)
    P.c("mu_load", "mu_tick")
    P.obj("mu_snap_slow", "snapshot~", x + 300, 420)
    P.obj("mu_snap_zc", "snapshot~", x + 450, 420)
    P.obj("mu_tick_t", "t b b b", x + 700, 330)
    P.c("mu_tick", "mu_tick_t")
    P.c("mu_tick_t", "mu_snap_zc", 0, 0); P.c("mu_tick_t", "mu_snap_slow", 1, 0)
    P.c("mu_slow_lp", "mu_snap_slow"); P.c("mu_zc_lp", "mu_snap_zc")
    # Loudness above the room's own quiet: the floor follows the quietest level lately (and forgets slowly),
    # so a quiet room plays nothing and a sudden or lively sound plays notes, whatever the mic's noise.
    P.obj("mu_fl_t", "t f f b", x + 150, 420)
    P.c("mu_snap_slow", "mu_fl_t")
    P.obj("mu_fl_rd", "value mfloor", x + 220, 450)
    ex("mu_fl_new", "min($f1,$f2+0.03)", x + 150, 480)
    P.obj("mu_fl_t2", "t f f", x + 150, 510)
    P.obj("mu_fl_wr", "value mfloor", x + 220, 540)
    ex("mu_fl_thr", "min(max($f1,46),58)+4", x + 150, 570)
    ex("mu_loud01", "min(max(($f1-$f2)/32,0),1)", x + 300, 600)     # 0 = at the room's quiet, 1 = 32 dB above it
    P.c("mu_fl_t", "mu_fl_rd", 2, 0); P.c("mu_fl_rd", "mu_fl_new", 0, 1)
    P.c("mu_fl_t", "mu_fl_new", 1, 0); P.c("mu_fl_new", "mu_fl_t2")
    P.c("mu_fl_t2", "mu_fl_wr", 1, 0); P.c("mu_fl_t2", "mu_fl_thr", 0, 0)
    P.c("mu_fl_thr", "mu_loud01", 0, 1)
    P.c("mu_fl_t", "mu_loud01", 0, 0)
    P.obj("mu_fl_init", "loadbang", x + 220, 420)
    P.msg("mu_fl_init_msg", "58", x + 220, 435)
    P.obj("mu_fl_init_wr", "value mfloor", x + 220, 465)
    P.c("mu_fl_init", "mu_fl_init_msg"); P.c("mu_fl_init_msg", "mu_fl_init_wr")
    ex("mu_eff", "(1-$f2)*0.4+$f2*$f1", x + 150, 640)                  # liveliness: the room (react = 1) or a steady middle (react = 0)
    P.c("mu_loud01", "mu_eff"); P.c("mu_r_react", "mu_eff", 0, 1)
    P.obj("mu_v_loud", "value mloud", x + 150, 480)
    P.c("mu_eff", "mu_v_loud")
    P.obj("mu_v_loud01", "value mloud01", x + 220, 620); P.c("mu_loud01", "mu_v_loud01")   # (before Ear for the Room)
    ex("mu_zc_hz", "$f1*$f2", x + 450, 450)
    P.c("mu_snap_zc", "mu_zc_hz"); P.c("mu_half_sr", "mu_zc_hz", 0, 1)
    ex("mu_bright", "min(max((69+17.3123*ln(max($f1,1)/440)-50)/50,0),1)", x + 450, 480)    # 0 = dull and low, 1 = hissy and high
    P.c("mu_zc_hz", "mu_bright")
    P.obj("mu_v_bright", "value mbright", x + 450, 510)
    P.c("mu_bright", "mu_v_bright")
    # Sudden loud sounds: the level jumps well above its recent average.
    ex("mu_onset_thr", "14-8*$f1", x, 330)
    P.c("mu_r_react", "mu_onset_thr")
    ex("mu_onset", "(($f1-$f2)>$f3)*($f1>46)", x + 300, 450)   # a real jump, and not just room noise
    P.c("mu_r_level", "mu_onset"); P.c("mu_snap_slow", "mu_onset", 0, 1)
    P.c("mu_onset_thr", "mu_onset", 0, 2)
    P.obj("mu_onset_sel", "sel 1", x + 300, 480)
    P.c("mu_onset", "mu_onset_sel")

    # ---- on / off: the switch opens the gates and the first chord appears at once ----
    P.obj("mu_r_on", "r music_on", x + 800, 50)
    P.obj("mu_on_sel", "sel 1", x + 800, 80)
    P.c("mu_r_on", "mu_on_sel")

    # ---- chimes: four voices taken in turn, each a bell-like tone with two overtones ----
    cx = x
    cy = 600
    P.obj("mu_deg", "t b b b f", cx, cy)                                  # in: a velocity from 0 to 1
    # (the three paths below each end in "mu_note": a scale degree in, a chime out)
    degree_in, degree_hz = degree_to_hz("mu_c", cx + 200, cy + 60)
    P.obj("mu_note_t", "t b f", cx + 200, cy + 250)
    P.c(degree_hz, "mu_note_t")
    P.obj("mu_v_freq", "value cfreq", cx + 260, cy + 280)
    P.c("mu_note_t", "mu_v_freq", 1, 0)
    P.obj("mu_rr", "f", cx + 200, cy + 310)
    P.obj("mu_rr_add", "+ 1", cx + 200, cy + 340)
    P.obj("mu_rr_mod", "mod 4", cx + 200, cy + 370)
    P.obj("mu_rr_sel", "sel 0 1 2 3", cx + 200, cy + 400)
    # Each chime gets its own length and colour, so a run of notes does not sound stamped out.
    P.obj("mu_tim_t", "t b b b", cx + 200, cy + 290)
    P.c("mu_note_t", "mu_tim_t", 0, 0)
    P.obj("mu_tim_rd", "random 1000", cx + 330, cy + 310)
    # (a note from a Melody rhythm cell brings its own ring, mlen ms; any other chime a random one)
    P.obj("mu_tim_ml", "value mlen", cx + 400, cy + 325)
    ex("mu_tim_dec", f"if(mlen>0,mlen,{CHIME_DECAY_MS[0]}+{CHIME_DECAY_MS[1] - CHIME_DECAY_MS[0]}*$f1/1000)", cx + 330, cy + 340)
    P.obj("mu_tim_wd", "value cdec", cx + 330, cy + 370)
    P.c("mu_tim_t", "mu_tim_rd", 2, 0); P.c("mu_tim_rd", "mu_tim_dec"); P.c("mu_tim_dec", "mu_tim_wd")
    P.obj("mu_tim_rb", "random 1000", cx + 450, cy + 310)
    ex("mu_tim_br", f"{CHIME_OVERTONE[0]}+{CHIME_OVERTONE[1] - CHIME_OVERTONE[0]}*$f1/1000", cx + 450, cy + 340)
    P.obj("mu_tim_wb", "value cbright", cx + 450, cy + 370)
    P.c("mu_tim_t", "mu_tim_rb", 1, 0); P.c("mu_tim_rb", "mu_tim_br"); P.c("mu_tim_br", "mu_tim_wb")
    P.c("mu_tim_t", "mu_rr", 0, 0)
    P.c("mu_rr", "mu_rr_add"); P.c("mu_rr_add", "mu_rr_mod"); P.c("mu_rr_mod", "mu_rr", 0, 1)
    P.c("mu_rr", "mu_rr_sel")
    P.obj("mu_v_vel", "value cvel", cx, cy + 40)
    # Mix (DadCraft, Oct 13): chimes and walking notes share these voices, so their trims scale each note's velocity
    for nm, recv in (("mixc", "mix_chimes"), ("mixw", "mix_walk")):
        P.obj(f"mu_{nm}_r", f"r {recv}", cx - 200 + (60 if nm == "mixw" else 0), cy - 20)
        P.obj(f"mu_{nm}_v", f"value {nm}", cx - 200 + (60 if nm == "mixw" else 0), cy - 5); P.c(f"mu_{nm}_r", f"mu_{nm}_v")
    ex("mu_v_trim", "$f1*mixc", cx - 60, cy + 30)
    P.c("mu_deg", "mu_v_trim", 3, 0); P.c("mu_v_trim", "mu_v_vel")
    voices = []
    for k in range(4):
        vx = cx + 600 + k * 260
        P.obj(f"mu_v{k}_trig", "t b b b b", vx, cy)
        P.c("mu_rr_sel", f"mu_v{k}_trig", k, 0)
        P.obj(f"mu_v{k}_f", "value cfreq", vx + 60, cy + 30)
        P.obj(f"mu_v{k}_ft", "t f f f", vx + 60, cy + 60)
        P.obj(f"mu_v{k}_f2", "* 2", vx + 100, cy + 90)
        P.obj(f"mu_v{k}_f3", "* 3", vx + 160, cy + 90)
        P.obj(f"mu_v{k}_o1", "osc~", vx + 60, cy + 120)
        P.obj(f"mu_v{k}_o2", "osc~", vx + 100, cy + 120)
        P.obj(f"mu_v{k}_o3", "osc~", vx + 160, cy + 120)
        P.obj(f"mu_v{k}_g2", "*~ 0.3", vx + 100, cy + 150)
        P.obj(f"mu_v{k}_g3", "*~ 0.1", vx + 160, cy + 150)
        P.c(f"mu_v{k}_trig", f"mu_v{k}_f", 3, 0)
        P.obj(f"mu_v{k}_br", "value cbright", vx + 220, cy + 30)
        P.obj(f"mu_v{k}_br3", "/ 3", vx + 220, cy + 60)
        P.c(f"mu_v{k}_trig", f"mu_v{k}_br", 2, 0)
        P.c(f"mu_v{k}_br", f"mu_v{k}_g2", 0, 1); P.c(f"mu_v{k}_br", f"mu_v{k}_br3"); P.c(f"mu_v{k}_br3", f"mu_v{k}_g3", 0, 1)
        P.obj(f"mu_v{k}_dec", "value cdec", vx + 30, cy + 15)
        P.c(f"mu_v{k}_trig", f"mu_v{k}_dec", 1, 0)
        P.c(f"mu_v{k}_f", f"mu_v{k}_ft")
        P.c(f"mu_v{k}_ft", f"mu_v{k}_o1", 2, 0)
        P.c(f"mu_v{k}_ft", f"mu_v{k}_f2", 1, 0); P.c(f"mu_v{k}_f2", f"mu_v{k}_o2")
        P.c(f"mu_v{k}_ft", f"mu_v{k}_f3", 0, 0); P.c(f"mu_v{k}_f3", f"mu_v{k}_o3")
        P.c(f"mu_v{k}_o2", f"mu_v{k}_g2"); P.c(f"mu_v{k}_o3", f"mu_v{k}_g3")
        P.sig2(f"mu_v{k}_s12", "+~", f"mu_v{k}_o1", f"mu_v{k}_g2", vx + 60, cy + 180)
        P.sig2(f"mu_v{k}_s", "+~", f"mu_v{k}_s12", f"mu_v{k}_g3", vx + 60, cy + 210)
        P.obj(f"mu_v{k}_vel", "value cvel", vx, cy + 30)
        P.c(f"mu_v{k}_trig", f"mu_v{k}_vel", 0, 0)
        P.obj(f"mu_v{k}_pk", "pack f 1600", vx, cy + 45)                 # velocity, decay (ms)
        P.c(f"mu_v{k}_dec", f"mu_v{k}_pk", 0, 1)
        P.msg(f"mu_v{k}_env_msg", ("0, " + BS + "$1 4, 0 " + BS + "$2 4").replace(",", comma), vx, cy + 60)
        P.obj(f"mu_v{k}_env", "vline~", vx, cy + 90)
        # Melody sounds: the synth plays unless found only and something is caught; the found twin plays unless synth
        caught = "(fs_filled[0]+fs_filled[1]+fs_filled[2]>0)"
        ex(f"mu_v{k}_vsyn", f"$f1*if(mel_src[0]==1&&{caught},0,1)*(kmelcl>0)", vx, cy + 38)
        P.obj(f"mu_v{k}_vt", "t f f", vx, cy + 34); P.c(f"mu_v{k}_vel", f"mu_v{k}_vt")
        P.c(f"mu_v{k}_vt", f"mu_v{k}_vsyn", 1, 0)
        # its audio runs only while it rings (its own switch~ group chv<k>): on at the note, off after the fade
        P.obj(f"mu_v{k}_vst", "t f f", vx, cy + 41); P.c(f"mu_v{k}_vsyn", f"mu_v{k}_vst"); P.c(f"mu_v{k}_vst", f"mu_v{k}_pk", 0, 0)
        P.obj(f"mu_v{k}_von", "moses 0.0001", vx + 30, cy + 41); P.c(f"mu_v{k}_vst", f"mu_v{k}_von", 1, 0)
        P.obj(f"mu_v{k}_vbt", "t b b", vx + 30, cy + 42); P.c(f"mu_v{k}_von", f"mu_v{k}_vbt", 1, 0)
        P.msg(f"mu_v{k}_swon", f"{BS}; sw_chv{k} 1", vx + 30, cy + 43); P.c(f"mu_v{k}_vbt", f"mu_v{k}_swon", 1, 0)
        P.obj(f"mu_v{k}_swdc", "value cdec", vx + 60, cy + 43); P.obj(f"mu_v{k}_swt", "+ 300", vx + 60, cy + 44)
        P.obj(f"mu_v{k}_swdl", "del", vx + 60, cy + 45); P.msg(f"mu_v{k}_swoff", f"{BS}; sw_chv{k} 0", vx + 60, cy + 46)
        P.c(f"mu_v{k}_vbt", f"mu_v{k}_swdc", 0, 0); P.c(f"mu_v{k}_swdc", f"mu_v{k}_swt"); P.c(f"mu_v{k}_swt", f"mu_v{k}_swdl")
        P.c(f"mu_v{k}_swdl", f"mu_v{k}_swoff")
        P.obj(f"grp_chv{k}_r", f"r sw_chv{k}", 10, 10); P.obj(f"grp_chv{k}_sw", "switch~", 10, 40); P.c(f"grp_chv{k}_r", f"grp_chv{k}_sw")
        P.c(f"mu_v{k}_vt", f"fm{k}_in", 0, 0)
        add_found_note(P, k, vx, cy + 1500, ex, caught, gate="(mel_src[0]>=1)*(kmelcl>0)")
        # the kits' melody voices (Kits, Oct 17; the layer Oct 19): the same note (a voice on Classic ignores it)
        P.obj(f"mu_v{k}_kv", "expr $f1 ; cbright ; cdec ; cfreq".replace("$", BS + "$").replace(" ; ", " " + BS + "; "),
              vx + 120, cy + 34)                                  # (this section's ex() doesn't escape ";")
        P.c(f"mu_v{k}_vt", f"mu_v{k}_kv", 0, 0)
        P.obj(f"mu_v{k}_kp", "pack f f f f", vx + 120, cy + 36)
        for o in (3, 2, 1, 0):
            P.c(f"mu_v{k}_kv", f"mu_v{k}_kp", o, o)
        P.msg(f"mu_v{k}_km", f"note {BS}$4 {BS}$1 {BS}$3 {BS}$2", vx + 120, cy + 38)
        P.c(f"mu_v{k}_kp", f"mu_v{k}_km"); P.c(f"mu_v{k}_km", "mu_kv_mel"); P.c(f"mu_v{k}_km", "mu_kv_mel2")
        P.msg(f"mu_v{k}_vmn", f"{BS}; voc_ctl mnote {BS}$1", vx + 200, cy + 38); P.c(f"mu_v{k}_kv", f"mu_v{k}_vmn", 3, 0)  # (the vocoder's melody)
        P.c(f"mu_v{k}_pk", f"mu_v{k}_env_msg"); P.c(f"mu_v{k}_env_msg", f"mu_v{k}_env")
        P.sig2(f"mu_v{k}_out", "*~", f"mu_v{k}_s", f"mu_v{k}_env", vx + 60, cy + 240)
        voices.append(f"mu_v{k}_out")
    acc = voices[0]
    for i, v in enumerate(voices[1:]):
        acc = P.sig2(f"mu_chime_acc{i}", "+~", acc, v, cx + 600, cy + 280 + 30 * i)
    P.obj("mel_src_t", "table mel_src 1", cx + 900, cy + 380)
    P.obj("mel_src_r", "r melody_src", cx + 900, cy + 400); P.obj("mel_src_w", "tabwrite mel_src", cx + 900, cy + 420)
    P.c("mel_src_r", "mel_src_w")
    facc = "fm0_out"
    for i in range(1, 4):
        facc = P.sig2(f"fm_acc{i}", "+~", facc, f"fm{i}_out", cx + 800, cy + 1800 + 20 * i)
    # an automatic level: the rings measured every 5 ms, turned down (never up) toward FM_LEVEL_DB, then a soft limit
    P.obj("fm_env", "env~ 1024 256", cx + 900, cy + 1880); P.c(facc, "fm_env")
    ex("fm_agc", f"dbtorms(100+min(0,({FM_LEVEL_DB}-max($f1,1))*{FM_LEVEL_RATIO}))", cx + 900, cy + 1895)
    P.c("fm_env", "fm_agc")
    P.obj("fm_agc_pk", "pack f 20", cx + 900, cy + 1910); P.obj("fm_agc_ln", "line~", cx + 900, cy + 1925)
    P.c("fm_agc", "fm_agc_pk"); P.c("fm_agc_pk", "fm_agc_ln")
    P.sig2("fm_lvl", "*~", facc, "fm_agc_ln", cx + 800, cy + 1940)
    soft_limit(P, "fm_lim", "fm_lvl", FM_LIMIT, 1.0, cx + 800, cy + 1960)
    P.obj("fm_cc_r", "r chime_char", cx + 1000, cy + 1880); P.obj("fm_cc_v", "value fm_cc", cx + 1000, cy + 1895); P.c("fm_cc_r", "fm_cc_v")
    P.obj("fm_rg_r", "r fm_ring", cx + 1100, cy + 1880); P.obj("fm_rg_v", "value fmring", cx + 1100, cy + 1895); P.c("fm_rg_r", "fm_rg_v")
    P.sig2("mu_chime_cl0", "+~", acc, "fm_lim", cx + 600, cy + 365)
    P.sig2("mu_chime_cl", "*~", "mu_chime_cl0", "kit_mel_cl", cx + 600, cy + 367)    # (Kits: Classic's share)
    P.sig2("mu_chime_all", "+~", "mu_chime_cl", "mu_kit_mel_mix", cx + 600, cy + 370)
    P.obj("mu_chime_scale", "*~ 0.22", cx + 600, cy + 380)
    P.c("mu_chime_all", "mu_chime_scale")
    P.obj("mu_chime_lp", "lop~ 3000", cx + 600, cy + 410)
    P.c("mu_chime_scale", "mu_chime_lp")
    # mu_deg: store velocity, then pick the next note. The melody walks a step or two from the last chime
    # (never the same note twice), around the register the brightness asks for, and turns back at its edges.
    P.obj("mu_jit", "random 4", cx + 100, cy + 40)
    ex("mu_jit_m", "$f1-2+($f1>=2)", cx + 100, cy + 70)                   # -2, -1, +1 or +2 scale steps
    P.c("mu_deg", "mu_jit", 2, 0); P.c("mu_jit", "mu_jit_m")
    P.obj("mu_rd_last", "value clast", cx + 160, cy + 70)
    P.c("mu_deg", "mu_rd_last", 1, 0)
    ex("mu_pick", f"min(max(if(abs($f4+$f2-int($f1*2*$f3+0.5)-$f3)>{CHIME_SPAN},$f4-$f2,$f4+$f2),"
                  f"int($f1*2*$f3+0.5)+$f3-{CHIME_SPAN}),int($f1*2*$f3+0.5)+$f3+{CHIME_SPAN})", cx, cy + 100)
    P.obj("mu_rd_pick", "value mbright", cx, cy + 70)                     # a bang makes it output its number
    P.c("mu_deg", "mu_rd_pick", 0, 0)
    P.c("mu_rd_pick", "mu_pick", 0, 0)
    P.c("mu_r_n", "mu_pick", 0, 2)
    P.c("mu_jit_m", "mu_pick", 0, 1)
    P.c("mu_rd_last", "mu_pick", 0, 3)
    P.obj("mu_pick_t", "t f f", cx, cy + 130)
    P.obj("mu_wr_last", "value clast", cx + 60, cy + 160)
    ex("mu_pick_ph", "if(mphon,mphdeg,$f1)", cx, cy + 115)                  # (a Melody phrase brings its own note)
    P.obj("mu_ph_v", "value mphon", cx + 100, cy + 115)
    P.obj("mu_dev_cl", "spigot 1", cx, cy + 108)                         # (Melodies that develop: add_composer)
    P.c("mu_pick", "mu_dev_cl"); P.c("mu_dev_cl", "mu_pick_ph"); P.c("mu_pick_ph", "mu_pick_t"); P.c("mu_pick_t", "mu_wr_last", 1, 0)
    P.obj("mu_reg_add", "+ 0", cx + 200, cy + 50)                       # register from the sensors (the app)
    P.obj("mu_reg_r", "r music_register", cx + 260, cy + 30); P.c("mu_reg_r", "mu_reg_add", 0, 1)
    P.obj("mu_sreg_add", "+ 0", cx + 200, cy + 60)                   # ...and the Evolving section's (add_composer)
    P.c("mu_reg_add", "mu_sreg_add"); P.c("mu_sreg_add", degree_in)
    P.c("mu_pick_t", "mu_reg_add")
    P.obj("mu_note_s", "s chime_deg", cx - 100, cy + 160)              # every note played, for the tests
    P.c("mu_pick_t", "mu_note_s")
    P.obj("mu_chime_s", "s chime_note", cx - 100, cy + 180)            # chimes only: the app's motif memory listens
    P.c("mu_pick_t", "mu_chime_s")
    # A remembered phrase coming back (motif memory, in the app): [motif <scale step> <velocity>( plays one chime.
    P.obj("mu_mo_up", "unpack f f", cx - 300, cy + 20)
    P.obj("mu_mo_vel", "value cvel", cx - 240, cy + 40)
    P.obj("mu_mo_t", "t f f f", cx - 300, cy + 60)
    P.obj("mu_mo_s", "s chime_deg", cx - 300, cy + 90)
    ex("mu_mo_trim", "$f1*mixc", cx - 240, cy + 30)
    P.c("mu_mo_q", "mu_mo_up"); P.c("mu_mo_up", "mu_mo_trim", 1, 0); P.c("mu_mo_trim", "mu_mo_vel"); P.c("mu_mo_up", "mu_mo_t", 0, 0)
    P.c("mu_mo_t", "mu_mo_s", 2, 0); P.c("mu_mo_t", "mu_reg_add", 0, 0)
    P._late_dn = getattr(P, "_late_dn", []) + [("mu_mo_t", "mu_dnc_t")]   # it counts as a chime for density

    # ---- density memory: every chime adds 1 (a walking-arpeggio note, the rhythm bed, DENSITY_ARP_WEIGHT), and the
    # count fades away over DENSITY_S ----
    for nm, w, src in (("c", 1, "mu_pick_t"), ("a", DENSITY_ARP_WEIGHT, "mu_rest_gate")):
        P.obj(f"mu_dn{nm}_t", "t b", x - 300 + 60 * (nm == "a"), 620)
        P.obj(f"mu_dn{nm}_rd", "value mdens", x - 300 + 60 * (nm == "a"), 640)
        P.obj(f"mu_dn{nm}_add", f"+ {w}", x - 300 + 60 * (nm == "a"), 660)
        P.obj(f"mu_dn{nm}_wr", "value mdens", x - 300 + 60 * (nm == "a"), 680)
        P._late_dn = getattr(P, "_late_dn", []) + [(src, f"mu_dn{nm}_t")]
        P.c(f"mu_dn{nm}_t", f"mu_dn{nm}_rd"); P.c(f"mu_dn{nm}_rd", f"mu_dn{nm}_add"); P.c(f"mu_dn{nm}_add", f"mu_dn{nm}_wr")
    P.obj("mu_dn_tick", "metro 250", x - 200, 600)
    P.c("mu_load", "mu_dn_tick")
    P.obj("mu_dn_rd2", "value mdens", x - 200, 640)
    P.obj("mu_dn_fade", f"* {math.exp(-0.25 / DENSITY_S):.5f}", x - 200, 660)
    P.obj("mu_dn_wr2", "value mdens", x - 200, 680)
    P.obj("mu_dn_on", "* 1", x - 200, 670)                              # density_on 0 (tests only) forgets at once
    P.obj("mu_dn_r_on", "r density_on", x - 120, 650); P.c("mu_dn_r_on", "mu_dn_on", 0, 1)
    P.c("mu_dn_tick", "mu_dn_rd2"); P.c("mu_dn_rd2", "mu_dn_fade"); P.c("mu_dn_fade", "mu_dn_on"); P.c("mu_dn_on", "mu_dn_wr2")
    keep = f"1000/(1+pow($f2/{DENSITY_BUSY},2))"                       # chance in 1000 a chime still plays

    # Path 1, sudden sounds: a chime whose loudness follows the room.
    P.obj("mu_ons_gate", "spigot 1", x + 300, 510)
    P.obj("mu_ons_gate_on", "spigot", x + 300, 540)
    P.c(handling_gate(P, "mu_ons_hg", "mu_onset_sel", x + 400, 480, wait=False), "mu_ons_gate")   # not handling noise
    P.obj("mu_ons_t", "t b b b", x + 300, 570)
    P.msg("mu_ons_close", "0", x + 360, 600); P.msg("mu_ons_open", "1", x + 420, 600)
    P.obj("mu_ons_wait", "del 700", x + 380, 630)
    P.c("mu_ons_gate", "mu_ons_gate_on"); P.c("mu_ons_gate_on", "mu_ons_t")
    P.c("mu_ons_t", "mu_ons_close", 2, 0); P.c("mu_ons_close", "mu_ons_gate", 0, 1)
    P.c("mu_ons_t", "mu_ons_wait", 1, 0); P.c("mu_ons_wait", "mu_ons_open"); P.c("mu_ons_open", "mu_ons_gate", 0, 1)
    ex("mu_ons_vel", "(0.3+0.5*$f1)*$f2", x + 300, 660)              # in a pocket the "sudden sound" may be rustle
    P.obj("mu_rd_ons", "value mloud", x + 300, 640)
    P.obj("mu_ons_rt", "t b b", x + 240, 610)
    P.obj("mu_ons_hush", "value phush", x + 240, 640)
    P.c("mu_ons_t", "mu_ons_rt", 0, 0); P.c("mu_ons_rt", "mu_ons_hush", 1, 0); P.c("mu_ons_hush", "mu_ons_vel", 0, 1)
    P.c("mu_ons_rt", "mu_rd_ons", 0, 0); P.c("mu_rd_ons", "mu_ons_vel")
    # While you walk, a sudden-sound chime waits for the next footstep or half-step, so it falls in with the
    # arpeggio instead of landing anywhere (DadCraft). Standing still it plays at once.
    P.obj("mu_q_now", "spigot 1", x + 200, 690)                      # not walking: straight through
    P.obj("mu_q_hold", "spigot 0", x + 300, 690)                     # walking: keep the latest one for the next slot
    P.obj("mu_dn1_t", "t f b b", x + 200, 670)                        # busy lately? skip some
    P.obj("mu_dn1_rnd", "random 1000", x + 260, 680); P.obj("mu_dn1_rd", "value mdens", x + 330, 680)
    ex("mu_dn1_keep", f"$f1<{keep}", x + 260, 690)
    P.obj("mu_dn1_gate", "spigot", x + 200, 695)
    P.c("mu_ons_vel", "mu_dn1_t"); P.c("mu_dn1_t", "mu_dn1_rd", 2, 0); P.c("mu_dn1_rd", "mu_dn1_keep", 0, 1)
    P.c("mu_dn1_t", "mu_dn1_rnd", 1, 0); P.c("mu_dn1_rnd", "mu_dn1_keep"); P.c("mu_dn1_keep", "mu_dn1_gate", 0, 1)
    P.c("mu_dn1_t", "mu_dn1_gate", 0, 0)
    P.c("mu_dn1_gate", "mu_q_now"); P.c("mu_dn1_gate", "mu_q_hold")
    P.c("mu_q_now", "mu_deg")
    P.obj("mu_q_store", "f", x + 300, 720)
    P.c("mu_q_hold", "mu_q_store", 0, 1)
    P.obj("mu_q_has", "moses 0.000001", x + 300, 750)
    P.obj("mu_q_go", "t f b", x + 300, 780)                          # clear the store, then play
    P.msg("mu_q_clear", "0", x + 380, 750)
    P.c("mu_q_store", "mu_q_has"); P.c("mu_q_has", "mu_q_go", 1, 0)
    P.c("mu_q_go", "mu_q_clear", 1, 0); P.c("mu_q_clear", "mu_q_store", 0, 1)
    P.c("mu_q_go", "mu_deg", 0, 0)
    P.obj("mu_q_flush", "spigot 1", x + 380, 720); P.c("mu_q_flush", "mu_q_store")   # (stopping plays a waiting chime)

    # Path 2, a slow pattern while you are still: now and then a chime, more often in a livelier room.
    P.obj("mu_still_tick", "metro 250", x + 800, 600)
    P.c("mu_load", "mu_still_tick")
    P.obj("mu_still_free", "spigot 1", x + 800, 615)                       # (synced: the clock's 8ths instead)
    P.obj("mu_still_gate", "spigot 1", x + 800, 630)
    P.obj("mu_still_on", "spigot", x + 800, 660)
    P.obj("mu_mr_stf", "spigot 1", x + 860, 615)                          # (Melody rhythm: the cells roll it instead)
    P.c("mu_still_tick", "mu_still_free"); P.c("mu_still_free", "mu_mr_stf"); P.c("mu_mr_stf", "mu_still_gate")
    P.c("mu_still_gate", "mu_still_on")
    P.obj("mu_still_t", "t b b", x + 800, 690)
    # Chimes Only Answer (chime_answer): the still pattern stays silent, whatever rolls it (free, the 8ths, Melody rhythm)
    P.obj("mu_still_ans", "spigot 1", x + 740, 675)
    P.obj("mu_ans_r", "r chime_answer", x + 680, 645); ex("mu_ans_not", "$f1<0.5", x + 680, 660)
    P.c("mu_ans_r", "mu_ans_not"); P.c("mu_ans_not", "mu_still_ans", 0, 1)
    P.c("mu_still_on", "mu_still_ans"); P.c("mu_still_ans", "mu_still_t")
    P.obj("mu_roll", "random 1000", x + 800, 720)
    ex("mu_chance", "$f1<$f2", x + 800, 750)
    # (a calm build adds a gentle chance of its own: up to CALM_CHIME_P in 1000 per 0.25 s at its peak, value calmc)
    P.obj("mu_calm_r", "r calm_chimes", x + 1100, 650); P.obj("mu_calm_v", "value calmc", x + 1100, 665); P.c("mu_calm_r", "mu_calm_v")
    ex("mu_p", f"(320*pow($f1,3)/(1+pow($f2/{DENSITY_BUSY},2))+calmc*{CALM_CHIME_P}+bandch*{BAND_CHIME_P})*$f3/250", x + 900, 720)   # chance in 1000 every 0.25 s: 0 in a quiet room, about 1 in 3 when lively (less when busy lately)
    P.obj("mu_p_dn", "value mdens", x + 980, 690)
    P.obj("mu_rd_p", "value mloud", x + 900, 690)
    P.c("mu_still_t", "mu_roll", 0, 0)
    P.obj("mu_p_t", "t b b", x + 900, 680)
    P.c("mu_still_t", "mu_p_t", 1, 0); P.c("mu_p_t", "mu_p_dn", 1, 0); P.c("mu_p_dn", "mu_p", 0, 1)
    P.c("mu_p_t", "mu_rd_p", 0, 0); P.c("mu_rd_p", "mu_p"); P.c("mu_p", "mu_chance", 0, 1)
    # ms between rolls (set by "sync"; written into the hot inlet: until Oct 15 it went into the cold one, never
    # reached mu_p, and synced rolls on the 8ths came ~20-25% more often than the free ones)
    P.obj("mu_p_len", "f 250", x + 1060, 690); P.c("mu_p_len", "mu_p", 0, 2)
    P.msg("mu_p_len0", "250", x + 1060, 670); P.c("mu_load", "mu_p_len0"); P.c("mu_p_len0", "mu_p", 0, 2)
    P.c("mu_roll", "mu_chance")
    P.obj("mu_chance_sel", "sel 1", x + 800, 780)
    P.c("mu_chance", "mu_chance_sel")
    ex("mu_still_vel", "(0.12+0.5*$f1)*$f2", x + 800, 840)
    P.obj("mu_still_load", "t b b", x + 800, 810)
    P.obj("mu_rd_still", "value mloud", x + 900, 810)
    P.obj("mu_still_hush", "value phush", x + 980, 810)
    P.c("mu_still_load", "mu_still_hush", 1, 0); P.c("mu_still_hush", "mu_still_vel", 0, 1)
    P.c("mu_chance_sel", "mu_still_load"); P.c("mu_still_load", "mu_rd_still"); P.c("mu_rd_still", "mu_still_vel")
    P.c("mu_still_vel", "mu_deg")
    P.msg("mu_still_off", "0", x + 900, 600); P.msg("mu_still_back", "1", x + 960, 600)
    P.c("mu_still_off", "mu_still_gate", 0, 1); P.c("mu_still_back", "mu_still_gate", 0, 1)

    # Path 3, footsteps: an arpeggio around the current chord. Louder when you walk faster.
    P.obj("mu_r_step", "r pulse", x + 1100, 600)
    P.obj("mu_step_t", "t b f", x + 1100, 630)
    P.c("mu_r_step", "mu_step_t")
    ex("mu_pace", "1/max($f1,0.25)", x + 1200, 660)
    ex("mu_pace_f", "min(max(($f1-1)/1.6,0),1)", x + 1200, 690)       # 0 = ambling (1 step/s), 1 = brisk (2.6+)
    # A harder step pushes the note. Walking notes ease in (walk_ease, DadCraft Oct 21: "extremely loud on walking",
    # glide to where they should be): at most about the standing chimes' loudness, following a pace that moves
    # WALK_EASE_PACE of the way to each new step's, and a walk starts soft, full after WALK_EASE_STEPS steps.
    ex("mu_pace_vel", f"if(wkease,({WALK_EASE_VEL[0]}+{WALK_EASE_VEL[1]}*$f1)*(0.9+0.2*$f2)*$f3,(0.4+0.6*$f1)*(0.8+0.4*$f2))",
       x + 1200, 720)
    P.obj("mu_r_strength", "r step_strength", x + 1350, 660)
    P.c("mu_r_strength", "mu_pace_vel", 0, 1)
    P.obj("mu_r_ease", "r walk_ease", x + 1450, 640); P.obj("mu_ease_v", "value wkease", x + 1450, 655)
    P.c("mu_r_ease", "mu_ease_v")
    P.obj("mu_r_pace", "r step", x + 1200, 640)                       # the pace comes from your real steps
    P.obj("mu_pace_in", "t f b", x + 1200, 648); P.c("mu_r_pace", "mu_pace_in")
    P.c("mu_pace_in", "mu_pace"); P.c("mu_pace", "mu_pace_f")
    ex("mu_ease_p", f"if(wkease,$f2+{WALK_EASE_PACE}*($f1-$f2),$f1)", x + 1250, 700)
    P.obj("mu_ease_pt", "t f f", x + 1250, 710)
    P.c("mu_pace_f", "mu_ease_p"); P.c("mu_ease_p", "mu_ease_pt"); P.c("mu_ease_pt", "mu_ease_p", 1, 1)
    P.c("mu_ease_pt", "mu_pace_vel", 0, 0)
    # this walk's steps so far (counted before the pace, so the note reads it), back to none when the walk ends
    P.obj("mu_ease_n", "f", x + 1400, 680); P.obj("mu_ease_inc", "+ 1", x + 1440, 680)
    ex("mu_ease_in", f"min(1,{WALK_EASE_START}+{1 - WALK_EASE_START}*$f1/{WALK_EASE_STEPS})", x + 1400, 700)
    P.c("mu_pace_in", "mu_ease_n", 1, 0); P.c("mu_ease_n", "mu_ease_inc"); P.c("mu_ease_inc", "mu_ease_n", 0, 1)
    P.c("mu_ease_n", "mu_ease_in"); P.c("mu_ease_in", "mu_pace_vel", 0, 2)
    # (each arpeggio note sets its own velocity, trimmed by the Mix's walking notes, just before it plays)
    P.obj("mu_arp_pv", "value apvel", x + 1000, 700); ex("mu_arp_trim", "$f1*mixw", x + 1000, 715)
    P.obj("mu_arp_cv", "value cvel", x + 1000, 730); P.c("mu_arp_pv", "mu_arp_trim"); P.c("mu_arp_trim", "mu_arp_cv")
    P.obj("mu_arp_gate", "spigot", x + 1100, 660)
    P.obj("mu_mr_arp", "spigot 1", x + 1050, 645)                         # (Melody rhythm: the cells play it instead)
    P.c("mu_step_t", "mu_mr_arp", 0, 0); P.c("mu_mr_arp", "mu_arp_gate", 0, 0)
    P.obj("mu_arp_t", "t b b b b", x + 1100, 690)
    P.c("mu_arp_gate", "mu_arp_t")
    P.obj("mu_v_chord", "value mchord", x + 1100, 720)
    n_pat, bar = len(ARP_PATTERNS), len(ARP_PATTERNS[0])
    P.obj("mu_ptab", f"table arp_pat {(n_pat + 1) * bar}", x + 1300, 20)    # (the last row: Patterns made by rules)
    P.msg("mu_ptab_init", f"{BS}; arp_pat 0 {' '.join(str(v) for pat in ARP_PATTERNS + (ARP_PATTERNS[0],) for v in pat)}", x + 1300, 50)
    P.c("mu_load", "mu_ptab_init")
    # step counter within the bar; at the start of each bar a different pattern is picked
    P.obj("mu_pat_i", "f", x + 1100, 750)
    P.obj("mu_pat_add", "+ 1", x + 1100, 780)
    P.obj("mu_pat_mod", f"mod {bar}", x + 1100, 810)
    P.c("mu_pat_i", "mu_pat_add"); P.c("mu_pat_add", "mu_pat_mod"); P.c("mu_pat_mod", "mu_pat_i", 0, 1)
    P.obj("mu_pat_it", "t f f", x + 1020, 780)
    P.c("mu_pat_i", "mu_pat_it")
    P.obj("mu_pat_bar", "sel 0", x + 960, 810)
    P.obj("mu_pat_rnd", "random 3", x + 960, 840)
    # the pace's pattern or a neighbour ($f1 0..2), never the one just played ($f2), pace 0..1 ($f3)
    top = n_pat - 1
    # (a repeat moves toward the pace's own pattern, so a stroll stays on the calm ones and a brisk walk on the busy)
    tgt = f"int($f3*{top}+0.5)"
    ex("mu_pat_new", f"if(min(max({tgt}+$f1-1, 0), {top})==$f2, "
                     f"if($f2<{tgt}, $f2+1, if($f2>{tgt}, $f2-1, if($f2<{top}, $f2+1, $f2-1))), "
                     f"min(max({tgt}+$f1-1, 0), {top}))", x + 960, 870)
    P.c("mu_pace_f", "mu_pat_new", 0, 2)
    P.obj("mu_pat_cur", "t f f", x + 960, 900)
    P.c("mu_pat_it", "mu_pat_bar", 1, 0); P.c("mu_pat_bar", "mu_pat_rnd"); P.c("mu_pat_rnd", "mu_pat_new")
    P.c("mu_pat_new", "mu_pat_cur"); P.c("mu_pat_cur", "mu_pat_new", 1, 1)
    ex("mu_pat_idx", f"$f1+{bar}*if(rgen,{n_pat},$f2)", x + 1100, 830)
    P.c("mu_pat_cur", "mu_pat_idx", 0, 1); P.c("mu_pat_it", "mu_pat_idx", 0, 0)
    P.obj("mu_pat_tab", "tabread arp_pat", x + 1100, 845)
    P.c("mu_pat_idx", "mu_pat_tab")
    ex("mu_arp_deg", "$f1+$f2+$f3", x + 1100, 870)
    P.obj("mu_arp_n", "value scale_n", x + 1250, 780)
    P.c("mu_arp_t", "mu_arp_pv", 3, 0)                                 # (first: this note's velocity)
    P.c("mu_arp_t", "mu_arp_n", 2, 0)                                 # bang: output the scale size
    P.c("mu_arp_n", "mu_arp_deg", 0, 2)
    P.c("mu_arp_t", "mu_v_chord", 1, 0); P.c("mu_v_chord", "mu_arp_deg", 0, 1)
    P.c("mu_arp_t", "mu_pat_i", 0, 0)
    P.c("mu_pat_tab", "mu_arp_deg")
    # now and then a step rests (the bar still moves on)
    P.obj("mu_rest_rnd", "random 100", x + 1300, 690)
    ex("mu_rest_play", f"$f1>=({ARP_REST_SLOW_PCT}-{ARP_REST_SLOW_PCT - ARP_REST_FAST_PCT}*$f2"
                       f"+min({DENSITY_ARP_MAX},max(0,$f3-{DENSITY_ARP_FROM})*{DENSITY_ARP_PCT}))", x + 1300, 720)
    P.obj("mu_rest_dn", "value mdens", x + 1400, 690)
    P.c("mu_pace_f", "mu_rest_play", 0, 1)        # strolling rests more than walking briskly
    P.obj("mu_rest_gate", "spigot 1", x + 1100, 885)
    P.obj("mu_rest_t", "t b b", x + 1300, 680)
    P.c("mu_arp_t", "mu_rest_t", 3, 0); P.c("mu_rest_t", "mu_rest_dn", 1, 0); P.c("mu_rest_dn", "mu_rest_play", 0, 2)
    P.c("mu_rest_t", "mu_rest_rnd", 0, 0); P.c("mu_rest_rnd", "mu_rest_play"); P.c("mu_rest_play", "mu_rest_gate", 0, 1)
    ex("mu_arp_ph", "if(mphon,mphdeg,$f1)", x + 1180, 878); P.c("mu_arp_deg", "mu_arp_ph"); P.c("mu_arp_ph", "mu_rest_gate")
    P.c("mu_rest_gate", "mu_reg_add"); P.c("mu_rest_gate", "mu_note_s")
    for src, dst in getattr(P, "_late_dn", []):
        P.c(src, dst)
    # The arpeggio note itself needs the chime path's tail (frequency, round robin): it joins at degree_in.
    # Walking state: while steps keep coming the "still" pattern stays quiet.
    P.obj("mu_walk_t", "t b b", x + 1100, 900)
    P.c("mu_step_t", "mu_walk_t", 0, 0)
    P.obj("mu_walk_del", "del 2500", x + 1160, 930)
    P.c("mu_walk_t", "mu_still_off", 1, 0); P.c("mu_walk_t", "mu_walk_del", 0, 0)
    P.c("mu_walk_del", "mu_still_back")
    P.msg("mu_ease_rst", "0", x + 1400, 940)                             # a new walk eases in from the start
    P.c("mu_walk_del", "mu_ease_rst"); P.c("mu_ease_rst", "mu_ease_n", 0, 1); P.c("mu_ease_rst", "mu_ease_p", 0, 1)
    # Chime slots: every footstep, and halfway to the next one. Walking ends -> anything waiting plays.
    # (with the beat synced, chimes wait for the clock's next 8th walking or standing: see "sync" below)
    P.msg("mu_q_walk", "1", x + 200, 660); P.msg("mu_q_stop", "0", x + 200, 630)
    P.obj("mu_q_w", "f", x + 230, 645)
    P.c("mu_walk_t", "mu_q_walk", 1, 0); P.c("mu_walk_del", "mu_q_stop")
    P.c("mu_q_walk", "mu_q_w"); P.c("mu_q_stop", "mu_q_w")
    P.obj("mu_q_wv", "value mwalk", x + 280, 645); P.c("mu_q_walk", "mu_q_wv"); P.c("mu_q_stop", "mu_q_wv")
    ex("mu_q_wait", "$f1||$f2", x + 230, 665)                          # walking, or synced to the beat
    P.c("mu_q_w", "mu_q_wait", 0, 0)
    P.obj("mu_q_wait_t", "t f f", x + 230, 675); P.c("mu_q_wait", "mu_q_wait_t")
    P.obj("mu_q_now_v", "== 0", x + 180, 680)
    P.c("mu_q_wait_t", "mu_q_now_v", 0, 0); P.c("mu_q_now_v", "mu_q_now", 0, 1); P.c("mu_q_wait_t", "mu_q_hold", 1, 1)
    P.c("mu_walk_del", "mu_q_flush")
    P.obj("mu_q_half_clip", "clip 0.25 1.2", x + 1250, 930)
    P.obj("mu_q_half_ms", "* 500", x + 1250, 960)
    P.obj("mu_q_half", "del 250", x + 1160, 990)
    P.c("mu_step_t", "mu_q_half_clip", 1, 0); P.c("mu_q_half_clip", "mu_q_half_ms"); P.c("mu_q_half_ms", "mu_q_half", 0, 1)
    P.c("mu_walk_t", "mu_q_half", 0, 0)                                  # restarts the half-step timer
    P.obj("mu_q_steps", "spigot 1", x + 1100, 1000)                       # (the step slots only when not synced)
    P.c("mu_walk_t", "mu_q_steps", 0, 0)                                 # on the step itself
    P.c("mu_q_half", "mu_q_steps"); P.c("mu_q_steps", "mu_q_store")
    # Groove: after half a minute of steady steps (the app sends "groove 1"), a soft note joins on every
    # half-step, a third above the last arpeggio note, fading in over a few seconds. It stops with the groove
    # or when you stop walking.
    P.obj("mu_gr_r", "r groove", x + 1400, 900)
    P.obj("mu_gr_chg", "change", x + 1400, 920)
    P.c("mu_gr_r", "mu_gr_chg")
    P.obj("mu_gr_gate", "spigot", x + 1250, 1010)
    P.c("mu_q_half", "mu_gr_gate"); P.c("mu_gr_chg", "mu_gr_gate", 0, 1)
    P.msg("mu_gr_off", "0", x + 1400, 960)
    P.c("mu_walk_del", "mu_gr_off"); P.c("mu_gr_off", "mu_gr_gate", 0, 1); P.c("mu_gr_off", "mu_gr_chg", 0, 0)
    P.obj("mu_gr_sel", "sel 0 1", x + 1400, 990)
    P.msg("mu_gr_down", "0 300", x + 1400, 1010)
    P.msg("mu_gr_up", f"0, 1 {GROOVE_FADE_IN_MS}".replace(",", " " + BS + ","), x + 1460, 1010)
    P.obj("mu_gr_ln", "line 0 50", x + 1400, 1030)
    P.c("mu_gr_chg", "mu_gr_sel"); P.c("mu_gr_sel", "mu_gr_down", 0, 0); P.c("mu_gr_sel", "mu_gr_up", 1, 0)
    P.c("mu_gr_down", "mu_gr_ln"); P.c("mu_gr_up", "mu_gr_ln")
    P.obj("mu_gr_t", "t b b", x + 1250, 1040)
    P.c("mu_gr_gate", "mu_gr_t")
    P.obj("mu_gr_pv", "value apvel", x + 1300, 1060)                   # this step's arpeggio loudness
    ex("mu_gr_vel", f"$f1*{GROOVE_VEL}*$f2*mixw", x + 1300, 1080)
    P.c("mu_gr_ln", "mu_gr_vel", 0, 1)
    P.obj("mu_gr_cv", "value cvel", x + 1300, 1100)
    P.c("mu_gr_t", "mu_gr_pv", 1, 0); P.c("mu_gr_pv", "mu_gr_vel"); P.c("mu_gr_vel", "mu_gr_cv")
    P.obj("mu_gr_last", "value alast", x + 1250, 1080)
    ex("mu_gr_deg", "$f1+2", x + 1250, 1110)
    P.c("mu_gr_t", "mu_gr_last", 0, 0); P.c("mu_gr_last", "mu_gr_deg")
    P.c("mu_gr_deg", "mu_reg_add"); P.c("mu_gr_deg", "mu_note_s")
    P.obj("mu_ap_store", "value apvel", x + 1350, 740)
    P.c("mu_pace_vel", "mu_ap_store")
    P.obj("mu_al_store", "value alast", x + 1150, 900)
    P.c("mu_rest_gate", "mu_al_store")
    # ---- sync: with the walking beat on its tempo clock, every note lands on the drums' grid, walking or still.
    # Sudden-sound chimes wait for the next 8th, the still pattern rolls on the 8ths, motif notes go to the
    # nearest 16th. (Harmony answers stay live: they follow a voice. Pad changes are slow fades.) ----
    sy = 1150
    P.obj("mu_sy_go", "t b b b", x + 200, sy + 40)
    for k, nm in enumerate(("beat_on", "beat_level", "beat_follow")):
        P.obj(f"mu_sy_r{k}", f"r {nm}", x + 200 + 80 * k, sy); P.obj(f"mu_sy_w{k}", f"value msy{k}", x + 200 + 80 * k, sy + 20)
        P.obj(f"mu_sy_b{k}", "t b f", x + 200 + 80 * k, sy + 10)
        P.c(f"mu_sy_r{k}", f"mu_sy_b{k}"); P.c(f"mu_sy_b{k}", f"mu_sy_w{k}", 1, 0); P.c(f"mu_sy_b{k}", "mu_sy_go", 0, 0)
    for k in range(3):
        P.obj(f"mu_sy_v{k}", f"value msy{k}", x + 300 + 80 * k, sy + 60); P.c("mu_sy_go", f"mu_sy_v{k}", k, 0)
    ex("mu_sy", "($f1>0)*($f2>0)*($f3>0.5)", x + 200, sy + 80)
    # (outlets fire right to left: v2 and v1 into the cold inlets, v0 into the hot one last)
    P.c("mu_sy_v0", "mu_sy", 0, 0); P.c("mu_sy_v1", "mu_sy", 0, 1); P.c("mu_sy_v2", "mu_sy", 0, 2)
    P.obj("mu_sy_chg", "change -1", x + 200, sy + 100); P.c("mu_sy", "mu_sy_chg")
    P.obj("mu_sy_t", "t b f f f f", x + 200, sy + 120); P.c("mu_sy_chg", "mu_sy_t")
    P.obj("mu_sy_not", "== 0", x + 300, sy + 140)
    P.obj("mu_sy_s", "s msync", x + 400, sy + 160); P.c("mu_sy_t", "mu_sy_s", 4, 0)   # (for the tests)
    P.c("mu_sy_t", "mu_sy_not", 3, 0)
    P.c("mu_sy_not", "mu_q_steps", 0, 1); P.c("mu_sy_not", "mu_still_free", 0, 1); P.c("mu_sy_not", "mu_q_flush", 0, 1)
    P.c("mu_sy_t", "mu_sy_8", 2, 1); P.c("mu_sy_t", "mu_mo_sy", 2, 1)
    P.c("mu_sy_t", "mu_q_wait", 1, 1)                                     # chimes wait...
    P.c("mu_sy_t", "mu_q_w", 0, 0)                                        # (...applied by re-sending the walking flag)
    # the clock's 8ths: the chime slot and the still pattern's roll
    P.obj("mu_sy_tick", "r tick16", x + 500, sy); P.obj("mu_sy_tk", "t f b", x + 500, sy + 20)
    P.c("mu_sy_tick", "mu_sy_tk")
    P.obj("mu_sy_mod", "mod 2", x + 500, sy + 40); P.obj("mu_sy_ev", "sel 0", x + 500, sy + 60)
    P.obj("mu_sy_8", "spigot 0", x + 500, sy + 80)
    P.c("mu_sy_tk", "mu_sy_mod", 0, 0); P.c("mu_sy_mod", "mu_sy_ev"); P.c("mu_sy_ev", "mu_sy_8")
    P.obj("mu_sy_8t", "t b b b", x + 500, sy + 100); P.c("mu_sy_8", "mu_sy_8t")
    P.obj("mu_sy_P", "value tc_P", x + 600, sy + 120); P.obj("mu_sy_half", "/ 2", x + 600, sy + 140)
    P.c("mu_sy_8t", "mu_sy_P", 2, 0); P.c("mu_sy_P", "mu_sy_half"); P.c("mu_sy_half", "mu_p_len", 0, 0)
    P.c("mu_sy_8t", "mu_q_store", 1, 0)                                    # a waiting chime plays
    P.obj("mu_mr_sts", "spigot 1", x + 560, sy + 110)
    P.c("mu_sy_8t", "mu_mr_sts", 0, 0); P.c("mu_mr_sts", "mu_still_gate")  # the still pattern rolls
    P.obj("mu_sy_250_sel", "sel 1", x + 700, sy + 120); P.msg("mu_sy_250", "250", x + 700, sy + 140)
    P.c("mu_sy_not", "mu_sy_250_sel"); P.c("mu_sy_250_sel", "mu_sy_250"); P.c("mu_sy_250", "mu_p_len", 0, 0)  # (free again)
    # motif notes: played at once if the last 16th was under half a 16th ago, else held until the next one
    P.obj("mu_tk_tm", "timer", x + 800, sy + 40); P.c("mu_sy_tk", "mu_tk_tm", 1, 0)
    P.obj("mu_mo_r", "r motif", x + 800, sy); P.obj("mu_mo_in", "t l b", x + 800, sy + 20)
    P.c("mu_mo_r", "mu_mo_in")
    P.obj("mu_mo_bt", "t b b b", x + 880, sy + 30)
    P.obj("mu_mo_P", "value tc_P", x + 900, sy + 50); P.obj("mu_mo_sy", "f 0", x + 960, sy + 50)
    ex("mu_mo_dl", "if($f3>0.5&&$f1>$f2/8,max($f2/4-$f1,0),0)", x + 800, sy + 80)
    P.c("mu_mo_in", "mu_mo_bt", 1, 0)
    P.c("mu_mo_bt", "mu_mo_sy", 2, 0); P.c("mu_mo_sy", "mu_mo_dl", 0, 2)
    P.c("mu_mo_bt", "mu_mo_P", 1, 0); P.c("mu_mo_P", "mu_mo_dl", 0, 1)
    P.c("mu_mo_bt", "mu_tk_tm", 0, 1); P.c("mu_tk_tm", "mu_mo_dl")
    P.obj("mu_mo_pk", "list append", x + 800, sy + 110)
    P.c("mu_mo_dl", "mu_mo_pk", 0, 1); P.c("mu_mo_in", "mu_mo_pk", 0, 0)
    P.obj("mu_mo_pipe", "pipe 0 0 0", x + 800, sy + 130); P.c("mu_mo_pk", "mu_mo_pipe")
    P.obj("mu_mo_q", "pack f f", x + 800, sy + 150)
    P.c("mu_mo_pipe", "mu_mo_q", 1, 1); P.c("mu_mo_pipe", "mu_mo_q", 0, 0)
    # Gates follow the on/off switch.
    for g in ("mu_ons_gate_on", "mu_still_on", "mu_arp_gate"):
        P.c("mu_r_on", g, 0, 1)
    add_melody_rhythm(P, x, ex)

    # ---- pad: four soft voices (root, third, fifth, octave) that glide to a new chord now and then ----
    px = x + 1500
    P.obj("mu_chord_tick", "metro 7000", px, 20)
    P.c("mu_load", "mu_chord_tick")
    P.obj("mu_chord_go", "spigot 1", px, 40)                       # (closed while the Evolving music picks: add_composer)
    P.obj("mu_chord_go_t", "t b b b", px, 50); P.c("mu_chord_go", "mu_chord_go_t")
    P.c("mu_chord_tick", "mu_chord_go")
    P.c("mu_scale_t", "mu_chord_go", 0, 0); P.c("mu_key_t", "mu_chord_go", 0, 0)
    P.obj("mu_pad_jit", "random 3", px + 80, 80)
    P.obj("mu_pad_jit_m", "- 1", px + 80, 110)
    P.obj("mu_r_pool_n", "r pool_n", px + 240, 80)
    # which of the scale's main chords: brighter sound, higher chord (plus a little wander)
    ex("mu_pad_idx", "int(min(max($f1*($f3-1)+$f2*0.9,0),$f3-1)+0.5)", px, 140)
    P.c("mu_chord_go_t", "mu_pad_jit", 1, 0); P.c("mu_pad_jit", "mu_pad_jit_m"); P.c("mu_pad_jit_m", "mu_pad_idx", 0, 1)
    P.c("mu_r_pool_n", "mu_pad_idx", 0, 2)
    P.obj("mu_rd_root", "value mbright", px + 160, 110)
    P.c("mu_chord_go_t", "mu_rd_root", 0, 0); P.c("mu_rd_root", "mu_pad_idx")
    P.obj("mu_pad_pool", "tabread chord_pool", px, 160)
    P.c("mu_pad_idx", "mu_pad_pool")
    # key drift: the pool's chords moved by the drift (mdrift scale steps); no contrast chord while drifted
    P.obj("mu_dr_pt", "t f b b", px, 170)
    P.c("mu_pad_pool", "mu_dr_pt")
    P.obj("mu_dr_rd1", "value mdrift", px + 60, 172); P.obj("mu_dr_rd2", "value mdrift", px + 120, 172)
    P.obj("mu_dr_n", "value scale_n", px + 180, 172)
    # now and then the contrast chord instead (when the scale has one)
    P.obj("mu_pad_roll", "random 100", px + 160, 140)
    P.c("mu_chord_go_t", "mu_pad_roll", 2, 0)
    P.obj("mu_r_pool_x", "r pool_x", px + 300, 140)
    ex("mu_pad_root", f"if($f2<{CONTRAST_PCT}&&$f3>=0&&$f4==0,$f3,$f1)", px, 180)
    P.c("mu_dr_pt", "mu_pad_root", 0, 0); P.c("mu_pad_roll", "mu_pad_root", 0, 1); P.c("mu_r_pool_x", "mu_pad_root", 0, 2)
    ex("mu_dr_apply", "($f1+$f2)%$f3", px, 190)
    P.c("mu_dr_pt", "mu_dr_rd1", 2, 0); P.c("mu_dr_rd1", "mu_pad_root", 0, 3)
    P.c("mu_dr_pt", "mu_dr_rd2", 1, 0); P.c("mu_dr_rd2", "mu_dr_apply", 0, 1)
    P.c("mu_dr_pt", "mu_dr_n", 1, 0); P.c("mu_dr_n", "mu_dr_apply", 0, 2)
    P.c("mu_pad_root", "mu_dr_apply")
    P.obj("mu_pad_root_v", "t f f b", px, 200)
    P.c("mu_dr_apply", "mu_pad_root_v")
    # the drift itself: every 3-6 minutes while the music plays (and the switch is on) a new home, often back home
    P.obj("mu_dr_r_set", "r drift_set", px + 300, 20); P.obj("mu_dr_wr", "value mdrift", px + 300, 40)
    P.c("mu_dr_r_set", "mu_dr_wr")
    P.obj("mu_dr_s", "s drift", px + 380, 40)                       # for the tests
    P.c("mu_dr_r_set", "mu_dr_s")
    P.obj("mu_dr_del", "del 240000", px + 300, 80)
    P.obj("mu_dr_sched", "t b b", px + 300, 100)
    P.obj("mu_dr_rms", "random 1000", px + 380, 110)
    ex("mu_dr_ms", f"{DRIFT_MIN_S * 1000}+{(DRIFT_MAX_S - DRIFT_MIN_S)}*$f1", px + 380, 130)
    P.c("mu_dr_sched", "mu_dr_rms", 0, 0); P.c("mu_dr_rms", "mu_dr_ms"); P.c("mu_dr_ms", "mu_dr_del")
    P.c("mu_on_sel", "mu_dr_sched")                                  # the clock starts with the music
    P.obj("mu_dr_now", "r drift_now", px + 460, 80)                  # (tests: drift at once)
    P.obj("mu_dr_fire", "t b b b b b", px + 300, 150)
    P.c("mu_dr_del", "mu_dr_fire"); P.c("mu_dr_now", "mu_dr_fire")
    P.obj("mu_dr_on", "r music_drift", px + 560, 80); P.obj("mu_dr_onv", "f 1", px + 560, 110)
    P.c("mu_dr_on", "mu_dr_onv", 0, 1)
    P.obj("mu_dr_r1", "random 1000", px + 360, 180); P.obj("mu_dr_r2", "random 1000", px + 420, 180)
    P.obj("mu_dr_cur", "value mdrift", px + 480, 180); P.obj("mu_dr_nn", "value drift_nv", px + 540, 180)
    P.obj("mu_dr_rnn", "r drift_n", px + 600, 160); P.obj("mu_dr_wnn", "value drift_nv", px + 600, 180)
    P.c("mu_dr_rnn", "mu_dr_wnn")
    ex("mu_dr_pick", f"if($f5==0,0,if($f3!=0&&$f1<{DRIFT_HOME_PCT * 10},0,if(drift_opts[int($f2*$f4/1000)]==$f3,"
                     f"drift_opts[(int($f2*$f4/1000)+1)%max($f4,1)],drift_opts[int($f2*$f4/1000)])))", px + 300, 210)
    P.c("mu_dr_fire", "mu_dr_onv", 4, 0); P.c("mu_dr_onv", "mu_dr_pick", 0, 4)
    P.c("mu_dr_fire", "mu_dr_nn", 3, 0); P.c("mu_dr_nn", "mu_dr_pick", 0, 3)
    P.c("mu_dr_fire", "mu_dr_cur", 2, 0); P.c("mu_dr_cur", "mu_dr_pick", 0, 2)
    P.c("mu_dr_fire", "mu_dr_r2", 1, 0); P.c("mu_dr_r2", "mu_dr_pick", 0, 1)
    P.c("mu_dr_fire", "mu_dr_r1", 0, 0); P.c("mu_dr_r1", "mu_dr_pick")
    P.obj("mu_dr_out", "t b f", px + 300, 240)
    P.c("mu_dr_pick", "mu_dr_out"); P.c("mu_dr_out", "mu_dr_wr", 1, 0); P.c("mu_dr_out", "mu_dr_s", 1, 0)
    P.c("mu_dr_out", "mu_chord_go", 0, 0); P.c("mu_dr_out", "mu_dr_sched", 0, 0)
    # switching drift off brings the home chord back at once
    P.obj("mu_dr_off", "sel 0", px + 560, 130); P.msg("mu_dr_home", "0", px + 560, 150)
    P.c("mu_dr_on", "mu_dr_off"); P.c("mu_dr_off", "mu_dr_home"); P.c("mu_dr_home", "mu_dr_out")
    P.obj("mu_chord_s", "s pad_chord", px + 240, 230)               # for the tests
    P.c("mu_pad_root_v", "mu_chord_s", 1, 0)
    P.obj("mu_v_chord_set", "value mchord", px + 80, 200)
    P.c("mu_pad_root_v", "mu_v_chord_set", 1, 0)
    # Instant chord when the music is switched on; slow glides afterwards.
    P.msg("mu_glide0", "0", px + 160, 20); P.msg("mu_glide1", str(PAD_GLIDE_MS), px + 200, 20)
    P.obj("mu_glide_s", "s pad_glide", px + 160, 50)
    P.c("mu_glide0", "mu_glide_s"); P.c("mu_glide1", "mu_glide_s")
    P.obj("mu_glide_wait", "del 400", px + 240, 50)
    P.obj("mu_on_t", "t b b b", x + 800, 110)          # glide off, pick the chord, then glide on again
    P.c("mu_on_sel", "mu_on_t")
    P.c("mu_on_t", "mu_glide0", 2, 0); P.c("mu_on_t", "mu_chord_go", 1, 0); P.c("mu_on_t", "mu_glide_wait", 0, 0)
    P.c("mu_glide_wait", "mu_glide1")
    P.obj("mu_r_glide", "r pad_glide", px + 160, 80)
    # Crossfade: every chord change flips to the other of two oscillator sets. The silent set takes the new notes at
    # once and fades in while the old one fades out, so no note slides through pitches outside the key.
    P.obj("mu_slot", "f", px + 400, 200)
    P.obj("mu_slot_add", "+ 1", px + 440, 200)
    P.obj("mu_slot_mod", "mod 2", px + 480, 200)
    P.c("mu_pad_root_v", "mu_slot", 2, 0)
    P.c("mu_slot", "mu_slot_add"); P.c("mu_slot_add", "mu_slot_mod"); P.c("mu_slot_mod", "mu_slot", 0, 1)
    P.obj("mu_slot_s", "s pad_slot", px + 400, 230)
    P.c("mu_slot_mod", "mu_slot_s")
    P.obj("mu_r_slot", "r pad_slot", px + 400, 260)
    for k in (0, 1):
        P.obj(f"mu_xf{k}_is", f"== {k}", px + 400 + 80 * k, 290)
        P.obj(f"mu_xf{k}_pk", f"pack 0 {PAD_GLIDE_MS}", px + 400 + 80 * k, 320)
        P.obj(f"mu_xf{k}", "line~", px + 400 + 80 * k, 350)
        P.c("mu_r_slot", f"mu_xf{k}_is"); P.c(f"mu_xf{k}_is", f"mu_xf{k}_pk"); P.c(f"mu_xf{k}_pk", f"mu_xf{k}")
        P.c("mu_r_glide", f"mu_xf{k}_pk", 0, 1)
        # this set's audio runs only while it sounds: on when it takes a chord, off once it has faded out
        P.obj(f"mu_xf{k}_sel", "sel 1 0", px + 400 + 80 * k, 370); P.c(f"mu_xf{k}_is", f"mu_xf{k}_sel")
        P.obj(f"mu_xf{k}_ont", "t b b", px + 400 + 80 * k, 385)
        P.obj(f"mu_xf{k}_dl", f"del {PAD_GLIDE_MS + 300}", px + 440 + 80 * k, 400)
        P.msg(f"mu_xf{k}_stop", "stop", px + 440 + 80 * k, 385)
        P.c(f"mu_xf{k}_sel", f"mu_xf{k}_ont", 0, 0); P.c(f"mu_xf{k}_ont", f"mu_xf{k}_stop", 1, 0); P.c(f"mu_xf{k}_stop", f"mu_xf{k}_dl")
        P.c(f"mu_xf{k}_sel", f"mu_xf{k}_dl", 1, 0)          # (the switches: add_found_pad, which also knows found sets)
        P.obj(f"mu_xf{k}_gl", "+ 300", px + 520 + 80 * k, 385); P.c("mu_r_glide", f"mu_xf{k}_gl"); P.c(f"mu_xf{k}_gl", f"mu_xf{k}_dl", 0, 1)
        P.obj(f"grp_pads{k}_r", f"r sw_pads{k}", 10, 10); P.obj(f"grp_pads{k}_sw", "switch~", 10, 40); P.c(f"grp_pads{k}_r", f"grp_pads{k}_sw")
    pad_sum = None
    offsets = ["+ 0", "+ 2", "+ 4", None]
    for i in range(4):
        vx = px + i * 160
        if offsets[i]:
            P.obj(f"mu_pad{i}_deg", offsets[i], vx, 230)
            P.c("mu_pad_root_v", f"mu_pad{i}_deg", 0, 0)
        else:
            P.obj(f"mu_pad{i}_deg", "expr $f1+$f2".replace("$", d), vx, 230)
            P.c("mu_pad_root_v", f"mu_pad{i}_deg", 0, 0)
            P.c("mu_r_n", f"mu_pad{i}_deg", 0, 1)
        dg_in, dg_cl = degree_to_hz(f"mu_p{i}", vx, 260)
        P.c(f"mu_pad{i}_deg", dg_in)
        P.obj(f"mu_pad{i}_hcl", "spigot 1", vx, 262); P.c(dg_cl, f"mu_pad{i}_hcl")   # Classic's note (add_composer)
        dg_hz = P.obj(f"mu_pad{i}_hz", "t f", vx, 264); P.c(f"mu_pad{i}_hcl", dg_hz)
        P.obj(f"mu_pad{i}_kf", "f", vx + 60, 270); P.c(dg_hz, f"mu_pad{i}_kf")
        P.msg(f"mu_pad{i}_kpn", f"pnote {i} {BS}$1", vx + 60, 280); P.c(f"mu_pad{i}_kf", f"mu_pad{i}_kpn"); P.c(f"mu_pad{i}_kpn", "mu_kv_pad"); P.c(f"mu_pad{i}_kpn", "mu_kv_pad2")
        P.msg(f"mu_pad{i}_vcn", f"{BS}; voc_ctl cnote {i} {BS}$1", vx + 120, 280); P.c(dg_hz, f"mu_pad{i}_vcn")   # (the vocoder's chord)
        P.obj(f"mu_pad{i}_kre", "r kit_pad_new", vx + 100, 260); P.c(f"mu_pad{i}_kre", f"mu_pad{i}_kf")
        P.obj(f"mu_pad{i}_kre2", "r kit2_pad_new", vx + 100, 265); P.c(f"mu_pad{i}_kre2", f"mu_pad{i}_kf")
        parts = []
        for k in (0, 1):                                   # the two crossfaded sets of this voice
            kx = vx + 75 * k
            P.obj(f"mu_pad{i}_{k}_gate", "spigot", kx, 440)
            P.c(dg_hz, f"mu_pad{i}_{k}_gate"); P.c(f"mu_xf{k}_is", f"mu_pad{i}_{k}_gate", 0, 1)
            P.obj(f"mu_pad{i}_{k}_t", "t f f", kx, 460)
            P.obj(f"mu_pad{i}_{k}_det", "* 1.004", kx + 40, 480)
            P.obj(f"mu_pad{i}_{k}_o1", "osc~", kx, 500)
            P.obj(f"mu_pad{i}_{k}_o2", "osc~", kx + 40, 510)
            P.c(f"mu_pad{i}_{k}_gate", f"mu_pad{i}_{k}_t")
            P.c(f"mu_pad{i}_{k}_t", f"mu_pad{i}_{k}_o1", 0, 0)
            P.c(f"mu_pad{i}_{k}_t", f"mu_pad{i}_{k}_det", 1, 0); P.c(f"mu_pad{i}_{k}_det", f"mu_pad{i}_{k}_o2")
            P.sig2(f"mu_pad{i}_{k}_s", "+~", f"mu_pad{i}_{k}_o1", f"mu_pad{i}_{k}_o2", kx, 530)
            parts.append(P.sig2(f"mu_pad{i}_{k}_x", "*~", f"mu_pad{i}_{k}_s", f"mu_xf{k}", kx, 545))
        P.sig2(f"mu_pad{i}_sum", "+~", parts[0], parts[1], vx, 560)
        P.obj(f"mu_pad{i}_g", "*~ " + ("0.042", "0.033", "0.03", "0.021")[i], vx, 590)
        P.c(f"mu_pad{i}_sum", f"mu_pad{i}_g")
        pad_sum = f"mu_pad{i}_g" if pad_sum is None else P.sig2(f"mu_pad_acc{i}", "+~", pad_sum, f"mu_pad{i}_g", vx, 620)
    P.obj("mu_pad_lp", "lop~ 1500", px, 650)
    P.sig2("mu_pad_cl0", "+~", pad_sum, "fp_sum", px, 636)            # (each set's group runs only while it is heard)
    P.sig2("mu_pad_cl", "*~", "mu_pad_cl0", "kit_pad_cl", px, 638)    # the kit's pad replaces it (Kits)
    P.sig2("mu_pad_all", "+~", "mu_pad_cl", "mu_kit_pad_mix", px, 640)
    P.c("mu_pad_all", "mu_pad_lp")
    # The pad swells and fades with the room, and breathes slowly.
    ex("mu_pad_fac", "0.5+0.5*$f1", px + 300, 650)
    P.obj("mu_pad_fac_sig", "sig~", px + 300, 680); P.obj("mu_pad_fac_lp", "lop~ 0.4", px + 300, 710)
    P.c("mu_pad_fac", "mu_pad_fac_sig"); P.c("mu_pad_fac_sig", "mu_pad_fac_lp")
    P.obj("mu_pad_tick", "metro 200", px + 420, 620)
    P.obj("mu_rd_pad", "value mloud", px + 420, 650)
    P.c("mu_load", "mu_pad_tick"); P.c("mu_pad_tick", "mu_rd_pad"); P.c("mu_rd_pad", "mu_pad_fac")
    P.obj("mu_breath", "osc~ 0.08", px + 300, 740)
    P.obj("mu_breath_g", "*~ 0.125", px + 300, 770)
    P.obj("mu_breath_o", "+~ 0.875", px + 300, 800)
    P.c("mu_breath", "mu_breath_g"); P.c("mu_breath_g", "mu_breath_o")
    P.sig2("mu_pad_a", "*~", "mu_pad_lp", "mu_pad_fac_lp", px, 830)
    P.sig2("mu_pad_b", "*~", "mu_pad_a", "mu_breath_o", px, 860)

    # ---- bass: one low voice under the pad (DadCraft's idea), so the chimes keep the upper register ----
    # It sits on the key's root and only sometimes moves to the chord's root, folded into 65-131 Hz, with a little
    # 2nd and 3rd harmonic so earbuds can play it. A note change dips out and back in (no slides). Each footstep
    # ducks it briefly while the walking beat plays, so it doesn't fight the kick.
    bx = px + 900
    P.obj("ba_in", "t f b b", bx, 230)
    P.c("mu_pad_root_v", "ba_in", 1, 0)
    P.obj("ba_roll", "random 100", bx + 80, 250)
    P.c("ba_in", "ba_roll", 1, 0)
    P.obj("ba_home", "value mdrift", bx + 160, 250)                    # the home note moves with the key drift
    P.c("ba_in", "ba_home", 2, 0)
    ex("ba_pick", f"if($f2<{BASS_FOLLOW_PCT},$f1,$f3)", bx, 270)
    P.c("ba_home", "ba_pick", 0, 2)
    P.c("ba_in", "ba_pick", 0, 0); P.c("ba_roll", "ba_pick", 0, 1)
    ba_deg, ba_hz = degree_to_hz("bad", bx, 290)
    P.c("ba_pick", ba_deg)
    ex("ba_fold", "if($f1/2>131,$f1/4,$f1/2)", bx, 460)
    P.obj("ba_hcl", "spigot 1", bx, 450); P.c(ba_hz, "ba_hcl"); P.c("ba_hcl", "ba_fold")   # Classic's bass (add_composer)
    P.obj("ba_chg", "change", bx, 480)
    P.c("ba_fold", "ba_chg")
    P.obj("ba_ct", "t f b", bx, 500)
    P.c("ba_chg", "ba_ct")
    P.msg("ba_dip", f"0 {BASS_DIP_MS}", bx + 80, 520)
    P.c("ba_ct", "ba_dip", 1, 0)
    P.obj("ba_wait", f"pipe {BASS_DIP_MS}", bx, 520)
    P.c("ba_ct", "ba_wait", 0, 0)
    P.obj("ba_ft", "t b f", bx, 540)
    P.c("ba_wait", "ba_ft")
    P.msg("ba_rise", f"1 {BASS_RISE_MS}", bx + 80, 560)
    P.c("ba_ft", "ba_rise", 0, 0)
    P.obj("ba_env", "line~", bx + 80, 580)
    P.c("ba_dip", "ba_env"); P.c("ba_rise", "ba_env")
    P.obj("ba_f2", "* 2", bx + 60, 560); P.obj("ba_f3", "* 3", bx + 120, 560)
    P.c("ba_ft", "ba_f2", 1, 0); P.c("ba_ft", "ba_f3", 1, 0)
    P.obj("ba_o1", "osc~", bx, 600); P.obj("ba_o2", "osc~", bx + 60, 600); P.obj("ba_o3", "osc~", bx + 120, 600)
    P.c("ba_ft", "ba_o1", 1, 0); P.c("ba_f2", "ba_o2"); P.c("ba_f3", "ba_o3")
    P.obj("ba_g2", "*~ 0.35", bx + 60, 620); P.obj("ba_g3", "*~ 0.15", bx + 120, 620)
    P.c("ba_o2", "ba_g2"); P.c("ba_o3", "ba_g3")
    P.sig2("ba_s12", "+~", "ba_o1", "ba_g2", bx, 640)
    P.sig2("ba_s", "+~", "ba_s12", "ba_g3", bx, 655)
    fp_sum, fb_out, fb_ln = add_found_pad(P, px, bx, ex)
    add_composer(P, px + 1600, 20)
    P.obj("ba_sgain", "*~ -1", bx + 40, 662); P.c(fb_ln, "ba_sgain")
    P.obj("ba_sg1", "+~ 1", bx + 40, 664); P.c("ba_sgain", "ba_sg1")      # the synth bass gives way to the found one
    P.sig2("ba_ss", "*~", "ba_s", "ba_sg1", bx, 664)
    P.sig2("ba_sall", "+~", "ba_ss", fb_out, bx, 667)
    # the kit's bass (Kits): each new bass note held in its slot; the Classic bass (synth and found) gives way
    P.obj("ba_kf", "f", bx + 200, 535); P.c("ba_ft", "ba_kf", 1, 0)        # (the note now: a new kit hears it at once)
    P.msg("ba_kpn", f"pnote 0 {BS}$1", bx + 200, 545); P.c("ba_kf", "ba_kpn"); P.c("ba_kpn", "ba_kv"); P.c("ba_kpn", "ba_kv2")
    P.obj("ba_kre", "r kit_bass_new", bx + 280, 525); P.c("ba_kre", "ba_kf")
    P.obj("ba_kre2", "r kit2_bass_new", bx + 280, 530); P.c("ba_kre2", "ba_kf")
    P.sig2("ba_scl", "*~", "ba_sall", "kit_bass_cl", bx, 667)
    P.sig2("ba_sallk", "+~", "ba_scl", "mu_kit_bass_mix", bx, 667)
    mixb = smoothed(P, "mix_bass", "mix_bass", bx + 400, 600)              # the Mix's bass trim
    P.sig2("ba_sm", "*~", "ba_sallk", mixb, bx, 668)
    P.sig2("ba_e", "*~", "ba_sm", "ba_env", bx, 670)
    # footstep duck, only while the walking beat is switched on and audible
    P.obj("ba_r_step", "r pulse", bx + 200, 600)
    P.obj("ba_r_bl", "r beat_level", bx + 280, 600)
    P.obj("ba_r_bo", "r beat_on", bx + 360, 600)
    P.obj("ba_bl", "f", bx + 200, 630)
    ex("ba_duck", f"1-{BASS_DUCK}*($f1>0.02)*$f2", bx + 200, 660)
    P.obj("ba_step_b", "t b", bx + 200, 615)                    # the step brings its interval: only the bang matters
    P.c("ba_r_step", "ba_step_b"); P.c("ba_step_b", "ba_bl"); P.c("ba_r_bl", "ba_bl", 0, 1); P.c("ba_r_bo", "ba_duck", 0, 1)
    P.c("ba_bl", "ba_duck")
    P.msg("ba_duck_msg", ("$1 10, 1 " + str(BASS_DUCK_MS) + " 30").replace("$", BS + "$").replace(",", comma), bx + 200, 690)
    P.obj("ba_duck_env", "vline~", bx + 200, 720)
    P.c("ba_duck", "ba_duck_msg"); P.c("ba_duck_msg", "ba_duck_env")
    P.obj("ba_duck_init", "loadbang", bx + 300, 690); P.msg("ba_duck_one", "1", bx + 300, 705)
    P.c("ba_duck_init", "ba_duck_one"); P.c("ba_duck_one", "ba_duck_env")
    P.sig2("ba_d", "*~", "ba_e", "ba_duck_env", bx, 740)
    P.obj("ba_lp", "lop~ 700", bx, 760)
    P.c("ba_d", "ba_lp")
    P.obj("ba_g", f"*~ {BASS_GAIN}", bx, 780)
    P.c("ba_lp", "ba_g")
    P.sig2("ba_a", "*~", "ba_g", "mu_pad_fac_lp", bx, 800)        # swells with the room like the pad
    P.sig2("ba_b", "*~", "ba_a", "mu_breath_o", bx, 820)
    # register: 0 = low (bass only), 1 = mid (the pad as before), 2 = both
    P.obj("ba_r_reg", "r music_pad_reg", bx + 400, 760)
    P.obj("ba_reg_t", "t f f", bx + 400, 780)
    P.c("ba_r_reg", "ba_reg_t")
    P.obj("ba_mid_on", "!= 0", bx + 400, 800); P.obj("ba_low_on", "!= 1", bx + 480, 800)
    P.c("ba_reg_t", "ba_mid_on", 1, 0); P.c("ba_reg_t", "ba_low_on", 0, 0)
    for nm in ("mid", "low"):
        P.obj(f"ba_{nm}_pk", "pack f 300", bx + 400 + (80 if nm == "low" else 0), 820)
        P.obj(f"ba_{nm}_ln", "line~", bx + 400 + (80 if nm == "low" else 0), 840)
        P.c(f"ba_{nm}_on", f"ba_{nm}_pk"); P.c(f"ba_{nm}_pk", f"ba_{nm}_ln")
    P.obj("ba_reg_init", "loadbang", bx + 560, 760); P.msg("ba_reg_both", "1", bx + 560, 780)
    P.c("ba_reg_init", "ba_reg_both"); P.c("ba_reg_both", "ba_mid_ln"); P.c("ba_reg_both", "ba_low_ln")
    P.sig2("mu_pad_mid", "*~", "mu_pad_b", "ba_mid_ln", px, 870)
    P.sig2("ba_out", "*~", "ba_b", "ba_low_ln", bx, 860)
    P.sig2("mu_pad_c", "+~", "mu_pad_mid", "ba_out", px, 880)

    # ---- tone, levels, on/off ----
    P.obj("mu_r_tone", "r music_tone", x + 800, 300)
    ex("mu_cut", "400*pow(2,$f1*4)", x + 800, 330)
    P.c("mu_r_tone", "mu_cut")
    ex("mu_cut_pad", "$f1*0.6", x + 800, 360)
    P.c("mu_cut", "mu_cut_pad")
    P.c("mu_cut_pad", "mu_pad_lp", 0, 1)
    P.obj("mu_warm_r", "r music_warm", x + 900, 330)
    P.obj("mu_warm_t", "t b f", x + 900, 345)
    P.obj("mu_cut_last", "f 1600", x + 860, 360)
    ex("mu_cut_warm", "$f1*(1-0.55*$f2)", x + 800, 375)
    P.c("mu_cut", "mu_cut_last", 0, 1); P.c("mu_cut", "mu_cut_warm", 0, 0)
    P.c("mu_warm_r", "mu_warm_t"); P.c("mu_warm_t", "mu_cut_warm", 1, 1); P.c("mu_warm_t", "mu_cut_last", 0, 0)
    P.c("mu_cut_last", "mu_cut_warm")
    P.c("mu_cut_warm", "mu_chime_lp", 0, 1)
    P.obj("mu_cut_s", "s chime_cut", x + 800, 395); P.c("mu_cut_warm", "mu_cut_s")   # for the tests
    pad_lvl = smoothed(P, "music_pad", "music_pad", px + 500, 20)
    arp_lvl = smoothed(P, "music_arp", "music_arp", px + 600, 20)
    mon = smoothed(P, "music_on", "music_on", px + 700, 20)
    # Stopping after a walk: the pad and bass take a breath (dip, then drift back), so a red light doesn't feel
    # as if the music didn't notice. Only steps start it, so someone who never walks hears no change.
    P.obj("mu_br_step", "r step", px + 300, 820)
    P.obj("mu_br_t", "t b b", px + 300, 840)
    P.obj("mu_br_del", f"del {STOP_AFTER_MS}", px + 300, 860)
    P.msg("mu_br_back", ("1 " + str(STOP_RETURN_MS)), px + 400, 860)
    P.msg("mu_br_dip", (f"{STOP_DIP} {STOP_DIP_MS}, 1 {STOP_RECOVER_MS} {STOP_DIP_MS}").replace(",", " " + BS + ","), px + 300, 880)
    P.obj("mu_br_ln", "vline~", px + 300, 900)
    P.obj("mu_br_init", "loadbang", px + 450, 860)
    P.msg("mu_br_one", "1", px + 450, 880)
    P.c("mu_br_init", "mu_br_one"); P.c("mu_br_one", "mu_br_ln")
    P.c("mu_br_step", "mu_br_t"); P.c("mu_br_t", "mu_br_back", 1, 0); P.c("mu_br_t", "mu_br_del", 0, 0)
    P.c("mu_br_back", "mu_br_ln"); P.c("mu_br_del", "mu_br_dip"); P.c("mu_br_dip", "mu_br_ln")
    # Pad Only With a Steady Sound (pad_room, DadCraft Oct 30: the pad was "a constant musical sound/tone"): the pad
    # and bass come in while the room holds a sound above its quiet (a hum, traffic, wind: rising over ~PAD_ROOM_UP_S)
    # and fade to silence when it stops (over ~PAD_ROOM_DOWN_S). A knock is too short to open it. Off: always 1.
    P.obj("mu_pr_tick", "metro 100", px + 600, 760); P.c("mu_load", "mu_pr_tick")
    P.obj("mu_pr_t", "t b b b", px + 600, 775); P.c("mu_pr_tick", "mu_pr_t")
    P.obj("mu_pr_l", "value mloud01", px + 600, 790); P.obj("mu_pr_h", "value mphold", px + 660, 790)
    P.obj("mu_pr_r", "r pad_room", px + 720, 760); P.obj("mu_pr_sw", "value mprsw", px + 720, 775); P.c("mu_pr_r", "mu_pr_sw")
    P.obj("mu_pr_swr", "value mprsw", px + 720, 790)
    ex("mu_pr_tg", f"($f1>{PAD_ROOM_MIN})*min(1,$f1*{PAD_ROOM_GAIN})", px + 600, 800)   # where it is heading
    ex("mu_pr_new", f"($f1>$f2)*min($f1,$f2+{0.1 / PAD_ROOM_UP_S:.4f})+($f1<=$f2)*max($f1,$f2-{0.1 / PAD_ROOM_DOWN_S:.4f})", px + 600, 810)   # (straight lines: it reaches silence)
    P.obj("mu_pr_wr", "t f f", px + 600, 825); P.obj("mu_pr_hw", "value mphold", px + 660, 840)
    ex("mu_pr_g", "1-$f2+$f2*$f1", px + 600, 840)
    P.c("mu_pr_t", "mu_pr_swr", 0, 0); P.c("mu_pr_swr", "mu_pr_g", 0, 1)
    P.c("mu_pr_t", "mu_pr_h", 1, 0); P.c("mu_pr_h", "mu_pr_new", 0, 1)
    P.c("mu_pr_t", "mu_pr_l", 2, 0); P.c("mu_pr_l", "mu_pr_tg"); P.c("mu_pr_tg", "mu_pr_new")
    P.c("mu_pr_new", "mu_pr_wr"); P.c("mu_pr_wr", "mu_pr_hw", 1, 0); P.c("mu_pr_wr", "mu_pr_g", 0, 0)
    P.obj("mu_pr_sig", "sig~ 1", px + 600, 855); P.obj("mu_pr_lp", "lop~ 2", px + 600, 870)
    P.c("mu_pr_g", "mu_pr_sig"); P.c("mu_pr_sig", "mu_pr_lp")
    P.sig2("mu_pad_rm", "*~", "mu_pad_c", "mu_pr_lp", px, 885)
    P.sig2("mu_pad_br", "*~", "mu_pad_rm", "mu_br_ln", px, 890)
    P.sig2("mu_pad_out", "*~", "mu_pad_br", pad_lvl, px, 900)
    P.sig2("mu_chime_out", "*~", "mu_chime_lp", arp_lvl, px + 200, 900)
    P.sig2("mu_mix0", "+~", "mu_pad_out", "mu_chime_out", px, 930)
    P.sig2("mu_mix0p", "*~", "mu_mix0", "kit_pump", px, 935)          # a kit's sidechain pump (Kits): ducks at its kicks
    P.sig2("mu_mix1", "+~", "mu_mix0p", add_room_notes(P, x + 2500, ex, degree_to_hz), px, 940)
    P.sig2("mu_mix", "+~", "mu_mix1", add_harmony(P, x + 3400, ex, degree_to_hz), px, 945)
    P.sig2("mu_out", "*~", "mu_mix", mon, px, 960)
    return "mu_out"

def add_harmony(P, hx, ex, degree_to_hz):
    """Answers a held pitch with a note of the pad's chord: the nearest chord note at least a third above it, an
    octave up (a whistle or bird: the nearest a third below it, an octave down), so it is clearly a second voice and
    never the note being sung. It is held as long as the sound is, in a soft organ tone. Only a
    pitch that stays steady for a moment, clearly above the room's quiet, counts, so noise and speech blips play
    nothing. Two voices take turns, so a new note never bends the one still fading. Returns the output signal."""
    P.sig2("mu_hm_src", "*~", "mic_gated", "hush_ln", hx, 20)          # quieter in a pocket, like the rest
    P.obj("mu_hm_aa", "lop~ 6000", hx, 35); P.c("mu_hm_src", "mu_hm_aa")         # (before halving the rate)
    P.obj("mu_hm_sig", f"sigmund~ -hop {HARM_HOP} -npts {HARM_NPTS} pitch env", hx, 50)   # env comes out first, then pitch
    P.c("mu_hm_aa", "mu_hm_sig")
    # Pitch tracker (`harm_tracker`, Oct 17): Classic = sigmund~; New = dcs_pitch~ (our YIN: exact, noise reads as no
    # pitch). Same outputs (level, then pitch), so the logic below doesn't know which it hears.
    P.obj("mu_hm_sig2", f"dcs_pitch~ {HARM_HOP}", hx + 100, 50); P.c("mu_hm_aa", "mu_hm_sig2")
    P.obj("mu_hm_trk_r", "r harm_tracker", hx + 300, 20)
    P.obj("mu_hm_trk", "expr $f1==0 ; $f1>=1".replace("$", BS + "$").replace(" ; ", " " + BS + "; "), hx + 300, 35)
    P.c("mu_hm_trk_r", "mu_hm_trk")
    for nm, init, src, o, sel in (("mu_hm_tcp", 1, "mu_hm_sig", 0, 0), ("mu_hm_tce", 1, "mu_hm_sig", 1, 0),
                                  ("mu_hm_tnp", 0, "mu_hm_sig2", 0, 1), ("mu_hm_tne", 0, "mu_hm_sig2", 1, 1)):
        P.obj(nm, f"spigot {init}", hx + 300 + 40 * len(nm), 65); P.c(src, nm, o, 0); P.c("mu_hm_trk", nm, sel, 1)
    P.obj("mu_hm_thr", "value mthr", hx + 200, 80)
    P.obj("mu_hm_env_t", "t f b", hx + 200, 50)
    ex("mu_hm_loud", f"$f1-$f2", hx + 200, 110)
    P.c("mu_hm_tce", "mu_hm_env_t"); P.c("mu_hm_tne", "mu_hm_env_t"); P.c("mu_hm_env_t", "mu_hm_thr", 1, 0)
    P.c("mu_hm_thr", "mu_hm_loud", 0, 1); P.c("mu_hm_env_t", "mu_hm_loud", 0, 0)
    # Each pitch frame: steady (close to the frame before) and loud enough? Count the steady frames in a row.
    P.obj("mu_hm_p_t", "t f f", hx, 80)
    P.c("mu_hm_tcp", "mu_hm_p_t"); P.c("mu_hm_tnp", "mu_hm_p_t")
    ex("mu_hm_ok", f"($f1>{HARM_RANGE[0]})*($f1<{HARM_RANGE[1]})*(abs($f1-$f2)<{HARM_STEADY_ST})*($f3>{HARM_ABOVE_DB})*($f4>0)", hx, 140)
    P.c("mu_hm_p_t", "mu_hm_ok", 1, 0); P.c("mu_hm_p_t", "mu_hm_ok", 0, 1)   # right first: compare with the old pitch
    P.c("mu_hm_loud", "mu_hm_ok", 0, 2)
    if os.environ.get("HARM_DEBUG"):
        P.obj("mu_hm_dbg_m", "metro 300", hx + 500, 50); P.obj("mu_hm_dbg_lb", "loadbang", hx + 500, 30)
        P.c("mu_hm_dbg_lb", "mu_hm_dbg_m")
        P.obj("mu_hm_dbg_p", "f", hx + 500, 80); P.obj("mu_hm_dbg_l", "f", hx + 560, 80); P.obj("mu_hm_dbg_e", "f", hx + 620, 80)
        P.obj("mu_hm_dbg_pk", "pack f f f", hx + 500, 110); P.obj("mu_hm_dbg_pr", "print hm", hx + 500, 140)
        P.obj("mu_hm_dbg_t", "t b b b", hx + 500, 65)
        P.c("mu_hm_dbg_m", "mu_hm_dbg_t"); P.c("mu_hm_dbg_t", "mu_hm_dbg_p", 0, 0); P.c("mu_hm_dbg_t", "mu_hm_dbg_l", 1, 0); P.c("mu_hm_dbg_t", "mu_hm_dbg_e", 2, 0)
        P.c("mu_hm_sig", "mu_hm_dbg_p", 0, 1); P.c("mu_hm_loud", "mu_hm_dbg_l", 0, 1); P.c("mu_hm_sig", "mu_hm_dbg_e", 1, 1)
        P.c("mu_hm_dbg_p", "mu_hm_dbg_pk", 0, 0); P.c("mu_hm_dbg_l", "mu_hm_dbg_pk", 0, 1); P.c("mu_hm_dbg_e", "mu_hm_dbg_pk", 0, 2)
        P.c("mu_hm_dbg_pk", "mu_hm_dbg_pr")
        P.obj("mu_hm_dbg_s1", "s hm_cnt", hx + 700, 240); P.c("mu_hm_next", "mu_hm_dbg_s1")
        P.obj("mu_hm_dbg_s2", "s hm_loud", hx + 700, 260); P.c("mu_hm_loud", "mu_hm_dbg_s2")
        P.obj("mu_hm_dbg_s3", "s hm_p", hx + 700, 280); P.c("mu_hm_sig", "mu_hm_dbg_s3")
    P.obj("mu_hm_r_lvl", "r harm_run", hx + 150, 110)                   # off: nothing is heard (Harmony at 0, no found pad)
    P.c("mu_hm_r_lvl", "mu_hm_ok", 0, 3)
    # The tracker misreads a frame now and then (an octave or a twelfth off): only three bad frames in a row end a note.
    P.obj("mu_hm_okt", "t f b f", hx, 145)
    ex("mu_hm_eff", "($f1+$f2+$f3)>0", hx + 60, 150)
    P.obj("mu_hm_ok1", "f", hx + 120, 150)
    P.c("mu_hm_ok", "mu_hm_okt"); P.c("mu_hm_okt", "mu_hm_eff", 2, 0); P.c("mu_hm_okt", "mu_hm_ok1", 1, 0)
    P.c("mu_hm_ok1", "mu_hm_eff", 0, 2); P.c("mu_hm_okt", "mu_hm_ok1", 0, 1); P.c("mu_hm_okt", "mu_hm_eff", 0, 1)
    P.obj("mu_hm_good", "spigot", hx + 100, 330)                        # only good frames set the pitch to answer
    P.c("mu_hm_ok", "mu_hm_good", 0, 1)
    P.obj("mu_hm_ok_t", "t f b", hx, 155)                              # read the count, then add to it or clear it
    P.obj("mu_hm_cnt", "f", hx + 60, 170)
    ex("mu_hm_next", "$f1*($f2+1)", hx, 200)
    P.c("mu_hm_eff", "mu_hm_ok_t"); P.c("mu_hm_ok_t", "mu_hm_cnt", 1, 0); P.c("mu_hm_cnt", "mu_hm_next", 0, 1)
    P.c("mu_hm_ok_t", "mu_hm_next", 0, 0)
    P.obj("mu_hm_cnt_t", "t f f", hx, 230)
    P.c("mu_hm_next", "mu_hm_cnt_t"); P.c("mu_hm_cnt_t", "mu_hm_cnt", 1, 1)
    ex("mu_hm_held", f"$f1>={HARM_STABLE_FRAMES}", hx, 260)
    P.c("mu_hm_cnt_t", "mu_hm_held")
    P.obj("mu_hm_chg", "change", hx, 290)
    P.obj("mu_hm_sel", "sel 1 0", hx, 320)
    P.c("mu_hm_held", "mu_hm_chg"); P.c("mu_hm_chg", "mu_hm_sel")
    # The answer. First the heard pitch as a scale step (0 = the key note, n = an octave up), then the chord note.
    P.obj("mu_hm_pitch", "f", hx + 100, 350)
    P.c("mu_hm_p_t", "mu_hm_good", 0, 0); P.c("mu_hm_good", "mu_hm_pitch", 0, 1)
    P.obj("mu_hm_root", "f 48", hx + 260, 350)
    P.c("mu_key_root", "mu_hm_root", 0, 1)
    P.obj("mu_hm_n", "value scale_n", hx + 360, 350)
    P.obj("mu_hm_n2", "value scale_n", hx + 420, 350)
    P.obj("mu_hm_ch", "value mchord", hx + 480, 350)
    P.obj("mu_hm_t", "t f f b b b b", hx + 100, 380)
    P.c("mu_hm_pitch", "mu_hm_t")
    P.c("mu_hm_t", "mu_hm_root", 5, 0); P.c("mu_hm_t", "mu_hm_n", 4, 0); P.c("mu_hm_t", "mu_hm_n2", 3, 0)
    P.c("mu_hm_t", "mu_hm_ch", 2, 0)
    rel = "(rint($f1)-$f2)"
    ex("mu_hm_d", f"floor({rel}/12)*$f3+pc_deg[{rel}-12*floor({rel}/12)]", hx + 100, 410)
    P.c("mu_hm_root", "mu_hm_d", 0, 1); P.c("mu_hm_n", "mu_hm_d", 0, 2); P.c("mu_hm_t", "mu_hm_d", 0, 0)
    # chord notes are the chord root r and 2 and 4 steps up; $f1 = heard step d, $f2 = r, $f3 = n, $f4 = pitch
    up = ("min(min($f2+$f3*ceil(($f1+2-$f2)/$f3),$f2+2+$f3*ceil(($f1-$f2)/$f3)),"
          "$f2+4+$f3*ceil(($f1-2-$f2)/$f3))+$f3")
    down = ("max(max($f2+$f3*floor(($f1-2-$f2)/$f3),$f2+2+$f3*floor(($f1-4-$f2)/$f3)),"
            "$f2+4+$f3*floor(($f1-6-$f2)/$f3))-$f3")
    ex("mu_hm_deg", f"if($f4<{HARM_HIGH},{up},{down})", hx + 100, 440)
    P.c("mu_hm_ch", "mu_hm_deg", 0, 1); P.c("mu_hm_n2", "mu_hm_deg", 0, 2); P.c("mu_hm_t", "mu_hm_deg", 1, 3)
    P.c("mu_hm_d", "mu_hm_deg", 0, 0)
    deg_in, deg_hz = degree_to_hz("mu_hm_c", hx + 100, 470)
    P.c("mu_hm_deg", deg_in)
    # Your voice as the harmony (harm_src 1 or 2): close harmony, the same choice without the octave jump (a third
    # or more above a low voice, below a voice from HV_SPLIT up and below a whistle or bird), sung by a pitch-shifted copy of the mic. The interval from the
    # heard note to the chord note is set when the note starts and held, so the copy follows your vibrato.
    ex("mu_hm_vdeg", f"if($f4<{HV_SPLIT},{up}-$f3,{down}+$f3)", hx + 700, 440)
    P.c("mu_hm_ch", "mu_hm_vdeg", 0, 1); P.c("mu_hm_n2", "mu_hm_vdeg", 0, 2); P.c("mu_hm_t", "mu_hm_vdeg", 1, 3)
    P.c("mu_hm_d", "mu_hm_vdeg", 0, 0)
    vdeg_in, vdeg_hz = degree_to_hz("mu_hm_vc", hx + 700, 470)
    P.c("mu_hm_vdeg", vdeg_in)
    ex("mu_hm_vsemi", "12*log($f1/mtof($f2))/log(2)", hx + 700, 560)
    P.c("mu_hm_t", "mu_hm_vsemi", 1, 1); P.c(vdeg_hz, "mu_hm_vsemi")
    ex("mu_hm_vwin", f"2*max(1,rint({HV_WINDOW_MS}*mtof($f1)/2000))*1000/mtof($f1)", hx + 900, 560)
    P.c("mu_hm_t", "mu_hm_vwin", 1, 0)                                  # (the pitch comes before the note)
    P.obj("mu_hm_vwt", "t f f", hx + 900, 575)
    P.c("mu_hm_vwin", "mu_hm_vwt")
    ex("mu_hm_vrate", "(1-pow(2,$f1/12))*1000/$f2", hx + 700, 580)
    P.c("mu_hm_vwt", "mu_hm_vrate", 1, 1)
    P.c("mu_hm_vsemi", "mu_hm_vrate")
    P.obj("mu_hm_vw", "f 40", hx + 960, 590); P.c("mu_hm_vwt", "mu_hm_vw", 0, 1)
    P.obj("mu_hm_vsemi_s", "s harm_semi", hx + 800, 580); P.c("mu_hm_vsemi", "mu_hm_vsemi_s")   # (for the tests)
    # call and response (the app): each heard note as a scale step, and when it ends
    P.obj("mu_hm_heard_s", "s heard_deg", hx + 200, 425); P.c("mu_hm_d", "mu_hm_heard_s")
    P.obj("mu_hm_degout", "s harm_deg", hx + 300, 480)
    P.c("mu_hm_deg", "mu_hm_degout")
    # Note on: frequency first, then pick the other voice and open it; note off closes the voice that is playing.
    P.obj("mu_hm_on", "t b b", hx, 350)
    P.c("mu_hm_sel", "mu_hm_on", 0, 0)
    P.c("mu_hm_on", "mu_hm_pitch", 1, 0)
    P.obj("mu_hm_vc", "f", hx, 680)
    P.obj("mu_hm_flip", "== 0", hx + 60, 680)
    P.c("mu_hm_vc", "mu_hm_flip"); P.c("mu_hm_flip", "mu_hm_vc", 0, 1)
    P.obj("mu_hm_on2", "t b b", hx, 665)
    P.c("mu_hm_on", "mu_hm_on2", 0, 0)
    P.c("mu_hm_on2", "mu_hm_vc", 1, 0)
    P.obj("mu_hm_cur", "f", hx + 120, 700)
    P.c("mu_hm_flip", "mu_hm_cur", 0, 1)
    P.c("mu_hm_on2", "mu_hm_cur", 0, 0)
    P.obj("mu_hm_off", "t b", hx + 200, 700)
    P.obj("mu_hm_heard_off", "s heard_off", hx + 260, 715); P.c("mu_hm_off", "mu_hm_heard_off")
    P.c("mu_hm_sel", "mu_hm_off", 1, 0)
    P.obj("mu_hm_vsel", "sel 0 1", hx + 120, 730)
    P.obj("mu_hm_osel", "sel 0 1", hx + 200, 730)
    P.obj("mu_hm_cur2", "f", hx + 200, 715)
    P.c("mu_hm_flip", "mu_hm_cur2", 0, 1)
    P.c("mu_hm_off", "mu_hm_cur2"); P.c("mu_hm_cur2", "mu_hm_osel")
    P.c("mu_hm_cur", "mu_hm_vsel")
    acc = None
    comma = " " + BS + ","
    for k in range(2):
        vx = hx + 400 + k * 200
        P.obj(f"mu_hm{k}_fr", "f", vx, 760)
        P.c(deg_hz, f"mu_hm{k}_fr", 0, 1)
        P.obj(f"mu_hm{k}_t", "t b b", vx, 745)
        P.c("mu_hm_vsel", f"mu_hm{k}_t", k, 0)
        P.c(f"mu_hm{k}_t", f"mu_hm{k}_fr", 1, 0)
        # A soft organ: the note plus its octave, twelfth and double octave, so it doesn't melt into a voice.
        P.obj(f"mu_hm{k}_o1", "osc~", vx, 790)
        P.c(f"mu_hm{k}_fr", f"mu_hm{k}_o1")
        acc_v = f"mu_hm{k}_o1"
        for h, g in ((2, 0.5), (3, 0.3), (4, 0.15)):
            P.obj(f"mu_hm{k}_f{h}", f"* {h}", vx + 40 * h, 775)
            P.obj(f"mu_hm{k}_o{h}", "osc~", vx + 40 * h, 790)
            P.obj(f"mu_hm{k}_g{h}", f"*~ {g}", vx + 40 * h, 810)
            P.c(f"mu_hm{k}_fr", f"mu_hm{k}_f{h}"); P.c(f"mu_hm{k}_f{h}", f"mu_hm{k}_o{h}"); P.c(f"mu_hm{k}_o{h}", f"mu_hm{k}_g{h}")
            acc_v = P.sig2(f"mu_hm{k}_a{h}", "+~", acc_v, f"mu_hm{k}_g{h}", vx, 815 + h)
        P.obj(f"mu_hm{k}_s", "*~ 0.6", vx, 830)
        P.c(acc_v, f"mu_hm{k}_s")
        P.msg(f"mu_hm{k}_atk", f"1 {HARM_ATTACK_MS}", vx, 850)
        P.msg(f"mu_hm{k}_rel", f"0 {HARM_RELEASE_MS}", vx + 80, 850)
        P.obj(f"mu_hm{k}_env", "vline~", vx, 870)
        P.c(f"mu_hm{k}_t", f"mu_hm{k}_atk", 0, 0)
        P.c("mu_hm_osel", f"mu_hm{k}_rel", k, 0)
        P.c(f"mu_hm{k}_atk", f"mu_hm{k}_env"); P.c(f"mu_hm{k}_rel", f"mu_hm{k}_env")
        P.obj(f"mu_hm{k}_sq", "*~", vx, 890)
        P.c(f"mu_hm{k}_env", f"mu_hm{k}_sq", 0, 0); P.c(f"mu_hm{k}_env", f"mu_hm{k}_sq", 0, 1)   # eased in and out
        P.sig2(f"mu_hm{k}_out", "*~", f"mu_hm{k}_s", f"mu_hm{k}_sq", vx, 910)
        acc = f"mu_hm{k}_out" if acc is None else P.sig2("mu_hm_sum", "+~", acc, f"mu_hm{k}_out", vx, 930)
    P.obj("mu_hm_lp", "lop~ 3500", hx, 950)
    P.c(acc, "mu_hm_lp")
    lvl = smoothed(P, "music_harm", "music_harm", hx + 300, 950)
    P.obj("mu_hm_gain", "*~ 0.4", hx, 970)
    P.c("mu_hm_lp", "mu_hm_gain")
    # the organ plays unless Harmony sound is "Your voice"; the organ voices compute only then (group horgan)
    P.obj("mu_hm_r_src", "r harm_src", hx + 900, 700); P.obj("mu_hm_org", "!= 1", hx + 900, 720)
    P.obj("mu_hm_org_s", "s harm_organ", hx + 900, 740); P.c("mu_hm_r_src", "mu_hm_org"); P.c("mu_hm_org", "mu_hm_org_s")
    P.obj("mu_hm_vok", "expr ($f1>=1)*(hm_lvl>0)".replace("$", BS + "$"), hx + 980, 720); P.c("mu_hm_r_src", "mu_hm_vok")
    # (the voice only switches on while Harmony is up: with it at 0 the tracker may still run for the found pad)
    P.obj("mu_hm_r_lv2", "r music_harm", hx + 1060, 700); P.obj("mu_hm_lv2", "value hm_lvl", hx + 1060, 720)
    P.obj("mu_hm_vsrc", "value hm_src", hx + 1140, 720); P.c("mu_hm_r_src", "mu_hm_vsrc")
    P.obj("mu_hm_lvb", "t b f", hx + 1060, 710); P.c("mu_hm_r_lv2", "mu_hm_lvb"); P.c("mu_hm_lvb", "mu_hm_lv2", 1, 0)
    ex("mu_hm_vok2", "(hm_src>=1)*(hm_lvl>0)+$f1*0", hx + 1060, 740); P.c("mu_hm_lvb", "mu_hm_vok2", 0, 0)
    P.c("mu_hm_vok2", "mu_hm_von", 0, 1); P.c("mu_hm_vok2", "mu_hm_voff", 0, 1)
    # your voice: the mic into its own buffer all the time (cheap); the reading grains run only while it sings
    P.obj("mu_hm_vwr", "delwrite~ hv_buf 200", hx + 700, 30); P.c("mu_hm_src", "mu_hm_vwr")
    P.obj("mu_hm_von", "spigot 0", hx + 700, 650); P.c("mu_hm_vok", "mu_hm_von", 0, 1)
    P.c("mu_hm_on", "mu_hm_von", 0, 0)
    P.obj("mu_hm_von_t", "t b b b b", hx + 700, 670); P.c("mu_hm_von", "mu_hm_von_t")
    P.msg("mu_hm_vsw1", f"{BS}; sw_hvoice 1", hx + 760, 690); P.c("mu_hm_von_t", "mu_hm_vsw1", 3, 0)
    P.msg("mu_hm_vstop", "stop", hx + 820, 690); P.c("mu_hm_von_t", "mu_hm_vstop", 2, 0)
    P.obj("mu_hm_vrt", "f", hx + 880, 690); P.c("mu_hm_vrate", "mu_hm_vrt", 0, 1)
    P.obj("mu_hm_vset", "t b b", hx + 880, 680); P.c("mu_hm_von_t", "mu_hm_vset", 1, 0)
    P.c("mu_hm_vset", "mu_hm_vw", 1, 0); P.c("mu_hm_vset", "mu_hm_vrt", 0, 0)
    P.msg("mu_hm_vatk", f"1 {HARM_ATTACK_MS}", hx + 700, 700); P.c("mu_hm_von_t", "mu_hm_vatk", 0, 0)
    P.obj("mu_hm_voff", "spigot 0", hx + 760, 720); P.c("mu_hm_vok", "mu_hm_voff", 0, 1); P.c("mu_hm_off", "mu_hm_voff")
    P.obj("mu_hm_voff_t", "t b b", hx + 760, 740); P.c("mu_hm_voff", "mu_hm_voff_t")
    P.msg("mu_hm_vrel", f"0 {HV_RELEASE_MS}", hx + 760, 760); P.c("mu_hm_voff_t", "mu_hm_vrel", 1, 0)
    P.obj("mu_hm_vdl", f"del {HV_RELEASE_MS + HV_WINDOW_MS + 100}", hx + 840, 760); P.c("mu_hm_voff_t", "mu_hm_vdl", 0, 0)
    P.c("mu_hm_vstop", "mu_hm_vdl")
    P.msg("mu_hm_vsw0", f"{BS}; sw_hvoice 0", hx + 840, 780); P.c("mu_hm_vdl", "mu_hm_vsw0")
    P.obj("grp_hvoice_r", "r sw_hvoice", 10, 10); P.obj("grp_hvoice_sw", "switch~", 10, 40); P.c("grp_hvoice_r", "grp_hvoice_sw")
    vx, vy = hx + 700, 800
    P.obj("hv_rate", "sig~", vx, vy); P.c("mu_hm_vrt", "hv_rate")
    P.obj("hv_ph1", "phasor~", vx, vy + 20); P.c("hv_rate", "hv_ph1")
    P.obj("hv_ph2o", "+~ 0.5", vx + 100, vy + 20); P.obj("hv_ph2", "wrap~", vx + 100, vy + 40)
    P.c("hv_ph1", "hv_ph2o"); P.c("hv_ph2o", "hv_ph2")
    for n, ph, x in (("1", "hv_ph1", vx), ("2", "hv_ph2", vx + 100)):
        P.obj(f"hv_tap{n}", f"*~ {HV_WINDOW_MS}", x, vy + 60); P.obj(f"hv_off{n}", "+~ 4", x, vy + 80)
        P.c("mu_hm_vw", f"hv_tap{n}", 0, 1)
        P.obj(f"hv_rd{n}", "delread4~ hv_buf", x, vy + 100)
        P.obj(f"hv_cos{n}", "cos~", x + 50, vy + 60); P.obj(f"hv_neg{n}", "*~ -0.5", x + 50, vy + 80)
        P.obj(f"hv_win{n}", "+~ 0.5", x + 50, vy + 100)
        P.c(ph, f"hv_tap{n}"); P.c(f"hv_tap{n}", f"hv_off{n}"); P.c(f"hv_off{n}", f"hv_rd{n}")
        P.c(ph, f"hv_cos{n}"); P.c(f"hv_cos{n}", f"hv_neg{n}"); P.c(f"hv_neg{n}", f"hv_win{n}")
        P.sig2(f"hv_grain{n}", "*~", f"hv_rd{n}", f"hv_win{n}", x, vy + 120)
    P.sig2("hv_shifted", "+~", "hv_grain1", "hv_grain2", vx, vy + 140)
    P.obj("hv_env", "vline~", vx + 200, vy + 120)
    P.c("mu_hm_vatk", "hv_env"); P.c("mu_hm_vrel", "hv_env")
    P.sig2("hv_env2", "*~", "hv_env", "hv_env", vx + 200, vy + 140)       # eased in and out
    # Pitch shifter (`shift_voice`, Oct 17): New = dcs_shift~ (PSOLA: keeps the voice's tone colour, no warble)
    # (it reads the voice from hv_buf, like the classic grains: a direct line from the harm group into this one
    #  and back would be a DSP loop between the two)
    P.obj("hv_din", "delread~ hv_buf 0", vx + 300, vy + 90)
    P.obj("hv_dcs", "dcs_shift~ 0", vx + 300, vy + 100); P.c("hv_din", "hv_dcs")
    P.msg("hv_dsemi", f"semi {BS}$1", vx + 300, vy + 80); P.c("mu_hm_vsemi", "hv_dsemi"); P.c("hv_dsemi", "hv_dcs")
    P.obj("hv_sv_r", "r shift_voice", vx + 400, vy + 60)
    P.obj("hv_sv", "expr 1-$f1 ; $f1".replace("$", BS + "$").replace(" ; ", " " + BS + "; "), vx + 400, vy + 80)
    P.c("hv_sv_r", "hv_sv")
    P.obj("hv_mc", "*~ 1", vx, vy + 145); P.obj("hv_mn", "*~ 0", vx + 300, vy + 145)
    P.c("hv_shifted", "hv_mc"); P.c("hv_dcs", "hv_mn"); P.c("hv_sv", "hv_mc", 0, 1); P.c("hv_sv", "hv_mn", 1, 1)
    P.sig2("hv_shift2", "+~", "hv_mc", "hv_mn", vx, vy + 150)
    P.sig2("hv_s", "*~", "hv_shift2", "hv_env2", vx, vy + 160)
    P.obj("hv_out", f"*~ {HV_GAIN}", vx, vy + 180); P.c("hv_s", "hv_out")
    P.sig2("mu_hm_both", "+~", "mu_hm_gain", "hv_out", hx, 980)
    return P.sig2("mu_hm_out", "*~", "mu_hm_both", lvl, hx, 990)

def add_room_notes(P, rx, ex, degree_to_hz):
    """The room plays the notes: the mic goes through narrow resonators tuned to the current chord, so a clap,
    a voice or traffic rings in key. Quiet rooms stay quiet (the input follows the room's liveliness). The
    resonators glide to the new notes when the chord, key or scale changes. Returns the output signal."""
    # Input: the gated mic without rumble. It opens as soon as the level rises above the room's own quiet
    # (the floor the chimes use), so a single clap rings but a quiet room stays silent. With react = 0 it stays open.
    P.sig2("rn_src", "*~", "mic_gated", "hush_ln", rx - 100, 20)    # quieter in a pocket, like the rest
    P.obj("rn_hip", "hip~ 120", rx, 20)
    P.c("rn_src", "rn_hip")
    # The level is measured in short windows, so the gate opens a little late; delay the sound to match.
    P.obj("rn_look_wr", "delwrite~ rn_look 100", rx, 50)
    P.obj("rn_look", f"delread~ rn_look {RES_LOOKAHEAD_MS}", rx, 80)
    P.c("rn_hip", "rn_look_wr")
    P.obj("rn_v_thr", "value mthr", rx + 300, 20)
    P.c("mu_fl_thr", "rn_v_thr")
    P.obj("rn_lvl_t", "t f f b b", rx + 150, 50)                   # the level meter itself: every 5 ms, not 33
    P.obj("rn_rd_thr", "value mthr", rx + 230, 80)
    P.c("meter", "rn_lvl_t"); P.c("rn_lvl_t", "rn_rd_thr", 3, 0)
    ex("rn_open", f"(1-$f3)+$f3*min(max(($f1-$f2)/{RES_OPEN_DB},0),1)", rx + 150, 110)
    P.c("rn_rd_thr", "rn_open", 0, 1); P.c("mu_r_react", "rn_open", 0, 2); P.c("rn_lvl_t", "rn_open", 0, 0)
    # Ring length: "how much was heard" (0..1) jumps with loudness, grows while the sound goes on, then relaxes.
    P.obj("rn_dt", "expr 256/$f1".replace("$", BS + "$"), rx + 450, 20)   # seconds between level readings
    P.c("sr", "rn_dt")
    P.obj("rn_rd_int", "value rn_int", rx + 330, 80)
    P.c("rn_lvl_t", "rn_rd_int", 2, 0)
    ex("rn_int", f"min(max($f3*exp(-$f4/{RES_RELAX_S}),min(max(($f1-$f2)/{RES_LOUD_DB},0),1)*{RES_LOUD_SHARE})"
                 f"+min(max(($f1-$f2)/{RES_LOUD_DB},0),1)*$f4/{RES_BUILD_S},1)", rx + 300, 110)
    P.c("rn_rd_thr", "rn_int", 0, 1); P.c("rn_rd_int", "rn_int", 0, 2); P.c("rn_dt", "rn_int", 0, 3)
    P.c("rn_lvl_t", "rn_int", 1, 0)
    P.obj("rn_int_t", "t f f", rx + 300, 140)
    P.obj("rn_wr_int", "value rn_int", rx + 380, 170)
    P.c("rn_int", "rn_int_t"); P.c("rn_int_t", "rn_wr_int", 1, 0)
    # Squared, so small sounds stay near the short chime and only big or long ones reach the bell.
    ex("rn_ring", f"{RES_RING_SHORT_S}+({RES_RING_LONG_S}-{RES_RING_SHORT_S})*pow((1-$f2)*{RES_STEADY}+$f2*$f1,2)", rx + 300, 170)
    P.c("rn_int_t", "rn_ring", 0, 0); P.c("mu_r_react", "rn_ring", 0, 1)
    # A narrower (longer-ringing) filter catches less of a sharp sound: feed it harder so notes start as loud.
    ex("rn_exc", f"sqrt($f1/{RES_RING_REF_S})", rx + 450, 200)
    P.c("rn_ring", "rn_exc")
    P.obj("rn_exc_sig", "sig~", rx + 450, 230)
    P.obj("rn_exc_lp", "lop~ 20", rx + 450, 260)
    P.c("rn_exc", "rn_exc_sig"); P.c("rn_exc_sig", "rn_exc_lp")
    P.obj("rn_open_sig", "sig~", rx + 150, 140)
    P.obj("rn_open_lp", "lop~ 20", rx + 150, 170)
    P.c("rn_open", "rn_open_sig"); P.c("rn_open_sig", "rn_open_lp")
    P.sig2("rn_in0", "*~", "rn_look", "rn_open_lp", rx, 200)
    P.sig2("rn_in", "*~", "rn_in0", "rn_exc_lp", rx, 290)
    acc = None
    for i, (octs, step) in enumerate(RES_NOTES):
        vx = rx + i * 170
        ex(f"rn{i}_deg", f"$f1+{octs}*$f2+{step}", vx, 180)        # chord root + octaves (scale size each) + step
        P.c("mu_r_n", f"rn{i}_deg", 0, 1)
        P.c("mu_pad_root_v", f"rn{i}_deg", 0, 0)        # the pad's chord root (a scale degree)
        dg_in, dg_hz = degree_to_hz(f"rn{i}", vx, 210)
        P.c(f"rn{i}_deg", dg_in)
        P.obj(f"rn{i}_ft", "t f f", vx, 390)
        P.c(dg_hz, f"rn{i}_ft")
        P.obj(f"rn{i}_pk", f"pack 0 {RES_GLIDE_MS}", vx, 420)
        P.obj(f"rn{i}_ln", "line~", vx, 450)
        P.c(f"rn{i}_ft", f"rn{i}_pk", 1, 0); P.c(f"rn{i}_pk", f"rn{i}_ln")
        ex(f"rn{i}_q", "6.28318*$f2*$f1*sqrt(262/$f2)", vx + 70, 420)    # [vcf~] rings for Q / (2 pi f) s
        P.c(f"rn{i}_ft", f"rn{i}_q", 0, 1)                       # the note (cold), then the ring length (every 5 ms)
        P.c("rn_ring", f"rn{i}_q", 0, 0)
        P.obj(f"rn{i}_vcf", "vcf~", vx, 480)
        P.c("rn_in", f"rn{i}_vcf", 0, 0); P.c(f"rn{i}_ln", f"rn{i}_vcf", 0, 1); P.c(f"rn{i}_q", f"rn{i}_vcf", 0, 2)
        acc = f"rn{i}_vcf" if acc is None else P.sig2(f"rn_acc{i}", "+~", acc, f"rn{i}_vcf", vx, 510)
    P.obj("rn_gain", f"*~ {RES_GAIN}", rx, 540)
    P.c(acc, "rn_gain")
    # Soft limit: a note sung right on a resonator passes at full strength, so cap the voice gently.
    soft_limit(P, "rn_soft", "rn_gain", RES_MAX, 1.0, rx, 570)
    lvl = smoothed(P, "music_res", "music_res", rx + 300, 540)
    # Fade in whenever the block switches on: its resonators start from rest, and with a sound already going
    # they would jump in with a click once the lookahead buffer fills.
    P.obj("rn_fade_sel", "sel 1", rx + 200, 540)
    P.msg("rn_fade_msg", ("0, 1 " + str(RES_FADE_IN_MS) + " " + str(RES_LOOKAHEAD_MS + 10)).replace(",", " " + BS + ","), rx + 200, 560)
    P.obj("rn_fade", "vline~", rx + 200, 580)
    P.c("grp_roomnotes_r", "rn_fade_sel"); P.c("rn_fade_sel", "rn_fade_msg"); P.c("rn_fade_msg", "rn_fade")
    P.sig2("rn_faded", "*~", "rn_soft", "rn_fade", rx, 590)
    P.sig2("rn_out", "*~", "rn_faded", lvl, rx, 600)
    # ---- "Room notes sound" res_src: 0 ring (the resonators above), 1 found, 2 both. Found: a sudden sound in the room
    # plays a caught sound on a chord tone (the resonators' own notes), through the found chime voice (in key, with the
    # Chime character) - two voices taking turns, at most one note per RNF_GAP_MS, never in a pocket ----
    fx, fy = rx, 700
    P.obj("rnf_r_src", "r res_src", fx, fy); P.obj("rnf_v_src", "value rnf_src", fx, fy + 15); P.c("rnf_r_src", "rnf_v_src")
    P.obj("rnf_ring", "!= 1", fx + 80, fy + 15); P.obj("rnf_ring_s", "s rn_ring", fx + 80, fy + 30)
    P.obj("rnf_fnd", ">= 1", fx + 140, fy + 15); P.obj("rnf_fnd_s", "s rnf_found", fx + 140, fy + 30)
    P.c("rnf_r_src", "rnf_fnd"); P.c("rnf_fnd", "rnf_fnd_s")
    P.c("rnf_r_src", "rnf_ring"); P.c("rnf_ring", "rnf_ring_s")              # (the resonators run unless found only)
    P.obj("rnf_r_pk", "r pocket", fx + 160, fy); P.obj("rnf_v_pk", "value rnf_pk", fx + 160, fy + 15); P.c("rnf_r_pk", "rnf_v_pk")
    P.obj("rnf_r_on", "r music_on", fx + 240, fy); P.obj("rnf_v_on", "value rnf_on", fx + 240, fy + 15); P.c("rnf_r_on", "rnf_v_on")
    P.obj("rnf_lb", "loadbang", fx + 320, fy); P.msg("rnf_open1", "1", fx + 320, fy + 15)
    P.obj("rnf_v_open", "value rnf_open", fx + 320, fy + 30); P.c("rnf_lb", "rnf_open1"); P.c("rnf_open1", "rnf_v_open")
    P.obj("rnf_det", "t b", fx, fy + 50); P.c(handling_gate(P, "rnf_hg", "fs_det_sel", fx + 60, fy + 20, wait=False), "rnf_det")
    ex("rnf_chk", "(rnf_src>=1)*(rnf_pk==0)*(rnf_on>0)*(rnf_open==1)+$f1*0", fx, fy + 65); P.c("rnf_det", "rnf_chk")
    P.obj("rnf_sel", "sel 1", fx, fy + 80); P.c("rnf_chk", "rnf_sel")
    P.obj("rnf_go", "t b b b b", fx, fy + 95); P.c("rnf_sel", "rnf_go")
    # 1. a short rest
    P.msg("rnf_close", "0", fx + 300, fy + 110); P.c("rnf_go", "rnf_close", 3, 0); P.c("rnf_close", "rnf_v_open")
    P.obj("rnf_rest", f"del {RNF_GAP_MS}", fx + 360, fy + 110); P.c("rnf_go", "rnf_rest", 3, 0); P.c("rnf_rest", "rnf_open1")
    # 2. its ring length, like a chime's
    P.obj("rnf_dr", "random 1000", fx + 200, fy + 110); P.c("rnf_go", "rnf_dr", 2, 0)
    ex("rnf_dec", f"{CHIME_DECAY_MS[0]}+{CHIME_DECAY_MS[1] - CHIME_DECAY_MS[0]}*$f1/1000", fx + 200, fy + 125)
    P.obj("rnf_dec_w", "value cdec", fx + 200, fy + 140); P.c("rnf_dr", "rnf_dec"); P.c("rnf_dec", "rnf_dec_w")
    # 3. a chord tone, from the resonators' own list (octaves above the chord root, root/third/fifth)
    P.obj("rnf_tones", f"table rnf_tone {2 * len(RES_NOTES)}", fx + 500, fy)
    P.msg("rnf_tones_i", f"{BS}; rnf_tone 0 " + " ".join(f"{o} {st}" for o, st in RES_NOTES), fx + 500, fy + 15)
    P.c("rnf_lb", "rnf_tones_i")
    P.obj("rnf_tr", f"random {len(RES_NOTES)}", fx + 100, fy + 110); P.c("rnf_go", "rnf_tr", 1, 0)
    ex("rnf_deg", "mchord+rnf_tone[2*$f1]*scale_n+rnf_tone[2*$f1+1]", fx + 100, fy + 125); P.c("rnf_tr", "rnf_deg")
    dg_in, dg_hz = degree_to_hz("rnf_c", fx + 100, fy + 140)
    P.c("rnf_deg", dg_in)
    P.obj("rnf_fq_w", "value cfreq", fx + 100, fy + 330); P.c(dg_hz, "rnf_fq_w")
    # 4. its loudness from the room, then the next voice plays it
    P.obj("rnf_ld", "value mloud", fx, fy + 350); P.c("rnf_go", "rnf_ld", 0, 0)
    ex("rnf_vel", "0.35+0.5*$f1", fx, fy + 365); P.c("rnf_ld", "rnf_vel")
    P.obj("rnf_rr", "f", fx, fy + 380); P.obj("rnf_rr1", "+ 1", fx + 40, fy + 380); P.obj("rnf_rrm", "mod 2", fx + 80, fy + 380)
    P.obj("rnf_vt", "t b f", fx, fy + 372); P.c("rnf_vel", "rnf_vt")
    P.c("rnf_vt", "rnf_rr", 0, 0); P.c("rnf_rr", "rnf_rr1"); P.c("rnf_rr1", "rnf_rrm"); P.c("rnf_rrm", "rnf_rr", 0, 1)
    P.obj("rnf_vsel", "sel 0 1", fx, fy + 395); P.c("rnf_rr", "rnf_vsel")
    caught = "(fs_filled[0]+fs_filled[1]+fs_filled[2]>0)"
    for k in range(2):
        P.c("rnf_vsel", f"rnf_vb{k}", k, 0); P.obj(f"rnf_vb{k}", "t b", fx + 60 * k, fy + 410)
        P.obj(f"rnf_vf{k}", "f", fx + 60 * k, fy + 425); P.c(f"rnf_vb{k}", f"rnf_vf{k}"); P.c("rnf_vt", f"rnf_vf{k}", 1, 1)
        add_found_note(P, k, fx + 700 + 500 * k, fy + 100, ex, caught, prefix="rf", gate="1")
        P.c(f"rnf_vf{k}", f"rf{k}_in")
    # 5. their level: like the found chimes' (down only toward FM_LEVEL_DB, then a soft limit), then the Room notes level
    P.sig2("rnf_sum", "+~", "rf0_out", "rf1_out", fx, fy + 500)
    P.obj("rnf_env", "env~ 1024 256", fx + 100, fy + 500); P.c("rnf_sum", "rnf_env")
    ex("rnf_agc", f"dbtorms(100+min(0,({FM_LEVEL_DB}-max($f1,1))*{FM_LEVEL_RATIO}))", fx + 100, fy + 515); P.c("rnf_env", "rnf_agc")
    P.obj("rnf_agc_pk", "pack f 20", fx + 100, fy + 530); P.obj("rnf_agc_ln", "line~", fx + 100, fy + 545)
    P.c("rnf_agc", "rnf_agc_pk"); P.c("rnf_agc_pk", "rnf_agc_ln")
    P.sig2("rnf_lvl", "*~", "rnf_sum", "rnf_agc_ln", fx, fy + 560)
    soft_limit(P, "rnf_lim", "rnf_lvl", FM_LIMIT, RNF_GAIN, fx, fy + 575)
    P.sig2("rnf_out", "*~", "rnf_lim", lvl, fx, fy + 680)
    mr = smoothed(P, "mix_ring", "mix_ring", fx + 200, fy + 640); mbl = smoothed(P, "mix_bells", "mix_bells", fx + 300, fy + 640)
    P.sig2("rn_out_m", "*~", "rn_out", mr, fx, fy + 690); P.sig2("rnf_out_m", "*~", "rnf_out", mbl, fx + 100, fy + 690)
    return P.sig2("rnx_out", "+~", "rn_out_m", "rnf_out_m", fx, fy + 700)
