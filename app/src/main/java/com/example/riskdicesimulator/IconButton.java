package com.example.riskdicesimulator;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.Button;
import android.widget.LinearLayout;

/** shadcn Button: centered icon + label, 50% opacity when disabled, 1px press offset. */
public class IconButton extends LinearLayout {
    public IconButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        setAlpha(enabled ? 1f : .5f);
    }

    @Override public void setPressed(boolean pressed) {
        super.setPressed(pressed);
        setTranslationY(pressed ? getResources().getDisplayMetrics().density : 0);
    }

    @Override public CharSequence getAccessibilityClassName() { return Button.class.getName(); }
}
