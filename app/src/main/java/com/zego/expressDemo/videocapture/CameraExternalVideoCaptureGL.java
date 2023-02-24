package com.zego.expressDemo.videocapture;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.camera2.CameraManager;
import android.opengl.GLES11Ext;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;

import com.zego.expressDemo.application.BaseApplication;
import com.zego.expressDemo.config.MediaConfig;
import com.zego.expressDemo.utils.AnalyticsLog;
import com.zego.expressDemo.utils.LogUtils;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import im.zego.zegoexpress.callback.IZegoCustomVideoCaptureHandler;
import im.zego.zegoexpress.constants.ZegoVideoFrameFormat;

public class CameraExternalVideoCaptureGL extends IZegoCustomVideoCaptureHandler implements Camera.PreviewCallback {

    private final static String TAG = CameraExternalVideoCaptureGL.class.getSimpleName();

    private static final int CAMERA_STOP_TIMEOUT_MS = 7000;
    private static final int CAMERA_OPEN_MAX_RETRY_TIME = 1;  // 摄像头启动最大重试次数
    private static final int CAMERA_OPEN_RETRY_DELAY = 1000;  // 摄像头重试时间间隔

    private static final int NUMBER_OF_CAPTURE_BUFFERS = 3;

    private final static int MAX_CAMERA_ID_COUNT = 2; // 最多管理的Camera数量，这里只对前后置摄像头进行管理
    private final static int MSG_RESUME_CAMERA = 0x10;  // 重启摄像头 MSG

    // 唤醒摄像头逻辑
    private final static int RESUME_CAMERA_WAIT_FRAME_COUNT = 3;  // 等待3帧去执行唤醒摄像头操作

    /**
     * 等待指定帧数去执行唤醒Camera任务
     * <p>
     * 前后置摄像头的切换，将会触发当前摄像头有效，再触发切换的摄像头无效。即假设当前使用的是前置摄像头，切换到后置摄像头的时候，将会先触发前置摄像头的有效回调，再触发后置摄像头的无效回调。<br>
     * 为了避免摄像头切换期间导致满足<b>所有摄像头有效</b>的条件，但其实这个时候摄像头是无效的，所以我们需进行延时唤醒摄像头。<br>
     * 如果是切换摄像头，将会在切换成功，触发摄像头无效回调时，移除延时任务。 {@link #removeResumeCameraTask()}
     */
    private final static int RESUME_CAMERA_TASK_DELAY = 1000 / MediaConfig.VideoFpsDefault * RESUME_CAMERA_WAIT_FRAME_COUNT;

    /**
     * 需要进入应用才能执行唤醒Camera操作的后台时长。
     * <p>
     * Android9.0以上，如果当前应用进入后台超过1分钟，系统将不允许当前应用使用摄像头，即任何的恢复手段在后台都是无效的，只能应用回到前台后，才能执行唤醒摄像头操作。
     */
    @TargetApi(21)
    private final static long BACKGROUND_TIME_INTERVAL_NEED_RESUME_CAMERA_FOR_API_29 = 59 * 1000;

    private int mCurrentRetryTime = 0;

    private final CameraManagerActivityLifecycleCallbacks mCameraManagerActivityLifecycleCallbacks;

    @TargetApi(21)
    private CameraManager mCameraManager;

    @TargetApi(21)
    private CameraAvailabilityCallback mCameraAvailabilityCallback;

    // 用于存储前后置摄像头当前的可用状态，主要用于唤醒摄像头，当前后置摄像头都有效的情况下。
    @TargetApi(21)
    private Map<String, Boolean> mCameraIDUsingMap;

    // 指示是否需要在onStart中唤醒摄像头。Android9.0以上，如果当前应用进入后台超过1分钟，系统将不允许当前应用使用摄像头，即任何的恢复手段在后台都是无效的，只能应用回到前台后，才能执行唤醒摄像头操作。
    @TargetApi(21)
    private boolean mNeedResumeCameraWhileOnStart = false;

