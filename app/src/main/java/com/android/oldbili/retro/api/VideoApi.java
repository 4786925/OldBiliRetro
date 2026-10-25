package com.android.oldbili.retro.api;

import com.android.oldbili.retro.model.Collection;
import com.android.oldbili.retro.model.VideoCard;
import com.android.oldbili.retro.model.VideoDetail;
import com.android.oldbili.retro.model.VideoPage;
import com.android.oldbili.retro.net.HttpUtil;
import com.android.oldbili.retro.util.Sp;
import com.android.oldbili.retro.util.TimeUtil;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 视频详情、播放地址、相关视频、在线人数。
 */
public class VideoApi {

    /** 按 BV 号取视频详情 */
    public static VideoDetail getDetail(String bvid) throws IOException, org.json.JSONException {
        JSONObject data = BiliApi.check(HttpUtil.getJson(
                "https://api.bilibili.com/x/web-interface/view?bvid=" + bvid));
        return parseDetail(data);
    }

    /**
     * 本次播放会话内、服务端返回过的全部可用清晰度（逗号分隔，如 "120,116,80,64,16"）。
     * 采用<b>并集累积</b>：B 站对同一视频短时间内重复请求 playurl 会降级
     * （切一次清晰度就只剩 360/720），并集能避免列表被一次降级“缩水”。
     */
    public static volatile String sLastAcceptQuality = "";

    /** 当前播放会话对应的 cid，切换视频时重置并集 */
    private static volatile long sAcceptCid = -1;

    /** 进入新视频时调用：cid 变了就清空可用档位并集 */
    public static void setCurrentCid(long cid) {
        if (cid > 0 && cid != sAcceptCid) {
            sAcceptCid = cid;
            sLastAcceptQuality = "";
        }
    }

    static void rememberAccept(org.json.JSONObject data) {
        try {
            org.json.JSONArray arr = data.optJSONArray("accept_quality");
            if (arr == null || arr.length() == 0) {
                return;
            }
            java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<String>();
            if (sLastAcceptQuality != null && sLastAcceptQuality.length() > 0) {
                String[] old = sLastAcceptQuality.split(",");
                for (int i = 0; i < old.length; i++) {
                    if (old[i].trim().length() > 0) {
                        set.add(old[i].trim());
                    }
                }
            }
            for (int i = 0; i < arr.length(); i++) {
                set.add(String.valueOf(arr.optInt(i)));
            }
            StringBuilder sb = new StringBuilder();
            java.util.Iterator<String> it = set.iterator();
            while (it.hasNext()) {
                if (sb.length() > 0) {
                    sb.append(',');
                }
                sb.append(it.next());
            }
            sLastAcceptQuality = sb.toString();
        } catch (Exception ignored) {
        }
    }

    // ==================== playurl 短时缓存 ====================
    // 服务端对同一 cid 的高清请求有频率限制（重复请求会降级到 720/360）。
    // 90 秒内的相同请求直接复用结果，来回切清晰度不会触发降级。

