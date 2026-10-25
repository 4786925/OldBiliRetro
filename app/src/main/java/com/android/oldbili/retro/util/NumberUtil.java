package com.android.oldbili.retro.util;

import java.util.Locale;

/**
 * 播放量/粉丝数等大数字的老式缩写：万、亿。
 */
public class NumberUtil {

    public static String toWan(long num) {
        if (num >= 100000000L) {
            return formatOne(num / 100000000.0) + "亿";
        }
        if (num >= 10000L) {
            return formatOne(num / 10000.0) + "万";
        }
        return String.valueOf(num);
    }

    /** 保留一位小数，整数则不带小数 */
    private static String formatOne(double value) {
        String s = String.format(Locale.getDefault(), "%.1f", value);
        if (s.endsWith(".0")) {
            return s.substring(0, s.length() - 2);
        }
        return s;
    }

    /** 安全解析整数，失败给默认值 */
    public static int safeInt(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return def;
        }
    }

    public static long safeLong(String s, long def) {
        try {
            return Long.parseLong(s);
        } catch (Exception e) {
            return def;
        }
    }
}
