package com.android.oldbili.retro;

/**
 * 播放器内打开的弹幕设置：清单里固定横屏，避免先竖屏再旋转的闪烁。
 * 从「我的 → 设置」进入的是 DanmakuSettingsActivity（固定竖屏）。
 */
public class DanmakuSettingsLandscapeActivity extends DanmakuSettingsActivity {

    @Override
    protected void applyScreenOrientation() {
        // 从播放器进入：固定横屏，覆盖设置里的自动旋转
        setRequestedOrientation(
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    }
}
