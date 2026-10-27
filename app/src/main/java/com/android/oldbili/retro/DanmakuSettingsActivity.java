package com.android.oldbili.retro;

import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.android.oldbili.retro.util.DensityUtil;
import com.android.oldbili.retro.util.Sp;

/**
 * 弹幕设置（独立页）：引擎开关、允许重叠、合并重复、强制滚动、
 * 最大行数、字号、不透明度、速度。
 */
public class DanmakuSettingsActivity extends BaseActivity {

    private LinearLayout mRows;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 只有从播放器进入时才强制横屏；从"我的→设置"进来保持竖屏
        if (getIntent() != null && getIntent().getBooleanExtra("landscape", false)) {
            setRequestedOrientation(
                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
        setTitle("弹幕设置");
        setContentView(R.layout.activity_decoder_settings);
        mRows = (LinearLayout) findViewById(R.id.rows);

        addDivider();

        addSwitch(R.string.decoder_danmaku_engine, 0, Sp.SET_DANMAKU_ENGINE, true);
        addSwitch(R.string.decoder_danmaku_overlap, 0, Sp.SET_DANMAKU_OVERLAP, false);
        addSwitch(R.string.decoder_danmaku_merge, 0, Sp.SET_DANMAKU_MERGE, false);
        addSwitch(R.string.decoder_danmaku_force_scroll, 0, Sp.SET_DANMAKU_FORCE_SCROLL, false);
        addSwitch(R.string.danmaku_debug_title, 0, Sp.SET_DANMAKU_DEBUG, false);
        // 最大行数也用滑块：0=自动铺满
        addIntSlider(R.string.decoder_danmaku_maxline, Sp.SET_DANMAKU_MAXLINE, 0, 0, 30);
        addSlider(R.string.decoder_danmaku_size, 0, Sp.SET_DANMAKU_SIZE, 1.0f, 0.5f, 1.5f);
        addSlider(R.string.decoder_danmaku_alpha, 0, Sp.SET_DANMAKU_ALPHA, 0.6f, 0.1f, 1.0f);
        addSlider(R.string.decoder_danmaku_speed, 0, Sp.SET_DANMAKU_SPEED, 1.0f, 0.5f, 2.0f);
    }

    private void addDivider() {
        View v = new View(this);
        v.setBackgroundColor(0x33FFFFFF);
        mRows.addView(v, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
    }

    /** 开关行 */
    private void addSwitch(final int titleRes, int descRes, final String key, boolean def) {
        final CheckBox checkBox = new CheckBox(this);
        checkBox.setText(titleRes);
        checkBox.setTextColor(0xFFDDDDDD);
        checkBox.setChecked(Sp.getBoolean(key, def));
        if (descRes != 0) {
            checkBox.setHint(getString(descRes));
        }
        checkBox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Sp.putBoolean(key, checkBox.isChecked());
            }
        });
        mRows.addView(checkBox, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (int) (40 * getResources().getDisplayMetrics().density)));
    }

    /** 数字输入行 */
    private void addNumber(final int titleRes, int descRes, final String key,
                           final int def, final int min, final int max) {
        TextView label = new TextView(this);
        label.setText(titleRes);
        label.setTextColor(0xFFDDDDDD);
        label.setTextSize(14);
        mRows.addView(label, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (int) (36 * getResources().getDisplayMetrics().density)));

        final EditText input = new EditText(this);
        input.setText(String.valueOf(Sp.getInt(key, def)));
        input.setTextColor(0xFFFFFFFF);
        input.setTextSize(14);
        input.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    commitNumber(input, key, def, min, max);
                }
            }
        });
        mRows.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (int) (40 * getResources().getDisplayMetrics().density)));
    }

    private void commitNumber(EditText input, String key, int def, int min, int max) {
        try {
            int value = Integer.parseInt(input.getText().toString().trim());
            if (value < min) {
                value = min;
            }
            if (value > max) {
                value = max;
            }
            input.setText(String.valueOf(value));
            Sp.putInt(key, value);
        } catch (NumberFormatException e) {
            input.setText(String.valueOf(def));
            Sp.putInt(key, def);
        }
    }

    /** 整数滑块行（0 显示"自动"） */
    private void addIntSlider(final int titleRes, final String key,
                              final int def, final int min, final int max) {
        final TextView label = new TextView(this);
        final int value = Sp.getInt(key, def);
        label.setText(getString(titleRes) + "（" + (value <= 0 ? "自动" : String.valueOf(value)) + "）");
        label.setTextColor(0xFFDDDDDD);
        label.setTextSize(14);
        mRows.addView(label, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (int) (36 * getResources().getDisplayMetrics().density)));

        final SeekBar bar = new SeekBar(this);
        bar.setMax(max - min);
        bar.setProgress(Math.max(0, Math.min(max - min, value - min)));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) {
                    return;
                }
                int v = min + progress;
                Sp.putInt(key, v);
                label.setText(getString(titleRes) + "（" + (v <= 0 ? "自动" : String.valueOf(v)) + "）");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        mRows.addView(bar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (int) (40 * getResources().getDisplayMetrics().density)));
    }

    /** 滑块行（浮点） */
    private void addSlider(final int titleRes, int descRes, final String key,
                           final float def, final float min, final float max) {
        final TextView label = new TextView(this);
        final float value = Sp.getFloat(key, def);
        label.setText(getString(titleRes) + "（" + value + "）");
        label.setTextColor(0xFFDDDDDD);
        label.setTextSize(14);
        mRows.addView(label, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (int) (36 * getResources().getDisplayMetrics().density)));

        final SeekBar bar = new SeekBar(this);
        bar.setMax(100);
        bar.setProgress((int) ((value - min) / (max - min) * 100));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) {
                    return;
                }
                float v = min + (max - min) * progress / 100f;
                v = Math.round(v * 10) / 10f;
                Sp.putFloat(key, v);
                label.setText(getString(titleRes) + "（" + v + "）");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        mRows.addView(bar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (int) (40 * getResources().getDisplayMetrics().density)));
    }
}
