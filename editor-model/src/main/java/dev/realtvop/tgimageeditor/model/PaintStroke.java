package dev.realtvop.tgimageeditor.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PaintStroke implements java.io.Serializable {
    public enum Kind { PEN, MARKER, NEON, ERASER, ARROW, RECTANGLE, OVAL }

    private final List<PaintPoint> points;
    private final int color;
    private final float width;
    private final Kind kind;

    public PaintStroke(List<PaintPoint> points, int color, float width) {
        this(points, color, width, Kind.PEN);
    }

    public PaintStroke(List<PaintPoint> points, int color, float width, Kind kind) {
        if (points == null || points.isEmpty()) throw new IllegalArgumentException("Stroke requires points");
        if (!Float.isFinite(width) || width <= 0f || width > 1f) {
            throw new IllegalArgumentException("Stroke width must be in (0, 1]");
        }
        this.points = Collections.unmodifiableList(new ArrayList<>(points));
        this.color = color;
        this.width = width;
        this.kind = kind;
    }

    public List<PaintPoint> points() { return points; }
    public int color() { return color; }
    public float width() { return width; }
    public Kind kind() { return kind; }
}