    private static final long PLAY_CACHE_TTL_MS = 90 * 1000L;
    private static final java.util.LinkedHashMap<String, Object[]> sPlayCache =
            new java.util.LinkedHashMap<String, Object[]>(8, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<String, Object[]> eldest) {
                    return size() > 12;
                }
            };
    private static final java.util.HashMap<String, Long> sPlayCacheTime =
            new java.util.HashMap<String, Long>();

    private static Object[] cacheGet(String key) {
        synchronized (sPlayCache) {
            Long t = sPlayCacheTime.get(key);
            Object[] v = sPlayCache.get(key);
            if (v == null || t == null || System.currentTimeMillis() - t > PLAY_CACHE_TTL_MS) {
                sPlayCache.remove(key);
                sPlayCacheTime.remove(key);
                return null;
            }
            return v;
        }
    }

    private static void cachePut(String key, Object[] value) {
        if (key == null || value == null) {
            return;
        }
        synchronized (sPlayCache) {
            sPlayCache.put(key, value);
            sPlayCacheTime.put(key, System.currentTimeMillis());
        }
    }

    /** 按 AV 号取视频详情 */
    public static VideoDetail getDetail(long aid) throws IOException, org.json.JSONException {
        JSONObject data = BiliApi.check(HttpUtil.getJson(
                "https://api.bilibili.com/x/web-interface/view?aid=" + aid));
        return parseDetail(data);
    }

    private static VideoDetail parseDetail(JSONObject data) {
        VideoDetail v = new VideoDetail();
        v.aid = data.optLong("aid");
        v.bvid = data.optString("bvid", "");
        v.title = data.optString("title", "");
        v.cover = data.optString("pic", "");
        v.desc = data.optString("desc", "");
        v.pubdateDesc = TimeUtil.toDateTime(data.optLong("pubdate", 0));
        v.duration = data.optInt("duration", 0);
        v.copyright = data.optInt("copyright", 1);

        JSONObject stat = data.optJSONObject("stat");
        if (stat != null) {
            v.view = stat.optLong("view", 0);
            v.like = stat.optLong("like", 0);
            v.coin = stat.optLong("coin", 0);
            v.favorite = stat.optLong("favorite", 0);
            v.reply = stat.optLong("reply", 0);
            v.danmaku = stat.optLong("danmaku", 0);
        }

        JSONObject owner = data.optJSONObject("owner");
        if (owner != null) {
            v.upMid = owner.optLong("mid", 0);
            v.upName = owner.optString("name", "");
            v.upFace = owner.optString("face", "");
        }

        JSONArray pages = data.optJSONArray("pages");
        if (pages != null) {
            for (int i = 0; i < pages.length(); i++) {
                JSONObject page = pages.optJSONObject(i);
                if (page != null) {
                    v.pages.add(new VideoPage(page.optLong("cid", 0),
                            page.optString("part", "")));
                }
            }
        }

        JSONArray accept = data.optJSONArray("accept_quality");
        if (accept != null) {
            for (int i = 0; i < accept.length(); i++) {
                int qn = accept.optInt(i, -1);
                if (qn > 0 && !v.qualities.contains(qn)) {
                    v.qualities.add(qn);
                }
            }
        }
        Collections.sort(v.qualities);

        JSONObject season = data.optJSONObject("ugc_season");
        if (season != null) {
            Collection c = new Collection();
            c.id = season.optLong("id", -1);
            c.title = season.optString("title", "");
            c.cover = season.optString("cover", "");
            c.intro = season.optString("intro", "");
            JSONArray sections = season.optJSONArray("sections");
            if (sections != null) {
                for (int i = 0; i < sections.length(); i++) {
                    JSONArray episodes = sections.optJSONObject(i).optJSONArray("episodes");
                    if (episodes == null) {
                        continue;
                    }
                    for (int j = 0; j < episodes.length(); j++) {
                        JSONObject ep = episodes.optJSONObject(j);
                        if (ep == null) {
                            continue;
                        }
                        Collection.Episode e = new Collection.Episode();
                        e.aid = ep.optLong("aid", 0);
                        e.bvid = ep.optString("bvid", "");
                        e.cid = ep.optLong("cid", 0);
                        e.title = ep.optString("title", "");
                        JSONObject arc = ep.optJSONObject("arc");
                        if (arc != null) {
                            e.cover = arc.optString("pic", "");
                        }
                        c.episodes.add(e);
                    }
                }
            }
            if (c.episodes.size() > 0) {
                v.collection = c;
            }
        }
        return v;
    }

    /** 播放地址参数：fnval=1 取单流 MP4/FLV，老播放器最稳 */
    public static String playUrlQuery(String idType, String id, long cid, int qn) {
        return "https://api.bilibili.com/x/player/wbi/playurl?"
                + idType + "=" + id
                + "&cid=" + cid
                + "&qn=" + qn
                + "&fnval=1"
                + "&fnver=0"
                + "&fourk=1"
                + "&platform=pc"
                + "&voice_balance=1"
                + "&gaia_source=pre-load"
                + "&isGaiaAvoided=true";
    }

    /**
     * 拿播放地址。
     * 返回数组：[0]=视频流url [1]=当前实际qn [2]=时长毫秒 [3]=上次播放cid [4]=上次播放进度毫秒
     */
    public static String[] getPlayUrl(String idType, String id, long cid, int qn) throws IOException, org.json.JSONException {
        Object[] result = getPlayUrlCandidates(idType, id, cid, qn);
        java.util.List<String> urls = (java.util.List<String>) result[0];
        if (urls == null || urls.size() == 0) {
            throw new IOException("未取到播放地址");
        }
        return new String[]{urls.get(0), String.valueOf(result[1]),
                String.valueOf(result[2]), String.valueOf(result[3]), String.valueOf(result[4])};
    }

    /**
     * 拿播放地址的候选线路（按优先级排好，播放器逐条尝试）：
     * 返回 [0]=List<String> 候选url [1]=实际qn [2]=时长毫秒 [3]=上次播放cid [4]=上次播放进度毫秒
     */
    public static Object[] getPlayUrlCandidates(String idType, String id, long cid, int qn)
            throws IOException, org.json.JSONException {
        String url = BiliApi.signWbi(playUrlQuery(idType, id, cid, qn));
        JSONObject data = BiliApi.check(HttpUtil.getJsonWithLogin(url));
        rememberAccept(data);
        java.util.List<String> candidates = new java.util.ArrayList<String>();
        JSONArray durl = data.optJSONArray("durl");
        if (durl != null && durl.length() > 0) {
            collectCandidates(durl.optJSONObject(0), candidates);
        }
        if (candidates.size() == 0) {
            JSONObject dash = data.optJSONObject("dash");
            if (dash != null) {
                JSONArray videos = dash.optJSONArray("video");
                if (videos != null && videos.length() > 0) {
                    java.util.List<String> all = new java.util.ArrayList<String>();
                    String base = videos.optJSONObject(0).optString("baseUrl", "");
                    if (base.length() > 0) {
                        all.add(base);
                    }
                    JSONArray backups = videos.optJSONObject(0).optJSONArray("backupUrl");
                    if (backups != null) {
                        for (int i = 0; i < backups.length(); i++) {
                            String b = backups.optString(i, "");
                            if (b.length() > 0) {
                                all.add(b);
                            }
                        }
                    }
                    collectUrls(all, candidates);
                }
            }
        }
        int actualQn = data.optInt("quality", qn);
        long durationMs = data.optLong("timelength", 0);
        long lastCid = data.optLong("last_play_cid", 0);
        int lastPlayMs = data.optInt("last_play_time", 0);
        return new Object[]{candidates, actualQn, durationMs, lastCid, lastPlayMs};
    }

    /**
     * 番剧播放地址（pgc 专用接口）：
     * 先 fnval=1 durl 单流；番剧 durl 只有 360/720 两档，480P/1080P 请求会被降级——
     * 未达标时改走 fnval=4048 dash（video+audio 双流）。
     * 返回 6 元素数组：[0]=video候选 [1]=实际qn [2]=时长毫秒 [3]=0 [4]=0 [5]=audio候选(\n分隔)
     */
    public static Object[] getPgcPlayUrlCandidates(long avid, long cid, long epId, int qn)
            throws IOException, org.json.JSONException {
        return getPgcPlayUrlCandidates(avid, cid, epId, qn, false);
    }

    /**
     * 番剧播放地址（pgc 专用接口）：只走 fnval=1 durl 单流。
     * 番剧单流服务端只提供 360/720 两档（480/1080 只有 dash，本播放器不支持，
     * 音画分离延迟巨大）——请求更高档由服务器自动降到 720。
     * 番剧 480/1080 下载仍可用（下载走普通视频接口的单流通道）。
     * 返回 6 元素：[0]=候选 [1]=qn [2]=时长 [3]=0 [4]=0 [5]=""
     */
    /**
     * 番剧播放地址（pgc 专用接口），按"播放流格式"设置取流：
     * - MP4 模式：fnval=1 durl 单流；
     * - DASH 模式（默认）：360/720 一律单流（音画同步）；1080P 先试单流，
     *   番剧单流只有 360/720，降级时才改用 dash（1080P 及以上由 dash 提供）。
     * 返回 6 元素：[0]=video候选 [1]=实际qn [2]=时长ms [3]=0 [4]=0 [5]=audio候选(\n分隔)
     */
    public static Object[] getPgcPlayUrlCandidates(long avid, long cid, long epId, int qn,
                                                   boolean durlOnly)
            throws IOException, org.json.JSONException {
        String key = "p|" + avid + "|" + cid + "|" + epId + "|" + qn + "|" + durlOnly;
        Object[] hit = cacheGet(key);
        if (hit != null) {
            return hit;
        }
        Object[] out = pgcPlayUrlUncached(avid, cid, epId, qn, durlOnly);
        cachePut(key, out);
        return out;
    }

    private static Object[] pgcPlayUrlUncached(long avid, long cid, long epId, int qn,
                                              boolean durlOnly)
            throws IOException, org.json.JSONException {
        boolean mp4 = durlOnly || "mp4".equals(Sp.getString(Sp.SET_STREAM_FORMAT, "dash"));
        // 720P 及以下一律单流（音画同步优先）
        boolean preferDurl = mp4 || qn <= 64;
        String durlUrl = "https://api.bilibili.com/pgc/player/web/playurl?"
                + "avid=" + avid + "&cid=" + cid
                + "&qn=" + qn
                + "&fnver=0&fnval=1&fourk=1&platform=pc"
                + (epId > 0 ? "&ep_id=" + epId : "");
        try {
            Object[] first = parsePgcPlayUrl(durlUrl);
            @SuppressWarnings("unchecked")
            java.util.List<String> candidates = (java.util.List<String>) first[0];
            int actualQn = (Integer) first[1];
            if (candidates.size() > 0 && (preferDurl || actualQn >= qn)) {
                return first;
            }
        } catch (Exception ignored) {
        }
        if (preferDurl) {
            return parsePgcPlayUrl(durlUrl);
        }
        // 1080P 及以上：dash 双流
        String dashUrl = "https://api.bilibili.com/pgc/player/web/playurl?"
                + "avid=" + avid + "&cid=" + cid
                + "&qn=" + qn
                + "&fnver=0&fnval=4048&fourk=1&platform=pc"
                + (epId > 0 ? "&ep_id=" + epId : "");
        return parsePgcPlayUrl(dashUrl);
    }

    private static Object[] parsePgcPlayUrl(String url) throws IOException, org.json.JSONException {
        JSONObject root = new JSONObject(new org.json.JSONTokener(HttpUtil.getWithLogin(url)));
        int code = root.optInt("code", -1);
        if (code != 0) {
            throw new IOException("接口错误(" + code + ")");
        }
        JSONObject result = root.optJSONObject("result");
        if (result != null) {
            rememberAccept(result);
        }
        if (result == null) {
            throw new IOException("无播放数据");
        }
        java.util.List<String> candidates = new java.util.ArrayList<String>();
        String audioUrl = "";
        JSONArray durl = result.optJSONArray("durl");
        if (durl != null && durl.length() > 0) {
            collectCandidates(durl.optJSONObject(0), candidates);
        }
        if (candidates.size() == 0) {
            JSONObject dash = result.optJSONObject("dash");
            if (dash != null) {
                JSONArray videos = dash.optJSONArray("video");
                if (videos != null && videos.length() > 0) {
                    // 大会员高清只有 dash：baseUrl + backupUrl 一起 http 化排序
                    java.util.List<String> all = new java.util.ArrayList<String>();
                    String base = videos.optJSONObject(0).optString("baseUrl", "");
                    if (base.length() > 0) {
                        all.add(base);
                    }
                    JSONArray backups = videos.optJSONObject(0).optJSONArray("backupUrl");
                    if (backups != null) {
                        for (int i = 0; i < backups.length(); i++) {
                            String b = backups.optString(i, "");
                            if (b.length() > 0) {
                                all.add(b);
                            }
                        }
                    }
                    collectUrls(all, candidates);
                }
                // dash 音轨（供双播放器同步放声音）
                JSONArray audios = dash.optJSONArray("audio");
                if (audios != null && audios.length() > 0) {
                    String abase = audios.optJSONObject(0).optString("baseUrl", "");
                    java.util.List<String> audioAll = new java.util.ArrayList<String>();
                    if (abase.length() > 0) {
                        audioAll.add(abase);
                    }
                    JSONArray abackups = audios.optJSONObject(0).optJSONArray("backupUrl");
                    if (abackups != null) {
                        for (int i = 0; i < abackups.length(); i++) {
                            String b = abackups.optString(i, "");
                            if (b.length() > 0) {
                                audioAll.add(b);
                            }
                        }
                    }
                    java.util.List<String> audioCandidates = new java.util.ArrayList<String>();
                    collectUrls(audioAll, audioCandidates);
                    if (audioCandidates.size() > 0) {
                        // 全部候选用换行拼接，播放器逐个尝试
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < audioCandidates.size(); i++) {
                            if (i > 0) {
                                sb.append('\n');
                            }
                            sb.append(audioCandidates.get(i));
                        }
                        audioUrl = sb.toString();
                    }
                }
            }
        }
        int actualQn = result.optInt("quality", 0);
        long durationMs = result.optLong("timelength", 0);
        return new Object[]{candidates, actualQn, durationMs, 0L, 0L, audioUrl};
    }

    /**
     * 播放专用取流（普通视频）：
     * 先 fnval=1 durl 单流（音画一体最稳）；请求档位未达标时（480/1080 等单流没有的档）
     * 改走 fnval=4048 dash（video 候选 + audio 候选），播放器用双播放器同步。
     * 返回 6 元素：[0]=video候选 [1]=实际qn [2]=时长ms [3]=lastCid [4]=last进度ms [5]=audio候选(\n分隔)
     */
    public static Object[] getPlayUrlCandidatesPlay(String idType, String id, long cid, int qn)
            throws IOException, org.json.JSONException {
        return getPlayUrlCandidatesPlay(idType, id, cid, qn, false);
    }

    /**
     * 播放专用取流（普通视频），按"播放流格式"设置：
     * - MP4 模式：fnval=1 durl 单流；
     * - DASH 模式（默认）：**1080P 及以下优先单流**（音画天然同步，不会延迟），
     *   只有 1080P60/2K 这种单流没有的档才走 fnval=4048 dash（video + audio 双流）。
     * 返回 6 元素：[0]=video候选 [1]=实际qn [2]=时长ms [3]=lastCid [4]=last进度ms [5]=audio候选(\n分隔)
     */
    public static Object[] getPlayUrlCandidatesPlay(String idType, String id, long cid, int qn,
                                                    boolean durlOnly)
            throws IOException, org.json.JSONException {
        String key = "v|" + idType + "|" + id + "|" + cid + "|" + qn + "|" + durlOnly;
        Object[] hit = cacheGet(key);
        if (hit != null) {
            return hit;
        }
        Object[] out = playUrlUncached(idType, id, cid, qn, durlOnly);
        cachePut(key, out);
        return out;
    }

    private static Object[] playUrlUncached(String idType, String id, long cid, int qn,
                                           boolean durlOnly)
            throws IOException, org.json.JSONException {
        boolean mp4 = durlOnly || "mp4".equals(Sp.getString(Sp.SET_STREAM_FORMAT, "dash"));
        // 1080P 及以下一律优先单流（同步优先）
        boolean preferDurl = mp4 || qn <= 80;
        String durlUrl = BiliApi.signWbi(playUrlQuery(idType, id, cid, qn));
        try {
            JSONObject data = BiliApi.check(HttpUtil.getJsonWithLogin(durlUrl));
        rememberAccept(data);
            java.util.List<String> candidates = new java.util.ArrayList<String>();
            JSONArray durl = data.optJSONArray("durl");
            if (durl != null && durl.length() > 0) {
                collectCandidates(durl.optJSONObject(0), candidates);
            }
            int actualQn = data.optInt("quality", qn);
            long durationMs = data.optLong("timelength", 0);
            long lastCid = data.optLong("last_play_cid", 0);
            int lastPlayMs = data.optInt("last_play_time", 0);
            if (candidates.size() > 0 && (preferDurl || actualQn >= qn)) {
                return new Object[]{candidates, actualQn, durationMs, lastCid, lastPlayMs, ""};
            }
        } catch (Exception ignored) {
        }
        if (preferDurl) {
            JSONObject data2 = BiliApi.check(HttpUtil.getJsonWithLogin(durlUrl));
            java.util.List<String> candidates2 = new java.util.ArrayList<String>();
            JSONArray durl2 = data2.optJSONArray("durl");
            if (durl2 != null && durl2.length() > 0) {
                collectCandidates(durl2.optJSONObject(0), candidates2);
            }
            return new Object[]{candidates2, data2.optInt("quality", qn),
                    data2.optLong("timelength", 0), data2.optLong("last_play_cid", 0),
                    data2.optInt("last_play_time", 0), ""};
        }
        // 只有 1080P60/2K 才走 dash 双流
        String dashUrl = BiliApi.signWbi(playUrlQuery(idType, id, cid, qn)
                .replace("fnval=1", "fnval=4048"));
        return parseDashPlayUrl(dashUrl, qn);
    }

    /** 解析 dash 播放数据：video 候选 + audio 候选（\n 分隔） */
    private static Object[] parseDashPlayUrl(String url, int qn)
            throws IOException, org.json.JSONException {
        JSONObject data = BiliApi.check(HttpUtil.getJsonWithLogin(url));
        rememberAccept(data);
        java.util.List<String> candidates = new java.util.ArrayList<String>();
        JSONArray durl = data.optJSONArray("durl");
        if (durl != null && durl.length() > 0) {
            collectCandidates(durl.optJSONObject(0), candidates);
        }
        String audioUrl = "";
        if (candidates.size() == 0) {
            JSONObject dash = data.optJSONObject("dash");
            if (dash != null) {
                JSONArray videos = dash.optJSONArray("video");
                if (videos != null && videos.length() > 0) {
                    java.util.List<String> all = new java.util.ArrayList<String>();
                    String base = videos.optJSONObject(0).optString("baseUrl", "");
                    if (base.length() > 0) {
                        all.add(base);
                    }
                    JSONArray backups = videos.optJSONObject(0).optJSONArray("backupUrl");
                    if (backups != null) {
                        for (int i = 0; i < backups.length(); i++) {
                            String b = backups.optString(i, "");
                            if (b.length() > 0) {
                                all.add(b);
                            }
                        }
                    }
                    collectUrls(all, candidates);
                }
                JSONArray audios = dash.optJSONArray("audio");
                if (audios != null && audios.length() > 0) {
                    java.util.List<String> audioAll = new java.util.ArrayList<String>();
                    String abase = audios.optJSONObject(0).optString("baseUrl", "");
                    if (abase.length() > 0) {
                        audioAll.add(abase);
                    }
                    JSONArray abackups = audios.optJSONObject(0).optJSONArray("backupUrl");
                    if (abackups != null) {
                        for (int i = 0; i < abackups.length(); i++) {
                            String b = abackups.optString(i, "");
                            if (b.length() > 0) {
                                audioAll.add(b);
                            }
                        }
                    }
                    java.util.List<String> audioCandidates = new java.util.ArrayList<String>();
                    collectUrls(audioAll, audioCandidates);
                    if (audioCandidates.size() > 0) {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < audioCandidates.size(); i++) {
                            if (i > 0) {
                                sb.append('\n');
                            }
                            sb.append(audioCandidates.get(i));
                        }
                        audioUrl = sb.toString();
                    }
                }
            }
        }
        int actualQn = data.optInt("quality", qn);
        long durationMs = data.optLong("timelength", 0);
        long lastCid = data.optLong("last_play_cid", 0);
        int lastPlayMs = data.optInt("last_play_time", 0);
        return new Object[]{candidates, actualQn, durationMs, lastCid, lastPlayMs, audioUrl};
    }

    /**
     * 智能选流：内置 IJK 的 ffmpeg 没有 openssl，播不了 https。
     * 主源（移动CDN mountaintoys）的 http 口会拒 400，但备用节点
     * （bilivideo.com / upos-*）的 http 口一直是通的，当年 ijk 版客户端就靠它们。
     * 优先级：原生 http > http化的备用节点 > 原始 https（下载/带TLS的播放器兜底）。
     */
    private static void collectCandidates(JSONObject durlItem, java.util.List<String> candidates) {
        if (durlItem == null) {
            return;
        }
        String mainUrl = durlItem.optString("url", "");
        java.util.List<String> all = new java.util.ArrayList<String>();
        if (mainUrl.length() > 0) {
            all.add(mainUrl);
        }
        JSONArray backups = durlItem.optJSONArray("backup_url");
        if (backups != null) {
            for (int i = 0; i < backups.length(); i++) {
                String b = backups.optString(i, "");
                if (b.length() > 0) {
                    all.add(b);
                }
            }
        }
        collectUrls(all, candidates);
    }

    /**
     * 候选排序（按设备能力）：
     * - armeabi-v7a/x86（so 带 OPENSSL，能播 https）：https 原始地址优先；
     * - arm64 等（无 openssl 版本）：http 化节点优先（B 站给老客户端的明文通道），https 兜底。
     */
    private static void collectUrls(java.util.List<String> all, java.util.List<String> candidates) {
        boolean hasOpenSsl = false;
        try {
            String abi = android.os.Build.CPU_ABI;
            if (abi != null && (abi.contains("armeabi-v7a") || abi.contains("x86"))) {
                hasOpenSsl = true;
            }
        } catch (Exception ignored) {
        }
        if (hasOpenSsl) {
            // https 优先
            for (int i = 0; i < all.size(); i++) {
                String u = all.get(i);
                if (u.startsWith("https://") && !candidates.contains(u)) {
                    candidates.add(u);
                }
            }
            for (int i = 0; i < all.size(); i++) {
                String u = all.get(i);
                if (u.startsWith("http://") && !candidates.contains(u)) {
                    candidates.add(u);
                }
            }
        } else {
            // http 化优先（arm64 无 TLS）
            for (int i = 0; i < all.size(); i++) {
                String u = all.get(i);
                if (u.startsWith("http://")) {
                    candidates.add(u);
                } else if (u.startsWith("https://")) {
                    String host = hostOf(u);
                    if (host != null && (host.contains("bilivideo") || host.contains("upos")
                            || host.contains("hdslb"))) {
                        String httpified = "http://" + u.substring(8);
                        if (!candidates.contains(httpified)) {
                            candidates.add(httpified);
                        }
                    }
                }
            }
            // 兜底：https 原始地址始终保留（下载走系统 TLS，必须能拿到 https）
            for (int i = 0; i < all.size(); i++) {
                String u = all.get(i);
                if (u.startsWith("https://") && !candidates.contains(u)) {
                    candidates.add(u);
                }
            }
        }
    }

    private static String hostOf(String url) {
        try {
            return new java.net.URL(url).getHost();
        } catch (Exception e) {
            int start = url.indexOf("://") + 3;
            int end = url.indexOf('/', start);
            if (end < 0) {
                end = url.length();
            }
            return url.substring(start, end);
        }
    }

    /** 相关视频 */
    public static List<VideoCard> getRelated(long aid, int page) throws IOException, org.json.JSONException {
        List<VideoCard> list = new ArrayList<VideoCard>();
        JSONArray arr = BiliApi.checkArray(HttpUtil.getJson(
                "https://api.bilibili.com/x/web-interface/archive/related?aid=" + aid + "&pn=" + page));
        for (int i = 0; i < arr.length(); i++) {
            list.add(CardParser.parse(arr.optJSONObject(i)));
        }
        return list;
    }

    /** 正在观看人数（字符串，可能带"万"） */
    public static String getOnline(long aid, long cid) {
        try {
            JSONObject data = BiliApi.check(HttpUtil.getJson(
                    "https://api.bilibili.com/x/player/online/total?aid=" + aid + "&cid=" + cid));
            Object total = data.opt("total");
            if (total instanceof Number) {
                return com.android.oldbili.retro.util.NumberUtil.toWan(((Number) total).longValue());
            }
            if (total instanceof String) {
                return (String) total;
            }
        } catch (Exception e) {
            android.util.Log.w("VideoApi", "在线人数获取失败: " + e.getMessage());
        }
        return "";
    }
}
