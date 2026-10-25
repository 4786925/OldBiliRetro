package com.android.oldbili.retro;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.oldbili.retro.api.DynamicApi;
import com.android.oldbili.retro.api.UserApi;
import com.android.oldbili.retro.model.DynamicItem;
import com.android.oldbili.retro.model.UserInfo;
import com.android.oldbili.retro.model.VideoCard;
import com.android.oldbili.retro.net.HttpUtil;
import com.android.oldbili.retro.net.ImageLoader;
import com.android.oldbili.retro.util.NumberUtil;
import com.android.oldbili.retro.util.Sp;
import com.android.oldbili.retro.widget.ListAdapters;
import com.android.oldbili.retro.widget.ListPage;

import java.util.ArrayList;
import java.util.List;

/**
 * 个人空间（自己的或任意 UP 主）：视频/动态/简介。
 * 关注按钮：未关注→"关注"，已关注→"已关注"，互粉→"已互粉"；另有私信按钮。
 */
public class UserActivity extends BaseActivity {

    private static final int TAB_VIDEOS = 0;
    private static final int TAB_DYNAMIC = 1;
    private static final int TAB_INTRO = 2;

    private long mMid;
    private String mName = "";
    private UserInfo mUser;
    private int mRelation;
    private int mBeRelation;
    private int mTab = TAB_VIDEOS;

    private ListPage mListPage;
    private TextView mTabVideos;
    private TextView mTabDynamic;
    private TextView mTabIntro;
    private Button mBtnFollow;
    private Button mBtnMessage;
    private View mIntroScroll;

