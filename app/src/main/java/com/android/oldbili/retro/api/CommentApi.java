package com.android.oldbili.retro.api;

import com.android.oldbili.retro.model.Comment;
import com.android.oldbili.retro.net.HttpUtil;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 评论：视频评论 / 动态评论 / 楼中楼 / 发评论 / 点赞。
 */
public class CommentApi {

    /** 上一次评论请求返回的评论总数（cursor.all_count），0 表示未知 */
    public static long sLastTotal;
    /** 上一次楼中楼请求返回的回复总数（0 表示未知） */
    public static long sLastSubTotal;

    /** 视频评论列表，cursor 传空表示第一页，floorBase 是已加载条数（楼层续排），返回 [列表, 下一页cursor] */
    public static Object[] getVideoComments(long oid, int mode, String cursor, int ps, int floorBase)
            throws IOException, org.json.JSONException {
        // mode: 2=按时间 3=按热度；老版 /x/v2/reply/main 已下线，走 WBI 版接口
        String url = "https://api.bilibili.com/x/v2/reply/wbi/main?type=1&oid=" + oid
                + "&plat=1&web_location=1315875&mode=" + mode
                + "&pagination_str=" + HttpUtil.encode("{\"offset\":\""
                + (cursor == null ? "" : cursor) + "\"}");
        // 注意：这个端点不能带 dm_img 风控参数，带了反而 -403（实测）
        url = BiliApi.signWbi(url);
        android.util.Log.i("CommentApi", "评论列表请求: " + url);
        JSONObject data = BiliApi.check(HttpUtil.getJson(url));
        JSONArray replies = data.optJSONArray("replies");
        List<Comment> list = new ArrayList<Comment>();
        if (replies != null) {
            for (int i = 0; i < replies.length(); i++) {
                Comment c = parseReply(replies.optJSONObject(i));
                // 新接口不下发 floor，按时间排序时用返回序号当楼层；热度排序下会置 0 隐藏
                if (c.floor == 0 && mode == 2) {
                    c.floor = floorBase + i + 1;
                }
                list.add(c);
            }
        }
        String next = "";
        JSONObject cursorObj = data.optJSONObject("cursor");
        if (cursorObj != null) {
            sLastTotal = cursorObj.optLong("all_count", 0);
            JSONObject pagination = cursorObj.optJSONObject("pagination_reply");
            if (pagination != null) {
                next = pagination.optString("next_offset", "");
            }
            if (next == null) {
                next = "";
            }
        }
        return new Object[]{list, next};
    }

    /** 楼中楼（子评论）。pn 从 1 开始分页，ps=10 */
    public static List<Comment> getSubComments(long oid, long root, int pn) throws IOException, org.json.JSONException {
        return getSubComments(oid, root, pn, 1);
    }

    /**
     * 楼中楼（子评论）。
     * @param sort 0=按时间 1=按热度
     */
    public static List<Comment> getSubComments(long oid, long root, int pn, int sort)
            throws IOException, org.json.JSONException {
        List<Comment> list = new ArrayList<Comment>();
        String url = "https://api.bilibili.com/x/v2/reply/reply?type=1&oid=" + oid
                + "&root=" + root + "&ps=20&pn=" + (pn <= 0 ? 1 : pn)
                + "&sort=" + sort;
        JSONObject data = BiliApi.check(HttpUtil.getJson(url));
        JSONObject page = data.optJSONObject("page");
        if (page != null) {
            sLastSubTotal = page.optLong("count", 0);
        }
        JSONArray replies = data.optJSONArray("replies");
        if (replies == null) {
            return list;
        }
        for (int i = 0; i < replies.length(); i++) {
            list.add(parseReply(replies.optJSONObject(i)));
        }
        return list;
    }

