package com.android.oldbili.retro.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 动态条目。
 */
public class DynamicItem {

    public static final int TYPE_AV = 8;      // 视频动态
    public static final int TYPE_TEXT = 4;    // 文字动态
    public static final int TYPE_DRAW = 2;    // 图文动态
    public static final int TYPE_FORWARD = 1; // 转发动态

    public String dynamicId = "";
    public int type;
    public long mid;
    public String userName = "";
    public String userFace = "";
    public String text = "";
    public long time;
    public List<String> pictures = new ArrayList<String>();
    public long like;

    // 视频动态附带的视频信息
    public long aid;
    public String bvid = "";
    public String videoTitle = "";
    public String videoCover = "";
    public long videoPlay;
    public int videoDuration;

    // 转发动态的原动态
    public DynamicItem origin;

    public String timeDesc() {
        return com.android.oldbili.retro.util.TimeUtil.toRelative(time);
    }
}
