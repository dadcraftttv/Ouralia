// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.dadcraftttv.ouralia;

import android.graphics.Canvas;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Pick-one settings (moved out of MainActivity on Oct 22, ROADMAP 22): the Key and Scale tiles and their
 * sheets (the Scale sheet's dots), side-by-side segment buttons for short choices, a tile and list sheet for the
 * rest. The generic pieces (sheetHeading, addListRows, segmentBar) stay in MainActivity for every sheet.
 */
final class ChoiceUi {
    private final MainActivity a;

    ChoiceUi(MainActivity activity) {
        a = activity;
    }

    /** Key and Scale as two big tiles (easier to see and hit than a menu); each opens its picker sheet. */
    void addKeyScaleTiles(LinearLayout body) {
        LinearLayout pair = new LinearLayout(a);
        pair.setOrientation(LinearLayout.HORIZONTAL);
        pair.setPadding(0, a.dp(14), 0, a.dp(2));
        Param key = Params.byId("music_key"), scale = Params.byId("music_scale");
        pair.addView(pickTile(key, this::showKeySheet, false), tileParams(0, a.dp(4)));
        pair.addView(pickTile(scale, this::showScaleSheet, false), tileParams(a.dp(4), 0));
        body.addView(pair);
    }

    private static LinearLayout.LayoutParams tileParams(int left, int right) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(left, 0, right, 0);
        return lp;
    }

    /** A tile: the setting's name small, its value large, a chevron; tapping it runs {@code open}. */
    private View pickTile(Param p, Runnable open, boolean compact) {
        LinearLayout tile = new LinearLayout(a);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER_VERTICAL);
        tile.setBackground(a.look.choiceTile(a.getDrawable(R.drawable.ic_chevron_down).mutate()));
        tile.setMinimumHeight(a.dp(compact ? 52 : 64));
        tile.setPadding(a.dp(16), a.dp(8), a.dp(40), a.dp(8));
        TextView name = new TextView(a);
        name.setText(p.shown());
        a.look.hint(name);
        name.setTextSize(12);
        tile.addView(name);
        TextView value = new TextView(a);
        value.setTypeface(a.look.heading());
        value.setTextSize(compact ? 15 : 17);
        value.setTextColor(a.look.p.text);
        value.setMaxLines(1);   // a long name ("Minor pentatonic") shrinks to fit rather than being cut
        value.setAutoSizeTextTypeUniformWithConfiguration(12, compact ? 15 : 17, 1, TypedValue.COMPLEX_UNIT_SP);
        value.setText(p.choices[choiceIndex(p, ParamStore.get(p.id))]);
        tile.addView(value);
        tile.setOnClickListener(v -> open.run());
        MainActivity.Row row = new MainActivity.Row(p, null, null, null);
        row.tile = value;
        a.rows.add(row);
        return tile;
    }

    /** The Key picker: the twelve notes as big buttons, four to a row; one tap picks and closes. */
    private void showKeySheet() {
        Param p = Params.byId("music_key");
        int chosen = choiceIndex(p, ParamStore.get(p.id));
        LinearLayout content = new LinearLayout(a);
        content.setOrientation(LinearLayout.VERTICAL);
        content.addView(a.sheetHeading(p.shown(), a.getString(R.string.key_sheet_hint)));
        for (int r = 0; r < 3; r++) {
            LinearLayout row = new LinearLayout(a);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int c = 0; c < 4; c++) {
                int i = r * 4 + c;
                boolean on = i == chosen;
                TextView b = new TextView(a);
                b.setText(p.choices[i]);
                b.setGravity(Gravity.CENTER);
                b.setTypeface(a.look.heading());
                b.setTextSize(18);
                b.setTextColor(on ? a.look.p.onPrimary : p.choices[i].length() > 1 ? a.look.p.muted : a.look.p.text);
                int bg = on ? a.look.p.primary : a.look.p.s2;
                b.setBackground(a.look.pressable(a.look.round(bg, Look.RADIUS_TILE), on ? a.look.p.onPrimary : a.look.p.text, Look.RADIUS_TILE));
                b.setSelected(on);
                b.setOnClickListener(v -> {
                    v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                    pickChoice(p, i);
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, a.dp(60), 1f);
                lp.setMargins(a.dp(4), a.dp(4), a.dp(4), a.dp(4));
                row.addView(b, lp);
            }
            content.addView(row);
        }
        a.look.fonts(content);
        a.openSheet(content);
    }

    /** The Scale picker: one row per scale with its notes as dots (filled = in the scale), the chosen one ticked. */
    private void showScaleSheet() {
        Param p = Params.byId("music_scale");
        Param key = Params.byId("music_key");
        LinearLayout content = new LinearLayout(a);
        content.setOrientation(LinearLayout.VERTICAL);
        content.addView(a.sheetHeading(p.shown(),
                a.getString(R.string.scale_sheet_hint, key.choices[choiceIndex(key, ParamStore.get(key.id))])));
        addChoiceList(content, p, (i, on) -> new ScaleDots(i < Params.SCALE_STEPS.length ? Params.SCALE_STEPS[i] : new int[0],
                on ? a.look.p.onContainer : a.look.p.primary, a.look.p.line));
        a.openSheet(content);
    }

    /** A setting's choices as list rows; a pick is stored and closes the sheet. */
    private void addChoiceList(LinearLayout content, Param p, MainActivity.RowExtra extra) {
        a.addListRows(content, p.choices, choiceIndex(p, ParamStore.get(p.id)), extra, i -> pickChoice(p, i));
    }

    /** Twelve dots, one per semitone above the key: filled where the scale has a note. Decoration only. */
    private final class ScaleDots extends View {
        private final boolean[] in = new boolean[12];
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final int onColor, offColor;

        ScaleDots(int[] steps, int onColor, int offColor) {
            super(a);
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
        a.updateMoverUi();
        a.refreshRows();
        a.closeSheet();
    }

    /**
     * A pick-one setting: two or three short choices as side-by-side buttons (one tap, the chosen one's explanation
     * underneath), longer lists as a tile that opens a list sheet.
     */
    void addChoiceRow(LinearLayout parent, Param p, boolean compact) {
        if (segmentable(p)) {
            addSegmentRow(parent, p, compact);
            return;
        }
        View tile = pickTile(p, () -> showChoiceSheet(p), compact);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, a.dp(compact ? 6 : 12), 0, 0);
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
        View label = a.nameAndHint(p, compact);
        label.setPadding(0, a.dp(compact ? 6 : 14), 0, a.dp(8));   // (in line with the slider names)
        parent.addView(label);
        LinearLayout bar = new LinearLayout(a);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackground(a.look.outline(a.look.p.line, 20));
        bar.setClipToOutline(true);
        bar.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
        android.graphics.drawable.GradientDrawable divider = new android.graphics.drawable.GradientDrawable();
        divider.setColor(a.look.p.line);
        divider.setSize(a.dp(1), 1);
        bar.setDividerDrawable(divider);
        MainActivity.Row row = new MainActivity.Row(p, null, null, null);
        row.segment = new TextView[p.choices.length];
        for (int i : displayOrder(p.choices)) {
            TextView b = new TextView(a);
            b.setText(shortName(p.choices[i]));
            b.setGravity(Gravity.CENTER);
            b.setTextSize(14);
            b.setMaxLines(1);
            b.setAutoSizeTextTypeUniformWithConfiguration(11, 14, 1, TypedValue.COMPLEX_UNIT_SP);
            b.setPadding(a.dp(6), 0, a.dp(6), 0);
            String full = p.choices[i];
            b.setContentDescription(p.label + ": " + full);
            final int pick = i;
            b.setOnClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                if (pick != choiceIndex(p, ParamStore.get(p.id))) {
                    ParamStore.set(p.id, pick);
                    a.updateMoverUi();
                    a.refreshRows();
                }
            });
            bar.addView(b, new LinearLayout.LayoutParams(0, a.dp(compact ? 40 : 44), 1f));
            row.segment[i] = b;
        }
        parent.addView(bar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        row.segmentHint = new TextView(a);
        a.look.hint(row.segmentHint);
        row.segmentHint.setPadding(0, a.dp(6), 0, 0);
        parent.addView(row.segmentHint);
        a.rows.add(row);
        showSegment(row, choiceIndex(p, ParamStore.get(p.id)));
    }

    /** The chosen button filled; its explanation (if it has one) underneath. */
    void showSegment(MainActivity.Row r, int chosen) {
        for (int i = 0; i < r.segment.length; i++) {
            styleSegment(r.segment[i], i == chosen);
        }
        String d = detail(r.param.choices[chosen]);
        r.segmentHint.setText(d);
        r.segmentHint.setVisibility(d.isEmpty() ? View.GONE : View.VISIBLE);
    }

    void styleSegment(TextView b, boolean on) {
        b.setSelected(on);
        b.setTypeface(on ? a.look.textStrong() : a.look.text());
        b.setTextColor(on ? a.look.p.onContainer : a.look.p.text);
        b.setBackground(on ? new android.graphics.drawable.ColorDrawable(a.look.p.container)
                : a.look.pressable(null, a.look.p.text, 0));
    }

    /** A list sheet for a pick-one setting: its choices, the chosen one ticked; one tap picks and closes. */
    private void showChoiceSheet(Param p) {
        LinearLayout content = new LinearLayout(a);
        content.setOrientation(LinearLayout.VERTICAL);
        content.addView(a.sheetHeading(p.shown(), null));
        addChoiceList(content, p, null);
        a.openSheet(content);
    }

    /** The entry a pick-one value shows: the first one if the value points past the list (a user kit gone). */
    static int choiceIndex(Param p, float v) {
        int i = Math.round(v);
        return i >= 0 && i < p.choices.length ? i : 0;
    }
}
