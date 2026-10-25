package com.android.oldbili.retro;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import com.android.oldbili.retro.api.BangumiApi;
import com.android.oldbili.retro.net.HttpUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * 番剧详情页：简介 + 追番按钮 + 剧集列表（选集与分P一致），点集播放。
 */
public class BangumiDetailActivity extends BaseActivity {

    private long mSeasonId;
    private int mType;
    private String mTitle = "";
    private final List<BangumiApi.BangumiEpisode> mEpisodes =
            new ArrayList<BangumiApi.BangumiEpisode>();
    private Button mFollowBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSeasonId = getIntent().getLongExtra("season_id", 0);
        mType = getIntent().getIntExtra("type", BangumiListActivity.TYPE_BANGUMI);
        mTitle = getIntent().getStringExtra("title");
        setTitle(mTitle != null && mTitle.length() > 0 ? mTitle : "番剧");

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(10, 10, 10, 10);

        TextView intro = new TextView(this);
        intro.setTextColor(0xFFB0B0B0);
        intro.setTextSize(12);
        intro.setText("加载中…");
        root.addView(intro);

        mFollowBtn = new Button(this);
        mFollowBtn.setText("追番");
        mFollowBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleFollow();
            }
        });
        root.addView(mFollowBtn);

        TextView epLabel = new TextView(this);
        epLabel.setText("选集");
        epLabel.setTextColor(0xFF33B5E5);
        epLabel.setTextSize(13);
        epLabel.setPadding(0, 14, 0, 4);
        root.addView(epLabel);

        // 选集：两列网格（封面+标题），整屏铺开不挤在一起
        final android.widget.GridView epList = new android.widget.GridView(this);
        epList.setNumColumns(2);
        epList.setHorizontalSpacing(6);
        epList.setVerticalSpacing(6);
        epList.setPadding(4, 4, 4, 4);
        epList.setCacheColorHint(0x00000000);
        root.addView(epList, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.MATCH_PARENT));
        setContentView(scroll);

        final TextView introView = intro;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final Object[] detail = BangumiApi.getSeasonDetail(mSeasonId);
                    final String title = (String) detail[0];
                    final String evaluate = (String) detail[1];
                    @SuppressWarnings("unchecked")
                    final List<BangumiApi.BangumiEpisode> eps =
                            (List<BangumiApi.BangumiEpisode>) detail[2];
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            if (title.length() > 0) {
                                setTitle(title);
                                mTitle = title;
                            }
                            introView.setText(evaluate != null && evaluate.length() > 0
                                    ? evaluate : "（没有简介）");
                            mEpisodes.addAll(eps);
                            epList.setAdapter(new EpisodeAdapter(BangumiDetailActivity.this, mEpisodes));
                            epList.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
                                @Override
                                public void onItemClick(android.widget.AdapterView<?> parent,
                                                        View view, int position, long id) {
                                    if (position < 0 || position >= mEpisodes.size()) {
                                        return;
                                    }
                                    playEpisode(mEpisodes.get(position));
                                }
                            });
                        }
                    });
                } catch (final Exception e) {
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            introView.setText("加载失败：" + e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }

    /** 播放单集：会员专属提示后仍尝试播放（大会员可直接看） */
    private void playEpisode(final BangumiApi.BangumiEpisode ep) {
        final String bvid = ep.bvid;
        if (bvid == null || bvid.length() == 0) {
            toast("该集没有可用的播放地址");
            return;
        }
        final Runnable start = new Runnable() {
            @Override
            public void run() {
                Intent intent = new Intent(BangumiDetailActivity.this, PlayerActivity.class);
                intent.putExtra("bvid", bvid);
                // 番剧播放关键：avid+cid 直达（bvid 走 wbi/playurl 只会给 360P 甚至失败）
                intent.putExtra("avid", ep.aid);
                intent.putExtra("cid", ep.cid);
                intent.putExtra("title", mTitle + " " + (ep.title.length() > 0 ? ep.title : ""));
                startActivity(intent);
            }
        };
        if (ep.badge != null && ep.badge.contains("会员")) {
            new AlertDialog.Builder(this)
                    .setTitle("大会员专属")
                    .setMessage("该集为大会员专属内容，是否尝试播放？")
                    .setPositiveButton("尝试播放", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            start.run();
                        }
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        } else {
            start.run();
        }
    }

    private void toggleFollow() {
        if (!HttpUtil.isLoggedIn()) {
            toast(R.string.need_login);
            startActivity(new Intent(this, LoginActivity.class));
            return;
        }
        final boolean toFollow = "追番".equals(mFollowBtn.getText().toString())
                || "追剧".equals(mFollowBtn.getText().toString());
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final int code = BangumiApi.followSeason(mSeasonId, mType, toFollow);
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            if (code == 0) {
                                mFollowBtn.setText(toFollow ? "已追番" : "追番");
                                toast(toFollow ? "追番成功" : "已取消追番");
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

    /** 剧集网格：封面 + 标题 + 会员角标 */
    static class EpisodeAdapter extends BaseAdapter {
        private final List<BangumiApi.BangumiEpisode> mData;
        private final LayoutInflater mInflater;

        EpisodeAdapter(android.content.Context context, List<BangumiApi.BangumiEpisode> data) {
            mData = data;
            mInflater = LayoutInflater.from(context);
        }

        @Override
        public int getCount() {
            return mData.size();
        }

        @Override
        public Object getItem(int position) {
            return mData.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.widget_item_bangumi_ep, parent, false);
            }
            BangumiApi.BangumiEpisode ep = mData.get(position);
            String name = ep.longTitle != null && ep.longTitle.length() > 0
                    ? ep.longTitle : ep.title;
            ((TextView) convertView.findViewById(R.id.title)).setText(name);
            // 会员角标
            String sub = ep.badge != null && ep.badge.length() > 0 ? ep.badge : "";
            TextView badgeView = (TextView) convertView.findViewById(R.id.badge);
            if (sub.length() > 0) {
                badgeView.setVisibility(View.VISIBLE);
                badgeView.setText(sub);
            } else {
                badgeView.setVisibility(View.GONE);
            }
            if (ep.cover != null && ep.cover.length() > 0) {
                com.android.oldbili.retro.net.ImageLoader.get().load(
                        (android.widget.ImageView) convertView.findViewById(R.id.cover),
                        com.android.oldbili.retro.net.ImageLoader.coverUrl(ep.cover, 320, 180));
            }
            return convertView;
        }
    }
}
