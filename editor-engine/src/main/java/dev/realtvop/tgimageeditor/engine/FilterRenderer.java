package dev.realtvop.tgimageeditor.engine;

import android.graphics.Bitmap;

import dev.realtvop.tgimageeditor.model.FilterState;
import dev.realtvop.tgimageeditor.nekogram.NekogramFilterPipeline;

/** Entry point for the extracted Nekogram OpenGL filter pipeline. */
public final class FilterRenderer {
    private FilterRenderer() {}

    public static Bitmap render(Bitmap source, FilterState state) {
        return NekogramFilterPipeline.render(source, state);
    }
}
