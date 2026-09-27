package dev.realtvop.tgimageeditor.model;

import java.util.Objects;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable snapshot shared by editor tools and export. */
public final class EditDocument implements java.io.Serializable {
    private final SourceImage source;
    private final CropState crop;
    private final FilterState filter;
    private final List<PaintStroke> paintStrokes;
    private final List<TextEntity> textEntities;

    private EditDocument(SourceImage source, CropState crop, FilterState filter,
                         List<PaintStroke> paintStrokes, List<TextEntity> textEntities) {
        this.source = Objects.requireNonNull(source, "source");
        this.crop = Objects.requireNonNull(crop, "crop");
        this.filter = Objects.requireNonNull(filter, "filter");
        this.paintStrokes = Collections.unmodifiableList(new ArrayList<>(paintStrokes));
        this.textEntities = Collections.unmodifiableList(new ArrayList<>(textEntities));
    }

    public static EditDocument create(SourceImage source) {
        return new EditDocument(source, CropState.FULL_IMAGE, FilterState.NONE,
                Collections.emptyList(), Collections.emptyList());
    }

    public SourceImage source() { return source; }
    public CropState crop() { return crop; }
    public FilterState filter() { return filter; }
    public List<PaintStroke> paintStrokes() { return paintStrokes; }
    public List<TextEntity> textEntities() { return textEntities; }

    public EditDocument withCrop(CropState value) {
        return new EditDocument(source, value, filter, paintStrokes, textEntities);
    }

    public EditDocument withFilter(FilterState value) {
        return new EditDocument(source, crop, value, paintStrokes, textEntities);
    }

    public EditDocument withPaintStrokes(List<PaintStroke> value) {
        return new EditDocument(source, crop, filter, Objects.requireNonNull(value, "value"), textEntities);
    }

    public EditDocument withDrawing(List<PaintStroke> strokes, List<TextEntity> entities) {
        return new EditDocument(source, crop, filter, Objects.requireNonNull(strokes, "strokes"),
                Objects.requireNonNull(entities, "entities"));
    }
}
