package com.android.oldbili.retro.util;

import android.content.Context;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 全局崩溃日志收集。
 * 崩溃时把堆栈写到应用私有目录下的 crash 文件夹，之后可从设置里导出成 TXT 到下载文件夹，
 * 方便用户把日志发给开发者。
 */
public class CrashHandler implements Thread.UncaughtExceptionHandler {

    private static CrashHandler sInstance;
    private final Context mContext;
    private final Thread.UncaughtExceptionHandler mDefault;
    private static String sCurrentPage = "";

    /** BaseActivity.onCreate 里上报，崩溃日志里带上当前页面 */
    public static void setCurrentPage(String page) {
        sCurrentPage = page == null ? "" : page;
    }

    public static void init(Context context) {
        if (sInstance == null) {
            sInstance = new CrashHandler(context.getApplicationContext());
            Thread.setDefaultUncaughtExceptionHandler(sInstance);
        }
    }

    private CrashHandler(Context context) {
        mContext = context;
        mDefault = Thread.getDefaultUncaughtExceptionHandler();
    }

    @Override
    public void uncaughtException(Thread thread, Throwable ex) {
        try {
            saveLog(thread, ex);
        } catch (Throwable ignored) {
            // 写日志失败也不能再抛异常
        }
        if (mDefault != null) {
            mDefault.uncaughtException(thread, ex);
        } else {
            android.os.Process.killProcess(android.os.Process.myPid());
        }
    }

    private void saveLog(Thread thread, Throwable ex) {
        String content = buildLog(thread, ex);
        // 1) 应用私有目录（设置里可导出）
        File dir = new File(mContext.getFilesDir(), "crash");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        String name = "crash-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault())
                .format(new Date()) + ".txt";
        writeFile(new File(dir, name), content);

        // 2) 尽量写一份到公共下载目录：Download/OldBiliRetro/crash/
        //    （老安卓 + 已授权存储的设备能成功），朋友没有 adb 也能把日志发回来
        try {
            File pub = new File(android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS), "OldBiliRetro/crash");
            if (!pub.exists()) {
                pub.mkdirs();
            }
            writeFile(new File(pub, name), content);
        } catch (Throwable ignored) {
        }
    }

    private String buildLog(Thread thread, Throwable ex) {
        StringBuilder sb = new StringBuilder();
        sb.append("OldBiliRetro 崩溃日志\n");
        sb.append("时间: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date())).append('\n');
        sb.append("安卓版本: ").append(DeviceInfoUtil.getAndroidVersion()).append('\n');
        sb.append("设备: ").append(DeviceInfoUtil.getDeviceModel()).append('\n');
        sb.append("应用版本: ").append(DeviceInfoUtil.getAppVersion(mContext)).append('\n');
        sb.append("当前页面: ").append(sCurrentPage).append('\n');
        sb.append("线程: ").append(thread == null ? "?" : thread.getName()).append("\n\n");
        StringWriter sw = new StringWriter();
        ex.printStackTrace(new PrintWriter(sw));
        sb.append(sw.toString());
        return sb.toString();
    }

    private void writeFile(File file, String content) {
        FileWriter fw = null;
        try {
            fw = new FileWriter(file);
            fw.write(content);
            fw.flush();
        } catch (Exception ignored) {
        } finally {
            if (fw != null) {
                try {
                    fw.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /** 是否有最近一次崩溃记录（启动时提示用） */
    public static File lastCrashFile() {
        if (sInstance == null) {
            return null;
        }
        try {
            File dir = new File(sInstance.mContext.getFilesDir(), "crash");
            File[] files = dir.listFiles();
            if (files == null || files.length == 0) {
                return null;
            }
            File newest = null;
            for (int i = 0; i < files.length; i++) {
                if (newest == null || files[i].lastModified() > newest.lastModified()) {
                    newest = files[i];
                }
            }
            return newest;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
