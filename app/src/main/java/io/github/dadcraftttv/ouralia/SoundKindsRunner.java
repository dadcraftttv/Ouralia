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
 * Sound kinds' Android side (Oct 31, step 4c: docs/design/sound-kinds.md). With the Diagnostics switch on
 * and the built-in patch playing, it asks the patch for the mic (sk_on), and as each ring segment fills (sk_ready)
 * reads the two before it, runs YAMNet on its own thread and logs "Heard: ..." when the kind changes, plus each
 * minute's cost. The model (YamNet, LiteRT) is in every phone build since Nov 5, ARM only (available()).
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
    private static Handler sends;   // (one thread for every Pd send, in the order the state was computed)
    private static SoundKinds.Model model;
    private static Layer layer;   // (Hear Far Sounds, step 4d; null without layer.bin)
    private static SoundKinds kinds;
    private static int seg, ticks, rate;
    private static volatile boolean busy;
    private static int dropped;
    private static final long[] nanos = new long[600];
    private static final long[] wholeNanos = new long[600];
    private static final long[] copyNanos = new long[600];    // (of which the ring copy, Pd's lock included)   // (the whole judgement: ring copy, resampling, YAMNet)
    /** Each worker thread's own ring copy and 16 kHz buffer, reused (review, Nov 4: ~1 MB a second was allocated).
     *  Per thread, since an old worker can still be finishing a judgement after a restart. */
    private static final ThreadLocal<float[]> ringCopy = new ThreadLocal<>();
    private static final ThreadLocal<SoundKinds.Resampler> resampler = ThreadLocal.withInitial(SoundKinds.Resampler::new);
    private static int judged;
    private static volatile boolean roomOn, gapsOn, waterOn;
    private static boolean musicOn;   // (Music's switch as last seen: the listener follows it)
    private static boolean waterSent;
    private static volatile int uwStyle;
    private static int uwSent;
    private static volatile boolean birdsOn;
    private static boolean birdsSent;
    private static volatile boolean trafficOn, flowOn;
    private static int trafficSent;
    static final String TRAFFIC_ID = "traffic_pass", FLOW_ID = "traffic_flow";

    static boolean flowEnabled() {
        return mixedEnabled() && ParamStore.get(FLOW_ID) >= 0.5f;
    }

    static boolean trafficEnabled() {
        return mixedEnabled() && ParamStore.get(TRAFFIC_ID) >= 0.5f;
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

    /** Call with the lock held: queue Pd sends behind every earlier state change (review finding 2, Nov 4). A stop's
     *  neutral state can no longer be overtaken by an older judgement or startup that read its state before the stop.
     *  The sends themselves run on their own thread, outside the lock: Pd's thread calls {@link #ready} holding Pd's
     *  lock, so sending under ours could deadlock. */
    private static void queue(Runnable r) {
        if (sends == null) {
            HandlerThread t = new HandlerThread("SoundKindsPd");
            t.start();
            sends = new Handler(t.getLooper());
        }
        sends.post(r);
    }

    private static volatile Boolean avail;

    /** True where the model can run: the class is there (every phone build since Nov 5, not the desktop) and the phone
     *  is ARM (LiteRT ships only ARM libraries here, DadCraft Oct 31: "ARM only is fine"). Off elsewhere, the sound-kind
     *  settings stay hidden. */
    static boolean available() {
        Boolean a = avail;
        if (a == null) {
            String[] abis = android.os.Build.SUPPORTED_ABIS;
            boolean arm = abis != null && abis.length > 0 && abis[0] != null && abis[0].startsWith("arm");
            boolean found;
            try {
                Class.forName(MODEL_CLASS);
                found = true;
            } catch (ClassNotFoundException e) {
                found = false;
            }
            avail = a = arm && found;
        }
        return a;
    }

    static boolean enabled(Context c) {
        return available() && c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(ON, false);
    }

    /** Make Room for Music (Nov 1): the music steps back while sound kinds hears music in the room. The Music
     *  setting {@link #ROOM_ID} since Nov 1 (was a Dev switch in Diagnostics). */
    static final String ROOM_ID = "room_make";

    static boolean musicPlaying() {
        return ParamStore.get("music_on") >= 0.5f;
    }

    /** Listen or not (DadCraft, Nov 4: "stop listening while those things that don't need it aren't on"): every
     *  sound-kind setting acts on the music, so they listen only while Music is on; the Diagnostics switch always. */
    private static boolean wanted(Context c, boolean room, boolean gaps, boolean water, int uw, boolean birds,
                                  boolean traffic, boolean flow) {
        return (musicPlaying() && (room || gaps || water || uw > 0 || birds || traffic || flow)) || (c != null && enabled(c));
    }

    static boolean roomEnabled() {
        return mixedEnabled() && ParamStore.get(ROOM_ID) >= 0.5f;
    }

    /** Mind the Gap (Nov 1, step 6): while people talk the chimes hold still and answer in the pauses. */
    static final String GAPS_ID = "voice_gaps";

    static boolean gapsEnabled() {
        return mixedEnabled() && ParamStore.get(GAPS_ID) >= 0.5f;
    }

    static final String WATER_ID = "water_trail";
    /** Mixed Surroundings (Nov 4): several kinds at once each count (SoundKinds.mixed). */
    static final String MIXED_ID = "sk_mixed";

    /** The one switch (DadCraft, Nov 5): the seven sound-kind responses act only while Mixed Surroundings is on, each
     *  still with its own sub-switch (on by default). */
    static boolean mixedEnabled() {
        return available() && ParamStore.get(MIXED_ID) >= 0.5f;
    }

    /** Hear Far Sounds (accuracy step 4d, Nov 6; SoundKinds.layerOn): under Mixed Surroundings, off by default. */
    static final String LAYER_ID = "sk_layer";

    static boolean layerEnabled() {
        return mixedEnabled() && ParamStore.get(LAYER_ID) >= 0.5f;
    }
    /** Each judgement's seven scores, whether YAMNet heard silence, the layer's probabilities (null: no layer) and
     *  YAMNet's own 521 scores go here (the service: SessionLog.kinds), so real mixes can be replayed
     *  (tools/replay_kinds.py). */
    interface KindsSink {
        void accept(float[] scores, boolean silent, float[] layer, float[] yamnet);
    }

    static volatile KindsSink scoresSink;
    /** Monotonic milliseconds for the judgements (the scene windows are measured in time); the tests step it. */
    static volatile java.util.function.LongSupplier clock = () -> System.nanoTime() / 1_000_000;

    static boolean waterEnabled() {
        return mixedEnabled() && ParamStore.get(WATER_ID) >= 0.5f;
    }

    /** Called with lock held: the value to send if it changed (Music off and no ears too), else -1. The caller
     *  sends it with {@link #sendWater} after letting go of the lock (Pd's callbacks take the lock: ready()). */
    private static int waterTick() {
        boolean next = thread != null && waterOn && ParamStore.get("music_on") >= 0.5f
                && kinds != null && kinds.waterLeads();
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

    /** Underwater (DadCraft, Nov 2; one style since Nov 5, the old Deeper): 0 off, 1 on. While settled water is heard
     *  the music and beat (never the mic) turn muffled and wobble; its own setting, independent of the trail. */
    static final String UW_ID = "underwater";

    static int uwEnabled() {   // ("go ahead and remove the gentle style", Nov 5: the patch has only the old Deeper)
        return mixedEnabled() && ParamStore.get(UW_ID) >= 0.5f ? 1 : 0;
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
        return mixedEnabled() && ParamStore.get(BIRDS_ID) >= 0.5f;
    }

    /** Called with lock held, like {@link #waterTick}: the value to send if it changed, else -1. */
    private static int birdsTick() {
        boolean next = thread != null && birdsOn && ParamStore.get("music_on") >= 0.5f
                && kinds != null && kinds.birdsLead();
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
        SoundKinds.mixed = mixedEnabled();
        SoundKinds.layerOn = layerEnabled();
        boolean room = roomEnabled(), gaps = gapsEnabled(), water = waterEnabled(), birds = birdsEnabled(), traffic = trafficEnabled(),
                flow = flowEnabled();
        int uw = uwEnabled();
        boolean music, talk, listen, same;
        int w, u, b, tr;
        synchronized (lock) {
            boolean trafficChanged = trafficOn != traffic || flowOn != flow, mus = musicPlaying(), musicChanged = mus != musicOn;
            musicOn = mus;
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
            same = (room == roomOn && gaps == gapsOn && !waterChanged && !uwChanged && !birdsChanged && !trafficChanged && !musicChanged) || app == null;
            if (same) {
                music = talk = listen = false;
            } else {
                roomOn = room;
                gapsOn = gaps;
                music = kinds != null && kinds.roomMusic();
                talk = kinds != null && kinds.talking();
                listen = wanted(app, room, gaps, water, uw, birds, traffic, flow);
            }
            boolean all = !same, rm = room && music, vt = gaps && talk;
            queue(() -> {
                sendWater(w);
                sendUw(u);
                sendBirds(b);
                sendTraffic(tr);
                if (all) {
                    PdBase.sendFloat("room_music", rm ? 1 : 0);
                    PdBase.sendFloat("voice_talk", vt ? 1 : 0);
                }
            });
        }
        if (same) {
            return;
        }
        if (listen) {
            start();
        } else {
            stop(musicPlaying() ? "Make Room for Music, Mind the Gap, Water Leaves a Trail, Underwater, Birds Lift the Chimes and the traffic switches off"
                    : "Music off");
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
        } else if (!wanted(null, roomEnabled(), gapsEnabled(), waterEnabled(), uwEnabled(), birdsEnabled(), trafficEnabled(),
                flowEnabled())) {   // (any of the Music sound-kind settings keeps listening, while Music is on)
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
            musicOn = musicPlaying();
        }
        if (wanted(c, roomOn, gapsOn, waterOn, uwStyle, birdsOn, trafficOn, flowOn)) {
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
                    Layer ly = null;
                    try (java.io.InputStream in = c.getAssets().open("sound_kinds/layer.bin")) {
                        ly = Layer.read(in);
                        k.layerCut = ly.cutoff;
                    } catch (IOException e) {   // (sound kinds still run without it, as before 4d)
                        Log.w(TAG, "no layer", e);
                    }
                    synchronized (lock) {
                        if (thread != owner) {
                            m.close();
                            return;
                        }
                        model = m;
                        layer = ly;
                        kinds = k;
                        seg = size / SoundKinds.SEGS;
                        judged = dropped = 0;
                        int traffic = trafficTick();
                        queue(() -> {
                            sendTraffic(traffic);
                            PdBase.sendFloat("sk_on", 1);
                        });
                    }
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
            layer = null;
            if (kinds != null) {
                kinds.reset();
            }
            kinds = null;
            seg = 0;
            water = waterTick();
            uw = uwTick();
            birds = birdsTick();
            traffic = trafficTick();
            queue(() -> {
                sendWater(water);
                sendUw(uw);
                sendBirds(birds);
                sendTraffic(traffic);
                PdBase.sendFloat("sk_on", 0);
                PdBase.sendFloat("room_music", 0);   // (no ears: the music comes back)
                PdBase.sendFloat("voice_talk", 0);
            });
        }
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

    /** Copies the SEGS - 1 whole segments of {@code s} samples, oldest (k + 1) to newest (k - 1), into {@code x}: at
     *  most two runs of the ring, so at most two takes of Pd's lock (four until Nov 4; the audio thread holds that
     *  lock while it computes each block). */
    static void readRing(float[] x, int k, int s) {
        int n = SoundKinds.SEGS;
        int first = (k + 1) % n, head = Math.min(n - 1, n - first);
        PdBase.readArray(x, 0, "sk_ring", first * s, head * s);
        if (head < n - 1) {
            PdBase.readArray(x, head * s, "sk_ring", 0, (n - 1 - head) * s);
        }
    }

    private static void judge(int k, Handler owner) {
        SoundKinds.Model m;
        Layer ly;
        SoundKinds ks;
        int s, r;
        Consumer<String> l;
        synchronized (lock) {
            if (worker != owner) {
                return;
            }
            m = model;
            ly = layer;
            ks = kinds;
            s = seg;
            r = rate;
            l = log;
        }
        if (m == null || ks == null) {
            return;
        }
        long start = System.nanoTime();
        int n = SoundKinds.SEGS;
        float[] x = ringCopy.get();
        if (x == null || x.length != (n - 1) * s) {
            x = new float[(n - 1) * s];
            ringCopy.set(x);
        }
        readRing(x, k, s);
        long copied = System.nanoTime() - start;
        float[] wave = resampler.get().next(x, x.length, r, k, s);
        if (wave == null) {
            return;
        }
        long t0 = System.nanoTime();
        float[] scores = m.scores(wave);
        float[] emb = ly != null ? m.embedding() : null;
        float[] lp = emb != null ? ly.probs(emb) : null;   // (Hear Far Sounds' layer: ~7k multiply-adds)
        long took = System.nanoTime() - t0, whole = System.nanoTime() - start;
        boolean changed, roomChanged, room, talkChanged, talk, step, gaps;
        int water, uw, birds, traffic;
        String line;
        synchronized (lock) {
            if (worker != owner || ks != kinds) {
                return;
            }
            if (judged < nanos.length) {
                copyNanos[judged] = copied;
                wholeNanos[judged] = whole;
                nanos[judged++] = took;
            }
            boolean wasRoom = ks.roomMusic(), wasTalk = ks.talking();
            changed = ks.judge(scores, lp, clock.getAsLong());   // (real time: the windows are measured in it)
            KindsSink sink = scoresSink;
            if (sink != null) sink.accept(ks.scores(), ks.silent(), ks.layerScores(), scores);   // (real mixes to replay)
            line = ks.heardLine();
            room = ks.roomMusic();
            roomChanged = room != wasRoom;
            talk = ks.talking();
            talkChanged = talk != wasTalk;
            water = waterTick();
            uw = uwTick();
            birds = birdsTick();
            traffic = trafficTick();
            step = roomOn;
            gaps = gapsOn;
            boolean sendRoom = roomChanged && step, sendTalk = talkChanged && gaps;
            queue(() -> {
                sendWater(water);
                sendUw(uw);
                sendBirds(birds);
                sendTraffic(traffic);
                if (sendRoom) {
                    PdBase.sendFloat("room_music", room ? 1 : 0);
                }
                if (sendTalk) {
                    PdBase.sendFloat("voice_talk", talk ? 1 : 0);
                }
            });
        }
        if (changed && l != null) {
            l.accept(line);
        }
        if (roomChanged) {
            if (l != null) {
                l.accept(room ? "Room music: heard" + (step ? ", the music steps back" : " (Make Room for Music off)")
                        : "Room music: gone" + (step ? ", the music comes back" : ""));
            }
        }
        if (talkChanged) {
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
            return kinds != null ? kinds.score() : 0;
        }
    }

    /** The minute's cost line (then counting starts again). */
    static String minuteLine() {
        synchronized (lock) {
            String s = SoundKinds.costLine(nanos, wholeNanos, judged)
                    + SoundKinds.medianLine("ring copy", copyNanos, judged) + (dropped > 0 ? ", " + dropped + " skipped (busy)" : "");
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
