package dev.realtvop.tgimageeditor.model;

public final class BlurState implements java.io.Serializable {
    public enum Type { NONE, RADIAL, LINEAR }
    /** Nekogram's default linear blur axis is vertical (PI/2 radians). */
    public static final BlurState NONE = new BlurState(Type.NONE, .5f, .5f, .35f, .15f, 90f);
    private final Type type;
    private final float centerX, centerY, size, feather, angle;

    public BlurState(Type type, float centerX, float centerY, float size, float feather, float angle) {
        this.type = java.util.Objects.requireNonNull(type, "type");
        this.centerX = unit(centerX); this.centerY = unit(centerY); this.size = unit(size); this.feather = unit(feather);
        if (!Float.isFinite(angle)) throw new IllegalArgumentException("Invalid blur angle");
        this.angle = angle;
    }

    private static float unit(float value) {
        if (!Float.isFinite(value) || value < 0f || value > 1f) throw new IllegalArgumentException("Blur value must be in [0, 1]");
        return value;
    }

    public Type type() { return type; }
    public float centerX() { return centerX; }
    public float centerY() { return centerY; }
    public float size() { return size; }
    public float feather() { return feather; }
    public float angle() { return angle; }
    public BlurState withType(Type v) { return new BlurState(v, centerX, centerY, size, feather, angle); }
    public BlurState withSize(float v) { return new BlurState(type, centerX, centerY, v, feather, angle); }
    public BlurState withFeather(float v) { return new BlurState(type, centerX, centerY, size, v, angle); }
    public BlurState withAngle(float v) { return new BlurState(type, centerX, centerY, size, feather, v); }
}
