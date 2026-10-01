package com.example.riskdicesimulator;

import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Layout;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

/**
 * Emulates CSS line-height: each line box is exactly {@code lineHeight} tall and the
 * font's content area is centered in it, even when that box is smaller than the font.
 */
final class CssLineHeight {
    private final TextView view;
    private int lineHeight;
    private float halfLeading;

    CssLineHeight(TextView view, AttributeSet attrs) {
        this.view = view;
        TypedArray values = view.getContext().obtainStyledAttributes(attrs, R.styleable.CssText);
        lineHeight = values.getDimensionPixelSize(R.styleable.CssText_cssLineHeight, 0);
        values.recycle();
    }

    /** Call before TextView.onMeasure; the spacing depends on the current font. */
    void prepare() {
        if (lineHeight <= 0) return;
        Paint.FontMetricsInt metrics = view.getPaint().getFontMetricsInt();
        int natural = metrics.descent - metrics.ascent;
        halfLeading = (lineHeight - natural) / 2f;
        if (view.getLineSpacingExtra() != lineHeight - natural || view.getLineSpacingMultiplier() != 1f)
            view.setLineSpacing(lineHeight - natural, 1f);
    }

    /** Returns the measured height, or -1 to keep the TextView's own measurement. */
    int height(int heightMeasureSpec) {
        if (lineHeight <= 0 || View.MeasureSpec.getMode(heightMeasureSpec) == View.MeasureSpec.EXACTLY) return -1;
        Layout layout = view.getLayout();
        int lines = Math.max(1, layout == null ? 1 : layout.getLineCount());
        if (view.getMaxLines() > 0) lines = Math.min(lines, view.getMaxLines());
        return lines * lineHeight + view.getCompoundPaddingTop() + view.getCompoundPaddingBottom();
    }

    void beginDraw(Canvas canvas) {
        canvas.save();
        if (lineHeight > 0) canvas.translate(0, halfLeading);
    }

    void endDraw(Canvas canvas) { canvas.restore(); }
}
