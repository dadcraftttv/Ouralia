// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.dadcraftttv.ouralia;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import org.puredata.core.PdBase;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Sound kinds' Android side (Oct 31, step 4c, Dev only: docs/design/sound-kinds.md). With the Diagnostics switch on
 * and the built-in patch playing, it asks the patch for the mic (sk_on), and as each ring segment fills (sk_ready)
 * reads the two before it, runs YAMNet on its own thread and logs "Heard: ..." when the kind changes, plus each
 * minute's cost. The model is only in the Dev build (src/dev YamNet), so the release has no switch and no LiteRT.
 */
final class SoundKindsRunner {
    private static final String TAG = "SoundKinds";
    private static final String PREFS = "sound_kinds", ON = "on";
    private static final String MODEL_CLASS = "io.github.dadcraftttv.ouralia.YamNet";

    private static final Object lock = new Object();
    private static Context app;
    private static Consumer<String> log;
    private static HandlerThread thread;
    private static Handler worker;
    private static SoundKinds.Model model;
    private static SoundKinds kinds;
    private static int seg, ticks, rate;
    private static volatile boolean busy;
    private static int dropped;
    private static final long[] nanos = new long[600];
    private static int judged;

    private SoundKindsRunner() { }

    /** True in the Dev build (the model is there). */
    static boolean available() {
        try {
            Class.forName(MODEL_CLASS);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    static boolean enabled(Context c) {
        return available() && c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(ON, false);
    }

    /** The Diagnostics switch: stored, and started or stopped at once if the built-in patch is playing. */
    static void setEnabled(Context c, boolean on) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(ON, on).apply();
        synchronized (lock) {
            if (app == null) {
                return;
            }
        }
        if (on) {
            start();
        } else {
            stop("switched off");
        }
    }

    /** The built-in patch started playing at {@code sampleRate}: listen if the switch is on. */
    static void attach(Context c, int sampleRate, Consumer<String> logger) {
        Log.i(TAG, "attach: available " + available() + ", switch " + enabled(c));
        synchronized (lock) {
            app = c.getApplicationContext();
            log = logger;
            rate = sampleRate;
        }
        if (enabled(c)) {
            start();
        }
    }

    /** The sound stopped (or a scene took over). */
    static void detach() {
        stop(null);
        synchronized (lock) {
            app = null;
            log = null;
        }
    }

    private static void start() {
        synchronized (lock) {
            if (thread != null || app == null) {
                return;
            }
            thread = new HandlerThread("SoundKinds");
            thread.start();
            worker = new Handler(thread.getLooper());
            ticks = 0;
            Context c = app;
            Consumer<String> l = log;
            Log.i(TAG, "starting at " + rate + " Hz");
            worker.post(() -> {
                try {
                    SoundKinds.Model m = (SoundKinds.Model) Class.forName(MODEL_CLASS)
                            .getConstructor(Context.class).newInstance(c);
                    SoundKinds k = new SoundKinds(asset(c, "sound_kinds/kinds.txt"));
                    int size = PdBase.arraySize("sk_ring");
                    synchronized (lock) {
                        model = m;
                        kinds = k;
                        seg = size / 3;
                        judged = dropped = 0;
                    }
                    PdBase.sendFloat("sk_on", 1);
                    l.accept(String.format(java.util.Locale.US, "Sound kinds: on (YAMNet, ring %d samples at %d Hz)", size, rate));
                } catch (Throwable e) {   // (no model, no LiteRT for this ABI, no ring in the patch)
                    Log.w(TAG, "Sound kinds could not start", e);
                    l.accept("Sound kinds: could not start (" + e.getClass().getSimpleName() + ")");
                }
            });
        }
    }

    private static void stop(String why) {
        HandlerThread t;
        Handler w;
        Consumer<String> l;
        synchronized (lock) {
            if (thread == null) {
                return;
            }
            t = thread;
            w = worker;
            l = log;
            thread = null;
            worker = null;
        }
        PdBase.sendFloat("sk_on", 0);
        w.post(() -> {
            synchronized (lock) {
                if (model != null) {
                    model.close();
                }
                model = null;
                kinds = null;
            }
            t.quitSafely();
        });
        if (why != null && l != null) {
            l.accept("Sound kinds: off (" + why + ")");
        }
    }

    /** The patch started writing segment {@code k}: the two before it are whole (Pd's thread: hand it on). */
    static void ready(int k) {
        Handler w;
        synchronized (lock) {
            w = worker;
            if (w == null || seg <= 0 || ++ticks < 3) {   // (the first two ticks: the ring isn't full yet)
                return;
            }
        }
        if (busy) {   // the last judgement is still running: skip this one
            synchronized (lock) {
                dropped++;
            }
            return;
        }
        busy = true;
        w.post(() -> {
            try {
                judge(k);
            } finally {
                busy = false;
            }
        });
    }

    private static void judge(int k) {
        SoundKinds.Model m;
        SoundKinds ks;
        int s, r;
        Consumer<String> l;
        synchronized (lock) {
            m = model;
            ks = kinds;
            s = seg;
            r = rate;
            l = log;
        }
        if (m == null || ks == null) {
            return;
        }
        float[] x = new float[2 * s];
        PdBase.readArray(x, 0, "sk_ring", ((k + 1) % 3) * s, s);   // the older whole segment (k - 2)
        PdBase.readArray(x, s, "sk_ring", ((k + 2) % 3) * s, s);   // the newer (k - 1)
        float[] wave = SoundKinds.to16k(x, x.length, r);
        if (wave == null) {
            return;
        }
        long t0 = System.nanoTime();
        float[] scores = m.scores(wave);
        long took = System.nanoTime() - t0;
        boolean changed;
        String line;
        synchronized (lock) {
            if (judged < nanos.length) {
                nanos[judged++] = took;
            }
            changed = ks.judge(scores);
            line = ks.heardLine();
        }
        if (changed && l != null) {
            l.accept(line);
        }
    }

    /** True while it listens. */
    static boolean on() {
        synchronized (lock) {
            return kinds != null;
        }
    }

    /** What it hears now (an index in SoundKinds.NAMES, -1: nothing yet or off) and its score. */
    static int kind() {
        synchronized (lock) {
            return kinds != null ? kinds.kind() : -1;
        }
    }

    static float score() {
        synchronized (lock) {
            return kinds != null && kinds.kind() >= 0 ? kinds.scores()[kinds.kind()] : 0;
        }
    }

    /** The minute's cost line (then counting starts again). */
    static String minuteLine() {
        synchronized (lock) {
            String s = SoundKinds.costLine(nanos, judged) + (dropped > 0 ? ", " + dropped + " skipped (busy)" : "");
            judged = dropped = 0;
            return s;
        }
    }

    private static String asset(Context c, String name) throws IOException {
        try (InputStream in = c.getAssets().open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            for (int n; (n = in.read(b)) > 0; ) {
                out.write(b, 0, n);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }
}
