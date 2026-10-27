package com.android.oldbili.retro;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.android.oldbili.retro.api.BiliApi;
import com.android.oldbili.retro.api.FavoriteApi;
import com.android.oldbili.retro.api.HistoryApi;
import com.android.oldbili.retro.api.InteractionApi;
import com.android.oldbili.retro.api.UserApi;
import com.android.oldbili.retro.api.VideoApi;
import com.android.oldbili.retro.download.DownloadManager;
import com.android.oldbili.retro.download.DownloadService;
import com.android.oldbili.retro.model.Collection;
import com.android.oldbili.retro.model.FavoriteFolder;
import com.android.oldbili.retro.model.UserInfo;
import com.android.oldbili.retro.model.VideoCard;
import com.android.oldbili.retro.model.VideoDetail;
import com.android.oldbili.retro.net.HttpUtil;
import com.android.oldbili.retro.net.ImageLoader;
import com.android.oldbili.retro.util.ClipboardCompat;
import com.android.oldbili.retro.util.DensityUtil;
import com.android.oldbili.retro.util.NumberUtil;
import com.android.oldbili.retro.util.Sp;
import com.android.oldbili.retro.util.TimeUtil;
import com.android.oldbili.retro.widget.ListHolders;

import java.util.ArrayList;
import java.util.List;

/**
 * 视频详情页：封面播放、数据、点赞投币收藏、稍后再看、分享（复制链接）、下载、
 * 选集(分P)/合集、UP主（点头像进个人主页，可关注）、简介、相关推荐；
 * 评论是独立页面（老版B站就这样）。
 */
public class VideoDetailActivity extends BaseActivity {

