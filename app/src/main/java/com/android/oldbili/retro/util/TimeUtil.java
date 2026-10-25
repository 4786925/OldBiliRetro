package com.android.oldbili.retro.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 时间与数字格式化的老一套写法。
 */
public class TimeUtil {

    /** 秒 -> mm:ss 或 hh:mm:ss */
    public static String toTime(int seconds) {
        if (seconds < 0) {
            seconds = 0;
        }
        int h = seconds / 3600;
        int m = (seconds % 3600) / 60;
        int s = seconds % 60;
        if (h > 0) {
            return String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s);
        }
        return String.format(Locale.getDefault(), "%02d:%02d", m, s);
    }

    /** 毫秒 -> mm:ss（播放进度用） */
    public static String toTimeMs(long ms) {
        return toTime((int) (ms / 1000));
    }

    /** unix 秒 -> yyyy-MM-dd */
    public static String toDate(long unixSeconds) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(new Date(unixSeconds * 1000L));
    }

    /** unix 秒 -> yyyy-MM-dd HH:mm:ss */
    public static String toDateTime(long unixSeconds) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date(unixSeconds * 1000L));
    }

    /** 相对时间：刚刚 / n分钟前 / n小时前 / n天前 / 日期 */
    public static String toRelative(long unixSeconds) {
        long now = System.currentTimeMillis() / 1000L;
        long diff = now - unixSeconds;
        if (diff < 60) {
            return "刚刚";
        }
        if (diff < 3600) {
            return (diff / 60) + "分钟前";
        }
        if (diff < 86400) {
            return (diff / 3600) + "小时前";
        }
        if (diff < 86400 * 30) {
            return (diff / 86400) + "天前";
        }
        return toDate(unixSeconds);
    }
}
