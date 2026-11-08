package com.android.oldbili.retro.notify;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import com.android.oldbili.retro.MainActivity;
import com.android.oldbili.retro.api.MsgUnreadApi;
import com.android.oldbili.retro.net.HttpUtil;
import com.android.oldbili.retro.util.Sp;

/**
 * 消息推送：AlarmManager 每 15 分钟唤醒一次，检查未读并弹通知。
 * 无常驻进程、无轮询线程休眠，适合 64MB / 96MB 的老设备。
 */
public class NotifyService extends Service {

    private static final int DEFAULT_INTERVAL_MIN = 5;

    private static int intervalMinutes() {
        int m = Sp.getInt("notify_interval_min", DEFAULT_INTERVAL_MIN);
        if (m < 1) {
            m = DEFAULT_INTERVAL_MIN;
        }
        if (m > 60) {
            m = 60;
        }
        return m;
    }

    private static long intervalMs() {
        return intervalMinutes() * 60L * 1000L;
    }
    private static final String CHANNEL_ID = "message_push";
    private static final int NOTIFY_ID = 20001;

    public static void schedule(Context ctx) {
        try {
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            if (am == null) {
                return;
            }
            Intent intent = new Intent(ctx, NotifyService.class);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) {
                flags |= 0x04000000; // FLAG_IMMUTABLE
            }
            PendingIntent pi = PendingIntent.getService(ctx, 0, intent, flags);
            long iv = intervalMs();
            am.setInexactRepeating(AlarmManager.RTC, System.currentTimeMillis() + iv, iv, pi);
        } catch (Throwable ignored) {
        }
    }

    public static void cancel(Context ctx) {
        try {
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            Intent intent = new Intent(ctx, NotifyService.class);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) {
                flags |= 0x04000000;
            }
            PendingIntent pi = PendingIntent.getService(ctx, 0, intent, flags);
            if (am != null) {
                am.cancel(pi);
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    check();
                } catch (Throwable ignored) {
                }
                stopSelf();
            }
        }).start();
        return START_NOT_STICKY;
    }

    private void check() {
        if (!HttpUtil.isLoggedIn()) {
            return;
        }
        boolean any = Sp.getBoolean(Sp.SET_NOTIFY_INTERACT, true)
                || Sp.getBoolean(Sp.SET_NOTIFY_FOLLOW, true)
                || Sp.getBoolean(Sp.SET_NOTIFY_RECOMMEND, false)
                || Sp.getBoolean(Sp.SET_NOTIFY_SUBSCRIBE, false)
                || Sp.getBoolean(Sp.SET_NOTIFY_OTHER, false);
        if (!any) {
            return;
        }
        MsgUnreadApi u;
        try {
            u = MsgUnreadApi.get();
        } catch (Exception e) {
            android.util.Log.w("NotifyService", "未读数获取失败: " + e.getMessage());
            return;
        }
        // 私信未读：msgfeed/unread 不含私信，需要额外查一次会话列表
        java.util.List<com.android.oldbili.retro.model.MessageSession> chats = null;
        int chatUnread = 0;
        try {
            com.android.oldbili.retro.net.HttpUtil.setQuickTimeout(5000, 6000);
            chats = com.android.oldbili.retro.api.MessageApi.getSessions();
            com.android.oldbili.retro.net.HttpUtil.clearQuickTimeout();
        } catch (Throwable e) {
            android.util.Log.w("NotifyService", "会话未读获取失败: " + e.getMessage());
        }
        if (chats != null) {
            for (int i = 0; i < chats.size(); i++) {
                chatUnread += Math.max(0, chats.get(i).unread);
            }
        }
        int interact = 0;
        if (Sp.getBoolean(Sp.SET_NOTIFY_INTERACT, true)) {
            interact = u.reply + u.at + u.like + chatUnread;
        }
        int follow = Sp.getBoolean(Sp.SET_NOTIFY_FOLLOW, true) ? u.up : 0;
        int recommend = Sp.getBoolean(Sp.SET_NOTIFY_RECOMMEND, false) ? u.recommend : 0;
        int subscribe = Sp.getBoolean(Sp.SET_NOTIFY_SUBSCRIBE, false) ? u.subscribe : 0;
        int other = Sp.getBoolean(Sp.SET_NOTIFY_OTHER, false) ? u.system : 0;

        int total = interact + follow + recommend + subscribe + other;
        if (total <= 0) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        if (interact > 0) {
            sb.append("互动 ").append(interact);
        }
        if (follow > 0) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append("关注更新 ").append(follow);
        }
        if (recommend > 0) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append("推荐 ").append(recommend);
        }
        if (subscribe > 0) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append("订阅 ").append(subscribe);
        }
        if (other > 0) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append("通知 ").append(other);
        }
        // 去重：未读数没变化且 30 分钟内已提醒过，就不再重复打扰
        long now = System.currentTimeMillis();
        int lastTotal = Sp.getInt("notify_last_total", -1);
        long lastTs = Sp.getLong("notify_last_ts", 0);
        if (total == lastTotal && now - lastTs < 30L * 60L * 1000L) {
            return;
        }
        Sp.putInt("notify_last_total", total);
        Sp.putLong("notify_last_ts", now);
        resolveTarget(u, interact, follow, recommend, subscribe, other, chats);
        String preview = Sp.getString("notify_text", "");
        String text = sb.toString();
        if (preview != null && preview.length() > 0) {
            text = preview;
        }
        show("你有 " + total + " 条新消息", text);
    }

    @SuppressWarnings("deprecation")
    private void show(String title, String text) {
        try {
            NotificationManager nm =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) {
                return;
            }
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) {
                flags |= 0x04000000;
            }
            PendingIntent pi = PendingIntent.getActivity(this, NOTIFY_ID, intent, flags);

            Notification n;
            if (Build.VERSION.SDK_INT >= 16) {
                Notification.Builder b = new Notification.Builder(this)
                        .setSmallIcon(android.R.drawable.stat_notify_chat)
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
                n = new Notification(android.R.drawable.stat_notify_chat, title,
                        System.currentTimeMillis());
                try {
                    Notification.class.getMethod("setLatestEventInfo", Context.class,
                            CharSequence.class, CharSequence.class, PendingIntent.class)
                            .invoke(n, this, title, text, pi);
                } catch (Throwable ignored) {
                }
                n.flags |= Notification.FLAG_AUTO_CANCEL;
            }
            nm.notify(NOTIFY_ID, n);
        } catch (Throwable ignored) {
        }
    }

    private void createChannel(NotificationManager nm) {
        try {
            Class<?> cls = Class.forName("android.app.NotificationChannel");
            Object channel = cls.getConstructor(String.class, CharSequence.class, int.class)
                    .newInstance(CHANNEL_ID, "消息通知", 3);
            NotificationManager.class
                    .getMethod("createNotificationChannel", cls)
                    .invoke(nm, channel);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * 记录点击通知要跳转的目标：
     * 回复/→评论（带 root_id + target_id）；点赞→对应视频；私信→对应会话。
     */
    private void resolveTarget(MsgUnreadApi u, int interact, int follow,
                               int recommend, int subscribe, int other,
                               java.util.List<com.android.oldbili.retro.model.MessageSession> chats) {
        try {
            Sp.putString("notify_kind", "");
            Sp.putString("notify_text", "");
            if (u.reply > 0) {
                java.util.List<com.android.oldbili.retro.model.MsgFeedItem> list =
                        com.android.oldbili.retro.api.MsgFeedApi.load(
                                com.android.oldbili.retro.api.MsgFeedApi.KIND_REPLY, 1);
                if (!list.isEmpty()) {
                    com.android.oldbili.retro.model.MsgFeedItem it = list.get(0);
                    Sp.putString("notify_kind", "reply");
                    Sp.putLong("notify_oid", it.sourceId);
                    Sp.putLong("notify_root", it.rootId);
                    Sp.putLong("notify_sub", it.targetId);
                    String who = it.userName == null || it.userName.length() == 0
                            ? "" : it.userName + "：";
                    String what = it.text == null ? "" : it.text;
                    if (what.length() > 40) {
                        what = what.substring(0, 40) + "…";
                    }
                    Sp.putString("notify_text", who + what);
                    return;
                }
            }
            if (u.at > 0) {
                java.util.List<com.android.oldbili.retro.model.MsgFeedItem> list =
                        com.android.oldbili.retro.api.MsgFeedApi.load(
                                com.android.oldbili.retro.api.MsgFeedApi.KIND_AT, 1);
                if (!list.isEmpty()) {
                    com.android.oldbili.retro.model.MsgFeedItem it = list.get(0);
                    Sp.putString("notify_kind", "reply");
                    Sp.putLong("notify_oid", it.sourceId);
                    Sp.putLong("notify_root", it.rootId);
                    Sp.putLong("notify_sub", it.targetId);
                    String who = it.userName == null || it.userName.length() == 0
                            ? "" : it.userName + "：";
                    String what = it.text == null ? "" : it.text;
                    if (what.length() > 40) {
                        what = what.substring(0, 40) + "…";
                    }
                    Sp.putString("notify_text", who + what);
                    return;
                }
            }
            if (u.like > 0) {
                java.util.List<com.android.oldbili.retro.model.MsgFeedItem> list =
                        com.android.oldbili.retro.api.MsgFeedApi.load(
                                com.android.oldbili.retro.api.MsgFeedApi.KIND_LIKE, 1);
                if (!list.isEmpty()) {
                    com.android.oldbili.retro.model.MsgFeedItem it = list.get(0);
                    if (it.uri != null && it.uri.length() > 0) {
                        Sp.putString("notify_kind", "uri");
                        Sp.putString("notify_uri", it.uri);
                        return;
                    }
                }
            }
            if (interact > 0) {
                // 私信：找第一个有未读的会话（复用 check() 已经拿到的列表）
                java.util.List<com.android.oldbili.retro.model.MessageSession> sessions = chats;
                if (sessions != null) {
                    for (int i = 0; i < sessions.size(); i++) {
                        com.android.oldbili.retro.model.MessageSession ss = sessions.get(i);
                        if (ss.unread > 0 && ss.uid > 0) {
                            Sp.putString("notify_kind", "chat");
                            Sp.putLong("notify_uid", ss.uid);
                            Sp.putString("notify_name", ss.name == null ? "" : ss.name);
                            Sp.putString("notify_face", ss.face == null ? "" : ss.face);
                            String who = ss.name == null || ss.name.length() == 0
                                    ? "有人" : ss.name;
                            String what = com.android.oldbili.retro.util.MsgText.preview(
                                    ss.lastMsg == null ? "" : ss.lastMsg);
                            if (what.length() > 40) {
                                what = what.substring(0, 40) + "…";
                            }
                            Sp.putString("notify_text", who + "：" + what);
                            return;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
