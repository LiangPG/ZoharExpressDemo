package com.zego.expressDemo.filter;

import android.graphics.SurfaceTexture;
import android.util.Log;

import im.zego.zegoexpress.ZegoExpressEngine;
import im.zego.zegoexpress.callback.IZegoCustomVideoProcessHandler;
import im.zego.zegoexpress.constants.ZegoPublishChannel;

public class STFilterTemp extends IZegoCustomVideoProcessHandler {
    private final static String TAG = STFilterTemp.class.getSimpleName();
    @Override
    public void onStart(ZegoPublishChannel channel) {
        super.onStart(channel);
    }

    @Override
    public void onStop(ZegoPublishChannel channel) {
        super.onStop(channel);
    }

    @Override
    public void onCapturedUnprocessedTextureData(int textureID, int width, int height, long referenceTimeMillisecond, ZegoPublishChannel channel) {
        ZegoExpressEngine.getEngine().sendCustomVideoProcessedTextureData(textureID, width, height, referenceTimeMillisecond, channel);
    }

    @Override
    public SurfaceTexture getCustomVideoProcessInputSurfaceTexture(int width, int height, ZegoPublishChannel channel) {
        Log.d(TAG, "-->:: getCustomVideoProcessInputSurfaceTexture width: " + width + ", height: " + height);
        return super.getCustomVideoProcessInputSurfaceTexture(width, height, channel);
    }
}
