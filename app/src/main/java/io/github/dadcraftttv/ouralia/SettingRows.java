// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.dadcraftttv.ouralia;

import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The settings as cards and rows (moved out of MainActivity on Oct 22, ROADMAP 22): a folding card per
 * section (its on/off switch and Movers in the header), the groups inside it, one row per setting (slider, switch,
 * choice, layer panel) with its mover controls, budget lines and rows that fade while what they need is off, and
 * refreshRows, which pulls every value from the store into the controls.
 */
final class SettingRows {
    private final MainActivity a;

    SettingRows(MainActivity activity) {
        a = activity;
    }

    final List<Row> rows = new ArrayList<>();

    /** The mover controls under each slider, greyed out while their section's movers are off. */
    private final List<MoverUi> moverUis = new ArrayList<>();

    private final Map<String, TextView> budgetLines = new HashMap<>();   // budget name -> its "used / left" line

    private final Map<View, String> fadedUnless = new HashMap<>();   // a row -> the setting it needs on (faded while off)

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

    /** Each section header's chevron, by its title view. */
    private final Map<TextView, ImageView> sectionChevrons = new HashMap<>();

    final Map<String, LinearLayout> sectionCards = new HashMap<>();   // by section id: header row, then body

    /** Pull current values from the store into every control. */
    /** A small dot after a slider's label while a mover is pushing it around (checked once a second). */
    static final String MOVER_DOT = "  \u25CF";

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
        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        a.look.card(card);
        LinearLayout headRow = new LinearLayout(a);
        headRow.setOrientation(LinearLayout.HORIZONTAL);
        headRow.setGravity(Gravity.CENTER_VERTICAL);
        headRow.setMinimumHeight(a.dp(56));
        headRow.setPadding(a.dp(Look.CARD_PAD), a.dp(6), a.dp(14), a.dp(6));
        headRow.setBackground(a.look.pressable(null, a.look.p.text, 0));
        LinearLayout titles = new LinearLayout(a);   // the title, and its bracketed explanation under it
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView header = new TextView(a);
        header.setTextSize(Look.TEXT_HEADING);
        header.setTypeface(a.look.heading());
        header.setTextColor(a.look.p.text);
        titles.addView(header);
        String subtitle = splitLabel(title)[1];
        if (!subtitle.isEmpty()) {
            TextView sub = new TextView(a);
            sub.setText(subtitle);
            a.look.hint(sub);
            titles.addView(sub);
        }
        headRow.addView(titles, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        for (View extra : extras) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginStart(a.dp(8));
            headRow.addView(extra, lp);
        }
        ImageView chevron = new ImageView(a);
        chevron.setImageTintList(android.content.res.ColorStateList.valueOf(a.look.p.muted));
        LinearLayout.LayoutParams chevronParams = new LinearLayout.LayoutParams(a.dp(22), a.dp(22));
        chevronParams.setMarginStart(a.dp(10));
        headRow.addView(chevron, chevronParams);
        sectionChevrons.put(header, chevron);

        LinearLayout body = new LinearLayout(a);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(a.dp(Look.CARD_PAD), 0, a.dp(Look.CARD_PAD), a.dp(16));

