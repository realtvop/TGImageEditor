package dev.realtvop.tgimageeditor.ui;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import dev.realtvop.tgimageeditor.model.BlurState;
import dev.realtvop.tgimageeditor.model.FilterState;

/** Telegram-style tool strip: select one parameter, then adjust it with a shared slider. */
public final class FilterControls extends LinearLayout {
    private static final class Tool {
        final int label;
        final Function<FilterState, Float> getter;
        final BiFunction<FilterState, Float, FilterState> updater;
        final float min, max;

        Tool(int label, Function<FilterState, Float> getter,
             BiFunction<FilterState, Float, FilterState> updater, float min, float max) {
            this.label = label; this.getter = getter; this.updater = updater; this.min = min; this.max = max;
        }
    }

    private final ArrayList<Tool> tools = new ArrayList<>();
    private final LinearLayout toolRow;
    private final TextView selectedTitle;
    private final SeekBar seek;
    private FilterState state = FilterState.NONE;
    private Consumer<FilterState> listener;
    private Tool selected;
    private boolean binding;

    public FilterControls(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setPadding(dp(8), dp(4), dp(8), dp(4));
        setBackgroundColor(0xE62B2B2F);
        HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        toolRow = new LinearLayout(context);
        scroll.addView(toolRow);
        addView(scroll, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        selectedTitle = new TextView(context);
        selectedTitle.setTextColor(Color.WHITE);
        selectedTitle.setGravity(Gravity.CENTER);
        addView(selectedTitle, new LayoutParams(LayoutParams.MATCH_PARENT, dp(28)));
        seek = new SeekBar(context);
        seek.setMax(200);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (binding || !fromUser || selected == null) return;
                float value = selected.min + (selected.max - selected.min) * progress / bar.getMax();
                state = selected.updater.apply(state, value);
                notifyChanged();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        addView(seek, new LayoutParams(LayoutParams.MATCH_PARENT, dp(42)));

        add(R.string.filter_enhance, FilterState::enhance, FilterState::withEnhance, 0, 1);
        add(R.string.filter_skin, FilterState::softenSkin, FilterState::withSoftenSkin, 0, 1);
        add(R.string.filter_exposure, FilterState::exposure, FilterState::withExposure, -1, 1);
        add(R.string.filter_contrast, FilterState::contrast, FilterState::withContrast, -1, 1);
        add(R.string.filter_saturation, FilterState::saturation, FilterState::withSaturation, -1, 1);
        add(R.string.filter_warmth, FilterState::warmth, FilterState::withWarmth, -1, 1);
        add(R.string.filter_fade, FilterState::fade, FilterState::withFade, 0, 1);
        add(R.string.filter_highlights, FilterState::highlights, FilterState::withHighlights, -1, 1);
        add(R.string.filter_shadows, FilterState::shadows, FilterState::withShadows, -1, 1);
        add(R.string.filter_vignette, FilterState::vignette, FilterState::withVignette, 0, 1);
        add(R.string.filter_grain, FilterState::grain, FilterState::withGrain, 0, 1);
        add(R.string.filter_sharpen, FilterState::sharpen, FilterState::withSharpen, 0, 1);
        add(R.string.filter_curve_blacks, s -> s.curve().blacks(), (s, v) -> s.withCurve(s.curve().withBlacks(v)), 0, 1);
        add(R.string.filter_curve_shadows, s -> s.curve().shadows(), (s, v) -> s.withCurve(s.curve().withShadows(v)), 0, 1);
        add(R.string.filter_curve_midtones, s -> s.curve().midtones(), (s, v) -> s.withCurve(s.curve().withMidtones(v)), 0, 1);
        add(R.string.filter_curve_highlights, s -> s.curve().highlights(), (s, v) -> s.withCurve(s.curve().withHighlights(v)), 0, 1);
        add(R.string.filter_curve_whites, s -> s.curve().whites(), (s, v) -> s.withCurve(s.curve().withWhites(v)), 0, 1);
        add(R.string.filter_blur_size, s -> s.blur().size(), (s, v) -> s.withBlur(s.blur().withSize(v)), 0, 1);
        add(R.string.filter_blur_feather, s -> s.blur().feather(), (s, v) -> s.withBlur(s.blur().withFeather(v)), 0, 1);
        add(R.string.filter_blur_angle, s -> s.blur().angle(), (s, v) -> s.withBlur(s.blur().withAngle(v)), -180, 180);
        addBlurMode(R.string.filter_blur_off, BlurState.Type.NONE);
        addBlurMode(R.string.filter_blur_radial, BlurState.Type.RADIAL);
        addBlurMode(R.string.filter_blur_linear, BlurState.Type.LINEAR);
        select(tools.get(0));
    }

    public void bind(FilterState state, Consumer<FilterState> listener) {
        this.state = state;
        this.listener = listener;
        select(selected == null ? tools.get(0) : selected);
    }

    private void add(int label, Function<FilterState, Float> getter,
                     BiFunction<FilterState, Float, FilterState> updater, float min, float max) {
        Tool tool = new Tool(label, getter, updater, min, max);
        tools.add(tool);
        Button button = new Button(getContext());
        button.setText(label);
        styleButton(button);
        button.setOnClickListener(v -> select(tool));
        toolRow.addView(button);
    }

    private void addBlurMode(int label, BlurState.Type type) {
        Button button = new Button(getContext());
        button.setText(label);
        styleButton(button);
        button.setOnClickListener(v -> {
            state = state.withBlur(state.blur().withType(type));
            notifyChanged();
        });
        toolRow.addView(button);
    }

    private void select(Tool tool) {
        selected = tool;
        selectedTitle.setText(tool.label);
        binding = true;
        float fraction = (tool.getter.apply(state) - tool.min) / (tool.max - tool.min);
        seek.setProgress(Math.round(fraction * seek.getMax()));
        binding = false;
    }

    private void notifyChanged() {
        if (listener != null) listener.accept(state);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void styleButton(Button button) {
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setTextSize(13);
        button.setMinHeight(dp(44));
        button.setMinWidth(dp(76));
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setBackgroundColor(0x002B2B2F);
    }
}
