package com.android.oldbili.retro;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * 更新日志：数据来自 assets/changelog.json（由 build-tools/make_changelog.py 从
 * docs/RELEASE_NOTES_*.md 自动生成，保证与 GitHub Release 说明一致）。
 */
public class ChangelogActivity extends BaseActivity {

    private static final int ACCENT = 0xFF33B5E5;
    private static final int TEXT_PRIMARY = 0xFFEDEDED;
    private static final int TEXT_SECONDARY = 0xFFB3B3B3;
    private static final int TEXT_TERTIARY = 0xFF8A8A8A;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("更新日志");

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF121212);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(14);
        root.setPadding(pad, dp(10), pad, dp(20));
        scroll.addView(root);

        int currentCode = currentVersionCode();
        try {
            String json = readAsset("changelog.json");
            JSONObject data = new JSONObject(json);
            JSONArray entries = data.optJSONArray("entries");
            if (entries == null || entries.length() == 0) {
                root.addView(text("暂无更新日志", 13, TEXT_TERTIARY));
            } else {
                for (int i = 0; i < entries.length(); i++) {
                    JSONObject e = entries.optJSONObject(i);
                    if (e == null) {
                        continue;
                    }
                    addEntry(root, e, currentCode);
                }
            }
        } catch (Exception ex) {
            root.addView(text("更新日志读取失败：" + ex.getMessage(), 13, TEXT_TERTIARY));
        }

        setContentView(scroll);
    }

    private void addEntry(LinearLayout root, JSONObject e, int currentCode) {
        int code = e.optInt("code", 0);
        String version = e.optString("version", "");
        String date = e.optString("date", "");

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, dp(12), 0, dp(4));

        TextView name = text(version, 16, TEXT_PRIMARY);
        name.setTypeface(name.getTypeface(), android.graphics.Typeface.BOLD);
        header.addView(name);

        if (code == currentCode) {
            TextView chip = text("当前版本", 10, Color.WHITE);
            chip.setBackgroundColor(ACCENT);
            chip.setPadding(dp(6), dp(2), dp(6), dp(2));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = dp(8);
            header.addView(chip, lp);
        }
        root.addView(header);

        if (date.length() > 0) {
            root.addView(text(date, 11, TEXT_TERTIARY));
        }

        JSONArray items = e.optJSONArray("items");
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                String s = items.optString(i, "");
                if (s.length() == 0) {
                    continue;
                }
                TextView tv = text("· " + s, 13, TEXT_SECONDARY);
                tv.setLineSpacing(dp(3), 1f);
                tv.setPadding(0, dp(4), 0, 0);
                root.addView(tv);
            }
        }

        JSONArray known = e.optJSONArray("known");
        if (known != null && known.length() > 0) {
            TextView k = text("已知问题", 12, 0xFFE8B04B);
            k.setPadding(0, dp(8), 0, 0);
            root.addView(k);
            for (int i = 0; i < known.length(); i++) {
                String s = known.optString(i, "");
                if (s.length() == 0) {
                    continue;
                }
                TextView tv = text("· " + s, 12, TEXT_TERTIARY);
                tv.setPadding(0, dp(3), 0, 0);
                root.addView(tv);
            }
        }

        android.view.View divider = new android.view.View(this);
        divider.setBackgroundColor(0xFF2A2A2A);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        dlp.topMargin = dp(14);
        root.addView(divider, dlp);
    }

    private TextView text(String s, int sizeSp, int color) {
        TextView tv = new TextView(this);
        tv.setText(s);
        tv.setTextSize(sizeSp);
        tv.setTextColor(color);
        return tv;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private String readAsset(String name) throws Exception {
        InputStream in = getAssets().open(name);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int len;
        while ((len = in.read(buf)) > 0) {
            out.write(buf, 0, len);
        }
        in.close();
        return new String(out.toByteArray(), "UTF-8");
    }

    private int currentVersionCode() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
        } catch (Exception e) {
            return -1;
        }
    }
}
