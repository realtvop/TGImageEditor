package dev.realtvop.tgimageeditor.model;

public final class PaintPoint {
    private final float x;
    private final float y;

    public PaintPoint(float x, float y) {
        if (!Float.isFinite(x) || !Float.isFinite(y) || x < 0f || x > 1f || y < 0f || y > 1f) {
            throw new IllegalArgumentException("Paint coordinates must be in [0, 1]");
        }
        this.x = x;
        this.y = y;
    }

    public float x() { return x; }
    public float y() { return y; }
}

