package dev.realtvop.tgimageeditor.engine;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.BitmapShader;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;

import java.util.List;

import dev.realtvop.tgimageeditor.model.PaintPoint;
import dev.realtvop.tgimageeditor.model.PaintStroke;
import dev.realtvop.tgimageeditor.model.TextEntity;
import org.telegram.ui.Components.Paint.NekogramPaintPipeline;
import org.telegram.ui.Components.Paint.Brush;

public final class PaintRenderer {
    private PaintRenderer() {}

    public static Bitmap render(Bitmap source, List<PaintStroke> strokes) {
        return render(source, strokes, java.util.Collections.emptyList());
    }

    public static Bitmap render(Bitmap source, List<PaintStroke> strokes, List<TextEntity> textEntities) {
        if (strokes.isEmpty() && textEntities.isEmpty()) return source;
        Bitmap painted = NekogramPaintPipeline.render(source, strokes);
        Bitmap output = painted == source ? source.copy(Bitmap.Config.ARGB_8888, true) : painted;
        Canvas canvas = new Canvas(output);
        float scale = output.getWidth();
        for (PaintStroke stroke : strokes) {
            if (!isNekogramPaint(stroke.kind())) {
                drawStroke(canvas, stroke, output.getWidth(), output.getHeight(), scale);
            }
        }
        for (TextEntity entity : textEntities) {
            TextRenderer.draw(canvas, entity, output.getWidth(), output.getHeight());
        }
        return output;
    }

    private static boolean isNekogramPaint(PaintStroke.Kind kind) {
        return kind == PaintStroke.Kind.PEN || kind == PaintStroke.Kind.MARKER
                || kind == PaintStroke.Kind.NEON || kind == PaintStroke.Kind.BLUR
                || kind == PaintStroke.Kind.ERASER || kind == PaintStroke.Kind.ARROW
                || kind == PaintStroke.Kind.RECTANGLE || kind == PaintStroke.Kind.OVAL;
    }

    public static void drawStroke(Canvas canvas, PaintStroke stroke, float width, float height, float widthScale) {
        if (stroke.kind() == PaintStroke.Kind.PEN
                || stroke.kind() == PaintStroke.Kind.MARKER
                || stroke.kind() == PaintStroke.Kind.NEON
                || stroke.kind() == PaintStroke.Kind.ERASER) {
            drawBrushStamps(canvas, stroke, width, height, widthScale);
            return;
        }
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
        Path path = strokePath(points, width, height);
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

    /**
     * Nekogram's paint renderer stamps a brush texture along the input path at a
     * brush-specific spacing. Canvas circles/ellipses are the standalone equivalent
     * of its radial and elliptical stamp textures.
     */
    private static void drawBrushStamps(Canvas canvas, PaintStroke stroke, float width,
                                        float height, float widthScale) {
        float radius = Math.max(.5f, stroke.width() * widthScale);
        float spacingRate = stroke.kind() == PaintStroke.Kind.MARKER ? .04f : .15f;
        float spacing = Math.max(1f, radius * 2f * spacingRate);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(stroke.color());
        if (stroke.kind() == PaintStroke.Kind.PEN) {
            paint.setAlpha(Math.round(ColorAlpha(stroke.color()) * .85f));
        } else if (stroke.kind() == PaintStroke.Kind.MARKER) {
            paint.setAlpha(Math.round(ColorAlpha(stroke.color()) * .30f));
        } else if (stroke.kind() == PaintStroke.Kind.NEON) {
            paint.setAlpha(Math.round(ColorAlpha(stroke.color()) * .70f));
        } else {
            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
            paint.setAlpha(255);
        }

        List<PaintPoint> points = stroke.points();
        if (points.size() == 1) {
            drawStamp(canvas, paint, points.get(0).x() * width, points.get(0).y() * height,
                    radius, 0f, stroke.kind());
            return;
        }
        float remainder = 0f;
        for (int i = 1; i < points.size(); i++) {
            PaintPoint previous = points.get(i - 1);
            PaintPoint current = points.get(i);
            float x1 = previous.x() * width;
            float y1 = previous.y() * height;
            float x2 = current.x() * width;
            float y2 = current.y() * height;
            float dx = x2 - x1;
            float dy = y2 - y1;
            float distance = (float) Math.hypot(dx, dy);
            if (distance <= 0f) continue;
            float ux = dx / distance;
            float uy = dy / distance;
            float offset = spacing - remainder;
            for (float travelled = offset; travelled <= distance; travelled += spacing) {
                float x = x1 + ux * travelled;
                float y = y1 + uy * travelled;
                drawStamp(canvas, paint, x, y, radius, (float) Math.atan2(dy, dx), stroke.kind());
            }
            remainder = (remainder + distance) % spacing;
        }
        PaintPoint last = points.get(points.size() - 1);
        drawStamp(canvas, paint, last.x() * width, last.y() * height, radius, 0f, stroke.kind());
    }

    private static void drawStamp(Canvas canvas, Paint paint, float x, float y, float radius,
                                  float angle, PaintStroke.Kind kind) {
        if (kind == PaintStroke.Kind.MARKER) {
            canvas.save();
            canvas.rotate((float) Math.toDegrees(angle), x, y);
            canvas.drawOval(new RectF(x - radius * 1.5f, y - radius, x + radius * 1.5f, y + radius), paint);
            canvas.restore();
        } else if (kind == PaintStroke.Kind.NEON) {
            Paint glow = new Paint(paint);
            glow.setAlpha(Math.round(paint.getAlpha() * .45f));
            canvas.drawCircle(x, y, radius * 1.65f, glow);
            Paint core = new Paint(paint);
            core.setColor(0xffffffff);
            core.setAlpha(Math.min(255, paint.getAlpha() + 45));
            canvas.drawCircle(x, y, Math.max(.5f, radius * .35f), core);
        } else {
            canvas.drawCircle(x, y, radius, paint);
        }
    }

    private static int ColorAlpha(int color) {
        return (color >>> 24) & 255;
    }

    public static void drawBlurStroke(Canvas canvas, PaintStroke stroke, Bitmap blurred,
                                      float width, float height, float widthScale) {
        int layer = canvas.saveLayer(0, 0, width, height, null);
        Paint mask = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | Paint.FILTER_BITMAP_FLAG);
        // PAINT_BRUSH_FSH uses the stamp's red channel as mask alpha. The source
        // WebP is opaque, so drawing it as an ordinary Android bitmap would make
        // every stamp's full rectangular bounds opaque.
        ColorMatrix redToAlpha = new ColorMatrix(new float[]{
                0, 0, 0, 0, 255,
                0, 0, 0, 0, 255,
                0, 0, 0, 0, 255,
                1, 0, 0, 0, 0
        });
        mask.setColorFilter(new ColorMatrixColorFilter(redToAlpha));
        Bitmap stamp = new Brush.Blurer().getStamp();
        drawBlurMask(canvas, stroke, stamp, mask, width, height, stroke.width() * widthScale);
        stamp.recycle();

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | Paint.FILTER_BITMAP_FLAG);
        BitmapShader shader = new BitmapShader(blurred, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        Matrix matrix = new Matrix();
        matrix.setScale(width / blurred.getWidth(), height / blurred.getHeight());
        shader.setLocalMatrix(matrix);
        paint.setShader(shader);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawRect(0, 0, width, height, paint);
        canvas.restoreToCount(layer);
    }

