package com.android.oldbili.retro;

import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * 兼容性列表：纯静态 HTML（assets/compat.html），
 * 任何架构 / 任何老系统（含安卓 2.3）的 WebView 都能渲染。
 */
public class CompatActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.compat_title);
        setContentView(R.layout.activity_eula);

        // 兼容性页只读，不需要"同意/不同意"
        findViewById(R.id.eula_buttons).setVisibility(android.view.View.GONE);

        WebView webView = (WebView) findViewById(R.id.web_view);
        webView.getSettings().setBuiltInZoomControls(true);
        webView.getSettings().setSupportZoom(true);
        webView.setBackgroundColor(0xFF101010);
        webView.setWebViewClient(new WebViewClient());
        webView.loadUrl("file:///android_asset/compat.html");
    }
}
