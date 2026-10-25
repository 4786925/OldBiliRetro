package com.android.oldbili.retro.util;

import android.os.Build;

/**
 * 设备版本检测与老机策略。
 * 策略（按安卓版本）：
 * - 2.3~4.4（SDK 9~19）：默认清晰度 360P
 * - 5.0~6.x（SDK 21~24）：默认 480P
 * - 7.0+（SDK 25+）：默认 720P
 * - 安卓 2（SDK<11）：关闭硬件加速（软件绘制）、少图模式、弹幕 8 行+关描边
 */
public class DeviceCompat {

    private static boolean sInited;

    /** App 启动时调用一次：写入老机模式开关 */
    public static synchronized void init(android.content.Context context) {
        if (sInited) {
            return;
        }
        sInited = true;
        boolean ancient = Build.VERSION.SDK_INT < 11;
        if (ancient) {
            Sp.putBoolean(Sp.LOW_IMAGE_MODE, true);
            Sp.putBoolean(Sp.LOW_END_MODE, true);
        }
    }

    /** 是否安卓 2（2.0~2.3.7，SDK<11） */
    public static boolean isAncient() {
        return Build.VERSION.SDK_INT < 11;
    }

    /** 低端/老机模式（安卓 2 自动开启） */
    public static boolean isLowEnd() {
        return Sp.getBoolean(Sp.LOW_END_MODE, false);
    }

    /** 少图模式（设置里的"无图模式"开关，或安卓 2 自动开启） */
    public static boolean isLowImageMode() {
        return Sp.getBoolean(Sp.LOW_IMAGE_MODE, false)
                || Sp.getBoolean("set_low_image", false);
    }

    /** 按安卓版本给默认清晰度（用户未手动设置时使用）——播放只保留 360P/720P */
    public static int defaultQuality() {
        int sdk = Build.VERSION.SDK_INT;
        if (sdk < 21) {
            return 16;   // 2.3~4.4：360P
        }
        return 64;       // 5.0+：720P（480P 已移除，720P 不可用时服务器自动降到 360P）
    }
}
