package dev.realtvop.tgimageeditor.nekogram;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

/** Standalone action bar retaining Nekogram's 56dp geometry and typography. */
public final class NekogramActionBar extends FrameLayout {
    private final BackView back;
    private final TextView title;
    private final TextView action;

    public NekogramActionBar(Context context) {
        super(context);
        int text = NekogramColors.text(context);
        setBackgroundColor(NekogramColors.surface(context));

        back = new BackView(context, text);
        addView(back, params(dp(56), dp(56), Gravity.START | Gravity.TOP));

        title = new TextView(context);
        title.setTextColor(text);
        title.setTextSize(20);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setSingleLine(true);
        LayoutParams titleParams = params(LayoutParams.MATCH_PARENT, dp(56), Gravity.TOP);
        titleParams.leftMargin = dp(56);
        titleParams.rightMargin = dp(88);
        addView(title, titleParams);

        action = new TextView(context);
        action.setTextColor(NekogramColors.accent(context));
        action.setTextSize(14);
        action.setGravity(Gravity.CENTER);
        action.setSingleLine(true);
        action.setBackground(ripple(context));
        addView(action, params(dp(88), dp(56), Gravity.END | Gravity.TOP));
    }

    public void setTitle(CharSequence value) { title.setText(value); }
    public void setAction(CharSequence value, OnClickListener listener) {
        action.setText(value);
        action.setOnClickListener(listener);
        action.setVisibility(value == null || value.length() == 0 ? GONE : VISIBLE);
    }
    public void setBackAction(OnClickListener listener) { back.setOnClickListener(listener); }
    public void setBackEnabled(boolean enabled) { back.setEnabled(enabled); back.setAlpha(enabled ? 1f : .5f); }
    public void setActionEnabled(boolean enabled) { action.setEnabled(enabled); action.setAlpha(enabled ? 1f : .5f); }
    public void setPhotoViewerMode() {
        setBackgroundColor(0xcc000000);
        back.setColor(0xffffffff);
        title.setTextColor(0xffffffff);
        action.setTextColor(0xff51bdf3);
    }

    private static LayoutParams params(int width, int height, int gravity) {
        LayoutParams params = new LayoutParams(width, height, gravity);
        return params;
    }

    private static android.graphics.drawable.Drawable ripple(Context context) {
        int pressed = (NekogramColors.text(context) & 0x00ffffff) | 0x18000000;
        return new RippleDrawable(ColorStateList.valueOf(pressed), new ColorDrawable(0x00000000), null);
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private static final class BackView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        BackView(Context context, int color) {
            super(context);
            paint.setColor(color);
            paint.setStrokeWidth(dp(context, 2));
            paint.setStrokeCap(Paint.Cap.SQUARE);
            setBackground(ripple(context));
            setContentDescription("Back");
        }
        @Override protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f - dp(getContext(), 1);
            float cy = getHeight() / 2f;
            canvas.drawLine(cx + dp(getContext(), 7), cy - dp(getContext(), 7), cx, cy, paint);
            canvas.drawLine(cx, cy, cx + dp(getContext(), 7), cy + dp(getContext(), 7), paint);
        }
        void setColor(int color) { paint.setColor(color); invalidate(); }
        private static int dp(Context context, float value) {
            return Math.round(value * context.getResources().getDisplayMetrics().density);
        }
    }
}
