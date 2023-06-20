package com.zego.expressDemo.filter;

import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.util.Log;

import com.zego.zegoavkit2.screencapture.ve_gl.GlRectDrawer;

import java.nio.ByteBuffer;

import im.zego.zegoexpress.ZegoExpressEngine;
import im.zego.zegoexpress.callback.IZegoCustomVideoProcessHandler;
import im.zego.zegoexpress.constants.ZegoPublishChannel;

public class STFilterTemp extends IZegoCustomVideoProcessHandler {
    private final static String TAG = STFilterTemp.class.getSimpleName();

    private final static float[] IDENTITY_MATRIX = new float[]{1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f};

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
        Log.d(TAG, "-->:: onCapturedUnprocessedTextureData start " + GLES20.glGetError());
        readBytesFromTexture(textureID, width, height);
        ZegoExpressEngine.getEngine().sendCustomVideoProcessedTextureData(textureID, width, height, referenceTimeMillisecond, channel);
    }

    @Override
    public SurfaceTexture getCustomVideoProcessInputSurfaceTexture(int width, int height, ZegoPublishChannel channel) {
        return super.getCustomVideoProcessInputSurfaceTexture(width, height, channel);
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

        GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, mReadPixelsByteBuffer);

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

        mReadPixelsByteBuffer.position(0);
        mReadPixelsByteBuffer.get(mRgbaByteArray);

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
