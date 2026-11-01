package com.android.oldbili.retro.api;

import com.android.oldbili.retro.model.MsgFeedItem;
import com.android.oldbili.retro.net.HttpUtil;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 消息中心的互动消息：回复我的 / @我 / 收到的赞 / 系统通知。
 * 全部需要登录（SESSDATA），匿名会返回 -101。
 */
public class MsgFeedApi {

    /** 1=回复 2=@我 3=收到的赞 4=系统通知 */
    public static final int KIND_REPLY = 1;
    public static final int KIND_AT = 2;
    public static final int KIND_LIKE = 3;
    public static final int KIND_SYSTEM = 4;

    public static List<MsgFeedItem> load(int kind, int page)
            throws IOException, org.json.JSONException {
        String path;
        switch (kind) {
            case KIND_AT:
                path = "at";
                break;
            case KIND_LIKE:
                path = "like";
                break;
            case KIND_SYSTEM:
                path = "system";
                break;
            default:
                path = "reply";
                break;
        }
        String url = "https://api.bilibili.com/x/msgfeed/" + path
                + "?platform=web&build=0&mobi_app=web&pn=" + (page <= 0 ? 1 : page) + "&ps=20";
        JSONObject data = BiliApi.check(HttpUtil.getJsonWithLogin(url));
        JSONArray items = data.optJSONArray("items");
        List<MsgFeedItem> list = new ArrayList<MsgFeedItem>();
        if (items == null) {
            return list;
        }
        for (int i = 0; i < items.length(); i++) {
            JSONObject o = items.optJSONObject(i);
            if (o == null) {
                continue;
            }
            MsgFeedItem item = new MsgFeedItem();
            item.id = o.optLong("id", 0);
            JSONObject user = o.optJSONObject("user");
            if (user != null) {
                item.mid = user.optLong("mid", 0);
                item.userName = user.optString("nickname", "");
                item.avatar = user.optString("avatar", "");
            }
            JSONObject inner = o.optJSONObject("item");
            if (inner != null) {
                item.uri = inner.optString("uri", "");
                item.rootId = inner.optLong("root_id", 0);
                item.targetId = inner.optLong("target_id", 0);
                item.sourceId = inner.optLong("subject_id", 0);
                // native_uri 里也带评论 id，兜底解析
                String nativeUri = inner.optString("native_uri", "");
                if (item.rootId <= 0 && nativeUri.length() > 0) {
                    java.util.regex.Matcher mm = java.util.regex.Pattern
                            .compile("comment_root_id=(\\d+)").matcher(nativeUri);
                    if (mm.find()) {
                        item.rootId = Long.parseLong(mm.group(1));
                    }
                }
                if (item.sourceId <= 0 && nativeUri.length() > 0) {
                    java.util.regex.Matcher mm = java.util.regex.Pattern
                            .compile("bilibili://video/(\\d+)").matcher(nativeUri);
                    if (mm.find()) {
                        item.sourceId = Long.parseLong(mm.group(1));
                    }
                }
                item.title = inner.optString("title", "");
                if (item.title.length() == 0) {
                    item.title = inner.optString("source_content", "");
                }
                // 别人回复/提到我的具体内容（真实字段）
                item.text = inner.optString("source_content", "");
                if (item.text.length() == 0) {
                    item.text = inner.optString("target_reply_content", "");
                }
                if (item.text.length() == 0) {
                    item.text = inner.optString("root_reply_content", "");
                }
                JSONObject rc = inner.optJSONObject("reply_content");
                if (item.text.length() == 0 && rc != null) {
                    item.text = rc.optString("message", "");
                }
                if (item.text.length() == 0) {
                    item.text = inner.optString("title", "");
                }
            }
            // 时间字段各类型不统一，逐个兜底（reply 类型用 reply_time）
            long t = o.optLong("reply_time", 0);
            if (t <= 0) {
                t = o.optLong("ctime", 0);
            }
            if (t <= 0) {
                t = o.optLong("time", 0);
            }
            if (t <= 0) {
                t = o.optLong("timestamp", 0);
            }
            if (t <= 0 && inner != null) {
                t = inner.optLong("ctime", 0);
                if (t <= 0) {
                    t = inner.optLong("time", 0);
                }
            }
            if (t > 100000000000L) {
                t = t / 1000L; // 毫秒转秒
            }
            // 有些类型的 source_content 是一段 JSON，取出可读文字
            if (item.text != null && item.text.trim().startsWith("{")) {
                try {
                    org.json.JSONObject tj = new org.json.JSONObject(item.text);
                    String[] keys = {"content", "title", "desc", "source_content",
                            "root_reply_content", "target_reply_content", "summary"};
                    for (int k = 0; k < keys.length; k++) {
                        String v = tj.optString(keys[k], "");
                        if (v.length() > 0 && !v.startsWith("{")) {
                            item.text = v;
                            break;
                        }
                    }
                } catch (Exception ignored) {
                }
            }
            item.time = t;
            item.timeDesc = t > 0
                    ? com.android.oldbili.retro.util.TimeUtil.toRelative(t) : "";
            list.add(item);
        }
        return list;
    }
}
