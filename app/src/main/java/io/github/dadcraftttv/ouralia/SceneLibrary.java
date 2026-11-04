// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.dadcraftttv.ouralia;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * The scenes the user has imported. A scene is a folder holding {@code _main.pd} (plus samples,
 * abstractions, and an optional {@code Info.plist} with its name and author). Scenes live side by
 * side in one directory; the player's own abstractions ({@code soundinput}, {@code soundoutput})
 * sit in a sibling folder called {@code pd}, which is where scenes look for them
 * ({@code declare -path ../pd}).
 */
final class SceneLibrary {

    /** Name of the Pd patch every scene starts from. */
    static final String MAIN_PATCH = "_main.pd";
    /** Folder next to the scenes that holds the player-provided abstractions. */
    static final String LIB_DIR = "pd";

    private static final long MAX_UNZIPPED_BYTES = 512L * 1024 * 1024;
    // One import's limits, the zip and the folder paths alike (review findings 3 and 4, Nov 4). RjDj's own scenes hold
    // tens of files a couple of folders deep; these leave room and stop a runaway import before it fills the phone.
    static final int MAX_FILES = 4096, MAX_NESTING = 12;
    static final int MAX_PLIST_BYTES = 64 * 1024;   // Info.plist is a few hundred bytes; past this it is not read
    static final int MAX_NAME_CHARS = 80, MAX_DESCRIPTION_CHARS = 400;

    /** What one import has used so far: bytes, files and how deep. Exceeding any limit fails the import. */
    static final class Budget {
        private long bytes;
        private int files;

        void file(int depth) throws IOException {
            if (++files > MAX_FILES) {
                throw new IOException("Scene has too many files");
            }
            if (depth > MAX_NESTING) {
                throw new IOException("Scene folders are nested too deeply");
            }
        }

        void bytes(int n) throws IOException {
            bytes += n;
            if (bytes > MAX_UNZIPPED_BYTES) {
                throw new IOException("Scene is too large");
            }
        }
    }

    /** {@code dest/name} for a name from a zip-free source (a folder provider): one plain name that stays inside
     *  {@code dest}, or an IOException. */
    static File child(File dest, String name) throws IOException {
        if (name == null || name.isEmpty() || !isPlainName(name) || name.indexOf('\0') >= 0) {
            throw new IOException("Unsafe file name: " + name);
        }
        File out = new File(dest, name);
        if (!out.getCanonicalPath().startsWith(dest.getCanonicalPath() + File.separator)) {
            throw new IOException("Unsafe file name: " + name);
        }
        return out;
    }

    /** One imported scene. {@code dirName} is the folder name and doubles as its stable id. */
    static final class Scene {
        final String dirName;
        final File dir;
        final String name;
        final String author;
        final String description;

        Scene(String dirName, File dir, String name, String author, String description) {
            this.dirName = dirName;
            this.dir = dir;
            this.name = name;
            this.author = author;
            this.description = description;
        }

        File mainPatch() {
            return new File(dir, MAIN_PATCH);
        }
    }

    private SceneLibrary() { }

    /** Imported scenes, sorted by name. */
    static List<Scene> list(File scenesDir) {
        List<Scene> result = new ArrayList<>();
        File[] children = scenesDir.listFiles();
        if (children != null) {
            for (File dir : children) {
                // (a dot folder is an import in progress or one left by a crash: never a scene, folderName() sees to it)
                if (dir.isDirectory() && !dir.getName().startsWith(".") && new File(dir, MAIN_PATCH).isFile()) {
                    result.add(describe(dir));
                }
            }
        }
        Collections.sort(result, (a, b) -> a.name.compareToIgnoreCase(b.name));
        return result;
    }

    /** The scene with this folder name, or null if it is gone. */
    static Scene find(File scenesDir, String dirName) {
        if (dirName == null || dirName.isEmpty() || !isPlainName(dirName)) {
            return null;
        }
        File dir = new File(scenesDir, dirName);
        return new File(dir, MAIN_PATCH).isFile() ? describe(dir) : null;
    }

    static void delete(Scene scene) {
        deleteRecursively(scene.dir);
    }

    // ---- built-in scenes ----

    /** Assets folder holding the scenes shipped inside the app (the four official RjDj example scenes). */
    static final String BUILTIN_ASSETS = "builtin_scenes";
    private static final String KEY_BUILTIN_DONE = "builtin_installed";

