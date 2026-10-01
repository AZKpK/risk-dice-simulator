package com.example.riskdicesimulator;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatTextView;

/** TextView with CSS line-height (see {@link CssLineHeight}). */
public class CssTextView extends AppCompatTextView {
    private final CssLineHeight line;

    public CssTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        line = new CssLineHeight(this, attrs);
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        line.prepare();
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int height = line.height(heightMeasureSpec);
        if (height >= 0) setMeasuredDimension(getMeasuredWidth(), resolveSize(height, heightMeasureSpec));
    }

    @Override protected void onDraw(Canvas canvas) {
        line.beginDraw(canvas);
        super.onDraw(canvas);
        line.endDraw(canvas);
    }
}
