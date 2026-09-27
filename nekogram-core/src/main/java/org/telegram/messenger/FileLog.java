package org.telegram.messenger;

import android.util.Log;

public final class FileLog {
    private static final String TAG = "NekogramEditor";
    private FileLog() {}
    public static void e(String value) { Log.e(TAG, value); }
    public static void e(Throwable value) { Log.e(TAG, value.getMessage(), value); }
}
