package dev.realtvop.tgimageeditor.nekogram;

import android.graphics.Bitmap;
import android.graphics.PointF;
import android.opengl.GLES20;

import org.telegram.ui.Components.FilterShaders;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

import dev.realtvop.tgimageeditor.model.BlurState;
import dev.realtvop.tgimageeditor.model.FilterState;
import dev.realtvop.tgimageeditor.model.ToneCurve;

/**
 * Headless static-image host for Nekogram's unmodified FilterShaders pipeline.
 * Pass order and readback match FilterGLThread at Nekogram revision e924154e.
 */
public final class NekogramFilterPipeline {
    private static final int EGL_CONTEXT_CLIENT_VERSION = 0x3098;
    private static final int EGL_OPENGL_ES2_BIT = 4;
    private static final Object EGL_LOCK = new Object();

    private NekogramFilterPipeline() {}

    public static Bitmap render(Bitmap source, FilterState state) {
        if (source == null || source.isRecycled()) throw new IllegalArgumentException("Source bitmap is unavailable");
        if (state == null) throw new NullPointerException("state");
        if (state.isIdentity()) return source;
        synchronized (EGL_LOCK) {
            return renderLocked(source, state);
        }
    }

    private static Bitmap renderLocked(Bitmap source, FilterState state) {
        EGL10 egl = (EGL10) EGLContext.getEGL();
        EGLDisplay display = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
        EGLContext context = EGL10.EGL_NO_CONTEXT;
        EGLSurface surface = EGL10.EGL_NO_SURFACE;
        try {
            check(display != EGL10.EGL_NO_DISPLAY, "eglGetDisplay", egl);
            check(egl.eglInitialize(display, new int[2]), "eglInitialize", egl);

            int[] configSpec = {
                    EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
                    EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8,
                    EGL10.EGL_BLUE_SIZE, 8, EGL10.EGL_ALPHA_SIZE, 8,
                    EGL10.EGL_SURFACE_TYPE, EGL10.EGL_PBUFFER_BIT,
                    EGL10.EGL_NONE
            };
            EGLConfig[] configs = new EGLConfig[1];
            int[] count = new int[1];
            check(egl.eglChooseConfig(display, configSpec, configs, 1, count) && count[0] > 0,
                    "eglChooseConfig", egl);
            context = egl.eglCreateContext(display, configs[0], EGL10.EGL_NO_CONTEXT,
                    new int[]{EGL_CONTEXT_CLIENT_VERSION, 2, EGL10.EGL_NONE});
            check(context != null && context != EGL10.EGL_NO_CONTEXT, "eglCreateContext", egl);
            surface = egl.eglCreatePbufferSurface(display, configs[0], new int[]{
                    EGL10.EGL_WIDTH, 1, EGL10.EGL_HEIGHT, 1, EGL10.EGL_NONE
            });
            check(surface != null && surface != EGL10.EGL_NO_SURFACE, "eglCreatePbufferSurface", egl);
            check(egl.eglMakeCurrent(display, surface, surface, context), "eglMakeCurrent", egl);

            FilterShaders shaders = new FilterShaders(false, null);
            shaders.setScaleBitmap(true);
            shaders.setDelegate(new StateDelegate(state));
            check(shaders.create(), "FilterShaders.create", egl);
            shaders.setRenderData(source, 0, 0, source.getWidth(), source.getHeight());

            int width = shaders.getRenderBufferWidth();
            int height = shaders.getRenderBufferHeight();
            GLES20.glViewport(0, 0, width, height);
            shaders.drawSkinSmoothPass();
            shaders.drawEnhancePass();
            shaders.drawSharpenPass();
            shaders.drawCustomParamsPass();
            boolean blurred = shaders.drawBlurPass();

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, shaders.getRenderFrameBuffer());
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                    GLES20.GL_TEXTURE_2D, shaders.getRenderTexture(blurred ? 0 : 1), 0);
            checkGl("render", GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER));

            ByteBuffer pixels = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder());
            GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixels);
            checkGl("glReadPixels", GLES20.glGetError());
            pixels.position(0);
            Bitmap output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            output.copyPixelsFromBuffer(pixels);
            return output;
        } finally {
            if (display != EGL10.EGL_NO_DISPLAY) {
                egl.eglMakeCurrent(display, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
                if (surface != null && surface != EGL10.EGL_NO_SURFACE) egl.eglDestroySurface(display, surface);
                if (context != null && context != EGL10.EGL_NO_CONTEXT) egl.eglDestroyContext(display, context);
                egl.eglTerminate(display);
            }
        }
    }

    private static void check(boolean ok, String operation, EGL10 egl) {
        if (!ok) throw new IllegalStateException(operation + " failed: 0x" + Integer.toHexString(egl.eglGetError()));
    }

    private static void checkGl(String operation, int value) {
        if (operation.equals("render")) {
            if (value != GLES20.GL_FRAMEBUFFER_COMPLETE) {
                throw new IllegalStateException(operation + " framebuffer incomplete: 0x" + Integer.toHexString(value));
            }
        } else if (value != GLES20.GL_NO_ERROR) {
            throw new IllegalStateException(operation + " failed: 0x" + Integer.toHexString(value));
        }
    }

    private static final class StateDelegate implements FilterShaders.FilterShadersDelegate {
        private final FilterState state;
        private ByteBuffer curveBuffer;

        StateDelegate(FilterState state) { this.state = state; }
        @Override public boolean shouldShowOriginal() { return false; }
        @Override public float getShadowsValue() { return state.shadows() * .55f + 1f; }
        @Override public float getSoftenSkinValue() { return Math.max(0f, state.softenSkin()); }
        @Override public float getHighlightsValue() { return state.highlights() * .75f + 1f; }
        @Override public float getEnhanceValue() { return Math.max(0f, state.enhance()); }
        @Override public float getExposureValue() { return state.exposure(); }
        @Override public float getContrastValue() { return state.contrast() * .3f + 1f; }
        @Override public float getWarmthValue() { return state.warmth(); }
        @Override public float getVignetteValue() { return Math.max(0f, state.vignette()); }
        @Override public float getSharpenValue() { return .11f + Math.max(0f, state.sharpen()) * .6f; }
        @Override public float getGrainValue() { return Math.max(0f, state.grain()) * .04f; }
        @Override public float getFadeValue() { return Math.max(0f, state.fade()); }
        @Override public float getTintHighlightsIntensityValue() { return 0f; }
        @Override public float getTintShadowsIntensityValue() { return 0f; }
        @Override public float getSaturationValue() {
            float value = state.saturation();
            if (value > 0) value *= 1.05f;
            return value + 1f;
        }
        @Override public int getTintHighlightsColor() { return 0; }
        @Override public int getTintShadowsColor() { return 0; }
        @Override public int getBlurType() {
            return state.blur().type() == BlurState.Type.RADIAL ? 1
                    : state.blur().type() == BlurState.Type.LINEAR ? 2 : 0;
        }
        @Override public float getBlurExcludeSize() { return state.blur().size(); }
        @Override public float getBlurExcludeBlurSize() { return state.blur().feather(); }
        @Override public float getBlurAngle() { return (float) Math.toRadians(state.blur().angle()); }
        @Override public PointF getBlurExcludePoint() {
            return new PointF(state.blur().centerX(), state.blur().centerY());
        }
        @Override public boolean shouldDrawCurvesPass() { return !state.curve().isLinear(); }
        @Override public ByteBuffer fillAndGetCurveBuffer() {
            if (curveBuffer == null) curveBuffer = createCurveBuffer(state.curve());
            curveBuffer.position(0);
            return curveBuffer;
        }
    }

    private static ByteBuffer createCurveBuffer(ToneCurve curve) {
        float[] values = interpolateCurve(curve);
        ByteBuffer buffer = ByteBuffer.allocateDirect(200 * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < 200; i++) {
            byte value = (byte) (values[i] * 255f);
            buffer.put(value).put(value).put(value).put((byte) Math.min(255, Math.round(i / 199f * 255f)));
        }
        buffer.position(0);
        return buffer;
    }

    /** Catmull-Rom sampling copied from PhotoFilterView.CurvesValue. */
    private static float[] interpolateCurve(ToneCurve curve) {
        float[] points = {
                -.001f, curve.blacks(), 0f, curve.blacks(), .25f, curve.shadows(),
                .5f, curve.midtones(), .75f, curve.highlights(), 1f, curve.whites(),
                1.001f, curve.whites()
        };
        float[] data = new float[200];
        int out = 0;
        for (int index = 1; index < points.length / 2 - 2; index++) {
            float p0x = points[(index - 1) * 2], p0y = points[(index - 1) * 2 + 1];
            float p1x = points[index * 2], p1y = points[index * 2 + 1];
            float p2x = points[(index + 1) * 2], p2y = points[(index + 1) * 2 + 1];
            float p3x = points[(index + 2) * 2], p3y = points[(index + 2) * 2 + 1];
            for (int i = 1; i < 100; i++) {
                float t = i / 100f, tt = t * t, ttt = tt * t;
                float pix = .5f * (2 * p1x + (p2x - p0x) * t
                        + (2 * p0x - 5 * p1x + 4 * p2x - p3x) * tt
                        + (3 * p1x - p0x - 3 * p2x + p3x) * ttt);
                float piy = .5f * (2 * p1y + (p2y - p0y) * t
                        + (2 * p0y - 5 * p1y + 4 * p2y - p3y) * tt
                        + (3 * p1y - p0y - 3 * p2y + p3y) * ttt);
                piy = Math.max(0f, Math.min(1f, piy));
                if (pix > p0x && (i - 1) % 2 == 0 && out < data.length) data[out++] = piy;
            }
        }
        while (out < data.length) data[out] = out++ / 199f;
        return data;
    }
}