    private static Comment parseReply(JSONObject r) {
        Comment c = new Comment();
        if (r == null) {
            return c;
        }
        c.rpid = r.optLong("rpid", 0);
        c.mid = r.optLong("mid", 0);
        c.floor = r.optLong("floor", 0);
        c.rcount = r.optLong("rcount", r.optLong("count", 0));
        c.action = r.optInt("action", 0);
        JSONObject member = r.optJSONObject("member");
        if (member != null) {
            c.userName = member.optString("uname", "");
            c.userFace = member.optString("avatar", "");
            // 用户等级：LV0~LV6（6 级以上统一显示 LV6+）
            JSONObject lv = member.optJSONObject("level_info");
            if (lv != null) {
                c.level = lv.optInt("current_level", 0);
            } else {
                c.level = member.optInt("level", 0);
            }
        }
        JSONObject content = r.optJSONObject("content");
        if (content != null) {
            c.message = content.optString("message", "");
            // 图文评论：content.pictures[].img_src
            JSONArray pictures = content.optJSONArray("pictures");
            if (pictures != null) {
                for (int i = 0; i < pictures.length(); i++) {
                    String src = pictures.optJSONObject(i) == null
                            ? "" : pictures.optJSONObject(i).optString("img_src", "");
                    if (src.length() > 0) {
                        c.pictures.add(src);
                    }
                }
            }
        }
        c.time = r.optLong("ctime", 0);
        c.like = r.optLong("like", 0);
        JSONObject upAction = r.optJSONObject("up_action");
        if (upAction != null && upAction.optInt("like", 0) > 0) {
            c.isUp = true;
        }
        JSONArray subReplies = r.optJSONArray("replies");
        if (subReplies != null) {
            for (int i = 0; i < subReplies.length(); i++) {
                c.replies.add(parseReply(subReplies.optJSONObject(i)));
            }
        }
        return c;
    }

    /**
     * 发评论/回复。
     * type: 1=视频评论, 11=动态评论; parent/root 非 0 表示楼中楼回复。
     * 返回接口 code，0 为成功。
     */
    public static long[] addComment(int type, long oid, String message, long root, long parent)
            throws IOException, org.json.JSONException {
        return addComment(type, oid, message, root, parent, "");
    }

    /** 发评论（支持带图，pictureUrl 为空则纯文字）。返回 [code, reply_action, 回显root, 回显parent, 新评论rpid, 我的昵称rpid字段占位, 头像占位] */
    public static long[] addComment(int type, long oid, String message, long root, long parent, String pictureUrl)
            throws IOException, org.json.JSONException {
        StringBuilder body = new StringBuilder();
        body.append("oid=").append(oid)
                .append("&type=").append(type)
                .append("&message=").append(HttpUtil.encode(message))
                .append("&plat=1")
                .append("&csrf=").append(HttpUtil.csrf());
        if (root > 0) {
            body.append("&root=").append(root);
        }
        if (parent > 0) {
            body.append("&parent=").append(parent);
        }
        if (pictureUrl != null && pictureUrl.length() > 0) {
            body.append("&picture=").append(HttpUtil.encode(pictureUrl));
        }
        String resp = HttpUtil.postWithLogin("https://api.bilibili.com/x/v2/reply/add", body.toString());
        JSONObject rootObj = new JSONObject(new org.json.JSONTokener(resp));
        int code = rootObj.optInt("code", -1);
        int action = 0;
        long echoRoot = 0;
        long echoParent = 0;
        long echoRpid = 0;
        JSONObject data = rootObj.optJSONObject("data");
        if (data != null) {
            action = data.optInt("reply_action", 0);
            JSONObject reply = data.optJSONObject("reply");
            if (reply != null) {
                echoRoot = reply.optLong("root", 0);
                echoParent = reply.optLong("parent", 0);
                echoRpid = reply.optLong("rpid", 0);
            }
        }
        android.util.Log.i("CommentApi", "发评论 oid=" + oid + " root=" + root + " parent=" + parent
                + " -> code=" + code + " action=" + action + " 接口回显 root=" + echoRoot
                + " parent=" + echoParent
                + " resp=" + (resp.length() > 160 ? resp.substring(0, 160) : resp));
        return new long[]{code, action, echoRoot, echoParent, echoRpid};
    }

    /** 删除评论/回复 */
    public static int delComment(int type, long oid, long rpid) throws IOException, org.json.JSONException {
        String body = "oid=" + oid + "&type=" + type + "&rpid=" + rpid
                + "&csrf=" + HttpUtil.csrf();
        String resp = HttpUtil.postWithLogin("https://api.bilibili.com/x/v2/reply/del", body);
        return new JSONObject(new org.json.JSONTokener(resp)).optInt("code", -1);
    }

