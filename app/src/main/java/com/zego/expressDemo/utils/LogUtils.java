package com.zego.expressDemo.utils;

import android.util.Log;

public class LogUtils {

    private final static String TAG = LogUtils.class.getSimpleName();

    public static void v(String tag, String message) {
        Log.v(tag, message);
    }

    public static void d(String tag, String message) {
        Log.d(tag, message);
    }

    public static void d(String message) {
        Log.d(TAG, message);
    }

    public static void i(String tag, String message) {
        Log.i(tag, message);
    }

    public static void w(String tag, String message) {
        Log.w(tag, message);
    }

    public static void e(String tag, String message) {
        Log.e(tag, message);
    }

    public static void e(String message) {
        Log.e(TAG, message);
    }

    public static void e(String msg1, String msg2, String msg3) {
        Log.e(TAG, msg1 + msg2 + msg3);
    }
}
