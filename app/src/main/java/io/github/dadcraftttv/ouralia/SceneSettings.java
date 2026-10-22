// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.dadcraftttv.ouralia;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;

/** Which scene is selected, remembered between runs. Empty means the built-in effects chain. */
final class SceneSettings {

    static final String BUILT_IN = "";

    private static final String PREFS = "ouralia_scene";
    private static final String KEY_SCENE = "scene";
    private static final String KEY_VOLUME = "volume";
    private static final String KEY_MIC_VOLUME = "mic_volume";
    private static final String KEY_RJDJ_TIMING = "rjdj_timing";
    private static final String KEY_STATE = "state_";   // + the scene's folder: its saved state (SceneState)

    /** The sample rate RjDj ran its scenes at; scene timings (delays, loop lengths) assume it. */
    static final int RJDJ_SAMPLE_RATE = 22050;

    private SceneSettings() { }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** The built-in effects, scene volume, mic volume and RjDj timing back to a fresh install. */
    static void resetAll(Context context) {
        prefs(context).edit().clear().apply();
    }

    /** An imported scene's saved state, as SceneState text ("" if none). Reset everything clears it with the rest. */
    static String sceneState(Context context, String dirName) {
        return prefs(context).getString(KEY_STATE + dirName, "");
    }

    static void setSceneState(Context context, String dirName, String state) {
        if (state == null || state.isEmpty()) {
            prefs(context).edit().remove(KEY_STATE + dirName).apply();
        } else {
            prefs(context).edit().putString(KEY_STATE + dirName, state).apply();
        }
    }

    static String selected(Context context) {
        return prefs(context).getString(KEY_SCENE, BUILT_IN);
    }

    static void select(Context context, String dirName) {
        prefs(context).edit().putString(KEY_SCENE, dirName).apply();
    }

    /** Output volume for scenes, 0 to 1 (sent as RjDj's #volume). */
    static float volume(Context context) {
        return prefs(context).getFloat(KEY_VOLUME, 1f);
    }

    static void setVolume(Context context, float volume) {
        prefs(context).edit().putFloat(KEY_VOLUME, volume).apply();
    }

    /** Microphone volume for scenes, 0 to 1 (sent as RjDj's #micvolume). */
    static float micVolume(Context context) {
        return prefs(context).getFloat(KEY_MIC_VOLUME, 1f);
    }

    static void setMicVolume(Context context, float volume) {
        prefs(context).edit().putFloat(KEY_MIC_VOLUME, volume).apply();
    }

    /** Whether scenes run at RjDj's 22050 Hz, so their timings come out as their authors heard them. */
    static boolean rjdjTiming(Context context) {
        return prefs(context).getBoolean(KEY_RJDJ_TIMING, true);
    }

    static void setRjdjTiming(Context context, boolean on) {
        prefs(context).edit().putBoolean(KEY_RJDJ_TIMING, on).apply();
    }

    /** Where imported scenes are stored. */
    static File scenesDir(Context context) {
        return new File(context.getFilesDir(), "scenes");
    }

    /** The selected scene, or null for the built-in chain (also when the scene has been deleted). */
    static SceneLibrary.Scene current(Context context) {
        return SceneLibrary.find(scenesDir(context), selected(context));
    }
}
