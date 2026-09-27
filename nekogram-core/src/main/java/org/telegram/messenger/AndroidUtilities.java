package org.telegram.messenger;

/** Minimal runtime boundary required by the extracted Nekogram image pipeline. */
public final class AndroidUtilities {
    private AndroidUtilities() {}

    public static int getPhotoSize(boolean unused) {
        return 2560;
    }

    public static String readRes(int unused) {
        return "";
    }
}
