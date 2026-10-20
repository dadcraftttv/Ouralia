// SPDX-License-Identifier: GPL-3.0-or-later
package app.pdscenes;

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
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.puredata.core.PdBase;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Control screen: engine on/off, presets, randomize, and one control per parameter. */
public class MainActivity extends Activity {

    private static final int REQ_PERMISSIONS = 1;
    private static final int REQ_EXPORT = 2;
    private static final int REQ_MIC = 3;   // the walkthrough's "Allow microphone" (doesn't start the sound)
    private Walkthrough walkthrough;
    /** Name chosen in the export dialog, used once the file picker returns. */
    private String exportName = "preset";
    private static final int REQ_IMPORT = 3;
    private static final int REQ_IMPORT_SCENE = 4;
    private static final int REQ_IMPORT_SCENE_FOLDER = 5;
    private static final int REQ_IMPORT_KIT = 23;
    private static final int REQ_IMPORT_KIT_FOLDER = 24;
    private TextView kitStatus;

    private final List<Row> rows = new ArrayList<>();
    /** The mover controls under each slider, greyed out while their section's movers are off. */
    private final List<MoverUi> moverUis = new ArrayList<>();
    private AudioManager audioManager;

    private TextView status;
    /** The bar pinned to the bottom: what is playing and why, Start/Stop, hold the world, save this sound. */
    private TextView nowLine;
    private final Map<String, TextView> budgetLines = new HashMap<>();   // budget name -> its "used / left" line
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
    private TextView sceneLabel;
    /** Presets, randomize and the effect sections: hidden while an imported scene is selected. */
    // ---- tabs: Sound, Music, Realms, Scenes, Setup (the one long list had outgrown itself) ----
    private static final int TAB_SOUND = 0, TAB_MUSIC = 1, TAB_REALMS = 2, TAB_SCENES = 3, TAB_SETUP = 4;
    private static final int[] TAB_NAMES = {R.string.tab_sound, R.string.tab_music, R.string.tab_realms,
            R.string.tab_scenes, R.string.tab_setup};
    private final LinearLayout[] tabPages = new LinearLayout[TAB_NAMES.length];     // each tab's whole page
    private final LinearLayout[] tabBodies = new LinearLayout[TAB_NAMES.length];    // its controls (hidden for a scene)
    private final TextView[] tabNotices = new TextView[TAB_NAMES.length];           // "paused while a scene plays"
    private final LinearLayout[] tabViews = new LinearLayout[TAB_NAMES.length];   // the tab bar's buttons
    private final ImageView[] tabIcons = new ImageView[TAB_NAMES.length];
    private final TextView[] tabLabels = new TextView[TAB_NAMES.length];
    private final int[] tabScroll = new int[TAB_NAMES.length];
    private int currentTab = -1;
    /** The Ouralia look for this screen's theme (Look.java, Styler.java). */
    private Styler look;
    private LinearLayout xyPadSlot;          // where the pad sits on the Sound tab (an XY pad for the movers)
    private LinearLayout scenePadSlot;       // ...and on the Scenes tab (the scene's own pad)
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
    private ScenePad scenePad;
    private LinearLayout sceneLevels;
    private TextView padHint;

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
        title.setText(R.string.app_name);
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
        // the scene playing and its buttons, on a card
        LinearLayout sceneCard = new LinearLayout(this);
        sceneCard.setOrientation(LinearLayout.VERTICAL);
        look.card(sceneCard);
        sceneCard.setPadding(dp(Look.CARD_PAD), dp(16), dp(Look.CARD_PAD), dp(14));
        LinearLayout.LayoutParams sceneCardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sceneCardParams.setMargins(0, 0, 0, dp(Look.CARD_GAP));
        scenes.addView(sceneCard, sceneCardParams);
        sceneLabel = new TextView(this);
        sceneLabel.setTypeface(look.heading());
        sceneLabel.setTextSize(Look.TEXT_HEADING);
        sceneLabel.setTextColor(look.p.text);
        sceneLabel.setPadding(dp(2), 0, 0, dp(8));
        sceneCard.addView(sceneLabel);
        // Choose scene: one wide button (the list sheet); importing: two chips under it
        LinearLayout sceneRow = newButtonRow(sceneCard);
        Button choose = addButton(sceneRow, R.string.choose_scene, this::showScenes);
        choose.setBackground(look.choiceTile(getDrawable(R.drawable.ic_chevron_down).mutate()));
        choose.setTextColor(look.p.text);
        choose.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        choose.setPadding(dp(16), 0, dp(40), 0);
        LinearLayout importRow = newButtonRow(sceneCard);
        chip(importRow, R.drawable.ic_download, R.string.import_scene, this::startImportScene);
        chip(importRow, R.drawable.ic_folder, R.string.import_scene_folder, this::startImportSceneFolder);
        scenePadSlot = new LinearLayout(this);
        scenePadSlot.setOrientation(LinearLayout.VERTICAL);
        scenes.addView(scenePadSlot);

        scenePad = new ScenePad(this);
        scenePad.setContentDescription(getString(R.string.pad_description));
        scenePad.setListener(this::sendTouch);
        scenePad.setLook(look.p);
        padHint = new TextView(this);
        padHint.setText(R.string.pad_hint);
        look.hint(padHint);
        padHint.setPadding(dp(4), dp(8), dp(4), 0);

        sceneLevels = new LinearLayout(this);
        sceneLevels.setOrientation(LinearLayout.VERTICAL);
        addLevelSlider(sceneLevels, R.string.scene_volume, SceneSettings.volume(this), v -> {
            SceneSettings.setVolume(this, v);
            sendSceneLevelsIfRunning();
        });
        addLevelSlider(sceneLevels, R.string.scene_mic_volume, SceneSettings.micVolume(this), v -> {
            SceneSettings.setMicVolume(this, v);
            sendSceneLevelsIfRunning();
        });
        Switch timing = new Switch(this);
        timing.setText(R.string.scene_rjdj_timing);
        look.label(timing);
        look.switchTint(timing);
        timing.setSwitchPadding(dp(16));
        timing.setMinHeight(dp(52));
        timing.setChecked(SceneSettings.rjdjTiming(this));
        timing.setOnCheckedChangeListener((b, on) -> {
            SceneSettings.setRjdjTiming(this, on);
            if (AudioService.isRunning()) {   // the rate is chosen when the engine starts
                startForegroundService(new Intent(this, AudioService.class).setAction(AudioService.ACTION_RESTART));
            }
        });
        sceneLevels.addView(timing);
        TextView hint = new TextView(this);
        hint.setText(R.string.scene_headphones_hint);
        look.hint(hint);
        hint.setPadding(dp(4), dp(6), dp(4), 0);
        sceneLevels.addView(hint);
        scenes.addView(sceneLevels);

