package com.arxiv.client.ui.reader;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

/**
 * ZoomableImageView supports smooth pinch-to-zoom, pan, double-tap zoom,
 * and mouse drag on Android Virtual Devices (AVD).
 * When zoomed in, it prevents parent ViewPager2 from intercepting touch events.
 */
public class ZoomableImageView extends AppCompatImageView {

    private final Matrix matrix = new Matrix();
    private final float[] matrixValues = new float[9];

    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;

    private float minScale = 1.0f;
    private float maxScale = 5.0f;
    private float currentScale = 1.0f;

    private final PointF lastTouch = new PointF();
    private boolean isDragging = false;

    public ZoomableImageView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public ZoomableImageView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ZoomableImageView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setScaleType(ScaleType.MATRIX);

        scaleDetector = new ScaleGestureDetector(context, new ScaleListener());
        gestureDetector = new GestureDetector(context, new GestureListener());
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (changed || currentScale == 1.0f) {
            fitToScreen();
        }
    }

    public void fitToScreen() {
        Drawable drawable = getDrawable();
        if (drawable == null || drawable.getIntrinsicWidth() <= 0 || drawable.getIntrinsicHeight() <= 0) {
            return;
        }

        int viewWidth = getWidth();
        int viewHeight = getHeight();
        if (viewWidth <= 0 || viewHeight <= 0) {
            return;
        }

        int drawableWidth = drawable.getIntrinsicWidth();
        int drawableHeight = drawable.getIntrinsicHeight();

        float scaleX = (float) viewWidth / (float) drawableWidth;
        float scaleY = (float) viewHeight / (float) drawableHeight;
        float scale = Math.min(scaleX, scaleY);

        float dx = (viewWidth - drawableWidth * scale) / 2f;
        float dy = (viewHeight - drawableHeight * scale) / 2f;

        matrix.reset();
        matrix.postScale(scale, scale);
        matrix.postTranslate(dx, dy);
        setImageMatrix(matrix);

        minScale = 1.0f;
        currentScale = 1.0f;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean scaleHandled = scaleDetector.onTouchEvent(event);
        boolean gestureHandled = gestureDetector.onTouchEvent(event);

        if (scaleDetector.isInProgress()) {
            if (getParent() != null) {
                getParent().requestDisallowInterceptTouchEvent(true);
            }
            return true;
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastTouch.set(event.getX(), event.getY());
                if (currentScale > 1.01f && getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                isDragging = false;
                break;

            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - lastTouch.x;
                float dy = event.getY() - lastTouch.y;

                if (currentScale > 1.01f) {
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    matrix.postTranslate(dx, dy);
                    clampMatrix();
                    setImageMatrix(matrix);
                    isDragging = true;
                }
                lastTouch.set(event.getX(), event.getY());
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isDragging = false;
                if (currentScale <= 1.01f && getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                break;
        }

        return scaleHandled || gestureHandled || super.onTouchEvent(event);
    }

    private void clampMatrix() {
        RectF rect = getTransformedRect();
        if (rect == null) return;

        int viewWidth = getWidth();
        int viewHeight = getHeight();

        float deltaX = 0f;
        float deltaY = 0f;

        if (rect.width() <= viewWidth) {
            deltaX = (viewWidth - rect.width()) / 2f - rect.left;
        } else if (rect.left > 0) {
            deltaX = -rect.left;
        } else if (rect.right < viewWidth) {
            deltaX = viewWidth - rect.right;
        }

        if (rect.height() <= viewHeight) {
            deltaY = (viewHeight - rect.height()) / 2f - rect.top;
        } else if (rect.top > 0) {
            deltaY = -rect.top;
        } else if (rect.bottom < viewHeight) {
            deltaY = viewHeight - rect.bottom;
        }

        matrix.postTranslate(deltaX, deltaY);
    }

    private RectF getTransformedRect() {
        Drawable drawable = getDrawable();
        if (drawable == null) return null;

        RectF rect = new RectF(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        matrix.mapRect(rect);
        return rect;
    }

    public void zoomIn() {
        applyScale(1.3f, getWidth() / 2f, getHeight() / 2f);
    }

    public void zoomOut() {
        applyScale(0.77f, getWidth() / 2f, getHeight() / 2f);
    }

    public void resetZoom() {
        fitToScreen();
    }

    public boolean isZoomed() {
        return currentScale > 1.05f;
    }

    private void applyScale(float scaleFactor, float focusX, float focusY) {
        float targetScale = currentScale * scaleFactor;
        if (targetScale < minScale) {
            scaleFactor = minScale / currentScale;
            currentScale = minScale;
        } else if (targetScale > maxScale) {
            scaleFactor = maxScale / currentScale;
            currentScale = maxScale;
        } else {
            currentScale = targetScale;
        }

        matrix.postScale(scaleFactor, scaleFactor, focusX, focusY);
        clampMatrix();
        setImageMatrix(matrix);

        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(currentScale > 1.01f);
        }
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            applyScale(detector.getScaleFactor(), detector.getFocusX(), detector.getFocusY());
            return true;
        }
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDoubleTap(MotionEvent e) {
            if (currentScale > 1.05f) {
                resetZoom();
            } else {
                applyScale(2.5f, e.getX(), e.getY());
            }
            return true;
        }
    }
}
