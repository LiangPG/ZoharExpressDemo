package com.zego.expressDemo.filter;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.opengl.GLES20;

import com.sensetime.effects.Senseme;
import com.zego.expressDemo.application.BaseApplication;
import com.zego.expressDemo.utils.LogUtils;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import im.zego.zegoexpress.ZegoExpressEngine;
import im.zego.zegoexpress.callback.IZegoCustomVideoProcessHandler;
import im.zego.zegoexpress.constants.ZegoPublishChannel;

public class STFilter extends IZegoCustomVideoProcessHandler {

    private static final String TAG = STFilter.class.getSimpleName();

    private final Context mContext;

    private int mInTextureWidth;
    private int mInTextureHeight;
    private byte[] mRgbaByteArray;
    private ByteBuffer mReadPixelsByteBuffer;
    private static int[] mReadPixelsFrameBuffer;
    private static int[] mBeautifyOutputTextureId;

    private static STMobileHumanActionNative mSTHumanActionNative = new STMobileHumanActionNative();
    private static STHumanAction mHumanAction = new STHumanAction();
    private static boolean mBeautyChange = false;
    private static boolean mStickerChange = false;
    private static boolean mFilterChange = false;
    private static boolean mMakeUpChange = false;
    private static int mStickerId;

    /**
     * 共用信息
     **/
    // 状态信息
    private static boolean mNeedBeautify = false;
    private static boolean mNeedSticker = false;
    private static boolean mNeedFilter = false;
    private static boolean mNeedMakeUp = false;
    private static boolean mNeedCheckHumanAction = true;
    //美妆开关配置本地缓存key
    public static String KEY_MAKE_UP_PROFILE = "key_make_up_profile";

    public STFilter(Context context) {
        mContext = context;
    }

    // 美颜相关
    private static ConcurrentHashMap<Integer, Integer> mBeautifyParams = new ConcurrentHashMap<Integer, Integer>() {
        {
            put(STEffectBeautyType.EFFECT_BEAUTY_BASE_FACE_SMOOTH, 50);
            put(STEffectBeautyType.EFFECT_BEAUTY_BASE_WHITTEN, 20);
            put(STEffectBeautyType.EFFECT_BEAUTY_BASE_REDDEN, 0);
            put(STEffectBeautyType.EFFECT_BEAUTY_TONE_CONTRAST, 0);  //对比度
            put(STEffectBeautyType.EFFECT_BEAUTY_TONE_SATURATION, 0); //饱和度
            put(STEffectBeautyType.EFFECT_BEAUTY_RESHAPE_ENLARGE_EYE, 29);
            put(STEffectBeautyType.EFFECT_BEAUTY_RESHAPE_SHRINK_FACE, 34);
            put(STEffectBeautyType.EFFECT_BEAUTY_RESHAPE_NARROW_FACE, 25);

            put(STEffectBeautyType.EFFECT_BEAUTY_PLASTIC_HAIRLINE_HEIGHT, 20);
            put(STEffectBeautyType.EFFECT_BEAUTY_PLASTIC_NOSE_LENGTH, 0);
            put(STEffectBeautyType.EFFECT_BEAUTY_PLASTIC_NARROW_NOSE, 21);
            put(STEffectBeautyType.EFFECT_BEAUTY_PLASTIC_PHILTRUM_LENGTH, 0);
            put(STEffectBeautyType.EFFECT_BEAUTY_PLASTIC_MOUTH_SIZE, 50);
            put(STEffectBeautyType.EFFECT_BEAUTY_PLASTIC_CHIN_LENGTH, 20);
            put(STEffectBeautyType.EFFECT_BEAUTY_RESHAPE_SHRINK_JAW, 10);
            put(STEffectBeautyType.EFFECT_BEAUTY_PLASTIC_PROFILE_RHINOPLASTY, 10);
            put(STEffectBeautyType.EFFECT_BEAUTY_PLASTIC_OPEN_CANTHUS, 0);
            put(STEffectBeautyType.EFFECT_BEAUTY_PLASTIC_BRIGHT_EYE, 25);
            put(STEffectBeautyType.EFFECT_BEAUTY_PLASTIC_APPLE_MUSLE, 30);
            put(STEffectBeautyType.EFFECT_BEAUTY_PLASTIC_REMOVE_NASOLABIAL_FOLDS, 60);
            put(STEffectBeautyType.EFFECT_BEAUTY_PLASTIC_REMOVE_DARK_CIRCLES, 69);
            put(STEffectBeautyType.EFFECT_BEAUTY_PLASTIC_WHITE_TEETH, 20);
            put(STEffectBeautyType.EFFECT_BEAUTY_PLASTIC_EYE_ANGLE, 0);
            put(STEffectBeautyType.EFFECT_BEAUTY_PLASTIC_EYE_DISTANCE, -23);

            put(STEffectBeautyType.EFFECT_BEAUTY_PLASTIC_THIN_FACE, 30);
            put(STEffectBeautyType.EFFECT_BEAUTY_RESHAPE_ROUND_EYE, 7);
            put(STEffectBeautyType.EFFECT_BEAUTY_TONE_CLEAR, 20);//清晰度


        }
    };

