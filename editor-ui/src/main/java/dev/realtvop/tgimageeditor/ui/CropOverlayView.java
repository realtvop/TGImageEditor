package dev.realtvop.tgimageeditor.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import dev.realtvop.tgimageeditor.model.CropState;

/** Interactive normalized crop rectangle drawn over the fitted image. */
final class CropOverlayView extends View {
    interface Listener {
        void onCropChanged(CropState crop);
    }

    private static final int NONE = 0;
    private static final int MOVE = 1;
    private static final int LEFT = 1 << 1;
    private static final int TOP = 1 << 2;
    private static final int RIGHT = 1 << 3;
    private static final int BOTTOM = 1 << 4;
    private static final float MIN_SIZE = .08f;

    private final Paint shadePaint = new Paint();
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF imageBounds = new RectF();
    private final RectF cropRect = new RectF();
    private CropState crop = CropState.FULL_IMAGE;
    private Listener listener;
    private int bitmapWidth = 1;
    private int bitmapHeight = 1;
    private int dragMode;
    private float lastX;
    private float lastY;

    CropOverlayView(Context context) {
        super(context);
        shadePaint.setColor(0x99000000);
        borderPaint.setColor(Color.WHITE);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(dp(2));
        gridPaint.setColor(0x99FFFFFF);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(dp(1));
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    void setBitmapSize(int width, int height) {
        bitmapWidth = Math.max(1, width);
        bitmapHeight = Math.max(1, height);
        invalidate();
    }

    void setCrop(CropState crop) {
        this.crop = crop;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        updateRects();
        canvas.drawRect(0, 0, getWidth(), cropRect.top, shadePaint);
        canvas.drawRect(0, cropRect.bottom, getWidth(), getHeight(), shadePaint);
        canvas.drawRect(0, cropRect.top, cropRect.left, cropRect.bottom, shadePaint);
        canvas.drawRect(cropRect.right, cropRect.top, getWidth(), cropRect.bottom, shadePaint);
        canvas.drawRect(cropRect, borderPaint);
        float thirdW = cropRect.width() / 3f;
        float thirdH = cropRect.height() / 3f;
        canvas.drawLine(cropRect.left + thirdW, cropRect.top, cropRect.left + thirdW, cropRect.bottom, gridPaint);
        canvas.drawLine(cropRect.left + thirdW * 2, cropRect.top, cropRect.left + thirdW * 2, cropRect.bottom, gridPaint);
        canvas.drawLine(cropRect.left, cropRect.top + thirdH, cropRect.right, cropRect.top + thirdH, gridPaint);
        canvas.drawLine(cropRect.left, cropRect.top + thirdH * 2, cropRect.right, cropRect.top + thirdH * 2, gridPaint);
    }

    private void updateRects() {
        float availableWidth = getWidth();
        float availableHeight = getHeight();
        float scale = Math.min(availableWidth / bitmapWidth, availableHeight / bitmapHeight);
        float width = bitmapWidth * scale;
        float height = bitmapHeight * scale;
        imageBounds.set((availableWidth - width) / 2f, (availableHeight - height) / 2f,
                (availableWidth + width) / 2f, (availableHeight + height) / 2f);
        cropRect.set(imageBounds.left + crop.left() * imageBounds.width(),
                imageBounds.top + crop.top() * imageBounds.height(),
                imageBounds.left + crop.right() * imageBounds.width(),
                imageBounds.top + crop.bottom() * imageBounds.height());
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        updateRects();
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            dragMode = hitTest(event.getX(), event.getY());
            if (dragMode == NONE) return false;
            lastX = event.getX();
            lastY = event.getY();
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE && dragMode != NONE) {
            float dx = (event.getX() - lastX) / imageBounds.width();
            float dy = (event.getY() - lastY) / imageBounds.height();
            updateCrop(dx, dy);
            lastX = event.getX();
            lastY = event.getY();
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            dragMode = NONE;
            return true;
        }
        return dragMode != NONE;
    }

    private int hitTest(float x, float y) {
        float touch = dp(32);
        int mode = NONE;
        if (Math.abs(x - cropRect.left) <= touch) mode |= LEFT;
        if (Math.abs(x - cropRect.right) <= touch) mode |= RIGHT;
        if (Math.abs(y - cropRect.top) <= touch) mode |= TOP;
        if (Math.abs(y - cropRect.bottom) <= touch) mode |= BOTTOM;
        if (mode != NONE) return mode;
        return cropRect.contains(x, y) ? MOVE : NONE;
    }

    private void updateCrop(float dx, float dy) {
        float left = crop.left();
        float top = crop.top();
        float right = crop.right();
        float bottom = crop.bottom();
        if (dragMode == MOVE) {
            dx = clamp(dx, -left, 1f - right);
            dy = clamp(dy, -top, 1f - bottom);
            left += dx;
            right += dx;
            top += dy;
            bottom += dy;
        } else {
            if ((dragMode & LEFT) != 0) left = clamp(left + dx, 0f, right - MIN_SIZE);
            if ((dragMode & RIGHT) != 0) right = clamp(right + dx, left + MIN_SIZE, 1f);
            if ((dragMode & TOP) != 0) top = clamp(top + dy, 0f, bottom - MIN_SIZE);
            if ((dragMode & BOTTOM) != 0) bottom = clamp(bottom + dy, top + MIN_SIZE, 1f);
        }
        crop = crop.withBounds(left, top, right, bottom);
        if (listener != null) listener.onCropChanged(crop);
        invalidate();
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}

