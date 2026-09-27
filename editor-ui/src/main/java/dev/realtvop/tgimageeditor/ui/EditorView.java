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

    public EditorView(Context context) {
        super(context);
        setBackgroundColor(Color.rgb(22, 22, 24));
        imageView = new ImageView(context);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        addView(imageView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER));
    }

    public void setBitmap(Bitmap bitmap) {
        imageView.setImageBitmap(bitmap);
    }
}

