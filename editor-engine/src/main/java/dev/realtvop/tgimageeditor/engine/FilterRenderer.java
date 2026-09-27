package dev.realtvop.tgimageeditor.engine;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import dev.realtvop.tgimageeditor.model.BlurState;
import dev.realtvop.tgimageeditor.model.FilterState;

/** Static-image implementation of Telegram's adjustment, curve, sharpen and focus-blur stages. */
public final class FilterRenderer {
    private FilterRenderer() {}

    public static Bitmap render(Bitmap source, FilterState state) {
        if (state.isIdentity()) return source;
        Bitmap output = colorAdjust(source, state);
        applyPixelAdjustments(output, state);
        Bitmap blurred = applyBlurEffects(output, state);
        if (blurred != output) output.recycle();
        return blurred;
    }

    private static Bitmap colorAdjust(Bitmap source, FilterState state) {
        ColorMatrix matrix = new ColorMatrix();
        float exposure = (float) Math.pow(2.0, state.exposure());
        matrix.postConcat(scale(exposure, exposure, exposure));
        float contrast = 1f + state.contrast();
        float translate = 128f * (1f - contrast);
        matrix.postConcat(new ColorMatrix(new float[]{
                contrast, 0, 0, 0, translate, 0, contrast, 0, 0, translate,
                0, 0, contrast, 0, translate, 0, 0, 0, 1, 0
        }));
        ColorMatrix saturation = new ColorMatrix();
        saturation.setSaturation(Math.max(0f, 1f + state.saturation()));
        matrix.postConcat(saturation);
        float warmth = state.warmth() * .2f;
        matrix.postConcat(scale(1f + warmth, 1f, 1f - warmth));
        if (state.fade() > 0f) {
            float amount = state.fade() * .35f;
            float remaining = 1f - amount;
            matrix.postConcat(new ColorMatrix(new float[]{
                    remaining, 0, 0, 0, 128f * amount, 0, remaining, 0, 0, 128f * amount,
                    0, 0, remaining, 0, 128f * amount, 0, 0, 0, 1, 0
            }));
        }
        Bitmap output = Bitmap.createBitmap(source.getWidth(), source.getHeight(), Bitmap.Config.ARGB_8888);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        paint.setColorFilter(new ColorMatrixColorFilter(matrix));
        new Canvas(output).drawBitmap(source, 0, 0, paint);
        return output;
    }

    private static void applyPixelAdjustments(Bitmap bitmap, FilterState state) {
        boolean needed = state.enhance() != 0f || state.highlights() != 0f || state.shadows() != 0f
                || state.vignette() != 0f || state.grain() != 0f || state.sharpen() != 0f
                || !state.curve().isLinear();
        if (!needed) return;
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        int low = 0, high = 255;
        if (state.enhance() > 0f) {
            int[] histogram = new int[256];
            for (int color : pixels) histogram[luma(color)]++;
            int threshold = Math.max(1, pixels.length / 200);
            int count = 0;
            while (low < 255 && (count += histogram[low]) < threshold) low++;
            count = 0;
            while (high > 0 && (count += histogram[high]) < threshold) high--;
        }
        float cx = width / 2f, cy = height / 2f;
        float maxRadius = (float) Math.hypot(cx, cy);
        int[] original = state.sharpen() > 0f ? pixels.clone() : pixels;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                int color = original[index];
                int a = color >>> 24;
                int r = (color >> 16) & 255, g = (color >> 8) & 255, b = color & 255;
                if (state.enhance() > 0f && high > low) {
                    r = blend(r, levels(r, low, high), state.enhance());
                    g = blend(g, levels(g, low, high), state.enhance());
                    b = blend(b, levels(b, low, high), state.enhance());
                }
                r = state.curve().map(r); g = state.curve().map(g); b = state.curve().map(b);
                float luminance = (r * .299f + g * .587f + b * .114f) / 255f;
                float lift = state.shadows() * (1f - luminance) * 72f + state.highlights() * luminance * 72f;
                r += Math.round(lift); g += Math.round(lift); b += Math.round(lift);
                if (state.sharpen() > 0f && x > 0 && x < width - 1 && y > 0 && y < height - 1) {
                    int left = original[index - 1], right = original[index + 1];
                    int up = original[index - width], down = original[index + width];
                    float amount = state.sharpen() * .7f;
                    r += Math.round((r * 4 - channel(left, 16) - channel(right, 16) - channel(up, 16) - channel(down, 16)) * amount);
                    g += Math.round((g * 4 - channel(left, 8) - channel(right, 8) - channel(up, 8) - channel(down, 8)) * amount);
                    b += Math.round((b * 4 - channel(left, 0) - channel(right, 0) - channel(up, 0) - channel(down, 0)) * amount);
                }
                if (state.vignette() > 0f) {
                    float distance = (float) Math.hypot(x - cx, y - cy) / maxRadius;
                    float factor = 1f - state.vignette() * Math.max(0f, distance - .25f) * .85f;
                    r *= factor; g *= factor; b *= factor;
                }
                if (state.grain() > 0f) {
                    int noise = (((x * 73856093) ^ (y * 19349663)) & 255) - 128;
                    float amount = state.grain() * .22f;
                    r += Math.round(noise * amount); g += Math.round(noise * amount); b += Math.round(noise * amount);
                }
                pixels[index] = (a << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
            }
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
    }

