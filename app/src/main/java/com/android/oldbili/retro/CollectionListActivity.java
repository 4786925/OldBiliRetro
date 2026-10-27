package com.android.oldbili.retro;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.android.oldbili.retro.api.VideoApi;
import com.android.oldbili.retro.model.Collection;
import com.android.oldbili.retro.model.VideoDetail;
import com.android.oldbili.retro.net.ImageLoader;
import com.android.oldbili.retro.util.FontManager;

/**
 * 合集列表：详情页点「合集（共 N 段视频）」后打开。
 * 长合集（几百段）也能流畅滚动，只有可见项才加载封面。
 */
public class CollectionListActivity extends BaseActivity {

    private long mAid;
    private String mBvid;
    private long mCurrentAid;
    private ListView mListView;
    private TextView mHeader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAid = getIntent() == null ? 0 : getIntent().getLongExtra("aid", 0);
        mBvid = getIntent() == null ? null : getIntent().getStringExtra("bvid");
        mCurrentAid = getIntent() == null ? 0 : getIntent().getLongExtra("current_aid", 0);
        setTitle(getString(R.string.collection_title));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        mHeader = new TextView(this);
        mHeader.setText("正在加载合集…");
        mHeader.setTextColor(0xFFB3B3B3);
        mHeader.setTextSize(13);
        mHeader.setPadding(dp(12), dp(10), dp(12), dp(10));
        root.addView(mHeader);
        mListView = new ListView(this);
        root.addView(mListView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        load();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void load() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final VideoDetail detail;
                    if (mAid > 0) {
                        detail = VideoApi.getDetail(mAid);
                    } else if (mBvid != null && mBvid.length() > 0) {
                        detail = VideoApi.getDetail(mBvid);
                    } else {
                        detail = null;
                    }
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            if (detail == null || !detail.isCollection()) {
                                mHeader.setText("没有找到合集信息");
                                return;
                            }
                            bind(detail);
                        }
                    });
                } catch (final Exception e) {
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            mHeader.setText("合集加载失败：" + e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }

    private void bind(VideoDetail detail) {
        final Collection c = detail.collection;
        mHeader.setText(c.title + " · 共 " + c.episodes.size() + " 段视频");
        mListView.setAdapter(new BaseAdapter() {
            @Override
            public int getCount() {
                return c.episodes.size();
            }

            @Override
            public Object getItem(int position) {
                return c.episodes.get(position);
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View row = convertView;
                if (row == null) {
                    row = getLayoutInflater().inflate(R.layout.widget_item_folder, parent, false);
                }
                final Collection.Episode ep = c.episodes.get(position);
                TextView title = (TextView) row.findViewById(R.id.title);
                TextView count = (TextView) row.findViewById(R.id.count);
                ImageView cover = (ImageView) row.findViewById(R.id.cover);
                title.setText((position + 1) + ". " + ep.title);
                FontManager.apply(title, CollectionListActivity.this);
                count.setText(ep.aid == mCurrentAid ? "正在播放" : "");
                ImageLoader.get().load(cover, ImageLoader.coverUrl(ep.cover, 160, 100));
                row.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        android.content.Intent i = new android.content.Intent(
                                CollectionListActivity.this, VideoDetailActivity.class);
                        if (ep.bvid != null && ep.bvid.length() > 0) {
                            i.putExtra("bvid", ep.bvid);
                        } else {
                            i.putExtra("aid", ep.aid);
                        }
                        startActivity(i);
                    }
                });
                return row;
            }
        });
    }
}