    // 美妆相关
    private static ConcurrentHashMap<Integer, MakeUpEntity> mMakeUpParams = new ConcurrentHashMap<Integer, MakeUpEntity>() {
        {
            put(STEffectBeautyType.EFFECT_BEAUTY_MAKEUP_LIP, new MakeUpEntity("", 80));
            put(STEffectBeautyType.EFFECT_BEAUTY_MAKEUP_CHEEK, new MakeUpEntity("", 80));
            put(STEffectBeautyType.EFFECT_BEAUTY_MAKEUP_NOSE, new MakeUpEntity("", 80));
            put(STEffectBeautyType.EFFECT_BEAUTY_MAKEUP_EYE_BROW, new MakeUpEntity("", 80));
            put(STEffectBeautyType.EFFECT_BEAUTY_MAKEUP_EYE_SHADOW, new MakeUpEntity("", 80));
            put(STEffectBeautyType.EFFECT_BEAUTY_MAKEUP_EYE_LINE, new MakeUpEntity("", 80));
            put(STEffectBeautyType.EFFECT_BEAUTY_MAKEUP_EYE_LASH, new MakeUpEntity("", 80));
            put(STEffectBeautyType.EFFECT_BEAUTY_MAKEUP_ALL, new MakeUpEntity("", 85));
        }
    };

    private static String mFilterStyle;
    private static int mFilterStrength = 65;
    private static String mSticker;
    private static long mDetectConfig = ST_MOBILE_FACE_DETECT | ST_MOBILE_SEG_SKIN;
    private static int mFaceCount = 0;
    private static Rect mFaceInfo = new Rect();
    // 其他
    private static Senseme.ErrorCallback mErrorCallback;
    private static long mLastCallbackTime = 0;
    private static final long CallbackIntervalMs = 10 * 1000;
    private static Senseme.FaceInfoCallBack mFaceInfoCallBack;
    private static boolean checkLicenseOk = false;
    private static final STMobileEffectNative mSTMobileEffectNative = new STMobileEffectNative();  //v8.3.1 新美颜使用

    public static boolean stCheckLicense(Context context) {
        if (!checkLicenseOk) {
            checkLicenseOk = STLicenseUtils.checkLicense(context);
        }
        LogUtils.d(TAG, "stCheckLicense checkLicenseOk= " + checkLicenseOk);
        return checkLicenseOk;
    }

    /**
     * 人脸检测属性的配置
     */
    public static void setHumanActionDetectConfig() {
        mDetectConfig = ST_MOBILE_FACE_DETECT | ST_MOBILE_SEG_SKIN;
    }

    /**
     * 活体检测
     */
    public static void setHumanActionLivingConfig() {
        mDetectConfig = ST_MOBILE_EYE_BLINK | ST_MOBILE_MOUTH_AH | ST_MOBILE_HEAD_YAW | ST_MOBILE_HEAD_PITCH | ST_MOBILE_BROW_JUMP;
    }

    /**
     * 美妆 和 贴纸
     */
    public static void setHumanActionmStickerConfig() {
        mDetectConfig = mSTMobileEffectNative.getHumanActionDetectConfig();
    }

    /**
     * 设置是否需要人脸检测
     *
     * @param mCheckHumanAction
     */
    public static void setHumanActionCheak(boolean mCheckHumanAction) {
        mNeedCheckHumanAction = mCheckHumanAction;
    }

