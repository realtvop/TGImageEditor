package dev.realtvop.tgimageeditor.ui;

import android.content.Context;
import android.graphics.PointF;

import org.telegram.ui.Components.PhotoFilterBlurControl;

import java.util.function.Consumer;

import dev.realtvop.tgimageeditor.model.BlurState;

/** Thin state adapter around Nekogram's original PhotoFilterBlurControl. */
public final class BlurOverlayView extends PhotoFilterBlurControl {
    private BlurState state = BlurState.NONE;
    private Consumer<BlurState> listener;
    private int bitmapWidth = 1;
    private int bitmapHeight = 1;

    public BlurOverlayView(Context context) {
        super(context);
        setVisibility(GONE);
        setDelegate((center, falloff, size, angle) -> {
            state = new BlurState(state.type(), center.x, center.y, size, falloff,
                    (float) Math.toDegrees(angle));
            if (listener != null) listener.accept(state);
        });
        addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> updateArea());
    }

    public void bind(BlurState state, Consumer<BlurState> listener) {
        this.listener = listener;
        apply(state);
    }

    public void setState(BlurState state) {
        apply(state);
    }

    public void setBitmapSize(int width, int height) {
        bitmapWidth = Math.max(1, width);
        bitmapHeight = Math.max(1, height);
        updateArea();
    }

    public void release() {
        listener = null;
        setVisibility(GONE);
    }

    private void apply(BlurState next) {
        state = next;
        boolean visible = next.type() != BlurState.Type.NONE;
        setVisibility(visible ? VISIBLE : GONE);
        if (!visible) return;
        setType(next.type() == BlurState.Type.LINEAR ? 0 : 1);
        setValues(new PointF(next.centerX(), next.centerY()), next.feather(), next.size(),
                (float) Math.toRadians(next.angle()));
        updateArea();
    }

    private void updateArea() {
        if (getWidth() <= 0 || getHeight() <= 0) return;
        float scale = Math.min(getWidth() / (float) bitmapWidth, getHeight() / (float) bitmapHeight);
        setActualAreaSize(bitmapWidth * scale, bitmapHeight * scale);
    }
}
