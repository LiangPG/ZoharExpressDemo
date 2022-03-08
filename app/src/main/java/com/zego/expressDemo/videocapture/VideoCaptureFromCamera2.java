package com.zego.expressDemo.videocapture;

import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;

import com.zego.expressDemo.ve_gl.EglBase;
import com.zego.expressDemo.ve_gl.EglBase14;
import com.zego.expressDemo.ve_gl.GlRectDrawer;
import com.zego.expressDemo.ve_gl.GlUtil;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import im.zego.zegoexpress.ZegoExpressEngine;
import im.zego.zegoexpress.callback.IZegoCustomVideoCaptureHandler;
import im.zego.zegoexpress.constants.ZegoPublishChannel;
import im.zego.zegoexpress.constants.ZegoVideoFlipMode;
import im.zego.zegoexpress.entity.ZegoTrafficControlInfo;

/**
 * VideoCaptureFromCamera2
 * 实现从摄像头采集数据并传给ZEGO SDK，需要继承实现ZEGO SDK 的ZegoVideoCaptureDevice类
 * 采用SURFACE_TEXTURE方式传递数据，将client返回的SurfaceTexture对象转为EglSurface再进行图像绘制
 */

public class VideoCaptureFromCamera2 extends IZegoCustomVideoCaptureHandler implements SurfaceTexture.OnFrameAvailableListener {
    private static final String TAG = "VideoCaptureFromCamera2";
    private static final int CAMERA_STOP_TIMEOUT_MS = 7000;

    private Camera mCam = null;
    private Camera.CameraInfo mCamInfo = null;
    // camera相关参数的初始值
    private int mFront = 1;
    private int mCameraWidth = 640;
    private int mCameraHeight = 480;
    private int mCaptureWidth = 0;
    private int mCaptureHeight = 0;
    private int mViewWidth = 0;
    private int mViewHeight = 0;
    private int mViewMode = 0;
    private int mFrameRate = 15;
    private int mDisplayRotation = 0;
    private int mImageRotation = 0;

    // 用于帧缓冲区对象（FBO）的相关变量
    private EglBase mDummyContext = null;
    private GlRectDrawer mDummyDrawer = null;
    private boolean mIsEgl14 = false;
    private int mInputTextureId = 0;
    private SurfaceTexture mInputSurfaceTexture = null;
    private final float[] INPUT_MATRIX = new float[16];
    private int mTextureId = 0;
    private int mFrameBufferId = 0;

    // 纹理变换矩阵
    private final float[] IDENTITY_MATRIX = new float[]{1.0f, 0.0f, 0.0f, 0.0f,
            0.0f, 1.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 1.0f, 0.0f,
            0.0f, 0.0f, 0.0f, 1.0f};

    private HandlerThread mThread = null;
    private volatile Handler cameraThreadHandler = null;
    private final AtomicBoolean isCameraRunning = new AtomicBoolean();
    private final Object pendingCameraRestartLock = new Object();
    private volatile boolean pendingCameraRestart = false;

    @Override
    public void onStart(ZegoPublishChannel channel) {
        mThread = new HandlerThread("camera-cap");
        mThread.start();
        // 创建camera异步消息处理handler
        cameraThreadHandler = new Handler(mThread.getLooper());

        final CountDownLatch barrier = new CountDownLatch(1);
        cameraThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                mDummyContext = EglBase.create(null, EglBase.CONFIG_PIXEL_BUFFER);

                try {
                    // 创建Surface
                    mDummyContext.createDummyPbufferSurface();
                    // 绑定eglContext、eglDisplay、eglSurface
                    mDummyContext.makeCurrent();
                    // 创建绘制类，用于FBO
                    mDummyDrawer = new GlRectDrawer();
                } catch (RuntimeException e) {
                    // Clean up before rethrowing the exception.
                    mDummyContext.releaseSurface();
                    e.printStackTrace();
                    throw e;
                }

                mIsEgl14 = EglBase14.isEGL14Supported();
                mInputTextureId = GlUtil.generateTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES);
                mInputSurfaceTexture = new SurfaceTexture(mInputTextureId);
                // 设置视频帧回调监听
                mInputSurfaceTexture.setOnFrameAvailableListener(VideoCaptureFromCamera2.this);

