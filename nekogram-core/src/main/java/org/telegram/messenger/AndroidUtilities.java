package org.telegram.messenger;

import android.content.res.Resources;
import android.graphics.Point;
import android.os.Handler;
import android.os.Looper;

/** Minimal runtime boundary required by the extracted Nekogram image pipeline. */
public final class AndroidUtilities {
    public static final float density = Resources.getSystem().getDisplayMetrics().density;
    public static final int statusBarHeight = resolveStatusBarHeight();
    public static final Point displaySize = new Point(
            Resources.getSystem().getDisplayMetrics().widthPixels,
            Resources.getSystem().getDisplayMetrics().heightPixels);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private AndroidUtilities() {}

    public static int dp(float value) { return Math.round(value * density); }
    public static void runOnUIThread(Runnable runnable) { MAIN.post(runnable); }
    public static void runOnUIThread(Runnable runnable, long delay) { MAIN.postDelayed(runnable, delay); }
    public static void cancelRunOnUIThread(Runnable runnable) { MAIN.removeCallbacks(runnable); }

    public static int getPhotoSize(boolean unused) {
        return 2560;
    }

    public static String readRes(int unused) {
        return "";
    }

    private static int resolveStatusBarHeight() {
        int id = Resources.getSystem().getIdentifier("status_bar_height", "dimen", "android");
        return id == 0 ? 0 : Resources.getSystem().getDimensionPixelSize(id);
    }
}
