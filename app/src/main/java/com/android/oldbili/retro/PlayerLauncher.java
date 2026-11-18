package com.android.oldbili.retro;

import android.content.Context;
import android.content.Intent;

/** 播放器入口：2.3 以下没有内置解码器，直接走外部播放器。 */
public final class PlayerLauncher {

    private PlayerLauncher() {
    }

    public static Intent intent(Context ctx) {
        if (android.os.Build.VERSION.SDK_INT < 9) {
            return new Intent(ctx, ExternalPlayActivity.class);
        }
        return new Intent(ctx, PlayerActivity.class);
    }

    public static void open(Context ctx, Intent intent) {
        if (android.os.Build.VERSION.SDK_INT < 9) {
            intent.setClass(ctx, ExternalPlayActivity.class);
        } else {
            intent.setClass(ctx, PlayerActivity.class);
        }
        ctx.startActivity(intent);
    }
}
