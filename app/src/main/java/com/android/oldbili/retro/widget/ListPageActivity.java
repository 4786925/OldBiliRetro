package com.android.oldbili.retro.widget;

import android.os.Bundle;
import android.view.View;
import android.widget.ListAdapter;

import com.android.oldbili.retro.BaseActivity;
import com.android.oldbili.retro.R;

/**
 * 列表页基类：只有一张列表的页面都从这里继承。
 * 子类只需实现 loadData(page) 并在异步完成后调用 finishLoad。
 */
public abstract class ListPageActivity extends BaseActivity {

    protected ListPage mPage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list_page);
        View root = ((android.view.ViewGroup) findViewById(android.R.id.content)).getChildAt(0);
        mPage = new ListPage(root);
        // 自动接翻页：滑到底调用 loadData(下一页)。
        // 子类若自己 setOnLoadMore 会覆盖这里，互不冲突。
        mPage.setOnLoadMore(new ListPage.OnLoadMoreListener() {
            @Override
            public void onLoadMore(int page) {
                loadData(page);
            }
        });
    }

    /** 子类实现：按页加载数据，完成后调 finishLoad */
    protected abstract void loadData(final int page);

    /** 第一页开始加载（转圈） */
    protected void startLoad() {
        mPage.showLoading();
        loadData(1);
    }

    /** 刷新（回到第一页） */
    protected void refresh() {
        startLoad();
    }

    /** 数据到位：空列表给提示，否则显示并处理翻页 */
    protected void finishLoad(ListAdapter adapter, int count, boolean hasMore) {
        mPage.setAdapter(adapter);
        mPage.finishLoadMore(hasMore);
        if (count <= 0) {
            mPage.showEmpty(getString(R.string.empty));
        } else {
            mPage.showContent();
        }
    }

    /** 加载失败 */
    protected void failLoad(String message) {
        mPage.showError(message == null ? getString(R.string.load_failed) : message,
                new Runnable() {
                    @Override
                    public void run() {
                        startLoad();
                    }
                });
    }
}
