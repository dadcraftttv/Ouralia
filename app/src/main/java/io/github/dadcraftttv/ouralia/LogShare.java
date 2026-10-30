// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.dadcraftttv.ouralia;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Hands the zip of session logs to the mail app the user picks (Send session logs, DadCraft, Oct 30). Not exported:
 * the mail app reads only the one file it was granted, for that one email. Serves nothing but {@code cache/share/}.
 */
public final class LogShare extends ContentProvider {
    static final String FILE = "ouralia-sessions.zip";

    static File file(Context c) {
        return new File(new File(c.getCacheDir(), "share"), FILE);
    }

    static Uri uri(Context c) {
        return Uri.parse("content://" + c.getPackageName() + ".logs/" + FILE);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    private File served(Uri uri) throws FileNotFoundException {
        if (!FILE.equals(uri.getLastPathSegment())) {
            throw new FileNotFoundException(uri.toString());
        }
        return file(getContext());
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        return ParcelFileDescriptor.open(served(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        File f;
        try {
            f = served(uri);
        } catch (IOException e) {
            return null;
        }
        MatrixCursor c = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        c.addRow(new Object[]{FILE, f.length()});
        return c;
    }

    @Override
    public String getType(Uri uri) {
        return "application/zip";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri uri, String selection, String[] args) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] args) {
        throw new UnsupportedOperationException();
    }
}
