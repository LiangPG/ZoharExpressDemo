package com.zego.expressDemo.filter;

import static com.sensetime.stmobile.STMobileHumanActionNative.ST_MOBILE_BROW_JUMP;
import static com.sensetime.stmobile.STMobileHumanActionNative.ST_MOBILE_EYE_BLINK;
import static com.sensetime.stmobile.STMobileHumanActionNative.ST_MOBILE_FACE_DETECT;
import static com.sensetime.stmobile.STMobileHumanActionNative.ST_MOBILE_HEAD_PITCH;
import static com.sensetime.stmobile.STMobileHumanActionNative.ST_MOBILE_HEAD_YAW;
import static com.sensetime.stmobile.STMobileHumanActionNative.ST_MOBILE_MOUTH_AH;
import static com.sensetime.stmobile.STMobileHumanActionNative.ST_MOBILE_SEG_SKIN;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.opengl.GLES20;
import android.os.Environment;

import com.oversea.commonmodule.base.BaseApplication;
import com.sensetime.effects.MakeUpEntity;
import com.sensetime.effects.utils.FileUtils;
import com.sensetime.effects.utils.GlUtil;
import com.sensetime.effects.utils.LogUtils;
import com.sensetime.effects.utils.STLicenseUtils;
import com.sensetime.stmobile.STCommonNative;
import com.sensetime.stmobile.STMobileEffectNative;
import com.sensetime.stmobile.STMobileHumanActionNative;
import com.sensetime.stmobile.model.STEffectRenderInParam;
import com.sensetime.stmobile.model.STEffectRenderOutParam;
import com.sensetime.stmobile.model.STEffectTexture;
import com.sensetime.stmobile.model.STHumanAction;
import com.sensetime.stmobile.params.STEffectBeautyParams;
import com.sensetime.stmobile.params.STEffectBeautyType;
import com.sensetime.stmobile.params.STRotateType;
import com.zego.expressDemo.utils.LogUtils;
import com.zego.zegoavkit2.screencapture.ve_gl.GlRectDrawer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import im.zego.zegoexpress.ZegoExpressEngine;
import im.zego.zegoexpress.callback.IZegoCustomVideoProcessHandler;
import im.zego.zegoexpress.constants.ZegoPublishChannel;
import im.zego.zegoexpress.entity.ZegoVideoFrameParam;

/**
 * 商汤美颜接口封装
 * 2019-07-14 阿宝
 */
@TargetApi(17)
public class Sense extends IZegoCustomVideoProcessHandler {
    private static final String TAG = "Senseme";
    private static Context mContext;

    private final static float[] IDENTITY_MATRIX = new float[]{1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f};

    // 状态信息
    private static boolean mInitHumanActionOkWithoutGL = false;
    private static boolean mInitBeautyOkWithoutGL = false;
    private int[] mFrameBuffers;
    private int[] mFrameBufferTextures;
    private GlRectDrawer mGlDrawer;
    private ByteBuffer mReadPixelsByteBuffer;
    private byte[] mRgbaByteArray;

