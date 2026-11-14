
package com.example.speaksmartbaguio;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

public class DictionaryFastScroller extends View {

    private RecyclerView recyclerView;

    private final Paint thumbPaint =
            new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Paint trackPaint =
            new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF thumbRect = new RectF();

    private float thumbHeight;
    private float thumbTop;
    private float touchOffset;

    private float minimumThumbHeight;
    private float thumbWidth;

    private boolean isDragging = false;
    private boolean bottomCallbackTriggered = false;

    private Runnable onReachBottomListener;

    public interface ScrollCallback {
        void onScrollChanged(int scrollY, int oldScrollY);
    }

    private ScrollCallback scrollCallback;

    private final RecyclerView.OnScrollListener scrollListener =
            new RecyclerView.OnScrollListener() {

                @Override
                public void onScrolled(
                        RecyclerView view,
                        int dx,
                        int dy
                ) {
                    updateThumbPosition();
                    invalidate();

                    int offset = view.computeVerticalScrollOffset();
                    int range = getScrollRange();
                    int extent = view.computeVerticalScrollExtent();

                    if (offset + extent < range - 2) {
                        bottomCallbackTriggered = false;
                    }

                    if (scrollCallback != null) {
                        scrollCallback.onScrollChanged(
                                offset,
                                offset - dy
                        );
                    }
                }
            };

    public DictionaryFastScroller(Context context) {
        super(context);
        init();
    }

    public DictionaryFastScroller(
            Context context,
            @Nullable AttributeSet attrs
    ) {
        super(context, attrs);
        init();
    }

    public DictionaryFastScroller(
            Context context,
            @Nullable AttributeSet attrs,
            int defStyleAttr
    ) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {

        thumbPaint.setColor(
                ContextCompat.getColor(
                        getContext(),
                        R.color.cyan_primary
                )
        );

        thumbPaint.setStyle(Paint.Style.FILL);

        trackPaint.setColor(
                ContextCompat.getColor(
                        getContext(),
                        R.color.cyan_deep
                )
        );

        trackPaint.setAlpha(65);
        trackPaint.setStyle(Paint.Style.FILL);

        float density = getResources()
                .getDisplayMetrics()
                .density;

        minimumThumbHeight = 48f * density;
        thumbWidth = 5f * density;

        setClickable(true);
        setFocusable(true);
    }

    public void attachToRecyclerView(
            @Nullable RecyclerView newRecyclerView
    ) {

        if (recyclerView != null) {
            recyclerView.removeOnScrollListener(scrollListener);
        }

        recyclerView = newRecyclerView;
        bottomCallbackTriggered = false;

        if (recyclerView != null) {
            recyclerView.addOnScrollListener(scrollListener);
        }

        updateThumbPosition();
        invalidate();
    }

    public void setScrollCallback(
            @Nullable ScrollCallback callback
    ) {
        scrollCallback = callback;
    }

    public void setOnReachBottomListener(
            @Nullable Runnable listener
    ) {
        onReachBottomListener = listener;
    }

    private int getScrollRange() {

        if (recyclerView == null) {
            return 0;
        }

        return Math.max(
                0,
                recyclerView.computeVerticalScrollRange()
        );
    }

    private void updateThumbPosition() {

        if (recyclerView == null || getHeight() <= 0) {
            return;
        }

        int range = getScrollRange();

        int extent =
                recyclerView.computeVerticalScrollExtent();

        int offset =
                recyclerView.computeVerticalScrollOffset();

        if (range <= extent || extent <= 0) {
            thumbHeight = getHeight();
            thumbTop = 0;
            return;
        }

        thumbHeight = getHeight()
                * (extent / (float) range);

        thumbHeight = Math.max(
                minimumThumbHeight,
                Math.min(getHeight(), thumbHeight)
        );

        float availableTrack = getHeight() - thumbHeight;

        float progress = offset / (float) (range - extent);

        progress = Math.max(
                0f,
                Math.min(1f, progress)
        );

        thumbTop = availableTrack * progress;
    }

    @Override
    protected void onDraw(Canvas canvas) {

        super.onDraw(canvas);

        if (recyclerView == null || getHeight() <= 0) {
            return;
        }

        if (getScrollRange()
                <= recyclerView.computeVerticalScrollExtent()) {
            return;
        }

        float centerX = getWidth() / 2f;

        float trackLeft = centerX - thumbWidth / 2f;
        float trackRight = centerX + thumbWidth / 2f;

        canvas.drawRoundRect(
                trackLeft,
                0,
                trackRight,
                getHeight(),
                thumbWidth,
                thumbWidth,
                trackPaint
        );

        thumbRect.set(
                trackLeft,
                thumbTop,
                trackRight,
                thumbTop + thumbHeight
        );

        canvas.drawRoundRect(
                thumbRect,
                thumbWidth,
                thumbWidth,
                thumbPaint
        );
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        if (recyclerView == null) {
            return false;
        }

        if (getScrollRange()
                <= recyclerView.computeVerticalScrollExtent()) {
            return false;
        }

        float y = event.getY();

        switch (event.getAction()) {

            case MotionEvent.ACTION_DOWN:

                updateThumbPosition();

                if (y >= thumbTop
                        && y <= thumbTop + thumbHeight) {

                    touchOffset = y - thumbTop;

                } else {

                    touchOffset = thumbHeight / 2f;
                }

                isDragging = true;
                bottomCallbackTriggered = false;

                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }

                moveThumbTo(y - touchOffset);

                return true;

            case MotionEvent.ACTION_MOVE:

                if (isDragging) {
                    moveThumbTo(y - touchOffset);
                    return true;
                }

                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:

                if (isDragging) {

                    isDragging = false;

                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(false);
                    }

                    performClick();
                    return true;
                }

                break;
        }

        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private void moveThumbTo(float requestedTop) {

        if (recyclerView == null) {
            return;
        }

        float availableTrack = getHeight() - thumbHeight;

        if (availableTrack <= 0) {
            return;
        }

        float newTop = Math.max(
                0f,
                Math.min(availableTrack, requestedTop)
        );

        thumbTop = newTop;

        float progress = newTop / availableTrack;

        int range = getScrollRange();

        int extent =
                recyclerView.computeVerticalScrollExtent();

        int maximumOffset = Math.max(0, range - extent);

        int targetOffset =
                Math.round(progress * maximumOffset);

        int currentOffset =
                recyclerView.computeVerticalScrollOffset();

        recyclerView.scrollBy(
                0,
                targetOffset - currentOffset
        );

        invalidate();

        if (progress >= 0.96f
                && !bottomCallbackTriggered
                && onReachBottomListener != null) {

            bottomCallbackTriggered = true;
            onReachBottomListener.run();
        }
    }

    @Override
    protected void onSizeChanged(
            int width,
            int height,
            int oldWidth,
            int oldHeight
    ) {

        super.onSizeChanged(
                width,
                height,
                oldWidth,
                oldHeight
        );

        updateThumbPosition();
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {

        if (recyclerView != null) {
            recyclerView.removeOnScrollListener(scrollListener);
            recyclerView = null;
        }

        scrollCallback = null;
        onReachBottomListener = null;

        super.onDetachedFromWindow();
    }
}
