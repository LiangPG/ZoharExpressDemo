package com.zego.expressDemo.utils;

import android.content.Context;

import com.zego.expressDemo.application.BaseApplication;
import com.zego.expressDemo.data.Constant;

public class Utils {
    public static Context getApp() {
        return BaseApplication.getInstance().getApplicationContext();
    }
}
