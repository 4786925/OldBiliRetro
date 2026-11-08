package com.android.oldbili.retro.api;

import com.android.oldbili.retro.model.MessageSession;
import com.android.oldbili.retro.model.PrivateMessage;
import com.android.oldbili.retro.model.UserInfo;
import com.android.oldbili.retro.net.HttpUtil;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 私信：会话列表、聊天记录、发送（api.vc.bilibili.com IM 接口）。
 */
public class MessageApi {

    /** 会话列表（缺昵称头像的会话用名片缓存补齐，带本地缓存防止反复请求被风控） */
    public static List<MessageSession> getSessions() throws IOException, org.json.JSONException {
        return getSessions(0);
    }

    /** 会话列表翻页：endTs 传上一批最小 timestamp，0 表示第一页 */
    public static List<MessageSession> getSessions(long endTs) throws IOException, org.json.JSONException {
        List<MessageSession> list = new ArrayList<MessageSession>();
        String url = "https://api.vc.bilibili.com/session_svr/v1/session_svr/get_sessions"
                + "?session_type=1&group_fold=1&unfollow_fold=0&sort_rule=2&build=0&mobi_app=web&size=100";
        if (endTs > 0) {
            url += "&end_ts=" + endTs;
        }
        JSONObject data = BiliApi.check(new JSONObject(new org.json.JSONTokener(HttpUtil.vcGet(url, true))));
        JSONArray sessions = data.optJSONArray("session_list");
        if (sessions == null) {
            return list;
        }
        for (int i = 0; i < sessions.length(); i++) {
            JSONObject s = sessions.optJSONObject(i);
            if (s == null) {
                continue;
            }
            MessageSession session = new MessageSession();
            session.kind = MessageSession.KIND_CHAT;
            session.uid = s.optLong("talker_id", 0);
            session.unread = s.optInt("unread_count", 0);
            // 服务端置顶：top_ts 是微秒时间戳，非 0 表示置顶
            session.top = s.optLong("top_ts", 0) > 0;
            JSONObject acc = s.optJSONObject("account_info");
            if (acc != null) {
                session.name = acc.optString("name", "");
                session.face = acc.optString("face", "");
            }
            JSONObject last = s.optJSONObject("last_msg");
            if (last != null) {
                session.time = last.optLong("timestamp", 0);
                String content = last.optString("content", "");
                session.lastMsg = parseContent(content);
            }
            if (session.uid > 0) {
                list.add(session);
            }
        }
        // 批量补昵称/头像（一次 50 个），把首次加载从上百次请求降到 2 次
        fillNamesBatch(list, 2);
        return list;
    }

