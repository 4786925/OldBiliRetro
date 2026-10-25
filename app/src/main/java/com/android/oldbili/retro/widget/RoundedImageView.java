package com.android.oldbili.retro.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.widget.ImageView;

/**
 * 圆形头像 ImageView（2014年那会儿 B 站客户端头像已经是圆的了）。
 * 老设备没有 CircleImageView 这种库，自己画一个。
 * XML 布局填充必须提供 (Context, AttributeSet) 构造器。
 */
public class RoundedImageView extends ImageView {

    private Bitmap mBitmap;
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public RoundedImageView(Context context) {
        super(context);
        setScaleType(ScaleType.CENTER_CROP);
    }

    public RoundedImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setScaleType(ScaleType.CENTER_CROP);
    }

    public RoundedImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setScaleType(ScaleType.CENTER_CROP);
    }

    @Override
    public void setImageBitmap(Bitmap bm) {
        mBitmap = bm;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mBitmap == null || mBitmap.isRecycled()) {
            super.onDraw(canvas);
            return;
        }
        float size = Math.min(getWidth(), getHeight());
        if (size <= 0) {
            return;
        }
        float scale = Math.max(size / mBitmap.getWidth(), size / mBitmap.getHeight());
        Matrix matrix = new Matrix();
        matrix.setScale(scale, scale);
        matrix.postTranslate((getWidth() - mBitmap.getWidth() * scale) / 2f,
                (getHeight() - mBitmap.getHeight() * scale) / 2f);
        Shader shader = new BitmapShader(mBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        shader.setLocalMatrix(matrix);
        mPaint.setShader(shader);
        canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, size / 2f, mPaint);
    }
}
