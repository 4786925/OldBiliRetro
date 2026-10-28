package com.android.oldbili.retro.widget;

import android.view.View;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.android.oldbili.retro.R;
import com.android.oldbili.retro.util.DensityUtil;

/**
 * 共用列表页控制器。
 *
 * 所有"只有一个列表"的页面（稍后再看、收藏、历史、关注、下载管理、搜索结果、
 * 首页各栏目……）都套同一张布局、用同一个控制器，加载中/空/出错/上拉翻页
 * 的行为全部统一在这里，改一处全生效。
 */
public class ListPage {

    private final View mRoot;
    private final ListView mListView;
    private final ProgressBar mProgressBar;
    private final View mEmptyLayout;
    private final TextView mEmptyText;
    private final View mErrorLayout;
    private final TextView mErrorText;
    private final Button mRetryButton;
    private final TextView mFooterText;

    private OnLoadMoreListener mLoadMoreListener;
    private Runnable mRetryRunnable;
    private Runnable mEmptyClickRunnable;
    private boolean mLoadingMore;
    private boolean mHasMore = true;
    private boolean mEnableLoadMore = true;

    public interface OnLoadMoreListener {
        /** 上拉到底时回调，page 从 2 开始 */
        void onLoadMore(int page);
    }

    /** @param root 包含 widget_list_page 布局的根视图（多页签共存时按根视图取控件，避免 ID 冲突） */
    public ListPage(View root) {
        mRoot = root;
        mListView = (ListView) root.findViewById(R.id.list_view);
        // 按键手机：让列表可以获得焦点，方向键逐项移动，确认键点击
        mListView.setFocusable(true);
        mListView.setFocusableInTouchMode(false);
        mListView.setItemsCanFocus(true);
        mProgressBar = (ProgressBar) root.findViewById(R.id.progress_bar);
        mEmptyLayout = root.findViewById(R.id.empty_layout);
        mEmptyText = (TextView) root.findViewById(R.id.empty_text);
        mErrorLayout = root.findViewById(R.id.error_layout);
        mErrorText = (TextView) root.findViewById(R.id.error_text);
        mRetryButton = (Button) root.findViewById(R.id.retry_button);

        // 底部"加载更多"指示
        mFooterText = new TextView(root.getContext());
        mFooterText.setGravity(android.view.Gravity.CENTER);
        mFooterText.setTextColor(0xFF7F7F7F);
        mFooterText.setTextSize(13);
        mFooterText.setPadding(0, root.getResources().getDimensionPixelSize(R.dimen.card_padding),
                0, root.getResources().getDimensionPixelSize(R.dimen.card_padding));
        mListView.addFooterView(mFooterText, null, false);

        mListView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
            }

