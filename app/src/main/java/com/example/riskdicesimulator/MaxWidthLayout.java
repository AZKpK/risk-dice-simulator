package com.example.riskdicesimulator;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;

/** Vertical column capped at Tailwind max-w-md (448px); the parent centers it. */
public class MaxWidthLayout extends LinearLayout {
    private final int maxWidth;

    public MaxWidthLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        maxWidth = Math.round(448 * getResources().getDisplayMetrics().density);
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (width > maxWidth) widthMeasureSpec = MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.EXACTLY);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