    private VideoDetail mDetail;
    private int mSelectedPage;
    // 番剧模式
    private long mSeasonId;
    private int mSeasonType;
    private int mSeasonEpIndex;
    private final List<com.android.oldbili.retro.api.BangumiApi.BangumiEpisode> mSeasonEps =
            new ArrayList<com.android.oldbili.retro.api.BangumiApi.BangumiEpisode>();
    private boolean mLoaded;
    private int mFollowRelation = 0;
    private int mFollowBeRelation = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_detail);
        load();
    }

    private void load() {
        setTitle("视频");
        // 番剧模式：season_id + type，复用视频详情整套 UI
        final long seasonId = getIntent().getLongExtra("season_id", 0);
        if (seasonId > 0) {
            mSeasonId = seasonId;
            mSeasonType = getIntent().getIntExtra("type", BangumiListActivity.TYPE_BANGUMI);
            mSeasonEpIndex = getIntent().getIntExtra("ep", 0);
            loadSeason();
            return;
        }
        // 外部 B 站链接（http/https VIEW）直达：从 data Uri 提取视频号
        final android.net.Uri data = getIntent().getData();
        if (data != null) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    parseExternalLink(data);
                }
            }).start();
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String bvid = getIntent().getStringExtra("bvid");
                    final VideoDetail detail;
                    if (bvid != null && bvid.length() > 0) {
                        detail = VideoApi.getDetail(bvid);
                    } else {
                        // aid 可能以 String 传入（搜索直达），兼容两种类型
                        long aid = getIntent().getLongExtra("aid", 0);
                        if (aid == 0) {
                            String aidStr = getIntent().getStringExtra("aid");
                            if (aidStr != null && aidStr.length() > 0) {
                                try {
                                    aid = Long.parseLong(aidStr.trim());
                                } catch (NumberFormatException ignored) {
                                }
                            }
                        }
                        if (aid == 0) {
                            throw new RuntimeException("缺少视频号");
                        }
                        detail = VideoApi.getDetail(aid);
                    }
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            mDetail = detail;
                            mLoaded = true;
                            fill();
                            loadRelated();
                            loadOnline();
                            loadFollowState();
                        }
                    });
                } catch (final Exception e) {
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            toast("加载失败：" + e.getMessage());
                            finish();
                        }
                    });
                }
            }
        }).start();
    }

    /** 解析外部 B 站链接（bilibili.com / b23.tv / bili2233.cn），提取 bvid/aid 后加载 */
    private void parseExternalLink(android.net.Uri data) {
        try {
            String url = data.toString();
            // 短链先解最终地址
            if (url.contains("b23.tv") || url.contains("bili2233.cn")) {
                url = com.android.oldbili.retro.net.HttpUtil.resolveFinalUrl(url);
            }
            String bvid = null;
            long aid = 0;
            // /video/BVxxxx 或 ?bvid=BVxxxx
            int bvIdx = url.indexOf("BV");
            if (bvIdx > 0) {
                String sub = url.substring(bvIdx);
                int end = sub.length();
                for (int i = 0; i < sub.length(); i++) {
                    char c = sub.charAt(i);
                    if (!(Character.isLetterOrDigit(c))) {
                        end = i;
                        break;
                    }
                }
                bvid = sub.substring(0, end);
                if (bvid.length() >= 10) {
                    bvid = bvid.substring(0, 10);
                }
            } else {
                int avIdx = url.indexOf("av");
                if (avIdx > 0) {
                    String sub = url.substring(avIdx + 2);
                    StringBuilder num = new StringBuilder();
                    for (int i = 0; i < sub.length(); i++) {
                        if (Character.isDigit(sub.charAt(i))) {
                            num.append(sub.charAt(i));
                        } else {
                            break;
                        }
                    }
                    if (num.length() > 0) {
                        aid = Long.parseLong(num.toString());
                    }
                }
            }
            if (bvid == null && aid == 0) {
                throw new RuntimeException("链接里没有找到视频号");
            }
            final String fBvid = bvid;
            final long fAid = aid;
            runOnUi(new Runnable() {
                @Override
                public void run() {
                    // 把解析结果塞回 intent 并清掉 data（避免再次走外链解析），走正常加载流程
                    if (fBvid != null) {
                        getIntent().putExtra("bvid", fBvid);
                    } else {
                        getIntent().putExtra("aid", fAid);
                    }
                    getIntent().setData(null);
                    load();
                }
            });
        } catch (final Exception e) {
            runOnUi(new Runnable() {
                @Override
                public void run() {
                    toast("链接解析失败：" + e.getMessage());
                    finish();
                }
            });
        }
    }

    /** 番剧模式加载：选集列表 + 当前集的完整数据（stats/评论/赞币收藏全有） */
    private void loadSeason() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final Object[] detail = com.android.oldbili.retro.api.BangumiApi.getSeasonDetail(mSeasonId);
                    @SuppressWarnings("unchecked")
                    final List<com.android.oldbili.retro.api.BangumiApi.BangumiEpisode> eps =
                            (List<com.android.oldbili.retro.api.BangumiApi.BangumiEpisode>) detail[2];
                    if (eps.size() == 0) {
                        throw new RuntimeException("该番剧没有可播放的剧集");
                    }
                    if (mSeasonEpIndex < 0 || mSeasonEpIndex >= eps.size()) {
                        mSeasonEpIndex = 0;
                    }
                    mSeasonEps.clear();
                    mSeasonEps.addAll(eps);
                    // 当前集的数据走 view 接口（播放/弹幕/点赞/投币/评论数全套）
                    final VideoDetail vd = eps.get(mSeasonEpIndex).aid > 0
                            ? VideoApi.getDetail(eps.get(mSeasonEpIndex).aid) : null;
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            if (vd == null) {
                                toast("该集暂不可用");
                                finish();
                                return;
                            }
                            mDetail = vd;
                            mLoaded = true;
                            fill();
                            loadFollowState();
                            loadOnline();
                        }
                    });
                } catch (final Exception e) {
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            toast("加载失败：" + e.getMessage());
                            finish();
                        }
                    });
                }
            }
        }).start();
    }

    /** 切换番剧集数：重新拉当前集数据并刷新 */
    private void switchSeasonEp(final int index) {
        if (index < 0 || index >= mSeasonEps.size() || index == mSeasonEpIndex) {
            return;
        }
        mSeasonEpIndex = index;
        final com.android.oldbili.retro.api.BangumiApi.BangumiEpisode ep = mSeasonEps.get(index);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final VideoDetail vd = ep.aid > 0 ? VideoApi.getDetail(ep.aid) : null;
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            if (vd == null) {
                                toast("该集暂不可用");
                                return;
                            }
                            mDetail = vd;
                            fill();
                            updatePartsStyle();
                        }
                    });
                } catch (final Exception e) {
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            toast("加载失败：" + e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }

    /** 番剧追番/取消追番 */
    private void toggleSeasonFollow(final Button btn) {
        if (!isLoggedIn()) {
            toast(R.string.need_login);
            startActivity(new Intent(this, LoginActivity.class));
            return;
        }
        final boolean isTv = mSeasonType == BangumiListActivity.TYPE_TV;
        final boolean toFollow = btn.getText().toString().startsWith("追");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final int code = com.android.oldbili.retro.api.BangumiApi.followSeason(
                            mSeasonId, mSeasonType, toFollow);
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            if (code == 0) {
                                // 本地记录追番状态：重进页面直接显示已追番/已追剧
                                Sp.putBoolean("bg_follow_" + mSeasonId, toFollow);
                                btn.setText(toFollow ? (isTv ? "已追剧" : "已追番") : (isTv ? "追剧" : "追番"));
                                toast(toFollow ? (isTv ? "追剧成功" : "追番成功") : "已取消");
                            } else {
                                toast("操作失败(" + code + ")");
                            }
                        }
                    });
                } catch (final Exception e) {
                    android.util.Log.w("Bangumi", "追番异常: " + e.getMessage());
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            toast("操作失败：" + (e.getMessage() == null ? "未知原因" : e.getMessage()));
                        }
                    });
                }
            }
        }).start();
    }

    private void fill() {
        setTitle(mDetail.title);
        ImageLoader.get().load((ImageView) findViewById(R.id.cover), mDetail.cover);
        ((TextView) findViewById(R.id.title)).setText(mDetail.title);
        ((TextView) findViewById(R.id.duration_text)).setText(TimeUtil.toTime(mDetail.duration));
        ((TextView) findViewById(R.id.stats)).setText(
                NumberUtil.toWan(mDetail.view) + "播放 · " + NumberUtil.toWan(mDetail.danmaku) + "弹幕 · "
                        + NumberUtil.toWan(mDetail.like) + "赞 · " + NumberUtil.toWan(mDetail.coin) + "币 · "
                        + NumberUtil.toWan(mDetail.favorite) + "收藏 · " + NumberUtil.toWan(mDetail.reply) + "评论"
                        + (mDetail.aid > 0 ? " · av" + mDetail.aid : ""));
        ((TextView) findViewById(R.id.pubdate)).setText(mDetail.pubdateDesc);
        final TextView introView = (TextView) findViewById(R.id.intro_text);
        introView.setText(
                mDetail.desc != null && mDetail.desc.length() > 0 ? mDetail.desc : "UP主没有写简介");
        // 长按简介：确认后复制到剪贴板
        introView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                new AlertDialog.Builder(VideoDetailActivity.this)
                        .setTitle("复制简介")
                        .setMessage("确定复制视频简介吗？")
                        .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                com.android.oldbili.retro.util.ClipboardCompat.copyOrShow(
                                        VideoDetailActivity.this,
                                        introView.getText().toString(),
                                        "已复制到剪贴板");
                            }
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .show();
                return true;
            }
        });

        // UP 主
        View upRow = findViewById(R.id.up_row);
        if (mSeasonId > 0) {
            // 番剧模式：不显示 UP 行，操作行第二排的追番按钮启用（评论左边）
            upRow.setVisibility(View.GONE);
            final Button followBtn = (Button) findViewById(R.id.btn_follow_season);
            followBtn.setVisibility(View.VISIBLE);
            final boolean isTv = mSeasonType == BangumiListActivity.TYPE_TV;
            boolean followed = Sp.getBoolean("bg_follow_" + mSeasonId, false);
            followBtn.setText(followed ? (isTv ? "已追剧" : "已追番") : (isTv ? "追剧" : "追番"));
            followBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggleSeasonFollow(followBtn);
                }
            });
        } else {
            findViewById(R.id.btn_follow_season).setVisibility(View.GONE);
            ImageLoader.get().load((ImageView) findViewById(R.id.up_face),
                    ImageLoader.avatarUrl(mDetail.upFace, 96));
            ((TextView) findViewById(R.id.up_name)).setText(mDetail.upName);
            // 昵称下小字：粉丝数 · 投稿数
            final TextView upMeta = (TextView) findViewById(R.id.up_meta);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    String meta = null;
                    try {
                        com.android.oldbili.retro.model.UserInfo card = UserApi.getUserCard(mDetail.upMid);
                        long fans = card != null ? card.fans : 0;
                        long videos = 0;
                        try {
                            String url = BiliApi.signWbi(
                                    "https://api.bilibili.com/x/space/wbi/arc/search?mid=" + mDetail.upMid + "&ps=1&pn=1");
                            org.json.JSONObject data = BiliApi.check(HttpUtil.getJson(url));
                            videos = data.optJSONObject("page").optLong("count", 0);
                        } catch (Exception ignored) {
                        }
                        meta = "粉丝 " + fans + " · 投稿 " + videos;
                    } catch (Exception ignored) {
                    }
                    final String result = meta;
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            if (result != null) {
                                upMeta.setText(result);
                                upMeta.setVisibility(View.VISIBLE);
                            }
                        }
                    });
                }
            }).start();
            upRow.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openUser(mDetail.upMid, mDetail.upName);
                }
            });
            ((Button) findViewById(R.id.btn_follow)).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggleFollow();
                }
            });
        }

        // 播放
        findViewById(R.id.play_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                play(mSelectedPage);
            }
        });
        findViewById(R.id.cover).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                play(mSelectedPage);
            }
        });

        // 操作行
        ((Button) findViewById(R.id.btn_like)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doLike();
            }
        });
        ((Button) findViewById(R.id.btn_coin)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doCoin();
            }
        });
        ((Button) findViewById(R.id.btn_fav)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doFavorite();
            }
        });
        ((Button) findViewById(R.id.btn_watchlater)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doWatchLater();
            }
        });
        ((Button) findViewById(R.id.btn_share)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doShare();
            }
        });
        // 评论区入口
        ((Button) findViewById(R.id.btn_comment)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mDetail == null) {
                    return;
                }
                Intent intent = new Intent(VideoDetailActivity.this, CommentListActivity.class);
                intent.putExtra("oid", mDetail.aid);
                intent.putExtra("type", 1);
                startActivity(intent);
            }
        });
        ((Button) findViewById(R.id.btn_download)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doDownload();
            }
        });

        // 选集（番剧分集 / 视频分P）
        if (mSeasonId > 0 && mSeasonEps.size() > 0) {
            findViewById(R.id.parts_section).setVisibility(View.VISIBLE);
            ((TextView) findViewById(R.id.parts_title)).setText("选集（共 " + mSeasonEps.size() + " 集）");
            LinearLayout container = (LinearLayout) findViewById(R.id.parts_container);
            container.removeAllViews();
            for (int i = 0; i < mSeasonEps.size(); i++) {
                final int index = i;
                com.android.oldbili.retro.api.BangumiApi.BangumiEpisode ep = mSeasonEps.get(i);
                TextView tv = new TextView(this);
                String epName = ep.longTitle != null && ep.longTitle.length() > 0
                        ? ep.longTitle : ep.title;
                if (ep.badge != null && ep.badge.length() > 0) {
                    epName = epName + "（" + ep.badge + "）";
                }
                tv.setText((i + 1) + ". " + epName);
                tv.setTextColor(i == mSeasonEpIndex ? 0xFF33B5E5 : 0xFFB3B3B3);
                tv.setTextSize(13);
                tv.setPadding(DensityUtil.dp2px(this, 10), DensityUtil.dp2px(this, 8),
                        DensityUtil.dp2px(this, 10), DensityUtil.dp2px(this, 8));
                tv.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mSelectedPage = index;
                        updatePartsStyle();
                        switchSeasonEp(index);
                    }
                });
                container.addView(tv);
            }
        } else if (mDetail.partCount() > 1) {
            findViewById(R.id.parts_section).setVisibility(View.VISIBLE);
            ((TextView) findViewById(R.id.parts_title)).setText(
                    getString(R.string.video_parts).replace("%d", String.valueOf(mDetail.partCount())));
            LinearLayout container = (LinearLayout) findViewById(R.id.parts_container);
            for (int i = 0; i < mDetail.pages.size(); i++) {
                final int index = i;
                TextView tv = new TextView(this);
                tv.setText((i + 1) + ". " + mDetail.pages.get(i).title);
                tv.setTextColor(index == 0 ? 0xFF33B5E5 : 0xFFB3B3B3);
                tv.setTextSize(13);
                tv.setPadding(DensityUtil.dp2px(this, 10), DensityUtil.dp2px(this, 8),
                        DensityUtil.dp2px(this, 10), DensityUtil.dp2px(this, 8));
                tv.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mSelectedPage = index;
                        updatePartsStyle();
                        play(index);
                    }
                });
                container.addView(tv);
            }
        } else if (mSeasonId > 0) {
            findViewById(R.id.parts_section).setVisibility(View.GONE);
        }

        // 合集：默认折叠，只显示一行标题；点击打开完整合集列表（可包含几百段）
        if (mDetail.isCollection()) {
            findViewById(R.id.collection_section).setVisibility(View.VISIBLE);
            final Collection c = mDetail.collection;
            TextView titleView = (TextView) findViewById(R.id.collection_title);
            titleView.setText(c.title + " · "
                    + getString(R.string.video_collection).replace("%d",
                    String.valueOf(c.episodes.size())) + "  ›");
            LinearLayout container = (LinearLayout) findViewById(R.id.collection_container);
            container.setVisibility(View.GONE);
            titleView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(VideoDetailActivity.this,
                            CollectionListActivity.class);
                    intent.putExtra("aid", mDetail.aid);
                    intent.putExtra("bvid", mDetail.bvid);
                    intent.putExtra("current_aid", mDetail.aid);
                    startActivity(intent);
                }
            });
        }
    }

    private void updatePartsStyle() {
        LinearLayout container = (LinearLayout) findViewById(R.id.parts_container);
        for (int i = 0; i < container.getChildCount(); i++) {
            TextView tv = (TextView) container.getChildAt(i);
            tv.setTextColor(i == mSelectedPage ? 0xFF33B5E5 : 0xFFB3B3B3);
        }
    }

    private void play(int pageIndex) {
        Intent intent = new Intent(this, PlayerActivity.class);
        if (mSeasonId > 0) {
            // 番剧分集：avid+cid 直达（bvid 走 wbi/playurl 只会给 360P）
            if (pageIndex >= 0 && pageIndex < mSeasonEps.size()) {
                com.android.oldbili.retro.api.BangumiApi.BangumiEpisode ep = mSeasonEps.get(pageIndex);
                intent.putExtra("bvid", ep.bvid);
                intent.putExtra("avid", ep.aid);
                intent.putExtra("cid", ep.cid);
                intent.putExtra("ep_id", ep.epId);
                intent.putExtra("title", mDetail.title);
                startActivity(intent);
            }
            return;
        }
        if (mDetail.bvid != null && mDetail.bvid.length() > 0) {
            intent.putExtra("bvid", mDetail.bvid);
        } else {
            intent.putExtra("aid", mDetail.aid);
        }
        intent.putExtra("page", pageIndex);
        intent.putExtra("title", mDetail.title);
        startActivity(intent);
    }

    private void openUser(long mid, String name) {
        Intent intent = new Intent(this, UserActivity.class);
        intent.putExtra("mid", mid);
        intent.putExtra("name", name);
        startActivity(intent);
    }

    private void loadRelated() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<VideoCard> cards = VideoApi.getRelated(mDetail.aid, 1);
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            LinearLayout container = (LinearLayout) findViewById(R.id.related_container);
                            int count = Math.min(cards.size(), 10);
                            for (int i = 0; i < count; i++) {
                                final VideoCard card = cards.get(i);
                                ListHolders.VideoCardHolder holder = new ListHolders.VideoCardHolder(
                                        LayoutInflater.from(VideoDetailActivity.this), null);
                                holder.bind(card);
                                holder.itemView.setOnClickListener(new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        if (card == null || !card.isPlayable()) {
                                            toast("该视频已失效");
                                            return;
                                        }
                                        Intent intent = new Intent(VideoDetailActivity.this,
                                                VideoDetailActivity.class);
                                        if (card.bvid != null && card.bvid.length() > 0) {
                                            intent.putExtra("bvid", card.bvid);
                                        } else {
                                            intent.putExtra("aid", card.aid);
                                        }
                                        startActivity(intent);
                                    }
                                });
                                container.addView(holder.itemView);
                            }
                        }
                    });
                } catch (Exception ignored) {
                }
            }
        }).start();
    }

    private void loadOnline() {
        if (!Sp.getBoolean(Sp.SET_PLAYER_SHOW_ONLINE, false)) {
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String online = VideoApi.getOnline(mDetail.aid,
                        mDetail.pages.size() > 0 ? mDetail.pages.get(mSelectedPage).cid : 0);
                runOnUi(new Runnable() {
                    @Override
                    public void run() {
                        if (online.length() > 0) {
                            ((TextView) findViewById(R.id.watching)).setText(online + "人在看");
                        }
                    }
                });
            }
        }).start();
    }

    private void loadFollowState() {
        if (!HttpUtil.isLoggedIn()) {
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                final int[] pair = UserApi.getRelationPair(mDetail.upMid);
                runOnUi(new Runnable() {
                    @Override
                    public void run() {
                        mFollowRelation = pair[0];
                        mFollowBeRelation = pair[1];
                        updateFollowButton();
                    }
                });
            }
        }).start();
    }

    private void updateFollowButton() {
        Button btn = (Button) findViewById(R.id.btn_follow);
        // 统一语义：2=已关注（新编码）；互粉 = 已关注 且 be_relation>0
        boolean followed = UserApi.isFollowed(mFollowRelation);
        boolean mutual = followed && mFollowBeRelation > 0;
        if (mutual) {
            btn.setText(R.string.follow_each);
        } else if (followed) {
            btn.setText(R.string.followed_btn);
        } else {
            btn.setText(R.string.follow);
        }
    }

    private void toggleFollow() {
        if (!isLoggedIn()) {
            toast(R.string.need_login);
            startActivity(new Intent(this, LoginActivity.class));
            return;
        }
        final boolean toFollow = !UserApi.isFollowed(mFollowRelation);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final int code = UserApi.follow(mDetail.upMid, toFollow);
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            if (code == 0) {
                                mFollowRelation = toFollow ? 1 : 0;
                                updateFollowButton();
                                toast(toFollow ? R.string.follow_ok : R.string.unfollow_ok);
                            } else if (code == 22014) {
                                mFollowRelation = 1;
                                updateFollowButton();
                                toast("已经关注过了");
                            } else {
                                toast("操作失败(" + code + ")");
                            }
                        }
                    });
                } catch (final Exception e) {
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            toast("操作失败：" + e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }

    private void doLike() {
        if (!isLoggedIn()) {
            toast(R.string.need_login);
            startActivity(new Intent(this, LoginActivity.class));
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // 已点赞再点 = 取消点赞
                    final int has = InteractionApi.hasLiked(mDetail.aid);
                    final boolean wantLike = has != 1;
                    final int code = InteractionApi.like(mDetail.aid, wantLike);
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            if (code == 0) {
                                if (wantLike) {
                                    toast(R.string.player_liked);
                                } else {
                                    toast("已取消点赞");
                                }
                            } else {
                                toast(R.string.player_like_failed);
                            }
                        }
                    });
                } catch (final Exception e) {
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            toast(R.string.player_like_failed);
                        }
                    });
                }
            }
        }).start();
    }

    private void doCoin() {
        if (!isLoggedIn()) {
            toast(R.string.need_login);
            startActivity(new Intent(this, LoginActivity.class));
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.video_coin)
                .setItems(new String[]{"投 1 枚硬币", "投 2 枚硬币"},
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, final int which) {
                                new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            final int code = InteractionApi.coin(mDetail.aid, which + 1);
                                            runOnUi(new Runnable() {
                                                @Override
                                                public void run() {
                                                    toast(code == 0 ? R.string.player_coined : R.string.player_coin_failed);
                                                }
                                            });
                                        } catch (final Exception e) {
                                            runOnUi(new Runnable() {
                                                @Override
                                                public void run() {
                                                    toast(R.string.player_coin_failed);
                                                }
                                            });
                                        }
                                    }
                                }).start();
                            }
                        })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void doFavorite() {
        if (!isLoggedIn()) {
            toast(R.string.need_login);
            startActivity(new Intent(this, LoginActivity.class));
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<FavoriteFolder> folders = FavoriteApi.getFolders(
                            Sp.getLong(Sp.MID, 0));
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            showFavoriteDialog(folders);
                        }
                    });
                } catch (final Exception e) {
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            toast(R.string.favorite_add_failed);
                        }
                    });
                }
            }
        }).start();
    }

    private void showFavoriteDialog(final List<FavoriteFolder> folders) {
        String[] names = new String[folders.size() + 2];
        for (int i = 0; i < folders.size(); i++) {
            names[i] = folders.get(i).title + " (" + folders.get(i).count + ")";
        }
        names[folders.size()] = getString(R.string.favorite_new);
        names[folders.size() + 1] = "取消收藏（从所有收藏夹移除）";
        new AlertDialog.Builder(this)
                .setTitle("收藏到收藏夹")
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, final int which) {
                        if (which == folders.size()) {
                            // 新建收藏夹
                            final EditText input = new EditText(VideoDetailActivity.this);
                            input.setHint(R.string.favorite_new_hint);
                            new AlertDialog.Builder(VideoDetailActivity.this)
                                    .setTitle(R.string.favorite_new)
                                    .setView(input)
                                    .setPositiveButton(R.string.confirm, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface d, int w) {
                                            final String title = input.getText().toString().trim();
                                            if (title.length() == 0) {
                                                return;
                                            }
                                            new Thread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    try {
                                                        final long fid = FavoriteApi.newFolder(title);
                                                        if (fid > 0) {
                                                            doAddFav(fid);
                                                        } else {
                                                            runOnUi(new Runnable() {
                                                                @Override
                                                                public void run() {
                                                                    toast(R.string.favorite_add_failed);
                                                                }
                                                            });
                                                        }
                                                    } catch (final Exception e) {
                                                        runOnUi(new Runnable() {
                                                            @Override
                                                            public void run() {
                                                                toast(R.string.favorite_add_failed);
                                                            }
                                                        });
                                                    }
                                                }
                                            }).start();
                                        }
                                    })
                                    .setNegativeButton(R.string.cancel, null)
                                    .show();
                        } else if (which == folders.size() + 1) {
                            // 取消收藏：遍历所有收藏夹移除
                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    int ok = 0;
                                    for (int i = 0; i < folders.size(); i++) {
                                        try {
                                            if (FavoriteApi.setFav(mDetail.aid, folders.get(i).id, false) == 0) {
                                                ok++;
                                            }
                                        } catch (Exception ignored) {
                                        }
                                    }
                                    final int okCount = ok;
                                    runOnUi(new Runnable() {
                                        @Override
                                        public void run() {
                                            toast(okCount > 0 ? "已取消收藏" : "取消收藏失败");
                                        }
                                    });
                                }
                            }).start();
                        } else {
                            doAddFav(folders.get(which).id);
                        }
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void doAddFav(final long folderId) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final int code = FavoriteApi.setFav(mDetail.aid, folderId, true);
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            toast(code == 0 ? R.string.favorite_add_ok : R.string.favorite_add_failed);
                        }
                    });
                } catch (final Exception e) {
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            toast(R.string.favorite_add_failed);
                        }
                    });
                }
            }
        }).start();
    }

    private void doWatchLater() {
        if (!isLoggedIn()) {
            toast(R.string.need_login);
            startActivity(new Intent(this, LoginActivity.class));
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final int code = HistoryApi.addToView(mDetail.aid);
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            if (code == 0) {
                                toast(R.string.watchlater_add_ok);
                            } else {
                                toast("操作失败");
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

    private void doShare() {
        // 分享=复制主站链接到剪贴板：哔哩哔哩主站域名 + AV号
        String link = getString(R.string.share_url_fmt).replace("%d", String.valueOf(mDetail.aid));
        ClipboardCompat.copyOrShow(this, link, getString(R.string.link_copied));
    }

    private void doDownload() {
        if (!hasStoragePermission()) {
            requestStoragePermission();
            return;
        }
        // 画质选择 + 分P选择 + 封面选项
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(DensityUtil.dp2px(this, 12), DensityUtil.dp2px(this, 8), 0, 0);

        // 分P / 分集：仅当前 / 全部
        final RadioGroup partGroup = new RadioGroup(this);
        RadioButton rbCur = new RadioButton(this);
        rbCur.setText(mSeasonId > 0 ? "仅下载当前集" : "仅下载当前分P");
        rbCur.setId(1);
        RadioButton rbAll = new RadioButton(this);
        rbAll.setText(mSeasonId > 0 ? "下载全部分集" : "下载全部分P");
        rbAll.setId(2);
        partGroup.addView(rbCur);
        partGroup.addView(rbAll);
        rbCur.setChecked(true);
        int partCount = mSeasonId > 0 ? mSeasonEps.size() : mDetail.partCount();
        if (partCount <= 1) {
            partGroup.setVisibility(View.GONE);
        }
        content.addView(partGroup);

        // 画质
        TextView qText = new TextView(this);
        qText.setText(R.string.download_quality);
        qText.setTextColor(0xFF33B5E5);
        qText.setTextSize(14);
        content.addView(qText);
        final RadioGroup qualityGroup = new RadioGroup(this);
        int defaultQn = Sp.getInt(Sp.SET_DOWNLOAD_QUALITY, 16);
        // 全标准档位打底
        final java.util.List<Integer> qList = new java.util.ArrayList<Integer>();
        qList.addAll(java.util.Arrays.asList(16, 32, 64, 80));
        if (mDetail.qualities != null) {
            for (int i = 0; i < mDetail.qualities.size(); i++) {
                int q = mDetail.qualities.get(i);
                if (q <= 80 && !qList.contains(q)) {
                    qList.add(q);
                }
            }
        }
        for (int i = 0; i < qList.size(); i++) {
            final int qn = qList.get(i);
            RadioButton rb = new RadioButton(this);
            rb.setText(BiliApi.qualityName(qn));
            rb.setId(100 + i);
            if (qn == defaultQn) {
                rb.setChecked(true);
            }
            qualityGroup.addView(rb);
        }
        if (qualityGroup.getCheckedRadioButtonId() == -1) {
            ((RadioButton) qualityGroup.getChildAt(0)).setChecked(true);
        }
        content.addView(qualityGroup);

        // 保存封面
        final CheckBox coverCheck = new CheckBox(this);
        coverCheck.setText(R.string.download_save_cover);
        coverCheck.setChecked(Sp.getBoolean(Sp.SET_DOWNLOAD_COVER, false));
        content.addView(coverCheck);

        TextView cqText = new TextView(this);
        cqText.setText(R.string.download_cover_quality);
        cqText.setTextColor(0xFF33B5E5);
        cqText.setTextSize(14);
        content.addView(cqText);
        final RadioGroup coverLevel = new RadioGroup(this);
        coverLevel.setOrientation(RadioGroup.HORIZONTAL);
        final String[] levels = {"低", "中", "高"};
        for (int i = 0; i < levels.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setText(levels[i]);
            rb.setId(200 + i);
            if (i == 1) {
                rb.setChecked(true);
            }
            coverLevel.addView(rb);
        }
        content.addView(coverLevel);

        new AlertDialog.Builder(this)
                .setTitle(R.string.video_download)
                .setView(content)
                .setPositiveButton(R.string.download, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        boolean all = partGroup.getCheckedRadioButtonId() == 2;
                        int qnIdx = qualityGroup.getCheckedRadioButtonId() - 100;
                        if (qnIdx < 0 || qnIdx >= qList.size()) {
                            qnIdx = 0;
                        }
                        int qn = qList.get(qnIdx);
                        String qualityName = BiliApi.qualityName(qn);
                        boolean saveCover = coverCheck.isChecked();
                        int levelIdx = coverLevel.getCheckedRadioButtonId() - 200;
                        String level = levels[levelIdx >= 0 && levelIdx < 3 ? levelIdx : 1];
                        startDownload(all, qn, qualityName, saveCover, level);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void startDownload(boolean all, int qn, String qualityName, boolean saveCover, String coverLevel) {
        DownloadManager manager = DownloadManager.get(this);
        int count = 0;
        if (mSeasonId > 0) {
            // 番剧分集下载
            if (all && mSeasonEps.size() > 1) {
                for (int i = 0; i < mSeasonEps.size(); i++) {
                    com.android.oldbili.retro.api.BangumiApi.BangumiEpisode ep = mSeasonEps.get(i);
                    manager.addTask(ep.aid, ep.bvid, ep.cid,
                            mDetail.title, (i + 1) + ". " + (ep.longTitle.length() > 0
                                    ? ep.longTitle : ep.title), i, mDetail.cover,
                            qn, qualityName, saveCover, coverLevel);
                    count++;
                }
            } else {
                int idx = mSeasonEpIndex >= 0 && mSeasonEpIndex < mSeasonEps.size()
                        ? mSeasonEpIndex : 0;
                com.android.oldbili.retro.api.BangumiApi.BangumiEpisode ep = mSeasonEps.get(idx);
                manager.addTask(ep.aid, ep.bvid, ep.cid,
                        mDetail.title, (idx + 1) + ". " + (ep.longTitle.length() > 0
                                ? ep.longTitle : ep.title), idx, mDetail.cover,
                        qn, qualityName, saveCover, coverLevel);
                count = 1;
            }
        } else if (all && mDetail.partCount() > 1) {
            for (int i = 0; i < mDetail.pages.size(); i++) {
                manager.addTask(mDetail.aid, mDetail.bvid, mDetail.pages.get(i).cid,
                        mDetail.title, mDetail.pages.get(i).title, i, mDetail.cover,
                        qn, qualityName, saveCover, coverLevel);
                count++;
            }
        } else {
            int idx = mSelectedPage < mDetail.pages.size() ? mSelectedPage : 0;
            long cid = mDetail.pages.size() > 0 ? mDetail.pages.get(idx).cid : 0;
            String part = mDetail.pages.size() > 0 ? mDetail.pages.get(idx).title : "";
            manager.addTask(mDetail.aid, mDetail.bvid, cid, mDetail.title, part, idx,
                    mDetail.cover, qn, qualityName, saveCover, coverLevel);
            count = 1;
        }
        // 起后台服务挂通知
        startService(new Intent(this, DownloadService.class));
        if (count > 0) {
            toast(getString(R.string.download_started) + " (" + count + ")");
        }
    }
}
