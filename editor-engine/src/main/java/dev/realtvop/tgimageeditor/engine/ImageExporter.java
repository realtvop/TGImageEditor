package dev.realtvop.tgimageeditor.engine;

import android.graphics.Bitmap;

import java.io.IOException;
import java.io.OutputStream;

public final class ImageExporter {
    private ImageExporter() {}

    public static void write(Bitmap bitmap, OutputStream output, Bitmap.CompressFormat format,
                             int quality) throws IOException {
        if (quality < 0 || quality > 100) {
            throw new IllegalArgumentException("quality must be in [0, 100]");
        }
        if (!bitmap.compress(format, quality, output)) {
            throw new IOException("Bitmap encoder rejected the image");
        }
        output.flush();
    }
}

