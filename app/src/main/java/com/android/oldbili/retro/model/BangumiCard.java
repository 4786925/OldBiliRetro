package com.android.oldbili.retro.model;

/**
 * 追番/追剧卡片。
 */
public class BangumiCard {

    public long seasonId;
    public String title = "";
    public String cover = "";
    public int total;           // 总集数
    public int progress = -1;   // 看到第几集（-1 未看）
    public String badge = "";   // 会员专享/独家等角标
    public boolean isFinish;    // 已完结

    public String progressDesc() {
        if (progress < 0) {
            return total > 0 ? "共 " + total + " 集" : "";
        }
        return "看到第 " + progress + " 集 / 共 " + total + " 集";
    }
}
