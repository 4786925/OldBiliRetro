package com.android.oldbili.retro.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 视频合集（一个人上传 n 个视频到合集，详情页显示"共n段视频"）。
 */
public class Collection {

    public long id;
    public String title = "";
    public String cover = "";
    public String intro = "";
    public long mid;
    public long view;
    public List<Episode> episodes = new ArrayList<Episode>();

    public static class Episode {
        public long aid;
        public String bvid = "";
        public long cid;
        public String title = "";
        public String cover = "";
    }
}