    /**
     * 上传评论配图（multipart）。
     * 成功返回图片 URL；失败返回 null。
     */
    public static String uploadReplyImage(byte[] imageData, String fileName) throws IOException, org.json.JSONException {
        String boundary = "----OldBiliRetro" + Long.toHexString(System.currentTimeMillis());
        String lineEnd = "\r\n";
        String contentType = "image/jpeg";
        int dot = fileName.lastIndexOf('.');
        String ext = dot >= 0 ? fileName.substring(dot + 1).toLowerCase() : "";
        if ("png".equals(ext)) {
            contentType = "image/png";
        } else if ("gif".equals(ext)) {
            contentType = "image/gif";
        } else if ("webp".equals(ext)) {
            contentType = "image/webp";
        }

        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        baos.write(("--" + boundary + lineEnd).getBytes("UTF-8"));
        baos.write(("Content-Disposition: form-data; name=\"file_up\"; filename=\"" + fileName + "\"" + lineEnd).getBytes("UTF-8"));
        baos.write(("Content-Type: " + contentType + lineEnd + lineEnd).getBytes("UTF-8"));
        baos.write(imageData);
        baos.write(lineEnd.getBytes("UTF-8"));
        // category
        baos.write(("--" + boundary + lineEnd).getBytes("UTF-8"));
        baos.write(("Content-Disposition: form-data; name=\"category\"" + lineEnd + lineEnd).getBytes("UTF-8"));
        baos.write(("daily" + lineEnd).getBytes("UTF-8"));
        // biz
        baos.write(("--" + boundary + lineEnd).getBytes("UTF-8"));
        baos.write(("Content-Disposition: form-data; name=\"biz\"" + lineEnd + lineEnd).getBytes("UTF-8"));
        baos.write(("reply" + lineEnd).getBytes("UTF-8"));
        // csrf
        baos.write(("--" + boundary + lineEnd).getBytes("UTF-8"));
        baos.write(("Content-Disposition: form-data; name=\"csrf\"" + lineEnd + lineEnd).getBytes("UTF-8"));
        baos.write((HttpUtil.csrf() + lineEnd).getBytes("UTF-8"));
        baos.write(("--" + boundary + "--" + lineEnd).getBytes("UTF-8"));

        String resp = HttpUtil.postBytesWithLogin(
                "https://api.bilibili.com/x/dynamic/feed/draw/upload_bfs",
                baos.toByteArray(),
                "multipart/form-data; boundary=" + boundary);
        JSONObject root = new JSONObject(new org.json.JSONTokener(resp));
        if (root.optInt("code", -1) != 0) {
            return null;
        }
        JSONObject data = root.optJSONObject("data");
        if (data == null) {
            return null;
        }
        String url = data.optString("image_url", "");
        return url.length() > 0 ? url : null;
    }

    /** 点赞评论：action 1=点赞 0=取消 */
    public static int likeComment(int type, long oid, long rpid, int action) throws IOException, org.json.JSONException {
        String body = "oid=" + oid + "&type=" + type + "&rpid=" + rpid
                + "&action=" + action + "&csrf=" + HttpUtil.csrf();
        String resp = HttpUtil.postWithLogin("https://api.bilibili.com/x/v2/reply/action", body);
        return new JSONObject(new org.json.JSONTokener(resp)).optInt("code", -1);
    }

    /**
     * 旧版分页接口（x/v2/reply）兜底：按 pn 翻页。
     * 游标接口拿不到 next_offset 时用它继续，避免只显示第一页。
     */
    public static List<Comment> getVideoCommentsLegacy(long oid, int sort, int pn, int ps)
            throws IOException, org.json.JSONException {
        List<Comment> list = new ArrayList<Comment>();
        int sortParam = sort == 2 ? 0 : 1; // 0=按时间 1=按热度
        String url = "https://api.bilibili.com/x/v2/reply?type=1&oid=" + oid
                + "&pn=" + (pn <= 0 ? 1 : pn) + "&ps=" + (ps <= 0 ? 20 : ps)
                + "&sort=" + sortParam;
        JSONObject root = HttpUtil.getJson(url);
        JSONObject data = BiliApi.check(root);
        JSONArray replies = data.optJSONArray("replies");
        if (replies == null) {
            return list;
        }
        JSONObject page = data.optJSONObject("page");
        if (page != null) {
            sLastTotal = page.optLong("count", sLastTotal);
        }
        for (int i = 0; i < replies.length(); i++) {
            list.add(parseReply(replies.optJSONObject(i)));
        }
        return list;
    }
}