            @Override
            public void onScroll(AbsListView view, int firstVisible, int visibleCount, int totalCount) {
                if (!mEnableLoadMore || !mHasMore || mLoadingMore || totalCount == 0) {
                    return;
                }
                if (firstVisible + visibleCount >= totalCount - 2) {
                    triggerLoadMore();
                }
            }
        });

        mRetryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mRetryRunnable != null) {
                    mRetryRunnable.run();
                }
            }
        });

        mEmptyLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mEmptyClickRunnable != null) {
                    mEmptyClickRunnable.run();
                }
            }
        });
    }

    /** 点空提示的回调（如未登录提示点击去登录） */
    public void setOnEmptyClick(Runnable runnable) {
        mEmptyClickRunnable = runnable;
    }

    public ListView getListView() {
        return mListView;
    }

    public void setAdapter(ListAdapter adapter) {
        mListView.setAdapter(adapter);
    }

    public void setOnItemClick(AdapterView.OnItemClickListener listener) {
        mListView.setOnItemClickListener(listener);
    }

    public void setOnItemLongClick(AdapterView.OnItemLongClickListener listener) {
        mListView.setOnItemLongClickListener(listener);
    }

    public void setOnLoadMore(OnLoadMoreListener listener) {
        mLoadMoreListener = listener;
    }

    public void setEnableLoadMore(boolean enable) {
        mEnableLoadMore = enable;
        if (!enable) {
            mFooterText.setVisibility(View.GONE);
        }
    }

    /** 显示加载中（转圈） */
    /** 加载中：转圈 + 文字提示 */
    public void showLoading(String text) {
        mListView.setVisibility(View.GONE);
        mErrorLayout.setVisibility(View.GONE);
        mProgressBar.setVisibility(View.VISIBLE);
        mEmptyText.setText(text);
        mEmptyLayout.setVisibility(View.VISIBLE);
    }

    public void showLoading() {
        mListView.setVisibility(View.GONE);
        mEmptyLayout.setVisibility(View.GONE);
        mErrorLayout.setVisibility(View.GONE);
        mProgressBar.setVisibility(View.VISIBLE);
    }

    /** 显示列表内容 */
    public void showContent() {
        mProgressBar.setVisibility(View.GONE);
        mEmptyLayout.setVisibility(View.GONE);
        mErrorLayout.setVisibility(View.GONE);
        mListView.setVisibility(View.VISIBLE);
    }

    /** 显示空提示 */
    public void showEmpty(String text) {
        mProgressBar.setVisibility(View.GONE);
        mErrorLayout.setVisibility(View.GONE);
        mListView.setVisibility(View.GONE);
        mEmptyText.setText(text);
        mEmptyLayout.setVisibility(View.VISIBLE);
    }

    /** 显示出错提示，点击重试回调 */
    public void showError(String text, Runnable retry) {
        mProgressBar.setVisibility(View.GONE);
        mEmptyLayout.setVisibility(View.GONE);
        mListView.setVisibility(View.GONE);
        mErrorText.setText(text);
        mRetryRunnable = retry;
        mErrorLayout.setVisibility(View.VISIBLE);
    }

    /** 翻页结束：还有更多显示"加载更多"，否则"没有更多了" */
    public void finishLoadMore(boolean hasMore) {
        mLoadingMore = false;
        mHasMore = hasMore;
        if (hasMore) {
            mFooterText.setText(R.string.load_more);
            mFooterText.setVisibility(View.VISIBLE);
        } else {
            mFooterText.setText(R.string.no_more);
            mFooterText.setVisibility(View.VISIBLE);
        }
    }

    /** 首页数据载入完成 */
    public void finishFirstLoad(int itemCount) {
        mLoadingMore = false;
        mFooterText.setVisibility(View.GONE);
    }

    private void triggerLoadMore() {
        if (mLoadMoreListener == null) {
            return;
        }
        mLoadingMore = true;
        mFooterText.setText(R.string.loading);
        mFooterText.setVisibility(View.VISIBLE);
        final int page = currentPage() + 1;
        mRoot.post(new Runnable() {
            @Override
            public void run() {
                mLoadMoreListener.onLoadMore(page);
            }
        });
    }

    /** 由调用方维护当前页码，这里按已加载条数估算 */
    private int currentPage() {
        ListAdapter adapter = mListView.getAdapter();
        if (adapter == null) {
            return 1;
        }
        int count = adapter.getCount() - mListView.getFooterViewsCount();
        if (count <= 0) {
            return 1;
        }
        return (count + 19) / 20;
    }

    // ==================== 上滑下拉刷新（2013 风格头部提示条） ====================

    public interface OnRefreshListener {
        /** 松手触发刷新，数据回来后调用 finishRefresh() */
        void onRefresh();
    }

    private OnRefreshListener mRefreshListener;
    private TextView mRefreshHeader;
    private int mRefreshHeaderHeight;
    private float mPullStartY = -1;
    private boolean mRefreshing;
    private int mPullThreshold;

    /** 开启下拉刷新（加一个高度随下拉变化的头部提示条） */
    public void enablePullRefresh(OnRefreshListener listener) {
        mRefreshListener = listener;
        mPullThreshold = (int) (mRoot.getResources().getDisplayMetrics().density * 56);

        mRefreshHeader = new TextView(mRoot.getContext());
        mRefreshHeader.setGravity(android.view.Gravity.CENTER);
        mRefreshHeader.setTextColor(0xFF9A9A9A);
        mRefreshHeader.setTextSize(13);
        mRefreshHeader.setBackgroundColor(0xFF191919);
        mRefreshHeader.setPadding(0, DensityUtil.dp2px(mRoot.getContext(), 8), 0,
                DensityUtil.dp2px(mRoot.getContext(), 8));
        mRefreshHeader.setText(R.string.pull_refresh);
        mListView.addHeaderView(mRefreshHeader, null, false);
        setRefreshHeaderHeight(0);

        mListView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, android.view.MotionEvent ev) {
                if (mRefreshListener == null) {
                    return false;
                }
                int action = ev.getAction() & android.view.MotionEvent.ACTION_MASK;
                switch (action) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        // 列表在顶部时开始跟踪下拉
                        if (!mRefreshing && mListView.getFirstVisiblePosition() == 0) {
                            mPullStartY = ev.getY();
                        } else {
                            mPullStartY = -1;
                        }
                        break;
                    case android.view.MotionEvent.ACTION_MOVE:
                        if (mPullStartY >= 0) {
                            float dy = ev.getY() - mPullStartY;
                            if (dy > 0) {
                                int h = (int) Math.min(dy / 2.2f, mPullThreshold * 2);
                                setRefreshHeaderHeight(h);
                                mRefreshHeader.setText(h > mPullThreshold
                                        ? R.string.pull_refresh_release : R.string.pull_refresh);
                                return true;
                            }
                        }
                        break;
                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL:
                        // 只有真的拉出过头（header 高度>0）才消费事件；
                        // 普通点击放行，否则下拉刷新挂上后列表条目会点不开
                        if (mPullStartY >= 0 && mRefreshHeaderHeight > 0) {
                            boolean trigger = mRefreshHeaderHeight > mPullThreshold;
                            mPullStartY = -1;
                            if (trigger && !mRefreshing) {
                                mRefreshing = true;
                                mRefreshHeader.setText(R.string.pull_refreshing);
                                setRefreshHeaderHeight(mPullThreshold / 2);
                                mRoot.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        mRefreshListener.onRefresh();
                                    }
                                });
                            } else {
                                mRefreshHeader.setText(R.string.pull_refresh);
                                setRefreshHeaderHeight(0);
                            }
                            return true;
                        }
                        mPullStartY = -1;
                        break;
                    default:
                        break;
                }
                return false;
            }
        });
    }

    /** 刷新完成，收起头部提示条 */
    public void finishRefresh() {
        mRefreshing = false;
        mRefreshHeader.setText(R.string.pull_refresh);
        setRefreshHeaderHeight(0);
    }

    private void setRefreshHeaderHeight(int h) {
        if (mRefreshHeader == null) {
            return;
        }
        android.view.ViewGroup.LayoutParams lp = mRefreshHeader.getLayoutParams();
        if (lp == null) {
            lp = new AbsListView.LayoutParams(AbsListView.LayoutParams.MATCH_PARENT, h);
        } else {
            lp.height = h;
        }
        mRefreshHeader.setLayoutParams(lp);
        mRefreshHeaderHeight = h;
    }
}
