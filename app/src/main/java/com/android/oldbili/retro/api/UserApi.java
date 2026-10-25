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
        try {
            JSONObject data = BiliApi.check(HttpUtil.getJson(
                    "https://api.bilibili.com/x/web-interface/card?mid=" + mid));
            JSONObject card = data.optJSONObject("card");
            UserInfo user = new UserInfo();
            if (card == null) {
                return user;
            }
            user.mid = card.optLong("mid", mid);
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
            if (user.name.length() > 0 || user.face.length() > 0) {
                return user;
            }
        } catch (Exception e) {
            android.util.Log.w("UserApi", "card 接口失败，走 acc/info: " + e.getMessage());
        }
        // 兜底：WBI 签名的空间信息接口（风控下仍然可用）
        try {
            String url = BiliApi.signWbi("https://api.bilibili.com/x/space/wbi/acc/info?mid=" + mid);
            JSONObject data = BiliApi.check(HttpUtil.getJson(url));
            UserInfo user = new UserInfo();
            user.mid = data.optLong("mid", mid);
            user.name = data.optString("name", "");
            user.face = data.optString("face", "");
            user.sign = data.optString("sign", "");
            // acc/info 不带粉丝/关注数，再用 relation/stat 补齐
            try {
                JSONObject stat = BiliApi.check(HttpUtil.getJson(
                        "https://api.bilibili.com/x/relation/stat?vmid=" + mid));
                user.fans = stat.optLong("follower", 0);
                user.follows = stat.optLong("following", 0);
            } catch (Exception ignored) {
            }
            // 获赞数用 upstat 补齐
            try {
                JSONObject upstat = BiliApi.check(HttpUtil.getJson(
                        "https://api.bilibili.com/x/space/upstat?mid=" + mid));
                user.likes = upstat.optLong("likes", 0);
            } catch (Exception ignored) {
            }
            return user;
        } catch (Exception e2) {
            android.util.Log.w("UserApi", "acc/info 也失败: " + e2.getMessage());
            UserInfo empty = new UserInfo();
            empty.mid = mid;
            return empty;
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
