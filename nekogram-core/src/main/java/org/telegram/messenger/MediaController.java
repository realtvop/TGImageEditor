package org.telegram.messenger;

/** Compile-time shell for an upstream helper that is not part of the standalone runtime. */
public final class MediaController {
    private MediaController() {}
    public static final class SavedFilterState {
        public float softenSkinValue, shadowsValue, highlightsValue, enhanceValue, exposureValue;
        public float contrastValue, warmthValue, vignetteValue, sharpenValue, grainValue, fadeValue;
        public float saturationValue, blurExcludeSize, blurExcludeBlurSize, blurAngle;
        public int tintHighlightsColor, tintShadowsColor, blurType;
        public android.graphics.PointF blurExcludePoint = new android.graphics.PointF(.5f, .5f);
        public CurvesToolValue curvesToolValue = new CurvesToolValue();
    }
    public static final class CurvesToolValue {
        public java.nio.ByteBuffer curveBuffer = java.nio.ByteBuffer.allocateDirect(800);
        public boolean shouldBeSkipped() { return true; }
        public void fillBuffer() {}
    }
}
