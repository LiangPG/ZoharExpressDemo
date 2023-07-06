package com.zego.expressDemo.filter;

import android.opengl.GLES20;
import android.util.Log;

import com.zego.zegoavkit2.screencapture.ve_gl.GlRectDrawer;

import java.nio.ByteBuffer;

import im.zego.zegoexpress.ZegoExpressEngine;
import im.zego.zegoexpress.callback.IZegoCustomVideoProcessHandler;
import im.zego.zegoexpress.constants.ZegoPublishChannel;
import im.zego.zegoexpress.entity.ZegoVideoFrameParam;

public class STFilterTemp extends IZegoCustomVideoProcessHandler {
    private final static String TAG = STFilterTemp.class.getSimpleName();

    private final static float[] IDENTITY_MATRIX = new float[]{1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f};

    private int mInTextureWidth;
    private int mInTextureHeight;

    @Override
    public void onStart(ZegoPublishChannel channel) {
        super.onStart(channel);
    }

    @Override
    public void onStop(ZegoPublishChannel channel) {
        destroyReadPixelsResource();

        mInTextureWidth = 0;
        mInTextureHeight = 0;
    }

    private void destroyReadPixelsResource() {
        if (mFrameBuffers != null) {
            GLES20.glDeleteFramebuffers(mFrameBuffers.length, mFrameBuffers, 0);
            mFrameBuffers = null;
        }
        if (mFrameBufferTextures != null) {
            GLES20.glDeleteTextures(mFrameBufferTextures.length, mFrameBufferTextures, 0);
            mFrameBufferTextures = null;
        }

        if (mGlDrawer != null) {
            mGlDrawer.release();
            mGlDrawer = null;
        }

        mReadPixelsByteBuffer = null;
        mRgbaByteArray = null;
        mI420ByteArray = null;
        mCopyI420ByteArray = null;
    }

    private final Object I420_BYTE_ARRAY_LOCK = new Object();

    private int mI420Width;
    private int mI420Height;
    private byte[] mI420ByteArray;
    private byte[] mCopyI420ByteArray;

    private long mLastTime;

    private void printConsume(String message) {
        Log.e(TAG, "-->:: " + message + ", consume: " + (System.currentTimeMillis() - mLastTime));
        mLastTime = System.currentTimeMillis();
    }

    @Override
    public void onCapturedUnprocessedRawData(ByteBuffer data, int[] dataLength, ZegoVideoFrameParam param, long referenceTimeMillisecond, ZegoPublishChannel channel) {
        mI420Width = param.width;
        mI420Height = param.height;
        synchronized (I420_BYTE_ARRAY_LOCK) {
            if (mI420ByteArray == null || mI420ByteArray.length != data.capacity()) {
                mI420ByteArray = new byte[data.capacity()];
                mCopyI420ByteArray = new byte[data.capacity()];
            }
            data.position(0);
            data.get(mI420ByteArray);
            data.position(0);
        }
    }

    @Override
    public void onCapturedUnprocessedTextureData(int textureID, int width, int height, long referenceTimeMillisecond, ZegoPublishChannel channel) {
        Log.d(TAG, "-->:: onCapturedUnprocessedTextureData");
        boolean isResolutionChange = mInTextureWidth != width || mInTextureHeight != height;
        if (isResolutionChange) {
            mInTextureWidth = width;
            mInTextureHeight = height;
            destroyReadPixelsResource();
        }
        if (mI420ByteArray == null || width != mI420Width || height != mI420Height) { // 当 byteArray == null 或者分辨率不一致的情况，都需要 readBytesFromTexture
            printConsume("readBytesFromTexture start");
            readBytesFromTexture(textureID, width, height);
            printConsume("readBytesFromTexture end");
        } else {
            printConsume("onCapturedUnprocessedTextureData copy start");
            synchronized (I420_BYTE_ARRAY_LOCK) {
                System.arraycopy(mI420ByteArray, 0, mCopyI420ByteArray, 0, mI420ByteArray.length);
            }
            printConsume("onCapturedUnprocessedTextureData copy end");
        }
        ZegoExpressEngine.getEngine().sendCustomVideoProcessedTextureData(textureID, width, height, referenceTimeMillisecond, channel);
    }

    private ByteBuffer mReadPixelsByteBuffer;
    private byte[] mRgbaByteArray;

    private int[] mFrameBuffers;
    private int[] mFrameBufferTextures;

    private GlRectDrawer mGlDrawer;

    private byte[] readBytesFromTexture(int textureID, int width, int height) {
        if (mGlDrawer == null) {
            mGlDrawer = new GlRectDrawer();
        }
        if (mFrameBufferTextures == null) {
            mFrameBufferTextures = new int[1];
            GLES20.glGenTextures(1, mFrameBufferTextures, 0);
        }
        if (mFrameBuffers == null) {
            mFrameBuffers = new int[1];
            GLES20.glGenFramebuffers(1, mFrameBuffers, 0);
            bindFrameBuffer(mFrameBufferTextures[0], mFrameBuffers[0], width, height);
        }
        if (mRgbaByteArray == null) {
            mRgbaByteArray = new byte[width * height * 4];
        }
        if (mReadPixelsByteBuffer == null) {
            mReadPixelsByteBuffer = ByteBuffer.allocateDirect(width * height * 4);
        }

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffers[0]);

        mGlDrawer.drawRgb(textureID, IDENTITY_MATRIX, width, height, 0, 0, width, height);

        mReadPixelsByteBuffer.position(0);
        GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, mReadPixelsByteBuffer);

        mReadPixelsByteBuffer.position(0);
        mReadPixelsByteBuffer.get(mRgbaByteArray);

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

        return mRgbaByteArray;
    }

    private void bindFrameBuffer(int textureId, int frameBuffer, int width, int height) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBuffer);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, textureId, 0);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
    }
}
