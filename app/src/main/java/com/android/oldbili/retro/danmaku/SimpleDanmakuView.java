package com.android.oldbili.retro.danmaku;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

import master.flame.danmaku.controller.DrawHandler;
import master.flame.danmaku.controller.IDanmakuView;
import master.flame.danmaku.danmaku.model.BaseDanmaku;
import master.flame.danmaku.danmaku.model.IDanmakus;
import master.flame.danmaku.danmaku.parser.BaseDanmakuParser;

/**
 * 模拟器兼容弹幕层：普通 View（不是 SurfaceView / TextureView），
 * 直接画在应用窗口里，和 TextureView 视频同层合成。
 *
 * 这样不会出现“弹幕 Surface 整层不透明把视频盖黑”的模拟器 GPU 问题。
 * 功能是精简版：滚动/顶部/底部弹幕、暂停、seek、隐藏。
 */
public class SimpleDanmakuView extends View implements IDanmakuView {

    public static class Item {
        public String text;
        public long time;
        public int color = Color.WHITE;
        public float size = 20f;
        public int type = BaseDanmaku.TYPE_SCROLL_RL;
        public String userHash;
    }

    private final List<Item> mItems = new ArrayList<Item>();
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean mPlaying;
    private long mBasePosition;
    private long mBaseRealtime;
    private long mCurrentPosition;
    private boolean mPrepared;

    public SimpleDanmakuView(Context context) {
        super(context);
        setBackgroundColor(Color.TRANSPARENT);
        setWillNotDraw(false);
        mPaint.setColor(Color.WHITE);
        mPaint.setTextSize(20f);
        mPaint.setShadowLayer(3f, 1f, 1f, Color.BLACK);
    }

    public void setItems(List<Item> list) {
        mItems.clear();
        if (list != null) {
            mItems.addAll(list);
        }
        mPrepared = true;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mItems.isEmpty() || getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        long pos = currentPosition();
        int lines = Math.max(1, getHeight() / 30);
        float lineHeight = (float) getHeight() / lines;
        long windowMs = 8000L;
        int drawn = 0;
        for (int i = 0; i < mItems.size() && drawn < 120; i++) {
            Item item = mItems.get(i);
            if (item == null || item.text == null) {
                continue;
            }
            long t = item.time;
            if (t > pos || pos - t > windowMs) {
                continue;
            }
            float size = item.size > 0 ? item.size : 20f;
            if (size > 30f) {
                size = 30f;
            }
            mPaint.setTextSize(size);
            mPaint.setColor(item.color == 0 ? Color.WHITE : item.color);
            mPaint.setAlpha(255);
            String text = item.text;
            float tw = mPaint.measureText(text);
            float x;
            float y;
            if (item.type == BaseDanmaku.TYPE_FIX_TOP) {
                x = (getWidth() - tw) / 2f;
                y = lineHeight;
            } else if (item.type == BaseDanmaku.TYPE_FIX_BOTTOM) {
                x = (getWidth() - tw) / 2f;
                y = getHeight() - lineHeight * 0.4f;
            } else {
                float progress = (float) (pos - t) / windowMs;
                x = getWidth() - progress * (getWidth() + tw);
                int line = Math.abs((item.userHash != null ? item.userHash.hashCode()
                        : text.hashCode())) % lines;
                y = lineHeight * (line + 1);
            }
            canvas.drawText(text, x, y, mPaint);
            drawn++;
        }
        if (mPlaying) {
            postInvalidateDelayed(33);
        }
    }

    private long currentPosition() {
        if (mPlaying) {
            return mBasePosition + (SystemClock.uptimeMillis() - mBaseRealtime);
        }
        return mCurrentPosition;
    }

    private void freezePosition() {
        mCurrentPosition = currentPosition();
        mBasePosition = mCurrentPosition;
        mBaseRealtime = SystemClock.uptimeMillis();
    }

    @Override
    public void start() {
        mBaseRealtime = SystemClock.uptimeMillis();
        mBasePosition = mCurrentPosition;
        mPlaying = true;
        invalidate();
    }

    @Override
    public void start(long position) {
        mCurrentPosition = position;
        start();
    }

    @Override
    public void pause() {
        freezePosition();
        mPlaying = false;
        invalidate();
    }

    @Override
    public void resume() {
        start();
    }

    @Override
    public void stop() {
        pause();
        seekTo(0L);
    }

    @Override
    public void seekTo(Long ms) {
        mCurrentPosition = ms == null ? 0 : ms;
        mBasePosition = mCurrentPosition;
        mBaseRealtime = SystemClock.uptimeMillis();
        invalidate();
    }

    @Override
    public void release() {
        mItems.clear();
        mPlaying = false;
    }

    @Override
    public void show() {
        setVisibility(VISIBLE);
    }

    @Override
    public void hide() {
        setVisibility(GONE);
    }

    @Override
    public void toggle() {
        if (getVisibility() == VISIBLE) {
            hide();
        } else {
            show();
        }
    }

    @Override
    public long hideAndPauseDrawTask() {
        pause();
        hide();
        return mCurrentPosition;
    }

    @Override
    public void showAndResumeDrawTask(Long position) {
        seekTo(position);
        show();
        start();
    }

    @Override
    public void clearDanmakusOnScreen() {
        invalidate();
    }

    @Override
    public void clearCache() {
    }

    @Override
    public void prepare(BaseDanmakuParser parser) {
        mPrepared = true;
    }

    @Override
    public boolean isPrepared() {
        return mPrepared;
    }

    @Override
    public boolean isPaused() {
        return !mPlaying;
    }

    @Override
    public boolean isHardwareAccelerated() {
        return false;
    }

    @Override
    public void setDrawingThreadType(int type) {
    }

    @Override
    public void enableDanmakuDrawingCache(boolean enable) {
    }

    @Override
    public boolean isDanmakuDrawingCacheEnabled() {
        return false;
    }

    @Override
    public void showFPS(boolean show) {
    }

    @Override
    public void addDanmaku(BaseDanmaku item) {
    }

    @Override
    public void removeAllDanmakus() {
        mItems.clear();
        invalidate();
    }

    @Override
    public void removeAllLiveDanmakus() {
    }

    @Override
    public IDanmakus getCurrentVisibleDanmakus() {
        return null;
    }

    @Override
    public void setCallback(DrawHandler.Callback callback) {
    }

    @Override
    public void setClock(DrawHandler.Clock clock) {
    }

    @Override
    public long getCurrentTime() {
        return currentPosition();
    }

    @Override
    public View getView() {
        return this;
    }

    @Override
    public void setOnDanmakuClickListener(OnDanmakuClickListener listener) {
    }

    @Override
    public OnDanmakuClickListener getOnDanmakuClickListener() {
        return null;
    }
}
