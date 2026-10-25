package com.android.oldbili.retro;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.DisplayMetrics;

import com.android.oldbili.retro.net.HttpUtil;
import com.android.oldbili.retro.net.ImageLoader;
import com.android.oldbili.retro.util.CrashHandler;
import com.android.oldbili.retro.util.Sp;

/**
 * 应用入口。
 * 开屏设置的"界面大小"通过全局密度缩放实现：手机、平板、手表一套布局通吃。
 */
public class OldBiliApp extends Application {

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(new ScaleContext(base));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Sp.init(this);
        CrashHandler.init(this);
        ImageLoader.init(this);
        // 二维码库（SwetakeQRCode）的数据文件在 assets 里，先喂给它 Context
        com.swetake.util.Qrcode.init(this);
        // 预热 UA 与 Cookie 静态块
        HttpUtil.TAG.length();
        notifyLastCrash();
    }

    /** 上次崩溃提示：每个崩溃只提示一次，告诉用户日志位置 */
    private void notifyLastCrash() {
        try {
            java.io.File last = com.android.oldbili.retro.util.CrashHandler.lastCrashFile();
            if (last == null) {
                return;
            }
            String key = "crash_notified_" + last.getName();
            if (Sp.getBoolean(key, false)) {
                return;
            }
            Sp.putBoolean(key, true);
            com.android.oldbili.retro.util.ToastUtil.show(this,
                    "上次运行崩溃过，日志已保存：Download/OldBiliRetro/crash/，可发给开发者");
        } catch (Throwable ignored) {
        }
    }

    /**
     * 密度缩放包装器：按开屏滑块存的倍数缩放 density 与 scaledDensity，
     * 所有 dp/sp 尺寸随之整体变大变小。每次取资源时现读设置，改完立刻生效。
     */
    private static class ScaleContext extends android.content.ContextWrapper {

        private float mBaseDensity = -1;
        private float mBaseScaled = -1;
        private int mBaseDpi = -1;

        public ScaleContext(Context base) {
            super(base);
        }

        @Override
        public Resources getResources() {
            Resources res = super.getResources();
            if (res == null) {
                return null;
            }
            DisplayMetrics dm = res.getDisplayMetrics();
            if (mBaseDensity < 0) {
                mBaseDensity = dm.density;
                mBaseScaled = dm.scaledDensity;
                mBaseDpi = dm.densityDpi;
            }
            float scale = Sp.getFloat(Sp.UI_SCALE, 1.0f);
            if (scale <= 0.1f) {
                scale = 1.0f;
            }
            if (Math.abs(scale - 1.0f) < 0.001f) {
                return res;
            }
            Configuration config = new Configuration(res.getConfiguration());
            DisplayMetrics newDm = new DisplayMetrics();
            newDm.setTo(dm);
            newDm.density = mBaseDensity * scale;
            newDm.scaledDensity = mBaseScaled * scale;
            newDm.densityDpi = mBaseDpi;
            try {
                return new Resources(res.getAssets(), newDm, config);
            } catch (Exception e) {
                return res;
            }
        }
    }
}
