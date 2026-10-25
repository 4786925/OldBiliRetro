package com.android.oldbili.retro.util;

import android.content.Context;

/**
 * 密度换算：dp/sp 与 px 互转。
 */
public class DensityUtil {

    public static int dp2px(Context context, float dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
    }

    public static int sp2px(Context context, float sp) {
        float scaled = context.getResources().getDisplayMetrics().scaledDensity;
        return (int) (sp * scaled + 0.5f);
    }
}
