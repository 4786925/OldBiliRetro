package com.android.oldbili.retro;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;

import com.android.oldbili.retro.api.BiliApi;
import com.android.oldbili.retro.api.FavoriteApi;
import com.android.oldbili.retro.model.FavoriteFolder;
import com.android.oldbili.retro.model.VideoCard;
import com.android.oldbili.retro.net.HttpUtil;
import com.android.oldbili.retro.util.Sp;
import com.android.oldbili.retro.widget.ListAdapters;
import com.android.oldbili.retro.widget.ListPage;

import java.util.ArrayList;
import java.util.List;

/**
 * 收藏夹列表：读取已登录账号的收藏夹，可选择收藏夹，也可新建。
 * 没有登录就提示"未登录无收藏夹"。
 */
public class FavoriteFolderActivity extends BaseActivity {

    private ListPage mListPage;
    private final List<FavoriteFolder> mFolders = new ArrayList<FavoriteFolder>();
    private long mUpMid = -1;   // 查看别人的收藏夹列表时传 up_mid；-1 表示自己的

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.favorite_folders);
        setContentView(R.layout.activity_favorite_folder);

        mUpMid = getIntent().getLongExtra("up_mid", -1);
        if (mUpMid == -1) {
            String midStr = getIntent().getStringExtra("up_mid");
            if (midStr != null && midStr.length() > 0) {
                try {
                    mUpMid = Long.parseLong(midStr.trim());
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (mUpMid == -1 && getIntent().hasExtra("up_mid")) {
            mUpMid = 0;
        }
        boolean self = mUpMid <= 0 || mUpMid == Sp.getLong(Sp.MID, -1);

        mListPage = new ListPage(findViewById(R.id.folder_list_root));
        mListPage.setEnableLoadMore(false);
        mListPage.setOnItemClick(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                int idx = position - mListPage.getListView().getHeaderViewsCount();
                if (idx >= 0 && idx < mFolders.size()) {
                    FavoriteFolder folder = mFolders.get(idx);
                    Intent intent = new Intent(FavoriteFolderActivity.this, FavoriteListActivity.class);
                    intent.putExtra("folder_id", folder.id);
                    intent.putExtra("title", folder.title);
                    long ownerMid = mUpMid > 0 ? mUpMid : Sp.getLong(Sp.MID, 0);
                    intent.putExtra("up_mid", ownerMid);
                    startActivity(intent);
                }
            }
        });
        mListPage.setOnItemLongClick(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                final int idx = position - mListPage.getListView().getHeaderViewsCount();
                if (idx >= 0 && idx < mFolders.size()) {
                    new AlertDialog.Builder(FavoriteFolderActivity.this)
                            .setTitle("删除收藏夹")
                            .setMessage("确定删除收藏夹「" + mFolders.get(idx).title + "」吗？")
                            .setPositiveButton(R.string.download_delete, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    doDeleteFolder(mFolders.get(idx).id);
                                }
                            })
                            .setNegativeButton(R.string.cancel, null)
                            .show();
                }
                return true;
            }
        });

        // 顶部三个选择：收藏夹（当前页）/ 追番 / 追剧
        final long tabVmid = mUpMid > 0 ? mUpMid : Sp.getLong(Sp.MID, 0);
        findViewById(R.id.tab_bangumi).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(FavoriteFolderActivity.this, BangumiListActivity.class);
                intent.putExtra("vmid", tabVmid);
                intent.putExtra("type", BangumiListActivity.TYPE_BANGUMI);
                startActivity(intent);
            }
        });
        findViewById(R.id.tab_tv).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(FavoriteFolderActivity.this, BangumiListActivity.class);
                intent.putExtra("vmid", tabVmid);
                intent.putExtra("type", BangumiListActivity.TYPE_TV);
                startActivity(intent);
            }
        });

        findViewById(R.id.btn_new_folder).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mUpMid > 0 && mUpMid != Sp.getLong(Sp.MID, -1)) {
                    toast("只能管理自己的收藏夹");
                    return;
                }
                showNewFolderDialog();
            }
        });
        // 看别人的夹时隐藏新建按钮
        if (mUpMid > 0 && mUpMid != Sp.getLong(Sp.MID, -1)) {
            findViewById(R.id.btn_new_folder).setVisibility(View.GONE);
        }

        refresh();
    }

    private void refresh() {
        final long target = mUpMid > 0 ? mUpMid : Sp.getLong(Sp.MID, 0);
        if (target <= 0) {
            if (!HttpUtil.isLoggedIn()) {
                mListPage.showEmpty("登录后才能查看自己的收藏夹");
                return;
            }
            // 已登录但本地 MID 缺失：从 nav 接口补回来再刷新
            mListPage.showLoading();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        org.json.JSONObject data = BiliApi.check(HttpUtil.getJson(
                                "https://api.bilibili.com/x/web-interface/nav"));
                        final long mid = data.optLong("mid", 0);
                        if (mid > 0) {
                            Sp.putLong(Sp.MID, mid);
                        }
                        runOnUi(new Runnable() {
                            @Override
                            public void run() {
                                if (mid > 0) {
                                    refresh();
                                } else {
                                    mListPage.showError("读取登录信息失败，请重新登录", new Runnable() {
                                        @Override
                                        public void run() {
                                            refresh();
                                        }
                                    });
                                }
                            }
                        });
                    } catch (final Exception e) {
                        runOnUi(new Runnable() {
                            @Override
                            public void run() {
                                mListPage.showError("登录可能已过期：" + e.getMessage(), new Runnable() {
                                    @Override
                                    public void run() {
                                        refresh();
                                    }
                                });
                            }
                        });
                    }
                }
            }).start();
            return;
        }
        mListPage.showLoading();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<FavoriteFolder> folders = FavoriteApi.getFolders(target);
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            mFolders.clear();
                            mFolders.addAll(folders);
                            mListPage.setAdapter(new ListAdapters.FolderList(FavoriteFolderActivity.this, mFolders));
                            mListPage.finishLoadMore(false);
                            if (mFolders.size() == 0) {
                                mListPage.showEmpty(mUpMid > 0 && mUpMid != Sp.getLong(Sp.MID, -1)
                                        ? "这个 UP 主没有公开收藏夹" : "还没有收藏夹，点下方按钮新建");
                            } else {
                                mListPage.showContent();
                            }
                            // 列表接口不带封面：逐个夹取第一张视频封面补齐
                            fetchCovers();
                        }
                    });
                } catch (final Exception e) {
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            mListPage.showError(e.getMessage(), new Runnable() {
                                @Override
                                public void run() {
                                    refresh();
                                }
                            });
                        }
                    });
                }
            }
        }).start();
    }

    /** 后台逐个夹取第一张视频封面（接口一次只给一个夹的封面数据） */
    private void fetchCovers() {
        final long mid = mUpMid > 0 ? mUpMid : Sp.getLong(Sp.MID, 0);
        new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < mFolders.size(); i++) {
                    final FavoriteFolder folder = mFolders.get(i);
                    if (folder.cover != null && folder.cover.length() > 0) {
                        continue;
                    }
                    try {
                        Object[] result = FavoriteApi.getFolderVideos(mid, folder.id, 1);
                        final List<VideoCard> videos = (List<VideoCard>) result[0];
                        if (videos != null && videos.size() > 0 && videos.get(0).cover != null) {
                            final String cover = videos.get(0).cover;
                            runOnUi(new Runnable() {
                                @Override
                                public void run() {
                                    folder.cover = cover;
                                    mListPage.setAdapter(new ListAdapters.FolderList(
                                            FavoriteFolderActivity.this, mFolders));
                                }
                            });
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }).start();
    }

    private void showNewFolderDialog() {
        final EditText input = new EditText(this);
        input.setHint(R.string.favorite_new_hint);
        new AlertDialog.Builder(this)
                .setTitle(R.string.favorite_new)
                .setView(input)
                .setPositiveButton(R.string.confirm, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        final String title = input.getText().toString().trim();
                        if (title.length() == 0) {
                            return;
                        }
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    final long fid = FavoriteApi.newFolder(title);
                                    runOnUi(new Runnable() {
                                        @Override
                                        public void run() {
                                            if (fid > 0) {
                                                toast(R.string.favorite_new_ok);
                                                refresh();
                                            } else {
                                                toast(R.string.favorite_add_failed);
                                            }
                                        }
                                    });
                                } catch (final Exception e) {
                                    runOnUi(new Runnable() {
                                        @Override
                                        public void run() {
                                            toast(R.string.favorite_add_failed);
                                        }
                                    });
                                }
                            }
                        }).start();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void doDeleteFolder(final long folderId) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final int code = FavoriteApi.delFolder(folderId);
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            toast(code == 0 ? "已删除" : "删除失败");
                            refresh();
                        }
                    });
                } catch (final Exception e) {
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            toast("删除失败");
                        }
                    });
                }
            }
        }).start();
    }
}
