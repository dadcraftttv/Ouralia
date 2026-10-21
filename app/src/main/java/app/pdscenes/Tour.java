// SPDX-License-Identifier: GPL-3.0-or-later
package app.pdscenes;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * The spotlight tour after the walkthrough's pages (DadCraft, Oct 21: the small unlabelled buttons get arrows, then a
 * live try of Echo). It lies over the whole screen: everything is dimmed and untouchable except the lit spots,
 * curved arrows run from the message card to each spot, and a numbered badge pairs each spot with its line in the
 * card. An "explain" step waits for Next; a "try it" step lets touches through its lit spot and goes on (or shows
 * Next) once its condition holds. The host builds the steps, since it knows the views.
 */
final class Tour extends FrameLayout {

    /** One lit place on the screen: a view (looked up when shown), what it is (null: no badge or line). */
    static final class Spot {
        final Supplier<View[]> views;   // lit as one rectangle around them all
        final String label;
        final boolean touchable;

        Spot(Supplier<View[]> views, String label, boolean touchable) {
            this.views = views;
            this.label = label;
            this.touchable = touchable;
        }
    }

    static final class Step {
        final String title, body;
        final List<Spot> spots = new ArrayList<>();
        Runnable enter;             // before the step shows: the right tab, scrolled into view
        BooleanSupplier done;       // a "try it" step: when it is done
        boolean autoNext;           // ...go on by itself (otherwise Next lights up)
        Supplier<String> status;    // a live line under the text (null or "" hides it)
        String nextText;            // the Next button's words, if not "Next"

        Step(String title, String body) {
            this.title = title;
            this.body = body;
        }

        Step spot(String label, View... views) {
            spots.add(new Spot(() -> views, label, false));
            return this;
        }

        Step spot(String label, boolean touchable, Supplier<View[]> views) {
            spots.add(new Spot(views, label, touchable));
            return this;
        }
    }

    private final Styler look;
    private final List<Step> steps;
    private final Runnable onEnd;   // the tour is over (finished or skipped): the host puts things back
    private final LinearLayout card, list;
    private final TextView counter, title, body, status;
    private final Button skip, next;
    private final Paint dim = new Paint(Paint.ANTI_ALIAS_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG),
            glow = new Paint(Paint.ANTI_ALIAS_FLAG), arrow = new Paint(Paint.ANTI_ALIAS_FLAG),
            badge = new Paint(Paint.ANTI_ALIAS_FLAG), badgeText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final ValueAnimator pulse = ValueAnimator.ofFloat(0f, 1f);
    private final Runnable poll = this::poll;
    private RectF[] rects = new RectF[0];
    private int index = -1;
    private boolean ended;

    Tour(Context context, Styler look, List<Step> steps, Runnable onEnd) {
        super(context);
        this.look = look;
        this.steps = steps;
        this.onEnd = onEnd;
        setWillNotDraw(false);
        setClickable(true);   // taps outside the lit spots stop here
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);

        dim.setColor(Look.alpha(0xFF000000, look.p.light ? 0.62f : 0.74f));
        dim.setStyle(Paint.Style.FILL);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(dp(2));
        ring.setColor(look.p.primary);
        glow.setStyle(Paint.Style.STROKE);
        arrow.setStyle(Paint.Style.STROKE);
        arrow.setStrokeWidth(dp(2.5f));
        arrow.setStrokeCap(Paint.Cap.ROUND);
        arrow.setStrokeJoin(Paint.Join.ROUND);
        arrow.setColor(Look.alpha(look.p.primary, 0.95f));
        badge.setColor(look.p.primary);
        badgeText.setColor(look.p.onPrimary);
        badgeText.setTextAlign(Paint.Align.CENTER);
        badgeText.setTextSize(dp(12));
        badgeText.setTypeface(look.textStrong());

