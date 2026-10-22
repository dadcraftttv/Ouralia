// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.dadcraftttv.ouralia;

import android.content.Intent;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import org.puredata.core.PdBase;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * The Scenes tab (moved out of MainActivity on Oct 22, ROADMAP 22): the scene playing, the picker, importing a
 * scene (a zip or a folder), the scene's levels, and the pad. The pad is shared: with no scene it is the Sound tab's
 * XY pad for the movers, with one it moves here and sends the scene "#touch".
 */
final class SceneUi {
    private final MainActivity a;
    private TextView sceneLabel;
    private LinearLayout scenePadSlot;   // where the pad sits on the Scenes tab (the scene's own pad)
    private ScenePad scenePad;
    private LinearLayout sceneLevels;
    private TextView padHint;

    SceneUi(MainActivity activity) {
        a = activity;
    }

    /** Fills the Scenes tab. */
    void build(LinearLayout scenes) {
        Styler look = a.look;
        // the scene playing and its buttons, on a card
        LinearLayout sceneCard = new LinearLayout(a);
        sceneCard.setOrientation(LinearLayout.VERTICAL);
        look.card(sceneCard);
        sceneCard.setPadding(a.dp(Look.CARD_PAD), a.dp(16), a.dp(Look.CARD_PAD), a.dp(14));
        LinearLayout.LayoutParams sceneCardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sceneCardParams.setMargins(0, 0, 0, a.dp(Look.CARD_GAP));
        scenes.addView(sceneCard, sceneCardParams);
        sceneLabel = new TextView(a);
        sceneLabel.setTypeface(look.heading());
        sceneLabel.setTextSize(Look.TEXT_HEADING);
        sceneLabel.setTextColor(look.p.text);
        sceneLabel.setPadding(a.dp(2), 0, 0, a.dp(8));
        sceneCard.addView(sceneLabel);
        // Choose scene: one wide button (the list sheet); importing: two chips under it
        LinearLayout sceneRow = a.newButtonRow(sceneCard);
        Button choose = a.addButton(sceneRow, R.string.choose_scene, this::showScenes);
        choose.setBackground(look.choiceTile(a.getDrawable(R.drawable.ic_chevron_down).mutate()));
        choose.setTextColor(look.p.text);
        choose.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        choose.setPadding(a.dp(16), 0, a.dp(40), 0);
        LinearLayout importRow = a.newButtonRow(sceneCard);
        a.chip(importRow, R.drawable.ic_download, R.string.import_scene, this::startImportScene);
        a.chip(importRow, R.drawable.ic_folder, R.string.import_scene_folder, this::startImportSceneFolder);
        scenePadSlot = new LinearLayout(a);
        scenePadSlot.setOrientation(LinearLayout.VERTICAL);
        scenes.addView(scenePadSlot);

        scenePad = new ScenePad(a);
        scenePad.setContentDescription(a.getString(R.string.pad_description));
        scenePad.setListener(this::sendTouch);
        scenePad.setLook(look.p);
        padHint = new TextView(a);
        padHint.setText(R.string.pad_hint);
        look.hint(padHint);
        padHint.setPadding(a.dp(4), a.dp(8), a.dp(4), 0);

        sceneLevels = new LinearLayout(a);
        sceneLevels.setOrientation(LinearLayout.VERTICAL);
        addLevelSlider(sceneLevels, R.string.scene_volume, SceneSettings.volume(a), v -> {
            SceneSettings.setVolume(a, v);
            sendSceneLevelsIfRunning();
        });
        addLevelSlider(sceneLevels, R.string.scene_mic_volume, SceneSettings.micVolume(a), v -> {
            SceneSettings.setMicVolume(a, v);
            sendSceneLevelsIfRunning();
        });
        Switch timing = new Switch(a);
        timing.setText(R.string.scene_rjdj_timing);
        look.label(timing);
        look.switchTint(timing);
        timing.setSwitchPadding(a.dp(16));
        timing.setMinHeight(a.dp(52));
        timing.setChecked(SceneSettings.rjdjTiming(a));
        timing.setOnCheckedChangeListener((b, on) -> {
            SceneSettings.setRjdjTiming(a, on);
            if (AudioService.isRunning()) {   // the rate is chosen when the engine starts
                a.startForegroundService(new Intent(a, AudioService.class).setAction(AudioService.ACTION_RESTART));
            }
        });
        sceneLevels.addView(timing);
        TextView hint = new TextView(a);
        hint.setText(R.string.scene_headphones_hint);
        look.hint(hint);
        hint.setPadding(a.dp(4), a.dp(6), a.dp(4), 0);
        sceneLevels.addView(hint);
        scenes.addView(sceneLevels);
    }