        // ---- Sound: presets and shortcuts, the XY pad, then the input and the effects ----
        // Presets: one wide button (the list sheet), then Save, Export and Import as small icon buttons
        LinearLayout presetRow = newButtonRow(sound);
        Button presets = addButton(presetRow, R.string.presets, this::showPresets);
        presets.setBackground(look.choiceTile(getDrawable(R.drawable.ic_chevron_down).mutate()));
        presets.setTextColor(look.p.text);
        presets.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        presets.setPadding(dp(16), 0, dp(40), 0);
        iconButton(presetRow, R.drawable.ic_add, R.string.save, this::showSave);
        iconButton(presetRow, R.drawable.ic_upload, R.string.export, this::startExport);
        iconButton(presetRow, R.drawable.ic_download, R.string.import_preset, this::startImport);

        // Randomize, Nudge, Undo: smaller outlined chips with icons
        LinearLayout randomRow = newButtonRow(sound);
        randomRow.setPadding(0, dp(2), 0, dp(4));
        chip(randomRow, R.drawable.ic_shuffle, R.string.randomize, () -> {
            ParamStore.randomize();
            refreshRows();
            uiHandler.removeCallbacks(morphFollow);
            uiHandler.post(morphFollow);   // the sliders follow the glide on screen
        });
        chip(randomRow, R.drawable.ic_wave, R.string.nudge, () -> {
            ParamStore.nudge(ParamStore.get("nudge_amount"));
            refreshRows();
        });
        undoButton = chip(randomRow, R.drawable.ic_undo, R.string.undo, () -> {
            ParamStore.undo();
            refreshRows();
        });
        xyPadSlot = new LinearLayout(this);
        xyPadSlot.setOrientation(LinearLayout.VERTICAL);
        look.card(xyPadSlot);   // the XY pad and its hint on a card, like the sections
        xyPadSlot.setPadding(dp(12), dp(4), dp(12), dp(14));
        LinearLayout.LayoutParams xyParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        xyParams.setMargins(0, dp(6), 0, dp(Look.CARD_GAP));
        sound.addView(xyPadSlot, xyParams);

        // ---- Realms ----
        Worlds.load(this);
        addSection(realms, "worlds", getString(R.string.section_worlds), true, this::fillWorldsSection);

        // ---- the sections, each on its tab ----
        for (Params.Section section : Params.SECTIONS) {
            LinearLayout tab = "beat".equals(section.id) || "music".equals(section.id) || "mix".equals(section.id) ? music
                    : "behavior".equals(section.id) ? setup : sound;
            addSection(tab, section.id, section.title, section.openByDefault,
                    body -> fillEffectSection(body, section), headerExtras(section));
        }

        addSection(music, "kits", getString(R.string.section_kits), false, this::fillKitsSection);

