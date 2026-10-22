// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.dadcraftttv.ouralia;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
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

    private final List<Row> rows = new ArrayList<>();
    /** The mover controls under each slider, greyed out while their section's movers are off. */
    private final List<MoverUi> moverUis = new ArrayList<>();
    private AudioManager audioManager;

    private TextView status;
    /** The bar pinned to the bottom: what is playing and why, Start/Stop, hold the world, save this sound. */
    private TextView nowLine;
    private final Map<String, TextView> budgetLines = new HashMap<>();   // budget name -> its "used / left" line
    private final Map<View, String> fadedUnless = new HashMap<>();   // a row -> the setting it needs on (faded while off)
    private Button holdButton;
    private Button snapButton;
    private final android.os.Handler uiHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private static final long NOW_LINE_MS = 1000;
    private final Runnable nowLineTick = new Runnable() {
        @Override
        public void run() {
            updateNowLine();
            updateMoverDots();
            updateIntensityRow();
            updateStatus();
            if (walkthrough != null) {
                walkthrough.refresh();   // headphones plugged in meanwhile
            }
            updateDiagnostics();
            uiHandler.postDelayed(this, NOW_LINE_MS);
        }
    };
    private TextView warning;
    private Button startStop;
    private Button undoButton;
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
    /** Setup's Appearance card (AppearanceUi.java). */
    private final AppearanceUi appearanceUi = new AppearanceUi(this);
    /** The Music tab's Kits card (KitUi.java). */
    private final KitUi kitUi = new KitUi(this);
    /** Presets (PresetUi.java). */
    private final PresetUi presetUi = new PresetUi(this);
    /** Setup's About card (AboutUi.java). */
    private final AboutUi aboutUi = new AboutUi(this);
    /** The Scenes tab (SceneUi.java). */
    private final SceneUi sceneUi = new SceneUi(this);
    LinearLayout xyPadSlot;          // where the pad sits on the Sound tab (an XY pad for the movers)
    /** One dropdown per automatic world, and the line saying which one is playing. */
    private final List<WorldRow> worldRows = new ArrayList<>();
    private TextView worldStatus;

    private static final class WorldRow {
        final Worlds.World world;
        final TextView value;   // the preset's name on the Realm's tile
        List<String> presets = new ArrayList<>();

        WorldRow(Worlds.World world, TextView value) {
            this.world = world;
            this.value = value;
        }
    }

    /** One on-screen control: a slider with a label, or a switch (for toggles). */
    private static final class MoverUi {
        final String sectionId;
        final String paramId;
        final View box;        // everything: the movement dropdown and the details
        final View details;    // speed and depth, shown only while a movement is chosen

        MoverUi(String sectionId, String paramId, View box, View details) {
            this.sectionId = sectionId;
            this.paramId = paramId;
            this.box = box;
            this.details = details;
        }
    }

    private static final class Row {
        final Param param;
        final TextView label;     // slider rows only
        final SeekBar slider;     // slider rows only
        final android.widget.CompoundButton switchView;  // toggle rows (a switch, or a card header's Movers pill)
        String[] choicesShown;    // the names last shown (the Kit lists change when user kits come and go)
        TextView tile;            // pick-one rows shown as a tile that opens a sheet: its value
        TextView[] segment;       // pick-one rows shown as side-by-side buttons
        TextView segmentHint;     // ...and the chosen one's explanation under them

        Row(Param param, TextView label, SeekBar slider, android.widget.CompoundButton switchView) {
            this.param = param;
            this.label = label;
            this.slider = slider;
            this.switchView = switchView;
            this.choicesShown = param.choices;
        }
    }

    /** A dropdown of connected audio devices, plus "Automatic" as the first entry. */
    private static final class DevicePicker {
        final boolean input;
        final View tile;
        final TextView value;   // the device shown on the tile
        List<AudioDevices.Device> devices = new ArrayList<>();
        String[] names = new String[0];   // what the sheet lists: Automatic, then the devices
        int selected;
        String shownKey = AudioSettings.AUTOMATIC;   // what the tile currently displays

        DevicePicker(boolean input, View tile, TextView value) {
            this.input = input;
            this.tile = tile;
            this.value = value;
        }
    }

    private DevicePicker inputPicker;
    private DevicePicker outputPicker;
    private TextView micHint;

    /** Advice that appears when a Bluetooth headset is the chosen microphone. */
    private void updateMicHint() {
        if (micHint == null) {
            return;
        }
        AudioDevices.Device in = AudioDevices.find(this, true);
        if (AudioDevices.isLeAudio(in)) {
            micHint.setText(R.string.mic_hint_le);
        } else if (AudioDevices.isBluetoothMic(in)) {
            micHint.setText(R.string.mic_hint_call);
        } else if (AudioDevices.wiredWithoutMic(this)) {
            micHint.setText(R.string.mic_hint_no_wired_mic);
        } else {
            micHint.setText("");
        }
        micHint.setVisibility(micHint.length() == 0 ? View.GONE : View.VISIBLE);
    }

    /** Re-checks for headphones when something is plugged in or removed. */
    private final AudioDeviceCallback deviceCallback = new AudioDeviceCallback() {
        @Override
        @SuppressWarnings("NullableProblems")
        public void onAudioDevicesAdded(AudioDeviceInfo[] added) {
            refreshDevicePickers();
            updateUi();
        }

        @Override
        @SuppressWarnings("NullableProblems")
        public void onAudioDevicesRemoved(AudioDeviceInfo[] removed) {
            refreshDevicePickers();
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
            refreshRows();
            uiHandler.removeCallbacks(morphFollow);
            uiHandler.post(morphFollow);   // the sliders follow the glide on screen
        });
        nudgeChip = chip(randomRow, R.drawable.ic_wave, R.string.nudge, () -> {
            ParamStore.nudge(ParamStore.get("nudge_amount"));
            refreshRows();
        });
        undoButton = chip(randomRow, R.drawable.ic_undo, R.string.undo, () -> {
            ParamStore.undo();
            refreshRows();
        });
        // the XY pad and its hint on a card that folds like the sections (DadCraft: hide it if you don't use it)
        View gap = new View(this);
        sound.addView(gap, new LinearLayout.LayoutParams(0, dp(6)));
        xyPadSlot = addSection(sound, "xy_pad", getString(R.string.xy_pad_title), true);

        // ---- Realms ----
        Worlds.load(this);
        addSection(realms, "worlds", getString(R.string.section_worlds), true, this::fillWorldsSection);

        // ---- the sections, each on its tab ----
        for (Params.Section section : Params.SECTIONS) {
            LinearLayout tab = "beat".equals(section.id) || "music".equals(section.id) || "mix".equals(section.id) ? music
                    : "behavior".equals(section.id) ? setup : sound;
            if (section.group != null) {
                addGroupHeading(tab, section.group);
            }
            addSection(tab, section.id, section.title, section.openByDefault,
                    body -> fillEffectSection(body, section), headerExtras(section));
        }

        addSection(music, "kits", getString(R.string.section_kits), false, kitUi::fillKitsSection);

        // ---- Setup ----
        addAudioSection(setup);
        addDiagnosticsSection(setup);
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
        refreshRows();   // rows on a hidden tab skip every refresh (a preset loaded on Sound left The Band's kit menus stale)
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
        bottom.addView(buildPlayer());
        LinearLayout.LayoutParams navParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(64));
        navParams.setMargins(0, dp(8), 0, 0);
        bottom.addView(buildTabStrip(), navParams);
        buildPlayerSheet();
        return bottom;
    }

    private DialView miniDial, playerDial;
    private TextView dialHint;
    private ImageButton miniStart, miniSave;
    private View playerMiddle;   // (the tour points at it)
    private ImageButton presetSave, presetExport, presetImport;
    private Button randomizeChip, nudgeChip;
    private Tour tour;                     // the spotlight tour after the walkthrough, while it shows
    private Object backEndsTour;           // its OnBackInvokedCallback on Android 13+
    private TextView miniStatus, miniSub;
    // One bottom sheet, slid up over a dimmed screen, for whatever needs it: the player, the Key and Scale pickers.
    private View sheetScrim;
    private LinearLayout sheetBox;
    private ScrollView sheetBody;   // scrolls when the content is taller than most of the screen (the kit lists)
    private LinearLayout playerContent;
    private boolean sheetOpen;
    private Object backClosesSheet;   // an OnBackInvokedCallback on Android 13+ (kept as Object for older ones)

    /** The docked player: the small dial with Start inside, the status line, and Save sound. */
    private View buildPlayer() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(look.round(look.p.dock, Look.RADIUS_CARD));
        card.setElevation(dp(2));
        card.setPadding(dp(8), dp(8), dp(6), dp(8));

        android.widget.FrameLayout ringBox = new android.widget.FrameLayout(this);
        miniDial = new DialView(this, look, false, "");
        ringBox.addView(miniDial, new android.widget.FrameLayout.LayoutParams(dp(56), dp(56)));
        miniStart = new ImageButton(this);
        miniStart.setBackground(look.pressable(look.round(look.p.primary, 20), look.p.onPrimary, 20));
        miniStart.setImageTintList(android.content.res.ColorStateList.valueOf(look.p.onPrimary));
        miniStart.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        miniStart.setPadding(dp(11), dp(11), dp(11), dp(11));
        miniStart.setOnClickListener(v -> onStartStop());
        ringBox.addView(miniStart, new android.widget.FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER));
        card.addView(ringBox, new LinearLayout.LayoutParams(dp(56), dp(56)));

        LinearLayout middle = new LinearLayout(this);
        middle.setOrientation(LinearLayout.HORIZONTAL);
        middle.setGravity(Gravity.CENTER_VERTICAL);
        middle.setPadding(dp(10), 0, dp(6), 0);
        middle.setBackground(look.pressable(null, look.p.text, Look.RADIUS_TILE));
        middle.setContentDescription(getString(R.string.player_open));
        middle.setOnClickListener(v -> openPlayer());
        playerMiddle = middle;
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        miniStatus = new TextView(this);
        miniStatus.setTypeface(look.heading());
        miniStatus.setTextSize(Look.TEXT_LABEL);
        miniStatus.setTextColor(look.p.text);
        miniStatus.setSingleLine(true);
        miniStatus.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(miniStatus);
        miniSub = new TextView(this);
        look.hint(miniSub);
        miniSub.setSingleLine(true);
        miniSub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(miniSub);
        middle.addView(texts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        ImageView up = new ImageView(this);
        up.setImageResource(R.drawable.ic_chevron_up);
        up.setImageTintList(android.content.res.ColorStateList.valueOf(look.p.muted));
        middle.addView(up, new LinearLayout.LayoutParams(dp(20), dp(20)));
        card.addView(middle, new LinearLayout.LayoutParams(0, dp(56), 1f));

        miniSave = new ImageButton(this);
        miniSave.setImageResource(R.drawable.ic_bookmark);
        miniSave.setImageTintList(android.content.res.ColorStateList.valueOf(look.p.onContainer));
        miniSave.setBackground(look.pressable(look.round(look.p.container, 22), look.p.onContainer, 22));
        miniSave.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        miniSave.setPadding(dp(12), dp(12), dp(12), dp(12));
        miniSave.setContentDescription(getString(R.string.snapshot));
        miniSave.setOnClickListener(v -> presetUi.saveSnapshot());
        card.addView(miniSave, new LinearLayout.LayoutParams(dp(44), dp(44)));
        return card;
    }

    /**
     * The bottom sheet (its content swapped in by {@link #openSheet}) and the player's content: the big Intensity
     * dial (drag around the ring) with Start/Stop in its middle, the full status, the realm line, Hold realm and Save
     * sound.
     */
    private void buildPlayerSheet() {
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

        playerContent = new LinearLayout(this);
        playerContent.setOrientation(LinearLayout.VERTICAL);
        playerContent.setGravity(Gravity.CENTER_HORIZONTAL);
        playerContent.setPadding(dp(4), 0, dp(4), 0);

        android.widget.FrameLayout dialBox = new android.widget.FrameLayout(this);
        playerDial = new DialView(this, look, true, getString(R.string.intensity_caption));
        playerDial.setContentDescription(getString(R.string.intensity_card));
        playerDial.setListener((v, done) -> {
            ParamStore.set(Intensity.ID, v);
            updateIntensityRow();
            if (done) {
                refreshRows();   // the slider in Input & output follows
            }
        });
        dialBox.addView(playerDial, new android.widget.FrameLayout.LayoutParams(dp(248), dp(248)));
        startStop = new Button(this);
        look.button(startStop, true);
        startStop.setTypeface(look.value());   // DadCraft: the words in the slider numbers' font
        startStop.setBackground(look.pressable(look.round(look.p.primary, 52), look.p.onPrimary, 52));
        startStop.setPadding(0, dp(20), 0, dp(16));
        startStop.setCompoundDrawablePadding(dp(2));
        startStop.setOnClickListener(v -> onStartStop());
        dialBox.addView(startStop, new android.widget.FrameLayout.LayoutParams(dp(104), dp(104), Gravity.CENTER));
        playerContent.addView(dialBox, new LinearLayout.LayoutParams(dp(248), dp(248)));

        status = new TextView(this);
        look.hint(status);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(6), 0, 0);
        playerContent.addView(status);
        dialHint = new TextView(this);   // (hidden while a scene plays: a scene has no Intensity)
        dialHint.setText(R.string.dial_hint);
        look.hint(dialHint);
        dialHint.setTextSize(12);
        dialHint.setGravity(Gravity.CENTER);
        dialHint.setPadding(0, dp(2), 0, 0);
        playerContent.addView(dialHint);
        nowLine = new TextView(this);
        look.label(nowLine);
        nowLine.setTextSize(Look.TEXT_HINT + 1);
        nowLine.setGravity(Gravity.CENTER);
        nowLine.setPadding(0, dp(4), 0, 0);
        playerContent.addView(nowLine);
        LinearLayout row = newButtonRow(playerContent);
        row.setPadding(0, dp(12), 0, 0);
        holdButton = addButton(row, R.string.hold_world, () -> {
            Worlds.setHeld(!Worlds.isHeld());
            updateNowLine();
        });
        snapButton = addButton(row, R.string.snapshot, presetUi::saveSnapshot);
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

    private void openPlayer() {
        openSheet(playerContent);
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

    /** Start's icon and words follow whether the sound is on. */
    private void updateStartButtons(boolean on) {
        miniStart.setImageResource(on ? R.drawable.ic_stop : R.drawable.ic_play);
        miniStart.setContentDescription(getString(on ? R.string.stop : R.string.start));
        startStop.setText(on ? R.string.stop : R.string.start);
        android.graphics.drawable.Drawable icon = getDrawable(on ? R.drawable.ic_stop : R.drawable.ic_play).mutate();
        icon.setTint(look.p.onPrimary);
        startStop.setCompoundDrawablesRelativeWithIntrinsicBounds(null, icon, null, null);
    }

    /** The dials follow Intensity (empty while a scene plays: a scene has no Intensity), and so does the player's line. */
    private void updateIntensityRow() {
        if (playerDial == null) {
            return;
        }
        SceneLibrary.Scene scene = SceneSettings.current(this);
        float v = ParamStore.get(Intensity.ID);
        playerDial.setActive(scene == null);
        miniDial.setActive(scene == null);
        dialHint.setVisibility(scene == null ? View.VISIBLE : View.GONE);
        playerDial.setValue(v);
        miniDial.setValue(v);
        String sub = scene != null ? scene.name : getString(R.string.scene_built_in);
        String realm = playingRealm();
        if (scene == null && realm != null) {   // DadCraft: the bar showed Built-in effects while a Realm played
            sub = getString(R.string.mini_realm, realm);
            if (Worlds.isHeld()) {
                sub = getString(R.string.now_line_held, sub);
            }
        }
        if (scene == null) {
            sub += " · " + getString(R.string.intensity_card_value, Math.round(v * 100))
                    + (Modulation.isMoving(Intensity.ID) ? MOVER_DOT : "");
        }
        miniSub.setText(sub);
    }
    /** The title of the Realm playing now (Realm Hopper on, built-in effects running), or null. */
    private String playingRealm() {
        if (!AudioService.isRunning() || !Worlds.isEnabled()) {
            return null;
        }
        String id = Worlds.currentWorld();
        for (Worlds.World w : Worlds.ALL) {
            if (w.id.equals(id)) {
                return w.title;
            }
        }
        return null;
    }

    /** The accent: the palette's (Ouralia violet until Appearance offers a choice). */
    private int accentColor() {
        return look.p.primary;
    }

    /** One line: the world playing now and, in a few words, why. Refreshed once a second while visible. */
    private void updateNowLine() {
        boolean builtIn = AudioService.isRunning() && SceneSettings.current(this) == null;
        boolean worlds = builtIn && Worlds.isEnabled();
        holdButton.setVisibility(worlds ? View.VISIBLE : View.GONE);
        holdButton.setText(Worlds.isHeld() ? R.string.holding_world : R.string.hold_world);
        snapButton.setVisibility(SceneSettings.current(this) == null ? View.VISIBLE : View.GONE);
        miniSave.setVisibility(snapButton.getVisibility());
        if (!worlds) {
            nowLine.setVisibility(View.GONE);
            return;
        }
        nowLine.setVisibility(View.VISIBLE);
        String id = Worlds.currentWorld();
        String name = getString(R.string.worlds_yours);
        for (Worlds.World w : Worlds.ALL) {
            if (w.id.equals(id)) {
                name = w.title;
            }
        }
        List<String> why = new ArrayList<>();
        int minute = Worlds.minuteOfDayNow();
        if (minute >= WorldSelector.NIGHT_START_MINUTE && minute < WorldSelector.NIGHT_END_MINUTE) {
            why.add(getString(R.string.why_night));
        }
        double nowS = android.os.SystemClock.elapsedRealtimeNanos() / 1e9;
        if (WorldSelector.UNSTABLE.equals(id)) {
            why.add(getString(R.string.why_shaken));
        }
        if (StepDetector.walking(nowS)) {
            boolean clock = ParamStore.get("beat_follow") >= 0.5f && AudioService.tempo() > 0;
            why.add(clock ? getString(R.string.why_walking_bpm, Math.round(AudioService.tempo()))
                    : getString(R.string.why_walking));
        }
        if (PhoneContext.inPocket()) {
            why.add(getString(R.string.why_pocket));
        }
        if (CalmBuild.level() > 0.02f) {
            why.add(getString(R.string.why_building));
        }
        if (HandlingDetector.level() > 0.3f) {
            why.add(getString(R.string.why_handling));
        }
        int room = Worlds.roomQuiet() ? R.string.why_room_quiet
                : Worlds.roomLoud() ? (Worlds.roomLively() ? R.string.why_room_lively : R.string.why_room_steady)
                : R.string.why_room_middle;
        why.add(getString(room, Math.round(Worlds.roomLevelDb())));
        if (ParamStore.get("beat_level") > 0 && ParamStore.get("beat_follow") >= 0.5f && ParamStore.get("rhythm_src") >= 0.5f) {
            int[] kit = AudioService.kit();
            why.add(getString(R.string.why_kit, kit[0], kit[1], kit[2], kit[3]));
        }
        String line = getString(R.string.now_line, name, String.join(" \u00b7 ", why));
        if (Worlds.isHeld()) {
            line = getString(R.string.now_line_held, line);
        }
        nowLine.setText(line);
    }

    /** While Randomize glides, redraw the sliders a few times a second so they visibly move. */
    private final Runnable morphFollow = new Runnable() {
        @Override
        public void run() {
            refreshRows();
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
            updateWorldStatus();
            updateIntensityRow();   // the player's line names the Realm
            refreshRows();   // sliders move on their own while a world fades in
        });
        refreshWorldRows();
        updateWorldStatus();
        uiHandler.post(nowLineTick);
        audioManager.registerAudioDeviceCallback(deviceCallback, null);
        refreshDevicePickers();
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

    /** A tappable header that shows or hides the returned container. State is remembered. */
    LinearLayout addSection(LinearLayout parent, String id, String title,
                                    boolean openByDefault) {
        return addSection(parent, id, title, openByDefault, null);
    }

    /**
     * A foldable section. {@code fill} (may be null) builds the rows the first time the section is shown, so
     * folded sections cost nothing at startup.
     */
    LinearLayout addSection(LinearLayout parent, String id, String title, boolean openByDefault,
                                    java.util.function.Consumer<LinearLayout> fill) {
        return addSection(parent, id, title, openByDefault, fill, new ArrayList<>());
    }

    /**
     * A foldable section card: a full-width tappable header (title, {@code extras} such as the Movers pill and the
     * section's on/off switch, a chevron), the rows inside. {@code fill} (may be null) builds the rows the first time
     * the section opens, so folded sections cost nothing at startup.
     */
    LinearLayout addSection(LinearLayout parent, String id, String title, boolean openByDefault,
                                    java.util.function.Consumer<LinearLayout> fill, List<View> extras) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        look.card(card);
        LinearLayout headRow = new LinearLayout(this);
        headRow.setOrientation(LinearLayout.HORIZONTAL);
        headRow.setGravity(Gravity.CENTER_VERTICAL);
        headRow.setMinimumHeight(dp(56));
        headRow.setPadding(dp(Look.CARD_PAD), dp(6), dp(14), dp(6));
        headRow.setBackground(look.pressable(null, look.p.text, 0));
        LinearLayout titles = new LinearLayout(this);   // the title, and its bracketed explanation under it
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView header = new TextView(this);
        header.setTextSize(Look.TEXT_HEADING);
        header.setTypeface(look.heading());
        header.setTextColor(look.p.text);
        titles.addView(header);
        String subtitle = splitLabel(title)[1];
        if (!subtitle.isEmpty()) {
            TextView sub = new TextView(this);
            sub.setText(subtitle);
            look.hint(sub);
            titles.addView(sub);
        }
        headRow.addView(titles, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        for (View extra : extras) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginStart(dp(8));
            headRow.addView(extra, lp);
        }
        ImageView chevron = new ImageView(this);
        chevron.setImageTintList(android.content.res.ColorStateList.valueOf(look.p.muted));
        LinearLayout.LayoutParams chevronParams = new LinearLayout.LayoutParams(dp(22), dp(22));
        chevronParams.setMarginStart(dp(10));
        headRow.addView(chevron, chevronParams);
        sectionChevrons.put(header, chevron);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(Look.CARD_PAD), 0, dp(Look.CARD_PAD), dp(16));

        boolean[] filled = {fill == null};
        Runnable show = () -> {
            if (!filled[0]) {
                filled[0] = true;
                fill.accept(body);
                look.fonts(body);
                refreshRows();   // the new rows start with the stored values
            }
        };
        boolean open = UiSettings.isExpanded(this, id, openByDefault);
        showSectionState(header, body, title, open);
        if (open) {
            show.run();
        }
        headRow.setOnClickListener(v -> {
            boolean nowOpen = body.getVisibility() != View.VISIBLE;
            UiSettings.setExpanded(this, id, nowOpen);
            showSectionState(header, body, title, nowOpen);
            if (nowOpen) {
                show.run();
                refreshRows();   // rows skipped while folded catch up
            }
        });

        card.addView(headRow);
        card.addView(body);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, 0, 0, dp(Look.CARD_GAP));
        parent.addView(card, cardParams);
        sectionCards.put(id, card);
        return body;
    }

    /** One effect section: its Movers switch, then each slider followed by its movement settings. */
    private void fillEffectSection(LinearLayout body, Params.Section section) {
        Param moversSwitch = Params.byId(Params.moversSwitchId(section.id));
        Param onSwitch = Params.onSwitch(section);   // (both sit on the card's header)
        for (Param p : Params.ALL) {
            if (p.section.equals(section.id) && p.moverOf == null && p != moversSwitch && p != onSwitch) {
                if (p.id.equals("music_key")) {   // Key and Scale: two tiles side by side, each opening a sheet
                    addKeyScaleTiles(body);
                    continue;
                } else if (p.id.equals("music_scale")) {
                    continue;
                }
                int[] heading = SUBHEADINGS.get(p.id);
                if (heading != null) {
                    addSubheading(body, heading[0], heading[1]);
                }
                if (p.id.startsWith("kitl_")) {
                    addLayerPanel(body, p, Params.byId("kitb_" + p.id.substring(5)));
                    continue;
                } else if (p.id.startsWith("kitb_")) {
                    continue;   // (inside its layer's panel)
                } else if (p.id.equals("echo_throw") || p.id.equals("echo_depth")) {
                    LinearLayout box = new LinearLayout(this);   // (only the New scatter style uses them)
                    box.setOrientation(LinearLayout.VERTICAL);
                    addRow(box, p);
                    body.addView(box);
                    fadedUnless.put(box, "echo_scatter_style");
                    continue;
                }
                addRow(body, p);
                if (Params.byId(Params.moverId(p.id, "mv")) != null) {
                    addMoverRows(body, section.id, p);
                }
                if (p.budget != null && lastOfBudget(p)) {   // the glitch matrix: how much of it is in use
                    TextView line = new TextView(this);
                    line.setTextSize(14);
                    line.setTypeface(Typeface.DEFAULT_BOLD);
                    line.setPadding(0, dp(10), 0, dp(4));
                    body.addView(line);
                    budgetLines.put(p.budget, line);
                }
            }
        }
        updateBudgetLines();
    }

    /**
     * The groups inside a long card (DadCraft, Oct 20: the kit menus "almost kind of run together"): a heading, and
     * a hint or 0, before the setting that starts each group.
     */
    private static final Map<String, int[]> SUBHEADINGS = new HashMap<>();
    static {
        SUBHEADINGS.put("gain", new int[]{R.string.sub_mic, 0});
        SUBHEADINGS.put("pocket_hush", new int[]{R.string.sub_pocket, R.string.sub_pocket_hint});
        SUBHEADINGS.put("dry", new int[]{R.string.sub_output, 0});
        SUBHEADINGS.put("music_var", new int[]{R.string.sub_notes, 0});
        SUBHEADINGS.put(Kits.ID, new int[]{R.string.kits_heading, 0});
        SUBHEADINGS.put("kitp_mel", new int[]{R.string.kits_mix_heading, R.string.kits_mix_hint});
        SUBHEADINGS.put("kitl_mel", new int[]{R.string.kits_layers_heading, R.string.kits_layers_hint});
        SUBHEADINGS.put("mel_rhythm", new int[]{R.string.music_more_heading, 0});
        SUBHEADINGS.put("mix_kick", new int[]{R.string.sub_drums, 0});
        SUBHEADINGS.put("music_pad", new int[]{R.string.sub_pad_bass, 0});
        SUBHEADINGS.put("music_arp", new int[]{R.string.sub_melody, 0});
        SUBHEADINGS.put("mix_ring", new int[]{R.string.sub_answers, R.string.sub_answers_hint});
        SUBHEADINGS.put("gc_amount", new int[]{R.string.sub_cloud, 0});
        SUBHEADINGS.put("gc_pct_stutter", new int[]{R.string.sub_kinds, R.string.sub_kinds_hint});
        SUBHEADINGS.put("glitch_hold", new int[]{R.string.sub_freeze, 0});
    }

    /** A small accent heading inside a card, with an optional hint under it and a line above it (not at the top). */
    /** A family of cards on a tab (Voice, Grit...): the subheading's style, between the cards. */
    private void addGroupHeading(LinearLayout tab, String title) {
        TextView head = new TextView(this);
        head.setText(title.toUpperCase(java.util.Locale.ROOT));
        head.setTypeface(look.textStrong());
        head.setTextSize(Look.TEXT_HINT);
        head.setLetterSpacing(0.1f);
        head.setTextColor(look.p.primaryText);
        head.setAccessibilityHeading(true);
        head.setPadding(dp(Look.CARD_PAD), dp(10), dp(Look.CARD_PAD), dp(8));
        tab.addView(head);
    }

    private void addSubheading(LinearLayout parent, int title, int hint) {
        if (parent.getChildCount() > 0) {
            View rule = new View(this);
            rule.setBackgroundColor(look.p.line);
            LinearLayout.LayoutParams ruleParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
            ruleParams.setMargins(0, dp(24), 0, dp(14));
            parent.addView(rule, ruleParams);
        } else {
            parent.addView(new View(this), new LinearLayout.LayoutParams(0, dp(8)));
        }
        TextView head = new TextView(this);
        head.setText(getString(title).toUpperCase(java.util.Locale.ROOT));
        head.setTypeface(look.textStrong());
        head.setTextSize(Look.TEXT_HINT);
        head.setLetterSpacing(0.1f);
        head.setTextColor(look.p.primaryText);
        head.setAccessibilityHeading(true);
        parent.addView(head);
        if (hint != 0) {
            TextView line = new TextView(this);
            line.setText(hint);
            look.hint(line);
            line.setPadding(0, dp(2), 0, 0);
            parent.addView(line);
        }
    }

    /** A part's layer and its blend in one outlined panel; the blend fades while the layer is None. */
    private void addLayerPanel(LinearLayout parent, Param layer, Param blend) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(look.outline(look.p.line, Look.RADIUS_TILE));
        panel.setPadding(dp(12), 0, dp(12), dp(12));
        addRow(panel, layer, false);
        LinearLayout blendBox = new LinearLayout(this);
        blendBox.setOrientation(LinearLayout.VERTICAL);
        addRow(blendBox, blend, false);
        panel.addView(blendBox);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(12), 0, 0);
        parent.addView(panel, lp);
        fadedUnless.put(blendBox, layer.id);
    }

    /** Rows that do nothing while another setting is off or None (a layer's blend, the New scatter's sliders) fade. */
    private void updateFadedRows() {
        for (Map.Entry<View, String> e : fadedUnless.entrySet()) {
            e.getKey().setAlpha(ParamStore.get(e.getValue()) >= 0.5f ? 1f : 0.4f);
        }
    }

    /** A section's own on/off switch ("Echo enabled"...): shown on its card's header. Null if it has none. */
    /**
     * What a section's header carries: the Movers pill (automatic movement for this section, on or off) and the
     * section's on/off switch, so both work with the card folded.
     */
    private List<View> headerExtras(Params.Section section) {
        List<View> extras = new ArrayList<>();
        Param movers = Params.byId(Params.moversSwitchId(section.id));
        if (movers != null) {
            android.widget.ToggleButton pill = new android.widget.ToggleButton(this);
            pill.setTextOn(getString(R.string.movers_on));
            pill.setTextOff(getString(R.string.movers_off));
            look.pill(pill);
            pill.setContentDescription(movers.label);
            pill.setChecked(ParamStore.get(movers.id) >= 0.5f);
            pill.setOnClickListener(v -> {
                ParamStore.set(movers.id, pill.isChecked() ? 1f : 0f);
                updateMoverUi();   // greys the section's mover controls in or out
            });
            extras.add(pill);
            rows.add(new Row(movers, null, null, pill));
        }
        Param on = Params.onSwitch(section);
        if (on != null) {
            Switch sw = new Switch(this);
            look.switchTint(sw);
            sw.setContentDescription(on.label);
            sw.setChecked(ParamStore.get(on.id) >= 0.5f);
            sw.setOnClickListener(v -> {
                ParamStore.set(on.id, sw.isChecked() ? 1f : 0f);
                updateMoverUi();
            });
            extras.add(sw);
            rows.add(new Row(on, null, null, sw));
        }
        return extras;
    }

    /** True for the last slider of its budget group (the line showing what is used goes under it). */
    private static boolean lastOfBudget(Param p) {
        boolean after = false;
        for (Param q : Params.ALL) {
            if (q == p) {
                after = true;
            } else if (after && p.budget.equals(q.budget)) {
                return false;
            }
        }
        return true;
    }

    /** "Glitch budget: 80% used, 20% left to silence" under the glitch matrix sliders. */
    private void updateBudgetLines() {
        for (Map.Entry<String, TextView> e : budgetLines.entrySet()) {
            float used = 0;
            for (Param q : Params.ALL) {
                if (e.getKey().equals(q.budget)) {
                    used += ParamStore.get(q.id);
                }
            }
            int u = Math.round(used);
            e.getValue().setText(u > 100 ? getString(R.string.glitch_budget_over, u)
                    : getString(R.string.glitch_budget, u, 100 - u));
        }
    }

    /** The Worlds section: an on/off switch, what is playing now, and which preset each world uses. */
    private void fillWorldsSection(LinearLayout body) {
        Switch auto = new Switch(this);
        auto.setText(R.string.worlds_auto);
        look.label(auto);
        look.switchTint(auto);
        auto.setSwitchPadding(dp(16));
        auto.setMinHeight(dp(52));
        auto.setChecked(Worlds.isEnabled());
        auto.setOnClickListener(v -> {
            if (!auto.isChecked()) {
                Worlds.setEnabled(false);
                updateWorldStatus();
                return;
            }
            // DadCraft: before Realms take over, say that they change the user's own settings (the mix too)
            auto.setChecked(false);
            new LookDialog()
                    .setTitle(R.string.realms_warn_title)
                    .setMessage(R.string.realms_warn_text)
                    .setPositiveButton(R.string.realms_warn_ok, (d, w) -> {
                        auto.setChecked(true);
                        Worlds.setEnabled(true);
                        updateWorldStatus();
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        });
        body.addView(auto);

        worldStatus = new TextView(this);
        look.hint(worldStatus);
        worldStatus.setPadding(0, dp(2), 0, dp(6));
        body.addView(worldStatus);

        // each Realm: its name and when it plays, its preset on a tile (a list sheet), what it changes
        for (Worlds.World w : Worlds.ALL) {
            LinearLayout block = new LinearLayout(this);
            block.setOrientation(LinearLayout.VERTICAL);
            block.setPadding(0, dp(14), 0, 0);
            LinearLayout head = new LinearLayout(this);
            head.setOrientation(LinearLayout.HORIZONTAL);
            head.setGravity(Gravity.CENTER_VERTICAL);
            head.setPadding(dp(4), 0, dp(4), dp(8));
            TextView name = new TextView(this);
            name.setText(w.title);
            look.label(name);
            head.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            TextView when = new TextView(this);
            when.setText(w.when);
            look.hint(when);
            head.addView(when);
            block.addView(head);

            LinearLayout tile = new LinearLayout(this);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(Gravity.CENTER_VERTICAL);
            tile.setBackground(look.choiceTile(getDrawable(R.drawable.ic_chevron_down).mutate()));
            tile.setMinimumHeight(dp(52));
            tile.setPadding(dp(16), dp(8), dp(40), dp(8));
            TextView value = new TextView(this);
            value.setTypeface(look.heading());
            value.setTextSize(15);
            value.setTextColor(look.p.text);
            value.setMaxLines(1);
            value.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tile.addView(value);
            tile.setContentDescription(getString(R.string.world_row, w.title, w.when));
            WorldRow row = new WorldRow(w, value);
            worldRows.add(row);
            tile.setOnClickListener(v -> showRealmSheet(row));
            block.addView(tile);
            addRealmIngredients(block, w);
            body.addView(block);
        }
        TextView note = new TextView(this);
        note.setText(R.string.worlds_note);
        look.hint(note);
        note.setPadding(dp(4), dp(16), dp(4), 0);
        body.addView(note);
        refreshWorldRows();
        updateWorldStatus();
    }

    /** A Realm's preset picker: Off, then every preset (built-in and yours), the chosen one ticked. */
    private void showRealmSheet(WorldRow row) {
        String[] names = new String[row.presets.size() + 1];
        names[0] = getString(R.string.world_off);
        for (int i = 0; i < row.presets.size(); i++) {
            names[i + 1] = row.presets.get(i);
        }
        int chosen = row.presets.indexOf(Worlds.presetName(row.world.id)) + 1;
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.addView(sheetHeading(row.world.title, row.world.when));
        addListRows(content, names, chosen, null, i -> {
            Worlds.setPreset(row.world.id, i == 0 ? Worlds.NONE : row.presets.get(i - 1));
            refreshWorldRows();
            closeSheet();
        });
        openSheet(content);
    }

    /**
     * A Realm's ingredients: "What it changes: everything" folds open to one switch per group (Rhythm, Music, Echo...).
     * Off = the Realm leaves that group at your own settings.
     */
    private void addRealmIngredients(LinearLayout body, Worlds.World w) {
        TextView summary = new TextView(this);
        look.hint(summary);
        summary.setGravity(Gravity.CENTER_VERTICAL);
        summary.setMinHeight(dp(40));
        summary.setPadding(dp(4), dp(4), dp(4), dp(4));
        summary.setCompoundDrawablePadding(dp(6));
        summary.setBackground(look.pressable(null, look.p.text, Look.RADIUS_TILE));
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(8), 0, 0, dp(4));
        box.setVisibility(View.GONE);
        Runnable update = () -> {
            List<String> left = new ArrayList<>();
            for (String[] g : Worlds.GROUPS) {
                if (!Worlds.uses(w.id, g[0])) {
                    left.add(g[1].replaceAll(" \\(.*\\)", ""));
                }
            }
            String what = left.isEmpty() ? getString(R.string.realm_changes_all)
                    : left.size() == Worlds.GROUPS.length ? getString(R.string.realm_changes_none)
                    : getString(R.string.realm_changes_but, String.join(", ", left));
            summary.setText(what);
            boolean open = box.getVisibility() == View.VISIBLE;
            android.graphics.drawable.Drawable chevron = getDrawable(open ? R.drawable.ic_chevron_up : R.drawable.ic_chevron_down).mutate();
            chevron.setTint(look.p.muted);
            chevron.setBounds(0, 0, dp(18), dp(18));
            summary.setCompoundDrawablesRelative(null, null, chevron, null);
        };
        for (String[] g : Worlds.GROUPS) {
            Switch c = new Switch(this);
            c.setText(g[1]);
            look.label(c);
            c.setTextSize(Look.TEXT_HINT + 1);
            look.switchTint(c);
            c.setSwitchPadding(dp(16));
            c.setMinHeight(dp(48));
            c.setChecked(Worlds.uses(w.id, g[0]));
            c.setOnCheckedChangeListener((b, on) -> {
                Worlds.setUses(w.id, g[0], on);
                update.run();
            });
            box.addView(c);
        }
        summary.setOnClickListener(v -> {
            box.setVisibility(box.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            update.run();
        });
        update.run();
        body.addView(summary);
        body.addView(box);
    }

    /** Shows each Realm's preset on its tile (the list changes when you save or import a preset). */
    void refreshWorldRows() {
        List<String> presets = new ArrayList<>(PresetManager.builtInNames());
        presets.addAll(PresetManager.userNames(this));
        for (WorldRow row : worldRows) {
            row.presets = presets;
            String chosen = Worlds.presetName(row.world.id);
            row.value.setText(presets.contains(chosen) ? chosen : getString(R.string.world_off));
        }
    }

    private void updateWorldStatus() {
        if (worldStatus == null) {
            return;
        }
        String id = Worlds.currentWorld();
        String name = getString(R.string.worlds_yours);
        for (Worlds.World w : Worlds.ALL) {
            if (w.id.equals(id)) {
                name = w.title;
            }
        }
        worldStatus.setText(!Worlds.isEnabled() ? getString(R.string.worlds_off_status)
                : getString(R.string.worlds_now, name));
    }

    /** Each section header's chevron, by its title view. */
    private final Map<TextView, ImageView> sectionChevrons = new HashMap<>();
    private final Map<String, LinearLayout> sectionCards = new HashMap<>();   // by section id: header row, then body

    private void showSectionState(TextView header, View body, String title, boolean open) {
        body.setVisibility(open ? View.VISIBLE : View.GONE);
        header.setText(splitLabel(title)[0]);
        ImageView chevron = sectionChevrons.get(header);
        if (chevron != null) {
            chevron.setImageResource(open ? R.drawable.ic_chevron_up : R.drawable.ic_chevron_down);
        }
        ((View) header.getParent().getParent()).setContentDescription(
                getString(open ? R.string.section_open : R.string.section_closed, title));
    }

    private void addAudioSection(LinearLayout parent) {
        LinearLayout body = addSection(parent, "audio", getString(R.string.section_audio), false);
        inputPicker = addDevicePicker(body, R.string.audio_input, true);
        outputPicker = addDevicePicker(body, R.string.audio_output, false);

        if (!AudioService.canSelectDevices()) {
            for (DevicePicker picker : new DevicePicker[] {inputPicker, outputPicker}) {
                picker.tile.setEnabled(false);
                picker.tile.setAlpha(0.5f);
            }
        }
        micHint = new TextView(this);
        look.hint(micHint);
        micHint.setPadding(dp(4), dp(8), 0, 0);
        body.addView(micHint);
        updateMicHint();
        TextView note = new TextView(this);
        note.setText(AudioService.canSelectDevices()
                ? R.string.audio_note : R.string.audio_unsupported);
        look.hint(note);
        note.setPadding(dp(4), dp(12), 0, 0);
        body.addView(note);
    }

    /** An audio device choice: a tile (its name small, the device large) that opens a list sheet of devices. */
    private DevicePicker addDevicePicker(LinearLayout parent, int labelRes, boolean input) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER_VERTICAL);
        tile.setBackground(look.choiceTile(getDrawable(R.drawable.ic_chevron_down).mutate()));
        tile.setMinimumHeight(dp(64));
        tile.setPadding(dp(16), dp(8), dp(40), dp(8));
        TextView name = new TextView(this);
        name.setText(labelRes);
        look.hint(name);
        name.setTextSize(12);
        tile.addView(name);
        TextView value = new TextView(this);
        value.setTypeface(look.heading());
        value.setTextSize(15);
        value.setTextColor(look.p.text);
        tile.addView(value);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(12), 0, 0);
        parent.addView(tile, lp);
        DevicePicker picker = new DevicePicker(input, tile, value);
        tile.setOnClickListener(v -> {
            LinearLayout content = new LinearLayout(this);
            content.setOrientation(LinearLayout.VERTICAL);
            content.addView(sheetHeading(getString(labelRes), null));
            addListRows(content, picker.names, picker.selected, null, i -> {
                closeSheet();
                String key = i == 0 || i > picker.devices.size()
                        ? AudioSettings.AUTOMATIC : picker.devices.get(i - 1).key;
                if (key.equals(picker.shownKey)) {
                    return;
                }
                picker.shownKey = key;
                picker.selected = i;
                value.setText(picker.names[i]);
                AudioSettings.setDeviceKey(MainActivity.this, picker.input, key);
                updateMicHint();
                if (AudioService.isRunning()) {
                    startForegroundService(new Intent(MainActivity.this, AudioService.class)
                            .setAction(AudioService.ACTION_RESTART));
                }
            });
            openSheet(content);
        });
        return picker;
    }

    /** Rebuilds both dropdowns from the devices connected right now. */
    private void refreshDevicePickers() {
        fillPicker(inputPicker);
        fillPicker(outputPicker);
        updateMicHint();
    }

    private void fillPicker(DevicePicker picker) {
        picker.devices = AudioDevices.list(this, picker.input);
        List<String> names = new ArrayList<>();
        names.add(getString(picker.input ? R.string.audio_automatic
                : picker.devices.isEmpty() ? R.string.audio_no_headphones
                : R.string.audio_output_automatic));
        for (AudioDevices.Device d : picker.devices) {
            names.add(d.label);
        }
        // A remembered device that isn't plugged in shows as Automatic, but stays remembered.
        String saved = AudioSettings.deviceKey(this, picker.input);
        int selected = 0;
        for (int i = 0; i < picker.devices.size(); i++) {
            if (picker.devices.get(i).key.equals(saved)) {
                selected = i + 1;
            }
        }
        picker.shownKey = selected == 0 ? AudioSettings.AUTOMATIC : saved;
        picker.names = names.toArray(new String[0]);
        picker.selected = selected;
        picker.value.setText(picker.names[selected]);
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
        LinearLayout echoCard = sectionCards.get("echo");
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
        refreshRows();
        updateMoverUi();

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
                .spot(getString(R.string.tour_start), miniStart)
                .spot(getString(R.string.tour_middle), playerMiddle)
                .spot(getString(R.string.tour_snapshot), miniSave));
        s = new Tour.Step(getString(R.string.tour_tabs_t), getString(R.string.tour_tabs_b));
        int[] tabWords = {R.string.tour_tab_sound, R.string.tour_tab_music, R.string.tour_tab_realms,
                R.string.tour_tab_scenes, R.string.tour_tab_setup};
        for (int t = 0; t < tabWords.length; t++) {
            s.spot(getString(tabWords[t]), tabViews[t]);
        }
        steps.add(s);
        if (!scene) {
            s = new Tour.Step(getString(R.string.tour_go_t), getString(R.string.tour_go_b))
                    .spot(null, true, () -> new View[] {miniStart});
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
            refreshRows();
            updateMoverUi();
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
        for (Row r : rows) {
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

    private void addRow(LinearLayout parent, Param p) {
        addRow(parent, p, false);
    }

    /** {@code compact} makes a smaller, tighter row, for the mover settings under a slider. */
    private void addRow(LinearLayout parent, Param p, boolean compact) {
        if (p.toggle) {
            // the name (its bracketed explanation on a quieter line under it) and the switch; tapping the words
            // flips it too
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.setMinimumHeight(dp(52));
            line.setPadding(0, dp(6), 0, dp(6));
            line.addView(nameAndHint(p, compact), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            Switch sw = new Switch(this);
            look.switchTint(sw);
            sw.setContentDescription(p.label);
            sw.setChecked(ParamStore.get(p.id) >= 0.5f);   // right from the start (folded rows aren't refreshed)
            sw.setOnClickListener(v -> {
                ParamStore.set(p.id, sw.isChecked() ? 1f : 0f);
                updateMoverUi();   // a section's movers switch greys its mover controls in or out
            });
            LinearLayout.LayoutParams swParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            swParams.setMarginStart(dp(12));
            line.addView(sw, swParams);
            line.setOnClickListener(v -> sw.performClick());
            parent.addView(line);
            rows.add(new Row(p, null, null, sw));
            return;
        }
        if (p.choices != null) {
            addChoiceRow(parent, p, compact);
            return;
        }

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(0, dp(compact ? 4 : 14), 0, 0);

        // the name (and its explanation) on the left, the value on the right in the accent, then the lock
        head.addView(nameAndHint(p, compact), new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView label = new TextView(this);   // (the value: what refreshRows and the mover dot update)
        label.setTypeface(look.value());
        label.setTextSize(compact ? Look.TEXT_HINT : Look.TEXT_VALUE);
        label.setTextColor(look.p.primaryText);
        label.setPadding(dp(12), 0, 0, 0);
        head.addView(label);

        if (Params.lockable(p)) {
            CheckBox lock = new CheckBox(this);
            look.lock(lock, getDrawable(R.drawable.ic_lock), getDrawable(R.drawable.ic_lock_open));
            lock.setContentDescription(getString(R.string.lock) + ": " + p.label);
            lock.setChecked(ParamStore.isLocked(p.id));
            lock.setOnCheckedChangeListener((b, checked) -> ParamStore.setLocked(p.id, checked));
            LinearLayout.LayoutParams lockParams = new LinearLayout.LayoutParams(dp(44), dp(44));
            lockParams.setMarginStart(dp(8));   // a little room between the value and its padlock
            head.addView(lock, lockParams);
        }
        parent.addView(head);

        SeekBar slider = new SeekBar(this);
        slider.setMax(1000);
        look.slider(slider);
        slider.setMinimumHeight(dp(40));
        slider.setContentDescription(p.label);
        float v0 = ParamStore.get(p.id);                  // right from the start (folded rows aren't refreshed)
        slider.setProgress(Math.round(p.toNorm(v0) * 1000f));
        label.setText(valueText(p, v0));
        parent.addView(slider);
        rows.add(new Row(p, label, slider, null));

        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                if (fromUser) {  // ignore changes we make ourselves in refreshRows()
                    float value = p.toValue(progress / 1000f);
                    float room = ParamStore.budgetRoom(p);   // the glitch matrix: the shares can't pass 100%
                    if (value > room) {
                        value = room;
                        s.setProgress(Math.round(p.toNorm(value) * 1000));
                    }
                    ParamStore.set(p.id, value);
                    label.setText(valueText(p, value));
                    if (p.budget != null) {
                        updateBudgetLines();
                    }
                }
            }

            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
    }

    /** Key and Scale as two big tiles (easier to see and hit than a menu); each opens its picker sheet. */
    private void addKeyScaleTiles(LinearLayout body) {
        LinearLayout pair = new LinearLayout(this);
        pair.setOrientation(LinearLayout.HORIZONTAL);
        pair.setPadding(0, dp(14), 0, dp(2));
        Param key = Params.byId("music_key"), scale = Params.byId("music_scale");
        pair.addView(pickTile(key, this::showKeySheet, false), tileParams(0, dp(4)));
        pair.addView(pickTile(scale, this::showScaleSheet, false), tileParams(dp(4), 0));
        body.addView(pair);
    }

    private static LinearLayout.LayoutParams tileParams(int left, int right) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(left, 0, right, 0);
        return lp;
    }

    /** A tile: the setting's name small, its value large, a chevron; tapping it runs {@code open}. */
    private View pickTile(Param p, Runnable open, boolean compact) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER_VERTICAL);
        tile.setBackground(look.choiceTile(getDrawable(R.drawable.ic_chevron_down).mutate()));
        tile.setMinimumHeight(dp(compact ? 52 : 64));
        tile.setPadding(dp(16), dp(8), dp(40), dp(8));
        TextView name = new TextView(this);
        name.setText(p.shown());
        look.hint(name);
        name.setTextSize(12);
        tile.addView(name);
        TextView value = new TextView(this);
        value.setTypeface(look.heading());
        value.setTextSize(compact ? 15 : 17);
        value.setTextColor(look.p.text);
        value.setMaxLines(1);   // a long name ("Minor pentatonic") shrinks to fit rather than being cut
        value.setAutoSizeTextTypeUniformWithConfiguration(12, compact ? 15 : 17, 1, TypedValue.COMPLEX_UNIT_SP);
        value.setText(p.choices[choiceIndex(p, ParamStore.get(p.id))]);
        tile.addView(value);
        tile.setOnClickListener(v -> open.run());
        Row row = new Row(p, null, null, null);
        row.tile = value;
        rows.add(row);
        return tile;
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

    /** The Key picker: the twelve notes as big buttons, four to a row; one tap picks and closes. */
    private void showKeySheet() {
        Param p = Params.byId("music_key");
        int chosen = choiceIndex(p, ParamStore.get(p.id));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.addView(sheetHeading(p.shown(), getString(R.string.key_sheet_hint)));
        for (int r = 0; r < 3; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int c = 0; c < 4; c++) {
                int i = r * 4 + c;
                boolean on = i == chosen;
                TextView b = new TextView(this);
                b.setText(p.choices[i]);
                b.setGravity(Gravity.CENTER);
                b.setTypeface(look.heading());
                b.setTextSize(18);
                b.setTextColor(on ? look.p.onPrimary : p.choices[i].length() > 1 ? look.p.muted : look.p.text);
                int bg = on ? look.p.primary : look.p.s2;
                b.setBackground(look.pressable(look.round(bg, Look.RADIUS_TILE), on ? look.p.onPrimary : look.p.text, Look.RADIUS_TILE));
                b.setSelected(on);
                b.setOnClickListener(v -> {
                    v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                    pickChoice(p, i);
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(60), 1f);
                lp.setMargins(dp(4), dp(4), dp(4), dp(4));
                row.addView(b, lp);
            }
            content.addView(row);
        }
        look.fonts(content);
        openSheet(content);
    }

    /** The Scale picker: one row per scale with its notes as dots (filled = in the scale), the chosen one ticked. */
    private void showScaleSheet() {
        Param p = Params.byId("music_scale");
        Param key = Params.byId("music_key");
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.addView(sheetHeading(p.shown(),
                getString(R.string.scale_sheet_hint, key.choices[choiceIndex(key, ParamStore.get(key.id))])));
        addChoiceList(content, p, (i, on) -> new ScaleDots(i < Params.SCALE_STEPS.length ? Params.SCALE_STEPS[i] : new int[0],
                on ? look.p.onContainer : look.p.primary, look.p.line));
        openSheet(content);
    }

    /** Something drawn at the end of a choice's row (the Scale sheet's dots). */
    interface RowExtra {
        View make(int index, boolean chosen);
    }

    /** A setting's choices as list rows; a pick is stored and closes the sheet. */
    private void addChoiceList(LinearLayout content, Param p, RowExtra extra) {
        addListRows(content, p.choices, choiceIndex(p, ParamStore.get(p.id)), extra, i -> pickChoice(p, i));
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

    /** Twelve dots, one per semitone above the key: filled where the scale has a note. Decoration only. */
    private final class ScaleDots extends View {
        private final boolean[] in = new boolean[12];
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final int onColor, offColor;

        ScaleDots(int[] steps, int onColor, int offColor) {
            super(MainActivity.this);
            for (int s : steps) {
                in[s % 12] = true;
            }
            this.onColor = onColor;
            this.offColor = offColor;
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float step = getWidth() / 12f, r = Math.min(step * 0.32f, getHeight() / 2f), y = getHeight() / 2f;
            for (int i = 0; i < 12; i++) {
                paint.setColor(in[i] ? onColor : offColor);
                canvas.drawCircle(step * (i + 0.5f), y, r, paint);
            }
        }
    }

    /** A pick made in a sheet: stored, every row follows, the sheet closes. */
    private void pickChoice(Param p, int i) {
        ParamStore.set(p.id, i);
        updateMoverUi();
        refreshRows();
        closeSheet();
    }

    /**
     * A pick-one setting: two or three short choices as side-by-side buttons (one tap, the chosen one's explanation
     * underneath), longer lists as a tile that opens a list sheet.
     */
    private void addChoiceRow(LinearLayout parent, Param p, boolean compact) {
        if (segmentable(p)) {
            addSegmentRow(parent, p, compact);
            return;
        }
        View tile = pickTile(p, () -> showChoiceSheet(p), compact);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(compact ? 6 : 12), 0, 0);
        parent.addView(tile, lp);
    }

    /** A choice's short name: the words before its explanation in brackets ("New (how the sound changes)" -> "New"). */
    static String shortName(String choice) {
        int i = choice.indexOf(" (");
        return i > 0 ? choice.substring(0, i) : choice;
    }

    /** The explanation in brackets, or "". */
    static String detail(String choice) {
        int i = choice.indexOf(" (");
        return i > 0 && choice.endsWith(")") ? choice.substring(i + 2, choice.length() - 1) : "";
    }

    /** Two choices with short names up to 18 letters, or three up to 12, fit side by side. */
    private static boolean segmentable(Param p) {
        int n = p.choices.length;
        if (n < 2 || n > 3) {
            return false;
        }
        for (String c : p.choices) {
            if (shortName(c).length() > (n == 2 ? 18 : 12)) {
                return false;
            }
        }
        return true;
    }

    /**
     * The order the buttons show in: as stored, except that "Both" sits in the middle (DadCraft: the original on the
     * left, Both as the unity in the middle, the other on the right). Only the screen order: stored values stay.
     */
    static int[] displayOrder(String[] choices) {
        int n = choices.length;
        if (n == 3 && shortName(choices[2]).equals("Both")) {
            return new int[] {0, 2, 1};
        }
        int[] order = new int[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        return order;
    }

    private void addSegmentRow(LinearLayout parent, Param p, boolean compact) {
        View label = nameAndHint(p, compact);
        label.setPadding(0, dp(compact ? 6 : 14), 0, dp(8));   // (in line with the slider names)
        parent.addView(label);
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackground(look.outline(look.p.line, 20));
        bar.setClipToOutline(true);
        bar.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
        android.graphics.drawable.GradientDrawable divider = new android.graphics.drawable.GradientDrawable();
        divider.setColor(look.p.line);
        divider.setSize(dp(1), 1);
        bar.setDividerDrawable(divider);
        Row row = new Row(p, null, null, null);
        row.segment = new TextView[p.choices.length];
        for (int i : displayOrder(p.choices)) {
            TextView b = new TextView(this);
            b.setText(shortName(p.choices[i]));
            b.setGravity(Gravity.CENTER);
            b.setTextSize(14);
            b.setMaxLines(1);
            b.setAutoSizeTextTypeUniformWithConfiguration(11, 14, 1, TypedValue.COMPLEX_UNIT_SP);
            b.setPadding(dp(6), 0, dp(6), 0);
            String full = p.choices[i];
            b.setContentDescription(p.label + ": " + full);
            final int pick = i;
            b.setOnClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                if (pick != choiceIndex(p, ParamStore.get(p.id))) {
                    ParamStore.set(p.id, pick);
                    updateMoverUi();
                    refreshRows();
                }
            });
            bar.addView(b, new LinearLayout.LayoutParams(0, dp(compact ? 40 : 44), 1f));
            row.segment[i] = b;
        }
        parent.addView(bar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        row.segmentHint = new TextView(this);
        look.hint(row.segmentHint);
        row.segmentHint.setPadding(0, dp(6), 0, 0);
        parent.addView(row.segmentHint);
        rows.add(row);
        showSegment(row, choiceIndex(p, ParamStore.get(p.id)));
    }

    /** The chosen button filled; its explanation (if it has one) underneath. */
    private void showSegment(Row r, int chosen) {
        for (int i = 0; i < r.segment.length; i++) {
            styleSegment(r.segment[i], i == chosen);
        }
        String d = detail(r.param.choices[chosen]);
        r.segmentHint.setText(d);
        r.segmentHint.setVisibility(d.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void styleSegment(TextView b, boolean on) {
        b.setSelected(on);
        b.setTypeface(on ? look.textStrong() : look.text());
        b.setTextColor(on ? look.p.onContainer : look.p.text);
        b.setBackground(on ? new android.graphics.drawable.ColorDrawable(look.p.container)
                : look.pressable(null, look.p.text, 0));
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
            styleSegment(b, i == chosen);
            final int index = i;
            b.setOnClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                for (int k = 0; k < buttons.length; k++) {
                    styleSegment(buttons[k], k == index);
                }
                pick.accept(index);
            });
            buttons[i] = b;
            bar.addView(b, new LinearLayout.LayoutParams(0, dp(44), 1f));
        }
        return bar;
    }

    /** A list sheet for a pick-one setting: its choices, the chosen one ticked; one tap picks and closes. */
    private void showChoiceSheet(Param p) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.addView(sheetHeading(p.shown(), null));
        addChoiceList(content, p, null);
        openSheet(content);
    }

    /** The entry a pick-one value shows: the first one if the value points past the list (a user kit gone). */
    private static int choiceIndex(Param p, float v) {
        int i = Math.round(v);
        return i >= 0 && i < p.choices.length ? i : 0;
    }

    /** The movement, speed and depth controls of one slider's mover, indented under it. */
    private void addMoverRows(LinearLayout parent, String sectionId, Param slider) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), 0, 0, dp(4));
        addRow(box, Params.byId(Params.moverId(slider.id, "mv")), true);
        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        addRow(details, Params.byId(Params.moverId(slider.id, "mvspeed")), true);
        addRow(details, Params.byId(Params.moverId(slider.id, "mvdepth")), true);
        box.addView(details);
        parent.addView(box);
        moverUis.add(new MoverUi(sectionId, slider.id, box, details));
    }

    /** Hides mover controls whose section has its Movers off; hides speed and depth while a mover is Off. */
    private void updateMoverUi() {
        for (MoverUi m : moverUis) {
            boolean on = ParamStore.get(Params.moversSwitchId(m.sectionId)) >= 0.5f;
            boolean moving = Math.round(ParamStore.get(Params.moverId(m.paramId, "mv"))) != 0;
            m.details.setVisibility(moving ? View.VISIBLE : View.GONE);
            // shown only while the card's Movers are on (greyed copies under every slider were clutter)
            m.box.setVisibility(on ? View.VISIBLE : View.GONE);
            setTreeEnabled(m.box, on);
        }
    }

    private static void setTreeEnabled(View view, boolean enabled) {
        view.setEnabled(enabled);
        if (view instanceof LinearLayout) {
            LinearLayout group = (LinearLayout) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                setTreeEnabled(group.getChildAt(i), enabled);
            }
        }
    }

    /** A slider's value as shown on its right (its name is a separate label). */
    private String valueText(Param p, float value) {
        return p.format(value);
    }

    /** A setting's name and its bracketed explanation, split: "Calm builds (standing still...)". " *" stays on the name. */
    static String[] splitLabel(String shown) {
        String star = shown.endsWith(" *") ? " *" : "";
        String core = star.isEmpty() ? shown : shown.substring(0, shown.length() - 2);
        String hint = detail(core);
        String name = hint.isEmpty() ? core : shortName(core);
        return new String[] {name + star, hint};
    }

    /** The name in the label style and, if it has one, its explanation under it in the hint style. */
    private View nameAndHint(Param p, boolean compact) {
        String[] parts = splitLabel(p.shown());
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(this);
        name.setText(parts[0]);
        if (compact) {
            look.hint(name);
        } else {
            look.label(name);
        }
        box.addView(name);
        if (!parts[1].isEmpty()) {
            TextView hint = new TextView(this);
            hint.setText(parts[1]);
            look.hint(hint);
            if (compact) {
                hint.setTextSize(12);
            }
            box.addView(hint);
        }
        return box;
    }

    /** Pull current values from the store into every control. */
    /** A small dot after a slider's label while a mover is pushing it around (checked once a second). */
    private static final String MOVER_DOT = "  \u25CF";

    private void updateMoverDots() {
        for (Row r : rows) {
            if (r.label == null || !r.label.isShown()) {
                continue;
            }
            boolean on = AudioService.isRunning() && Modulation.isMoving(r.param.id);
            CharSequence text = r.label.getText();
            boolean has = text.toString().endsWith(MOVER_DOT);
            if (on != has) {
                r.label.setText(valueText(r.param, ParamStore.get(r.param.id)) + (on ? MOVER_DOT : ""));
            }
        }
    }

    void refreshRows() {
        for (Row r : rows) {
            View shown = r.slider != null ? r.slider : r.switchView != null ? r.switchView
                    : r.tile != null ? r.tile : r.segment[0];
            if (shown != null && !shown.isShown()) {
                continue;   // folded away: refreshed when its section opens (saves work while worlds fade)
            }
            float v = ParamStore.get(r.param.id);
            if (r.switchView != null) {
                r.switchView.setChecked(v >= 0.5f);
            } else if (r.tile != null) {
                if (r.choicesShown != r.param.choices) {   // (a user kit came or went)
                    r.choicesShown = r.param.choices;
                    kitUi.updateKitStatus();
                }
                r.tile.setText(r.param.choices[choiceIndex(r.param, v)]);
            } else if (r.segment != null) {
                showSegment(r, choiceIndex(r.param, v));
            } else {
                r.slider.setProgress(Math.round(r.param.toNorm(v) * 1000f));
                r.label.setText(valueText(r.param, v));
            }
        }
        updateMoverUi();
        updateBudgetLines();
        updateFadedRows();
        updateIntensityRow();
        undoButton.setEnabled(ParamStore.canUndo());
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

    private void onStartStop() {
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
        status.setText(R.string.mic_permission_required);
    }

    /** Reflects engine state and headphone presence on screen. */
    private void updateUi() {
        boolean on = AudioService.isRunning();
        updateStartButtons(on);
        updateStatus();
        warning.setVisibility(headphonesConnected() ? View.GONE : View.VISIBLE);
        updateNowLine();
    }

    /** The status line: stopped, running, paused for a call, an error, or the mic overloading (checked once a second). */
    private void updateStatus() {
        boolean on = AudioService.isRunning();
        String error = AudioService.getLastError();
        boolean missing = on && AudioService.objectsMissing();
        boolean overload = on && !AudioService.isPausedForCall() && AudioService.clips().warning(ClipWatch.IN);
        status.setTypeface(overload || missing ? look.textStrong() : look.text());
        if (error != null && !on) {
            status.setText(error);
        } else if (missing) {
            status.setText(R.string.status_objects_missing);
        } else if (overload) {
            status.setText(R.string.status_mic_overload);
        } else {
            int hz = AudioService.actualSampleRateHz();
            status.setText(!on ? getString(R.string.status_stopped)
                    : AudioService.isPausedForCall() ? getString(R.string.status_call)
                    : hz > 0 ? getString(R.string.status_running_rate, hz) : getString(R.string.status_running));
        }
        // the docked player shows the same, on one line: warnings in the error colour so they're seen at a glance
        miniStatus.setText(status.getText());
        miniStatus.setTextColor(overload || missing || (error != null && !on) ? look.p.err : look.p.text);
    }

    private TextView diagText;
    private android.widget.ProgressBar diagLevel;

    /** Setup's Diagnostics: the mic and headphones really in use, the mic level and overloads in the last minute. */
    private void addDiagnosticsSection(LinearLayout parent) {
        addSection(parent, "diagnostics", getString(R.string.section_diag), false, body -> {
            diagText = new TextView(this);
            diagText.setTextSize(14);
            diagText.setPadding(0, dp(4), 0, dp(4));
            body.addView(diagText);
            diagLevel = new android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            diagLevel.setMax(80);   // -80 .. 0 dB
            body.addView(diagLevel, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(10)));
            TextView note = new TextView(this);
            note.setText(R.string.diag_note);
            note.setTextSize(13);
            note.setPadding(0, dp(8), 0, 0);
            body.addView(note);
            updateDiagnostics();
        });
    }

    private void updateDiagnostics() {
        if (diagText == null || !diagText.isShown()) {
            return;
        }
        boolean on = AudioService.isRunning();
        diagLevel.setVisibility(on ? View.VISIBLE : View.GONE);
        if (!on) {
            diagText.setText(R.string.diag_not_running);
            return;
        }
        List<String> lines = new ArrayList<>();
        String mic = AudioDevices.inputInUse(this);
        String asked = AudioService.inputAsked();
        lines.add(mic != null ? getString(R.string.diag_mic, mic)
                : getString(R.string.diag_mic_unknown, asked != null ? asked : getString(R.string.diag_automatic)));
        if (AudioService.callModeMic()) {
            lines.add("  " + getString(R.string.diag_call_mode));
        }
        int hz = AudioService.actualSampleRateHz();
        String out = AudioService.outputLabel() != null ? AudioService.outputLabel() : "?";
        lines.add(hz > 0 ? getString(R.string.diag_out_rate, out, hz) : getString(R.string.diag_out, out));
        int level = Math.round(Math.max(-80f, Math.min(0f, Modulation.micLevelDb())));
        int gate = Math.round(ParamStore.get("gate_thresh"));
        lines.add(gate > -80 ? getString(R.string.diag_level_gate, level, gate) : getString(R.string.diag_level, level));
        diagLevel.setProgress(level + 80);
        ClipWatch c = AudioService.clips();
        if (!c.hasReports()) {
            lines.add(getString(R.string.diag_scene));
        } else {
            int in = c.episodes(ClipWatch.IN), outs = c.episodes(ClipWatch.OUT);
            lines.add(getString(R.string.diag_clip_in, in));
            if (in > 0) {
                lines.add("  " + getString(R.string.diag_clip_in_help));
            }
            lines.add(getString(R.string.diag_clip_gain, c.episodes(ClipWatch.GAIN)));
            lines.add(getString(R.string.diag_handling, AudioService.handlingSkipsLastMinute()));
            lines.add(getString(R.string.diag_clip_out, outs));
            if (outs > 0) {
                lines.add("  " + getString(R.string.diag_clip_out_help));
            }
        }
        diagText.setText(String.join("\n", lines));
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
