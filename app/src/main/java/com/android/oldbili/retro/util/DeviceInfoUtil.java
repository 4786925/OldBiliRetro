package com.android.oldbili.retro.util;

import android.content.Context;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.WindowManager;

/**
 * 设备信息检测，设置页里展示用。
 */
public class DeviceInfoUtil {

    public static String getAndroidVersion() {
        return Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")";
    }

    public static String getDeviceModel() {
        return Build.MANUFACTURER + " " + Build.MODEL;
    }

    public static String getCpuAbi() {
        String abi = "";
        if (Build.VERSION.SDK_INT >= 21) {
            String[] abis = Build.SUPPORTED_ABIS;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < abis.length; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(abis[i]);
            }
            abi = sb.toString();
        } else {
            abi = Build.CPU_ABI + ", " + Build.CPU_ABI2;
        }
        return abi;
    }

    public static String getAppVersion(Context context) {
        try {
            return context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    public static String getScreenSize(Context context) {
        DisplayMetrics dm = new DisplayMetrics();
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        wm.getDefaultDisplay().getMetrics(dm);
        return dm.widthPixels + " x " + dm.heightPixels + " (" + dm.densityDpi + "dpi)";
    }

    public static String getRamInfo() {
        // 注意：MemoryInfo 必须先由 ActivityManager.getMemoryInfo() 填充，
        // 否则 totalMem 恒为 0（之前"安卓17显示内存 0MB"就是这个 bug）。
        if (android.os.Build.VERSION.SDK_INT >= 16) {
            try {
                android.app.ActivityManager am =
                        (android.app.ActivityManager) sAppContext.getSystemService(
                                android.content.Context.ACTIVITY_SERVICE);
                if (am != null) {
                    android.app.ActivityManager.MemoryInfo mi =
                            new android.app.ActivityManager.MemoryInfo();
                    am.getMemoryInfo(mi);
                    if (mi.totalMem > 0) {
                        return (mi.totalMem / 1024 / 1024) + " MB";
                    }
                }
            } catch (Exception ignored) {
            }
        }
        try {
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.FileReader("/proc/meminfo"));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("MemTotal:")) {
                    String[] parts = line.trim().split("\\s+");
                    long kb = Long.parseLong(parts[1]);
                    reader.close();
                    return (kb / 1024) + " MB";
                }
            }
            reader.close();
        } catch (Exception ignored) {
        }
        return "未知";
    }

    /** 应用启动时注入 context（用于取系统服务） */
    private static android.content.Context sAppContext;

    public static void init(android.content.Context context) {
        if (context != null) {
            sAppContext = context.getApplicationContext();
        }
    }
}