    // 摄像头重启相关控制变量
    private final Object mPendingCameraRestartLock = new Object();
    private volatile boolean mPendingCameraRestart = false;

    private HandlerThread mThread = null;
    private volatile Handler cameraThreadHandler = null;
    private final AtomicBoolean isCameraRunning = new AtomicBoolean();

    private WeakReference<IZegoVideoFrameConsumer> mConsumerReference;
    private SurfaceTexture mInputSurfaceTexture = null;

    private Camera mCam = null;
    private Camera.CameraInfo mCamInfo = null;
    private int mCamRotation = 0;
    private final Object mCameraSwitchLock = new Object();
    private final Set<byte[]> queuedBuffers = new HashSet<>();
    private byte[] mRemoteVideoData;

    // camera相关参数的初始值
    private int mCameraWidth;
    private int mCameraHeight;
    private int mFrameRate = MediaConfig.VideoFpsDefault;
    private int mFront = 1;  // 默认打开前置摄像头

    private boolean mIsSetColorWatermark = false;
    private byte[] mColorWatermark = new byte[3];

    public CameraExternalVideoCaptureGL(IZegoVideoFrameConsumer consumer) {
        if (mConsumerReference != null) {
            LogUtils.e(" onInitialize  get != null and clear");
            mConsumerReference.clear();
        }

        if (mConsumerReference == null || mConsumerReference.get() == null) {
            LogUtils.e(" onInitialize mConsumerReference == null or get == null");
            mConsumerReference = new WeakReference<>(consumer);
        }

        mCameraManagerActivityLifecycleCallbacks = new CameraManagerActivityLifecycleCallbacks();
        setApplicationContext();
        if (mThread == null) {
            mThread = new HandlerThread("camera-cap");
            mThread.start();
            // 创建camera异步消息处理handler
            cameraThreadHandler = new Handler(mThread.getLooper()) {
                public void handleMessage(Message msg) {
                    if (MSG_RESUME_CAMERA == msg.what) {
                        restartCam();
                    }
                }
            };
            mInputSurfaceTexture = new SurfaceTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES);
            LogUtils.d("初始化 cameraThreadHandler.getLooper().getThread(） =" + cameraThreadHandler.getLooper().getThread());
        }