    // 设置美颜开关
    public static int stEnableBeautify(final boolean needBeautify) {
        LogUtils.v(TAG, "stEnableBeautify:");
        mNeedBeautify = needBeautify;
        LogUtils.v(TAG, "stEnableBeautify, enable=" + needBeautify);
        return 0;
    }

    /**
     * 设置美颜参数
     *
     * @param type  BaseBeautyParams | MicroBeautyParams
     * @param value 美颜参数 [-100,100]
     */
    public static int stSetBeautyParam(final int type, final int value) {
        LogUtils.v(TAG, "stSetBeautyParam:");
        if (mBeautifyParams.containsKey(type)) {
            // 暂存参数
            int v = value;
            if (type == STEffectBeautyType.EFFECT_BEAUTY_PLASTIC_CHIN_LENGTH || type == STEffectBeautyType.EFFECT_BEAUTY_PLASTIC_NOSE_LENGTH
                    || type == STEffectBeautyType.EFFECT_BEAUTY_PLASTIC_EYE_DISTANCE || type == STEffectBeautyType.EFFECT_BEAUTY_PLASTIC_EYE_ANGLE
                    || type == STEffectBeautyType.EFFECT_BEAUTY_PLASTIC_HAIRLINE_HEIGHT || type == STEffectBeautyType.EFFECT_BEAUTY_PLASTIC_MOUTH_SIZE
                    || type == STEffectBeautyType.EFFECT_BEAUTY_PLASTIC_PHILTRUM_LENGTH) {
                v = (v > 100) ? 100 : v;
                v = (v < -100) ? -100 : v;
            } else {
                v = (v > 100) ? 100 : v;
                v = (v < 0) ? 0 : v;
            }
            mBeautifyParams.put(type, v);
            mBeautyChange = true;
            LogUtils.v(TAG, "stSetBeautyParam, type=" + type + " value=" + v + " result= ");
            return 0;
        } else {
            LogUtils.w(TAG, "stSetBeautyParam, error param[0].");
            if (mErrorCallback != null && System.currentTimeMillis() - mLastCallbackTime > CallbackIntervalMs) {
                if (mErrorCallback != null) {
                    mErrorCallback.onSTError(-1, "美颜参数错误:type=" + type);
                }
                mLastCallbackTime = System.currentTimeMillis();
            }
            return -1;
        }
    }

    /**
     * 设置美妆参数
     *
     * @param type
     * @param entity 参数对象
     */
    public static int stSetMakeUpBeautyParam(final int type, MakeUpEntity entity) {
        if (isOpenMakeUp()) {
            LogUtils.v(TAG, "stSetMakeUpBeautyParam:");
            if (mMakeUpParams.containsKey(type)) {
                mMakeUpParams.put(type, entity);
                mMakeUpChange = true;
                return 0;
            } else {
                LogUtils.w(TAG, "stSetMakeUpBeautyParam, error param[0].");
                if (mErrorCallback != null && System.currentTimeMillis() - mLastCallbackTime > CallbackIntervalMs) {
                    if (mErrorCallback != null) {
                        mErrorCallback.onSTError(-1, "美颜参数错误:type=" + type);
                    }
                    mLastCallbackTime = System.currentTimeMillis();
                }
                return -1;
            }
        }
        return -1;
    }

    // 设置滤镜开关
    public static int stEnableFilter(final boolean needFilter) {
        LogUtils.v(TAG, "stEnableFilter:");
        mNeedFilter = needFilter;
        LogUtils.v(TAG, "stEnableFilter, enable=" + needFilter);
        return 0;
    }

    // 设置滤镜风格
    public static int stSetFilterStyle(final String modelPath) {
        if (mFilterStyle == null || !mFilterStyle.equals(modelPath)) {
            mFilterStyle = modelPath;
            mFilterChange = true;
        }
        LogUtils.v(TAG, "stSetFilterStyle, filter=" + modelPath);
        return 0;
    }

    // 设置滤镜参数
    public static int stSetFilterStrength(int value) {
        LogUtils.v(TAG, "stSetFilterStrength:");
        value = (value > 100) ? 100 : value;
        value = (value < 0) ? 0 : value;
        if (mFilterStrength != value) {
            mFilterStrength = value;
            mFilterChange = true;
        }
        LogUtils.v(TAG, "stSetFilterStrength, value=" + value);
        return 0;
    }

