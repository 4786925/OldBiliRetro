package com.android.oldbili.retro.model;

/**
 * 播放历史条目。
 */
public class HistoryItem {

    public long aid;
    public String bvid = "";
    public long cid;
    public String title = "";
    public String cover = "";
    public String upName = "";
    public long viewAt;     // unix 秒
    public int progress;    // 已看秒数
    public int duration;    // 总时长秒
    public String kid = ""; // 历史记录 id（删除单条历史用）

    public String viewAtDesc() {
        return com.android.oldbili.retro.util.TimeUtil.toRelative(viewAt);
    }

    public String progressDesc() {
        return "看到 " + com.android.oldbili.retro.util.TimeUtil.toTime(progress)
                + " / " + com.android.oldbili.retro.util.TimeUtil.toTime(duration);
    }
}