        LogUtils.e(" onInitialize cameraThreadHandler");
    }

    /**
     * 设置 ApplicationContext 对象，必须执行该方法才能保证功能的正常
     * <br>
     * 内部主要执行了绑定ActivityLifecycleCallbacks 和初始化 {@link CameraManager} 对象
     */
    private void setApplicationContext() {
        BaseApplication.getInstance().registerActivityLifecycleCallbacks(mCameraManagerActivityLifecycleCallbacks);
        if (isApi21()) {
            initCameraManager();

            initCameraIDUsingMap();
            registerCameraAvailabilityCallback();
        }
    }

    public void destroy() {
        LogUtils.d(" onDispose");
        // 停止camera采集任务
        stopCapture();

        if (cameraThreadHandler != null) {
            final CountDownLatch barrier = new CountDownLatch(1);
            cameraThreadHandler.post(() -> {

                if (mInputSurfaceTexture != null) {
                    mInputSurfaceTexture.release();
                    mInputSurfaceTexture = null;
                }

                if (isApi21()) {
                    mCameraManager.unregisterAvailabilityCallback(mCameraAvailabilityCallback);
                    mCameraManager = null;
                    mCameraAvailabilityCallback = null;
                    mCameraIDUsingMap = null;
                    mNeedResumeCameraWhileOnStart = false;
                }

                BaseApplication.getInstance().unregisterActivityLifecycleCallbacks(mCameraManagerActivityLifecycleCallbacks);
                cameraThreadHandler.removeCallbacksAndMessages(null);
                barrier.countDown();
            });
            try {
                barrier.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (cameraThreadHandler != null) {
                cameraThreadHandler.removeCallbacksAndMessages(null);
            }
        }
        cameraThreadHandler = null;

        if (mThread != null) {
            mThread.quit();
        }
        mThread = null;

        if (mConsumerReference != null) {
            mConsumerReference.clear();
        }
    }

    public void start() {
        LogUtils.d("onStart startCapture");
        startCapture();
    }

    public int getFront() {
        return mFront;
    }

    public void stop() {
        LogUtils.d("onStop stopCapture");
        stopCapture();
    }

    private void startCapture() {
        LogUtils.d("startCapture");

        //zego demo中没有这句话，是自己加的。因为会出现inital方法在该方法之后的情况
        if (cameraThreadHandler == null) {
            LogUtils.d(" startCapture cameraThreadHandler == null");
            return;
        }

        if (isCameraRunning.getAndSet(true)) {
            Log.e(TAG, "Camera has already been started.");
            return;
        }

        final boolean didPost = maybePostOnCameraThread(() -> {
            synchronized (mCameraSwitchLock) {
                try {
                    // 创建camera
                    int createRef = createCamOnCameraThread();
                    // 启动camera
                    int startRef = startCamOnCameraThread();

                    if (createRef != 0) {
                        Log.e(TAG, "createCamOnCameraThread error, errorCode: " + createRef);
                    }

                    if (startRef != 0) {
                        Log.e(TAG, "startCamOnCameraThread error, errorCode: " + startRef);
                    }
                } catch (Exception e) {
                    LogUtils.e(TAG, e.getMessage());
                }
            }
        });

        if (!didPost) {
            Log.e(TAG, "Calling startCapture() for already start camera.");
        }
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
            LogUtils.e("CameraExternalVideoCaptureGL", "startCamOnCameraThread", "mInputSurfaceTexture is null");
            return -1;
        }

        try {
            // 设置预览SurfaceTexture
            mCam.setPreviewTexture(mInputSurfaceTexture);
            mCam.setPreviewCallbackWithBuffer(this);
            // 启动camera预览
            mCam.startPreview();
            Log.i(TAG, "startPreview success");
        } catch (Exception e) {
            e.printStackTrace();
            LogUtils.d("startCamOnCameraThread Exception e =" + e.toString());
        }
        return 0;
    }

    private int createCamOnCameraThread() {
        checkIsOnCameraThread();
        if (!isCameraRunning.get()) {
            Log.w(TAG, "createCamOnCameraThread: Camera has stopped");
            return 0;
        }

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
                try {
                    mCam = Camera.open(i);
                } catch (RuntimeException e) {
                    if (mCurrentRetryTime < CAMERA_OPEN_MAX_RETRY_TIME) {
                        mCurrentRetryTime++;
                        Log.e(TAG, "no camera found, open camera error, retry later");
                        cameraThreadHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                restartCam();
                            }
                        }, CAMERA_OPEN_RETRY_DELAY);
                    } else {
                        Log.e(TAG, "no camera found, open camera error, do not retry");
                    }
                    return -1;
                }
                break;
            }
        }

        // 没找到欲设置的camera
        if (mCam == null) {
            Log.w(TAG, "[WARNING] no camera found, try default");
            // 先试图打开默认camera
            mCam = Camera.open();
            if (mCam == null) {
                if (mCurrentRetryTime < CAMERA_OPEN_MAX_RETRY_TIME) {
                    mCurrentRetryTime++;
                    Log.e(TAG, "no camera found, open camera error, retry later");
                    cameraThreadHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            restartCam();
                        }
                    }, CAMERA_OPEN_RETRY_DELAY);
                } else {
                    Log.e(TAG, "no camera found, open camera error, do not retry");
                }
                return -1;
            }
        }
        Camera.Parameters parms = mCam.getParameters();

        // 获取camera首选的size
        Camera.Size size = getOptimalPreviewSize(mCam, MediaConfig.mVideoCaptureWidth, MediaConfig.mVideoCaptureHeight);
        // 设置camera的采集视图size
        if (size != null) {
            parms.setPreviewSize(size.width, size.height);
            mCameraWidth = size.width;
            mCameraHeight = size.height;
        } else {
            parms.setPreviewSize(MediaConfig.mVideoCaptureWidth, MediaConfig.mVideoCaptureHeight);
            mCameraWidth = MediaConfig.mVideoCaptureWidth;
            mCameraHeight = MediaConfig.mVideoCaptureHeight;
        }

        LogUtils.d("createCameraSize mCameraWidth*mCameraHeight2 == " + mCameraWidth + "*" + mCameraHeight);
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
                    Log.i(TAG, "[WARNING] vcap: set focus mode error (stack trace followed)!!!");
                    ex.printStackTrace();
                }
            }
        }
        if (!bFocusModeSet) {
            Log.i(TAG, "[WARNING] vcap: focus mode left unset !!");
        }

        try {
            // 设置camera的参数
            mCam.setParameters(parms);
        } catch (Exception ex) {
            Log.w(TAG, "vcap: set camera parameters error with exception");
            ex.printStackTrace();
        }

        Camera.Parameters actualParm = mCam.getParameters();
        mCameraWidth = actualParm.getPreviewSize().width;
        mCameraHeight = actualParm.getPreviewSize().height;
        mCamRotation = mCamInfo.orientation;

        AnalyticsLog.INSTANCE.reportMakeupModelInfo(mCameraWidth + "*" + mCameraHeight);

        createPool();
        return 0;
    }

    // 停止推流时
    private void stopCapture() {
        Log.d(TAG, "stopCapture");
        final boolean didPost = maybePostOnCameraThread(() -> {
            synchronized (mCameraSwitchLock) {
                // 停止camera
                stopCaptureOnCameraThread();
                // 释放camera资源
                releaseCam();
            }
        });
        if (!didPost) {
            Log.e(TAG, "Calling stopCapture() for already stopped camera.");
            return;
        }
        Log.i(TAG, "stopCapture done");
        isCameraRunning.set(false);
    }

    // camera停止采集
    private void stopCaptureOnCameraThread() {
        checkIsOnCameraThread();
        Log.d(TAG, "stopCaptureOnCameraThread");

        if (cameraThreadHandler != null) {
            cameraThreadHandler.removeCallbacksAndMessages(this);
        }

        if (mCam != null) {
            // 停止camera预览
            try {
                mCam.stopPreview();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // 为camera分配内存存放采集数据
    private void createPool() {
        queuedBuffers.clear();
        int frameSize = mCameraWidth * mCameraHeight * 3 / 2;
        for (int i = 0; i < NUMBER_OF_CAPTURE_BUFFERS; ++i) {
            final ByteBuffer buffer = ByteBuffer.allocateDirect(frameSize);
            queuedBuffers.add(buffer.array());
            // 减少camera预览时的内存占用
            mCam.addCallbackBuffer(buffer.array());
        }
    }

    private void checkIsOnCameraThread() {
        if (cameraThreadHandler == null) {
            LogUtils.e(TAG, "Camera is not initialized - can't check thread.");
        } else if (Thread.currentThread() != cameraThreadHandler.getLooper().getThread()) {
//            LogUtils.d("Thread.currentThread() =" + Thread.currentThread().getName());
//            LogUtils.d("cameraThreadHandler.getLooper().getThread(） =" + cameraThreadHandler.getLooper().getThread());
            throw new IllegalStateException("Wrong thread");
        }
    }

    private boolean maybePostOnCameraThread(Runnable runnable) {
        if (cameraThreadHandler == null) {
            LogUtils.d(" xiangxing maybePostOnCameraThread cameraThreadHandler == null");
        }

        if (!isCameraRunning.get()) {
            LogUtils.d(" xiangxing maybePostOnCameraThread isCameraRunning.get() == " +
                    isCameraRunning.get());
        }

        return cameraThreadHandler != null && isCameraRunning.get()
                && cameraThreadHandler.postAtTime(runnable, this, SystemClock.uptimeMillis());
    }


    public void switchCamera() {
        mFront = mFront ^ 1;
        restartCam();
    }

    public void addColorWatermark(int color) {
        mIsSetColorWatermark = true;
//        MediaPreprocess.convertRGB2YUV(color, mColorWatermark);
    }

    public void removeColorWatermark() {
        mIsSetColorWatermark = false;
    }

    // 释放camera
    private void releaseCam() {
        // * release cam
        if (mCam != null) {
            mCam.release();
            mCam = null;
        }

        // * release cam info
        mCamInfo = null;
    }

    // 重启camera
    private void restartCam() {
        if (!isCameraRunning.get()) {
            Log.w(TAG, "restartCam Camera has already been stopped.");
            return;
        }
        synchronized (mPendingCameraRestartLock) {
            if (mPendingCameraRestart) {
                // Do not handle multiple camera switch request to avoid blocking
                // camera thread by handling too many switch request from a queue.
                return;
            }
            mPendingCameraRestart = true;
        }

        cameraThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                stopCaptureOnCameraThread();
                releaseCam();
                createCamOnCameraThread();
                startCamOnCameraThread();
                synchronized (mPendingCameraRestartLock) {
                    mPendingCameraRestart = false;
                }
            }
        });
    }

    /**
     * @return 是否Android5.0或以上版本
     */
    private boolean isApi21() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    }

    /**
     * @return 是否Android9.0或以上版本
     */
    private boolean isApi28() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P;
    }

    @TargetApi(21)
    private void initCameraManager() {
        mCameraManager = (CameraManager) BaseApplication.getInstance().getSystemService(Context.CAMERA_SERVICE);
    }

    @TargetApi(21)
    private void registerCameraAvailabilityCallback() {
        mCameraAvailabilityCallback = new CameraAvailabilityCallback();
        mCameraManager.registerAvailabilityCallback(mCameraAvailabilityCallback, cameraThreadHandler);
    }

    @TargetApi(21)
    private void initCameraIDUsingMap() {
        mCameraIDUsingMap = new HashMap<>();
        // 获取当前所有连接中的摄像头设备ID
        String[] cameraIDList = new String[0];
        try {
            cameraIDList = mCameraManager.getCameraIdList();
        } catch (Exception ignore) {
        }

        // 最多对前后置摄像头进行管理
        int maxCameraIDCount = Math.min(cameraIDList.length, MAX_CAMERA_ID_COUNT);
        for (int i = 0; i < maxCameraIDCount; i++) {
            mCameraIDUsingMap.put(cameraIDList[i], true);
        }
    }

    /**
     * 后台时长是否超过指定时长 {@link #BACKGROUND_TIME_INTERVAL_NEED_RESUME_CAMERA_FOR_API_29}
     */
    @TargetApi(28)
    private boolean isMoreThanBackgroundTimeInterval() {
        return false;
    }

    /**
     * 启动延时任务去唤醒摄像头
     *
     * @see #removeResumeCameraTask()
     */
    private void startResumeCameraTask() {
        // 如果存在延时唤醒摄像头任务，则返回
        if (cameraThreadHandler.hasMessages(MSG_RESUME_CAMERA)) {
            return;
        }
        // 启动延时任务去唤醒摄像头
        cameraThreadHandler.sendEmptyMessageDelayed(MSG_RESUME_CAMERA, RESUME_CAMERA_TASK_DELAY);
    }

    /**
     * 移除唤醒摄像头的延时任务
     *
     * @see #startResumeCameraTask()
     */
    private void removeResumeCameraTask() {
        cameraThreadHandler.removeMessages(MSG_RESUME_CAMERA);
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        checkIsOnCameraThread();
        removeResumeCameraTask();

        if (!isCameraRunning.get()) {
            Log.e(TAG, "onPreviewFrame: Camera is stopped");
            return;
        }

        if (!queuedBuffers.contains(data)) {
            // |data| is an old invalid buffer.
            return;
        }
//        if (mIsSetColorWatermark && data != null) {
//            Arrays.fill(data, 0, mCameraWidth * mCameraHeight, mColorWatermark[0]);
//            for (int i = mCameraWidth * mCameraHeight; i < data.length - 1; i += 2) {
//                data[i] = mColorWatermark[2];
//                data[i + 1] = mColorWatermark[1];
//            }
//        } else {
//            Senseme.stProcessNv21BufferWithoutGL(data, mCameraWidth, mCameraHeight, 360 - mCamRotation, false);
//        }

        // 将采集的数据传给ZEGO SDK
        if (mConsumerReference.get() != null) {
            mConsumerReference.get().consumeByteArrayFrame(data, ZegoVideoFrameFormat.NV21.value(), mCameraWidth, mCameraHeight, mCamRotation, System.currentTimeMillis());
        }
        // 实现camera预览时的内存复用
        camera.addCallbackBuffer(data);
    }

    /**
     * 获取合适的支持的预览分辨率，注意这个只适应横屏分辨率（一般系统 API 返回的都是横屏分辨率）
     * 获取规则：（按下面顺序，如果前面的条件满足即返回）
     * 1、分辨率一致，直接返回
     * 2、分辨率比例必须要在设置的分辨率比例范围内，最大浮动 ±scaleFloatingRange，面积接近要求的返回。（比例优先，避免差别过大导致画面裁剪严重）
     * 3、如果 2 步骤没选择到合适的，即分辨率跟要求的差别比较大，那么选择面积最接近的。
     *
     * @param camera             系统支持的分辨率列表
     * @param requestedWidth     要求的分辨率大小的宽
     * @param requestedHeight    要求的分辨率大小的高
     * @param shortSideLimit     短边的限制（这里为了简单，只会跟高进行判断），如果系统支持要求的分辨率，该短边限制将无效。如果支持的分辨率大小的高都比短边的大，该短边限制逻辑也会无效
     * @param scaleFloatingRange 分辨率比例最大浮动范围。如果没有比例在这浮动范围内，该参数无效。建议值为 0.3f
     * @return 合适的支持的预览分辨率
     */
    public static Camera.Size getSuitableSupportedSize(Camera camera, int requestedWidth, int requestedHeight, int shortSideLimit, float scaleFloatingRange) {
        List<Camera.Size> supportedSizes = camera.getParameters().getSupportedPreviewSizes();

        if (supportedSizes == null || supportedSizes.isEmpty()) {
            return null;
        }

        Camera.Size resultSize = null;

        // 步骤1
        List<Camera.Size> filteredSupportedSizes = new ArrayList<>();
        for (Camera.Size supportedSize : supportedSizes) {
            if (supportedSize.width == requestedWidth && supportedSize.height == requestedHeight) {
                return supportedSize;
            }

            if (supportedSize.height <= shortSideLimit) {
                filteredSupportedSizes.add(supportedSize);
            }
        }

        // 如果都不满足短边要求，那么就使用系统返回的进行后续逻辑判断
        if (filteredSupportedSizes.isEmpty()) {
            filteredSupportedSizes = supportedSizes;
        }

        float requestedResolutionScale = requestedWidth / (float) requestedHeight;
        long requestedResolutionArea = requestedWidth * requestedHeight;

        // 步骤2
        for (Camera.Size filteredSupportedSize : filteredSupportedSizes) {
            float resolutionScale = filteredSupportedSize.width / (float) filteredSupportedSize.height;
            if (Math.abs(requestedResolutionScale - resolutionScale) > scaleFloatingRange) {
                continue;
            }
            // 选择面积最接近目标的
            if (resultSize == null || Math.abs(filteredSupportedSize.width * filteredSupportedSize.height - requestedResolutionArea)
                    < Math.abs(resultSize.width * resultSize.height - requestedResolutionArea)) {
                resultSize = filteredSupportedSize;
            }
        }

        if (resultSize != null) {
            return resultSize;
        }

        // 步骤3
        for (Camera.Size filteredSupportedSize : filteredSupportedSizes) {
            if (resultSize == null ||
                    // 选择面积最接近目标的
                    Math.abs(filteredSupportedSize.width * filteredSupportedSize.height - requestedResolutionArea)
                            < Math.abs(resultSize.width * resultSize.height - requestedResolutionArea)) {
                resultSize = filteredSupportedSize;
            }
        }
        return resultSize;
    }


    public static Camera.Size getOptimalPreviewSize(Camera camera, int width, int height) {
        Camera.Size optimalSize = null;
        double minHeightDiff = Double.MAX_VALUE;
        double minWidthDiff = Double.MAX_VALUE;
        try {
            List<Camera.Size> sizes = camera.getParameters().getSupportedPreviewSizes();
            if (sizes == null) {
                return null;
            }
            // 找到宽度差距最小的
            for (Camera.Size size : sizes) {
                if (Math.abs(size.width - width) < minWidthDiff) {
                    minWidthDiff = Math.abs(size.width - width);
                }
            }
            // 在宽度差距最小的里面，找到高度差距最小的
            for (Camera.Size size : sizes) {
                if (Math.abs(size.width - width) == minWidthDiff) {
                    if (Math.abs(size.height - height) < minHeightDiff) {
                        optimalSize = size;
                        minHeightDiff = Math.abs(size.height - height);
                    }
                }
            }
            if (optimalSize != null) {
                HashMap<String, String> reportMap = new HashMap<>();
                reportMap.put("CameraSize", optimalSize.width + "*" + optimalSize.height);
//                AnalyticsLog.INSTANCE.reportAFConversionDataSuccess(reportMap);
            }
        } catch (Exception e) {
            e.printStackTrace();
            optimalSize = null;
        }
        return optimalSize;
    }

    private class CameraManagerActivityLifecycleCallbacks implements Application.ActivityLifecycleCallbacks {
        @Override
        public void onActivityCreated(Activity activity, Bundle savedInstanceState) {

        }

        @Override
        public void onActivityStarted(Activity activity) {
            if (!isApi21() || mNeedResumeCameraWhileOnStart) {
                startResumeCameraTask();
                mNeedResumeCameraWhileOnStart = false;
            }
        }

        @Override
        public void onActivityResumed(Activity activity) {

        }

        @Override
        public void onActivityPaused(Activity activity) {

        }

        @Override
        public void onActivityStopped(Activity activity) {
        }

        @Override
        public void onActivitySaveInstanceState(Activity activity, Bundle outState) {

        }

        @Override
        public void onActivityDestroyed(Activity activity) {

        }
    }

    /**
     * 摄像头变成可用和不可用回调
     * <p>
     * 系统上所有应用导致的摄像头可用和不可用都会触发该回调。
     */
    @TargetApi(21)
    private class CameraAvailabilityCallback extends CameraManager.AvailabilityCallback {
        @Override
        public void onCameraAvailable(String cameraId) {
            // 设置 对于CameraID 的 Camera是否可用
            if (mCameraIDUsingMap.containsKey(cameraId)) {
                mCameraIDUsingMap.put(cameraId, true);

                // 如果允许使用摄像头，才需检查是否需要执行唤醒摄像头操作。
                if (isCameraRunning.get()) {
                    boolean isAllCameraAvailable = true;

                    for (String key : mCameraIDUsingMap.keySet()) {
                        Boolean isCameraAvailable = mCameraIDUsingMap.get(key);
                        if (isCameraAvailable == null || !isCameraAvailable) {
                            isAllCameraAvailable = false;
                            break;
                        }
                    }
                    if (isAllCameraAvailable) {
                        // Android9.0以上，如果当前应用进入后台超过1分钟，系统将不允许当前应用使用摄像头，即任何的恢复手段在后台都是无效的，只能应用回到前台后，才能执行唤醒摄像头操作。
                        if (isApi28() && isMoreThanBackgroundTimeInterval()) {
                            mNeedResumeCameraWhileOnStart = true;
                        } else {
                            mNeedResumeCameraWhileOnStart = false;
                            startResumeCameraTask();
                        }
                    }
                }
            }
        }

        @Override
        public void onCameraUnavailable(String cameraId) {
            // 设置 对于CameraID 的 Camera是否可用
            if (mCameraIDUsingMap.containsKey(cameraId)) {
                mCameraIDUsingMap.put(cameraId, false);
                mNeedResumeCameraWhileOnStart = false;
                removeResumeCameraTask();
            }
        }
    }
}
