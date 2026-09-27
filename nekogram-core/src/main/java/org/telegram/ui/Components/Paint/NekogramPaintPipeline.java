package org.telegram.ui.Components.Paint;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.opengl.GLES20;
import android.opengl.GLUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;
import javax.microedition.khronos.opengles.GL10;

import dev.realtvop.tgimageeditor.model.PaintPoint;
import dev.realtvop.tgimageeditor.model.PaintStroke;

/** Headless host around Nekogram's original brush shaders, stamps, and Render path code. */
public final class NekogramPaintPipeline {
    private static final int EGL_CONTEXT_CLIENT_VERSION = 0x3098;
    private static final int EGL_OPENGL_ES2_BIT = 4;
    private static final Object LOCK = new Object();

    private NekogramPaintPipeline() {}

    public static Bitmap render(Bitmap source, List<PaintStroke> strokes) {
        boolean hasBrushStroke = false;
        for (PaintStroke stroke : strokes) {
            if (isBrush(stroke.kind())) { hasBrushStroke = true; break; }
        }
        if (!hasBrushStroke) return source;
        synchronized (LOCK) { return renderLocked(source, strokes); }
    }

    private static Bitmap renderLocked(Bitmap source, List<PaintStroke> strokes) {
        EGL10 egl = (EGL10) EGLContext.getEGL();
        EGLDisplay display = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
        EGLContext context = EGL10.EGL_NO_CONTEXT;
        EGLSurface surface = EGL10.EGL_NO_SURFACE;
        try {
            check(display != EGL10.EGL_NO_DISPLAY, "eglGetDisplay", egl);
            check(egl.eglInitialize(display, new int[2]), "eglInitialize", egl);
            int[] spec = {EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
                    EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8, EGL10.EGL_BLUE_SIZE, 8,
                    EGL10.EGL_ALPHA_SIZE, 8, EGL10.EGL_SURFACE_TYPE, EGL10.EGL_PBUFFER_BIT,
                    EGL10.EGL_NONE};
            EGLConfig[] configs = new EGLConfig[1];
            int[] count = new int[1];
            check(egl.eglChooseConfig(display, spec, configs, 1, count) && count[0] > 0, "eglChooseConfig", egl);
            context = egl.eglCreateContext(display, configs[0], EGL10.EGL_NO_CONTEXT,
                    new int[]{EGL_CONTEXT_CLIENT_VERSION, 2, EGL10.EGL_NONE});
            check(context != EGL10.EGL_NO_CONTEXT, "eglCreateContext", egl);
            surface = egl.eglCreatePbufferSurface(display, configs[0],
                    new int[]{EGL10.EGL_WIDTH, 1, EGL10.EGL_HEIGHT, 1, EGL10.EGL_NONE});
            check(surface != EGL10.EGL_NO_SURFACE, "eglCreatePbufferSurface", egl);
            check(egl.eglMakeCurrent(display, surface, surface, context), "eglMakeCurrent", egl);

            int width = source.getWidth(), height = source.getHeight();
            float[] projection = GLMatrix.LoadOrtho(0, width, 0, height, -1, 1);
            FloatBuffer quad = floats(0, 0, width, 0, 0, height, width, height);
            FloatBuffer uv = floats(0, 0, 1, 0, 0, 1, 1, 1);
            int framebuffer = genFramebuffer();
            int paintTexture = emptyTexture(width, height);
            int maskTexture = emptyTexture(width, height);
            int blurredTexture = uploadTexture(createBlurredSource(source));
            Map<String, Shader> shaders = ShaderSet.setup();
            Map<Integer, Integer> stampTextures = new HashMap<>();

            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);
            for (PaintStroke stroke : strokes) {
                if (!isBrush(stroke.kind())) continue;
                Brush brush = brush(stroke.kind());
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer);
                GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                        GLES20.GL_TEXTURE_2D, maskTexture, 0);
                GLES20.glViewport(0, 0, width, height);
                GLES20.glClearColor(0, 0, 0, 0);
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

