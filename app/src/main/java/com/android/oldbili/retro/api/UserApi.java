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
 * 用户信息、个人空间、关注关系。
 */
public class UserApi {

    /**
     * 用户名片（免登录）。
     * card 接口被风控时自动降级到 wbi acc/info，保证头像点击永不空白。
     */
    public static UserInfo getUserCard(long mid) throws IOException, org.json.JSONException {
        // 个人主页/详情页都要求秒开：内部就用快速超时，避免 20 秒卡死
        com.android.oldbili.retro.net.HttpUtil.setQuickTimeout(4000, 5000);
        try {
            return getUserCardSlow(mid);
        } finally {
            com.android.oldbili.retro.net.HttpUtil.clearQuickTimeout();
        }
    }

    private static UserInfo getUserCardSlow(long mid)
            throws IOException, org.json.JSONException {
        // 先查缓存（10 分钟内不再请求，详情页秒开）
        UserInfo cached = readCardCache(mid);
        if (cached != null) {
            return cached;
        }
        UserInfo user = new UserInfo();
        user.mid = mid;

        // 1) 旧名片接口（部分网络/账号会被 412 风控，失败就继续往下补）
        try {
            JSONObject data = BiliApi.check(HttpUtil.getJson(
                    "https://api.bilibili.com/x/web-interface/card?mid=" + mid));
            JSONObject card = data.optJSONObject("card");
            if (card != null) {
                user.name = card.optString("name", "");
                user.face = card.optString("face", "");
                user.sign = card.optString("sign", "");
                user.fans = card.optLong("fans", 0);
                user.follows = card.optLong("attention", 0);
                user.likes = card.optLong("likes", 0);
                JSONObject level = card.optJSONObject("level_info");
                if (level != null) {
                    user.level = level.optInt("current_level", 0);
                }
            }
        } catch (Exception e) {
            android.util.Log.w("UserApi", "card 失败: " + e.getMessage());
        }

        // 2) WBI 空间信息（补昵称/签名/等级）
        if (user.name.length() == 0 || user.sign.length() == 0) {
            try {
                JSONObject data = BiliApi.check(HttpUtil.getJson(BiliApi.signWbiWithDm(
                        "https://api.bilibili.com/x/space/wbi/acc/info?mid=" + mid)));
                if (user.name.length() == 0) {
                    user.name = data.optString("name", "");
                }
                if (user.face.length() == 0) {
                    user.face = data.optString("face", "");
                }
                if (user.sign.length() == 0) {
                    user.sign = data.optString("sign", "");
                }
                if (user.level == 0) {
                    user.level = data.optInt("level", 0);
                }
            } catch (Exception e) {
                android.util.Log.w("UserApi", "acc/info 失败: " + e.getMessage());
            }
        }

        // 2.5) 登录态下的老空间接口（带 Cookie 可绕过部分风控）
        if (user.name.length() == 0 || user.face.length() == 0) {
            try {
                JSONObject data = BiliApi.check(HttpUtil.getJsonWithLogin(
                        "https://api.bilibili.com/x/space/acc/info?mid=" + mid));
                if (user.name.length() == 0) {
                    user.name = data.optString("name", "");
                }
                if (user.face.length() == 0) {
                    user.face = data.optString("face", "");
                }
                if (user.sign.length() == 0) {
                    user.sign = data.optString("sign", "");
                }
                if (user.level == 0) {
                    user.level = data.optInt("level", 0);
                }
            } catch (Exception e) {
                android.util.Log.w("UserApi", "space/acc/info 失败: " + e.getMessage());
            }
        }

        // 3) 关注/粉丝数：relation/stat 最稳（不校验登录，也不吃 412）
        try {
            JSONObject stat = BiliApi.check(HttpUtil.getJson(
                    "https://api.bilibili.com/x/relation/stat?vmid=" + mid));
            user.fans = stat.optLong("follower", user.fans);
            user.follows = stat.optLong("following", user.follows);
        } catch (Exception e) {
            android.util.Log.w("UserApi", "relation/stat 失败: " + e.getMessage());
        }

        // 4) 获赞数
        if (user.likes == 0) {
            try {
                JSONObject upstat = BiliApi.check(HttpUtil.getJson(
                        "https://api.bilibili.com/x/space/upstat?mid=" + mid));
                user.likes = upstat.optLong("likes", 0);
            } catch (Exception ignored) {
            }
        }

        // 5) 昵称/头像兜底：vc 批量名片（登录态可用）
        if (user.name.length() == 0 || user.face.length() == 0) {
            try {
                String resp = HttpUtil.vcGet(
                        "https://api.vc.bilibili.com/account/v1/user/cards?uids=" + mid, true);
                JSONObject root = new JSONObject(new org.json.JSONTokener(resp));
                JSONObject data = root.optJSONObject("data");
                if (data != null) {
                    JSONObject card = data.optJSONObject(String.valueOf(mid));
                    if (card != null) {
                        if (user.name.length() == 0) {
                            user.name = card.optString("name", "");
                        }
                        if (user.face.length() == 0) {
                            user.face = card.optString("face", "");
                        }
                        if (user.sign.length() == 0) {
                            user.sign = card.optString("sign", "");
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }

        writeCardCache(mid, user);
        return user;
    }

    private static UserInfo readCardCache(long mid) {
        try {
            String raw = com.android.oldbili.retro.util.Sp.getString("user_card_cache_" + mid, "");
            if (raw == null || raw.length() == 0) {
                return null;
            }
            JSONObject o = new JSONObject(new org.json.JSONTokener(raw));
            if (System.currentTimeMillis() - o.optLong("t", 0) > 10 * 60 * 1000L) {
                return null;
            }
            UserInfo u = new UserInfo();
            u.mid = mid;
            u.name = o.optString("n", "");
            u.face = o.optString("f", "");
            u.sign = o.optString("s", "");
            u.fans = o.optLong("fans", 0);
            u.follows = o.optLong("fl", 0);
            u.likes = o.optLong("lk", 0);
            u.level = o.optInt("lv", 0);
            // 头像为空也不用缓存（否则头像会一直不加载）
            if (u.face.length() == 0) {
                return null;
            }
            return u;
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static void writeCardCache(long mid, UserInfo u) {
        // 信息太少的不要缓存，否则 10 分钟内头像一直空
        if (u == null || (u.name.length() == 0 && u.face.length() == 0)) {
            return;
        }
        try {
            JSONObject o = new JSONObject();
            o.put("t", System.currentTimeMillis());
            o.put("n", u.name);
            o.put("f", u.face);
            o.put("s", u.sign);
            o.put("fans", u.fans);
            o.put("fl", u.follows);
            o.put("lk", u.likes);
            o.put("lv", u.level);
            com.android.oldbili.retro.util.Sp.putString("user_card_cache_" + mid, o.toString());
        } catch (Throwable ignored) {
        }
    }

    /** 关注状态 attribute：2=已关注（新编码）1/6=已关注（旧编码）0=未关注 */
    public static int getRelation(long mid) {
        try {
            JSONObject data = BiliApi.check(HttpUtil.getJson(
                    "https://api.bilibili.com/x/space/acc/relation?mid=" + mid));
            return data.optJSONObject("relation").optInt("attribute", 0);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 完整关系：返回 [我的关注状态 attribute, 对方关注我状态 be_relation.attribute]。
     * 当前服务端把"已关注"与"互粉"都返回 attribute=2，互粉要靠 be_relation 判断。
     */
    public static int[] getRelationPair(long mid) {
        try {
            JSONObject data = BiliApi.check(HttpUtil.getJson(
                    "https://api.bilibili.com/x/space/acc/relation?mid=" + mid));
            int attr = data.optJSONObject("relation").optInt("attribute", 0);
            int beAttr = data.optJSONObject("be_relation").optInt("attribute", 0);
            return new int[]{attr, beAttr};
        } catch (Exception e) {
            return new int[]{0, 0};
        }
    }

    /**
     * 统一关系语义：服务端新旧两代编码混用——
     * 老：1=已关注 2=互粉 6=特别关注；新：2=已关注（无论互粉与否）。
     * 实测当前接口单向关注与互粉都返回 2，所以这里 2 一律视为"我已关注"。
     */
    public static boolean isFollowed(int attribute) {
        return attribute == 1 || attribute == 2 || attribute == 3 || attribute == 6
                || (attribute & 1) != 0;
    }

    /** 是否互粉：我关注了TA 且 be_relation 表明 TA 也关注了我 */
    public static boolean isMutual(UserInfo user) {
        return user != null && isFollowed(user.relation) && user.beRelation > 0;
    }

    public static boolean isMutual(int attribute) {
        return attribute == 3;
    }

    /** 我的信息（登录） */
    public static UserInfo getMyInfo() throws IOException, org.json.JSONException {
        UserInfo user = new UserInfo();
        try {
            JSONObject data = BiliApi.check(HttpUtil.getJsonWithLogin(
                    "https://api.bilibili.com/x/space/myinfo"));
            user.mid = data.optLong("mid", 0);
            user.name = data.optString("name", "");
            user.face = data.optString("face", "");
            user.sign = data.optString("sign", "");
            user.level = data.optInt("level", 0);
            return user;
        } catch (Exception e) {
            // 兜底：nav 也能给基本资料
            JSONObject data = BiliApi.check(HttpUtil.getJsonWithLogin(
                    "https://api.bilibili.com/x/web-interface/nav"));
            user.mid = data.optLong("mid", 0);
            user.name = data.optString("uname", "");
            user.face = data.optString("face", "");
            user.sign = data.optString("sign", "");
            JSONObject level = data.optJSONObject("level_info");
            if (level != null) {
                user.level = level.optInt("current_level", 0);
            }
            return user;
        }
    }

    /** UP 主投稿（WBI，参数对齐网页端），返回 [列表, 总页数] */
    public static Object[] getSpaceVideos(long mid, int page) throws IOException, org.json.JSONException {
        String url = BiliApi.signWbi("https://api.bilibili.com/x/space/wbi/arc/search?"
                + "keyword=&mid=" + mid + "&order_avoided=true&order=pubdate&pn=" + page
                + "&ps=20&tid=0&web_location=333.999");
        JSONObject data = BiliApi.check(HttpUtil.getJson(url));
        JSONObject listObj = data.optJSONObject("list");
        List<VideoCard> list = new ArrayList<VideoCard>();
        if (listObj != null) {
            JSONArray vlist = listObj.optJSONArray("vlist");
            if (vlist != null) {
                for (int i = 0; i < vlist.length(); i++) {
                    JSONObject item = vlist.optJSONObject(i);
                    if (item == null) {
                        continue;
                    }
                    VideoCard card = new VideoCard();
                    card.aid = item.optLong("aid", 0);
                    card.bvid = item.optString("bvid", "");
                    card.title = item.optString("title", "");
                    card.cover = item.optString("pic", "");
                    card.duration = parseLength(item.optString("length", ""));
                    card.play = item.optLong("play", 0);
                    card.danmaku = item.optLong("video_review", 0);
                    card.upMid = mid;
                    list.add(card);
                }
            }
        }
        JSONObject pageObj = data.optJSONObject("page");
        int totalPages = 1;
        if (pageObj != null) {
            int count = pageObj.optInt("count", 0);
            int ps = pageObj.optInt("ps", 20);
            totalPages = ps > 0 ? (count + ps - 1) / ps : 1;
        }
        return new Object[]{list, totalPages};
    }

    private static int parseLength(String text) {
        if (text == null || text.length() == 0) {
            return 0;
        }
        try {
            String[] parts = text.split(":");
            if (parts.length == 3) {
                return Integer.parseInt(parts[0]) * 3600
                        + Integer.parseInt(parts[1]) * 60 + Integer.parseInt(parts[2]);
            }
            if (parts.length == 2) {
                return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    /** 关注列表 / 粉丝列表 */
    public static List<UserInfo> getFollowList(long mid, int page, boolean followers) throws IOException, org.json.JSONException {
        List<UserInfo> list = new ArrayList<UserInfo>();
        String type = followers ? "followers" : "followings";
        JSONObject data = BiliApi.check(HttpUtil.getJson(
                "https://api.bilibili.com/x/relation/" + type + "?vmid=" + mid
                        + "&ps=20&pn=" + page + "&order=desc"));
        JSONArray items = data.optJSONArray("list");
        if (items == null) {
            return list;
        }
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) {
                continue;
            }
            UserInfo user = new UserInfo();
            user.mid = item.optLong("mid", 0);
            user.name = item.optString("uname", "");
            user.face = item.optString("face", "");
            user.sign = item.optString("sign", "");
            user.relation = item.optInt("attribute", 0);
            // 关注列表里 attribute=2 只表示"已关注"，互粉要结合 be_relation
            JSONObject beRel = item.optJSONObject("be_relation");
            if (beRel == null) {
                beRel = item.optJSONObject("beRelation");
            }
            if (beRel != null) {
                user.beRelation = beRel.optInt("attribute", 0);
            }
            list.add(user);
        }
        return list;
    }

    /** 关注/取关：follow=true 关注 */
    public static int follow(long mid, boolean follow) throws IOException, org.json.JSONException {
        String body = "fid=" + mid + "&act=" + (follow ? 1 : 2)
                + "&re_src=11&csrf=" + HttpUtil.csrf();
        String resp = HttpUtil.postWithLogin("https://api.bilibili.com/x/relation/modify", body);
        return new JSONObject(new org.json.JSONTokener(resp)).optInt("code", -1);
    }

    /** 移除粉丝：act=3 把对方移出我的粉丝 */
    public static int removeFollower(long mid) throws IOException, org.json.JSONException {
        String body = "fid=" + mid + "&act=3&re_src=11&csrf=" + HttpUtil.csrf();
        String resp = HttpUtil.postWithLogin("https://api.bilibili.com/x/relation/modify", body);
        return new JSONObject(new org.json.JSONTokener(resp)).optInt("code", -1);
    }

    /** 黑名单：add=true 拉黑（act=5），false 移出黑名单（act=6） */
    public static int setBlacklist(long mid, boolean add) throws IOException, org.json.JSONException {
        String body = "fid=" + mid + "&act=" + (add ? 5 : 6) + "&re_src=11&csrf=" + HttpUtil.csrf();
        String resp = HttpUtil.postWithLogin("https://api.bilibili.com/x/relation/modify", body);
        return new JSONObject(new org.json.JSONTokener(resp)).optInt("code", -1);
    }

    /** UP 主点赞的视频（需登录）。兼容 list 数组与 list.vlist 两种结构 */
    public static List<VideoCard> getLikeVideos(long vmid, int page) throws IOException, org.json.JSONException {
        return getSpaceVideoList("https://api.bilibili.com/x/space/like/video?vmid=" + vmid
                + "&pn=" + page + "&ps=20");
    }

    /** UP 主投币的视频（需登录） */
    public static List<VideoCard> getCoinVideos(long vmid, int page) throws IOException, org.json.JSONException {
        return getSpaceVideoList("https://api.bilibili.com/x/space/coin/video?vmid=" + vmid
                + "&pn=" + page + "&ps=20");
    }

    private static List<VideoCard> getSpaceVideoList(String url) throws IOException, org.json.JSONException {
        List<VideoCard> list = new ArrayList<VideoCard>();
        JSONObject data = BiliApi.check(HttpUtil.getJsonWithLogin(url));
        JSONArray items = data.optJSONArray("list");
        if (items == null) {
            JSONObject listObj = data.optJSONObject("list");
            if (listObj != null) {
                items = listObj.optJSONArray("vlist");
            }
        }
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                VideoCard card = new VideoCard();
                card.aid = item.optLong("aid", 0);
                card.bvid = item.optString("bvid", "");
                card.title = item.optString("title", "");
                card.cover = item.optString("pic", "");
                card.upName = item.optString("author", "");
                card.upMid = item.optLong("mid", 0);
                card.play = item.optLong("play", 0);
                list.add(card);
            }
        }
        return list;
    }
}
