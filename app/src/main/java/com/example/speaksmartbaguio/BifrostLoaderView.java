
package com.example.speaksmartbaguio;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

public class BifrostLoaderView extends View {

    private final Paint bridgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path bridgePath = new Path();
    private final PathMeasure pathMeasure = new PathMeasure();

    private ValueAnimator animator;

    private float animationProgress = 0f;

    private float[] position = new float[2];
    private float[] tangent = new float[2];

    public BifrostLoaderView(Context context) {
        super(context);
        initialize();
    }

    public BifrostLoaderView(
            Context context,
            AttributeSet attrs
    ) {
        super(context, attrs);
        initialize();
    }

    public BifrostLoaderView(
            Context context,
            AttributeSet attrs,
            int defStyleAttr
    ) {
        super(context, attrs, defStyleAttr);
        initialize();
    }

    private void initialize() {

        setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        bridgePaint.setStyle(Paint.Style.STROKE);
        bridgePaint.setStrokeWidth(dp(3));
        bridgePaint.setStrokeCap(Paint.Cap.ROUND);
        bridgePaint.setStrokeJoin(Paint.Join.ROUND);

        bridgePaint.setColor(Color.rgb(13, 163, 150));
        bridgePaint.setShadowLayer(
                dp(7),
                0,
                0,
                Color.rgb(13, 214, 192)
        );

        glowPaint.setStyle(Paint.Style.FILL);
        glowPaint.setColor(Color.rgb(13, 214, 192));

        lightPaint.setStyle(Paint.Style.FILL);
        lightPaint.setColor(Color.WHITE);
        lightPaint.setShadowLayer(
                dp(10),
                0,
                0,
                Color.rgb(13, 214, 192)
        );
    }

    private float dp(float value) {
        return value * getResources()
                .getDisplayMetrics().density;
    }

    private void createBridgePath() {

        float width = getWidth();
        float height = getHeight();

        if (width <= 0 || height <= 0) {
            return;
        }

        bridgePath.reset();

        float startX = dp(12);
        float endX = width - dp(12);

        float baseY = height - dp(12);
        float archY = dp(8);

        bridgePath.moveTo(startX, baseY);

        bridgePath.quadTo(
                width / 2f,
                archY,
                endX,
                baseY
        );

        pathMeasure.setPath(bridgePath, false);
    }

    @Override
    protected void onSizeChanged(
            int w,
            int h,
            int oldw,
            int oldh
    ) {
        super.onSizeChanged(w, h, oldw, oldh);
        createBridgePath();
    }

    @Override
    protected void onDraw(Canvas canvas) {

        super.onDraw(canvas);

        if (pathMeasure.getLength() <= 0) {
            createBridgePath();
        }

        // Draw the glowing Bifrost bridge.

        canvas.drawPath(
                bridgePath,
                bridgePaint
        );

        // Draw the traveling light.

        float distance =
                animationProgress * pathMeasure.getLength();

        pathMeasure.getPosTan(
                distance,
                position,
                tangent
        );

        float x = position[0];
        float y = position[1];

        // Outer glow.

        glowPaint.setShader(
                new RadialGradient(
                        x,
                        y,
                        dp(13),
                        new int[] {
                                Color.argb(110, 13, 214, 192),
                                Color.argb(35, 13, 214, 192),
                                Color.TRANSPARENT
                        },
                        null,
                        Shader.TileMode.CLAMP
                )
        );

        canvas.drawCircle(
                x,
                y,
                dp(13),
                glowPaint
        );

        // Bright moving light.

        canvas.drawCircle(
                x,
                y,
                dp(4),
                lightPaint
        );
    }

    private void startAnimation() {

        if (animator != null && animator.isRunning()) {
            return;
        }

        animator = ValueAnimator.ofFloat(0f, 1f);

        animator.setDuration(1800);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());

        animator.addUpdateListener(animation -> {

            animationProgress =
                    (float) animation.getAnimatedValue();

            invalidate();
        });

        animator.start();
    }

    private void stopAnimation() {

        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopAnimation();
        super.onDetachedFromWindow();
    }
}
