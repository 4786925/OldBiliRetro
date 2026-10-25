package com.android.oldbili.retro;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.oldbili.retro.api.BangumiApi;
import com.android.oldbili.retro.model.BangumiCard;
import com.android.oldbili.retro.net.HttpUtil;
import com.android.oldbili.retro.util.Sp;
import com.android.oldbili.retro.widget.ListAdapters;
import com.android.oldbili.retro.widget.ListPageActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * 追番/追剧列表页。type=1 追番，type=2 追剧。
 */
public class BangumiListActivity extends ListPageActivity {

    public static final int TYPE_BANGUMI = 1;
    public static final int TYPE_TV = 2;

    private long mVmid;
    private int mType;
    private final List<BangumiCard> mCards = new ArrayList<BangumiCard>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mVmid = getIntent().getLongExtra("vmid", 0);
        mType = getIntent().getIntExtra("type", TYPE_BANGUMI);
        String name = getIntent().getStringExtra("name");
        setTitle((mType == TYPE_TV ? "追剧" : "追番")
                + (name != null && name.length() > 0 ? " · " + name : ""));

        mPage.setOnItemClick(new android.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(android.widget.AdapterView<?> parent, View view, int position, long id) {
                int idx = position - mPage.getListView().getHeaderViewsCount();
                if (idx < 0 || idx >= mCards.size()) {
                    return;
                }
                BangumiCard card = mCards.get(idx);
                // 番剧详情复用视频详情页整套 UI（选集/播放/弹幕/赞币/评论/上映时间）
                Intent intent = new Intent(BangumiListActivity.this, VideoDetailActivity.class);
                intent.putExtra("season_id", card.seasonId);
                intent.putExtra("type", mType);
                startActivity(intent);
            }
        });
        startLoad();
    }

    @Override
    protected void loadData(final int page) {
        if (!HttpUtil.isLoggedIn() && mVmid != Sp.getLong(Sp.MID, 0)) {
            // 看自己的追番需要登录；看别人的公开追番列表不需要
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<BangumiCard> cards = BangumiApi.getFollowList(mVmid, mType, page);
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            if (page == 1) {
                                mCards.clear();
                            }
                            mCards.addAll(cards);
                            // 翻页保持位置，不跳回顶部
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
                            finishLoad(new ListAdapters.BangumiList(BangumiListActivity.this, mCards),
                                    mCards.size(), cards.size() >= 15);
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

    /** 番剧卡片适配器已统一到 ListAdapters.BangumiList */
}
