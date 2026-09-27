package dev.realtvop.tgimageeditor.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

import dev.realtvop.tgimageeditor.engine.PaintRenderer;
import dev.realtvop.tgimageeditor.model.PaintPoint;
import dev.realtvop.tgimageeditor.model.PaintStroke;
import dev.realtvop.tgimageeditor.model.TextEntity;
import dev.realtvop.tgimageeditor.engine.TextRenderer;

final class PaintOverlayView extends View {
    interface Listener {
        void onChanged(List<PaintStroke> strokes, List<TextEntity> entities);
    }

    private final RectF imageBounds = new RectF();
    private final ArrayList<PaintStroke> strokes = new ArrayList<>();
    private final ArrayList<PaintPoint> activePoints = new ArrayList<>();
    private final ArrayList<TextEntity> textEntities = new ArrayList<>();
    private final ArrayList<Boolean> undoKinds = new ArrayList<>();
    private Listener listener;
    private int bitmapWidth = 1;
    private int bitmapHeight = 1;
    private PaintStroke.Kind brushKind = PaintStroke.Kind.PEN;
    private int brushColor = Color.WHITE;
    private float brushWidth = .012f;
    private int selectedText = -1;
    private float lastX;
    private float lastY;
    private float initialDistance;
    private float initialAngle;
    private TextEntity initialEntity;

    PaintOverlayView(Context context) {
        super(context);
    }

    void bind(int width, int height, List<PaintStroke> value, List<TextEntity> entities, Listener listener) {
        bitmapWidth = Math.max(1, width);
        bitmapHeight = Math.max(1, height);
        strokes.clear();
        strokes.addAll(value);
        textEntities.clear();
        textEntities.addAll(entities);
        undoKinds.clear();
        for (int i = 0; i < strokes.size(); i++) undoKinds.add(false);
        for (int i = 0; i < textEntities.size(); i++) undoKinds.add(true);
        activePoints.clear();
        this.listener = listener;
        invalidate();
    }

    void undo() {
        if (!undoKinds.isEmpty()) {
            boolean text = undoKinds.remove(undoKinds.size() - 1);
            if (text && !textEntities.isEmpty()) textEntities.remove(textEntities.size() - 1);
            if (!text && !strokes.isEmpty()) strokes.remove(strokes.size() - 1);
            selectedText = -1;
            notifyChanged();
            invalidate();
        }
    }

    void addText(String text) {
        TextEntity.Style[] styles = TextEntity.Style.values();
        TextEntity entity = new TextEntity(text, .5f, .5f, .085f, 1f, 0f, brushColor,
                styles[textEntities.size() % styles.length]);
        textEntities.add(entity);
        undoKinds.add(true);
        selectedText = textEntities.size() - 1;
        notifyChanged();
        invalidate();
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
        for (int i = 0; i < textEntities.size(); i++) {
            TextEntity entity = textEntities.get(i);
            TextRenderer.draw(canvas, entity, imageBounds.width(), imageBounds.height());
            if (i == selectedText) {
                android.graphics.Paint selection = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                selection.setColor(0xff4b9cff);
                selection.setStyle(android.graphics.Paint.Style.STROKE);
                selection.setStrokeWidth(2f * getResources().getDisplayMetrics().density);
                canvas.drawRect(TextRenderer.approximateBounds(entity, imageBounds.width(), imageBounds.height()), selection);
            }
        }
        canvas.restore();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        updateBounds();
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            if (!imageBounds.contains(event.getX(), event.getY())) return false;
            selectedText = findText(event.getX() - imageBounds.left, event.getY() - imageBounds.top);
            lastX = event.getX();
            lastY = event.getY();
            if (selectedText >= 0) {
                invalidate();
                return true;
            }
            activePoints.clear();
            addPoint(event);
            invalidate();
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN && selectedText >= 0 && event.getPointerCount() == 2) {
            initialDistance = distance(event);
            initialAngle = angle(event);
            initialEntity = textEntities.get(selectedText);
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE && selectedText >= 0) {
            TextEntity entity = textEntities.get(selectedText);
            if (event.getPointerCount() >= 2 && initialEntity != null) {
                float scale = initialEntity.scale() * distance(event) / Math.max(1f, initialDistance);
                float rotation = initialEntity.rotation() + angle(event) - initialAngle;
                textEntities.set(selectedText, initialEntity.withTransform(entity.x(), entity.y(), scale, rotation));
            } else {
                float dx = (event.getX() - lastX) / imageBounds.width();
                float dy = (event.getY() - lastY) / imageBounds.height();
                textEntities.set(selectedText, entity.withTransform(entity.x() + dx, entity.y() + dy,
                        entity.scale(), entity.rotation()));
                lastX = event.getX();
                lastY = event.getY();
            }
            invalidate();
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE && !activePoints.isEmpty()) {
            addPoint(event);
            invalidate();
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP && selectedText >= 0) {
            initialEntity = null;
            notifyChanged();
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP && !activePoints.isEmpty()) {
            addPoint(event);
            strokes.add(new PaintStroke(activePoints, brushColor, brushWidth, brushKind));
            undoKinds.add(false);
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
        if (listener != null) listener.onChanged(new ArrayList<>(strokes), new ArrayList<>(textEntities));
    }

    private int findText(float x, float y) {
        for (int i = textEntities.size() - 1; i >= 0; i--) {
            if (TextRenderer.approximateBounds(textEntities.get(i), imageBounds.width(), imageBounds.height()).contains(x, y)) return i;
        }
        return -1;
    }

    private static float distance(MotionEvent event) {
        return (float) Math.hypot(event.getX(1) - event.getX(0), event.getY(1) - event.getY(0));
    }

    private static float angle(MotionEvent event) {
        return (float) Math.toDegrees(Math.atan2(event.getY(1) - event.getY(0), event.getX(1) - event.getX(0)));
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
