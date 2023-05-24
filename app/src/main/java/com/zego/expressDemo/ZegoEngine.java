package com.zego.expressDemo;

import android.content.Context;
import android.hardware.Camera;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import com.zego.expressDemo.application.BaseApplication;
import com.zego.expressDemo.bean.MixerConfig;
import com.zego.expressDemo.bean.PlayStreamStateBean;
import com.zego.expressDemo.bean.ZegoVideoCanvas;
import com.zego.expressDemo.data.Constant;
import com.zego.expressDemo.data.User;
import com.zego.expressDemo.data.ZegoDataCenter;
import com.zego.expressDemo.utils.AnalyticsLog;
import com.zego.expressDemo.utils.LogUtils;
import com.zego.expressDemo.utils.MD5Utils;
import com.zego.expressDemo.utils.SPUtils;
import com.zego.expressDemo.utils.Utils;
import com.zego.expressDemo.videocapture.CameraExternalVideoCaptureGL;
import com.zego.expressDemo.videocapture.IZegoVideoFrameConsumer;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import im.zego.zegoexpress.ZegoExpressEngine;
import im.zego.zegoexpress.callback.IZegoAudioDataHandler;
import im.zego.zegoexpress.callback.IZegoCustomAudioProcessHandler;
import im.zego.zegoexpress.callback.IZegoDataRecordEventHandler;
import im.zego.zegoexpress.callback.IZegoEventHandler;
import im.zego.zegoexpress.callback.IZegoMixerStartCallback;
import im.zego.zegoexpress.callback.IZegoMixerStopCallback;
import im.zego.zegoexpress.callback.IZegoPublisherTakeSnapshotCallback;
import im.zego.zegoexpress.callback.IZegoPublisherUpdateCdnUrlCallback;
import im.zego.zegoexpress.constants.ZegoANSMode;
import im.zego.zegoexpress.constants.ZegoAudioChannel;
import im.zego.zegoexpress.constants.ZegoAudioCodecID;
import im.zego.zegoexpress.constants.ZegoAudioDataCallbackBitMask;
import im.zego.zegoexpress.constants.ZegoAudioSampleRate;
import im.zego.zegoexpress.constants.ZegoAudioSourceType;
import im.zego.zegoexpress.constants.ZegoAudioVADStableStateMonitorType;
import im.zego.zegoexpress.constants.ZegoAudioVADType;
import im.zego.zegoexpress.constants.ZegoDataRecordState;
import im.zego.zegoexpress.constants.ZegoDataRecordType;
import im.zego.zegoexpress.constants.ZegoPlayerState;
import im.zego.zegoexpress.constants.ZegoPublishChannel;
import im.zego.zegoexpress.constants.ZegoPublisherState;
import im.zego.zegoexpress.constants.ZegoRoomState;
import im.zego.zegoexpress.constants.ZegoScenario;
import im.zego.zegoexpress.constants.ZegoStreamResourceMode;
import im.zego.zegoexpress.constants.ZegoUpdateType;
import im.zego.zegoexpress.constants.ZegoVideoBufferType;
import im.zego.zegoexpress.constants.ZegoVideoCodecID;
import im.zego.zegoexpress.constants.ZegoVideoFrameFormat;
import im.zego.zegoexpress.constants.ZegoVideoMirrorMode;
import im.zego.zegoexpress.entity.ZegoAudioConfig;
import im.zego.zegoexpress.entity.ZegoAudioFrameParam;
import im.zego.zegoexpress.entity.ZegoCDNConfig;
import im.zego.zegoexpress.entity.ZegoCanvas;
import im.zego.zegoexpress.entity.ZegoCustomAudioConfig;
import im.zego.zegoexpress.entity.ZegoCustomAudioProcessConfig;
import im.zego.zegoexpress.entity.ZegoCustomVideoCaptureConfig;
import im.zego.zegoexpress.entity.ZegoDataRecordConfig;
import im.zego.zegoexpress.entity.ZegoDataRecordProgress;
import im.zego.zegoexpress.entity.ZegoMixerInput;
import im.zego.zegoexpress.entity.ZegoMixerOutput;
import im.zego.zegoexpress.entity.ZegoMixerTask;
import im.zego.zegoexpress.entity.ZegoPlayerConfig;
import im.zego.zegoexpress.entity.ZegoPublishStreamQuality;
import im.zego.zegoexpress.entity.ZegoRoomConfig;
import im.zego.zegoexpress.entity.ZegoStream;
import im.zego.zegoexpress.entity.ZegoUser;
import im.zego.zegoexpress.entity.ZegoVideoConfig;
import im.zego.zegoexpress.entity.ZegoVideoFrameParam;
import im.zego.zegoexpress.utils.ZegoLibraryLoadUtil;

/**
 * 外部采集设计思路：
 * 1、一个采集类多路输出源。
 * 2、生命周期由 ZegoEngine 全程管控。
 * 3、统一通过辅路流的预览能力来实现预览。因为只有辅路工作的情况下，需要启动主路录制来实现音频数据驱动，此时会触发主路的视频编码，因此需要做限制。
 * 4、enableCamera 和 setDummyImage 能力是耦合的。所以 enableCamera 需要控制主辅路。
 * 生命周期控制时机：
 * 1、enableCamera
 * 2、startPreview
 * 3、startPublish
 * 塞数据时机：
 * 1、主路流不管什么情况，执行塞数据
 * 2、辅路流根据是否有推辅路流，有的话，则塞数据
 * <p>
 * <p>
 * <p>
 * 1、C++ 音频能力
 * 2、预埋 高优先级逻辑
 * 3、配对首帧慢问题
 */
@SuppressWarnings("unused")
public class ZegoEngine implements IZegoVideoFrameConsumer {

    private static final String TAG = ZegoEngine.class.getSimpleName();

    private static final String DEFAULT_EVENT_HANDLER_KEY = "default";
    public static final String EVENT_HANDLER_KEY_WAIT_SITING = "sit_waiting";
    public static final String EVENT_HANDLER_KEY_PK_1 = "live_pk_1";
    public static final String EVENT_HANDLER_KEY_PK_2 = "live_pk_2";
    public static final String EVENT_HANDLER_KEY_LINE = "live_line_";
    public static final String EVENT_LIVE_ROOM_AUDIENCE = "live_room_audience";
    public static final String EVENT_LIVE_PLAY = "live_play";

    private static final String STREAM_EXTRA_INFO_CDN_TAG = "CDN";
    private static final String CDN_STREAM_ID_PREFIX = "CDN_";

    private static final String TIMER_NAME_SEND_CDN_SINGLE_LAYOUT_SEI = "send_cdn_sei";
    private static final String SEI_MESSAGE_KEY_LAYOUT_USER_COUNT = "luc";

    private static final int MSG_WHAT_STOP_MIX_TASK = 0x10;
    private static final int MSG_WHAT_REMOVE_PUBLISH_CDN_URL_TASK = 0x11;
    public static final long TIME_KEEP_STREAM_PUBLISH_IN_MILLS = 3000;

    private static volatile ZegoEngine sEngine;

    /**
     * 获取引擎对象实例。
     *
     * @return 返回引擎对象实例
     */
    public static ZegoEngine getEngine() {
        if (sEngine == null) {
            synchronized (ZegoEngine.class) {
                if (sEngine == null) {
                    sEngine = new ZegoEngine();
                }
            }
        }
        return sEngine;
    }

    /**
     * 销毁引擎对象
     * <p>
     * 现有的场景，不建议执行
     */
    public synchronized static void destroyEngine() {
        LogUtils.i(TAG, "destroyEngine sEngine: " + sEngine);
        if (sEngine != null) {
            sEngine.release();
        }
        sEngine = null;
    }

