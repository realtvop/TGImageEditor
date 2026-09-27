package dev.realtvop.tgimageeditor.model;

/** Non-destructive adjustment values. Values use the editor's normalized -1..1 range. */
public final class FilterState {
    public static final FilterState NONE = new FilterState(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

    private final float enhance;
    private final float exposure;
    private final float contrast;
    private final float saturation;
    private final float warmth;
    private final float fade;
    private final float highlights;
    private final float shadows;
    private final float vignette;
    private final float grain;

    public FilterState(float enhance, float exposure, float contrast, float saturation,
                       float warmth, float fade, float highlights, float shadows,
                       float vignette, float grain) {
        this.enhance = normalized(enhance, "enhance");
        this.exposure = normalized(exposure, "exposure");
        this.contrast = normalized(contrast, "contrast");
        this.saturation = normalized(saturation, "saturation");
        this.warmth = normalized(warmth, "warmth");
        this.fade = normalized(fade, "fade");
        this.highlights = normalized(highlights, "highlights");
        this.shadows = normalized(shadows, "shadows");
        this.vignette = normalized(vignette, "vignette");
        this.grain = normalized(grain, "grain");
    }

    private static float normalized(float value, String name) {
        if (!Float.isFinite(value) || value < -1f || value > 1f) {
            throw new IllegalArgumentException(name + " must be in [-1, 1]");
        }
        return value;
    }

    public float enhance() { return enhance; }
    public float exposure() { return exposure; }
    public float contrast() { return contrast; }
    public float saturation() { return saturation; }
    public float warmth() { return warmth; }
    public float fade() { return fade; }
    public float highlights() { return highlights; }
    public float shadows() { return shadows; }
    public float vignette() { return vignette; }
    public float grain() { return grain; }
}

