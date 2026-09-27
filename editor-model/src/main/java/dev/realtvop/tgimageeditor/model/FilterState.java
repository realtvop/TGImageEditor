package dev.realtvop.tgimageeditor.model;

public final class FilterState implements java.io.Serializable {
    public static final FilterState NONE = new FilterState(0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, ToneCurve.LINEAR, BlurState.NONE);
    private final float enhance, exposure, contrast, saturation, warmth, fade;
    private final float highlights, shadows, vignette, grain, sharpen, softenSkin;
    private final ToneCurve curve;
    private final BlurState blur;

    public FilterState(float enhance, float exposure, float contrast, float saturation,
                       float warmth, float fade, float highlights, float shadows,
                       float vignette, float grain) {
        this(enhance, exposure, contrast, saturation, warmth, fade, highlights, shadows,
                vignette, grain, 0f, 0f, ToneCurve.LINEAR, BlurState.NONE);
    }

    private FilterState(float enhance, float exposure, float contrast, float saturation,
                        float warmth, float fade, float highlights, float shadows,
                        float vignette, float grain, float sharpen, float softenSkin,
                        ToneCurve curve, BlurState blur) {
        this.enhance = normalized(enhance, "enhance"); this.exposure = normalized(exposure, "exposure");
        this.contrast = normalized(contrast, "contrast"); this.saturation = normalized(saturation, "saturation");
        this.warmth = normalized(warmth, "warmth"); this.fade = normalized(fade, "fade");
        this.highlights = normalized(highlights, "highlights"); this.shadows = normalized(shadows, "shadows");
        this.vignette = normalized(vignette, "vignette"); this.grain = normalized(grain, "grain");
        this.sharpen = normalized(sharpen, "sharpen"); this.softenSkin = normalized(softenSkin, "softenSkin");
        this.curve = java.util.Objects.requireNonNull(curve, "curve");
        this.blur = java.util.Objects.requireNonNull(blur, "blur");
    }

    private static float normalized(float value, String name) {
        if (!Float.isFinite(value) || value < -1f || value > 1f) throw new IllegalArgumentException(name + " must be in [-1, 1]");
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
    public float sharpen() { return sharpen; }
    public float softenSkin() { return softenSkin; }
    public ToneCurve curve() { return curve; }
    public BlurState blur() { return blur; }

    public boolean isIdentity() {
        return enhance == 0f && exposure == 0f && contrast == 0f && saturation == 0f && warmth == 0f
                && fade == 0f && highlights == 0f && shadows == 0f && vignette == 0f && grain == 0f
                && sharpen == 0f && softenSkin == 0f && curve.isLinear() && blur.type() == BlurState.Type.NONE;
    }

    public FilterState withEnhance(float v) { return copy(v, exposure, contrast, saturation, warmth, fade, highlights, shadows, vignette, grain, sharpen, softenSkin, curve, blur); }
    public FilterState withExposure(float v) { return copy(enhance, v, contrast, saturation, warmth, fade, highlights, shadows, vignette, grain, sharpen, softenSkin, curve, blur); }
    public FilterState withContrast(float v) { return copy(enhance, exposure, v, saturation, warmth, fade, highlights, shadows, vignette, grain, sharpen, softenSkin, curve, blur); }
    public FilterState withSaturation(float v) { return copy(enhance, exposure, contrast, v, warmth, fade, highlights, shadows, vignette, grain, sharpen, softenSkin, curve, blur); }
    public FilterState withWarmth(float v) { return copy(enhance, exposure, contrast, saturation, v, fade, highlights, shadows, vignette, grain, sharpen, softenSkin, curve, blur); }
    public FilterState withFade(float v) { return copy(enhance, exposure, contrast, saturation, warmth, v, highlights, shadows, vignette, grain, sharpen, softenSkin, curve, blur); }
    public FilterState withHighlights(float v) { return copy(enhance, exposure, contrast, saturation, warmth, fade, v, shadows, vignette, grain, sharpen, softenSkin, curve, blur); }
    public FilterState withShadows(float v) { return copy(enhance, exposure, contrast, saturation, warmth, fade, highlights, v, vignette, grain, sharpen, softenSkin, curve, blur); }
    public FilterState withVignette(float v) { return copy(enhance, exposure, contrast, saturation, warmth, fade, highlights, shadows, v, grain, sharpen, softenSkin, curve, blur); }
    public FilterState withGrain(float v) { return copy(enhance, exposure, contrast, saturation, warmth, fade, highlights, shadows, vignette, v, sharpen, softenSkin, curve, blur); }
    public FilterState withSharpen(float v) { return copy(enhance, exposure, contrast, saturation, warmth, fade, highlights, shadows, vignette, grain, v, softenSkin, curve, blur); }
    public FilterState withSoftenSkin(float v) { return copy(enhance, exposure, contrast, saturation, warmth, fade, highlights, shadows, vignette, grain, sharpen, v, curve, blur); }
    public FilterState withCurve(ToneCurve v) { return copy(enhance, exposure, contrast, saturation, warmth, fade, highlights, shadows, vignette, grain, sharpen, softenSkin, v, blur); }
    public FilterState withBlur(BlurState v) { return copy(enhance, exposure, contrast, saturation, warmth, fade, highlights, shadows, vignette, grain, sharpen, softenSkin, curve, v); }

    private static FilterState copy(float enhance, float exposure, float contrast, float saturation,
                                    float warmth, float fade, float highlights, float shadows,
                                    float vignette, float grain, float sharpen, float softenSkin,
                                    ToneCurve curve, BlurState blur) {
        return new FilterState(enhance, exposure, contrast, saturation, warmth, fade, highlights,
                shadows, vignette, grain, sharpen, softenSkin, curve, blur);
    }
}