    /**
     * 批量补名字：account/v1/user/cards。返回的 data 既可能是数组，
     * 也可能是 {uid: {...}} 的对象，两种都兼容。
     */
    private static void fillNamesBatch(List<MessageSession> list, int maxBatch) {
        List<Long> uids = new ArrayList<Long>();
        for (int i = 0; i < list.size(); i++) {
            MessageSession s = list.get(i);
            if (s.uid <= 0) {
                continue;
            }
            if (s.name.length() > 0 && s.face.length() > 0) {
                continue;
            }
            com.android.oldbili.retro.model.UserInfo c = readCardCache(s.uid);
            if (c != null) {
                if (s.name.length() == 0) {
                    s.name = c.name;
                }
                if (s.face.length() == 0) {
                    s.face = c.face;
                }
                if (s.name.length() > 0 && s.face.length() > 0) {
                    continue;
                }
            }
            uids.add(Long.valueOf(s.uid));
            if (uids.size() >= 100) {
                break;
            }
        }
        int batch = 0;
        int from = 0;
        while (batch < maxBatch && from < uids.size()) {
            int to = Math.min(from + 50, uids.size());
            StringBuilder sb = new StringBuilder();
            for (int i = from; i < to; i++) {
                if (sb.length() > 0) {
                    sb.append(',');
                }
                sb.append(uids.get(i).longValue());
            }
            from = to;
            batch++;
            try {
                String url = "https://api.vc.bilibili.com/account/v1/user/cards?uids=" + sb
                        + "&build=0&mobi_app=web";
                JSONObject data = BiliApi.check(new JSONObject(
                        new org.json.JSONTokener(HttpUtil.vcGet(url, true))));
                JSONArray cards = data.optJSONArray("data");
                if (cards == null) {
                    cards = data.optJSONArray("cards");
                }
                JSONObject cardMap = null;
                if (cards == null) {
                    cardMap = data.optJSONObject("data");
                    if (cardMap == null) {
                        cardMap = data.optJSONObject("cards");
                    }
                }
                if (cards == null && cardMap == null) {
                    android.util.Log.w("MessageApi", "名片批量返回异常: " + data);
                    break;
                }
                java.util.List<JSONObject> items = new java.util.ArrayList<JSONObject>();
                if (cards != null) {
                    for (int i = 0; i < cards.length(); i++) {
                        JSONObject c = cards.optJSONObject(i);
                        if (c != null) {
                            items.add(c);
                        }
                    }
                } else {
                    java.util.Iterator<String> it = cardMap.keys();
                    while (it.hasNext()) {
                        String k = it.next();
                        JSONObject c = cardMap.optJSONObject(k);
                        if (c != null) {
                            items.add(c);
                        }
                    }
                }
                for (int i = 0; i < items.size(); i++) {
                    JSONObject c = items.get(i);
                    long mid = c.optLong("mid", 0);
                    String name = c.optString("name", "");
                    String face = c.optString("face", "");
                    if (mid <= 0 || name.length() == 0) {
                        continue;
                    }
                    writeCardCache(mid, name, face);
                    for (int j = 0; j < list.size(); j++) {
                        MessageSession s = list.get(j);
                        if (s.uid != mid) {
                            continue;
                        }
                        if (s.name.length() == 0) {
                            s.name = name;
                        }
                        if (s.face.length() == 0) {
                            s.face = face;
                        }
                    }
                }
            } catch (Throwable e) {
                android.util.Log.w("MessageApi", "批量补名字失败: " + e.getMessage());
            }
        }
    }

