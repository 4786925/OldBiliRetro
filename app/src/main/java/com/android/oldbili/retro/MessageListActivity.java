package com.android.oldbili.retro;

import android.os.Bundle;

/**
 * 私信会话列表（独立页，从"我的"进入）。逻辑与主界面消息页签完全一致（复用 MessagePage）。
 */
public class MessageListActivity extends BaseActivity {

    private MessagePage mMessagePage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.message_list);
        mMessagePage = new MessagePage(this);
        setContentView(mMessagePage.getView());
        mMessagePage.onShow();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mMessagePage != null) {
            mMessagePage.onShow();
        }
    }
}
