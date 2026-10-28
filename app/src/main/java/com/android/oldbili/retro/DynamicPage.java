package com.android.oldbili.retro;

import android.app.Activity;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;

import com.android.oldbili.retro.api.DynamicApi;
import com.android.oldbili.retro.model.DynamicItem;
import com.android.oldbili.retro.net.HttpUtil;
import com.android.oldbili.retro.widget.ListAdapters;
import com.android.oldbili.retro.widget.ListPage;

import java.util.ArrayList;
import java.util.List;

/**
 * 动态页签：登录后的关注动态流，未登录提示去登录。
 * 视频动态点击进视频详情，其他动态进动态详情（可看评论、可回复）。
 */
public class DynamicPage {

    private final Activity mActivity;
    private View mView;
    private ListPage mListPage;

    private String mOffset = "";
    private boolean mHasMore = true;
    private boolean mLoaded;
    private boolean mLastLogged;

    private final List<DynamicItem> mItems = new ArrayList<DynamicItem>();

    public DynamicPage(Activity activity) {
        mActivity = activity;
    }

    public View getView() {
        if (mView == null) {
            mView = LayoutInflater.from(mActivity).inflate(R.layout.widget_list_page, null);
            mListPage = new ListPage(mView);
            mListPage.setOnLoadMore(new ListPage.OnLoadMoreListener() {
                @Override
                public void onLoadMore(int page) {
                    loadMore();
                }
            });
            // 顶部下滑强制刷新
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
                    if (idx < 0 || idx >= mItems.size()) {
                        return;
                    }
                    DynamicItem item = mItems.get(idx);
                    if (item.type == DynamicItem.TYPE_AV && (item.aid > 0 || item.bvid.length() > 0)) {
                        Intent intent = new Intent(mActivity, VideoDetailActivity.class);
                        if (item.bvid.length() > 0) {
                            intent.putExtra("bvid", item.bvid);
                        } else {
                            intent.putExtra("aid", item.aid);
                        }
                        mActivity.startActivity(intent);
                    } else {
                        Intent intent = new Intent(mActivity, DynamicDetailActivity.class);
                        intent.putExtra("dynamic", item.dynamicId);
                        mActivity.startActivity(intent);
                    }
                }
            });
            mListPage.setOnEmptyClick(new Runnable() {
                @Override
                public void run() {
                    if (!HttpUtil.isLoggedIn()) {
                        mActivity.startActivity(new Intent(mActivity, LoginActivity.class));
                    }
                }
            });
        }
        return mView;
    }

    public void onShow() {
        if (mView == null) {
            return;
        }
        boolean logged = HttpUtil.isLoggedIn();
        if (!mLoaded || logged != mLastLogged) {
            mLoaded = true;
            mLastLogged = logged;
            refresh();
        }
    }

    /** 双击页签/下拉强制刷新 */
    public void forceRefresh() {
        refresh();
    }

    private void refresh() {
        mOffset = "";
        mHasMore = true;
        mItems.clear();
        if (!HttpUtil.isLoggedIn()) {
            mListPage.showEmpty("未登录，点击去登录后查看动态");
            mListPage.finishRefresh();
            return;
        }
        // 离线兜底：先显示上次缓存的动态（有网再后台刷新）
        List<DynamicItem> cached = readCache();
        if (cached.size() > 0) {
            mItems.addAll(cached);
            mListPage.setAdapter(new ListAdapters.DynamicList(mActivity, mItems));
            mListPage.showContent();
        } else {
            mListPage.showLoading("加载中…");
        }
        loadMore();
    }

    private void loadMore() {
        if (!mHasMore) {
            mListPage.finishRefresh();
            return;
        }
        final String offset = mOffset;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Object[] result = DynamicApi.getFeed(offset);
                    final List<DynamicItem> items = (List<DynamicItem>) result[0];
                    final String next = (String) result[1];
                    mActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mOffset = next;
                            mHasMore = next != null && next.length() > 0;
                            // 翻页/刷新后保持滚动位置，不强行回顶部
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
                            mItems.addAll(items);
                            mListPage.setAdapter(new ListAdapters.DynamicList(mActivity, mItems));
                            if (first > 0) {
                                try {
                                    mListPage.getListView().setSelectionFromTop(first, top);
                                } catch (Exception ignored) {
                                }
                            }
                            mListPage.finishLoadMore(mHasMore);
                            mListPage.finishRefresh();
                            if (mItems.size() == 0) {
                                mListPage.showEmpty("关注的人还没有发动态");
                            } else {
                                mListPage.showContent();
                                saveCache();
                            }
                        }
                    });
                } catch (final Exception e) {
                    mActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mListPage.finishRefresh();
                            if (mItems.size() == 0) {
                                String err = e.getMessage();
                                if (err != null && (err.contains("-403") || err.contains("-352") || err.contains("-412"))) {
                                    err = "动态接口被风控拦截，已自动刷新身份指纹，请点击重试";
                                }
                                mListPage.showError(err, new Runnable() {
                                    @Override
                                    public void run() {
                                        refresh();
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

    // ==================== 离线缓存 ====================

    private static final String CACHE_KEY = "dynamic_page_cache";

    private void saveCache() {
        try {
            org.json.JSONArray arr = new org.json.JSONArray();
            for (int i = 0; i < mItems.size(); i++) {
                DynamicItem d = mItems.get(i);
                org.json.JSONObject o = new org.json.JSONObject();
                o.put("id", d.dynamicId);
                o.put("type", d.type);
                o.put("mid", d.mid);
                o.put("name", d.userName);
                o.put("face", d.userFace);
                o.put("text", d.text);
                o.put("time", d.time);
                o.put("like", d.like);
                o.put("aid", d.aid);
                o.put("bvid", d.bvid);
                o.put("vtitle", d.videoTitle);
                o.put("vcover", d.videoCover);
                o.put("vplay", d.videoPlay);
                o.put("vdur", d.videoDuration);
                arr.put(o);
            }
            com.android.oldbili.retro.util.Sp.putString(CACHE_KEY, arr.toString());
        } catch (Exception ignored) {
        }
    }

    private List<DynamicItem> readCache() {
        List<DynamicItem> list = new ArrayList<DynamicItem>();
        try {
            org.json.JSONArray arr = new org.json.JSONArray(new org.json.JSONTokener(
                    com.android.oldbili.retro.util.Sp.getString(CACHE_KEY, "[]")));
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject o = arr.optJSONObject(i);
                if (o == null) {
                    continue;
                }
                DynamicItem d = new DynamicItem();
                d.dynamicId = o.optString("id", "");
                d.type = o.optInt("type", 0);
                d.mid = o.optLong("mid", 0);
                d.userName = o.optString("name", "");
                d.userFace = o.optString("face", "");
                d.text = o.optString("text", "");
                d.time = o.optLong("time", 0);
                d.like = o.optLong("like", 0);
                d.aid = o.optLong("aid", 0);
                d.bvid = o.optString("bvid", "");
                d.videoTitle = o.optString("vtitle", "");
                d.videoCover = o.optString("vcover", "");
                d.videoPlay = o.optLong("vplay", 0);
                d.videoDuration = o.optInt("vdur", 0);
                list.add(d);
            }
        } catch (Exception ignored) {
        }
        return list;
    }
}
