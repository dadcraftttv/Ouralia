// SPDX-License-Identifier: GPL-3.0-or-later
package app.pdscenes;

import android.app.Dialog;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.SweepGradient;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * The first-run walkthrough (roadmap item 5, DadCraft Oct 13; redesigned Oct 21 to "pop" like the rest of the app):
 * five full-screen pages, each with a glowing badge, then "Show me around" hands over to the spotlight tour on the
 * real screen ({@link Tour}). Everything starts off, so Start plays only the room. Reopened from Setup, About. The
 * host (MainActivity) supplies what the pages need from the phone.
 */
final class Walkthrough {

    /** What the pages ask of the screen behind them. */
    interface Host {
        boolean headphonesConnected();

        boolean micAllowed();

        void askForMic();

        void startTour();

        int dp(int v);
    }

    static final int PAGES = 5;
    private static final int PAGE_HEADPHONES = 1, PAGE_MIC = 2;

    private static final int[] TITLES = {R.string.walk_t0, R.string.walk_t1, R.string.walk_t2, R.string.walk_t3,
            R.string.walk_t4};
    private static final int[] BODIES = {R.string.walk_b0, R.string.walk_b1, R.string.walk_b2, R.string.walk_b3,
            R.string.walk_b4};
    private static final int[] ICONS = {R.drawable.ic_wave, R.drawable.ic_headphones, R.drawable.ic_mic,
            R.drawable.ic_tab_sound, R.drawable.ic_play};

    private final Dialog dialog;
    private final Host host;
    private final android.content.Context context;
    private final LinearLayout root, content, dots;
    private final Hero hero;
    private final TextView title, body, status;
    private final Button action, back, next;
    private Styler look;
    private int page;

    Walkthrough(android.app.Activity activity, int theme, Host host) {
        this.context = activity;
        this.host = host;
        dialog = new Dialog(activity, theme);
        root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = host.dp(24);
        root.setPadding(pad, host.dp(16), pad, host.dp(16));

        // the progress pills and Skip along the top
        LinearLayout top = new LinearLayout(activity);
        top.setGravity(Gravity.CENTER_VERTICAL);
        dots = new LinearLayout(activity);
        dots.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < PAGES; i++) {
            View d = new View(activity);
            LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(host.dp(8), host.dp(8));
            dp.setMarginEnd(host.dp(6));
            dots.addView(d, dp);
        }
        top.addView(dots, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button skip = new Button(activity, null, android.R.attr.borderlessButtonStyle);
        skip.setAllCaps(false);
        skip.setText(R.string.walk_skip);
        skip.setOnClickListener(v -> dialog.dismiss());
        top.addView(skip);
        root.addView(top);

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_VERTICAL);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        hero = new Hero(activity);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(host.dp(128), host.dp(128));
        hp.bottomMargin = host.dp(28);
        content.addView(hero, hp);
        title = new TextView(activity);
        title.setTextSize(28);
        title.setPadding(0, 0, 0, host.dp(14));
        content.addView(title);
        body = new TextView(activity);
        body.setTextSize(17);
        body.setLineSpacing(0, 1.18f);
        content.addView(body);
        status = new TextView(activity);
        status.setTextSize(15);
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.setCompoundDrawablePadding(host.dp(8));
        status.setPadding(host.dp(12), host.dp(8), host.dp(14), host.dp(8));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.topMargin = host.dp(20);
        content.addView(status, sp);
        action = new Button(activity);
        action.setAllCaps(false);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        ap.topMargin = host.dp(16);
        content.addView(action, ap);

