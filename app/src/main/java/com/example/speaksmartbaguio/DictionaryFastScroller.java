package com.example.speaksmartbaguio;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

public class DictionaryFastScroller extends View {

    private final Paint thumbPaint =
            new Paint(Paint.ANTI_ALIAS_FLAG);

    private RecyclerView recyclerView;

    private RecyclerView.OnScrollListener scrollListener;

    private Runnable onReachBottomListener;

    private boolean dragging = false;
    private boolean thumbVisible = false;

    private float thumbTop = 0f;
    private float thumbHeight = 0f;
    private float lastTouchY = 0f;
    private float dragOffsetY = 0f;

    private final float thumbWidth;
    private final float minimumThumbHeight;
    private final float horizontalInset;

    private int thumbColor = 0xFF06B8A8;
    private int activeThumbColor = 0xFF08D4C2;

    public DictionaryFastScroller(Context context) {
        this(context, null);
    }

    public DictionaryFastScroller(
            Context context,
            @Nullable AttributeSet attrs
    ) {
        this(context, attrs, 0);
    }

    public DictionaryFastScroller(
            Context context,
            @Nullable AttributeSet attrs,
            int defStyleAttr
    ) {
        super(context, attrs, defStyleAttr);

        thumbWidth = dp(6);
        minimumThumbHeight = dp(36);
        horizontalInset = dp(8);

        setClickable(true);
        setFocusable(true);

        thumbPaint.setColor(thumbColor);
        thumbPaint.setStyle(Paint.Style.FILL);
    }

    private float dp(float value) {
        return value * getResources()
                .getDisplayMetrics().density;
    }

    /**
     * Connects or disconnects the custom scrollbar
     * from the dictionary RecyclerView.
     */
    public void attachToRecyclerView(
            @Nullable RecyclerView newRecyclerView
    ) {
        // Remove the previous listener before reconnecting.
        if (recyclerView != null && scrollListener != null) {
            recyclerView.removeOnScrollListener(scrollListener);
        }

        recyclerView = newRecyclerView;
        scrollListener = null;

        dragging = false;
        thumbVisible = false;
        thumbTop = 0f;
        thumbHeight = 0f;

        if (recyclerView != null) {
            scrollListener = new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(
                        RecyclerView recyclerView,
                        int dx,
                        int dy
                ) {
                    invalidate();
                }

                @Override
                public void onScrollStateChanged(
                        RecyclerView recyclerView,
                        int newState
                ) {
                    invalidate();
                }
            };

            recyclerView.addOnScrollListener(scrollListener);
        }

