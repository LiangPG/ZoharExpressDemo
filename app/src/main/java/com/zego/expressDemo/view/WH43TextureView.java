package com.zego.expressDemo.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.TextureView;

import androidx.annotation.Nullable;

/**
 * 长宽 4：3 LinearLayout，长宽都会默认修改为固定值，因此wrap_content可能会失效。
 */
public class WH43TextureView extends TextureView {

    public WH43TextureView(Context context) {
        super(context);
    }

    public WH43TextureView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public WH43TextureView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // 强制设置成父布局提供的或者自身定义的宽度，并且计算高度，强制设置为EXACTLY
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = widthSize * 3 / 4;

        super.onMeasure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(heightSize, MeasureSpec.EXACTLY));
    }
}
