package dev.realtvop.tgimageeditor.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.function.Consumer;

import dev.realtvop.tgimageeditor.model.PaintStroke;

/** Trimmed LPhotoPaintView chrome: brush strip, selection halo, tabs, cancel and done. */
public final class PaintControls extends FrameLayout {
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

    private static final int[] COLORS = {Color.WHITE, 0xffff453a, 0xffffcc00, 0xff30d158, 0xff0a84ff, 0xffbf5af2};
    private PaintStroke.Kind kind = PaintStroke.Kind.PEN;
    private int colorIndex;
    private float width = .012f;
    private Consumer<BrushSpec> listener;
    private Runnable textRequestListener;
    private final LinearLayout tools;
    private final TextView drawTab;
    private final TextView textTab;
    private BrushButton selected;
    private BrushButton defaultBrush;

    public PaintControls(Context context) {
        super(context);
        setPadding(dp(8), dp(8), dp(8), 0);
        setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00000000, 0x80000000, 0xff000000}));

        tools = new LinearLayout(context);
        tools.setGravity(Gravity.CENTER_VERTICAL);
        tools.setPadding(dp(16), 0, dp(16), 0);
        addView(tools, frame(-1, 48, Gravity.TOP));
        tools.addView(colorButton(), weighted());
        addBrush(PaintStroke.Kind.PEN);
        addBrush(PaintStroke.Kind.ARROW);
        addBrush(PaintStroke.Kind.MARKER);
        addBrush(PaintStroke.Kind.NEON);
        addBrush(PaintStroke.Kind.BLUR);
        addBrush(PaintStroke.Kind.ERASER);
        TextView add = symbol("+");
        add.setOnClickListener(v -> showShapes());
        tools.addView(add, weighted());

        LinearLayout tabs = new LinearLayout(context);
        tabs.setPadding(dp(52), 0, dp(52), 0);
        addView(tabs, frame(-1, 40, Gravity.BOTTOM));
        drawTab = tab(R.string.paint_draw_tab);
        textTab = tab(R.string.paint_text_tab);
        tabs.addView(drawTab, new LinearLayout.LayoutParams(0, -1, 1f));
        tabs.addView(textTab, new LinearLayout.LayoutParams(0, -1, 1f));
        drawTab.setOnClickListener(v -> selectTab(false));
        textTab.setOnClickListener(v -> {
            selectTab(true);
            if (textRequestListener != null) textRequestListener.run();
        });

        TextView cancel = symbol("×");
        cancel.setTextSize(28);
        addView(cancel, frame(40, 40, Gravity.BOTTOM | Gravity.LEFT));
        TextView done = symbol("✓");
        done.setTextSize(24);
        addView(done, frame(40, 40, Gravity.BOTTOM | Gravity.RIGHT));
        cancel.setTag("cancel");
        done.setTag("done");
        selectBrush(defaultBrush);
    }

    public void setListener(Consumer<BrushSpec> listener) {
        this.listener = listener;
        notifyChanged();
    }

    public void setTextRequestListener(Runnable listener) { textRequestListener = listener; }

    public void setActions(Runnable cancel, Runnable done) {
        findViewWithTag("cancel").setOnClickListener(v -> cancel.run());
        findViewWithTag("done").setOnClickListener(v -> done.run());
    }

    private View colorButton() {
        View view = new View(getContext()) {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas canvas) {
                float radius = Math.min(getWidth(), getHeight()) / 2f - dp(8);
                paint.setColor(COLORS[colorIndex]);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, radius, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(2));
                paint.setColor(Color.WHITE);
                canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, radius, paint);
            }
        };
        view.setOnClickListener(v -> {
            colorIndex = (colorIndex + 1) % COLORS.length;
            v.invalidate();
            notifyChanged();
        });
        return view;
    }

    private void addBrush(PaintStroke.Kind value) {
        BrushButton button = new BrushButton(getContext(), value);
        button.setOnClickListener(v -> selectBrush(button));
        tools.addView(button, weighted());
        if (value == PaintStroke.Kind.PEN) defaultBrush = button;
    }

    private void selectBrush(BrushButton button) {
        if (selected != null) selected.setSelected(false);
        selected = button;
        selected.setSelected(true);
        kind = button.kind;
        width = kind == PaintStroke.Kind.MARKER ? .018f : kind == PaintStroke.Kind.ERASER || kind == PaintStroke.Kind.BLUR ? .024f : .012f;
        selectTab(false);
        notifyChanged();
    }

    private void showShapes() {
        String[] names = {getContext().getString(R.string.paint_arrow), getContext().getString(R.string.paint_rectangle), getContext().getString(R.string.paint_oval)};
        PaintStroke.Kind[] values = {PaintStroke.Kind.ARROW, PaintStroke.Kind.RECTANGLE, PaintStroke.Kind.OVAL};
        new AlertDialog.Builder(getContext()).setItems(names, (dialog, which) -> {
            kind = values[which];
            selectTab(false);
            notifyChanged();
        }).show();
    }

    private void selectTab(boolean text) {
        drawTab.setAlpha(text ? .6f : 1f);
        textTab.setAlpha(text ? 1f : .6f);
    }

    private void notifyChanged() {
        if (listener != null) listener.accept(new BrushSpec(kind, COLORS[colorIndex], width));
    }

    private TextView tab(int label) {
        TextView view = new TextView(getContext());
        view.setText(label);
        view.setAllCaps(true);
        view.setTextColor(Color.WHITE);
        view.setTextSize(14);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        return view;
    }

    private TextView symbol(String value) {
        TextView view = new TextView(getContext());
        view.setText(value);
        view.setTextColor(Color.WHITE);
        view.setTextSize(22);
        view.setGravity(Gravity.CENTER);
        return view;
    }

    private LinearLayout.LayoutParams weighted() { return new LinearLayout.LayoutParams(0, dp(40), 1f); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private FrameLayout.LayoutParams frame(int width, int height, int gravity) {
        return new FrameLayout.LayoutParams(width < 0 ? width : dp(width), height < 0 ? height : dp(height), gravity);
    }

    private final class BrushButton extends View {
        final PaintStroke.Kind kind;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path path = new Path();

        BrushButton(Context context, PaintStroke.Kind kind) {
            super(context);
            this.kind = kind;
        }

        @Override protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            if (isSelected()) {
                paint.setColor(0x30ffffff);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx, cy, dp(17), paint);
            }
            paint.setColor(Color.WHITE);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeWidth(dp(kind == PaintStroke.Kind.MARKER ? 5 : 3));
            path.rewind();
            if (kind == PaintStroke.Kind.ERASER) {
                RectF rect = new RectF(cx - dp(8), cy - dp(6), cx + dp(8), cy + dp(6));
                canvas.save(); canvas.rotate(-35, cx, cy); canvas.drawRoundRect(rect, dp(2), dp(2), paint); canvas.restore();
            } else if (kind == PaintStroke.Kind.ARROW) {
                canvas.drawLine(cx - dp(9), cy + dp(7), cx + dp(8), cy - dp(7), paint);
                canvas.drawLine(cx + dp(8), cy - dp(7), cx + dp(1), cy - dp(7), paint);
                canvas.drawLine(cx + dp(8), cy - dp(7), cx + dp(7), cy, paint);
            } else if (kind == PaintStroke.Kind.BLUR) {
                paint.setStyle(Paint.Style.FILL);
                paint.setAlpha(150);
                canvas.drawCircle(cx, cy, dp(9), paint);
                paint.setAlpha(255);
            } else {
                path.moveTo(cx - dp(8), cy + dp(8));
                path.quadTo(cx, cy - dp(9), cx + dp(9), cy - dp(6));
                canvas.drawPath(path, paint);
                if (kind == PaintStroke.Kind.NEON) {
                    paint.setStrokeWidth(dp(7)); paint.setAlpha(80); canvas.drawPath(path, paint); paint.setAlpha(255);
                }
            }
        }
    }
}
