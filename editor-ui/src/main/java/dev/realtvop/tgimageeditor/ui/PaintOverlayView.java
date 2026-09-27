package dev.realtvop.tgimageeditor.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import dev.realtvop.tgimageeditor.engine.PaintRenderer;
import dev.realtvop.tgimageeditor.model.PaintPoint;
import dev.realtvop.tgimageeditor.model.PaintStroke;

final class PaintOverlayView extends View {
    private final RectF imageBounds = new RectF();
    private final ArrayList<PaintStroke> strokes = new ArrayList<>();
    private final ArrayList<PaintPoint> activePoints = new ArrayList<>();
    private Consumer<List<PaintStroke>> listener;
    private int bitmapWidth = 1;
    private int bitmapHeight = 1;
    private PaintStroke.Kind brushKind = PaintStroke.Kind.PEN;
    private int brushColor = Color.WHITE;
    private float brushWidth = .012f;

    PaintOverlayView(Context context) {
        super(context);
    }

    void bind(int width, int height, List<PaintStroke> value, Consumer<List<PaintStroke>> listener) {
        bitmapWidth = Math.max(1, width);
        bitmapHeight = Math.max(1, height);
        strokes.clear();
        strokes.addAll(value);
        activePoints.clear();
        this.listener = listener;
        invalidate();
    }

    void undo() {
        if (!strokes.isEmpty()) {
            strokes.remove(strokes.size() - 1);
            notifyChanged();
            invalidate();
        }
    }

    void setBrush(PaintControls.BrushSpec brush) {
        brushKind = brush.kind;
        brushColor = brush.color;
        brushWidth = brush.width;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        updateBounds();
        canvas.save();
        canvas.clipRect(imageBounds);
        canvas.translate(imageBounds.left, imageBounds.top);
        for (PaintStroke stroke : strokes) {
            PaintRenderer.drawStroke(canvas, stroke, imageBounds.width(), imageBounds.height(),
                    Math.min(imageBounds.width(), imageBounds.height()));
        }
        if (!activePoints.isEmpty()) {
            PaintRenderer.drawStroke(canvas, new PaintStroke(activePoints, brushColor, brushWidth, brushKind),
                    imageBounds.width(), imageBounds.height(), Math.min(imageBounds.width(), imageBounds.height()));
        }
        canvas.restore();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        updateBounds();
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            if (!imageBounds.contains(event.getX(), event.getY())) return false;
            activePoints.clear();
            addPoint(event);
            invalidate();
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE && !activePoints.isEmpty()) {
            addPoint(event);
            invalidate();
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP && !activePoints.isEmpty()) {
            addPoint(event);
            strokes.add(new PaintStroke(activePoints, brushColor, brushWidth, brushKind));
            activePoints.clear();
            notifyChanged();
            invalidate();
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            activePoints.clear();
            invalidate();
            return true;
        }
        return false;
    }

    private void addPoint(MotionEvent event) {
        float x = clamp((event.getX() - imageBounds.left) / imageBounds.width());
        float y = clamp((event.getY() - imageBounds.top) / imageBounds.height());
        activePoints.add(new PaintPoint(x, y));
    }

    private void notifyChanged() {
        if (listener != null) listener.accept(new ArrayList<>(strokes));
    }

    private void updateBounds() {
        float scale = Math.min((float) getWidth() / bitmapWidth, (float) getHeight() / bitmapHeight);
        float width = bitmapWidth * scale;
        float height = bitmapHeight * scale;
        imageBounds.set((getWidth() - width) / 2f, (getHeight() - height) / 2f,
                (getWidth() + width) / 2f, (getHeight() + height) / 2f);
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
