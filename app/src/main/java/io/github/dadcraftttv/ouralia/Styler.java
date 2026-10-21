package io.github.dadcraftttv.ouralia;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

/**
 * Puts the Ouralia look (Look.java: colours, sizes, shapes) on plain Android views: the fonts, rounded cards, tinted
 * switches and sliders, pill buttons. One per screen, made for the theme the screen was built in.
 */
final class Styler {
    final Look.Palette p;
    private final Context context;
    private final float density;
    private final int themeText, themeTextSecondary;

    private static Typeface sora, inter, interSemi, mono;

    Styler(Context context, int theme) {
        this.context = context;
        this.density = context.getResources().getDisplayMetrics().density;
        float[] accent = accentOf(context);
        p = Look.palette(theme, accent[0], accent[1]);
        TypedArray a = context.getTheme().obtainStyledAttributes(
                new int[] {android.R.attr.textColorPrimary, android.R.attr.textColorSecondary});
        themeText = a.getColor(0, 0);
        themeTextSecondary = a.getColor(1, 0);
        a.recycle();
        if (sora == null) {   // the fonts load once per process (variable fonts: one file, any weight)
            sora = font("Sora.ttf", 600);
            inter = font("Inter.ttf", 400);
            interSemi = font("Inter.ttf", 600);
            mono = font("JetBrainsMono.ttf", 700);
        }
    }

    /** The chosen accent's hue and colourfulness (UiSettings): a ready-made one, the wallpaper's, or your own. */
    static float[] accentOf(Context context) {
        return accentForId(context, UiSettings.accent(context));
    }