    // 设置贴纸开关，初始化贴纸之前调用无效
    public static int stEnableSticker(final boolean needSticker) {
        LogUtils.v(TAG, "stEnableSticker:");
        mNeedSticker = needSticker;
        LogUtils.v(TAG, "stEnableSticker, enable=" + needSticker);
        return 0;
    }

    // 设置美妆开关，初始化美妆之前调用无效
    public static int stEnableMakeUp(final boolean needMakeUp) {
        LogUtils.v(TAG, "stEnableMakeUp:");
        if (isOpenMakeUp()) {
            mNeedMakeUp = needMakeUp;
        } else {
            mNeedMakeUp = false;
        }
        LogUtils.v(TAG, "stEnableMakeUp, enable=" + mNeedMakeUp);
        return 0;
    }

    // 添加贴纸效果，自动移除上一个贴纸
    public static int stAddSticker(final String sticker) {
        LogUtils.v(TAG, "stAddSticker:");
        if (mSticker == null) {
            mSticker = sticker;
            mStickerChange = true;
            LogUtils.v(TAG, "stAddSticker, sticker=" + sticker);
        } else {
            if (!mSticker.equals(sticker)) {
                mSticker = sticker;
                mStickerChange = true;
                LogUtils.v(TAG, "stAddSticker, sticker=" + sticker);
            }
        }
        return 0;
    }

    // 移除贴纸效果
    public static int stRemoveSticker() {
        LogUtils.v(TAG, "stRemoveSticker:");
        if (mSticker != null) {
            LogUtils.v(TAG, "stRemoveSticker, sticker=" + mSticker);
            mSticker = null;
            mStickerChange = true;
        }
        return 0;
    }

    public static int stRemoveSticker(final String sticker) {
        LogUtils.v(TAG, "stRemoveSticker:");
        if (mSticker != null && mSticker.equals(sticker)) {
            LogUtils.v(TAG, "stRemoveSticker, sticker=" + mSticker);
            mSticker = null;
            mStickerChange = true;
        }
        return 0;
    }

    /**
     * 设置基础美颜参数集
     */
    public static void stSetBaseBeautyParams(int smooth, int whiten, int redden, int enlargeEye, int shrinkFace, int narrowFace) {
        LogUtils.v(TAG, "stSetBaseBeautyParams:");
        stSetBeautyParam(STEffectBeautyType.EFFECT_BEAUTY_BASE_FACE_SMOOTH, smooth);
        stSetBeautyParam(STEffectBeautyType.EFFECT_BEAUTY_BASE_WHITTEN, whiten);
        stSetBeautyParam(STEffectBeautyType.EFFECT_BEAUTY_BASE_REDDEN, redden);
        stSetBeautyParam(STEffectBeautyType.EFFECT_BEAUTY_RESHAPE_ENLARGE_EYE, enlargeEye);
        stSetBeautyParam(STEffectBeautyType.EFFECT_BEAUTY_RESHAPE_SHRINK_FACE, shrinkFace);
        stSetBeautyParam(STEffectBeautyType.EFFECT_BEAUTY_RESHAPE_NARROW_FACE, narrowFace);
    }

