// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.dadcraftttv.ouralia;

import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.json.JSONException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Presets (moved out of MainActivity on Oct 22, ROADMAP 22): the list sheet (load, press and hold to delete),
 * Save, the one-tap snapshot, export and import as JSON files, and Reset everything, the one place settings are ever
 * wiped.
 */
final class PresetUi {
    private final MainActivity a;

    PresetUi(MainActivity activity) {
        a = activity;
    }

    /** Name chosen in the export dialog, used once the file picker returns. */
    private String exportName = "preset";

    /** The presets: built-in, then yours (marked saved); tap one to load it, press and hold one of yours to delete it. */
    void showPresets() {
        List<String> names = new ArrayList<>();
        List<Boolean> isUser = new ArrayList<>();
        for (String n : PresetManager.builtInNames()) {
            names.add(n);
            isUser.add(false);
        }
        for (String n : PresetManager.userNames(a)) {
            names.add(n);
            isUser.add(true);
        }
        String[] labels = new String[names.size()];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = isUser.get(i)
                    ? a.getString(R.string.preset_saved_suffix, names.get(i)) : names.get(i);
        }
        LinearLayout content = new LinearLayout(a);
        content.setOrientation(LinearLayout.VERTICAL);
        content.addView(a.sheetHeading(a.getString(R.string.presets), a.getString(R.string.presets_delete_hint)));
        a.addListRows(content, labels, -1, null, i -> {
            a.closeSheet();
            loadPreset(names.get(i), isUser.get(i));
        });
        for (int i = 0; i < names.size(); i++) {   // child 0 is the heading
            if (isUser.get(i)) {
                String name = names.get(i);
                content.getChildAt(i + 1).setOnLongClickListener(v -> {
                    a.closeSheet();
                    confirmDelete(name);
                    return true;
                });
            }
        }
        a.openSheet(content);
    }

    /** A text field for a dialog: inset like the dialog's title, its underline and cursor in the accent. */
    private View boxed(EditText input) {
        input.setTypeface(a.look.text());
        input.setTextColor(a.look.p.text);
        input.setBackgroundTintList(android.content.res.ColorStateList.valueOf(a.look.p.primary));
        android.widget.FrameLayout box = new android.widget.FrameLayout(a);
        box.setPadding(a.dp(24), a.dp(8), a.dp(24), 0);
        box.addView(input);
        return box;
    }

    private void loadPreset(String name, boolean user) {
        Map<String, Float> values = user
                ? PresetManager.readUser(a, name) : PresetManager.builtIn(name);
        if (values == null) {
            a.toast(R.string.toast_read_failed, name);
            return;
        }
        ParamStore.applyPreset(values);
        a.refreshRows();
        a.toast(R.string.toast_loaded, name);
    }

    /** The one place settings are ever wiped: only when the user taps Reset and confirms. */
    void confirmResetAll() {
        a.new LookDialog()
                .setTitle(R.string.reset_title)
                .setMessage(R.string.reset_message)
                .setPositiveButton(R.string.reset_confirm, (d, w) -> {
                    Worlds.resetSettings();
                    ParamStore.resetAll();
                    ParamStore.save(a);
                    AudioSettings.reset(a);
                    SceneSettings.resetAll(a);
                    if (AudioService.isRunning()) {   // scene, devices and rate are chosen at start
                        a.startForegroundService(new Intent(a, AudioService.class)
                                .setAction(AudioService.ACTION_RESTART));
                    }
                    a.toast(R.string.toast_reset);
                    a.recreate();   // rebuild every control from the fresh settings
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void confirmDelete(String name) {
        a.new LookDialog()
                .setTitle(a.getString(R.string.delete_title, name))
                .setPositiveButton(R.string.delete, (d, w) -> {
                    PresetManager.delete(a, name);
                    a.realmsUi.refreshWorldRows();
                    a.toast(R.string.toast_deleted);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    void showSave() {
        EditText input = new EditText(a);
        input.setSingleLine(true);
        input.setText(R.string.default_preset_name);
        input.selectAll();
        a.new LookDialog()
                .setTitle(R.string.save_title)
                .setView(boxed(input))
                .setPositiveButton(R.string.save, (d, w) -> {
                    try {
                        String used = PresetManager.save(
                                a, input.getText().toString(), ParamStore.snapshot());
                        a.toast(R.string.toast_saved, used);
                        a.realmsUi.refreshWorldRows();
                    } catch (IOException e) {
                        Toast.makeText(a, e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Asks for the preset's name first: it becomes both the file name and the name shown after importing. */
    void startExport() {
        EditText input = new EditText(a);
        input.setSingleLine(true);
        input.setText(R.string.default_preset_name);
        input.selectAll();
        a.new LookDialog()
                .setTitle(R.string.export_title)
                .setView(boxed(input))
                .setPositiveButton(R.string.export, (d, w) -> {
                    exportName = PresetManager.sanitize(input.getText().toString());
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                            .addCategory(Intent.CATEGORY_OPENABLE)
                            .setType("application/json")
                            .putExtra(Intent.EXTRA_TITLE, exportName + ".json");
                    a.startActivityForResult(intent, MainActivity.REQ_EXPORT);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    void startImport() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*");
        a.startActivityForResult(intent, MainActivity.REQ_IMPORT);
    }

    void exportTo(Uri uri) {
        try (OutputStream out = a.getContentResolver().openOutputStream(uri)) {
            if (out == null) {
                throw new IOException(a.getString(R.string.err_open_file));
            }
            out.write(PresetManager.toJson(exportName, ParamStore.snapshot())
                    .getBytes(StandardCharsets.UTF_8));
            a.toast(R.string.toast_exported);
        } catch (IOException | RuntimeException e) {
            a.toast(R.string.toast_export_failed, e.getMessage());
        }
    }

    void importFrom(Uri uri) {
        try (InputStream in = a.getContentResolver().openInputStream(uri)) {
            if (in == null) {
                throw new IOException(a.getString(R.string.err_open_file));
            }
            PresetManager.Preset preset = PresetManager.parse(PresetManager.readAll(in));
            String name = PresetManager.isBuiltInName(PresetManager.sanitize(preset.name))
                    ? a.getString(R.string.imported_name, preset.name) : preset.name;
            String used = PresetManager.save(a, PresetManager.uniqueName(a, name), preset.values);
            ParamStore.applyPreset(preset.values);
            a.refreshRows();
            a.toast(R.string.toast_imported, used);
            a.realmsUi.refreshWorldRows();
        } catch (IOException | JSONException | RuntimeException e) {
            a.toast(R.string.toast_import_failed, e.getMessage());
        }
    }

    /** Saves the sound playing right now as a preset named after the time, to rename later if wanted. */
    void saveSnapshot() {
        String base = new java.text.SimpleDateFormat("EEE H-mm", java.util.Locale.getDefault()).format(new java.util.Date());
        List<String> taken = PresetManager.userNames(a);
        String name = base;
        for (int n = 2; taken.contains(name); n++) {
            name = base + " " + n;
        }
        try {
            String used = PresetManager.save(a, name, ParamStore.snapshot());
            a.toast(R.string.toast_saved, used);
            a.realmsUi.refreshWorldRows();
        } catch (IOException e) {
            a.toast(R.string.toast_save_failed, e.getMessage());
        }
    }
}
