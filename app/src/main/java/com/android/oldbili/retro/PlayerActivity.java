package com.android.oldbili.retro;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import com.android.oldbili.retro.api.BiliApi;
import com.android.oldbili.retro.api.InteractionApi;
import com.android.oldbili.retro.api.UserApi;
import com.android.oldbili.retro.api.VideoApi;
import com.android.oldbili.retro.danmaku.DanmakuHelper;
import com.android.oldbili.retro.model.VideoDetail;
import com.android.oldbili.retro.model.VideoPage;
import com.android.oldbili.retro.net.HttpUtil;
import com.android.oldbili.retro.net.ProxyStreamService;
import com.android.oldbili.retro.util.ClipboardCompat;
import com.android.oldbili.retro.util.DensityUtil;
import com.android.oldbili.retro.util.Sp;
import com.android.oldbili.retro.util.TimeUtil;

import master.flame.danmaku.ui.widget.DanmakuView;
import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;

/**
 * 内置播放器（IJK Player）。
 *
 * - 点击屏幕唤出小菜单（清晰度/横竖屏/弹幕开关/选集/点赞/投币/关注/分享/发弹幕）+ 左上返回键，3秒自动隐藏
 * - 返回键/返回手势唤出菜单；再按一次提示"再按一次退出视频"，第二次才退出
 * - 左半屏上下滑调亮度、右半屏调音量、左右滑快进快退
 * - 硬件/软件解码按解码设置；失败按设置交给 MX Player / VLC / MoboPlayer / QQ影音
 * - 陀螺仪自动横竖屏（设置里可关），也可用菜单按钮手动切换
 */
public class PlayerActivity extends Activity {

    // 视频信息
    private String mIdType = "bvid";
    private String mId = "";
    private int mPageIndex;
    private String mTitle = "";
    private boolean mIsLive;
    private long mLiveRoomId;
    private long mAvid;         // 番剧单集模式
    private long mCid;
    private long mEpId;
    private int mQualityOverride;
    private long mStartSeekMs;
    private VideoDetail mDetail;
    private int mCurrentQn = 16;

    // 播放器
    private IjkMediaPlayer mPlayer;
    private FrameLayout mSurfaceContainer;
    private SurfaceView mSurfaceView;
    private TextureView mTextureView;
    private master.flame.danmaku.controller.IDanmakuView mDanmakuView;
    private boolean mPrepared;
    private boolean mDanmakuEnabled;
    private boolean mHardwareDecode;
    private String mVideoUrl = "";
    /** 候选线路：某条不通自动换下一条 */
    private java.util.List<String> mCandidates = new java.util.ArrayList<String>();
    private int mCandidateIndex;
    private String mExternalUrl = "";

    // 控制器
    private View mController;
    private ImageButton mBtnPlay;
    private TextView mTimeText;
    private SeekBar mProgress;
    private TextView mBtnQuality;
    private TextView mLoadingText;
    private TextView mErrorText;
    private View mLoadingLayout;
    private Handler mHandler = new Handler();
    private Runnable mHideControllerRunnable;
    private boolean mExitToastShown;
    private Runnable mClearExitFlagRunnable;
    private boolean mUserSeeking;
    private boolean mLongPressSpeed;

    // 手势
    private GestureDetector mGestureDetector;
    private float mGestureStartX;
    private float mGestureStartY;
    private float mGestureStartBrightness;
    private int mGestureStartVolume;
    private int mGestureType; // 0无 1亮度 2音量 3快进
    private long mGestureStartPosition;
    private int mVideoWidth;
    private int mVideoHeight;
    private int mSarNum = 1;
    private int mSarDen = 1;

    // 自动横竖屏（陀螺仪）
    private OrientationEventListener mOrientationListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // 切清晰度重进：第一时间保持横屏（在界面创建前设置，避免先竖后横"转一下"）
        if (getIntent().getBooleanExtra("landscape", false)) {
            setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
        setContentView(R.layout.activity_player);

        mIdType = getIntent().getStringExtra("bvid") != null ? "bvid" : "aid";
        mId = mIdType.equals("bvid") ? getIntent().getStringExtra("bvid")
                : String.valueOf(getIntent().getLongExtra("aid", 0));
        mPageIndex = getIntent().getIntExtra("page", 0);
        mTitle = getIntent().getStringExtra("title");
        mIsLive = getIntent().getBooleanExtra("live", false);
        mLiveRoomId = getIntent().getLongExtra("room_id", 0);
        // 番剧单集模式：avid+cid 直达（番剧 bvid 走 wbi/playurl 只会给 360P 甚至失败）
        mAvid = getIntent().getLongExtra("avid", 0);
        mCid = getIntent().getLongExtra("cid", 0);
        mEpId = getIntent().getLongExtra("ep_id", 0);
        mQualityOverride = getIntent().getIntExtra("quality_override", 0);
        mStartSeekMs = getIntent().getLongExtra("seek_ms", 0);

        mSurfaceContainer = (FrameLayout) findViewById(R.id.surface_container);
        mController = findViewById(R.id.controller);
        mBtnPlay = (ImageButton) findViewById(R.id.btn_play);
        mTimeText = (TextView) findViewById(R.id.time_text);
        mProgress = (SeekBar) findViewById(R.id.progress);
        mBtnQuality = (TextView) findViewById(R.id.btn_quality);
        mLoadingText = (TextView) findViewById(R.id.loading_text);
        mErrorText = (TextView) findViewById(R.id.error_text);
        mLoadingLayout = findViewById(R.id.loading_layout);

        mDanmakuEnabled = Sp.getBoolean(Sp.SET_DANMAKU_DEFAULT, true);
        mHardwareDecode = Sp.getBoolean(Sp.SET_DECODER_MODE, true);

        initPlayerSurface();
        initController();
        initGestures();
        initOrientation();

        if (mTitle != null && mTitle.length() > 0) {
            setTitle(mTitle);
        }

        if (mIsLive) {
            loadLive();
        } else {
            loadVideo();
        }
    }

    // ==================== 画面与播放器 ====================

