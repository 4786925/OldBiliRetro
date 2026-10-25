package com.android.oldbili.retro.model;

/**
 * 分 P（视频段）。
 */
public class VideoPage {

    public long cid;
    public String title = "";

    public VideoPage(long cid, String title) {
        this.cid = cid;
        this.title = title;
    }
}
