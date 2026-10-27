package com.android.oldbili.retro;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;

/**
 * 内置浏览器：官网 / 专栏 / 番剧等外部页面。
 */
public class WebActivity extends BaseActivity {

    private WebView mWebView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web);

        String url = getIntent() == null ? null : getIntent().getStringExtra("url");
        if (url == null || url.length() == 0) {
            url = "https://www.bilibili.com";
        }
        setTitle(getIntent() == null ? null : getIntent().getStringExtra("title"));

        mWebView = (WebView) findViewById(R.id.web_view);
        final ProgressBar bar = (ProgressBar) findViewById(R.id.web_progress);
        mWebView.getSettings().setJavaScriptEnabled(true);
        mWebView.getSettings().setBuiltInZoomControls(true);
        mWebView.setBackgroundColor(0xFF101010);
        mWebView.setWebViewClient(new WebViewClient());
        mWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                bar.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
                bar.setProgress(newProgress);
            }
        });
        mWebView.loadUrl(url);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && mWebView != null && mWebView.canGoBack()) {
            mWebView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        if (mWebView != null) {
            mWebView.destroy();
            mWebView = null;
        }
        super.onDestroy();
    }
}