    private static Bitmap applyBlurEffects(Bitmap source, FilterState state) {
        if (state.softenSkin() <= 0f && state.blur().type() == BlurState.Type.NONE) return source;
        int width = source.getWidth(), height = source.getHeight();
        Bitmap tiny = Bitmap.createScaledBitmap(source, Math.max(1, width / 18), Math.max(1, height / 18), true);
        Bitmap blurred = Bitmap.createScaledBitmap(tiny, width, height, true);
        tiny.recycle();
        Bitmap output = source.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(output);
        if (state.softenSkin() > 0f) {
            Paint soften = new Paint(Paint.FILTER_BITMAP_FLAG);
            soften.setAlpha(Math.round(state.softenSkin() * 90f));
            canvas.drawBitmap(blurred, 0, 0, soften);
        }
        BlurState blur = state.blur();
        if (blur.type() != BlurState.Type.NONE) {
            Canvas blurCanvas = new Canvas(blurred);
            Paint mask = new Paint(Paint.ANTI_ALIAS_FLAG);
            mask.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
            if (blur.type() == BlurState.Type.RADIAL) {
                float radius = (float) Math.hypot(width, height);
                float edge = Math.min(.99f, blur.size() + Math.max(.01f, blur.feather()));
                mask.setShader(new RadialGradient(blur.centerX() * width, blur.centerY() * height, radius,
                        new int[]{0x00000000, 0x00000000, 0xff000000},
                        new float[]{0f, blur.size(), edge}, Shader.TileMode.CLAMP));
            } else {
                double angle = Math.toRadians(blur.angle());
                float dx = (float) Math.cos(angle) * width;
                float dy = (float) Math.sin(angle) * height;
                float inner = Math.max(.01f, .5f - blur.size() * .5f);
                float outer = Math.max(0f, inner - blur.feather() * .5f);
                mask.setShader(new LinearGradient(width / 2f - dx, height / 2f - dy,
                        width / 2f + dx, height / 2f + dy,
                        new int[]{0xff000000, 0x00000000, 0x00000000, 0xff000000},
                        new float[]{0f, outer, 1f - outer, 1f}, Shader.TileMode.CLAMP));
            }
            blurCanvas.drawRect(0, 0, width, height, mask);
            canvas.drawBitmap(blurred, 0, 0, null);
        }
        blurred.recycle();
        return output;
    }

    private static int luma(int c) { return Math.round(((c >> 16 & 255) * .299f) + ((c >> 8 & 255) * .587f) + ((c & 255) * .114f)); }
    private static int channel(int c, int shift) { return (c >> shift) & 255; }
    private static int levels(int value, int low, int high) { return clamp(Math.round((value - low) * 255f / (high - low))); }
    private static int blend(int a, int b, float amount) { return Math.round(a + (b - a) * amount); }
    private static int clamp(int value) { return Math.max(0, Math.min(255, value)); }

    private static ColorMatrix scale(float red, float green, float blue) {
        return new ColorMatrix(new float[]{red, 0, 0, 0, 0, 0, green, 0, 0, 0,
                0, 0, blue, 0, 0, 0, 0, 0, 1, 0});
    }
}
