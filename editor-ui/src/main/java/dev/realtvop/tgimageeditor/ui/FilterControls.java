package dev.realtvop.tgimageeditor.ui;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import dev.realtvop.tgimageeditor.model.FilterState;

/** Compact adjustment controls whose state remains owned by the edit document. */
public final class FilterControls extends LinearLayout {
    private static final class Binding {
        final Function<FilterState, Float> getter;
        final float min;
        final float max;

        Binding(Function<FilterState, Float> getter, float min, float max) {
            this.getter = getter;
            this.min = min;
            this.max = max;
        }
    }

    private FilterState state = FilterState.NONE;
    private Consumer<FilterState> listener;
    private boolean binding;

    public FilterControls(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setPadding(dp(12), dp(8), dp(12), dp(8));
        setBackgroundColor(0xE6161618);
        addControl("Exposure", FilterState::exposure, FilterState::withExposure, -1f, 1f);
        addControl("Contrast", FilterState::contrast, FilterState::withContrast, -1f, 1f);
        addControl("Saturation", FilterState::saturation, FilterState::withSaturation, -1f, 1f);
        addControl("Warmth", FilterState::warmth, FilterState::withWarmth, -1f, 1f);
        addControl("Fade", FilterState::fade, FilterState::withFade, 0f, 1f);
    }

    public void bind(FilterState state, Consumer<FilterState> listener) {
        this.state = state;
        this.listener = listener;
        binding = true;
        for (int i = 0; i < getChildCount(); i++) {
            LinearLayout row = (LinearLayout) getChildAt(i);
            SeekBar seek = (SeekBar) row.getChildAt(1);
            Binding binding = (Binding) seek.getTag();
            float fraction = (binding.getter.apply(state) - binding.min) / (binding.max - binding.min);
            seek.setProgress(Math.round(fraction * seek.getMax()));
        }
        binding = false;
    }

    private void addControl(String label, Function<FilterState, Float> getter,
                            BiFunction<FilterState, Float, FilterState> update,
                            float min, float max) {
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(getContext());
        title.setText(label);
        title.setTextColor(Color.WHITE);
        title.setWidth(dp(92));
        row.addView(title);

        SeekBar seek = new SeekBar(getContext());
        seek.setMax(200);
        seek.setTag(new Binding(getter, min, max));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (binding || !fromUser) return;
                float value = min + (max - min) * progress / seekBar.getMax();
                state = update.apply(state, value);
                if (listener != null) listener.accept(state);
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        row.addView(seek, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
