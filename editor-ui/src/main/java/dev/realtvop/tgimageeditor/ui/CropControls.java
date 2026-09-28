package dev.realtvop.tgimageeditor.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.Locale;
import java.util.function.Consumer;

import dev.realtvop.tgimageeditor.model.CropState;
import dev.realtvop.tgimageeditor.nekogram.NekogramEditorIcons;

/** CropRotationWheel and 48dp PickerBottomLayoutViewer extracted as a standalone panel. */
public final class CropControls extends FrameLayout {
    private static final int ACCENT = 0xff51bdf3;
    private CropState state = CropState.FULL_IMAGE;
    private Consumer<CropState> listener;
    private Consumer<Float> aspectListener;
    private float originalRatio;
    private final RotationWheel wheel;
    private final ImageView mirror;
    private final ImageView rotate;

    public CropControls(Context context) {
        super(context);
        setBackgroundColor(Color.BLACK);
        wheel = new RotationWheel(context);
        addView(wheel, frame(-1, 64, Gravity.TOP));

        mirror = icon(NekogramEditorIcons.Icon.FLIP);
        mirror.setOnClickListener(v -> update(state.toggleMirror()));
        mirror.setOnLongClickListener(v -> { showRatios(); return true; });
        addView(mirror, frame(70, 64, Gravity.TOP | Gravity.LEFT));

        rotate = icon(NekogramEditorIcons.Icon.ROTATE);
        rotate.setOnClickListener(v -> update(state.rotateClockwise()));
        addView(rotate, frame(70, 64, Gravity.TOP | Gravity.RIGHT));

        FrameLayout footer = new FrameLayout(context);
        footer.setBackgroundColor(0xcc000000);
        FrameLayout.LayoutParams footerParams = frame(-1, 48, Gravity.BOTTOM);
        addView(footer, footerParams);
        footer.addView(action(R.string.action_cancel, Color.WHITE), frame(-2, -1, Gravity.LEFT));
        footer.addView(action(R.string.crop_reset, Color.WHITE), frame(-2, -1, Gravity.CENTER));
        footer.addView(action(R.string.action_done, ACCENT), frame(-2, -1, Gravity.RIGHT));
    }

    public void bind(CropState state, float originalRatio, Consumer<CropState> listener,
                     Consumer<Float> aspectListener) {
        this.listener = listener;
        this.aspectListener = aspectListener;
        this.originalRatio = originalRatio;
        setState(state);
    }

    public void setState(CropState value) {
        state = value;
        wheel.setValue(value.fineRotationDegrees());
        mirror.setColorFilter(value.mirrored() ? ACCENT : Color.WHITE);
        rotate.setColorFilter(value.quarterTurns() != 0 ? ACCENT : Color.WHITE);
    }

    public void setActions(Runnable cancel, Runnable done) {
        FrameLayout footer = (FrameLayout) getChildAt(getChildCount() - 1);
        footer.getChildAt(0).setOnClickListener(v -> cancel.run());
        footer.getChildAt(1).setOnClickListener(v -> update(CropState.FULL_IMAGE));
        footer.getChildAt(2).setOnClickListener(v -> done.run());
    }

    private void update(CropState value) {
        setState(value);
        if (listener != null) listener.accept(value);
    }

    private void showRatios() {
        String[] labels = {getContext().getString(R.string.crop_free), getContext().getString(R.string.crop_original),
                getContext().getString(R.string.crop_square), getContext().getString(R.string.crop_4_3),
                getContext().getString(R.string.crop_3_4), getContext().getString(R.string.crop_16_9),
                getContext().getString(R.string.crop_9_16)};
        float[] ratios = {0f, originalRatio, 1f, 4f / 3f, 3f / 4f, 16f / 9f, 9f / 16f};
        new AlertDialog.Builder(getContext()).setItems(labels, (dialog, which) -> {
            if (aspectListener != null) aspectListener.accept(ratios[which]);
        }).show();
    }

    private ImageView icon(NekogramEditorIcons.Icon icon) {
        ImageView view = new ImageView(getContext());
        view.setScaleType(ImageView.ScaleType.CENTER);
        view.setImageDrawable(NekogramEditorIcons.drawable(getContext(), icon));
        return view;
    }

    private TextView action(int label, int color) {
        TextView view = new TextView(getContext());
        view.setText(label);
        view.setAllCaps(true);
        view.setTextColor(color);
        view.setTextSize(14);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(20), 0, dp(20), 0);
        return view;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private FrameLayout.LayoutParams frame(int width, int height, int gravity) {
        return new FrameLayout.LayoutParams(width < 0 ? width : dp(width), height < 0 ? height : dp(height), gravity);
    }

    private final class RotationWheel extends android.view.View {
        private final Paint white = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint blue = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private float value;
        private float previousX;

        RotationWheel(Context context) {
            super(context);
            white.setColor(Color.WHITE);
            blue.setColor(ACCENT);
            text.setColor(Color.WHITE);
            text.setTextSize(dp(14));
        }

        void setValue(float value) {
            this.value = value;
            invalidate();
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                previousX = event.getX();
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                float next = Math.max(-45f, Math.min(45f,
                        value + (previousX - event.getX()) / getResources().getDisplayMetrics().density / (float) Math.PI / 1.65f));
                if (Math.abs(next) < .05f) next = 0f;
                previousX = event.getX();
                update(state.withFineRotation(next));
                return true;
            }
            return event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL;
        }

        @Override protected void onDraw(Canvas canvas) {
            int width = getWidth(), height = getHeight();
            float angle = -value * 2f;
            float delta = angle % 5f;
            int segments = (int) Math.floor(angle / 5f);
            int radius = width / 2 - Math.round(dp(70));
            for (int i = -15; i <= 15; i++) {
                float degrees = 90f - (i * 5f + delta);
                int offset = (int) (radius * Math.cos(Math.toRadians(degrees)));
                float fraction = Math.abs(offset) / (float) Math.max(1, radius);
                Paint paint = (i < 0 ? i > segments : i < segments) ? blue : white;
                paint.setAlpha(Math.min(255, Math.max(0, (int) ((1f - fraction * fraction) * 255))));
                int lineHeight = Math.round(i == segments ? dp(16) : dp(12));
                canvas.drawRect(width / 2f + offset - 1, (height - lineHeight) / 2f,
                        width / 2f + offset + 1, (height + lineHeight) / 2f, paint);
            }
            blue.setAlpha(255);
            rect.set(width / 2f - dp(1.25f), height / 2f - dp(11), width / 2f + dp(1.25f), height / 2f + dp(11));
            canvas.drawRoundRect(rect, dp(2), dp(2), blue);
            String degrees = String.format(Locale.US, "%.1fº", Math.abs(value) < .099f ? Math.abs(value) : value);
            canvas.drawText(degrees, (width - text.measureText(degrees)) / 2f, dp(14), text);
        }

        private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
    }
}
