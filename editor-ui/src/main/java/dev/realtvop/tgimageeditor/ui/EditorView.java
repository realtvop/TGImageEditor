package dev.realtvop.tgimageeditor.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;

/** Stable host for the image, crop, paint, and text editing surfaces. */
public final class EditorView extends FrameLayout {
    private final ImageView imageView;
    private final CropOverlayView cropOverlay;
    private final PaintOverlayView paintOverlay;
    private final BlurOverlayView blurOverlay;

    public EditorView(Context context) {
        super(context);
        setBackgroundColor(Color.rgb(22, 22, 24));
        imageView = new ImageView(context);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        addView(imageView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER));
        cropOverlay = new CropOverlayView(context);
        cropOverlay.setVisibility(GONE);
        addView(cropOverlay, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        paintOverlay = new PaintOverlayView(context);
        paintOverlay.setVisibility(GONE);
        addView(paintOverlay, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        blurOverlay = new BlurOverlayView(context);
        addView(blurOverlay, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    public void setBitmap(Bitmap bitmap) {
        imageView.setImageBitmap(bitmap);
        if (bitmap != null) {
            cropOverlay.setBitmapSize(bitmap.getWidth(), bitmap.getHeight());
            blurOverlay.setBitmapSize(bitmap.getWidth(), bitmap.getHeight());
        }
    }

    public void beginCrop(dev.realtvop.tgimageeditor.model.CropState crop,
                          java.util.function.Consumer<dev.realtvop.tgimageeditor.model.CropState> listener) {
        cropOverlay.setCrop(crop);
        cropOverlay.setListener(listener::accept);
        cropOverlay.setVisibility(VISIBLE);
    }

    public void updateCrop(dev.realtvop.tgimageeditor.model.CropState crop) {
        cropOverlay.setCrop(crop);
    }

    public void setCropAspectRatio(float ratio) {
        cropOverlay.setAspectRatio(ratio);
    }

    public void endCrop() {
        cropOverlay.setVisibility(GONE);
    }

    public void beginBlur(dev.realtvop.tgimageeditor.model.BlurState blur,
                          java.util.function.Consumer<dev.realtvop.tgimageeditor.model.BlurState> listener) {
        blurOverlay.bind(blur, listener);
    }

    public void updateBlur(dev.realtvop.tgimageeditor.model.BlurState blur) {
        blurOverlay.setState(blur);
    }

    public void endBlur() {
        blurOverlay.release();
    }

    public void beginPaint(Bitmap base, java.util.List<dev.realtvop.tgimageeditor.model.PaintStroke> strokes,
                           java.util.List<dev.realtvop.tgimageeditor.model.TextEntity> entities,
                           java.util.function.BiConsumer<java.util.List<dev.realtvop.tgimageeditor.model.PaintStroke>,
                                   java.util.List<dev.realtvop.tgimageeditor.model.TextEntity>> listener) {
        if (imageView.getDrawable() == null) return;
        paintOverlay.bind(base, strokes, entities, listener::accept);
        paintOverlay.setVisibility(VISIBLE);
    }

    public void undoPaint() {
        paintOverlay.undo();
    }

    public void setPaintBrush(PaintControls.BrushSpec brush) {
        paintOverlay.setBrush(brush);
    }

    public void addPaintText(String text) {
        paintOverlay.addText(text);
    }

    public void endPaint() {
        paintOverlay.setVisibility(GONE);
        paintOverlay.release();
    }
}
