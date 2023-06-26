package com.zego.expressDemo.filter;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;

import com.blankj.utilcode.util.ToastUtils;
import com.bytedance.labcv.effectsdk.BytedEffectConstants;
import com.meelive.ingkee.logger.IKLog;
import com.meelive.ingkee.tracker.TrackerConstants;
import com.meelive.ingkee.tracker.Trackers;
import com.zego.byted_effects.EffectHelper;
import com.zego.byted_effects.base.ProcessInput;
import com.zego.byted_effects.effect.EffectManager;
import com.zego.ve_gl.EglBase;
import com.zego.ve_gl.EglBase14;
import com.zego.ve_gl.GlRectDrawer;
import com.zego.ve_gl.GlUtil;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import im.zego.zegoexpress.ZegoExpressEngine;
import im.zego.zegoexpress.callback.IZegoCustomVideoProcessHandler;
import im.zego.zegoexpress.constants.ZegoPublishChannel;

public class CustomVideoProcessHandlerImpl extends IZegoCustomVideoProcessHandler implements EffectManager.OnEffectListener, SurfaceTexture.OnFrameAvailableListener {

    // TODO 测试分辨率改变
    private final Context mContext;

    private EffectHelper mEffectHelper;

    private HandlerThread mProcessThread;
    private Handler mProcessHandler;

    private SurfaceTexture mProcessInputSurfaceTexture;
    private int mProcessInputTextureId;
    private boolean mIsEgl14;
    private EglBase mProcessEglContext;
    private GlRectDrawer mProcessDrawer;
    private Surface mProcessOutputSurface;

    private final float[] IDENTIFY_MATRIX = new float[]{
            1.0f, 0.0f, 0.0f, 0.0f,
            0.0f, 1.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 1.0f, 0.0f,
            0.0f, 0.0f, 0.0f, 1.0f
    };

    private int mOutputWidth = 0;
    private int mOutputHeight = 0;

    private boolean mStopFlag = false;

    //FileUtils.copyFilterAssetsToFile
    private final boolean isBytedEffectsResourceReady = true;

    public CustomVideoProcessHandlerImpl(Context context) {
        mContext = context;
    }

    private void initEffectsHelper() {
        if (isBytedEffectsResourceReady) {
            mEffectHelper.init();
            mEffectHelper.setImageSize(mOutputWidth, mOutputHeight);
            mEffectHelper.recoverStatus();
        }
    }

    public void init() {
        if (mEffectHelper == null) {
            mEffectHelper = new EffectHelper(mContext, EffectManager.EffectType.PREVIEW);
            mEffectHelper.setOnEffectListener(this);
        }
        if (mProcessThread == null) {
            mProcessThread = new HandlerThread("byted-effect-process");
            mProcessThread.start();
        }
        if (mProcessHandler == null) {
            mProcessHandler = new Handler(mProcessThread.getLooper());

        }
        mProcessHandler.post(() -> {
            mProcessEglContext = EglBase.create(null, EglBase.CONFIG_RECORDABLE);

            // 构造前处理需要使用的 SurfaceTexture
            mProcessInputSurfaceTexture = new SurfaceTexture(0);
            mProcessInputSurfaceTexture.setOnFrameAvailableListener(CustomVideoProcessHandlerImpl.this);
            mProcessInputSurfaceTexture.detachFromGLContext();

            mIsEgl14 = EglBase14.isEGL14Supported();
            mStopFlag = false;
        });

    }

    public void initObj() {
        mEffectHelper = new EffectHelper(mContext, EffectManager.EffectType.PREVIEW);
        mEffectHelper.setOnEffectListener(this);

        mProcessThread = new HandlerThread("byted-effect-process");
        mProcessThread.start();
        mProcessHandler = new Handler(mProcessThread.getLooper());
    }

    @Override
    public void onStart(ZegoPublishChannel channel) {
    }

    @Override
    public void onStop(ZegoPublishChannel channel) {
    }

    public void dispose() {
        if (mProcessHandler == null) return;
        final CountDownLatch barrier = new CountDownLatch(1);
        mProcessHandler.post(new Runnable() {
            @Override
            public void run() {
                mStopFlag = true;
                release();
                barrier.countDown();
            }
        });

        try {
            barrier.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        mProcessHandler = null;

        mProcessThread.quit();
        mProcessThread = null;
    }

    private void release() {
        try {
            if (mProcessEglContext.hasSurface()) {
                mProcessEglContext.makeCurrent();

                if (mProcessDrawer != null) {
                    mProcessDrawer.release();
                    mProcessDrawer = null;
                }

                if (mProcessInputTextureId != 0) {
                    mProcessInputSurfaceTexture.detachFromGLContext();

                    int[] textures = new int[]{mProcessInputTextureId};
                    GLES20.glDeleteTextures(1, textures, 0);
                    mProcessInputTextureId = 0;
                }

                mEffectHelper.destroy();
                mEffectHelper = null;
            }
            mProcessEglContext.release();
            mProcessEglContext = null;

            mOutputWidth = 0;
            mOutputHeight = 0;

            if (mProcessOutputSurface != null) {
                mProcessOutputSurface.release();
                mProcessOutputSurface = null;
            }

            if (mProcessInputSurfaceTexture != null) {
                mProcessInputSurfaceTexture.setOnFrameAvailableListener(null);
                mProcessInputSurfaceTexture.release();
                mProcessInputSurfaceTexture = null;
            }
        } catch (Exception e) {
            ToastUtils.showShort("操作失败，请重新尝试~");
            IKLog.i("❌❌ 原生zego release e=" + e);
        }
    }

    public String getCurrentTimeStr() {
        java.util.Date dt = new java.util.Date(System.currentTimeMillis());
        SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.US);
        return fmt.format(dt);
    }