    /**
     * 设置微整形参数集
     */
    public static void stSetMicroBeautyParams(int chinLength, int shrinkJaw, int removeNasolabialFolds,
                                              int narrowNose, int noseLength, int profileRhinoplasty,
                                              int removeDarkCircles, int brightEye, int openCanthus,
                                              int eyeDistance, int eyeAngle, int hairlineHeight,
                                              int appleMusle, int mouthSize, int whiteTeeth,
                                              int philtrumLength, int RoundEye) {
        LogUtils.v(TAG, "stSetMicroBeautyParams:");
        stSetBeautyParam(STEffectBeautyType.EFFECT_BEAUTY_PLASTIC_CHIN_LENGTH, chinLength);
        stSetBeautyParam(STEffectBeautyType.EFFECT_BEAUTY_RESHAPE_SHRINK_JAW, shrinkJaw);
        stSetBeautyParam(STEffectBeautyType.EFFECT_BEAUTY_PLASTIC_REMOVE_NASOLABIAL_FOLDS, removeNasolabialFolds);
        stSetBeautyParam(STEffectBeautyType.EFFECT_BEAUTY_PLASTIC_NARROW_NOSE, narrowNose);
        stSetBeautyParam(STEffectBeautyType.EFFECT_BEAUTY_PLASTIC_NOSE_LENGTH, noseLength);
        stSetBeautyParam(STEffectBeautyType.EFFECT_BEAUTY_PLASTIC_PROFILE_RHINOPLASTY, profileRhinoplasty);
        stSetBeautyParam(STEffectBeautyType.EFFECT_BEAUTY_PLASTIC_REMOVE_DARK_CIRCLES, removeDarkCircles);
        stSetBeautyParam(STEffectBeautyType.EFFECT_BEAUTY_PLASTIC_BRIGHT_EYE, brightEye);
        stSetBeautyParam(STEffectBeautyType.EFFECT_BEAUTY_PLASTIC_OPEN_CANTHUS, openCanthus);
        stSetBeautyParam(STEffectBeautyType.EFFECT_BEAUTY_PLASTIC_EYE_DISTANCE, eyeDistance);
        stSetBeautyParam(STEffectBeautyType.EFFECT_BEAUTY_PLASTIC_EYE_ANGLE, eyeAngle);
        stSetBeautyParam(STEffectBeautyType.EFFECT_BEAUTY_PLASTIC_HAIRLINE_HEIGHT, hairlineHeight);
        stSetBeautyParam(STEffectBeautyType.EFFECT_BEAUTY_PLASTIC_APPLE_MUSLE, appleMusle);
        stSetBeautyParam(STEffectBeautyType.EFFECT_BEAUTY_PLASTIC_MOUTH_SIZE, mouthSize);
        stSetBeautyParam(STEffectBeautyType.EFFECT_BEAUTY_PLASTIC_WHITE_TEETH, whiteTeeth);
        stSetBeautyParam(STEffectBeautyType.EFFECT_BEAUTY_PLASTIC_PHILTRUM_LENGTH, philtrumLength);
        stSetBeautyParam(STEffectBeautyType.EFFECT_BEAUTY_RESHAPE_ROUND_EYE, RoundEye);
    }

    /**
     * 设置口红参数集
     */
    public static void stSetLipstickBeautyParams(MakeUpEntity entity) {
        LogUtils.v(TAG, "stSetLipstickBeautyParams:");
        stSetMakeUpBeautyParam(STEffectBeautyType.EFFECT_BEAUTY_MAKEUP_LIP, entity);
    }

    /**
     * 设置腮红参数集
     */
    public static void stSetBlushBeautyParams(MakeUpEntity entity) {
        LogUtils.v(TAG, "stSetBlushBeautyParams:");
        stSetMakeUpBeautyParam(STEffectBeautyType.EFFECT_BEAUTY_MAKEUP_CHEEK, entity);
    }

    /**
     * 设置修容参数集
     */
    public static void stSetContourBeautyParams(MakeUpEntity entity) {
        LogUtils.v(TAG, "stSetContourBeautyParams:");
        stSetMakeUpBeautyParam(STEffectBeautyType.EFFECT_BEAUTY_MAKEUP_NOSE, entity);
    }

    /**
     * 设置眉毛参数集
     */
    public static void stSetEyebrowBeautyParams(MakeUpEntity entity) {
        LogUtils.v(TAG, "stSetEyebrowBeautyParams:");
        stSetMakeUpBeautyParam(STEffectBeautyType.EFFECT_BEAUTY_MAKEUP_EYE_BROW, entity);
    }

    /**
     * 设置眼影参数集
     */
    public static void stSetEyeshadowBeautyParams(MakeUpEntity entity) {
        LogUtils.v(TAG, "stSetEyeshadowBeautyParams:");
        stSetMakeUpBeautyParam(STEffectBeautyType.EFFECT_BEAUTY_MAKEUP_EYE_SHADOW, entity);
    }

    /**
     * 设置眼线参数集
     */
    public static void stSetEyelinerBeautyParams(MakeUpEntity entity) {
        LogUtils.v(TAG, "stSetEyelinerBeautyParams:");
        stSetMakeUpBeautyParam(STEffectBeautyType.EFFECT_BEAUTY_MAKEUP_EYE_LINE, entity);
    }

