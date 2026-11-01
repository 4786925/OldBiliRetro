package com.android.oldbili.retro.model;

/** 消息中心的互动消息（回复 / @我 / 点赞 / 系统通知） */
public class MsgFeedItem {
    public long id;
    public long mid;
    public String userName = "";
    public String avatar = "";
    public String title = "";      // 来源标题（视频标题 / 动态内容开头）
    public String text = "";       // 对方说了什么
    public String uri = "";        // 跳转地址
    public long time;              // 秒
    public String timeDesc = "";
    /** 评论 rpid（root_id） */
    public long rootId;
    /** 被回复的具体评论 rpid（target_id） */
    public long targetId;
    /** 来源视频 aid（subject_id） */
    public long sourceId;
}
