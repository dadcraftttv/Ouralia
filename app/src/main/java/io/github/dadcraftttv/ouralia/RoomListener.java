// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.dadcraftttv.ouralia;

/**
 * What kind of place it sounds like (Oct 22, ROADMAP 18 dcs_listen~). The patch's room listener judges the last 2 s
 * twice a second: talking, music, traffic, wind (0..1 each). For now it only listens (DadCraft: "show it first"):
 * the Now line and Diagnostics show it and the event log records each change, so his walks tell how often it is
 * right before it drives anything. Plain logic: a kind is heard once it leads above ENTER for ENTER_N judgements in
 * a row, and lets go after LEAVE_N under LEAVE (so a pause in a talk doesn't flicker it).
 */
final class RoomListener {
    static final int TALKING = 0, MUSIC = 1, TRAFFIC = 2, WIND = 3;
    static final float ENTER = 0.5f, LEAVE = 0.3f;
    static final int ENTER_N = 2, LEAVE_N = 4;

    private final float[] last = new float[4];
    private int kind = -1, candidate = -1, candidateN, lowN;

    /** One judgement (talking, music, traffic, wind); true when what is heard changed. */
    boolean judge(float[] v) {
        System.arraycopy(v, 0, last, 0, Math.min(4, v.length));
        int lead = -1;
        for (int i = 0; i < 4; i++) {
            if (last[i] > ENTER && (lead < 0 || last[i] > last[lead])) {
                lead = i;
            }
        }
        if (kind >= 0) {
            lowN = last[kind] < LEAVE ? lowN + 1 : 0;
        }
        candidateN = lead >= 0 && lead == candidate ? candidateN + 1 : (lead >= 0 ? 1 : 0);
        candidate = lead;
        int was = kind;
        if (lead >= 0 && lead != kind && candidateN >= ENTER_N) {
            kind = lead;
            lowN = 0;
        } else if (kind >= 0 && lowN >= LEAVE_N) {
            kind = -1;
        }
        return kind != was;
    }

    /** What is heard now (TALKING..WIND), or -1 for none of them. */
    int kind() {
        return kind;
    }

    /** The last judgement's four numbers. */
    float[] scores() {
        return last.clone();
    }

    void reset() {
        java.util.Arrays.fill(last, 0);
        kind = candidate = -1;
        candidateN = lowN = 0;
    }
}
