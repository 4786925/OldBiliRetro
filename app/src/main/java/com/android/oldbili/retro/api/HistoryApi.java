package com.android.oldbili.retro.api;

import com.android.oldbili.retro.model.HistoryItem;
import com.android.oldbili.retro.model.VideoCard;
import com.android.oldbili.retro.net.HttpUtil;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 播放历史与稍后再看。
 */
public class HistoryApi {

    /** 播放历史（游标分页），返回 [列表, 下一页游标(可为0)] */
    public static Object[] getHistory(long cursorMax, long cursorViewAt) throws IOException, org.json.JSONException {
        List<HistoryItem> list = new ArrayList<HistoryItem>();
        StringBuilder url = new StringBuilder();
        url.append("https://api.bilibili.com/x/web-interface/history/cursor?type=archive&ps=20&platform=pc");
        if (cursorMax > 0) {
            url.append("&max=").append(cursorMax);
        }
        if (cursorViewAt > 0) {
            url.append("&view_at=").append(cursorViewAt);
        }
        JSONObject data = BiliApi.check(HttpUtil.getJsonWithLogin(url.toString()));
        JSONArray items = data.optJSONArray("list");
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                HistoryItem h = new HistoryItem();
                h.title = item.optString("title", "");
                h.cover = item.optString("cover", "");
                h.progress = item.optInt("progress", 0);
                h.duration = item.optInt("duration", 0);
                h.viewAt = item.optLong("view_at", 0);
                h.upName = item.optString("author_name", "");
                JSONObject history = item.optJSONObject("history");
                if (history != null) {
                    h.aid = history.optLong("oid", 0);
                    h.cid = history.optLong("cid", 0);
                    h.bvid = history.optString("bvid", "");
                    h.kid = history.optString("kid", "");
                }
                if (h.aid > 0) {
                    list.add(h);
                }
            }
        }
        JSONObject cursor = data.optJSONObject("cursor");
        long nextMax = 0;
        long nextViewAt = 0;
        if (cursor != null) {
            nextMax = cursor.optLong("max", 0);
            nextViewAt = cursor.optLong("view_at", 0);
        }
        return new Object[]{list, nextMax, nextViewAt};
    }

    /** 上报播放进度（秒） */
    public static void report(long aid, long cid, int progressSec) {
        try {
            String body = "aid=" + aid + "&cid=" + cid + "&progress=" + progressSec
                    + "&platform=android&csrf=" + HttpUtil.csrf();
            HttpUtil.postWithLogin("https://api.bilibili.com/x/v2/history/report", body);
        } catch (Exception e) {
            android.util.Log.w("HistoryApi", "上报进度失败: " + e.getMessage());
        }
    }

    /** 删除单条历史（kid 为历史记录 id） */
    public static int delHistory(String kid) throws IOException, org.json.JSONException {
        if (kid == null || kid.length() == 0) {
            return -1;
        }
        String body = "kid=" + HttpUtil.encode(kid) + "&csrf=" + HttpUtil.csrf();
        String resp = HttpUtil.postWithLogin("https://api.bilibili.com/x/v2/history/delete", body);
        return new org.json.JSONObject(new org.json.JSONTokener(resp)).optInt("code", -1);
    }

    /** 稍后再看列表 */
    public static List<VideoCard> getToView() throws IOException, org.json.JSONException {
        List<VideoCard> list = new ArrayList<VideoCard>();
        JSONObject data = BiliApi.check(HttpUtil.getJsonWithLogin(
                "https://api.bilibili.com/x/v2/history/toview/web"));
        JSONArray items = data.optJSONArray("list");
        if (items == null) {
            return list;
        }
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) {
                continue;
            }
            VideoCard card = new VideoCard();
            card.aid = item.optLong("aid", 0);
            card.bvid = item.optString("bvid", "");
            card.cid = item.optLong("cid", 0);
            card.title = item.optString("title", "");
            card.cover = item.optString("pic", "");
            card.duration = item.optInt("duration", 0);
            JSONObject stat = item.optJSONObject("stat");
            if (stat != null) {
                card.play = stat.optLong("view", 0);
                card.danmaku = stat.optLong("danmaku", 0);
            }
            JSONObject owner = item.optJSONObject("owner");
            if (owner != null) {
                card.upMid = owner.optLong("mid", 0);
                card.upName = owner.optString("name", "");
            }
            list.add(card);
        }
        return list;
    }

    /** 加入稍后再看 */
    public static int addToView(long aid) throws IOException, org.json.JSONException {
        String resp = HttpUtil.postWithLogin("https://api.bilibili.com/x/v2/history/toview/add",
                "aid=" + aid + "&csrf=" + HttpUtil.csrf());
        return new JSONObject(new org.json.JSONTokener(resp)).optInt("code", -1);
    }

    /** 移出稍后再看 */
    public static int delToView(long aid) throws IOException, org.json.JSONException {
        String resp = HttpUtil.postWithLogin("https://api.bilibili.com/x/v2/history/toview/del",
                "viewed=true&aid=" + aid + "&csrf=" + HttpUtil.csrf());
        return new JSONObject(new org.json.JSONTokener(resp)).optInt("code", -1);
    }
}
