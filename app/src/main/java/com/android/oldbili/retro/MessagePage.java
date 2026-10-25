package com.android.oldbili.retro;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;

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

    public View getView() {
        if (mView == null) {
            mView = LayoutInflater.from(mActivity).inflate(R.layout.widget_list_page, null);
            mListPage = new ListPage(mView);
            // 顶部下滑强制刷新
            mListPage.enablePullRefresh(new ListPage.OnRefreshListener() {
                @Override
                public void onRefresh() {
                    refresh();
                }
            });
            // 会话很多时支持翻页（几千条也能翻）
            mListPage.setOnLoadMore(new ListPage.OnLoadMoreListener() {
                @Override
                public void onLoadMore(int page) {
                    loadMoreSessions();
                }
            });
            mListPage.setOnItemClick(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    int idx = position - mListPage.getListView().getHeaderViewsCount();
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
        refresh();
    }

    /** 双击页签/下拉强制刷新 */
    public void forceRefresh() {
        refresh();
    }

    private boolean mLoadingMore;
    private long mMinTime;   // 当前已加载会话的最小时间戳（翻页游标）

    private void refresh() {
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
                                    }
                                }
                                updateMinTime(result);
                                refreshAdapter();
                                saveCache();
                                mListPage.finishLoadMore(true);
                            } else {
                                mListPage.finishLoadMore(false);
                            }
                        }
                    });
                } catch (Exception e) {
                    mActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mLoadingMore = false;
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

    private static final String CACHE_KEY = "msg_page_cache";

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
}
