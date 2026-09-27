package dev.realtvop.tgimageeditor.engine;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

import java.util.List;

import dev.realtvop.tgimageeditor.model.PaintPoint;
import dev.realtvop.tgimageeditor.model.PaintStroke;

public final class PaintRenderer {
    private PaintRenderer() {}

    public static Bitmap render(Bitmap source, List<PaintStroke> strokes) {
        if (strokes.isEmpty()) return source;
        Bitmap output = source.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(output);
        float scale = Math.min(output.getWidth(), output.getHeight());
        for (PaintStroke stroke : strokes) drawStroke(canvas, stroke, output.getWidth(), output.getHeight(), scale);
        return output;
    }

    public static void drawStroke(Canvas canvas, PaintStroke stroke, float width, float height, float widthScale) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        paint.setColor(stroke.color());
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(stroke.width() * widthScale);
        List<PaintPoint> points = stroke.points();
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
        canvas.drawPath(path, paint);
    }
}