        invalidate();
    }

    /**
     * Called when the user drags the thumb near the bottom.
     * The Fragment can use this to load the next API page.
     */
    public void setOnReachBottomListener(
            @Nullable Runnable listener
    ) {
        onReachBottomListener = listener;
    }

    /**
     * Allows the thumb colors to be customized.
     * The track color parameter is retained for compatibility,
     * but no track is drawn.
     */
    public void setScrollbarColors(
            int trackColor,
            int thumbColor,
            int activeThumbColor
    ) {
        this.thumbColor = thumbColor;
        this.activeThumbColor = activeThumbColor;

        thumbPaint.setColor(thumbColor);

        invalidate();
    }

    private int getScrollRange() {
        if (recyclerView == null) {
            return 0;
        }

        return recyclerView.computeVerticalScrollRange();
    }

    private int getScrollExtent() {
        if (recyclerView == null) {
            return 0;
        }

        return recyclerView.computeVerticalScrollExtent();
    }

    private int getScrollOffset() {
        if (recyclerView == null) {
            return 0;
        }

        return recyclerView.computeVerticalScrollOffset();
    }

    private boolean canScroll() {
        return recyclerView != null
                && getHeight() > 0
                && getScrollRange() > getScrollExtent();
    }

    private float getAvailableTravel() {
        return Math.max(
                0f,
                getHeight() - thumbHeight
        );
    }

    private void updateThumbPosition() {
        thumbVisible = canScroll();

        if (!thumbVisible) {
            thumbTop = 0f;
            thumbHeight = 0f;
            return;
        }

        int range = getScrollRange();
        int extent = getScrollExtent();
        int offset = getScrollOffset();

        if (range <= 0 || extent <= 0) {
            thumbVisible = false;
            thumbTop = 0f;
            thumbHeight = 0f;
            return;
        }

        float proportionalHeight =
                getHeight() * ((float) extent / range);

        thumbHeight = Math.max(
                minimumThumbHeight,
                proportionalHeight
        );

        thumbHeight = Math.min(
                getHeight(),
                thumbHeight
        );

        float travel = getAvailableTravel();

        int maximumOffset = Math.max(
                1,
                range - extent
        );

        float progress = Math.max(
                0f,
                Math.min(
                        1f,
                        (float) offset / maximumOffset
                )
        );

        thumbTop = travel * progress;

        thumbTop = Math.max(
                0f,
                Math.min(travel, thumbTop)
        );
    }

    /**
     * Draws ONLY the teal thumb.
     * No vertical track line is drawn.
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        updateThumbPosition();

        if (!thumbVisible) {
            return;
        }

        float centerX = getWidth() - horizontalInset;

        float thumbLeft = centerX - thumbWidth / 2f;
        float thumbRight = centerX + thumbWidth / 2f;

        RectF thumbRect = new RectF(
                thumbLeft,
                thumbTop,
                thumbRight,
                thumbTop + thumbHeight
        );

        thumbPaint.setColor(
                dragging ? activeThumbColor : thumbColor
        );

        canvas.drawRoundRect(
                thumbRect,
                thumbWidth / 2f,
                thumbWidth / 2f,
                thumbPaint
        );
    }

    private float clampProgress(float progress) {
        return Math.max(
                0f,
                Math.min(1f, progress)
        );
    }

    private void scrollToProgress(float progress) {
        if (recyclerView == null || !canScroll()) {
            return;
        }

        progress = clampProgress(progress);

        int range = getScrollRange();
        int extent = getScrollExtent();

        int maximumOffset = Math.max(
                0,
                range - extent
        );

        int targetOffset = Math.round(
                progress * maximumOffset
        );

        int currentOffset = getScrollOffset();

        recyclerView.scrollBy(
                0,
                targetOffset - currentOffset
        );

        invalidate();

        if (progress >= 0.96f
                && onReachBottomListener != null) {
            onReachBottomListener.run();
        }
    }

    private float progressForTouch(float touchY) {
        updateThumbPosition();

        float travel = getAvailableTravel();

        if (travel <= 0f) {
            return 0f;
        }

        float desiredTop = touchY - dragOffsetY;

        return clampProgress(desiredTop / travel);
    }

    private boolean isTouchInsideThumb(float x, float y) {
        updateThumbPosition();

        if (!thumbVisible) {
            return false;
        }

        float centerX = getWidth() - horizontalInset;

        // Expand the touch area without changing the visible thumb.
        float touchHalfWidth = dp(18);
        float touchTopPadding = dp(8);
        float touchBottomPadding = dp(8);

        return x >= centerX - touchHalfWidth
                && x <= centerX + touchHalfWidth
                && y >= thumbTop - touchTopPadding
                && y <= thumbTop + thumbHeight
                + touchBottomPadding;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (recyclerView == null) {
            return false;
        }

        switch (event.getActionMasked()) {

            case MotionEvent.ACTION_DOWN:

                if (!canScroll()) {
                    return false;
                }

                lastTouchY = event.getY();

                if (isTouchInsideThumb(
                        event.getX(),
                        event.getY()
                )) {
                    updateThumbPosition();

                    dragging = true;

                    dragOffsetY = lastTouchY - thumbTop;

                    if (getParent() != null) {
                        getParent()
                                .requestDisallowInterceptTouchEvent(true);
                    }

                    thumbPaint.setColor(activeThumbColor);
                    invalidate();

                    return true;
                }

                // Allow tapping the invisible track area to jump
                // to a position in the list.
                dragOffsetY = thumbHeight / 2f;

                scrollToProgress(
                        progressForTouch(lastTouchY)
                );

                dragging = true;

                if (getParent() != null) {
                    getParent()
                            .requestDisallowInterceptTouchEvent(true);
                }

                invalidate();

                return true;

            case MotionEvent.ACTION_MOVE:

                if (!dragging) {
                    return false;
                }

                lastTouchY = event.getY();

                scrollToProgress(
                        progressForTouch(lastTouchY)
                );

                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:

                if (!dragging) {
                    return false;
                }

                dragging = false;

                if (getParent() != null) {
                    getParent()
                            .requestDisallowInterceptTouchEvent(false);
                }

                thumbPaint.setColor(thumbColor);

                invalidate();

                performClick();

                return true;

            default:
                return dragging;
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }
}
