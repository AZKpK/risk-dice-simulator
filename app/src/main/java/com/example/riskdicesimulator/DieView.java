package com.example.riskdicesimulator;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;

/**
 * The v0 Die: a gradient body with inset/outer box shadows and white pips, or a dashed
 * placeholder without a value. New rolls play the CSS "tumble" keyframes.
 */
public class DieView extends View {
    public enum State { IDLE, WON, LOST, UNUSED }

    private static final int[] PIPS_1 = {4}, PIPS_2 = {2, 6}, PIPS_3 = {2, 4, 6}, PIPS_4 = {0, 2, 6, 8},
            PIPS_5 = {0, 2, 4, 6, 8}, PIPS_6 = {0, 2, 3, 5, 6, 8};
    private static final int[][] PIPS = {null, PIPS_1, PIPS_2, PIPS_3, PIPS_4, PIPS_5, PIPS_6};
    // oklch(.63 .22 25) -> oklch(.42 .17 25) and oklch(.62 .19 255) -> oklch(.4 .16 260), sampled in oklab.
    private static final int[] ATTACKER_GRADIENT = {0xFFF1383E, 0xFFE13037, 0xFFD12830, 0xFFC22029, 0xFFB21622, 0xFFA30C1B, 0xFF940015};
    private static final int[] DEFENDER_GRADIENT = {0xFF1D84F5, 0xFF1879E5, 0xFF136DD6, 0xFF0E62C7, 0xFF0956B8, 0xFF054BAA, 0xFF03409C};
    private static final PathInterpolator EASE = new PathInterpolator(.2f, .8f, .3f, 1.2f);

    private final float dp;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint layerPaint = new Paint();
    private final Path path = new Path(), frame = new Path();
    private final RectF rect = new RectF(), hole = new RectF();
    private final BlurMaskFilter glowBlur, darkInsetBlur, lightInsetBlur, pipBlur;
    private final DashPathEffect dashes;
    private final float[] highlightRadii;
    private final ColorMatrixColorFilter lostFilter = new ColorMatrixColorFilter(grayscale(.4f)),
            unusedFilter = new ColorMatrixColorFilter(grayscale(1f));
    private LinearGradient bodyShader, highlightShader;
    private boolean shaderAttacker;
    private boolean attacker = true;
    private int value;
    private State state = State.IDLE;
    private float animationAlpha = 1f;
    private ValueAnimator tumble;

    public DieView(Context context) { this(context, null); }

