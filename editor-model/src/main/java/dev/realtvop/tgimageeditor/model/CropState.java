package dev.realtvop.tgimageeditor.model;

/** Crop geometry in normalized, orientation-corrected source coordinates. */
public final class CropState {
    public static final CropState FULL_IMAGE = new CropState(0.5f, 0.5f, 1f, 1f, 0f, 0, false);

    private final float centerX;
    private final float centerY;
    private final float width;
    private final float height;
    private final float fineRotationDegrees;
    private final int quarterTurns;
    private final boolean mirrored;

    public CropState(float centerX, float centerY, float width, float height,
                     float fineRotationDegrees, int quarterTurns, boolean mirrored) {
        requireUnit(centerX, "centerX");
        requireUnit(centerY, "centerY");
        requirePositiveUnit(width, "width");
        requirePositiveUnit(height, "height");
        if (!Float.isFinite(fineRotationDegrees)) {
            throw new IllegalArgumentException("fineRotationDegrees must be finite");
        }
        this.centerX = centerX;
        this.centerY = centerY;
        this.width = width;
        this.height = height;
        this.fineRotationDegrees = fineRotationDegrees;
        this.quarterTurns = Math.floorMod(quarterTurns, 4);
        this.mirrored = mirrored;
    }

    private static void requireUnit(float value, String name) {
        if (!Float.isFinite(value) || value < 0f || value > 1f) {
            throw new IllegalArgumentException(name + " must be in [0, 1]");
        }
    }

    private static void requirePositiveUnit(float value, String name) {
        if (!Float.isFinite(value) || value <= 0f || value > 1f) {
            throw new IllegalArgumentException(name + " must be in (0, 1]");
        }
    }

    public float centerX() { return centerX; }
    public float centerY() { return centerY; }
    public float width() { return width; }
    public float height() { return height; }
    public float fineRotationDegrees() { return fineRotationDegrees; }
    public int quarterTurns() { return quarterTurns; }
    public boolean mirrored() { return mirrored; }

    public boolean isIdentity() {
        return centerX == 0.5f && centerY == 0.5f && width == 1f && height == 1f
                && fineRotationDegrees == 0f && quarterTurns == 0 && !mirrored;
    }
}

