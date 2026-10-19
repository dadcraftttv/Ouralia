package app.pdscenes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Kits: the kit files parse, and choosing a kit sends each part its defaults, then its settings, then its engine. */
public class Q {
    static int fails = 0;

    static void check(boolean ok, String what) {
        System.out.println((ok ? "ok:   " : "FAIL: ") + what);
        if (!ok) fails++;
    }

    public static void main(String[] a) throws Exception {
        Path kits = Path.of(System.getProperty("user.dir"), "app/src/main/assets/kits");
        Kits.source = name -> {
            try {
                return Files.readString(kits.resolve(name + ".txt"));
            } catch (Exception e) {
                return null;
            }
        };
        check(Kits.BUILT_IN.length == Params.byId(Kits.ID).choices.length,
                "every built-in kit has an entry in the Kit menu (" + Kits.BUILT_IN.length + ")");
        for (String name : Kits.BUILT_IN) {
            check(Kits.source.read(name) != null && !Kits.parse(Kits.source.read(name)).title.isEmpty(),
                    "kit file " + name + ".txt is there and has a title");
        }
        Kits.Kit ac = Kits.parse(Kits.source.read("acoustic"));
        check(ac.title.equals("Acoustic") && ac.parts.size() == 3, "Acoustic gives all three parts a sound");
        List<String> sent = new ArrayList<>();
        Kits.send(0, (r, s, v) -> sent.add(r + " " + s + " " + v));
        check(sent.contains("kit_mel engine 0.0") && sent.contains("kit_bass engine 0.0") && sent.contains("kit_pad engine 0.0")
                && sent.stream().noneMatch(m -> m.contains("engine") && !m.endsWith(" 0.0")),
                "Classic: every part's engine 0 (its own sound) (" + sent.size() + " messages)");
        sent.clear();
        Kits.send(1, (r, s, v) -> sent.add(r + " " + s + " " + v));
        int lastMel = sent.lastIndexOf("kit_mel engine 1.0"), brightMel = sent.lastIndexOf("kit_mel bright 0.45");
        check(lastMel > brightMel && brightMel > sent.indexOf("kit_mel engine 0.0") && sent.contains("kit_bass engine 2.0")
                && sent.contains("kit_pad attack 1200.0"),
                "Acoustic: defaults first, then its settings, the engine last (melody pluck, bass bowed, pad attack 1200)");
        // mix and match: the Acoustic kit with the Classic pad; then only the pad changes back
        for (int k = 0; k < Kits.PARTS.length; k++) {
            check(Params.byId("kitp_" + Kits.PARTS[k]) != null
                    && Params.byId("kitp_" + Kits.PARTS[k]).choices.length == Kits.BUILT_IN.length + 1,
                    "the " + Kits.PARTS[k] + " has a 'from' menu: the kit + every built-in kit");
        }
        sent.clear();
        Kits.send(1, new int[]{0, 0, 1}, true, (r, s, v) -> sent.add(r + " " + s + " " + v));
        check(sent.contains("kit_mel engine 1.0") && sent.contains("kit_bass engine 2.0")
                && sent.stream().noneMatch(m -> m.startsWith("kit_pad engine") && !m.endsWith(" 0.0")),
                "Acoustic with the pad from Classic: melody pluck, bass bowed, pad its own sound");
        sent.clear();
        Kits.send(1, new int[]{0, 0, 0}, false, (r, s, v) -> sent.add(r + " " + s + " " + v));
        check(!sent.isEmpty() && sent.stream().allMatch(m -> m.startsWith("kit_pad")) && sent.contains("kit_pad engine 2.0"),
                "switching only the pad back to the kit sends only the pad (melody and bass keep their notes)");
        sent.clear();
        Kits.send(1, new int[4], new int[]{0, 0, 7, 0}, false, (r, s, v) -> sent.add(r + " " + s + " " + v));
        check(!sent.isEmpty() && sent.stream().allMatch(m -> m.startsWith("kit2_pad ")) && sent.contains("kit2_pad engine 8.0"),
                "a pad layer (Chiptune: NES) goes to kit2_pad alone: the first kit's parts keep their notes");
        sent.clear();
        Kits.send(1, new int[4], new int[4], false, (r, s, v) -> sent.add(r + " " + s + " " + v));
        check(sent.contains("kit2_pad engine 0.0") && sent.stream().allMatch(m -> m.startsWith("kit2_pad ")),
                "the layer back to None: kit2_pad goes silent (engine 0)");
        check(Kits.layerChoices()[0].equals("None") && Kits.layerChoices().length == Kits.fromChoices().length
                && Params.byId("kitl_pad").choices.length == Kits.layerChoices().length,
                "the layer menus: None, then every kit");
        check(Params.byId(Kits.ID).inPreset && !Params.byId(Kits.ID).resetWhenAbsent && !Params.byId("kitp_pad").resetWhenAbsent,
                "kit settings are in presets, and a preset or Realm that doesn't mention them leaves them alone");
        Kits.Kit odd = Kits.parse("title: X\nmel: engine pluck, wobble 3, bright high, bright 0.2\nnoise: engine bowed\n");
        check(odd.parts.size() == 1 && odd.parts.get("mel").size() == 3,
                "unknown parts and unreadable values are skipped (" + odd.parts.get("mel").size() + " settings kept)");
        userKits();
        System.out.println(fails == 0 ? "ALL PASSED" : fails + " FAILED");
    }

