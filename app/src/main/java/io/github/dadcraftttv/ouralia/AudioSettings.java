// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.dadcraftttv.ouralia;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * The chosen input and output device, remembered between runs. A device is stored by a stable
 * key (see {@link AudioDevices}) rather than its numeric id, because Android hands out a new id
 * each time a device is reconnected. An empty key means "Automatic" (let Android choose).
 */
final class AudioSettings {

    static final String AUTOMATIC = "";

    private static final String PREFS = "ouralia_audio";
    private static final String KEY_INPUT = "input";
    private static final String KEY_OUTPUT = "output";
    // The room in stereo (StereoRoom): the switch, and what this phone's mics gave when measured
    private static final String KEY_STEREO = "stereo_room";
    private static final String KEY_STEREO_PRESET = "stereo_preset";   // -2 not measured yet, -1 mono only
    private static final String KEY_STEREO_TRIM = "stereo_trim_db";
    private static final String KEY_STEREO_ADJUST = "stereo_adjust_db";   // Stereo Level, by ear, on top of the trim
    static final int STEREO_ADJUST_RANGE = 12;                             // dB either way

    private AudioSettings() { }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static String deviceKey(Context context, boolean input) {
        return prefs(context).getString(input ? KEY_INPUT : KEY_OUTPUT, AUTOMATIC);
    }

    /** Both devices back to Automatic, the room in stereo off and unmeasured. */
    static void reset(Context context) {
        prefs(context).edit().clear().apply();
    }

    static void setDeviceKey(Context context, boolean input, String key) {
        prefs(context).edit().putString(input ? KEY_INPUT : KEY_OUTPUT, key).apply();
    }

    static boolean stereoRoom(Context context) {
        return prefs(context).getBoolean(KEY_STEREO, false);
    }

    static void setStereoRoom(Context context, boolean on) {
        prefs(context).edit().putBoolean(KEY_STEREO, on).apply();
    }

    /** What the last measurement chose, or null if this phone hasn't been measured. */
    static StereoRoom.Choice stereoChoice(Context context) {
        int preset = prefs(context).getInt(KEY_STEREO_PRESET, -2);
        return preset == -2 ? null : new StereoRoom.Choice(preset, prefs(context).getFloat(KEY_STEREO_TRIM, 0f));
    }

    /** A new measurement is a new starting point: Stereo Level goes back to 0. */
    static void setStereoChoice(Context context, StereoRoom.Choice choice) {
        prefs(context).edit().putInt(KEY_STEREO_PRESET, choice.preset).putFloat(KEY_STEREO_TRIM, choice.trimDb)
                .putInt(KEY_STEREO_ADJUST, 0).apply();
    }

    static int stereoAdjust(Context context) {
        return prefs(context).getInt(KEY_STEREO_ADJUST, 0);
    }

    static void setStereoAdjust(Context context, int db) {
        prefs(context).edit().putInt(KEY_STEREO_ADJUST, db).apply();
    }

    /** The level the stereo room plays at: the measured match plus Stereo Level. */
    static float stereoTrimDb(Context context) {
        StereoRoom.Choice c = stereoChoice(context);
        return (c == null ? 0f : c.trimDb) + stereoAdjust(context);
    }
}
