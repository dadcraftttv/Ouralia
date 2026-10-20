// SPDX-License-Identifier: GPL-3.0-or-later
package app.pdscenes;

import android.content.Context;
import android.content.SharedPreferences;

/** Screen preferences: chosen theme, which sections are expanded, the tab shown. Not part of presets. */
final class UiSettings {

    static final int THEME_LIGHT = 0;
    static final int THEME_DARK = 1;
    static final int THEME_AMOLED = 2;
    /** Follows the phone's own light/dark setting (Light or Dark). */
    static final int THEME_PHONE = 3;

    private static final String PREFS = "pdscenes_ui";
    private static final String KEY_THEME = "theme";
    private static final String KEY_OPEN_PREFIX = "open_";

    private UiSettings() { }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static int theme(Context context) {
        return prefs(context).getInt(KEY_THEME, THEME_DARK);
    }

    static void setTheme(Context context, int theme) {
        prefs(context).edit().putInt(KEY_THEME, theme).apply();
    }

    /** The theme to draw in now: Phone resolves to Light or Dark by the phone's setting. */
    static int effectiveTheme(Context context) {
        int t = theme(context);
        if (t != THEME_PHONE) {
            return t;
        }
        int night = context.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return night == android.content.res.Configuration.UI_MODE_NIGHT_YES ? THEME_DARK : THEME_LIGHT;
    }

    private static final String KEY_ACCENT = "accent", KEY_HUE = "accent_hue", KEY_CHROMA = "accent_chroma";

    /** The accent: one of Look.ACCENT_IDS, "wall" (the wallpaper's) or "custom". Not part of presets. */
    static String accent(Context context) {
        return prefs(context).getString(KEY_ACCENT, "violet");
    }

    static void setAccent(Context context, String id) {
        prefs(context).edit().putString(KEY_ACCENT, id).apply();
    }

    /** Your own colour: hue (degrees) and colourfulness (0..1). */
    static float[] customAccent(Context context) {
        SharedPreferences p = prefs(context);
        return new float[] {p.getFloat(KEY_HUE, 290f), p.getFloat(KEY_CHROMA, 0.85f)};
    }

    static void setCustomAccent(Context context, float hue, float chroma) {
        prefs(context).edit().putFloat(KEY_HUE, hue).putFloat(KEY_CHROMA, chroma).putString(KEY_ACCENT, "custom").apply();
    }

    static int themeResource(int theme) {
        switch (theme) {
            case THEME_LIGHT:
                return R.style.Theme_PdScenes_Light;
            case THEME_AMOLED:
                return R.style.Theme_PdScenes_Amoled;
            default:
                return R.style.Theme_PdScenes_Dark;
        }
    }

    static boolean isLight(int theme) {
        return theme == THEME_LIGHT;
    }

    private static final String KEY_WALKTHROUGH = "walkthrough_seen";

    /** True once the first-run walkthrough has been shown. */
    static boolean walkthroughSeen(Context context) {
        return prefs(context).getBoolean(KEY_WALKTHROUGH, false);
    }

    static void setWalkthroughSeen(Context context) {
        prefs(context).edit().putBoolean(KEY_WALKTHROUGH, true).apply();
    }

    private static final String KEY_TAB = "tab";

    /** The tab last shown (0 Sound, 1 Music, 2 Realms, 3 Scenes, 4 Setup). */
    static int tab(Context context) {
        return prefs(context).getInt(KEY_TAB, 0);
    }

    static void setTab(Context context, int tab) {
        prefs(context).edit().putInt(KEY_TAB, tab).apply();
    }

    static boolean isExpanded(Context context, String sectionId, boolean defaultOpen) {
        return prefs(context).getBoolean(KEY_OPEN_PREFIX + sectionId, defaultOpen);
    }

    static void setExpanded(Context context, String sectionId, boolean open) {
        prefs(context).edit().putBoolean(KEY_OPEN_PREFIX + sectionId, open).apply();
    }
}