    public DieView(Context context, AttributeSet attrs) {
        super(context, attrs);
        dp = getResources().getDisplayMetrics().density;
        glowBlur = blur(8);
        darkInsetBlur = blur(4);
        lightInsetBlur = blur(2);
        pipBlur = blur(1);
        dashes = new DashPathEffect(new float[]{6 * dp, 6 * dp}, 0);
        float corner = 14 * dp;
        highlightRadii = new float[]{corner, corner, corner, corner, 0, 0, 0, 0};
        // Blur mask filters need software rendering before API 28.
        if (Build.VERSION.SDK_INT < 28) setLayerType(LAYER_TYPE_SOFTWARE, null);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void bind(boolean attacker, int value, State state) {
        this.attacker = attacker;
        this.value = value;
        this.state = state;
        if (value > 0) {
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
            String side = getContext().getString(attacker ? R.string.side_attacker : R.string.side_defender);
            int suffix = state == State.LOST ? R.string.die_lost : state == State.WON ? R.string.die_won
                    : state == State.UNUSED ? R.string.die_unused : 0;
            setContentDescription(getContext().getString(R.string.die_description, side, value,
                    suffix == 0 ? "" : getContext().getString(suffix)));
        } else {
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            setContentDescription(null);
        }
        if (tumble == null || !tumble.isRunning()) animationAlpha = baseAlpha();
        if (attacker != shaderAttacker) bodyShader = null;
        invalidate();
    }

    private float baseAlpha() {
        if (value == 0) return 1f;
        return state == State.LOST ? .35f : state == State.UNUSED ? .25f : 1f;
    }

    /** CSS: tumble .55s cubic-bezier(.2,.8,.3,1.2), per-keyframe easing, fill-mode backwards. */
    public void tumble(long delay) {
        if (tumble != null) tumble.cancel();
        if (value == 0) return;
        applyTumble(0f);
        tumble = ValueAnimator.ofFloat(0f, 1f);
        tumble.setDuration(550);
        tumble.setStartDelay(delay);
        tumble.setInterpolator(new LinearInterpolator());
        tumble.addUpdateListener(animation -> applyTumble((float) animation.getAnimatedValue()));
        tumble.start();
    }

    private void applyTumble(float t) {
        float y, rotation, scale, alpha;
        if (t < .6f) {
            float e = EASE.getInterpolation(t / .6f);
            y = lerp(-18, 3, e); rotation = lerp(-220, 12, e); scale = lerp(.6f, 1.05f, e); alpha = lerp(0, 1, e);
        } else {
            float e = EASE.getInterpolation((t - .6f) / .4f);
            y = lerp(3, 0, e); rotation = lerp(12, 0, e); scale = lerp(1.05f, 1, e); alpha = lerp(1, baseAlpha(), e);
        }
        setTranslationY(y * dp);
        setRotation(rotation);
        setScaleX(scale);
        setScaleY(scale);
        animationAlpha = Math.max(0, Math.min(1, alpha));
        invalidate();
    }

    private static float lerp(float from, float to, float t) { return from + (to - from) * t; }

    @Override protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight(), radius = 18 * dp;
        if (value == 0) {
            drawPlaceholder(canvas, w, h, radius);
            return;
        }
        // filter + opacity apply to the whole die, shadows included.
        layerPaint.setColorFilter(state == State.LOST ? lostFilter : state == State.UNUSED ? unusedFilter : null);
        layerPaint.setAlpha(Math.round(255 * animationAlpha));
        float bleed = 32 * dp;
        canvas.saveLayer(-bleed, -bleed, w + bleed, h + bleed, layerPaint);

        boolean shadows = state != State.UNUSED;
        if (shadows) {
            // 0 3px 0 0 var(--color-*-deep)
            paint.setColor(getContext().getColor(attacker ? R.color.attacker_deep : R.color.defender_deep));
            rect.set(0, 3 * dp, w, h + 3 * dp);
            canvas.drawRoundRect(rect, radius, radius, paint);
            // 0 8px 16px -4px rgba(220,38,38,.45) / rgba(37,99,235,.45)
            paint.setColor(attacker ? 0x73DC2626 : 0x732563EB);
            paint.setMaskFilter(glowBlur);
            rect.set(4 * dp, 12 * dp, w - 4 * dp, h + 4 * dp);
            canvas.drawRoundRect(rect, radius - 4 * dp, radius - 4 * dp, paint);
            paint.setMaskFilter(null);
        }

        if (bodyShader == null) createShaders(w, h);
        paint.setShader(bodyShader);
        rect.set(0, 0, w, h);
        path.reset();
        path.addRoundRect(rect, radius, radius, Path.Direction.CW);
        canvas.drawPath(path, paint);
        paint.setShader(null);

        if (shadows) {
            canvas.save();
            canvas.clipPath(path);
            // inset -3px -5px 8px rgba(0,0,0,.45), then inset 2px 3px 4px rgba(255,255,255,.35) on top.
            insetShadow(canvas, rect, radius, -3, -5, darkInsetBlur, 0x73000000);
            insetShadow(canvas, rect, radius, 2, 3, lightInsetBlur, 0x59FFFFFF);
            canvas.restore();
        }

        drawPips(canvas, w, h);

        // Highlight: absolute inset-x-2 top-1 h-1/3 rounded-t-xl bg-gradient-to-b from-white/25.
        float top = 4 * dp, bottom = top + h / 3;
        paint.setShader(highlightShader);
        rect.set(8 * dp, top, w - 8 * dp, bottom);
        path.reset();
        path.addRoundRect(rect, highlightRadii, Path.Direction.CW);
        canvas.drawPath(path, paint);
        paint.setShader(null);

        canvas.restore();
    }