    void update() {
        SceneLibrary.Scene scene = SceneSettings.current(a);
        sceneLabel.setText(a.getString(R.string.scene_current,
                scene != null ? scene.name : a.getString(R.string.scene_built_in)));
        // a scene replaces the built-in sound: its tabs say so instead of showing controls that do nothing
        for (int t : new int[]{MainActivity.TAB_SOUND, MainActivity.TAB_MUSIC, MainActivity.TAB_REALMS}) {
            a.tabBodies[t].setVisibility(scene != null ? View.GONE : View.VISIBLE);
            a.tabNotices[t].setVisibility(scene != null ? View.VISIBLE : View.GONE);
        }
        scenePad.setScene(scene);
        // With no scene the pad is an XY controller for the effects' movers (on the Sound tab); with one, the
        // scene's own pad (on the Scenes tab).
        LinearLayout slot = scene != null ? scenePadSlot : a.xyPadSlot;
        if (scenePad.getParent() != slot) {
            if (scenePad.getParent() != null) {
                ((android.view.ViewGroup) scenePad.getParent()).removeView(scenePad);
                ((android.view.ViewGroup) padHint.getParent()).removeView(padHint);
            }
            LinearLayout.LayoutParams padParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            padParams.topMargin = a.dp(8);
            slot.addView(scenePad, padParams);
            slot.addView(padHint);
        }
        padHint.setVisibility(scene != null ? View.GONE : View.VISIBLE);
        sceneLevels.setVisibility(scene != null ? View.VISIBLE : View.GONE);
    }

    private void sendSceneLevelsIfRunning() {
        if (AudioService.isRunning() && SceneSettings.current(a) != null) {
            AudioService.sendSceneLevels(a);
        }
    }

