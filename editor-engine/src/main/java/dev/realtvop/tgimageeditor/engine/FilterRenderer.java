package dev.realtvop.tgimageeditor.engine;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;

import dev.realtvop.tgimageeditor.model.FilterState;

/** CPU fallback for the first adjustment slice. GPU filters can replace this behind the same API. */
public final class FilterRenderer {
    private FilterRenderer() {}

    public static Bitmap render(Bitmap source, FilterState state) {
        if (state.isIdentity()) return source;

        ColorMatrix matrix = new ColorMatrix();
        float exposure = (float) Math.pow(2.0, state.exposure());
        matrix.postConcat(scale(exposure, exposure, exposure));

        float contrast = 1f + state.contrast();
        float translate = 128f * (1f - contrast);
        matrix.postConcat(new ColorMatrix(new float[]{
                contrast, 0, 0, 0, translate,
                0, contrast, 0, 0, translate,
                0, 0, contrast, 0, translate,
                0, 0, 0, 1, 0
        }));

        ColorMatrix saturation = new ColorMatrix();
        saturation.setSaturation(Math.max(0f, 1f + state.saturation()));
        matrix.postConcat(saturation);

        float warmth = state.warmth() * .2f;
        matrix.postConcat(scale(1f + warmth, 1f, 1f - warmth));

        if (state.fade() != 0f) {
            float amount = Math.max(0f, state.fade()) * .35f;
            float remaining = 1f - amount;
            matrix.postConcat(new ColorMatrix(new float[]{
                    remaining, 0, 0, 0, 128f * amount,
                    0, remaining, 0, 0, 128f * amount,
                    0, 0, remaining, 0, 128f * amount,
                    0, 0, 0, 1, 0
            }));
        }

        Bitmap output = Bitmap.createBitmap(source.getWidth(), source.getHeight(), Bitmap.Config.ARGB_8888);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        paint.setColorFilter(new ColorMatrixColorFilter(matrix));
        new Canvas(output).drawBitmap(source, 0, 0, paint);
        return output;
    }

    private static ColorMatrix scale(float red, float green, float blue) {
        return new ColorMatrix(new float[]{
                red, 0, 0, 0, 0,
                0, green, 0, 0, 0,
                0, 0, blue, 0, 0,
                0, 0, 0, 1, 0
        });
    }
}