    /**
     * Copies each shipped scene into {@code scenesDir} once. A scene the user later deletes stays deleted;
     * a scene added in a later app version arrives on the next start. "Reset everything" forgets what was
     * installed, so the shipped scenes come back. A folder that already exists under the same name
     * (the user imported the same scene) is left alone.
     */
    static void installBuiltIns(Context context, File scenesDir) {
        SharedPreferences prefs = context.getSharedPreferences("ouralia_scene", Context.MODE_PRIVATE);
        Set<String> done = new HashSet<>(prefs.getStringSet(KEY_BUILTIN_DONE, new HashSet<String>()));
        try {
            String[] names = context.getAssets().list(BUILTIN_ASSETS);
            if (names == null) {
                return;
            }
            if (!scenesDir.exists() && !scenesDir.mkdirs()) {
                return;
            }
            boolean changed = false;
            for (String name : names) {
                if (done.contains(name)) {
                    continue;
                }
                File target = new File(scenesDir, name);
                if (!target.exists()) {
                    File staging = newStaging(scenesDir);
                    try {
                        AudioService.copyAssetDir(context.getAssets(), BUILTIN_ASSETS + "/" + name, staging);
                        if (!staging.renameTo(target)) {
                            continue;   // try again next start
                        }
                    } finally {
                        deleteRecursively(staging);
                    }
                }
                done.add(name);
                changed = true;
            }
            if (changed) {
                prefs.edit().putStringSet(KEY_BUILTIN_DONE, done).apply();
            }
        } catch (IOException e) {
            // Nothing to show; the scenes are a bonus. The next start tries again.
        }
    }

    // ---- importing ----

    /**
     * Unpacks a zipped scene into {@code scenesDir}. The zip may hold the scene folder itself
     * ("Foo.rj/_main.pd") or the scene's files directly ("_main.pd"); the folder is found by looking
     * for the shallowest {@code _main.pd}. An existing scene with the same name is replaced.
     *
     * @param fallbackName used as the scene's folder name when the zip has no folder of its own
     *                     (typically the file name the user picked, without its extension)
     */
    static Scene importZip(InputStream zip, String fallbackName, File scenesDir) throws IOException {
        File staging = newStaging(scenesDir);
        try {
            unzip(zip, staging);
            return installFolder(staging, fallbackName, scenesDir);
        } finally {
            deleteRecursively(staging);
        }
    }

    /** A fresh, unused folder name inside {@code scenesDir} for unpacking or copying into. */
    static File newStaging(File scenesDir) {
        return new File(scenesDir, ".import-" + System.nanoTime());
    }

    /**
     * Moves the scene found inside {@code staging} into place under its final name, replacing any scene
     * of the same name. The caller deletes {@code staging} afterwards.
     */
    static Scene installFolder(File staging, String fallbackName, File scenesDir) throws IOException {
        File root = findSceneRoot(staging);
        if (root == null) {
            throw new IOException("No " + MAIN_PATCH + " found. Is this a scene?");
        }
        String name = root.equals(staging) ? fallbackName : root.getName();
        File target = new File(scenesDir, folderName(name));
        // A scene of the same name is set aside, not deleted, until the new one is in place (review finding 3).
        File old = target.exists() ? new File(scenesDir, ".replaced-" + System.nanoTime()) : null;
        if (old != null && !target.renameTo(old)) {
            throw new IOException("Could not replace the scene");
        }
        if (!root.renameTo(target)) {
            if (old != null) {
                old.renameTo(target);
            }
            throw new IOException("Could not store the scene");
        }
        if (old != null) {
            deleteRecursively(old);
        }
        return describe(target);
    }

