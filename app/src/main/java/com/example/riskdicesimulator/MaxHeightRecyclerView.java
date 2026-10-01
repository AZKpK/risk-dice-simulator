package com.example.riskdicesimulator;

import android.content.Context;
import android.util.AttributeSet;

import androidx.recyclerview.widget.RecyclerView;

/** Wraps its content up to Tailwind max-h-96 (384px), then scrolls. */
public class MaxHeightRecyclerView extends RecyclerView {
    private final int maxHeight;

    public MaxHeightRecyclerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        maxHeight = Math.round(384 * getResources().getDisplayMetrics().density);
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST));
    }
}
