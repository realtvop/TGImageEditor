package dev.realtvop.tgimageeditor.engine;

import android.graphics.Bitmap;

import dev.realtvop.tgimageeditor.model.EditDocument;

public final class DecodedImage {
    private final Bitmap bitmap;
    private final EditDocument document;

    public DecodedImage(Bitmap bitmap, EditDocument document) {
        this.bitmap = bitmap;
        this.document = document;
    }

    public Bitmap bitmap() { return bitmap; }
    public EditDocument document() { return document; }
}

