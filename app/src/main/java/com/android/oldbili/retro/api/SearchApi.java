package com.android.oldbili.retro.api;

import com.android.oldbili.retro.model.UserInfo;
import com.android.oldbili.retro.model.VideoCard;
import com.android.oldbili.retro.net.HttpUtil;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 搜索：视频、用户，支持 AV/BV 直达。
 */
public class SearchApi {

    /** 搜索视频（WBI，参数对齐网页端：pagesize 不是 page_size） */
    public static List<VideoCard> searchVideo(String keyword, int page) throws IOException, org.json.JSONException {
        List<VideoCard> list = new ArrayList<VideoCard>();
        String url = BiliApi.signWbi("https://api.bilibili.com/x/web-interface/wbi/search/type?"
                + "search_type=video&keyword=" + HttpUtil.encode(keyword)
                + "&page=" + page + "&pagesize=20");
        JSONObject data = BiliApi.check(HttpUtil.getJson(url));
        JSONArray result = data.optJSONArray("result");
        if (result == null) {
            return list;
        }
        for (int i = 0; i < result.length(); i++) {
            JSONObject item = result.optJSONObject(i);
            if (item == null) {
                continue;
            }
            if ("video".equals(item.optString("type", ""))) {
                VideoCard card = new VideoCard();
                card.aid = item.optLong("aid", 0);
                card.bvid = item.optString("bvid", "");
                // 搜索接口标题带高亮 HTML（<em class="keyword">xx</em>），剥掉再显示
                card.title = stripHtml(item.optString("title", ""));
                card.cover = item.optString("pic", "");
                card.duration = parseDuration(item.optString("duration", ""));
                card.play = item.optLong("play", 0);
                card.danmaku = item.optLong("video_review", 0);
                card.upMid = item.optLong("mid", 0);
                card.upName = item.optString("author", "");
                list.add(card);
            }
        }
        return list;
    }

