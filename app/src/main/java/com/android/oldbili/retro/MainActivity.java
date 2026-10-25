package com.android.oldbili.retro;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * 主界面：底部 首页/动态/消息/我的 四个页签。
 * 各页签内容由 HomePage/DynamicPage/MessagePage/MinePage 负责，
 * 搜索从首页顶部搜索框进入。
 */
public class MainActivity extends BaseActivity {

    private FrameLayout mContent;
    private HomePage mHomePage;
    private DynamicPage mDynamicPage;
    private MessagePage mMessagePage;
    private MinePage mMinePage;
    private int mCurrentTab = 0;

    private final int[] TAB_IDS = {R.id.tab_home, R.id.tab_dynamic, R.id.tab_message, R.id.tab_mine};
    private final int[] TAB_ICONS = {R.id.tab_home_icon, R.id.tab_dynamic_icon,
            R.id.tab_message_icon, R.id.tab_mine_icon};
    private final int[] TAB_TEXTS = {R.id.tab_home_text, R.id.tab_dynamic_text,
            R.id.tab_message_text, R.id.tab_mine_text};
    private final int[] TAB_TITLES = {R.string.tab_home, R.string.tab_dynamic,
            R.string.tab_message, R.string.tab_mine};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setShowBack(false);
        setContentView(R.layout.activity_main);

        mContent = (FrameLayout) findViewById(R.id.content);
        mHomePage = new HomePage(this);
        mDynamicPage = new DynamicPage(this);
        mMessagePage = new MessagePage(this);
        mMinePage = new MinePage(this);

        for (int i = 0; i < TAB_IDS.length; i++) {
            final int index = i;
            findViewById(TAB_IDS[i]).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    long now = System.currentTimeMillis();
                    // 双击同一个页签 = 强制刷新该页（动态/消息/首页都支持）
                    if (index == mLastTabClicked && now - mLastTabClickTime < 400) {
                        mLastTabClickTime = 0;
                        mLastTabClicked = -1;
                        switchTab(index);
                        forceRefresh(index);
                        return;
                    }
                    mLastTabClickTime = now;
                    mLastTabClicked = index;
                    switchTab(index);
                }
            });
        }
        // 支持外部指定起始页签（调试与深度链接用）：am start ... --ei tab 3
        int startTab = getIntent().getIntExtra("tab", -1);
        if (startTab < 0 || startTab > 3) {
            startTab = 0;
        }
        switchTab(startTab);
    }

    private long mLastTabClickTime;
    private int mLastTabClicked = -1;

    private void switchTab(int index) {
        mCurrentTab = index;
        setTitle(TAB_TITLES[index]);
        mContent.removeAllViews();
        switch (index) {
            case 0:
                mContent.addView(mHomePage.getView());
                mHomePage.onShow();
                break;
            case 1:
                mContent.addView(mDynamicPage.getView());
                mDynamicPage.onShow();
                break;
            case 2:
                mContent.addView(mMessagePage.getView());
                mMessagePage.onShow();
                break;
            default:
                mContent.addView(mMinePage.getView());
                mMinePage.onShow();
                break;
        }
        // 页签选中态：2013 风格——选中图标实心蓝、未选中实心灰（colorFilter 全版本可用）
        for (int i = 0; i < TAB_IDS.length; i++) {
            TextView text = (TextView) findViewById(TAB_TEXTS[i]);
            ImageView icon = (ImageView) findViewById(TAB_ICONS[i]);
            if (i == index) {
                text.setTextColor(0xFF33B5E5);
                icon.setColorFilter(0xFF33B5E5, android.graphics.PorterDuff.Mode.SRC_IN);
            } else {
                text.setTextColor(0xFF8A8A8A);
                icon.setColorFilter(0xFF9E9E9E, android.graphics.PorterDuff.Mode.SRC_IN);
            }
        }
    }

    /** 双击页签强制刷新 */
    private void forceRefresh(int index) {
        switch (index) {
            case 0:
                mHomePage.forceRefresh();
                break;
            case 1:
                mDynamicPage.forceRefresh();
                break;
            case 2:
                mMessagePage.forceRefresh();
                break;
            default:
                mMinePage.onShow();
                break;
        }
    }

    @Override
    public void onBackPressed() {
        if (mCurrentTab != 0) {
            switchTab(0);
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 登录状态可能变了，回到主界面刷新当前页
        switch (mCurrentTab) {
            case 0:
                mHomePage.onShow();
                break;
            case 1:
                mDynamicPage.onShow();
                break;
            case 2:
                mMessagePage.onShow();
                break;
            default:
                mMinePage.onShow();
                break;
        }
    }
}
