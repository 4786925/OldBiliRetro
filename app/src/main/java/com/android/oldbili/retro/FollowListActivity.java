package com.android.oldbili.retro;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;

import com.android.oldbili.retro.api.UserApi;
import com.android.oldbili.retro.model.UserInfo;
import com.android.oldbili.retro.widget.ListAdapters;
import com.android.oldbili.retro.widget.ListPageActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * 关注的人 / 粉丝列表（共用列表页，按 extra 区分）。
 */
public class FollowListActivity extends ListPageActivity {

    private long mMid;
    private boolean mFollowers;
    private final List<UserInfo> mUsers = new ArrayList<UserInfo>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mMid = getIntent().getLongExtra("mid", 0);
        mFollowers = !getIntent().getBooleanExtra("following", true);
        setTitle(mFollowers ? R.string.fans_list : R.string.followed_list);

        mPage.setOnItemClick(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                int idx = position - mPage.getListView().getHeaderViewsCount();
                if (idx >= 0 && idx < mUsers.size()) {
                    UserInfo user = mUsers.get(idx);
                    Intent intent = new Intent(FollowListActivity.this, UserActivity.class);
                    intent.putExtra("mid", user.mid);
                    intent.putExtra("name", user.name);
                    startActivity(intent);
                }
            }
        });
        // 长按批量管理：关注列表=批量取消关注，粉丝列表=批量移除粉丝
        mPage.setOnItemLongClick(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                int idx = position - mPage.getListView().getHeaderViewsCount();
                if (idx >= 0 && idx < mUsers.size()) {
                    showBatchDialog();
                }
                return true;
            }
        });
        startLoad();
    }

    private void showBatchDialog() {
        com.android.oldbili.retro.widget.BatchSelectDialog.showUsers(this,
                mFollowers ? "批量移除粉丝" : "批量取消关注", mUsers,
                new com.android.oldbili.retro.widget.BatchSelectDialog.OnConfirmListener() {
                    @Override
                    public void onConfirm(final List<Long> mids) {
                        if (mids.size() == 0) {
                            toast("没有选中任何人");
                            return;
                        }
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                int ok = 0;
                                for (int i = 0; i < mids.size(); i++) {
                                    try {
                                        int code = mFollowers
                                                ? UserApi.removeFollower(mids.get(i))
                                                : UserApi.follow(mids.get(i), false);
                                        if (code == 0) {
                                            ok++;
                                        }
                                    } catch (Exception ignored) {
                                    }
                                }
                                final int okCount = ok;
                                runOnUi(new Runnable() {
                                    @Override
                                    public void run() {
                                        toast(mFollowers ? "已移除 " + okCount + " 个粉丝"
                                                : "已取消关注 " + okCount + " 人");
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
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<UserInfo> users = UserApi.getFollowList(mMid, page, mFollowers);
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            if (page == 1) {
                                mUsers.clear();
                            }
                            mUsers.addAll(users);
                            boolean hasMore = users.size() >= 20;
                            // 不再显示右侧"已关注"蓝字（用户要求去掉）
                            finishLoad(new ListAdapters.UserList(FollowListActivity.this, mUsers, false),
                                    mUsers.size(), hasMore);
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
