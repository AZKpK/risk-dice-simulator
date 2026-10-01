package com.example.riskdicesimulator;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * radial-gradient(ellipse at top, oklch(.24 0 0), oklch(.16 0 0)) with rounded-3xl and a border.
 * Chrome sizes "farthest-corner" ellipses with the farthest-side aspect ratio.
 */
final class ArenaBackground extends Drawable {
    private static final int[] COLORS = {0xFF1F1F1F, 0xFF1C1C1C, 0xFF191919, 0xFF161616, 0xFF131313, 0xFF101010, 0xFF0D0D0D};
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float radius, strokeWidth;
    private final RectF rect = new RectF();

    ArenaBackground(float density, int borderColor) {
        radius = 22 * density;
        strokeWidth = density;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(strokeWidth);
        stroke.setColor(borderColor);
    }

    @Override protected void onBoundsChange(Rect bounds) {
        float w = bounds.width(), h = Math.max(1, bounds.height());
        float rx = (float) (Math.sqrt(2) * w / 2), ry = (float) (Math.sqrt(2) * h);
        RadialGradient gradient = new RadialGradient(0, 0, rx, COLORS, null, Shader.TileMode.CLAMP);
        Matrix matrix = new Matrix();
        matrix.setScale(1, ry / rx);
        matrix.postTranslate(bounds.left + w / 2, bounds.top);
        gradient.setLocalMatrix(matrix);
        fill.setShader(gradient);
    }

    @Override public void draw(Canvas canvas) {
        rect.set(getBounds());
        canvas.drawRoundRect(rect, radius, radius, fill);
        rect.inset(strokeWidth / 2, strokeWidth / 2);
        canvas.drawRoundRect(rect, radius - strokeWidth / 2, radius - strokeWidth / 2, stroke);
    }

    @Override public void setAlpha(int alpha) { fill.setAlpha(alpha); stroke.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter filter) { fill.setColorFilter(filter); stroke.setColorFilter(filter); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
