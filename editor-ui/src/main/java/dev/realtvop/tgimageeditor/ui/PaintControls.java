package dev.realtvop.tgimageeditor.ui;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.SeekBar;

import java.util.function.Consumer;

import dev.realtvop.tgimageeditor.model.PaintStroke;

public final class PaintControls extends LinearLayout {
    public static final class BrushSpec {
        public final PaintStroke.Kind kind;
        public final int color;
        public final float width;

        BrushSpec(PaintStroke.Kind kind, int color, float width) {
            this.kind = kind;
            this.color = color;
            this.width = width;
        }
    }

    private PaintStroke.Kind kind = PaintStroke.Kind.PEN;
    private int color = Color.WHITE;
    private float width = .012f;
    private Consumer<BrushSpec> listener;

    public PaintControls(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setPadding(dp(8), dp(4), dp(8), dp(4));
        setBackgroundColor(0xE6161618);

        HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout tools = new LinearLayout(context);
        addTool(tools, R.string.paint_pen, PaintStroke.Kind.PEN);
        addTool(tools, R.string.paint_marker, PaintStroke.Kind.MARKER);
        addTool(tools, R.string.paint_neon, PaintStroke.Kind.NEON);
        addTool(tools, R.string.paint_eraser, PaintStroke.Kind.ERASER);
        addTool(tools, R.string.paint_arrow, PaintStroke.Kind.ARROW);
        addTool(tools, R.string.paint_rectangle, PaintStroke.Kind.RECTANGLE);
        addTool(tools, R.string.paint_oval, PaintStroke.Kind.OVAL);
        scroll.addView(tools);
        addView(scroll, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        LinearLayout options = new LinearLayout(context);
        options.setGravity(Gravity.CENTER_VERTICAL);
        int[] colors = {Color.WHITE, 0xffef5350, 0xffffca28, 0xff66bb6a, 0xff42a5f5, 0xffab47bc};
        for (int value : colors) {
            Button button = new Button(context);
            button.setText("●");
            button.setTextColor(value);
            button.setTextSize(22);
            button.setMinWidth(dp(40));
            button.setOnClickListener(v -> {
                color = value;
                notifyChanged();
            });
            options.addView(button, new LayoutParams(dp(44), dp(44)));
        }
        SeekBar weight = new SeekBar(context);
        weight.setMax(100);
        weight.setProgress(25);
        weight.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                width = .004f + progress / 100f * .05f;
                notifyChanged();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        options.addView(weight, new LayoutParams(0, dp(44), 1f));
        addView(options, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    public void setListener(Consumer<BrushSpec> listener) {
        this.listener = listener;
        notifyChanged();
    }

    private void addTool(LinearLayout parent, int title, PaintStroke.Kind value) {
        Button button = new Button(getContext());
        button.setText(title);
        button.setOnClickListener(v -> {
            kind = value;
            notifyChanged();
        });
        parent.addView(button);
    }

    private void notifyChanged() {
        if (listener != null) listener.accept(new BrushSpec(kind, color, width));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}

