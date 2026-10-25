package com.android.oldbili.retro;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.oldbili.retro.util.DeviceInfoUtil;
import com.android.oldbili.retro.util.DensityUtil;

/**
 * 设备信息检测：机型、安卓版本、CPU 架构、内存、分辨率。
 */
public class DeviceInfoActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.device_info_title);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(DensityUtil.dp2px(this, 16), DensityUtil.dp2px(this, 8),
                DensityUtil.dp2px(this, 16), DensityUtil.dp2px(this, 8));

        addRow(root, "设备型号", DeviceInfoUtil.getDeviceModel());
        addRow(root, "安卓版本", DeviceInfoUtil.getAndroidVersion());
        addRow(root, "CPU 架构", DeviceInfoUtil.getCpuAbi());
        addRow(root, "物理内存", DeviceInfoUtil.getRamInfo());
        addRow(root, "屏幕分辨率", DeviceInfoUtil.getScreenSize(this));
        addRow(root, "应用版本", DeviceInfoUtil.getAppVersion(this));
        addRow(root, "内置播放器", "IJK Player");
        addRow(root, "弹幕引擎", "DanmakuFlameMaster");

        Button export = new Button(this);
        export.setText("导出本页信息");
        export.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                StringBuilder sb = new StringBuilder();
                sb.append("OldBiliRetro 设备信息\n");
                sb.append("设备型号: ").append(DeviceInfoUtil.getDeviceModel()).append('\n');
                sb.append("安卓版本: ").append(DeviceInfoUtil.getAndroidVersion()).append('\n');
                sb.append("CPU 架构: ").append(DeviceInfoUtil.getCpuAbi()).append('\n');
                sb.append("物理内存: ").append(DeviceInfoUtil.getRamInfo()).append('\n');
                sb.append("屏幕分辨率: ").append(DeviceInfoUtil.getScreenSize(DeviceInfoActivity.this)).append('\n');
                sb.append("应用版本: ").append(DeviceInfoUtil.getAppVersion(DeviceInfoActivity.this)).append('\n');
                com.android.oldbili.retro.util.ClipboardCompat.copyOrShow(DeviceInfoActivity.this,
                        sb.toString(), "设备信息已复制到剪贴板");
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = DensityUtil.dp2px(this, 16);
        root.addView(export, lp);

        setContentView(root);
    }

    private void addRow(LinearLayout root, String name, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, DensityUtil.dp2px(this, 8), 0, DensityUtil.dp2px(this, 8));

        TextView nameView = new TextView(this);
        nameView.setText(name);
        nameView.setTextColor(0xFFB3B3B3);
        nameView.setTextSize(14);
        row.addView(nameView, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView valueView = new TextView(this);
        valueView.setText(value);
        valueView.setTextColor(0xFFFFFFFF);
        valueView.setTextSize(14);
        valueView.setGravity(Gravity.RIGHT);
        row.addView(valueView, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1.4f));

        root.addView(row);

        View divider = new View(this);
        divider.setBackgroundColor(0x22FFFFFF);
        root.addView(divider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
    }
}
