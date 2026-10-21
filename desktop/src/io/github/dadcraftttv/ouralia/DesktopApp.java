// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.dadcraftttv.ouralia;

import android.content.Context;
import android.content.SharedPreferences;

import org.puredata.core.PdBase;
import org.puredata.core.PdReceiver;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * The desktop version (stream mode): the phone app's own logic (settings, presets, movers, Realms, Intensity, Calm
 * builds, motifs, call and response) driving the built-in patch in {@link DesktopEngine}, with a window
 * ({@link DesktopWindow}). There are no sensors: walking, pockets, light and shaking never happen here, so the
 * beat keeps the tempo you set and the Realms follow the room's loudness and the time of day.
 *
 * <p>Settings live in ~/.config/ouralia (the phone's own stores, as JSON files), saved presets in
 * ~/.local/share/ouralia/presets, in the phone's preset format, so files move between the two.
 *
 * <pre>
 * --patch FILE      the built-in patch (run.sh passes it)
 * --no-window       just the sound (typed lines change settings: "mic 0.5", "wet 0.3")
 * --seconds N       stop after N seconds (for tests)
 * </pre>
 */
final class DesktopApp {

    /**
     * The phone's defaults that don't suit a desktop mic, set on the first start (and by Reset everything). The
     * compressor (-30 dB, 4:1) evens out a phone mic, but a desktop mic usually arrives levelled already, and
     * squeezing it again sounded overdriven (DadCraft, Oct 16: the AudioBox directly with the compressor off "sounds
     * amazing"). The beat keeps a set tempo: there are no steps here.
     */
    static final Map<String, Float> DESKTOP_DEFAULTS = Map.of("comp_thresh", 0f, "tempo_mode", 1f);

    static final String DESKTOP_PREFS = "ouralia_desktop";
    private static final long TICK_MS = 33;                       // the phone's mover tick
    private static final int[] SCALE_SIZES = {7, 7, 5, 5, 7, 7, 7, 6};   // the patch's scales, by music_scale

    static Context context;
    static DesktopEngine engine;
    static final ScheduledExecutorService clock = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "tick");
        t.setDaemon(true);
        return t;
    });

    private static final MotifMemory motif = new MotifMemory(new Random());
    private static final CallResponse callResponse = new CallResponse(new Random());
    private static volatile float tempoBpm;
    private static String worldShown;
    private static float calmChimesSent;
    private static int minuteOfDay = Worlds.minuteOfDayNow();
    private static long lastTick = System.nanoTime(), lastClock, lastKitCheck;
    private static String kitSignature;
    /** Our own Pd objects loaded (Oct 19): the patch's [dcs_loaded] sends dcs_ok as it opens. Without them nothing
     *  plays (the effects are dcs objects in every sound's path), so the window says so instead of staying silent. */
    private static volatile boolean dcsOk;
    private static volatile long dcsCheckAt;
    static final String DCS_MISSING = "Our own Pd objects (dcs) didn't load, so nothing will play. Run make -C dcs and "
            + "start again. If that doesn't help, send DadCraft the log.";
    private static Consumer<String> say = System.out::println;

    private DesktopApp() { }

    /** Our own Pd objects (dcs/, `[declare -lib dcs]`) are a library Pd opens itself, and it needs libpd's functions:
     *  Java opens libpdnative.so privately, so it is opened here first with its symbols shared (RTLD_GLOBAL). */
    /** Held for good (Oct 19): JNA closes a library whose NativeLibrary is garbage collected, and then our Pd objects
     *  (dcs) couldn't find libpd's symbols. The window's slower start let a collection run first, so the window app
     *  played without any of them (only the plain Pd parts: the Warp sound), while --no-window worked. */
    private static com.sun.jna.NativeLibrary libpdShared;

    static void shareLibpd() {
        for (String dir : System.getProperty("java.library.path", "").split(File.pathSeparator)) {
            File f = new File(dir, "libpdnative.so");
            if (f.isFile()) {
                libpdShared = com.sun.jna.NativeLibrary.getInstance(f.getAbsolutePath(),
                        Map.of(com.sun.jna.Library.OPTION_OPEN_FLAGS, 0x102));   // RTLD_NOW | RTLD_GLOBAL
                return;
            }
        }
    }

    public static void main(String[] args) throws Exception {
        shareLibpd();
        String patch = null;
        boolean window = true;
        double seconds = -1;
        String snapshot = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--patch": patch = args[++i]; break;
                case "--no-window": window = false; break;
                case "--seconds": seconds = Double.parseDouble(args[++i]); break;
                case "--snapshot": snapshot = args[++i]; break;   // (tests: each tab drawn to a png)
                default: throw new IllegalArgumentException("Unknown option " + args[i]);
            }
        }
        File home = new File(System.getProperty("user.home"));
        String xdgConfig = System.getenv("XDG_CONFIG_HOME"), xdgData = System.getenv("XDG_DATA_HOME");
        File configBase = xdgConfig != null && !xdgConfig.isEmpty() ? new File(xdgConfig) : new File(home, ".config");
        File dataBase = xdgData != null && !xdgData.isEmpty() ? new File(xdgData) : new File(home, ".local/share");
        File config = new File(configBase, "ouralia"), data = new File(dataBase, "ouralia");
        Context.moveFromWorkingName(new File(configBase, "pdscenes"), config);
        Context.moveFromWorkingName(new File(dataBase, "pdscenes"), data);
        boolean firstStart = !new File(config, "ouralia_state.json").exists();
        context = new Context(data, config);
        ParamStore.loadOnce(context);
        Worlds.load(context);
        if (firstStart) {
            applyDesktopDefaults();
        }

        // the first start listens to nothing until a mic is picked: the system's default input can be anything
        // (on DadCraft's laptop the built-in mic reads full scale all the time)
        boolean pickMic = !desktopPrefs().contains("mic");
        if (pickMic && window) {
            DesktopEngine.Setup s = setup();
            s.mic = "none";
            saveSetup(s);
        }
        DesktopWindow win = window ? DesktopWindow.open() : null;
        if (win != null) {
            say = win::say;
        }
        engine = new DesktopEngine(m -> say.accept(m));
        PdBase.setReceiver(new Receiver());
        for (String s : new String[]{"mic_level", "chime_note", "heard_deg", "heard_off", "tempo", "dcs_ok"}) {
            PdBase.subscribe(s);
        }
        if (win != null) {
            engine.openStopped();   // (like the phone: nothing is heard or played until Start)
        }
        try {
            dcsOk = false;
            engine.start(patch, setup());
            dcsCheckAt = System.nanoTime() + 1_000_000_000L;
        } catch (IOException e) {
            if (win != null) {
                win.fatal(e.getMessage());
                return;
            }
            System.err.println(e.getMessage());
            System.exit(2);
        }
        PdBase.computeAudio(true);
        started = engine.running();
        File kits = new File(new File(patch).getAbsoluteFile().getParentFile().getParentFile(), "kits");   // (assets/kits)
        Kits.source = name -> {
            try {
                return new String(java.nio.file.Files.readAllBytes(new File(kits, name + ".txt").toPath()),
                        java.nio.charset.StandardCharsets.UTF_8);
            } catch (IOException e) {
                return null;
            }
        };
        Kits.refreshChoices();   // (the part menus say which kits lack a part: they need the kits' text)
        freshStart();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            ParamStore.save(context);
            engine.stop();
        }));
        clock.scheduleAtFixedRate(DesktopApp::tick, TICK_MS, TICK_MS, TimeUnit.MILLISECONDS);
        clock.scheduleAtFixedRate(() -> ParamStore.save(context), 5, 5, TimeUnit.SECONDS);
        if (engine.running()) {
            sayRunning();
        } else {
            say.accept(String.format(Locale.US, "Ready (%d Hz, %d samples a cycle). Press Start to play.",
                    engine.sampleRate(), engine.framesPerCycle()));
        }
        if (win != null) {
            win.started();
            if (pickMic) {
                win.pickMic();
            }
        } else {
            commands();
        }
        if (snapshot != null && win != null) {
            Thread.sleep(1500);
            win.snapshot(snapshot);
        }
        if (seconds >= 0) {
            Thread.sleep((long) (seconds * 1000));
            System.exit(0);
        }
    }

    private static boolean started;   // played since the window opened (a later Start reopens the patch)

    /** What a newly opened patch starts with: the app's memories cleared, every setting and kit sent. */
    private static void freshStart() {
        Modulation.reset();
        motif.reset();
        callResponse.reset();
        Worlds.reset();
        CalmBuild.reset();
        calmChimesSent = 0f;
        worldShown = null;
        ParamStore.sendAllToPd();
    }

    private static void sayRunning() {
        say.accept(String.format(Locale.US, "Running at %d Hz, %d samples a cycle. Mic: %s. Playing to \"Ouralia\"%s.",
                engine.sampleRate(), engine.framesPerCycle(), orWords(engine.connected("mic"), "none"),
                engine.connected("headphones") != null ? " and your headphones" : ""));
    }

    /** The window's Start/Stop button. */
    static void setRunning(boolean run) {
        if (run && started) {   // like the phone: each Start begins afresh (the first one has a patch just opened)
            try {
                dcsOk = false;
                engine.reopenPatch();
                dcsCheckAt = System.nanoTime() + 1_000_000_000L;
            } catch (IOException e) {
                say.accept(e.getMessage());
                return;
            }
            freshStart();
        }
        started |= run;
        engine.setRunning(run);
        if (run) {
            sayRunning();
        } else {
            say.accept("Stopped. Nothing is heard or played until you press Start.");
        }
    }

    /** Sets the desktop's own defaults (first start, Reset everything). */
    static void applyDesktopDefaults() {
        for (Map.Entry<String, Float> d : DESKTOP_DEFAULTS.entrySet()) {
            ParamStore.set(d.getKey(), d.getValue());
        }
    }

    // ---- what to connect, kept between starts ----

    static SharedPreferences desktopPrefs() {
        return context.getSharedPreferences(DESKTOP_PREFS, Context.MODE_PRIVATE);
    }

    static DesktopEngine.Setup setup() {
        SharedPreferences p = desktopPrefs();
        DesktopEngine.Setup s = new DesktopEngine.Setup();
        s.mic = p.getString("mic", null);
        s.game = p.getBoolean("game", false);
        s.gameOutput = p.getString("game_output", null);
        s.listen = p.getBoolean("listen", false);
        s.micLevel = p.getFloat("mic_level", 1f);
        s.gameLevel = p.getFloat("game_level", 1f);
        return s;
    }

    static void saveSetup(DesktopEngine.Setup s) {
        SharedPreferences.Editor e = desktopPrefs().edit();
        if (s.mic == null) {
            e.remove("mic");
        } else {
            e.putString("mic", s.mic);
        }
        if (s.gameOutput == null) {
            e.remove("game_output");
        } else {
            e.putString("game_output", s.gameOutput);
        }
        e.putBoolean("game", s.game).putBoolean("listen", s.listen)
                .putFloat("mic_level", s.micLevel).putFloat("game_level", s.gameLevel).apply();
    }

    static float tempo() {
        return tempoBpm;
    }

    // ---- the tick: the phone's mover tick without the phone (no steps, pocket, handling, light or shaking) ----

    private static void tick() {
        try {
            long now = System.nanoTime();
            double dt = Math.min((now - lastTick) / 1e9, 0.25);
            lastTick = now;
            double nowS = now / 1e9;
            PdBase.pollPdMessageQueue();   // (this libpd queues the patch's messages: mic level, chimes, tempo)
            if (dcsCheckAt != 0 && now - dcsCheckAt > 0) {   // (a second after the patch opened)
                dcsCheckAt = 0;
                if (!dcsOk) {
                    say.accept(DCS_MISSING);
                }
            }
            ParamStore.morphTick(dt);
            Modulation.tick(dt, PdBase::sendFloat);
            String world = Worlds.currentWorld();
            if (world != null ? !world.equals(worldShown) : worldShown != null) {
                float cue = world == null ? 0f : worldShown == null ? 1f : 2f;   // up into a Realm, home, sideways
                worldShown = world;
                PdBase.sendFloat("realm_cue", cue);
            }
            if (now - lastKitCheck > 1_000_000_000L) {   // the kit bench: a kit saved, added or removed plays at once
                lastKitCheck = now;
                String sig = Kits.signature();
                if (!sig.equals(kitSignature)) {
                    boolean first = kitSignature == null;
                    kitSignature = sig;
                    if (!first) {
                        Kits.refreshChoices();
                        ParamStore.sendKit(false);
                        say.accept("Your kits changed: " + Kits.userKits().size() + " in " + Kits.userDir + ".");
                    }
                }
            }
            if (now - lastClock > 30_000_000_000L) {
                minuteOfDay = Worlds.minuteOfDayNow();
                lastClock = now;
            }
            Worlds.tick(dt, Modulation.micLevelDb(), false, false, minuteOfDay, Double.MAX_VALUE);
            boolean calm = ParamStore.get(CalmBuild.ID) >= 0.5f && ParamStore.get("music_on") >= 0.5f
                    && !(Worlds.roomLoud() && Worlds.roomLively());
            if (CalmBuild.tick(dt, calm)) {
                say.accept(CalmBuild.phase() == CalmBuild.Phase.RISING ? "Calm build starts." : "Calm build ends.");
            }
            float chimes = CalmBuild.layer(CalmBuild.CHIMES);
            if (Math.abs(chimes - calmChimesSent) > 0.01f || (chimes == 0f) != (calmChimesSent == 0f)) {
                calmChimesSent = chimes;
                PdBase.sendFloat("calm_chimes", chimes);
            }
            int scaleSize = SCALE_SIZES[Math.max(0, Math.min(SCALE_SIZES.length - 1,
                    Math.round(Modulation.output(Params.byId("music_scale"), ParamStore.get("music_scale")))))];
            float answerLevel = ParamStore.get("music_on") >= 0.5f ? ParamStore.get("music_answer") : 0f;
            play(callResponse.poll(nowS, answerLevel, scaleSize), CallResponse.velocity(answerLevel));
            play(motif.poll(nowS, false), MotifMemory.VELOCITY);
        } catch (RuntimeException e) {
            e.printStackTrace();   // (keep ticking)
        }
    }

    /** A remembered or answering phrase: each note at its time, through the patch's motif path. */
    private static void play(List<MotifMemory.Note> notes, float velocity) {
        if (notes == null) {
            return;
        }
        for (MotifMemory.Note n : notes) {
            clock.schedule(() -> PdBase.sendList("motif", (float) n.degree, velocity), Math.round(n.at * 1000),
                    TimeUnit.MILLISECONDS);
        }
    }

    /** What the patch reports: the mic's loudness, chimes played, sung notes heard, the tempo. */
    private static final class Receiver implements PdReceiver {
        @Override public void receiveFloat(String source, float x) {
            double t = System.nanoTime() / 1e9;
            switch (source) {
                case "mic_level": Modulation.setMicLevelDb(x - 100f); break;   // (Pd's dB: 100 = full scale)
                case "chime_note": motif.onNote(Math.round(x), t); break;
                case "heard_deg": callResponse.onNote(Math.round(x), t); break;
                case "tempo": tempoBpm = x; break;
                case "dcs_ok": dcsOk = true; break;
                default: break;
            }
        }

        @Override public void receiveBang(String source) {
            if ("heard_off".equals(source)) {
                callResponse.onNoteOff(System.nanoTime() / 1e9);
            }
        }

        @Override public void print(String s) {   // (this libpd hands over whole lines, mostly without "\n")
            String l = s.trim();
            if (!l.isEmpty() && !l.contains("version") && !l.startsWith("pd 0.") && !l.startsWith("verbose(")) {
                System.err.println("Pd: " + l);
            }
        }

        @Override public void receiveSymbol(String source, String symbol) { }
        @Override public void receiveList(String source, Object... args) { }
        @Override public void receiveMessage(String source, String symbol, Object... args) { }
    }

    // ---- without a window: typed lines ----

    private static void commands() {
        Thread t = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(System.in))) {
                for (String line; (line = r.readLine()) != null; ) {
                    if (line.trim().equals("apps")) {
                        Map<String, String> labels = DesktopEngine.appLabels();
                        List<String> named = new java.util.ArrayList<>();
                        for (String a : engine.apps()) {
                            named.add(labels.getOrDefault(a, a));
                        }
                        System.out.println("apps: " + named);
                        continue;
                    }
                    String[] f = line.trim().split("[\\s=]+", 2);
                    if (f.length != 2) {
                        continue;
                    }
                    if (f[0].equals("mic_device") || f[0].equals("game_device") || f[0].equals("listen")) {
                        DesktopEngine.Setup s = setup();   // "mic_device AudioBox" / none / default, "game_device
                        String v = f[1].trim();             // Speakers" / off / default, "listen 1"
                        if (f[0].equals("mic_device")) {
                            s.mic = v.equals("default") ? null : v;
                        } else if (f[0].equals("game_device")) {
                            s.game = !v.equals("off");
                            s.gameOutput = v.equals("default") || v.equals("off") ? null : v;
                        } else {
                            s.listen = v.equals("1");
                        }
                        saveSetup(s);
                        engine.apply(s);
                        System.out.println(f[0] + " = " + v + " (mic: " + engine.connected("mic") + ", game: "
                                + engine.connected("game sound") + ", headphones: " + engine.connected("headphones") + ")");
                        continue;
                    }
                    try {
                        float v = Float.parseFloat(f[1]);
                        if (f[0].equals("mic") || f[0].equals("game")) {
                            DesktopEngine.Setup s = setup();
                            if (f[0].equals("mic")) {
                                s.micLevel = v;
                            } else {
                                s.gameLevel = v;
                            }
                            saveSetup(s);
                            engine.setLevels(s.micLevel, s.gameLevel);
                        } else if (Params.byId(f[0]) != null) {
                            ParamStore.set(f[0], v);
                        } else {
                            System.out.println("No setting called " + f[0]);
                            continue;
                        }
                        System.out.println(f[0] + " = " + v);
                    } catch (NumberFormatException e) {
                        System.out.println("Not a number: " + f[1]);
                    }
                }
            } catch (IOException ignored) {
                // no terminal
            }
        }, "commands");
        t.setDaemon(true);
        t.start();
    }

    private static String orWords(String s, String none) {
        return s != null ? s : none;
    }
}
