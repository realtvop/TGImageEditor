package dev.realtvop.tgimageeditor.model;

import java.util.Objects;

/** Immutable snapshot shared by editor tools and export. */
public final class EditDocument {
    private final SourceImage source;
    private final CropState crop;
    private final FilterState filter;

    private EditDocument(SourceImage source, CropState crop, FilterState filter) {
        this.source = Objects.requireNonNull(source, "source");
        this.crop = Objects.requireNonNull(crop, "crop");
        this.filter = Objects.requireNonNull(filter, "filter");
    }

    public static EditDocument create(SourceImage source) {
        return new EditDocument(source, CropState.FULL_IMAGE, FilterState.NONE);
    }

    public SourceImage source() { return source; }
    public CropState crop() { return crop; }
    public FilterState filter() { return filter; }

    public EditDocument withCrop(CropState value) {
        return new EditDocument(source, value, filter);
    }

    public EditDocument withFilter(FilterState value) {
        return new EditDocument(source, crop, value);
    }
}