    /**
     * 设置眼睫毛参数集
     */
    public static void stSetEyslashBeautyParams(MakeUpEntity entity) {
        LogUtils.v(TAG, "stSetEyslasheautyParams:");
        stSetMakeUpBeautyParam(STEffectBeautyType.EFFECT_BEAUTY_MAKEUP_EYE_LASH, entity);
    }

    /**
     * 设置美瞳参数集
     */
    public static void stSetStyleBeautyParams(MakeUpEntity entity) {
        LogUtils.v(TAG, "stSetStyleBeautyParams:");
        stSetMakeUpBeautyParam(STEffectBeautyType.EFFECT_BEAUTY_MAKEUP_ALL, entity);
    }

    /**
     * 是否开启美妆
     *
     * @return
     */
    public static boolean isOpenMakeUp() {
        SharedPreferences sp = BaseApplication.getInstance().getApplicationContext().getSharedPreferences("spUtils", Context.MODE_PRIVATE);
        return sp.getBoolean(KEY_MAKE_UP_PROFILE, false);
    }

    public static int stGetFaceCount() {
        return mFaceCount;
    }

    // 获取人脸属性信息 Rect
    public static Rect stGetFaceInfo() {
        return mFaceInfo;
    }

    // 注册/注销美颜错误回调
    public static void stSetErrorListener(Senseme.ErrorCallback call) {
        mErrorCallback = call;
    }

    // 注册/注销人脸信息回调
    public static void stSetFaceInfoListener(Senseme.FaceInfoCallBack call) {
        mFaceInfoCallBack = call;
    }

    private void initHumanAction() {
        int result = mSTHumanActionNative.createInstanceFromAssetFile(FileUtils.getActionModelName(),
                STCommonNative.ST_MOBILE_DETECT_MODE_VIDEO, mContext.getAssets());
        mSTHumanActionNative.setFaceActionThreshold(ST_MOBILE_EYE_BLINK, 0.2f);
        mSTHumanActionNative.addSubModelFromAssetFile(FileUtils.MODEL_SEGMENT_SKIN, mContext.getAssets());
        if (isOpenMakeUp()) {
            Senseme.stEnableMakeUp(true);
            mSTHumanActionNative.addSubModelFromAssetFile(FileUtils.MODEL_NAME_LIPS_PARSING, mContext.getAssets());
            mSTHumanActionNative.addSubModelFromAssetFile(FileUtils.HEAD_SEGMENT_DBL, mContext.getAssets());
            mSTHumanActionNative.addSubModelFromAssetFile(FileUtils.MODEL_NAME_FACE_EXTRA, mContext.getAssets());
        }
        LogUtils.i(TAG, "initHumanActionWithoutGL, createInstance result=" + result);
    }

