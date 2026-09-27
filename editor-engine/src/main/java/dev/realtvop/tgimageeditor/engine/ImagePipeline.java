package dev.realtvop.tgimageeditor.engine;

import android.graphics.Bitmap;

import dev.realtvop.tgimageeditor.model.EditDocument;

/** Authoritative renderer used by both preview commits and export. */
public final class ImagePipeline {
    private ImagePipeline() {}

    public static Bitmap render(Bitmap source, EditDocument document) {
        Bitmap cropped = CropRenderer.render(source, document.crop());
        Bitmap filtered = FilterRenderer.render(cropped, document.filter());
        if (cropped != source && cropped != filtered) cropped.recycle();
        return filtered;
    }
}