    private static void drawBlurMask(Canvas canvas, PaintStroke stroke, Bitmap stamp, Paint paint,
                                     float width, float height, float radius) {
        List<PaintPoint> points = stroke.points();
        if (points.size() == 1) {
            drawBlurStamp(canvas, stamp, paint, points.get(0).x() * width, points.get(0).y() * height, radius);
            return;
        }
        double remainder = 0;
        double step = Math.max(1f, .15f * radius);
        for (int i = 0; i < points.size() - 1; i++) {
            PaintPoint first = points.get(i);
            PaintPoint second = points.get(i + 1);
            float x1 = first.x() * width;
            float y1 = first.y() * height;
            float x2 = second.x() * width;
            float y2 = second.y() * height;
            double dx = x2 - x1;
            double dy = y2 - y1;
            double distance = Math.hypot(dx, dy);
            double unitX = distance == 0 ? 1 : dx / distance;
            double unitY = distance == 0 ? 1 : dy / distance;
            double travelled = remainder;
            while (travelled <= distance) {
                drawBlurStamp(canvas, stamp, paint, (float) (x1 + unitX * travelled),
                        (float) (y1 + unitY * travelled), radius);
                travelled += step;
            }
            if (i == points.size() - 2) {
                drawBlurStamp(canvas, stamp, paint, x2, y2, radius);
            }
            remainder = travelled - distance;
        }
    }

    private static void drawBlurStamp(Canvas canvas, Bitmap stamp, Paint paint, float x, float y, float radius) {
        canvas.drawBitmap(stamp, null, new RectF(x - radius, y - radius, x + radius, y + radius), paint);
    }

    public static Bitmap createBlurredCopy(Bitmap source, int maximumDimension) {
        return NekogramPaintPipeline.createBlurredCopy(source);
    }

    private static Path strokePath(List<PaintPoint> points, float width, float height) {
        Path path = new Path();
        PaintPoint first = points.get(0);
        path.moveTo(first.x() * width, first.y() * height);
        if (points.size() == 1) {
            path.lineTo(first.x() * width + .01f, first.y() * height + .01f);
            return path;
        }
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
        return path;
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
