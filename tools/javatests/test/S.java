package io.github.dadcraftttv.ouralia;
import java.io.*; import java.nio.file.*; import java.util.*; import java.util.zip.*;
public class S {
    static int fails = 0;
    static void check(boolean c, String m) { System.out.println((c ? "ok:   " : "FAIL: ") + m); if (!c) fails++; }
    /** A document provider in memory: folders, files, and which calls fail. */
    static class Fake implements SceneTree.Tree {
        Map<String, SceneTree.Doc> docs = new HashMap<>(); Map<String, List<String>> kids = new HashMap<>(); Map<String, String> data = new HashMap<>();
        Set<String> failList = new HashSet<>(), failOpen = new HashSet<>(); boolean failName;
        void dir(String id, String name) { docs.put(id, new SceneTree.Doc(id, name, true)); }
        void file(String id, String name, String content) { docs.put(id, new SceneTree.Doc(id, name, false)); data.put(id, content); }
        void under(String p, String c) { kids.computeIfAbsent(p, k -> new ArrayList<>()).add(c); }
        public List<SceneTree.Doc> children(String id) {
            if (failList.contains(id)) throw new SecurityException("permission revoked");
            List<SceneTree.Doc> out = new ArrayList<>(); for (String k : kids.getOrDefault(id, List.of())) out.add(docs.get(k)); return out;
        }
        public InputStream open(String id) { if (failOpen.contains(id)) throw new IllegalStateException("provider gone"); return new ByteArrayInputStream(data.get(id).getBytes()); }
        public String name(String id) { if (failName) throw new SecurityException("no"); return docs.get(id).name; }
    }
    static byte[] zip(String... nameContent) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(b)) {
            for (int i = 0; i < nameContent.length; i += 2) {
                z.putNextEntry(new ZipEntry(nameContent[i]));
                z.write(nameContent[i + 1].getBytes("UTF-8")); z.closeEntry();
            }
        }
        return b.toByteArray();
    }
    static final String PLIST = "<plist><dict><key>info</key><dict><key>name</key><string>Echo &amp; Co</string>"
            + "<key>author</key><string>Jane</string><key>description</key><string>Hi</string></dict></dict></plist>";
    public static void main(String[] a) throws Exception {
        File dir = Files.createTempDirectory("scenes").toFile();
        SceneLibrary.Scene s = SceneLibrary.importZip(new ByteArrayInputStream(zip(
                "Foo.rj/_main.pd", "#N canvas;", "Foo.rj/Info.plist", PLIST, "Foo.rj/rj/x.pd", "x",
                "__MACOSX/Foo.rj/._main.pd", "junk")), "ignored", dir);
        check(s.dirName.equals("Foo.rj") && s.name.equals("Echo & Co") && s.author.equals("Jane"), "folder zip: " + s.dirName + " / " + s.name + " / " + s.author);
        check(new File(dir, "Foo.rj/rj/x.pd").isFile() && !new File(dir, "__MACOSX").exists(), "nested files kept, Mac junk dropped");
        SceneLibrary.Scene f = SceneLibrary.importZip(new ByteArrayInputStream(zip("_main.pd", "#N canvas;", "a.wav", "w")), "My Scene.rj.zip", dir);
        check(f.dirName.equals("My Scene.rj") && f.name.equals("My Scene"), "flat zip named from the file: " + f.dirName);
        SceneLibrary.Scene w = SceneLibrary.importZip(new ByteArrayInputStream(zip("wrap/Deep.rj/_main.pd", "#N canvas;")), "x", dir);
        check(w.dirName.equals("Deep.rj"), "scene one folder down: " + w.dirName);
        SceneLibrary.importZip(new ByteArrayInputStream(zip("Foo.rj/_main.pd", "#N canvas;")), "x", dir);
        check(!new File(dir, "Foo.rj/rj").exists() && SceneLibrary.find(dir, "Foo.rj").name.equals("Foo"), "re-import replaces the old copy; no plist falls back to folder name");
        check(SceneLibrary.list(dir).size() == 3 && Arrays.stream(dir.list()).noneMatch(n -> n.startsWith(".import")), "list finds 3 scenes, no staging left over");
        SceneLibrary.Scene low = SceneLibrary.importZip(new ByteArrayInputStream(zip("Low.rj/_main.pd", "#N canvas;", "Low.rj/info.plist", PLIST)), "x", dir);
        check(low.name.equals("Echo & Co") && low.author.equals("Jane"), "a lowercase info.plist is read too (PdParty takes it): " + low.name);
        SceneLibrary.delete(low);
        try { SceneLibrary.importZip(new ByteArrayInputStream(zip("readme.txt", "hi")), "x", dir); check(false, "zip without _main.pd rejected"); }
        catch (IOException e) { check(e.getMessage().contains("_main.pd"), "zip without _main.pd rejected: " + e.getMessage()); }
        try { SceneLibrary.importZip(new ByteArrayInputStream(zip("../evil.txt", "x", "_main.pd", "y")), "x", dir); check(false, "zip-slip rejected"); }
        catch (IOException e) { check(!new File(dir.getParentFile(), "evil.txt").exists(), "zip-slip rejected: " + e.getMessage()); }
        try { SceneLibrary.importZip(new ByteArrayInputStream("not a zip".getBytes()), "x", dir); check(false, "garbage rejected"); }
        catch (IOException e) { check(true, "non-zip rejected: " + e.getMessage()); }
        check(SceneLibrary.find(dir, "../x") == null && SceneLibrary.find(dir, "") == null && SceneLibrary.find(dir, "nope.rj") == null, "find() refuses odd or missing names");
        SceneLibrary.delete(SceneLibrary.find(dir, "Foo.rj"));
        check(SceneLibrary.find(dir, "Foo.rj") == null, "delete removes the scene");
        check(SceneLibrary.folderName("a/b:c").equals("a_b_c.rj") && SceneLibrary.folderName("").equals("scene.rj") && SceneLibrary.folderName(".hid.zip").equals("scene_hid.rj"), "folderName cleans: " + SceneLibrary.folderName("a/b:c") + ", " + SceneLibrary.folderName(""));
        // ---- review findings 3 and 4 (Nov 4): bounded metadata, shared import limits, provider failures ----
        String big = "<plist><dict><key>name</key><string>" + "x".repeat(SceneLibrary.MAX_PLIST_BYTES) + "</string></dict></plist>";
        SceneLibrary.Scene bp = SceneLibrary.importZip(new ByteArrayInputStream(zip("Big.rj/_main.pd", "#N canvas;", "Big.rj/Info.plist", big)), "x", dir);
        check(bp.name.equals("Big"), "an oversized Info.plist is not read: the folder name instead (" + bp.name.length() + " chars)");
        String longName = "<plist><dict><key>name</key><string>" + "N".repeat(500) + "</string><key>description</key><string>"
                + "d".repeat(5000) + "</string></dict></plist>";
        SceneLibrary.Scene ln = SceneLibrary.importZip(new ByteArrayInputStream(zip("Long.rj/_main.pd", "#N canvas;", "Long.rj/Info.plist", longName)), "x", dir);
        check(ln.name.length() <= SceneLibrary.MAX_NAME_CHARS && ln.description.length() <= SceneLibrary.MAX_DESCRIPTION_CHARS,
                "shown name and description are bounded (" + ln.name.length() + ", " + ln.description.length() + ")");
        String[] many = new String[2 * (SceneLibrary.MAX_FILES + 1) + 2];
        many[0] = "Long.rj/_main.pd"; many[1] = "#N canvas;";
        for (int i = 0; i <= SceneLibrary.MAX_FILES; i++) { many[2 + 2 * i] = "Long.rj/f" + i; many[3 + 2 * i] = ""; }
        try { SceneLibrary.importZip(new ByteArrayInputStream(zip(many)), "x", dir); check(false, "too many files rejected"); }
        catch (IOException e) { check(SceneLibrary.find(dir, "Long.rj").description.length() > 0, "too many files rejected, the old copy kept: " + e.getMessage()); }
        String deep = "Deep2.rj/" + "d/".repeat(SceneLibrary.MAX_NESTING + 1) + "x.pd";
        try { SceneLibrary.importZip(new ByteArrayInputStream(zip("Deep2.rj/_main.pd", "#N canvas;", deep, "x")), "x", dir); check(false, "deep nesting rejected"); }
        catch (IOException e) { check(SceneLibrary.find(dir, "Deep2.rj") == null, "too deep a zip rejected: " + e.getMessage()); }
        new File(dir, ".import-1/Ghost.rj").mkdirs(); Files.write(new File(dir, ".import-1/_main.pd").toPath(), "x".getBytes());
        check(SceneLibrary.list(dir).stream().noneMatch(x -> x.dirName.startsWith(".")), "a leftover import folder is never listed as a scene");
        // Folder imports through a fake provider.
        Fake t = new Fake();
        t.dir("root", "Picked"); t.dir("good", "Good.rj"); t.file("g1", "_main.pd", "#N canvas;"); t.under("root", "good"); t.under("good", "g1");
        t.dir("bad", "Bad.rj"); t.file("b1", "_main.pd", "#N canvas;"); t.under("root", "bad"); t.under("bad", "b1"); t.failOpen.add("b1");
        t.dir("lost", "Lost"); t.under("root", "lost"); t.failList.add("lost");
        t.dir("evil", "Evil.rj"); t.file("e1", "_main.pd", "x"); t.file("e2", "x/../../escaped.txt", "x"); t.under("root", "evil"); t.under("evil", "e1"); t.under("evil", "e2");
        SceneTree.Result r = SceneTree.importTree(t, "root", dir);
        check(r.imported.size() == 1 && r.imported.get(0).dirName.equals("Good.rj") && r.failed.containsAll(List.of("Bad.rj", "Lost", "Evil.rj")),
                "provider failures become failed scenes, the good one still imports: " + r.failed);
        check(!new File(dir, "escaped.txt").exists() && SceneLibrary.find(dir, "Evil.rj") == null, "a provider name with ../ is refused");
        Fake d = new Fake(); d.dir("root", "Nest.rj"); d.file("m", "_main.pd", "x"); d.under("root", "m");
        String parent = "root";
        for (int i = 0; i <= SceneLibrary.MAX_NESTING; i++) { d.dir("n" + i, "n" + i); d.under(parent, "n" + i); parent = "n" + i; }
        check(SceneTree.importTree(d, "root", dir).failed.equals(List.of("Nest.rj")), "too deep a folder is refused");
        Fake gone = new Fake(); gone.failList.add("root"); gone.failName = true;
        SceneTree.Result g = SceneTree.importTree(gone, "root", dir);
        check(g.imported.isEmpty() && g.failed.equals(List.of("scene")), "a provider that refuses everything fails quietly: " + g.failed);
        java.util.List<Object[]> st = java.util.List.of(new Object[]{"rjsave-ex-one", "a", 30f}, new Object[]{"x", "1.5", -2.25f});
        java.util.List<Object[]> back = SceneState.decode(SceneState.encode(st));
        check(back.size() == 2 && Arrays.equals(back.get(0), st.get(0)) && Arrays.equals(back.get(1), st.get(1)),
                "a scene's saved state survives the text (a word that looks like a number stays a word): " + SceneState.encode(st).replace("\n", " | "));
        check(SceneState.decode("junk\ns:ok\tf:1\n").size() == 1 && SceneState.decode(null).isEmpty(), "a spoilt line is skipped");
        System.out.println(fails == 0 ? "ALL PASSED" : fails + " FAILED"); System.exit(fails == 0 ? 0 : 1);
    }
}
