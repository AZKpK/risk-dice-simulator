package com.example.riskdicesimulator;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.PathInterpolator;

/** Fully rounded progress track; the fill is clipped to it (overflow-hidden rounded-full). */
public class BarView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path clip = new Path();
    private int trackColor, fillColor;
    private float fraction;
    private boolean roundedFill;
    private ValueAnimator animator;

    public BarView(Context context, AttributeSet attrs) { super(context, attrs); }

    public void setColors(int track, int fill, boolean roundedFill) {
        trackColor = track;
        fillColor = fill;
        this.roundedFill = roundedFill;
        invalidate();
    }

    /** @param durationMs 0 for no transition (transition-[width] duration-500 otherwise). */
    public void setFraction(float value, long durationMs) {
        value = Math.max(0, Math.min(1, value));
        if (animator != null) animator.cancel();
        if (durationMs <= 0 || !isLaidOut()) {
            fraction = value;
            invalidate();
            return;
        }
        animator = ValueAnimator.ofFloat(fraction, value);
        animator.setDuration(durationMs);
        animator.setInterpolator(new PathInterpolator(.4f, 0, .2f, 1));
        animator.addUpdateListener(a -> { fraction = (float) a.getAnimatedValue(); invalidate(); });
        animator.start();
    }

    @Override protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        rect.set(0, 0, w, h);
        clip.reset();
        clip.addRoundRect(rect, h / 2, h / 2, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clip);
        canvas.drawColor(trackColor);
        paint.setColor(fillColor);
        rect.set(0, 0, w * fraction, h);
        if (roundedFill) canvas.drawRoundRect(rect, h / 2, h / 2, paint);
        else canvas.drawRect(rect, paint);
        canvas.restore();
    }
}
