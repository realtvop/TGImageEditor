package dev.realtvop.tgimageeditor.model;

/** Crop geometry normalized within the source after mirror and rotation are applied. */
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
    public float left() { return centerX - width / 2f; }
    public float top() { return centerY - height / 2f; }
    public float right() { return centerX + width / 2f; }
    public float bottom() { return centerY + height / 2f; }

    public CropState withBounds(float left, float top, float right, float bottom) {
        if (left < 0f || top < 0f || right > 1f || bottom > 1f || right <= left || bottom <= top) {
            throw new IllegalArgumentException("Crop bounds must form a positive rectangle inside the image");
        }
        return new CropState((left + right) / 2f, (top + bottom) / 2f,
                right - left, bottom - top, fineRotationDegrees, quarterTurns, mirrored);
    }

    public CropState rotateClockwise() {
        return new CropState(centerX, centerY, width, height, fineRotationDegrees,
                quarterTurns + 1, mirrored);
    }

    public CropState toggleMirror() {
        return new CropState(centerX, centerY, width, height, fineRotationDegrees,
                quarterTurns, !mirrored);
    }

    public CropState withFineRotation(float degrees) {
        if (!Float.isFinite(degrees) || degrees < -45f || degrees > 45f) {
            throw new IllegalArgumentException("Fine rotation must be in [-45, 45]");
        }
        return new CropState(centerX, centerY, width, height, degrees, quarterTurns, mirrored);
    }

    public boolean isIdentity() {
        return centerX == 0.5f && centerY == 0.5f && width == 1f && height == 1f
                && fineRotationDegrees == 0f && quarterTurns == 0 && !mirrored;
    }
}