    /**
     * 昵称头像补齐：优先本地名片缓存（Sp），没有才请求名片接口，
     * 并且同一批补齐之间加间隔，避免连续请求被打成风控。
     */
    /**
     * 并发补剩下的昵称：单线程逐个请求太慢（首次登录上百个会话），
     * 这里用 8 个线程并发，几秒内补齐，补完由调用方刷新列表。
     */
    public static void fillRemainingNames(List<MessageSession> list, int max) {
        java.util.List<MessageSession> todo = new java.util.ArrayList<MessageSession>();
        for (int i = 0; i < list.size() && todo.size() < max; i++) {
            MessageSession s = list.get(i);
            if (s.uid > 0 && s.name.length() == 0) {
                todo.add(s);
            }
        }
        if (todo.isEmpty()) {
            return;
        }
        int threads = android.os.Build.VERSION.SDK_INT <= 10 ? 2 : 8;
        final java.util.concurrent.ExecutorService pool =
                java.util.concurrent.Executors.newFixedThreadPool(threads);
        final java.util.concurrent.CountDownLatch latch =
                new java.util.concurrent.CountDownLatch(todo.size());
        for (int i = 0; i < todo.size(); i++) {
            final MessageSession s = todo.get(i);
            pool.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        fillNameFace(s);
                        if (s.name.length() > 0) {
                            writeCardCache(s.uid, s.name, s.face);
                        }
                    } catch (Throwable ignored) {
                    } finally {
                        latch.countDown();
                    }
                }
            });
        }
        try {
            latch.await(60, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
        }
        pool.shutdownNow();
    }

    private static void fillNameFace(MessageSession session) {
        if (session.name.length() > 0 && session.face.length() > 0) {
            return;
        }
        com.android.oldbili.retro.model.UserInfo cached = readCardCache(session.uid);
        if (cached != null) {
            if (session.name.length() == 0 && cached.name.length() > 0) {
                session.name = cached.name;
            }
            if (session.face.length() == 0 && cached.face.length() > 0) {
                session.face = cached.face;
            }
            if (session.name.length() > 0) {
                return;
            }
        }
        com.android.oldbili.retro.model.UserInfo card = getUserCard(session.uid);
        if (card != null && (card.name.length() > 0 || card.face.length() > 0)) {
            writeCardCache(session.uid, card.name, card.face);
            if (session.name.length() == 0) {
                session.name = card.name;
            }
            if (session.face.length() == 0) {
                session.face = card.face;
            }
            try {
                Thread.sleep(250);
            } catch (InterruptedException ignored) {
            }
        }
    }

    // ---- 名片缓存（uid -> name|face，Sp 存 JSON，7 天有效） ----

    private static com.android.oldbili.retro.model.UserInfo readCardCache(long uid) {
        try {
            JSONObject root = new JSONObject(new org.json.JSONTokener(
                    com.android.oldbili.retro.util.Sp.getString("msg_card_cache", "{}")));
            String key = String.valueOf(uid);
            JSONObject item = root.optJSONObject(key);
            if (item != null && item.optString("n", "").length() > 0
                    && System.currentTimeMillis() - item.optLong("t", 0) < 7L * 24 * 3600 * 1000) {
                com.android.oldbili.retro.model.UserInfo info = new com.android.oldbili.retro.model.UserInfo();
                info.mid = uid;
                info.name = item.optString("n", "");
                info.face = item.optString("f", "");
                return info;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static void writeCardCache(long uid, String name, String face) {
        try {
            JSONObject root = new JSONObject(new org.json.JSONTokener(
                    com.android.oldbili.retro.util.Sp.getString("msg_card_cache", "{}")));
            JSONObject item = new JSONObject();
            item.put("n", name == null ? "" : name);
            item.put("f", face == null ? "" : face);
            item.put("t", System.currentTimeMillis());
            root.put(String.valueOf(uid), item);
            com.android.oldbili.retro.util.Sp.putString("msg_card_cache", root.toString());
        } catch (Exception ignored) {
        }
    }

    /**
     * 特别关注更新（消息里的"特别关注"分组）。
     * 网页端结构：data.items[]{ bvid,title,cover,owner{mid,name,face},publish_time }
     * 兼容：data.list / items 下的 video{} 包裹结构。
     */
    public static List<MessageSession> getFollowUp() {
        List<MessageSession> list = new ArrayList<MessageSession>();
        try {
            JSONObject data = BiliApi.check(HttpUtil.getJsonWithLogin(
                    "https://api.bilibili.com/x/web-feed/follow-up?type=1&pn=1&ps=10"));
            JSONArray items = data.optJSONArray("items");
            if (items == null) {
                items = data.optJSONArray("list");
            }
            if (items != null) {
                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.optJSONObject(i);
                    if (item == null) {
                        continue;
                    }
                    JSONObject video = item.optJSONObject("video");
                    if (video != null) {
                        item = video;
                    }
                    MessageSession s = new MessageSession();
                    s.kind = MessageSession.KIND_FOLLOW_UP;
                    s.bvid = item.optString("bvid", "");
                    s.videoTitle = item.optString("title", "");
                    s.videoCover = item.optString("cover", item.optString("pic", ""));
                    s.time = item.optLong("publish_time", item.optLong("pubtime", 0));
                    JSONObject owner = item.optJSONObject("owner");
                    if (owner != null) {
                        s.uid = owner.optLong("mid", 0);
                        s.name = owner.optString("name", "");
                        s.face = owner.optString("face", "");
                    }
                    if (s.bvid.length() > 0 || s.uid > 0) {
                        list.add(s);
                    }
                }
            }
        } catch (Exception e) {
            android.util.Log.w("MessageApi", "特别关注获取失败: " + e.getMessage());
        }
        return list;
    }

    /** 删除会话（接口失败由调用方本地兜底） */
    public static int removeSession(long talkerId) {
        try {
            String body = "talker_id=" + talkerId + "&session_type=1"
                    + "&csrf=" + HttpUtil.csrf() + "&csrf_token=" + HttpUtil.csrf()
                    + "&build=0&mobi_app=web";
            String resp = HttpUtil.vcPost(
                    "https://api.vc.bilibili.com/session_svr/v1/session_svr/remove_session", body, true);
            return new JSONObject(new org.json.JSONTokener(resp)).optInt("code", -1);
        } catch (Exception e) {
            return -1;
        }
    }

    /** 标记会话已读 */
    public static int updateAck(long talkerId) {
        try {
            String body = "talker_id=" + talkerId + "&session_type=1&ack_seqno=0"
                    + "&csrf_token=" + HttpUtil.csrf() + "&csrf=" + HttpUtil.csrf()
                    + "&build=0&mobi_app=web";
            String resp = HttpUtil.vcPost(
                    "https://api.vc.bilibili.com/session_svr/v1/session_svr/update_ack", body, true);
            return new JSONObject(new org.json.JSONTokener(resp)).optInt("code", -1);
        } catch (Exception e) {
            return -1;
        }
    }

    /** 与某人的聊天记录（时间正序）。endSeqno>0 时取该序号之前的更早消息（翻页） */
    public static List<PrivateMessage> getMessages(long talkerId, long endSeqno) throws IOException, org.json.JSONException {
        List<PrivateMessage> list = new ArrayList<PrivateMessage>();
        String url = "https://api.vc.bilibili.com/svr_sync/v1/svr_sync/fetch_session_msgs"
                + "?session_type=1&talker_id=" + talkerId + "&size=50&build=0&mobi_app=web";
        if (endSeqno > 0) {
            url += "&end_seqno=" + endSeqno;
        }
        JSONObject data = BiliApi.check(new JSONObject(new org.json.JSONTokener(HttpUtil.vcGet(url, true))));
        JSONArray messages = data.optJSONArray("messages");
        if (messages == null) {
            return list;
        }
        // 接口返回倒序，这里正过来方便聊天页追加
        for (int i = messages.length() - 1; i >= 0; i--) {
            JSONObject m = messages.optJSONObject(i);
            if (m == null) {
                continue;
            }
            PrivateMessage msg = new PrivateMessage();
            msg.fromUid = m.optLong("sender_uid", 0);
            msg.time = m.optLong("timestamp", 0);
            msg.seqno = m.optLong("msg_seqno", 0);
            msg.text = parseContent(m.optString("content", ""));
            list.add(msg);
        }
        return list;
    }

    /** IM 的 content 字段是 {"content":"文字"} 样式的 JSON，剥壳取文字 */
    private static String parseContent(String content) {
        if (content == null || content.length() == 0) {
            return "";
        }
        if (content.startsWith("{") && content.endsWith("}")) {
            try {
                JSONObject obj = new JSONObject(new org.json.JSONTokener(content));
                String text = obj.optString("content", "");
                if (text.length() > 0) {
                    return text;
                }
                JSONArray items = obj.optJSONArray("content");
                if (items != null) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject item = items.optJSONObject(i);
                        if (item != null) {
                            String t = item.optString("text", "");
                            if (t.length() > 0) {
                                sb.append(t);
                            }
                        }
                    }
                    if (sb.length() > 0) {
                        return sb.toString();
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return content;
    }

    /** 发送私信，返回接口 code */
    public static int sendMessage(long myUid, long receiverId, String text) throws IOException, org.json.JSONException {
        String contentJson;
        try {
            JSONObject obj = new JSONObject();
            obj.put("content", text);
            contentJson = obj.toString();
        } catch (Exception e) {
            contentJson = "{\"content\":\"" + text + "\"}";
        }
        String url = "https://api.vc.bilibili.com/web_im/v1/web_im/send_msg?"
                + "msg[dev_id]=" + System.currentTimeMillis()
                + "&msg[msg_type]=1"
                + "&msg[content]=" + HttpUtil.encode(contentJson)
                + "&msg[receiver_type]=1"
                + "&msg[sender_uid]=" + myUid
                + "&msg[receiver_id]=" + receiverId
                + "&msg[timestamp]=" + (System.currentTimeMillis() / 1000)
                + "&msg[msg_status]=0"
                + "&csrf=" + HttpUtil.csrf()
                + "&csrf_token=" + HttpUtil.csrf();
        String resp = HttpUtil.vcPost(url, "", true);
        return new JSONObject(new org.json.JSONTokener(resp)).optInt("code", -1);
    }

    /** 批量补全用户信息（会话列表里 account_info 可能缺失） */
    public static UserInfo getUserCard(long uid) {
        try {
            return UserApi.getUserCard(uid);
        } catch (Exception e) {
            return null;
        }
    }
}
