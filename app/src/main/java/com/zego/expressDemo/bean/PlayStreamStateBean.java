package com.zego.expressDemo.bean;

/**
 * 缓存流的状态
 */
public class PlayStreamStateBean {
    public boolean isRecvVideoFirstFrame;
    public boolean isRecvAudioFirstFrame;
    public boolean isRenderVideoFirstFrame;

    @Override
    public String toString() {
        return "PlayStreamStateBean{" +
                "isRecvVideoFirstFrame=" + isRecvVideoFirstFrame +
                ", isRecvAudioFirstFrame=" + isRecvAudioFirstFrame +
                ", isRenderVideoFirstFrame=" + isRenderVideoFirstFrame +
                '}';
    }
}
