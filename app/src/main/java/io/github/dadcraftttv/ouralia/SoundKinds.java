// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.dadcraftttv.ouralia;

import java.util.Arrays;
import java.util.Locale;

/**
 * Sound kinds, step 4c (Oct 31, a Dev spike: docs/design/sound-kinds.md). Plain logic: the mic's last ~1 s (from the
 * patch's sk_ring) is brought to 16 kHz for YAMNet, its 521 scores become seven kinds (the highest of each kind's
 * classes, kinds.txt), and a kind is heard once it leads two judgements in a row, or one with a score of STRONG or more
 * (a judgement every ~0.25 s; the gate is 2 s). Only
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
    /** A lead this sure changes the kind at once (the 2 s check, Oct 31: birds took ~3 s with two in a row). */
    static final float STRONG = 0.5f;
    /** The patch's ring: SEGS segments (patchgen SK_N); the SEGS - 1 whole ones are read each time. */
    static final int SEGS = 5;
    /**
     * Make Room for Music (Nov 1): music in the room when it was the kind for ROOM_ON of the last ROOM_N judgements
     * (~20 s at one every ~0.25 s), gone again under ROOM_OFF. DadCraft's Nov 1 runs replayed: never on the walk or at
     * home, on 86% of a song-filled 14 minutes, 3 changes.
     */
    static final int MUSIC = 1, ROOM_N = 80;
    static final float ROOM_ON = 0.4f, ROOM_OFF = 0.15f;
    /**
     * Mind the Gap (Nov 1, step 6): people talking when voices were the kind for TALK_ON of the last TALK_N
     * judgements (~8 s: talk starts and stops more often than a song), over again under TALK_OFF.
     */
    static final int VOICES = 0, TALK_N = 32;
    static final float TALK_ON = 0.4f, TALK_OFF = 0.15f;

    static final int WATER = 4, WATER_N = 32;
    static final float WATER_ON = 0.4f, WATER_OFF = 0.15f;
    /** Traffic Passes Through (Nov 3): fast score permission, released after 2 s of losing. */
    static final int TRAFFIC = 2;
    static final float TRAFFIC_ENTER = 0.20f, TRAFFIC_LEAVE = 0.10f;
    static final long TRAFFIC_RELEASE_MS = 2000;
    private int trafficN;
    private long trafficBadSince = -1;
    private boolean traffic;

    /** Birds Lift the Chimes (Nov 2): the same ~8 s window as water, so the gaps between calls hold it. */
    static final int BIRDS = 3, BIRDS_N = 32;
    static final float BIRDS_ON = 0.4f, BIRDS_OFF = 0.15f;

    /** The model, only in the Dev build (src/dev YamNet: LiteRT). */
    interface Model {
        /** 521 scores for SAMPLES samples at RATE. */
        float[] scores(float[] wave);
        void close();
    }

    private final int[][] cols;
    private final float[] last = new float[NAMES.length];
    private int kind = -1, candidate = -1, candidateN;
    private final boolean[] wasMusic = new boolean[ROOM_N];
    private int musicN, roomI;
    private boolean roomMusic;
    private final boolean[] wasTalk = new boolean[TALK_N];
    private int talkN, talkI;
    private boolean talking;
    private final boolean[] wasWater = new boolean[WATER_N];
    private int waterN, waterI;
    private boolean water;
    private final boolean[] wasBirds = new boolean[BIRDS_N];
    private int birdsN, birdsI;
    /** Traffic Flow (Nov 3): a settled traffic scene, the room-music window's length (~20 s) so a short lull
     *  at a red light holds it: traffic the kind for TRAFFIC_SCENE_ON of the last TRAFFIC_SCENE_N, gone under OFF. */
    static final int TRAFFIC_SCENE_N = 80;
    static final float TRAFFIC_SCENE_ON = 0.4f, TRAFFIC_SCENE_OFF = 0.15f;
    private final boolean[] wasTraffic = new boolean[TRAFFIC_SCENE_N];
    private int trafficSceneN, trafficI;
    private boolean trafficScene;
    private boolean birds;


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
        return to16k(x, n, rate, null);
    }

    /** The same into {@code out} (SAMPLES long; null: a new one), so the runner reuses one buffer (review, Nov 4). */
    static float[] to16k(float[] x, int n, int rate, float[] out) {
        double step = (double) rate / RATE;
        float[] h = lowPass(rate);
        int taps = (h.length - 1) / 2;
        double first = n - 2 - taps - (SAMPLES - 1) * step;   // where the oldest output sample falls (its next one in reach)
        if (first < taps) {
            return null;
        }
        if (out == null || out.length != SAMPLES) {
            out = new float[SAMPLES];
        }
        for (int j = 0; j < SAMPLES; j++) {
            double at = first + j * step;
            int i0 = (int) at;
            double f = at - i0;
            float a = filtered(x, i0, h, taps);
            // (at 48 kHz the step is exactly 3, so f is always 0: the second point would only be multiplied by 0)
            out[j] = f == 0 ? a : (float) ((1 - f) * a + f * filtered(x, i0 + 1, h, taps));
        }
        return out;
    }

    /**
     * Resampling that keeps what the last judgement already computed (review, Nov 4). Consecutive judgements read the
     * ring shifted by exactly one segment, and when a segment is a whole number of 16 kHz steps (48 kHz: always) the
     * output grid shifts with it: the older part of the new output is the old output moved along, sample for sample,
     * and only the newest segment's part is computed. Anything else (the first time, a skipped judgement, a new rate
     * or segment size) computes it all. One per worker thread.
     */
    static final class Resampler {
        private final float[] out = new float[SAMPLES];
        private int lastK = -1, lastRate, lastSeg, lastN;
        int fresh;   // (how many output samples the last call computed: for the tests)

        /** As {@link #to16k}, for the window ending before ring segment {@code k} of {@code seg} samples. */
        float[] next(float[] x, int n, int rate, int k, int seg) {
            double step = (double) rate / RATE;
            int istep = (int) step, d = istep == step && seg % istep == 0 ? seg / istep : -1;
            boolean shift = d > 0 && d < SAMPLES && k == (lastK + 1) % SEGS && rate == lastRate && seg == lastSeg
                    && n == lastN;
            lastK = k;
            lastRate = rate;
            lastSeg = seg;
            lastN = n;
            if (!shift) {
                fresh = SAMPLES;
                if (to16k(x, n, rate, out) == null) {
                    lastK = -1;
                    return null;
                }
                return out;
            }
            float[] h = lowPass(rate);
            int taps = (h.length - 1) / 2;
            int first = n - 2 - taps - (SAMPLES - 1) * istep;   // (whole: the step is)
            System.arraycopy(out, d, out, 0, SAMPLES - d);
            for (int j = SAMPLES - d; j < SAMPLES; j++) {
                out[j] = filtered(x, first + j * istep, h, taps);
            }
            fresh = d;
            return out;
        }
    }

    private static int cachedRate;
    private static float[] cachedH;

    /** The windowed-sinc low-pass at 7.2 kHz for {@code rate}, 32 taps each side; made once per rate. */
    private static synchronized float[] lowPass(int rate) {
        if (cachedH != null && cachedRate == rate) {
            return cachedH;
        }
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
        cachedRate = rate;
        cachedH = h;
        return h;
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
        return judge(scores, System.nanoTime() / 1_000_000);
    }

    /** Monotonic milliseconds, explicit for threshold/lifecycle tests. */
    boolean judge(float[] scores, long now) {
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
        boolean trafficLeads = last[TRAFFIC] >= TRAFFIC_ENTER;
        boolean trafficLoses = last[TRAFFIC] < TRAFFIC_LEAVE;
        for (int k = 0; k < QUIET; k++) {
            if (k != TRAFFIC) {
                trafficLeads &= last[TRAFFIC] > last[k];
                trafficLoses |= last[k] > last[TRAFFIC];
            }
        }
        trafficN = trafficLeads ? trafficN + 1 : 0;
        if (!traffic && trafficN >= ENTER_N) {
            traffic = true;
        }
        if (!trafficLoses) {
            trafficBadSince = -1;
        } else if (trafficBadSince < 0) {
            trafficBadSince = now;
        } else if (now - trafficBadSince >= TRAFFIC_RELEASE_MS) {
            traffic = false;
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
        if (lead != kind && (candidateN >= ENTER_N || (lead != QUIET && last[lead] >= STRONG))) {
            kind = lead;
        }
        boolean m = kind == MUSIC;   // the room-music window (a fixed ROOM_N long, empty at first: fills from silence)
        musicN += (m ? 1 : 0) - (wasMusic[roomI] ? 1 : 0);
        wasMusic[roomI] = m;
        roomI = (roomI + 1) % ROOM_N;
        if (!roomMusic && musicN >= ROOM_ON * ROOM_N) {
            roomMusic = true;
        } else if (roomMusic && musicN <= ROOM_OFF * ROOM_N) {
            roomMusic = false;
        }
        boolean v = kind == VOICES;   // the talk window, the same way
        talkN += (v ? 1 : 0) - (wasTalk[talkI] ? 1 : 0);
        wasTalk[talkI] = v;
        talkI = (talkI + 1) % TALK_N;
        if (!talking && talkN >= TALK_ON * TALK_N) {
            talking = true;
        } else if (talking && talkN <= TALK_OFF * TALK_N) {
            talking = false;
        }
        boolean w = kind == WATER;
        waterN += (w ? 1 : 0) - (wasWater[waterI] ? 1 : 0);
        wasWater[waterI] = w;
        waterI = (waterI + 1) % WATER_N;
        if (!water && waterN >= WATER_ON * WATER_N) {
            water = true;
        } else if (water && waterN <= WATER_OFF * WATER_N) {
            water = false;
        }
        boolean b = kind == BIRDS;
        birdsN += (b ? 1 : 0) - (wasBirds[birdsI] ? 1 : 0);
        wasBirds[birdsI] = b;
        birdsI = (birdsI + 1) % BIRDS_N;
        if (!birds && birdsN >= BIRDS_ON * BIRDS_N) {
            birds = true;
        } else if (birds && birdsN <= BIRDS_OFF * BIRDS_N) {
            birds = false;
        }
        boolean tr = kind == TRAFFIC;
        trafficSceneN += (tr ? 1 : 0) - (wasTraffic[trafficI] ? 1 : 0);
        wasTraffic[trafficI] = tr;
        trafficI = (trafficI + 1) % TRAFFIC_SCENE_N;
        if (!trafficScene && trafficSceneN >= TRAFFIC_SCENE_ON * TRAFFIC_SCENE_N) {
            trafficScene = true;
        } else if (trafficScene && trafficSceneN <= TRAFFIC_SCENE_OFF * TRAFFIC_SCENE_N) {
            trafficScene = false;
        }
        return kind != was;
    }

    /** What is heard now (an index in NAMES), or -1 before the first (two, unless strong) judgements. */
    int kind() {
        return kind;
    }

    /** True while music has been playing in the room (see ROOM_N). */
    boolean roomMusic() {
        return roomMusic;
    }

    /** True while people have been talking (see TALK_N). */
    boolean talking() {
        return talking;
    }

    /** True while settled water fills the window (about eight seconds). */
    boolean water() {
        return water;
    }

    /** True while settled birds fill the window (about eight seconds), like {@link #water}. */
    boolean birds() {
        return birds;
    }

    /** True while settled traffic fills the window (about twenty seconds). */
    boolean trafficScene() {
        return trafficScene;
    }

    /** Fast score permission, independent of the optional Mind the Gap switch. */
    boolean trafficAllowed() {
        return traffic && !talking;
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
        return costLine(nanos, null, n);
    }

    /** "; <what> median X ms, slowest Y ms" over the first {@code n}, or "" when there are none. */
    static String medianLine(String what, long[] nanos, int n) {
        if (n == 0) {
            return "";
        }
        long[] s = Arrays.copyOf(nanos, n);
        Arrays.sort(s);
        return String.format(Locale.US, "; %s median %.1f ms, slowest %.1f ms", what, s[n / 2] / 1e6, s[n - 1] / 1e6);
    }

    /** With {@code whole}: each judgement from reading the ring to the scores, i.e. the copy and resampling too. */
    static String costLine(long[] nanos, long[] whole, int n) {
        if (n == 0) {
            return "Sound kinds: no judgements this minute";
        }
        long[] s = Arrays.copyOf(nanos, n);
        Arrays.sort(s);
        String line = String.format(Locale.US, "Sound kinds: %d judgements, YAMNet median %.1f ms, slowest %.1f ms",
                n, s[n / 2] / 1e6, s[n - 1] / 1e6);
        if (whole != null) {
            long[] w = Arrays.copyOf(whole, n);
            Arrays.sort(w);
            line += String.format(Locale.US, "; whole judgement median %.1f ms, slowest %.1f ms", w[n / 2] / 1e6,
                    w[n - 1] / 1e6);
        }
        return line;
    }

    void reset() {
        traffic = false;
        trafficN = 0;
        trafficBadSince = -1;
        Arrays.fill(last, 0);
        kind = candidate = -1;
        candidateN = 0;
        Arrays.fill(wasMusic, false);
        musicN = roomI = 0;
        roomMusic = false;
        Arrays.fill(wasTalk, false);
        talkN = talkI = 0;
        talking = false;
        Arrays.fill(wasWater, false);
        waterN = waterI = 0;
        water = false;
        Arrays.fill(wasBirds, false);
        Arrays.fill(wasTraffic, false);
        trafficSceneN = trafficI = 0;
        trafficScene = false;
        birdsN = birdsI = 0;
        birds = false;
    }
}
