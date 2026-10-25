package com.android.oldbili.retro.api;

import com.android.oldbili.retro.net.HttpUtil;

import org.json.JSONObject;

import java.io.IOException;

/**
 * 互动操作：点赞、投币、发弹幕。
 * （关注/取关在 UserApi.follow，收藏在 FavoriteApi.setFav）
 */
public class InteractionApi {

    /** 点赞：like=true 点赞，false 取消 */
    public static int like(long aid, boolean like) throws IOException, org.json.JSONException {
        String body = "aid=" + aid + "&like=" + (like ? 1 : 2) + "&csrf=" + HttpUtil.csrf();
        String resp = HttpUtil.postWithLogin("https://api.bilibili.com/x/web-interface/archive/like", body);
        return new JSONObject(new org.json.JSONTokener(resp)).optInt("code", -1);
    }

    /** 是否已点赞（需登录）：返回 1=已点赞 0=未点赞 -1=查询失败 */
    public static int hasLiked(long aid) {
        try {
            String resp = HttpUtil.getWithLogin("https://api.bilibili.com/x/web-interface/archive/has/like?aid=" + aid);
            JSONObject root = new JSONObject(new org.json.JSONTokener(resp));
            return root.optInt("data", root.optInt("code", -1) == 0 ? 0 : -1);
        } catch (Exception e) {
            return -1;
        }
    }

    /** 投币：count 1 或 2 */
    public static int coin(long aid, int count) throws IOException, org.json.JSONException {
        String body = "aid=" + aid + "&multiply=" + count
                + "&select_like=0&cross_domain=true&csrf=" + HttpUtil.csrf();
        String resp = HttpUtil.postWithLogin("https://api.bilibili.com/x/web-interface/coin/add", body);
        return new JSONObject(new org.json.JSONTokener(resp)).optInt("code", -1);
    }

    /**
     * 发弹幕。
     * @param cid 视频 cid
     * @param msg 弹幕内容
     * @param progressMs 进度（毫秒）
     * @param bvid 视频 BV 号
     * @param color 颜色（10进制 RGB）
     * @param mode 1滚动 5顶部 4底部
     * @return 接口 code
     */
    public static int sendDanmaku(long cid, String msg, long progressMs, String bvid,
                                  int color, int mode) throws IOException, org.json.JSONException {
        String body = "type=1"
                + "&oid=" + cid
                + "&msg=" + HttpUtil.encode(msg)
                + "&bvid=" + bvid
                + "&progress=" + progressMs
                + "&color=" + color
                + "&fontsize=25"
                + "&mode=" + mode
                + "&pool=0"
                + "&rnd=" + (System.currentTimeMillis() / 1000)
                + "&plat=1"
                + "&csrf=" + HttpUtil.csrf();
        String resp = HttpUtil.postWithLogin("https://api.bilibili.com/x/v2/dm/post", body);
        return new JSONObject(new org.json.JSONTokener(resp)).optInt("code", -1);
    }
}
