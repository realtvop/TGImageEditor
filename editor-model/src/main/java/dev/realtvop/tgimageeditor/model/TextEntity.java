package dev.realtvop.tgimageeditor.model;

import java.util.Objects;

public final class TextEntity implements java.io.Serializable {
    public enum Style { PLAIN, OUTLINE, FRAME }

    private final String text;
    private final float x;
    private final float y;
    private final float size;
    private final float scale;
    private final float rotation;
    private final int color;
    private final Style style;

    public TextEntity(String text, float x, float y, float size, float scale,
                      float rotation, int color, Style style) {
        this.text = Objects.requireNonNull(text, "text");
        if (text.trim().isEmpty()) throw new IllegalArgumentException("Text must not be empty");
        if (!unit(x) || !unit(y)) throw new IllegalArgumentException("Text position must be in [0, 1]");
        if (!Float.isFinite(size) || size <= 0f || size > 1f) throw new IllegalArgumentException("Invalid text size");
        if (!Float.isFinite(scale) || scale <= 0f) throw new IllegalArgumentException("Invalid text scale");
        if (!Float.isFinite(rotation)) throw new IllegalArgumentException("Invalid text rotation");
        this.x = x;
        this.y = y;
        this.size = size;
        this.scale = scale;
        this.rotation = rotation;
        this.color = color;
        this.style = Objects.requireNonNull(style, "style");
    }

    private static boolean unit(float value) {
        return Float.isFinite(value) && value >= 0f && value <= 1f;
    }

    public String text() { return text; }
    public float x() { return x; }
    public float y() { return y; }
    public float size() { return size; }
    public float scale() { return scale; }
    public float rotation() { return rotation; }
    public int color() { return color; }
    public Style style() { return style; }

    public TextEntity withTransform(float x, float y, float scale, float rotation) {
        return new TextEntity(text, Math.max(0f, Math.min(1f, x)), Math.max(0f, Math.min(1f, y)),
                size, Math.max(.25f, Math.min(4f, scale)), rotation, color, style);
    }
}