                Shader brushShader = shaders.get(brush.getShaderName(Brush.PAINT_TYPE_BRUSH));
                GLES20.glUseProgram(brushShader.program);
                int stamp = stampTextures.computeIfAbsent(brush.getStampResId(), id -> uploadTexture(brush.getStamp()));
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, stamp);
                GLES20.glUniformMatrix4fv(brushShader.getUniform("mvpMatrix"), 1, false, projection, 0);
                GLES20.glUniform1i(brushShader.getUniform("texture"), 0);
                RenderState renderState = new RenderState();
                renderState.viewportScale = 1f;
                Path path = path(stroke, width, height, brush);
                Render.RenderPath(path, renderState, false);

                GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                        GLES20.GL_TEXTURE_2D, paintTexture, 0);
                Shader composite = shaders.get(brush.getShaderName(Brush.PAINT_TYPE_COMPOSITE));
                GLES20.glUseProgram(composite.program);
                GLES20.glUniformMatrix4fv(composite.getUniform("mvpMatrix"), 1, false, projection, 0);
                GLES20.glUniform1i(composite.getUniform("texture"), 0);
                GLES20.glUniform1i(composite.getUniform("mask"), 1);
                Shader.SetColorUniform(composite.getUniform("color"), withAlpha(stroke.color(), brush.getOverrideAlpha()));
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, paintTexture);
                GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, maskTexture);
                if (brush instanceof Brush.Blurer) {
                    GLES20.glUniform1i(composite.getUniform("blured"), 2);
                    GLES20.glActiveTexture(GLES20.GL_TEXTURE2);
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, blurredTexture);
                }
                GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ZERO);
                quad.position(0); uv.position(0);
                GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 8, quad);
                GLES20.glEnableVertexAttribArray(0);
                GLES20.glVertexAttribPointer(1, 2, GLES20.GL_FLOAT, false, 8, uv);
                GLES20.glEnableVertexAttribArray(1);
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
                GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);
            }

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer);
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                    GLES20.GL_TEXTURE_2D, paintTexture, 0);
            ByteBuffer pixels = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder());
            GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixels);
            pixels.position(0);
            Bitmap overlay = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            overlay.copyPixelsFromBuffer(pixels);
            Bitmap output = source.copy(Bitmap.Config.ARGB_8888, true);
            new Canvas(output).drawBitmap(overlay, 0, 0, null);
            overlay.recycle();
            return output;
        } finally {
            if (display != EGL10.EGL_NO_DISPLAY) {
                egl.eglMakeCurrent(display, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
                if (surface != EGL10.EGL_NO_SURFACE) egl.eglDestroySurface(display, surface);
                if (context != EGL10.EGL_NO_CONTEXT) egl.eglDestroyContext(display, context);
                egl.eglTerminate(display);
            }
        }
    }

    private static Path path(PaintStroke stroke, int width, int height, Brush brush) {
        Point[] points = new Point[stroke.points().size()];
        for (int i = 0; i < points.length; i++) {
            PaintPoint point = stroke.points().get(i);
            points[i] = new Point(point.x() * width, point.y() * height, 1, i == 0 || i == points.length - 1);
        }
        Path path = new Path(points);
        path.setup(stroke.color(), stroke.width() * Math.min(width, height), brush);
        return path;
    }

    private static Brush brush(PaintStroke.Kind kind) {
        if (kind == PaintStroke.Kind.MARKER) return new Brush.Elliptical();
        if (kind == PaintStroke.Kind.NEON) return new Brush.Neon();
        if (kind == PaintStroke.Kind.BLUR) return new Brush.Blurer();
        if (kind == PaintStroke.Kind.ERASER) return new Brush.Eraser();
        return new Brush.Radial();
    }

    private static boolean isBrush(PaintStroke.Kind kind) {
        return kind == PaintStroke.Kind.PEN || kind == PaintStroke.Kind.MARKER
                || kind == PaintStroke.Kind.NEON || kind == PaintStroke.Kind.BLUR
                || kind == PaintStroke.Kind.ERASER;
    }

    private static int withAlpha(int color, float scale) {
        int alpha = Math.min(255, Math.round(((color >>> 24) & 255) * scale));
        return (color & 0x00ffffff) | (alpha << 24);
    }

    private static int genFramebuffer() {
        int[] id = new int[1]; GLES20.glGenFramebuffers(1, id, 0); return id[0];
    }

    private static int emptyTexture(int width, int height) {
        int[] id = new int[1]; GLES20.glGenTextures(1, id, 0);
        GLES20.glBindTexture(GL10.GL_TEXTURE_2D, id[0]);
        textureParams();
        GLES20.glTexImage2D(GL10.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        return id[0];
    }

    private static int uploadTexture(Bitmap bitmap) {
        int[] id = new int[1]; GLES20.glGenTextures(1, id, 0);
        GLES20.glBindTexture(GL10.GL_TEXTURE_2D, id[0]);
        textureParams();
        GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, bitmap, 0);
        return id[0];
    }

    private static void textureParams() {
        GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
        GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
        GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);
    }

    private static FloatBuffer floats(float... values) {
        ByteBuffer bytes = ByteBuffer.allocateDirect(values.length * 4).order(ByteOrder.nativeOrder());
        FloatBuffer result = bytes.asFloatBuffer(); result.put(values); result.position(0); return result;
    }

    /** Mirrors Painting.setBrush(Blurer): nearest 1/8 downsample then radius-8 fastBlurMore. */
    private static Bitmap createBlurredSource(Bitmap source) {
        int width = Math.max(1, source.getWidth() / 8), height = Math.max(1, source.getHeight() / 8);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawBitmap(source, null, new Rect(0, 0, width, height), null);
        stackBlurMore(bitmap, 8);
        return bitmap;
    }

    private static void stackBlurMore(Bitmap bitmap, int radius) {
        int width = bitmap.getWidth(), height = bitmap.getHeight(), r1 = radius + 1;
        if (radius > 15 || radius * 2 + 1 >= width || radius * 2 + 1 >= height
                || width * height > 150 * 150) return;
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        int[] output = new int[pixels.length];
        int[] source = new int[pixels.length];
        for (int channel = 0; channel < 4; channel++) {
            int shift = channel == 0 ? 0 : channel == 1 ? 8 : channel == 2 ? 16 : 24;
            for (int i = 0; i < pixels.length; i++) source[i] = (pixels[i] >>> shift) & 255;
            int[] horizontal = new int[pixels.length];
            for (int y = 0; y < height; y++) {
                int row = y * width;
                long current = source[row], all = -radius * current;
                long sum = current * ((r1 * (r1 + 1)) >> 1);
                for (int i = 1; i <= radius; i++) { current = source[row + i]; sum += current * (r1 - i); all += current; }
                for (int x = 0; x < width; x++) {
                    horizontal[row + x] = (int) ((sum >> 6) & 255);
                    int start = x < r1 ? 0 : x - r1;
                    int end = x < width - r1 ? x + r1 : width - 1;
                    all += source[row + start] - 2L * source[row + x] + source[row + end];
                    sum += all;
                }
            }
            for (int x = 0; x < width; x++) {
                long all = -radius * (long) horizontal[x];
                long sum = horizontal[x] * (long) ((r1 * (r1 + 1)) >> 1);
                for (int i = 1; i <= radius; i++) { sum += horizontal[i * width + x] * (long) (r1 - i); all += horizontal[i * width + x]; }
                for (int y = 0; y < height; y++) {
                    int value = (int) ((sum >> 6) & 255);
                    output[y * width + x] |= value << shift;
                    int start = y < r1 ? 0 : y - r1;
                    int end = y < height - r1 ? y + r1 : height - 1;
                    all += horizontal[start * width + x] - 2L * horizontal[y * width + x] + horizontal[end * width + x];
                    sum += all;
                }
            }
        }
        bitmap.setPixels(output, 0, width, 0, 0, width, height);
    }

    private static void check(boolean value, String operation, EGL10 egl) {
        if (!value) throw new IllegalStateException(operation + " failed: 0x" + Integer.toHexString(egl.eglGetError()));
    }
}
