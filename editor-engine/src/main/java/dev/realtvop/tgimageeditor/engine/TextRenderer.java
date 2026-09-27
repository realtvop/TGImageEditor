package dev.realtvop.tgimageeditor.engine;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import dev.realtvop.tgimageeditor.model.TextEntity;

public final class TextRenderer {
    private TextRenderer() {}

    public static void draw(Canvas canvas, TextEntity entity, float width, float height) {
        float minSide = Math.min(width, height);
        float textSize = entity.size() * entity.scale() * minSide;
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        fill.setTextSize(textSize);
        fill.setTextAlign(Paint.Align.CENTER);
        fill.setColor(entity.color());
        fill.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        String[] lines = entity.text().split("\n", -1);
        Paint.FontMetrics metrics = fill.getFontMetrics();
        float lineHeight = metrics.descent - metrics.ascent;
        float blockHeight = lineHeight * lines.length;
        float maxWidth = 0f;
        for (String line : lines) maxWidth = Math.max(maxWidth, fill.measureText(line));

        canvas.save();
        canvas.translate(entity.x() * width, entity.y() * height);
        canvas.rotate(entity.rotation());
        if (entity.style() == TextEntity.Style.FRAME) {
            Paint frame = new Paint(Paint.ANTI_ALIAS_FLAG);
            frame.setColor(0xbb000000);
            float padding = textSize * .28f;
            canvas.drawRoundRect(new RectF(-maxWidth / 2f - padding, -blockHeight / 2f - padding,
                    maxWidth / 2f + padding, blockHeight / 2f + padding), padding, padding, frame);
        }
        float baseline = -blockHeight / 2f - metrics.ascent;
        for (String line : lines) {
            if (entity.style() == TextEntity.Style.OUTLINE) {
                Paint outline = new Paint(fill);
                outline.setStyle(Paint.Style.STROKE);
                outline.setStrokeJoin(Paint.Join.ROUND);
                outline.setStrokeWidth(Math.max(2f, textSize * .10f));
                outline.setColor(0xff000000);
                canvas.drawText(line, 0, baseline, outline);
            }
            canvas.drawText(line, 0, baseline, fill);
            baseline += lineHeight;
        }
        canvas.restore();
    }

    public static RectF approximateBounds(TextEntity entity, float width, float height) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float textSize = entity.size() * entity.scale() * Math.min(width, height);
        paint.setTextSize(textSize);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        String[] lines = entity.text().split("\n", -1);
        float maxWidth = textSize;
        for (String line : lines) maxWidth = Math.max(maxWidth, paint.measureText(line));
        float blockHeight = textSize * 1.25f * lines.length;
        float cx = entity.x() * width;
        float cy = entity.y() * height;
        return new RectF(cx - maxWidth * .65f, cy - blockHeight * .65f,
                cx + maxWidth * .65f, cy + blockHeight * .65f);
    }
}

