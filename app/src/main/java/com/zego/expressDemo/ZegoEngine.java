package com.zego.expressDemo;

import android.content.Context;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import com.zego.expressDemo.application.BaseApplication;
import com.zego.expressDemo.bean.GameStreamEntity;
import com.zego.expressDemo.bean.MixerConfig;
import com.zego.expressDemo.bean.PlayStreamStateBean;
import com.zego.expressDemo.bean.ZegoVideoCanvas;
import com.zego.expressDemo.config.GlobalType;
import com.zego.expressDemo.config.JavaGlobalConfig;
import com.zego.expressDemo.data.Constant;
import com.zego.expressDemo.data.User;
import com.zego.expressDemo.data.ZegoDataCenter;
import com.zego.expressDemo.filter.STFilter;
import com.zego.expressDemo.utils.AnalyticsLog;
import com.zego.expressDemo.utils.JsonUtil;
import com.zego.expressDemo.utils.LogUtils;
import com.zego.expressDemo.utils.MD5Utils;
import com.zego.expressDemo.utils.SPUtils;
import com.zego.expressDemo.utils.Utils;
import com.zego.expressDemo.videocapture.IZegoVideoFrameConsumer;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import im.zego.zegoexpress.ZegoExpressEngine;
import im.zego.zegoexpress.callback.IZegoAudioDataHandler;
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
import im.zego.zegoexpress.constants.ZegoCapturePipelineScaleMode;
import im.zego.zegoexpress.constants.ZegoDataRecordState;
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
import im.zego.zegoexpress.constants.ZegoVideoSourceType;
import im.zego.zegoexpress.entity.ZegoAudioConfig;
import im.zego.zegoexpress.entity.ZegoAudioFrameParam;
import im.zego.zegoexpress.entity.ZegoCDNConfig;
import im.zego.zegoexpress.entity.ZegoCanvas;
import im.zego.zegoexpress.entity.ZegoCustomVideoProcessConfig;
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
 * 3、统一通过主路流的预览能力来实现预览。
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
 * <p>
 * TODO
 * 6、确认切换同一个房间，是否需要停止转推。
 * 7、确认 public void startPublishRTCStream 这个接口存在的意义
 * <p>
 * 测试内容：
 * 1、切换摄像头
 * 2、水印功能
 * 3、美颜功能
 * 4、前后台切换
 * 5、分辨率切换
 * 6、AudioDataObserver
 * 7、动态转推
 * 8、切换摄像头
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

    public static final long TIME_KEEP_STREAM_PUBLISH_IN_MILLS = 3000;

    private static final ZegoAudioSampleRate SAMPLE_RATE_AUDIO_CAPTURE_DATA_OBSERVER = ZegoAudioSampleRate.ZEGO_AUDIO_SAMPLE_RATE_16K;
    private static final ZegoAudioChannel CHANNEL_COUNT_AUDIO_CAPTURE_DATA_OBSERVER = ZegoAudioChannel.MONO;

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

    private final Handler mUiHandler = new Handler(Looper.getMainLooper());

    private final ZegoExpressEngine mExpressEngine;
    private ByteBuffer mCaptureDataByteBuffer;
    private final Map<String, ZegoEngineEventHandler> mEventHandlerMap;

    private boolean isLoginSuccess;
    private String mRoomID; // 当前登录的房间，null 表示为登录房间
    private UserStreamInfo mPublishStreamInfoRTC; // 当前 RTC 推流信息，使用主路流推 RTC 流
    private UserStreamInfo mPublishStreamInfoCDN;  // 当前 CDN 推流的信息，使用 辅路流推 CDN 流
    private final List<UserStreamInfo> mPlayingStreamInfos;
    private final Map<UserStreamInfo, PlayStreamStateBean> mPlayingStreamStateBeans;

    private boolean isPreview;
    private boolean isEnableCamera = true;
    private boolean isFontCamera = true;
    private boolean isEnableAudioCaptureDevice = true;
    private boolean isStartAudioCaptureDataObserverInternal = false;
    private boolean isStartAudioCaptureDataObserverExternal = false;

    private ZegoCanvas mLocalCanvas;
    private final Map<Long, WeakReference<ZegoCanvas>> mRemoteCanvasMap;

    private MixerConfig mMixerConfig;
    private String mMixerTargetUrl;
    private String mMixerTargetUrlWithPriority;
    private final List<String> mPublishTargetUrlList;
    private int mEncodeProfile = 0; // 编码器配置，默认为0，baseline
    private int mGopSize = 2; // sdk 默认的gop size 为2

    private ZegoEngine() {
        LogUtils.i(TAG, "ZegoEngine() init");
        mEventHandlerMap = new HashMap<>();
        mPlayingStreamInfos = new ArrayList<>();
        mRemoteCanvasMap = new HashMap<>();
        mPlayingStreamStateBeans = new HashMap<>();
        mPublishTargetUrlList = new ArrayList<>();

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

        // 由于辅路复用主路内容，所以这里使用高清预览，避免主路的缩放影响主路
        mExpressEngine.setCapturePipelineScaleMode(ZegoCapturePipelineScaleMode.POST);

        ZegoCustomVideoProcessConfig customVideoProcessConfig = new ZegoCustomVideoProcessConfig();
        customVideoProcessConfig.bufferType = ZegoVideoBufferType.GL_TEXTURE_2D;
        mExpressEngine.enableCustomVideoProcessing(true, customVideoProcessConfig);
        mExpressEngine.setCustomVideoProcessHandler(new STFilter(BaseApplication.getInstance()));

        // CDN 流音频来源
        mExpressEngine.setAudioSource(ZegoAudioSourceType.MAIN_PUBLISH_CHANNEL, ZegoPublishChannel.AUX);
        // 辅路流使用拷贝主路内容
        mExpressEngine.setVideoSource(ZegoVideoSourceType.MAIN_PUBLISH_CHANNEL, ZegoPublishChannel.AUX);

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

        mExpressEngine.enableHardwareDecoder(true);
        mExpressEngine.enableHardwareEncoder(true);
    }

    private void setVideoMirrorMode(ZegoVideoMirrorMode mirrorMode) {
        mExpressEngine.setVideoMirrorMode(mirrorMode, ZegoPublishChannel.MAIN);
        mExpressEngine.setVideoMirrorMode(mirrorMode, ZegoPublishChannel.AUX);
    }

    private void release() {
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
    }

    /**
     * 开始录制
     */
    public void startRecordingCaptured(ZegoDataRecordConfig recordConfig) {
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
        mExpressEngine.stopRecordingCapturedData(ZegoPublishChannel.MAIN);
        mExpressEngine.setDataRecordEventHandler(null);
    }

    public void stopPreview() {
        LogUtils.i(TAG, "stopPreview isPreview: " + isPreview);
        isPreview = false;
        mExpressEngine.stopPreview();
    }

    public void enableCamera(boolean enable) { // 由于推流内容是一致的，因此 enableCamera 是影响所有通道的
        LogUtils.i(TAG, "enableCamera enable: " + enable);
        isEnableCamera = enable;
        mExpressEngine.enableCamera(enable, ZegoPublishChannel.MAIN);
        mExpressEngine.enableCamera(enable, ZegoPublishChannel.AUX);
    }

    public boolean isBackCamera() {
        return !isFontCamera;
    }

    public void setLocalVideoCanvas(ZegoVideoCanvas localVideoCanvas) {
        LogUtils.i(TAG, "setLocalVideoCanvas localVideoCanvas: " + localVideoCanvas);
        mLocalCanvas = localVideoCanvas == null ? null : localVideoCanvas.convertToZegoCanvas();
        if (isPreview) {
            mExpressEngine.startPreview(mLocalCanvas);
        }
    }

    public void setRemoteVideoCanvas(ZegoVideoCanvas remoteVideoCanvas) {
        LogUtils.i(TAG, "setRemoteVideoCanvas remoteVideoCanvas: " + remoteVideoCanvas);
        ZegoCanvas canvas = remoteVideoCanvas.convertToZegoCanvas();
        mRemoteCanvasMap.put(remoteVideoCanvas.uid, new WeakReference<>(canvas));

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
     * @param type    设置主辅流类型 1、主流  2、辅流  3、同时设置
     */
    public void setVideoConfig(int width, int height, int fps, int bitrate, int type) {
        LogUtils.i(TAG, "setVideoConfig width: " + width + ", height: " + height + ", fps: " + fps + ", bitrate: " + bitrate);
        ZegoVideoConfig videoConfig = new ZegoVideoConfig();
        // hardcore 540P 采集
        videoConfig.captureWidth = 540;
        videoConfig.captureHeight = 960;
        videoConfig.encodeWidth = width;
        videoConfig.encodeHeight = height;
        videoConfig.fps = fps;
        videoConfig.bitrate = bitrate;
        if (type == 1) {
            mExpressEngine.setVideoConfig(videoConfig, ZegoPublishChannel.MAIN);
        } else if (type == 2) {
            mExpressEngine.setVideoConfig(videoConfig, ZegoPublishChannel.AUX);
        } else {
            mExpressEngine.setVideoConfig(videoConfig, ZegoPublishChannel.MAIN);
            mExpressEngine.setVideoConfig(videoConfig, ZegoPublishChannel.AUX);
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
        private final Map<StreamType, UserStreamInfo> publishStreamInfos;
        private final List<UserStreamInfo> playStreamInfos;

        public JoinLiveBuilder(String roomID) {
            this.roomID = roomID;
            publishStreamInfos = new HashMap<>();
            playStreamInfos = new ArrayList<>();
        }

        public JoinLiveBuilder putPublishStreamInfo(UserStreamInfo publishStreamInfo) {
            // 过滤无效的输入
            if (UserStreamInfo.isInvalid(publishStreamInfo)) {
                return this;
            }
            this.publishStreamInfos.put(publishStreamInfo.streamType, publishStreamInfo);
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
            ZegoEngine.getEngine().joinLive(roomID, publishStreamInfos, playStreamInfos);
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
     * @param roomID             指定的房间ID，如果当前已经登录房间，内部执行的停止当前的推拉流，然后执行切换房间操作。作为拉流器的时候，roomID 建议规则为 "player-" + 当前用户的UserID。这样子的话，可以避免收到正常房间的流新增删除触发的拉流。
     * @param publishStreamInfos 推流的配置。null 表示不需要推流。
     * @param playStreamInfos    预先拉流配置，表示这次登录房间的时候，预先需要拉的流，如果此时在拉别的流，则停止这些流的拉流操作。 null 表示不需要预先拉流。登录房间后，还是会根据 onRoomStreamUpdate 去触发拉流和停止拉流
     */
    private void joinLive(String roomID, Map<StreamType, UserStreamInfo> publishStreamInfos, List<UserStreamInfo> playStreamInfos) {
        LogUtils.d(TAG, "joinLive roomID: " + roomID + ", mRoomID: " + mRoomID +
                ", publishStreamInfos: " + publishStreamInfos + ", playStreamInfos: " + playStreamInfos +
                ", mPublishStreamInfoRTC: " + mPublishStreamInfoRTC + ", mPublishStreamInfoCDN: " + mPublishStreamInfoCDN +
                ", mPlayingStreamInfos: " + mPlayingStreamInfos);
        boolean isLoginSameRoom = false;
        isLoginSuccess = false;

        if (mRoomID == null) {
            ZegoUser mLocalUser = new ZegoUser(User.get().getUserId() + "", User.get().getUserId() + "");
            mExpressEngine.loginRoom(roomID, mLocalUser, new ZegoRoomConfig());
        } else if (mRoomID.equals(roomID)) {
            if (mPublishStreamInfoRTC != null) {
                UserStreamInfo publishStreamInfoRTC = publishStreamInfos.get(StreamType.RTC);
                if (!mPublishStreamInfoRTC.equals(publishStreamInfoRTC) && null != publishStreamInfoRTC) {
                    stopPublish(mPublishStreamInfoRTC);
                    // 如果 RTC 流地址不一样，停止转推，在 stopPublish RTC 内部执行了
                }
            }
            if (mPublishStreamInfoCDN != null) {
                UserStreamInfo publishStreamInfoCDN = publishStreamInfos.get(StreamType.CDN);
                if (!mPublishStreamInfoCDN.equals(publishStreamInfoCDN) && null != publishStreamInfoCDN) {
                    stopPublish(mPublishStreamInfoCDN);
                }
            }
            joinLiveSuccess(roomID, User.get().getUserId());
            isLoginSameRoom = true;
            // 如果是同一个房间，不会执行清除远程视图的逻辑。
        } else {
            // 切换房间，直接将转推都停掉
            removeAllPublishCdnUrl();

            // 如果已经登录房间，则直接通过 switchRoom 进行房间切换，该逻辑会停止推拉流的。
            mExpressEngine.switchRoom(mRoomID, roomID);

            // 切换房间后，都停止推流了
            mPublishStreamInfoRTC = null;
            mPublishStreamInfoCDN = null;

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

        startPublishStreamMap(publishStreamInfos); // 如果 publishStreamInfo != null，则进行拉流
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

        mExpressEngine.useFrontCamera(true);
        isFontCamera = true;

        // 默认音频采集设备打开
        if (!isEnableAudioCaptureDevice) { // enableAudioCaptureDevice 为同步操作。需要严谨检查
            isEnableAudioCaptureDevice = true;
            mExpressEngine.enableAudioCaptureDevice(true);
        }
        // 默认推音频
        mExpressEngine.mutePublishStreamAudio(false, ZegoPublishChannel.MAIN);
        mExpressEngine.mutePublishStreamAudio(false, ZegoPublishChannel.AUX);
        LogUtils.d(TAG, "resetDeviceState end");
    }

    public void leaveLive(boolean isClosePreview) {
        LogUtils.i(TAG, "leaveLive isLoginSuccess: " + isLoginSuccess);
        // reset 会将 uiHandler 中的延时任务都移除
        stopMixerTaskIfNeed();

        mExpressEngine.logoutRoom();

        reset(isClosePreview);
    }

    public void leaveLive() {
        leaveLive(false);
    }

    /**
     * 开启监听音频采集数据
     * 通过 hardcore 来指定采样率和声道数
     */
    public void startAudioCaptureDataObserver(final IZegoAudioCaptureDataHandler dataHandler) {
        LogUtils.i(TAG, "startAudioCaptureDataObserver isStartAudioCaptureDataObserverExternal: " + isStartAudioCaptureDataObserverExternal + ", isStartAudioCaptureDataObserverInternal: " + isStartAudioCaptureDataObserverInternal + ", dataHandler: " + dataHandler);

        startAudioCaptureDataObserverIfNeed();
        isStartAudioCaptureDataObserverExternal = true;

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
        LogUtils.i(TAG, "stopAudioCaptureDataObserver isStartAudioCaptureDataObserverExternal: " + isStartAudioCaptureDataObserverExternal + ", isStartAudioCaptureDataObserverInternal: " + isStartAudioCaptureDataObserverInternal);

        isStartAudioCaptureDataObserverExternal = false;
        stopAudioCaptureDataObserverIfNeed();

        mExpressEngine.setAudioDataHandler(null);
    }

    /**
     * 停止推 RTC 或者 CDN 流
     */
    public void stopPublish(UserStreamInfo publishStreamInfo) {
        LogUtils.i(TAG, "stopPublish publishStreamInfo: " + publishStreamInfo + ", mPublishStreamInfoRTC: " + mPublishStreamInfoRTC + ", mPublishStreamInfoCDN: " + mPublishStreamInfoCDN);
        if (mPublishStreamInfoRTC != null && mPublishStreamInfoRTC.equals(publishStreamInfo)) {
            // 先 remove 后 mPublishStreamInfoRTC = null，因为 removeAllPublishCdnUrl 内部会判断 mPublishStreamInfoRTC 的值
            removeAllPublishCdnUrl();

            mPublishStreamInfoRTC = null;
            mExpressEngine.stopPublishingStream(ZegoPublishChannel.MAIN);
        } else if (mPublishStreamInfoCDN != null && mPublishStreamInfoCDN.equals(publishStreamInfo)) {
            mPublishStreamInfoCDN = null;
            mExpressEngine.stopPublishingStream(ZegoPublishChannel.AUX);
        }

        startAudioCaptureDataObserverInternalIfNeed();
        stopAudioCaptureDataObserverInternalIfNeed();
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
     * 添加转推地址，只有在推 rtc 流后添加才有效
     * 停止推流、切换房间或者同一个房间推不同的 RTC 流，转推操作会停止，转推列表会主动清空。
     *
     * @param targetURL 转推的目标 CDN 地址
     */
    public void addPublishCdnUrl(String targetURL) {
        LogUtils.i(TAG, "addPublishCdnUrl targetUrl: " + targetURL + ", mPublishTargetUrlList: " + mPublishTargetUrlList + ", mPublishStreamInfoRTC: " + mPublishStreamInfoRTC);
        if (mPublishTargetUrlList.contains(targetURL) || TextUtils.isEmpty(targetURL) || mPublishStreamInfoRTC == null) {
            return;
        }

        final String streamID = mPublishStreamInfoRTC.target;
        mExpressEngine.addPublishCdnUrl(streamID, targetURL, new IZegoPublisherUpdateCdnUrlCallback() {
            @Override
            public void onPublisherUpdateCdnUrlResult(int error) {
                LogUtils.d(TAG, "setPublishCdnUrl onPublisherUpdateCdnUrlResult streamID: " + streamID + ", targetUrl: " + targetURL + ", error: " + error);
            }
        });
        mPublishTargetUrlList.add(targetURL);
    }

    /**
     * 移除转推地址。
     * 停止推流、切换房间或者同一个房间推不同的 RTC 流，转推操作会停止，转推列表会主动清空。
     *
     * @param targetURL 需要移除的转推目标 CDN 地址。
     */
    public void removePublishCdnUrl(String targetURL) {
        LogUtils.i(TAG, "removePublishCdnUrl targetUrl: " + targetURL + ", mPublishTargetUrlList: " + mPublishTargetUrlList + ", mPublishStreamInfoRTC: " + mPublishStreamInfoRTC);
        if (!mPublishTargetUrlList.contains(targetURL) || TextUtils.isEmpty(targetURL) || mPublishStreamInfoRTC == null) {
            return;
        }
        removePublishCdnUrlInner(targetURL);

        mPublishTargetUrlList.remove(targetURL);
    }

    /**
     * 返回当前在转推的 CDN 地址列表。
     * 停止推流、切换房间或者同一个房间推不同的 RTC 流，转推操作会停止，该列表会主动清空。
     *
     * @return 当前在转推的 CDN 地址列表。
     */
    public List<String> getCurrentPublishCdnUrlList() {
        return mPublishTargetUrlList;
    }

    /**
     * 禁止掉本地推流中 音频数据
     */
    public void mutePublishStreamAudio(boolean mute) {
        LogUtils.i(TAG, "mutePublishStreamAudio mute: " + mute);
        mExpressEngine.mutePublishStreamAudio(mute, ZegoPublishChannel.MAIN);
        mExpressEngine.mutePublishStreamAudio(mute, ZegoPublishChannel.AUX);
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
                ", mPublishStreamInfoRTC: " + mPublishStreamInfoRTC + ", mPublishStreamInfoCDN: " + mPublishStreamInfoCDN);
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
        if (mMixerConfig == null || mMixerConfig.inputList.isEmpty() || TextUtils.isEmpty(mMixerTargetUrl)) {
            // 没有指定混流参数或者混流输入参数为空，或者没有输出路径
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
        if (isFontCamera) {
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
        isFontCamera = !isFontCamera;
        mExpressEngine.useFrontCamera(isFontCamera);
        boolean isSetMirrorMode = (boolean) SPUtils.get(Utils.getApp(), User.get().getUserId() + Constant.KEY_VIDEO_MIRROR_MODE_VALUE, false);
        switchMirrorMode(isSetMirrorMode);
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

    private void removeAllPublishCdnUrl() {
        LogUtils.d(TAG, "removeAllPublishCdnUrl mPublishTargetUrlList: " + mPublishTargetUrlList + ", mPublishStreamInfoRTC: " + mPublishStreamInfoRTC);
        if (mPublishStreamInfoRTC == null) {
            return;
        }
        for (String targetUrl : mPublishTargetUrlList) {
            removePublishCdnUrlInner(targetUrl);
        }
        mPublishTargetUrlList.clear();
    }

    private void removePublishCdnUrlInner(String targetURL) {
        final String streamID = mPublishStreamInfoRTC.target;
        mExpressEngine.removePublishCdnUrl(streamID, targetURL, new IZegoPublisherUpdateCdnUrlCallback() {
            @Override
            public void onPublisherUpdateCdnUrlResult(int error) {
                LogUtils.d(TAG, "removePublishCdnUrl onPublisherUpdateCdnUrlResult streamID: " + streamID + ", targetUrl: " + targetURL + ", error: " + error);
            }
        });
    }

    private void startAudioCaptureDataObserverInternalIfNeed() {
        LogUtils.d(TAG, "startAudioCaptureDataObserverInternalIfNeed mPublishStreamInfoRTC: " + mPublishStreamInfoRTC + ", mPublishStreamInfoCDN: " + mPublishStreamInfoCDN);
        if (mPublishStreamInfoRTC == null && mPublishStreamInfoCDN != null) {
            startAudioCaptureDataObserverInternal();
        }
    }

    private void stopAudioCaptureDataObserverInternalIfNeed() {
        LogUtils.d(TAG, "stopAudioCaptureDataObserverInternalIfNeed mPublishStreamInfoRTC: " + mPublishStreamInfoRTC + ", mPublishStreamInfoCDN: " + mPublishStreamInfoCDN);
        // 如果推流 rtc 流，或者 cdn 没有推，则停止
        if (mPublishStreamInfoRTC != null || mPublishStreamInfoCDN == null) {
            stopAudioCaptureDataObserverInternal();
        }
    }

    private void startAudioCaptureDataObserverInternal() {
        LogUtils.d(TAG, "startAudioCaptureDataObserverInternal isStartAudioCaptureDataObserverExternal: " + isStartAudioCaptureDataObserverExternal + ", isStartAudioCaptureDataObserverInternal: " + isStartAudioCaptureDataObserverInternal);
        startAudioCaptureDataObserverIfNeed();
        isStartAudioCaptureDataObserverInternal = true;
    }

    private void stopAudioCaptureDataObserverInternal() {
        LogUtils.d(TAG, "stopAudioCaptureDataObserverInternal isStartAudioCaptureDataObserverExternal: " + isStartAudioCaptureDataObserverExternal + ", isStartAudioCaptureDataObserverInternal: " + isStartAudioCaptureDataObserverInternal);
        isStartAudioCaptureDataObserverInternal = false;
        stopAudioCaptureDataObserverIfNeed();
    }

    private void startAudioCaptureDataObserverIfNeed() {
        boolean hasStart = isStartAudioCaptureDataObserverInternal || isStartAudioCaptureDataObserverExternal;
        LogUtils.d(TAG, "startAudioCaptureDataObserverIfNeed hasStart: " + hasStart);
        if (hasStart) {
            return;
        }

        ZegoAudioFrameParam frameParam = new ZegoAudioFrameParam();
        frameParam.sampleRate = SAMPLE_RATE_AUDIO_CAPTURE_DATA_OBSERVER;
        frameParam.channel = CHANNEL_COUNT_AUDIO_CAPTURE_DATA_OBSERVER;

        mExpressEngine.startAudioDataObserver(ZegoAudioDataCallbackBitMask.CAPTURED.value(), frameParam);
    }

    private void stopAudioCaptureDataObserverIfNeed() {
        boolean needStop = !isStartAudioCaptureDataObserverInternal && !isStartAudioCaptureDataObserverExternal;
        LogUtils.d(TAG, "stopAudioCaptureDataObserverIfNeed needStop: " + needStop);
        if (!needStop) {
            return;
        }
        mExpressEngine.stopAudioDataObserver();
    }

    /**
     * 触发延迟停止混流逻辑
     */
    public void triggerStopMixerDelayTask() {
        // 对于观众拉两路 RTMP 流的 单播PK 切换优化方案，其实下面的延迟逻辑使跟优先级开关没什么关系。
        // 这里仅仅是以该开关统一管理该优化策略。
        stopMixerTaskIfNeed();
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
        removeAllPublishCdnUrl();

        mRoomID = null;
        mPublishStreamInfoRTC = null;
        mPublishStreamInfoCDN = null;
        mLocalCanvas = null;
        mMixerTargetUrl = null;
        mMixerTargetUrlWithPriority = null;
        mMixerConfig = null;

        isStartAudioCaptureDataObserverExternal = false;
        isStartAudioCaptureDataObserverInternal = false;

        isLoginSuccess = false;
        isPreview = isClosePreview;
        mPublishTargetUrlList.clear();
        mPlayingStreamInfos.clear();
        mRemoteCanvasMap.clear();
        mPlayingStreamStateBeans.clear();
        mEventHandlerMap.clear();

        mUiHandler.removeCallbacksAndMessages(null);
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
        String gameUrlStr = JavaGlobalConfig.getInstance().getConfig(GlobalType.M_2274, "");
        List<GameStreamEntity> streamList = JsonUtil.parseList(gameUrlStr, GameStreamEntity.class);
        List<UserStreamInfo> copyPlayingStreamInfos = new ArrayList<>(mPlayingStreamInfos);
        for (UserStreamInfo playingStreamInfo : copyPlayingStreamInfos) {
            if (playingStreamInfo.streamType == StreamType.CDN) { // 只对 CDN 流进行停止拉流处理
                boolean hasFound = needPlayStreamInfos.contains(playingStreamInfo);
                if (hasFound) {
                    // 如果要求拉的正在拉流，那么回调当前流的状态
                    callPlayStreamStateIfNeed(playingStreamInfo);
                } else {
                    boolean isHaveSameStream = false;
                    if (null != streamList && streamList.size() > 0) {
                        for (GameStreamEntity entity : streamList) {
                            if (TextUtils.equals(playingStreamInfo.target, entity.getBattleGamePullUrl())) {
                                isHaveSameStream = true;
                                break;
                            }
                        }
                    }
                    if (!isHaveSameStream) {
                        stopPlayStream(playingStreamInfo);
                    }
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

        WeakReference<ZegoCanvas> playCanvasReference = mRemoteCanvasMap.get(playStreamInfo.userID);
        ZegoCanvas playCanvas = playCanvasReference == null ? null : playCanvasReference.get();

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

    private void startPublishStreamMap(Map<StreamType, UserStreamInfo> publishStreamInfos) {
        startPublishStream(publishStreamInfos.get(StreamType.RTC));
        startPublishStream(publishStreamInfos.get(StreamType.CDN));
    }

    public void startPublishRTCStream(UserStreamInfo publishStreamInfo) {
        startPublishStream(publishStreamInfo);
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
                    mPublishStreamInfoCDN = publishStreamInfo;
                    //CDN 开启gopSize和encodeProfile配置
                    mExpressEngine.callExperimentalAPI(getEncoderProfileJsonStr(mEncodeProfile, ZegoPublishChannel.AUX.value()));
                    mExpressEngine.callExperimentalAPI(getKeyFrameIntervalJsonStr(mGopSize, ZegoPublishChannel.AUX.value()));
                    String cdnTargetUrl = publishStreamInfo.target;
                    ZegoCDNConfig cdnConfig = new ZegoCDNConfig();
                    cdnConfig.url = cdnTargetUrl;
                    mExpressEngine.enablePublishDirectToCDN(true, cdnConfig, ZegoPublishChannel.AUX);
                    String cdnPublishStreamID = getCDNStreamIDUserStreamInfo(publishStreamInfo);
                    mExpressEngine.startPublishingStream(cdnPublishStreamID, ZegoPublishChannel.AUX);
                    mExpressEngine.setStreamExtraInfo(STREAM_EXTRA_INFO_CDN_TAG, ZegoPublishChannel.AUX, null);
                    break;
                default:
                    break;
            }

            startAudioCaptureDataObserverInternalIfNeed();
            stopAudioCaptureDataObserverInternalIfNeed();
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
        mExpressEngine.sendCustomVideoCaptureRawData(mCaptureDataByteBuffer, data.length, frameParam, timestamp, ZegoPublishChannel.MAIN);
        if (mPublishStreamInfoCDN != null) {
            mExpressEngine.sendCustomVideoCaptureRawData(mCaptureDataByteBuffer, data.length, frameParam, timestamp, ZegoPublishChannel.AUX);
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
        private boolean is265Encoder;
        private boolean is265Decoder;

        public UserStreamInfo(long userID, String target, StreamType streamType) {
            this.userID = userID;
            this.target = target;
            this.streamType = streamType;
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

        @NonNull
        @Override
        public String toString() {
            return "UserStreamInfo{" +
                    "userID=" + userID +
                    ", target='" + target + '\'' +
                    ", streamType=" + streamType +
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
