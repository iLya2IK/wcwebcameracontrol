package com.sggdev.wcsdk;

public class Log {
    static final boolean LOG = BuildConfig.DEBUG;

    public static final int INFO =  android.util.Log.INFO;
    public static final int DEBUG =  android.util.Log.DEBUG;

    public static void i(String tag, String string) {
        if (LOG) android.util.Log.i(tag, string);
    }
    public static void e(String tag, String string) {
        android.util.Log.e(tag, string);
    }
    public static void d(String tag, String string) {
        if (LOG) android.util.Log.d(tag, string);
    }
    public static void v(String tag, String string) {
        if (LOG) android.util.Log.v(tag, string);
    }
    public static void w(String tag, String string) {
        android.util.Log.w(tag, string);
    }
}