    private int mVideosPage = 1;
    private boolean mVideosHasMore = true;
    private String mDynamicOffset = "";
    private boolean mDynamicHasMore = true;
    private final List<VideoCard> mVideos = new ArrayList<VideoCard>();
    private final List<DynamicItem> mDynamics = new ArrayList<DynamicItem>();
    private boolean mVideoTabLoaded;
    private boolean mDynamicTabLoaded;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user);

        // 支持外部以字符串方式传 mid（老设备 am 命令没有 long 参数，也方便深度链接）
        mMid = getIntent().getLongExtra("mid", 0);
        if (mMid == 0) {
            String midStr = getIntent().getStringExtra("mid");
            if (midStr != null && midStr.length() > 0) {
                try {
                    mMid = Long.parseLong(midStr.trim());
                } catch (NumberFormatException ignored) {
                }
            }
        }
        mName = getIntent().getStringExtra("name");
        setTitle(mName != null && mName.length() > 0 ? mName : "个人空间");
        int startTab = getIntent().getIntExtra("tab", -1);
        if (startTab == TAB_VIDEOS || startTab == TAB_DYNAMIC || startTab == TAB_INTRO) {
            mTab = startTab;
        }

        mListPage = new ListPage(findViewById(R.id.user_list));
        mTabVideos = (TextView) findViewById(R.id.tab_videos);
        mTabDynamic = (TextView) findViewById(R.id.tab_dynamic);
        mTabIntro = (TextView) findViewById(R.id.tab_intro);
        mBtnFollow = (Button) findViewById(R.id.btn_follow);
        mBtnMessage = (Button) findViewById(R.id.btn_message);
        mIntroScroll = findViewById(R.id.intro_scroll);

        // 看自己时不给关注/私信/拉黑按钮
        if (mMid == Sp.getLong(Sp.MID, -1)) {
            mBtnFollow.setVisibility(View.GONE);
            mBtnMessage.setVisibility(View.GONE);
            findViewById(R.id.btn_blacklist).setVisibility(View.GONE);
        }

        mBtnFollow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleFollow();
            }
        });
        mBtnMessage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(UserActivity.this, MessageActivity.class);
                intent.putExtra("uid", mMid);
                intent.putExtra("name", mUser != null ? mUser.name : mName);
                intent.putExtra("face", mUser != null ? mUser.face : "");
                startActivity(intent);
            }
        });
        // 黑名单：拉黑/取消拉黑
        final Button btnBlack = (Button) findViewById(R.id.btn_blacklist);
        btnBlack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showBlacklistDialog();
            }
        });

        mTabVideos.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchTab(TAB_VIDEOS);
            }
        });
        mTabDynamic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchTab(TAB_DYNAMIC);
            }
        });
        mTabIntro.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchTab(TAB_INTRO);
            }
        });

        // 空间入口：点赞视频 / 投币视频 / 收藏夹列表
        findViewById(R.id.entry_like).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSpaceList(SpaceListActivity.KIND_LIKE);
            }
        });
        findViewById(R.id.entry_coin).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSpaceList(SpaceListActivity.KIND_COIN);
            }
        });
        findViewById(R.id.entry_favs).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(UserActivity.this, FavoriteFolderActivity.class);
                intent.putExtra("up_mid", mMid);
                startActivity(intent);
            }
        });
        // 看 TA 的追番 / 追剧
        findViewById(R.id.entry_bangumi).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openBangumiList(BangumiListActivity.TYPE_BANGUMI);
            }
        });
        findViewById(R.id.entry_tv).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openBangumiList(BangumiListActivity.TYPE_TV);
            }
        });

        mListPage.setOnLoadMore(new ListPage.OnLoadMoreListener() {
            @Override
            public void onLoadMore(int page) {
                loadMore();
            }
        });
        mListPage.setOnItemClick(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                int idx = position - mListPage.getListView().getHeaderViewsCount();
                if (mTab == TAB_VIDEOS) {
                    if (idx >= 0 && idx < mVideos.size()) {
                        VideoCard card = mVideos.get(idx);
                    if (card == null || !card.isPlayable()) {
                        toast("该视频已失效");
                        return;
                    }
                        Intent intent = new Intent(UserActivity.this, VideoDetailActivity.class);
                        if (card.bvid != null && card.bvid.length() > 0) {
                            intent.putExtra("bvid", card.bvid);
                        } else {
                            intent.putExtra("aid", card.aid);
                        }
                        startActivity(intent);
                    }
                } else if (mTab == TAB_DYNAMIC) {
                    if (idx >= 0 && idx < mDynamics.size()) {
                        DynamicItem item = mDynamics.get(idx);
                        if (item.type == DynamicItem.TYPE_AV && (item.aid > 0 || item.bvid.length() > 0)) {
                            Intent intent = new Intent(UserActivity.this, VideoDetailActivity.class);
                            if (item.bvid.length() > 0) {
                                intent.putExtra("bvid", item.bvid);
                            } else {
                                intent.putExtra("aid", item.aid);
                            }
                            startActivity(intent);
                        } else {
                            Intent intent = new Intent(UserActivity.this, DynamicDetailActivity.class);
                            intent.putExtra("oid", Long.parseLong(item.dynamicId.length() > 0 ? item.dynamicId : "0"));
                            startActivity(intent);
                        }
                    }
                }
            }
        });

        loadUserInfo();
        switchTab(mTab);
    }

    private void loadUserInfo() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final UserInfo user = UserApi.getUserCard(mMid);
                    final int[] pair = HttpUtil.isLoggedIn() ? UserApi.getRelationPair(mMid) : new int[]{0, 0};
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            mUser = user;
                            mRelation = pair[0];
                            mBeRelation = pair[1];
                            fillHeader();
                        }
                    });
                } catch (final Exception e) {
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            toast("用户信息加载失败");
                        }
                    });
                }
            }
        }).start();
    }

    private void fillHeader() {
        ((TextView) findViewById(R.id.name)).setText(mUser.name);
        long uid = mUser.mid > 0 ? mUser.mid : mMid;
        ((TextView) findViewById(R.id.uid)).setText("UID: " + uid);
        ((TextView) findViewById(R.id.stats)).setText(
                "关注 " + NumberUtil.toWan(mUser.follows) + " · 粉丝 " + NumberUtil.toWan(mUser.fans)
                        + (mUser.likes > 0 ? " · 获赞 " + NumberUtil.toWan(mUser.likes) : "")
                        + (mUser.level > 0 ? " · Lv" + mUser.level : ""));
        if (mUser.sign != null && mUser.sign.length() > 0) {
            ((TextView) findViewById(R.id.sign)).setText(mUser.sign);
        }
        final ImageView avatar = (ImageView) findViewById(R.id.avatar);
        ImageLoader.get().load(avatar, ImageLoader.avatarUrl(mUser.face, 128));
        // 点头像看大图；长按弹保存/取消
        avatar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAvatarFullscreen();
            }
        });
        avatar.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                askSaveAvatar();
                return true;
            }
        });
        updateFollowButton();
    }

    private String avatarFullUrl() {
        return mUser != null && mUser.face != null && mUser.face.length() > 0
                ? mUser.face : "";
    }

    /** 全屏看头像，长按保存 */
    private void showAvatarFullscreen() {
        final String url = avatarFullUrl();
        if (url.length() == 0) {
            return;
        }
        final ImageView big = new ImageView(this);
        big.setBackgroundColor(0xFF000000);
        big.setScaleType(ImageView.ScaleType.FIT_CENTER);
        ImageLoader.get().load(big, url);
        final android.app.Dialog dialog = new android.app.Dialog(this,
                android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setContentView(big);
        big.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                askSaveAvatar();
                return true;
            }
        });
        big.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        dialog.show();
    }

    /** 保存头像：弹"保存/取消" */
    private void askSaveAvatar() {
        final String url = avatarFullUrl();
        if (url.length() == 0) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("保存头像")
                .setMessage("保存到 Pictures/OldBiliRetro ？")
                .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                final boolean ok = com.android.oldbili.retro.util.SaveImageUtil.save(
                                        UserActivity.this, url);
                                runOnUi(new Runnable() {
                                    @Override
                                    public void run() {
                                        toast(ok ? "已保存到 Pictures/OldBiliRetro" : "保存失败，请重试");
                                    }
                                });
                            }
                        }).start();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** 黑名单操作 */
    private void showBlacklistDialog() {
        if (!isLoggedIn()) {
            toast(R.string.need_login);
            startActivity(new Intent(this, LoginActivity.class));
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("黑名单")
                .setItems(new String[]{"加入黑名单", "移出黑名单"}, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        final boolean add = which == 0;
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    final int code = UserApi.setBlacklist(mMid, add);
                                    runOnUi(new Runnable() {
                                        @Override
                                        public void run() {
                                            toast(code == 0 ? (add ? "已加入黑名单" : "已移出黑名单")
                                                    : "操作失败(" + code + ")");
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
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void updateFollowButton() {
        boolean followed = UserApi.isFollowed(mRelation);
        boolean mutual = followed && mBeRelation > 0;
        mBtnFollow.setText(mutual ? R.string.follow_each
                : followed ? R.string.followed_btn : R.string.follow);
    }

    private void toggleFollow() {
        if (!isLoggedIn()) {
            toast(R.string.need_login);
            startActivity(new Intent(this, LoginActivity.class));
            return;
        }
        final boolean toFollow = !UserApi.isFollowed(mRelation);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final int code = UserApi.follow(mMid, toFollow);
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            if (code == 0) {
                                mRelation = toFollow ? 1 : 0;
                                mBeRelation = 0;
                                updateFollowButton();
                                toast(toFollow ? R.string.follow_ok : R.string.unfollow_ok);
                            } else if (code == 22014) {
                                // 已经关注过了（接口语义新旧混用导致的重复关注）
                                mRelation = 1;
                                updateFollowButton();
                                toast("已经关注过了");
                            } else {
                                toast("操作失败(" + code + ")");
                            }
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

    private void openSpaceList(int kind) {
        Intent intent = new Intent(UserActivity.this, SpaceListActivity.class);
        intent.putExtra("kind", kind);
        intent.putExtra("vmid", mMid);
        intent.putExtra("name", mUser != null ? mUser.name : mName);
        startActivity(intent);
    }

    private void openBangumiList(int type) {
        Intent intent = new Intent(UserActivity.this, BangumiListActivity.class);
        intent.putExtra("vmid", mMid);
        intent.putExtra("type", type);
        intent.putExtra("name", mUser != null ? mUser.name : mName);
        startActivity(intent);
    }

    private void switchTab(int tab) {
        mTab = tab;
        mTabVideos.setTextColor(tab == TAB_VIDEOS ? 0xFF33B5E5 : 0xFFB3B3B3);
        mTabDynamic.setTextColor(tab == TAB_DYNAMIC ? 0xFF33B5E5 : 0xFFB3B3B3);
        mTabIntro.setTextColor(tab == TAB_INTRO ? 0xFF33B5E5 : 0xFFB3B3B3);
        mIntroScroll.setVisibility(tab == TAB_INTRO ? View.VISIBLE : View.GONE);
        findViewById(R.id.user_list).setVisibility(tab == TAB_INTRO ? View.GONE : View.VISIBLE);

        if (tab == TAB_INTRO) {
            ((TextView) findViewById(R.id.intro_text)).setText(
                    mUser != null && mUser.sign != null && mUser.sign.length() > 0
                            ? mUser.sign : "这个人很懒，什么都没写");
            return;
        }
        if (tab == TAB_VIDEOS) {
            if (!mVideoTabLoaded) {
                mVideoTabLoaded = true;
                mListPage.showLoading();
                loadVideos(1);
            } else {
                mListPage.setAdapter(new ListAdapters.VideoList(this, mVideos));
                mListPage.showContent();
            }
        } else {
            if (!mDynamicTabLoaded) {
                mDynamicTabLoaded = true;
                mListPage.showLoading();
                loadDynamics("");
            } else {
                mListPage.setAdapter(new ListAdapters.DynamicList(this, mDynamics));
                mListPage.showContent();
            }
        }
    }

    private void loadMore() {
        if (mTab == TAB_VIDEOS) {
            loadVideos(mVideosPage + 1);
        } else {
            loadDynamics(mDynamicOffset);
        }
    }

    private void loadVideos(final int page) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Object[] result = UserApi.getSpaceVideos(mMid, page);
                    final List<VideoCard> videos = (List<VideoCard>) result[0];
                    final int totalPages = (Integer) result[1];
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            mVideosPage = page;
                            mVideosHasMore = page < totalPages;
                            mVideos.addAll(videos);
                            mListPage.setAdapter(new ListAdapters.VideoList(UserActivity.this, mVideos));
                            mListPage.finishLoadMore(mVideosHasMore);
                            if (mVideos.size() == 0) {
                                mListPage.showEmpty("这个UP还没有投稿");
                            } else {
                                mListPage.showContent();
                            }
                        }
                    });
                } catch (final Exception e) {
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            if (mVideos.size() == 0) {
                                mListPage.showError(e.getMessage(), new Runnable() {
                                    @Override
                                    public void run() {
                                        loadVideos(1);
                                    }
                                });
                            }
                        }
                    });
                }
            }
        }).start();
    }

    private void loadDynamics(final String offset) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Object[] result = DynamicApi.getSpaceFeed(mMid, offset);
                    final List<DynamicItem> items = (List<DynamicItem>) result[0];
                    final String next = (String) result[1];
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            mDynamicOffset = next;
                            mDynamicHasMore = next != null && next.length() > 0;
                            mDynamics.addAll(items);
                            mListPage.setAdapter(new ListAdapters.DynamicList(UserActivity.this, mDynamics));
                            mListPage.finishLoadMore(mDynamicHasMore);
                            if (mDynamics.size() == 0) {
                                mListPage.showEmpty("这个UP还没有发动态");
                            } else {
                                mListPage.showContent();
                            }
                        }
                    });
                } catch (final Exception e) {
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            if (mDynamics.size() == 0) {
                                mListPage.showError(e.getMessage(), new Runnable() {
                                    @Override
                                    public void run() {
                                        loadDynamics("");
                                    }
                                });
                            }
                        }
                    });
                }
            }
        }).start();
    }
}
