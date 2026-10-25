package com.android.oldbili.retro.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 评论（评论区/动态评论共用）。
 */
public class Comment {

    public long rpid;
    public long mid;
    public long floor;          // 楼层号
    public long rcount;         // 楼中楼回复数
    public int action;          // 1=我点过赞 0=未点赞
    public String userName = "";
    public String userFace = "";
    public String message = "";
    public long time;           // unix 秒
    public long like;
    public boolean isUp;        // UP主点赞标识
    public List<Comment> replies = new ArrayList<Comment>(); // 子评论（热评下）
    public List<String> pictures = new ArrayList<String>();  // 图文评论配图 URL

    public String timeDesc() {
        return com.android.oldbili.retro.util.TimeUtil.toRelative(time);
    }
}
