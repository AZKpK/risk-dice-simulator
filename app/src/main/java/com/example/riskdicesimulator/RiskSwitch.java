package com.example.riskdicesimulator;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.widget.CompoundButton;
import android.widget.Switch;

/** shadcn Switch (default size): 32 x 18.4 track, 16px thumb. */
public class RiskSwitch extends CompoundButton {
    private final float dp;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private float position;
    private ValueAnimator animator;

    public RiskSwitch(Context context, AttributeSet attrs) {
        super(context, attrs);
        dp = getResources().getDisplayMetrics().density;
        setButtonDrawable(null);
        setBackground(null);
        setMinWidth(0);
        setMinHeight(0);
        setPadding(0, 0, 0, 0);
        position = isChecked() ? 1 : 0;
    }

    @Override public void setChecked(boolean checked) {
        boolean changed = checked != isChecked();
        super.setChecked(checked);
        if (dp == 0) return; // Called from the superclass constructor.
        if (animator != null) animator.cancel();
        if (!changed || !isLaidOut()) {
            position = checked ? 1 : 0;
            invalidate();
            return;
        }
        animator = ValueAnimator.ofFloat(position, checked ? 1 : 0);
        animator.setDuration(150);
        animator.setInterpolator(new android.view.animation.PathInterpolator(.4f, 0, .2f, 1));
        animator.addUpdateListener(a -> { position = (float) a.getAnimatedValue(); invalidate(); });
        animator.start();
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(Math.round(32 * dp), Math.round(18.4f * dp));
    }

    @Override protected void onDraw(Canvas canvas) {
        float h = getHeight();
        rect.set(0, 0, getWidth(), h);
        paint.setColor(getContext().getColor(isChecked() ? R.color.primary : R.color.input_80));
        canvas.drawRoundRect(rect, h / 2, h / 2, paint);
        float size = 16 * dp, left = dp + position * (size - 2 * dp);
        paint.setColor(getContext().getColor(isChecked() ? R.color.primary_foreground : R.color.foreground));
        canvas.drawCircle(left + size / 2, h / 2, size / 2, paint);
    }

    @Override public CharSequence getAccessibilityClassName() { return Switch.class.getName(); }
}
