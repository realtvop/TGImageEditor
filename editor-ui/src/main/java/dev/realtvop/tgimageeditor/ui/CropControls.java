package dev.realtvop.tgimageeditor.ui;

import android.content.Context;
import android.graphics.Color;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.function.Consumer;

import dev.realtvop.tgimageeditor.model.CropState;

public final class CropControls extends LinearLayout {
    private CropState state = CropState.FULL_IMAGE;
    private Consumer<CropState> listener;
    private Consumer<Float> aspectListener;
    private boolean binding;

    public CropControls(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setPadding(dp(8), dp(4), dp(8), dp(4));
        setBackgroundColor(0xE6161618);
        HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout ratios = new LinearLayout(context);
        addRatio(ratios, R.string.crop_free, 0f);
        addRatio(ratios, R.string.crop_original, -1f);
        addRatio(ratios, R.string.crop_square, 1f);
        addRatio(ratios, R.string.crop_4_3, 4f / 3f);
        addRatio(ratios, R.string.crop_3_4, 3f / 4f);
        addRatio(ratios, R.string.crop_16_9, 16f / 9f);
        addRatio(ratios, R.string.crop_9_16, 9f / 16f);
        scroll.addView(ratios);
        addView(scroll, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        TextView title = new TextView(context);
        title.setText(R.string.crop_straighten);
        title.setTextColor(Color.WHITE);
        title.setGravity(android.view.Gravity.CENTER);
        addView(title, new LayoutParams(LayoutParams.MATCH_PARENT, dp(26)));
        SeekBar rotation = new SeekBar(context);
        rotation.setMax(180);
        rotation.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (!binding && fromUser) state = state.withFineRotation((progress - 90) / 2f);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {
                if (listener != null) listener.accept(state);
            }
        });
        rotation.setTag("rotation");
        addView(rotation, new LayoutParams(LayoutParams.MATCH_PARENT, dp(42)));
    }

    public void bind(CropState state, float originalRatio, Consumer<CropState> listener,
                     Consumer<Float> aspectListener) {
        this.state = state;
        this.listener = listener;
        this.aspectListener = ratio -> aspectListener.accept(ratio < 0f ? originalRatio : ratio);
        SeekBar rotation = (SeekBar) getChildAt(2);
        binding = true;
        rotation.setProgress(Math.round(state.fineRotationDegrees() * 2f + 90f));
        binding = false;
    }

    public void setState(CropState state) {
        this.state = state;
        SeekBar rotation = (SeekBar) getChildAt(2);
        binding = true;
        rotation.setProgress(Math.round(state.fineRotationDegrees() * 2f + 90f));
        binding = false;
    }

    private void addRatio(LinearLayout row, int title, float ratio) {
        Button button = new Button(getContext());
        button.setText(title);
        button.setOnClickListener(v -> {
            if (aspectListener != null) aspectListener.accept(ratio);
        });
        row.addView(button);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
