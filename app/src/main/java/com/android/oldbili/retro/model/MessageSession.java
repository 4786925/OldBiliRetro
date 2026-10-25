package com.android.oldbili.retro.model;

/**
 * 消息会话。
 * kind=0 私信会话；kind=1 特别关注更新视频条目。
 */
public class MessageSession {

    public static final int KIND_CHAT = 0;
    public static final int KIND_FOLLOW_UP = 1;

    public int kind = KIND_CHAT;
    public long uid;            // 私信对方 uid（特别关注时为 UP 的 mid）
    public String name = "";
    public String face = "";
    public String lastMsg = "";
    public long time;
    public int unread;
    /** 特别关注条目：视频信息 */
    public String bvid = "";
    public String videoTitle = "";
    public String videoCover = "";
    /** 本地置顶标记（不持久化在模型，由页面用 Sp 维护） */
    public boolean top;

    public String timeDesc() {
        return com.android.oldbili.retro.util.TimeUtil.toRelative(time);
    }
}
