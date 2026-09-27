package dev.realtvop.tgimageeditor.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;

/** Stable host for editor tool surfaces. The first milestone renders the normalized source. */
public final class EditorView extends FrameLayout {
    private final ImageView imageView;
    private final CropOverlayView cropOverlay;

    public EditorView(Context context) {
        super(context);
        setBackgroundColor(Color.rgb(22, 22, 24));
        imageView = new ImageView(context);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        addView(imageView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER));
        cropOverlay = new CropOverlayView(context);
        cropOverlay.setVisibility(GONE);
        addView(cropOverlay, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    public void setBitmap(Bitmap bitmap) {
        imageView.setImageBitmap(bitmap);
        if (bitmap != null) cropOverlay.setBitmapSize(bitmap.getWidth(), bitmap.getHeight());
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

    public void endCrop() {
        cropOverlay.setVisibility(GONE);
    }
}
