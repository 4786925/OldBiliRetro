package com.android.oldbili.retro.api;

import com.android.oldbili.retro.model.LiveRoom;
import com.android.oldbili.retro.net.HttpUtil;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 直播：直播间列表、房间信息、播放地址。
 */
public class LiveApi {

    /** 推荐直播列表 */
    public static List<LiveRoom> getRoomList(int page) throws IOException, org.json.JSONException {
        List<LiveRoom> list = new ArrayList<LiveRoom>();
        JSONObject data = BiliApi.check(HttpUtil.getJson(
                "https://api.live.bilibili.com/xlive/web-ucenter/v1/xfetter/GetWebList"
                        + "?platform=web&parent_area_id=&area_id=&sort_type=online&page=" + page));
        JSONArray items = data.optJSONArray("list");
        if (items == null) {
            return list;
        }
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) {
                continue;
            }
            LiveRoom room = new LiveRoom();
            room.roomId = item.optLong("roomid", 0);
            room.uid = item.optLong("uid", 0);
            room.title = item.optString("title", "");
            room.cover = item.optString("user_cover", "");
            room.online = item.optLong("online", 0);
            room.ownerName = item.optString("uname", "");
            room.ownerFace = item.optString("face", "");
            list.add(room);
        }
        return list;
    }

    /** 房间信息（兼容新旧两种结构：平铺与 room_info/anchor_info 嵌套） */
    public static LiveRoom getRoomInfo(long roomId) throws IOException, org.json.JSONException {
        LiveRoom room = new LiveRoom();
        JSONObject data = BiliApi.check(HttpUtil.getJson(
                "https://api.live.bilibili.com/room/v1/Room/get_info?room_id=" + roomId));
        JSONObject info = data.optJSONObject("room_info");
        if (info == null) {
            info = data;
        }
        room.roomId = info.optLong("room_id", roomId);
        room.uid = info.optLong("uid", 0);
        room.title = info.optString("title", "");
        room.cover = info.optString("cover", "");
        room.online = info.optLong("online", 0);
        // 主播名与头像在 anchor_info.base_info 里
        JSONObject anchor = data.optJSONObject("anchor_info");
        if (anchor != null) {
            JSONObject base = anchor.optJSONObject("base_info");
            if (base != null) {
                room.ownerName = base.optString("uname", "");
                room.ownerFace = base.optString("face", "");
            }
        }
        return room;
    }

    /** 关注主播的直播列表（需登录）；失败时抛异常由调用方兜底 */
    public static List<LiveRoom> getFollowLiveRooms(int page) throws IOException, org.json.JSONException {
        List<LiveRoom> list = new ArrayList<LiveRoom>();
        JSONObject data = BiliApi.check(HttpUtil.getJsonWithLogin(
                "https://api.live.bilibili.com/relation/v1/feed/feed_list?page=" + page
                        + "&pagesize=20"));
        JSONArray items = data.optJSONArray("list");
        if (items == null) {
            return list;
        }
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) {
                continue;
            }
            LiveRoom room = new LiveRoom();
            room.roomId = item.optLong("roomid", 0);
            room.uid = item.optLong("uid", 0);
            room.title = item.optString("title", "");
            room.cover = item.optString("user_cover", item.optString("cover", ""));
            room.online = item.optLong("online", 0);
            room.ownerName = item.optString("uname", "");
            room.ownerFace = item.optString("face", "");
            if (room.roomId > 0) {
                list.add(room);
            }
        }
        return list;
    }

    /** 直播弹幕（历史轮询）：gethistory 返回最近的弹幕 */
    public static List<LiveDanmaku> getLiveDanmaku(long realRoomId) {
        List<LiveDanmaku> list = new ArrayList<LiveDanmaku>();
        try {
            JSONObject data = BiliApi.check(HttpUtil.getJson(
                    "https://api.live.bilibili.com/xlive/web-room/v1/dM/gethistory?roomid=" + realRoomId));
            JSONArray rooms = data.optJSONArray("room");
            if (rooms != null) {
                for (int i = 0; i < rooms.length(); i++) {
                    JSONObject item = rooms.optJSONObject(i);
                    if (item == null) {
                        continue;
                    }
                    LiveDanmaku dm = new LiveDanmaku();
                    dm.text = item.optString("text", "");
                    dm.name = item.optString("nickname", "");
                    dm.uid = item.optLong("uid", 0);
                    dm.timeline = item.optString("timeline", "");
                    list.add(dm);
                }
            }
        } catch (Exception e) {
            android.util.Log.w("LiveApi", "弹幕获取失败: " + e.getMessage());
        }
        return list;
    }

    /** 发送直播弹幕 */
    public static int sendLiveDanmaku(long realRoomId, String msg)
            throws IOException, org.json.JSONException {
        String body = "color=16777215&fontsize=25&mode=1&msg=" + HttpUtil.encode(msg)
                + "&rnd=" + System.currentTimeMillis()
                + "&roomid=" + realRoomId
                + "&bubble=0&csrf=" + HttpUtil.csrf();
        String resp = HttpUtil.postWithLogin("https://api.live.bilibili.com/msg/send", body);
        return new JSONObject(new org.json.JSONTokener(resp)).optInt("code", -1);
    }

    /** 一条直播弹幕 */
    public static class LiveDanmaku {
        public long uid;
        public String name = "";
        public String text = "";
        public String timeline = "";
    }

    /**
     * 直播播放地址（选 http_stream + flv + avc 的原画流）。
     * @return 流地址，失败返回空串
     */
    public static String getLivePlayUrl(long roomId, int qn) {
        try {
            JSONObject data = BiliApi.check(HttpUtil.getJson(
                    "https://api.live.bilibili.com/xlive/web-room/v2/index/getRoomPlayInfo"
                            + "?room_id=" + roomId
                            + "&protocol=0,1&format=0,1,2&codec=0,1&qn=" + qn
                            + "&platform=web&ptype=8&dolby=5&panorama=1"));
            JSONArray streams = data.optJSONObject("playurl_info").optJSONObject("playurl")
                    .optJSONArray("stream");
            if (streams == null) {
                return "";
            }
            // 依次找：http_stream 里 flv/avc；没有就退到 http_hls 的 m3u8
            String hlsFallback = "";
            for (int i = 0; i < streams.length(); i++) {
                JSONObject stream = streams.optJSONObject(i);
                if (stream == null) {
                    continue;
                }
                String protocol = stream.optString("protocol_name", "");
                JSONArray formats = stream.optJSONArray("format");
                if (formats == null) {
                    continue;
                }
                for (int j = 0; j < formats.length(); j++) {
                    JSONObject format = formats.optJSONObject(j);
                    if (format == null) {
                        continue;
                    }
                    String formatName = format.optString("format_name", "");
                    if ("http_hls".equals(protocol) && "ts".equals(formatName)) {
                        hlsFallback = pickUrl(format);
                    }
                    if ("http_stream".equals(protocol) && "flv".equals(formatName)) {
                        JSONArray codecs = format.optJSONArray("codec");
                        if (codecs != null) {
                            for (int k = 0; k < codecs.length(); k++) {
                                JSONObject codec = codecs.optJSONObject(k);
                                if (codec != null && "avc".equals(codec.optString("codec_name", ""))) {
                                    String url = pickUrl(codec);
                                    if (url.length() > 0) {
                                        return url;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return hlsFallback;
        } catch (Exception e) {
            android.util.Log.w("LiveApi", "直播地址获取失败: " + e.getMessage());
            return "";
        }
    }

    private static String pickUrl(JSONObject obj) {
        JSONArray infos = obj.optJSONArray("url_info");
        if (infos != null && infos.length() > 0) {
            JSONObject info = infos.optJSONObject(0);
            String url = info.optString("host", "") + obj.optString("base_url", "")
                    + info.optString("extra", "");
            // IJK 的 ffmpeg 无 openssl，直播流也统一走 http
            if (url.startsWith("https://")) {
                url = "http://" + url.substring(8);
            }
            return url;
        }
        return "";
    }
}
