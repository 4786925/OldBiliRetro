package com.android.oldbili.retro.api;

import com.android.oldbili.retro.net.HttpUtil;

import org.json.JSONObject;

/**
 * 消息未读数：x/msgfeed/unread（需登录）。
 * 返回各类未读：回复 / @我 / 点赞 / UP 更新 / 推荐 / 订阅 / 系统。
 */
public class MsgUnreadApi {

    public int reply;
    public int at;
    public int like;
    public int up;
    public int recommend;
    public int subscribe;
    public int system;
    public int total;

    public static MsgUnreadApi get() throws Exception {
        String url = "https://api.bilibili.com/x/msgfeed/unread?build=0&mobi_app=web";
        JSONObject data = BiliApi.check(HttpUtil.getJsonWithLogin(url));
        MsgUnreadApi u = new MsgUnreadApi();
        u.reply = pick(data, "reply", "reply_count");
        u.at = pick(data, "at", "at_count");
        u.like = pick(data, "like", "like_count");
        u.up = pick(data, "up", "up_count", "follow");
        u.recommend = pick(data, "rec", "recommend", "rec_count");
        u.subscribe = pick(data, "sub", "subscribe", "season");
        u.system = pick(data, "sys_msg", "system", "sys");
        u.total = u.reply + u.at + u.like + u.up + u.recommend + u.subscribe + u.system;
        return u;
    }

    private static int pick(JSONObject o, String... keys) {
        for (int i = 0; i < keys.length; i++) {
            int v = o.optInt(keys[i], 0);
            if (v > 0) {
                return v;
            }
        }
        return 0;
    }
}
