package com.android.oldbili.retro;

import android.app.Activity;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.TextView;

import com.android.oldbili.retro.api.HomeApi;
import com.android.oldbili.retro.api.LiveApi;
import com.android.oldbili.retro.model.LiveRoom;
import com.android.oldbili.retro.model.VideoCard;
import com.android.oldbili.retro.net.HttpUtil;
import com.android.oldbili.retro.util.Sp;
import com.android.oldbili.retro.widget.ListAdapters;
import com.android.oldbili.retro.widget.ListPage;

import java.util.ArrayList;
import java.util.List;

/**
 * 首页页签：顶部 推荐/热门/直播 三栏 + 搜索入口。
 * 登录后推荐栏是账号个性化推荐，未登录按设置显示默认热门推荐。
 */
public class HomePage {

    private static final int TAB_RECOMMEND = 0;
    private static final int TAB_POPULAR = 1;
    private static final int TAB_BANGUMI = 2;
    private static final int TAB_TV = 3;
    private static final int TAB_LIVE = 4;

    private final Activity mActivity;
    private View mView;
    private ListPage mListPage;
    private TextView mTabRecommend;
    private TextView mTabPopular;
    private TextView mTabBangumi;
    private TextView mTabTv;
    private TextView mTabLive;

    private int mTab = TAB_RECOMMEND;
    private int mPage = 1;
    private boolean mHasMore = true;
    private boolean mLoaded;
    private boolean mLastLogged;
    private String mLastLayout = "";

    private final List<VideoCard> mVideos = new ArrayList<VideoCard>();
    private final List<LiveRoom> mLives = new ArrayList<LiveRoom>();
    private final List<com.android.oldbili.retro.model.BangumiCard> mBangumis =
            new ArrayList<com.android.oldbili.retro.model.BangumiCard>();

    public HomePage(Activity activity) {
        mActivity = activity;
    }

