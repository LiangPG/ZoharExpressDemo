package com.zego.expressDemo.utils;

import android.util.Log;

public class AnalyticsLog {
    private final static String TAG = AnalyticsLog.class.getSimpleName();

    public final static AnalyticsLog INSTANCE = new AnalyticsLog();

    public void reportZegoErrorInfo(String message) {
        Log.d(TAG, message);
    }

    public void reportMakeupModelInfo(String resolution) {
        Log.d(TAG, resolution);
    }

    public void reportMakeupModelInfo(String bitrate, String fps, String resolution) {
        Log.d(TAG, bitrate + fps + resolution);
    }

    public void i(String tag, String message) {
        Log.i(tag, message);
    }

    public void logE(String tag, String message) {
        Log.e(tag, message);
    }
}
