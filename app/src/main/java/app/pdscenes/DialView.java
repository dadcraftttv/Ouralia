package app.pdscenes;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * The Intensity dial: a 270 degree ring, open at the bottom, filled from bottom-left as Intensity rises, in the
 * accent's two-colour glow. The large one (in the player sheet) can be dragged around the ring and shows the number
 * in its opening; the small one (in the docked player) only shows. It draws only when the value changes.
 */
final class DialView extends View {
    interface Listener {
        /** {@code done} is true when the finger lifts. */
        void onChange(float value, boolean done);
    }

    private static final float START = 135f, SWEEP = 270f;

    private final Styler look;
    private final boolean large;
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint number = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint caption = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF ring = new RectF();
    private final String captionText;
    private float value;
    private boolean active = true;
    private boolean dragging;
    private Listener listener;

    DialView(Context context, Styler look, boolean large, String captionText) {
        super(context);
        this.look = look;
        this.large = large;
        this.captionText = captionText;
        float stroke = look.dp(large ? 8 : 4);
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(stroke);
        track.setStrokeCap(Paint.Cap.ROUND);
        track.setColor(look.p.s2);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeWidth(stroke);
        arc.setStrokeCap(Paint.Cap.ROUND);
        number.setTypeface(look.value());
        number.setTextSize(look.dp(22));
        number.setColor(look.p.text);
        number.setTextAlign(Paint.Align.CENTER);
        caption.setTypeface(look.value());
        caption.setTextSize(look.dp(10));
        caption.setLetterSpacing(0.14f);
        caption.setColor(look.p.muted);
        caption.setTextAlign(Paint.Align.CENTER);
        setImportantForAccessibility(large ? IMPORTANT_FOR_ACCESSIBILITY_YES : IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    /** 0..1. Ignored while the finger is on the dial (the finger wins). */
    void setValue(float v) {
        if (dragging) {
            return;
        }
        v = Math.max(0f, Math.min(1f, v));
        if (v != value) {
            value = v;
            invalidate();
        }
    }

    /** A scene has no Intensity: the ring then shows empty and doesn't take touches. */
    void setActive(boolean active) {
        if (active != this.active) {
            this.active = active;
            invalidate();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        float half = track.getStrokeWidth() / 2 + look.dp(large ? 4 : 1);
        ring.set(half, half, w - half, h - half);
        // bottom-left (the aurora colour) to top-right (the accent), as in the mockups
        arc.setShader(new LinearGradient(0, h, w, 0, look.p.aurora, look.p.primary, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawArc(ring, START, SWEEP, false, track);
        if (active && value > 0.002f) {
            canvas.drawArc(ring, START, SWEEP * value, false, arc);
        }
        if (large && active) {
            float cx = getWidth() / 2f, base = getHeight() - look.dp(10);
            canvas.drawText(String.valueOf(Math.round(value * 100)), cx, base - look.dp(14), number);
            canvas.drawText(captionText, cx, base, caption);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!large || !active || !isEnabled()) {
            return false;
        }
        float dx = e.getX() - getWidth() / 2f, dy = e.getY() - getHeight() / 2f;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // only the ring itself: the middle belongs to the Start button, which sits on top
                if (Math.hypot(dx, dy) < ring.width() / 2 * 0.6) {
                    return false;
                }
                dragging = true;
                getParent().requestDisallowInterceptTouchEvent(true);   // the sheet mustn't scroll away under it
                follow(dx, dy, false);
                return true;
            case MotionEvent.ACTION_MOVE:
                follow(dx, dy, false);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                follow(dx, dy, true);
                dragging = false;
                return true;
            default:
                return true;
        }
    }

    /** Sets the value from the finger's angle; in the opening at the bottom it holds at the nearer end. */
    private void follow(float dx, float dy, boolean done) {
        float a = (float) Math.toDegrees(Math.atan2(dy, dx));
        float along = ((a - START) % 360f + 360f) % 360f;
        float v = along <= SWEEP ? along / SWEEP : (along > SWEEP + 45f ? 0f : 1f);
        if (Math.round(v * 20) != Math.round(value * 20)) {   // a fine tick every 5 steps, felt under the finger
            performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
        }
        value = v;
        invalidate();
        if (listener != null) {
            listener.onChange(v, done);
        }
    }

    // Read and changed by screen readers as a slider, in steps of 5.
    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.SeekBar");
        info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(
                AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT, 0, 100, Math.round(value * 100)));
        if (active) {
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
        }
    }

    @Override
    public boolean performAccessibilityAction(int action, Bundle args) {
        if (active && (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                || action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) {
            float v = Math.max(0f, Math.min(1f, value + (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD ? 0.05f : -0.05f)));
            value = v;
            invalidate();
            if (listener != null) {
                listener.onChange(v, true);
            }
            return true;
        }
        return super.performAccessibilityAction(action, args);
    }
}