    public View getView() {
        if (mView == null) {
            mView = LayoutInflater.from(mActivity).inflate(R.layout.activity_home, null);
            mListPage = new ListPage(mView);
            mTabRecommend = (TextView) mView.findViewById(R.id.tab_recommend);
            mTabPopular = (TextView) mView.findViewById(R.id.tab_popular);
            mTabBangumi = (TextView) mView.findViewById(R.id.tab_bangumi);
            mTabTv = (TextView) mView.findViewById(R.id.tab_tv);
            mTabLive = (TextView) mView.findViewById(R.id.tab_live);

            if (!Sp.getBoolean(Sp.SET_HOME_LIVE, true)) {
                mTabLive.setVisibility(View.GONE);
            }

            // 搜索入口
            mView.findViewById(R.id.search_bar).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mActivity.startActivity(new Intent(mActivity, SearchActivity.class));
                }
            });

            mTabRecommend.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    switchTab(TAB_RECOMMEND);
                }
            });
            mTabPopular.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    switchTab(TAB_POPULAR);
                }
            });
            mTabBangumi.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    switchTab(TAB_BANGUMI);
                }
            });
            mTabTv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    switchTab(TAB_TV);
                }
            });
            mTabLive.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    switchTab(TAB_LIVE);
                }
            });

            mListPage.setOnLoadMore(new ListPage.OnLoadMoreListener() {
                @Override
                public void onLoadMore(int page) {
                    load(page);
                }
            });
            // 下拉刷新（2013 风格）
            mListPage.enablePullRefresh(new ListPage.OnRefreshListener() {
                @Override
                public void onRefresh() {
                    refresh();
                }
            });
            mListPage.setOnItemClick(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    int idx = position - mListPage.getListView().getHeaderViewsCount();
                    if (mTab == TAB_LIVE) {
                        if (idx >= 0 && idx < mLives.size()) {
                            LiveRoom room = mLives.get(idx);
                            Intent intent = new Intent(mActivity, LiveRoomActivity.class);
                            intent.putExtra("room_id", room.roomId);
                            intent.putExtra("title", room.title);
                            mActivity.startActivity(intent);
                        }
                        return;
                    }
                    if (mTab == TAB_BANGUMI || mTab == TAB_TV) {
                        if (idx >= 0 && idx < mBangumis.size()) {
                            com.android.oldbili.retro.model.BangumiCard card = mBangumis.get(idx);
                            Intent intent = new Intent(mActivity, VideoDetailActivity.class);
                            intent.putExtra("season_id", card.seasonId);
                            intent.putExtra("type", mTab == TAB_BANGUMI
                                    ? BangumiListActivity.TYPE_BANGUMI : BangumiListActivity.TYPE_TV);
                            mActivity.startActivity(intent);
                        }
                        return;
                    }
                    if (idx >= 0 && idx < mVideos.size()) {
                        openVideo(mVideos.get(idx));
                    }
                }
            });
        }
        return mView;
    }

    private void openVideo(VideoCard card) {
        if (card == null || !card.isPlayable()) {
            com.android.oldbili.retro.util.ToastUtil.show(mActivity, "该视频已失效");
            return;
        }
        Intent intent = new Intent(mActivity, VideoDetailActivity.class);
        if (card.bvid != null && card.bvid.length() > 0) {
            intent.putExtra("bvid", card.bvid);
        } else {
            intent.putExtra("aid", card.aid);
        }
        mActivity.startActivity(intent);
    }

    public void onShow() {
        if (mView == null) {
            return;
        }
        boolean logged = HttpUtil.isLoggedIn();
        String layout = Sp.getString(Sp.SET_HOME_LAYOUT, "single");
        if (!mLoaded || logged != mLastLogged || !layout.equals(mLastLayout)) {
            mLoaded = true;
            mLastLogged = logged;
            mLastLayout = layout;
            refresh();
        }
    }

    /** 双击页签强制刷新 */
    public void forceRefresh() {
        refresh();
    }

    private void refresh() {
        mPage = 1;
        mHasMore = true;
        mVideos.clear();
        mLives.clear();
        mBangumis.clear();
        load(1);
    }

    private void switchTab(int tab) {
        if (mTab == tab && mLoaded) {
            return;
        }
        mTab = tab;
        mPage = 1;
        mHasMore = true;
        mVideos.clear();
        mLives.clear();
        mBangumis.clear();
        updateTabStyle();
        load(1);
    }

    private void updateTabStyle() {
        mTabRecommend.setTextColor(mTab == TAB_RECOMMEND ? 0xFF33B5E5 : 0xFFB3B3B3);
        mTabPopular.setTextColor(mTab == TAB_POPULAR ? 0xFF33B5E5 : 0xFFB3B3B3);
        mTabBangumi.setTextColor(mTab == TAB_BANGUMI ? 0xFF33B5E5 : 0xFFB3B3B3);
        mTabTv.setTextColor(mTab == TAB_TV ? 0xFF33B5E5 : 0xFFB3B3B3);
        mTabLive.setTextColor(mTab == TAB_LIVE ? 0xFF33B5E5 : 0xFFB3B3B3);
    }

    private void load(final int page) {
        if (page == 1) {
            mListPage.showLoading();
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (mTab == TAB_LIVE) {
                        // 登录时优先关注主播直播列表，否则/失败用推荐直播兜底
                        List<LiveRoom> rooms;
                        try {
                            rooms = LiveApi.getFollowLiveRooms(page);
                            if (rooms.size() == 0) {
                                rooms = LiveApi.getRoomList(page);
                            }
                        } catch (Exception e) {
                            rooms = LiveApi.getRoomList(page);
                        }
                        final List<LiveRoom> result = rooms;
                        mActivity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mLives.addAll(result);
                                mListPage.setAdapter(new ListAdapters.LiveList(mActivity, mLives));
                                mListPage.finishLoadMore(result.size() >= 20);
                                mListPage.finishRefresh();
                                if (mLives.size() == 0) {
                                    mListPage.showEmpty(mActivity.getString(R.string.empty));
                                } else {
                                    mListPage.showContent();
                                }
                            }
                        });
                        return;
                    }

                    if (mTab == TAB_BANGUMI || mTab == TAB_TV) {
                        final List<com.android.oldbili.retro.model.BangumiCard> cards =
                                com.android.oldbili.retro.api.BangumiApi.getSeasonIndex(
                                        mTab == TAB_BANGUMI ? 1 : 2, page);
                        mActivity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                // 翻页保持滚动位置，不跳回顶部
                                int first = 0;
                                int top = 0;
                                try {
                                    android.widget.ListView lv = mListPage.getListView();
                                    if (lv.getChildCount() > 0) {
                                        first = lv.getFirstVisiblePosition();
                                        View child = lv.getChildAt(0);
                                        top = child == null ? 0 : child.getTop();
                                    }
                                } catch (Exception ignored) {
                                }
                                mBangumis.addAll(cards);
                                mListPage.setAdapter(new ListAdapters.BangumiList(mActivity, mBangumis));
                                if (first > 0) {
                                    try {
                                        mListPage.getListView().setSelectionFromTop(first, top);
                                    } catch (Exception ignored) {
                                    }
                                }
                                mListPage.finishLoadMore(cards.size() >= 20);
                                mListPage.finishRefresh();
                                if (mBangumis.size() == 0) {
                                    mListPage.showEmpty("暂无内容");
                                } else {
                                    mListPage.showContent();
                                }
                            }
                        });
                        return;
                    }

                    List<VideoCard> cards = loadVideoTab(page);
                    // 过滤掉接口返回的失效卡（无 aid 无 bvid 的广告/失效位）
                    final List<VideoCard> result = new ArrayList<VideoCard>();
                    for (int i = 0; i < cards.size(); i++) {
                        if (cards.get(i) != null && cards.get(i).isPlayable()) {
                            result.add(cards.get(i));
                        }
                    }
                    mActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mVideos.addAll(result);
                            // 首页显示布局：单行大图 / 双行紧凑（设置里切换）
                            boolean doubleLine = "double".equals(Sp.getString(Sp.SET_HOME_LAYOUT, "single"));
                            if (doubleLine) {
                                mListPage.setAdapter(new ListAdapters.VideoListCompact(mActivity, mVideos));
                            } else {
                                mListPage.setAdapter(new ListAdapters.VideoList(mActivity, mVideos));
                            }
                            mListPage.finishLoadMore(result.size() >= 20);
                            mListPage.finishRefresh();
                            if (mVideos.size() == 0) {
                                mListPage.showEmpty(mActivity.getString(R.string.empty));
                            } else {
                                mListPage.showContent();
                            }
                        }
                    });
                } catch (final Exception e) {
                    mActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (mVideos.size() == 0 && mLives.size() == 0) {
                                mListPage.finishRefresh();
                                mListPage.showError(e.getMessage(), new Runnable() {
                                    @Override
                                    public void run() {
                                        refresh();
                                    }
                                });
                            } else {
                                mListPage.finishRefresh();
                                mListPage.finishLoadMore(true);
                            }
                        }
                    });
                }
            }
        }).start();
    }

    private List<VideoCard> loadVideoTab(int page) throws Exception {
        if (mTab == TAB_POPULAR) {
            return HomeApi.getPopular(page, 20);
        }
        // 推荐栏：登录是个性化推荐；游客也走 rcmd（游客推荐流），挂了才按设置兜底
        try {
            return HomeApi.getRecommend(page);
        } catch (Exception e) {
            String def = Sp.getString(Sp.SET_HOME_DEFAULT, "popular");
            if ("recommend".equals(def)) {
                return HomeApi.getRanking(0, page);
            }
            return HomeApi.getPopular(page, 20);
        }
    }
}
