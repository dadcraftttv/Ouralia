// SPDX-License-Identifier: GPL-3.0-or-later
package app.pdscenes;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Kits: what each part of the music sounds like (Oct 17). The music engine decides the notes; a kit gives each part
 * (melody, bass, pad, drums) an engine of the patch's kit voice (dcs_voice~) and its settings, or leaves it Classic.
 *
 * A kit is a text file (assets/kits/<name>.txt), one line per part:
 * <pre>
 * title: Acoustic
 * mel: engine pluck, bright 0.45, level 1.2
 * bass: engine bowed, bright 0.25, attack 400, release 900
 * </pre>
 * A part not listed stays Classic. User kits (Oct 17) are folders in files/kits ("001 My kit/kit.txt"), numbered in
 * import order, listed after the built-in ones: menu index BUILT_IN.length + n is the n-th. Mix and match: each part can take its sound from another kit ("Melody from" etc.,
 * Params kitp_<part>: 0 = the Kit, else BUILT_IN[n - 1]). A part whose kit changed (or every part when the patch
 * opens) first gets the voice's defaults, then its kit's settings, the engine last, as messages to kit_<part>.
 *
 * Samples (stage 2, Oct 18): a user kit's folder can hold recordings, named in its kit.txt: "sample <file> <root>"
 * (a melodic part: the file and the note it plays, C4 = 60), "kick/snare/hat/ohat <file>" (drums). The voice reads
 * WAV files itself; other formats are decoded to WAV at import (Android's decoders). A part whose files are missing
 * (a preset carried the kit's text but not its files) stays Classic.
 */
final class Kits {
    static final String ID = "kit";
    static final String[] PARTS = {"mel", "bass", "pad", "drums"};
    /** The Kit menu, in order (Params' choices for "kit" follow it). */
    static final String[] BUILT_IN = {"classic", "acoustic", "synth", "edm", "futurebass", "lowend", "chipnes",
            "chipgb", "chipgenesis"};
    /** The built-in kits' short names ("Melody from" etc.). */
    static final String[] TITLES = {"Classic", "Acoustic", "Synth", "EDM", "Future bass", "Low end", "Chiptune: NES",
            "Chiptune: Game Boy", "Chiptune: Genesis"};
    /** Room for user kits in the Params' ranges (the menus list only the kits there are). */
    static final int MAX_USER = 99;
    static final String FILE = "kit.txt";
    /** The Params holding a kit's menu index: the Kit, then each part's "from" (whose index is one more). */
    static final String[] PARAMS = {ID, "kitp_mel", "kitp_bass", "kitp_pad", "kitp_drums",
            "kitl_mel", "kitl_bass", "kitl_pad", "kitl_drums"};
    static final String[] ENGINES = {"classic", "pluck", "bowed", "modal", "analog", "drums", "chop", "sub", "chip", "sample"};
    static final String[] MODAL = {"", "bell", "marimba", "bowl"};
    static final String[] CHIPS = {"", "nes", "gameboy", "genesis"};
    static final String[] WAVES = {"pulse", "triangle", "wave", "fm"};
    /** Settings that name a file: "sample" (with its root note), and the drums' kick, snare, hat, open hat. */
    static final String[] DRUM_FILES = {"kick", "snare", "hat", "ohat"};
    /** The sample engine's scale per part (PARTS order): a normalised recording at `level 1` plays about as loud as
     *  the Classic part (measured, test_patch.py "a sample kit"). */
    static final float[] SAMPLE_GAIN = {6.7f, 6f, 0.26f, 6f};
    private static final String[] NOTE_NAMES = {"c", "c#", "d", "d#", "e", "f", "f#", "g", "g#", "a", "a#", "b"};
    /** The voice's own defaults, sent before a kit's settings (so nothing lingers from the kit before). */
    static final String[][] DEFAULTS = {{"engine", "0"}, {"bright", "0.4"}, {"level", "1"}, {"attack", "300"},
            {"release", "800"}, {"decay", "3000"}, {"modal", "1"}, {"cutoff", "4"}, {"reso", "0.3"}, {"fenv", "6"},
            {"detune", "8"}, {"sub", "0.3"}, {"saws", "2"}, {"ktune", "50"}, {"kdecay", "350"}, {"kdrive", "0.3"},
            {"stone", "185"}, {"sdecay", "180"}, {"hdecay", "45"}, {"pump", "0"}, {"pumprel", "250"}, {"wobble", "0"},
            {"wrate", "2"}, {"bend", "0"}, {"bendms", "120"}, {"chop", "180"}, {"octave", "0"}, {"chance", "1"},
            {"hchance", "1"}, {"glide", "0"}, {"drive", "0.3"}, {"chip", "1"}, {"wave", "0"}, {"duty", "0.5"},
            {"sus", "0.6"}, {"vib", "0"}, {"arp", "0"}, {"fmratio", "1"}, {"fmindex", "2"}, {"fmfb", "0"},
            {"clear", "0"}, {"oneshot", "1"}, {"loop", "0"}, {"loopstart", "0"}, {"loopend", "0"}};

    /** Reads a built-in kit's text (assets on the phone, files on the desktop); null if missing. */
    interface Source {
        String read(String name);
    }

    /** Where the messages go (the app: PdBase.sendMessage). */
    interface Out {
        void send(String receiver, String selector, float value);

        /** A message with words (a sample's path). */
        default void sendMessage(String receiver, String selector, Object... args) {
        }
    }

    /** Decodes a recording that isn't WAV into a WAV file (the app: PackLibrary.decode; null on the desktop). */
    interface Decoder {
        void decode(File in, File out) throws IOException;
    }

    static volatile Decoder decoder;

    static final class Kit {
        String title = "";
        /** The kit's folder (a user kit: where its samples are), else null. */
        File dir;
        /** part -> (setting, value), in file order; a file setting is (name, file, root note or null) */
        final Map<String, List<Object[]>> parts = new LinkedHashMap<>();
    }

    /** One user kit: its folder and kit.txt's text. */
    static final class UserKit {
        final File dir;
        final String text;
        final String title;

        UserKit(File dir, String text) {
            this.dir = dir;
            this.text = text;
            String t = parse(text).title;
            this.title = t.isEmpty() ? dir.getName().replaceFirst("^\\d+ ", "") : t;
        }
    }

    static volatile Source source;
    /** Where user kits live (files/kits; null: none, as in the tests that don't set it). */
    static volatile File userDir;
    /** The settings each part last got (null = none yet, "" = its own sound): a part whose sound didn't change isn't
     *  sent again (its held notes would start over). Settings, not the kit's index: a part edited on the desktop bench
     *  is sent again (and only that part), and deleting a kit (which renumbers the ones after it) resends nothing. */
    private static final String[] sentPart = new String[8];   // (the parts, then their layers)

    private Kits() {
    }

    /** A kit file's text, parsed. Unknown parts and settings are skipped (a newer kit on an older app still loads). */
    static Kit parse(String text) {
        Kit kit = new Kit();
        for (String raw : text.split("\n")) {
            String line = raw.trim();
            int colon = line.indexOf(':');
            if (line.isEmpty() || line.startsWith("#") || colon < 0) {
                continue;
            }
            String key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT), rest = line.substring(colon + 1).trim();
            if (key.equals("title")) {
                kit.title = rest;
                continue;
            }
            if (!isPart(key)) {
                continue;
            }
            List<Object[]> settings = new ArrayList<>();
            for (String item : rest.split(",")) {
                String[] w = item.trim().split("\\s+");
                if (w.length < 2) {
                    continue;
                }
                String name = w[0].toLowerCase(Locale.ROOT);
                if (isFileSetting(name)) {
                    settings.add(fileSetting(name, w));
                    continue;
                }
                float value;
                if (name.equals("engine")) {
                    value = indexOf(ENGINES, w[1]);
                } else if (name.equals("modal")) {
                    value = indexOf(MODAL, w[1]);
                } else if (name.equals("chip") && indexOf(CHIPS, w[1]) > 0) {
                    value = indexOf(CHIPS, w[1]);
                } else if (name.equals("wave") && indexOf(WAVES, w[1]) >= 0) {
                    value = indexOf(WAVES, w[1]);
                } else {
                    try {
                        value = Float.parseFloat(w[1]);
                    } catch (NumberFormatException e) {
                        continue;
                    }
                }
                if (value >= 0) {
                    settings.add(new Object[]{name, value});
                }
            }
            kit.parts.put(key, settings);
        }
        return kit;
    }

    static boolean isFileSetting(String name) {
        return name.equals("sample") || indexOf(DRUM_FILES, name) >= 0;
    }

    /** (name, file, root): "sample piano c4.wav C4" is the file "piano c4.wav" at C4; the root defaults to C4. */
    private static Object[] fileSetting(String name, String[] w) {
        int end = w.length;
        Float root = null;
        if (name.equals("sample")) {
            root = 60f;
            int n = w.length >= 3 ? noteNumber(w[w.length - 1]) : -1;
            if (n >= 0) {
                root = (float) n;
                end = w.length - 1;
            }
        }
        return new Object[]{name, String.join(" ", Arrays.asList(w).subList(1, end)), root};
    }

    /** A note's MIDI number from "60", "C4", "f#3" or "Bb2" (C4 = 60), -1 if it isn't one. */
    static int noteNumber(String word) {
        String w = word.toLowerCase(Locale.ROOT);
        if (w.matches("\\d{1,3}")) {
            int n = Integer.parseInt(w);
            return n <= 127 ? n : -1;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("([a-g])([#b]?)(-?\\d)").matcher(w);
        if (!m.matches()) {
            return -1;
        }
        int pc = indexOf(NOTE_NAMES, m.group(1)) + (m.group(2).equals("#") ? 1 : m.group(2).equals("b") ? -1 : 0);
        return pc + 12 * (Integer.parseInt(m.group(3)) + 1);
    }

    /** Where a kit's file plays from: the file itself if WAV, else its decoded copy; null if the name leaves the
     *  kit's folder. */
    static File sampleFile(File dir, String name) {
        if (dir == null || name.contains("..") || name.startsWith("/")) {
            return null;
        }
        return name.toLowerCase(Locale.ROOT).endsWith(".wav") ? new File(dir, name)
                : new File(dir, ".decoded-" + name.replace('/', '_') + ".wav");
    }

    /** The files a kit's text names that its folder doesn't have (nothing to play them from). */
    static List<String> missingFiles(String text, File dir) {
        List<String> out = new ArrayList<>();
        for (List<Object[]> settings : parse(text).parts.values()) {
            for (Object[] st : settings) {
                if (st.length == 3 && st[1] instanceof String) {
                    File f = sampleFile(dir, (String) st[1]);
                    if (f == null || !f.isFile()) {
                        out.add((String) st[1]);
                    }
                }
            }
        }
        return out;
    }

    /** Decodes every non-WAV recording a kit names (once: the decoded copy sits beside it). */
    static void prepareSamples(File dir, String text) {
        Decoder d = decoder;
        if (d == null) {
            return;
        }
        for (List<Object[]> settings : parse(text).parts.values()) {
            for (Object[] st : settings) {
                if (st.length == 3 && st[1] instanceof String && !((String) st[1]).toLowerCase(Locale.ROOT).endsWith(".wav")) {
                    File in = new File(dir, (String) st[1]), out = sampleFile(dir, (String) st[1]);
                    if (out != null && in.isFile() && !out.isFile()) {
                        try {
                            d.decode(in, out);
                        } catch (IOException e) {
                            out.delete();      // (reported as missing)
                        }
                    }
                }
            }
        }
    }

    /** The kit (BUILT_IN index) a part plays: its own choice (1 = the first kit...), or the Kit when 0. */
    static int kitFor(int kit, int from) {
        return from > 0 ? from - 1 : kit;
    }

    /** Sends the sound of every part whose kit changed (all of them when `force`, e.g. the patch just opened).
     *  `from` = each part's "from" choice (Params kitp_<part>), in PARTS order. No layers. */
    static void send(int kit, int[] from, boolean force, Out out) {
        send(kit, from, new int[PARTS.length], force, out);
    }

    /** The same, with each part's layer (Params kitl_<part>: 0 = none, else BUILT_IN[n - 1]), sent to kit2_<part>
     *  (Oct 19: two kits blended on one part). A part with no layer gets the voice's defaults there: silent. */
    static synchronized void send(int kit, int[] from, int[] layer, boolean force, Out out) {
        for (int j = 0; j < 2 * PARTS.length; j++) {
            int k = j % PARTS.length;
            int l = j < PARTS.length ? 0 : (k < layer.length ? layer[k] : 0);
            int index = j < PARTS.length ? kitFor(kit, k < from.length ? from[k] : 0) : l - 1;
            String text = index < 0 ? null : text(index);
            Kit parsed = text == null ? null : parse(text);
            if (parsed != null) {
                parsed.dir = dir(index);
            }
            String key = parsed == null ? "" : describe(parsed.parts.get(PARTS[k])) + (parsed.dir == null ? "" : parsed.dir);
            if (!force && key.equals(sentPart[j])) {
                continue;
            }
            sentPart[j] = key;
            sendPart((j < PARTS.length ? "kit_" : "kit2_") + PARTS[k], PARTS[k], parsed, out);
        }
    }

    /** The whole kit `index` to every part (no mix and match). */
    static void send(int index, Out out) {
        send(index, new int[PARTS.length], true, out);
    }

    private static String describe(List<Object[]> settings) {
        StringBuilder sb = new StringBuilder();
        if (settings != null) {
            for (Object[] st : settings) {
                for (Object o : st) {
                    sb.append(o).append(' ');
                }
                sb.append(',');
            }
        }
        return sb.toString();
    }

    /** The folder of the user kit at menu index `index`, null for a built-in kit or none. */
    static File dir(int index) {
        List<UserKit> user = userKits();
        int n = index - BUILT_IN.length;
        return n >= 0 && n < user.size() ? user.get(n).dir : null;
    }

    /** The text of the kit at menu index `index` (built-in or user), null if there is none. */
    static String text(int index) {
        if (index >= 0 && index < BUILT_IN.length) {
            Source src = source;
            return src == null ? null : src.read(BUILT_IN[index]);
        }
        List<UserKit> user = userKits();
        int n = index - BUILT_IN.length;
        return n >= 0 && n < user.size() ? user.get(n).text : null;
    }

    private static void sendPart(String recv, String part, Kit kit, Out out) {
        {
            for (String[] d : DEFAULTS) {
                out.send(recv, d[0], Float.parseFloat(d[1]));
            }
            out.send(recv, "sgain", SAMPLE_GAIN[indexOf(PARTS, part)]);
            List<Object[]> settings = kit == null ? null : kit.parts.get(part);
            if (settings == null) {
                return;
            }
            // a part whose recordings aren't all here stays Classic (its defaults are sent: engine 0)
            for (Object[] st : settings) {
                if (st.length == 3) {
                    File f = sampleFile(kit.dir, (String) st[1]);
                    if (f == null || !f.isFile()) {
                        return;
                    }
                }
            }
            // settings first, the engine last: a note arriving in between hears the new sound whole
            Object[] engine = null;
            for (Object[] st : settings) {
                if (st[0].equals("engine")) {
                    engine = st;
                } else if (st.length == 3) {
                    String path = sampleFile(kit.dir, (String) st[1]).getPath();
                    if (st[0].equals("sample")) {
                        out.sendMessage(recv, "sample", path, st[2]);
                    } else {
                        out.sendMessage(recv, "dsample", (float) indexOf(DRUM_FILES, (String) st[0]), path);
                    }
                } else {
                    out.send(recv, (String) st[0], (Float) st[1]);
                }
            }
            if (engine != null) {
                out.send(recv, "engine", (Float) engine[1]);
            }
        }
    }

    // ---- user kits ----

    /** The user kits, in import order (folder names sort by their number). A folder without a readable kit.txt is
     *  skipped. */
    static List<UserKit> userKits() {
        List<UserKit> out = new ArrayList<>();
        File[] dirs = folders();
        for (File d : dirs) {
            try {
                out.add(new UserKit(d, new String(Files.readAllBytes(new File(d, FILE).toPath()), StandardCharsets.UTF_8)));
            } catch (IOException e) {
                // (not a kit, or half copied: left out of the menus)
            }
            if (out.size() == MAX_USER) {
                break;
            }
        }
        return out;
    }

    private static File[] folders() {
        File dir = userDir;
        File[] dirs = dir == null ? null : dir.listFiles(f -> f.isDirectory() && !f.getName().startsWith("."));
        if (dirs == null) {
            return new File[0];
        }
        Arrays.sort(dirs, (a, b) -> {
            int c = Long.compare(number(a.getName()), number(b.getName()));
            return c != 0 ? c : a.getName().compareTo(b.getName());
        });
        return dirs;
    }

    /** A folder's leading number ("012 My kit" = 12); folders made by hand without one come after. */
    private static long number(String name) {
        int i = 0;
        while (i < name.length() && i < 9 && Character.isDigit(name.charAt(i))) {
            i++;
        }
        return i == 0 ? Long.MAX_VALUE : Long.parseLong(name.substring(0, i));
    }

    /** Changes when a user kit is added, removed or edited (the desktop bench polls it). */
    static String signature() {
        StringBuilder sb = new StringBuilder();
        for (File d : folders()) {
            File f = new File(d, FILE);
            sb.append(d.getName()).append('/').append(f.lastModified()).append('/').append(f.length()).append('\n');
        }
        return sb.toString();
    }

    /** The Kit menu: the built-in kits (as the Param names them), then the user kits. */
    static String[] kitChoices(String[] builtInLabels) {
        List<String> names = new ArrayList<>(Arrays.asList(builtInLabels).subList(0, BUILT_IN.length));
        for (UserKit u : userKits()) {
            names.add(u.title);
        }
        return names.toArray(new String[0]);
    }

    /** A part's "from" menu: the Kit, the built-in kits, the user kits. */
    static String[] fromChoices() {
        List<String> names = new ArrayList<>();
        names.add("The kit");
        names.addAll(Arrays.asList(TITLES));
        for (UserKit u : userKits()) {
            names.add(u.title);
        }
        return names.toArray(new String[0]);
    }

    /** A part's layer menu: none, the built-in kits, the user kits. */
    static String[] layerChoices() {
        String[] from = fromChoices();
        from[0] = "None";
        return from;
    }

    /** Puts the user kits in the menus of the Kit Params (after an import or a delete, and at start). */
    static void refreshChoices() {
        for (String id : PARAMS) {
            Param p = Params.byId(id);
            if (p != null) {
                p.choices = ID.equals(id) ? kitChoices(p.choices) : id.startsWith("kitl_") ? layerChoices() : fromChoices();
            }
        }
    }

    /** What a kit's text says that this app won't play (unknown parts, settings or engine names), for the import
     *  report. Empty: all of it plays. */
    static List<String> check(String text) {
        List<String> problems = new ArrayList<>();
        boolean anyPart = false;
        int lineNo = 0;
        for (String raw : text.split("\n")) {
            lineNo++;
            String line = raw.trim();
            int colon = line.indexOf(':');
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (colon < 0) {
                problems.add("Line " + lineNo + ": no \"part:\" at the start");
                continue;
            }
            String key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT), rest = line.substring(colon + 1).trim();
            if (key.equals("title")) {
                continue;
            }
            if (!isPart(key)) {
                problems.add("Line " + lineNo + ": unknown part \"" + key + "\" (parts: " + String.join(", ", PARTS) + ")");
                continue;
            }
            anyPart = true;
            for (String item : rest.split(",")) {
                String[] w = item.trim().split("\\s+");
                if (w.length < 2) {
                    if (!item.trim().isEmpty()) {
                        problems.add("Line " + lineNo + ": \"" + item.trim() + "\" has no value");
                    }
                    continue;
                }
                String name = w[0].toLowerCase(Locale.ROOT);
                if (name.equals("engine")) {
                    if (indexOf(ENGINES, w[1]) < 0) {
                        problems.add("Line " + lineNo + ": unknown engine \"" + w[1] + "\" (engines: "
                                + String.join(", ", ENGINES) + ")");
                    }
                } else if (name.equals("modal")) {
                    if (indexOf(MODAL, w[1]) <= 0) {
                        problems.add("Line " + lineNo + ": unknown modal \"" + w[1] + "\" (bell, marimba, bowl)");
                    }
                } else if (name.equals("chip") && indexOf(CHIPS, w[1]) <= 0) {
                    problems.add("Line " + lineNo + ": unknown chip \"" + w[1] + "\" (nes, gameboy, genesis)");
                } else if (name.equals("wave") && indexOf(WAVES, w[1]) < 0) {
                    problems.add("Line " + lineNo + ": unknown wave \"" + w[1] + "\" (pulse, triangle, wave, fm)");
                } else if (name.equals("chip") || name.equals("wave")) {
                    continue;
                } else if (isFileSetting(name)) {
                    if (name.equals("sample") && w.length >= 3 && noteNumber(w[w.length - 1]) < 0
                            && !w[w.length - 1].toLowerCase(Locale.ROOT).matches(".*\\.\\w{2,4}")) {
                        problems.add("Line " + lineNo + ": \"" + w[w.length - 1] + "\" is not a note (C4, F#3, 60)");
                    }
                    continue;
                } else if (!isSetting(name)) {
                    problems.add("Line " + lineNo + ": unknown setting \"" + name + "\"");
                } else {
                    try {
                        Float.parseFloat(w[1]);
                    } catch (NumberFormatException e) {
                        problems.add("Line " + lineNo + ": \"" + w[1] + "\" is not a number");
                    }
                }
            }
        }
        if (!anyPart) {
            problems.add(0, "No parts: a kit needs at least one line like \"mel: engine pluck\" (parts: "
                    + String.join(", ", PARTS) + ")");
        }
        return problems;
    }

    /** True when check() found no part at all: not a kit. */
    static boolean hasParts(String text) {
        return !parse(text).parts.isEmpty();
    }

    /** Adds a kit with this text (from a .txt file or a preset) and returns its menu index. A user kit with the
     *  same text already here is reused, as is a built-in one. */
    static synchronized int install(String text) throws IOException {
        int same = find(text);
        if (same >= 0) {
            return same;
        }
        File dir = newFolder(parse(text).title);
        try (FileOutputStream out = new FileOutputStream(new File(dir, FILE))) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
        return indexOf(dir);
    }

    /** Adds a kit folder (from a zip or a folder import, already copied to `staging`, which is moved in) and
     *  returns its menu index; its kit.txt may sit one folder down. A user kit with the same text is reused. */
    static synchronized int installFolder(File staging) throws IOException {
        File root = staging;
        if (!new File(root, FILE).isFile()) {
            File[] subs = staging.listFiles(f -> f.isDirectory() && new File(f, FILE).isFile());
            if (subs == null || subs.length != 1) {
                throw new IOException("No " + FILE + " in it");
            }
            root = subs[0];
        }
        String text = new String(Files.readAllBytes(new File(root, FILE).toPath()), StandardCharsets.UTF_8);
        if (!hasParts(text)) {
            throw new IOException(check(text).get(0));
        }
        prepareSamples(root, text);
        int same = find(text);
        if (same >= BUILT_IN.length) {
            File old = dir(same);
            if (old == null || missingFiles(text, old).isEmpty() || !missingFiles(text, root).isEmpty()) {
                return same;
            }
            // the same kit without its recordings (a preset brought its text): this one has them
            File aside = new File(old.getPath() + ".old");
            if (!old.renameTo(aside) || !root.renameTo(old)) {
                throw new IOException("Cannot move the kit into " + old);
            }
            deleteTree(aside);
            Arrays.fill(sentPart, null);   // (played again, now with them)
            return same;
        }
        File dir = newFolder(parse(text).title);
        if (!dir.delete() || !root.renameTo(dir)) {
            throw new IOException("Cannot move the kit into " + dir);
        }
        return indexOf(dir);
    }

    /** The menu index of a kit with exactly this text (built-in or user), -1 if none. */
    static int find(String text) {
        String t = text.trim();
        for (int i = 0; i < BUILT_IN.length; i++) {
            String b = text(i);
            if (b != null && b.trim().equals(t)) {
                return i;
            }
        }
        List<UserKit> user = userKits();
        for (int n = 0; n < user.size(); n++) {
            if (user.get(n).text.trim().equals(t)) {
                return BUILT_IN.length + n;
            }
        }
        return -1;
    }

    private static int indexOf(File dir) {
        List<UserKit> user = userKits();
        for (int n = 0; n < user.size(); n++) {
            if (user.get(n).dir.equals(dir)) {
                return BUILT_IN.length + n;
            }
        }
        return -1;
    }

    /** A new, empty folder numbered after the last one ("007 Title"). */
    private static File newFolder(String title) throws IOException {
        File base = userDir;
        if (base == null || (!base.isDirectory() && !base.mkdirs())) {
            throw new IOException("Cannot create the kits folder");
        }
        if (userKits().size() >= MAX_USER) {
            throw new IOException("There are " + MAX_USER + " kits already: delete one first");
        }
        long next = 1;
        for (File d : folders()) {
            long n = number(d.getName());
            if (n != Long.MAX_VALUE) {
                next = Math.max(next, n + 1);
            }
        }
        String clean = title.replaceAll("[^A-Za-z0-9 _-]", "").trim();
        if (clean.length() > 40) {
            clean = clean.substring(0, 40).trim();
        }
        File dir = new File(base, String.format(Locale.ROOT, "%03d %s", next, clean.isEmpty() ? "kit" : clean).trim());
        if (!dir.mkdirs()) {
            throw new IOException("Cannot create " + dir);
        }
        return dir;
    }

    /** Deletes the user kit at menu index `index` (its folder and files). */
    static synchronized void delete(int index) {
        List<UserKit> user = userKits();
        int n = index - BUILT_IN.length;
        if (n >= 0 && n < user.size()) {
            deleteAll(user.get(n).dir);
        }
    }

    private static void deleteAll(File f) {
        File[] kids = f.listFiles();
        if (kids != null) {
            for (File k : kids) {
                deleteAll(k);
            }
        }
        f.delete();
    }

    /** A kit Param's value after the kit at `removed` (a Kit menu index) was deleted: that kit becomes the first
     *  choice (Classic, or "The kit"), the ones after it move up one. `fromMenu`: a part's "from" (index + 1). */
    static int afterDelete(int value, int removed, boolean fromMenu) {
        int r = fromMenu ? removed + 1 : removed;
        return value == r ? 0 : value > r ? value - 1 : value;
    }

    private static void deleteTree(File f) {
        File[] kids = f.listFiles();
        if (kids != null) {
            for (File k : kids) {
                deleteTree(k);
            }
        }
        f.delete();
    }

    private static boolean isSetting(String name) {
        for (String[] d : DEFAULTS) {
            if (d[0].equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPart(String key) {
        for (String p : PARTS) {
            if (p.equals(key)) {
                return true;
            }
        }
        return false;
    }

    private static int indexOf(String[] names, String word) {
        for (int i = 0; i < names.length; i++) {
            if (names[i].equalsIgnoreCase(word)) {
                return i;
            }
        }
        return -1;
    }
}