        boolean[] filled = {fill == null};
        Runnable show = () -> {
            if (!filled[0]) {
                filled[0] = true;
                fill.accept(body);
                a.look.fonts(body);
                refreshRows();   // the new rows start with the stored values
            }
        };
        boolean open = UiSettings.isExpanded(a, id, openByDefault);
        showSectionState(header, body, title, open);
        if (open) {
            show.run();
        }
        headRow.setOnClickListener(v -> {
            boolean nowOpen = body.getVisibility() != View.VISIBLE;
            UiSettings.setExpanded(a, id, nowOpen);
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
        cardParams.setMargins(0, 0, 0, a.dp(Look.CARD_GAP));
        parent.addView(card, cardParams);
        sectionCards.put(id, card);
        return body;
    }

    private final TapTempo tapTempo = new TapTempo();

    /** Under Set Tempo: tap along a few times and Tempo becomes Set at the taps' BPM (TapTempo). */
    private void addTapTempo(LinearLayout body) {
        LinearLayout row = a.newButtonRow(body);
        android.widget.Button tap = a.addButton(row, R.string.tap_tempo, () -> { });
        tap.setOnTouchListener((v, e) -> {   // on the touch down, not the release: taps land on the beat
            if (e.getAction() != android.view.MotionEvent.ACTION_DOWN) {
                return false;
            }
            v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
            float bpm = tapTempo.tap(android.os.SystemClock.uptimeMillis());
            if (bpm > 0) {
                ParamStore.set("tempo_mode", 1f);
                ParamStore.set("tempo_bpm", bpm);
                refreshRows();
                tap.setText(a.getString(R.string.tap_tempo_bpm, Math.round(bpm)));
            } else {
                tap.setText(R.string.tap_tempo_more);
            }
            return false;
        });
    }

    /** One effect section: its Movers switch, then each slider followed by its movement settings. */
    void fillEffectSection(LinearLayout body, Params.Section section) {
        Param moversSwitch = Params.byId(Params.moversSwitchId(section.id));
        Param onSwitch = Params.onSwitch(section);   // (both sit on the card's header)
        for (Param p : Params.ALL) {
            if (p.section.equals(section.id) && p.moverOf == null && p != moversSwitch && p != onSwitch) {
                if (p.id.equals("music_key")) {   // Key and Scale: two tiles side by side, each opening a sheet
                    a.choiceUi.addKeyScaleTiles(body);
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
                    LinearLayout box = new LinearLayout(a);   // (only the New scatter style uses them)
                    box.setOrientation(LinearLayout.VERTICAL);
                    addRow(box, p);
                    body.addView(box);
                    fadedUnless.put(box, "echo_scatter_style");
                    continue;
                }
                addRow(body, p);
                if (p.id.equals("tempo_bpm")) {
                    addTapTempo(body);
                }
                if (Params.byId(Params.moverId(p.id, "mv")) != null) {
                    addMoverRows(body, section.id, p);
                }
                if (p.budget != null && lastOfBudget(p)) {   // the glitch matrix: how much of it is in use
                    TextView line = new TextView(a);
                    line.setTextSize(14);
                    line.setTypeface(Typeface.DEFAULT_BOLD);
                    line.setPadding(0, a.dp(10), 0, a.dp(4));
                    body.addView(line);
                    budgetLines.put(p.budget, line);
                }
            }
        }
        updateBudgetLines();
    }

    /** A small accent heading inside a card, with an optional hint under it and a line above it (not at the top). */
    /** A family of cards on a tab (Voice, Grit...): the subheading's style, between the cards. */
    void addGroupHeading(LinearLayout tab, String title) {
        TextView head = new TextView(a);
        head.setText(title.toUpperCase(java.util.Locale.ROOT));
        head.setTypeface(a.look.textStrong());
        head.setTextSize(Look.TEXT_HINT);
        head.setLetterSpacing(0.1f);
        head.setTextColor(a.look.p.primaryText);
        head.setAccessibilityHeading(true);
        head.setPadding(a.dp(Look.CARD_PAD), a.dp(10), a.dp(Look.CARD_PAD), a.dp(8));
        tab.addView(head);
    }

    private void addSubheading(LinearLayout parent, int title, int hint) {
        if (parent.getChildCount() > 0) {
            View rule = new View(a);
            rule.setBackgroundColor(a.look.p.line);
            LinearLayout.LayoutParams ruleParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, a.dp(1));
            ruleParams.setMargins(0, a.dp(24), 0, a.dp(14));
            parent.addView(rule, ruleParams);
        } else {
            parent.addView(new View(a), new LinearLayout.LayoutParams(0, a.dp(8)));
        }
        TextView head = new TextView(a);
        head.setText(a.getString(title).toUpperCase(java.util.Locale.ROOT));
        head.setTypeface(a.look.textStrong());
        head.setTextSize(Look.TEXT_HINT);
        head.setLetterSpacing(0.1f);
        head.setTextColor(a.look.p.primaryText);
        head.setAccessibilityHeading(true);
        parent.addView(head);
        if (hint != 0) {
            TextView line = new TextView(a);
            line.setText(hint);
            a.look.hint(line);
            line.setPadding(0, a.dp(2), 0, 0);
            parent.addView(line);
        }
    }

