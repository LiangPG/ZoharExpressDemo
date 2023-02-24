package com.zego.expressDemo.videocapture;

import java.nio.ByteBuffer;

import im.zego.zegoexpress.constants.ZegoVideoFrameFormat;

/**
 * IZegoVideoFrameConsumer 接口。
 */
public interface IZegoVideoFrameConsumer {
    /**
     * 接收 ByteBuffer 类型的视频帧。
     *
     * @param buffer    Byte Buffer 型的视频数据
     * @param format    像素格式。暂时支持 {@link ZegoVideoFrameFormat#I420 I420}、{@link ZegoVideoFrameFormat#NV21 NV21}、 {@link ZegoVideoFrameFormat#RGBA32 RGBA} 三种格式
     * @param width     视频帧的宽度
     * @param height    视频帧的高度
     * @param rotation  视频帧顺时针旋转的角度。
     * @param timestamp 传入的视频帧的时间戳。开发者必须为每一个视频帧设置一个时间戳
     */
    void consumeByteBufferFrame(ByteBuffer buffer, int format, int width, int height, int rotation, long timestamp);

    /**
     * Byte Array 型的数据数据
     *
     * @param data      Byte Array 型的数据数据
     * @param format    像素格式。暂时支持 {@link ZegoVideoFrameFormat#I420 I420}、{@link ZegoVideoFrameFormat#NV21 NV21}、 {@link ZegoVideoFrameFormat#RGBA32 RGBA} 三种格式
     * @param width     视频帧的宽度
     * @param height    视频帧的高度
     * @param rotation  视频帧顺时针旋转的角度。
     * @param timestamp 传入的视频帧的时间戳。开发者必须为每一个视频帧设置一个时间戳
     */
    void consumeByteArrayFrame(byte[] data, int format, int width, int height, int rotation, long timestamp);

    /**
     * 接收 Texture 类型的视频帧。
     *
     * @param textureId Texture 的 ID
     * @param format    像素格式。 暂时只支持  这种格式
     * @param width     视频帧的宽度
     * @param height    视频帧的高度
     * @param rotation  视频帧顺时针旋转的角度。
     * @param timestamp 传入的视频帧的时间戳。
     * @param matrix    texture 的纹理坐标。 <b>暂不支持</b>
     */
    void consumeTextureFrame(int textureId, int format, int width, int height, int rotation, long timestamp, float[] matrix);
}
