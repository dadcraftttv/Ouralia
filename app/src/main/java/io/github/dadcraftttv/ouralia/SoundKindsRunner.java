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
    private static final String PREFS = "sound_kinds", ON = "on", ROOM = "room";
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
    private static volatile boolean roomOn;

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

    /** Make Room for Music (Nov 1): the music steps back while sound kinds hears music in the room. The Music
     *  setting {@link #ROOM_ID} since Nov 1 (was a Dev switch in Diagnostics). */
    static final String ROOM_ID = "room_make";

    static boolean roomEnabled() {
        return available() && ParamStore.get(ROOM_ID) >= 0.5f;
    }

    /** The old Diagnostics switch's stored value moves to the setting, once (stored values are kept). */
    static void carryRoomSwitch(Context c) {
        android.content.SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (p.contains(ROOM)) {
            ParamStore.set(ROOM_ID, p.getBoolean(ROOM, false) ? 1f : 0f);
            ParamStore.save(c);
            p.edit().remove(ROOM).apply();
        }
    }

    /** Called each tick of the sound's loop: the setting changed (row, preset, reset): listen or not, and tell the
     *  patch at once (only stepping back if music is heard now). */
    static void roomTick() {
        boolean on = roomEnabled();
        boolean now, listen;
        synchronized (lock) {
            if (on == roomOn || app == null) {
                return;
            }
            roomOn = on;
            now = kinds != null && kinds.roomMusic();
            listen = on || enabled(app);
        }
        PdBase.sendFloat("room_music", on && now ? 1 : 0);
        if (listen) {
            start();
        } else {
            stop("Make Room for Music off");
        }
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
        } else if (!roomEnabled()) {   // (Make Room for Music on keeps listening)
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
            roomOn = roomEnabled();
        }
        if (enabled(c) || roomOn) {
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
                        seg = size / SoundKinds.SEGS;
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
        PdBase.sendFloat("room_music", 0);   // (no ears: the music comes back)
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

    /** The patch started writing segment {@code k}: the SEGS - 1 before it are whole (Pd's thread: hand it on). */
    static void ready(int k) {
        Handler w;
        synchronized (lock) {
            w = worker;
            if (w == null || seg <= 0 || ++ticks < SoundKinds.SEGS) {   // (until the ring is full)
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
        int n = SoundKinds.SEGS;
        float[] x = new float[(n - 1) * s];
        for (int j = 1; j < n; j++) {   // the whole segments, oldest (k + 1, i.e. k - n + 1) to newest (k - 1)
            PdBase.readArray(x, (j - 1) * s, "sk_ring", ((k + j) % n) * s, s);
        }
        float[] wave = SoundKinds.to16k(x, x.length, r);
        if (wave == null) {
            return;
        }
        long t0 = System.nanoTime();
        float[] scores = m.scores(wave);
        long took = System.nanoTime() - t0;
        boolean changed, roomChanged, room;
        String line;
        synchronized (lock) {
            if (judged < nanos.length) {
                nanos[judged++] = took;
            }
            boolean wasRoom = ks.roomMusic();
            changed = ks.judge(scores);
            line = ks.heardLine();
            room = ks.roomMusic();
            roomChanged = room != wasRoom;
        }
        if (changed && l != null) {
            l.accept(line);
        }
        if (roomChanged) {
            boolean step = roomOn;
            if (step) {
                PdBase.sendFloat("room_music", room ? 1 : 0);
            }
            if (l != null) {
                l.accept(room ? "Room music: heard" + (step ? ", the music steps back" : " (Make Room for Music off)")
                        : "Room music: gone" + (step ? ", the music comes back" : ""));
            }
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
