package dev.realtvop.tgimageeditor.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import java.util.function.Consumer;

import dev.realtvop.tgimageeditor.model.BlurState;

/** Interactive focus-blur control modeled after Nekogram's PhotoFilterBlurControl. */
public final class BlurOverlayView extends View {
    private final RectF imageBounds = new RectF();
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private BlurState state = BlurState.NONE;
    private Consumer<BlurState> listener;
    private int bitmapWidth = 1;
    private int bitmapHeight = 1;
    private float startDistance;
    private float startSize;
    private float startAngle;
    private float startStateAngle;
    private float startCenterX;
    private float startCenterY;
    private float startPointerX;
    private float startPointerY;
    private boolean moving;
    private boolean pinching;

    public BlurOverlayView(Context context) {
        super(context);
        setWillNotDraw(false);
        linePaint.setColor(Color.WHITE);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(dp(2));
        centerPaint.setColor(Color.WHITE);
        setVisibility(GONE);
    }

    public void bind(BlurState state, Consumer<BlurState> listener) {
        this.state = state;
        this.listener = listener;
        setVisibility(state.type() == BlurState.Type.NONE ? GONE : VISIBLE);
        invalidate();
    }

    public void setState(BlurState state) {
        this.state = state;
        setVisibility(state.type() == BlurState.Type.NONE ? GONE : VISIBLE);
        invalidate();
    }

    public void setBitmapSize(int width, int height) {
        bitmapWidth = Math.max(1, width);
        bitmapHeight = Math.max(1, height);
        invalidate();
    }

    public void release() {
        listener = null;
        moving = false;
        pinching = false;
        setVisibility(GONE);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (state.type() == BlurState.Type.NONE) return;
        updateBounds();
        float minSide = Math.min(imageBounds.width(), imageBounds.height());
        float cx = imageBounds.left + state.centerX() * imageBounds.width();
        float cy = imageBounds.top + state.centerY() * imageBounds.width()
                + (imageBounds.height() - imageBounds.width()) * .5f;
        float outer = minSide * state.size();
        float inner = minSide * Math.max(0f, state.size() - state.feather());
        canvas.save();
        if (state.type() == BlurState.Type.RADIAL) {
            canvas.drawCircle(cx, cy, outer, linePaint);
            if (inner > dp(4)) canvas.drawCircle(cx, cy, inner, linePaint);
            canvas.drawCircle(cx, cy, dp(8), centerPaint);
        } else {
            float angle = (float) Math.toRadians(state.angle());
            float ux = (float) Math.cos(angle);
            float uy = (float) Math.sin(angle);
            float vx = -uy;
            float vy = ux;
            drawBand(canvas, cx, cy, outer, ux, uy, vx, vy);
            if (inner > dp(4)) drawBand(canvas, cx, cy, inner, ux, uy, vx, vy);
            canvas.drawCircle(cx, cy, dp(8), centerPaint);
        }
        canvas.restore();
    }

    private void drawBand(Canvas canvas, float cx, float cy, float radius, float ux, float uy, float vx, float vy) {
        float extent = Math.max(imageBounds.width(), imageBounds.height()) * 1.2f;
        float x1 = cx + ux * radius - vx * extent;
        float y1 = cy + uy * radius - vy * extent;
        float x2 = cx + ux * radius + vx * extent;
        float y2 = cy + uy * radius + vy * extent;
        canvas.drawLine(x1, y1, x2, y2, linePaint);
        x1 = cx - ux * radius - vx * extent;
        y1 = cy - uy * radius - vy * extent;
        x2 = cx - ux * radius + vx * extent;
        y2 = cy - uy * radius + vy * extent;
        canvas.drawLine(x1, y1, x2, y2, linePaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (state.type() == BlurState.Type.NONE) return false;
        updateBounds();
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            moving = true;
            pinching = false;
            startCenterX = state.centerX();
            startCenterY = state.centerY();
            startPointerX = event.getX();
            startPointerY = event.getY();
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN && event.getPointerCount() >= 2) {
            moving = false;
            pinching = true;
            startDistance = distance(event);
            startSize = state.size();
            startAngle = pointerAngle(event);
            startStateAngle = state.angle();
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            BlurState next = state;
            if (pinching && event.getPointerCount() >= 2) {
                float factor = distance(event) / Math.max(1f, startDistance);
                next = next.withSize(clamp(startSize * factor, .03f, 1f));
                if (next.type() == BlurState.Type.LINEAR) {
                    float delta = pointerAngle(event) - startAngle;
                    next = next.withAngle(startStateAngle + (float) Math.toDegrees(delta));
                }
            } else if (moving) {
                next = next.withCenter(
                        clamp(startCenterX + (event.getX() - startPointerX) / imageBounds.width(), 0f, 1f),
                        clamp(startCenterY + (event.getY() - startPointerY) / imageBounds.width(), 0f, 1f));
            }
            state = next;
            if (listener != null) listener.accept(next);
            invalidate();
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP
                || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            moving = false;
            pinching = false;
            return true;
        }
        return true;
    }

    private void updateBounds() {
        float scale = Math.min((float) getWidth() / bitmapWidth, (float) getHeight() / bitmapHeight);
        float width = bitmapWidth * scale;
        float height = bitmapHeight * scale;
        imageBounds.set((getWidth() - width) / 2f, (getHeight() - height) / 2f,
                (getWidth() + width) / 2f, (getHeight() + height) / 2f);
    }

    private static float distance(MotionEvent event) {
        return (float) Math.hypot(event.getX(1) - event.getX(0), event.getY(1) - event.getY(0));
    }

    private static float pointerAngle(MotionEvent event) {
        return (float) Math.atan2(event.getY(1) - event.getY(0), event.getX(1) - event.getX(0));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
