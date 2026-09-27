package dev.realtvop.tgimageeditor.model;

/** Five-point luminance curve matching Telegram's 0/25/50/75/100 control layout. */
public final class ToneCurve {
    public static final ToneCurve LINEAR = new ToneCurve(0f, .25f, .5f, .75f, 1f);
    private final float blacks, shadows, midtones, highlights, whites;

    public ToneCurve(float blacks, float shadows, float midtones, float highlights, float whites) {
        this.blacks = unit(blacks); this.shadows = unit(shadows); this.midtones = unit(midtones);
        this.highlights = unit(highlights); this.whites = unit(whites);
    }

    private static float unit(float value) {
        if (!Float.isFinite(value) || value < 0f || value > 1f) throw new IllegalArgumentException("Curve level must be in [0, 1]");
        return value;
    }

    public float blacks() { return blacks; }
    public float shadows() { return shadows; }
    public float midtones() { return midtones; }
    public float highlights() { return highlights; }
    public float whites() { return whites; }
    public boolean isLinear() { return blacks == 0f && shadows == .25f && midtones == .5f && highlights == .75f && whites == 1f; }
    public ToneCurve withBlacks(float v) { return new ToneCurve(v, shadows, midtones, highlights, whites); }
    public ToneCurve withShadows(float v) { return new ToneCurve(blacks, v, midtones, highlights, whites); }
    public ToneCurve withMidtones(float v) { return new ToneCurve(blacks, shadows, v, highlights, whites); }
    public ToneCurve withHighlights(float v) { return new ToneCurve(blacks, shadows, midtones, v, whites); }
    public ToneCurve withWhites(float v) { return new ToneCurve(blacks, shadows, midtones, highlights, v); }

    public int map(int input) {
        float x = Math.max(0, Math.min(255, input)) / 255f;
        float scaled = x * 4f;
        int segment = Math.min(3, (int) scaled);
        float t = scaled - segment;
        float[] y = {blacks, shadows, midtones, highlights, whites};
        return Math.max(0, Math.min(255, Math.round((y[segment] + (y[segment + 1] - y[segment]) * t) * 255f)));
    }
}

