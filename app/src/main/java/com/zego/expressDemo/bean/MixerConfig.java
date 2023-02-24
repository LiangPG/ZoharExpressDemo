package com.zego.expressDemo.bean;

import android.graphics.Rect;

import java.util.ArrayList;

import im.zego.zegoexpress.constants.ZegoMixerInputContentType;
import im.zego.zegoexpress.entity.ZegoMixerAudioConfig;
import im.zego.zegoexpress.entity.ZegoMixerVideoConfig;
import im.zego.zegoexpress.entity.ZegoWatermark;

public class MixerConfig {
    public final ZegoMixerAudioConfig audioConfig;
    public final ZegoMixerVideoConfig videoConfig;
    public final ArrayList<MixerInput> inputList;
    public int backgroundColor;
    public ZegoWatermark mZegoWatermark;
    public String backgroundImageURL;

    public MixerConfig() {
        this.inputList = new ArrayList<>();
        this.audioConfig = new ZegoMixerAudioConfig();
        this.videoConfig = new ZegoMixerVideoConfig();
        this.backgroundColor = 0;
        this.backgroundImageURL = "";
    }

    @Override
    public String toString() {
        return "MixerConfig{" +
                "audioConfig=" + audioConfig +
                ", videoConfig=" + videoConfig +
                ", inputList=" + inputList +
                ", backgroundColor=" + backgroundColor +
                ", mZegoWatermark=" + mZegoWatermark +
                '}';
    }

    public static class MixerInput {
        public long uid;
        public ZegoMixerInputContentType contentType;
        public Rect layout;
        public int soundLevelID;

        public MixerInput(long uid, ZegoMixerInputContentType contentType, Rect layout) {
            this.uid = uid;
            this.contentType = contentType;
            this.layout = layout;
        }

        public MixerInput(long uid, ZegoMixerInputContentType contentType, Rect layout, int soundLevelID) {
            this.uid = uid;
            this.contentType = contentType;
            this.layout = layout;
            this.soundLevelID = soundLevelID;
        }

        @Override
        public String toString() {
            return "MixerInput{" +
                    "uid=" + uid +
                    ", contentType=" + contentType +
                    ", layout=" + layout +
                    ", soundLevelID=" + soundLevelID +
                    '}';
        }
    }
}
