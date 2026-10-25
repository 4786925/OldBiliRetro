package com.android.oldbili.retro.util;

import android.content.Context;

import java.io.File;

/**
 * 缓存清理：图片缓存、消息/动态页面缓存、临时文件。
 * 退出登录时必须清掉上一个账号的残留（消息、动态、收藏等），防止换人使用泄露隐私。
 */
public class CacheUtil {

    /** 清全部缓存（设置页"清除缓存"） */
    public static void clearAll(Context context) {
        clearUserCache();
        try {
            com.android.oldbili.retro.net.ImageLoader.clearAll();
        } catch (Exception ignored) {
        }
        // 清临时目录（保留下载目录与崩溃日志）
        try {
            File cacheDir = context.getCacheDir();
            if (cacheDir != null && cacheDir.isDirectory()) {
                File[] children = cacheDir.listFiles();
                if (children != null) {
                    for (int i = 0; i < children.length; i++) {
                        deleteTree(children[i]);
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    /** 只清账号相关缓存（退出登录时调用） */
    public static void clearUserCache() {
        try {
            Sp.putString("dynamic_page_cache", "[]");
            Sp.putString("msg_page_cache", "[]");
            // 已读消息/未读标记等账号态
            Sp.putString("msg_unread_cache", "");
        } catch (Exception ignored) {
        }
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (int i = 0; i < children.length; i++) {
                    deleteTree(children[i]);
                }
            }
        }
        file.delete();
    }
}
