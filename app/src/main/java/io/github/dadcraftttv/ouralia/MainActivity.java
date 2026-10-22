// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.dadcraftttv.ouralia;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Control screen: engine on/off, presets, randomize, and one control per parameter. */
public class MainActivity extends Activity {

    private static final int REQ_PERMISSIONS = 1;
    static final int REQ_EXPORT = 2;
    private static final int REQ_MIC = 3;   // the walkthrough's "Allow microphone" (doesn't start the sound)
    private Walkthrough walkthrough;
    static final int REQ_IMPORT = 3;
    static final int REQ_IMPORT_SCENE = 4;
    static final int REQ_IMPORT_SCENE_FOLDER = 5;
    static final int REQ_IMPORT_KIT = 23;
    static final int REQ_IMPORT_KIT_FOLDER = 24;

    private AudioManager audioManager;

    private final android.os.Handler uiHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private static final long NOW_LINE_MS = 1000;
    private final Runnable nowLineTick = new Runnable() {
        @Override
        public void run() {
            playerUi.updateNowLine();
            settingRows.updateMoverDots();
            playerUi.updateIntensityRow();
            playerUi.updateStatus();
            if (walkthrough != null) {
                walkthrough.refresh();   // headphones plugged in meanwhile
            }
            devicesUi.updateDiagnostics();
            uiHandler.postDelayed(this, NOW_LINE_MS);
        }
    };
    private TextView warning;
    Button undoButton;
    /** Presets, randomize and the effect sections: hidden while an imported scene is selected. */
    // ---- tabs: Sound, Music, Realms, Scenes, Setup (the one long list had outgrown itself) ----
    static final int TAB_SOUND = 0, TAB_MUSIC = 1, TAB_REALMS = 2, TAB_SCENES = 3, TAB_SETUP = 4;
    private static final int[] TAB_NAMES = {R.string.tab_sound, R.string.tab_music, R.string.tab_realms,
            R.string.tab_scenes, R.string.tab_setup};
    private final LinearLayout[] tabPages = new LinearLayout[TAB_NAMES.length];     // each tab's whole page
    final LinearLayout[] tabBodies = new LinearLayout[TAB_NAMES.length];    // its controls (hidden for a scene)
    final TextView[] tabNotices = new TextView[TAB_NAMES.length];           // "paused while a scene plays"
    private final LinearLayout[] tabViews = new LinearLayout[TAB_NAMES.length];   // the tab bar's buttons
    private final ImageView[] tabIcons = new ImageView[TAB_NAMES.length];
    private final TextView[] tabLabels = new TextView[TAB_NAMES.length];
    private final int[] tabScroll = new int[TAB_NAMES.length];
    private int currentTab = -1;
    /** The Ouralia look for this screen's theme (Look.java, Styler.java). */
    Styler look;
    /** The settings as cards and rows (SettingRows.java). */
    final SettingRows settingRows = new SettingRows(this);
    /** The docked player and its sheet (PlayerUi.java). */
    final PlayerUi playerUi = new PlayerUi(this);
    /** Setup's Audio Devices and Diagnostics cards (DevicesUi.java). */
    private final DevicesUi devicesUi = new DevicesUi(this);
    /** The Realms tab (RealmsUi.java). */
    final RealmsUi realmsUi = new RealmsUi(this);
    /** Pick-one settings (ChoiceUi.java). */
    final ChoiceUi choiceUi = new ChoiceUi(this);
    /** Setup's Appearance card (AppearanceUi.java). */
    private final AppearanceUi appearanceUi = new AppearanceUi(this);
    /** The Music tab's Kits card (KitUi.java). */
    final KitUi kitUi = new KitUi(this);
    /** Presets (PresetUi.java). */
    final PresetUi presetUi = new PresetUi(this);
    /** Setup's About card (AboutUi.java). */
    private final AboutUi aboutUi = new AboutUi(this);
    /** The Scenes tab (SceneUi.java). */
    private final SceneUi sceneUi = new SceneUi(this);
    LinearLayout xyPadSlot;          // where the pad sits on the Sound tab (an XY pad for the movers)

    /** Re-checks for headphones when something is plugged in or removed. */
    private final AudioDeviceCallback deviceCallback = new AudioDeviceCallback() {
        @Override
        @SuppressWarnings("NullableProblems")
        public void onAudioDevicesAdded(AudioDeviceInfo[] added) {
            devicesUi.refreshDevicePickers();
            updateUi();
        }

        @Override
        @SuppressWarnings("NullableProblems")
        public void onAudioDevicesRemoved(AudioDeviceInfo[] removed) {
            devicesUi.refreshDevicePickers();
            updateUi();
        }
    };

    // ---- lifecycle ----

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        int theme = UiSettings.effectiveTheme(this);   // (Phone: Light or Dark, as the phone is set)
        setTheme(UiSettings.themeResource(theme));   // must be set before any view is created
        super.onCreate(savedInstanceState);
        look = new Styler(this, theme);
        ParamStore.loadOnce(this);
        if (Kits.source == null) {                       // (the part menus say which kits lack a part)
            Kits.source = AudioService.builtInKits(this);
            Kits.refreshChoices();
        }
        Kits.decoder = AudioFiles::decode;               // (a kit's MP3, OGG, FLAC... recordings, at import)
        SceneLibrary.installBuiltIns(this, SceneSettings.scenesDir(this));
        audioManager = getSystemService(AudioManager.class);

