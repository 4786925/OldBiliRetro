package com.android.oldbili.retro.api;

import com.android.oldbili.retro.model.VideoCard;
import com.android.oldbili.retro.net.HttpUtil;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 首页内容：推荐、热门、排行榜。
 */
public class HomeApi {

    /** 登录后的推荐流（WBI，参数对齐网页端），未登录会失败 */
    public static List<VideoCard> getRecommend(int page) throws IOException, org.json.JSONException {
        List<VideoCard> list = new ArrayList<VideoCard>();
        long uniqId = (long) (new java.util.Random().nextDouble() * (1500000000000L - 1300000000000L)) + 1300000000000L;
        String url = BiliApi.signWbi("https://api.bilibili.com/x/web-interface/wbi/index/top/feed/rcmd?"
                + "web_location=1430650&feed_version=V8&homepage_ver=1&uniq_id=" + uniqId
                + "&screen=1100-2056&fresh_idx=" + page + "&fresh_idx_1h=" + page
                + "&brush=" + page + "&fetch_row=" + ((page - 1) * 20));
        JSONObject data = BiliApi.check(HttpUtil.getJsonWithLogin(url));
        JSONArray items = data.optJSONArray("item");
        if (items == null) {
            return list;
        }
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) {
                continue;
            }
            VideoCard card = CardParser.parse(item);
            card.bvid = item.optString("bvid", "");
            if (card.cid == 0) {
                card.cid = item.optLong("cid", 0);
            }
            list.add(card);
        }
        return list;
    }

    /** 热门（免登录） */
    public static List<VideoCard> getPopular(int page, int ps) throws IOException, org.json.JSONException {
        List<VideoCard> list = new ArrayList<VideoCard>();
        JSONObject data = BiliApi.check(HttpUtil.getJson(
                "https://api.bilibili.com/x/web-interface/popular?pn=" + page + "&ps=" + ps));
        JSONArray items = data.optJSONArray("list");
        if (items == null) {
            return list;
        }
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) {
                continue;
            }
            VideoCard card = CardParser.parse(item);
            if (card.bvid.length() == 0) {
                card.bvid = item.optString("bvid", "");
            }
            card.badge = String.valueOf((page - 1) * ps + i + 1);
            list.add(card);
        }
        return list;
    }

    /** 排行榜（rid=0 全站） */
    public static List<VideoCard> getRanking(int rid, int page) throws IOException, org.json.JSONException {
        List<VideoCard> list = new ArrayList<VideoCard>();
        JSONObject data = BiliApi.check(HttpUtil.getJson(
                "https://api.bilibili.com/x/web-interface/ranking?rid=" + rid + "&type=all&pn=" + page + "&ps=20"));
        JSONArray items = data.optJSONArray("list");
        if (items == null) {
            return list;
        }
        for (int i = 0; i < items.length(); i++) {
            VideoCard card = CardParser.parse(items.optJSONObject(i));
            card.badge = String.valueOf((page - 1) * 20 + i + 1);
            list.add(card);
        }
        return list;
    }
}
