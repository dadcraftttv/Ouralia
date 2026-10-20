package app.pdscenes;

/**
 * The Ouralia look in one place: every colour of a theme worked out from one accent colour, plus the type sizes and
 * shapes. Plain Java with no Android in it, so the tests (and one day an iOS app) use exactly the same numbers.
 *
 * The accent gives only a hue and how colourful it is; each theme sets the lightness itself and then checks the
 * contrast, so any accent a user picks keeps text readable (WCAG AA, 4.5:1, for every text colour on what it sits on).
 * DESIGN-NOTES "The look".
 */
final class Look {
    private Look() { }

    // Themes, the same numbers as UiSettings.
    static final int LIGHT = 0;
    static final int DARK = 1;
    static final int AMOLED = 2;

    /** Ouralia violet (#8B6CFF), the default accent. */
    static final float DEFAULT_HUE = 252.7f;
    static final float DEFAULT_CHROMA = 1f;

    /**
     * The ready-made accents (Setup, Appearance): id (stored), hue, colourfulness. Their names are in strings.xml
     * (accent_names, same order). "wall" (the wallpaper's, Android 12+) and "custom" (Your own colour) are the others.
     */
    static final String[] ACCENT_IDS = {"violet", "blue", "teal", "green", "amber", "coral", "pink", "graphite"};
    static final float[][] ACCENTS = {
            {DEFAULT_HUE, 1f}, {215f, 1f}, {172f, 0.9f}, {130f, 0.75f}, {40f, 1f}, {12f, 1f}, {330f, 1f}, {240f, 0.08f}};

    /** How an accent looks on its swatch (and the custom colour ring's knob): mid lightness, before any theme. */
    static int swatch(float hue, float chroma) {
        return hsl(hue, 80 * Math.max(0f, Math.min(1f, chroma)), 55);
    }

    /** The least contrast any text colour gets against what it sits on. */
    static final double MIN_TEXT_CONTRAST = 4.5;

    // Shapes and type (dp / sp). The fonts: Sora for headings, Inter for text, JetBrains Mono for values.
    static final int RADIUS_CARD = 24;
    static final int RADIUS_TILE = 16;
    static final int RADIUS_PILL = 24;
    static final int CARD_GAP = 10;
    static final int CARD_PAD = 20;
    static final int TEXT_TITLE = 21;
    static final int TEXT_HEADING = 16;
    static final int TEXT_LABEL = 15;
    static final int TEXT_HINT = 13;
    static final int TEXT_VALUE = 15;

    /** One theme's colours, ARGB. */
    static final class Palette {
        int bg, s1, s2, line, text, muted, primary, primaryText, onPrimary, container, onContainer, aurora,
                dock, sheet, err, errBg;
        boolean light;
    }

    /** The palette for {@code theme} with an accent of {@code hue} (degrees) and {@code chroma} (0 = grey, 1 = full). */
    static Palette palette(int theme, float hue, float chroma) {
        float h = ((hue % 360f) + 360f) % 360f;
        float f = Math.max(0f, Math.min(1f, chroma));
        float h2 = (h + 260f) % 360f;   // the dial's second colour: a cool partner to the accent
        Palette p = new Palette();
        if (theme == LIGHT) {
            p.light = true;
            p.bg = hsl(h, 35 * f, 97);
            p.s1 = hsl(h, 30 * f, 93.5f);
            p.s2 = hsl(h, 25 * f, 88);
            p.line = hsl(h, 14 * f, 80);
            p.dock = 0xFFFFFFFF;
            p.sheet = hsl(h, 40 * f, 99);
            p.text = fit(h, 35 * f, 10, p.s2, -1);
            p.muted = fit(h, 10 * f, 34, p.s2, -1);
            p.primary = fit(h, 58 * f, 44, 0xFFFFFFFF, -1);
            p.onPrimary = 0xFFFFFFFF;
            p.primaryText = fit(h, 60 * f, 36, p.s2, -1);
            p.container = hsl(h, 80 * f, 90);
            p.onContainer = fit(h, 50 * f, 22, p.container, -1);
            p.aurora = hsl(h2, 80 * f, 38);
            p.err = 0xFF8C1D18;
            p.errBg = 0xFFFFDAD6;
        } else {
            boolean amoled = theme == AMOLED;
            p.bg = amoled ? 0xFF000000 : hsl(h, 25 * f, 6);
            p.s1 = hsl(h, 16 * f, amoled ? 9 : 12);
            p.s2 = hsl(h, 16 * f, amoled ? 15 : 18);
            p.line = hsl(h, 12 * f, 26);
            p.dock = hsl(h, 20 * f, amoled ? 13 : 16);
            p.sheet = hsl(h, 18 * f, amoled ? 11 : 14);
            p.text = fit(h, 30 * f, 94, p.s2, +1);
            p.muted = fit(h, 12 * f, 70, p.s2, +1);
            p.primary = hsl(h, 90 * f, 76);
            p.onPrimary = fit(h, 60 * f, 12, p.primary, -1);
            p.primaryText = fit(h, 90 * f, 82, p.s2, +1);
            p.container = hsl(h, 40 * f, 26);
            p.onContainer = fit(h, 85 * f, 91, p.container, +1);
            p.aurora = hsl(h2, 75 * f, 62);
            p.err = 0xFFFFB4AB;
            p.errBg = 0xFF4A1714;
        }
        return p;
    }

