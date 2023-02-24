package com.zego.expressDemo;

import java.util.HashMap;

import im.zego.zegoexpress.constants.ZegoAudioVADStableStateMonitorType;
import im.zego.zegoexpress.entity.ZegoDataRecordProgress;
import im.zego.zegoexpress.entity.ZegoPublishStreamQuality;


public abstract class ZegoEngineEventHandler {
    public void onJoinLiveSuccess(String liveRoomID, long uid) {
    }

    public void onJoinLiveFailed(String liveRoomID, long uid, int error) {
    }

    public void onPublisherQualityUpdate(long userID, ZegoPublishStreamQuality quality) {
    }

    public void onLeaveLive(String liveRoomID, long uid) {
    }

    /**
     * 本地采集的第一帧
     */
    public void onPublisherCapturedVideoFirstFrame() {
    }

    public void onPlayerPlayFailed(ZegoEngine.UserStreamInfo playStreamInfo, int error) {}

    public void onPlayerRecvVideoFirstFrame(ZegoEngine.UserStreamInfo playStreamInfo) {
    }

    public void onPlayerRecvAudioFirstFrame(ZegoEngine.UserStreamInfo playStreamInfo) {
    }

    // 远程第一帧画面解码成功
    public void onPlayerRenderVideoFirstFrame(ZegoEngine.UserStreamInfo playStreamInfo) {
    }

    public void onCapturedSoundLevelUpdate(float soundLevel) {
    }

    public void onRemoteSoundLevelUpdate(HashMap<Long, Float> soundLevels) {
    }

    public void onMixerSoundLevelUpdate(HashMap<Integer, Float> soundLevels) {
    }

    public void onCapturedDataRecordProgressUpdate(ZegoDataRecordProgress progress) {
    }

    public void onPlayerRecvSEI(String streamID, byte[] data) {
    }
    public void onStartAudioVADStableStateMonitor(ZegoAudioVADStableStateMonitorType zegoAudioVADStableStateMonitorType) {
    }
}