    static void unzip(InputStream in, File dest) throws IOException {
        if (!dest.mkdirs()) {
            throw new IOException("Cannot create " + dest);
        }
        String destPath = dest.getCanonicalPath() + File.separator;
        Budget budget = new Budget();
        byte[] buf = new byte[16 * 1024];
        try (ZipInputStream zin = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                String entryName = entry.getName();
                File out = new File(dest, entryName);
                // Refuse "../" paths that would write outside the scene folder.
                if (!out.getCanonicalPath().startsWith(destPath)) {
                    throw new IOException("Unsafe path in zip: " + entryName);
                }
                if (entryName.startsWith("__MACOSX/") || entryName.contains("/.")
                        || entryName.startsWith(".")) {
                    continue;   // Mac resource forks and hidden files
                }
                if (entry.isDirectory()) {
                    out.mkdirs();
                    continue;
                }
                budget.file(entryName.split("/").length - 1);
                File parent = out.getParentFile();
                if (parent != null) {
                    parent.mkdirs();
                }
                try (OutputStream os = new FileOutputStream(out)) {
                    int n;
                    while ((n = zin.read(buf)) > 0) {
                        budget.bytes(n);
                        os.write(buf, 0, n);
                    }
                }
            }
        }
    }

    /** The folder holding the shallowest _main.pd under {@code dir}, or null. */
    private static File findSceneRoot(File dir) {
        if (new File(dir, MAIN_PATCH).isFile()) {
            return dir;
        }
        File[] children = dir.listFiles(File::isDirectory);
        if (children == null) {
            return null;
        }
        Arrays.sort(children);
        // Breadth first: check every child before going deeper, so "Foo.rj/" beats "Foo.rj/sub/".
        for (File child : children) {
            if (new File(child, MAIN_PATCH).isFile()) {
                return child;
            }
        }
        for (File child : children) {
            File found = findSceneRoot(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    // ---- scene details ----

    private static Scene describe(File dir) {
        String name = null;
        String author = "";
        String description = "";
        File plist = infoPlist(dir);
        if (plist != null) {
            try {
                String xml = new String(readAll(plist, MAX_PLIST_BYTES), StandardCharsets.UTF_8);
                name = clip(plistString(xml, "name"), MAX_NAME_CHARS);
                author = clip(orEmpty(plistString(xml, "author")), MAX_NAME_CHARS);
                description = clip(orEmpty(plistString(xml, "description")), MAX_DESCRIPTION_CHARS);
            } catch (IOException e) {
                // Fall through to the folder name.
            }
        }
        if (name == null || name.trim().isEmpty()) {
            name = stripSuffix(dir.getName());
        }
        return new Scene(dir.getName(), dir, name.trim(), author.trim(), description.trim());
    }

    /**
     * The scene's Info.plist, in any capitals (PdParty also takes "info.plist"; Android's file names care about case),
     * or null. Every scene in the RjDj archives uses Info.plist; the "index.rdf" in our early notes was never a thing.
     */
    static File infoPlist(File dir) {
        File exact = new File(dir, "Info.plist");
        if (exact.isFile()) {
            return exact;
        }
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isFile() && f.getName().equalsIgnoreCase("Info.plist")) {
                    return f;
                }
            }
        }
        return null;
    }

    /** First {@code <string>} after {@code <key>key</key>}. Info.plist is simple enough for this. */
    static String plistString(String xml, String key) {
        Matcher m = Pattern.compile(
                "<key>\\s*" + Pattern.quote(key) + "\\s*</key>\\s*<string>(.*?)</string>",
                Pattern.DOTALL).matcher(xml);
        return m.find() ? unescapeXml(m.group(1)) : null;
    }

    private static String unescapeXml(String s) {
        return s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&apos;", "'").replace("&amp;", "&");
    }

    private static String clip(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max - 1).trim() + "\u2026";
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }

    // ---- file names ----

    /** Folder name for a scene: keeps a ".rj" suffix (some scenes expect it) and drops odd characters. */
    static String folderName(String raw) {
        String base = stripSuffix(raw == null ? "" : raw).replaceAll("[^A-Za-z0-9 _.()-]", "_").trim();
        if (base.isEmpty() || base.startsWith(".")) {
            base = "scene" + base.replace(".", "_");
        }
        return base + ".rj";
    }

    private static String stripSuffix(String name) {
        // "Foo.rj.zip" loses both suffixes.
        boolean stripped = true;
        while (stripped) {
            stripped = false;
            String lower = name.toLowerCase();
            for (String ext : new String[]{".rj", ".rjz", ".zip"}) {
                if (lower.endsWith(ext)) {
                    name = name.substring(0, name.length() - ext.length());
                    stripped = true;
                }
            }
        }
        return name;
    }

    private static boolean isPlainName(String name) {
        return !name.contains("/") && !name.contains("\\") && !name.equals("..") && !name.equals(".");
    }

    // ---- small file helpers ----

    /** The whole file, or an IOException when it is longer than {@code max} (nothing big is ever held in memory). */
    private static byte[] readAll(File f, int max) throws IOException {
        if (f.length() > max) {
            throw new IOException("Too large: " + f.getName());
        }
        try (InputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                if (out.size() + n > max) {
                    throw new IOException("Too large: " + f.getName());
                }
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }

    static void deleteRecursively(File f) {
        File[] children = f.listFiles();
        if (children != null) {
            for (File c : children) {
                deleteRecursively(c);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }
}
