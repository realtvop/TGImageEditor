package dev.realtvop.tgimageeditor.engine;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;

import java.util.List;

import dev.realtvop.tgimageeditor.model.PaintPoint;
import dev.realtvop.tgimageeditor.model.PaintStroke;
import dev.realtvop.tgimageeditor.model.TextEntity;

public final class PaintRenderer {
    private PaintRenderer() {}

    public static Bitmap render(Bitmap source, List<PaintStroke> strokes) {
        return render(source, strokes, java.util.Collections.emptyList());
    }

    public static Bitmap render(Bitmap source, List<PaintStroke> strokes, List<TextEntity> textEntities) {
        if (strokes.isEmpty() && textEntities.isEmpty()) return source;
        Bitmap output = source.copy(Bitmap.Config.ARGB_8888, true);
        Bitmap overlay = Bitmap.createBitmap(output.getWidth(), output.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(overlay);
        float scale = Math.min(output.getWidth(), output.getHeight());
        for (PaintStroke stroke : strokes) drawStroke(canvas, stroke, output.getWidth(), output.getHeight(), scale);
        new Canvas(output).drawBitmap(overlay, 0, 0, null);
        overlay.recycle();
        Canvas outputCanvas = new Canvas(output);
        for (TextEntity entity : textEntities) {
            TextRenderer.draw(outputCanvas, entity, output.getWidth(), output.getHeight());
        }
        return output;
    }

    public static void drawStroke(Canvas canvas, PaintStroke stroke, float width, float height, float widthScale) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        paint.setColor(stroke.color());
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(stroke.width() * widthScale);
        if (stroke.kind() == PaintStroke.Kind.MARKER) {
            paint.setAlpha(110);
            paint.setStrokeWidth(paint.getStrokeWidth() * 1.8f);
        } else if (stroke.kind() == PaintStroke.Kind.ERASER) {
            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
            paint.setStrokeWidth(paint.getStrokeWidth() * 1.8f);
        }
        List<PaintPoint> points = stroke.points();
        if (stroke.kind() == PaintStroke.Kind.ARROW || stroke.kind() == PaintStroke.Kind.RECTANGLE
                || stroke.kind() == PaintStroke.Kind.OVAL) {
            drawShape(canvas, stroke, paint, width, height);
            return;
        }
        Path path = new Path();
        PaintPoint first = points.get(0);
        path.moveTo(first.x() * width, first.y() * height);
        if (points.size() == 1) {
            path.lineTo(first.x() * width + .01f, first.y() * height + .01f);
        } else {
            for (int i = 1; i < points.size(); i++) {
                PaintPoint previous = points.get(i - 1);
                PaintPoint point = points.get(i);
                float x = point.x() * width;
                float y = point.y() * height;
                path.quadTo(previous.x() * width, previous.y() * height,
                        (previous.x() * width + x) / 2f, (previous.y() * height + y) / 2f);
            }
            PaintPoint last = points.get(points.size() - 1);
            path.lineTo(last.x() * width, last.y() * height);
        }
        if (stroke.kind() == PaintStroke.Kind.NEON) {
            Paint glow = new Paint(paint);
            glow.setAlpha(90);
            glow.setStrokeWidth(paint.getStrokeWidth() * 2.8f);
            canvas.drawPath(path, glow);
            paint.setColor(0xffffffff);
            paint.setStrokeWidth(Math.max(1f, paint.getStrokeWidth() * .35f));
        }
        canvas.drawPath(path, paint);
    }

    private static void drawShape(Canvas canvas, PaintStroke stroke, Paint paint, float width, float height) {
        PaintPoint start = stroke.points().get(0);
        PaintPoint end = stroke.points().get(stroke.points().size() - 1);
        float x1 = start.x() * width;
        float y1 = start.y() * height;
        float x2 = end.x() * width;
        float y2 = end.y() * height;
        if (stroke.kind() == PaintStroke.Kind.RECTANGLE) {
            canvas.drawRect(new RectF(Math.min(x1, x2), Math.min(y1, y2), Math.max(x1, x2), Math.max(y1, y2)), paint);
        } else if (stroke.kind() == PaintStroke.Kind.OVAL) {
            canvas.drawOval(new RectF(Math.min(x1, x2), Math.min(y1, y2), Math.max(x1, x2), Math.max(y1, y2)), paint);
        } else {
            canvas.drawLine(x1, y1, x2, y2, paint);
            double angle = Math.atan2(y2 - y1, x2 - x1);
            float head = Math.max(paint.getStrokeWidth() * 4f, Math.min(width, height) * .035f);
            Path arrow = new Path();
            arrow.moveTo(x2, y2);
            arrow.lineTo(x2 - (float) Math.cos(angle - .55) * head,
                    y2 - (float) Math.sin(angle - .55) * head);
            arrow.moveTo(x2, y2);
            arrow.lineTo(x2 - (float) Math.cos(angle + .55) * head,
                    y2 - (float) Math.sin(angle + .55) * head);
            canvas.drawPath(arrow, paint);
        }
    }
}
