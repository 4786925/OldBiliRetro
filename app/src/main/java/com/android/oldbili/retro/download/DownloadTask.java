package com.android.oldbili.retro.download;

/**
 * 一个下载任务（一个分P）。
 */
public class DownloadTask {

    public static final int STATE_WAITING = 0;
    public static final int STATE_DOWNLOADING = 1;
    public static final int STATE_PAUSED = 2;
    public static final int STATE_DONE = 3;
    public static final int STATE_FAILED = 4;

    public long aid;
    public String bvid = "";
    public long cid;
    public String title = "";
    public String part = "";
    public String cover = "";
    public int qn;             // 清晰度 qn 值
    public String qualityName = "";
    public boolean saveCover;
    public String coverLevel = "中"; // 低/中/高

    public int state = STATE_WAITING;
    public long progress;      // 已下载字节
    public long total;         // 总字节
    public String fileName = "";
    public String coverName = "";
    /** 上次进度通知时间戳（内存态，节流用；不参与持久化） */
    public transient long lastNotifyTime;

    /** 进度百分比 */
    public int percent() {
        if (total <= 0) {
            return 0;
        }
        return (int) (progress * 100 / total);
    }

    public String stateDesc() {
        switch (state) {
            case STATE_WAITING:
                return "等待中";
            case STATE_DOWNLOADING:
                return "下载中 " + percent() + "%";
            case STATE_PAUSED:
                return "已暂停 " + percent() + "%";
            case STATE_DONE:
                return "已完成";
            case STATE_FAILED:
                return "下载失败";
            default:
                return "";
        }
    }
}
