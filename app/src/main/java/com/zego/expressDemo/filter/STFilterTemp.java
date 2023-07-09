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
        mI420ByteArray = null;
        mCopyI420ByteArray = null;

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
        synchronized (I420_BYTE_ARRAY_LOCK) {
            mI420Width = param.width;
            mI420Height = param.height;
            int sumDataLength = param.width * param.height * 3 / 2; // 总长度，明确清楚是 i420 的情况
            if (mI420ByteArray == null || mI420ByteArray.length != sumDataLength) {
                mI420ByteArray = new byte[sumDataLength];
                mCopyI420ByteArray = new byte[sumDataLength];
            }

            if (sumDataLength == data.capacity()) {
                data.get(mI420ByteArray);
            } else {
                data.position(0); // 先将位置调节到 0
                for (int i = 0; i < param.height; i++) { // 读 Y 的数据，每次读 width 的内容，每次跳转到 i * yStrides
                    data.position(i * param.strides[0]);
                    data.get(mI420ByteArray, i * param.width, param.width);  // 每次读 width 的内容
                }
                for (int i = 0; i < param.height / 2; i++) { // 读 U 的数据，每次读 width / 2 的内容，每次跳转到 data[0] + i * uStrides
                    data.position(dataLength[0] + i * param.strides[1]);
                    data.get(mI420ByteArray, param.width * param.height + i * param.width / 2, param.width / 2);
                }
                for (int i = 0; i < param.height / 2; i++) { // 读 Y 的数据，每次读 width / 2 的内容，每次跳转到 data[0] + i * yStrides
                    data.position(dataLength[0] + dataLength[1] + i * param.strides[2]);
                    data.get(mI420ByteArray, param.width * param.height * 5 / 4 + i * param.width / 2, param.width / 2);
                }

                data.position(0); // 重置位置到 0
            }
        }
    }

    @Override
    public void onCapturedUnprocessedTextureData(int textureID, int width, int height, long referenceTimeMillisecond, ZegoPublishChannel channel) {
        printConsume("onCapturedUnprocessedTextureData");
        boolean isResolutionChange = mInTextureWidth != width || mInTextureHeight != height;
        if (isResolutionChange) {
            mInTextureWidth = width;
            mInTextureHeight = height;
            destroyReadPixelsResource();
        }

        boolean needFallbackReadBytes;
        synchronized (I420_BYTE_ARRAY_LOCK) {
            needFallbackReadBytes = mI420ByteArray == null || width != mI420Width || height != mI420Height;
            if (!needFallbackReadBytes) {
                printConsume("onCapturedUnprocessedTextureData copy start");

                System.arraycopy(mI420ByteArray, 0, mCopyI420ByteArray, 0, mI420ByteArray.length);
                printConsume("onCapturedUnprocessedTextureData copy end");
            }
        }

        if (needFallbackReadBytes) {
            printConsume("readBytesFromTexture start");
            readBytesFromTexture(textureID, width, height);
            printConsume("readBytesFromTexture end");
        }
        printConsume("sendCustomVideoProcessedTextureData start");
        ZegoExpressEngine.getEngine().sendCustomVideoProcessedTextureData(textureID, width, height, referenceTimeMillisecond, channel);
        printConsume("sendCustomVideoProcessedTextureData end");
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