    private void initBeauty() {
        int result = mSTMobileEffectNative.createInstance(mContext, STMobileEffectNative.EFFECT_CONFIG_NONE);
        LogUtils.i(TAG, "initBeautyWithoutGL, mSTMobileEffectNativeWithoutGL createInstance result=" + result);
        mSTMobileEffectNative.setBeautyParam(STEffectBeautyParams.ENABLE_WHITEN_SKIN_MASK, 1);
        //设置美⽩模式, 0为ST_BEAUTIFY_WHITEN_STRENGTH, 1为ST_BEAUTIFY_WHITEN2_STRENGTH, 2为 ST_BEAUTIFY_WHITEN3_STRENGTH
        int whiteMode = mSTMobileEffectNative.setBeautyMode(STEffectBeautyType.EFFECT_BEAUTY_BASE_WHITTEN, STEffectBeautyType.WHITENING3_MODE);
        //设置磨⽪模式, 默认值2.0, 1表示对全图磨⽪, 2表示精细化磨⽪
        int smoothMode = mSTMobileEffectNative.setBeautyMode(STEffectBeautyType.EFFECT_BEAUTY_BASE_FACE_SMOOTH, STEffectBeautyType.SMOOTH2_MODE);
        LogUtils.i(TAG, "initBeautyWithoutGL, whiteMode=" + whiteMode + " smoothMode= " + smoothMode);

        if (result == 0) {
            for (Map.Entry<Integer, Integer> entry : mBeautifyParams.entrySet()) {
                mSTMobileEffectNative.setBeautyStrength(entry.getKey(), (float) entry.getValue() / 100);
            }
            //设置滤镜
            if (mFilterStyle != null) {
                int ret = mSTMobileEffectNative.setBeauty(STEffectBeautyType.EFFECT_BEAUTY_FILTER, mFilterStyle);
                int ret1 = mSTMobileEffectNative.setBeautyStrength(STEffectBeautyType.EFFECT_BEAUTY_FILTER, (float) mFilterStrength / 100);
            }

            if (isOpenMakeUp()) {
                for (Map.Entry<Integer, MakeUpEntity> entry : mMakeUpParams.entrySet()) {
                    MakeUpEntity entity = entry.getValue();
                    if (entity.isAssets()) {
                        mSTMobileEffectNative.setBeautyFromAssetsFile(entry.getKey(), entity.path, mContext.getAssets());
                    } else {
                        String path = mContext.getExternalFilesDir(null) + File.separator + entity.path;
                        mSTMobileEffectNative.setBeauty(entry.getKey(), path);
                    }
                    mSTMobileEffectNative.setBeautyStrength(entry.getKey(), (float) entity.progress / 100);
                }
            }
            //设置贴纸
            mSTMobileEffectNative.addPackage(null);
        }
    }

    @Override
    public void onStart(ZegoPublishChannel channel) {
        initHumanAction();
        initBeauty();
    }

    @Override
    public void onStop(ZegoPublishChannel channel) {
        destroyTexture();
        destroyReadPixelsResource();

        mSTHumanActionNative.destroyInstance();
        mSTMobileEffectNative.destroyInstance();

        mInTextureWidth = 0;
        mInTextureHeight = 0;
    }

    private void destroyTexture() {
        if (mBeautifyOutputTextureId != null) {
            GLES20.glDeleteTextures(mBeautifyOutputTextureId.length, mBeautifyOutputTextureId, 0);
            mBeautifyOutputTextureId = null;
        }
    }

    private void destroyReadPixelsResource() {
        if (mReadPixelsFrameBuffer != null) {
            GLES20.glDeleteFramebuffers(mReadPixelsFrameBuffer.length, mReadPixelsFrameBuffer, 0);
            mReadPixelsFrameBuffer = null;
        }
        mReadPixelsByteBuffer = null;
        mRgbaByteArray = null;
    }

