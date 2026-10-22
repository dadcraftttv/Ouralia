// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.dadcraftttv.ouralia;

import android.content.Intent;
import android.net.Uri;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * The Music tab's Kits card (moved out of MainActivity on Oct 22, ROADMAP 22): which of your own kits are
 * installed, importing one (a kit.txt, a zip or a folder, checked before it plays) and deleting one.
 */
final class KitUi {
    private final MainActivity a;

    KitUi(MainActivity activity) {
        a = activity;
    }

    private TextView kitStatus;

    /** Music tab: your own kits (Kits: text files, folders or zips), imported into the Kit menus. */
    void fillKitsSection(LinearLayout body) {
        kitStatus = new TextView(a);
        kitStatus.setTextSize(15);
        kitStatus.setPadding(0, a.dp(4), 0, a.dp(4));
        body.addView(kitStatus);
        LinearLayout row = a.newButtonRow(body);
        a.addButton(row, R.string.kit_import, () -> a.startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), MainActivity.REQ_IMPORT_KIT));
        a.addButton(row, R.string.kit_import_folder, () -> a.startActivityForResult(
                new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), MainActivity.REQ_IMPORT_KIT_FOLDER));
        LinearLayout row2 = a.newButtonRow(body);
        a.addButton(row2, R.string.kit_howto, () -> a.new LookDialog().setTitle(R.string.kit_howto)
                .setMessage(R.string.kit_howto_text).setPositiveButton(R.string.close, null).show());
        a.addButton(row2, R.string.kit_delete, this::deleteKit);
        updateKitStatus();
    }

    void updateKitStatus() {
        if (kitStatus == null) {
            return;
        }
        List<String> titles = new ArrayList<>();
        for (Kits.UserKit u : Kits.userKits()) {
            titles.add(u.title);
        }
        kitStatus.setText(titles.isEmpty() ? a.getString(R.string.kit_none_status)
                : a.getString(R.string.kit_status, String.join(", ", titles)));
    }

    private void deleteKit() {
        List<Kits.UserKit> kits = Kits.userKits();
        if (kits.isEmpty()) {
            a.toast(R.string.kit_none_to_delete);
            return;
        }
        String[] items = new String[kits.size()];
        for (int i = 0; i < items.length; i++) {
            items[i] = kits.get(i).title;
        }
        a.new LookDialog().setTitle(R.string.kit_delete).setItems(items, (d, which) ->
                a.new LookDialog().setMessage(a.getString(R.string.kit_delete_confirm, items[which]))
                        .setPositiveButton(R.string.kit_delete, (d2, w2) -> {
                            int index = Kits.BUILT_IN.length + which;
                            Kits.delete(index);
                            Kits.refreshChoices();
                            ParamStore.kitDeleted(index);
                            updateKitStatus();
                            a.settingRows.refreshRows();
                        }).setNegativeButton(android.R.string.cancel, null).show())
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    /** Copies a kit in (a kit.txt, a zip or a folder) off the main thread, then plays it as the Kit. */
    void importKitFrom(Uri uri, boolean folder) {
        String name = folder ? "" : a.displayName(uri).toLowerCase(java.util.Locale.ROOT);
        new Thread(() -> {
            File kitsDir = Kits.userDir;
            File staging = null;
            try {
                if (kitsDir == null || (!kitsDir.isDirectory() && !kitsDir.mkdirs())) {
                    throw new IOException("Cannot create the kits folder");
                }
                int index;
                if (folder) {
                    staging = SceneLibrary.newStaging(kitsDir);
                    TreeImporter.copyAll(a.getContentResolver(), uri, staging);
                    index = Kits.installFolder(staging);
                } else {
                    try (InputStream in = a.getContentResolver().openInputStream(uri)) {
                        if (in == null) {
                            throw new IOException(a.getString(R.string.err_open_file));
                        }
                        if (name.endsWith(".zip")) {
                            staging = AudioFiles.unzipToStaging(in, kitsDir);
                            index = Kits.installFolder(staging);
                        } else {
                            String text = PresetManager.readAll(in);   // (a kit.txt: at most 64 KB)
                            if (!Kits.hasParts(text)) {
                                throw new IOException(Kits.check(text).get(0));
                            }
                            index = Kits.install(text);
                        }
                    }
                }
                if (index < 0) {
                    throw new IOException("Cannot add the kit");
                }
                String text = Kits.text(index);
                List<String> problems = text == null ? new ArrayList<>() : Kits.check(text);
                if (text != null) {
                    for (String f : Kits.missingFiles(text, Kits.dir(index))) {
                        problems.add(a.getString(R.string.kit_missing_file, f));
                    }
                }
                a.runOnUiThread(() -> {
                    Kits.refreshChoices();
                    ParamStore.set(Kits.ID, index);
                    updateKitStatus();
                    a.settingRows.refreshRows();
                    String title = Params.byId(Kits.ID).choices[index];
                    String msg = a.getString(R.string.kit_imported, title);
                    if (!problems.isEmpty()) {
                        msg += "\n\n" + a.getString(R.string.kit_warnings) + "\n\u2022 " + String.join("\n\u2022 ", problems);
                    }
                    a.new LookDialog().setTitle(R.string.kit_import_done).setMessage(msg)
                            .setPositiveButton(R.string.close, null).show();
                });
            } catch (IOException | RuntimeException e) {
                String why = e.getMessage();
                a.runOnUiThread(() -> a.new LookDialog().setTitle(R.string.kit_import_failed)
                        .setMessage(why).setPositiveButton(R.string.close, null).show());
            } finally {
                if (staging != null) {
                    SceneLibrary.deleteRecursively(staging);
                }
            }
        }, "kit-import").start();
    }
}