                barrier.countDown();
            }
        });
        try {
            barrier.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        startCamera();
    }

    @Override
    public void onStop(ZegoPublishChannel channel) {
        stopCamera();

        if (cameraThreadHandler != null) {
            final CountDownLatch barrier = new CountDownLatch(1);
            cameraThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    mInputSurfaceTexture.release();
                    mInputSurfaceTexture = null;

                    // 绑定eglContext、eglDisplay、eglSurface
                    mDummyContext.makeCurrent();
                    if (mInputTextureId != 0) {
                        int[] textures = new int[]{mInputTextureId};
                        GLES20.glDeleteTextures(1, textures, 0);
                        mInputTextureId = 0;
                    }

                    if (mTextureId != 0) {
                        int[] textures = new int[]{mTextureId};
                        GLES20.glDeleteTextures(1, textures, 0);
                        mTextureId = 0;
                    }

                    if (mFrameBufferId != 0) {
                        int[] frameBuffers = new int[]{mFrameBufferId};
                        GLES20.glDeleteFramebuffers(1, frameBuffers, 0);
                        mFrameBufferId = 0;
                    }

                    mDummyDrawer = null;
                    mDummyContext.release();
                    mDummyContext = null;

                    barrier.countDown();
                }
            });
            try {
                barrier.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        mThread.quit();
        mThread = null;
    }

    @Override
    public void onEncodedDataTrafficControl(ZegoTrafficControlInfo trafficControlInfo, ZegoPublishChannel channel) {
    }

    // 启动camera
    protected int startCamera() {
        if (isCameraRunning.getAndSet(true)) {
            Log.e(TAG, "Camera has already been started.");
            return 0;
        }

        final boolean didPost = maybePostOnCameraThread(new Runnable() {
            @Override
            public void run() {
                // * Create and Start Cam
                createCamOnCameraThread();
                startCamOnCameraThread();
            }
        });

        return 0;
    }

    // 关闭camera
    protected int stopCamera() {
        final CountDownLatch barrier = new CountDownLatch(1);
        final boolean didPost = maybePostOnCameraThread(new Runnable() {
            @Override
            public void run() {
                stopCaptureOnCameraThread(true /* stopHandler */);
                releaseCam();
                barrier.countDown();
            }
        });
        if (!didPost) {
            Log.e(TAG, "Calling stopCapture() for already stopped camera.");
            return 0;
        }
        try {
            if (!barrier.await(CAMERA_STOP_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                Log.e(TAG, "Camera stop timeout");
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Log.d(TAG, "stopCapture done");

        return 0;
    }

    private void checkIsOnCameraThread() {
        if (cameraThreadHandler == null) {
            Log.e(TAG, "Camera is not initialized - can't check thread.");
        } else if (Thread.currentThread() != cameraThreadHandler.getLooper().getThread()) {
            throw new IllegalStateException("Wrong thread");
        }
    }

    private boolean maybePostOnCameraThread(Runnable runnable) {
        return cameraThreadHandler != null && isCameraRunning.get()
                && cameraThreadHandler.postAtTime(runnable, this, SystemClock.uptimeMillis());
    }

    // 创建camera
    private int createCamOnCameraThread() {
        checkIsOnCameraThread();
        if (!isCameraRunning.get()) {
            Log.e(TAG, "startCaptureOnCameraThread: Camera is stopped");
            return 0;
        }

        Log.i(TAG, "board: " + Build.BOARD);
        Log.i(TAG, "device: " + Build.DEVICE);
        Log.i(TAG, "manufacturer: " + Build.MANUFACTURER);
        Log.i(TAG, "brand: " + Build.BRAND);
        Log.i(TAG, "model: " + Build.MODEL);
        Log.i(TAG, "product: " + Build.PRODUCT);
        Log.i(TAG, "sdk: " + Build.VERSION.SDK_INT);

        // 获取欲设置camera的索引号
        int nFacing = (mFront != 0) ? Camera.CameraInfo.CAMERA_FACING_FRONT : Camera.CameraInfo.CAMERA_FACING_BACK;

        if (mCam != null) {
            // 已打开camera
            return 0;
        }

        mCamInfo = new Camera.CameraInfo();
        // 获取camera的数目
        int nCnt = Camera.getNumberOfCameras();
        // 得到欲设置camera的索引号并打开camera
        for (int i = 0; i < nCnt; i++) {
            Camera.getCameraInfo(i, mCamInfo);
            if (mCamInfo.facing == nFacing) {
                mCam = Camera.open(i);
                break;
            }
        }

        // 没找到欲设置的camera
        if (mCam == null) {
            Log.i(TAG, "[WARNING] no camera found, try default\n");
            // 先试图打开默认camera
            mCam = Camera.open();

            if (mCam == null) {
                Log.i(TAG, "[ERROR] no camera found\n");
                return -1;
            }
        }

        // *
        // * Now set preview size
        // *
        boolean bSizeSet = false;
        Camera.Parameters parms = mCam.getParameters();
        // 获取camera首选的size
        Camera.Size psz = parms.getPreferredPreviewSizeForVideo();

        // 设置camera的采集视图size
        parms.setPreviewSize(psz.width, psz.height);
        mCameraWidth = psz.width;
        mCameraHeight = psz.height;

        // 获取camera支持的帧率范围，并设置预览帧率范围
        List<int[]> supported = parms.getSupportedPreviewFpsRange();

        for (int[] entry : supported) {
            if ((entry[0] == entry[1]) && entry[0] == mFrameRate * 1000) {
                parms.setPreviewFpsRange(entry[0], entry[1]);
                break;
            }
        }

        // 获取camera的实际帧率
        int[] realRate = new int[2];
        parms.getPreviewFpsRange(realRate);
        if (realRate[0] == realRate[1]) {
            mFrameRate = realRate[0] / 1000;
        } else {
            mFrameRate = realRate[1] / 2 / 1000;
        }

        // 设置camera的对焦模式
        boolean bFocusModeSet = false;
        for (String mode : parms.getSupportedFocusModes()) {
            if (mode.compareTo(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO) == 0) {
                try {
                    parms.setFocusMode(mode);
                    bFocusModeSet = true;
                    break;
                } catch (Exception ex) {
                    Log.i(TAG, "[WARNING] vcap: set focus mode error (stack trace followed)!!!\n");
                    ex.printStackTrace();
                }
            }
        }
        if (!bFocusModeSet) {
            Log.i(TAG, "[WARNING] vcap: focus mode left unset !!\n");
        }

        try {
            // 设置camera的参数
            mCam.setParameters(parms);
        } catch (Exception ex) {
            Log.i(TAG, "vcap: set camera parameters error with exception\n");
            ex.printStackTrace();
        }

        Camera.Parameters actualParm = mCam.getParameters();
        mCameraWidth = actualParm.getPreviewSize().width;
        mCameraHeight = actualParm.getPreviewSize().height;
        Log.i(TAG, "[WARNING] vcap: focus mode " + actualParm.getFocusMode());

        int result;
        if (mCamInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (mCamInfo.orientation + mDisplayRotation) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {  // back-facing
            result = (mCamInfo.orientation - mDisplayRotation + 360) % 360;
        }
        // 设置预览图像的转方向
        mCam.setDisplayOrientation(result);
        mImageRotation = result;

        // 绑定eglContext、eglDisplay、eglSurface
        mDummyContext.makeCurrent();
        ;
        Log.d(TAG, "ImageRotation:" + mImageRotation);
        if (mTextureId != 0) {
            int[] textures = new int[]{mTextureId};
            GLES20.glDeleteTextures(1, textures, 0);
            mTextureId = 0;
        }

        if (mFrameBufferId != 0) {
            int[] frameBuffers = new int[]{mFrameBufferId};
            GLES20.glDeleteFramebuffers(1, frameBuffers, 0);
            mFrameBufferId = 0;
        }

        return 0;
    }

    // 启动camera
    private int startCamOnCameraThread() {
        checkIsOnCameraThread();
        if (!isCameraRunning.get() || mCam == null) {
            Log.e(TAG, "startPreviewOnCameraThread: Camera is stopped");
            return 0;
        }

        // * mCam.setDisplayOrientation(90);
        if (mInputSurfaceTexture == null) {
            Log.e(TAG, "mInputSurfaceTexture == null");
            return -1;
        }

        try {
            // 设置预览SurfaceTexture
            mCam.setPreviewTexture(mInputSurfaceTexture);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // 启动camera预览
        mCam.startPreview();
        Log.e(TAG, "startPreview success");

        return 0;
    }

    // camera停止采集
    private int stopCaptureOnCameraThread(boolean stopHandler) {
        checkIsOnCameraThread();
        Log.d(TAG, "stopCaptureOnCameraThread");

        if (stopHandler) {
            // Clear the cameraThreadHandler first, in case stopPreview or
            // other driver code deadlocks. Deadlock in
            // android.hardware.Camera._stopPreview(Native Method) has
            // been observed on Nexus 5 (hammerhead), OS version LMY48I.
            // The camera might post another one or two preview frames
            // before stopped, so we have to check |isCameraRunning|.
            // Remove all pending Runnables posted from |this|.
            isCameraRunning.set(false);
            cameraThreadHandler.removeCallbacksAndMessages(this /* token */);
        }

        if (mCam != null) {
            // 停止camera预览
            mCam.stopPreview();
        }
        return 0;
    }

    // 重启camera
    private int restartCam() {
        synchronized (pendingCameraRestartLock) {
            if (pendingCameraRestart) {
                // Do not handle multiple camera switch request to avoid blocking
                // camera thread by handling too many switch request from a queue.
                Log.w(TAG, "Ignoring camera switch request.");
                return 0;
            }
            pendingCameraRestart = true;
        }

        final boolean didPost = maybePostOnCameraThread(new Runnable() {
            @Override
            public void run() {
                stopCaptureOnCameraThread(false);
                releaseCam();
                createCamOnCameraThread();
                startCamOnCameraThread();
                synchronized (pendingCameraRestartLock) {
                    pendingCameraRestart = false;
                }
            }
        });

        if (!didPost) {
            synchronized (pendingCameraRestartLock) {
                pendingCameraRestart = false;
            }
        }

        return 0;
    }

    // 释放camera
    private int releaseCam() {
        // * release cam
        if (mCam != null) {
            mCam.release();
            mCam = null;
        }

        // * release cam info
        mCamInfo = null;
        return 0;
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        // 绑定eglContext、eglDisplay、eglSurface
        mDummyContext.makeCurrent();
        surfaceTexture.updateTexImage();
        long timestamp = surfaceTexture.getTimestamp();
        surfaceTexture.getTransformMatrix(INPUT_MATRIX);

        // do preprocessing here such as camera 360 sdk

        // 纠正图像展示方向
        int width = mCameraWidth;
        int height = mCameraHeight;
        if (mImageRotation == 90 || mImageRotation == 270) {
            int temp = width;
            width = height;
            height = temp;
        }

        if (mTextureId == 0) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            mTextureId = GlUtil.generateTexture(GLES20.GL_TEXTURE_2D);
            // 生成2D纹理
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);

            // 创建帧缓冲对象，绘制纹理到帧缓冲区并返回缓冲区索引
            mFrameBufferId = GlUtil.generateFrameBuffer(mTextureId);
            Log.e("zego", "生成的mFrameBufferId=" + mFrameBufferId);
        } else {
            // 绑定帧缓冲区
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBufferId);
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        // 绘制OES texture
        mDummyDrawer.drawOes(mInputTextureId, INPUT_MATRIX,
                width, height, 0, 0, width, height);
        // 解邦帧缓冲区
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

        // 默认 Y
        ZegoExpressEngine.getEngine().setCustomVideoCaptureFlipMode(ZegoVideoFlipMode.NONE);
        ZegoExpressEngine.getEngine().sendCustomVideoCaptureTextureData(mTextureId, width, height, System.currentTimeMillis());
    }
}
