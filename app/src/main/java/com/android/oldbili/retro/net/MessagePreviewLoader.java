package com.android.oldbili.retro.net;

import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import com.android.oldbili.retro.api.MessageApi;
import com.android.oldbili.retro.model.PrivateMessage;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 会话列表的「最后一条消息预览」懒加载。
 * B 站会话接口对折叠/未关注会话不下发 last_msg，只能按会话再取一次消息，
 * 这里做单线程队列 + 内存缓存，避免刷屏时打爆接口。
 */
public class MessagePreviewLoader {

    private static final ConcurrentHashMap<Long, String> CACHE =
            new ConcurrentHashMap<Long, String>();
    private static final ExecutorService POOL = Executors.newSingleThreadExecutor();
    private static final Handler UI = new Handler(Looper.getMainLooper());

    public static String cached(long uid) {
        String v = CACHE.get(uid);
        return v == null ? "" : v;
    }

    public static void load(final long uid, final TextView target) {
        String hit = CACHE.get(uid);
        if (hit != null && hit.length() > 0) {
            target.setText(hit);
            return;
        }
        POOL.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    List<PrivateMessage> list = MessageApi.getMessages(uid, 0);
                    String text = "";
                    if (list != null && !list.isEmpty()) {
                        // getMessages 已按时间正序返回（旧→新），最新一条在末尾
                        PrivateMessage latest = list.get(0);
                        for (int i = 0; i < list.size(); i++) {
                            PrivateMessage m = list.get(i);
                            if (m != null && m.time >= latest.time) {
                                latest = m;
                            }
                        }
                        text = latest.text == null ? "" : latest.text;
                    }
                    if (text == null || text.length() == 0) {
                        text = "[消息]";
                    }
                    CACHE.put(uid, text);
                    final String finalText = text;
                    UI.post(new Runnable() {
                        @Override
                        public void run() {
                            target.setText(finalText);
                        }
                    });
                } catch (Throwable ignored) {
                }
            }
        });
    }
}