    public void bytedanceSetComposeNodes(List<String> nodes, List<String> tag) {
        mProcessHandler.post(() -> {
            mEffectHelper.setComposeNodes(nodes.toArray(new String[]{}), tag.toArray(new String[]{}));
        });
    }

    public void bytedanceSetFilter(String path) {
        mProcessHandler.post(() -> {
            mEffectHelper.setFilter(path);
        });
    }

    public void bytedanceSetSticker(String path) {
        mProcessHandler.post(() -> {
            mEffectHelper.setSticker(path);
        });
    }

    public void bytedanceUpdateFilterIntensity(double value) {
        mProcessHandler.post(() -> {
            mEffectHelper.updateFilterIntensity((float) value);
        });
    }

    public void bytedanceUpdateComposerNodeIntensity(int id, String node, String key, double value) {
        mProcessHandler.post(() -> {
            mEffectHelper.updateComposerNodeIntensity(id, node, key, (float) value);
        });
    }

    @Override
    public void onCapturedUnprocessedTextureData(int textureID, int width, int height, long referenceTimeMillisecond, ZegoPublishChannel channel) {
        // DO NOTHING
    }

    @Override
    public SurfaceTexture getCustomVideoProcessInputSurfaceTexture(int width, int height, ZegoPublishChannel channel) {
        final CountDownLatch barrier = new CountDownLatch(1);

        mProcessHandler.post(() -> {
            // 设置 Surface
            setOutputSurface(ZegoExpressEngine.getEngine().getCustomVideoProcessOutputSurfaceTexture(width, height), width, height);
            barrier.countDown();
        });

        try {
            barrier.await();
        } catch (InterruptedException e) {
            IKLog.i("❌❌ 原生zego getCustomVideoProcessInputSurfaceTexture e=" + e);
        }

        return mProcessInputSurfaceTexture;
    }

    private void setOutputSurface(SurfaceTexture surfaceTexture, int width, int height) {
        try {
            if (mProcessEglContext.hasSurface()) {
                mProcessEglContext.makeCurrent();

                if (mProcessDrawer != null) {
                    mProcessDrawer.release();
                    mProcessDrawer = null;
                }

                if (mProcessInputTextureId != 0) {
                    mProcessInputSurfaceTexture.detachFromGLContext();

                    int[] textures = new int[]{mProcessInputTextureId};
                    GLES20.glDeleteTextures(1, textures, 0);
                    mProcessInputTextureId = 0;
                }

                mEffectHelper.destroy();

                mProcessEglContext.releaseSurface();
                mProcessEglContext.detachCurrent();
            }

            if (mProcessOutputSurface != null) {
                mProcessOutputSurface.release();
                mProcessOutputSurface = null;
            }

            surfaceTexture.setDefaultBufferSize(width, height);

            mOutputWidth = width;
            mOutputHeight = height;

            mProcessOutputSurface = new Surface(surfaceTexture);
            mProcessEglContext.createSurface(mProcessOutputSurface);
            mProcessEglContext.makeCurrent();

            initEffectsHelper();
        } catch (Exception e) {
            ToastUtils.showShort("操作失败，请重新尝试~");
            IKLog.i("❌❌ 原生zego setOutputSurface e=" + e);
        }
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        if (mStopFlag) {
            return;
        }
        if (mProcessInputTextureId == 0) {
            mProcessInputTextureId = GlUtil.generateTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES);
            surfaceTexture.attachToGLContext(mProcessInputTextureId);
        }

        // 需要在GL上下文中调用
        surfaceTexture.updateTexImage();//todo ：java.lang.IllegalStateException: Unable to update texture contents (see logcat for details)
        long timestampNs = surfaceTexture.getTimestamp();

        if (mProcessDrawer == null) {
            mProcessDrawer = new GlRectDrawer();
        }

        if (isBytedEffectsResourceReady) {
            ProcessInput processInput = new ProcessInput();
            processInput.texture = mProcessInputTextureId;
            processInput.textureFormat = BytedEffectConstants.TextureFormat.Texture_Oes;
            processInput.textureSize = new ProcessInput.Size(mOutputWidth, mOutputHeight);

            checkGLES2WithBugReport("mEffectHelper.process start");
            int textureID = mEffectHelper.process(processInput).texture;
            checkGLES2WithBugReport("mEffectHelper.process end");

            // 避免美颜没有恢复正常的环境，这里强制进行恢复。
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

            // 返回美颜数据给 SDK
            mProcessDrawer.drawRgb(textureID, IDENTIFY_MATRIX,
                    mOutputWidth, mOutputHeight, 0, 0, mOutputWidth, mOutputHeight);
        } else {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            mProcessDrawer.drawOes(mProcessInputTextureId, IDENTIFY_MATRIX,
                    0, 0, mOutputWidth, mOutputHeight, mOutputWidth, mOutputHeight);
        }

        if (mIsEgl14) {
            ((EglBase14) mProcessEglContext).swapBuffers(timestampNs);
        } else {
            mProcessEglContext.swapBuffers();
        }
    }

    @Override
    public void onEffectInitialized() {
    }

    private void checkGLES2WithBugReport(String step) {
        int error = GLES20.glGetError();
        if (error != 0) {
            Map map = new HashMap();
            map.put("error",step + ", error: " + error);
            Trackers.getInstance().sendTrackData(map, "GLError", TrackerConstants.LOG_TYPE_BASE);
        }
    }
}
