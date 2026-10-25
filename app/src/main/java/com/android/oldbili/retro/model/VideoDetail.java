package com.android.oldbili.retro.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 视频详情。
 */
public class VideoDetail {

    public long aid;
    public String bvid = "";
    public String title = "";
    public String cover = "";
    public String desc = "";
    public String pubdateDesc = "";
    public int duration;            // 秒
    public long view;
    public long like;
    public long coin;
    public long favorite;
    public long reply;
    public long danmaku;
    public long upMid;
    public String upName = "";
    public String upFace = "";
    public int copyright;           // 1=原创 2=转载

    public List<VideoPage> pages = new ArrayList<VideoPage>();
    /** 可用清晰度（qn 值，升序） */
    public List<Integer> qualities = new ArrayList<Integer>();

    /** 合集（ugc_season） */
    public Collection collection;

    public boolean isCollection() {
        return collection != null && collection.episodes.size() > 0;
    }

    /** 共 n 段 */
    public int partCount() {
        return pages == null ? 0 : pages.size();
    }
}