    private final Handler mUiHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            if (msg.what == MSG_WHAT_STOP_MIX_TASK) {
                stopMixerTaskIfNeed();
            } else if (msg.what == MSG_WHAT_REMOVE_PUBLISH_CDN_URL_TASK) {
                removePublishCdnUrlIfNeed();
            }
        }
    };

    private final ZegoExpressEngine mExpressEngine;
    private final CameraExternalVideoCaptureGL mCameraCapture;
    private ByteBuffer mCaptureDataByteBuffer;
    private final Map<String, ZegoEngineEventHandler> mEventHandlerMap;

    private boolean isLoginSuccess;
    private String mRoomID; // 当前登录的房间，null 表示为登录房间
    private UserStreamInfo mPublishStreamInfoRTC; // 当前 RTC 推流信息，使用主路流推 RTC 流
    private final Map<ZegoPublishChannel, UserStreamInfo> mCdnPublishStreamInfoMap;  // 当前 CDN 推流的信息，使用 Aux、THIRD、FOURTH 进行推流
    private final List<UserStreamInfo> mPlayingStreamInfos;
    private final Map<UserStreamInfo, PlayStreamStateBean> mPlayingStreamStateBeans;

    private boolean isPreview;
    private boolean isEnableCamera = true;
    private boolean isStartAudioRecord = false;
    private boolean isStartAudioRecordForCDNPublish = false;
    private boolean isEnableAudioCaptureDevice = true;
    private ZegoCanvas mLocalCanvas;
    private final Map<Long, ZegoCanvas> mRemoteCanvasMap;

    private MixerConfig mMixerConfig;
    private String mMixerTargetUrl;
    private String mMixerTargetUrlWithPriority;
    private String mPublishTargetUrl;
    private int mEncodeProfile = 0; // 编码器配置，默认为0，baseline
    private int mGopSize = 2; // sdk 默认的gop size 为2

    private ZegoEngine() {
        LogUtils.i(TAG, "ZegoEngine() init");
        mEventHandlerMap = new HashMap<>();
        mCdnPublishStreamInfoMap = new HashMap<>();
        mPlayingStreamInfos = new ArrayList<>();
        mRemoteCanvasMap = new HashMap<>();
        mPlayingStreamStateBeans = new HashMap<>();

        mCameraCapture = new CameraExternalVideoCaptureGL(this);

        // ZegoScenario 参数说明：
        // GENERAL 表示全程使用媒体音量，只使用软件的 3A 处理，上麦占用麦克风，下麦不占用麦克风。IOS 由于上下麦有设备启停的操作，所以上下麦会有轻微的卡顿感觉。
        // COMMUNICATION 表示全程使用通话音量，打开硬件的 3A 处理，全程占用麦克风。由于全程不存在设备启停和模式切换问题，上下麦不会有卡顿问题。
        // LIVE 麦下使用媒体音量，麦上使用通话音量，打开硬件的 3A 处理，上麦占用麦克风，下麦不占用麦克风。由于存在模式的切换，所以上下麦会有轻微的卡顿感觉。
        // 通话音量和媒体音量区别：
        // 通话音量：不可以调节成 0
        // 媒体音量：可以调节成 0
        // 两个音量模式会记忆之前的音量值，如媒体音量为0的情况下，切换成通话音量，那么此时通话音量的音量值是之前通话音量的设置值，而不是媒体音量的0。
        try {
            mExpressEngine = ZegoExpressEngine.createEngine(ZegoDataCenter.APP_ID,
                    ZegoDataCenter.APP_SIGN, ZegoDataCenter.IS_TEST_ENV, ZegoScenario.LIVE, BaseApplication.getInstance(), new IZegoEventHandler() {

                        @Override
                        public void onRoomStreamUpdate(String roomID, ZegoUpdateType updateType, ArrayList<ZegoStream> streamList, JSONObject extendedData) {
                            List<UserStreamInfo> streamInfoList = parseToStreamInfo(streamList); // 直推CDN的，还是会通过房间流更新回调告知其他房间用户，所以这里会将直推CDN的给过滤掉
                            if (ZegoUpdateType.ADD == updateType) {
                                startPlayStreamList(streamInfoList);
                            } else {
                                stopPlayStreamList(streamInfoList);
                            }

                            if (!streamInfoList.isEmpty()) {
                                // empty 表示 CDN 流更新，只有 RTC 流更新才进行混流更新
                                updateMixerTaskIfNeed();
                            }
                        }

                        @Override
                        public void onRoomStateUpdate(String roomID, ZegoRoomState state, int errorCode, JSONObject extendedData) {
                            if (!roomID.equals(mRoomID)) { // 如果房间 ID 不一致，直接过滤
                                return;
                            }
                            if (!isLoginSuccess && state == ZegoRoomState.CONNECTED && errorCode == 0) {
                                joinLiveSuccess(roomID, User.get().getUserId());
                            } else if (state == ZegoRoomState.DISCONNECTED && isLoginSuccess && errorCode == 0) {
                                leaveLive(roomID, User.get().getUserId());
                            } else if (state == ZegoRoomState.DISCONNECTED && !isLoginSuccess && errorCode != 0) {
                                joinLiveFailed(roomID, User.get().getUserId(), errorCode);
                            } else if (state == ZegoRoomState.DISCONNECTED && isLoginSuccess) {
                                liveDisconnected();
                            }
                        }

                        @Override
                        public void onPublisherStateUpdate(String streamID, ZegoPublisherState state, int errorCode, JSONObject extendedData) {
                            LogUtils.d(TAG, "onPublisherStateUpdate streamID: " + streamID + " , state: " + state);
                            boolean isRtc = mPublishStreamInfoRTC != null && mPublishStreamInfoRTC.target.equals(streamID);
                            if (isRtc && ZegoPublisherState.PUBLISHING == state) {
                                updateMixerTaskIfNeed();
                            }
                        }

                        @Override
                        public void onPublisherQualityUpdate(String streamID, ZegoPublishStreamQuality quality) {
                            if (mPublishStreamInfoRTC != null && mPublishStreamInfoRTC.target.equals(streamID)) {
                                publisherQualityUpdate(User.get().getUserId(), quality);
                            }
                        }

                        @Override
                        public void onPublisherCapturedVideoFirstFrame(ZegoPublishChannel channel) {
                            publisherCapturedVideoFirstFrame();
                        }

                        @Override
                        public void onPlayerStateUpdate(String streamID, ZegoPlayerState state, int errorCode, JSONObject extendedData) {
                            LogUtils.d(TAG, "onPlayerStateUpdate streamID: " + streamID + " , state: " + state);
                            if (state == ZegoPlayerState.NO_PLAY && errorCode != 0) {
                                callOnPlayerPlayFailed(getPlayingStreamInfoByStreamID(streamID), errorCode);
                            }
                        }

                        @Override
                        public void onPlayerRecvVideoFirstFrame(String streamID) {
                            LogUtils.d(TAG, "onPlayerRecvVideoFirstFrame streamID: " + streamID);
                            playerRecvVideoFirstFrame(getPlayingStreamInfoByStreamID(streamID));
                        }

                        @Override
                        public void onPlayerRecvAudioFirstFrame(String streamID) {
                            playerRecvAudioFirstFrame(getPlayingStreamInfoByStreamID(streamID));
                        }

                        @Override
                        public void onPlayerRenderVideoFirstFrame(String streamID) {
                            playerRenderVideoFirstFrame(getPlayingStreamInfoByStreamID(streamID));
                        }

//                        @Override
//                        public void onDeviceError(int errorCode, String deviceName) {
//                            LogUtils.d("onDeviceError errorCode = $errorCode  deviceName = $deviceName");
//                        }

                        @Override
                        public void onCapturedSoundLevelUpdate(float soundLevel) {
                            capturedSoundLevelUpdate(soundLevel);
                        }

                        @Override
                        public void onRemoteSoundLevelUpdate(HashMap<String, Float> soundLevels) {
                            remoteSoundLevelUpdate(soundLevels);
                        }

                        @Override
                        public void onMixerSoundLevelUpdate(HashMap<Integer, Float> soundLevels) {
                            mixerSoundLevelUpdate(soundLevels);
                        }

                        @Override
                        public void onPublisherVideoEncoderChanged(ZegoVideoCodecID fromCodecID, ZegoVideoCodecID toCodecID, ZegoPublishChannel channel) {
                            super.onPublisherVideoEncoderChanged(fromCodecID, toCodecID, channel);
                            //机子不符合265   fromCodecID(265)  -> toCodecID(264)
                        }

                        @Override
                        public void onPlayerRecvSEI(String streamID, byte[] data) {
                            playerRecvSEI(streamID, data);
                        }

                        @Override
                        public void onAudioVADStateUpdate(ZegoAudioVADStableStateMonitorType zegoAudioVADStableStateMonitorType, ZegoAudioVADType zegoAudioVADType) {
//                        super.onAudioVADStateUpdate(zegoAudioVADStableStateMonitorType, zegoAudioVADType);
                            onStartAudioVADStableStateMonitor(zegoAudioVADStableStateMonitorType, zegoAudioVADType);
                        }
                    });

        } catch (UnsatisfiedLinkError e) {
            ensureSoLoaded();
            throw e;
        }

        // 设置外部视频采集
        ZegoCustomVideoCaptureConfig customVideoCaptureConfig = new ZegoCustomVideoCaptureConfig();
        customVideoCaptureConfig.bufferType = ZegoVideoBufferType.RAW_DATA;
        mExpressEngine.enableCustomVideoCapture(true, customVideoCaptureConfig, ZegoPublishChannel.MAIN);
        mExpressEngine.enableCustomVideoCapture(true, customVideoCaptureConfig, ZegoPublishChannel.AUX);
        mExpressEngine.enableCustomVideoCapture(true, customVideoCaptureConfig, ZegoPublishChannel.THIRD);
        mExpressEngine.enableCustomVideoCapture(true, customVideoCaptureConfig, ZegoPublishChannel.FOURTH);
        mExpressEngine.setCustomVideoCaptureHandler(mCameraCapture);

        // 启动外部音频前处理
        mExpressEngine.enableCustomAudioCaptureProcessing(true, new ZegoCustomAudioProcessConfig());
        mExpressEngine.setCustomAudioProcessHandler(new IZegoCustomAudioProcessHandler() {
            @Override
            public void onProcessCapturedAudioData(ByteBuffer data, int dataLength, ZegoAudioFrameParam param, double timestamp) {
                if (!mCdnPublishStreamInfoMap.isEmpty()) {
                    for (int channelIndexValue = ZegoPublishChannel.AUX.value(); channelIndexValue <= ZegoPublishChannel.FOURTH.value(); channelIndexValue++) {
                        ZegoPublishChannel publishChannel = ZegoPublishChannel.getZegoPublishChannel(channelIndexValue);
                        if (mCdnPublishStreamInfoMap.containsKey(publishChannel)) { // 这里有线程安全问题
                            mExpressEngine.sendCustomAudioCapturePCMData(data, dataLength, param, publishChannel);
                        }
                    }
                }
            }
        });

        // CDN 流音频来源
        ZegoCustomAudioConfig audioConfig = new ZegoCustomAudioConfig();
        audioConfig.sourceType = ZegoAudioSourceType.CUSTOM;
        mExpressEngine.enableCustomAudioIO(true, audioConfig, ZegoPublishChannel.AUX);
        mExpressEngine.enableCustomAudioIO(true, audioConfig, ZegoPublishChannel.THIRD);
        mExpressEngine.enableCustomAudioIO(true, audioConfig, ZegoPublishChannel.FOURTH);

        // 默认预览推流都镜像
        boolean isSetMirrorMode = (boolean) SPUtils.get(Utils.getApp(), User.get().getUserId() + Constant.KEY_VIDEO_MIRROR_MODE_VALUE, false);
        if (isSetMirrorMode) {
            setVideoMirrorMode(ZegoVideoMirrorMode.ONLY_PREVIEW_MIRROR);
        } else {
            setVideoMirrorMode(ZegoVideoMirrorMode.BOTH_MIRROR);
        }

        // 音频编码格式，CDN 只支持 NORMAL
        ZegoAudioConfig mainAudioConfig = new ZegoAudioConfig();
        mainAudioConfig.codecID = ZegoAudioCodecID.LOW3;
        mExpressEngine.setAudioConfig(mainAudioConfig, ZegoPublishChannel.MAIN);
        ZegoAudioConfig auxAudioConfig = new ZegoAudioConfig();
        auxAudioConfig.codecID = ZegoAudioCodecID.NORMAL;
        mExpressEngine.setAudioConfig(auxAudioConfig, ZegoPublishChannel.AUX);
        mExpressEngine.setAudioConfig(auxAudioConfig, ZegoPublishChannel.THIRD);
        mExpressEngine.setAudioConfig(auxAudioConfig, ZegoPublishChannel.FOURTH);
//        mExpressEngine.enableHardwareDecoder(true);
//        mExpressEngine.enableHardwareEncoder(true);
    }

    private void setVideoMirrorMode(ZegoVideoMirrorMode mirrorMode) {
        mExpressEngine.setVideoMirrorMode(mirrorMode, ZegoPublishChannel.MAIN);
        mExpressEngine.setVideoMirrorMode(mirrorMode, ZegoPublishChannel.AUX);
        mExpressEngine.setVideoMirrorMode(mirrorMode, ZegoPublishChannel.THIRD);
        mExpressEngine.setVideoMirrorMode(mirrorMode, ZegoPublishChannel.FOURTH);
    }

    private void release() {
        mCameraCapture.destroy();

        ZegoExpressEngine.destroyEngine(null);
    }

    public void setZegoEngineEventHandler(ZegoEngineEventHandler handler) {
        LogUtils.i(TAG, "setZegoEngineEventHandler handler: " + handler);
        mEventHandlerMap.put(DEFAULT_EVENT_HANDLER_KEY, handler);
    }

    /**
     * 如果有某个场景与其他场景共存的情况，如坐台小窗与直播间共存。那么长活的需要帮忙特定场景监听器。
     *
     * @param sceneType 场景类型
     */
    public void setZegoEngineEventHandler(String sceneType, ZegoEngineEventHandler handler) {
        LogUtils.i(TAG, "setZegoEngineEventHandler sceneType: " + sceneType + ", handler: " + handler);
        if (handler == null) {
            mEventHandlerMap.remove(sceneType);
        } else {
            mEventHandlerMap.put(sceneType, handler);
        }
    }

    public void startPreview() {
        LogUtils.i(TAG, "startPreview isPreview: " + isPreview + ", mLocalCanvas: " + mLocalCanvas);
        isPreview = true;
        mExpressEngine.startPreview(mLocalCanvas);

        checkAndSetVideoCaptureState();
    }

    /**
     * 开始录制
     */
    public void startRecordingCaptured(ZegoDataRecordConfig recordConfig) {
        LogUtils.i(TAG, "startRecordingCaptured isStartAudioRecord: " + isStartAudioRecord);
        if (isStartAudioRecord) {
            return;
        }
        isStartAudioRecord = true;

        // 先停止由于 CDN 推流导致的录制（强制）
        stopRecordCapturedDataForCDNPublishInner();

        mExpressEngine.startRecordingCapturedData(recordConfig, ZegoPublishChannel.MAIN);
        mExpressEngine.setDataRecordEventHandler(new IZegoDataRecordEventHandler() {

            public void onCapturedDataRecordStateUpdate(ZegoDataRecordState state, int errorCode, ZegoDataRecordConfig config, ZegoPublishChannel channel) {
                // 开发者可以在这里根据错误码或者录制状态处理录制过程状态变更的逻辑，例如在界面上进行 UI 的提示等
                LogUtils.i(TAG, "startRecordingCaptured: " + state.toString());
            }

            public void onCapturedDataRecordProgressUpdate(ZegoDataRecordProgress progress, ZegoDataRecordConfig config, ZegoPublishChannel channel) {
                // 开发者可以在这里根据录制进度处理录制过程进度变更的逻辑，例如在界面上进行 UI 的提示等
                LogUtils.i(TAG, "startRecordingCaptured: " + progress.currentFileSize + ", mLocalCanvas: " + mLocalCanvas);
            }
        });
    }

    /**
     * 停止录制
     */
    public void stopRecordingCaptured() {
        LogUtils.i(TAG, "stopRecordingCaptured isStartAudioRecord: " + isStartAudioRecord);
        if (!isStartAudioRecord) {
            return;
        }
        mExpressEngine.stopRecordingCapturedData(ZegoPublishChannel.MAIN);
        mExpressEngine.setDataRecordEventHandler(null);

        isStartAudioRecord = false;

        startRecordCapturedDataForCDNPublishIfNeed();
    }

    private void startRecordCapturedDataForCDNPublishIfNeed() {
        LogUtils.d(TAG, "startRecordCapturedDataForCDNPublishIfNeed isStartAudioRecord: " + isStartAudioRecord + " ,isStartAudioRecordForCDNPublish: " + isStartAudioRecordForCDNPublish + ", mPublishStreamInfoRTC: " + mPublishStreamInfoRTC + ", mCdnPublishStreamInfoMap: " + mCdnPublishStreamInfoMap);
        if (isStartAudioRecord || isStartAudioRecordForCDNPublish || mPublishStreamInfoRTC != null || mCdnPublishStreamInfoMap.isEmpty()) { // 已经在录制了，或者主路在推流，或者不需要推 CDN
            return;
        }
        ZegoDataRecordConfig recordConfig = new ZegoDataRecordConfig();
        recordConfig.recordType = ZegoDataRecordType.ONLY_AUDIO;
        recordConfig.filePath = new File(BaseApplication.getInstance().getExternalCacheDir(), "temp.aac").getAbsolutePath();
        mExpressEngine.startRecordingCapturedData(recordConfig, ZegoPublishChannel.MAIN);

        isStartAudioRecordForCDNPublish = true;

        // 当启动主路的本地媒体录制时，则需要主动关闭主路的摄像头
        mExpressEngine.enableCamera(false, ZegoPublishChannel.MAIN);
    }

    private void stopRecordCapturedDataForCDNPublishIfNeed() {
        // 外部启动了录制，或者主路流在推，或者 CDN 列表没有的情况下，需要尝试停止内部音频驱动录制
        if (isStartAudioRecord || mPublishStreamInfoRTC != null || mCdnPublishStreamInfoMap.isEmpty()) {
            stopRecordCapturedDataForCDNPublishInner();
        }
    }

    private void stopRecordCapturedDataForCDNPublishInner() {
        LogUtils.d(TAG, "stopRecordCapturedDataForCDNPublishInner isStartAudioRecordForCDNPublish: " + isStartAudioRecordForCDNPublish);
        if (isStartAudioRecordForCDNPublish) {
            mExpressEngine.stopRecordingCapturedData(ZegoPublishChannel.MAIN);
            isStartAudioRecordForCDNPublish = false;

            // 当停止因推辅路 CDN 触发的本地媒体录制，需要按需启停主路摄像头
            mExpressEngine.enableCamera(isEnableCamera, ZegoPublishChannel.MAIN);
        }
    }

    public void stopPreview() {
        LogUtils.i(TAG, "stopPreview isPreview: " + isPreview);
        isPreview = false;
        mExpressEngine.stopPreview(ZegoPublishChannel.AUX);

        checkAndSetVideoCaptureState();
    }

    public void enableCamera(boolean enable) {
        LogUtils.i(TAG, "enableCamera enable: " + enable);
        isEnableCamera = enable;
        mExpressEngine.enableCamera(enable, ZegoPublishChannel.MAIN);
        mExpressEngine.enableCamera(enable, ZegoPublishChannel.AUX);
        mExpressEngine.enableCamera(enable, ZegoPublishChannel.THIRD);
        mExpressEngine.enableCamera(enable, ZegoPublishChannel.FOURTH);
        checkAndSetVideoCaptureState();
    }

    /**
     * 检测并且按需启动状态
     */
    private void checkAndSetVideoCaptureState() {
        LogUtils.d(TAG, "checkAndSetVideoCaptureState isEnableCamera: " + isEnableCamera + ", isPreview: " + isPreview + ", mCdnPublishStreamInfoMap: " + mCdnPublishStreamInfoMap + ", mPublishStreamInfoRTC: " + mPublishStreamInfoRTC);
        if (!isEnableCamera // 关闭摄像头
                || (!isPreview && mCdnPublishStreamInfoMap.isEmpty() && mPublishStreamInfoRTC == null)) { // 没有预览并且没有推流
            mCameraCapture.stop();
        } else {
            mCameraCapture.start();
        }
    }

    public boolean isBackCamera() {
        return mCameraCapture.getFront() == Camera.CameraInfo.CAMERA_FACING_BACK;
    }

    public void setLocalVideoCanvas(ZegoVideoCanvas localVideoCanvas) {
        LogUtils.i(TAG, "setLocalVideoCanvas localVideoCanvas: " + localVideoCanvas);
        mLocalCanvas = localVideoCanvas == null ? null : localVideoCanvas.convertToZegoCanvas();
        if (isPreview) {
            mExpressEngine.startPreview(mLocalCanvas, ZegoPublishChannel.AUX);
        }
    }

    public void setRemoteVideoCanvas(ZegoVideoCanvas remoteVideoCanvas) {
        LogUtils.i(TAG, "setRemoteVideoCanvas remoteVideoCanvas: " + remoteVideoCanvas);
        ZegoCanvas canvas = remoteVideoCanvas.convertToZegoCanvas();
        mRemoteCanvasMap.put(remoteVideoCanvas.uid, canvas);

        String streamID = getStreamIDByUidIfPlaying(remoteVideoCanvas.uid);
        if (!TextUtils.isEmpty(streamID)) { // 指定的流在拉
            mExpressEngine.startPlayingStream(streamID, canvas);
        }
    }

    /**
     * 设置视频的编码参数
     *
     * @param width   视频的宽
     * @param height  视频的高
     * @param fps     视频的编码帧率
     * @param bitrate 视频的编码码率
     * @param channel 设置推流通道类型，null 表示同时设置
     */
    public void setVideoConfig(int width, int height, int fps, int bitrate, ZegoPublishChannel channel) {
        LogUtils.i(TAG, "setVideoConfig width: " + width + ", height: " + height + ", fps: " + fps + ", bitrate: " + bitrate);
        ZegoVideoConfig videoConfig = new ZegoVideoConfig();
        videoConfig.encodeWidth = width;
        videoConfig.encodeHeight = height;
        videoConfig.fps = fps;
        videoConfig.bitrate = bitrate;
        if (channel != null) {
            mExpressEngine.setVideoConfig(videoConfig, channel);
        } else {
            mExpressEngine.setVideoConfig(videoConfig, ZegoPublishChannel.MAIN);
            mExpressEngine.setVideoConfig(videoConfig, ZegoPublishChannel.AUX);
            mExpressEngine.setVideoConfig(videoConfig, ZegoPublishChannel.THIRD);
            mExpressEngine.setVideoConfig(videoConfig, ZegoPublishChannel.FOURTH);

        }
        AnalyticsLog.INSTANCE.reportMakeupModelInfo(bitrate + "", fps + "", width + "*" + height);
    }

    /**
     * 设置编码器的编码模式，如 baseline(0)、high_profile(2)
     * 目前只会作用于cdn
     *
     * @param mEncodeProfile 模式
     */
    public void setEncodeProfile(int mEncodeProfile) {
        if (mEncodeProfile >= 0 && mEncodeProfile <= 2) {
            this.mEncodeProfile = mEncodeProfile;
        }
    }

    /**
     * 设置编码的 gop大小，单位秒
     * 目前只会作用于cdn
     */
    public void setGopSize(int mGopSize) {
        if (mGopSize < 2) {
            return;
        }
        this.mGopSize = mGopSize;
    }

    public void startSoundLevelMonitor(int timeInMS) {
        LogUtils.i(TAG, "startSoundLevelMonitor timeInMS: " + timeInMS);
        mExpressEngine.startSoundLevelMonitor(timeInMS);
    }

    /**
     * 发送媒体增强补充信息
     */
    public void sendSEI(byte[] bytes) {
        mExpressEngine.sendSEI(bytes);
    }

    /**
     * 发送媒体增强补充信息
     */
    public void sendSEI(byte[] bytes, ZegoPublishChannel channel) {
        mExpressEngine.sendSEI(bytes, channel);
    }

    public static class JoinLiveBuilder {
        private final String roomID;
        private UserStreamInfo rtcPublishStreamInfo;
        private final Map<ZegoPublishChannel, UserStreamInfo> cdnPublishStreamInfos;

        private final List<UserStreamInfo> playStreamInfos;

        public JoinLiveBuilder(String roomID) {
            this.roomID = roomID;
            cdnPublishStreamInfos = new HashMap<>();
            playStreamInfos = new ArrayList<>();
        }

        public JoinLiveBuilder putPublishStreamInfo(UserStreamInfo publishStreamInfo) {
            // 过滤无效的输入
            if (UserStreamInfo.isInvalid(publishStreamInfo)) {
                return this;
            }
            if (publishStreamInfo.streamType == StreamType.RTC) {
                rtcPublishStreamInfo = publishStreamInfo;
                rtcPublishStreamInfo.publishChannel = ZegoPublishChannel.MAIN;
            } else {
                cdnPublishStreamInfos.put(publishStreamInfo.publishChannel == null ? ZegoPublishChannel.AUX : publishStreamInfo.publishChannel, publishStreamInfo);
            }
            return this;
        }

        public JoinLiveBuilder addPlayStreamInfo(UserStreamInfo playStreamInfo) {
            // 过滤无效的输入
            if (UserStreamInfo.isInvalid(playStreamInfo)) {
                return this;
            }
            this.playStreamInfos.add(playStreamInfo);
            return this;
        }

        public JoinLiveBuilder clearPlayStream() {
            playStreamInfos.clear();
            return this;
        }

        public void joinLive() {
            ZegoEngine.getEngine().joinLive(roomID, rtcPublishStreamInfo, cdnPublishStreamInfos, playStreamInfos);
        }
    }


    public static JoinLiveBuilder getLiveBuilder(String roomID) {
        return new JoinLiveBuilder(roomID);
    }

    /**
     * 是否已经登录房间
     */
    public boolean hasLoginRoom() {
        return !TextUtils.isEmpty(mRoomID);
    }

    /**
     * 加入直播，并根据配置确认是否推拉流
     *
     * @param roomID                指定的房间ID，如果当前已经登录房间，内部执行的停止当前的推拉流，然后执行切换房间操作。作为拉流器的时候，roomID 建议规则为 "player-" + 当前用户的UserID。这样子的话，可以避免收到正常房间的流新增删除触发的拉流。
     * @param rtcPublishStreamInfo  rtc 推流的配置。null 表示不推 RTC 流。rtcPublishStreamInfo 和 cdnPublishStreamInfos 都为 null 的情况下表示不推流
     * @param cdnPublishStreamInfos cdn 推流的配置。null 表示不推 CDN 流。rtcPublishStreamInfo 和 cdnPublishStreamInfos 都为 null 的情况下表示不推流
     * @param playStreamInfos       预先拉流配置，表示这次登录房间的时候，预先需要拉的流，如果此时在拉别的流，则停止这些流的拉流操作。 null 表示不需要预先拉流。登录房间后，还是会根据 onRoomStreamUpdate 去触发拉流和停止拉流
     */
    private void joinLive(String roomID, UserStreamInfo rtcPublishStreamInfo, Map<ZegoPublishChannel, UserStreamInfo> cdnPublishStreamInfos, List<UserStreamInfo> playStreamInfos) {
        LogUtils.d(TAG, "joinLive roomID: " + roomID + ", mRoomID: " + mRoomID +
                ", publishStreamInfos: " + rtcPublishStreamInfo + ", cdnPublishStreamInfos: " + cdnPublishStreamInfos + ", playStreamInfos: " + playStreamInfos +
                ", mPublishStreamInfoRTC: " + mPublishStreamInfoRTC + ", mCdnPublishStreamInfoMap: " + mCdnPublishStreamInfoMap +
                ", mPlayingStreamInfos: " + mPlayingStreamInfos);
        boolean isLoginSameRoom = false;
        isLoginSuccess = false;

        if (mRoomID == null) {
            ZegoUser mLocalUser = new ZegoUser(User.get().getUserId() + "", User.get().getUserId() + "");
            mExpressEngine.loginRoom(roomID, mLocalUser, new ZegoRoomConfig());
        } else if (mRoomID.equals(roomID)) {
            if (mPublishStreamInfoRTC != null) {
                // 保持之前的逻辑，如果当前的推流地址和要求推流的地址不一样，则停止推流。当没有要求的推流地址，由于登录同一个房间，这里直接忽略。
                if (!mPublishStreamInfoRTC.equals(rtcPublishStreamInfo) && null != rtcPublishStreamInfo) {
                    stopPublish(mPublishStreamInfoRTC);
                    // 如果 RTC 流地址不一样，停止转推
                    removePublishCdnUrlIfNeed();
                }
            }
            for (int channelIndexValue = ZegoPublishChannel.AUX.value(); channelIndexValue <= ZegoPublishChannel.FOURTH.value(); channelIndexValue++) {
                ZegoPublishChannel publishChannel = ZegoPublishChannel.getZegoPublishChannel(channelIndexValue);
                UserStreamInfo currentCdnPublishInfoForChannel = mCdnPublishStreamInfoMap.get(publishChannel);
                UserStreamInfo publishStreamInfoCDN = cdnPublishStreamInfos.get(publishChannel);
                // 保持之前的逻辑，如果当前的推流地址和要求推流的地址不一样，则停止推流。当没有要求的推流地址，由于登录同一个房间，这里直接忽略。
                if (currentCdnPublishInfoForChannel != null // 当前在推流
                        && !currentCdnPublishInfoForChannel.equals(publishStreamInfoCDN) // 推流的ID跟期望推流的ID不一样
                        && null != publishStreamInfoCDN) {  // 准备要推流，对于重复登录同一个房间的，只有在使用同一个通道推不同流的情况下，才会停止推流。
                    stopPublish(currentCdnPublishInfoForChannel);
                }
            }
            joinLiveSuccess(roomID, User.get().getUserId());
            isLoginSameRoom = true;
            // 如果是同一个房间，不会执行清除远程视图的逻辑。
        } else {
            if (mPublishStreamInfoRTC != null) {
                if (!mPublishStreamInfoRTC.equals(rtcPublishStreamInfo)) {
                    // 如果 RTC 流地址不一样，停止转推
                    removePublishCdnUrlIfNeed();
                } else {
                    // 如果是一样的话，按需启动延时任务，这里的规则还是，切换房间，所有东西能清就清。
                    triggerRemovePublishCdnUrl();
                }
            }

            // 如果已经登录房间，则直接通过 switchRoom 进行房间切换，该逻辑会停止推拉流的。
            mExpressEngine.switchRoom(mRoomID, roomID);

            // 切换房间后，都停止推流了
            mPublishStreamInfoRTC = null;
            mCdnPublishStreamInfoMap.clear();

            mPlayingStreamInfos.clear(); // 正在拉的流都会停止。
            mRemoteCanvasMap.clear(); // 如果切换房间，则清除远程视图。
            mPlayingStreamStateBeans.clear();
        }
        mRoomID = roomID;

        if (isLoginSameRoom) {
            checkCdnPlayStreamInfoAndPlay(playStreamInfos);
        } else {
            // 如果不是同一个房间，那么设备状态需要重置，推拉流列表严格按照参数指定的
            resetDeviceState();
            checkAllPlayStreamInfoAndPlay(playStreamInfos);  // 对在拉的流做检测，最终结果只会拉 playStreamInfos 中的流。
        }

        startPublishStreams(rtcPublishStreamInfo, cdnPublishStreamInfos); // 如果 publishStreamInfo != null，则进行拉流

        checkAndSetVideoCaptureState();
    }

    /**
     * 登录房间的时候，需要重置设备状态
     */
    private void resetDeviceState() {
        LogUtils.d(TAG, "resetDeviceState");
        // 摄像头恢复默认状态
        isEnableCamera = true;
        mExpressEngine.enableCamera(true, ZegoPublishChannel.MAIN);
        mExpressEngine.enableCamera(true, ZegoPublishChannel.AUX);
        mExpressEngine.enableCamera(true, ZegoPublishChannel.THIRD);
        mExpressEngine.enableCamera(true, ZegoPublishChannel.FOURTH);
        mCameraCapture.removeColorWatermark();
        // 默认音频采集设备打开
        if (!isEnableAudioCaptureDevice) { // enableAudioCaptureDevice 为同步操作。需要严谨检查
            isEnableAudioCaptureDevice = true;
            mExpressEngine.enableAudioCaptureDevice(true);
        }
        // 默认推音频
        mExpressEngine.mutePublishStreamAudio(false, ZegoPublishChannel.MAIN);
        mExpressEngine.mutePublishStreamAudio(false, ZegoPublishChannel.AUX);
        mExpressEngine.mutePublishStreamAudio(false, ZegoPublishChannel.THIRD);
        mExpressEngine.mutePublishStreamAudio(false, ZegoPublishChannel.FOURTH);
        LogUtils.d(TAG, "resetDeviceState end");
    }

    public void leaveLive(boolean isClosePreview) {
        LogUtils.i(TAG, "leaveLive isLoginSuccess: " + isLoginSuccess);
        // reset 会将 uiHandler 中的延时任务都移除
        removePublishCdnUrlIfNeed();
        stopMixerTaskIfNeed();

        mExpressEngine.logoutRoom();

        reset(isClosePreview);
    }

    public void leaveLive() {
        leaveLive(false);
    }

    /**
     * 开启监听音频采集数据
     *
     * @param sampleRate   采样率
     * @param channelCount 声道数
     */
    public void startAudioCaptureDataObserver(int sampleRate, int channelCount, final IZegoAudioCaptureDataHandler dataHandler) {
        ZegoAudioFrameParam frameParam = new ZegoAudioFrameParam();
        frameParam.sampleRate = ZegoAudioSampleRate.getZegoAudioSampleRate(sampleRate);
        frameParam.channel = ZegoAudioChannel.getZegoAudioChannel(channelCount);
        mExpressEngine.startAudioDataObserver(ZegoAudioDataCallbackBitMask.CAPTURED.value(), frameParam);
        mExpressEngine.setAudioDataHandler(new IZegoAudioDataHandler() {
            @Override
            public void onCapturedAudioData(ByteBuffer data, int dataLength, ZegoAudioFrameParam param) {
                if (dataHandler != null) {
                    byte[] byteArray = new byte[dataLength];
                    data.get(byteArray);

                    dataHandler.onCapturedAudioData(byteArray, dataLength, param.sampleRate.value(), param.channel.value());
                }
            }
        });
    }

    /**
     * 停止监听音频采集数据
     */
    public void stopAudioCaptureDataObserver() {
        mExpressEngine.stopAudioDataObserver();
        mExpressEngine.setAudioDataHandler(null);
    }

    /**
     * 停止推 RTC 或者 CDN 流
     */
    public void stopPublish(UserStreamInfo publishStreamInfo) {
        LogUtils.i(TAG, "stopPublish publishStreamInfo: " + publishStreamInfo + ", mPublishStreamInfoRTC: " + mPublishStreamInfoRTC + ", mCdnPublishStreamInfoMap: " + mCdnPublishStreamInfoMap);
        if (publishStreamInfo == null) {
            return;
        }

        if ((publishStreamInfo.publishChannel == ZegoPublishChannel.MAIN || publishStreamInfo.publishChannel == null) && // 没有指定，则尝试，有指定，则直接判断
                mPublishStreamInfoRTC != null && mPublishStreamInfoRTC.equals(publishStreamInfo)) {
            mPublishStreamInfoRTC = null;
            mExpressEngine.stopPublishingStream(ZegoPublishChannel.MAIN);
        } else if (!mCdnPublishStreamInfoMap.isEmpty() && publishStreamInfo.publishChannel != ZegoPublishChannel.MAIN) { // 如果有 CDN 流在推流，并且没有指定或者指定的是非 main 通道
            if (publishStreamInfo.publishChannel == null) { // 没有指定 channel，则遍历
                for (int channelIndexValue = ZegoPublishChannel.AUX.value(); channelIndexValue <= ZegoPublishChannel.FOURTH.value(); channelIndexValue++) {
                    ZegoPublishChannel publishChannel = ZegoPublishChannel.getZegoPublishChannel(channelIndexValue);
                    UserStreamInfo currentCdnPublishInfoForChannel = mCdnPublishStreamInfoMap.get(publishChannel);
                    if (publishStreamInfo.equals(currentCdnPublishInfoForChannel)) { // 找到则停止推流，由于这里没有指定 channel，所以会对所有符合条件的 publishStreamInfo 都执行停止推流
                        mExpressEngine.stopPublishingStream(publishChannel);
                        mCdnPublishStreamInfoMap.remove(publishChannel); // 从 map 中移除
                    }
                }
            } else { // 指定了非 main 通道的
                UserStreamInfo currentCdnPublishInfoForChannel = mCdnPublishStreamInfoMap.get(publishStreamInfo.publishChannel);
                if (publishStreamInfo.equals(currentCdnPublishInfoForChannel)) { // 跟指定 channel 的 streamInfo 一致，则停止推流
                    mExpressEngine.stopPublishingStream(publishStreamInfo.publishChannel);
                    mCdnPublishStreamInfoMap.remove(publishStreamInfo.publishChannel); // 从 map 中移除
                }
            }
        }

        // 根据逻辑确认当前是否需要因为辅路音频而进行本地媒体录制
        startRecordCapturedDataForCDNPublishIfNeed();
        stopRecordCapturedDataForCDNPublishIfNeed();
    }

    /**
     * 拉指定 CDN 流，只能拉 CDN 流
     * 使用该方法，必须保证有对应的调用 stopPlayStream 或者 leaveLive，否则会有串音或者性能问题。
     * 暂时建议只在个人主页上使用该方法。
     *
     * @param playStreamInfo 拉流信息
     */
    public void startPlayStream(UserStreamInfo playStreamInfo) {
        LogUtils.i(TAG, "startPlayStream playStreamInfo: " + playStreamInfo);
        if (playStreamInfo == null || playStreamInfo.streamType != StreamType.CDN) {
            LogUtils.w(TAG, "startPlayStream playStreamInfo illegal");
            return;
        }
        if (mPlayingStreamInfos.contains(playStreamInfo)) {
            LogUtils.d(TAG, "startPlayStream playStreamInfo has already playing");
            callPlayStreamStateIfNeed(playStreamInfo);
            return;
        }
        startPlayStreamInner(playStreamInfo, false);
    }

    /**
     * @param playStreamInfo 拉流信息
     */
    public void startPlayStreamRtc(UserStreamInfo playStreamInfo) {
        if (mPlayingStreamInfos.contains(playStreamInfo)) {
            LogUtils.d(TAG, "startPlayRtcStream playStreamInfo has already playing");
            return;
        }
        startPlayStreamInner(playStreamInfo, false);
    }

    /**
     * 停止指定拉流
     *
     * @param playStreamInfo 拉流信息
     */
    public void stopPlayStream(UserStreamInfo playStreamInfo) {
        LogUtils.i(TAG, "stopPlayStream playStreamInfo: " + playStreamInfo);
        if (playStreamInfo == null) {
            return;
        }
        stopPlayStreamInner(playStreamInfo);
    }

    /**
     * 如果当前在推 rtc 流，则将 rtc 流转推到 targetURL。
     * <p>
     * 转推也会在 joinLive 和 leaveLive 的时候停止。
     *
     * @param targetURL 指定转推的 CDN 地址，null 或者 空字符串表示停止当前转推
     */
    public void setPublishCdnUrl(String targetURL) {
        LogUtils.i(TAG, "setPublishCdnUrl targetURL: " + targetURL + ", mPublishTargetUrl: " + mPublishTargetUrl + ", mPublishStreamInfoRTC: " + mPublishStreamInfoRTC);
        if (targetURL == null && mPublishTargetUrl == null) {
            LogUtils.w(TAG, "setPublishCdnUrl targetURL is null"); // 现在，期望都没有转推，所以不执行操作
            return;
        }

        if (targetURL != null && targetURL.equals(mPublishTargetUrl)) {
            // 如果是支持优先级，那么转推地址一样表示更新优先级，需要更新，否则直接返回
            LogUtils.w(TAG, "setPublishCdnUrl mPublishTargetUrl have not changed! mPublishTargetUrl: " + mPublishTargetUrl);
            return;
        } else {
            // 如果地址不一样，先停止转推
            // 由于这里的设置逻辑，只支持一个转推输出。所以有这个逻辑。
            removePublishCdnUrlIfNeed();
        }

        // 如果之前没有转推，或者现在没有推实时流，则直接返回
        if (TextUtils.isEmpty(targetURL) || mPublishStreamInfoRTC == null) {
            LogUtils.w(TAG, "setPublishCdnUrl is not publishing rtc");
            return;
        }

        String streamID = mPublishStreamInfoRTC.target;
        mExpressEngine.addPublishCdnUrl(mPublishStreamInfoRTC.target, targetURL, new IZegoPublisherUpdateCdnUrlCallback() {
            @Override
            public void onPublisherUpdateCdnUrlResult(int error) {
                LogUtils.d(TAG, "setPublishCdnUrl onPublisherUpdateCdnUrlResult streamID: " + streamID + ", targetUrl: " + targetURL + ", error: " + error);
            }
        });
        mPublishTargetUrl = targetURL;
    }

    /**
     * 禁止掉本地推流中 音频数据
     */
    public void mutePublishStreamAudio(boolean mute) {
        LogUtils.i(TAG, "mutePublishStreamAudio mute: " + mute);
        mExpressEngine.mutePublishStreamAudio(mute, ZegoPublishChannel.MAIN);
        mExpressEngine.mutePublishStreamAudio(mute, ZegoPublishChannel.AUX);
        mExpressEngine.mutePublishStreamAudio(mute, ZegoPublishChannel.THIRD);
        mExpressEngine.mutePublishStreamAudio(mute, ZegoPublishChannel.FOURTH);
    }

    /**
     * 暂时支持对播放器流静音
     */
    public void mutePlayStreamAudio(UserStreamInfo playStreamInfo, boolean mute) {
        String streamID = getStreamID(playStreamInfo);
        if (!TextUtils.isEmpty(streamID)) {
            mExpressEngine.mutePlayStreamAudio(streamID, mute);
        }
    }

    /**
     * 禁用本地麥克風設備
     */
    public void enableAudioCaptureDevice(boolean enable) {
        LogUtils.i(TAG, "enableAudioCaptureDevice enable: " + enable);
        mExpressEngine.enableAudioCaptureDevice(enable);
    }


    public void setMixerConfig(MixerConfig config) {
        LogUtils.i(TAG, "setMixerConfig config: " + config);
        mMixerConfig = config;
        updateMixerTaskIfNeed();
    }

    /**
     * 主动更新混流参数
     */
    public void updateMixerTaskConfig(MixerConfig config) {
        LogUtils.i(TAG, "updateMixerTaskConfig config: " + config);
        mMixerConfig = config;
        updateMixerTask();
    }

    /**
     * 开/关噪声抑制
     *
     * @param enable 开关
     */
    public void enableANS(boolean enable) {
        LogUtils.i(TAG, "enableANS ");
        mExpressEngine.enableANS(enable);
        mExpressEngine.setANSMode(ZegoANSMode.AI);
    }

    /**
     * 开始语音的稳态检测
     *
     * @param monitorType 语音检测器类型。
     */
    public void startAudioVADStableStateMonitor(ZegoAudioVADStableStateMonitorType monitorType) {
        LogUtils.i(TAG, "startAudioVADStableStateMonitor ");
        mExpressEngine.startAudioVADStableStateMonitor(monitorType, 500);
    }

    /**
     * 停止语音的稳态检测。
     *
     * @param monitorType 语音检测器类型。
     */
    public void stopAudioVADStableStateMonitor(ZegoAudioVADStableStateMonitorType monitorType) {
        LogUtils.i(TAG, "stopAudioVADStableStateMonitor ");
        mExpressEngine.stopAudioVADStableStateMonitor(monitorType);
    }

    /**
     * 设置混流输出的地址
     *
     * @param mixerTargetUrl 混流输出的地址
     */
    public void setMixerTargetUrl(String mixerTargetUrl) {
        LogUtils.i(TAG, "setMixerTargetUrl mixerTargetUrl: " + mixerTargetUrl + ", mMixerTargetUrl: " + mMixerTargetUrl);
        if ((mixerTargetUrl == null && mMixerTargetUrl == null)) {
            LogUtils.d(TAG, "setMixerTargetUrl targetUrl is null!");
            return;
        }

        if (mixerTargetUrl != null && mixerTargetUrl.equals(mMixerTargetUrl)) {
            // 如果是支持优先级，那么混流输入地址一样表示更新优先级，需要更新
            LogUtils.w(TAG, "setMixerTargetUrl mMixerTargetUrl have not changed! mMixerTargetUrl: " + mMixerTargetUrl);
            return;
        } else {
            // 如果地址不一样，先停止混流
            // 由于这里的设置逻辑，只支持一个混流输出。所以有这个逻辑。
            stopMixerTaskIfNeed();
        }

        if (TextUtils.isEmpty(mixerTargetUrl)) {
            return;
        }
        mMixerTargetUrl = mixerTargetUrl;
        mMixerTargetUrlWithPriority = genPriorityUrl(mMixerTargetUrl);
        updateMixerTaskIfNeed();
    }

    /**
     * 更新混流
     */
    private void updateMixerTask() {
        LogUtils.d(TAG, "updateMixerTask mMixerTargetUrl: " + mMixerTargetUrl + ", mMixerConfig: " + mMixerConfig +
                ", mPlayingStreamInfos: " + mPlayingStreamInfos +
                ", mPublishStreamInfoRTC: " + mPublishStreamInfoRTC);
        if (mMixerTargetUrl.isEmpty()) {
            return;
        }
        final String mixerTargetUrl = mMixerTargetUrl;
        ZegoMixerTask mixerTask = new ZegoMixerTask(mixerTargetUrl);
        mixerTask.setAudioConfig(mMixerConfig.audioConfig);
        mixerTask.setVideoConfig(mMixerConfig.videoConfig);
        mixerTask.setWatermark(mMixerConfig.mZegoWatermark);
        mixerTask.setInputList(getValidMixerInputList(mMixerConfig.inputList));
        if (!TextUtils.isEmpty(mMixerConfig.backgroundImageURL)) {
            mixerTask.setBackgroundImageURL(mMixerConfig.backgroundImageURL);
        }

        final String outputTarget = mMixerTargetUrl;
        ZegoMixerOutput zegoMixerOutput = new ZegoMixerOutput(outputTarget);
        ArrayList<ZegoMixerOutput> outputList = new ArrayList<>();
        outputList.add(zegoMixerOutput);
        mixerTask.setOutputList(outputList);

        mixerTask.setBackgroundColor(mMixerConfig.backgroundColor);
        mExpressEngine.startMixerTask(mixerTask, new IZegoMixerStartCallback() {
            @Override
            public void onMixerStartResult(int error, JSONObject extendedData) {
                LogUtils.d(TAG, "startMixerTask onMixerStartResult mixerTaskID: " + mixerTargetUrl + ", targetUrl: " + outputTarget + ", error: " + error + ", extendedData: " + extendedData);
            }
        });
    }

    /**
     * 265 解码
     */
    public int is265DecoderSupport() {
//        if (mExpressEngine.isVideoDecoderSupported(ZegoVideoCodecID.H265)){
//            return 1;
//        } else {
        return 0;
//        }
    }

    /**
     * 265 编码
     *
     * @return
     */
    public int is265EncoderSupport() {
//        if (mExpressEngine.isVideoEncoderSupported(ZegoVideoCodecID.H265)){
//            return 1;
//        } else {
        return 0;
//        }
    }

    /**
     * 获取流优先级参数
     */
    private String genPriorityUrl(String publishTargetUrl) {
        if (publishTargetUrl.contains("?")) {
            return publishTargetUrl + "&wsPRI=" + System.currentTimeMillis();
        } else {
            return publishTargetUrl + "?wsPRI=" + System.currentTimeMillis();
        }
    }

    /**
     * 在特定时机 更新混流  --> 主动更新、流新增删除
     */
    private void updateMixerTaskIfNeed() {
        LogUtils.d(TAG, "updateMixerTaskIfNeed mMixerTargetUrl: " + mMixerTargetUrl + ", mMixerConfig: " + mMixerConfig);
        if (mMixerConfig == null || mMixerConfig.inputList.isEmpty() || TextUtils.isEmpty(mMixerTargetUrl) || mUiHandler.hasMessages(MSG_WHAT_STOP_MIX_TASK)) {
            // 没有指定混流参数或者混流输入参数为空，或者没有输出路径，或者现在在异步移除混流认为中，则直接返回
            return;
        }

        updateMixerTask();
    }

    /**
     * 根据 MixerInput 找出需要混流的streamID
     */
    // 根据期望的混流输入，在当前播放的流列表中查找，只有在流列表里面的，才执行混流
    // 因为 ZEGO 只能对存在的流进行混流，如果首次执行任务的时候，对不存在的流进行混流，那么任务将会失败，所以这里需要找出存在的流。
    private ArrayList<ZegoMixerInput> getValidMixerInputList(List<MixerConfig.MixerInput> expectMixerInputList) {
        ArrayList<ZegoMixerInput> playingInputList = new ArrayList<>();
        for (MixerConfig.MixerInput expectInput : expectMixerInputList) {
            String streamID;
            if (mPublishStreamInfoRTC != null && mPublishStreamInfoRTC.userID == expectInput.uid) {
                streamID = mPublishStreamInfoRTC.target;
            } else {
                streamID = getStreamIDByUidIfPlaying(expectInput.uid);
            }

            if (TextUtils.isEmpty(streamID)) {
                continue;
            }

            ZegoMixerInput zegoMixerInput = new ZegoMixerInput(streamID, expectInput.contentType, expectInput.layout, expectInput.soundLevelID);
            playingInputList.add(zegoMixerInput);
        }

        return playingInputList;
    }

    /**
     * 在推流中获取截图
     */
    public void takePublishStreamSnapshot(IZegoPublisherTakeSnapshotCallback mIZegoPublisherTakeSnapshotCallback) {
        mExpressEngine.takePublishStreamSnapshot(mIZegoPublisherTakeSnapshotCallback);
    }

    /**
     * 切换镜像
     */
    public void switchMirrorMode(Boolean isChecked) {
        if (mCameraCapture.getFront() == 0) {
            //切换到后置
            if (isChecked) {
                setVideoMirrorMode(ZegoVideoMirrorMode.ONLY_PUBLISH_MIRROR);
            } else {
                setVideoMirrorMode(ZegoVideoMirrorMode.NO_MIRROR);
            }
        } else {
            //切换到前置
            if (isChecked) {
                setVideoMirrorMode(ZegoVideoMirrorMode.ONLY_PREVIEW_MIRROR);
            } else {
                setVideoMirrorMode(ZegoVideoMirrorMode.BOTH_MIRROR);
            }
        }
    }

    /**
     * 切换摄像头
     */
    public void switchCamera() {
        mCameraCapture.switchCamera();
        boolean isSetMirrorMode = (boolean) SPUtils.get(Utils.getApp(), User.get().getUserId() + Constant.KEY_VIDEO_MIRROR_MODE_VALUE, false);
        switchMirrorMode(isSetMirrorMode);
    }

    // TODO 通过 SDK 的能力来实现。
    public void addColorWatermark(int color) {
        mCameraCapture.addColorWatermark(color);
    }

    public void removeColorWatermark() {
        mCameraCapture.removeColorWatermark();
    }
    // ------------- 相机能力 -------------- //

    /**
     * 根据UID 获取 流ID 当UID不存在是置为空
     */
    private String getStreamIDByUidIfPlaying(long uid) {
        for (UserStreamInfo userStreamInfo : mPlayingStreamInfos) {
            if (userStreamInfo.userID == uid) {
                return getStreamID(userStreamInfo);
            }
        }
        return null;
    }


    /**
     * 根据UserStreamInfo 生成 流ID
     */
    private String getStreamID(UserStreamInfo userStreamInfo) {
        return userStreamInfo.streamType == StreamType.RTC ? userStreamInfo.target : getCDNStreamIDUserStreamInfo(userStreamInfo);
    }

    /**
     * 根据 streamID 获取 UserStreamInfo
     */
    private UserStreamInfo getPlayingStreamInfoByStreamID(String streamID) {
        for (UserStreamInfo playingStreamInfo : mPlayingStreamInfos) {
            if (getStreamID(playingStreamInfo).equals(streamID)) {
                return playingStreamInfo;
            }
        }
        return null;
    }

    /**
     * ZegoStream
     *
     * @param zegoStreamList 转换成 UserStreamInfo
     */
    private List<UserStreamInfo> parseToStreamInfo(List<ZegoStream> zegoStreamList) {
        List<UserStreamInfo> streamInfos = new ArrayList<>(zegoStreamList.size());
        for (ZegoStream zegoStream : zegoStreamList) {
            if (STREAM_EXTRA_INFO_CDN_TAG.equals(zegoStream.extraInfo)) {
                continue; // CDN 流不会在 onRoomStreamUpdate 中进行处理，如果保证拉流器加入的房间ID都是伪造的房间ID，那么是不会出现这个问题的。
            }
            UserStreamInfo streamInfo = new UserStreamInfo(Long.parseLong(zegoStream.user.userID), zegoStream.streamID, StreamType.RTC);
            streamInfos.add(streamInfo);
        }
        return streamInfos;
    }

    private void removePublishCdnUrlIfNeed() {
        LogUtils.d(TAG, "removePublishCdnUrlIfNeed mPublishTargetUrl: " + mPublishTargetUrl + ", mPublishStreamInfoRTC: " + mPublishStreamInfoRTC);
        String publishTargetUrl = mPublishTargetUrl;
        mPublishTargetUrl = null;
        // 如果之前没有转推，或者现在没有推实时流，则直接返回
        if (TextUtils.isEmpty(publishTargetUrl) || mPublishStreamInfoRTC == null) {
            return;
        }

        String streamID = mPublishStreamInfoRTC.target;

        // 这里只需要关 rtc streamID，所以不做任何转换
        mExpressEngine.removePublishCdnUrl(streamID, publishTargetUrl, new IZegoPublisherUpdateCdnUrlCallback() {
            @Override
            public void onPublisherUpdateCdnUrlResult(int error) {
                LogUtils.d(TAG, "removePublishCdnUrl onPublisherUpdateCdnUrlResult streamID: " + streamID + ", targetUrl: " + publishTargetUrl + ", error: " + error);
            }
        });
    }

    /**
     * 触发延迟停止混流逻辑
     */
    public void triggerStopMixerDelayTask() {
        // 对于观众拉两路 RTMP 流的 单播PK 切换优化方案，其实下面的延迟逻辑使跟优先级开关没什么关系。
        // 这里仅仅是以该开关统一管理该优化策略。
        stopMixerTaskIfNeed();
    }

    private void triggerRemovePublishCdnUrl() {
        removePublishCdnUrlIfNeed();
    }

    private void stopMixerTaskIfNeed() {
        LogUtils.d(TAG, "stopMixerTaskIfNeed mMixerTargetUrl: " + mMixerTargetUrl);
        String mixerTaskID = mMixerTargetUrl;
        mMixerTargetUrl = null;
        mMixerTargetUrlWithPriority = null;

        // 如果现在没有混流任务，直接返回
        if (TextUtils.isEmpty(mixerTaskID)) {
            return;
        }

        ZegoMixerTask mixerTask = new ZegoMixerTask(mixerTaskID);
        mExpressEngine.stopMixerTask(mixerTask, new IZegoMixerStopCallback() {
            @Override
            public void onMixerStopResult(int error) {
                LogUtils.d(TAG, "stopMixerTask onMixerStopResult mixerTaskID: " + mixerTaskID + ", targetUrl: " + mixerTaskID + ", error: " + error);
            }
        });
    }

    private void reset(boolean isClosePreview) {
        mRoomID = null;
        mPublishStreamInfoRTC = null;
        mLocalCanvas = null;
        mPublishTargetUrl = null;
        mMixerTargetUrl = null;
        mMixerTargetUrlWithPriority = null;
        mMixerConfig = null;

        isStartAudioRecord = false;
        isStartAudioRecordForCDNPublish = false;

        isLoginSuccess = false;
        isPreview = isClosePreview;
        mCdnPublishStreamInfoMap.clear();
        mPlayingStreamInfos.clear();
        mRemoteCanvasMap.clear();
        mPlayingStreamStateBeans.clear();
        mEventHandlerMap.clear();

        mUiHandler.removeCallbacksAndMessages(null);

        checkAndSetVideoCaptureState();
    }

    private void reset() {
        reset(false);
    }

    private void joinLiveSuccess(String liveRoomID, long uid) {
        LogUtils.d(TAG, "joinLiveSuccess: liveRoomID: " + liveRoomID + ", uid: " + uid);
        isLoginSuccess = true;
        callOnJoinLiveSuccess(liveRoomID, uid);
    }

    // 登录房间失败
    private void joinLiveFailed(String liveRoomID, long uid, int error) {
        LogUtils.d(TAG, "joinLiveSuccess: liveRoomID: " + liveRoomID + ", uid: " + uid + ", error: " + error);
        // 登录失败，会停止推流和拉流
        reset();
        callOnJoinLiveFailed(liveRoomID, uid, error);
    }

    private void liveDisconnected() {
        reset();
    }

    /**
     * 对当前所有在拉的流 mPlayingStreamInfos 进行检查
     * 如果没有在 needPlayStreamInfos 中，则停止。
     * 如果在 needPlayStreamInfos 中，则回调流的状态
     * 如果 needPlayStreamInfos 中存在，但 mPlayingStreamInfos 中不存在，则触发拉流操作。
     *
     * @param needPlayStreamInfos 要求拉的流列表
     */
    private void checkCdnPlayStreamInfoAndPlay(List<UserStreamInfo> needPlayStreamInfos) {
        LogUtils.d(TAG, "checkCdnPlayStreamInfoAndPlay needPlayStreamInfos: " + needPlayStreamInfos);
        List<UserStreamInfo> copyPlayingStreamInfos = new ArrayList<>(mPlayingStreamInfos);
        for (UserStreamInfo playingStreamInfo : copyPlayingStreamInfos) {
            if (playingStreamInfo.streamType == StreamType.CDN) { // 只对 CDN 流进行停止拉流处理
                boolean hasFound = needPlayStreamInfos.contains(playingStreamInfo);
                if (hasFound) {
                    // 如果要求拉的正在拉流，那么回调当前流的状态
                    callPlayStreamStateIfNeed(playingStreamInfo);
                } else {
                    stopPlayStream(playingStreamInfo);
                }
            } else {
                // RTC 流都需要回调状态
                callPlayStreamStateIfNeed(playingStreamInfo);
            }
        }
        // 此时 mPlayingStreamInfos 只有 rtc 流和需要拉的 CDN流

        // 对不在 mPlayingStreamInfos 流进行拉流
        for (UserStreamInfo needPlayStreamInfo : needPlayStreamInfos) {
            boolean hasFound = mPlayingStreamInfos.contains(needPlayStreamInfo);
            if (!hasFound) {
                startPlayStreamInner(needPlayStreamInfo, false);
            }
        }
    }

    /**
     * 对当前所有在拉的流 mPlayingStreamInfos 进行检查
     * 如果没有在 needPlayStreamInfos 中，则停止。
     * 如果在 needPlayStreamInfos 中，则回调流的状态
     * 如果 needPlayStreamInfos 中存在，但 mPlayingStreamInfos 中不存在，则触发拉流操作。
     *
     * @param needPlayStreamInfos 要求拉的流列表
     */
    private void checkAllPlayStreamInfoAndPlay(List<UserStreamInfo> needPlayStreamInfos) {
        LogUtils.d(TAG, "checkAllPlayStreamInfoAndPlay needPlayStreamInfos: " + needPlayStreamInfos);
        // 确认需要拉的流是否都在拉。
        for (UserStreamInfo needPlayStreamInfo : needPlayStreamInfos) {
            boolean hasFound = mPlayingStreamInfos.contains(needPlayStreamInfo);

            if (!hasFound) {
                startPlayStreamInner(needPlayStreamInfo, false);
            } else {
                callPlayStreamStateIfNeed(needPlayStreamInfo);
            }
        }

        // 如果不需要拉的流，则停止拉流。
        List<UserStreamInfo> copyPlayingStreamInfoList = new ArrayList<>(mPlayingStreamInfos);
        for (UserStreamInfo currentPlayingStreamInfo : copyPlayingStreamInfoList) {
            boolean hasFound = needPlayStreamInfos.contains(currentPlayingStreamInfo);

            if (!hasFound) {
                stopPlayStreamInner(currentPlayingStreamInfo);
            }
        }
        mPlayingStreamInfos.clear();
        mPlayingStreamInfos.addAll(needPlayStreamInfos);
    }

    /**
     * 是否已经渲染了首帧
     *
     * @param playStreamInfo 拉流信息
     * @return 返回指定拉流信息是否已经渲染了首帧
     */
    public boolean isRecvRenderVideoFirstFrame(UserStreamInfo playStreamInfo) {
        PlayStreamStateBean playStreamStateBean = mPlayingStreamStateBeans.get(playStreamInfo);

        LogUtils.d(TAG, "isRecvRenderVideoFirstFrame playStreamInfo: " + playStreamInfo + ", playStreamStateBean: " + playStreamStateBean);

        return playStreamStateBean != null && playStreamStateBean.isRenderVideoFirstFrame;
    }

    // 如果流已经在来了，则检测是否需要将之前的状态给回调出去
    private void callPlayStreamStateIfNeed(UserStreamInfo playStreamInfo) {
        PlayStreamStateBean playStreamStateBean = mPlayingStreamStateBeans.get(playStreamInfo);

        LogUtils.d(TAG, "callPlayStreamStateIfNeed playStreamInfo: " + playStreamInfo + ", playStreamStateBean: " + playStreamStateBean);

        if (playStreamStateBean == null) {
            return;
        }
        if (playStreamStateBean.isRecvAudioFirstFrame) {
            playerRecvAudioFirstFrame(playStreamInfo);
        }
        if (playStreamStateBean.isRecvVideoFirstFrame) {
            playerRecvVideoFirstFrame(playStreamInfo);
        }
        if (playStreamStateBean.isRenderVideoFirstFrame) {
            playerRenderVideoFirstFrame(playStreamInfo);
        }
    }

    private void startPlayStreamList(List<UserStreamInfo> streamInfoListToStart) {
        for (UserStreamInfo streamInfoToStart : streamInfoListToStart) {
            boolean hasFound = mPlayingStreamInfos.contains(streamInfoToStart);

            if (!hasFound) {
                startPlayStreamInner(streamInfoToStart, true);
            }
        }
    }

    private void stopPlayStreamList(List<UserStreamInfo> streamInfoListToStop) {
        for (UserStreamInfo streamInfoToStop : streamInfoListToStop) {
            stopPlayStreamInner(streamInfoToStop);
        }
    }

    private void stopPlayStreamInner(UserStreamInfo playStreamInfo) {
        LogUtils.w(TAG, "stopPlayStreamInner playStreamInfo: " + playStreamInfo);
        mPlayingStreamStateBeans.remove(playStreamInfo);
        boolean isExist = mPlayingStreamInfos.remove(playStreamInfo);
        if (!isExist) {
            LogUtils.w(TAG, "stopPlayStreamInner playStreamInfo: " + playStreamInfo + " is not exist!!");
        }
        if (TextUtils.isEmpty(playStreamInfo.target)) {
            return;
        }
        switch (playStreamInfo.streamType) {
            case RTC:
                mExpressEngine.stopPlayingStream(playStreamInfo.target);
                break;
            case CDN:
                mExpressEngine.stopPlayingStream(getCDNStreamIDUserStreamInfo(playStreamInfo));
                break;
            default:
                break;
        }
    }

    /**
     * @param insertFirst 兼容逻辑，只有通过 onRoomStreamUpdate 的情况下，才为true，让肯定有效的流在列表前面，保证视图设置逻辑有效
     */
    private void startPlayStreamInner(UserStreamInfo playStreamInfo, boolean insertFirst) {
        LogUtils.d(TAG, "startPlayStreamInner playStreamInfo: " + playStreamInfo);
        if (TextUtils.isEmpty(playStreamInfo.target)) {
            return;
        }
        if (insertFirst) {
            mPlayingStreamInfos.add(0, playStreamInfo);
        } else {
            mPlayingStreamInfos.add(playStreamInfo);
        }
        mPlayingStreamStateBeans.put(playStreamInfo, new PlayStreamStateBean());

        ZegoCanvas playCanvas = mRemoteCanvasMap.get(playStreamInfo.userID);

        switch (playStreamInfo.streamType) {
            case RTC:
                mExpressEngine.startPlayingStream(playStreamInfo.target, playCanvas);
                break;
            case CDN:
                ZegoCDNConfig cdnConfig = new ZegoCDNConfig();
                cdnConfig.url = playStreamInfo.target;

                ZegoPlayerConfig playerConfig = new ZegoPlayerConfig();
                playerConfig.cdnConfig = cdnConfig;
                playerConfig.resourceMode = ZegoStreamResourceMode.ONLY_CDN;
                if (null != playCanvas) {
                    Log.e("startPlayingStream", getCDNStreamIDUserStreamInfo(playStreamInfo) + "@@" + playCanvas.toString() + "@@@@@" + cdnConfig.url);
                } else {
                    Log.e("startPlayingStream", "@@@@@" + cdnConfig.url);
                }
                mExpressEngine.startPlayingStream(getCDNStreamIDUserStreamInfo(playStreamInfo), playCanvas, playerConfig);
            default:
                break;
        }
    }

    private void startPublishStreams(UserStreamInfo rtcPublishStream, Map<ZegoPublishChannel, UserStreamInfo> cdnPublishStreamInfos) {
        startPublishStream(rtcPublishStream);
        for (int channelIndexValue = ZegoPublishChannel.AUX.value(); channelIndexValue <= ZegoPublishChannel.FOURTH.value(); channelIndexValue++) {
            ZegoPublishChannel publishChannel = ZegoPublishChannel.getZegoPublishChannel(channelIndexValue);
            startPublishStream(cdnPublishStreamInfos.get(publishChannel));
        }

        startRecordCapturedDataForCDNPublishIfNeed();
        stopRecordCapturedDataForCDNPublishIfNeed();
    }

    private void startPublishStream(UserStreamInfo publishStreamInfo) {
        LogUtils.d(TAG, "startPublishStream publishStreamInfo: " + publishStreamInfo);
        if (publishStreamInfo != null) {
            switch (publishStreamInfo.streamType) {
                case RTC:
                    mPublishStreamInfoRTC = publishStreamInfo;
                    ZegoVideoConfig config = mExpressEngine.getVideoConfig(ZegoPublishChannel.MAIN);
                    if (publishStreamInfo.is265Encoder) {
                        mExpressEngine.enableH265EncodeFallback(true);
                        config.setCodecID(ZegoVideoCodecID.H265);
                    } else {
                        config.setCodecID(ZegoVideoCodecID.DEFAULT);
                    }
                    mExpressEngine.setVideoConfig(config, ZegoPublishChannel.MAIN);
                    mExpressEngine.startPublishingStream(publishStreamInfo.target, ZegoPublishChannel.MAIN);
                    break;
                case CDN:
                    mCdnPublishStreamInfoMap.put(publishStreamInfo.publishChannel, publishStreamInfo);
                    //CDN 开启gopSize和encodeProfile配置
                    mExpressEngine.callExperimentalAPI(getEncoderProfileJsonStr(mEncodeProfile, publishStreamInfo.publishChannel.value()));
                    mExpressEngine.callExperimentalAPI(getKeyFrameIntervalJsonStr(mGopSize, publishStreamInfo.publishChannel.value()));
                    String cdnTargetUrl = publishStreamInfo.target;
                    ZegoCDNConfig cdnConfig = new ZegoCDNConfig();
                    cdnConfig.url = cdnTargetUrl;
                    mExpressEngine.enablePublishDirectToCDN(true, cdnConfig, publishStreamInfo.publishChannel);
                    String cdnPublishStreamID = getCDNStreamIDUserStreamInfo(publishStreamInfo);
                    mExpressEngine.startPublishingStream(cdnPublishStreamID, publishStreamInfo.publishChannel);
                    mExpressEngine.setStreamExtraInfo(STREAM_EXTRA_INFO_CDN_TAG, publishStreamInfo.publishChannel, null);
                    break;
                default:
                    break;
            }
        }
    }

    private String getEncoderProfileJsonStr(int profile, int channel) {
        JSONObject methodObj = new JSONObject();
        try {
            methodObj.put("method", "express.video.set_video_encoder_profile");
            JSONObject paramObj = new JSONObject();
            paramObj.put("profile", profile);
            paramObj.put("channel", channel);
            methodObj.put("params", paramObj);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return methodObj.toString();
    }

    private String getKeyFrameIntervalJsonStr(int second, int channel) {
        JSONObject methodObj = new JSONObject();
        try {
            methodObj.put("method", "express.video.set_video_key_frame_interval");
            JSONObject paramObj = new JSONObject();
            paramObj.put("interval_in_second", second);
            paramObj.put("channel", channel);
            methodObj.put("params", paramObj);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return methodObj.toString();
    }

    private String getCDNStreamIDUserStreamInfo(UserStreamInfo cdnPlayStreamInfo) {
        if (null == cdnPlayStreamInfo || null == cdnPlayStreamInfo.target) {
            return "";
        }
        int lastSlashIndex = cdnPlayStreamInfo.target.lastIndexOf('/');
        int lastQuestionMark = cdnPlayStreamInfo.target.lastIndexOf('?');
        String streamIDPostfix;
        if (lastSlashIndex == -1 || lastSlashIndex == cdnPlayStreamInfo.target.length() - 1) {
            streamIDPostfix = cdnPlayStreamInfo.userID + "";
        } else {
            if (lastQuestionMark == -1 || lastQuestionMark < lastSlashIndex) {
                streamIDPostfix = cdnPlayStreamInfo.target.substring(lastSlashIndex + 1);
            } else {
                streamIDPostfix = cdnPlayStreamInfo.target.substring(lastSlashIndex + 1, lastQuestionMark);
            }
        }
        return CDN_STREAM_ID_PREFIX + streamIDPostfix;
    }


    private void callOnJoinLiveSuccess(final String liveRoomID, final long uid) {
        for (ZegoEngineEventHandler engineEventHandler : mEventHandlerMap.values()) {
            if (engineEventHandler != null) {
                mUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        engineEventHandler.onJoinLiveSuccess(liveRoomID, uid);
                    }
                });
            }
        }
    }

    private void callOnJoinLiveFailed(final String liveRoomID, final long uid, final int error) {
        for (ZegoEngineEventHandler engineEventHandler : mEventHandlerMap.values()) {
            if (engineEventHandler != null) {
                mUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        engineEventHandler.onJoinLiveFailed(liveRoomID, uid, error);
                    }
                });
            }
        }
    }

    private void publisherQualityUpdate(long streamID, ZegoPublishStreamQuality quality) {
        for (ZegoEngineEventHandler engineEventHandler : mEventHandlerMap.values()) {
            if (engineEventHandler != null) {
                mUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        engineEventHandler.onPublisherQualityUpdate(streamID, quality);
                    }
                });
            }
        }
    }

    private void leaveLive(String liveRoomID, long uid) {
        for (ZegoEngineEventHandler engineEventHandler : mEventHandlerMap.values()) {
            if (engineEventHandler != null) {
                mUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        engineEventHandler.onLeaveLive(liveRoomID, uid);
                    }
                });
            }
        }
    }

    private void publisherCapturedVideoFirstFrame() {
        for (ZegoEngineEventHandler engineEventHandler : mEventHandlerMap.values()) {
            if (engineEventHandler != null) {
                mUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        engineEventHandler.onPublisherCapturedVideoFirstFrame();
                    }
                });
            }
        }
    }

    private void callOnPlayerPlayFailed(UserStreamInfo playerStreamInfo, int error) {
        if (playerStreamInfo == null) {
            return;
        }
        for (ZegoEngineEventHandler engineEventHandler : mEventHandlerMap.values()) {
            if (engineEventHandler != null) {
                mUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        engineEventHandler.onPlayerPlayFailed(playerStreamInfo, error);
                    }
                });
            }
        }
    }

    private void playerRecvAudioFirstFrame(UserStreamInfo playerStreamInfo) {
        if (playerStreamInfo == null) {
            return;
        }

        PlayStreamStateBean playStreamStateBean = mPlayingStreamStateBeans.get(playerStreamInfo);
        if (playStreamStateBean != null) {
            playStreamStateBean.isRecvAudioFirstFrame = true;
        }

        for (ZegoEngineEventHandler engineEventHandler : mEventHandlerMap.values()) {
            if (engineEventHandler != null) {
                mUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        engineEventHandler.onPlayerRecvAudioFirstFrame(playerStreamInfo);
                    }
                });
            }
        }
    }

    private void playerRecvVideoFirstFrame(UserStreamInfo playerStreamInfo) {
        if (playerStreamInfo == null) {
            return;
        }
        PlayStreamStateBean playStreamStateBean = mPlayingStreamStateBeans.get(playerStreamInfo);
        if (playStreamStateBean != null) {
            playStreamStateBean.isRecvVideoFirstFrame = true;
        }

        for (ZegoEngineEventHandler engineEventHandler : mEventHandlerMap.values()) {
            if (engineEventHandler != null) {
                mUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        engineEventHandler.onPlayerRecvVideoFirstFrame(playerStreamInfo);
                    }
                });
            }
        }
    }

    private void playerRenderVideoFirstFrame(UserStreamInfo playerStreamInfo) {
        LogUtils.d(TAG, "playerRenderVideoFirstFrame playerStreamInfo: " + playerStreamInfo + ", mEventHandlerMap: " + mEventHandlerMap);
        if (playerStreamInfo == null) {
            return;
        }
        PlayStreamStateBean playStreamStateBean = mPlayingStreamStateBeans.get(playerStreamInfo);
        if (playStreamStateBean != null) {
            playStreamStateBean.isRenderVideoFirstFrame = true;
        }

        for (Map.Entry<String, ZegoEngineEventHandler> eventHandlerEntry : mEventHandlerMap.entrySet()) {
            ZegoEngineEventHandler engineEventHandler = eventHandlerEntry.getValue();
            if (engineEventHandler != null) {
                mUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        engineEventHandler.onPlayerRenderVideoFirstFrame(playerStreamInfo);
                    }
                });
            }
        }
    }

    /**
     * 本地采集的声浪值
     */
    private void capturedSoundLevelUpdate(float soundLevel) {
        for (ZegoEngineEventHandler engineEventHandler : mEventHandlerMap.values()) {
            if (engineEventHandler != null) {
                mUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        engineEventHandler.onCapturedSoundLevelUpdate(soundLevel);
                    }
                });
            }
        }
    }

    /**
     * 远端采集的声浪值
     */
    private void remoteSoundLevelUpdate(HashMap<String, Float> soundLevels) {
        HashMap<Long, Float> sounds = new HashMap<>();
        for (Map.Entry<String, Float> entry : soundLevels.entrySet()) {
            UserStreamInfo info;
            if (null != entry.getKey() && (info = getPlayingStreamInfoByStreamID(entry.getKey())) != null) {
                sounds.put(info.getUserID(), entry.getValue());
            }
        }

        for (ZegoEngineEventHandler engineEventHandler : mEventHandlerMap.values()) {
            if (engineEventHandler != null) {
                mUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        engineEventHandler.onRemoteSoundLevelUpdate(sounds);
                    }
                });
            }
        }
    }

    /**
     * 混流多路流监听
     */
    private void mixerSoundLevelUpdate(HashMap<Integer, Float> soundLevels) {
        for (ZegoEngineEventHandler engineEventHandler : mEventHandlerMap.values()) {
            if (engineEventHandler != null) {
                mUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        engineEventHandler.onMixerSoundLevelUpdate(soundLevels);
                    }
                });
            }
        }
    }

    /**
     * 监听接收 SEI 信息的回调, 当发送端调用 sendSEI 发送信息时会触发此回调
     */
    private void playerRecvSEI(String streamID, byte[] data) {
        for (ZegoEngineEventHandler engineEventHandler : mEventHandlerMap.values()) {
            if (engineEventHandler != null) {
                mUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (null != data && !TextUtils.isEmpty(streamID)) {
                            engineEventHandler.onPlayerRecvSEI(streamID, data);
                        }
                    }
                });
            }
        }
    }

    /**
     * 开始语音的稳态检测
     */
    private void onStartAudioVADStableStateMonitor(ZegoAudioVADStableStateMonitorType zegoAudioVADStableStateMonitorType, ZegoAudioVADType zegoAudioVADType) {
        for (ZegoEngineEventHandler engineEventHandler : mEventHandlerMap.values()) {
            if (engineEventHandler != null) {
                mUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        engineEventHandler.onStartAudioVADStableStateMonitor(zegoAudioVADStableStateMonitorType);
                    }
                });
            }
        }
    }

    @Override
    public void consumeByteArrayFrame(byte[] data, int format, int width, int height, int rotation, long timestamp) {
        if (mCaptureDataByteBuffer == null || mCaptureDataByteBuffer.capacity() < data.length) {
            mCaptureDataByteBuffer = ByteBuffer.allocateDirect(data.length);
        }

        mCaptureDataByteBuffer.position(0);
        mCaptureDataByteBuffer.put(data);

        ZegoVideoFrameParam frameParam = new ZegoVideoFrameParam();
        frameParam.width = width;
        frameParam.height = height;
        frameParam.rotation = rotation;
        // 对齐规则 -> 一次性读一行有多少个像素
        // NV21，Y平面一次性对齐 width 个像素
        // UV 平面，由于 UV 交差摆放，所以也是 width 个像素（U和V分别是Y的一半，基于 width 来说）
        frameParam.strides[0] = width;
        frameParam.strides[1] = width;
        frameParam.format = ZegoVideoFrameFormat.getZegoVideoFrameFormat(format);

        if (isStartAudioRecord || mPublishStreamInfoRTC != null) {
            mExpressEngine.sendCustomVideoCaptureRawData(mCaptureDataByteBuffer, data.length, frameParam, timestamp, ZegoPublishChannel.MAIN);
        }

        // 预览通过辅路流进行，因此预览需要不断输入数据
        mExpressEngine.sendCustomVideoCaptureRawData(mCaptureDataByteBuffer, data.length, frameParam, timestamp, ZegoPublishChannel.AUX);

        if (!mCdnPublishStreamInfoMap.isEmpty()) {
            for (int channelIndexValue = ZegoPublishChannel.THIRD.value(); channelIndexValue <= ZegoPublishChannel.FOURTH.value(); channelIndexValue++) {
                ZegoPublishChannel publishChannel = ZegoPublishChannel.getZegoPublishChannel(channelIndexValue);
                if (mCdnPublishStreamInfoMap.containsKey(publishChannel)) { // 这里有线程安全问题
                    mExpressEngine.sendCustomVideoCaptureRawData(mCaptureDataByteBuffer, data.length, frameParam, timestamp, publishChannel);
                }
            }
        }
    }

    @Override
    public void consumeByteBufferFrame(ByteBuffer buffer, int format, int width, int height, int rotation, long timestamp) {
        // DO NOTHING
    }

    @Override
    public void consumeTextureFrame(int textureId, int format, int width, int height, int rotation, long timestamp, float[] matrix) {
        // DO NOTHING
    }

    /**
     * 用户流信息
     */
    public static class UserStreamInfo {
        private final long userID;
        /**
         * 对于 RTC，target 为 StreamID
         * 对应 CDN，target 为 URL
         */
        private final String target;
        private final StreamType streamType;
        /**
         * 推流通道，不设置默认为 aux 通道
         */
        private ZegoPublishChannel publishChannel;
        private boolean is265Encoder;
        private boolean is265Decoder;

        public UserStreamInfo(long userID, String target, StreamType streamType) {
            this.userID = userID;
            this.target = target;
            this.streamType = streamType;
        }

        /**
         * 设置推流通道，只能设置 AUX、THIRD、FOURTH 通道，不设置默认为 aux 通道
         */
        public void setPublishChannel(ZegoPublishChannel publishChannel) {
            if (publishChannel == ZegoPublishChannel.MAIN) {
                // 不能设置为 main 通道
                return;
            }
            this.publishChannel = publishChannel;
        }

        public ZegoPublishChannel getPublishChannel() {
            return publishChannel;
        }

        public boolean is265Encoder() {
            return is265Encoder;
        }

        public void set265Encoder(boolean is265Encoder) {
            this.is265Encoder = is265Encoder;
        }

        public boolean is265Decoder() {
            return is265Decoder;
        }

        public void set265Decoder(boolean is265Decoder) {
            this.is265Decoder = is265Decoder;
        }

        /**
         * equals 方法，仅对重要信息，userID、target 和 streamType，并没有对 publishChannel 进行判断。因此在使用 equals 方法进行判断的时候需要注意。
         */
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            UserStreamInfo that = (UserStreamInfo) o;
            return userID == that.userID && TextUtils.equals(target, that.target) &&
                    streamType == that.streamType;
        }

        @Override
        public int hashCode() {
            return Objects.hash(userID, target, streamType);
        }

        @Override
        public String toString() {
            return "UserStreamInfo{" +
                    "userID=" + userID +
                    ", target='" + target + '\'' +
                    ", streamType=" + streamType +
                    ", publishChannel=" + publishChannel +
                    ", is265Encoder=" + is265Encoder +
                    '}';
        }

        public static boolean isInvalid(UserStreamInfo userStreamInfo) {
            return userStreamInfo == null || userStreamInfo.userID < 0 || TextUtils.isEmpty(userStreamInfo.target);
        }

        public long getUserID() {
            return userID;
        }

        public StreamType getStreamType() {
            return streamType;
        }

        public String getTarget() {
            return target;
        }
    }

    public enum StreamType {
        RTC,
        CDN
    }

    public static String getPublishStreamIDByUserID(String roomId, long usrID) {
        return MD5Utils.md5(roomId) + "-" + usrID;
    }

    boolean hasSoLoaded = false;

    private void ensureSoLoaded() {
        // 3. 判断 so 库是否已损坏
        try {
            System.loadLibrary("ZegoExpressEngine");
            hasSoLoaded = true;
        } catch (UnsatisfiedLinkError var1) {
            // 注意：此处请进行上报：System load ZegoExpressSDK native library failed，var1.getMessage()
            AnalyticsLog.INSTANCE.reportZegoErrorInfo("System load ZegoExpressSDK native library failed" + var1.getMessage());
            hasSoLoaded = false;
        }

        // 4. 若 so 库加载失败尝试使用其他方法进行加载
        if (!hasSoLoaded) {
            try {
                hasSoLoaded = ZegoLibraryLoadUtil.loadSoFile("libZegoExpressEngine.so", BaseApplication.getInstance().getApplicationContext());
            } catch (UnsatisfiedLinkError var2) {
                // 注意：此处请进行上报：Util load library libZegoExpressEngine.so failed: UnsatisfiedLinkError，var2.getMessage()
                AnalyticsLog.INSTANCE.reportZegoErrorInfo("Util load library libZegoExpressEngine.so failed: UnsatisfiedLinkError" + var2.getMessage());
            } catch (Exception var3) {
                // 注意：此处请进行上报：Util load library libZegoExpressEngine.so failed: Exception，var3.getMessage()
                AnalyticsLog.INSTANCE.reportZegoErrorInfo("Util load library libZegoExpressEngine.so failed: Exception" + var3.getMessage());
            }
        }

    }

    /**
     * 切换到蓝牙
     */
    public static void changeToHeadset() {
        AudioManager mAudioManager = (AudioManager) BaseApplication.getInstance().getSystemService(Context.AUDIO_SERVICE);
        mAudioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        mAudioManager.startBluetoothSco();
        mAudioManager.setBluetoothScoOn(true);
        mAudioManager.setSpeakerphoneOn(false);
    }
}