    private void initPlayerSurface() {
        // TextureView 是安卓4.0+的组件，2.x 老设备强制用 SurfaceView
        boolean useTexture = Sp.getBoolean(Sp.SET_DECODER_DISPLAY, true)
                && android.os.Build.VERSION.SDK_INT >= 14;
        if (useTexture) {
            mTextureView = new TextureView(this);
            mTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                    if (mPlayer != null) {
                        mPlayer.setSurface(new Surface(surface));
                    }
                }

                @Override
                public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                    // 容器尺寸变了重新适配比例
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            adjustSurfaceSize();
                        }
                    });
                }

                @Override
                public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                    return false;
                }

                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture surface) {
                }
            });
            mSurfaceContainer.addView(mTextureView, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        } else {
            mSurfaceView = new SurfaceView(this);
            mSurfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(SurfaceHolder holder) {
                    if (mPlayer != null) {
                        mPlayer.setDisplay(holder);
                    }
                }

                @Override
                public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                    // surface 尺寸变化（首播 16:9 初始布局 → 视频比例精确布局）后重新挂显示
                    if (mPlayer != null) {
                        mPlayer.setDisplay(holder);
                    }
                }

                @Override
                public void surfaceDestroyed(SurfaceHolder holder) {
                }
            });
            // 首播初始布局：按当前方向给合理初始值，避免首播画面拉伸
            // （竖屏 16:9 贴顶；横屏先全屏，视频尺寸回调后再按真实比例精确调整）
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            boolean landscapeNow =
                    getResources().getConfiguration().orientation
                            == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
            FrameLayout.LayoutParams lp;
            if (landscapeNow) {
                lp = new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
                lp.gravity = android.view.Gravity.CENTER;
            } else {
                int sw = Math.min(dm.widthPixels, dm.heightPixels);
                int initialH = sw * 9 / 16;
                lp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, initialH);
                lp.gravity = android.view.Gravity.TOP;
            }
            mSurfaceContainer.addView(mSurfaceView, lp);
        }

        // 弹幕层（DanmakuFlameMaster）
        // 4.0+ 用 TextureView 硬件加速绘制（60fps 顺滑，高分屏不卡）；
        // 2.3 用 SurfaceView（软件 Canvas，老设备本来就 30fps 足够）
        if (android.os.Build.VERSION.SDK_INT >= 14) {
            mDanmakuView = new master.flame.danmaku.ui.widget.DanmakuTextureView(this);
            // 注意：TextureView 不支持背景 drawable，不能 setBackgroundColor
        } else {
            mDanmakuView = new DanmakuView(this);
            ((View) mDanmakuView).setBackgroundColor(Color.TRANSPARENT);
        }
        mSurfaceContainer.addView((View) mDanmakuView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    }

    private void initPlayer() {
        mPlayer = new IjkMediaPlayer();
        setIjkOptions();
        mPlayer.setScreenOnWhilePlaying(true);
        mPlayer.setOnPreparedListener(new IMediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(IMediaPlayer mp) {
                mPrepared = true;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mLoadingLayout.setVisibility(View.GONE);
                        mPlayer.start();
                        // 音轨已就绪：延迟 350ms 再出声——视频首帧渲染需要时间，
                        // 若同步 start，声音会比画面早 0.3 秒左右（人耳明显）
                        if (mAudioReady && mAudioPlayer != null) {
                            mHandler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        if (mAudioPlayer != null) {
                                            mAudioPlayer.start();
                                            long target = mPendingSeekMs > 0 ? mPendingSeekMs
                                                    : (mStartSeekMs > 0 ? mStartSeekMs : 0);
                                            if (target > 0) {
                                                mAudioPlayer.seekTo((int) target);
                                            }
                                        }
                                    } catch (Exception ignored) {
                                    }
                                }
                            }, 350);
                        }
                        // 切清晰度重进带来的起始进度（recreate 时透传）
                        if (mPendingSeekMs < 0 && mStartSeekMs > 0 && !mIsLive) {
                            mPendingSeekMs = mStartSeekMs;
                        }
                        // 从历史位置/切换清晰度/切分P后恢复进度
                        if (mPendingSeekMs > 0 && !mIsLive) {
                            long target = mPendingSeekMs;
                            mPendingSeekMs = -1;
                            if (target < mPlayer.getDuration()) {
                                mPlayer.seekTo(target);
                                if (mAudioPlayer != null) {
                                    try {
                                        mAudioPlayer.seekTo((int) target);
                                    } catch (Exception ignored) {
                                    }
                                }
                                DanmakuHelper.seekTo(mDanmakuView, target);
                            }
                        }
                        if (mIsLive) {
                            mProgress.setEnabled(false);
                        } else {
                            loadDanmaku();
                            startProgressTicker();
                        }
                        // 首播时序保险：延迟重试画面比例调整，避免"必须切一下画质才正常"
                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                adjustSurfaceSize();
                            }
                        }, 200);
                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                adjustSurfaceSize();
                            }
                        }, 800);
                        showController();
                    }
                });
            }
        });
        mPlayer.setOnErrorListener(new IMediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(IMediaPlayer mp, int what, int extra) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        onPlayError();
                    }
                });
                return true;
            }
        });
        mPlayer.setOnCompletionListener(new IMediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(IMediaPlayer mp) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (Sp.getBoolean(Sp.SET_PLAYER_LOOP, false) && !mIsLive) {
                            // 洗脑循环
                            mPlayer.seekTo(0);
                            mPlayer.start();
                            DanmakuHelper.seekTo(mDanmakuView, 0);
                        } else {
                            showController();
                            mBtnPlay.setImageResource(R.drawable.ic_play);
                        }
                    }
                });
            }
        });
        mPlayer.setOnVideoSizeChangedListener(new IMediaPlayer.OnVideoSizeChangedListener() {
            @Override
            public void onVideoSizeChanged(IMediaPlayer mp, int width, int height,
                                           int sarNum, int sarDen) {
                mVideoWidth = width;
                mVideoHeight = height;
                if (sarNum > 0 && sarDen > 0) {
                    mSarNum = sarNum;
                    mSarDen = sarDen;
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        adjustSurfaceSize();
                    }
                });
            }
        });
    }

    /** 按视频宽高比适配画面：竖屏不拉伸、横屏不变形（TextureView 用矩阵，SurfaceView 用尺寸） */
    private void adjustSurfaceSize() {
        if (mVideoWidth <= 0 || mVideoHeight <= 0) {
            return;
        }
        int cw = mSurfaceContainer.getWidth();
        int ch = mSurfaceContainer.getHeight();
        if (cw <= 0 || ch <= 0) {
            // 布局还没量好：稍后重试
            mSurfaceContainer.post(new Runnable() {
                @Override
                public void run() {
                    adjustSurfaceSize();
                }
            });
            return;
        }
        // 显示宽高（考虑像素长宽比 SAR，4:3 老片源不被算成 16:9）
        float sar = (mSarNum > 0 && mSarDen > 0) ? mSarNum * 1f / mSarDen : 1f;
        float displayW = mVideoWidth * sar;
        float displayH = mVideoHeight;
        float viewRatio = cw * 1f / ch;
        float videoRatio = displayW / displayH;
        if (mTextureView != null) {
            // TextureView：把视频内容按比例缩放到 View 内（居中，留黑边）
            android.graphics.Matrix matrix = new android.graphics.Matrix();
            float scale = videoRatio > viewRatio
                    ? cw * 1f / displayW
                    : ch * 1f / displayH;
            matrix.setScale(displayW * scale / cw, displayH * scale / ch);
            mTextureView.setTransform(matrix);
        } else if (mSurfaceView != null) {
            // SurfaceView：布局按显示比例等比缩放（至少一边铺满容器，不变形拉伸）
            float scale = Math.min(cw / displayW, ch / displayH);
            int w = Math.round(displayW * scale);
            int h = Math.round(displayH * scale);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h);
            // 竖屏贴顶（画面在上方，下方留给控制器/黑边），横屏居中
            lp.gravity = ch > cw
                    ? (android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL)
                    : android.view.Gravity.CENTER;
            mSurfaceView.setLayoutParams(lp);
        }
    }

    /** 解码设置：硬件/软件、音频输出、丢帧（参照哔哩终端安卓4.0修复版） */
    private void setIjkOptions() {
        setIjkOptions(mPlayer);
    }

    private void setIjkOptions(IjkMediaPlayer player) {
        player.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", mHardwareDecode ? 1 : 0);
        player.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "opensles",
                Sp.getBoolean(Sp.SET_DECODER_AUDIO, false) ? 1 : 0);
        player.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-auto-rotate", 1);
        player.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-handle-resolution-change", 1);
        player.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop",
                Sp.getBoolean(Sp.SET_DECODER_FRAMEDROP, true) ? 4 : -1);
        player.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 1);
        player.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "http-detect-range-support", 1);
        player.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "timeout", 15 * 1000 * 1000);
        player.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "reconnect", 1);
        // B 站流需要 Referer + 防盗链 Cookie
        String cdnCookie = HttpUtil.cdnCookie();
        StringBuilder header = new StringBuilder();
        header.append("User-Agent: ").append(HttpUtil.USER_AGENT).append("\r\n");
        header.append("Referer: https://www.bilibili.com/\r\n");
        if (cdnCookie != null && cdnCookie.length() > 0) {
            header.append("Cookie: ").append(cdnCookie).append("\r\n");
        }
        player.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "headers", header.toString());
    }

    /** 后台取 UP 的粉丝数与投稿数，显示在控制条上 */
    private void loadUpInfo(final VideoDetail detail) {
        if (detail == null || detail.upMid <= 0) {
            return;
        }
        final TextView upInfo = (TextView) findViewById(R.id.up_info);
        if (upInfo == null) {
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                String text = null;
                try {
                    com.android.oldbili.retro.model.UserInfo card = UserApi.getUserCard(detail.upMid);
                    long fans = card != null ? card.fans : 0;
                    long videos = 0;
                    try {
                        String url = BiliApi.signWbi(
                                "https://api.bilibili.com/x/space/wbi/arc/search?mid=" + detail.upMid + "&ps=1&pn=1");
                        org.json.JSONObject data = BiliApi.check(HttpUtil.getJson(url));
                        videos = data.optJSONObject("page").optLong("count", 0);
                    } catch (Exception ignored) {
                    }
                    String name = detail.upName;
                    if ((name == null || name.length() == 0) && card != null) {
                        name = card.name;
                    }
                    text = "UP：" + (name == null || name.length() == 0 ? String.valueOf(detail.upMid) : name)
                            + " · 粉丝 " + fans + " · 投稿 " + videos;
                } catch (Exception e) {
                    text = null;
                }
                final String result = text;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (result != null) {
                            upInfo.setText(result);
                            upInfo.setVisibility(View.VISIBLE);
                        }
                    }
                });
            }
        }).start();
    }

    private void loadVideo() {
        mLoadingLayout.setVisibility(View.VISIBLE);
        mLoadingText.setText(R.string.player_loading);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // 番剧单集模式：走 pgc 专用接口（支持大会员高清），bvid/普通接口只有 360P
                    if (mAvid > 0 && mCid > 0) {
                        final long cid = mCid;
                        // 新视频：重置可用清晰度并集（避免上一个视频的档位串台）
                        VideoApi.setCurrentCid(cid);
                        int defaultQn = mQualityOverride > 0 ? mQualityOverride
                                : Sp.getInt(Sp.SET_DEFAULT_QUALITY, 0);
                        // 未设置过清晰度时按安卓版本给默认：2.3~4.4=360P，5.0+=720P
                        if (defaultQn <= 0) {
                            defaultQn = com.android.oldbili.retro.util.DeviceCompat.defaultQuality();
                        }
                        // 安卓 2 隐藏 1080P60 及更高：请求上限 1080P
                        if (android.os.Build.VERSION.SDK_INT < 11 && defaultQn > 80) {
                            defaultQn = 80;
                        }
                        boolean forceDurl = Sp.getBoolean("force_durl_once", false);
                        Sp.putBoolean("force_durl_once", false);
                        Object[] play = VideoApi.getPgcPlayUrlCandidates(mAvid, cid, mEpId, defaultQn, forceDurl);
                        final java.util.List<String> candidates = (java.util.List<String>) play[0];
                        if (candidates == null || candidates.size() == 0) {
                            throw new RuntimeException("未取到播放地址");
                        }
                        final String url = candidates.get(0);
                        final String audioUrl = play.length > 5 ? String.valueOf(play[5]) : "";
                        mCurrentQn = com.android.oldbili.retro.util.NumberUtil.safeInt(String.valueOf(play[1]), defaultQn);
                        // 服务端把请求档位降级了：明确告知用户
                        if (mCurrentQn < defaultQn) {
                            final String dg = "当前仅可播放 "
                                    + BiliApi.qualityName(mCurrentQn) + "（服务端已降级）";
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    toast(dg);
                                }
                            });
                        }
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mVideoUrl = url;
                                mCandidates = candidates;
                                mCandidateIndex = 0;
                                mAudioUrl = audioUrl;
                                mBtnQuality.setText(BiliApi.qualityName(mCurrentQn));
                                startPlay(url);
                                loadDanmaku();
                            }
                        });
                        return;
                    }

                    final VideoDetail detail = mIdType.equals("bvid")
                            ? VideoApi.getDetail(mId) : VideoApi.getDetail(Long.parseLong(mId));
                    if (mPageIndex >= detail.pages.size()) {
                        mPageIndex = 0;
                    }
                    final VideoPage page = detail.pages.size() > 0 ? detail.pages.get(mPageIndex) : null;
                    final long cid = page != null ? page.cid : 0;
                    // 新视频：重置可用清晰度并集
                    VideoApi.setCurrentCid(cid);
                    int defaultQn = mQualityOverride > 0 ? mQualityOverride
                            : Sp.getInt(Sp.SET_DEFAULT_QUALITY, 0);
                    // 未设置过清晰度时按安卓版本给默认：2.3~4.4=360P，5.0+=720P
                    if (defaultQn <= 0) {
                        defaultQn = com.android.oldbili.retro.util.DeviceCompat.defaultQuality();
                    }
                    // 安卓 2 隐藏 1080P60 及更高：请求上限 1080P
                    if (android.os.Build.VERSION.SDK_INT < 11 && defaultQn > 80) {
                        defaultQn = 80;
                    }
                    mCurrentQn = defaultQn;
                    boolean forceDurl = Sp.getBoolean("force_durl_once", false);
                    Sp.putBoolean("force_durl_once", false);
                    Object[] play = VideoApi.getPlayUrlCandidatesPlay(mIdType, mId, cid, defaultQn, forceDurl);
                    final java.util.List<String> candidates = (java.util.List<String>) play[0];
                    if (candidates == null || candidates.size() == 0) {
                        throw new RuntimeException("未取到播放地址");
                    }
                    final String url = candidates.get(0);
                    final String audioUrl = play.length > 5 ? String.valueOf(play[5]) : "";
                    mCurrentQn = com.android.oldbili.retro.util.NumberUtil.safeInt(String.valueOf(play[1]), defaultQn);
                    // 服务端降级提示（同一视频短时间内重复请求 playurl 会被限流降档）
                    if (mCurrentQn < defaultQn) {
                        final String dg = "当前仅可播放 "
                                + BiliApi.qualityName(mCurrentQn) + "（服务端已降级）";
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                toast(dg);
                            }
                        });
                    }
                    // 从历史位置播放（接口会返回上次的 cid 与进度）
                    final long lastCid = Long.parseLong(String.valueOf(play[3]));
                    final long lastProgressMs = Long.parseLong(String.valueOf(play[4]));
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mDetail = detail;
                            mVideoUrl = url;
                            mCandidates = candidates;
                            mCandidateIndex = 0;
                            mAudioUrl = audioUrl;
                            loadUpInfo(detail);
                            mTitle = detail.title;
                            setTitle(detail.title);
                            mBtnQuality.setText(BiliApi.qualityName(mCurrentQn));
                            startPlay(url);
                            if (Sp.getBoolean(Sp.SET_PLAYER_FROM_LAST, true)
                                    && lastCid == cid && lastProgressMs > 3000) {
                                mPendingSeekMs = lastProgressMs;
                            }
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mLoadingLayout.setVisibility(View.GONE);
                            mErrorText.setText("视频加载失败：" + e.getMessage());
                            mErrorText.setVisibility(View.VISIBLE);
                            offerRetry();
                        }
                    });
                }
            }
        }).start();
    }

    private long mPendingSeekMs = -1;
    // dash 音轨（番剧大会员高清：画面/声音分离，双播放器同步）
    private android.media.MediaPlayer mAudioPlayer;
    private String mAudioUrl = "";

    private void startPlay(String url) {
        // 已移除本地代理转发：直接用 IJK 播放候选地址
        // （v7a/x86 的 so 已带 OPENSSL 可播 https；arm64 无 openssl 版本，只能走 http 节点）
        // 每次完整重建播放器实例：reset 复用在新系统/IJK 组合下切源会随机失败
        // （黑屏/播放失败），完整重建虽然多花 1~2 秒，但每次都能稳定出画面。
        if (mPlayer != null) {
            try {
                mPlayer.release();
            } catch (Exception ignored) {
            }
            mPlayer = null;
        }
        initPlayer();
        if (mTextureView != null && mTextureView.isAvailable()) {
            mPlayer.setSurface(new Surface(mTextureView.getSurfaceTexture()));
        }
        // SurfaceView 路线（老设备）：surface 在播放器创建前就已就绪，必须在这里补挂显示
        if (mSurfaceView != null && mSurfaceView.getHolder().getSurface().isValid()) {
            mPlayer.setDisplay(mSurfaceView.getHolder());
        }
        try {
            mPlayer.setDataSource(url);
            mPlayer.prepareAsync();
        } catch (Exception e) {
            onPlayError();
        }
        startAudioPlayer();
    }

    private int mAudioCandidateIndex;
    private boolean mAudioReady;
    private final android.media.AudioManager.OnAudioFocusChangeListener mAudioFocusListener =
            new android.media.AudioManager.OnAudioFocusChangeListener() {
                @Override
                public void onAudioFocusChange(int focusChange) {
                    // 焦点变化时跟随暂停/恢复音轨
                    try {
                        if (mAudioPlayer == null) {
                            return;
                        }
                        if (focusChange == android.media.AudioManager.AUDIOFOCUS_LOSS) {
                            mAudioPlayer.pause();
                        } else if (focusChange == android.media.AudioManager.AUDIOFOCUS_GAIN
                                && mPlayer != null && mPlayer.isPlaying()) {
                            mAudioPlayer.start();
                        }
                    } catch (Exception ignored) {
                    }
                }
            };

    /** dash 音轨播放器：与视频播放器同步（番剧/视频大会员高清需要） */
    private void startAudioPlayer() {
        releaseAudioPlayer();
        if (mAudioUrl == null || mAudioUrl.length() == 0) {
            return;
        }
        final String[] candidates = mAudioUrl.split("\n");
        mAudioCandidateIndex = 0;
        tryAudioCandidate(candidates, 0);
    }

    /** 逐个尝试音轨候选，失败自动下一个；全部失败则退回单流重播 */
    private void tryAudioCandidate(final String[] candidates, final int index) {
        if (candidates == null || index < 0 || index >= candidates.length) {
            android.util.Log.w("Player", "全部音轨候选失败，退回单流重播");
            // dash 声音通道不可用：强制单流（有音）重进一次
            Sp.putBoolean("force_durl_once", true);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Intent restart = new Intent(PlayerActivity.this, PlayerActivity.class);
                    restart.putExtras(getIntent());
                    restart.putExtra("quality_override", mCurrentQn);
                    restart.putExtra("seek_ms", mPlayer != null ? mPlayer.getCurrentPosition() : 0);
                    restart.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    finish();
                    startActivity(restart);
                    overridePendingTransition(0, 0);
                }
            });
            return;
        }
        mAudioCandidateIndex = index;
        try {
            android.media.MediaPlayer player = new android.media.MediaPlayer();
            mAudioPlayer = player;
            player.setOnPreparedListener(new android.media.MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(android.media.MediaPlayer mp) {
                    android.util.Log.i("Player", "音轨候选[" + index + "] prepared OK");
                    mAudioReady = true;
                    try {
                        android.media.AudioManager am =
                                (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
                        if (am != null) {
                            am.requestAudioFocus(mAudioFocusListener,
                                    android.media.AudioManager.STREAM_MUSIC,
                                    android.media.AudioManager.AUDIOFOCUS_GAIN);
                        }
                    } catch (Exception ignored) {
                    }
                    if (mPlayer != null && mPrepared) {
                        try {
                            mAudioPlayer.start();
                            long target = mPendingSeekMs > 0 ? mPendingSeekMs
                                    : (mStartSeekMs > 0 ? mStartSeekMs : 0);
                            if (target > 0) {
                                mAudioPlayer.seekTo((int) target);
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
            });
            player.setOnErrorListener(new android.media.MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(android.media.MediaPlayer mp, int what, int extra) {
                    android.util.Log.w("Player", "音轨候选[" + index + "]错误 what=" + what
                            + " extra=" + extra + "，试下一个");
                    releaseAudioPlayer();
                    tryAudioCandidate(candidates, index + 1);
                    return true;
                }
            });
            // 带 UA/Referer/Cookie 头（B 站 CDN 防盗链校验）；老系统用无头版
            if (android.os.Build.VERSION.SDK_INT >= 14) {
                java.util.Map<String, String> headers = new java.util.HashMap<String, String>();
                headers.put("User-Agent", HttpUtil.USER_AGENT);
                headers.put("Referer", "https://www.bilibili.com/");
                String cookie = HttpUtil.cdnCookie();
                if (cookie != null && cookie.length() > 0) {
                    headers.put("Cookie", cookie);
                }
                // 反射调用 setDataSource(String, Map)：避免低版本 SDK 编译签名差异
                java.lang.reflect.Method m = android.media.MediaPlayer.class
                        .getMethod("setDataSource", String.class, java.util.Map.class);
                m.invoke(player, candidates[index], headers);
            } else {
                player.setDataSource(candidates[index]);
            }
            player.prepareAsync();
        } catch (Exception e) {
            android.util.Log.w("Player", "音轨候选[" + index + "]异常: " + e.getMessage() + "，试下一个");
            releaseAudioPlayer();
            tryAudioCandidate(candidates, index + 1);
        }
    }

    private void releaseAudioPlayer() {
        mAudioReady = false;
        if (mAudioPlayer != null) {
            try {
                android.media.AudioManager am =
                        (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
                if (am != null) {
                    am.abandonAudioFocus(mAudioFocusListener);
                }
            } catch (Exception ignored) {
            }
            try {
                mAudioPlayer.release();
            } catch (Exception ignored) {
            }
            mAudioPlayer = null;
        }
    }

    private void loadLive() {
        mLoadingLayout.setVisibility(View.VISIBLE);
        mLoadingText.setText("加载直播间…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final String url = com.android.oldbili.retro.api.LiveApi.getLivePlayUrl(mLiveRoomId, 10000);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (url == null || url.length() == 0) {
                                mLoadingLayout.setVisibility(View.GONE);
                                mErrorText.setText("主播不在直播或获取流失败");
                                mErrorText.setVisibility(View.VISIBLE);
                                return;
                            }
                            mVideoUrl = url;
                            mBtnQuality.setText("原画");
                            startPlay(url);
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mLoadingLayout.setVisibility(View.GONE);
                            mErrorText.setText("直播加载失败");
                            mErrorText.setVisibility(View.VISIBLE);
                        }
                    });
                }
            }
        }).start();
    }

    private void loadDanmaku() {
        long cid;
        if (mAvid > 0) {
            cid = mCid;   // 番剧单集模式
        } else {
            if (mDetail == null || mDetail.pages.size() == 0) {
                return;
            }
            cid = mDetail.pages.get(mPageIndex).cid;
        }
        long durationMs = 0;
        if (mPlayer != null) {
            durationMs = mPlayer.getDuration();
        }
        DanmakuHelper.applyConfig();
        DanmakuHelper.loadDanmaku(mDanmakuView, cid, durationMs, mDanmakuEnabled,
                new DanmakuHelper.StatusListener() {
                    @Override
                    public void onStatus(final String status) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                updateDanmakuStatus(status);
                            }
                        });
                    }
                });
    }

    private TextView mDmStatusText;

    /** 屏幕内弹幕状态浮层：受设置开关控制（默认关） */
    private void updateDanmakuStatus(String status) {
        if (!Sp.getBoolean(Sp.SET_DANMAKU_DEBUG, false)) {
            if (mDmStatusText != null) {
                mDmStatusText.setVisibility(View.GONE);
            }
            return;
        }
        if (mDmStatusText == null) {
            mDmStatusText = new TextView(this);
            mDmStatusText.setTextColor(0xFFFFFFFF);
            mDmStatusText.setTextSize(10);
            mDmStatusText.setBackgroundColor(0xAA000000);
            mDmStatusText.setPadding(6, 3, 6, 3);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                    android.view.Gravity.TOP | android.view.Gravity.RIGHT);
            lp.topMargin = DensityUtil.dp2px(this, 50);
            lp.rightMargin = DensityUtil.dp2px(this, 6);
            mSurfaceContainer.addView(mDmStatusText, lp);
        }
        mDmStatusText.setText(status);
        mDmStatusText.setVisibility(View.VISIBLE);
    }

    // ==================== 控制器（小菜单） ====================

    private void initController() {
        // 顶部标题
        TextView playerTitle = (TextView) findViewById(R.id.player_title);
        if (playerTitle != null) {
            playerTitle.setText(mTitle != null ? mTitle : "");
        }
        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });
        mBtnPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                togglePlay();
            }
        });
        findViewById(R.id.btn_quality).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showQualityDialog();
            }
        });
        // 倍速
        findViewById(R.id.btn_speed).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSpeedDialog();
            }
        });
        // 弹幕设置：播放中打开，返回后实时生效（与设置页同步）
        findViewById(R.id.btn_dm_settings).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(PlayerActivity.this, DanmakuSettingsActivity.class));
            }
        });
        findViewById(R.id.btn_rotate).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleOrientation();
            }
        });
        findViewById(R.id.btn_danmaku).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleDanmaku();
            }
        });
        findViewById(R.id.btn_parts).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPartsDialog();
            }
        });
        findViewById(R.id.btn_like).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doLike();
            }
        });
        findViewById(R.id.btn_coin).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doCoin();
            }
        });
        findViewById(R.id.btn_follow).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doFollow();
            }
        });
        findViewById(R.id.btn_share).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doShare();
            }
        });
        findViewById(R.id.btn_send_danmaku).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSendDanmakuDialog();
            }
        });

        mProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && mPlayer != null && mPrepared && !mIsLive) {
                    long target = mPlayer.getDuration() * progress / 1000;
                    mTimeText.setText(TimeUtil.toTimeMs(target) + " / "
                            + TimeUtil.toTimeMs(mPlayer.getDuration()));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                mUserSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                mUserSeeking = false;
                if (mPlayer != null && mPrepared && !mIsLive) {
                    long target = mPlayer.getDuration() * seekBar.getProgress() / 1000;
                    mPlayer.seekTo(target);
                    if (mAudioPlayer != null) {
                        try {
                            mAudioPlayer.seekTo((int) target);
                        } catch (Exception ignored) {
                        }
                    }
                    DanmakuHelper.seekTo(mDanmakuView, target);
                }
            }
        });
    }

    private void toggleController() {
        if (mController.getVisibility() == View.VISIBLE) {
            hideController();
        } else {
            showController();
        }
    }

    private void showController() {
        mController.setVisibility(View.VISIBLE);
        View topBar = findViewById(R.id.top_bar);
        if (topBar != null) {
            topBar.setVisibility(View.VISIBLE);
        }
        findViewById(R.id.btn_back).setVisibility(View.VISIBLE);
        if (mHideControllerRunnable != null) {
            mHandler.removeCallbacks(mHideControllerRunnable);
        }
        mHideControllerRunnable = new Runnable() {
            @Override
            public void run() {
                hideController();
            }
        };
        // 3秒后自动隐藏
        mHandler.postDelayed(mHideControllerRunnable, 3000);
    }

    private void hideController() {
        mController.setVisibility(View.GONE);
        View topBar = findViewById(R.id.top_bar);
        if (topBar != null) {
            topBar.setVisibility(View.GONE);
        }
        findViewById(R.id.btn_back).setVisibility(View.GONE);
    }

    /** 倍速选择：0.75 / 1 / 1.25 / 1.5 / 2 */
    private void showSpeedDialog() {
        if (mPlayer == null || !mPrepared || mIsLive) {
            toast("直播暂不支持倍速");
            return;
        }
        final float[] speeds = {0.75f, 1f, 1.25f, 1.5f, 2f};
        final String[] names = {"0.75x", "1.0x", "1.25x", "1.5x", "2.0x"};
        new AlertDialog.Builder(this)
                .setTitle("播放倍速")
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            mPlayer.setSpeed(speeds[which]);
                            ((TextView) findViewById(R.id.btn_speed)).setText(names[which]);
                            toast("倍速 " + names[which]);
                        } catch (Exception e) {
                            toast("倍速切换失败");
                        }
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** 转屏（configChanges 声明后不重建 Activity）：重新按新方向适配画面 */
    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // 转屏后容器尺寸变化，延迟多次重算画面布局（SurfaceView 2.3 重点）
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                adjustSurfaceSize();
            }
        }, 100);
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                adjustSurfaceSize();
            }
        }, 400);
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                adjustSurfaceSize();
            }
        }, 1000);
    }

    private void togglePlay() {
        if (mPlayer == null || !mPrepared) {
            return;
        }
        if (mPlayer.isPlaying()) {
            mPlayer.pause();
            if (mAudioPlayer != null) {
                try {
                    mAudioPlayer.pause();
                } catch (Exception ignored) {
                }
            }
            DanmakuHelper.pause(mDanmakuView);
            mBtnPlay.setImageResource(R.drawable.ic_play);
        } else {
            mPlayer.start();
            if (mAudioPlayer != null) {
                try {
                    mAudioPlayer.start();
                } catch (Exception ignored) {
                }
            }
            DanmakuHelper.start(mDanmakuView);
            mBtnPlay.setImageResource(R.drawable.ic_pause);
        }
    }

    private void toggleOrientation() {
        // 播放器固定横屏，不再支持切换方向
        toast("播放器已固定横屏");
    }

    private void toggleDanmaku() {
        if (mIsLive) {
            toast("直播暂不支持弹幕");
            return;
        }
        mDanmakuEnabled = !mDanmakuEnabled;
        if (mDanmakuEnabled) {
            loadDanmaku();
            DanmakuHelper.start(mDanmakuView);
        } else {
            mDanmakuView.hide();
            mDanmakuView.removeAllDanmakus();
        }
        toast(mDanmakuEnabled ? R.string.player_danmaku_on : R.string.player_danmaku_off);
    }

    private long mLastAudioSync;

    private void startProgressTicker() {
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mPlayer != null && mPrepared && !mIsLive && !mUserSeeking) {
                    long pos = mPlayer.getCurrentPosition();
                    long dur = mPlayer.getDuration();
                    if (dur > 0) {
                        mProgress.setProgress((int) (pos * 1000 / dur));
                        mTimeText.setText(TimeUtil.toTimeMs(pos) + " / " + TimeUtil.toTimeMs(dur));
                    }
                    // dash 双播放器音画校准：音频超前 400ms 即回拉，低频校准防断音
                    if (mAudioPlayer != null) {
                        try {
                            long now = System.currentTimeMillis();
                            if (now - mLastAudioSync >= 6000 && mAudioPlayer.isPlaying()) {
                                long apos = mAudioPlayer.getCurrentPosition();
                                if (apos - pos > 400) {
                                    mLastAudioSync = now;
                                    mAudioPlayer.seekTo((int) pos);
                                }
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
                mHandler.postDelayed(this, 500);
            }
        }, 500);
    }

    private void showQualityDialog() {
        if (mIsLive) {
            toast("当前没有可选清晰度");
            return;
        }
        // 按"播放流格式"给出清晰度：
        // DASH（默认）：360/480/720/1080/1080P60/2K（安卓 2 隐藏 1080P60 及更高）
        // MP4：单流档位 360/480/720/1080（番剧单流只有 360/720，由服务端自动降级）
        boolean mp4 = "mp4".equals(Sp.getString(Sp.SET_STREAM_FORMAT, "dash"));
        boolean ancient = android.os.Build.VERSION.SDK_INT < 11;
        final java.util.List<Integer> list = new java.util.ArrayList<Integer>();
        list.add(16);
        list.add(32);
        list.add(64);
        list.add(80);
        if (!mp4 && !ancient) {
            list.add(116);
            list.add(120);
        }
        // 按服务端实际可用档位过滤（B 站已对多数视频下架 480P（32）等档位，
        // 选了只会被降级到 360P）：保留当前正在播放的档位，避免列表为空
        if (VideoApi.sLastAcceptQuality != null && VideoApi.sLastAcceptQuality.length() > 0) {
            String[] accept = VideoApi.sLastAcceptQuality.split(",");
            java.util.List<Integer> filtered = new java.util.ArrayList<Integer>();
            for (int i = 0; i < list.size(); i++) {
                int q = list.get(i);
                for (int j = 0; j < accept.length; j++) {
                    if (accept[j].trim().equals(String.valueOf(q))) {
                        filtered.add(q);
                        break;
                    }
                }
            }
            if (mCurrentQn > 0 && !filtered.contains(mCurrentQn)) {
                filtered.add(mCurrentQn);
                java.util.Collections.sort(filtered);
            }
            if (filtered.size() > 0) {
                list.clear();
                list.addAll(filtered);
            }
        }
        String[] names = new String[list.size()];
        for (int i = 0; i < list.size(); i++) {
            names[i] = BiliApi.qualityName(list.get(i));
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.player_quality)
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, final int which) {
                        switchQuality(list.get(which));
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void switchQuality(final int qn) {
        if (qn == mCurrentQn) {
            return;
        }
        mLoadingLayout.setVisibility(View.VISIBLE);
        mLoadingText.setText("切换清晰度…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final long cid = mAvid > 0 ? mCid
                            : (mDetail != null && mDetail.pages.size() > 0 ? mDetail.pages.get(mPageIndex).cid : 0);
                    Object[] play = null;
                    String lastErr = "";
                    // 番剧：pgc 通道 → 重试 → 无 ep_id → wbi 通道，层层兜底
                    if (mAvid > 0) {
                        try {
                            play = VideoApi.getPgcPlayUrlCandidates(mAvid, cid, mEpId, qn);
                        } catch (Exception e1) {
                            lastErr = e1.getMessage();
                            try {
                                Thread.sleep(600);
                                play = VideoApi.getPgcPlayUrlCandidates(mAvid, cid, mEpId, qn);
                            } catch (Exception e2) {
                                lastErr = e2.getMessage();
                                try {
                                    play = VideoApi.getPgcPlayUrlCandidates(mAvid, cid, 0, qn);
                                } catch (Exception e3) {
                                    lastErr = e3.getMessage();
                                    play = VideoApi.getPlayUrlCandidates("avid", String.valueOf(mAvid), cid, qn);
                                }
                            }
                        }
                    } else {
                        play = VideoApi.getPlayUrlCandidates(mIdType, mId, cid, qn);
                    }
                    final java.util.List<String> candidates = (java.util.List<String>) play[0];
                    if (candidates == null || candidates.size() == 0) {
                        throw new RuntimeException("未取到播放地址" + (lastErr.length() > 0 ? "：" + lastErr : ""));
                    }
                    final String url = candidates.get(0);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            long pos = mPlayer != null ? mPlayer.getCurrentPosition() : 0;
                            // 播放器内改清晰度 = 改设置默认清晰度（两处一致）
                            Sp.putInt(Sp.SET_DEFAULT_QUALITY, qn);
                            // 整个 Activity 重建：全新 surface + 全新播放器实例，画面必然恢复。
                            // 实例内换源（reset/重建）在部分流上会画面停住、进度条不动。
                            Intent restart = new Intent(PlayerActivity.this, PlayerActivity.class);
                            restart.putExtras(getIntent());
                            restart.putExtra("quality_override", qn);
                            restart.putExtra("seek_ms", pos);
                            // 保持当前横竖屏状态，不让切清晰度重置方向
                            int orient = getResources().getConfiguration().orientation;
                            restart.putExtra("landscape",
                                    orient == android.content.res.Configuration.ORIENTATION_LANDSCAPE);
                            restart.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            finish();
                            startActivity(restart);
                            overridePendingTransition(0, 0);
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mLoadingLayout.setVisibility(View.GONE);
                            toast("切换失败：" + e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }

    private void showPartsDialog() {
        if (mIsLive || mDetail == null || mDetail.pages.size() <= 1) {
            toast("这个视频没有分P");
            return;
        }
        String[] names = new String[mDetail.pages.size()];
        for (int i = 0; i < mDetail.pages.size(); i++) {
            names[i] = (i + 1) + ". " + mDetail.pages.get(i).title;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.player_parts)
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, final int which) {
                        mPageIndex = which;
                        mPendingSeekMs = -1;
                        mLoadingLayout.setVisibility(View.VISIBLE);
                        mLoadingText.setText("切换分P…");
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    final long cid = mDetail.pages.get(which).cid;
                                    Object[] play = VideoApi.getPlayUrlCandidates(mIdType, mId, cid, mCurrentQn);
                                    final java.util.List<String> candidates = (java.util.List<String>) play[0];
                                    if (candidates == null || candidates.size() == 0) {
                                        throw new RuntimeException("未取到播放地址");
                                    }
                                    final String url = candidates.get(0);
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            mCandidates = candidates;
                                            mCandidateIndex = 0;
                                            mVideoUrl = url;
                                            startPlay(url);
                                            loadDanmaku();
                                        }
                                    });
                                } catch (final Exception e) {
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            mLoadingLayout.setVisibility(View.GONE);
                                            toast("切换失败");
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

    private void showSendDanmakuDialog() {
        if (mIsLive) {
            toast("直播暂不支持发弹幕");
            return;
        }
        if (!HttpUtil.isLoggedIn()) {
            toast(R.string.need_login);
            return;
        }
        final EditText input = new EditText(this);
        input.setHint(R.string.player_send_danmaku);
        new AlertDialog.Builder(this)
                .setTitle("发弹幕")
                .setView(input)
                .setPositiveButton("发送", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        final String text = input.getText().toString().trim();
                        if (text.length() == 0) {
                            return;
                        }
                        final long cid = mDetail.pages.size() > 0 ? mDetail.pages.get(mPageIndex).cid : 0;
                        final long progress = mPlayer != null ? mPlayer.getCurrentPosition() : 0;
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    final int code = InteractionApi.sendDanmaku(cid, text, progress,
                                            mDetail.bvid, 0xFFFFFF, 1);
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            if (code == 0) {
                                                DanmakuHelper.showSelf(mDanmakuView, text, progress);
                                                toast(R.string.player_danmaku_sent);
                                            } else {
                                                toast("发送失败(" + code + ")");
                                            }
                                        }
                                    });
                                } catch (final Exception e) {
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            toast("发送失败");
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

    private void doLike() {
        if (mIsLive || mDetail == null) {
            return;
        }
        if (!HttpUtil.isLoggedIn()) {
            toast(R.string.need_login);
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final int code = InteractionApi.like(mDetail.aid, true);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            toast(code == 0 ? R.string.player_liked : R.string.player_like_failed);
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
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
        if (mIsLive || mDetail == null) {
            return;
        }
        if (!HttpUtil.isLoggedIn()) {
            toast(R.string.need_login);
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
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    toast(code == 0 ? R.string.player_coined : R.string.player_coin_failed);
                                                }
                                            });
                                        } catch (final Exception e) {
                                            runOnUiThread(new Runnable() {
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

    private void doFollow() {
        if (mIsLive || mDetail == null) {
            return;
        }
        if (!HttpUtil.isLoggedIn()) {
            toast(R.string.need_login);
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final int relation = UserApi.getRelation(mDetail.upMid);
                    // 统一语义：2=已关注（新编码），isFollowed 兼容两代
                    final boolean toFollow = !UserApi.isFollowed(relation);
                    final int code = UserApi.follow(mDetail.upMid, toFollow);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (code == 0) {
                                toast(toFollow ? R.string.follow_ok : R.string.unfollow_ok);
                            } else if (code == 22014) {
                                toast("已经关注过了");
                            } else {
                                toast(R.string.player_follow_failed);
                            }
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            toast(R.string.player_follow_failed);
                        }
                    });
                }
            }
        }).start();
    }

    private void doShare() {
        if (mDetail == null) {
            return;
        }
        String link = getString(R.string.share_url_fmt).replace("%d", String.valueOf(mDetail.aid));
        ClipboardCompat.copyOrShow(this, link, getString(R.string.link_copied));
    }

    // ==================== 失败换线路，最后交第三方播放器 ====================

    private void onPlayError() {
        mLoadingLayout.setVisibility(View.GONE);
        // 还有候选线路就自动换一条重试
        if (!mIsLive && mCandidates != null && mCandidateIndex + 1 < mCandidates.size()) {
            mCandidateIndex++;
            String next = mCandidates.get(mCandidateIndex);
            mVideoUrl = next;
            mLoadingLayout.setVisibility(View.VISIBLE);
            mLoadingText.setText("这条线路不通，正在换线 (" + (mCandidateIndex + 1) + "/"
                    + mCandidates.size() + ")…");
            mErrorText.setVisibility(View.GONE);
            startPlay(next);
            return;
        }
        mErrorText.setVisibility(View.VISIBLE);
        // 不再提供第三方播放器兜底（IJK 已足够，开关与功能一并移除）
        offerRetry();
    }

    private void offerRetry() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.player_error)
                .setMessage("重试一次？")
                .setPositiveButton(R.string.click_retry, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mErrorText.setVisibility(View.GONE);
                        if (mIsLive) {
                            loadLive();
                        } else {
                            loadVideo();
                        }
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ==================== 手势：亮度/音量/快进 ====================

    private void initGestures() {
        mGestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                toggleController();
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                togglePlay();
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                // 长按倍速（设置里开）：松开屏幕后恢复 1.0x
                if (Sp.getBoolean(Sp.SET_PLAYER_LONGCLICK, true) && mPlayer != null && mPrepared && !mIsLive) {
                    mLongPressSpeed = true;
                    mPlayer.setSpeed(2.0f);
                    toast("2倍速");
                }
            }
        });
    }

    private float mLastTouchX;
    private float mLastTouchY;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        mGestureDetector.onTouchEvent(event);
        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                mGestureType = 0;
                mGestureStartX = event.getRawX();
                mGestureStartY = event.getRawY();
                mLastTouchX = event.getRawX();
                mLastTouchY = event.getRawY();
                WindowManager.LayoutParams lp = getWindow().getAttributes();
                mGestureStartBrightness = lp.screenBrightness;
                AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
                mGestureStartVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC);
                if (mPlayer != null && mPrepared) {
                    mGestureStartPosition = mPlayer.getCurrentPosition();
                }
                break;

            case MotionEvent.ACTION_MOVE: {
                float dx = event.getRawX() - mGestureStartX;
                float dy = event.getRawY() - mGestureStartY;
                if (mGestureType == 0 && (Math.abs(dx) > 24 || Math.abs(dy) > 24)) {
                    if (Math.abs(dx) > Math.abs(dy)) {
                        mGestureType = 3; // 快进
                    } else {
                        // 左半屏亮度，右半屏音量
                        mGestureType = mGestureStartX < getResources().getDisplayMetrics().widthPixels / 2
                                ? 1 : 2;
                    }
                }
                if (mGestureType == 1) {
                    float target = mGestureStartBrightness - dy / 500f;
                    if (target < 0.01f) {
                        target = 0.01f;
                    }
                    if (target > 1f) {
                        target = 1f;
                    }
                    WindowManager.LayoutParams lp2 = getWindow().getAttributes();
                    lp2.screenBrightness = target;
                    getWindow().setAttributes(lp2);
                    mTimeText.setText("亮度 " + Math.round(target * 100) + "%");
                    showController();
                } else if (mGestureType == 2) {
                    AudioManager am2 = (AudioManager) getSystemService(AUDIO_SERVICE);
                    int max = am2.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                    int target = mGestureStartVolume - (int) (dy / 60f);
                    if (target < 0) {
                        target = 0;
                    }
                    if (target > max) {
                        target = max;
                    }
                    am2.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0);
                    mTimeText.setText("音量 " + Math.round(target * 100f / max) + "%");
                    showController();
                } else if (mGestureType == 3 && mPlayer != null && mPrepared && !mIsLive) {
                    long target = mGestureStartPosition + (long) (dx * 2000);
                    long dur = mPlayer.getDuration();
                    if (target < 0) {
                        target = 0;
                    }
                    if (target > dur) {
                        target = dur;
                    }
                    mPlayer.seekTo(target);
                    if (mAudioPlayer != null) {
                        try {
                            mAudioPlayer.seekTo((int) target);
                        } catch (Exception ignored) {
                        }
                    }
                    DanmakuHelper.seekTo(mDanmakuView, target);
                    mTimeText.setText(TimeUtil.toTimeMs(target) + " / " + TimeUtil.toTimeMs(dur));
                    showController();
                }
                break;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (mLongPressSpeed && mPlayer != null) {
                    mLongPressSpeed = false;
                    mPlayer.setSpeed(1f);
                }
                mGestureType = 0;
                break;
        }
        return true;
    }

    // ==================== 横竖屏 ====================

    private void initOrientation() {
        // 播放器固定横屏（Manifest screenOrientation=landscape）：
        // 不再监听陀螺仪、不再跟随设备方向，避免画面被转回竖屏。
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    }

    // ==================== 生命周期 ====================

    @Override
    protected void onResume() {
        super.onResume();
        if (mOrientationListener != null && Sp.getBoolean(Sp.SET_AUTO_ROTATE, true)) {
            mOrientationListener.enable();
        }
        // 从弹幕设置页返回：配置实时生效（字号/透明度/速度/行数/描边），无需重进视频
        if (mDanmakuView != null && mDanmakuView.isPrepared()) {
            try {
                DanmakuHelper.applyConfig();
                // 触发一次行重排，让新字号/行数立即套用
                mDanmakuView.seekTo(0L);
            } catch (Exception ignored) {
            }
        }
        if (mPlayer != null && mPrepared && !mPlayer.isPlaying()) {
            // 手动暂停过就不自动续播
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mOrientationListener != null) {
            mOrientationListener.disable();
        }
        reportProgress();
        if (mPlayer != null && mPrepared && mPlayer.isPlaying()) {
            if (!Sp.getBoolean(Sp.SET_PLAYER_BACKGROUND, false) || isFinishing()) {
                mPlayer.pause();
                if (mAudioPlayer != null) {
                    try {
                        mAudioPlayer.pause();
                    } catch (Exception ignored) {
                    }
                }
                DanmakuHelper.pause(mDanmakuView);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mHandler.removeCallbacksAndMessages(null);
        if (mOrientationListener != null) {
            mOrientationListener.disable();
        }
        DanmakuHelper.release(mDanmakuView);
        if (mPlayer != null) {
            mPlayer.release();
            mPlayer = null;
        }
        releaseAudioPlayer();
    }

    private void reportProgress() {
        if (mIsLive || mDetail == null || mDetail.pages.size() == 0 || mPlayer == null || !mPrepared) {
            return;
        }
        final long cid = mDetail.pages.get(mPageIndex).cid;
        final int progress = (int) (mPlayer.getCurrentPosition() / 1000);
        if (progress < 3) {
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                com.android.oldbili.retro.api.HistoryApi.report(mDetail.aid, cid, progress);
            }
        }).start();
    }

    @Override
    public void onBackPressed() {
        // 返回键/返回手势：先唤出小菜单；菜单在时第一次提示"再按一次退出视频"
        if (mController.getVisibility() != View.VISIBLE) {
            showController();
            return;
        }
        if (!mExitToastShown) {
            mExitToastShown = true;
            toast(R.string.player_exit_tip);
            mClearExitFlagRunnable = new Runnable() {
                @Override
                public void run() {
                    mExitToastShown = false;
                }
            };
            mHandler.postDelayed(mClearExitFlagRunnable, 2000);
            return;
        }
        super.onBackPressed();
    }

    private void toast(String text) {
        com.android.oldbili.retro.util.ToastUtil.show(this, text);
    }

    private void toast(int resId) {
        toast(getString(resId));
    }
}
