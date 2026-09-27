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
import dev.realtvop.tgimageeditor.nekogram.NekogramFilterPipeline;

/** Nekogram GL filter pipeline plus the shared Gaussian helper used by the blur brush. */
public final class FilterRenderer {
    private FilterRenderer() {}

    public static Bitmap render(Bitmap source, FilterState state) {
        return NekogramFilterPipeline.render(source, state);
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
        Bitmap output = source.copy(Bitmap.Config.ARGB_8888, true);
        Bitmap blurred = null;
        if (state.softenSkin() > 0f || state.blur().type() != BlurState.Type.NONE) {
            blurred = gaussianBlur(source, 8, 3f);
        }
        int[] sharpPixels = new int[width * height];
        int[] blurPixels = blurred == null ? null : new int[width * height];
        output.getPixels(sharpPixels, 0, width, 0, 0, width, height);
        if (blurred != null) blurred.getPixels(blurPixels, 0, width, 0, 0, width, height);
        BlurState blur = state.blur();
        float aspect = height / (float) width;
        float angle = (float) Math.toRadians(blur.angle());
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                int sharp = sharpPixels[index];
                int blurredColor = blurPixels == null ? sharp : blurPixels[index];
                float blurMix = state.softenSkin() > 0f ? state.softenSkin() * .65f : 0f;
                if (blur.type() != BlurState.Type.NONE) {
                    // This is the same coordinate normalization used by Nekogram's
                    // radialBlurFragmentShaderCode and linearBlurFragmentShaderCode.
                    float tx = x / (float) Math.max(1, width - 1);
                    float ty = y / (float) Math.max(1, height - 1) * aspect + .5f - .5f * aspect;
                    float dx = tx - blur.centerX();
                    float dy = ty - blur.centerY();
                    float distance;
                    if (blur.type() == BlurState.Type.RADIAL) {
                        distance = (float) Math.hypot(dx, dy);
                    } else {
                        distance = Math.abs(dx * aspect * (float) Math.cos(angle) + dy * (float) Math.sin(angle));
                    }
                    blurMix = smoothstep(blur.size() - blur.feather(), blur.size(), distance);
                }
                if (blurMix > 0f) {
                    sharpPixels[index] = mixColor(sharp, blurredColor, Math.min(1f, blurMix));
                }
            }
        }
        output.setPixels(sharpPixels, 0, width, 0, 0, width, height);
        if (blurred != null) blurred.recycle();
        return output;
    }

    /** Separable Gaussian pass matching Nekogram's fixed radius-8, sigma-3 blur program. */
    private static Bitmap gaussianBlur(Bitmap source, int radius, float sigma) {
        return gaussianBlur(source, radius, sigma, 2048);
    }

    /** Shared by the focus filter and the painting blurer. */
    public static Bitmap gaussianBlurCopy(Bitmap source, int maximumDimension) {
        if (source == null || source.isRecycled()) throw new IllegalArgumentException("Source bitmap is unavailable");
        return gaussianBlur(source, 8, 3f, Math.max(256, maximumDimension));
    }

    private static Bitmap gaussianBlur(Bitmap source, int radius, float sigma, int maximumDimension) {
        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();
        float scale = Math.min(1f, maximumDimension / (float) Math.max(sourceWidth, sourceHeight));
        int width = Math.max(1, Math.round(sourceWidth * scale));
        int height = Math.max(1, Math.round(sourceHeight * scale));
        Bitmap working = source;
        if (width != sourceWidth || height != sourceHeight) {
            working = Bitmap.createScaledBitmap(source, width, height, true);
        }
        int[] input = new int[width * height];
        int[] horizontal = new int[input.length];
        int[] result = new int[input.length];
        working.getPixels(input, 0, width, 0, 0, width, height);
        float[] weights = gaussianWeights(radius, sigma);
        for (int y = 0; y < height; y++) {
            int row = y * width;
            for (int x = 0; x < width; x++) {
                float a = 0f, r = 0f, g = 0f, b = 0f;
                for (int offset = -radius; offset <= radius; offset++) {
                    int sampleX = Math.max(0, Math.min(width - 1, x + offset));
                    int color = input[row + sampleX];
                    float weight = weights[offset + radius];
                    a += (color >>> 24) * weight;
                    r += ((color >>> 16) & 255) * weight;
                    g += ((color >>> 8) & 255) * weight;
                    b += (color & 255) * weight;
                }
                horizontal[row + x] = argb(a, r, g, b);
            }
        }
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float a = 0f, r = 0f, g = 0f, b = 0f;
                for (int offset = -radius; offset <= radius; offset++) {
                    int sampleY = Math.max(0, Math.min(height - 1, y + offset));
                    int color = horizontal[sampleY * width + x];
                    float weight = weights[offset + radius];
                    a += (color >>> 24) * weight;
                    r += ((color >>> 16) & 255) * weight;
                    g += ((color >>> 8) & 255) * weight;
                    b += (color & 255) * weight;
                }
                result[y * width + x] = argb(a, r, g, b);
            }
        }
        Bitmap blurred = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        blurred.setPixels(result, 0, width, 0, 0, width, height);
        if (working != source) working.recycle();
        if (width != sourceWidth || height != sourceHeight) {
            Bitmap scaled = Bitmap.createScaledBitmap(blurred, sourceWidth, sourceHeight, true);
            blurred.recycle();
            return scaled;
        }
        return blurred;
    }

    private static float[] gaussianWeights(int radius, float sigma) {
        float[] weights = new float[radius * 2 + 1];
        float sum = 0f;
        for (int i = 0; i <= radius; i++) {
            float weight = (float) (Math.exp(-(i * i) / (2f * sigma * sigma))
                    / Math.sqrt(2f * Math.PI * sigma * sigma));
            weights[i + radius] = weight;
            weights[radius - i] = weight;
            sum += i == 0 ? weight : weight * 2f;
        }
        for (int i = 0; i < weights.length; i++) weights[i] /= sum;
        return weights;
    }

    private static int mixColor(int sharp, int blur, float amount) {
        int a = Math.round((sharp >>> 24) + (((blur >>> 24) & 255) - (sharp >>> 24)) * amount);
        int r = Math.round(((sharp >>> 16) & 255) + (((blur >>> 16) & 255) - ((sharp >>> 16) & 255)) * amount);
        int g = Math.round(((sharp >>> 8) & 255) + (((blur >>> 8) & 255) - ((sharp >>> 8) & 255)) * amount);
        int b = Math.round((sharp & 255) + ((blur & 255) - (sharp & 255)) * amount);
        return (clamp(a) << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    private static int argb(float a, float r, float g, float b) {
        return (clamp(Math.round(a)) << 24) | (clamp(Math.round(r)) << 16)
                | (clamp(Math.round(g)) << 8) | clamp(Math.round(b));
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        if (edge0 == edge1) return value < edge0 ? 0f : 1f;
        float t = Math.max(0f, Math.min(1f, (value - edge0) / (edge1 - edge0)));
        return t * t * (3f - 2f * t);
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
