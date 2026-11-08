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
        setupNotify();
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
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        // 通知点进来带 notify_kind，交给 openFromNotification 处理；
        // 其它情况（从桌面图标进入）一律回到首页，避免停在上次的页面
        boolean fromNotify = intent != null && intent.hasExtra("notify_kind");
        if (!fromNotify) {
            int tab = intent == null ? -1 : intent.getIntExtra("tab", -1);
            switchTab(tab >= 0 && tab <= 3 ? tab : 0);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        openFromNotification();
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

    /** 按键手机：左右键切换底部标签页 */
    @Override
    protected boolean onKeypadKey(int keyCode) {
        if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT) {
            switchTab((mCurrentTab + TAB_IDS.length - 1) % TAB_IDS.length);
            return true;
        }
        if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) {
            switchTab((mCurrentTab + 1) % TAB_IDS.length);
            return true;
        }
        return false;
    }



    /** 推送通知：排程 + 安卓 13+ 首次启动申请权限 */
    private void setupNotify() {
        try {
            if (!com.android.oldbili.retro.util.Sp.getBoolean(
                    com.android.oldbili.retro.util.Sp.SET_NOTIFY_ENABLE, true)) {
                return;
            }
            com.android.oldbili.retro.notify.NotifyService.schedule(this);
            // 打开应用时立刻检查一次（等同于立刻收到提醒）
            startService(new android.content.Intent(this,
                    com.android.oldbili.retro.notify.NotifyService.class));
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                boolean asked = com.android.oldbili.retro.util.Sp.getBoolean("notify_asked", false);
                if (!asked) {
                    com.android.oldbili.retro.util.Sp.putBoolean("notify_asked", true);
                    requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1001);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /** 从通知栏点进来：跳到对应评论 / 私信 / 点赞内容 */
    private void openFromNotification() {
        // 也支持通过 Intent 直接指定（便于测试/外部唤起）
        try {
            android.content.Intent in = getIntent();
            if (in != null && in.hasExtra("notify_kind")) {
                com.android.oldbili.retro.util.Sp.putString("notify_kind",
                        in.getStringExtra("notify_kind"));
                com.android.oldbili.retro.util.Sp.putLong("notify_oid",
                        in.getLongExtra("notify_oid", 0));
                com.android.oldbili.retro.util.Sp.putLong("notify_root",
                        in.getLongExtra("notify_root", 0));
                com.android.oldbili.retro.util.Sp.putLong("notify_sub",
                        in.getLongExtra("notify_sub", 0));
                com.android.oldbili.retro.util.Sp.putLong("notify_uid",
                        in.getLongExtra("notify_uid", 0));
                com.android.oldbili.retro.util.Sp.putString("notify_uri",
                        in.getStringExtra("notify_uri"));
            }
        } catch (Throwable ignored) {
        }
        try {
            String kind = com.android.oldbili.retro.util.Sp.getString("notify_kind", "");
            if (kind == null || kind.length() == 0) {
                return;
            }
            com.android.oldbili.retro.util.Sp.putString("notify_kind", "");
            long oid = com.android.oldbili.retro.util.Sp.getLong("notify_oid", 0);
            long root = com.android.oldbili.retro.util.Sp.getLong("notify_root", 0);
            long sub = com.android.oldbili.retro.util.Sp.getLong("notify_sub", 0);
            long uid = com.android.oldbili.retro.util.Sp.getLong("notify_uid", 0);
            String uri = com.android.oldbili.retro.util.Sp.getString("notify_uri", "");

            if ("reply".equals(kind) && oid > 0) {
                android.content.Intent i = new android.content.Intent(this,
                        CommentListActivity.class);
                i.putExtra("oid", oid);
                i.putExtra("highlight_rpid", root);
                i.putExtra("highlight_sub_rpid", sub);
                startActivity(i);
                return;
            }
            if ("chat".equals(kind) && uid > 0) {
                android.content.Intent i = new android.content.Intent(this,
                        MessageActivity.class);
                i.putExtra("uid", uid);
                i.putExtra("name", com.android.oldbili.retro.util.Sp.getString("notify_name", ""));
                i.putExtra("face", com.android.oldbili.retro.util.Sp.getString("notify_face", ""));
                startActivity(i);
                return;
            }
            if ("uri".equals(kind) && uri != null && uri.length() > 0) {
                android.content.Intent i = new android.content.Intent(
                        android.content.Intent.ACTION_VIEW, android.net.Uri.parse(uri));
                i.setClass(this, UrlOpenActivity.class);
                startActivity(i);
            }
        } catch (Throwable ignored) {
        }
    }
}
