package com.android.oldbili.retro;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;

import com.android.oldbili.retro.api.BiliApi;
import com.android.oldbili.retro.api.UserApi;
import com.android.oldbili.retro.model.VideoCard;
import com.android.oldbili.retro.net.HttpUtil;
import com.android.oldbili.retro.widget.ListAdapters;
import com.android.oldbili.retro.widget.ListPageActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * UP 主空间附属列表：点赞的视频 / 投币的视频（共用列表页）。
 */
public class SpaceListActivity extends ListPageActivity {

    public static final int KIND_LIKE = 1;
    public static final int KIND_COIN = 2;

    private int mKind;
    private long mVmid;
    private final List<VideoCard> mVideos = new ArrayList<VideoCard>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mKind = getIntent().getIntExtra("kind", KIND_LIKE);
        mVmid = getIntent().getLongExtra("vmid", 0);
        String name = getIntent().getStringExtra("name");
        setTitle((mKind == KIND_COIN ? "投币视频" : "点赞视频")
                + (name != null && name.length() > 0 ? " · " + name : ""));

        mPage.setOnItemClick(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                int idx = position - mPage.getListView().getHeaderViewsCount();
                if (idx < 0 || idx >= mVideos.size()) {
                    return;
                }
                VideoCard card = mVideos.get(idx);
                    if (card == null || !card.isPlayable()) {
                        toast("该视频已失效");
                        return;
                    }
                Intent intent = new Intent(SpaceListActivity.this, VideoDetailActivity.class);
                if (card.bvid != null && card.bvid.length() > 0) {
                    intent.putExtra("bvid", card.bvid);
                } else {
                    intent.putExtra("aid", card.aid);
                }
                startActivity(intent);
            }
        });
        startLoad();
    }

    @Override
    protected void loadData(final int page) {
        if (!HttpUtil.isLoggedIn()) {
            mPage.showEmpty("登录后才能查看点赞/投币记录");
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<VideoCard> videos = mKind == KIND_COIN
                            ? UserApi.getCoinVideos(mVmid, page)
                            : UserApi.getLikeVideos(mVmid, page);
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            if (page == 1) {
                                mVideos.clear();
                            }
                            mVideos.addAll(videos);
                            finishLoad(new ListAdapters.VideoList(SpaceListActivity.this, mVideos),
                                    mVideos.size(), videos.size() >= 20);
                            // 点赞/投币列表不带播放弹幕数：后台逐个补齐统计
                            fillStats(videos);
                        }
                    });
                } catch (final Exception e) {
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            String msg = e.getMessage();
                            if (msg != null && msg.contains("53013")) {
                                msg = "该用户未公开点赞/投币列表（隐私设置）";
                            } else if (msg != null && (msg.contains("-412") || msg.contains("-352"))) {
                                msg = "接口受限（风控），请稍后重试";
                            } else if (msg != null && msg.contains("-101")) {
                                msg = "接口受限（未登录或登录过期）";
                            }
                            failLoad(msg);
                        }
                    });
                }
            }
        }).start();
    }

    /** 后台逐个补齐播放/弹幕数（接口 list 里没有），完成后刷新列表 */
    private void fillStats(final List<VideoCard> batch) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean changed = false;
                for (int i = 0; i < batch.size(); i++) {
                    final VideoCard card = batch.get(i);
                    if (card == null || card.aid <= 0 || card.play > 0) {
                        continue;
                    }
                    try {
                        org.json.JSONObject data = BiliApi.check(HttpUtil.getJson(
                                "https://api.bilibili.com/x/web-interface/archive/stat?aid=" + card.aid));
                        long view = data.optLong("view", 0);
                        long danmaku = data.optLong("danmaku", 0);
                        if (view > 0 || danmaku > 0) {
                            card.play = view;
                            card.danmaku = danmaku;
                            changed = true;
                        }
                        Thread.sleep(250);
                    } catch (Exception ignored) {
                    }
                }
                if (changed) {
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            mPage.setAdapter(new ListAdapters.VideoList(SpaceListActivity.this, mVideos));
                        }
                    });
                }
            }
        }).start();
    }
}
