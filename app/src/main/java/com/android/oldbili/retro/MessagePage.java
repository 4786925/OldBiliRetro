package com.android.oldbili.retro;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.oldbili.retro.api.MessageApi;
import com.android.oldbili.retro.model.MessageSession;
import com.android.oldbili.retro.net.HttpUtil;
import com.android.oldbili.retro.util.Sp;
import com.android.oldbili.retro.widget.ListAdapters;
import com.android.oldbili.retro.widget.ListPage;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 消息页签：特别关注更新 + 私信会话。
 * 点击私信进聊天（红点清除），点击特别关注进视频详情；
 * 长按会话可置顶/取消置顶/删除。
 */
public class MessagePage {

    private final Activity mActivity;
    private View mView;
    private ListPage mListPage;

    private final List<MessageSession> mAll = new ArrayList<MessageSession>();

    public MessagePage(Activity activity) {
        mActivity = activity;
    }

    // ==================== 顶部 tab（私信 / 回复 / @我 / 收到的赞 / 系统通知） ====================
    private static final int TAB_PRIVATE = 0;
    private int mTabIndex = TAB_PRIVATE;
    private TextView[] mFeedTabs;
    private final List<com.android.oldbili.retro.model.MsgFeedItem> mFeedItems =
            new java.util.ArrayList<com.android.oldbili.retro.model.MsgFeedItem>();
    // 每个互动 tab 各自独立的列表/适配器，彻底避免互相串内容
    private final List<com.android.oldbili.retro.model.MsgFeedItem> mReplyItems =
            new java.util.ArrayList<com.android.oldbili.retro.model.MsgFeedItem>();
    private final List<com.android.oldbili.retro.model.MsgFeedItem> mAtItems =
            new java.util.ArrayList<com.android.oldbili.retro.model.MsgFeedItem>();
    private ListAdapters.MsgFeedList mReplyAdapter;
    private ListAdapters.MsgFeedList mAtAdapter;
    private int mFeedPage = 1;
    private boolean mFeedLoading;
    private int mFeedSeq;
    /** 会话刷新序号：切到互动消息 tab 后丢弃迟到的私信结果 */
    private int mSessionSeq;
    /** 当前列表实际显示的 tab（-1 未知），用于判断点击是否需要真正切换 */
    private int mDisplayedMode = -1;
    private ListAdapters.MsgFeedList mMsgFeedAdapter;

