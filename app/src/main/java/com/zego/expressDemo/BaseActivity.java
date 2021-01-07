package com.zego.expressDemo;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Activity 抽象基类
 */
public abstract class BaseActivity extends AppCompatActivity {

    protected static final String TAG = "zohar";

    protected static final String ROOM_ID = "room_id_1";
    protected static final String ROOM_ID_2 = "room_id_2";
    protected static final String STREAM_ID = "stream_id_1";
    protected static final String STREAM_ID_2 = "stream_id_2";
    protected static final String STREAM_ID_3 = "stream_id_3";

    /**
     * 申请权限 code
     */
    protected static final int PERMISSIONS_REQUEST_CODE = 1002;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(newBase);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 申请权限
        checkOrRequestPermission(PERMISSIONS_REQUEST_CODE);
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
    }

    /**
     * 获取contentView
     *
     * @return 返回contentView
     */
    protected View getContentView() {
        ViewGroup contentLayout = getWindow().getDecorView().findViewById(android.R.id.content);
        return contentLayout != null && contentLayout.getChildCount() != 0 ? contentLayout.getChildAt(0) : null;
    }

    // 相机存储音频权限申请
    private static String[] PERMISSIONS_REQUEST = {Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE};

    /**
     * 检查并申请权限
     *
     * @param requestCode requestCode
     * @return 权限是否已经允许
     */
    protected boolean checkOrRequestPermission(int requestCode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                this.requestPermissions(PERMISSIONS_REQUEST, requestCode);
                return false;
            }
        }
        return true;
    }
}