        // The settings scroll; the bar with Start/Stop stays at the bottom, in reach of a thumb while walking.
        android.widget.FrameLayout screen = new android.widget.FrameLayout(this);
        screen.setBackgroundColor(look.p.bg);
        if (Build.VERSION.SDK_INT < 35) {   // (35+ draws edge to edge: the screen's own colour shows behind the bars)
            getWindow().setStatusBarColor(look.p.bg);
            getWindow().setNavigationBarColor(look.p.bg);
        }
        ScrollView scroll = new ScrollView(this);
        mainScroll = scroll;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);
        screen.addView(scroll, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
        // The player and tab bar sit over the settings, which scroll on behind; the end of the list stops above them.
        View bar = buildBottomBar();
        android.widget.FrameLayout.LayoutParams barParams = new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.BOTTOM);
        screen.addView(bar, barParams);
        screen.addView(sheetScrim, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
        screen.addView(sheetBox, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.BOTTOM));
        bar.addOnLayoutChangeListener((v, l, t, r, b2, ol, ot, or, ob) -> {
            int under = v.getHeight() + barParams.bottomMargin;
            if (root.getPaddingBottom() != pad + under) {
                root.setPadding(pad, pad, pad, pad + under);
            }
        });
        screen.setClipToPadding(false);   // the card's shadow may spill into the navigation bar area, not stop at it
        setContentView(screen);

        // Targeting Android 15+ draws edge-to-edge; keep content clear of the system bars.
        if (Build.VERSION.SDK_INT >= 30) {
            screen.setOnApplyWindowInsetsListener((v, insets) -> {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return WindowInsets.CONSUMED;
            });
        }

        // the top row: the name, and while no headphones are connected a small red chip saying so (tap it for why)
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(4), dp(4), 0, dp(12));
        TextView title = new TextView(this);
        title.setText(R.string.wordmark);   // lower case, as in the mockups
        title.setTextSize(Look.TEXT_TITLE + 3);
        title.setTypeface(look.heading());
        title.setLetterSpacing(0.06f);
        title.setTextColor(look.p.text);
        top.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        warning = new TextView(this);
        warning.setText(R.string.no_headphones_chip);
        warning.setTextSize(12);
        warning.setTypeface(look.textStrong());
        warning.setTextColor(look.p.err);
        warning.setGravity(Gravity.CENTER_VERTICAL);
        warning.setBackground(look.pressable(look.round(look.p.errBg, 10), look.p.err, 10));
        warning.setPadding(dp(10), 0, dp(12), 0);
        warning.setMinHeight(dp(34));
        android.graphics.drawable.Drawable phonesOff = getDrawable(R.drawable.ic_headphones_off).mutate();
        phonesOff.setTint(look.p.err);
        warning.setCompoundDrawablesRelativeWithIntrinsicBounds(phonesOff, null, null, null);
        warning.setCompoundDrawablePadding(dp(6));
        warning.setContentDescription(getString(R.string.no_headphones_warning));
        warning.setOnClickListener(v -> new LookDialog()
                .setTitle(R.string.no_headphones_chip)
                .setMessage(R.string.no_headphones_warning)
                .setPositiveButton(R.string.close, null)
                .show());
        top.addView(warning, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(top);

        // the five tab pages, one shown at a time (the strip that switches them sits on the bottom card)
        for (int t = 0; t < TAB_NAMES.length; t++) {
            LinearLayout page = new LinearLayout(this);
            page.setOrientation(LinearLayout.VERTICAL);
            TextView notice = new TextView(this);
            notice.setText(R.string.tab_paused_scene);
            notice.setTextSize(15);
            notice.setPadding(0, dp(16), 0, dp(16));
            notice.setVisibility(View.GONE);
            page.addView(notice);
            LinearLayout body = new LinearLayout(this);
            body.setOrientation(LinearLayout.VERTICAL);
            page.addView(body);
            page.setVisibility(View.GONE);
            root.addView(page);
            tabPages[t] = page;
            tabNotices[t] = notice;
            tabBodies[t] = body;
        }
        LinearLayout sound = tabBodies[TAB_SOUND], music = tabBodies[TAB_MUSIC], realms = tabBodies[TAB_REALMS];
        LinearLayout scenes = tabBodies[TAB_SCENES], setup = tabBodies[TAB_SETUP];

        // ---- Scenes ----
        sceneUi.build(scenes);

        // ---- Sound: presets and shortcuts, the XY pad, then the input and the effects ----
        // Presets: one wide button (the list sheet), then Save, Export and Import as small icon buttons
        LinearLayout presetRow = newButtonRow(sound);
        Button presets = addButton(presetRow, R.string.presets, presetUi::showPresets);
        presets.setBackground(look.choiceTile(getDrawable(R.drawable.ic_chevron_down).mutate()));
        presets.setTextColor(look.p.text);
        presets.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        presets.setPadding(dp(16), 0, dp(40), 0);
        presetSave = iconButton(presetRow, R.drawable.ic_add, R.string.save, presetUi::showSave);
        presetExport = iconButton(presetRow, R.drawable.ic_upload, R.string.export, presetUi::startExport);
        presetImport = iconButton(presetRow, R.drawable.ic_download, R.string.import_preset, presetUi::startImport);

        // Randomize, Nudge, Undo: smaller outlined chips with icons
        LinearLayout randomRow = newButtonRow(sound);
        randomRow.setPadding(0, dp(2), 0, dp(4));
        randomizeChip = chip(randomRow, R.drawable.ic_shuffle, R.string.randomize, () -> {
            ParamStore.randomize();
            settingRows.refreshRows();
            uiHandler.removeCallbacks(morphFollow);
            uiHandler.post(morphFollow);   // the sliders follow the glide on screen
        });
        nudgeChip = chip(randomRow, R.drawable.ic_wave, R.string.nudge, () -> {
            ParamStore.nudge(ParamStore.get("nudge_amount"));
            settingRows.refreshRows();
        });
        undoButton = chip(randomRow, R.drawable.ic_undo, R.string.undo, () -> {
            ParamStore.undo();
            settingRows.refreshRows();
        });
        // the XY pad and its hint on a card that folds like the sections (DadCraft: hide it if you don't use it)
        View gap = new View(this);
        sound.addView(gap, new LinearLayout.LayoutParams(0, dp(6)));
        xyPadSlot = settingRows.addSection(sound, "xy_pad", getString(R.string.xy_pad_title), true);

        // ---- Realms ----
        Worlds.load(this);
        settingRows.addSection(realms, "worlds", getString(R.string.section_worlds), true, realmsUi::fillWorldsSection);

        // ---- the sections, each on its tab ----
        for (Params.Section section : Params.SECTIONS) {
            LinearLayout tab = "beat".equals(section.id) || "music".equals(section.id) || "mix".equals(section.id) ? music
                    : "behavior".equals(section.id) ? setup : sound;
            if (section.group != null) {
                settingRows.addGroupHeading(tab, section.group);
            }
            settingRows.addSection(tab, section.id, section.title, section.openByDefault,
                    body -> settingRows.fillEffectSection(body, section), settingRows.headerExtras(section));
        }

        settingRows.addSection(music, "kits", getString(R.string.section_kits), false, kitUi::fillKitsSection);

        // ---- Setup ----
        devicesUi.addAudioSection(setup);
        devicesUi.addDiagnosticsSection(setup);
        appearanceUi.addAppearanceSection(setup);
        LinearLayout resetRow = newButtonRow(setup);
        Button reset = addButton(resetRow, R.string.reset_all, presetUi::confirmResetAll);
        look.danger(reset);
        // the same gap above and below it as between the cards
        ((LinearLayout.LayoutParams) reset.getLayoutParams()).setMargins(dp(3), 0, dp(3), 0);
        ((LinearLayout.LayoutParams) resetRow.getLayoutParams()).bottomMargin = dp(Look.CARD_GAP);
        aboutUi.addAboutSection(setup);
        look.fonts(root);
        showTab(UiSettings.tab(this));
        if ((getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
                && getIntent().getBooleanExtra("stereo_probe", false) && !AudioService.isRunning()
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            StereoProbe.run();   // debug builds only: is the mic's stereo two mics? (logcat StereoProbe)
        }
        if (FirstRun.showWalkthrough(UiSettings.walkthroughSeen(this), installTimes()[0], installTimes()[1])) {
            showWalkthrough();
        }
        sceneUi.update();
    }

    private static final int[] TAB_ICONS = {R.drawable.ic_tab_sound, R.drawable.ic_tab_music, R.drawable.ic_tab_realms,
            R.drawable.ic_tab_scenes, R.drawable.ic_tab_setup};

    /** The tab bar: a pill of five icons, in thumb reach; the shown tab widens into a soft pill with its name. */
    private View buildTabStrip() {
        LinearLayout strip = new LinearLayout(this);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setGravity(Gravity.CENTER_VERTICAL);
        android.graphics.drawable.GradientDrawable bg = look.round(look.p.s1, 32);
        bg.setStroke(dp(1), look.p.line);
        strip.setBackground(bg);
        strip.setPadding(dp(8), 0, dp(8), 0);
        android.animation.LayoutTransition slide = new android.animation.LayoutTransition();
        slide.enableTransitionType(android.animation.LayoutTransition.CHANGING);   // the pill slides as it widens
        strip.setLayoutTransition(slide);
        for (int t = 0; t < TAB_NAMES.length; t++) {
            if (t > 0) {
                strip.addView(new android.widget.Space(this), new LinearLayout.LayoutParams(0, 1, 1f));
            }
            LinearLayout b = new LinearLayout(this);
            b.setOrientation(LinearLayout.HORIZONTAL);
            b.setGravity(Gravity.CENTER);
            ImageView icon = new ImageView(this);
            icon.setImageResource(TAB_ICONS[t]);
            b.addView(icon, new LinearLayout.LayoutParams(dp(22), dp(22)));
            TextView label = new TextView(this);
            label.setText(TAB_NAMES[t]);
            label.setTypeface(look.textStrong());
            label.setTextSize(14);
            label.setTextColor(look.p.onContainer);
            label.setPadding(dp(8), 0, 0, 0);
            label.setSingleLine(true);
            b.addView(label);
            final int tab = t;
            b.setOnClickListener(v -> showTab(tab));
            b.setContentDescription(getString(R.string.tab_description, getString(TAB_NAMES[t])));
            b.setMinimumWidth(dp(48));
            strip.addView(b, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(48)));
            tabViews[t] = b;
            tabIcons[t] = icon;
            tabLabels[t] = label;
        }
        return strip;
    }

    /** Shows one tab (each keeps its own scroll position) and remembers it for next time. */
    private void showTab(int tab) {
        tab = Math.max(0, Math.min(TAB_NAMES.length - 1, tab));
        if (currentTab >= 0 && mainScroll != null) {
            tabScroll[currentTab] = mainScroll.getScrollY();
        }
        for (int t = 0; t < TAB_NAMES.length; t++) {
            tabPages[t].setVisibility(t == tab ? View.VISIBLE : View.GONE);
            LinearLayout b = tabViews[t];
            if (b != null) {
                boolean on = t == tab;
                tabLabels[t].setVisibility(on ? View.VISIBLE : View.GONE);
                tabIcons[t].setImageTintList(android.content.res.ColorStateList.valueOf(on ? look.p.onContainer : look.p.muted));
                b.setBackground(on ? look.round(look.p.container, Look.RADIUS_PILL) : look.pressable(null, look.p.text, Look.RADIUS_PILL));
                b.setPadding(dp(on ? 16 : 13), 0, dp(on ? 16 : 13), 0);
                b.setSelected(on);
            }
        }
        currentTab = tab;
        UiSettings.setTab(this, tab);
        settingRows.refreshRows();   // rows on a hidden tab skip every refresh (a preset loaded on Sound left The Band's kit menus stale)
        if (mainScroll != null) {
            final int y = tabScroll[tab];
            mainScroll.post(() -> mainScroll.scrollTo(0, y));
        }
    }

    /**
     * The bottom of the screen, in thumb reach while walking: the docked player (Start, what's happening, Save
     * sound) above the tab bar. Tapping the player opens the player sheet with the Intensity dial.
     */
    private View buildBottomBar() {
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setBackgroundColor(look.p.bg);   // the settings scroll on behind it, out of sight
        bottom.setPadding(dp(12), dp(8), dp(12), dp(12));
        bottom.addView(playerUi.buildPlayer());
        LinearLayout.LayoutParams navParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(64));
        navParams.setMargins(0, dp(8), 0, 0);
        bottom.addView(buildTabStrip(), navParams);
        buildSheet();
        playerUi.buildPlayerContent();
        return bottom;
    }

    private ImageButton presetSave, presetExport, presetImport;
    private Button randomizeChip, nudgeChip;
    private Tour tour;                     // the spotlight tour after the walkthrough, while it shows
    private Object backEndsTour;           // its OnBackInvokedCallback on Android 13+
    // One bottom sheet, slid up over a dimmed screen, for whatever needs it: the player, the Key and Scale pickers.
    private View sheetScrim;
    private LinearLayout sheetBox;
    private ScrollView sheetBody;   // scrolls when the content is taller than most of the screen (the kit lists)
    private boolean sheetOpen;
    private Object backClosesSheet;   // an OnBackInvokedCallback on Android 13+ (kept as Object for older ones)

    /** The bottom sheet, its content swapped in by {@link #openSheet}: the player, the pickers, the lists. */
    private void buildSheet() {
        sheetScrim = new View(this);
        sheetScrim.setBackgroundColor(Look.alpha(0xFF000000, look.p.light ? 0.35f : 0.6f));
        sheetScrim.setAlpha(0f);
        sheetScrim.setVisibility(View.GONE);
        sheetScrim.setOnClickListener(v -> closeSheet());
        sheetScrim.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        sheetBox = new SheetLayout();
        sheetBox.setOrientation(LinearLayout.VERTICAL);
        sheetBox.setGravity(Gravity.CENTER_HORIZONTAL);
        android.graphics.drawable.GradientDrawable sheetBg = new android.graphics.drawable.GradientDrawable();
        sheetBg.setColor(look.p.sheet);
        float r = dp(28);
        sheetBg.setCornerRadii(new float[] {r, r, r, r, 0, 0, 0, 0});
        sheetBox.setBackground(sheetBg);
        sheetBox.setPadding(dp(16), 0, dp(16), dp(24));
        sheetBox.setClickable(true);   // taps on the sheet stay on it
        sheetBox.setVisibility(View.GONE);

        android.widget.FrameLayout handle = new android.widget.FrameLayout(this);
        View grip = new View(this);
        grip.setBackground(look.round(look.p.line, 2));
        handle.addView(grip, new android.widget.FrameLayout.LayoutParams(dp(36), dp(4), Gravity.CENTER));
        handle.setContentDescription(getString(R.string.close));
        handle.setOnClickListener(v -> closeSheet());
        sheetBox.addView(handle, new LinearLayout.LayoutParams(dp(72), dp(32)));
        sheetBody = new ScrollView(this) {
            @Override
            protected void onMeasure(int widthSpec, int heightSpec) {
                int most = Math.round(getResources().getDisplayMetrics().heightPixels * 0.72f);
                super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(most, MeasureSpec.AT_MOST));
            }
        };
        sheetBody.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sheetBox.addView(sheetBody, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    /**
     * The sheet's frame: drag it down (from the grab bar or anywhere a control isn't using the touch) and it follows
     * the finger; let go past a quarter of its height, or flick it, and it closes, otherwise it springs back. A tap on
     * a button inside still works (a drag only takes over once the finger has clearly moved down), and the dial keeps
     * its own drags.
     */
    private final class SheetLayout extends LinearLayout {
        private final int slop = android.view.ViewConfiguration.get(MainActivity.this).getScaledTouchSlop();
        private float downX, downY;
        private android.view.VelocityTracker velocity;

        SheetLayout() {
            super(MainActivity.this);
        }

        @Override
        public boolean onInterceptTouchEvent(android.view.MotionEvent e) {
            if (e.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
                begin(e);
            } else if (e.getActionMasked() == android.view.MotionEvent.ACTION_MOVE) {
                float dy = e.getRawY() - downY, dx = e.getRawX() - downX;
                // clearly downwards, and a long list already at its top: the sheet takes the drag (else the list scrolls)
                return dy > slop && dy > Math.abs(dx) && !sheetBody.canScrollVertically(-1);
            }
            return false;
        }

        @Override
        public boolean onTouchEvent(android.view.MotionEvent e) {
            switch (e.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    begin(e);
                    return true;
                case android.view.MotionEvent.ACTION_MOVE: {
                    track(e);
                    float dy = Math.max(0, e.getRawY() - downY);
                    setTranslationY(dy);
                    sheetScrim.setAlpha(1f - Math.min(1f, dy / Math.max(1, getHeight())));
                    return true;
                }
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL: {
                    track(e);
                    float vy = 0;
                    if (velocity != null) {
                        velocity.computeCurrentVelocity(1000);
                        vy = velocity.getYVelocity();
                        velocity.recycle();
                        velocity = null;
                    }
                    if (getTranslationY() > getHeight() * 0.25f || vy > dp(1000)) {
                        closeSheet();
                    } else {   // not far enough: back up
                        animate().translationY(0).setDuration(200)
                                .setInterpolator(new android.view.animation.DecelerateInterpolator(2f)).start();
                        sheetScrim.animate().alpha(1f).setDuration(200).start();
                    }
                    return true;
                }
                default:
                    return true;
            }
        }

        private void begin(android.view.MotionEvent e) {
            downX = e.getRawX();
            downY = e.getRawY();
            if (velocity != null) {
                velocity.recycle();
            }
            velocity = android.view.VelocityTracker.obtain();
            track(e);
        }

        /** Velocity from screen positions (the sheet itself moves under the finger). */
        private void track(android.view.MotionEvent e) {
            if (velocity != null) {
                android.view.MotionEvent screen = android.view.MotionEvent.obtain(e);
                screen.setLocation(e.getRawX(), e.getRawY());
                velocity.addMovement(screen);
                screen.recycle();
            }
        }
    }

    /** Slides the sheet up (300 ms, over a dimming screen) holding {@code content}; if it is up, swaps the content. */
    void openSheet(View content) {
        if (content.getParent() != sheetBody) {
            sheetBody.removeAllViews();
            if (content.getParent() != null) {
                ((android.view.ViewGroup) content.getParent()).removeView(content);
            }
            sheetBody.addView(content);
        }
        sheetBody.scrollTo(0, 0);
        sheetBody.post(() -> {   // a long list opens with the chosen row in view, not hidden further down
            if (content instanceof android.view.ViewGroup) {
                android.view.ViewGroup list = (android.view.ViewGroup) content;
                for (int i = 0; i < list.getChildCount(); i++) {
                    View row = list.getChildAt(i);
                    if (row.isSelected() && row.getBottom() > sheetBody.getHeight()) {
                        sheetBody.scrollTo(0, row.getTop() - sheetBody.getHeight() / 3);
                        break;
                    }
                }
            }
        });
        if (sheetOpen) {
            return;
        }
        sheetOpen = true;
        sheetBox.animate().cancel();
        sheetScrim.animate().cancel();
        sheetScrim.setVisibility(View.VISIBLE);
        sheetScrim.animate().alpha(1f).setDuration(250).start();
        sheetBox.setVisibility(View.VISIBLE);
        sheetBox.setTranslationY(dp(900));   // out of sight until it has measured itself
        sheetBox.post(() -> {
            sheetBox.setTranslationY(sheetBox.getHeight());
            sheetBox.animate().translationY(0).setDuration(300)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(2f)).start();
        });
        if (Build.VERSION.SDK_INT >= 33) {
            android.window.OnBackInvokedCallback back = this::closeSheet;
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, back);
            backClosesSheet = back;
        }
    }

    void closeSheet() {
        if (!sheetOpen) {
            return;
        }
        sheetOpen = false;
        sheetScrim.animate().alpha(0f).setDuration(200).withEndAction(() -> sheetScrim.setVisibility(View.GONE)).start();
        sheetBox.animate().translationY(sheetBox.getHeight()).setDuration(220)
                .setInterpolator(new android.view.animation.AccelerateInterpolator(1.5f))
                .withEndAction(() -> sheetBox.setVisibility(View.GONE)).start();
        if (Build.VERSION.SDK_INT >= 33 && backClosesSheet != null) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback((android.window.OnBackInvokedCallback) backClosesSheet);
            backClosesSheet = null;
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {   // Android 12 and older (13+ uses the callbacks registered in openSheet, startTour)
        if (tour != null && tour.isRunning()) {
            tour.end();
        } else if (sheetOpen) {
            closeSheet();
        } else {
            super.onBackPressed();
        }
    }

    /** The accent: the palette's (Ouralia violet until Appearance offers a choice). */
    private int accentColor() {
        return look.p.primary;
    }

    /** While Randomize glides, redraw the sliders a few times a second so they visibly move. */
    private final Runnable morphFollow = new Runnable() {
        @Override
        public void run() {
            settingRows.refreshRows();
            if (ParamStore.isMorphing()) {
                uiHandler.postDelayed(this, 120);
            }
        }
    };

    /** Where the settings were scrolled to, so a theme change (which rebuilds the screen) keeps the place. */
    private static final String STATE_SCROLL = "scrollY";
    private ScrollView mainScroll;

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (mainScroll != null && currentTab >= 0) {
            tabScroll[currentTab] = mainScroll.getScrollY();
            out.putIntArray(STATE_SCROLL, tabScroll);
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle saved) {
        super.onRestoreInstanceState(saved);
        int[] all = saved.getIntArray(STATE_SCROLL);
        if (all != null && all.length == tabScroll.length) {
            System.arraycopy(all, 0, tabScroll, 0, all.length);
        }
        int y = currentTab >= 0 ? tabScroll[currentTab] : 0;
        if (y <= 0 || mainScroll == null) {
            return;
        }
        // The list reaches its full height over a couple of layout passes (the card's height pads its end),
        // so keep trying until the position fits.
        ScrollView scroll = mainScroll;
        scroll.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
            int tries;

            @Override
            public void onGlobalLayout() {
                scroll.scrollTo(0, y);
                if (scroll.getScrollY() == y || ++tries >= 5) {
                    scroll.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Event-driven updates instead of polling: the service and the audio routing tell us.
        AudioService.setStateListener(this::updateUi);
        Worlds.load(this);
        Worlds.setListener(() -> {
            realmsUi.updateWorldStatus();
            playerUi.updateIntensityRow();   // the player's line names the Realm
            settingRows.refreshRows();   // sliders move on their own while a world fades in
        });
        realmsUi.refreshWorldRows();
        realmsUi.updateWorldStatus();
        uiHandler.post(nowLineTick);
        audioManager.registerAudioDeviceCallback(deviceCallback, null);
        devicesUi.refreshDevicePickers();
        updateUi();
    }

    @Override
    protected void onPause() {
        AudioService.setStateListener(null);
        Worlds.setListener(null);
        uiHandler.removeCallbacks(nowLineTick);
        uiHandler.removeCallbacks(morphFollow);
        audioManager.unregisterAudioDeviceCallback(deviceCallback);
        ParamStore.save(this);
        super.onPause();
    }

    // ---- building the screen ----

    LinearLayout newButtonRow(LinearLayout parent) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBaselineAligned(false);   // a two-line button would otherwise push its one-line neighbours down
        parent.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return row;
    }

    Button addButton(LinearLayout row, int textRes, Runnable action) {
        Button b = new Button(this);
        b.setText(textRes);
        look.button(b, false);
        b.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);   // (all as tall as the tallest)
        lp.setMargins(dp(3), dp(4), dp(3), dp(4));
        row.addView(b, lp);
        return b;
    }

    /** A square icon button (48 dp, on the card surface) at the end of a button row. */
    private ImageButton iconButton(LinearLayout row, int icon, int description, Runnable action) {
        ImageButton b = new ImageButton(this);
        b.setImageResource(icon);
        b.setImageTintList(android.content.res.ColorStateList.valueOf(look.p.text));
        b.setBackground(look.pressable(look.round(look.p.s1, 14), look.p.text, 14));
        b.setContentDescription(getString(description));
        b.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(48), dp(48));
        lp.setMargins(dp(3), dp(4), dp(3), dp(4));
        row.addView(b, lp);
        return b;
    }

    /**
     * An outlined chip with an icon and a word (Randomize, Nudge, Undo); the chips of a row share its width equally
     * (DadCraft: no open pocket on the right). It greys out when disabled.
     */
    Button chip(LinearLayout row, int icon, int text, Runnable action) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTypeface(look.textStrong());
        b.setTextSize(13);
        b.setTextColor(new android.content.res.ColorStateList(new int[][] {{-android.R.attr.state_enabled}, {}},
                new int[] {Look.alpha(look.p.muted, 0.5f), look.p.text}));
        b.setBackground(look.pressable(look.outline(look.p.line, 18), look.p.text, 18));
        b.setStateListAnimator(null);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(dp(40));
        b.setMinimumHeight(dp(40));
        b.setPadding(dp(10), 0, dp(10), 0);
        b.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        // the icon and the word centred together (a Button centres only the word, leaving the icon at the edge)
        b.addOnLayoutChangeListener((v, l, t, r, bt, ol, ot, or, ob) -> {
            int content = dp(16) + dp(6) + Math.round(b.getPaint().measureText(b.getText().toString()));
            int side = Math.max(dp(6), (b.getWidth() - content) / 2);
            if (b.getPaddingLeft() != side) {
                b.setPadding(side, 0, side, 0);
            }
        });
        android.graphics.drawable.Drawable d = getDrawable(icon).mutate();
        d.setBounds(0, 0, dp(16), dp(16));
        d.setTintList(b.getTextColors());
        b.setCompoundDrawablesRelative(d, null, null, null);
        b.setCompoundDrawablePadding(dp(6));
        b.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(40), 1f);   // the row's chips share it equally
        lp.setMargins(dp(3), dp(4), dp(3), dp(4));
        row.addView(b, lp);
        return b;
    }

    /** When the app was first installed and last updated (ms); equal on a fresh install. */
    private long[] installTimes() {
        try {
            android.content.pm.PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return new long[]{info.firstInstallTime, info.lastUpdateTime};
        } catch (PackageManager.NameNotFoundException e) {
            return new long[]{0, 1};
        }
    }

    void showWalkthrough() {
        UiSettings.setWalkthroughSeen(this);
        walkthrough = new Walkthrough(this, UiSettings.themeResource(UiSettings.effectiveTheme(this)), new Walkthrough.Host() {
            @Override
            public boolean headphonesConnected() {
                return MainActivity.this.headphonesConnected();
            }

            @Override
            public boolean micAllowed() {
                return checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
            }

            @Override
            public void askForMic() {
                List<String> wanted = new ArrayList<>();
                wanted.add(Manifest.permission.RECORD_AUDIO);
                if (Build.VERSION.SDK_INT >= 33) {
                    wanted.add(Manifest.permission.POST_NOTIFICATIONS);
                }
                requestPermissions(wanted.toArray(new String[0]), REQ_MIC);
            }

            @Override
            public void startTour() {
                MainActivity.this.startTour();
            }

            @Override
            public int dp(int v) {
                return MainActivity.this.dp(v);
            }
        });
        walkthrough.applyLook(look);
        walkthrough.open();
    }

    /**
     * The spotlight tour after the walkthrough (DadCraft, Oct 21): arrows to the small buttons on the Sound tab and
     * the player, then a live try: press Start, open Echo, turn it up from its defaults and hear your voice change.
     * Echo (its values, switch, movers and whether its card was open) goes back to how it was when the tour ends,
     * however it ends: on a new install, Echo level 0. (Between them sits Echo follows your steps: left dark, as its
     * sheet would open under the tour.)
     */
    private void startTour() {
        if (tour != null && tour.isRunning()) {
            return;
        }
        closeSheet();
        showTab(TAB_SOUND);
        LinearLayout echoCard = settingRows.sectionCards.get("echo");
        View echoHead = echoCard.getChildAt(0), echoBody = echoCard.getChildAt(1);
        View movers = null, onOff = null, chevron = null;
        android.view.ViewGroup head = (android.view.ViewGroup) echoHead;
        for (int i = 1; i < head.getChildCount(); i++) {
            View c = head.getChildAt(i);
            if (c instanceof android.widget.ToggleButton) {
                movers = c;
            } else if (c instanceof Switch) {
                onOff = c;
            } else if (c instanceof ImageView) {
                chevron = c;
            }
        }

        // what the tour may change, to put back at the end
        Map<String, Float> echoBefore = new HashMap<>();
        String echoMovers = Params.moversSwitchId("echo");
        for (Param p : Params.ALL) {
            if (p.section.equals("echo") || p.id.equals(echoMovers)) {
                echoBefore.put(p.id, ParamStore.get(p.id));
            }
        }
        boolean echoWasOpen = echoBody.getVisibility() == View.VISIBLE;
        if (echoWasOpen) {
            echoHead.performClick();   // folded, so the tour can ask for it to be opened
        }
        // the try starts from Echo's defaults (Echo level 0, switched on) with nothing moving it but the user
        for (Param p : Params.ALL) {
            if (p.section.equals("echo")) {
                ParamStore.set(p.id, p.def);
            }
        }
        ParamStore.set("echo_on", 1f);
        if (Params.byId(echoMovers) != null) {
            ParamStore.set(echoMovers, 0f);
        }
        settingRows.refreshRows();
        settingRows.updateMoverUi();

        boolean scene = SceneSettings.current(this) != null;   // (the effects are paused: no live try)
        Runnable top = () -> mainScroll.post(() -> mainScroll.scrollTo(0, 0));
        Runnable toEcho = () -> mainScroll.post(() -> scrollToShow(echoHead, 0.3f));
        List<Tour.Step> steps = new ArrayList<>();
        Tour.Step s = new Tour.Step(getString(R.string.tour_presets_t), getString(R.string.tour_presets_b))
                .spot(getString(R.string.tour_save), presetSave)
                .spot(getString(R.string.tour_export), presetExport)
                .spot(getString(R.string.tour_import), presetImport);
        s.enter = top;
        steps.add(s);
        s = new Tour.Step(getString(R.string.tour_shortcuts_t), getString(R.string.tour_shortcuts_b))
                .spot(getString(R.string.tour_randomize), randomizeChip)
                .spot(getString(R.string.tour_nudge), nudgeChip)
                .spot(getString(R.string.tour_undo), undoButton);
        s.enter = top;
        steps.add(s);
        s = new Tour.Step(getString(R.string.tour_card_t), getString(R.string.tour_card_b));
        if (movers != null) {
            s.spot(getString(R.string.tour_movers), movers);
        }
        if (onOff != null) {
            s.spot(getString(R.string.tour_switch), onOff);
        }
        s.spot(getString(R.string.tour_open), chevron);
        s.enter = toEcho;
        steps.add(s);
        steps.add(new Tour.Step(getString(R.string.tour_player_t), getString(R.string.tour_player_b))
                .spot(getString(R.string.tour_start), playerUi.miniStart)
                .spot(getString(R.string.tour_middle), playerUi.playerMiddle)
                .spot(getString(R.string.tour_snapshot), playerUi.miniSave));
        s = new Tour.Step(getString(R.string.tour_tabs_t), getString(R.string.tour_tabs_b));
        int[] tabWords = {R.string.tour_tab_sound, R.string.tour_tab_music, R.string.tour_tab_realms,
                R.string.tour_tab_scenes, R.string.tour_tab_setup};
        for (int t = 0; t < tabWords.length; t++) {
            s.spot(getString(tabWords[t]), tabViews[t]);
        }
        steps.add(s);
        if (!scene) {
            s = new Tour.Step(getString(R.string.tour_go_t), getString(R.string.tour_go_b))
                    .spot(null, true, () -> new View[] {playerUi.miniStart});
            s.done = AudioService::isRunning;
            s.autoNext = true;
            s.status = () -> headphonesConnected() ? "" : getString(R.string.tour_go_phones);
            steps.add(s);
            s = new Tour.Step(getString(R.string.tour_echo_t), getString(R.string.tour_echo_b))
                    .spot(null, true, () -> new View[] {echoHead});
            s.enter = toEcho;
            s.done = () -> echoBody.getVisibility() == View.VISIBLE;
            s.autoNext = true;
            steps.add(s);
            s = new Tour.Step(getString(R.string.tour_try_t), getString(R.string.tour_try_b))
                    .spot(null, true, () -> new View[] {rowView("echo_time", false), rowView("echo_time", true)})
                    .spot(null, true, () -> new View[] {rowView("feedback", false), rowView("wet", true)});
            s.enter = () -> mainScroll.post(() -> scrollToShow(rowView("echo_time", false), 0.12f));
            s.done = () -> ParamStore.get("wet") >= 0.15f;
            s.status = () -> getString(ParamStore.get("wet") >= 0.15f ? R.string.tour_try_more : R.string.tour_try_first);
            steps.add(s);
        }
        s = new Tour.Step(getString(R.string.tour_end_t), getString(scene ? R.string.tour_end_scene_b : R.string.tour_end_b));
        s.enter = top;
        steps.add(s);

        tour = new Tour(this, look, steps, () -> {
            for (Map.Entry<String, Float> e : echoBefore.entrySet()) {
                if (ParamStore.get(e.getKey()) != e.getValue()) {
                    ParamStore.set(e.getKey(), e.getValue());
                }
            }
            if ((echoBody.getVisibility() == View.VISIBLE) != echoWasOpen) {
                echoHead.performClick();
            }
            settingRows.refreshRows();
            settingRows.updateMoverUi();
            if (Build.VERSION.SDK_INT >= 33 && backEndsTour != null) {
                getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback((android.window.OnBackInvokedCallback) backEndsTour);
                backEndsTour = null;
            }
        });
        tour.start((android.view.ViewGroup) mainScroll.getParent());
        if (Build.VERSION.SDK_INT >= 33) {
            android.window.OnBackInvokedCallback back = tour::end;
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_OVERLAY, back);
            backEndsTour = back;
        }
    }

    /** A slider row's name line, or its slider, for the tour. Null until its card has opened. */
    private View rowView(String id, boolean slider) {
        for (SettingRows.Row r : settingRows.rows) {
            if (r.param.id.equals(id) && r.slider != null) {
                return slider ? r.slider : (View) r.label.getParent();
            }
        }
        return null;
    }

    /** Scrolls the settings so {@code v} sits {@code fromTop} (0..1) of the way down the visible part. */
    private void scrollToShow(View v, float fromTop) {
        if (v == null) {
            return;
        }
        int y = 0;
        for (View x = v; x != null && x != mainScroll; x = x.getParent() instanceof View ? (View) x.getParent() : null) {
            y += x.getTop();
        }
        mainScroll.scrollTo(0, Math.max(0, y - Math.round(mainScroll.getHeight() * fromTop)));
    }

    @Override
    protected void onDestroy() {
        if (tour != null && tour.isRunning()) {
            tour.end();   // (puts Echo back)
        }
        super.onDestroy();
    }

    /** A sheet's title and the line under it. */
    View sheetHeading(String title, String hint) {
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setPadding(dp(8), dp(4), dp(8), dp(14));
        TextView t = new TextView(this);
        t.setText(title);
        t.setTypeface(look.heading());
        t.setTextSize(20);
        t.setTextColor(look.p.text);
        head.addView(t);
        if (hint != null) {
            TextView h = new TextView(this);
            h.setText(hint);
            look.hint(h);
            head.addView(h);
        }
        return head;
    }

    /** Something drawn at the end of a choice's row (the Scale sheet's dots). */
    interface RowExtra {
        View make(int index, boolean chosen);
    }

    /** One row per name: a tick on the chosen one (highlighted), the full name (wrapping), {@code extra} at the end. */
    void addListRows(LinearLayout content, String[] names, int chosen, RowExtra extra,
                             java.util.function.IntConsumer pick) {
        for (int i = 0; i < names.length; i++) {
            boolean on = i == chosen;
            int fg = on ? look.p.onContainer : look.p.text;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(52));
            row.setPadding(dp(12), dp(8), dp(12), dp(8));
            row.setBackground(on ? look.round(look.p.container, 14) : look.pressable(null, look.p.text, 14));
            row.setSelected(on);
            ImageView tick = new ImageView(this);
            tick.setImageResource(R.drawable.ic_check);
            tick.setImageTintList(android.content.res.ColorStateList.valueOf(fg));
            tick.setVisibility(on ? View.VISIBLE : View.INVISIBLE);
            row.addView(tick, new LinearLayout.LayoutParams(dp(20), dp(20)));
            TextView name = new TextView(this);
            name.setText(names[i]);
            look.label(name);
            name.setTextColor(fg);
            name.setPadding(dp(10), 0, dp(8), 0);
            row.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            if (extra != null) {
                row.addView(extra.make(i, on), new LinearLayout.LayoutParams(dp(128), dp(12)));
            }
            final int index = i;
            row.setOnClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                pick.accept(index);
            });
            content.addView(row, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }
    }

    /** Side-by-side buttons for {@code names}, {@code chosen} filled; a tap restyles them and calls {@code pick}. */
    View segmentBar(String[] names, int chosen, java.util.function.IntConsumer pick) {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackground(look.outline(look.p.line, 20));
        bar.setClipToOutline(true);
        bar.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
        android.graphics.drawable.GradientDrawable divider = new android.graphics.drawable.GradientDrawable();
        divider.setColor(look.p.line);
        divider.setSize(dp(1), 1);
        bar.setDividerDrawable(divider);
        TextView[] buttons = new TextView[names.length];
        for (int i = 0; i < names.length; i++) {
            TextView b = new TextView(this);
            b.setText(names[i]);
            b.setGravity(Gravity.CENTER);
            b.setTextSize(14);
            b.setMaxLines(1);
            choiceUi.styleSegment(b, i == chosen);
            final int index = i;
            b.setOnClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                for (int k = 0; k < buttons.length; k++) {
                    choiceUi.styleSegment(buttons[k], k == index);
                }
                pick.accept(index);
            });
            buttons[i] = b;
            bar.addView(b, new LinearLayout.LayoutParams(0, dp(44), 1f));
        }
        return bar;
    }

    /**
     * An AlertDialog in the app's look: rounded on the sheet colour, the fonts, buttons in the accent. Every dialog
     * (confirmations, Save, the kit and feedback ones) is built with this instead of AlertDialog.Builder.
     */
    final class LookDialog extends AlertDialog.Builder {
        LookDialog() {
            super(MainActivity.this);
        }

        @Override
        public AlertDialog create() {
            AlertDialog d = super.create();
            d.setOnShowListener(x -> {
                if (d.getWindow() != null) {
                    d.getWindow().setBackgroundDrawable(new android.graphics.drawable.InsetDrawable(
                            look.round(look.p.sheet, 28), dp(16), dp(24), dp(16), dp(24)));
                    look.fonts(d.getWindow().getDecorView());
                }
                int titleId = getResources().getIdentifier("alertTitle", "id", "android");
                TextView title = titleId != 0 ? d.findViewById(titleId) : null;
                if (title != null) {
                    title.setTypeface(look.heading());
                    title.setTextColor(look.p.text);
                }
                for (int which : new int[] {AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL}) {
                    Button b = d.getButton(which);
                    if (b != null) {
                        b.setTypeface(look.textStrong());
                        b.setTextColor(look.p.primaryText);
                        b.setAllCaps(false);
                    }
                }
            });
            return d;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        if (requestCode == REQ_EXPORT) {
            presetUi.exportTo(uri);
        } else if (requestCode == REQ_IMPORT) {
            presetUi.importFrom(uri);
        } else if (requestCode == REQ_IMPORT_SCENE) {
            sceneUi.importSceneFrom(uri);
        } else if (requestCode == REQ_IMPORT_SCENE_FOLDER) {
            sceneUi.importSceneFolderFrom(uri);
        } else if (requestCode == REQ_IMPORT_KIT || requestCode == REQ_IMPORT_KIT_FOLDER) {
            kitUi.importKitFrom(uri, requestCode == REQ_IMPORT_KIT_FOLDER);
        }
    }

    /** The file name behind a picked document, e.g. "Eargasm.rj.zip"; empty if unknown. */
    String displayName(Uri uri) {
        try (android.database.Cursor c = getContentResolver().query(
                uri, new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst() && !c.isNull(0)) {
                return c.getString(0);
            }
        } catch (RuntimeException e) {
            // Fall through.
        }
        return "";
    }

    void toast(int messageRes, Object... args) {
        Toast.makeText(this, getString(messageRes, args), Toast.LENGTH_SHORT).show();
    }

    // ---- engine control ----

    void onStartStop() {
        if (AudioService.isRunning()) {
            startService(new Intent(this, AudioService.class).setAction(AudioService.ACTION_STOP));
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            List<String> wanted = new ArrayList<>();
            wanted.add(Manifest.permission.RECORD_AUDIO);
            if (Build.VERSION.SDK_INT >= 33) {
                wanted.add(Manifest.permission.POST_NOTIFICATIONS);
            }
            requestPermissions(wanted.toArray(new String[0]), REQ_PERMISSIONS);
            return;
        }
        startAudio();
    }

    private void startAudio() {
        startForegroundService(
                new Intent(this, AudioService.class).setAction(AudioService.ACTION_START));
    }

    @Override
    @SuppressWarnings("NullableProblems")
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQ_MIC) {
            if (walkthrough != null) {
                walkthrough.refresh();
            }
            return;
        }
        if (requestCode != REQ_PERMISSIONS) {
            return;
        }
        for (int i = 0; i < permissions.length; i++) {
            if (Manifest.permission.RECORD_AUDIO.equals(permissions[i])
                    && results[i] == PackageManager.PERMISSION_GRANTED) {
                startAudio();
                return;
            }
        }
        playerUi.status.setText(R.string.mic_permission_required);
    }

    /** Reflects engine state and headphone presence on screen. */
    private void updateUi() {
        boolean on = AudioService.isRunning();
        playerUi.updateStartButtons(on);
        playerUi.updateStatus();
        warning.setVisibility(headphonesConnected() ? View.GONE : View.VISIBLE);
        playerUi.updateNowLine();
    }

    private boolean headphonesConnected() {
        for (AudioDeviceInfo d : audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            switch (d.getType()) {
                case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
                case AudioDeviceInfo.TYPE_WIRED_HEADSET:
                case AudioDeviceInfo.TYPE_USB_HEADSET:
                case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
                    return true;
                default:
                    if (Build.VERSION.SDK_INT >= 31
                            && d.getType() == AudioDeviceInfo.TYPE_BLE_HEADSET) {
                        return true;
                    }
            }
        }
        return false;
    }

    int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
