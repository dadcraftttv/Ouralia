// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.dadcraftttv.ouralia;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Finds and copies the scenes in a picked folder, through {@link Tree} (the system's document provider in the app,
 * a fake in the tests). The folder can be one scene ("Eargasm.rj") or a folder of scenes ("rjdj_scenes"); each one
 * found goes into the library under its folder's name. Plain Java, so it can be tested off the phone.
 *
 * <p>Review finding 4 (Nov 4): every provider failure (a revoked permission, a provider gone away, a runtime error)
 * ends as a failed scene, never as an exception out of the import thread; one import shares one
 * {@link SceneLibrary.Budget} with the zip path (bytes, files, nesting); provider names are checked before use.
 */
final class SceneTree {

    /** The picked folder, as a provider sees it. Any method may throw: the walk catches it. */
    interface Tree {
        List<Doc> children(String dirId) throws IOException;

        InputStream open(String fileId) throws IOException;

        /** The display name of {@code id}, or null. */
        String name(String id) throws IOException;
    }

    static final class Doc {
        final String id;
        final String name;
        final boolean isDir;

        Doc(String id, String name, boolean isDir) {
            this.id = id;
            this.name = name;
            this.isDir = isDir;
        }
    }

    /** What happened to a folder import. */
    static final class Result {
        final List<SceneLibrary.Scene> imported = new ArrayList<>();
        final List<String> failed = new ArrayList<>();
    }

    private static final int MAX_DEPTH = 2;   // picked folder, a folder of scenes, one more level (finding scenes)

    private final Tree tree;
    private final File scenesDir;
    private final SceneLibrary.Budget budget = new SceneLibrary.Budget();   // (the whole import's)

    private SceneTree(Tree tree, File scenesDir) {
        this.tree = tree;
        this.scenesDir = scenesDir;
    }

    static Result importTree(Tree tree, String rootId, File scenesDir) {
        SceneTree importer = new SceneTree(tree, scenesDir);
        Result result = new Result();
        importer.visit(new Doc(rootId, nameOf(tree, rootId), true), 0, result);
        return result;
    }

    /** The folder's display name, or "scene" when the provider can't say. */
    static String nameOf(Tree tree, String id) {
        try {
            String n = tree.name(id);
            return n != null && !n.isEmpty() ? n : "scene";
        } catch (IOException | RuntimeException e) {
            return "scene";
        }
    }

    /** Copies {@code dir} as a scene if it holds _main.pd; otherwise looks in its sub-folders. */
    private void visit(Doc dir, int depth, Result result) {
        List<Doc> children;
        try {
            children = tree.children(dir.id);
        } catch (IOException | RuntimeException e) {
            result.failed.add(dir.name);
            return;
        }
        boolean isScene = false;
        for (Doc c : children) {
            if (!c.isDir && SceneLibrary.MAIN_PATCH.equals(c.name)) {
                isScene = true;
                break;
            }
        }
        if (isScene) {
            copyScene(dir, result);
        } else if (depth < MAX_DEPTH) {
            for (Doc c : children) {
                if (c.isDir && c.name != null && !c.name.startsWith(".")) {
                    visit(c, depth + 1, result);
                }
            }
        }
    }

    private void copyScene(Doc dir, Result result) {
        File staging = SceneLibrary.newStaging(scenesDir);
        try {
            if (!staging.mkdirs()) {
                throw new IOException("Cannot create " + staging);
            }
            copyInto(dir.id, staging, 0);
            result.imported.add(SceneLibrary.installFolder(staging, dir.name, scenesDir));
        } catch (IOException | RuntimeException e) {
            result.failed.add(dir.name);
        } finally {
            SceneLibrary.deleteRecursively(staging);
        }
    }

    private void copyInto(String dirId, File dest, int depth) throws IOException {
        for (Doc c : tree.children(dirId)) {
            if (c.name == null || c.name.startsWith(".")) {
                continue;   // hidden files and Mac metadata
            }
            File out = SceneLibrary.child(dest, c.name);
            if (c.isDir) {
                if (depth + 1 > SceneLibrary.MAX_NESTING) {
                    throw new IOException("Scene folders are nested too deeply");
                }
                if (!out.mkdirs()) {
                    throw new IOException("Cannot create " + out);
                }
                copyInto(c.id, out, depth + 1);
            } else {
                budget.file(depth);
                try (InputStream in = tree.open(c.id);
                     OutputStream os = new FileOutputStream(out)) {
                    if (in == null) {
                        throw new IOException("Cannot read " + c.name);
                    }
                    byte[] buf = new byte[32 * 1024];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        budget.bytes(n);
                        os.write(buf, 0, n);
                    }
                }
            }
        }
    }
}
