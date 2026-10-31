// SPDX-License-Identifier: GPL-3.0-or-later
package android.content;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * The desktop's stand-in for the little of Android's Context that the shared logic uses: a files folder (saved
 * presets) and named settings stores, kept as JSON files.
 */
public class Context {

    public static final int MODE_PRIVATE = 0;

    private final File files;
    private final File config;
    private final Map<String, SharedPreferences> stores = new HashMap<>();

    public Context(File files, File config) {
        this.files = files;
        this.config = config;
    }

    public File getFilesDir() {
        files.mkdirs();
        return files;
    }

    public Context getApplicationContext() {
        return this;
    }

    public synchronized SharedPreferences getSharedPreferences(String name, int mode) {
        return stores.computeIfAbsent(name, n -> new JsonPrefs(new File(config, n + ".json")));
    }

    /** The working name's folders (~/.config/pdscenes, ~/.local/share/pdscenes) become Ouralia's, once, their stores
     *  renamed from pdscenes_* to ouralia_*, so settings and presets carry over the rename. */
    public static void moveFromWorkingName(File old, File now) {
        if (!old.isDirectory() || now.exists() || !old.renameTo(now)) {
            return;
        }
        File[] files = now.listFiles();
        for (File f : files != null ? files : new File[0]) {
            if (f.getName().startsWith("pdscenes_")) {
                f.renameTo(new File(now, "ouralia_" + f.getName().substring("pdscenes_".length())));
            }
        }
    }
}