    /** User kits (Oct 17): installed into a folder, listed after the built-in ones, deleted, carried by presets. */
    static void userKits() throws Exception {
        Path dir = Files.createTempDirectory("kits");
        Kits.userDir = dir.toFile();
        int b = Kits.BUILT_IN.length;
        String mine = "title: Night Glass\nmel: engine modal, modal bell, bright 0.6, level 6\nbass: engine sub, decay 2000\n";
        int a = Kits.install(mine);
        Kits.refreshChoices();
        String[] kitMenu = Params.byId(Kits.ID).choices, fromMenu = Params.byId("kitp_mel").choices;
        check(a == b && kitMenu.length == b + 1 && kitMenu[b].equals("Night Glass") && kitMenu[1].startsWith("Acoustic (")
                        && fromMenu.length == b + 2 && fromMenu[b + 1].equals("Night Glass"),
                "an installed kit is the next entry of the Kit and 'from' menus (built-in labels kept)");
        check(Kits.install(mine + "\n") == a && Kits.userKits().size() == 1, "the same kit installed again is reused");
        check(Kits.install(Kits.source.read("synth")) == 2 && Kits.userKits().size() == 1,
                "a copy of a built-in kit is the built-in one");
        List<String> sent = new ArrayList<>();
        Kits.send(a, (r, s, v) -> sent.add(r + " " + s + " " + v));
        check(sent.contains("kit_mel engine 3.0") && sent.contains("kit_mel modal 1.0") && sent.contains("kit_bass engine 7.0")
                && sent.stream().noneMatch(m -> m.startsWith("kit_pad engine") && !m.endsWith(" 0.0")),
                "a user kit plays: melody modal bell, bass sub, the pad Classic");
        // the desktop bench: the kit file edited, only the part that changed is sent again
        String sig = Kits.signature();
        Path file = Kits.userKits().get(0).dir.toPath().resolve(Kits.FILE);
        Files.writeString(file, mine.replace("decay 2000", "decay 900"));
        file.toFile().setLastModified(System.currentTimeMillis() + 5000);
        sent.clear();
        Kits.send(a, new int[4], false, (r, s, v) -> sent.add(r + " " + s + " " + v));
        check(!Kits.signature().equals(sig) && sent.contains("kit_bass decay 900.0")
                        && sent.stream().noneMatch(m -> m.startsWith("kit_mel")),
                "an edited kit file plays again at once, only the parts that changed");
        Files.writeString(file, mine);
        int c = Kits.install("title: Second\npad: engine bowed\n");
        check(c == b + 1, "a second kit comes after the first (import order)");
        // a preset carries its user kits, by text: the kits deleted here, loading it brings them back
        Map<String, Float> values = ParamStore.snapshot();
        values.put(Kits.ID, (float) c);
        values.put("kitp_mel", (float) (a + 1));
        String json = PresetManager.toJson("With kits", values);
        check(json.contains("\"kits\"") && json.contains("Night Glass") && json.contains("Second"),
                "a preset saved with user kits carries their text");
        Kits.delete(a);
        Kits.refreshChoices();
        check(Kits.userKits().size() == 1 && Kits.userKits().get(0).title.equals("Second")
                        && Params.byId(Kits.ID).choices.length == b + 1,
                "deleting a kit removes it from the menus");
        check(Kits.afterDelete(c, a, false) == b && Kits.afterDelete(a, a, false) == 0 && Kits.afterDelete(2, a, false) == 2
                        && Kits.afterDelete(a + 1, a, true) == 0 && Kits.afterDelete(c + 1, a, true) == b + 1,
                "after a delete: its users go back to the first choice, later kits move up one");
        Kits.delete(b);
        Map<String, Float> loaded = PresetManager.parse(json).values;
        int k = Math.round(loaded.get(Kits.ID)), m = Math.round(loaded.get("kitp_mel")) - 1;
        check(Kits.userKits().size() == 2 && Kits.text(k).contains("Second") && Kits.text(m).contains("Night Glass")
                        && Params.byId(Kits.ID).choices.length == b + 2,
                "loading the preset installs its kits again and points at them (" + k + ", " + m + ")");
        loaded = PresetManager.parse(json.replaceAll("(?s),\\s*\"kits\".*\\}\\s*\\}", "}")).values;
        check(Math.round(loaded.get(Kits.ID)) == 0, "a preset naming a user kit it doesn't carry plays Classic");
        // import checks: what won't play is reported; a folder with kit.txt one level down installs
        List<String> problems = Kits.check("title: X\nmel: engine harp, wobble 0.5, flutter 2\nlead: engine pluck\n");
        check(problems.size() == 3 && problems.get(0).contains("harp") && problems.get(1).contains("flutter")
                        && problems.get(2).contains("lead") && Kits.check(mine).isEmpty()
                        && Kits.check("# just a note\n").get(0).startsWith("No parts"),
                "check() names unknown engines, settings and parts, and a file with no parts");
        Path staging = Files.createTempDirectory(dir, ".import-");
        Files.createDirectories(staging.resolve("Wrapped"));
        Files.writeString(staging.resolve("Wrapped").resolve(Kits.FILE), "title: Wrapped\ndrums: engine drums\n");
        int w = Kits.installFolder(staging.toFile());
        check(w == b + 2 && Kits.userKits().get(2).title.equals("Wrapped"), "a zip or folder with kit.txt inside installs");
        sampleKits(dir);
        Kits.userDir = null;
        Kits.refreshChoices();
    }

