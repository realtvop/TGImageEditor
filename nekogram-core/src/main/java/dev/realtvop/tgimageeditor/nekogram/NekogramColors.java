package dev.realtvop.tgimageeditor.nekogram;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;

/** Material You aware color boundary used by the extracted Nekogram chrome. */
public final class NekogramColors {
    private NekogramColors() {}

    public static boolean isDark(Context context) {
        return (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    public static int surface(Context context) {
        if (Build.VERSION.SDK_INT >= 31) {
            return context.getColor(isDark(context) ? android.R.color.system_neutral1_900
                    : android.R.color.system_neutral1_10);
        }
        return isDark(context) ? 0xff1f1f1f : 0xfffafafa;
    }

    public static int surfaceContainer(Context context) {
        if (Build.VERSION.SDK_INT >= 31) {
            return context.getColor(isDark(context) ? android.R.color.system_neutral1_800
                    : android.R.color.system_neutral1_50);
        }
        return isDark(context) ? 0xff2b2b2f : 0xfff0f0f0;
    }

    public static int text(Context context) { return isDark(context) ? 0xffffffff : 0xff1c1b1f; }
    public static int textSecondary(Context context) { return isDark(context) ? 0xffc9c5ca : 0xff49454f; }

    public static int accent(Context context) {
        if (Build.VERSION.SDK_INT >= 31) return context.getColor(android.R.color.system_accent1_500);
        return 0xff527da3;
    }
}
