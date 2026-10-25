package com.android.oldbili.retro;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;

import com.android.oldbili.retro.api.FavoriteApi;
import com.android.oldbili.retro.model.VideoCard;
import com.android.oldbili.retro.net.HttpUtil;
import com.android.oldbili.retro.util.Sp;
import com.android.oldbili.retro.widget.ListAdapters;
import com.android.oldbili.retro.widget.ListPageActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * 收藏夹内容（共用列表页）。
 */
public class FavoriteListActivity extends ListPageActivity {

    private long mFolderId;
    private long mUpMid;
    private long mTotal;
    private final List<VideoCard> mVideos = new ArrayList<VideoCard>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mFolderId = getIntent().getLongExtra("folder_id", 0);
        mUpMid = getIntent().getLongExtra("up_mid", Sp.getLong(Sp.MID, 0));
        if (mUpMid <= 0) {
            mUpMid = Sp.getLong(Sp.MID, 0);
        }
        String title = getIntent().getStringExtra("title");
        if (title != null && title.length() > 0) {
            setTitle(title);
        } else {
            setTitle(R.string.favorite_folders);
        }

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
                    Intent intent = new Intent(FavoriteListActivity.this, VideoDetailActivity.class);
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
                    new AlertDialog.Builder(FavoriteListActivity.this)
                            .setItems(new String[]{"取消收藏这条", "批量管理"}, new DialogInterface.OnClickListener() {
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

    /** 批量管理：多选后一次移除 */
    private void showBatchDialog() {
        com.android.oldbili.retro.widget.BatchSelectDialog.show(this, "批量取消收藏", mVideos,
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
                                        if (FavoriteApi.setFav(aids.get(i), mFolderId, false) == 0) {
                                            ok++;
                                        }
                                    } catch (Exception ignored) {
                                    }
                                }
                                final int okCount = ok;
                                runOnUi(new Runnable() {
                                    @Override
                                    public void run() {
                                        toast("已移除 " + okCount + " 条");
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
                    final int code = FavoriteApi.setFav(aid, mFolderId, false);
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            if (code == 0) {
                                toast(R.string.favorite_del_ok);
                            } else {
                                toast("操作失败");
                            }
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
            mPage.showEmpty(getString(R.string.favorite_none));
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Object[] result = FavoriteApi.getFolderVideos(mUpMid, mFolderId, page);
                    final List<VideoCard> videos = (List<VideoCard>) result[0];
                    final long total = (Long) result[1];
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            mTotal = total;
                            if (page == 1) {
                                mVideos.clear();
                            }
                            // 翻页保持滚动位置，不跳回顶部
                            int first = 0;
                            int top = 0;
                            try {
                                android.widget.ListView lv = mPage.getListView();
                                if (lv.getChildCount() > 0) {
                                    first = lv.getFirstVisiblePosition();
                                    View child = lv.getChildAt(0);
                                    top = child == null ? 0 : child.getTop();
                                }
                            } catch (Exception ignored) {
                            }
                            mVideos.addAll(videos);
                            boolean hasMore = mVideos.size() < total;
                            finishLoad(new ListAdapters.VideoList(FavoriteListActivity.this, mVideos),
                                    mVideos.size(), hasMore);
                            if (first > 0) {
                                try {
                                    mPage.getListView().setSelectionFromTop(first, top);
                                } catch (Exception ignored) {
                                }
                            }
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
