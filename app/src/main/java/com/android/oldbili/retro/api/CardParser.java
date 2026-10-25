package com.android.oldbili.retro.api;

import com.android.oldbili.retro.model.VideoCard;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 各列表接口公用的 archive JSON 解析：搜索、推荐、热门、历史、收藏、稍后再看
 * 返回的视频条目字段长得都一样，统一在这里解析成 VideoCard。
 */
public class CardParser {

    /** 从 archive 对象解析视频卡片 */
    public static VideoCard parse(JSONObject arc) {
        VideoCard card = new VideoCard();
        if (arc == null) {
            return card;
        }
        card.aid = arc.optLong("aid");
        card.bvid = arc.optString("bvid", "");
        card.title = arc.optString("title", "");
        card.cover = arc.optString("pic", "");
        card.duration = arc.optInt("duration", 0);
        card.cid = arc.optLong("cid", 0);

        JSONObject stat = arc.optJSONObject("stat");
        if (stat != null) {
            card.play = stat.optLong("view", 0);
            card.danmaku = stat.optLong("danmaku", 0);
        }
        JSONObject owner = arc.optJSONObject("owner");
        if (owner != null) {
            card.upMid = owner.optLong("mid", 0);
            card.upName = owner.optString("name", "");
            card.upFace = owner.optString("face", "");
        }
        return card;
    }

    /** 从 cards JSONArray 解析列表（有则用之） */
    public static VideoCard parseFromCards(JSONArray cards, int index) {
        return parse(cards.optJSONObject(index));
    }
}