    /** A part's layer and its blend in one outlined panel; the blend fades while the layer is None. */
    private void addLayerPanel(LinearLayout parent, Param layer, Param blend) {
        LinearLayout panel = new LinearLayout(a);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(a.look.outline(a.look.p.line, Look.RADIUS_TILE));
        panel.setPadding(a.dp(12), 0, a.dp(12), a.dp(12));
        addRow(panel, layer, false);
        LinearLayout blendBox = new LinearLayout(a);
        blendBox.setOrientation(LinearLayout.VERTICAL);
        addRow(blendBox, blend, false);
        panel.addView(blendBox);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, a.dp(12), 0, 0);
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
    List<View> headerExtras(Params.Section section) {
        List<View> extras = new ArrayList<>();
        Param movers = Params.byId(Params.moversSwitchId(section.id));
        if (movers != null) {
            android.widget.ToggleButton pill = new android.widget.ToggleButton(a);
            pill.setTextOn(a.getString(R.string.movers_on));
            pill.setTextOff(a.getString(R.string.movers_off));
            a.look.pill(pill);
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
            Switch sw = new Switch(a);
            a.look.switchTint(sw);
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
            e.getValue().setText(u > 100 ? a.getString(R.string.glitch_budget_over, u)
                    : a.getString(R.string.glitch_budget, u, 100 - u));
        }
    }

    private void showSectionState(TextView header, View body, String title, boolean open) {
        body.setVisibility(open ? View.VISIBLE : View.GONE);
        header.setText(splitLabel(title)[0]);
        ImageView chevron = sectionChevrons.get(header);
        if (chevron != null) {
            chevron.setImageResource(open ? R.drawable.ic_chevron_up : R.drawable.ic_chevron_down);
        }
        ((View) header.getParent().getParent()).setContentDescription(
                a.getString(open ? R.string.section_open : R.string.section_closed, title));
    }

    private void addRow(LinearLayout parent, Param p) {
        addRow(parent, p, false);
    }

