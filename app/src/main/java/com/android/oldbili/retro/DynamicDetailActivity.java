package com.android.oldbili.retro;

import android.os.Bundle;

/**
 * 动态详情：动态的评论区（评论列表 + 回复 + 发评论，图片发不出去就只发文字）。
 * 与视频评论区共用同一套页面逻辑（type=11）。
 */
public class DynamicDetailActivity extends CommentListActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 动态评论：oid=动态id，type=11
        getIntent().putExtra("oid", getIntent().getLongExtra("oid",
                getIntent().getLongExtra("dynamic", 0)));
        getIntent().putExtra("type", 11);
        super.onCreate(savedInstanceState);
        setTitle("动态评论");
    }
}