        // Back and Next, in thumb reach
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, host.dp(12), 0, 0);
        back = new Button(activity, null, android.R.attr.borderlessButtonStyle);
        back.setAllCaps(false);
        back.setText(R.string.walk_back);
        back.setOnClickListener(v -> turn(page - 1));
        next = new Button(activity);
        next.setAllCaps(false);
        next.setOnClickListener(v -> {
            if (page == PAGES - 1) {
                dialog.dismiss();
                host.startTour();
            } else {
                turn(page + 1);
            }
        });
        row.addView(back);
        row.addView(new View(activity), new LinearLayout.LayoutParams(0, 1, 1f));
        row.addView(next, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, host.dp(52)));
        root.addView(row);
        // (Skip's colours are set with the rest in applyLook)
        skipButton = skip;

        dialog.setContentView(root);
        // clear of the status and navigation bars (the page is drawn edge to edge)
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars());
                v.setPadding(pad, bars.top + host.dp(16), pad, bars.bottom + host.dp(16));
                return insets;
            });
        }
        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            }
        });
        dialog.setOnDismissListener(d -> hero.stop());
    }

    private final Button skipButton;

    /** The app's look (Look.java through Styler): its page colour, fonts, and accent buttons. */
    void applyLook(Styler look) {
        this.look = look;
        // the page's colour with a soft glow of the accent and the dial's second colour behind the badge
        android.graphics.drawable.GradientDrawable glow = new android.graphics.drawable.GradientDrawable();
        glow.setGradientType(android.graphics.drawable.GradientDrawable.RADIAL_GRADIENT);
        glow.setGradientCenter(0.2f, 0.3f);
        glow.setGradientRadius(look.dp(420));
        glow.setColors(new int[] {Look.alpha(look.p.primary, look.p.light ? 0.16f : 0.22f),
                Look.alpha(look.p.aurora, 0.06f), look.p.bg});
        root.setBackground(new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[] {
                new android.graphics.drawable.ColorDrawable(look.p.bg), glow}));
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(look.p.bg));
        }
        hero.setLook(look);
        title.setTypeface(look.heading());
        title.setTextColor(look.p.text);
        body.setTypeface(look.text());
        body.setTextColor(look.p.text);
        status.setTypeface(look.textStrong());
        look.button(action, false);
        look.button(next, true);
        next.setPadding(look.dp(28), 0, look.dp(28), 0);
        for (Button b : new Button[] {back, skipButton}) {
            b.setTypeface(look.textStrong());
            b.setTextColor(look.p.primaryText);
        }
    }

    void open() {
        show(0);
        dialog.show();
        hero.start();
    }

    boolean isShowing() {
        return dialog.isShowing();
    }

    /** Called once a second and after the mic question: the live lines (headphones, permission) catch up. */
    void refresh() {
        if (dialog.isShowing()) {
            show(page);
        }
    }

    /** The next page slides in from the side it comes from. */
    private void turn(int p) {
        int dir = p > page ? 1 : -1;
        int shift = host.dp(40);
        content.animate().alpha(0f).translationX(-dir * shift).setDuration(140).withEndAction(() -> {
            show(p);
            content.setTranslationX(dir * shift);
            content.animate().alpha(1f).translationX(0).setDuration(220)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(2f)).start();
            hero.pop();
        }).start();
    }

    private void show(int p) {
        page = Math.max(0, Math.min(PAGES - 1, p));
        title.setText(TITLES[page]);
        body.setText(context.getString(BODIES[page]));
        hero.setIcon(context.getDrawable(ICONS[page]));
        back.setVisibility(page == 0 ? View.INVISIBLE : View.VISIBLE);
        next.setText(page == PAGES - 1 ? R.string.walk_tour : R.string.walk_next);
        status.setVisibility(View.GONE);
        action.setVisibility(View.GONE);
        if (look != null) {
            for (int i = 0; i < PAGES; i++) {
                View d = dots.getChildAt(i);
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) d.getLayoutParams();
                lp.width = host.dp(i == page ? 24 : 8);
                d.setLayoutParams(lp);
                d.setBackground(look.round(i <= page ? look.p.primary : look.p.line, 4));
            }
        }
        if (page == PAGE_HEADPHONES) {
            showStatus(host.headphonesConnected(), R.string.walk_phones_yes, R.string.walk_phones_no);
        } else if (page == PAGE_MIC) {
            boolean allowed = host.micAllowed();
            showStatus(allowed, R.string.walk_mic_yes, R.string.walk_mic_no);
            if (!allowed) {
                action.setVisibility(View.VISIBLE);
                action.setText(R.string.walk_mic_allow);
                action.setOnClickListener(v -> host.askForMic());
            }
        }
    }

    /** A live line as a chip: a tick on the accent when it's fine, a gentle warning when not yet. */
    private void showStatus(boolean ok, int yes, int no) {
        status.setVisibility(View.VISIBLE);
        status.setText(ok ? yes : no);
        if (look == null) {
            return;
        }
        int fg = ok ? look.p.onContainer : look.p.err;
        status.setTextColor(fg);
        status.setBackground(look.round(ok ? look.p.container : look.p.errBg, 18));
        Drawable icon = context.getDrawable(ok ? R.drawable.ic_check : R.drawable.ic_headphones_off);
        if (icon != null && page == PAGE_MIC && !ok) {
            icon = context.getDrawable(R.drawable.ic_mic);
        }
        if (icon != null) {
            icon = icon.mutate();
            icon.setTint(fg);
            icon.setBounds(0, 0, look.dp(18), look.dp(18));
        }
        status.setCompoundDrawablesRelative(icon, null, null, null);
    }

    /** The page's badge: its icon on a soft disc, inside a slowly turning ring in the dial's two colours. */
    private static final class Hero extends View {
        private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG), disc = new Paint(Paint.ANTI_ALIAS_FLAG),
                halo = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final android.animation.ValueAnimator spin = android.animation.ValueAnimator.ofFloat(0f, 360f);
        private Drawable icon;
        private Styler look;
        private float angle;

        Hero(android.content.Context c) {
            super(c);
            ringPaint.setStyle(Paint.Style.STROKE);
            ringPaint.setStrokeCap(Paint.Cap.ROUND);
            spin.setDuration(9000);
            spin.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            spin.setInterpolator(new android.view.animation.LinearInterpolator());
            spin.addUpdateListener(a -> {
                angle = (float) a.getAnimatedValue();
                invalidate();
            });
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        void setLook(Styler look) {
            this.look = look;
            disc.setColor(look.p.container);
            halo.setColor(Look.alpha(look.p.primary, 0.12f));
            ringPaint.setStrokeWidth(look.dp(5));
        }

        void setIcon(Drawable d) {
            icon = d == null ? null : d.mutate();
            if (icon != null && look != null) {
                icon.setTint(look.p.onContainer);
            }
            invalidate();
        }

        void start() {
            if (android.animation.ValueAnimator.areAnimatorsEnabled()) {
                spin.start();
            }
        }

        void stop() {
            spin.cancel();
        }

        /** A little bounce as a page arrives. */
        void pop() {
            setScaleX(0.85f);
            setScaleY(0.85f);
            animate().scaleX(1f).scaleY(1f).setDuration(320)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(2.2f)).start();
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            if (look != null) {
                ringPaint.setShader(new SweepGradient(w / 2f, h / 2f,
                        new int[] {look.p.aurora, look.p.primary, Look.alpha(look.p.primary, 0f), look.p.aurora},
                        new float[] {0f, 0.55f, 0.8f, 1f}));
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (look == null) {
                return;
            }
            float cx = getWidth() / 2f, cy = getHeight() / 2f, r = Math.min(cx, cy);
            canvas.drawCircle(cx, cy, r, halo);
            canvas.save();
            canvas.rotate(angle, cx, cy);
            canvas.drawCircle(cx, cy, r - look.dp(10), ringPaint);
            canvas.restore();
            canvas.drawCircle(cx, cy, r - look.dp(22), disc);
            if (icon != null) {
                int s = look.dp(44);
                icon.setBounds(Math.round(cx - s / 2f), Math.round(cy - s / 2f), Math.round(cx + s / 2f),
                        Math.round(cy + s / 2f));
                icon.draw(canvas);
            }
        }
    }
}
