// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.dadcraftttv.ouralia;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Imports scenes from a folder the user picked in the system file picker. The folder can be one
 * scene ("Eargasm.rj") or a folder of scenes ("rjdj_scenes"); every scene found is copied into
 * the library. This is only the system document provider's side: the walk, its limits and its
 * error handling are {@link SceneTree}'s.
 */
final class TreeImporter implements SceneTree.Tree {

    private final ContentResolver resolver;
    private final Uri tree;

    private TreeImporter(ContentResolver resolver, Uri tree) {
        this.resolver = resolver;
        this.tree = tree;
    }

    static SceneTree.Result importTree(ContentResolver resolver, Uri tree, File scenesDir) {
        SceneTree.Result result;
        try {
            result = SceneTree.importTree(new TreeImporter(resolver, tree), DocumentsContract.getTreeDocumentId(tree),
                    scenesDir);
        } catch (RuntimeException e) {   // (a tree Uri the provider no longer knows)
            result = new SceneTree.Result();
            result.failed.add(String.valueOf(tree.getLastPathSegment()));
        }
        return result;
    }

    /** The picked folder's own name. */
    static String treeName(ContentResolver resolver, Uri tree) {
        try {
            return SceneTree.nameOf(new TreeImporter(resolver, tree), DocumentsContract.getTreeDocumentId(tree));
        } catch (RuntimeException e) {
            return "scene";
        }
    }

    @Override
    public List<SceneTree.Doc> children(String parentId) throws IOException {
        List<SceneTree.Doc> result = new ArrayList<>();
        Uri uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId);
        String[] cols = {DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE};
        try (Cursor c = resolver.query(uri, cols, null, null, null)) {
            if (c == null) {
                throw new IOException("Cannot list the folder");
            }
            while (c.moveToNext()) {
                boolean isDir = DocumentsContract.Document.MIME_TYPE_DIR.equals(c.getString(2));
                result.add(new SceneTree.Doc(c.getString(0), c.getString(1), isDir));
            }
        } catch (RuntimeException e) {   // SecurityException (permission gone), IllegalArgumentException, ...
            throw new IOException("Cannot list the folder", e);
        }
        return result;
    }

    @Override
    public InputStream open(String fileId) throws IOException {
        try {
            return resolver.openInputStream(DocumentsContract.buildDocumentUriUsingTree(tree, fileId));
        } catch (RuntimeException e) {
            throw new IOException("Cannot read a file", e);
        }
    }

    @Override
    public String name(String docId) throws IOException {
        Uri uri = DocumentsContract.buildDocumentUriUsingTree(tree, docId);
        try (Cursor c = resolver.query(uri,
                new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
            return c != null && c.moveToFirst() ? c.getString(0) : null;
        } catch (RuntimeException e) {
            throw new IOException("Cannot read the folder's name", e);
        }
    }
}
