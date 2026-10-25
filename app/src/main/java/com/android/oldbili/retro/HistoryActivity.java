package com.android.oldbili.retro;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;

import com.android.oldbili.retro.api.HistoryApi;
import com.android.oldbili.retro.model.HistoryItem;
import com.android.oldbili.retro.net.HttpUtil;
import com.android.oldbili.retro.widget.ListAdapters;
import com.android.oldbili.retro.widget.ListPageActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * 播放历史（共用列表页）。
 */
public class HistoryActivity extends ListPageActivity {

    private long mNextMax;
    private long mNextViewAt;
    private final List<HistoryItem> mItems = new ArrayList<HistoryItem>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.history_title);
        mPage.setOnItemClick(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                int idx = position - mPage.getListView().getHeaderViewsCount();
                if (idx >= 0 && idx < mItems.size()) {
                    HistoryItem item = mItems.get(idx);
                    Intent intent = new Intent(HistoryActivity.this, VideoDetailActivity.class);
                    if (item.bvid != null && item.bvid.length() > 0) {
                        intent.putExtra("bvid", item.bvid);
                    } else {
                        intent.putExtra("aid", item.aid);
                    }
                    startActivity(intent);
                }
            }
        });
        // 长按批量删除历史
        mPage.setOnItemLongClick(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                int idx = position - mPage.getListView().getHeaderViewsCount();
                if (idx >= 0 && idx < mItems.size()) {
                    showBatchDialog();
                }
                return true;
            }
        });
        startLoad();
    }

    private void showBatchDialog() {
        com.android.oldbili.retro.widget.BatchSelectDialog.showHistory(this, "批量删除历史", mItems,
                new com.android.oldbili.retro.widget.BatchSelectDialog.OnConfirmStringsListener() {
                    @Override
                    public void onConfirm(final List<String> kids) {
                        if (kids.size() == 0) {
                            toast("没有选中任何条目");
                            return;
                        }
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                int ok = 0;
                                for (int i = 0; i < kids.size(); i++) {
                                    try {
                                        if (HistoryApi.delHistory(kids.get(i)) == 0) {
                                            ok++;
                                        }
                                    } catch (Exception ignored) {
                                    }
                                }
                                final int okCount = ok;
                                runOnUi(new Runnable() {
                                    @Override
                                    public void run() {
                                        toast("已删除 " + okCount + " 条历史");
                                        refresh();
                                    }
                                });
                            }
                        }).start();
                    }
                });
    }

    @Override
    protected void loadData(final int page) {
        if (!HttpUtil.isLoggedIn()) {
            mPage.showEmpty(getString(R.string.need_login));
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final long max = page == 1 ? 0 : mNextMax;
                    final long viewAt = page == 1 ? 0 : mNextViewAt;
                    Object[] result = HistoryApi.getHistory(max, viewAt);
                    final List<HistoryItem> items = (List<HistoryItem>) result[0];
                    final long nextMax = (Long) result[1];
                    final long nextViewAt = (Long) result[2];
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            if (page == 1) {
                                mItems.clear();
                            }
                            mNextMax = nextMax;
                            mNextViewAt = nextViewAt;
                            mItems.addAll(items);
                            boolean hasMore = items.size() >= 20;
                            finishLoad(new ListAdapters.HistoryList(HistoryActivity.this, mItems),
                                    mItems.size(), hasMore);
                        }
                    });
                } catch (final Exception e) {
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            failLoad(e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }
}
