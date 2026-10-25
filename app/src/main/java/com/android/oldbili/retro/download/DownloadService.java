package com.android.oldbili.retro.download;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import com.android.oldbili.retro.DownloadActivity;
import com.android.oldbili.retro.R;

/**
 * 下载后台服务：挂着前台通知，进程被系统回收时也能把下载跑完。
 */
public class DownloadService extends Service {

    private static final int NOTIFY_ID = 1001;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        DownloadManager.get(this).addListener(mListener);
        startForeground(NOTIFY_ID, buildNotification(null));
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        DownloadManager.get(this).removeListener(mListener);
        super.onDestroy();
    }

    private final DownloadManager.Listener mListener = new DownloadManager.Listener() {
        @Override
        public void onTaskChanged(DownloadTask task) {
            updateNotification();
        }

        @Override
        public void onTaskRemoved(DownloadTask task) {
            updateNotification();
        }
    };

    private long mLastNotifyTime;

    private void updateNotification() {
        // 双层节流：即便进度事件再多，通知刷新也控制在 1 秒以内，
        // 防止 2.x 老设备状态栏被高频 notify 刷爆导致整机卡死
        long now = System.currentTimeMillis();
        if (now - mLastNotifyTime < 900) {
            return;
        }
        mLastNotifyTime = now;
        // 统计正在下的任务
        DownloadTask active = null;
        int activeCount = 0;
        int doneCount = 0;
        for (DownloadTask task : DownloadManager.get(this).getTasks()) {
            if (task.state == DownloadTask.STATE_DOWNLOADING) {
                if (active == null) {
                    active = task;
                }
                activeCount++;
            } else if (task.state == DownloadTask.STATE_DONE) {
                doneCount++;
            }
        }
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (active == null) {
            stopForeground(true);
            return;
        }
        nm.notify(NOTIFY_ID, buildNotification(active));
    }

    private Notification buildNotification(DownloadTask task) {
        Intent intent = new Intent(this, DownloadActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        String title = "OldBiliRetro 下载";
        String text = task != null ? task.title : "正在下载…";

        // 安卓3.0+ 用 Notification.Builder；2.x 用老式通知（setLatestEventInfo）
        if (Build.VERSION.SDK_INT >= 11) {
            Notification.Builder builder = new Notification.Builder(this)
                    .setSmallIcon(R.drawable.ic_download)
                    .setContentTitle(title)
                    .setContentIntent(pi)
                    .setOngoing(true)
                    .setContentText(text);
            if (task != null) {
                builder.setProgress(100, task.percent(), false);
            }
            // 安卓8+ 必须挂通知渠道。compileSdk 停在 23，渠道用反射创建，保持老工具链干净
            if (Build.VERSION.SDK_INT >= 26) {
                try {
                    NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                    Class<?> channelClass = Class.forName("android.app.NotificationChannel");
                    Object channel = channelClass.getConstructor(String.class, CharSequence.class, int.class)
                            .newInstance("download", "下载", 2); // IMPORTANCE_LOW
                    nm.getClass().getMethod("createNotificationChannel", channelClass).invoke(nm, channel);
                    builder.getClass().getMethod("setChannelId", String.class).invoke(builder, "download");
                } catch (Exception ignored) {
                }
            }
            return builder.build();
        }

        Notification legacy = new Notification(R.drawable.ic_download, text, System.currentTimeMillis());
        legacy.flags |= Notification.FLAG_ONGOING_EVENT;
        // setLatestEventInfo 在新 SDK 里是隐藏接口，老设备上通过反射调用（2.x 上一定存在）
        try {
            java.lang.reflect.Method method = Notification.class.getMethod("setLatestEventInfo",
                    Context.class, CharSequence.class, CharSequence.class, PendingIntent.class);
            method.invoke(legacy, this, title, text, pi);
        } catch (Exception ignored) {
        }
        return legacy;
    }
}
