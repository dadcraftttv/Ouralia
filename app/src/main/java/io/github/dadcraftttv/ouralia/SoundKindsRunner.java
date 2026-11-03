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
    private static volatile boolean roomOn, gapsOn, waterOn;
    private static boolean waterSent;
    private static volatile int uwStyle;
    private static int uwSent;
    private static volatile boolean birdsOn;
    private static boolean birdsSent;
    private static volatile boolean trafficOn, flowOn;
    private static int trafficSent;
    static final String TRAFFIC_ID = "traffic_pass", FLOW_ID = "traffic_flow";

    static boolean flowEnabled() {
        return available() && ParamStore.get(FLOW_ID) >= 0.5f;
    }

    static boolean trafficEnabled() {
        return available() && ParamStore.get(TRAFFIC_ID) >= 0.5f;
    }

    /** Both traffic states in one: pass + 4 * flow, each 0 off/reset, 1 listening, 2 permitted (pass) or settled
     *  scene (flow), 3 settled talk. -1 when neither changed. Read under lock, send outside it. */
    private static int trafficTick() {
        boolean live = thread != null && ParamStore.get("music_on") >= 0.5f && kinds != null;
        int pass = live && trafficOn ? (kinds.talking() ? 3 : kinds.trafficAllowed() ? 2 : 1) : 0;
        int flow = live && flowOn ? (kinds.talking() ? 3 : kinds.trafficScene() ? 2 : 1) : 0;
        int next = pass + 4 * flow;
        if (next == trafficSent) {
            return -1;
        }
        trafficSent = next;
        return next;
    }

    private static void sendTraffic(int state) {
        if (state >= 0) {
            PdBase.sendFloat("traffic_state", state % 4);
            PdBase.sendFloat("traffic_flow_state", state / 4);
        }
    }

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

    /** Mind the Gap (Nov 1, step 6): while people talk the chimes hold still and answer in the pauses. */
    static final String GAPS_ID = "voice_gaps";

    static boolean gapsEnabled() {
        return available() && ParamStore.get(GAPS_ID) >= 0.5f;
    }

    static final String WATER_ID = "water_trail";

    static boolean waterEnabled() {
        return available() && ParamStore.get(WATER_ID) >= 0.5f;
    }

    /** Called with lock held: the value to send if it changed (Music off and no ears too), else -1. The caller
     *  sends it with {@link #sendWater} after letting go of the lock (Pd's callbacks take the lock: ready()). */
    private static int waterTick() {
        boolean next = thread != null && waterOn && ParamStore.get("music_on") >= 0.5f
                && kinds != null && kinds.water();
        if (next == waterSent) {
            return -1;
        }
        waterSent = next;
        return next ? 1 : 0;
    }

    private static void sendWater(int w) {
        if (w >= 0) {
            PdBase.sendFloat("water_trail", w);
        }
    }

    /** Underwater (DadCraft, Nov 2: "can we build both?"): 0 Off, 1 Gentle, 2 Deeper. While settled water is heard
     *  the music and beat (never the mic) turn muffled and wobble; its own setting, independent of the trail. */
    static final String UW_ID = "underwater";

    static int uwEnabled() {
        return available() ? Math.max(0, Math.min(2, Math.round(ParamStore.get(UW_ID)))) : 0;
    }

    /** Called with lock held, like {@link #waterTick}: the style to send if it changed (0 = water over, Music off,
     *  no ears or the setting off), else -1. Sent with {@link #sendUw} after letting go of the lock. */
    private static int uwTick() {
        int next = thread != null && uwStyle > 0 && ParamStore.get("music_on") >= 0.5f
                && kinds != null && kinds.water() ? uwStyle : 0;
        if (next == uwSent) {
            return -1;
        }
        uwSent = next;
        return next;
    }

    private static void sendUw(int u) {
        if (u >= 0) {
            PdBase.sendFloat("underwater", u);
        }
    }

    /** Birds Lift the Chimes (DadCraft, Nov 2: "do birds lift the Chimes"): while settled birds are heard, chimes
     *  already earned turn to high Glass. Birds alone play nothing. Its own setting, like the water ones. */
    static final String BIRDS_ID = "birds_lift";

    static boolean birdsEnabled() {
        return available() && ParamStore.get(BIRDS_ID) >= 0.5f;
    }

    /** Called with lock held, like {@link #waterTick}: the value to send if it changed, else -1. */
    private static int birdsTick() {
        boolean next = thread != null && birdsOn && ParamStore.get("music_on") >= 0.5f
                && kinds != null && kinds.birds();
        if (next == birdsSent) {
            return -1;
        }
        birdsSent = next;
        return next ? 1 : 0;
    }

    private static void sendBirds(int b) {
        if (b >= 0) {
            PdBase.sendFloat("birds_lift", b);
        }
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

    /** Called each tick of the sound's loop: Make Room for Music, Mind the Gap or Water Leaves a Trail changed (row, preset, reset):
     *  listen or not, and tell the patch at once (only stepping back or holding if music or talk is heard now). */
    static void settingsTick() {
        boolean room = roomEnabled(), gaps = gapsEnabled(), water = waterEnabled(), birds = birdsEnabled(), traffic = trafficEnabled(),
                flow = flowEnabled();
        int uw = uwEnabled();
        boolean music, talk, listen, same;
        int w, u, b, tr;
        synchronized (lock) {
            boolean trafficChanged = trafficOn != traffic || flowOn != flow;
            trafficOn = traffic;
            flowOn = flow;
            boolean waterChanged = waterOn != water, uwChanged = (uwStyle > 0) != (uw > 0), birdsChanged = birdsOn != birds;
            waterOn = water;
            uwStyle = uw;
            birdsOn = birds;
            w = waterTick();
            u = uwTick();
            b = birdsTick();
            tr = trafficTick();
            same = (room == roomOn && gaps == gapsOn && !waterChanged && !uwChanged && !birdsChanged && !trafficChanged) || app == null;
            if (same) {
                music = talk = listen = false;
            } else {
                roomOn = room;
                gapsOn = gaps;
                music = kinds != null && kinds.roomMusic();
                talk = kinds != null && kinds.talking();
                listen = room || gaps || water || uw > 0 || birds || traffic || flow || enabled(app);
            }
        }
        sendWater(w);
        sendUw(u);
        sendBirds(b);
        sendTraffic(tr);
        if (same) {
            return;
        }
        PdBase.sendFloat("room_music", room && music ? 1 : 0);
        PdBase.sendFloat("voice_talk", gaps && talk ? 1 : 0);
        if (listen) {
            start();
        } else {
            stop("Make Room for Music, Mind the Gap, Water Leaves a Trail, Underwater, Birds Lift the Chimes and the traffic switches off");
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
        } else if (!roomEnabled() && !gapsEnabled() && !waterEnabled() && uwEnabled() == 0 && !birdsEnabled() && !trafficEnabled()
                && !flowEnabled()) {   // (any of the Music sound-kind settings keeps listening)
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
            gapsOn = gapsEnabled();
            waterOn = waterEnabled();
            uwStyle = uwEnabled();
            birdsOn = birdsEnabled();
            trafficOn = trafficEnabled();
            flowOn = flowEnabled();
        }
        if (enabled(c) || roomOn || gapsOn || waterOn || uwStyle > 0 || birdsOn || trafficOn || flowOn) {
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
            seg = 0;
            HandlerThread owner = thread;
            Context c = app;
            Consumer<String> l = log;
            Log.i(TAG, "starting at " + rate + " Hz");
            worker.post(() -> {
                try {
                    SoundKinds k = new SoundKinds(asset(c, "sound_kinds/kinds.txt"));
                    int size = PdBase.arraySize("sk_ring");
                    if (size < SoundKinds.SEGS) {
                        throw new IOException("sound kinds ring unavailable");
                    }
                    SoundKinds.Model m = (SoundKinds.Model) Class.forName(MODEL_CLASS)
                            .getConstructor(Context.class).newInstance(c);
                    synchronized (lock) {
                        if (thread != owner) {
                            m.close();
                            return;
                        }
                        model = m;
                        kinds = k;
                        seg = size / SoundKinds.SEGS;
                        judged = dropped = 0;
                    }
                    int traffic;
                    synchronized (lock) {
                        traffic = trafficTick();
                    }
                    sendTraffic(traffic);
                    PdBase.sendFloat("sk_on", 1);
                    l.accept(String.format(java.util.Locale.US, "Sound kinds: on (YAMNet, ring %d samples at %d Hz)", size, rate));
                } catch (Throwable e) {   // (no model, no LiteRT for this ABI, no ring in the patch)
                    synchronized (lock) {
                        if (thread != owner) {
                            return;
                        }
                    }
                    stop(null);
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
        int water, uw, birds, traffic;
        SoundKinds.Model stoppedModel;
        synchronized (lock) {
            if (thread == null) {
                return;
            }
            t = thread;
            w = worker;
            l = log;
            thread = null;
            worker = null;
            stoppedModel = model;
            model = null;
            if (kinds != null) {
                kinds.reset();
            }
            kinds = null;
            seg = 0;
            water = waterTick();
            uw = uwTick();
            birds = birdsTick();
            traffic = trafficTick();
        }
        sendWater(water);
        sendUw(uw);
        sendBirds(birds);
        sendTraffic(traffic);
        PdBase.sendFloat("sk_on", 0);
        PdBase.sendFloat("room_music", 0);   // (no ears: the music comes back)
        PdBase.sendFloat("voice_talk", 0);
        w.post(() -> {
            if (stoppedModel != null) {
                stoppedModel.close();
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
                judge(k, w);
            } finally {
                busy = false;
            }
        });
    }

    private static void judge(int k, Handler owner) {
        SoundKinds.Model m;
        SoundKinds ks;
        int s, r;
        Consumer<String> l;
        synchronized (lock) {
            if (worker != owner) {
                return;
            }
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
        boolean changed, roomChanged, room, talkChanged, talk;
        int water, uw, birds, traffic;
        String line;
        synchronized (lock) {
            if (worker != owner || ks != kinds) {
                return;
            }
            if (judged < nanos.length) {
                nanos[judged++] = took;
            }
            boolean wasRoom = ks.roomMusic(), wasTalk = ks.talking();
            changed = ks.judge(scores);
            line = ks.heardLine();
            room = ks.roomMusic();
            roomChanged = room != wasRoom;
            talk = ks.talking();
            talkChanged = talk != wasTalk;
            water = waterTick();
            uw = uwTick();
            birds = birdsTick();
            traffic = trafficTick();
        }
        sendWater(water);
        sendUw(uw);
        sendBirds(birds);
        sendTraffic(traffic);
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
        if (talkChanged) {
            boolean gaps = gapsOn;
            if (gaps) {
                PdBase.sendFloat("voice_talk", talk ? 1 : 0);
            }
            if (l != null) {
                l.accept(talk ? "Talk: heard" + (gaps ? ", the chimes wait for the gaps" : " (Mind the Gap off)")
                        : "Talk: over" + (gaps ? ", the chimes play again" : ""));
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
