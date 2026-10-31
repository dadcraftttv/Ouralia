// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.dadcraftttv.ouralia;

import java.util.Arrays;
import java.util.Locale;

/**
 * Sound kinds, step 4c (Oct 31, a Dev spike: docs/design/sound-kinds.md). Plain logic: the mic's last ~1 s (from the
 * patch's sk_ring) is brought to 16 kHz for YAMNet, its 521 scores become seven kinds (the highest of each kind's
 * classes, kinds.txt), and a kind is heard once it leads two judgements in a row (~1 s; the gate is 2 s). Only
 * listens: the Now line, Diagnostics and the event log show it. {@link SoundKindsRunner} does the Android part.
 */
final class SoundKinds {
    static final String[] NAMES = {"voices", "music", "traffic", "birds", "water", "steps", "quiet"};
    static final int QUIET = 6;
    /** YAMNet's input: 0.975 s at 16 kHz. */
    static final int RATE = 16000, SAMPLES = 15600;
    /** No kind scores this much: the room is quiet (4b: 0.1 hid quiet traffic). */
    static final float QUIET_FLOOR = 0.05f;
    static final int ENTER_N = 2;

    /** The model, only in the Dev build (src/dev YamNet: LiteRT). */
    interface Model {
        /** 521 scores for SAMPLES samples at RATE. */
        float[] scores(float[] wave);
        void close();
    }

    private final int[][] cols;
    private final float[] last = new float[NAMES.length];
    private int kind = -1, candidate = -1, candidateN;

    /** {@code kinds}: kinds.txt's lines ("voices 0 1 2 ...", "#" comments), one per name, in NAMES' order. */
    SoundKinds(String kinds) {
        cols = new int[NAMES.length][];
        for (String line : kinds.split("\n")) {
            String[] p = line.trim().split("\\s+");
            int k = Arrays.asList(NAMES).indexOf(p[0]);
            if (line.startsWith("#") || k < 0) {
                continue;
            }
            cols[k] = new int[p.length - 1];
            for (int i = 1; i < p.length; i++) {
                cols[k][i - 1] = Integer.parseInt(p[i]);
            }
        }
        for (int[] c : cols) {
            if (c == null || c.length == 0) {
                throw new IllegalArgumentException("kinds.txt misses a kind");
            }
        }
    }

    /**
     * The newest SAMPLES at RATE from {@code n} samples at {@code rate} (the end of {@code x} is the newest): a
     * windowed-sinc low-pass at 7.2 kHz, then read at 16 kHz with linear steps. Null if there is too little.
     */
    static float[] to16k(float[] x, int n, int rate) {
        double step = (double) rate / RATE;
        int taps = 32;                                    // each side
        double cut = 7200.0 / rate;                       // cycles per sample
        float[] h = new float[2 * taps + 1];
        double sum = 0;
        for (int i = -taps; i <= taps; i++) {
            double s = i == 0 ? 2 * cut : Math.sin(2 * Math.PI * cut * i) / (Math.PI * i);
            double w = 0.42 + 0.5 * Math.cos(Math.PI * i / (taps + 1)) + 0.08 * Math.cos(2 * Math.PI * i / (taps + 1));
            h[i + taps] = (float) (s * w);
            sum += s * w;
        }
        for (int i = 0; i < h.length; i++) {
            h[i] /= (float) sum;
        }
        double first = n - 2 - taps - (SAMPLES - 1) * step;   // where the oldest output sample falls (its next one in reach)
        if (first < taps) {
            return null;
        }
        float[] out = new float[SAMPLES];
        for (int j = 0; j < SAMPLES; j++) {
            double at = first + j * step;
            int i0 = (int) at;
            double f = at - i0;
            out[j] = (float) ((1 - f) * filtered(x, i0, h, taps) + f * filtered(x, i0 + 1, h, taps));
        }
        return out;
    }

    private static float filtered(float[] x, int i, float[] h, int taps) {
        float s = 0;
        for (int k = -taps; k <= taps; k++) {
            s += h[k + taps] * x[i + k];
        }
        return s;
    }

    /** One judgement from YAMNet's scores; true when what is heard changed. */
    boolean judge(float[] scores) {
        int lead = 0;
        for (int k = 0; k < NAMES.length; k++) {
            float m = 0;
            for (int c : cols[k]) {
                m = Math.max(m, scores[c]);
            }
            last[k] = m;
            if (k != QUIET && m > last[lead]) {
                lead = k;
            }
        }
        int top = 0;   // YAMNet's own top class
        for (int c = 1; c < scores.length; c++) {
            if (scores[c] > scores[top]) {
                top = c;
            }
        }
        if (last[lead] < QUIET_FLOOR || top == cols[QUIET][0]) {
            lead = QUIET;
        }
        candidateN = lead == candidate ? candidateN + 1 : 1;
        candidate = lead;
        int was = kind;
        if (lead != kind && candidateN >= ENTER_N) {
            kind = lead;
        }
        return kind != was;
    }

    /** What is heard now (an index in NAMES), or -1 before the first two judgements. */
    int kind() {
        return kind;
    }

    /** The last judgement's numbers, one per kind. */
    float[] scores() {
        return last.clone();
    }

    /** "Heard: birds 0.71" for the event log. */
    String heardLine() {
        return kind < 0 ? "Heard: nothing yet" : String.format(Locale.US, "Heard: %s %.2f", NAMES[kind], last[kind]);
    }

    /** The minute's cost for the event log: how many judgements and how long YAMNet took (median, slowest). */
    static String costLine(long[] nanos, int n) {
        if (n == 0) {
            return "Sound kinds: no judgements this minute";
        }
        long[] s = Arrays.copyOf(nanos, n);
        Arrays.sort(s);
        return String.format(Locale.US, "Sound kinds: %d judgements, YAMNet median %.1f ms, slowest %.1f ms",
                n, s[n / 2] / 1e6, s[n - 1] / 1e6);
    }

    void reset() {
        Arrays.fill(last, 0);
        kind = candidate = -1;
        candidateN = 0;
    }
}
