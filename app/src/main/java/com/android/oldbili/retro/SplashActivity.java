package com.android.oldbili.retro;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.android.oldbili.retro.util.DeviceInfoUtil;
import com.android.oldbili.retro.util.Sp;

/**
 * 开屏页。
 * 首次安装：先同意利用规约，再设界面大小（大中小滑块，手机/平板/手表都能正常显示）；
 * 之后直接进主界面。
 */
public class SplashActivity extends BaseActivity {

    private LinearLayout mSetupLayout;
    private LinearLayout mEulaLayout;
    private SeekBar mScaleSeek;
    private TextView mScaleValue;
    private TextView mScalePreview;
    private TextView mLoading;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setShowBack(false);
        // 老机版本策略（按安卓版本）：清晰度默认档/少图/低端渲染
        com.android.oldbili.retro.util.DeviceCompat.init(this);
        com.android.oldbili.retro.util.DeviceInfoUtil.init(this);
        setContentView(R.layout.activity_splash);

        mSetupLayout = (LinearLayout) findViewById(R.id.setup_layout);
        mEulaLayout = (LinearLayout) findViewById(R.id.eula_layout);
        mScaleSeek = (SeekBar) findViewById(R.id.scale_seek);
        mScaleValue = (TextView) findViewById(R.id.scale_value);
        mScalePreview = (TextView) findViewById(R.id.scale_preview);
        mLoading = (TextView) findViewById(R.id.loading);

        TextView version = (TextView) findViewById(R.id.version);
        version.setText("版本 " + DeviceInfoUtil.getAppVersion(this));

        boolean eulaOk = Sp.getBoolean(Sp.EULA_OK, false);
        if (!eulaOk) {
            // 首次安装：先看规约
            mEulaLayout.setVisibility(View.VISIBLE);
            mLoading.setVisibility(View.GONE);
            findViewById(R.id.eula_view).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(SplashActivity.this, EulaActivity.class));
                }
            });
            findViewById(R.id.eula_agree).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Sp.putBoolean(Sp.EULA_OK, true);
                    // 不再显示界面大小滑块，默认 100%
                    Sp.putFloat(Sp.UI_SCALE, 1.0f);
                    enter();
                }
            });
            findViewById(R.id.eula_exit).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
            return;
        }

        // 不再显示界面大小滑块，默认 100%
        if (Sp.getFloat(Sp.UI_SCALE, 0f) <= 0.1f) {
            Sp.putFloat(Sp.UI_SCALE, 1.0f);
        }
        enter();
    }

    private void showScaleSetup() {
        mEulaLayout.setVisibility(View.GONE);
        mLoading.setVisibility(View.GONE);
        mSetupLayout.setVisibility(View.VISIBLE);

        // 滑块：0.75 ~ 1.35，步进 0.05，默认 1.0（中）
        float current = Sp.getFloat(Sp.UI_SCALE, 1.0f);
        int progress = Math.round((current - 0.75f) / 0.05f);
        if (progress < 0) {
            progress = 0;
        }
        if (progress > 12) {
            progress = 12;
        }
        mScaleSeek.setProgress(progress);
        updateScale(progress);

        mScaleSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int p, boolean fromUser) {
                updateScale(p);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        final Button done = (Button) findViewById(R.id.scale_done);
        done.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                enter();
            }
        });
    }

    private void updateScale(int progress) {
        float scale = 0.75f + progress * 0.05f;
        Sp.putFloat(Sp.UI_SCALE, scale);
        String label;
        if (scale < 0.9f) {
            label = getString(R.string.ui_scale_small);
        } else if (scale > 1.1f) {
            label = getString(R.string.ui_scale_big);
        } else {
            label = getString(R.string.ui_scale_mid);
        }
        mScaleValue.setText(label + " (" + Math.round(scale * 100) + "%)");
        // 预览文字跟着变，所见即所得
        mScalePreview.setTextSize(10f * scale);
    }

    private void enter() {
        // 留个 300ms 的启动画面，然后进主界面
        new android.os.Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                startActivity(new Intent(SplashActivity.this, MainActivity.class));
                finish();
            }
        }, 300);
    }
}
