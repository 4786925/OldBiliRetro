package com.android.oldbili.retro.util;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 设置存取的小封装。
 * 所有开关、登录信息、历史记录都从这里读写。
 */
public class Sp {

    public static final String COOKIES = "cookies";
    public static final String CSRF = "csrf";
    public static final String MID = "mid";
    public static final String UI_SCALE = "ui_scale";
    public static final String EULA_OK = "eula_ok";

    public static final String SET_HOME_DEFAULT = "set_home_default";
    public static final String SET_RECOMMEND_SOURCE = "set_recommend_source";
    public static final String SET_HOME_LIVE = "set_home_live";
    /** 首页显示布局：single=单行大图 double=双行紧凑 */
    public static final String SET_HOME_LAYOUT = "set_home_layout";
    public static final String SET_DANMAKU_ENGINE = "set_danmaku_engine";
    public static final String SET_IMAGE_QUALITY = "set_image_quality";
    public static final String SET_COMMENT_SORT = "set_comment_sort";
    public static final String SET_INCOGNITO = "set_incognito";

    public static final String SET_DECODER_MODE = "set_decoder_mode";
    public static final String SET_DECODER_AUDIO = "set_decoder_audio";
    public static final String SET_DECODER_DISPLAY = "set_decoder_display";
    public static final String SET_DECODER_FRAMEDROP = "set_decoder_framedrop";
    public static final String SET_PLAYER_LONGCLICK = "set_player_longclick";
    public static final String SET_PLAYER_LOOP = "set_player_loop";
    public static final String SET_PLAYER_BACKGROUND = "set_player_background";
    public static final String SET_PLAYER_FROM_LAST = "set_player_from_last";
    public static final String SET_PLAYER_SHOW_ONLINE = "set_player_show_online";
    public static final String SET_PLAYER_SCALE = "set_player_scale";
    public static final String SET_DANMAKU_OVERLAP = "set_danmaku_overlap";
    public static final String SET_DANMAKU_MERGE = "set_danmaku_merge";
    public static final String SET_DANMAKU_FORCE_SCROLL = "set_danmaku_force_scroll";
    public static final String SET_DANMAKU_MAXLINE = "set_danmaku_maxline";
    // 老机模式（安卓 2 自动开）：少图 / 低端渲染
    public static final String LOW_IMAGE_MODE = "low_image_mode";
    public static final String LOW_END_MODE = "low_end_mode";
    public static final String SET_DANMAKU_SIZE = "set_danmaku_size";
    public static final String SET_DANMAKU_ALPHA = "set_danmaku_alpha";
    public static final String SET_DANMAKU_SPEED = "set_danmaku_speed";

    public static final String SET_DEFAULT_QUALITY = "set_default_quality";
    /** 播放流格式：dash（全清晰度，默认）/ mp4（单流最稳） */
    public static final String SET_STREAM_FORMAT = "set_stream_format";
    public static final String SET_AUTO_ROTATE = "set_auto_rotate";
    public static final String SET_DANMAKU_DEFAULT = "set_danmaku_default";
    /** 弹幕状态浮层（屏幕右上角调试信息） */
    public static final String SET_DANMAKU_DEBUG = "set_danmaku_debug";
    public static final String SET_EXTERNAL_PLAYER = "set_external_player";
    public static final String SET_EXTERNAL_PLAYER_CHOICE = "set_external_player_choice";
    public static final String SET_DOWNLOAD_DIR = "set_download_dir";
    public static final String SET_DOWNLOAD_COVER = "set_download_cover";
    public static final String SET_DOWNLOAD_QUALITY = "set_download_quality";

    public static final String SEARCH_HISTORY = "search_history";

    private static SharedPreferences sSp;

    public static void init(Context context) {
        if (sSp == null) {
            sSp = context.getApplicationContext().getSharedPreferences("oldbili", Context.MODE_PRIVATE);
        }
    }

    public static String getString(String key, String def) {
        return sSp.getString(key, def);
    }

    public static int getInt(String key, int def) {
        return sSp.getInt(key, def);
    }

    public static long getLong(String key, long def) {
        return sSp.getLong(key, def);
    }

    public static boolean getBoolean(String key, boolean def) {
        return sSp.getBoolean(key, def);
    }

    public static float getFloat(String key, float def) {
        return sSp.getFloat(key, def);
    }

    public static void putString(String key, String value) {
        sSp.edit().putString(key, value).commit();
    }

    public static void putInt(String key, int value) {
        sSp.edit().putInt(key, value).commit();
    }

    public static void putLong(String key, long value) {
        sSp.edit().putLong(key, value).commit();
    }

    public static void putBoolean(String key, boolean value) {
        sSp.edit().putBoolean(key, value).commit();
    }

    public static void putFloat(String key, float value) {
        sSp.edit().putFloat(key, value).commit();
    }
}