    private void drawPlaceholder(Canvas canvas, float w, float h, float radius) {
        // border-2 border-dashed border-*/40
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2 * dp);
        paint.setPathEffect(dashes);
        paint.setColor(getContext().getColor(attacker ? R.color.attacker_40 : R.color.defender_40));
        rect.set(dp, dp, w - dp, h - dp);
        canvas.drawRoundRect(rect, radius - dp, radius - dp, paint);
        paint.setPathEffect(null);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawPips(Canvas canvas, float w, float h) {
        float padding = 10 * dp, cellW = (w - 2 * padding) / 3, cellH = (h - 2 * padding) / 3;
        float r = getResources().getDimension(R.dimen.pip_size) / 2;
        for (int index : PIPS[value]) {
            float cx = padding + cellW * (index % 3 + .5f), cy = padding + cellH * (index / 3 + .5f);
            // 0 1px 0 rgba(255,255,255,.3)
            paint.setColor(0x4DFFFFFF);
            canvas.drawCircle(cx, cy + dp, r, paint);
            paint.setColor(0xFFFFFFFF);
            canvas.drawCircle(cx, cy, r, paint);
            // inset 0 1px 2px rgba(0,0,0,.45)
            if (state != State.UNUSED) {
                canvas.save();
                path.reset();
                path.addCircle(cx, cy, r, Path.Direction.CW);
                canvas.clipPath(path);
                frame.reset();
                frame.setFillType(Path.FillType.EVEN_ODD);
                frame.addRect(cx - 3 * r, cy - 3 * r, cx + 3 * r, cy + 3 * r, Path.Direction.CW);
                frame.addCircle(cx, cy + dp, r, Path.Direction.CW);
                paint.setColor(0x73000000);
                paint.setMaskFilter(pipBlur);
                canvas.drawPath(frame, paint);
                paint.setMaskFilter(null);
                canvas.restore();
            }
        }
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        bodyShader = null;
    }

    private void createShaders(float w, float h) {
        // linear-gradient(145deg, color 0%, deep 100%)
        double angle = Math.toRadians(145);
        float dx = (float) Math.sin(angle), dy = (float) -Math.cos(angle);
        float half = (Math.abs(w * dx) + Math.abs(h * dy)) / 2;
        bodyShader = new LinearGradient(w / 2 - dx * half, h / 2 - dy * half, w / 2 + dx * half, h / 2 + dy * half,
                attacker ? ATTACKER_GRADIENT : DEFENDER_GRADIENT, null, Shader.TileMode.CLAMP);
        float top = 4 * dp;
        highlightShader = new LinearGradient(0, top, 0, top + h / 3, 0x40FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP);
        shaderAttacker = attacker;
    }

    private void insetShadow(Canvas canvas, RectF body, float radius, float x, float y, BlurMaskFilter blur, int color) {
        frame.reset();
        frame.setFillType(Path.FillType.EVEN_ODD);
        frame.addRect(body.left - 40 * dp, body.top - 40 * dp, body.right + 40 * dp, body.bottom + 40 * dp, Path.Direction.CW);
        hole.set(body);
        hole.offset(x * dp, y * dp);
        frame.addRoundRect(hole, radius, radius, Path.Direction.CW);
        paint.setColor(color);
        paint.setMaskFilter(blur);
        canvas.drawPath(frame, paint);
        paint.setMaskFilter(null);
    }

    /** CSS blur radius b has sigma b/2; Skia converts a mask radius r to sigma 0.57735r + 0.5. */
    private BlurMaskFilter blur(float sigmaDp) {
        return new BlurMaskFilter(Math.max(.5f, (sigmaDp * dp - .5f) / .57735f), BlurMaskFilter.Blur.NORMAL);
    }

    /** CSS grayscale(amount). */
    private static ColorMatrix grayscale(float amount) {
        float g = 1 - amount;
        return new ColorMatrix(new float[]{
                .2126f + .7874f * g, .7152f - .7152f * g, .0722f - .0722f * g, 0, 0,
                .2126f - .2126f * g, .7152f + .2848f * g, .0722f - .0722f * g, 0, 0,
                .2126f - .2126f * g, .7152f - .7152f * g, .0722f + .9278f * g, 0, 0,
                0, 0, 0, 1, 0});
    }
}