    /** 去掉高亮标签与 HTML 实体（<em class="keyword"> 等） */
    private static String stripHtml(String s) {        if (s == null || s.length() == 0) {
            return "";
        }
        // 去标签
        StringBuilder sb = new StringBuilder();
        boolean inTag = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '<') {
                inTag = true;
            } else if (c == '>') {
                inTag = false;
            } else if (!inTag) {
                sb.append(c);
            }
        }
        String out = sb.toString();
        // 反转义常见实体
        out = out.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ");
        return out;
    }

    /** 搜索用户（WBI，参数对齐网页端：pagesize 不是 page_size） */
    public static List<UserInfo> searchUser(String keyword, int page) throws IOException, org.json.JSONException {
        List<UserInfo> list = new ArrayList<UserInfo>();
        String url = BiliApi.signWbi("https://api.bilibili.com/x/web-interface/wbi/search/type?"
                + "search_type=bili_user&keyword=" + HttpUtil.encode(keyword)
                + "&page=" + page + "&pagesize=20");
        JSONObject data = BiliApi.check(HttpUtil.getJson(url));
        JSONArray result = data.optJSONArray("result");
        if (result == null) {
            return list;
        }
        for (int i = 0; i < result.length(); i++) {
            JSONObject item = result.optJSONObject(i);
            if (item == null) {
                continue;
            }
            if ("bili_user".equals(item.optString("type", ""))) {
                UserInfo user = new UserInfo();
                user.mid = item.optLong("mid", 0);
                user.name = item.optString("uname", "");
                user.face = item.optString("upic", "");
                user.sign = item.optString("usign", "");
                user.fans = item.optLong("fans", 0);
                list.add(user);
            }
        }
        return list;
    }

    /** 搜索结果的 "mm:ss" 时长字符串转秒 */
    private static int parseDuration(String text) {
        if (text == null || text.length() == 0) {
            return 0;
        }
        try {
            String[] parts = text.split(":");
            if (parts.length == 3) {
                return Integer.parseInt(parts[0]) * 3600
                        + Integer.parseInt(parts[1]) * 60
                        + Integer.parseInt(parts[2]);
            }
            if (parts.length == 2) {
                return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    /** 搜索番剧（media_bangumi）/ 影视（media_ft） */
    public static List<com.android.oldbili.retro.model.BangumiCard> searchBangumi(String keyword, int page, boolean tv)
            throws IOException, org.json.JSONException {
        List<com.android.oldbili.retro.model.BangumiCard> list =
                new ArrayList<com.android.oldbili.retro.model.BangumiCard>();
        String url = BiliApi.signWbi("https://api.bilibili.com/x/web-interface/wbi/search/type?"
                + "search_type=" + (tv ? "media_ft" : "media_bangumi")
                + "&keyword=" + HttpUtil.encode(keyword)
                + "&page=" + page + "&pagesize=20");
        JSONObject data = BiliApi.check(HttpUtil.getJson(url));
        JSONArray result = data.optJSONArray("result");
        if (result == null) {
            return list;
        }
        for (int i = 0; i < result.length(); i++) {
            JSONObject item = result.optJSONObject(i);
            if (item == null) {
                continue;
            }
            com.android.oldbili.retro.model.BangumiCard card = new com.android.oldbili.retro.model.BangumiCard();
            card.seasonId = item.optLong("season_id", 0);
            card.title = stripHtml(item.optString("title", ""));
            card.cover = item.optString("cover", "");
            card.badge = item.optString("badge", "");
            card.total = item.optInt("total_count", 0);
            card.isFinish = "1".equals(item.optString("is_finish", "0"))
                    || item.optBoolean("is_finish", false);
            list.add(card);
        }
        return list;
    }

    /** 搜索直播 */
    public static List<com.android.oldbili.retro.model.LiveRoom> searchLive(String keyword, int page)
            throws IOException, org.json.JSONException {
        List<com.android.oldbili.retro.model.LiveRoom> list =
                new ArrayList<com.android.oldbili.retro.model.LiveRoom>();
        String url = BiliApi.signWbi("https://api.bilibili.com/x/web-interface/wbi/search/type?"
                + "search_type=live&keyword=" + HttpUtil.encode(keyword)
                + "&page=" + page + "&pagesize=20");
        JSONObject data = BiliApi.check(HttpUtil.getJson(url));
        JSONArray result = data.optJSONArray("result");
        if (result == null) {
            return list;
        }
        for (int i = 0; i < result.length(); i++) {
            JSONObject item = result.optJSONObject(i);
            if (item == null) {
                continue;
            }
            if (!"live".equals(item.optString("type", "")) && !"live_room".equals(item.optString("type", ""))) {
                continue;
            }
            com.android.oldbili.retro.model.LiveRoom room = new com.android.oldbili.retro.model.LiveRoom();
            room.roomId = item.optLong("roomid", item.optLong("room_id", 0));
            room.title = stripHtml(item.optString("title", ""));
            room.ownerName = item.optString("uname", "");
            room.cover = item.optString("cover", item.optString("user_cover", ""));
            room.online = item.optInt("online", 0);
            if (room.roomId > 0) {
                list.add(room);
            }
        }
        return list;
    }

    /**
     * 判断输入是不是 AV/BV 号或 UID。
     * 返回 null 表示普通关键词；否则返回数组 [idType, id]，
     * idType 为 "aid"、"bvid" 或 "mid"。
     */
    public static String[] detectId(String input) {
        if (input == null) {
            return null;
        }
        String s = input.trim().toLowerCase();
        if (s.matches("av\\d+")) {
            return new String[]{"aid", s.substring(2)};
        }
        if (s.matches("bv[0-9a-z]{10}")) {
            return new String[]{"bvid", s.toUpperCase()};
        }
        // 纯数字 6~10 位视为 UID，直达个人空间
        if (s.matches("\\d{6,10}")) {
            return new String[]{"mid", s};
        }
        return null;
    }
}
