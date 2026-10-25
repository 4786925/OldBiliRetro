package com.android.oldbili.retro.api;

import com.android.oldbili.retro.model.FavoriteFolder;
import com.android.oldbili.retro.model.VideoCard;
import com.android.oldbili.retro.net.HttpUtil;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 收藏夹：列表、内容、新建、添加/删除。
 */
public class FavoriteApi {

    /** 收藏夹列表（必须登录） */
    public static List<FavoriteFolder> getFolders(long upMid) throws IOException, org.json.JSONException {
        List<FavoriteFolder> list = new ArrayList<FavoriteFolder>();
        JSONObject data = BiliApi.check(HttpUtil.getJsonWithLogin(
                "https://api.bilibili.com/x/v3/fav/folder/created/list-all?up_mid=" + upMid + "&type=0"));
        JSONArray items = data.optJSONArray("list");
        if (items == null) {
            return list;
        }
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) {
                continue;
            }
            FavoriteFolder folder = new FavoriteFolder();
            folder.id = item.optLong("id", 0);
            folder.title = item.optString("title", "");
            folder.count = item.optLong("media_count", 0);
            folder.cover = item.optString("cover", "");
            list.add(folder);
        }
        return list;
    }

    /** 收藏夹内容，返回 [列表, 总数]。v3 新接口优先，老 space/fav/arc 兜底（后者已开始被风控） */
    public static Object[] getFolderVideos(long mid, long folderId, int page) throws IOException, org.json.JSONException {
        // 1) v3 资源列表（网页现行接口）
        try {
            JSONObject data = BiliApi.check(HttpUtil.getJsonWithLogin(
                    "https://api.bilibili.com/x/v3/fav/resource/list?media_id=" + folderId
                            + "&pn=" + page + "&ps=20&keyword=&order=mtime&type=0&tid=0&platform=web"));
            List<VideoCard> list = new ArrayList<VideoCard>();
            JSONArray medias = data.optJSONArray("medias");
            if (medias != null) {
                for (int i = 0; i < medias.length(); i++) {
                    JSONObject item = medias.optJSONObject(i);
                    if (item == null) {
                        continue;
                    }
                    VideoCard card = new VideoCard();
                    card.aid = item.optLong("id", 0);
                    card.bvid = item.optString("bvid", "");
                    card.title = item.optString("title", "");
                    card.cover = item.optString("cover", "");
                    card.duration = item.optInt("duration", 0);
                    JSONObject upper = item.optJSONObject("upper");
                    if (upper != null) {
                        card.upName = upper.optString("name", "");
                        card.upMid = upper.optLong("mid", 0);
                    }
                    JSONObject cnt = item.optJSONObject("cnt_info");
                    if (cnt != null) {
                        card.play = cnt.optLong("play", 0);
                        card.danmaku = cnt.optLong("danmaku", 0);
                    }
                    list.add(card);
                }
            }
            long total = data.optJSONObject("info") != null
                    ? data.optJSONObject("info").optLong("media_count", list.size()) : list.size();
            return new Object[]{list, total};
        } catch (Exception v3Failed) {
            android.util.Log.w("FavoriteApi", "v3 收藏列表失败，回退老接口: " + v3Failed.getMessage());
        }
        // 2) 老接口兜底（archives 结构）
        List<VideoCard> list = new ArrayList<VideoCard>();
        JSONObject data = BiliApi.check(HttpUtil.getJsonWithLogin(
                "https://api.bilibili.com/x/space/fav/arc?vmid=" + mid
                        + "&fid=" + folderId + "&pn=" + page + "&ps=20&order=fav_time&tid=0&keyword="));
        JSONArray archives = data.optJSONArray("archives");
        if (archives != null) {
            for (int i = 0; i < archives.length(); i++) {
                JSONObject item = archives.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                VideoCard card = new VideoCard();
                card.aid = item.optLong("aid", 0);
                card.bvid = item.optString("bvid", "");
                card.title = item.optString("title", "");
                card.cover = item.optString("pic", "");
                card.duration = item.optInt("duration", 0);
                JSONObject owner = item.optJSONObject("owner");
                if (owner != null) {
                    card.upName = owner.optString("name", "");
                    card.upMid = owner.optLong("mid", 0);
                }
                JSONObject stat = item.optJSONObject("stat");
                if (stat != null) {
                    card.play = stat.optLong("view", 0);
                    card.danmaku = stat.optLong("danmaku", 0);
                }
                list.add(card);
            }
        }
        JSONObject pageObj = data.optJSONObject("page");
        long total = 0;
        if (pageObj != null) {
            total = pageObj.optLong("count", 0);
        }
        return new Object[]{list, total};
    }

    /** 收藏视频到指定收藏夹（add=true 收藏 / false 取消） */
    public static int setFav(long aid, long folderId, boolean add) throws IOException, org.json.JSONException {
        String body = "rid=" + aid
                + "&type=2"
                + "&add_media_ids=" + (add ? folderId : "")
                + "&del_media_ids=" + (add ? "" : folderId)
                + "&platform=web"
                + "&eab_x=2"
                + "&ramval=0"
                + "&ga=1"
                + "&gaia_source=pre-load"
                + "&csrf=" + HttpUtil.csrf();
        String resp = HttpUtil.postWithLogin("https://api.bilibili.com/x/v3/fav/resource/deal", body);
        return new JSONObject(new org.json.JSONTokener(resp)).optInt("code", -1);
    }

    /** 新建收藏夹，返回 fid，失败 -1 */
    public static long newFolder(String title) throws IOException, org.json.JSONException {
        String body = "title=" + HttpUtil.encode(title) + "&public=0&csrf=" + HttpUtil.csrf();
        String resp = HttpUtil.postWithLogin("https://api.bilibili.com/x/v3/fav/folder/add", body);
        JSONObject all = new JSONObject(new org.json.JSONTokener(resp));
        if (all.optInt("code", -1) != 0) {
            return -1;
        }
        return all.optJSONObject("data").optLong("fid", -1);
    }

    /** 删除收藏夹 */
    public static int delFolder(long folderId) throws IOException, org.json.JSONException {
        String body = "media_ids=" + folderId + "&csrf=" + HttpUtil.csrf();
        String resp = HttpUtil.postWithLogin("https://api.bilibili.com/x/v3/fav/folder/del", body);
        return new JSONObject(new org.json.JSONTokener(resp)).optInt("code", -1);
    }
}
