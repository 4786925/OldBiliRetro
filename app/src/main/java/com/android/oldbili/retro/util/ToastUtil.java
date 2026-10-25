package com.android.oldbili.retro.util;

import android.content.Context;
import android.widget.Toast;

/**
 * Toast 小封装，主线程/子线程都能弹。
 */
public class ToastUtil {

    private static Toast sToast;

    public static void show(final Context context, final String text) {
        if (context == null || text == null) {
            return;
        }
        android.os.Looper looper = android.os.Looper.myLooper();
        if (looper == android.os.Looper.getMainLooper()) {
            showInner(context, text);
        } else {
            new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    showInner(context, text);
                }
            });
        }
    }

    public static void show(final Context context, final int resId) {
        show(context, context == null ? "" : context.getString(resId));
    }

    private static void showInner(Context context, String text) {
        if (sToast != null) {
            sToast.cancel();
        }
        sToast = Toast.makeText(context.getApplicationContext(), text, Toast.LENGTH_SHORT);
        sToast.show();
    }
}
