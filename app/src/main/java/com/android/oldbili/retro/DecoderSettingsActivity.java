package com.android.oldbili.retro;

import android.app.AlertDialog;
import android.content.DialogInterface;
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
 * 解码设置（全部选项参照哔哩终端安卓4.0修复版的"内置播放器设置"）：
 * 解码方式（IJK软解/硬解）、音频输出、显示方式、丢帧、
 * 长按倍速、洗脑循环、熄屏继续播放、从历史位置播放、显示实时人数、视频可缩放，
 * 以及弹幕允许重叠/合并重复/强制滚动/最大行数/字号/不透明度/速度。
 */
public class DecoderSettingsActivity extends BaseActivity {

    private LinearLayout mRows;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.decoder_title);
        setContentView(R.layout.activity_decoder_settings);
        mRows = (LinearLayout) findViewById(R.id.rows);

        addDivider();

        addChoose(R.string.decoder_mode, R.string.decoder_mode_desc,
                Sp.SET_DECODER_MODE, "true",
                new String[]{getString(R.string.decoder_hardware), getString(R.string.decoder_software)},
                new String[]{"true", "false"});
        addChoose(R.string.decoder_audio, R.string.decoder_audio_desc,
                Sp.SET_DECODER_AUDIO, "false",
                new String[]{"OpenSL ES", "AudioTrack"},
                new String[]{"true", "false"});
        addChoose(R.string.decoder_display, R.string.decoder_display_desc,
                Sp.SET_DECODER_DISPLAY, "true",
                new String[]{"TextureView", "SurfaceView"},
                new String[]{"true", "false"});

        addDivider();

        addSwitch(R.string.decoder_framedrop, R.string.decoder_framedrop_desc,
                Sp.SET_DECODER_FRAMEDROP, true);
        addSwitch(R.string.decoder_longclick, 0, Sp.SET_PLAYER_LONGCLICK, true);
        addSwitch(R.string.decoder_loop, 0, Sp.SET_PLAYER_LOOP, false);
        addSwitch(R.string.decoder_background, 0, Sp.SET_PLAYER_BACKGROUND, false);
        addSwitch(R.string.decoder_from_last, 0, Sp.SET_PLAYER_FROM_LAST, true);
        addSwitch(R.string.decoder_show_online, 0, Sp.SET_PLAYER_SHOW_ONLINE, false);
        addSwitch(R.string.decoder_scale, 0, Sp.SET_PLAYER_SCALE, true);
        // 弹幕设置已拆到独立的"弹幕设置"页
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
        checkBox.setTextColor(0xFFFFFFFF);
        checkBox.setTextSize(15);
        checkBox.setChecked(Sp.getBoolean(key, def));
        // 安卓4.1以下系统默认 padding 已含按钮预留，覆盖会把文字压到开关上；
        // 只在 4.1+ 手动加边距
        if (android.os.Build.VERSION.SDK_INT >= 16) {
            checkBox.setPadding(DensityUtil.dp2px(this, 14), DensityUtil.dp2px(this, 10),
                    DensityUtil.dp2px(this, 14), DensityUtil.dp2px(this, 10));
        }
        checkBox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Sp.putBoolean(key, checkBox.isChecked());
            }
        });
        mRows.addView(checkBox);

        if (descRes != 0) {
            TextView desc = new TextView(this);
            desc.setText(descRes);
            desc.setTextColor(0xFF7F7F7F);
            desc.setTextSize(11);
            desc.setPadding(DensityUtil.dp2px(this, 14), 0, DensityUtil.dp2px(this, 14),
                    DensityUtil.dp2px(this, 6));
            mRows.addView(desc);
        }
    }

    /** 二选一行（点击弹选择框） */
    private void addChoose(final int titleRes, final int descRes, final String key,
                           String def, final String[] entries, final String[] values) {
        final TextView row = new TextView(this);
        row.setText(titleRes);
        row.setTextColor(0xFFFFFFFF);
        row.setTextSize(15);
        row.setPadding(DensityUtil.dp2px(this, 14), DensityUtil.dp2px(this, 10),
                DensityUtil.dp2px(this, 14), DensityUtil.dp2px(this, 10));
        String current = Sp.getString(key, def);
        String label = entries[entries.length - 1];
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) {
                label = entries[i];
                break;
            }
        }
        row.setText(getString(titleRes) + "  [" + label + "]");
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(DecoderSettingsActivity.this)
                        .setTitle(titleRes)
                        .setItems(entries, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Sp.putString(key, values[which]);
                                rebuildRow(row, titleRes, entries, values, key);
                            }
                        })
                        .show();
            }
        });
        mRows.addView(row);

        if (descRes != 0) {
            TextView desc = new TextView(this);
            desc.setText(descRes);
            desc.setTextColor(0xFF7F7F7F);
            desc.setTextSize(11);
            desc.setPadding(DensityUtil.dp2px(this, 14), 0, DensityUtil.dp2px(this, 14),
                    DensityUtil.dp2px(this, 6));
            mRows.addView(desc);
        }
    }

    private void rebuildRow(TextView row, int titleRes, String[] entries, String[] values, String key) {
        String current = Sp.getString(key, "");
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) {
                row.setText(getString(titleRes) + "  [" + entries[i] + "]");
                return;
            }
        }
    }

    /** 数字输入行 */
    private void addNumber(final int titleRes, int descRes, final String key,
                           final int def, final int min, final int max) {
        final TextView row = new TextView(this);
        row.setText(getString(titleRes) + "  [" + Sp.getInt(key, def) + "]");
        row.setTextColor(0xFFFFFFFF);
        row.setTextSize(15);
        row.setPadding(DensityUtil.dp2px(this, 14), DensityUtil.dp2px(this, 10),
                DensityUtil.dp2px(this, 14), DensityUtil.dp2px(this, 10));
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final EditText input = new EditText(DecoderSettingsActivity.this);
                input.setText(String.valueOf(Sp.getInt(key, def)));
                input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
                new AlertDialog.Builder(DecoderSettingsActivity.this)
                        .setTitle(titleRes)
                        .setView(input)
                        .setPositiveButton(R.string.confirm, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                int value;
                                try {
                                    value = Integer.parseInt(input.getText().toString().trim());
                                } catch (Exception e) {
                                    value = def;
                                }
                                if (value < min) {
                                    value = min;
                                }
                                if (value > max) {
                                    value = max;
                                }
                                Sp.putInt(key, value);
                                row.setText(getString(titleRes) + "  [" + value + "]");
                            }
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .show();
            }
        });
        mRows.addView(row);
    }

    /** 滑块行（0.01 精度） */
    private void addSlider(final int titleRes, int descRes, final String key,
                           final float def, final float min, final float max) {
        final TextView row = new TextView(this);
        row.setText(getString(titleRes) + "  [" + String.format("%.2f", Sp.getFloat(key, def)) + "]");
        row.setTextColor(0xFFFFFFFF);
        row.setTextSize(15);
        row.setPadding(DensityUtil.dp2px(this, 14), DensityUtil.dp2px(this, 10),
                DensityUtil.dp2px(this, 14), DensityUtil.dp2px(this, 10));
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LinearLayout content = new LinearLayout(DecoderSettingsActivity.this);
                content.setOrientation(LinearLayout.VERTICAL);
                content.setPadding(DensityUtil.dp2px(DecoderSettingsActivity.this, 16), 8,
                        DensityUtil.dp2px(DecoderSettingsActivity.this, 16), 0);
                final SeekBar seek = new SeekBar(DecoderSettingsActivity.this);
                seek.setMax(100);
                float current = Sp.getFloat(key, def);
                seek.setProgress(Math.round((current - min) * 100 / (max - min)));
                final TextView valueText = new TextView(DecoderSettingsActivity.this);
                valueText.setText(String.format("%.2f", current));
                valueText.setTextColor(0xFF33B5E5);
                valueText.setGravity(android.view.Gravity.CENTER);
                valueText.setTextSize(15);
                seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        float value = min + progress * (max - min) / 100f;
                        valueText.setText(String.format("%.2f", value));
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                    }
                });
                content.addView(valueText);
                content.addView(seek);
                new AlertDialog.Builder(DecoderSettingsActivity.this)
                        .setTitle(titleRes)
                        .setView(content)
                        .setPositiveButton(R.string.confirm, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                float value = min + seek.getProgress() * (max - min) / 100f;
                                Sp.putFloat(key, value);
                                row.setText(getString(titleRes) + "  [" + String.format("%.2f", value) + "]");
                            }
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .show();
            }
        });
        mRows.addView(row);
    }
}
