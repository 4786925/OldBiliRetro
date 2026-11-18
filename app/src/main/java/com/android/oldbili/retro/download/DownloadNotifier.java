package com.android.oldbili.retro.download;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.android.oldbili.retro.DownloadActivity;
import com.android.oldbili.retro.PlayerActivity;

/**
 * 下载完成通知：点通知直接用本地文件播放。
 * 通知渠道用反射创建，避免 compileSdk 23 缺少 API 26 符号。
 */
public class DownloadNotifier {

    private static final String CHANNEL_ID = "download_done";
    private static int sNextId = 10000;

    @SuppressWarnings("deprecation")
    public static void notifyDone(Context ctx, DownloadTask task, String filePath) {
        try {
            NotificationManager nm =
                    (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) {
                return;
            }

            Intent intent;
            if (filePath != null && filePath.length() > 0) {
                intent = new Intent(ctx, PlayerActivity.class);
                intent.putExtra("local_path", filePath);
                intent.putExtra("title", task.title);
                intent.putExtra("landscape", true);
            } else {
                intent = new Intent(ctx, DownloadActivity.class);
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) {
                flags |= 0x04000000; // FLAG_IMMUTABLE
            }
            int id = sNextId++;
            PendingIntent pi = PendingIntent.getActivity(ctx, id, intent, flags);

            String title = "下载完成";
            String text = task.title != null && task.title.length() > 0
                    ? task.title : task.fileName;

            Notification n;
            if (Build.VERSION.SDK_INT >= 16) {
                Notification.Builder b = new Notification.Builder(ctx)
                        .setSmallIcon(android.R.drawable.stat_sys_download_done)
                        .setContentTitle(title)
                        .setContentText(text)
                        .setAutoCancel(true)
                        .setContentIntent(pi);
                if (Build.VERSION.SDK_INT >= 26) {
                    createChannel(nm);
                    try {
                        b.getClass().getMethod("setChannel", String.class)
                                .invoke(b, CHANNEL_ID);
                    } catch (Throwable ignored) {
                    }
                }
                n = b.build();
            } else {
                n = new Notification(android.R.drawable.stat_sys_download_done, title,
                        System.currentTimeMillis());
                // 老系统（API < 16）用反射设置内容，新版 SDK 已移除该方法
                try {
                    Notification.class.getMethod("setLatestEventInfo", Context.class,
                            CharSequence.class, CharSequence.class, PendingIntent.class)
                            .invoke(n, ctx, title, text, pi);
                } catch (Throwable ignored) {
                }
                n.flags |= Notification.FLAG_AUTO_CANCEL;
            }
            nm.notify(id, n);
        } catch (Throwable ignored) {
        }
    }

    /** 反射创建通知渠道（API 26+） */
    private static void createChannel(NotificationManager nm) {
        try {
            Class<?> cls = Class.forName("android.app.NotificationChannel");
            Object channel = cls.getConstructor(String.class, CharSequence.class, int.class)
                    .newInstance(CHANNEL_ID, "下载完成", 2);
            NotificationManager.class
                    .getMethod("createNotificationChannel", cls)
                    .invoke(nm, channel);
        } catch (Throwable ignored) {
        }
    }
}
