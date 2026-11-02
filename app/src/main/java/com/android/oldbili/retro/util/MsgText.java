package com.android.oldbili.retro.util;

import org.json.JSONObject;

/**
 * 私信内容的显示处理：
 * - B 站分享卡片是 JSON（{"title":...,"bvid":...}），要显示成可读标题并支持点击打开
 * - 普通文本里的网址要能点开
 */
public class MsgText {

    public static final String KIND_TEXT = "text";
    public static final String KIND_VIDEO = "video";
    public static final String KIND_LINK = "link";

    /** 解析结果 */
    public static class Card {
        public String kind = KIND_TEXT;
        public String title = "";
        public String author = "";
        public String bvid = "";
        public String url = "";
        public String text = "";
    }

    public static boolean looksJson(String s) {
        if (s == null) {
            return false;
        }
        s = s.trim();
        return s.startsWith("{") && s.endsWith("}");
    }

    public static Card parse(String raw) {
        Card c = new Card();
        c.text = raw == null ? "" : raw;
        if (!looksJson(raw)) {
            return c;
        }
        try {
            JSONObject o = new JSONObject(raw.trim());
            String title = first(o, "title", "name", "desc");
            String bvid = o.optString("bvid", "");
            String url = first(o, "url", "uri", "link");
            String author = first(o, "author", "owner", "uname");
            if (bvid.length() > 0) {
                c.kind = KIND_VIDEO;
                c.title = title;
                c.author = author;
                c.bvid = bvid;
                if (c.title.length() == 0) {
                    c.title = "分享视频 " + bvid;
                }
                return c;
            }
            if (url.length() > 0) {
                c.kind = KIND_LINK;
                c.title = title.length() > 0 ? title : url;
                c.url = url;
                return c;
            }
            // 纯文本卡片：{"content":"..."} / {"text":"..."}
            String text = first(o, "content", "text", "message");
            if (text.length() > 0) {
                c.text = text;
                return c;
            }
            if (title.length() > 0) {
                c.text = title;
            }
        } catch (Throwable ignored) {
        }
        return c;
    }

    private static String first(JSONObject o, String... keys) {
        for (int i = 0; i < keys.length; i++) {
            String v = o.optString(keys[i], "");
            if (v != null && v.length() > 0 && !"null".equals(v)) {
                return v;
            }
        }
        return "";
    }

    /** 会话列表里的一行预览 */
    public static String preview(String raw) {
        if (raw == null) {
            return "";
        }
        Card c = parse(raw);
        if (c.kind == KIND_VIDEO) {
            return "[视频] " + c.title;
        }
        if (c.kind == KIND_LINK) {
            return "[链接] " + c.title;
        }
        String t = (c.text == null ? "" : c.text).replace('\n', ' ').trim();
        if (t.length() > 60) {
            t = t.substring(0, 60) + "…";
        }
        return t;
    }

    /** 聊天气泡里显示的内容 */
    public static String bubble(Card c) {
        if (c.kind == KIND_VIDEO) {
            String s = "[视频] " + c.title;
            if (c.author.length() > 0) {
                s = s + "\n" + c.author;
            }
            return s + "\n" + c.bvid + " · 点击播放";
        }
        if (c.kind == KIND_LINK) {
            return "[链接] " + c.title + "\n" + c.url;
        }
        return c.text;
    }
}
