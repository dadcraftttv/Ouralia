// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.dadcraftttv.ouralia;

/**
 * Tap tempo (Oct 22, ROADMAP 22's app ideas): the Tap button under Set Tempo. Each tap after the first gives a BPM
 * from the taps' spacing (the mean of the last few gaps); a pause starts over, and a gap far off the others (a missed
 * or doubled tap) starts over from that gap. The BPM is folded into the Set Tempo range by octaves (tapping every
 * other beat of a fast song still lands on it). Plain logic; the button sets Tempo to Set and Set Tempo to it.
 */
final class TapTempo {
    /** A pause this long starts over. */
    static final long RESET_MS = 2000;
    /** The gaps averaged. */
    static final int KEEP = 5;
    /** A gap this far (share) from the mean so far starts over from it. */
    static final double OFF_SHARE = 0.4;
    static final float MIN_BPM = 60f, MAX_BPM = 160f;   // (Set Tempo's range)

    private final long[] gaps = new long[KEEP];
    private int count;      // gaps kept
    private long lastTap = -1;

    /** A tap at this time (ms); returns the BPM so far, or 0 with nothing to go on yet. */
    float tap(long nowMs) {
        long gap = lastTap < 0 ? -1 : nowMs - lastTap;
        lastTap = nowMs;
        if (gap <= 0 || gap > RESET_MS) {
            count = 0;
            return 0f;
        }
        if (count > 0 && Math.abs(gap - mean()) > OFF_SHARE * mean()) {
            count = 0;
        }
        System.arraycopy(gaps, 0, gaps, 1, KEEP - 1);
        gaps[0] = gap;
        count = Math.min(count + 1, KEEP);
        return fold(60000f / (float) mean());
    }

    /** Taps since the last start-over (the first tap counts). */
    int taps() {
        return lastTap < 0 ? 0 : count + 1;
    }

    private double mean() {
        long sum = 0;
        for (int i = 0; i < count; i++) {
            sum += gaps[i];
        }
        return sum / (double) count;
    }

    /** Into MIN_BPM..MAX_BPM by octaves, to a whole BPM. */
    static float fold(float bpm) {
        while (bpm > MAX_BPM) {
            bpm /= 2;
        }
        while (bpm < MIN_BPM && bpm > 0) {
            bpm *= 2;
        }
        return Math.round(bpm);
    }
}
