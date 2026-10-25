package com.android.oldbili.retro;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * 利用规约（纯文本 HTML，任何设备的老 WebView 都能打开）。
 */
public class EulaActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.set_eula);
        setContentView(R.layout.activity_eula);

        WebView webView = (WebView) findViewById(R.id.web_view);
        webView.getSettings().setBuiltInZoomControls(true);
        webView.setBackgroundColor(0xFF000000);
        webView.setWebViewClient(new WebViewClient());
        webView.loadUrl("file:///android_asset/eula.html");

        // 首次安装：同意前可以先看兼容性列表
        findViewById(R.id.open_compat).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(EulaActivity.this, CompatActivity.class));
            }
        });

        findViewById(R.id.agree).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setResult(RESULT_OK);
                finish();
            }
        });
        findViewById(R.id.disagree).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setResult(RESULT_CANCELED);
                finish();
            }
        });
    }
}