        card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        android.graphics.drawable.GradientDrawable cardBg = look.round(look.p.sheet, Look.RADIUS_CARD);
        cardBg.setStroke(dp(1), Look.alpha(look.p.primary, 0.45f));
        card.setBackground(cardBg);
        card.setElevation(dp(8));
        card.setPadding(dp(20), dp(18), dp(12), dp(10));
        card.setClickable(true);
        counter = text(12, look.textStrong(), look.p.primaryText);
        counter.setLetterSpacing(0.12f);
        card.addView(counter);
        title = text(20, look.heading(), look.p.text);
        title.setPadding(0, dp(6), dp(8), dp(6));
        card.addView(title);
        body = text(15, look.text(), look.p.text);
        body.setLineSpacing(0, 1.12f);
        body.setPadding(0, 0, dp(8), 0);
        card.addView(body);
        list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        card.addView(list);
        status = text(14, look.textStrong(), look.p.primaryText);
        status.setPadding(0, dp(10), dp(8), 0);
        card.addView(status);
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, 0);
        skip = new Button(context, null, android.R.attr.borderlessButtonStyle);
        skip.setAllCaps(false);
        skip.setText(R.string.tour_skip);
        skip.setTypeface(look.textStrong());
        skip.setTextColor(look.p.muted);
        skip.setOnClickListener(v -> end());
        row.addView(skip);
        row.addView(new View(context), new LinearLayout.LayoutParams(0, 1, 1f));
        next = new Button(context);
        look.button(next, true);
        next.setOnClickListener(v -> show(index + 1));
        row.addView(next);
        card.addView(row);
        LayoutParams cp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP);
        cp.setMargins(dp(16), 0, dp(16), 0);
        addView(card, cp);

        pulse.setDuration(1400);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.addUpdateListener(a -> invalidate());   // (also follows a spot that scrolls)
    }

    /** Lays the tour over {@code screen} and shows the first step. */
    void start(ViewGroup screen) {
        screen.addView(this, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        setAlpha(0f);
        animate().alpha(1f).setDuration(250).start();
        pulse.start();
        show(0);
    }

    boolean isRunning() {
        return !ended;
    }

    /** Skip, Back, or the last step's Done: the tour goes, and the host puts back what it changed. */
    void end() {
        if (ended) {
            return;
        }
        ended = true;
        removeCallbacks(poll);
        pulse.cancel();
        animate().alpha(0f).setDuration(200).withEndAction(() -> {
            if (getParent() != null) {
                ((ViewGroup) getParent()).removeView(this);
            }
        }).start();
        onEnd.run();
    }

    private void show(int i) {
        if (i >= steps.size()) {
            end();
            return;
        }
        index = i;
        Step s = steps.get(i);
        removeCallbacks(poll);
        if (s.enter != null) {
            s.enter.run();
        }
        counter.setText(getContext().getString(R.string.tour_counter, i + 1, steps.size()).toUpperCase());
        title.setText(s.title);
        body.setText(s.body);
        list.removeAllViews();
        int n = 0;
        for (Spot spot : s.spots) {
            if (spot.label != null) {
                list.addView(listLine(++n, spot.label));
            }
        }
        next.setText(s.nextText != null ? s.nextText
                : getContext().getString(i == steps.size() - 1 ? R.string.walk_done : R.string.tour_next));
        skip.setVisibility(i == steps.size() - 1 ? View.INVISIBLE : View.VISIBLE);
        setContentDescription(s.title + ". " + s.body);
        poll();
        card.setAlpha(0f);
        rects = new RectF[0];
        // after the host's scrolling and the card's new text have been laid out
        post(() -> post(() -> {
            if (index == i) {
                place();
                card.animate().alpha(1f).setDuration(220).start();
                announceForAccessibility(s.title);
            }
        }));
    }

    /** The live parts of a "try it" step: its status line, and whether it is done. */
    private void poll() {
        if (ended || index < 0) {
            return;
        }
        Step s = steps.get(index);
        String line = s.status != null ? s.status.get() : null;
        status.setVisibility(line == null || line.isEmpty() ? View.GONE : View.VISIBLE);
        status.setText(line);
        if (s.done != null) {
            boolean done = s.done.getAsBoolean();
            if (done && s.autoNext) {
                int at = index;
                postDelayed(() -> {
                    if (index == at && !ended) {
                        show(at + 1);
                    }
                }, 700);
                return;
            }
            next.setVisibility(done ? View.VISIBLE : (s.autoNext ? View.GONE : View.VISIBLE));
            next.setEnabled(done);
            next.setAlpha(done ? 1f : 0.4f);
            postDelayed(poll, 250);
        } else {
            next.setVisibility(View.VISIBLE);
            next.setEnabled(true);
            next.setAlpha(1f);
        }
    }

    /** The card goes above or below the lit spots, wherever there is more room, leaving space for the arrows. */
    private void place() {
        updateRects();
        int h = card.getHeight(), H = getHeight(), gap = dp(64), edge = dp(16);
        float top = Float.MAX_VALUE, bottom = 0;
        boolean any = false;
        for (RectF r : rects) {
            if (r.width() > 0) {
                any = true;
                top = Math.min(top, r.top);
                bottom = Math.max(bottom, r.bottom);
            }
        }
        float y;
        if (!any) {
            y = (H - h) / 2f;
        } else if (H - bottom - gap - edge >= h || H - bottom > top) {
            y = Math.min(bottom + gap, H - h - edge);
        } else {
            y = Math.max(edge, top - gap - h);
        }
        card.setTranslationY(y);
        card.setTranslationX(0);
    }

    private void updateRects() {
        Step s = index >= 0 && index < steps.size() ? steps.get(index) : null;
        if (s == null) {
            rects = new RectF[0];
            return;
        }
        int[] me = new int[2], at = new int[2];
        getLocationInWindow(me);
        List<RectF> out = new ArrayList<>();
        int pad = dp(3);   // (neighbouring buttons' rings just meet)
        for (Spot spot : s.spots) {
            RectF u = null;
            View[] views = spot.views.get();
            if (views != null) {
                for (View v : views) {
                    if (v == null || !v.isShown() || v.getWidth() == 0) {
                        continue;
                    }
                    v.getLocationInWindow(at);
                    RectF r = new RectF(at[0] - me[0], at[1] - me[1], at[0] - me[0] + v.getWidth(),
                            at[1] - me[1] + v.getHeight());
                    if (u == null) {
                        u = r;
                    } else {
                        u.union(r);
                    }
                }
            }
            if (u == null) {
                u = new RectF(-1, -1, -1, -1);   // (not on screen: no hole, nothing touchable)
            } else {
                u.inset(-pad, -pad);
            }
            out.add(u);
        }
        rects = out.toArray(new RectF[0]);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN && index >= 0 && !ended) {
            float x = e.getX(), y = e.getY();
            boolean onCard = x >= card.getLeft() && x <= card.getRight()
                    && y >= card.getTop() + card.getTranslationY() && y <= card.getBottom() + card.getTranslationY();
            List<Spot> spots = steps.get(index).spots;
            for (int i = 0; i < rects.length && i < spots.size(); i++) {
                if (!onCard && spots.get(i).touchable && rects[i].contains(x, y)) {
                    return false;   // through to the app underneath
                }
            }
        }
        return super.dispatchTouchEvent(e);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        updateRects();
        float r = dp(16);
        path.reset();
        path.setFillType(Path.FillType.EVEN_ODD);
        path.addRect(0, 0, getWidth(), getHeight(), Path.Direction.CW);
        for (RectF h : rects) {
            if (h.width() > 0) {
                path.addRoundRect(h, r, r, Path.Direction.CW);
            }
        }
        canvas.drawPath(path, dim);
        float t = (float) pulse.getAnimatedValue();
        List<Spot> spots = index >= 0 && index < steps.size() ? steps.get(index).spots : new ArrayList<>();
        int n = 0;
        float cardTop = card.getTop() + card.getTranslationY(), cardBottom = cardTop + card.getHeight();
        for (int i = 0; i < rects.length; i++) {
            RectF h = rects[i];
            if (h.width() <= 0) {
                continue;
            }
            canvas.drawRoundRect(h, r, r, ring);
            // a soft ring that swells out and fades, again and again
            float grow = dp(10) * t;
            glow.setStrokeWidth(dp(3));
            glow.setColor(Look.alpha(look.p.primary, 0.55f * (1f - t)));
            RectF g = new RectF(h);
            g.inset(-grow, -grow);
            canvas.drawRoundRect(g, r + grow, r + grow, glow);
            if (card.getAlpha() > 0 && card.getHeight() > 0) {
                drawArrow(canvas, h, cardTop, cardBottom, i, rects.length);
            }
            if (i < spots.size() && spots.get(i).label != null) {
                n++;
                // on the middle of the spot's far edge, away from its arrow
                float bx = h.centerX(), by = h.centerY() < cardTop ? h.top : h.bottom, br = dp(11);
                canvas.drawCircle(bx, by, br + dp(2), dim);
                canvas.drawCircle(bx, by, br, badge);
                canvas.drawText(String.valueOf(n), bx, by - (badgeText.descent() + badgeText.ascent()) / 2, badgeText);
            }
        }
    }

    /** A curved arrow from the card's edge to the spot's nearest side, the arrows of one card spread along it. */
    private void drawArrow(Canvas canvas, RectF h, float cardTop, float cardBottom, int i, int count) {
        boolean below = h.centerY() > cardBottom;   // the spot under the card
        boolean above = h.centerY() < cardTop;
        if (!below && !above) {
            return;
        }
        float cardLeft = card.getLeft() + dp(28), cardRight = card.getRight() - dp(28);
        int rank = 0;   // (left to right, so the arrows don't cross)
        for (RectF o : rects) {
            if (o.width() > 0 && o.centerX() < h.centerX()) {
                rank++;
            }
        }
        float sx = count == 1 ? Math.max(cardLeft, Math.min(cardRight, h.centerX()))
                : cardLeft + (cardRight - cardLeft) * (rank + 0.5f) / count;
        float sy = below ? cardBottom + dp(6) : cardTop - dp(6);
        float ex = h.centerX(), ey = below ? h.top - dp(8) : h.bottom + dp(8);
        if (Math.abs(ey - sy) < dp(14)) {
            return;
        }
        float my = (sy + ey) / 2;   // leaves the card straight, drops straight onto the spot
        arrow.setAlpha(Math.round(255 * card.getAlpha()));
        Path a = new Path();
        a.moveTo(sx, sy);
        a.cubicTo(sx, my, ex, my, ex, ey);
        canvas.drawPath(a, arrow);
        float len = dp(9), dir = Math.signum(ey - sy);
        Path head = new Path();
        head.moveTo(ex - len * 0.75f, ey - dir * len);
        head.lineTo(ex, ey);
        head.lineTo(ex + len * 0.75f, ey - dir * len);
        canvas.drawPath(head, arrow);
    }

    private View listLine(int n, String text) {
        LinearLayout line = new LinearLayout(getContext());
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setPadding(0, dp(10), dp(8), 0);
        TextView num = text(12, look.textStrong(), look.p.onPrimary);
        num.setText(String.valueOf(n));
        num.setGravity(Gravity.CENTER);
        num.setBackground(look.round(look.p.primary, 11));
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(dp(22), dp(22));
        np.setMarginEnd(dp(10));
        np.topMargin = dp(1);
        line.addView(num, np);
        TextView t = text(14, look.text(), look.p.text);
        t.setLineSpacing(0, 1.1f);
        t.setText(boldBeforeColon(text));
        line.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return line;
    }

    /** "Save: keeps..." with the name in bold. */
    private CharSequence boldBeforeColon(String s) {
        int c = s.indexOf(':');
        if (c <= 0) {
            return s;
        }
        android.text.SpannableString out = new android.text.SpannableString(s);
        out.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, c, 0);
        return out;
    }

    private TextView text(float size, android.graphics.Typeface face, int color) {
        TextView t = new TextView(getContext());
        t.setTextSize(size);
        t.setTypeface(face);
        t.setTextColor(color);
        return t;
    }

    private int dp(float v) {
        return look.dp(v);
    }
}