    /** The hue and colourfulness of the accent {@code id} ("custom": your own colour, "wall": the wallpaper's). */
    static float[] accentForId(Context context, String id) {
        if (id.equals("custom")) {
            return UiSettings.customAccent(context);
        }
        if (id.equals("wall")) {
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                return Look.hueChroma(context.getColor(android.R.color.system_accent1_500));
            }
            id = "violet";   // (the wallpaper colours came with Android 12)
        }
        for (int i = 0; i < Look.ACCENT_IDS.length; i++) {
            if (Look.ACCENT_IDS[i].equals(id)) {
                return Look.ACCENTS[i].clone();
            }
        }
        return new float[] {Look.DEFAULT_HUE, Look.DEFAULT_CHROMA};
    }

    private Typeface font(String file, int weight) {
        try {
            return new Typeface.Builder(context.getAssets(), "fonts/" + file)
                    .setFontVariationSettings("'wght' " + weight).build();
        } catch (RuntimeException e) {   // a missing font file must not stop the app: fall back to the system font
            return weight >= 600 ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT;
        }
    }

    Typeface heading() { return sora; }
    Typeface text() { return inter; }
    Typeface textStrong() { return interSemi; }
    Typeface value() { return mono; }

    int dp(float v) {
        return Math.round(v * density);
    }

    /** A rounded rectangle in {@code color}. */
    GradientDrawable round(int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    /** A rounded outline. */
    GradientDrawable outline(int stroke, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(0);
        d.setStroke(dp(1), stroke);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    /** {@code content} that ripples when pressed, in the text colour of what sits on it. */
    Drawable pressable(Drawable content, int onColor, float radiusDp) {
        return new RippleDrawable(ColorStateList.valueOf(Look.alpha(onColor, 0.16f)), content,
                content != null ? null : round(0xFFFFFFFF, radiusDp));
    }

    /** A section card: the s1 surface, rounded, clipping its pressed header to the corners. */
    void card(View v) {
        v.setBackground(round(p.s1, Look.RADIUS_CARD));
        v.setClipToOutline(true);
    }

    /** A button: {@code primary} = the filled accent pill (Start), otherwise the soft tonal pill. */
    void button(Button b, boolean primary) {
        int bg = primary ? p.primary : p.container, fg = primary ? p.onPrimary : p.onContainer;
        b.setBackground(pressable(round(bg, Look.RADIUS_PILL), fg, Look.RADIUS_PILL));
        b.setTextColor(enabledColors(fg));
        b.setTypeface(primary ? sora : interSemi);
        b.setTextSize(primary ? 16 : 14);
        b.setAllCaps(false);
        b.setStateListAnimator(null);   // flat: no Material lift
        b.setMinHeight(dp(48));
        b.setMinimumHeight(dp(48));
        b.setPadding(dp(16), dp(8), dp(16), dp(8));
    }

    /** Text colours that fade when the control is disabled (Undo with nothing to undo). */
    private ColorStateList enabledColors(int c) {
        return new ColorStateList(new int[][] {{-android.R.attr.state_enabled}, {}},
                new int[] {Look.alpha(c, 0.45f), c});
    }

    /**
     * The Material 3 switch: a 52x32 track (outlined when off, accent when on) and a thumb that grows from a small
     * dot (off) to a large one (on).
     */
    void switchTint(Switch s) {
        android.graphics.drawable.StateListDrawable track = new android.graphics.drawable.StateListDrawable();
        GradientDrawable on = round(p.primary, 16);
        on.setSize(dp(52), dp(32));
        GradientDrawable off = round(p.s2, 16);
        off.setStroke(dp(2), p.line);
        off.setSize(dp(52), dp(32));
        track.addState(new int[] {android.R.attr.state_checked}, on);
        track.addState(new int[] {}, off);
        android.graphics.drawable.StateListDrawable thumb = new android.graphics.drawable.StateListDrawable();
        thumb.addState(new int[] {android.R.attr.state_checked}, dot(p.onPrimary, 24, 4));
        thumb.addState(new int[] {}, dot(p.muted, 16, 8));
        s.setTrackDrawable(track);
        s.setThumbDrawable(thumb);
        s.setThumbTintList(null);
        s.setTrackTintList(null);
        s.setSwitchMinWidth(dp(52));
        s.setShowText(false);
    }

    /** A round dot {@code sizeDp} across with {@code insetDp} of room around it (so both thumbs are 32 tall). */
    private Drawable dot(int color, int sizeDp, int insetDp) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        d.setSize(dp(sizeDp), dp(sizeDp));
        // (a LayerDrawable's insets add to its size without counting as padding: an InsetDrawable's would, and the
        // Switch then shrinks the track to make room for it)
        android.graphics.drawable.LayerDrawable l = new android.graphics.drawable.LayerDrawable(new Drawable[] {d});
        l.setLayerInset(0, dp(insetDp), dp(insetDp), dp(insetDp), dp(insetDp));
        return l;
    }

    /** A pick-one menu's closed state: a rounded tile with a chevron on the right. */
    Drawable choiceTile(Drawable chevron) {
        chevron.setTint(p.muted);
        android.graphics.drawable.LayerDrawable layers = new android.graphics.drawable.LayerDrawable(
                new Drawable[] {round(p.s2, Look.RADIUS_TILE), chevron});
        layers.setLayerGravity(1, android.view.Gravity.END | android.view.Gravity.CENTER_VERTICAL);
        layers.setLayerInsetEnd(1, dp(12));
        return pressable(layers, p.text, Look.RADIUS_TILE);
    }

    /** A button for something that can't be undone (Reset everything): solid stop-sign red, white bold text. */
    void danger(Button b) {
        b.setBackground(pressable(round(Look.DANGER, Look.RADIUS_PILL), Look.ON_DANGER, Look.RADIUS_PILL));
        b.setTextColor(Look.ON_DANGER);
        b.setTypeface(sora);
    }

    void check(CompoundButton c) {
        c.setButtonTintList(new ColorStateList(new int[][] {{android.R.attr.state_checked}, {}},
                new int[] {p.primary, p.muted}));
    }

    /** A lock toggle shown as a padlock: closed and in the accent when locked, open and quiet when not. */
    void lock(CompoundButton c, Drawable closed, Drawable open) {
        android.graphics.drawable.StateListDrawable icon = new android.graphics.drawable.StateListDrawable();
        icon.addState(new int[] {android.R.attr.state_checked}, closed);
        icon.addState(new int[] {}, open);
        // a CheckBox draws its button at the start edge, so pad the padlock out to the whole 44dp box: then the
        // icon sits in the middle of the round press glow
        c.setButtonDrawable(new android.graphics.drawable.InsetDrawable(icon, dp(12)));
        c.setButtonTintList(new ColorStateList(new int[][] {{android.R.attr.state_checked}, {}},
                new int[] {p.primary, p.muted}));
        c.setText("");
        c.setGravity(android.view.Gravity.CENTER);
        c.setPadding(0, 0, 0, 0);
        c.setBackground(pressable(null, p.text, 22));
    }

    /** A small on/off pill (a card header's Movers): filled when on, an outline when off. */
    void pill(android.widget.ToggleButton b) {
        android.graphics.drawable.StateListDrawable bg = new android.graphics.drawable.StateListDrawable();
        bg.addState(new int[] {android.R.attr.state_checked}, round(p.container, 10));
        bg.addState(new int[] {}, outline(p.line, 10));
        b.setBackground(pressable(bg, p.text, 10));
        b.setTextColor(new ColorStateList(new int[][] {{android.R.attr.state_checked}, {}},
                new int[] {p.onContainer, p.muted}));
        b.setTypeface(interSemi);
        b.setTextSize(12);
        b.setAllCaps(false);
        b.setStateListAnimator(null);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(dp(32));
        b.setMinimumHeight(dp(32));
        b.setPadding(dp(12), 0, dp(12), 0);
    }

    void slider(SeekBar s) {
        s.setProgressTintList(ColorStateList.valueOf(p.primary));
        s.setProgressBackgroundTintList(ColorStateList.valueOf(p.s2));
        s.setProgressBackgroundTintMode(android.graphics.PorterDuff.Mode.SRC);
        s.setThumbTintList(ColorStateList.valueOf(p.primary));
    }

    /** A setting's name. */
    void label(TextView t) {
        t.setTypeface(interSemi);
        t.setTextSize(Look.TEXT_LABEL);
        t.setTextColor(p.text);
    }

    /** A quiet line under a setting. */
    void hint(TextView t) {
        t.setTypeface(inter);
        t.setTextSize(Look.TEXT_HINT);
        t.setTextColor(p.muted);
    }

    /**
     * Gives every text in {@code root} the new fonts and colours, unless it was styled already: bold becomes Inter
     * semibold, the rest Inter; the theme's own text colours become the palette's. Run on each tab and on each
     * section the first time it is filled.
     */
    void fonts(View root) {
        if (root instanceof TextView) {
            TextView t = (TextView) root;
            Typeface tf = t.getTypeface();
            if (tf != sora && tf != inter && tf != interSemi && tf != mono) {
                t.setTypeface(tf != null && tf.isBold() ? interSemi : inter);
            }
            int c = t.getCurrentTextColor();
            if (c == themeText) {
                t.setTextColor(p.text);
            } else if (c == themeTextSecondary) {
                t.setTextColor(p.muted);
            }
        }
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) {
                fonts(g.getChildAt(i));
            }
        }
    }
}
