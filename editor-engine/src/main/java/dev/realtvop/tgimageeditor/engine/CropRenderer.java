package dev.realtvop.tgimageeditor.engine;

import android.graphics.Bitmap;
import android.graphics.Matrix;

import dev.realtvop.tgimageeditor.model.CropState;

/** Applies crop transforms in the same order used by the crop preview. */
public final class CropRenderer {
    private CropRenderer() {}

    public static Bitmap render(Bitmap source, CropState state) {
        Bitmap transformed = transformSource(source, state);
        int left = clamp(Math.round(state.left() * transformed.getWidth()), 0, transformed.getWidth() - 1);
        int top = clamp(Math.round(state.top() * transformed.getHeight()), 0, transformed.getHeight() - 1);
        int right = clamp(Math.round(state.right() * transformed.getWidth()), left + 1, transformed.getWidth());
        int bottom = clamp(Math.round(state.bottom() * transformed.getHeight()), top + 1, transformed.getHeight());

        if (left == 0 && top == 0 && right == transformed.getWidth() && bottom == transformed.getHeight()) {
            return transformed;
        }
        Bitmap cropped = Bitmap.createBitmap(transformed, left, top, right - left, bottom - top);
        if (transformed != source) transformed.recycle();
        return cropped;
    }

    public static Bitmap transformSource(Bitmap source, CropState state) {
        if (state.quarterTurns() == 0 && state.fineRotationDegrees() == 0f && !state.mirrored()) {
            return source;
        }
        Matrix matrix = new Matrix();
        if (state.mirrored()) matrix.postScale(-1f, 1f);
        matrix.postRotate(state.quarterTurns() * 90f + state.fineRotationDegrees());
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}

