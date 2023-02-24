package com.zego.expressDemo.bean;

import android.view.View;

import im.zego.zegoexpress.constants.ZegoViewMode;
import im.zego.zegoexpress.entity.ZegoCanvas;

/**
 * 视频显示属性
 */
public class ZegoVideoCanvas {

    /**
     * 用于渲染视频的view, 推荐使用 TextureView, 也可以使用 SurfaceView 或 Surface
     */
    public View view;
    /**
     * 视频输出填充类型
     *
     * @see ZegoViewMode#ASPECT_FIT
     * @see ZegoViewMode#ASPECT_FILL
     * @see ZegoViewMode#SCALE_TO_FILL
     */
    public ZegoViewMode viewMode;

    public long uid;

    public ZegoVideoCanvas(View view, long uid) {
        this.view = view;
        this.viewMode = ZegoViewMode.ASPECT_FILL;
        this.uid = uid;
    }

    public ZegoVideoCanvas(View view, ZegoViewMode viewMode, long uid) {
        this.view = view;
        this.viewMode = viewMode;
        this.uid = uid;
    }

    @Override
    public String toString() {
        return "ZegoVideoCanvas{" +
                "view=" + view +
                ", v=" + viewMode +
                ", uid=" + uid +
                '}';
    }

    public ZegoCanvas convertToZegoCanvas() {
        ZegoCanvas canvas = new ZegoCanvas(view);
        canvas.viewMode = viewMode;
        return canvas;
    }
}
