package com.example.riskdicesimulator;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.core.content.res.ResourcesCompat;

/**
 * Compact two-row dice grid (font-mono text-sm leading-tight, 1ch columns, gap-x-2).
 * Bold dice won their pair, dimmed dice lost, faint dice had no pair.
 */
public class DiceStackView extends View {
    private final float dp, lineHeight, charWidth, gap;
    private final Paint regular = new Paint(Paint.ANTI_ALIAS_FLAG), bold = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int attackerColor, defenderColor;
    private int[] attackerDice = new int[0], defenderDice = new int[0];

    public DiceStackView(Context context, AttributeSet attrs) {
        super(context, attrs);
        dp = getResources().getDisplayMetrics().density;
        float size = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 14, getResources().getDisplayMetrics());
        Typeface mono = ResourcesCompat.getFont(context, R.font.geist_mono_regular);
        Typeface monoBold = ResourcesCompat.getFont(context, R.font.geist_mono_bold);
        regular.setTypeface(mono);
        bold.setTypeface(monoBold);
        regular.setTextSize(size);
        bold.setTextSize(size);
        lineHeight = size * 1.25f;
        charWidth = regular.measureText("0");
        gap = 8 * dp;
        attackerColor = context.getColor(R.color.attacker);
        defenderColor = context.getColor(R.color.defender);
    }

    public void bind(int[] attacker, int[] defender) {
        attackerDice = attacker;
        defenderDice = defender;
        setContentDescription(getContext().getString(R.string.dice_stack_description, join(attacker), join(defender)));
        requestLayout();
        invalidate();
    }

    private static String join(int[] dice) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < dice.length; i++) text.append(i == 0 ? "" : ", ").append(dice[i]);
        return text.toString();
    }

    private int columns() { return Math.max(attackerDice.length, defenderDice.length); }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int columns = columns();
        int width = (int) Math.ceil(columns * charWidth + Math.max(0, columns - 1) * gap);
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize((int) Math.ceil(2 * lineHeight), heightMeasureSpec));
    }

    @Override protected void onDraw(Canvas canvas) {
        drawRow(canvas, attackerDice, defenderDice, true, 0);
        drawRow(canvas, defenderDice, attackerDice, false, lineHeight);
    }

    private void drawRow(Canvas canvas, int[] own, int[] other, boolean attacker, float top) {
        Paint.FontMetrics metrics = regular.getFontMetrics();
        float baseline = top + (lineHeight - (metrics.descent - metrics.ascent)) / 2 - metrics.ascent;
        for (int i = 0; i < own.length; i++) {
            float alpha;
            Paint paint = regular;
            if (i >= other.length) alpha = .3f;
            else {
                boolean attackerWins = attacker ? own[i] > other[i] : other[i] > own[i];
                boolean won = attacker == attackerWins;
                if (won) paint = bold;
                alpha = won ? 1f : .6f;
            }
            paint.setColor(attacker ? attackerColor : defenderColor);
            paint.setAlpha(Math.round(255 * alpha));
            canvas.drawText(String.valueOf(own[i]), i * (charWidth + gap), baseline, paint);
        }
    }
}
