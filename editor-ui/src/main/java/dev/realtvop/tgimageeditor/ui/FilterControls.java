package dev.realtvop.tgimageeditor.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.ui.Components.PhotoEditorSeekBar;

import java.util.ArrayList;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import dev.realtvop.tgimageeditor.model.BlurState;
import dev.realtvop.tgimageeditor.model.FilterState;
import dev.realtvop.tgimageeditor.nekogram.NekogramEditorIcons;

/** Standalone host matching PhotoFilterView's 186dp tools panel. */
public final class FilterControls extends FrameLayout {
    private static final int ACCENT = 0xff51bdf3;

    private final ArrayList<ToolRow> tuneRows = new ArrayList<>();
    private final ArrayList<ToolRow> curveRows = new ArrayList<>();
    private final ScrollView tunePanel;
    private final ScrollView curvePanel;
    private final LinearLayout blurPanel;
    private final ImageView tuneButton;
    private final ImageView blurButton;
    private final ImageView curveButton;
    private final TextView doneButton;
    private FilterState state = FilterState.NONE;
    private Consumer<FilterState> listener;
    private boolean binding;

    public FilterControls(Context context) {
        super(context);
        setBackgroundColor(Color.TRANSPARENT);

        tunePanel = panel();
        LinearLayout tuneList = list(tunePanel);
        addTune(tuneList, R.string.filter_enhance, FilterState::enhance, FilterState::withEnhance, 0, 100);
        addTune(tuneList, R.string.filter_skin, FilterState::softenSkin, FilterState::withSoftenSkin, 0, 100);
        addTune(tuneList, R.string.filter_exposure, FilterState::exposure, FilterState::withExposure, -100, 100);
        addTune(tuneList, R.string.filter_contrast, FilterState::contrast, FilterState::withContrast, -100, 100);
        addTune(tuneList, R.string.filter_saturation, FilterState::saturation, FilterState::withSaturation, -100, 100);
        addTune(tuneList, R.string.filter_warmth, FilterState::warmth, FilterState::withWarmth, -100, 100);
        addTune(tuneList, R.string.filter_fade, FilterState::fade, FilterState::withFade, 0, 100);
        addTune(tuneList, R.string.filter_highlights, FilterState::highlights, FilterState::withHighlights, -100, 100);
        addTune(tuneList, R.string.filter_shadows, FilterState::shadows, FilterState::withShadows, -100, 100);
        addTune(tuneList, R.string.filter_vignette, FilterState::vignette, FilterState::withVignette, 0, 100);
        addTune(tuneList, R.string.filter_grain, FilterState::grain, FilterState::withGrain, 0, 100);
        addTune(tuneList, R.string.filter_sharpen, FilterState::sharpen, FilterState::withSharpen, 0, 100);
        addView(tunePanel, frame(-1, 138, Gravity.TOP));

        curvePanel = panel();
        LinearLayout curveList = list(curvePanel);
        addCurve(curveList, R.string.filter_curve_blacks, s -> s.curve().blacks(), (s, v) -> s.withCurve(s.curve().withBlacks(v)));
        addCurve(curveList, R.string.filter_curve_shadows, s -> s.curve().shadows(), (s, v) -> s.withCurve(s.curve().withShadows(v)));
        addCurve(curveList, R.string.filter_curve_midtones, s -> s.curve().midtones(), (s, v) -> s.withCurve(s.curve().withMidtones(v)));
        addCurve(curveList, R.string.filter_curve_highlights, s -> s.curve().highlights(), (s, v) -> s.withCurve(s.curve().withHighlights(v)));
        addCurve(curveList, R.string.filter_curve_whites, s -> s.curve().whites(), (s, v) -> s.withCurve(s.curve().withWhites(v)));
        curvePanel.setVisibility(INVISIBLE);
        addView(curvePanel, frame(-1, 138, Gravity.TOP));

        blurPanel = new LinearLayout(context);
        blurPanel.setGravity(Gravity.CENTER);
        blurPanel.setVisibility(INVISIBLE);
        blurPanel.addView(modeText(R.string.filter_blur_off, BlurState.Type.NONE), linear(80, 60));
        blurPanel.addView(modeText(R.string.filter_blur_radial, BlurState.Type.RADIAL), linear(100, 60));
        blurPanel.addView(modeText(R.string.filter_blur_linear, BlurState.Type.LINEAR), linear(100, 60));
        FrameLayout.LayoutParams blurParams = frame(dp(280), 60, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        blurParams.topMargin = dp(40);
        addView(blurPanel, blurParams);

        FrameLayout bottom = new FrameLayout(context);
        bottom.setBackgroundColor(Color.BLACK);
        addView(bottom, frame(-1, 48, Gravity.BOTTOM));
        TextView cancel = actionText(R.string.action_cancel, Color.WHITE);
        bottom.addView(cancel, frame(-2, -1, Gravity.LEFT));
        doneButton = actionText(R.string.action_done, ACCENT);
        bottom.addView(doneButton, frame(-2, -1, Gravity.RIGHT));

        LinearLayout modes = new LinearLayout(context);
        modes.setGravity(Gravity.CENTER);
        tuneButton = modeIcon(NekogramEditorIcons.Icon.TUNE, () -> showMode(0));
        blurButton = modeIcon(NekogramEditorIcons.Icon.BLUR, () -> showMode(1));
        curveButton = modeIcon(NekogramEditorIcons.Icon.CURVE, () -> showMode(2));
        modes.addView(tuneButton, linear(56, 48));
        modes.addView(blurButton, linear(56, 48));
        modes.addView(curveButton, linear(56, 48));
        bottom.addView(modes, frame(-2, -1, Gravity.CENTER));
        showMode(0);
    }

    public void bind(FilterState value, Consumer<FilterState> listener) {
        this.listener = listener;
        setState(value);
    }

    public void setState(FilterState value) {
        state = value;
        binding = true;
        for (ToolRow row : tuneRows) row.sync(value);
        for (ToolRow row : curveRows) row.sync(value);
        binding = false;
        updateBlurSelection();
    }

    public void setActions(Runnable cancel, Runnable done) {
        ((TextView) ((FrameLayout) getChildAt(getChildCount() - 1)).getChildAt(0)).setOnClickListener(v -> cancel.run());
        doneButton.setOnClickListener(v -> done.run());
    }

    public void setDoneEnabled(boolean enabled) {
        doneButton.setEnabled(enabled);
        doneButton.setAlpha(enabled ? 1f : .5f);
    }

    private ScrollView panel() {
        ScrollView view = new ScrollView(getContext());
        view.setFillViewport(true);
        view.setVerticalScrollBarEnabled(false);
        view.setBackgroundColor(0xff000000);
        return view;
    }

    private LinearLayout list(ScrollView panel) {
        LinearLayout list = new LinearLayout(getContext());
        list.setOrientation(LinearLayout.VERTICAL);
        panel.addView(list, new ScrollView.LayoutParams(-1, -2));
        return list;
    }

    private void addTune(LinearLayout parent, int label, Function<FilterState, Float> getter,
                         BiFunction<FilterState, Float, FilterState> updater, int min, int max) {
        ToolRow row = new ToolRow(getContext(), label, getter, updater, min, max);
        tuneRows.add(row);
        parent.addView(row, new LinearLayout.LayoutParams(-1, dp(40)));
    }

    private void addCurve(LinearLayout parent, int label, Function<FilterState, Float> getter,
                          BiFunction<FilterState, Float, FilterState> updater) {
        ToolRow row = new ToolRow(getContext(), label, getter, updater, 0, 100);
        curveRows.add(row);
        parent.addView(row, new LinearLayout.LayoutParams(-1, dp(40)));
    }

    private TextView modeText(int label, BlurState.Type type) {
        TextView view = new TextView(getContext());
        view.setText(label);
        view.setTag(type);
        view.setTextSize(13);
        view.setGravity(Gravity.CENTER);
        view.setOnClickListener(v -> {
            state = state.withBlur(state.blur().withType(type));
            updateBlurSelection();
            notifyChanged();
        });
        return view;
    }

    private void updateBlurSelection() {
        for (int i = 0; i < blurPanel.getChildCount(); i++) {
            TextView child = (TextView) blurPanel.getChildAt(i);
            child.setTextColor(child.getTag() == state.blur().type() ? ACCENT : Color.WHITE);
        }
    }

    private ImageView modeIcon(NekogramEditorIcons.Icon icon, Runnable click) {
        ImageView view = new ImageView(getContext());
        view.setScaleType(ImageView.ScaleType.CENTER);
        view.setImageDrawable(NekogramEditorIcons.drawable(getContext(), icon));
        view.setOnClickListener(v -> click.run());
        return view;
    }

    private void showMode(int mode) {
        tunePanel.setVisibility(mode == 0 ? VISIBLE : INVISIBLE);
        blurPanel.setVisibility(mode == 1 ? VISIBLE : INVISIBLE);
        curvePanel.setVisibility(mode == 2 ? VISIBLE : INVISIBLE);
        tint(tuneButton, mode == 0);
        tint(blurButton, mode == 1);
        tint(curveButton, mode == 2);
    }

    private void tint(ImageView view, boolean selected) {
        view.setColorFilter(selected ? new PorterDuffColorFilter(ACCENT, PorterDuff.Mode.SRC_IN) : null);
    }

    private TextView actionText(int label, int color) {
        TextView view = new TextView(getContext());
        view.setText(label);
        view.setTextSize(14);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setAllCaps(true);
        view.setPadding(dp(20), 0, dp(20), 0);
        return view;
    }

    private void notifyChanged() {
        if (!binding && listener != null) listener.accept(state);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private FrameLayout.LayoutParams frame(int width, int height, int gravity) {
        return new FrameLayout.LayoutParams(width < 0 ? width : dp(width), height < 0 ? height : dp(height), gravity);
    }

    private LinearLayout.LayoutParams linear(int width, int height) {
        return new LinearLayout.LayoutParams(dp(width), dp(height));
    }

    private final class ToolRow extends FrameLayout {
        private final Function<FilterState, Float> getter;
        private final BiFunction<FilterState, Float, FilterState> updater;
        private final int min;
        private final int max;
        private final TextView name;
        private final TextView value;
        private final PhotoEditorSeekBar seek;

        ToolRow(Context context, int label, Function<FilterState, Float> getter,
                BiFunction<FilterState, Float, FilterState> updater, int min, int max) {
            super(context);
            this.getter = getter;
            this.updater = updater;
            this.min = min;
            this.max = max;
            name = rowText(label, Color.WHITE);
            name.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            addView(name, frame(80, -2, Gravity.LEFT | Gravity.CENTER_VERTICAL));
            value = rowText(label, ACCENT);
            value.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            value.setVisibility(INVISIBLE);
            addView(value, frame(80, -2, Gravity.LEFT | Gravity.CENTER_VERTICAL));
            seek = new PhotoEditorSeekBar(context);
            seek.setTag(0);
            seek.setMinMax(min, max);
            seek.setDelegate((id, progress) -> {
                if (binding) return;
                float normalized = progress / 100f;
                state = updater.apply(state, normalized);
                value.setText(progress > 0 ? "+" + progress : Integer.toString(progress));
                name.setVisibility(INVISIBLE);
                value.setVisibility(VISIBLE);
                removeCallbacks(this::hideValue);
                postDelayed(this::hideValue, 1000);
                notifyChanged();
            });
            FrameLayout.LayoutParams seekParams = frame(-1, 40, Gravity.CENTER_VERTICAL);
            seekParams.leftMargin = dp(96);
            seekParams.rightMargin = dp(24);
            addView(seek, seekParams);
        }

        void sync(FilterState state) {
            int progress = Math.round(getter.apply(state) * 100f);
            seek.setMinMax(min, max);
            seek.setProgress(progress, false);
            value.setText(progress > 0 ? "+" + progress : Integer.toString(progress));
        }

        private TextView rowText(int label, int color) {
            TextView text = new TextView(getContext());
            text.setText(label);
            text.setTextColor(color);
            text.setTextSize(12);
            text.setSingleLine(true);
            return text;
        }

        private void hideValue() {
            value.setVisibility(INVISIBLE);
            name.setVisibility(VISIBLE);
        }
    }
}
