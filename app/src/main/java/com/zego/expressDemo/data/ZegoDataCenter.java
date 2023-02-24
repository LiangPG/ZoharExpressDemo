package com.zego.expressDemo.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.text.TextUtils;

import com.zego.expressDemo.application.BaseApplication;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

import im.zego.zegoexpress.entity.ZegoUser;

/**
 * Zego App ID 和 Sign Data，需从Zego主页申请。
 */
public class ZegoDataCenter {

    private static final String SP_NAME = "sp_name_base";
    private static final String SP_KEY_UID = "sp_key_user_id";
    private static final String SP_KEY_USER_NAME = "sp_key_user_name";

    public static final boolean IS_TEST_ENV = true;

    public static final long APP_ID = 1739272706L;

    public static final String APP_SIGN = "1ec3f85cb2f21370264eb371c8c65ca37fa33b9defef2a85e0c899ae82c0f6f8";

    public static final User ZEGO_USER = new User(getUID(), getUserName()); // 根据自己情况初始化唯一识别USER

    /**
     * 获取保存的UserName，如果没有，则新建
     */
    private static int getUID() {
        SharedPreferences sp = BaseApplication.sApplication.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        int uid = sp.getInt(SP_KEY_UID, 0);
        if (uid == 0) {
            UUID uuid = UUID.randomUUID();
            uid = Math.abs(uuid.hashCode());
            // 保存uid
            sp.edit().putInt(SP_KEY_UID, uid).apply();
        }
        return uid;
    }

    /**
     * 获取保存的UserName，如果没有，则新建
     */
    private static String getUserName() {
        SharedPreferences sp = BaseApplication.sApplication.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        String userName = sp.getString(SP_KEY_USER_NAME, "");
        if (TextUtils.isEmpty(userName)) {
            String monthAndDay = new SimpleDateFormat("MMdd", Locale.CHINA).format(new Date());
            // 以设备名称 + 时间日期 + 一位随机数  作为用户名
            userName = Build.MODEL + monthAndDay + new Random().nextInt(10);
            // 保存用户名
            sp.edit().putString(SP_KEY_USER_NAME, userName).apply();
        }
        return userName;
    }
}
