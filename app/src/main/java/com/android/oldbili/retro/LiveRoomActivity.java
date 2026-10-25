package com.android.oldbili.retro;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.oldbili.retro.api.LiveApi;
import com.android.oldbili.retro.danmaku.DanmakuHelper;
import com.android.oldbili.retro.model.LiveRoom;
import com.android.oldbili.retro.net.HttpUtil;
import com.android.oldbili.retro.net.ImageLoader;
import com.android.oldbili.retro.util.NumberUtil;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import master.flame.danmaku.ui.widget.DanmakuView;

/**
 * 直播间：房间信息（封面/标题/人数）+ 内置播放 + 弹幕（看/发）+ 观众列表（点头像进主页）。
 */
public class LiveRoomActivity extends BaseActivity {

    private long mRoomId;
    private long mRealRoomId;
    private LiveRoom mRoom;
    private master.flame.danmaku.controller.IDanmakuView mDanmakuView;
    private boolean mPolling = true;
    private final Set<String> mSeenTimelines = new HashSet<String>();
    private final Map<Long, String[]> mViewers = new HashMap<Long, String[]>(); // uid -> [name]
    private boolean mDanmakuPrepared;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_live_room);

        mRoomId = getIntent().getLongExtra("room_id", 0);
        String title = getIntent().getStringExtra("title");
        setTitle(title != null && title.length() > 0 ? title : "直播间");

        // 弹幕层：盖在界面上方（播放时在 PlayerActivity 内）
        final FrameLayout root = (FrameLayout) findViewById(android.R.id.content);
        // 4.0+ 用 TextureView 硬件加速绘制（60fps）；2.3 用 SurfaceView
        if (android.os.Build.VERSION.SDK_INT >= 14) {
            mDanmakuView = new master.flame.danmaku.ui.widget.DanmakuTextureView(this);
            // TextureView 不支持背景 drawable
        } else {
            mDanmakuView = new DanmakuView(this);
            ((View) mDanmakuView).setBackgroundColor(0x00000000);
        }
        FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        root.addView((View) mDanmakuView, dlp);
        DanmakuHelper.applyConfig();
        DanmakuHelper.prepareLive(mDanmakuView);

        findViewById(R.id.live_play).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(LiveRoomActivity.this, PlayerActivity.class);
                intent.putExtra("live", true);
                intent.putExtra("room_id", mRoomId);
                intent.putExtra("title", mRoom != null ? mRoom.title : "");
                startActivity(intent);
            }
        });
        findViewById(R.id.live_cover).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                findViewById(R.id.live_play).performClick();
            }
        });
        // 观众列表
        findViewById(R.id.live_viewers).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showViewers();
            }
        });
        // 发弹幕
        final EditText input = (EditText) findViewById(R.id.live_danmaku_input);
        findViewById(R.id.live_danmaku_send).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendDanmaku(input);
            }
        });

        loadInfo();
    }

    private void loadInfo() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final LiveRoom room = LiveApi.getRoomInfo(mRoomId);
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            mRoom = room;
                            mRealRoomId = room.roomId;
                            ((TextView) findViewById(R.id.live_title)).setText(room.title);
                            ((TextView) findViewById(R.id.live_info)).setText(
                                    NumberUtil.toWan(room.online) + "人在看 · 房间号 " + room.roomId);
                            ImageLoader.get().load((ImageView) findViewById(R.id.live_cover), room.cover);
                        }
                    });
                } catch (final Exception e) {
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            toast(R.string.live_not_live);
                        }
                    });
                }
            }
        }).start();
        // 弹幕轮询
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (mPolling && mRealRoomId <= 0) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ignored) {
                    }
                }
                while (mPolling) {
                    final java.util.List<LiveApi.LiveDanmaku> list = LiveApi.getLiveDanmaku(mRealRoomId);
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            for (int i = 0; i < list.size(); i++) {
                                LiveApi.LiveDanmaku dm = list.get(i);
                                if (dm.timeline.length() > 0 && mSeenTimelines.contains(dm.timeline)) {
                                    continue;
                                }
                                if (dm.timeline.length() > 0) {
                                    mSeenTimelines.add(dm.timeline);
                                    if (mSeenTimelines.size() > 500) {
                                        mSeenTimelines.clear();
                                    }
                                }
                                if (dm.uid > 0 && !mViewers.containsKey(dm.uid)) {
                                    mViewers.put(dm.uid, new String[]{dm.name});
                                }
                                DanmakuHelper.addLiveDanmaku(mDanmakuView,
                                        dm.name.length() > 0 ? dm.name + "：" + dm.text : dm.text);
                            }
                        }
                    });
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException ignored) {
                    }
                }
            }
        }).start();
    }

    private void sendDanmaku(final EditText input) {
        if (!HttpUtil.isLoggedIn()) {
            toast(R.string.need_login);
            startActivity(new Intent(this, LoginActivity.class));
            return;
        }
        final String text = input.getText().toString().trim();
        if (text.length() == 0) {
            toast("说点什么吧");
            return;
        }
        input.setText("");
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
        }
        DanmakuHelper.addLiveDanmaku(mDanmakuView, "我：" + text);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final int code = LiveApi.sendLiveDanmaku(mRealRoomId, text);
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            toast(code == 0 ? "弹幕已发送" : "发送失败(" + code + ")，可能未登录或等级不够");
                        }
                    });
                } catch (final Exception e) {
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            toast("发送失败");
                        }
                    });
                }
            }
        }).start();
    }

    /** 观众列表（正在发弹幕的观众），点头像/名字进主页 */
    private void showViewers() {
        final java.util.List<long[]> uids = new java.util.ArrayList<long[]>();
        for (Long uid : mViewers.keySet()) {
            uids.add(new long[]{uid});
        }
        if (uids.size() == 0) {
            toast("还没有弹幕，稍等片刻再试");
            return;
        }
        final String[] names = new String[uids.size()];
        for (int i = 0; i < uids.size(); i++) {
            String[] info = mViewers.get(uids.get(i)[0]);
            names[i] = info != null && info[0].length() > 0 ? info[0] : ("UID " + uids.get(i)[0]);
        }
        new android.app.AlertDialog.Builder(this)
                .setTitle("观众（" + uids.size() + " 人正在发弹幕）")
                .setItems(names, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        if (which >= 0 && which < uids.size()) {
                            Intent intent = new Intent(LiveRoomActivity.this, UserActivity.class);
                            intent.putExtra("mid", uids.get(which)[0]);
                            intent.putExtra("name", names[which]);
                            startActivity(intent);
                        }
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    protected void onDestroy() {
        mPolling = false;
        if (mDanmakuView != null) {
            try {
                mDanmakuView.release();
            } catch (Exception ignored) {
            }
        }
        super.onDestroy();
    }
}
