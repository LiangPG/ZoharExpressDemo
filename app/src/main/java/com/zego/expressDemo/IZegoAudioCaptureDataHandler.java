package com.zego.expressDemo;

public interface IZegoAudioCaptureDataHandler {
    void onCapturedAudioData(byte[] data, int dataLength, int sampleRate, int channel);
}
