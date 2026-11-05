// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.dadcraftttv.ouralia;

import java.util.Arrays;
import java.util.Locale;

/**
 * Sound kinds, step 4c (Oct 31, a Dev spike: docs/design/sound-kinds.md). Plain logic: the mic's last ~1 s (from the
 * patch's sk_ring) is brought to 16 kHz for YAMNet, its 521 scores become seven kinds (the highest of each kind's
 * classes, kinds.txt), and a kind is heard once it leads two judgements in a row, or one with a score of STRONG or more
 * (a judgement every ~0.25 s; the gate is 2 s); its scene windows are measured in time (STEP_MS). Only
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
     * The scene windows are measured in time, not judgements (the 2026-11-03 review, built Nov 5, DadCraft: "go ahead
     * with the replay tool and the time-based windows"): each judgement counts for the time since the one before (STEP_MS
     * the first time, at most MAX_STEP_MS: a judgement hears ~1 s), and a window holds the judgements of its last span.
     * A count window stretched when work was skipped (32 judgements were 8 s only at one every 0.25 s); now a gap is
     * time no kind was heard. At one judgement every STEP_MS the shares are exactly the old counts' (13 of 32 = 3.25 s
     * of 8 s), so every number tuned on the counts holds.
     */
    static final long STEP_MS = 250, MAX_STEP_MS = 1000;

    /** A share of the last {@code ms} during which a kind was heard, on at {@code on} of it, off again at {@code off}. */
    static final class Window {
        long ms;
        float on, off;
        private long[] at = new long[0];
        private int[] dur = new int[0];
        private int head, size;
        private long heardMs;
        private boolean state;

        Window(long ms, float on, float off) {
            this.ms = ms;
            this.on = on;
            this.off = off;
            clear();
        }

        void clear() {
            int cap = (int) (4 * ms / STEP_MS) + 8;   // (judgements faster than one every STEP_MS / 4: the oldest go early)
            if (at.length != cap) {
                at = new long[cap];
                dur = new int[cap];
            }
            head = size = 0;
            heardMs = 0;
            state = false;
        }

        /** One judgement at {@code now}, standing for {@code stepMs}; the window's state after it. */
        boolean add(long now, int stepMs, boolean heard) {
            if (size == at.length) {
                drop();
            }
            int i = (head + size++) % at.length;
            at[i] = now;
            dur[i] = heard ? stepMs : 0;
            heardMs += dur[i];
            while (size > 0 && at[head] <= now - ms) {
                drop();
            }
            if (!state && heardMs >= Math.round(on * ms)) {
                state = true;
            } else if (state && heardMs <= Math.round(off * ms)) {
                state = false;
            }
            return state;
        }

        private void drop() {
            heardMs -= dur[head];
            head = (head + 1) % at.length;
            size--;
        }

        boolean on() {
            return state;
        }

        /** The share of the span heard just now (for the replay tool). */
        float share() {
            return (float) heardMs / ms;
        }
    }

    /**
     * Make Room for Music (Nov 1): music in the room when it was the kind for ROOM_ON of the last ROOM_MS (~20 s),
     * gone again under ROOM_OFF. DadCraft's Nov 1 runs replayed: never on the walk or at home, on 86% of a song-filled
     * 14 minutes, 3 changes.
     */
    static final int MUSIC = 1;
    static final long ROOM_MS = 20_000;
    static final float ROOM_ON = 0.4f, ROOM_OFF = 0.15f;
    /**
     * Mind the Gap (Nov 1, step 6): people talking when voices were the kind for TALK_ON of the last TALK_MS (~8 s:
     * talk starts and stops more often than a song), over again under TALK_OFF.
     */
    static final int VOICES = 0;
    static final long TALK_MS = 8_000;
    static final float TALK_ON = 0.4f, TALK_OFF = 0.15f;

    static final int WATER = 4;
    static final long WATER_MS = 8_000;
    static final float WATER_ON = 0.4f, WATER_OFF = 0.15f;
    /** Traffic Passes Through (Nov 3): fast score permission, released after 2 s of losing. */
    static final int TRAFFIC = 2;
    static final float TRAFFIC_ENTER = 0.20f, TRAFFIC_LEAVE = 0.10f;
    static final long TRAFFIC_RELEASE_MS = 2000;
    private int trafficN;
    private long trafficBadSince = -1;
    private boolean traffic;

    /** Birds Lift the Chimes (Nov 2): the same ~8 s window as water, so the gaps between calls hold it. */
    static final int BIRDS = 3;
    static final long BIRDS_MS = 8_000;
    static final float BIRDS_ON = 0.4f, BIRDS_OFF = 0.15f;

    /**
     * Mixed Surroundings (Nov 4, the 2026-11-03 review's item 3, DadCraft: "go ahead and start on mixed surroundings";
     * `sk_mixed`, off by default): real places are mixes (talk by a fountain, birds by a road), but each scene above
     * counted only while its kind won, so in a mix each got part of its window and maybe none came on. On, a kind
     * counts while it is clearly there: at least PRESENT_MIN and PRESENT_REL of the leader's score. The two decorative
     * gestures (Water Leaves a Trail, Birds Lift the Chimes) don't stack: the one heard louder lately leads (scores
     * averaged over ~8 s), the other takes over only past LEAD_RATIO of it. Nothing scoring while YAMNet doesn't hear silence is "unsure",
     * not a quiet room (Calm Builds measures quiet from the level, so it is unchanged). Off: exactly as before.
     */
    static volatile boolean mixed;
    static final float PRESENT_MIN = 0.2f, PRESENT_REL = 0.5f;
    static final float LEAD_RATIO = 1.25f;
    static final long AVG_MS = 8_000;   // (the gestures' averages: each judgement moves them by its step / AVG_MS)
    private int gesture = -1;   // WATER or BIRDS while both are heard (mixed only)
    private float waterAvg, birdsAvg;
    private boolean unsure, silent;
    private final boolean[] in = new boolean[NAMES.length];   // (this judgement's kinds clearly there)

    /**
     * The numbers the replay tool may try other values for (tools/replay_kinds.py --set); the app keeps the defaults.
     * presentMin is per kind, so a kind that scores low (water) can get its own.
     */
    final float[] presentMin = new float[NAMES.length];
    float presentRel = PRESENT_REL, quietFloor = QUIET_FLOOR, strong = STRONG, leadRatio = LEAD_RATIO;

    /** The model (YamNet on LiteRT; the tests stand in a fake). */
    interface Model {
        /** 521 scores for SAMPLES samples at RATE. */
        float[] scores(float[] wave);
        void close();
    }

    private final int[][] cols;
    private final float[] last = new float[NAMES.length];
    private int kind = -1, candidate = -1, candidateN;
    private long lastNow = Long.MIN_VALUE;
    final Window room = new Window(ROOM_MS, ROOM_ON, ROOM_OFF);
    final Window talk = new Window(TALK_MS, TALK_ON, TALK_OFF);
    final Window waterW = new Window(WATER_MS, WATER_ON, WATER_OFF);
    final Window birdsW = new Window(BIRDS_MS, BIRDS_ON, BIRDS_OFF);
    /** Traffic Flow (Nov 3): a settled traffic scene, the room-music window's length (~20 s) so a short lull
     *  at a red light holds it: traffic the kind for TRAFFIC_SCENE_ON of the last TRAFFIC_SCENE_MS, gone under OFF. */
    static final long TRAFFIC_SCENE_MS = 20_000;
    static final float TRAFFIC_SCENE_ON = 0.4f, TRAFFIC_SCENE_OFF = 0.15f;
    final Window trafficW = new Window(TRAFFIC_SCENE_MS, TRAFFIC_SCENE_ON, TRAFFIC_SCENE_OFF);


    /** {@code kinds}: kinds.txt's lines ("voices 0 1 2 ...", "#" comments), one per name, in NAMES' order. */
    SoundKinds(String kinds) {
        cols = new int[NAMES.length][];
        Arrays.fill(presentMin, PRESENT_MIN);
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

    /**
     * One judgement from YAMNet's scores, STEP_MS after the last (the tests' steady clock); true when what is heard
     * changed. The app passes the real time.
     */
    boolean judge(float[] scores) {
        return judge(scores, lastNow == Long.MIN_VALUE ? 0 : lastNow + STEP_MS);
    }

    /** Monotonic milliseconds. */
    boolean judge(float[] scores, long now) {
        float[] k7 = new float[NAMES.length];
        for (int k = 0; k < NAMES.length; k++) {
            float m = 0;
            for (int c : cols[k]) {
                m = Math.max(m, scores[c]);
            }
            k7[k] = m;
        }
        int top = 0;   // YAMNet's own top class
        for (int c = 1; c < scores.length; c++) {
            if (scores[c] > scores[top]) {
                top = c;
            }
        }
        return judgeKinds(k7, top == cols[QUIET][0], now);
    }

    /**
     * The same from the seven kinds' scores and whether YAMNet's top class was Silence: what a session log keeps, so
     * the replay tool runs exactly this.
     */
    boolean judgeKinds(float[] kinds, boolean yamnetSilent, long now) {
        int step = lastNow == Long.MIN_VALUE ? (int) STEP_MS : (int) Math.max(0, Math.min(MAX_STEP_MS, now - lastNow));
        lastNow = now;
        int lead = 0;
        for (int k = 0; k < NAMES.length; k++) {
            last[k] = kinds[k];
            if (k != QUIET && last[k] > last[lead]) {
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
        int best = lead;   // (the strongest kind, before quiet)
        silent = yamnetSilent;
        if (last[lead] < quietFloor || silent) {
            lead = QUIET;
        }
        boolean mix = mixed;
        float a = Math.min(1f, (float) step / AVG_MS);
        waterAvg += (last[WATER] - waterAvg) * a;
        birdsAvg += (last[BIRDS] - birdsAvg) * a;
        unsure = mix && !silent && last[best] < quietFloor;
        for (int k = 0; k < QUIET; k++) {
            in[k] = !silent && last[k] >= presentMin[k] && last[k] >= presentRel * last[best];
        }
        candidateN = lead == candidate ? candidateN + 1 : 1;
        candidate = lead;
        int was = kind;
        if (lead != kind && (candidateN >= ENTER_N || (lead != QUIET && last[lead] >= strong))) {
            kind = lead;
        }
        // (each scene window: while its kind is clearly there with Mixed Surroundings, else while it is the kind;
        //  empty at first, so they fill from silence)
        room.add(now, step, mix ? in[MUSIC] : kind == MUSIC);
        talk.add(now, step, mix ? in[VOICES] : kind == VOICES);
        boolean water = waterW.add(now, step, mix ? in[WATER] : kind == WATER);
        boolean birds = birdsW.add(now, step, mix ? in[BIRDS] : kind == BIRDS);
        trafficW.add(now, step, mix ? in[TRAFFIC] : kind == TRAFFIC);
        if (!mix || !(water && birds)) {
            gesture = water ? WATER : birds ? BIRDS : -1;
        } else if (gesture != WATER && gesture != BIRDS) {
            gesture = waterAvg >= birdsAvg ? WATER : BIRDS;
        } else if (gesture == WATER && birdsAvg > leadRatio * waterAvg) {
            gesture = BIRDS;
        } else if (gesture == BIRDS && waterAvg > leadRatio * birdsAvg) {
            gesture = WATER;
        }
        return kind != was;
    }

    /** Mixed only: whether each kind was clearly there in the last judgement (for the replay tool). */
    boolean present(int k) {
        return in[k];
    }

    /** Whether YAMNet's own top class was Silence in the last judgement (the session log keeps it). */
    boolean silent() {
        return silent;
    }

    /** Which gesture leads while water and birds are both heard (WATER, BIRDS or -1; for the replay tool). */
    int gesture() {
        return gesture;
    }

    /** What is heard now (an index in NAMES), or -1 before the first (two, unless strong) judgements. */
    int kind() {
        return kind;
    }

    /** True while music has been playing in the room (see ROOM_N). */
    boolean roomMusic() {
        return room.on();
    }

    /** True while people have been talking (see TALK_N). */
    boolean talking() {
        return talk.on();
    }

    /** True while settled water fills the window (about eight seconds). */
    boolean water() {
        return waterW.on();
    }

    /** Water Leaves a Trail's water: {@link #water}, unless birds lead the gestures (mixed only). */
    boolean waterLeads() {
        return waterW.on() && (!mixed || gesture != BIRDS);
    }

    /** Birds Lift the Chimes' birds: {@link #birds}, unless water leads the gestures (mixed only). */
    boolean birdsLead() {
        return birdsW.on() && (!mixed || gesture != WATER);
    }

    /** Mixed only: no kind scored though YAMNet didn't hear silence (an unfamiliar sound, not a quiet room). */
    boolean unsure() {
        return unsure;
    }

    /** True while settled birds fill the window (about eight seconds), like {@link #water}. */
    boolean birds() {
        return birdsW.on();
    }

    /** True while settled traffic fills the window (about twenty seconds). */
    boolean trafficScene() {
        return trafficW.on();
    }

    /** Fast score permission, independent of the optional Mind the Gap switch. */
    boolean trafficAllowed() {
        return traffic && !talk.on();
    }

    /** The last judgement's numbers, one per kind. */
    float[] scores() {
        return last.clone();
    }

    /** "Heard: birds 0.71" for the event log. */
    String heardLine() {
        return kind < 0 ? "Heard: nothing yet" : unsure ? "Heard: unsure" : String.format(Locale.US, "Heard: %s %.2f", NAMES[kind], last[kind]);
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
        lastNow = Long.MIN_VALUE;
        for (Window w : new Window[]{room, talk, waterW, birdsW, trafficW}) {
            w.clear();
        }
        gesture = -1;
        unsure = silent = false;
        Arrays.fill(in, false);
        waterAvg = birdsAvg = 0;
    }
}
