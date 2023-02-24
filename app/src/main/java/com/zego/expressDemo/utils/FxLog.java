package com.zego.expressDemo.utils;

import android.util.Log;

public class FxLog {
    private final static String TAG = FxLog.class.getSimpleName();

    public static void d(String tag, String message) {
        Log.d(tag, message);
    }

    public static void d(String message) {
        Log.d(TAG, message);
    }

    public static void i(String tag, String message) {
        Log.i(tag, message);
    }

    public static void logE(String tag, String message, String extraInfo) {
        Log.e(tag, message);
    }
}