    /** Sample kits (stage 2, Oct 18): files named in kit.txt, played from the kit's folder, Classic when missing. */
    static void sampleKits(Path dir) throws Exception {
        check(Kits.noteNumber("C4") == 60 && Kits.noteNumber("f#3") == 54 && Kits.noteNumber("Bb2") == 46
                        && Kits.noteNumber("72") == 72 && Kits.noteNumber("piano.wav") == -1,
                "note names: C4 = 60, sharps and flats, plain numbers");
        String text = "title: Keys\nmel: engine sample, sample piano c4.wav C4, sample piano c5.wav 72, attack 2\n"
                + "drums: engine sample, kick kick.wav, ohat open hat.wav\n";
        Kits.Kit k = Kits.parse(text);
        Object[] s0 = k.parts.get("mel").get(1);
        check(s0[1].equals("piano c4.wav") && ((Float) s0[2]) == 60f && k.parts.get("drums").get(2)[1].equals("open hat.wav"),
                "a sample line: the file (spaces allowed) and its root note");
        check(Kits.sampleFile(dir.toFile(), "../x.wav") == null && Kits.sampleFile(dir.toFile(), "a.mp3").getName().endsWith(".wav"),
                "a kit's files stay in its folder; a non-WAV file plays from its decoded copy");
        int a = Kits.install(text);                    // (text only, as a preset would bring it)
        List<String> sent = new ArrayList<>();
        Kits.Out out = new Kits.Out() {
            public void send(String r, String s, float v) { sent.add(r + " " + s + " " + v); }
            public void sendMessage(String r, String s, Object... args) { sent.add(r + " " + s + " " + java.util.Arrays.toString(args)); }
        };
        Kits.send(a, new int[4], true, out);
        check(sent.stream().noneMatch(m -> m.contains("engine 9")) && Kits.missingFiles(text, Kits.dir(a)).size() == 4,
                "a sample kit without its files plays Classic, and says which are missing");
        Path staging = Files.createTempDirectory(dir, ".import-");
        Files.writeString(staging.resolve(Kits.FILE), text);
        for (String f : new String[]{"piano c4.wav", "piano c5.wav", "kick.wav", "open hat.wav"}) {
            Files.write(staging.resolve(f), new byte[]{1});
        }
        int b = Kits.installFolder(staging.toFile());
        sent.clear();
        Kits.send(a, new int[4], true, out);
        String c4 = Kits.dir(a).toPath().resolve("piano c4.wav").toString();
        check(b == a && Kits.missingFiles(text, Kits.dir(a)).isEmpty() && sent.contains("kit_mel sample [" + c4 + ", 60.0]")
                        && sent.contains("kit_drums dsample [3.0, " + Kits.dir(a).toPath().resolve("open hat.wav") + "]")
                        && sent.contains("kit_mel engine 9.0") && sent.indexOf("kit_mel clear 0.0") < sent.indexOf("kit_mel sample [" + c4 + ", 60.0]"),
                "the kit imported with its files fills in the text-only copy: it plays them, cleared first, engine last");
        check(!Kits.check("mel: engine sample, sample a.wav Q9\n").isEmpty() && Kits.check(text).isEmpty(),
                "check() names a root that isn't a note");
    }
}
