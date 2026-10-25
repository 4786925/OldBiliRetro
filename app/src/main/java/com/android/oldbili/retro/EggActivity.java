package com.android.oldbili.retro;

import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * 彩蛋页：一段 Html 短文（现在是空白占位，后续再加内容）。
 */
public class EggActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("彩蛋");
        WebView webView = new WebView(this);
        webView.setBackgroundColor(0xFF000000);
        webView.setWebViewClient(new WebViewClient());
        setContentView(webView);
        webView.loadUrl("file:///android_asset/egg.html");
    }
}
