package com.android.oldbili.retro.api;

import com.android.oldbili.retro.model.DynamicItem;
import com.android.oldbili.retro.net.HttpUtil;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 动态：关注动态流、个人空间动态。
 */
public class DynamicApi {

    /** 关注动态，offset 传空字符串表示第一页，返回 [列表, 下一页offset] */
    public static Object[] getFeed(String offset) throws IOException, org.json.JSONException {
        String url = "https://api.bilibili.com/x/polymer/web-dynamic/v1/feed/all?type=all"
                + "&features=itemOpusStyle,listOnlyfans,opusBigCover,onlyfansVote,forwardListHidden,"
                + "decorationCard,commentsNewVersion,onlyfansAssetsV2,ugcDelete";
        if (offset != null && offset.length() > 0) {
            url += "&offset=" + HttpUtil.encode(offset);
        }
        // 动态接口做 WBI 签名；dm_img 风控参数在这个端点上反而会触发 -403（实测），不带
        url = BiliApi.signWbi(url);
        JSONObject data = BiliApi.check(HttpUtil.getJsonWithLogin(url));
        List<DynamicItem> list = new ArrayList<DynamicItem>();
        JSONArray items = data.optJSONArray("items");
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                DynamicItem item = parseItem(items.optJSONObject(i));
                if (item != null) {
                    list.add(item);
                }
            }
        }
        String next = "";
        JSONObject offsetObj = data.optJSONObject("offset");
        if (offsetObj != null) {
            next = offsetObj.optString("offset", "");
        }
        return new Object[]{list, next};
    }

    /** 个人空间动态 */
    public static Object[] getSpaceFeed(long hostMid, String offset) throws IOException, org.json.JSONException {
        String url = "https://api.bilibili.com/x/polymer/web-dynamic/v1/feed/space?host_mid=" + hostMid
                + "&features=itemOpusStyle,listOnlyfans,opusBigCover,onlyfansVote,forwardListHidden,"
                + "decorationCard,commentsNewVersion,onlyfansAssetsV2,ugcDelete";
        if (offset != null && offset.length() > 0) {
            url += "&offset=" + HttpUtil.encode(offset);
        }
        // 动态接口做 WBI 签名；dm_img 风控参数在这个端点上反而会触发 -403（实测），不带
        url = BiliApi.signWbi(url);
        JSONObject data = BiliApi.check(HttpUtil.getJsonWithLogin(url));
        List<DynamicItem> list = new ArrayList<DynamicItem>();
        JSONArray items = data.optJSONArray("items");
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                DynamicItem item = parseItem(items.optJSONObject(i));
                if (item != null) {
                    list.add(item);
                }
            }
        }
        String next = "";
        JSONObject offsetObj = data.optJSONObject("offset");
        if (offsetObj != null) {
            next = offsetObj.optString("offset", "");
        }
        return new Object[]{list, next};
    }

    /** 解析单条动态（polymer feed 的 item 结构） */
    private static DynamicItem parseItem(JSONObject item) {
        if (item == null) {
            return null;
        }
        DynamicItem d = new DynamicItem();
        d.dynamicId = item.optString("id_str", "");
        String type = item.optString("type", "");

        JSONObject modules = item.optJSONObject("modules");
        if (modules == null) {
            return null;
        }
        JSONObject author = modules.optJSONObject("module_author");
        if (author != null) {
            d.mid = author.optLong("mid", 0);
            d.userName = author.optString("name", "");
            d.userFace = author.optString("face", "");
            d.time = author.optLong("pub_ts", 0);
        }
        JSONObject stat = modules.optJSONObject("module_stat");
        if (stat != null) {
            d.like = stat.optJSONObject("like") != null ? stat.optJSONObject("like").optLong("count", 0) : 0;
        }
        JSONObject dynamic = modules.optJSONObject("module_dynamic");
        if (dynamic == null) {
            return null;
        }
        // 文字
        JSONObject desc = dynamic.optJSONObject("desc");
        if (desc != null) {
            d.text = desc.optString("text", "");
        }
        // 主体
        JSONObject major = dynamic.optJSONObject("major");
        if (major != null) {
            if ("DYNAMIC_TYPE_AV".equals(type)) {
                JSONObject archive = major.optJSONObject("archive");
                if (archive != null) {
                    d.type = DynamicItem.TYPE_AV;
                    d.aid = archive.optLong("aid", 0);
                    d.bvid = archive.optString("bvid", "");
                    d.videoTitle = archive.optString("title", "");
                    d.videoCover = archive.optString("cover", "");
                    d.videoDuration = parseDurationText(archive.optString("duration_text", ""));
                    JSONObject aStat = archive.optJSONObject("stat");
                    if (aStat != null) {
                        d.videoPlay = aStat.optLong("play", 0);
                    }
                }
            } else if ("DYNAMIC_TYPE_DRAW".equals(type)) {
                d.type = DynamicItem.TYPE_DRAW;
                JSONArray pics = major.optJSONArray("draw");
                if (pics != null) {
                    for (int i = 0; i < pics.length(); i++) {
                        String pic = pics.optJSONObject(i) != null
                                ? pics.optJSONObject(i).optString("src", "") : "";
                        if (pic.length() > 0) {
                            d.pictures.add(pic);
                        }
                    }
                }
            } else if ("DYNAMIC_TYPE_FORWARD".equals(type)) {
                d.type = DynamicItem.TYPE_FORWARD;
                if (desc != null) {
                    JSONObject orig = desc.optJSONObject("orig");
                    if (orig != null) {
                        d.origin = parseItem(orig);
                    }
                }
            } else if ("DYNAMIC_TYPE_WORD".equals(type)) {
                d.type = DynamicItem.TYPE_TEXT;
            }
        }
        return d;
    }

    private static int parseDurationText(String text) {
        if (text == null) {
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
}