    private static int[] mVideoTextureIdWithoutGL;
    private static int[] mBeautifyTextureIdWithoutGL;
    private static int mTextureWidthWithoutGL = 960;
    private static int mTextureHeightWithoutGL = 540;
    // 美颜相关
    private static final Object mHumanActionHandleLockWithoutGL = new Object();
    private static final Object mBeautyLockWithoutGL = new Object();
    private static final STMobileHumanActionNative mSTHumanActionNativeWithoutGL = new STMobileHumanActionNative();
    private static final STHumanAction mHumanActionBeautyOutputWithoutGL = new STHumanAction();
    private static boolean mBeautyChangeWithoutGL = false;
    private static boolean mStickerChangeWithoutGL = false;
    private static boolean mFilterChangeWithoutGL = false;
    private static boolean mMakeUpChangeWithoutGL = false;
    private static int mStickerIdWithoutGL;
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
    private static final String KEY_MAKE_UP_IS_INITIALIZE = "key_make_up_is_initialize";
    // 美颜相关
    private static final ConcurrentHashMap<Integer, Integer> mBeautifyParams = new ConcurrentHashMap<Integer, Integer>() {
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
    private static final ConcurrentHashMap<Integer, MakeUpEntity> mMakeUpParams = new ConcurrentHashMap<Integer, MakeUpEntity>() {
        {
            put(STEffectBeautyType.EFFECT_BEAUTY_MAKEUP_LIP, new MakeUpEntity("",80));
            put(STEffectBeautyType.EFFECT_BEAUTY_MAKEUP_CHEEK, new MakeUpEntity("",80));
            put(STEffectBeautyType.EFFECT_BEAUTY_MAKEUP_NOSE, new MakeUpEntity("",80));
            put(STEffectBeautyType.EFFECT_BEAUTY_MAKEUP_EYE_BROW, new MakeUpEntity("",80));
            put(STEffectBeautyType.EFFECT_BEAUTY_MAKEUP_EYE_SHADOW, new MakeUpEntity("",80));
            put(STEffectBeautyType.EFFECT_BEAUTY_MAKEUP_EYE_LINE, new MakeUpEntity("",80));
            put(STEffectBeautyType.EFFECT_BEAUTY_MAKEUP_EYE_LASH, new MakeUpEntity("",80));
            put(STEffectBeautyType.EFFECT_BEAUTY_MAKEUP_ALL, new MakeUpEntity("",85));
        }
    };
    private static String mFilterStyle;
    private static int mFilterStrength = 65;
    private static String mSticker;
    private static long mDetectConfig = ST_MOBILE_FACE_DETECT|ST_MOBILE_SEG_SKIN;
    private static int mFaceCount = 0;

    private static ErrorCallback mErrorCallback;
    private static long mLastCallbackTime = 0;
    private static final long CallbackIntervalMs = 10 * 1000;
    private static FaceInfoCallBack mFaceInfoCallBack;
    private static boolean checkLicenseOk = false;
    private static final STMobileEffectNative mSTMobileEffectNativeWithoutGL = new STMobileEffectNative();

    private static class Singleton {
        private static final Sense INSTANCE = new Sense(BaseApplication.getInstance().getApplicationContext());
    }

    public static Sense getInstance() {
        return Sense.Singleton.INSTANCE;
    }

    private Sense(Context context){
        mContext = context;
    }

    public static boolean stCheckLicense(Context mContext) {
        if (!checkLicenseOk) {
            checkLicenseOk = STLicenseUtils.checkLicense(mContext);
        }
        LogUtils.d(TAG, "stCheckLicense checkLicenseOk= " + checkLicenseOk);
        return checkLicenseOk;
    }

    public static void init() {
        LogUtils.d(TAG, "createEGLContextWithoutGL:");
        setInitializeState(false);
    }

    // 创建商汤SDK实例，调用方提供OpenGL环境
    private static void initSenseTimeWithoutGL() {
        initHumanActionWithoutGL();
        initBeautyWithoutGL();
    }

    // 释放商汤SDK实例，调用方提供OpenGL环境
    private static void destroySenseTimeWithoutGL() {
        LogUtils.v(TAG, "destroySenseTimeWithoutGL:");
        mSTHumanActionNativeWithoutGL.destroyInstance();
        mInitHumanActionOkWithoutGL = false;
        mSTMobileEffectNativeWithoutGL.destroyInstance();
        mInitBeautyOkWithoutGL = false;
    }

    // 创建各种纹理
    private static void initTextureWithoutGL() {

        if (mVideoTextureIdWithoutGL == null) {
            mVideoTextureIdWithoutGL = new int[1];
            GlUtil.initEffectTexture(mTextureWidthWithoutGL, mTextureHeightWithoutGL, mVideoTextureIdWithoutGL, GLES20.GL_TEXTURE_2D);
            LogUtils.d(TAG, "initTextureWithoutGL, mVideoTextureIdWithoutGL=" + mVideoTextureIdWithoutGL[0]);
        }
        if (mBeautifyTextureIdWithoutGL == null) {
            mBeautifyTextureIdWithoutGL = new int[1];
            GlUtil.initEffectTexture(mTextureWidthWithoutGL, mTextureHeightWithoutGL, mBeautifyTextureIdWithoutGL, GLES20.GL_TEXTURE_2D);
            LogUtils.d(TAG, "initTextureWithoutGL, mBeautifyTextureIdWithoutGL=" + mBeautifyTextureIdWithoutGL[0]);
        }
    }

    // 释放各种纹理
    private static void destroyTextureWithoutGL() {
        LogUtils.v(TAG, "destroyTextureWithoutGL:");

        if (mVideoTextureIdWithoutGL != null) {
            LogUtils.d(TAG, "destroyTextureWithoutGL, mVideoTextureIdWithoutGL=" + mVideoTextureIdWithoutGL[0]);
            GLES20.glDeleteTextures(mVideoTextureIdWithoutGL.length, mVideoTextureIdWithoutGL, 0);
            mVideoTextureIdWithoutGL = null;
        }
        if (mBeautifyTextureIdWithoutGL != null) {
            LogUtils.d(TAG, "destroyTextureWithoutGL, mBeautifyTextureIdWithoutGL=" + mBeautifyTextureIdWithoutGL[0]);
            GLES20.glDeleteTextures(mBeautifyTextureIdWithoutGL.length, mBeautifyTextureIdWithoutGL, 0);
            mBeautifyTextureIdWithoutGL = null;
        }
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

    public static int stGetFaceCount() {
        return mFaceCount;
    }

    // 创建人脸检测实例，调用方提供OpenGL环境
    private static void initHumanActionWithoutGL() {
        synchronized (mHumanActionHandleLockWithoutGL) {
            if (!mInitHumanActionOkWithoutGL) {
                if (mContext != null) {
                    int result = mSTHumanActionNativeWithoutGL.createInstanceFromAssetFile(FileUtils.getActionModelName(),
                            STCommonNative.ST_MOBILE_DETECT_MODE_VIDEO, mContext.getAssets());
                    mSTHumanActionNativeWithoutGL.setFaceActionThreshold(ST_MOBILE_EYE_BLINK,0.2f);
                    mSTHumanActionNativeWithoutGL.addSubModelFromAssetFile(FileUtils.MODEL_SEGMENT_SKIN, mContext.getAssets());
                    if (isOpenMakeUp()){
                        setInitializeState(true);
                        Sense.stEnableMakeUp(true);
                        mSTHumanActionNativeWithoutGL.addSubModelFromAssetFile(FileUtils.MODEL_NAME_LIPS_PARSING, mContext.getAssets());
                        mSTHumanActionNativeWithoutGL.addSubModelFromAssetFile(FileUtils.HEAD_SEGMENT_DBL, mContext.getAssets());
                        mSTHumanActionNativeWithoutGL.addSubModelFromAssetFile(FileUtils.MODEL_NAME_FACE_EXTRA, mContext.getAssets());
                    }
                    LogUtils.i(TAG, "initHumanActionWithoutGL, createInstance result=" + result);

                    mInitHumanActionOkWithoutGL = result == 0;
                } else {
                    LogUtils.w(TAG, "please call stCreateEGLContext first.");
                }
            }
        }
    }

    // 创建美颜实例，调用方提供OpenGL环境
    private static void initBeautyWithoutGL() {
        synchronized (mBeautyLockWithoutGL) {
            if (!mInitBeautyOkWithoutGL) {
                int result = mSTMobileEffectNativeWithoutGL.createInstance(mContext, STMobileEffectNative.EFFECT_CONFIG_NONE);
                LogUtils.i(TAG, "initBeautyWithoutGL, mSTMobileEffectNativeWithoutGL createInstance result=" + result);
                mSTMobileEffectNativeWithoutGL.setBeautyParam(STEffectBeautyParams.ENABLE_WHITEN_SKIN_MASK, 1);
                //设置美⽩模式, 0为ST_BEAUTIFY_WHITEN_STRENGTH, 1为ST_BEAUTIFY_WHITEN2_STRENGTH, 2为 ST_BEAUTIFY_WHITEN3_STRENGTH
                int whiteMode = mSTMobileEffectNativeWithoutGL.setBeautyMode(STEffectBeautyType.EFFECT_BEAUTY_BASE_WHITTEN, STEffectBeautyType.WHITENING3_MODE);
                //设置磨⽪模式, 默认值2.0, 1表示对全图磨⽪, 2表示精细化磨⽪
                int smoothMode = mSTMobileEffectNativeWithoutGL.setBeautyMode(STEffectBeautyType.EFFECT_BEAUTY_BASE_FACE_SMOOTH, STEffectBeautyType.SMOOTH2_MODE);
                LogUtils.i(TAG, "initBeautyWithoutGL, whiteMode=" + whiteMode + " smoothMode= " + smoothMode);

                if (result == 0) {
                    for (Map.Entry<Integer, Integer> entry : mBeautifyParams.entrySet()) {
                        mSTMobileEffectNativeWithoutGL.setBeautyStrength(entry.getKey(), (float) entry.getValue() / 100);
                    }
                    //设置滤镜
                    if (mFilterStyle != null) {
                        int ret = mSTMobileEffectNativeWithoutGL.setBeauty(STEffectBeautyType.EFFECT_BEAUTY_FILTER, mFilterStyle);
                        int ret1 = mSTMobileEffectNativeWithoutGL.setBeautyStrength(STEffectBeautyType.EFFECT_BEAUTY_FILTER, (float) mFilterStrength / 100);
                    }

                    if (isOpenMakeUp() && getInitializeState()){
                        for (Map.Entry<Integer, MakeUpEntity> entry : mMakeUpParams.entrySet()){
                            MakeUpEntity entity = entry.getValue();
                            if (entity.isAssets()){
                                mSTMobileEffectNativeWithoutGL.setBeautyFromAssetsFile(entry.getKey(),entity.path,mContext.getAssets());
                            }else {
                                String path = mContext.getExternalFilesDir(null) + File.separator + entity.path;
                                mSTMobileEffectNativeWithoutGL.setBeauty(entry.getKey(),path);
                            }
                            mSTMobileEffectNativeWithoutGL.setBeautyStrength(entry.getKey(),(float) entity.progress / 100);
                        }
                    }
                    //设置贴纸
                    mSTMobileEffectNativeWithoutGL.addPackage(null);
                    mInitBeautyOkWithoutGL = true;
                }
            }
        }
    }

    /**
     * 人脸检测属性的配置
     *
     */
    public static void setHumanActionDetectConfig() {
        mDetectConfig = ST_MOBILE_FACE_DETECT|ST_MOBILE_SEG_SKIN;
    }

    /**
     * 活体检测
     *
     */
    public static void setHumanActionLivingConfig() {
        mDetectConfig = ST_MOBILE_EYE_BLINK|ST_MOBILE_MOUTH_AH|ST_MOBILE_HEAD_YAW|ST_MOBILE_HEAD_PITCH|ST_MOBILE_BROW_JUMP;
    }

    /**
     * 美妆 和 贴纸
     */
    public static void setHumanActionmStickerConfig(){
            if (null != mSTMobileEffectNativeWithoutGL){
            mDetectConfig = mSTMobileEffectNativeWithoutGL.getHumanActionDetectConfig();
        }
    }

    /**
     * 设置是否需要人脸检测
     * @param mCheckHumanAction
     */
    public static void setHumanActionCheck(boolean mCheckHumanAction){
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
            mBeautyChangeWithoutGL = true;
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
     * @param type
     * @param entity 参数对象
     */
    public static int stSetMakeUpBeautyParam(final int type,MakeUpEntity entity) {
        if (isOpenMakeUp() && getInitializeState()){
            LogUtils.v(TAG, "stSetMakeUpBeautyParam:");
            if (mMakeUpParams.containsKey(type)) {
                mMakeUpParams.put(type, entity);
                mMakeUpChangeWithoutGL = true;
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
            mFilterChangeWithoutGL = true;
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
            mFilterChangeWithoutGL = true;
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
        if (isOpenMakeUp() && getInitializeState()){
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
            mStickerChangeWithoutGL = true;
            LogUtils.v(TAG, "stAddSticker, sticker=" + sticker);
        } else {
            if (!mSticker.equals(sticker)) {
                mSticker = sticker;
                mStickerChangeWithoutGL = true;
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
            mStickerChangeWithoutGL = true;
        }
        return 0;
    }

    public static int stRemoveSticker(final String sticker) {
        LogUtils.v(TAG, "stRemoveSticker:");
        if (mSticker != null && mSticker.equals(sticker)) {
            LogUtils.v(TAG, "stRemoveSticker, sticker=" + mSticker);
            mSticker = null;
            mStickerChangeWithoutGL = true;
        }
        return 0;
    }

    @Override
    public void onStop(ZegoPublishChannel channel) {
        destroySenseTimeWithoutGL();

        destroyTextureWithoutGL();
        destroyReadPixelsResource();

        mTextureWidthWithoutGL = 0;
        mTextureHeightWithoutGL = 0;

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
        LogUtils.e(TAG, "--><:: " + message + ", consume: " + (System.currentTimeMillis() - mLastTime));
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
    public void onCapturedUnprocessedTextureData(int inTexture, int width, int height, long referenceTimeMillisecond, ZegoPublishChannel channel) {
        printConsume("onCapturedUnprocessedTextureData start");
        boolean isResolutionChange = mTextureWidthWithoutGL != width || mTextureHeightWithoutGL != height;
        if (isResolutionChange) {
            mTextureWidthWithoutGL = width;
            mTextureHeightWithoutGL = height;
            destroyTextureWithoutGL();
            destroyReadPixelsResource();

            printConsume("destroyReadPixelsResource end");
        }
        initTextureWithoutGL();
        initSenseTimeWithoutGL();

        printConsume("initSenseTimeWithoutGL end");

        if (mNeedBeautify || mNeedSticker || mNeedFilter || mNeedMakeUp) {
            if (mNeedFilter) {
                if (mFilterChangeWithoutGL) {
                    mFilterChangeWithoutGL = false;
                    mSTMobileEffectNativeWithoutGL.setBeauty(STEffectBeautyType.EFFECT_BEAUTY_FILTER, mFilterStyle);
                    mSTMobileEffectNativeWithoutGL.setBeautyStrength(STEffectBeautyType.EFFECT_BEAUTY_FILTER, (float) mFilterStrength / 100);
                }
            }

            printConsume("mNeedFilter end");

            if (mNeedBeautify || mNeedSticker || mNeedMakeUp) {
                if (mStickerChangeWithoutGL) {
                    mStickerChangeWithoutGL = false;
                    if (mStickerIdWithoutGL != 0) {
                        mSTMobileEffectNativeWithoutGL.removeEffect(mStickerIdWithoutGL);
                        mStickerIdWithoutGL = 0;
                    }
                    if (mSticker != null) {
                        mStickerIdWithoutGL = mSTMobileEffectNativeWithoutGL.addPackage(mSticker);
                    }
                }

                printConsume("addPackage end");

                STHumanAction humanAction = null;
                if (mNeedCheckHumanAction) {
                    boolean needFallbackReadBytes;
                    synchronized (I420_BYTE_ARRAY_LOCK) {
                        needFallbackReadBytes = mI420ByteArray == null || width != mI420Width || height != mI420Height;
                        if (!needFallbackReadBytes) {
                            printConsume("onCapturedUnprocessedTextureData copy start");
                            System.arraycopy(mI420ByteArray, 0, mCopyI420ByteArray, 0, mI420ByteArray.length);
                            printConsume("onCapturedUnprocessedTextureData copy end");
                            mSTHumanActionNativeWithoutGL.nativeHumanActionDetectPtr(mCopyI420ByteArray, STCommonNative.ST_PIX_FMT_YUV420P,
                                    mDetectConfig, STRotateType.ST_CLOCKWISE_ROTATE_0, width, height);
                        }
                    }
                    if (needFallbackReadBytes) { // 当 byteArray == null 或者分辨率不一致的情况，都需要 readBytesFromTexture
                        printConsume("readBytesFromTexture start");
                        byte[] humanRgbaByteArray = readBytesFromTexture(inTexture, width, height);
                        printConsume("readBytesFromTexture end");
                        mSTHumanActionNativeWithoutGL.nativeHumanActionDetectPtr(humanRgbaByteArray, STCommonNative.ST_PIX_FMT_RGBA8888,
                                mDetectConfig, STRotateType.ST_CLOCKWISE_ROTATE_0, width, height);
                    }
                    printConsume("nativeHumanActionDetectPtr end");
                    humanAction = mSTHumanActionNativeWithoutGL.getNativeHumanAction();
                    printConsume("getNativeHumanAction end");

                    if (humanAction != null && humanAction.getFaceCount() > 0) {
                        mFaceCount = humanAction.getFaceCount();
                    } else {
                        mFaceCount = 0;
                    }
                    if (mFaceInfoCallBack != null && null != humanAction) {
                        STHumanAction finalHumanAction = humanAction;
                        (new Thread(new Runnable() {
                            public void run() {
                                if (mFaceInfoCallBack != null) {
                                    mFaceInfoCallBack.onSTFaceInfo(finalHumanAction, width, height, null);
                                }

                            }
                        })).start();
                    }
                }

                if (mNeedMakeUp) {
                    if (mMakeUpChangeWithoutGL) {
                        mMakeUpChangeWithoutGL = false;
                        for (Map.Entry<Integer, MakeUpEntity> entry : mMakeUpParams.entrySet()) {
                            MakeUpEntity entity = entry.getValue();
                            if (entity.isAssets()) {
                                mSTMobileEffectNativeWithoutGL.setBeautyFromAssetsFile(entry.getKey(), entity.path, mContext.getAssets());
                            } else {
                                String path = mContext.getExternalFilesDir(null) + File.separator + entity.path;
                                mSTMobileEffectNativeWithoutGL.setBeauty(entry.getKey(), path);
                            }
                            mSTMobileEffectNativeWithoutGL.setBeautyStrength(entry.getKey(), (float) entity.progress / 100);
                        }
                    }
                    printConsume("mNeedMakeUp end");
                }

                if (mNeedBeautify) {
                    if (mBeautyChangeWithoutGL) {
                        mBeautyChangeWithoutGL = false;
                        for (Map.Entry<Integer, Integer> entry : mBeautifyParams.entrySet()) {
                            mSTMobileEffectNativeWithoutGL.setBeautyStrength(entry.getKey(), (float) entry.getValue() / 100);
                        }
                    }

                    printConsume("mBeautyChangeWithoutGL end");


                    STEffectTexture stEffectTexture = new STEffectTexture(inTexture, width, height, 0);
                    STEffectTexture stEffectTextureOut = new STEffectTexture(mBeautifyTextureIdWithoutGL[0], width, height, 0);

                    printConsume("render init");

                    STEffectRenderInParam sTEffectRenderInParam =
                            new STEffectRenderInParam(mSTHumanActionNativeWithoutGL.getNativeHumanActionResultPtr(), null, 3,
                                    STRotateType.ST_CLOCKWISE_ROTATE_0, false, null, stEffectTexture, null);
                    STEffectRenderOutParam stEffectRenderOutParam = new STEffectRenderOutParam(stEffectTextureOut, null, mHumanActionBeautyOutputWithoutGL);

                    printConsume("render start");

                    int result = mSTMobileEffectNativeWithoutGL.render(sTEffectRenderInParam, stEffectRenderOutParam, false);

                    printConsume("render end");

                    int outTexture = inTexture;
                    if (result == 0) {
                        outTexture = mBeautifyTextureIdWithoutGL[0];
                    }

                    printConsume("sendCustomVideoProcessedTextureData start");
                    ZegoExpressEngine.getEngine().sendCustomVideoProcessedTextureData(outTexture, width, height, referenceTimeMillisecond, channel);
                    printConsume("sendCustomVideoProcessedTextureData end");
                }
            }
        }
    }

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

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        mGlDrawer.drawRgb(textureID, IDENTITY_MATRIX, width, height, 0, 0, width, height);

        mReadPixelsByteBuffer.position(0);
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

    // 注册/注销美颜错误回调
    public static void stSetErrorListener(ErrorCallback call) {
        mErrorCallback = call;
    }

    // 注册/注销人脸信息回调
    public static void stSetFaceInfoListener(FaceInfoCallBack call) {
        mFaceInfoCallBack = call;
    }

    // Just for debug
    // 注:即便生成的图片看起来正常，也不能表示纹理没有问题
    public static Bitmap textureToBitmap(int textureId, int width, int height) {
        ByteBuffer mTmpBuffer = ByteBuffer.allocate(height * width * 4);

        int[] mFrameBuffers = new int[1];
        if (textureId != -1) {
            GLES20.glGenFramebuffers(1, mFrameBuffers, 0);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffers[0]);
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, textureId, 0);
        }
        GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, mTmpBuffer);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(mTmpBuffer);
        return bitmap;
    }

    // Just for debug
    public static void saveBitmapToLocal(Bitmap bitmap) {
        try {
            FileOutputStream out = new FileOutputStream(Environment.getExternalStorageDirectory().getAbsolutePath() + "/fuliao-" + System.currentTimeMillis() + ".png");
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static boolean isWritable = true;
    // Just for debug
    private static void writeFile(Context context, byte[] bytes) {
        if (!isWritable) {
            return;
        }
        isWritable = false;
        String path = context.getFilesDir().getPath() + "/test.yuv";
        try {
            FileOutputStream out = new FileOutputStream(path);//指定写到哪个路径中
            FileChannel fileChannel = out.getChannel();
            fileChannel.write(ByteBuffer.wrap(bytes)); //将字节流写入文件中
            fileChannel.force(true);//制刷新
            fileChannel.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Just for debug
    private static void saveRgbaBufferToLocal(ByteBuffer rgba) {
        try {
            rgba.flip();
            FileOutputStream out = new FileOutputStream(Environment.getExternalStorageDirectory().getAbsolutePath() + "/fuliao-" + System.currentTimeMillis() + ".rgba");
            out.write(rgba.array());
            out.flush();
            out.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 设置基础美颜参数集
     *
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
     *
     */
    public static void stSetContourBeautyParams(MakeUpEntity entity) {
        LogUtils.v(TAG, "stSetContourBeautyParams:");
        stSetMakeUpBeautyParam(STEffectBeautyType.EFFECT_BEAUTY_MAKEUP_NOSE, entity);
    }

    /**
     * 设置眉毛参数集
     *
     */
    public static void stSetEyebrowBeautyParams(MakeUpEntity entity) {
        LogUtils.v(TAG, "stSetEyebrowBeautyParams:");
        stSetMakeUpBeautyParam(STEffectBeautyType.EFFECT_BEAUTY_MAKEUP_EYE_BROW, entity);
    }

    /**
     * 设置眼影参数集
     *
     */
    public static void stSetEyeshadowBeautyParams(MakeUpEntity entity) {
        LogUtils.v(TAG, "stSetEyeshadowBeautyParams:");
        stSetMakeUpBeautyParam(STEffectBeautyType.EFFECT_BEAUTY_MAKEUP_EYE_SHADOW, entity);
    }

    /**
     * 设置眼线参数集
     *
     */
    public static void stSetEyelinerBeautyParams(MakeUpEntity entity) {
        LogUtils.v(TAG, "stSetEyelinerBeautyParams:");
        stSetMakeUpBeautyParam(STEffectBeautyType.EFFECT_BEAUTY_MAKEUP_EYE_LINE, entity);
    }

    /**
     * 设置眼睫毛参数集
     *
     */
    public static void stSetEyslashBeautyParams(MakeUpEntity entity) {
        LogUtils.v(TAG, "stSetEyslasheautyParams:");
        stSetMakeUpBeautyParam(STEffectBeautyType.EFFECT_BEAUTY_MAKEUP_EYE_LASH, entity);
    }

    /**
     * 设置美瞳参数集
     *
     */
    public static void stSetStyleBeautyParams(MakeUpEntity entity) {
        LogUtils.v(TAG, "stSetStyleBeautyParams:");
        stSetMakeUpBeautyParam(STEffectBeautyType.EFFECT_BEAUTY_MAKEUP_ALL, entity);
    }

    /**
     * 是否开启美妆
     * @return
     */
    public static boolean isOpenMakeUp(){
        SharedPreferences sp =  mContext.getApplicationContext().getSharedPreferences("spUtils", Context.MODE_PRIVATE);
        return sp.getBoolean(KEY_MAKE_UP_PROFILE,false);
    }

    public static void setInitializeState(boolean state){
        SharedPreferences sp1 =  mContext.getApplicationContext().getSharedPreferences("spUtils", Context.MODE_PRIVATE);
        sp1.edit().putBoolean(KEY_MAKE_UP_IS_INITIALIZE,state).commit();
    }

    public static boolean getInitializeState(){
        SharedPreferences sp1 =  mContext.getApplicationContext().getSharedPreferences("spUtils", Context.MODE_PRIVATE);
        return  sp1.getBoolean(KEY_MAKE_UP_IS_INITIALIZE,false);
    }

    /**
     * 人脸检测接口回调
     */
    public interface FaceInfoCallBack {
        void onSTFaceInfo(STHumanAction humanAction,int picWidth,int pinHeight, Rect[] var2);
    }

    public interface ErrorCallback {
        void onSTError(int error, String msg);
    }
}
