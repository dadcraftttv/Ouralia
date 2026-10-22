// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.dadcraftttv.ouralia;

import android.graphics.Canvas;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Setup's Appearance card (moved out of MainActivity on Oct 22, ROADMAP 22): the theme (Phone, Light, Dark,
 * AMOLED) and the accent colour (ready-made swatches, the wallpaper's on Android 12+, or your own from the colour
 * ring). A change rebuilds the screen in the new look; the sound keeps playing. Neither is part of presets.
 */
final class AppearanceUi {
    private final MainActivity a;

    AppearanceUi(MainActivity activity) {
        a = activity;
    }

    /**
     * Appearance: the theme (Phone, Light, Dark, AMOLED) and the accent colour (ready-made swatches, the wallpaper's on
     * Android 12+, or your own from a colour ring). A change rebuilds the screen in the new look; the sound keeps
     * playing. Neither is part of presets.
     */
    void addAppearanceSection(LinearLayout parent) {
        LinearLayout body = a.addSection(parent, "appearance", a.getString(R.string.section_appearance), false);
        int[] themes = {UiSettings.THEME_PHONE, UiSettings.THEME_LIGHT, UiSettings.THEME_DARK, UiSettings.THEME_AMOLED};
        String[] names = {a.getString(R.string.theme_phone), a.getString(R.string.theme_light),
                a.getString(R.string.theme_dark), a.getString(R.string.theme_amoled)};
        int current = UiSettings.theme(a), chosen = 0;
        for (int i = 0; i < themes.length; i++) {
            if (themes[i] == current) {
                chosen = i;
            }
        }
        TextView themeLabel = new TextView(a);
        themeLabel.setText(R.string.theme_label);
        a.look.label(themeLabel);
        themeLabel.setPadding(a.dp(4), a.dp(8), 0, a.dp(8));
        body.addView(themeLabel);
        body.addView(a.segmentBar(names, chosen, i -> {
            if (themes[i] != UiSettings.theme(a)) {
                UiSettings.setTheme(a, themes[i]);
                a.recreate();   // rebuilds the screen in the new theme; audio keeps running
            }
        }));
        if (current == UiSettings.THEME_PHONE || current == UiSettings.THEME_AMOLED) {
            TextView hint = new TextView(a);
            hint.setText(current == UiSettings.THEME_PHONE ? R.string.theme_hint_phone : R.string.theme_hint_amoled);
            a.look.hint(hint);
            hint.setPadding(a.dp(4), a.dp(6), 0, 0);
            body.addView(hint);
        }

        // the accent: a grid of round swatches, five to a row
        String accent = UiSettings.accent(a);
        String[] accentNames = a.getResources().getStringArray(R.array.accent_names);
        List<String> ids = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 31) {
            ids.add("wall");
            labels.add(a.getString(R.string.accent_wall));
        }
        for (int i = 0; i < Look.ACCENT_IDS.length; i++) {
            ids.add(Look.ACCENT_IDS[i]);
            labels.add(accentNames[i]);
        }
        ids.add("custom");
        labels.add(a.getString(R.string.accent_custom));
        String shownName = labels.get(Math.max(0, ids.indexOf(accent)));

        LinearLayout head = new LinearLayout(a);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setPadding(a.dp(4), a.dp(18), a.dp(4), a.dp(10));
        TextView accentLabel = new TextView(a);
        accentLabel.setText(R.string.accent_label);
        a.look.label(accentLabel);
        head.addView(accentLabel, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView accentName = new TextView(a);
        accentName.setText(shownName);
        a.look.label(accentName);
        accentName.setTextColor(a.look.p.primaryText);
        head.addView(accentName);
        body.addView(head);

        LinearLayout row = null;
        for (int i = 0; i < ids.size(); i++) {
            if (i % 5 == 0) {
                row = new LinearLayout(a);
                row.setOrientation(LinearLayout.HORIZONTAL);
                body.addView(row);
            }
            String id = ids.get(i);
            row.addView(swatch(id, labels.get(i), id.equals(accent)), new LinearLayout.LayoutParams(0, a.dp(56), 1f));
        }
        for (int i = ids.size() % 5; i > 0 && i < 5; i++) {   // keep the last row's swatches in their columns
            row.addView(new android.widget.Space(a), new LinearLayout.LayoutParams(0, a.dp(56), 1f));
        }
        TextView hint = new TextView(a);
        hint.setText(R.string.accent_hint);
        a.look.hint(hint);
        hint.setPadding(a.dp(4), a.dp(8), 0, a.dp(4));
        body.addView(hint);
    }