    /** A 0-100% slider with a live label; {@code onChange} gets a fraction from 0 to 1. */
    private void addLevelSlider(LinearLayout parent, int labelRes, float initial,
                                java.util.function.Consumer<Float> onChange) {
        TextView label = new TextView(a);
        a.look.label(label);
        label.setPadding(a.dp(4), a.dp(14), 0, 0);
        label.setText(a.getString(labelRes, Math.round(initial * 100)));
        SeekBar bar = new SeekBar(a);
        bar.setMax(100);
        a.look.slider(bar);
        bar.setMinimumHeight(a.dp(40));
        bar.setContentDescription(a.getString(labelRes, Math.round(initial * 100)));
        bar.setProgress(Math.round(initial * 100));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar b, int progress, boolean fromUser) {
                label.setText(a.getString(labelRes, progress));
                if (fromUser) {
                    onChange.accept(progress / 100f);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar b) { }

            @Override
            public void onStopTrackingTouch(SeekBar b) { }
        });
        parent.addView(label);
        parent.addView(bar);
    }

    /** Passes a finger on the scene pad to the scene as an RjDj "#touch" message. */
    private void sendTouch(String type, int finger, int x, int y) {
        if (finger == 1 && !ScenePad.UP.equals(type)) {   // the first finger also drives the Pad X / Pad Y movers
            Modulation.onPad(x / (float) ScenePad.GRID, y / (float) ScenePad.GRID);
        }
        if (AudioService.isRunning() && SceneSettings.current(a) != null) {
            PdBase.sendMessage("#touch", type, (float) finger, (float) x, (float) y);
        }
    }

    /** The scene picker: the built-in effects, then every imported scene; press and hold one to delete it. */
    private void showScenes() {
        List<SceneLibrary.Scene> scenes = SceneLibrary.list(SceneSettings.scenesDir(a));
        String[] labels = new String[scenes.size() + 1];
        labels[0] = a.getString(R.string.scene_built_in);
        SceneLibrary.Scene current = SceneSettings.current(a);
        int chosen = 0;
        for (int i = 0; i < scenes.size(); i++) {
            SceneLibrary.Scene sc = scenes.get(i);
            labels[i + 1] = sc.author.isEmpty() ? sc.name
                    : a.getString(R.string.scene_by_author, sc.name, sc.author);
            if (current != null && sc.dirName.equals(current.dirName)) {
                chosen = i + 1;
            }
        }
        LinearLayout content = new LinearLayout(a);
        content.setOrientation(LinearLayout.VERTICAL);
        content.addView(a.sheetHeading(a.getString(R.string.scenes_title),
                a.getString(scenes.isEmpty() ? R.string.scenes_none : R.string.scenes_delete_hint)));
        a.addListRows(content, labels, chosen, null, i -> {
            a.closeSheet();
            selectScene(i == 0 ? SceneSettings.BUILT_IN : scenes.get(i - 1).dirName);
        });
        for (int i = 1; i <= scenes.size(); i++) {   // row 0 is the heading, row 1 the built-in effects
            SceneLibrary.Scene sc = scenes.get(i - 1);
            content.getChildAt(i + 1).setOnLongClickListener(v -> {
                a.closeSheet();
                confirmDeleteScene(sc);
                return true;
            });
        }
        a.openSheet(content);
    }

    private void selectScene(String dirName) {
        if (dirName.equals(SceneSettings.selected(a))) {
            return;
        }
        SceneSettings.select(a, dirName);
        update();
        if (AudioService.isRunning()) {
            // The patch is opened when the engine starts, so swap scenes by restarting it.
            a.startForegroundService(
                    new Intent(a, AudioService.class).setAction(AudioService.ACTION_RESTART));
        }
    }

    private void confirmDeleteScene(SceneLibrary.Scene scene) {
        a.new LookDialog()
                .setTitle(a.getString(R.string.delete_scene_title, scene.name))
                .setPositiveButton(R.string.delete, (d, w) -> {
                    boolean wasSelected = scene.dirName.equals(SceneSettings.selected(a));
                    if (wasSelected) {
                        selectScene(SceneSettings.BUILT_IN);   // stops using its files first
                    }
                    SceneLibrary.delete(scene);
                    a.toast(R.string.toast_scene_deleted);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void startImportScene() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*");
        a.startActivityForResult(intent, MainActivity.REQ_IMPORT_SCENE);
    }

    private void startImportSceneFolder() {
        a.startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), MainActivity.REQ_IMPORT_SCENE_FOLDER);
    }

    /** Copies one scene folder, or every scene inside a folder of scenes, on a background thread. */
    void importSceneFolderFrom(Uri tree) {
        a.toast(R.string.toast_scene_folder_importing);
        new Thread(() -> {
            File dir = SceneSettings.scenesDir(a);
            if (!dir.isDirectory() && !dir.mkdirs()) {
                a.runOnUiThread(() -> a.toast(R.string.toast_scene_import_failed, "Cannot create " + dir));
                return;
            }
            TreeImporter.Result r = TreeImporter.importTree(a.getContentResolver(), tree, dir);
            a.runOnUiThread(() -> {
                if (r.imported.isEmpty()) {
                    a.toast(R.string.toast_scene_none_found);
                } else if (r.imported.size() == 1 && r.failed.isEmpty()) {
                    a.toast(R.string.toast_scene_imported, r.imported.get(0).name);
                    selectScene(r.imported.get(0).dirName);
                } else {
                    // Don't guess which of many to start; the user picks from the list.
                    a.toast(R.string.toast_scenes_imported, r.imported.size(), r.failed.size());
                }
            });
        }, "scene-folder-import").start();
    }

    /** Unzips on a background thread (scenes can hold many samples), then offers to switch to it. */
    void importSceneFrom(Uri uri) {
        String fileName = a.displayName(uri);
        a.toast(R.string.toast_scene_importing);
        new Thread(() -> {
            try (InputStream in = a.getContentResolver().openInputStream(uri)) {
                if (in == null) {
                    throw new IOException(a.getString(R.string.err_open_file));
                }
                File dir = SceneSettings.scenesDir(a);
                if (!dir.isDirectory() && !dir.mkdirs()) {
                    throw new IOException("Cannot create " + dir);
                }
                SceneLibrary.Scene scene = SceneLibrary.importZip(in, fileName, dir);
                a.runOnUiThread(() -> {
                    a.toast(R.string.toast_scene_imported, scene.name);
                    selectScene(scene.dirName);
                });
            } catch (IOException | RuntimeException e) {
                a.runOnUiThread(() -> a.toast(R.string.toast_scene_import_failed, e.getMessage()));
            }
        }, "scene-import").start();
    }
}
