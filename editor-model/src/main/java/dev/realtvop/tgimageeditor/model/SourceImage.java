package dev.realtvop.tgimageeditor.model;

import java.util.Objects;

/** Metadata for the orientation-normalized source bitmap used by the editor. */
public final class SourceImage {
    private final String id;
    private final int width;
    private final int height;

    public SourceImage(String id, int width, int height) {
        this.id = Objects.requireNonNull(id, "id");
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Source dimensions must be positive");
        }
        this.width = width;
        this.height = height;
    }

    public String id() {
        return id;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }
}