    /** One accent swatch: a 44 dp circle (ringed when chosen); tapping it switches to it, or opens the colour ring. */
    private View swatch(String id, String name, boolean chosen) {
        android.widget.FrameLayout box = new android.widget.FrameLayout(a);
        View dot = new View(a);
        android.graphics.drawable.GradientDrawable fill = new android.graphics.drawable.GradientDrawable();
        fill.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        boolean rainbow = id.equals("custom") && !chosen;
        if (rainbow) {
            fill.setGradientType(android.graphics.drawable.GradientDrawable.SWEEP_GRADIENT);
            fill.setColors(new int[] {0xFFE84B4B, 0xFFE8C94B, 0xFF5BD06A, 0xFF4BC6E8, 0xFF6A6BF0, 0xFFD25BD9, 0xFFE84B4B});
        } else {
            float[] hc = id.equals("custom") ? UiSettings.customAccent(a)
                    : id.equals("wall") ? Styler.accentForId(a, "wall") : Styler.accentForId(a, id);
            fill.setColor(Look.swatch(hc[0], hc[1]));
        }
        if (chosen) {
            fill.setStroke(a.dp(3), a.look.p.s1);   // a gap, then the ring drawn by the box behind
        }
        dot.setBackground(fill);
        if (chosen) {
            android.graphics.drawable.GradientDrawable ring = new android.graphics.drawable.GradientDrawable();
            ring.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            ring.setColor(a.look.p.text);
            View halo = new View(a);
            halo.setBackground(ring);
            box.addView(halo, new android.widget.FrameLayout.LayoutParams(a.dp(50), a.dp(50), Gravity.CENTER));
        }
        box.addView(dot, new android.widget.FrameLayout.LayoutParams(a.dp(chosen ? 46 : 44), a.dp(chosen ? 46 : 44), Gravity.CENTER));
        int mark = chosen ? R.drawable.ic_check : id.equals("custom") ? R.drawable.ic_plus : id.equals("wall") ? R.drawable.ic_image : 0;
        if (mark != 0) {
            ImageView icon = new ImageView(a);
            icon.setImageResource(mark);
            icon.setImageTintList(android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
            icon.setElevation(a.dp(1));
            box.addView(icon, new android.widget.FrameLayout.LayoutParams(a.dp(20), a.dp(20), Gravity.CENTER));
        }
        box.setContentDescription(name);
        box.setSelected(chosen);
        box.setOnClickListener(v -> {
            if (id.equals("custom")) {
                showColorSheet();
            } else if (!chosen) {
                UiSettings.setAccent(a, id);
                a.recreate();
            }
        });
        return box;
    }

    /**
     * Your own colour: drag around the ring for the colour, Colorfulness from grey to full; the middle of the ring
     * is the Start button as it would look in this theme. Use this colour rebuilds the screen in it.
     */
    private void showColorSheet() {
        float[] hc = UiSettings.customAccent(a);
        LinearLayout content = new LinearLayout(a);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.addView(a.sheetHeading(a.getString(R.string.accent_custom), a.getString(R.string.accent_custom_hint)),
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        HueRing ring = new HueRing(hc[0], hc[1]);
        content.addView(ring, new LinearLayout.LayoutParams(a.dp(232), a.dp(232)));

        LinearLayout head = new LinearLayout(a);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setPadding(a.dp(8), a.dp(16), a.dp(8), 0);
        TextView label = new TextView(a);
        label.setText(R.string.accent_colorfulness);
        a.look.label(label);
        head.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView amount = new TextView(a);
        amount.setTypeface(a.look.value());
        amount.setTextColor(a.look.p.muted);
        amount.setTextSize(Look.TEXT_VALUE);
        head.addView(amount);
        content.addView(head, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        SeekBar colorful = new SeekBar(a);
        colorful.setMax(100);
        colorful.setProgress(Math.round(hc[1] * 100));
        colorful.setContentDescription(a.getString(R.string.accent_colorfulness));
        a.look.slider(colorful);
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
        LinearLayout.LayoutParams sliderParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, a.dp(44));
        content.addView(colorful, sliderParams);

        LinearLayout buttons = a.newButtonRow(content);
        buttons.setPadding(0, a.dp(10), 0, 0);
        Button cancel = a.addButton(buttons, R.string.cancel, a::closeSheet);
        cancel.setBackground(a.look.pressable(a.look.outline(a.look.p.line, Look.RADIUS_PILL), a.look.p.text, Look.RADIUS_PILL));
        cancel.setTextColor(a.look.p.text);
        Button use = a.addButton(buttons, R.string.accent_use, () -> {
            UiSettings.setCustomAccent(a, ring.hue, ring.chroma);
            a.closeSheet();
            a.recreate();
        });
        a.look.button(use, true);
        ((LinearLayout.LayoutParams) use.getLayoutParams()).setMargins(a.dp(3), a.dp(4), a.dp(3), a.dp(4));
        a.openSheet(content);
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
        private final int theme = UiSettings.effectiveTheme(a);

        HueRing(float hue, float chroma) {
            super(a);
            this.hue = hue;
            this.chroma = chroma;
            band.setStyle(android.graphics.Paint.Style.STROKE);
            band.setStrokeWidth(a.dp(28));
            word.setTypeface(a.look.heading());
            word.setTextSize(a.dp(15));
            word.setTextAlign(android.graphics.Paint.Align.CENTER);
            setContentDescription(a.getString(R.string.accent_custom));
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
            float cx = getWidth() / 2f, cy = getHeight() / 2f, r = Math.min(cx, cy) - a.dp(15);
            band.setAlpha(Math.round(255 * (0.35f + 0.65f * chroma)));
            canvas.drawCircle(cx, cy, r, band);
            double angle = Math.toRadians(hue - 90);
            float kx = cx + (float) (r * Math.cos(angle)), ky = cy + (float) (r * Math.sin(angle));
            fill.setColor(0xFFFFFFFF);
            canvas.drawCircle(kx, ky, a.dp(16), fill);
            fill.setColor(Look.swatch(hue, chroma));
            canvas.drawCircle(kx, ky, a.dp(12), fill);
            Look.Palette preview = Look.palette(theme, hue, chroma);
            fill.setColor(preview.primary);
            canvas.drawCircle(cx, cy, a.dp(54), fill);
            word.setColor(preview.onPrimary);
            canvas.drawText(a.getString(R.string.start), cx, cy + a.dp(5), word);
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
}
