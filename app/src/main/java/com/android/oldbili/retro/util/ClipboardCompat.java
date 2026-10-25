package com.android.oldbili.retro.util;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.text.ClipboardManager;

/**
 * 剪贴板兼容。
 * 安卓3.0+ 用新接口；2.x 老设备用 android.text.ClipboardManager。
 */
public class ClipboardCompat {

    public static boolean copy(Context context, String text) {
        if (context == null || text == null) {
            return false;
        }
        try {
            if (Build.VERSION.SDK_INT >= 11) {
                android.content.ClipboardManager cm = (android.content.ClipboardManager)
                        context.getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(android.content.ClipData.newPlainText("text", text));
            } else {
                ClipboardManager cm = (ClipboardManager)
                        context.getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setText(text);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 老设备没有系统剪贴板时，弹个可复制的对话框兜底 */
    public static void copyOrShow(final Activity activity, final String text, final String successTip) {
        if (copy(activity, text)) {
            ToastUtil.show(activity, successTip);
            return;
        }
        final android.widget.TextView tv = new android.widget.TextView(activity);
        tv.setPadding(24, 24, 24, 24);
        tv.setTextIsSelectable(true);
        tv.setText(text);
        tv.setTextColor(0xFFFFFFFF);
        new android.app.AlertDialog.Builder(activity)
                .setTitle("复制内容")
                .setView(tv)
                .setPositiveButton("关闭", null)
                .show();
    }
}
