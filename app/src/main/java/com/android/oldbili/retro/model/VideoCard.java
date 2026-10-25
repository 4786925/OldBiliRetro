package com.android.oldbili.retro.model;

/**
 * 视频卡片：列表页通用，搜索/推荐/历史/收藏/稍后再看全用它。
 */
public class VideoCard {

    public long aid;
    public String bvid = "";
    public long cid;

    /** 是否可打开详情（有 aid 或 bvid 才算有效卡片；接口里的广告/失效卡两者都是空的） */
    public boolean isPlayable() {
        return aid > 0 || (bvid != null && bvid.length() > 0);
    }
    public String title = "";
    public String cover = "";
    public long play;
    public long danmaku;
    public int duration;      // 秒
    public long upMid;
    public String upName = "";
    public String upFace = "";
    public String badge = ""; // 热门榜排名之类的角标文字

    /** 播放数文案 */
    public String playDesc() {
        return com.android.oldbili.retro.util.NumberUtil.toWan(play);
    }

    /** 弹幕数文案 */
    public String danmakuDesc() {
        return com.android.oldbili.retro.util.NumberUtil.toWan(danmaku);
    }

    /** 时长文案 */
    public String durationDesc() {
        return com.android.oldbili.retro.util.TimeUtil.toTime(duration);
    }
}
