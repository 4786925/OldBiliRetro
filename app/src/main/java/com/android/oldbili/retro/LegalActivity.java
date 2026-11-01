package com.android.oldbili.retro;

import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 法律信息：列明本软件遵守/涉及的主要法律法规，便于合规审查与免责说明。
 */
public class LegalActivity extends BaseActivity {

    private static final String[] LAWS = {
            "《中华人民共和国网络安全法》",
            "《中华人民共和国个人信息保护法》",
            "《中华人民共和国数据安全法》",
            "《中华人民共和国著作权法》",
            "《计算机软件保护条例》",
            "《信息网络传播权保护条例》",
            "《中华人民共和国民法典》（人格权编、侵权责任编）",
            "《中华人民共和国未成年人保护法》",
            "《网络信息内容生态治理规定》",
            "《互联网信息服务管理办法》",
            "《移动互联网应用程序信息服务管理规定》",
            "《App 违法违规收集使用个人信息行为认定方法》"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("法律信息");

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF101010);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(14);
        root.setPadding(pad, pad, pad, dp(24));
        scroll.addView(root);

        root.addView(text("本软件为第三方开源客户端，与哔哩哔哩官方无任何关联。\n"
                + "开发者仅提供播放/浏览工具，不存储、不传播任何视频内容，"
                + "所有内容版权归原权利人所有。\n"
                + "用户应遵守下列法律法规及平台协议，因使用本软件产生的任何后果由用户自行承担。", 13, 0xFFB3B3B3));

        root.addView(text("\n涉及的主要法律法规：", 14, 0xFFEDEDED));
        for (int i = 0; i < LAWS.length; i++) {
            TextView tv = text("· " + LAWS[i], 13, 0xFFB3B3B3);
            tv.setPadding(0, dp(5), 0, 0);
            root.addView(tv);
        }

        root.addView(text("\n免责声明：", 14, 0xFFEDEDED));
        root.addView(text("1. 本软件不以任何形式提供或传播受版权保护的内容；\n"
                + "2. 所有视频、弹幕、评论等数据均来自哔哩哔哩公开接口，版权归原作者所有；\n"
                + "3. 使用者需自行确保其行为符合当地法律法规；\n"
                + "4. 本软件按“现状”提供，不对可用性、合法性作任何明示或默示担保；\n"
                + "5. 如权利人认为存在侵权，请通过关于页联系方式告知，开发者将及时处理。", 13, 0xFFB3B3B3));

        setContentView(scroll);
    }

    private TextView text(String s, int sizeSp, int color) {
        TextView tv = new TextView(this);
        tv.setText(s);
        tv.setTextSize(sizeSp);
        tv.setTextColor(color);
        tv.setLineSpacing(dp(3), 1f);
        return tv;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