    @Override
    public void onCapturedUnprocessedTextureData(int inTexture, int width, int height, long referenceTimeMillisecond, ZegoPublishChannel channel) {
        boolean isResolutionChange = mInTextureWidth != width || mInTextureHeight != height;
        if (isResolutionChange) {
            destroyTexture();
            destroyReadPixelsResource();
        }
        if (mBeautifyOutputTextureId == null) {
            mBeautifyOutputTextureId = new int[1];
            GlUtil.initEffectTexture(width, height, inTexture, GLES20.GL_TEXTURE_2D);
            mInTextureWidth = width;
            mInTextureHeight = height;
        }

        if (mNeedBeautify || mNeedSticker || mNeedFilter || mNeedMakeUp) {
            if (mNeedFilter) {
                if (mFilterChange) {
                    mFilterChange = false;
                    mSTMobileEffectNative.setBeauty(STEffectBeautyType.EFFECT_BEAUTY_FILTER, mFilterStyle);
                    mSTMobileEffectNative.setBeautyStrength(STEffectBeautyType.EFFECT_BEAUTY_FILTER, (float) mFilterStrength / 100);
                }
            }

            if (mNeedBeautify || mNeedSticker || mNeedMakeUp) {
                if (mStickerChange) {
                    mStickerChange = false;
                    if (mStickerId != 0) {
                        mSTMobileEffectNative.removeEffect(mStickerId);
                        mStickerId = 0;
                    }
                    if (mSticker != null) {
                        mStickerId = mSTMobileEffectNative.addPackage(mSticker);
                    }
                }
                STHumanAction humanAction = null;
                if (mNeedCheckHumanAction) {
                    mSTHumanActionNative.nativeHumanActionDetectPtr(readBytesFromTexture(inTexture, width, height), STCommonNative.ST_PIX_FMT_RGBA8888,
                            mDetectConfig, 180 / 90, width, height);
                    humanAction = mSTHumanActionNative.getNativeHumanAction();

                    if (humanAction != null && humanAction.getFaceCount() > 0) {
                        mFaceCount = humanAction.getFaceCount();
                        mFaceInfo = humanAction.getFaces()[0].getFace106().getRect().getRect();
                    } else {
                        mFaceCount = 0;
                    }
                    if (mFaceInfoCallBack != null && null != humanAction) {
                        STHumanAction finalHumanAction = humanAction;
                        (new Thread(new Runnable() {
                            public void run() {
                                if (Senseme.mFaceInfoCallBack != null) {
                                    Senseme.mFaceInfoCallBack.onSTFaceInfo(finalHumanAction, width, height, null);
                                }

                            }
                        })).start();
                    }
                }

                if (mNeedMakeUp) {
                    if (mMakeUpChange) {
                        mMakeUpChange = false;
                        for (Map.Entry<Integer, MakeUpEntity> entry : mMakeUpParams.entrySet()) {
                            MakeUpEntity entity = entry.getValue();
                            if (entity.isAssets()) {
                                mSTMobileEffectNative.setBeautyFromAssetsFile(entry.getKey(), entity.path, mContext.getAssets());
                            } else {
                                String path = mContext.getExternalFilesDir(null) + File.separator + entity.path;
                                mSTMobileEffectNative.setBeauty(entry.getKey(), path);
                            }
                            mSTMobileEffectNative.setBeautyStrength(entry.getKey(), (float) entity.progress / 100);
                        }
                    }
                }

                if (mNeedBeautify) {
                    if (mBeautyChange) {
                        mBeautyChange = false;
                        for (Map.Entry<Integer, Integer> entry : mBeautifyParams.entrySet()) {
                            mSTMobileEffectNative.setBeautyStrength(entry.getKey(), (float) entry.getValue() / 100);
                        }
                    }


                    STEffectTexture stEffectTexture = new STEffectTexture(inTexture, width, height, 0);
                    STEffectTexture stEffectTextureOut = new STEffectTexture(mBeautifyOutputTextureId[0], width, height, 0);

                    STEffectRenderInParam sTEffectRenderInParam =
                            new STEffectRenderInParam(mSTHumanActionNative.getNativeHumanActionResultPtr(), null, 3,
                                    STRotateType.ST_CLOCKWISE_ROTATE_270, false, null, stEffectTexture, null);
                    STEffectRenderOutParam stEffectRenderOutParam = new STEffectRenderOutParam(stEffectTextureOut, null, mHumanAction);

                    result = mSTMobileEffectNative.render(sTEffectRenderInParam, stEffectRenderOutParam, false);

                    int outTexture = inTexture;
                    if (result == 0) {
                        outTexture = mBeautifyOutputTextureId[0];
                    }
                    ZegoExpressEngine.getEngine().sendCustomVideoProcessedTextureData(outTexture, width, height, referenceTimeMillisecond, channel);
                }
            }
        }
    }

    private byte[] readBytesFromTexture(int texture, int width, int height) {
        if (mReadPixelsFrameBuffer == null) {
            mReadPixelsFrameBuffer = new int[1];
            GLES20.glGenFramebuffers(1, mReadPixelsFrameBuffer, 0);
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, texture, 0);
        }
        if (mRgbaByteArray == null) {
            mRgbaByteArray = new byte[width * height * 4];
        }
        if (mReadPixelsByteBuffer == null) {
            mReadPixelsByteBuffer = ByteBuffer.allocateDirect(width * height * 4);
        }

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mReadPixelsFrameBuffer[0]);
        GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, mReadPixelsByteBuffer);

        mReadPixelsByteBuffer.position(0);
        mReadPixelsByteBuffer.get(mRgbaByteArray);

        return mRgbaByteArray;
    }

    /**
     * 人脸检测接口回调
     */
    public interface FaceInfoCallBack {
        void onSTFaceInfo(STHumanAction humanAction, int picWidth, int pinHeight, Rect[] var2);
    }

    public interface ErrorCallback {
        void onSTError(int error, String msg);
    }
}