    /** {@code compact} makes a smaller, tighter row, for the mover settings under a slider. */
    private void addRow(LinearLayout parent, Param p, boolean compact) {
        if (p.toggle) {
            // the name (its bracketed explanation on a quieter line under it) and the switch; tapping the words
            // flips it too
            LinearLayout line = new LinearLayout(a);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.setMinimumHeight(a.dp(52));
            line.setPadding(0, a.dp(6), 0, a.dp(6));
            line.addView(nameAndHint(p, compact), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            Switch sw = new Switch(a);
            a.look.switchTint(sw);
            sw.setContentDescription(p.label);
            sw.setChecked(ParamStore.get(p.id) >= 0.5f);   // right from the start (folded rows aren't refreshed)
            sw.setOnClickListener(v -> {
                ParamStore.set(p.id, sw.isChecked() ? 1f : 0f);
                updateMoverUi();   // a section's movers switch greys its mover controls in or out
            });
            LinearLayout.LayoutParams swParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            swParams.setMarginStart(a.dp(12));
            line.addView(sw, swParams);
            line.setOnClickListener(v -> sw.performClick());
            parent.addView(line);
            rows.add(new Row(p, null, null, sw));
            return;
        }
        if (p.choices != null) {
            a.choiceUi.addChoiceRow(parent, p, compact);
            return;
        }

        LinearLayout head = new LinearLayout(a);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(0, a.dp(compact ? 4 : 14), 0, 0);

        // the name (and its explanation) on the left, the value on the right in the accent, then the lock
        head.addView(nameAndHint(p, compact), new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView label = new TextView(a);   // (the value: what refreshRows and the mover dot update)
        label.setTypeface(a.look.value());
        label.setTextSize(compact ? Look.TEXT_HINT : Look.TEXT_VALUE);
        label.setTextColor(a.look.p.primaryText);
        label.setPadding(a.dp(12), 0, 0, 0);
        head.addView(label);

        if (Params.lockable(p)) {
            CheckBox lock = new CheckBox(a);
            a.look.lock(lock, a.getDrawable(R.drawable.ic_lock), a.getDrawable(R.drawable.ic_lock_open));
            lock.setContentDescription(a.getString(R.string.lock) + ": " + p.label);
            lock.setChecked(ParamStore.isLocked(p.id));
            lock.setOnCheckedChangeListener((b, checked) -> ParamStore.setLocked(p.id, checked));
            LinearLayout.LayoutParams lockParams = new LinearLayout.LayoutParams(a.dp(44), a.dp(44));
            lockParams.setMarginStart(a.dp(8));   // a little room between the value and its padlock
            head.addView(lock, lockParams);
        }
        parent.addView(head);

        SeekBar slider = new SeekBar(a);
        slider.setMax(1000);
        a.look.slider(slider);
        slider.setMinimumHeight(a.dp(40));
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

    /** The movement, speed and depth controls of one slider's mover, indented under it. */
    private void addMoverRows(LinearLayout parent, String sectionId, Param slider) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(a.dp(16), 0, 0, a.dp(4));
        addRow(box, Params.byId(Params.moverId(slider.id, "mv")), true);
        LinearLayout details = new LinearLayout(a);
        details.setOrientation(LinearLayout.VERTICAL);
        addRow(details, Params.byId(Params.moverId(slider.id, "mvspeed")), true);
        addRow(details, Params.byId(Params.moverId(slider.id, "mvdepth")), true);
        box.addView(details);
        parent.addView(box);
        moverUis.add(new MoverUi(sectionId, slider.id, box, details));
    }

    /** Hides mover controls whose section has its Movers off; hides speed and depth while a mover is Off. */
    void updateMoverUi() {
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
        String hint = ChoiceUi.detail(core);
        String name = hint.isEmpty() ? core : ChoiceUi.shortName(core);
        return new String[] {name + star, hint};
    }

    /** The name in the label style and, if it has one, its explanation under it in the hint style. */
    View nameAndHint(Param p, boolean compact) {
        String[] parts = splitLabel(p.shown());
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(a);
        name.setText(parts[0]);
        if (compact) {
            a.look.hint(name);
        } else {
            a.look.label(name);
        }
        box.addView(name);
        if (!parts[1].isEmpty()) {
            TextView hint = new TextView(a);
            hint.setText(parts[1]);
            a.look.hint(hint);
            if (compact) {
                hint.setTextSize(12);
            }
            box.addView(hint);
        }
        return box;
    }

    void updateMoverDots() {
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
                    a.kitUi.updateKitStatus();
                }
                r.tile.setText(r.param.choices[ChoiceUi.choiceIndex(r.param, v)]);
            } else if (r.segment != null) {
                a.choiceUi.showSegment(r, ChoiceUi.choiceIndex(r.param, v));
            } else {
                r.slider.setProgress(Math.round(r.param.toNorm(v) * 1000f));
                r.label.setText(valueText(r.param, v));
            }
        }
        updateMoverUi();
        updateBudgetLines();
        updateFadedRows();
        a.playerUi.updateIntensityRow();
        a.undoButton.setEnabled(ParamStore.canUndo());
    }

    static final class Row {
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
}
