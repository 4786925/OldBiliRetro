package com.android.oldbili.retro;

import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.android.oldbili.retro.api.SearchApi;
import com.android.oldbili.retro.model.UserInfo;
import com.android.oldbili.retro.model.VideoCard;
import com.android.oldbili.retro.util.DensityUtil;
import com.android.oldbili.retro.util.Sp;
import com.android.oldbili.retro.widget.ListAdapters;
import com.android.oldbili.retro.widget.ListPage;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 搜索页。
 * 支持关键词搜索（视频/用户）、AV号 BV号 快捷直达；
 * 搜索历史最多 20 条，最上面有"清除搜索记录"按钮。
 */
public class SearchActivity extends BaseActivity {

    private EditText mKeyword;
    private LinearLayout mTypeRow;
    private final TextView[] mTypeTabs = new TextView[5];
    private ScrollView mHistoryScroll;
    private LinearLayout mHistoryItems;
    private ListPage mListPage;

    private int mSearchType = 0;    // 0视频 1番剧 2影视 3直播 4用户
    private int mPage = 1;
    private String mCurrentKeyword = "";
    private final List<VideoCard> mVideos = new ArrayList<VideoCard>();
    private final List<UserInfo> mUsers = new ArrayList<UserInfo>();
    private final List<com.android.oldbili.retro.model.BangumiCard> mBangumis =
            new ArrayList<com.android.oldbili.retro.model.BangumiCard>();
    private final List<com.android.oldbili.retro.model.LiveRoom> mLives =
            new ArrayList<com.android.oldbili.retro.model.LiveRoom>();
    /** 影视（type=2）单独一段缓存，避免和番剧互相覆盖 */
    private final List<com.android.oldbili.retro.model.BangumiCard> mTvs =
            new ArrayList<com.android.oldbili.retro.model.BangumiCard>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.tab_search);
        setContentView(R.layout.activity_search);

        mKeyword = (EditText) findViewById(R.id.keyword);
        mTypeRow = (LinearLayout) findViewById(R.id.type_row);
        mTypeTabs[0] = (TextView) findViewById(R.id.type_video);
        mTypeTabs[1] = (TextView) findViewById(R.id.type_bangumi);
        mTypeTabs[2] = (TextView) findViewById(R.id.type_tv);
        mTypeTabs[3] = (TextView) findViewById(R.id.type_live);
        mTypeTabs[4] = (TextView) findViewById(R.id.type_user);
        mHistoryScroll = (ScrollView) findViewById(R.id.history_scroll);
        mHistoryItems = (LinearLayout) findViewById(R.id.history_items);
        mListPage = new ListPage(findViewById(R.id.result_page));

        findViewById(R.id.search_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doSearch();
            }
        });
        mKeyword.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    doSearch();
                    return true;
                }
                return false;
            }
        });

        // 五个类型页签
        for (int i = 0; i < mTypeTabs.length; i++) {
            final int type = i;
            mTypeTabs[i].setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mSearchType != type) {
                        mSearchType = type;
                        updateTypeStyle();
                        startSearch();
                    }
                }
            });
        }

        findViewById(R.id.clear_history).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Sp.putString(Sp.SEARCH_HISTORY, "[]");
                mHistoryItems.removeAllViews();
            }
        });

        // 外部可带关键词直达（深度链接 + 老设备测试用），走完整 doSearch（含 AV/BV/UID 识别）
        String kw = getIntent().getStringExtra("keyword");
        if (kw != null && kw.length() > 0) {
            mKeyword.setText(kw);
            mKeyword.setSelection(kw.length());
            mHistoryScroll.setVisibility(View.GONE);
            doSearch();
        }

        // 下拉刷新：清掉当前类型重新拉取
        mListPage.enablePullRefresh(new ListPage.OnRefreshListener() {
            @Override
            public void onRefresh() {
                if (mCurrentKeyword == null || mCurrentKeyword.length() == 0) {
                    mListPage.finishRefresh();
                    return;
                }
                clearCurrentType();
                mPage = 1;
                load(1);
                mListPage.getListView().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mListPage.finishRefresh();
                    }
                }, 600);
            }
        });

        mListPage.setOnLoadMore(new ListPage.OnLoadMoreListener() {
            @Override
            public void onLoadMore(int page) {
                load(page);
            }
        });
        mListPage.setOnItemClick(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                int idx = position - mListPage.getListView().getHeaderViewsCount();
                if (mSearchType == 4) {
                    if (idx >= 0 && idx < mUsers.size()) {
                        Intent intent = new Intent(SearchActivity.this, UserActivity.class);
                        intent.putExtra("mid", mUsers.get(idx).mid);
                        intent.putExtra("name", mUsers.get(idx).name);
                        startActivity(intent);
                    }
                    return;
                }
                if (mSearchType == 1 || mSearchType == 2) {
                    List<com.android.oldbili.retro.model.BangumiCard> list =
                            mSearchType == 2 ? mTvs : mBangumis;
                    if (idx >= 0 && idx < list.size()) {
                        com.android.oldbili.retro.model.BangumiCard card = list.get(idx);
                        Intent intent = new Intent(SearchActivity.this, VideoDetailActivity.class);
                        intent.putExtra("season_id", card.seasonId);
                        intent.putExtra("type", mSearchType == 1
                                ? BangumiListActivity.TYPE_BANGUMI : BangumiListActivity.TYPE_TV);
                        startActivity(intent);
                    }
                    return;
                }
                if (mSearchType == 3) {
                    if (idx >= 0 && idx < mLives.size()) {
                        com.android.oldbili.retro.model.LiveRoom room = mLives.get(idx);
                        Intent intent = new Intent(SearchActivity.this, PlayerActivity.class);
                        intent.putExtra("live", true);
                        intent.putExtra("room_id", room.roomId);
                        intent.putExtra("title", room.title);
                        startActivity(intent);
                    }
                    return;
                }
                if (idx >= 0 && idx < mVideos.size()) {
                    VideoCard card = mVideos.get(idx);
                    if (card == null || !card.isPlayable()) {
                        toast("该视频已失效");
                        return;
                    }
                    Intent intent = new Intent(SearchActivity.this, VideoDetailActivity.class);
                    if (card.bvid != null && card.bvid.length() > 0) {
                        intent.putExtra("bvid", card.bvid);
                    } else {
                        intent.putExtra("aid", card.aid);
                    }
                    startActivity(intent);
                }
            }
        });

        renderHistory();
    }

    private void updateTypeStyle() {
        for (int i = 0; i < mTypeTabs.length; i++) {
            mTypeTabs[i].setTextColor(i == mSearchType ? 0xFF33B5E5 : 0xFFB3B3B3);
        }
    }

    private void doSearch() {
        String keyword = mKeyword.getText().toString().trim();
        if (keyword.length() == 0) {
            toast("请输入搜索内容");
            return;
        }
        // 收键盘
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(mKeyword.getWindowToken(), 0);
        }

        // AV / BV 号直达视频页，UID 直达个人空间
        String[] idInfo = SearchApi.detectId(keyword);
        if (idInfo != null) {
            saveHistory(keyword);
            if ("mid".equals(idInfo[0])) {
                Intent intent = new Intent(this, UserActivity.class);
                intent.putExtra("mid", idInfo[1]);
                startActivity(intent);
                return;
            }
            Intent intent = new Intent(this, VideoDetailActivity.class);
            intent.putExtra(idInfo[0], idInfo[1]);
            startActivity(intent);
            return;
        }

        saveHistory(keyword);
        if (!keyword.equals(mCurrentKeyword)) {
            mVideos.clear();
            mUsers.clear();
            mBangumis.clear();
            mTvs.clear();
            mLives.clear();
        }
        mCurrentKeyword = keyword;
        mHistoryScroll.setVisibility(View.GONE);
        mTypeRow.setVisibility(View.VISIBLE);
        findViewById(R.id.result_page).setVisibility(View.VISIBLE);
        startSearch();
    }

    private void startSearch() {
        mPage = 1;
        // 该类型已有结果就直接用缓存（切换页签不再清空、不再重复请求）
        if (hasCached(mSearchType)) {
            renderCached();
            return;
        }
        load(1);
    }

    private boolean hasCached(int type) {
        if (type == 1) {
            return !mBangumis.isEmpty();
        }
        if (type == 2) {
            return !mTvs.isEmpty();
        }
        if (type == 3) {
            return !mLives.isEmpty();
        }
        if (type == 4) {
            return !mUsers.isEmpty();
        }
        return !mVideos.isEmpty();
    }

    private void clearCurrentType() {
        if (mSearchType == 1) {
            mBangumis.clear();
        } else if (mSearchType == 2) {
            mTvs.clear();
        } else if (mSearchType == 3) {
            mLives.clear();
        } else if (mSearchType == 4) {
            mUsers.clear();
        } else {
            mVideos.clear();
        }
    }

    /** 直接渲染已缓存的结果（切换页签立即显示） */
    private void renderCached() {
        if (mSearchType == 1) {
            mListPage.setAdapter(new ListAdapters.BangumiList(this, mBangumis));
        } else if (mSearchType == 2) {
            mListPage.setAdapter(new ListAdapters.BangumiList(this, mTvs));
        } else if (mSearchType == 3) {
            mListPage.setAdapter(new ListAdapters.LiveList(this, mLives));
        } else if (mSearchType == 4) {
            mListPage.setAdapter(new ListAdapters.UserList(this, mUsers, false));
        } else {
            mListPage.setAdapter(new ListAdapters.VideoList(this, mVideos));
        }
        mListPage.finishLoadMore(true);
        mListPage.showContent();
    }

    private void load(final int page) {
        if (page == 1) {
            mListPage.showLoading("加载中…");
        }
        // 翻页时记录/恢复滚动位置（setAdapter 重建会跳回顶部）
        final int[] scroll = page == 1 ? null : captureScroll();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (mSearchType == 4) {
                        final List<UserInfo> users = fetchUsers(page);
                        runOnUi(new Runnable() {
                            @Override
                            public void run() {
                                mUsers.addAll(users);
                                mListPage.setAdapter(new ListAdapters.UserList(SearchActivity.this, mUsers, false));
                                restoreScroll(scroll);
                                mListPage.finishLoadMore(users.size() >= 20);
                                if (mUsers.size() == 0) {
                                    mListPage.showEmpty("没有找到相关用户");
                                } else {
                                    mListPage.showContent();
                                }
                            }
                        });
                    } else if (mSearchType == 1 || mSearchType == 2) {
                        final List<com.android.oldbili.retro.model.BangumiCard> cards =
                                fetchBangumi(page);
                        runOnUi(new Runnable() {
                            @Override
                            public void run() {
                                if (mSearchType == 2) {
                                    mTvs.addAll(cards);
                                } else {
                                    mBangumis.addAll(cards);
                                }
                                mListPage.setAdapter(new ListAdapters.BangumiList(
                                        SearchActivity.this, mSearchType == 2 ? mTvs : mBangumis));
                                restoreScroll(scroll);
                                mListPage.finishLoadMore(cards.size() >= 20);
                                if ((mSearchType == 2 ? mTvs.size() : mBangumis.size()) == 0) {
                                    mListPage.showEmpty(mSearchType == 1 ? "没有找到相关番剧" : "没有找到相关影视");
                                } else {
                                    mListPage.showContent();
                                }
                            }
                        });
                    } else if (mSearchType == 3) {
                        final List<com.android.oldbili.retro.model.LiveRoom> lives =
                                fetchLive(page);
                        runOnUi(new Runnable() {
                            @Override
                            public void run() {
                                mLives.addAll(lives);
                                mListPage.setAdapter(new ListAdapters.LiveList(SearchActivity.this, mLives));
                                restoreScroll(scroll);
                                mListPage.finishLoadMore(lives.size() >= 20);
                                if (mLives.size() == 0) {
                                    mListPage.showEmpty("没有找到相关直播");
                                } else {
                                    mListPage.showContent();
                                }
                            }
                        });
                    } else {
                        final List<VideoCard> videos = fetchVideos(page);
                        runOnUi(new Runnable() {
                            @Override
                            public void run() {
                                mVideos.addAll(videos);
                                mListPage.setAdapter(new ListAdapters.VideoList(SearchActivity.this, mVideos));
                                restoreScroll(scroll);
                                mListPage.finishLoadMore(videos.size() >= 20);
                                if (mVideos.size() == 0) {
                                    mListPage.showEmpty("没有找到相关视频");
                                } else {
                                    mListPage.showContent();
                                }
                            }
                        });
                    }
                } catch (final Exception e) {
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            if (mVideos.size() == 0 && mUsers.size() == 0
                                    && mBangumis.size() == 0 && mTvs.size() == 0
                                    && mLives.size() == 0) {
                                mListPage.showError(e.getMessage(), new Runnable() {
                                    @Override
                                    public void run() {
                                        startSearch();
                                    }
                                });
                            } else {
                                mListPage.finishLoadMore(true);
                            }
                        }
                    });
                }
            }
        }).start();
    }

    /** 记录当前列表滚动位置 [firstVisible, top] */
    private int[] captureScroll() {
        try {
            android.widget.ListView lv = mListPage.getListView();
            if (lv != null && lv.getChildCount() > 0) {
                android.view.View child = lv.getChildAt(0);
                return new int[]{lv.getFirstVisiblePosition(), child == null ? 0 : child.getTop()};
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /** 恢复滚动位置（翻页后不跳回顶部） */
    private void restoreScroll(int[] pos) {
        if (pos == null || pos[0] <= 0) {
            return;
        }
        try {
            mListPage.getListView().setSelectionFromTop(pos[0], pos[1]);
        } catch (Exception ignored) {
        }
    }

    // ==================== 搜索历史（20条上限） ====================

    private void saveHistory(String keyword) {
        try {
            List<String> list = readHistory();
            list.remove(keyword);
            list.add(0, keyword);
            while (list.size() > 20) {
                list.remove(list.size() - 1);
            }
            JSONArray arr = new JSONArray();
            for (int i = 0; i < list.size(); i++) {
                arr.put(list.get(i));
            }
            Sp.putString(Sp.SEARCH_HISTORY, arr.toString());
            renderHistory();
        } catch (Exception ignored) {
        }
    }

    private List<String> readHistory() {
        List<String> list = new ArrayList<String>();
        String saved = Sp.getString(Sp.SEARCH_HISTORY, "[]");
        try {
            JSONArray arr = new JSONArray(new org.json.JSONTokener(saved));
            for (int i = 0; i < arr.length(); i++) {
                String s = arr.optString(i, "");
                if (s.length() > 0 && !list.contains(s)) {
                    list.add(s);
                }
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    private void renderHistory() {
        mHistoryItems.removeAllViews();
        List<String> list = readHistory();
        for (int i = 0; i < list.size(); i++) {
            final String keyword = list.get(i);
            TextView tv = new TextView(this);
            tv.setText(keyword);
            tv.setTextColor(0xFFB3B3B3);
            tv.setTextSize(14);
            tv.setPadding(DensityUtil.dp2px(this, 12), DensityUtil.dp2px(this, 10),
                    DensityUtil.dp2px(this, 12), DensityUtil.dp2px(this, 10));
            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mKeyword.setText(keyword);
                    doSearch();
                }
            });
            mHistoryItems.addView(tv);
        }
        if (list.size() == 0) {
            TextView tv = new TextView(this);
            tv.setText("暂无搜索历史");
            tv.setTextColor(0xFF7F7F7F);
            tv.setTextSize(13);
            tv.setPadding(DensityUtil.dp2px(this, 12), DensityUtil.dp2px(this, 20), 0, 0);
            mHistoryItems.addView(tv);
        }
    }

    // ==================== 带自动重试的搜索请求（412/空结果重试） ====================

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }

    private List<VideoCard> fetchVideos(int page) throws Exception {
        List<VideoCard> l = SearchApi.searchVideo(mCurrentKeyword, page);
        for (int r = 0; page == 1 && l.isEmpty() && r < 2; r++) {
            sleep(800L * (r + 1));
            l = SearchApi.searchVideo(mCurrentKeyword, page);
        }
        return l;
    }

    private List<UserInfo> fetchUsers(int page) throws Exception {
        List<UserInfo> l = SearchApi.searchUser(mCurrentKeyword, page);
        for (int r = 0; page == 1 && l.isEmpty() && r < 2; r++) {
            sleep(800L * (r + 1));
            l = SearchApi.searchUser(mCurrentKeyword, page);
        }
        return l;
    }

    private List<com.android.oldbili.retro.model.LiveRoom> fetchLive(int page) throws Exception {
        List<com.android.oldbili.retro.model.LiveRoom> l = SearchApi.searchLive(mCurrentKeyword, page);
        for (int r = 0; page == 1 && l.isEmpty() && r < 2; r++) {
            sleep(800L * (r + 1));
            l = SearchApi.searchLive(mCurrentKeyword, page);
        }
        return l;
    }

    private List<com.android.oldbili.retro.model.BangumiCard> fetchBangumi(int page) throws Exception {
        List<com.android.oldbili.retro.model.BangumiCard> l =
                SearchApi.searchBangumi(mCurrentKeyword, page, mSearchType == 2);
        for (int r = 0; page == 1 && l.isEmpty() && r < 2; r++) {
            sleep(800L * (r + 1));
            l = SearchApi.searchBangumi(mCurrentKeyword, page, mSearchType == 2);
        }
        return l;
    }
}