    public View getView() {
        if (mView == null) {
            mView = LayoutInflater.from(mActivity).inflate(R.layout.widget_list_page, null);
            mListPage = new ListPage(mView);
            buildTabBar();
            // 顶部下滑强制刷新
            mListPage.enablePullRefresh(new ListPage.OnRefreshListener() {
                @Override
                public void onRefresh() {
                    if (mTabIndex == TAB_PRIVATE) {
                        refresh();
                    } else {
                        loadFeed(mTabIndex, 1);
                    }
                }
            });
            // 会话很多时支持翻页（几千条也能翻）
            mListPage.setOnLoadMore(new ListPage.OnLoadMoreListener() {
                @Override
                public void onLoadMore(int page) {
                    if (mTabIndex == TAB_PRIVATE) {
                        loadMoreSessions();
                    } else {
                        loadFeed(mTabIndex, mFeedPage + 1);
                    }
                }
            });
            mListPage.setOnItemClick(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    int idx = position - mListPage.getListView().getHeaderViewsCount();
                    if (mTabIndex != TAB_PRIVATE) {
                        List<com.android.oldbili.retro.model.MsgFeedItem> cur =
                                mTabIndex == 2 ? mAtItems : mReplyItems;
                        if (idx >= 0 && idx < cur.size()) {
                            openFeedItem(cur.get(idx));
                        }
                        return;
                    }
                    if (idx < 0 || idx >= mAll.size()) {
                        return;
                    }
                    MessageSession session = mAll.get(idx);
                    if (session.kind == MessageSession.KIND_FOLLOW_UP) {
                        if (session.bvid.length() == 0) {
                            return;
                        }
                        Intent intent = new Intent(mActivity, VideoDetailActivity.class);
                        intent.putExtra("bvid", session.bvid);
                        mActivity.startActivity(intent);
                        return;
                    }
                    // 进聊天：红点清零 + 上报已读（自己发的消息不会带红点，红点仅对方发消息产生）
                    final MessageSession chatSession = session;
                    chatSession.unread = 0;
                    refreshAdapter();
                    saveCache();
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            MessageApi.updateAck(chatSession.uid);
                        }
                    }).start();
                    Intent intent = new Intent(mActivity, MessageActivity.class);
                    intent.putExtra("uid", chatSession.uid);
                    intent.putExtra("name", chatSession.name);
                    intent.putExtra("face", chatSession.face);
                    mActivity.startActivity(intent);
                }
            });
            mListPage.setOnItemLongClick(new AdapterView.OnItemLongClickListener() {
                @Override
                public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                    int idx = position - mListPage.getListView().getHeaderViewsCount();
                    if (idx < 0 || idx >= mAll.size()) {
                        return true;
                    }
                    final MessageSession session = mAll.get(idx);
                    if (session.kind != MessageSession.KIND_CHAT) {
                        return true;
                    }
                    final boolean isTop = isTopUid(session.uid);
                    new AlertDialog.Builder(mActivity)
                            .setItems(new String[]{isTop ? "取消置顶" : "置顶", "删除该聊天"},
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            if (which == 0) {
                                                setTopUid(session.uid, !isTop);
                                                refresh();
                                            } else {
                                                deleteSession(session);
                                            }
                                        }
                                    })
                            .setNegativeButton(R.string.cancel, null)
                            .show();
                    return true;
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

    /** 切到消息页签时刷新（发完消息回来立刻更新） */
    public void onShow() {
        if (mView == null) {
            return;
        }
        if (mTabIndex == TAB_PRIVATE) {
            refresh();
        } else {
            loadFeed(mTabIndex, 1);
        }
    }

    /** 双击页签/下拉强制刷新 */
    public void forceRefresh() {
        refresh();
    }

    private boolean mLoadingMore;
    private long mMinTime;   // 当前已加载会话的最小时间戳（翻页游标）

    private void refresh() {
        final int sessionSeq = ++mSessionSeq;
        // 当前在互动消息 tab 时，刷新对应列表，避免被私信列表覆盖（点了回复又跳回私信）
        if (mTabIndex != TAB_PRIVATE) {
            mFeedPage = 1;
            loadFeed(mTabIndex, 1);
            return;
        }
        if (!HttpUtil.isLoggedIn()) {
            mAll.clear();
            mListPage.showEmpty("未登录，点击去登录后查看消息");
            mListPage.finishRefresh();
            return;
        }
        // 离线兜底：先显示上次缓存的列表（有网再后台刷新）
        if (mAll.size() == 0) {
            List<MessageSession> cached = readCache();
            if (cached.size() > 0) {
                mAll.addAll(cached);
                refreshAdapter();
                mListPage.showContent();
            }
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                // 消息页请求限制在 5~6 秒，避免无限转圈
                com.android.oldbili.retro.net.HttpUtil.setQuickTimeout(5000, 6000);
                final List<MessageSession> followUps = MessageApi.getFollowUp();
                final List<MessageSession> chats;
                try {
                    chats = MessageApi.getSessions();
                } catch (Exception e) {
                    android.util.Log.w("MessagePage", "会话获取失败: " + e.getMessage());
                    final String errMsg = e.getMessage();
                    mActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mListPage.finishRefresh();
                        com.android.oldbili.retro.net.HttpUtil.clearQuickTimeout();
                            if (mAll.size() == 0) {
                                // 登录失效/未登录：明确引导重新登录
                                if (errMsg != null && errMsg.contains("-101")) {
                                    mListPage.showError("登录已失效，请重新登录后查看消息",
                                            new Runnable() {
                                                @Override
                                                public void run() {
                                                    mActivity.startActivity(new Intent(mActivity, LoginActivity.class));
                                                }
                                            });
                                } else {
                                    // 把具体异常原因显示出来，实机截图即可诊断
                                    String reason = errMsg == null ? "" : errMsg;
                                    if (reason.length() > 60) {
                                        reason = reason.substring(0, 60);
                                    }
                                    mListPage.showError("消息加载失败（" + reason + "），点击重试",
                                            new Runnable() {
                                                @Override
                                                public void run() {
                                                    refresh();
                                                }
                                            });
                                }
                            }
                            // 有缓存就保留当前显示（没网也能看到上次的消息）
                        }
                    });
                    return;
                }
                mActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // 无论是否切 tab，都要结束刷新并清掉快速超时，否则会一直转圈
                        mListPage.finishRefresh();
                        com.android.oldbili.retro.net.HttpUtil.clearQuickTimeout();
                        if (sessionSeq != mSessionSeq || mTabIndex != TAB_PRIVATE) {
                            return; // 已切到互动消息 tab，丢弃私信结果
                        }
                        buildMergedList(followUps, chats);
                        refreshAdapter();
                        if (mAll.size() == 0) {
                            mListPage.showEmpty("还没有消息");
                        } else {
                            mListPage.showContent();
                        }
                        saveCache();
                        updateMinTime(chats);
                        mListPage.finishLoadMore(true);
                        mListPage.finishRefresh();
                    }
                });
            }
        }).start();
    }

    /** 翻页加载更早的会话 */
    private void loadMoreSessions() {
        if (mLoadingMore || mMinTime <= 0) {
            return;
        }
        mLoadingMore = true;
        final long endTs = mMinTime;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    List<MessageSession> more = MessageApi.getSessions(endTs);
                    // 有的环境对 end_ts 边界敏感，空结果时减 1 再试一次
                    if (more.size() == 0) {
                        more = MessageApi.getSessions(endTs - 1);
                    }
                    final List<MessageSession> result = more;
                    mActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mLoadingMore = false;
                            int added = 0;
                            if (result.size() > 0) {
                                // 去重后追加
                                Set<Long> have = new HashSet<Long>();
                                for (int i = 0; i < mAll.size(); i++) {
                                    have.add(mAll.get(i).uid);
                                }
                                for (int i = 0; i < result.size(); i++) {
                                    MessageSession s = result.get(i);
                                    if (!have.contains(s.uid)) {
                                        mAll.add(s);
                                        added++;
                                    }
                                }
                                updateMinTime(result);
                                refreshAdapter();
                                saveCache();
                            }
                            if (added > 0) {
                                mListPage.finishLoadMore(true);
                            } else {
                                // 服务端翻页到头/重复返回同一页：停止继续请求
                                mMinTime = 0;
                                mListPage.finishLoadMore(false);
                            }
                        }
                    });
                } catch (Throwable e) {
                    mActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mLoadingMore = false;
                            mMinTime = 0;
                            mListPage.finishLoadMore(false);
                        }
                    });
                }
            }
        }).start();
    }

    private void updateMinTime(List<MessageSession> chats) {
        long min = 0;
        for (int i = 0; i < chats.size(); i++) {
            if (chats.get(i).time > 0 && (min == 0 || chats.get(i).time < min)) {
                min = chats.get(i).time;
            }
        }
        if (min > 0) {
            mMinTime = min;
        }
    }

    // ==================== 离线缓存 ====================

    private static final String CACHE_KEY = "msg_page_cache_v3";

    private void saveCache() {
        try {
            JSONArray arr = new JSONArray();
            for (int i = 0; i < mAll.size(); i++) {
                MessageSession s = mAll.get(i);
                JSONObject o = new JSONObject();
                o.put("kind", s.kind);
                o.put("uid", s.uid);
                o.put("name", s.name);
                o.put("face", s.face);
                o.put("last", s.lastMsg);
                o.put("time", s.time);
                o.put("unread", s.unread);
                o.put("bvid", s.bvid);
                o.put("vtitle", s.videoTitle);
                o.put("vcover", s.videoCover);
                arr.put(o);
            }
            Sp.putString(CACHE_KEY, arr.toString());
        } catch (Exception ignored) {
        }
    }

    private List<MessageSession> readCache() {
        List<MessageSession> list = new ArrayList<MessageSession>();
        try {
            JSONArray arr = new JSONArray(new org.json.JSONTokener(Sp.getString(CACHE_KEY, "[]")));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) {
                    continue;
                }
                MessageSession s = new MessageSession();
                s.kind = o.optInt("kind", MessageSession.KIND_CHAT);
                s.uid = o.optLong("uid", 0);
                s.name = o.optString("name", "");
                s.face = o.optString("face", "");
                s.lastMsg = o.optString("last", "");
                s.time = o.optLong("time", 0);
                s.unread = o.optInt("unread", 0);
                s.bvid = o.optString("bvid", "");
                s.videoTitle = o.optString("vtitle", "");
                s.videoCover = o.optString("vcover", "");
                list.add(s);
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    /** 合并排序：置顶私信 → 特别关注 → 普通私信（按时间倒序） */
    private void buildMergedList(List<MessageSession> followUps, List<MessageSession> chats) {
        mAll.clear();
        // 置顶
        for (int i = 0; i < chats.size(); i++) {
            MessageSession s = chats.get(i);
            if (isTopUid(s.uid)) {
                s.top = true;
                mAll.add(s);
            }
        }
        // 特别关注
        if (followUps != null) {
            mAll.addAll(followUps);
        }
        // 普通会话
        for (int i = 0; i < chats.size(); i++) {
            MessageSession s = chats.get(i);
            if (!isTopUid(s.uid)) {
                s.top = false;
                mAll.add(s);
            }
        }
    }

    private void refreshAdapter() {
        if (mTabIndex != TAB_PRIVATE) {
            return;
        }
        mDisplayedMode = TAB_PRIVATE;
        // 记录并恢复滚动位置：从聊天页返回/后台刷新时列表不再跳到顶部
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
        mListPage.setAdapter(new ListAdapters.SessionList(mActivity, mAll));
        mListPage.finishLoadMore(false);
        if (first > 0) {
            try {
                mListPage.getListView().setSelectionFromTop(first, top);
            } catch (Exception ignored) {
            }
        }
    }

    private void deleteSession(final MessageSession session) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final int code = MessageApi.removeSession(session.uid);
                mActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // 无论接口是否成功，本地都移除，保证体验
                        mAll.remove(session);
                        refreshAdapter();
                        com.android.oldbili.retro.util.ToastUtil.show(mActivity,
                                code == 0 ? "会话已删除" : "已从列表移除");
                    }
                });
            }
        }).start();
    }

    // ==================== 本地置顶 ====================

    private static Set<Long> readTopUids() {
        Set<Long> set = new HashSet<Long>();
        try {
            JSONArray arr = new JSONArray(new org.json.JSONTokener(
                    Sp.getString("msg_top_uids", "[]")));
            for (int i = 0; i < arr.length(); i++) {
                set.add(arr.optLong(i, 0));
            }
        } catch (Exception ignored) {
        }
        return set;
    }

    private static void writeTopUids(Set<Long> set) {
        JSONArray arr = new JSONArray();
        for (Long uid : set) {
            arr.put(uid);
        }
        Sp.putString("msg_top_uids", arr.toString());
    }

    private static boolean isTopUid(long uid) {
        return readTopUids().contains(uid);
    }

    private static void setTopUid(long uid, boolean top) {
        Set<Long> set = readTopUids();
        if (top) {
            set.add(uid);
        } else {
            set.remove(uid);
        }
        writeTopUids(set);
    }

    // ==================== 消息中心 tab ====================

    private void buildTabBar() {
        try {
            LinearLayout bar = new LinearLayout(mActivity);
            bar.setOrientation(LinearLayout.HORIZONTAL);
            bar.setBackgroundColor(0xFF161616);
            String[] names = {"私信", "回复", "@我"};
            mFeedTabs = new TextView[names.length];
            for (int i = 0; i < names.length; i++) {
                final int idx = i;
                TextView tv = new TextView(mActivity);
                tv.setText(names[i]);
                tv.setTextSize(13);
                tv.setGravity(android.view.Gravity.CENTER);
                int pad = (int) (10 * mActivity.getResources().getDisplayMetrics().density);
                tv.setPadding(0, pad, 0, pad);
                tv.setLayoutParams(new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                tv.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        switchTab(idx);
                    }
                });
                mFeedTabs[i] = tv;
                bar.addView(tv);
            }
            ((LinearLayout) mView).addView(bar, 0);
            updateTabStyle();
        } catch (Throwable ignored) {
        }
    }

    private void updateTabStyle() {
        if (mFeedTabs == null) {
            return;
        }
        for (int i = 0; i < mFeedTabs.length; i++) {
            mFeedTabs[i].setTextColor(i == mTabIndex ? 0xFF33B5E5 : 0xFFB3B3B3);
        }
    }

    private void switchTab(int index) {
        if (index == mDisplayedMode) {
            return;
        }
        mDisplayedMode = index;
        mTabIndex = index;
        if (index != TAB_PRIVATE) {
            mSessionSeq++; // 作废还在飞行的私信请求
        } else {
            mFeedSeq++;    // 作废还在飞行的互动消息请求
        }
        updateTabStyle();
        if (index == TAB_PRIVATE) {
            mTabIndex = TAB_PRIVATE;
            // 立刻切回私信：先用缓存渲染，避免好几秒还显示回复列表
            if (mAll.isEmpty()) {
                java.util.List<MessageSession> cached = readCache();
                if (!cached.isEmpty()) {
                    mAll.addAll(cached);
                }
            }
            if (!mAll.isEmpty()) {
                refreshAdapter();
                mListPage.showContent();
            } else {
                mListPage.showLoading("加载中…");
            }
            refresh();
        } else {
            mFeedItems.clear();
            mFeedPage = 1;
            if (mMsgFeedAdapter != null) {
                mMsgFeedAdapter.setItems(mFeedItems);
            }
            mListPage.showLoading("加载中…");
            loadFeed(index, 1);
        }
    }

    private void loadFeed(final int kind, final int page) {
        final int seq = ++mFeedSeq;
        mFeedLoading = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                com.android.oldbili.retro.net.HttpUtil.setQuickTimeout(5000, 6000);
                try {
                    final List<com.android.oldbili.retro.model.MsgFeedItem> items =
                            com.android.oldbili.retro.api.MsgFeedApi.load(kind, page);
                    mActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (seq != mFeedSeq) {
                                return; // 已切到别的 tab，丢弃旧结果
                            }
                            mFeedLoading = false;
                            mFeedPage = page;
                            // 记录滚动位置，追加时不跳顶
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
                            List<com.android.oldbili.retro.model.MsgFeedItem> targetList =
                                    kind == com.android.oldbili.retro.api.MsgFeedApi.KIND_AT
                                            ? mAtItems : mReplyItems;
                            ListAdapters.MsgFeedList targetAdapter =
                                    kind == com.android.oldbili.retro.api.MsgFeedApi.KIND_AT
                                            ? mAtAdapter : mReplyAdapter;
                            if (page == 1) {
                                targetList.clear();
                            }
                            // 去重：B 站翻到超出范围时会重新返回第一页，导致内容循环
                            int added = 0;
                            for (int k = 0; k < items.size(); k++) {
                                com.android.oldbili.retro.model.MsgFeedItem it = items.get(k);
                                boolean dup = false;
                                for (int j = 0; j < targetList.size(); j++) {
                                    if (targetList.get(j).id == it.id) {
                                        dup = true;
                                        break;
                                    }
                                }
                                if (!dup) {
                                    targetList.add(it);
                                    added++;
                                }
                            }
                            mDisplayedMode = kind;
                            if (targetAdapter == null) {
                                targetAdapter = new ListAdapters.MsgFeedList(mActivity, targetList);
                                if (kind == com.android.oldbili.retro.api.MsgFeedApi.KIND_AT) {
                                    mAtAdapter = targetAdapter;
                                } else {
                                    mReplyAdapter = targetAdapter;
                                }
                                mListPage.setAdapter(targetAdapter);
                            } else {
                                targetAdapter.setItems(targetList);
                                mListPage.setAdapter(targetAdapter);
                            }
                            if (first > 0 && first < mFeedItems.size()) {
                                try {
                                    mListPage.getListView().setSelectionFromTop(first, top);
                                } catch (Exception ignored) {
                                }
                            }
                            boolean hasMore = added >= 20 && items.size() >= 20;
                            if (page > 1 && added == 0) {
                                hasMore = false;
                            }
                            mListPage.finishLoadMore(hasMore);
                            mListPage.finishRefresh();
                            if (targetList.isEmpty()) {
                                mListPage.showEmpty("暂无消息");
                            } else {
                                mListPage.showContent();
                            }
                            com.android.oldbili.retro.net.HttpUtil.clearQuickTimeout();
                        }
                    });
                } catch (final Exception e) {
                    mActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (seq != mFeedSeq) {
                                return;
                            }
                            mFeedLoading = false;
                            mListPage.finishLoadMore(false);
                            mListPage.finishRefresh();
                            mListPage.showEmpty("加载失败：" + e.getMessage());
                            com.android.oldbili.retro.net.HttpUtil.clearQuickTimeout();
                        }
                    });
                }
            }
        }).start();
    }

    /** 优先用接口里的 subject_id + root_id 精确定位评论 */
    private void openFeedItem(com.android.oldbili.retro.model.MsgFeedItem item) {
        if (item == null) {
            return;
        }
        if (item.sourceId > 0 && item.rootId > 0) {
            try {
                android.content.Intent intent = new android.content.Intent(
                        mActivity, CommentListActivity.class);
                intent.putExtra("oid", item.sourceId);
                intent.putExtra("highlight_rpid", item.rootId);
                intent.putExtra("highlight_sub_rpid", item.targetId);
                mActivity.startActivity(intent);
                return;
            } catch (Throwable ignored) {
            }
        }
        openFeedUri(item.uri);
    }

    /** 打开互动消息对应的视频 / 动态；能定位到具体评论时直接高亮 */
    private void openFeedUri(String uri) {
        if (uri == null || uri.length() == 0) {
            return;
        }
        try {
            // 1) 视频评论：/video/BVxxx/?comment_root_id=123 或 #reply123
            String bvid = extract(uri, "video/(BV[0-9A-Za-z]+)");
            String aid = extract(uri, "[?&]oid=(\\d+)");
            String rpid = extract(uri, "comment_root_id=(\\d+)");
            if (rpid.length() == 0) {
                rpid = extract(uri, "#reply(\\d+)");
            }
            if (rpid.length() == 0) {
                rpid = extract(uri, "comment_id=(\\d+)");
            }
            if (rpid.length() == 0) {
                rpid = extract(uri, "root_id=(\\d+)");
            }
            if (rpid.length() == 0) {
                rpid = extract(uri, "reply_id=(\\d+)");
            }
            if (rpid.length() > 0 && (bvid.length() > 0 || aid.length() > 0)) {
                long mid = aid.length() > 0 ? Long.parseLong(aid) : 0;
                android.content.Intent intent = new android.content.Intent(
                        mActivity, CommentListActivity.class);
                if (mid > 0) {
                    intent.putExtra("oid", mid);
                } else {
                    // 只有 bvid：交给评论页自己解析成 aid
                    intent.putExtra("bvid", bvid);
                }
                intent.putExtra("highlight_rpid", Long.parseLong(rpid));
                mActivity.startActivity(intent);
                return;
            }
            // 2) 其它（动态/番剧等）走通用链接打开
            android.content.Intent intent = new android.content.Intent(
                    android.content.Intent.ACTION_VIEW, android.net.Uri.parse(uri));
            intent.setClass(mActivity, UrlOpenActivity.class);
            mActivity.startActivity(intent);
        } catch (Throwable ignored) {
        }
    }

    private static String extract(String text, String regex) {
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(regex).matcher(text);
            if (m.find()) {
                return m.group(1);
            }
        } catch (Throwable ignored) {
        }
        return "";
    }
}