        // ---- Setup ----
        addAudioSection(setup);
        addDiagnosticsSection(setup);
        addAppearanceSection(setup);
        LinearLayout resetRow = newButtonRow(setup);
        Button reset = addButton(resetRow, R.string.reset_all, this::confirmResetAll);
        look.danger(reset);
        // the same gap above and below it as between the cards
        ((LinearLayout.LayoutParams) reset.getLayoutParams()).setMargins(dp(3), 0, dp(3), 0);
        ((LinearLayout.LayoutParams) resetRow.getLayoutParams()).bottomMargin = dp(Look.CARD_GAP);
        addAboutSection(setup);
        look.fonts(root);
        showTab(UiSettings.tab(this));
        refreshRows();
        if (FirstRun.showWalkthrough(UiSettings.walkthroughSeen(this), installTimes()[0], installTimes()[1])) {
            showWalkthrough();
        }
        updateSceneUi();
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
        miniSave.setOnClickListener(v -> saveSnapshot());
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
        snapButton = addButton(row, R.string.snapshot, this::saveSnapshot);
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
    private void openSheet(View content) {
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

    private void closeSheet() {
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
    public void onBackPressed() {   // Android 12 and older (13+ uses the callback registered in openSheet)
        if (sheetOpen) {
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
        if (scene == null) {
            sub += " · " + getString(R.string.intensity_card_value, Math.round(v * 100))
                    + (Modulation.isMoving(Intensity.ID) ? MOVER_DOT : "");
        }
        miniSub.setText(sub);
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

    /** Saves the sound playing right now as a preset named after the time, to rename later if wanted. */
    private void saveSnapshot() {
        String base = new java.text.SimpleDateFormat("EEE H-mm", java.util.Locale.getDefault()).format(new java.util.Date());
        List<String> taken = PresetManager.userNames(this);
        String name = base;
        for (int n = 2; taken.contains(name); n++) {
            name = base + " " + n;
        }
        try {
            String used = PresetManager.save(this, name, ParamStore.snapshot());
            toast(R.string.toast_saved, used);
            refreshWorldRows();
        } catch (IOException e) {
            toast(R.string.toast_save_failed, e.getMessage());
        }
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

    private LinearLayout newButtonRow(LinearLayout parent) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBaselineAligned(false);   // a two-line button would otherwise push its one-line neighbours down
        parent.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private Button addButton(LinearLayout row, int textRes, Runnable action) {
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
    private void iconButton(LinearLayout row, int icon, int description, Runnable action) {
        ImageButton b = new ImageButton(this);
        b.setImageResource(icon);
        b.setImageTintList(android.content.res.ColorStateList.valueOf(look.p.text));
        b.setBackground(look.pressable(look.round(look.p.s1, 14), look.p.text, 14));
        b.setContentDescription(getString(description));
        b.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(48), dp(48));
        lp.setMargins(dp(3), dp(4), dp(3), dp(4));
        row.addView(b, lp);
    }

    /**
     * An outlined chip with an icon and a word (Randomize, Nudge, Undo); the chips of a row share its width equally
     * (DadCraft: no open pocket on the right). It greys out when disabled.
     */
    private Button chip(LinearLayout row, int icon, int text, Runnable action) {
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
    private LinearLayout addSection(LinearLayout parent, String id, String title,
                                    boolean openByDefault) {
        return addSection(parent, id, title, openByDefault, null);
    }

    /**
     * A foldable section. {@code fill} (may be null) builds the rows the first time the section is shown, so
     * folded sections cost nothing at startup.
     */
    private LinearLayout addSection(LinearLayout parent, String id, String title, boolean openByDefault,
                                    java.util.function.Consumer<LinearLayout> fill) {
        return addSection(parent, id, title, openByDefault, fill, new ArrayList<>());
    }

    /**
     * A foldable section card: a full-width tappable header (title, {@code extras} such as the Movers pill and the
     * section's on/off switch, a chevron), the rows inside. {@code fill} (may be null) builds the rows the first time
     * the section opens, so folded sections cost nothing at startup.
     */
    private LinearLayout addSection(LinearLayout parent, String id, String title, boolean openByDefault,
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
        return body;
    }

    /** One effect section: its Movers switch, then each slider followed by its movement settings. */
    private void fillEffectSection(LinearLayout body, Params.Section section) {
        Param moversSwitch = Params.byId(Params.moversSwitchId(section.id));
        Param onSwitch = onSwitch(section);   // (both sit on the card's header)
        for (Param p : Params.ALL) {
            if (p.section.equals(section.id) && p.moverOf == null && p != moversSwitch && p != onSwitch) {
                if (p.id.equals("music_key")) {   // Key and Scale: two tiles side by side, each opening a sheet
                    addKeyScaleTiles(body);
                    continue;
                } else if (p.id.equals("music_scale")) {
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

    /** A section's own on/off switch ("Echo enabled"...): shown on its card's header. Null if it has none. */
    private static Param onSwitch(Params.Section section) {
        for (Param p : Params.ALL) {
            if (p.section.equals(section.id) && p.toggle && p.moverOf == null && p.label.endsWith(" enabled")) {
                return p;
            }
        }
        return null;
    }

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
        Param on = onSwitch(section);
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
    private void refreshWorldRows() {
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

    private void addAboutSection(LinearLayout parent) {
        LinearLayout body = addSection(parent, "about", getString(R.string.section_about), false);
        TextView text = new TextView(this);
        text.setTextSize(14);
        String source = getString(R.string.source_url);
        text.setText(source.isEmpty() ? getString(R.string.about_text)
                : getString(R.string.about_text_with_source, source));
        text.setPadding(0, 0, 0, dp(8));
        body.addView(text);
        TextView sensorLine = new TextView(this);
        sensorLine.setTextSize(12);
        sensorLine.setText(getString(R.string.sensors_line, SensorInput.describe(this)));
        sensorLine.setPadding(0, 0, 0, dp(8));
        body.addView(sensorLine);
        TextView feedback = new TextView(this);
        feedback.setTextSize(14);
        feedback.setAutoLinkMask(android.text.util.Linkify.EMAIL_ADDRESSES);
        feedback.setText(R.string.about_feedback);
        feedback.setPadding(0, 0, 0, dp(8));
        body.addView(feedback);
        LinearLayout row = newButtonRow(body);
        addButton(row, R.string.show_licenses, this::showLicenses);
        addButton(row, R.string.walk_show_again, this::showWalkthrough);
        LinearLayout row2 = newButtonRow(body);
        addButton(row2, R.string.feedback_send, this::sendFeedback);
    }

    /**
     * Opens an email to DadCraft with the phone, Android and app version filled in and, if the tester agrees, the
     * end of the event log (no audio: times, room levels, what the app did), so a report carries what's needed.
     */
    private void sendFeedback() {
        CheckBox withLog = new CheckBox(this);
        withLog.setText(R.string.feedback_with_log);
        withLog.setChecked(true);
        TextView note = new TextView(this);
        note.setText(R.string.feedback_log_note);
        note.setTextSize(13);
        note.setPadding(0, dp(8), 0, 0);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(withLog);
        box.addView(note);
        new LookDialog().setTitle(R.string.feedback_send).setView(box)
                .setPositiveButton(R.string.feedback_write, (d, w) -> {
                    String version = "?";
                    try {
                        version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
                    } catch (PackageManager.NameNotFoundException ignored) {
                        // keep "?"
                    }
                    StringBuilder body = new StringBuilder(getString(R.string.feedback_body_start)).append("\n\n\n");
                    body.append("---\n").append(getString(R.string.feedback_device, Build.MANUFACTURER + " " + Build.MODEL,
                            Build.VERSION.RELEASE, version)).append("\n");
                    SceneLibrary.Scene scene = SceneSettings.current(this);
                    body.append(getString(R.string.feedback_playing, scene != null ? scene.name
                            : getString(R.string.scene_built_in))).append("\n");
                    if (withLog.isChecked()) {
                        body.append("\n").append(getString(R.string.feedback_log_heading)).append("\n").append(eventLogTail(200));
                    }
                    Intent mail = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
                            .putExtra(Intent.EXTRA_EMAIL, new String[]{getString(R.string.feedback_address)})
                            .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.feedback_subject, version))
                            .putExtra(Intent.EXTRA_TEXT, body.toString());
                    try {
                        startActivity(mail);
                    } catch (android.content.ActivityNotFoundException e) {
                        toast(R.string.feedback_no_mail, getString(R.string.feedback_address));
                    }
                })
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    /** The last {@code lines} lines of the event log (the older file first, if it rolled over). */
    private String eventLogTail(int lines) {
        java.util.ArrayDeque<String> tail = new java.util.ArrayDeque<>();
        for (String name : new String[]{"events.log.1", "events.log"}) {
            File f = new File(getFilesDir(), name);
            if (!f.isFile()) {
                continue;
            }
            try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(f))) {
                String l;
                while ((l = r.readLine()) != null) {
                    tail.addLast(l);
                    if (tail.size() > lines) {
                        tail.removeFirst();
                    }
                }
            } catch (IOException ignored) {
                // a missing or unreadable log just leaves less to send
            }
        }
        return tail.isEmpty() ? getString(R.string.feedback_log_empty) : String.join("\n", tail);
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

    private void showWalkthrough() {
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
            public void start() {
                if (!AudioService.isRunning()) {
                    onStartStop();
                }
            }

            @Override
            public int dp(int v) {
                return MainActivity.this.dp(v);
            }
        });
        walkthrough.applyLook(look);
        walkthrough.open();
    }

    /** Licence texts shipped inside the app (assets/licenses), as the GPL requires. */
    private void showLicenses() {
        String[][] docs = {
                {"Notice", "NOTICE.txt"},
                {"GNU GPL v3", "GPL-3.0.txt"},
                {"RjDj library (rjlib)", "rjlib-LICENSE.txt"},
                {"Pure Data (BSD)", "pure-data-LICENSE.txt"},
                {"PdParty helper objects (BSD)", "pdparty-LICENSE.txt"},
                {"Oboe and libpd (Apache 2.0)", "Apache-2.0.txt"},
                {"Fonts: Sora, Inter, JetBrains Mono (OFL)", "fonts-OFL.txt"},
        };
        String[] names = new String[docs.length];
        for (int i = 0; i < docs.length; i++) {
            names[i] = docs[i][0];
        }
        new LookDialog()
                .setTitle(R.string.show_licenses)
                .setItems(names, (d, which) -> showAssetText(docs[which][0], "licenses/" + docs[which][1]))
                .setNegativeButton(R.string.close, null)
                .show();
    }

    private void showAssetText(String title, String assetPath) {
        String text;
        try (InputStream in = getAssets().open(assetPath)) {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                bytes.write(buf, 0, n);
            }
            text = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            text = getString(R.string.err_open_file);
        }
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(12);
        view.setTypeface(Typeface.MONOSPACE);
        view.setPadding(dp(16), dp(8), dp(16), dp(8));
        view.setTextIsSelectable(true);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(view);
        new LookDialog()
                .setTitle(title)
                .setView(scroll)
                .setPositiveButton(R.string.close, null)
                .show();
    }

    /**
     * Appearance: the theme (Phone, Light, Dark, AMOLED) and the accent colour (ready-made swatches, the wallpaper's on
     * Android 12+, or your own from a colour ring). A change rebuilds the screen in the new look; the sound keeps
     * playing. Neither is part of presets.
     */
    private void addAppearanceSection(LinearLayout parent) {
        LinearLayout body = addSection(parent, "appearance", getString(R.string.section_appearance), false);
        int[] themes = {UiSettings.THEME_PHONE, UiSettings.THEME_LIGHT, UiSettings.THEME_DARK, UiSettings.THEME_AMOLED};
        String[] names = {getString(R.string.theme_phone), getString(R.string.theme_light),
                getString(R.string.theme_dark), getString(R.string.theme_amoled)};
        int current = UiSettings.theme(this), chosen = 0;
        for (int i = 0; i < themes.length; i++) {
            if (themes[i] == current) {
                chosen = i;
            }
        }
        TextView themeLabel = new TextView(this);
        themeLabel.setText(R.string.theme_label);
        look.label(themeLabel);
        themeLabel.setPadding(dp(4), dp(8), 0, dp(8));
        body.addView(themeLabel);
        body.addView(segmentBar(names, chosen, i -> {
            if (themes[i] != UiSettings.theme(this)) {
                UiSettings.setTheme(this, themes[i]);
                recreate();   // rebuilds the screen in the new theme; audio keeps running
            }
        }));
        if (current == UiSettings.THEME_PHONE || current == UiSettings.THEME_AMOLED) {
            TextView hint = new TextView(this);
            hint.setText(current == UiSettings.THEME_PHONE ? R.string.theme_hint_phone : R.string.theme_hint_amoled);
            look.hint(hint);
            hint.setPadding(dp(4), dp(6), 0, 0);
            body.addView(hint);
        }

        // the accent: a grid of round swatches, five to a row
        String accent = UiSettings.accent(this);
        String[] accentNames = getResources().getStringArray(R.array.accent_names);
        List<String> ids = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 31) {
            ids.add("wall");
            labels.add(getString(R.string.accent_wall));
        }
        for (int i = 0; i < Look.ACCENT_IDS.length; i++) {
            ids.add(Look.ACCENT_IDS[i]);
            labels.add(accentNames[i]);
        }
        ids.add("custom");
        labels.add(getString(R.string.accent_custom));
        String shownName = labels.get(Math.max(0, ids.indexOf(accent)));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setPadding(dp(4), dp(18), dp(4), dp(10));
        TextView accentLabel = new TextView(this);
        accentLabel.setText(R.string.accent_label);
        look.label(accentLabel);
        head.addView(accentLabel, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView accentName = new TextView(this);
        accentName.setText(shownName);
        look.label(accentName);
        accentName.setTextColor(look.p.primaryText);
        head.addView(accentName);
        body.addView(head);

        LinearLayout row = null;
        for (int i = 0; i < ids.size(); i++) {
            if (i % 5 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                body.addView(row);
            }
            String id = ids.get(i);
            row.addView(swatch(id, labels.get(i), id.equals(accent)), new LinearLayout.LayoutParams(0, dp(56), 1f));
        }
        for (int i = ids.size() % 5; i > 0 && i < 5; i++) {   // keep the last row's swatches in their columns
            row.addView(new android.widget.Space(this), new LinearLayout.LayoutParams(0, dp(56), 1f));
        }
        TextView hint = new TextView(this);
        hint.setText(R.string.accent_hint);
        look.hint(hint);
        hint.setPadding(dp(4), dp(8), 0, dp(4));
        body.addView(hint);
    }

    /** One accent swatch: a 44 dp circle (ringed when chosen); tapping it switches to it, or opens the colour ring. */
    private View swatch(String id, String name, boolean chosen) {
        android.widget.FrameLayout box = new android.widget.FrameLayout(this);
        View dot = new View(this);
        android.graphics.drawable.GradientDrawable fill = new android.graphics.drawable.GradientDrawable();
        fill.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        boolean rainbow = id.equals("custom") && !chosen;
        if (rainbow) {
            fill.setGradientType(android.graphics.drawable.GradientDrawable.SWEEP_GRADIENT);
            fill.setColors(new int[] {0xFFE84B4B, 0xFFE8C94B, 0xFF5BD06A, 0xFF4BC6E8, 0xFF6A6BF0, 0xFFD25BD9, 0xFFE84B4B});
        } else {
            float[] hc = id.equals("custom") ? UiSettings.customAccent(this)
                    : id.equals("wall") ? Styler.accentForId(this, "wall") : Styler.accentForId(this, id);
            fill.setColor(Look.swatch(hc[0], hc[1]));
        }
        if (chosen) {
            fill.setStroke(dp(3), look.p.s1);   // a gap, then the ring drawn by the box behind
        }
        dot.setBackground(fill);
        if (chosen) {
            android.graphics.drawable.GradientDrawable ring = new android.graphics.drawable.GradientDrawable();
            ring.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            ring.setColor(look.p.text);
            View halo = new View(this);
            halo.setBackground(ring);
            box.addView(halo, new android.widget.FrameLayout.LayoutParams(dp(50), dp(50), Gravity.CENTER));
        }
        box.addView(dot, new android.widget.FrameLayout.LayoutParams(dp(chosen ? 46 : 44), dp(chosen ? 46 : 44), Gravity.CENTER));
        int mark = chosen ? R.drawable.ic_check : id.equals("custom") ? R.drawable.ic_plus : id.equals("wall") ? R.drawable.ic_image : 0;
        if (mark != 0) {
            ImageView icon = new ImageView(this);
            icon.setImageResource(mark);
            icon.setImageTintList(android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
            icon.setElevation(dp(1));
            box.addView(icon, new android.widget.FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
        }
        box.setContentDescription(name);
        box.setSelected(chosen);
        box.setOnClickListener(v -> {
            if (id.equals("custom")) {
                showColorSheet();
            } else if (!chosen) {
                UiSettings.setAccent(this, id);
                recreate();
            }
        });
        return box;
    }

    /**
     * Your own colour: drag around the ring for the colour, Colorfulness from grey to full; the middle of the ring
     * is the Start button as it would look in this theme. Use this colour rebuilds the screen in it.
     */
    private void showColorSheet() {
        float[] hc = UiSettings.customAccent(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.addView(sheetHeading(getString(R.string.accent_custom), getString(R.string.accent_custom_hint)),
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        HueRing ring = new HueRing(hc[0], hc[1]);
        content.addView(ring, new LinearLayout.LayoutParams(dp(232), dp(232)));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setPadding(dp(8), dp(16), dp(8), 0);
        TextView label = new TextView(this);
        label.setText(R.string.accent_colorfulness);
        look.label(label);
        head.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView amount = new TextView(this);
        amount.setTypeface(look.value());
        amount.setTextColor(look.p.muted);
        amount.setTextSize(Look.TEXT_VALUE);
        head.addView(amount);
        content.addView(head, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        SeekBar colorful = new SeekBar(this);
        colorful.setMax(100);
        colorful.setProgress(Math.round(hc[1] * 100));
        colorful.setContentDescription(getString(R.string.accent_colorfulness));
        look.slider(colorful);
        amount.setText(Math.round(hc[1] * 100) + "%");
        colorful.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar b, int progress, boolean fromUser) {
                ring.setChroma(progress / 100f);
                amount.setText(progress + "%");
            }

            @Override public void onStartTrackingTouch(SeekBar b) { }
            @Override public void onStopTrackingTouch(SeekBar b) { }
        });
        LinearLayout.LayoutParams sliderParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44));
        content.addView(colorful, sliderParams);

        LinearLayout buttons = newButtonRow(content);
        buttons.setPadding(0, dp(10), 0, 0);
        Button cancel = addButton(buttons, R.string.cancel, this::closeSheet);
        cancel.setBackground(look.pressable(look.outline(look.p.line, Look.RADIUS_PILL), look.p.text, Look.RADIUS_PILL));
        cancel.setTextColor(look.p.text);
        Button use = addButton(buttons, R.string.accent_use, () -> {
            UiSettings.setCustomAccent(this, ring.hue, ring.chroma);
            closeSheet();
            recreate();
        });
        look.button(use, true);
        ((LinearLayout.LayoutParams) use.getLayoutParams()).setMargins(dp(3), dp(4), dp(3), dp(4));
        openSheet(content);
    }

    /**
     * The colour ring: every hue around it, a knob at the chosen one; inside, the Start button in the colour this
     * theme would make of it. Drag anywhere on the ring (or around the middle) to turn it.
     */
    private final class HueRing extends View {
        float hue, chroma;
        private final android.graphics.Paint band = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Paint fill = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Paint word = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final int theme = UiSettings.effectiveTheme(MainActivity.this);

        HueRing(float hue, float chroma) {
            super(MainActivity.this);
            this.hue = hue;
            this.chroma = chroma;
            band.setStyle(android.graphics.Paint.Style.STROKE);
            band.setStrokeWidth(dp(28));
            word.setTypeface(look.heading());
            word.setTextSize(dp(15));
            word.setTextAlign(android.graphics.Paint.Align.CENTER);
            setContentDescription(getString(R.string.accent_custom));
        }

        void setChroma(float c) {
            chroma = c;
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            int[] hues = new int[13];
            for (int i = 0; i <= 12; i++) {
                hues[i] = Look.swatch(i * 30f, 1f);
            }
            android.graphics.SweepGradient sweep = new android.graphics.SweepGradient(w / 2f, h / 2f, hues, null);
            android.graphics.Matrix turn = new android.graphics.Matrix();
            turn.setRotate(-90, w / 2f, h / 2f);   // hue 0 at the top, going clockwise
            sweep.setLocalMatrix(turn);
            band.setShader(sweep);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f, r = Math.min(cx, cy) - dp(15);
            band.setAlpha(Math.round(255 * (0.35f + 0.65f * chroma)));
            canvas.drawCircle(cx, cy, r, band);
            double a = Math.toRadians(hue - 90);
            float kx = cx + (float) (r * Math.cos(a)), ky = cy + (float) (r * Math.sin(a));
            fill.setColor(0xFFFFFFFF);
            canvas.drawCircle(kx, ky, dp(16), fill);
            fill.setColor(Look.swatch(hue, chroma));
            canvas.drawCircle(kx, ky, dp(12), fill);
            Look.Palette preview = Look.palette(theme, hue, chroma);
            fill.setColor(preview.primary);
            canvas.drawCircle(cx, cy, dp(54), fill);
            word.setColor(preview.onPrimary);
            canvas.drawText(getString(R.string.start), cx, cy + dp(5), word);
        }

        @Override
        public boolean onTouchEvent(android.view.MotionEvent e) {
            if (e.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
                getParent().requestDisallowInterceptTouchEvent(true);   // the sheet mustn't slide away under it
            }
            float dx = e.getX() - getWidth() / 2f, dy = e.getY() - getHeight() / 2f;
            hue = (float) ((Math.toDegrees(Math.atan2(dy, dx)) + 90 + 360) % 360);
            invalidate();
            return true;
        }
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
    private View sheetHeading(String title, String hint) {
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
    private interface RowExtra {
        View make(int index, boolean chosen);
    }

    /** A setting's choices as list rows; a pick is stored and closes the sheet. */
    private void addChoiceList(LinearLayout content, Param p, RowExtra extra) {
        addListRows(content, p.choices, choiceIndex(p, ParamStore.get(p.id)), extra, i -> pickChoice(p, i));
    }

    /** One row per name: a tick on the chosen one (highlighted), the full name (wrapping), {@code extra} at the end. */
    private void addListRows(LinearLayout content, String[] names, int chosen, RowExtra extra,
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
    private View segmentBar(String[] names, int chosen, java.util.function.IntConsumer pick) {
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

    private void refreshRows() {
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
                    updateKitStatus();
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
        updateIntensityRow();
        undoButton.setEnabled(ParamStore.canUndo());
    }

    // ---- presets ----

    /** The presets: built-in, then yours (marked saved); tap one to load it, press and hold one of yours to delete it. */
    private void showPresets() {
        List<String> names = new ArrayList<>();
        List<Boolean> isUser = new ArrayList<>();
        for (String n : PresetManager.builtInNames()) {
            names.add(n);
            isUser.add(false);
        }
        for (String n : PresetManager.userNames(this)) {
            names.add(n);
            isUser.add(true);
        }
        String[] labels = new String[names.size()];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = isUser.get(i)
                    ? getString(R.string.preset_saved_suffix, names.get(i)) : names.get(i);
        }
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.addView(sheetHeading(getString(R.string.presets), getString(R.string.presets_delete_hint)));
        addListRows(content, labels, -1, null, i -> {
            closeSheet();
            loadPreset(names.get(i), isUser.get(i));
        });
        for (int i = 0; i < names.size(); i++) {   // child 0 is the heading
            if (isUser.get(i)) {
                String name = names.get(i);
                content.getChildAt(i + 1).setOnLongClickListener(v -> {
                    closeSheet();
                    confirmDelete(name);
                    return true;
                });
            }
        }
        openSheet(content);
    }

    /** A text field for a dialog: inset like the dialog's title, its underline and cursor in the accent. */
    private View boxed(EditText input) {
        input.setTypeface(look.text());
        input.setTextColor(look.p.text);
        input.setBackgroundTintList(android.content.res.ColorStateList.valueOf(look.p.primary));
        android.widget.FrameLayout box = new android.widget.FrameLayout(this);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        box.addView(input);
        return box;
    }

    /**
     * An AlertDialog in the app's look: rounded on the sheet colour, the fonts, buttons in the accent. Every dialog
     * (confirmations, Save, the kit and feedback ones) is built with this instead of AlertDialog.Builder.
     */
    private final class LookDialog extends AlertDialog.Builder {
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

    private void loadPreset(String name, boolean user) {
        Map<String, Float> values = user
                ? PresetManager.readUser(this, name) : PresetManager.builtIn(name);
        if (values == null) {
            toast(R.string.toast_read_failed, name);
            return;
        }
        ParamStore.applyPreset(values);
        refreshRows();
        toast(R.string.toast_loaded, name);
    }

    /** The one place settings are ever wiped: only when the user taps Reset and confirms. */
    private void confirmResetAll() {
        new LookDialog()
                .setTitle(R.string.reset_title)
                .setMessage(R.string.reset_message)
                .setPositiveButton(R.string.reset_confirm, (d, w) -> {
                    Worlds.resetSettings();
                    ParamStore.resetAll();
                    ParamStore.save(this);
                    AudioSettings.reset(this);
                    SceneSettings.resetAll(this);
                    if (AudioService.isRunning()) {   // scene, devices and rate are chosen at start
                        startForegroundService(new Intent(this, AudioService.class)
                                .setAction(AudioService.ACTION_RESTART));
                    }
                    toast(R.string.toast_reset);
                    recreate();   // rebuild every control from the fresh settings
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void confirmDelete(String name) {
        new LookDialog()
                .setTitle(getString(R.string.delete_title, name))
                .setPositiveButton(R.string.delete, (d, w) -> {
                    PresetManager.delete(this, name);
                    refreshWorldRows();
                    toast(R.string.toast_deleted);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showSave() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(R.string.default_preset_name);
        input.selectAll();
        new LookDialog()
                .setTitle(R.string.save_title)
                .setView(boxed(input))
                .setPositiveButton(R.string.save, (d, w) -> {
                    try {
                        String used = PresetManager.save(
                                this, input.getText().toString(), ParamStore.snapshot());
                        toast(R.string.toast_saved, used);
                        refreshWorldRows();
                    } catch (IOException e) {
                        Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Asks for the preset's name first: it becomes both the file name and the name shown after importing. */
    private void startExport() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(R.string.default_preset_name);
        input.selectAll();
        new LookDialog()
                .setTitle(R.string.export_title)
                .setView(boxed(input))
                .setPositiveButton(R.string.export, (d, w) -> {
                    exportName = PresetManager.sanitize(input.getText().toString());
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                            .addCategory(Intent.CATEGORY_OPENABLE)
                            .setType("application/json")
                            .putExtra(Intent.EXTRA_TITLE, exportName + ".json");
                    startActivityForResult(intent, REQ_EXPORT);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void startImport() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*");
        startActivityForResult(intent, REQ_IMPORT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        if (requestCode == REQ_EXPORT) {
            exportTo(uri);
        } else if (requestCode == REQ_IMPORT) {
            importFrom(uri);
        } else if (requestCode == REQ_IMPORT_SCENE) {
            importSceneFrom(uri);
        } else if (requestCode == REQ_IMPORT_SCENE_FOLDER) {
            importSceneFolderFrom(uri);
        } else if (requestCode == REQ_IMPORT_KIT || requestCode == REQ_IMPORT_KIT_FOLDER) {
            importKitFrom(uri, requestCode == REQ_IMPORT_KIT_FOLDER);
        }
    }

    // ---- user kits ----

    /** Music tab: your own kits (Kits: text files, folders or zips), imported into the Kit menus. */
    private void fillKitsSection(LinearLayout body) {
        kitStatus = new TextView(this);
        kitStatus.setTextSize(15);
        kitStatus.setPadding(0, dp(4), 0, dp(4));
        body.addView(kitStatus);
        LinearLayout row = newButtonRow(body);
        addButton(row, R.string.kit_import, () -> startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), REQ_IMPORT_KIT));
        addButton(row, R.string.kit_import_folder, () -> startActivityForResult(
                new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), REQ_IMPORT_KIT_FOLDER));
        LinearLayout row2 = newButtonRow(body);
        addButton(row2, R.string.kit_howto, () -> new LookDialog().setTitle(R.string.kit_howto)
                .setMessage(R.string.kit_howto_text).setPositiveButton(R.string.close, null).show());
        addButton(row2, R.string.kit_delete, this::deleteKit);
        updateKitStatus();
    }

    private void updateKitStatus() {
        if (kitStatus == null) {
            return;
        }
        List<String> titles = new ArrayList<>();
        for (Kits.UserKit u : Kits.userKits()) {
            titles.add(u.title);
        }
        kitStatus.setText(titles.isEmpty() ? getString(R.string.kit_none_status)
                : getString(R.string.kit_status, String.join(", ", titles)));
    }

    private void deleteKit() {
        List<Kits.UserKit> kits = Kits.userKits();
        if (kits.isEmpty()) {
            toast(R.string.kit_none_to_delete);
            return;
        }
        String[] items = new String[kits.size()];
        for (int i = 0; i < items.length; i++) {
            items[i] = kits.get(i).title;
        }
        new LookDialog().setTitle(R.string.kit_delete).setItems(items, (d, which) ->
                new LookDialog().setMessage(getString(R.string.kit_delete_confirm, items[which]))
                        .setPositiveButton(R.string.kit_delete, (d2, w2) -> {
                            int index = Kits.BUILT_IN.length + which;
                            Kits.delete(index);
                            Kits.refreshChoices();
                            ParamStore.kitDeleted(index);
                            updateKitStatus();
                            refreshRows();
                        }).setNegativeButton(android.R.string.cancel, null).show())
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    /** Copies a kit in (a kit.txt, a zip or a folder) off the main thread, then plays it as the Kit. */
    private void importKitFrom(Uri uri, boolean folder) {
        String name = folder ? "" : displayName(uri).toLowerCase(java.util.Locale.ROOT);
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
                    TreeImporter.copyAll(getContentResolver(), uri, staging);
                    index = Kits.installFolder(staging);
                } else {
                    try (InputStream in = getContentResolver().openInputStream(uri)) {
                        if (in == null) {
                            throw new IOException(getString(R.string.err_open_file));
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
                        problems.add(getString(R.string.kit_missing_file, f));
                    }
                }
                runOnUiThread(() -> {
                    Kits.refreshChoices();
                    ParamStore.set(Kits.ID, index);
                    updateKitStatus();
                    refreshRows();
                    String title = Params.byId(Kits.ID).choices[index];
                    String msg = getString(R.string.kit_imported, title);
                    if (!problems.isEmpty()) {
                        msg += "\n\n" + getString(R.string.kit_warnings) + "\n\u2022 " + String.join("\n\u2022 ", problems);
                    }
                    new LookDialog().setTitle(R.string.kit_import_done).setMessage(msg)
                            .setPositiveButton(R.string.close, null).show();
                });
            } catch (IOException | RuntimeException e) {
                String why = e.getMessage();
                runOnUiThread(() -> new LookDialog().setTitle(R.string.kit_import_failed)
                        .setMessage(why).setPositiveButton(R.string.close, null).show());
            } finally {
                if (staging != null) {
                    SceneLibrary.deleteRecursively(staging);
                }
            }
        }, "kit-import").start();
    }

    private void exportTo(Uri uri) {
        try (OutputStream out = getContentResolver().openOutputStream(uri)) {
            if (out == null) {
                throw new IOException(getString(R.string.err_open_file));
            }
            out.write(PresetManager.toJson(exportName, ParamStore.snapshot())
                    .getBytes(StandardCharsets.UTF_8));
            toast(R.string.toast_exported);
        } catch (IOException | RuntimeException e) {
            toast(R.string.toast_export_failed, e.getMessage());
        }
    }

    private void importFrom(Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) {
                throw new IOException(getString(R.string.err_open_file));
            }
            PresetManager.Preset preset = PresetManager.parse(PresetManager.readAll(in));
            String name = PresetManager.isBuiltInName(PresetManager.sanitize(preset.name))
                    ? getString(R.string.imported_name, preset.name) : preset.name;
            String used = PresetManager.save(this, PresetManager.uniqueName(this, name), preset.values);
            ParamStore.applyPreset(preset.values);
            refreshRows();
            toast(R.string.toast_imported, used);
            refreshWorldRows();
        } catch (IOException | JSONException | RuntimeException e) {
            toast(R.string.toast_import_failed, e.getMessage());
        }
    }

    // ---- scenes ----

    private void updateSceneUi() {
        SceneLibrary.Scene scene = SceneSettings.current(this);
        sceneLabel.setText(getString(R.string.scene_current,
                scene != null ? scene.name : getString(R.string.scene_built_in)));
        // a scene replaces the built-in sound: its tabs say so instead of showing controls that do nothing
        for (int t : new int[]{TAB_SOUND, TAB_MUSIC, TAB_REALMS}) {
            tabBodies[t].setVisibility(scene != null ? View.GONE : View.VISIBLE);
            tabNotices[t].setVisibility(scene != null ? View.VISIBLE : View.GONE);
        }
        scenePad.setScene(scene);
        // With no scene the pad is an XY controller for the effects' movers (on the Sound tab); with one, the
        // scene's own pad (on the Scenes tab).
        LinearLayout slot = scene != null ? scenePadSlot : xyPadSlot;
        if (scenePad.getParent() != slot) {
            if (scenePad.getParent() != null) {
                ((android.view.ViewGroup) scenePad.getParent()).removeView(scenePad);
                ((android.view.ViewGroup) padHint.getParent()).removeView(padHint);
            }
            LinearLayout.LayoutParams padParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            padParams.topMargin = dp(8);
            slot.addView(scenePad, padParams);
            slot.addView(padHint);
        }
        padHint.setVisibility(scene != null ? View.GONE : View.VISIBLE);
        sceneLevels.setVisibility(scene != null ? View.VISIBLE : View.GONE);
    }

    private void sendSceneLevelsIfRunning() {
        if (AudioService.isRunning() && SceneSettings.current(this) != null) {
            AudioService.sendSceneLevels(this);
        }
    }

    /** A 0-100% slider with a live label; {@code onChange} gets a fraction from 0 to 1. */
    private void addLevelSlider(LinearLayout parent, int labelRes, float initial,
                                java.util.function.Consumer<Float> onChange) {
        TextView label = new TextView(this);
        look.label(label);
        label.setPadding(dp(4), dp(14), 0, 0);
        label.setText(getString(labelRes, Math.round(initial * 100)));
        SeekBar bar = new SeekBar(this);
        bar.setMax(100);
        look.slider(bar);
        bar.setMinimumHeight(dp(40));
        bar.setContentDescription(getString(labelRes, Math.round(initial * 100)));
        bar.setProgress(Math.round(initial * 100));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar b, int progress, boolean fromUser) {
                label.setText(getString(labelRes, progress));
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
        if (AudioService.isRunning() && SceneSettings.current(this) != null) {
            PdBase.sendMessage("#touch", type, (float) finger, (float) x, (float) y);
        }
    }

    /** The scene picker: the built-in effects, then every imported scene; press and hold one to delete it. */
    private void showScenes() {
        List<SceneLibrary.Scene> scenes = SceneLibrary.list(SceneSettings.scenesDir(this));
        String[] labels = new String[scenes.size() + 1];
        labels[0] = getString(R.string.scene_built_in);
        SceneLibrary.Scene current = SceneSettings.current(this);
        int chosen = 0;
        for (int i = 0; i < scenes.size(); i++) {
            SceneLibrary.Scene sc = scenes.get(i);
            labels[i + 1] = sc.author.isEmpty() ? sc.name
                    : getString(R.string.scene_by_author, sc.name, sc.author);
            if (current != null && sc.dirName.equals(current.dirName)) {
                chosen = i + 1;
            }
        }
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.addView(sheetHeading(getString(R.string.scenes_title),
                getString(scenes.isEmpty() ? R.string.scenes_none : R.string.scenes_delete_hint)));
        addListRows(content, labels, chosen, null, i -> {
            closeSheet();
            selectScene(i == 0 ? SceneSettings.BUILT_IN : scenes.get(i - 1).dirName);
        });
        for (int i = 1; i <= scenes.size(); i++) {   // row 0 is the heading, row 1 the built-in effects
            SceneLibrary.Scene sc = scenes.get(i - 1);
            content.getChildAt(i + 1).setOnLongClickListener(v -> {
                closeSheet();
                confirmDeleteScene(sc);
                return true;
            });
        }
        openSheet(content);
    }

    private void selectScene(String dirName) {
        if (dirName.equals(SceneSettings.selected(this))) {
            return;
        }
        SceneSettings.select(this, dirName);
        updateSceneUi();
        if (AudioService.isRunning()) {
            // The patch is opened when the engine starts, so swap scenes by restarting it.
            startForegroundService(
                    new Intent(this, AudioService.class).setAction(AudioService.ACTION_RESTART));
        }
    }

    private void confirmDeleteScene(SceneLibrary.Scene scene) {
        new LookDialog()
                .setTitle(getString(R.string.delete_scene_title, scene.name))
                .setPositiveButton(R.string.delete, (d, w) -> {
                    boolean wasSelected = scene.dirName.equals(SceneSettings.selected(this));
                    if (wasSelected) {
                        selectScene(SceneSettings.BUILT_IN);   // stops using its files first
                    }
                    SceneLibrary.delete(scene);
                    toast(R.string.toast_scene_deleted);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void startImportScene() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*");
        startActivityForResult(intent, REQ_IMPORT_SCENE);
    }

    private void startImportSceneFolder() {
        startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), REQ_IMPORT_SCENE_FOLDER);
    }

    /** Copies one scene folder, or every scene inside a folder of scenes, on a background thread. */
    private void importSceneFolderFrom(Uri tree) {
        toast(R.string.toast_scene_folder_importing);
        new Thread(() -> {
            File dir = SceneSettings.scenesDir(this);
            if (!dir.isDirectory() && !dir.mkdirs()) {
                runOnUiThread(() -> toast(R.string.toast_scene_import_failed, "Cannot create " + dir));
                return;
            }
            TreeImporter.Result r = TreeImporter.importTree(getContentResolver(), tree, dir);
            runOnUiThread(() -> {
                if (r.imported.isEmpty()) {
                    toast(R.string.toast_scene_none_found);
                } else if (r.imported.size() == 1 && r.failed.isEmpty()) {
                    toast(R.string.toast_scene_imported, r.imported.get(0).name);
                    selectScene(r.imported.get(0).dirName);
                } else {
                    // Don't guess which of many to start; the user picks from the list.
                    toast(R.string.toast_scenes_imported, r.imported.size(), r.failed.size());
                }
            });
        }, "scene-folder-import").start();
    }

    /** Unzips on a background thread (scenes can hold many samples), then offers to switch to it. */
    private void importSceneFrom(Uri uri) {
        String fileName = displayName(uri);
        toast(R.string.toast_scene_importing);
        new Thread(() -> {
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) {
                    throw new IOException(getString(R.string.err_open_file));
                }
                File dir = SceneSettings.scenesDir(this);
                if (!dir.isDirectory() && !dir.mkdirs()) {
                    throw new IOException("Cannot create " + dir);
                }
                SceneLibrary.Scene scene = SceneLibrary.importZip(in, fileName, dir);
                runOnUiThread(() -> {
                    toast(R.string.toast_scene_imported, scene.name);
                    selectScene(scene.dirName);
                });
            } catch (IOException | RuntimeException e) {
                runOnUiThread(() -> toast(R.string.toast_scene_import_failed, e.getMessage()));
            }
        }, "scene-import").start();
    }

    /** The file name behind a picked document, e.g. "Eargasm.rj.zip"; empty if unknown. */
    private String displayName(Uri uri) {
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

    private void toast(int messageRes, Object... args) {
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

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
