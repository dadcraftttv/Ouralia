package io.github.dadcraftttv.ouralia;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Session Log (DadCraft, Oct 29: an app that "logs what it hears ... or logs what it plays", every session kept so
 * several can come back from the phone): one JSON line per event in {@code files/sessions/<start>.jsonl}, the record
 * Maestro's work on dcs_composer reads. Off by default (Session Log, in Behavior). Only measurements and decisions:
 * never audio (the mic never touches disk).
 *
 * Lines: {@code start} (version, host, every setting), {@code hear} once a second (mic and room level, Intensity,
 * Realm, calm build, walking, pocket), {@code set} for a setting that changed, {@code bar} at each bar line, {@code pd}
 * for what the patch reports (chord, section, notes, drum hits, heard notes, found sounds, tempo), {@code mark} for
 * DadCraft's own note at that moment, {@code stop}. Every line has {@code t}, seconds since the start.
 *
 * Shared by the phone and the desktop: the host calls {@link #setDir}, {@link #second} once a second (which starts
 * and stops the file as the switch and the sound go on and off), {@link #pd} from its Pd receiver for each of
 * {@link #RECEIVERS}, {@link #mark} and {@link #stop}. The writing happens on its own thread.
 */
final class SessionLog {
    static final String PARAM = "session_log";
    /** What the patch already sends that the log keeps (subscribed by the host). */
    static final String[] RECEIVERS = {"chime_deg", "comp_chord", "comp_section", "drum_kick", "drum_snare",
            "heard_deg", "heard_off", "fs_caught", "fs_levels", "room_feat", "room_kind", "tempo", "tick16"};
    private static final java.util.Set<String> KEPT = new java.util.HashSet<>(Arrays.asList(RECEIVERS));
    /** The newest sessions are kept up to this much in all; past it the oldest go. A session is ~6 MB an hour, so
     *  this is about 8 hours of listening (200 MB until rc5.2: DadCraft, Oct 30, no log that fills a phone). */
    static long maxTotalBytes = 50L * 1024 * 1024;   // (smaller in the tests)

    private static final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "session-log");
        t.setDaemon(true);
        return t;
    });
    private static volatile File dir;
    private static volatile boolean recording;
    private static volatile long startNanos;
    private static Writer out;                                   // writer thread only
    private static volatile File current;                        // the file being written, while recording
    private static Map<String, Float> lastSettings = new HashMap<>();   // second()'s caller only
    private static int bar;
    private static String version = "?", host = "?";

    private SessionLog() {
    }

    static void setDir(File filesDir, String appVersion, String hostName) {
        dir = new File(filesDir, "sessions");
        version = appVersion;
        host = hostName;
    }

    /** For the screen, which can open before the sound service has set the folder. */
    static void ensureDir(File filesDir) {
        if (dir == null) {
            dir = new File(filesDir, "sessions");
        }
    }

    static boolean recording() {
        return recording;
    }

    /** Once a second from the host: starts a session when the switch is on and the sound plays, ends it when either
     *  stops, and writes what is heard now and any setting that changed. */
    static synchronized void second(boolean playing, boolean walking, boolean inPocket) {
        boolean wanted = playing && ParamStore.get(PARAM) >= 0.5f && dir != null;
        if (wanted && !recording) {
            start();
        } else if (!wanted && recording) {
            stop();
        }
        if (!recording) {
            return;
        }
        StringBuilder s = line("hear")
                .append(",\"mic_db\":").append(num(Modulation.micLevelDb()))
                .append(",\"room_db\":").append(num(Worlds.roomLevelDb()))
                .append(",\"intensity\":").append(num(Modulation.intensityNow()))
                .append(",\"lively\":").append(Worlds.roomLively())
                .append(",\"hearing\":").append(str(Worlds.hearing()))
                .append(",\"world\":").append(str(Worlds.currentWorld()))
                .append(",\"calm\":").append(str(CalmBuild.phase().name().toLowerCase(Locale.US)))
                .append(",\"walking\":").append(walking)
                .append(",\"pocket\":").append(inPocket).append('}');
        write(s);
        Map<String, Float> now = ParamStore.snapshot();
        for (Map.Entry<String, Float> e : now.entrySet()) {
            Float was = lastSettings.get(e.getKey());
            if (was == null || Math.abs(was - e.getValue()) > 1e-6f) {
                write(line("set").append(",\"id\":").append(str(e.getKey()))
                        .append(",\"v\":").append(num(e.getValue())).append('}'));
            }
        }
        lastSettings = now;
    }

    /** A message from the patch on one of {@link #RECEIVERS}; the clock's 16th notes only as bar lines. */
    static void pd(String source, Object... args) {
        if (!recording || !KEPT.contains(source)) {
            return;
        }
        if ("tick16".equals(source)) {
            if (args.length == 1 && args[0] instanceof Float && (Float) args[0] == 0f) {
                write(line("bar").append(",\"n\":").append(++bar).append('}'));
            }
            return;
        }
        StringBuilder s = line("pd").append(",\"src\":").append(str(source)).append(",\"v\":[");
        for (int i = 0; i < args.length; i++) {
            Object a = args[i];
            s.append(i == 0 ? "" : ",").append(a instanceof Float ? num((Float) a) : str(String.valueOf(a)));
        }
        write(s.append("]}"));
    }

    /** DadCraft's note at this moment ("flat", "like", or anything typed). */
    static void mark(String text) {
        mark(text, System.nanoTime());
    }

    /** A mark timed at {@code nanoTime} (when the button was pressed, for a note typed afterwards). */
    static void mark(String text, long nanoTime) {
        if (recording) {
            write(line("mark", nanoTime).append(",\"text\":").append(str(text)).append('}'));
        }
    }

    static synchronized void stop() {
        if (!recording) {
            return;
        }
        write(line("stop").append('}'));
        recording = false;
        writer.execute(() -> {
            try {
                if (out != null) {
                    out.close();
                }
            } catch (IOException ignored) {
            }
            out = null;
            current = null;
        });
    }

    /** stop(), then waits (up to 2 s) for the file to be closed: for a program about to exit. */
    static void stopAndWait() {
        stop();
        try {
            writer.submit(() -> { }).get(2, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
    }

    /** The kept sessions, oldest first. */
    static List<File> sessions() {
        File d = dir;
        File[] files = d == null ? null : d.listFiles((x, name) -> name.endsWith(".jsonl"));
        List<File> list = new ArrayList<>(files == null ? Collections.<File>emptyList() : Arrays.asList(files));
        list.sort((a, b) -> stem(a).compareTo(stem(b)));   // (by start: "...-140000" before "...-140000-2")
        return list;
    }

    /** The finished sessions (not the one being written), oldest first. */
    static List<File> finished() {
        List<File> list = sessions();
        File now = current;
        if (now != null) {
            list.remove(now);
        }
        return list;
    }

    static long totalBytes(List<File> files) {
        long total = 0;
        for (File f : files) {
            total += f.length();
        }
        return total;
    }

    /** Deletes every finished session (the one being written stays); returns how many went. */
    static int deleteAll() {
        int n = 0;
        for (File f : finished()) {
            if (f.delete()) {
                n++;
            }
        }
        return n;
    }

    /**
     * Send session logs (DadCraft, Oct 30: "it should always be optional"): packs the newest finished sessions into
     * one zip, as many as fit in {@code maxZipBytes} (an email's attachment limit). Returns {packed, left out}.
     */
    static int[] pack(File zip, long maxZipBytes) throws IOException {
        List<File> all = finished();
        List<File> chosen = new ArrayList<>();
        long size = 22;   // (the zip's closing record)
        for (int i = all.size() - 1; i >= 0; i--) {   // newest first
            File f = all.get(i);
            long more = deflatedSize(f) + 2L * (f.getName().length() + 60);   // (each entry's two headers)
            if (size + more > maxZipBytes) {
                break;
            }
            size += more;
            chosen.add(0, f);
        }
        zip.getParentFile().mkdirs();
        try (java.util.zip.ZipOutputStream z = new java.util.zip.ZipOutputStream(new FileOutputStream(zip))) {
            z.setLevel(java.util.zip.Deflater.BEST_COMPRESSION);
            byte[] buf = new byte[65536];
            for (File f : chosen) {
                z.putNextEntry(new java.util.zip.ZipEntry(f.getName()));
                try (java.io.InputStream in = new java.io.FileInputStream(f)) {
                    for (int n; (n = in.read(buf)) > 0; ) {
                        z.write(buf, 0, n);
                    }
                }
                z.closeEntry();
            }
        }
        return new int[]{chosen.size(), all.size() - chosen.size()};
    }

    private static long deflatedSize(File f) throws IOException {
        java.util.zip.Deflater d = new java.util.zip.Deflater(java.util.zip.Deflater.BEST_COMPRESSION, true);
        byte[] in = new byte[65536], sink = new byte[65536];
        long size = 0;
        try (java.io.InputStream s = new java.io.FileInputStream(f)) {
            for (int n; (n = s.read(in)) > 0; ) {
                d.setInput(in, 0, n);
                while (!d.needsInput()) {
                    size += d.deflate(sink);
                }
            }
            d.finish();
            while (!d.finished()) {
                size += d.deflate(sink);
            }
        } finally {
            d.end();
        }
        return size;
    }

    private static void start() {
        startNanos = System.nanoTime();
        bar = 0;
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        File d = dir;
        lastSettings = ParamStore.snapshot();
        StringBuilder s = line("start").append(",\"version\":").append(str(version))
                .append(",\"host\":").append(str(host))
                .append(",\"wall\":").append(str(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).format(new Date())))
                .append(",\"settings\":{");
        boolean first = true;
        for (Map.Entry<String, Float> e : lastSettings.entrySet()) {
            s.append(first ? "" : ",").append(str(e.getKey())).append(':').append(num(e.getValue()));
            first = false;
        }
        s.append("}}");
        writer.execute(() -> {
            try {
                d.mkdirs();
                prune(d);
                File f = new File(d, stamp + ".jsonl");
                for (int k = 2; f.exists(); k++) {   // (two sessions in one second: each its own file)
                    f = new File(d, stamp + "-" + k + ".jsonl");
                }
                out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8));
                current = f;
            } catch (IOException e) {
                out = null;
            }
        });
        recording = true;
        write(s);
    }

    private static String stem(File f) {
        String n = f.getName();
        return n.substring(0, n.length() - ".jsonl".length());
    }

    /** Keeps the newest sessions within maxTotalBytes (the oldest go first); never the one about to start. */
    private static void prune(File d) {
        List<File> all = sessions();
        long total = 0;
        for (File f : all) {
            total += f.length();
        }
        for (File f : all) {
            if (total <= maxTotalBytes) {
                break;
            }
            total -= f.length();
            f.delete();
        }
    }

    private static StringBuilder line(String event) {
        return line(event, System.nanoTime());
    }

    private static StringBuilder line(String event, long nanoTime) {
        double t = Math.max(0, nanoTime - startNanos) / 1e9;
        return new StringBuilder(96).append("{\"t\":").append(String.format(Locale.US, "%.3f", t))
                .append(",\"ev\":\"").append(event).append('"');
    }

    private static void write(StringBuilder s) {
        String text = s.append('\n').toString();
        boolean flush = !text.contains("\"ev\":\"pd\"") && !text.contains("\"ev\":\"bar\"");   // (once a second or so)
        writer.execute(() -> {
            try {
                if (out != null) {
                    out.write(text);
                    if (flush) {
                        out.flush();
                    }
                }
            } catch (IOException ignored) {
            }
        });
    }

    private static String num(float x) {
        return Float.isFinite(x) ? String.format(Locale.US, "%.4g", x) : "null";
    }

    static String str(String text) {
        if (text == null) {
            return "null";
        }
        StringBuilder s = new StringBuilder(text.length() + 2).append('"');
        for (char c : text.toCharArray()) {
            if (c == '"' || c == '\\') {
                s.append('\\').append(c);
            } else if (c < 0x20) {
                s.append(String.format(Locale.US, "\\u%04x", (int) c));
            } else {
                s.append(c);
            }
        }
        return s.append('"').toString();
    }
}
