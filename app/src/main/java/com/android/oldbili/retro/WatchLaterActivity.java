package com.android.oldbili.retro;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;

import com.android.oldbili.retro.api.HistoryApi;
import com.android.oldbili.retro.model.VideoCard;
import com.android.oldbili.retro.net.HttpUtil;
import com.android.oldbili.retro.widget.ListAdapters;
import com.android.oldbili.retro.widget.ListPageActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * 稍后再看（共用列表页）。
 */
public class WatchLaterActivity extends ListPageActivity {

    private final List<VideoCard> mVideos = new ArrayList<VideoCard>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.watchlater_title);
        mPage.setEnableLoadMore(false);
        mPage.setOnItemClick(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                int idx = position - mPage.getListView().getHeaderViewsCount();
                if (idx >= 0 && idx < mVideos.size()) {
                    VideoCard card = mVideos.get(idx);
                    if (card == null || !card.isPlayable()) {
                        toast("该视频已失效");
                        return;
                    }
                    Intent intent = new Intent(WatchLaterActivity.this, VideoDetailActivity.class);
                    if (card.bvid != null && card.bvid.length() > 0) {
                        intent.putExtra("bvid", card.bvid);
                    } else {
                        intent.putExtra("aid", card.aid);
                    }
                    startActivity(intent);
                }
            }
        });
        mPage.setOnItemLongClick(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                final int idx = position - mPage.getListView().getHeaderViewsCount();
                if (idx >= 0 && idx < mVideos.size()) {
                    new AlertDialog.Builder(WatchLaterActivity.this)
                            .setItems(new String[]{"移出这条", "批量管理"}, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    if (which == 0) {
                                        doRemove(mVideos.get(idx).aid);
                                    } else {
                                        showBatchDialog();
                                    }
                                }
                            })
                            .setNegativeButton(R.string.cancel, null)
                            .show();
                }
                return true;
            }
        });
        startLoad();
    }

    /** 批量管理：多选后一次移出 */
    private void showBatchDialog() {
        com.android.oldbili.retro.widget.BatchSelectDialog.show(this, "批量移出稍后再看", mVideos,
                new com.android.oldbili.retro.widget.BatchSelectDialog.OnConfirmListener() {
                    @Override
                    public void onConfirm(final List<Long> aids) {
                        if (aids.size() == 0) {
                            toast("没有选中任何视频");
                            return;
                        }
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                int ok = 0;
                                for (int i = 0; i < aids.size(); i++) {
                                    try {
                                        if (HistoryApi.delToView(aids.get(i)) == 0) {
                                            ok++;
                                        }
                                    } catch (Exception ignored) {
                                    }
                                }
                                final int okCount = ok;
                                runOnUi(new Runnable() {
                                    @Override
                                    public void run() {
                                        toast("已移出 " + okCount + " 条");
                                        refresh();
                                    }
                                });
                            }
                        }).start();
                    }
                });
    }

    private void doRemove(final long aid) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final int code = HistoryApi.delToView(aid);
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            toast(code == 0 ? "已移出" : "操作失败");
                            refresh();
                        }
                    });
                } catch (final Exception e) {
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            toast("操作失败");
                        }
                    });
                }
            }
        }).start();
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
                    final List<VideoCard> videos = HistoryApi.getToView();
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            mVideos.clear();
                            mVideos.addAll(videos);
                            finishLoad(new ListAdapters.VideoList(WatchLaterActivity.this, mVideos),
                                    mVideos.size(), false);
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
