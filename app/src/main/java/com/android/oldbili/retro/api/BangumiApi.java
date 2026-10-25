package com.android.oldbili.retro.api;

import com.android.oldbili.retro.model.BangumiCard;
import com.android.oldbili.retro.net.HttpUtil;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.ArrayList;
import java.util.List;

/**
 * 追番/追剧接口。
 * 列表：x/space/bangumi/follow/list?type=1追番|2追剧
 * 剧集：pgc/view/web/season?season_id=
 * 追/取消追：x/season/user/status（season_type 1番剧 2影视）
 */
public class BangumiApi {

    /** 追番/追剧列表（风控时自动改走 WBI 签名版） */
    public static List<BangumiCard> getFollowList(long vmid, int type, int page)
            throws java.io.IOException, org.json.JSONException {
        String url = "https://api.bilibili.com/x/space/bangumi/follow/list?type=" + type
                + "&vmid=" + vmid + "&pn=" + page + "&ps=15";
        JSONObject data;
        try {
            data = BiliApi.check(HttpUtil.getJson(url));
        } catch (Exception first) {
            data = BiliApi.check(HttpUtil.getJson(BiliApi.signWbi(url)));
        }
        List<BangumiCard> list = new ArrayList<BangumiCard>();
        JSONArray items = data.optJSONArray("list");
        if (items == null) {
            return list;
        }
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) {
                continue;
            }
            BangumiCard card = new BangumiCard();
            card.seasonId = item.optLong("season_id", 0);
            card.title = item.optString("title", "");
            card.cover = item.optString("cover", "");
            card.total = item.optInt("total_count", item.optInt("new_ep", 0));
            card.isFinish = item.optBoolean("is_finish", false);
            // 追番列表带进度
            JSONObject progress = item.optJSONObject("progress");
            if (progress != null) {
                card.progress = progress.optInt("index", -1);
            }
            // 会员/独家角标
            card.badge = item.optString("badge", "");
            if (card.badge.length() == 0) {
                card.badge = item.optString("badge_type", "");
            }
            list.add(card);
        }
        return list;
    }

    /** 番剧详情（含剧集列表）。返回 [标题, 简介, 剧集列表] */
    public static Object[] getSeasonDetail(long seasonId)
            throws java.io.IOException, org.json.JSONException {
        // 该接口没有 data 层：{"code":0,"result":{...}}——不能走 BiliApi.check() 解包
        JSONObject root = null;
        try {
            String resp = HttpUtil.get(
                    "https://api.bilibili.com/pgc/view/web/season?season_id=" + seasonId);
            JSONObject r0 = new JSONObject(new JSONTokener(resp));
            if (r0.optInt("code", 0) == 0) {
                root = r0;
            }
        } catch (Exception ignored) {
        }
        if (root == null) {
            // 风控下改走 WBI 签名版
            String resp = HttpUtil.get(BiliApi.signWbi(
                    "https://api.bilibili.com/x/pgc/view/web/season?season_id=" + seasonId));
            root = new JSONObject(new JSONTokener(resp));
        }
        if (root.optInt("code", 0) != 0) {
            throw new java.io.IOException("接口错误(" + root.optInt("code", -1) + ")");
        }
        JSONObject result = root.optJSONObject("result");
        if (result == null) {
            JSONObject data = root.optJSONObject("data");
            if (data != null) {
                result = data.optJSONObject("result");
            }
        }
        String title = "";
        String evaluate = "";
        List<BangumiEpisode> episodes = new ArrayList<BangumiEpisode>();
        if (result != null) {
            title = result.optString("title", "");
            evaluate = result.optString("evaluate", "");
            JSONArray eps = result.optJSONArray("episodes");
            if (eps != null) {
                for (int i = 0; i < eps.length(); i++) {
                    JSONObject ep = eps.optJSONObject(i);
                    if (ep == null) {
                        continue;
                    }
                    BangumiEpisode e = new BangumiEpisode();
                    e.epId = ep.optLong("id", 0);
                    e.aid = ep.optLong("aid", 0);
                    e.bvid = ep.optString("bvid", "");
                    e.cid = ep.optLong("cid", 0);
                    e.title = ep.optString("title", "");
                    e.longTitle = ep.optString("long_title", "");
                    e.badge = ep.optString("badge", "");
                    e.cover = ep.optString("cover", "");
                    e.duration = ep.optLong("duration", 0) / 1000;
                    episodes.add(e);
                }
            }
        }
        return new Object[]{title, evaluate, episodes};
    }

    /** 追/取消追番剧：seasonType 1番剧 2影视（主通道 pgc/web/follow，多路兜底） */
    public static int followSeason(long seasonId, int seasonType, boolean follow)
            throws java.io.IOException, org.json.JSONException {
        String action = follow ? "add" : "del";
        int lastCode = -1;
        String lastResp = "";
        // 通道1：pgc/web/follow/add|del（当前官方接口，GET 带 query 参数）
        try {
            String url = "https://api.bilibili.com/pgc/web/follow/" + action
                    + "?season_id=" + seasonId + "&csrf=" + HttpUtil.csrf();
            String resp = HttpUtil.getWithLogin(url);
            if (!resp.startsWith("<")) {
                int code = new JSONObject(new org.json.JSONTokener(resp)).optInt("code", -1);
                if (code == 0) {
                    return 0;
                }
                lastCode = code;
            }
            lastResp = resp;
        } catch (Exception ignored) {
        }
        // 通道2：同接口 POST 形式
        try {
            String body = "season_id=" + seasonId + "&csrf=" + HttpUtil.csrf();
            String resp2 = HttpUtil.postWithLogin(
                    "https://api.bilibili.com/pgc/web/follow/" + action, body);
            if (!resp2.startsWith("<")) {
                int code2 = new JSONObject(new org.json.JSONTokener(resp2)).optInt("code", -1);
                if (code2 == 0) {
                    return 0;
                }
                lastCode = code2;
            }
            lastResp = resp2;
        } catch (Exception ignored) {
        }
        // 通道3：wbi 签名 x/season/user/status
        try {
            String getUrl = BiliApi.signWbi("https://api.bilibili.com/x/season/user/status?"
                    + "season_id=" + seasonId + "&season_type=" + seasonType
                    + "&follow=" + (follow ? 1 : 0) + "&csrf=" + HttpUtil.csrf());
            String resp3 = HttpUtil.getWithLogin(getUrl);
            if (!resp3.startsWith("<")) {
                int code3 = new JSONObject(new org.json.JSONTokener(resp3)).optInt("code", -1);
                if (code3 == 0) {
                    return 0;
                }
                lastCode = code3;
            }
            lastResp = resp3;
        } catch (Exception ignored) {
        }
        if (lastResp.startsWith("<")) {
            throw new java.io.IOException("请求被安全验证拦截，请稍后重试或更换网络");
        }
        throw new java.io.IOException("接口错误(" + lastCode + ")");
    }

    /** 番剧/影视索引（首页 tab）：seasonType 1番剧 2影视（失败自动改走 WBI 签名版） */
    public static List<BangumiCard> getSeasonIndex(int seasonType, int page)
            throws java.io.IOException, org.json.JSONException {
        String url = "https://api.bilibili.com/pgc/season/index/result?season_version=-1&area=-1"
                + "&is_finish=-1&copyright=-1&season_status=-1&season_month=-1&year=-1"
                + "&style_id=-1&order=3&st=1&sort=0&page=" + page
                + "&season_type=" + seasonType + "&pagesize=20&type=1";
        try {
            return parseSeasonIndex(BiliApi.check(HttpUtil.getJson(url)));
        } catch (Exception first) {
            // 风控下改走 WBI 签名版
            String wbiUrl = BiliApi.signWbi(url);
            return parseSeasonIndex(BiliApi.check(HttpUtil.getJson(wbiUrl)));
        }
    }

    private static List<BangumiCard> parseSeasonIndex(JSONObject data) {
        List<BangumiCard> list = new ArrayList<BangumiCard>();
        JSONArray items = data.optJSONArray("list");
        if (items == null) {
            return list;
        }
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) {
                continue;
            }
            BangumiCard card = new BangumiCard();
            card.seasonId = item.optLong("season_id", 0);
            card.title = item.optString("title", "");
            card.cover = item.optString("cover", "");
            card.badge = item.optString("badge", "");
            card.total = item.optInt("total_count", 0);
            card.isFinish = item.optBoolean("is_finish", false);
            if (card.seasonId > 0) {
                list.add(card);
            }
        }
        return list;
    }

    /** 单集信息 */
    public static class BangumiEpisode {
        public long epId;
        public long aid;
        public String bvid = "";
        public long cid;
        public String title = "";
        public String longTitle = "";
        public String badge = "";   // "会员" 等
        public long duration;
        public String cover = "";
    }
}