    /**
     * The colour at hue/saturation/lightness, made lighter ({@code dir} +1) or darker (-1) a step at a time until
     * it reads against {@code against}. Pale yellows and greens are bright at the same lightness as a blue, so the
     * fixed lightness alone could fail for them.
     */
    static int fit(float h, float s, float l, int against, int dir) {
        int c = hsl(h, s, l);
        while (contrast(c, against) < MIN_TEXT_CONTRAST && l > 0 && l < 100) {
            l += dir;
            c = hsl(h, s, l);
        }
        return c;
    }

    /** HSL (degrees, percent, percent) to opaque ARGB. */
    static int hsl(float h, float s, float l) {
        float sf = Math.max(0, Math.min(100, s)) / 100f, lf = Math.max(0, Math.min(100, l)) / 100f;
        float c = (1 - Math.abs(2 * lf - 1)) * sf;
        float hp = (((h % 360f) + 360f) % 360f) / 60f;
        float x = c * (1 - Math.abs(hp % 2 - 1));
        float r = 0, g = 0, b = 0;
        if (hp < 1) { r = c; g = x; } else if (hp < 2) { r = x; g = c; } else if (hp < 3) { g = c; b = x; }
        else if (hp < 4) { g = x; b = c; } else if (hp < 5) { r = x; b = c; } else { r = c; b = x; }
        float m = lf - c / 2;
        return 0xFF000000 | (Math.round((r + m) * 255) << 16) | (Math.round((g + m) * 255) << 8) | Math.round((b + m) * 255);
    }

    /** The hue (degrees) and chroma (0..1, the HSL saturation) of a colour, e.g. the wallpaper's accent. */
    static float[] hueChroma(int argb) {
        float r = ((argb >> 16) & 0xFF) / 255f, g = ((argb >> 8) & 0xFF) / 255f, b = (argb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        float h = 0;
        if (d > 0) {
            if (max == r) h = ((g - b) / d) % 6; else if (max == g) h = (b - r) / d + 2; else h = (r - g) / d + 4;
            h *= 60;
            if (h < 0) h += 360;
        }
        float l = (max + min) / 2;
        float s = d == 0 ? 0 : d / (1 - Math.abs(2 * l - 1));
        return new float[] {h, Math.min(1f, s)};
    }

    /** WCAG contrast ratio between two opaque colours (1 to 21). */
    static double contrast(int a, int b) {
        double la = luminance(a), lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double luminance(int c) {
        return 0.2126 * channel((c >> 16) & 0xFF) + 0.7152 * channel((c >> 8) & 0xFF) + 0.0722 * channel(c & 0xFF);
    }

    private static double channel(int v) {
        double s = v / 255.0;
        return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    }

    /** {@code c} at alpha {@code a} (0..1), e.g. for a pressed ripple. */
    static int alpha(int c, float a) {
        return (Math.round(Math.max(0, Math.min(1, a)) * 255) << 24) | (c & 0x00FFFFFF);
    }
}
