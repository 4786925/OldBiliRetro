package com.android.oldbili.retro;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.android.oldbili.retro.util.DeviceInfoUtil;

/**
 * 关于页。
 * 作者名字是个按钮：点一次出现"连击5次有彩蛋"；连击5次打开彩蛋页面（一段Html短文）。
 */
public class AboutActivity extends BaseActivity {

    private int mAuthorClicks;
    private long mLastClickTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.set_about);
        setContentView(R.layout.activity_about);

        ((TextView) findViewById(R.id.version)).setText(
                "版本 " + DeviceInfoUtil.getAppVersion(this));

        Button authorButton = (Button) findViewById(R.id.author_button);
        authorButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                long now = System.currentTimeMillis();
                if (now - mLastClickTime > 2000) {
                    mAuthorClicks = 0;
                }
                mLastClickTime = now;
                mAuthorClicks++;
                if (mAuthorClicks >= 5) {
                    mAuthorClicks = 0;
                    startActivity(new Intent(AboutActivity.this, EggActivity.class));
                } else {
                    toast(R.string.about_egg_tip);
                }
            }
        });
    }
}
