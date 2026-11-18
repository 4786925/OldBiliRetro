package com.android.oldbili.retro;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import com.android.oldbili.retro.api.VideoApi;
import com.android.oldbili.retro.model.VideoDetail;

/**
 * 低版本（Android 2.3 以下）播放兜底：
 * 解析出 MP4 直链后交给外部播放器；没装播放器就引导去官网下载。
 */
public class ExternalPlayActivity extends Activity {

    private static final int QN = 32; // 480P，MP4 单流最稳

    private String mIdType = "aid";
    private String mId = "";
    private long mCid;
    private long mAvid;
    private long mEpId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent it = getIntent();
        if (it.getStringExtra("bvid") != null) {
            mIdType = "bvid";
            mId = it.getStringExtra("bvid");
        } else {
            mId = String.valueOf(it.getLongExtra("aid", 0));
        }
        mCid = it.getLongExtra("cid", 0);
        mAvid = it.getLongExtra("avid", 0);
        mEpId = it.getLongExtra("ep_id", 0);
        if (mEpId > 0 && mCid > 0) {
            resolvePgc();
        } else {
            resolveNormal();
        }
    }

    private void resolveNormal() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    long cid = mCid;
                    if (cid <= 0) {
                        VideoDetail detail = mIdType.equals("bvid")
                                ? VideoApi.getDetail(mId) : VideoApi.getDetail(Long.parseLong(mId));
                        if (detail != null && detail.pages != null && detail.pages.size() > 0) {
                            cid = detail.pages.get(0).cid;
                        }
                    }
                    if (cid <= 0) {
                        fail("没有找到视频分P信息");
                        return;
                    }
                    Object[] play = VideoApi.getPlayUrlCandidatesPlay(mIdType, mId, cid, QN, true);
                    if (pickUrl(play) == null) {
                        // 480P 拿不到就退 360P
                        play = VideoApi.getPlayUrlCandidatesPlay(mIdType, mId, cid, 16, true);
                    }
                    open(play);
                } catch (Throwable e) {
                    android.util.Log.e("ExternalPlay", "resolve failed", e);
                    fail("解析播放地址失败: " + e.getMessage());
                }
            }
        }).start();
    }

    private void resolvePgc() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Object[] play = VideoApi.getPgcPlayUrlCandidates(mAvid, mCid, mEpId, QN, true);
                    open(play);
                } catch (Throwable e) {
                    fail("解析番剧地址失败: " + e.getMessage());
                }
            }
        }).start();
    }

    private void open(Object[] play) {
        String picked = pickUrl(play);
        final String url = picked;
        android.util.Log.i("ExternalPlay", "url=" + url);
        if (url == null || url.length() == 0) {
            fail("没有取到 MP4 播放地址");
            return;
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // CDN 校验 Referer/Cookie，老系统播放器不会带；
                // 走应用内本地代理补上请求头，再交给外部播放器。
                String playUrl = url;
                try {
                    playUrl = com.android.oldbili.retro.net.HttpProxyServer.get().wrap(url);
                } catch (Throwable e) {
                    android.util.Log.w("ExternalPlay", "wrap failed: " + e);
                }
                android.util.Log.i("ExternalPlay", "final=" + playUrl);
                startExternal(playUrl);
            }
        });
    }

    private String pickUrl(Object[] play) {
        if (play == null || play.length == 0) {
            return null;
        }
        Object first = play[0];
        if (first instanceof String) {
            return (String) first;
        }
        if (first instanceof java.util.List) {
            java.util.List list = (java.util.List) first;
            if (!list.isEmpty()) {
                Object o = list.get(0);
                if (o instanceof String) {
                    return (String) o;
                }
            }
        }
        return null;
    }

    private void startExternal(String url) {
        // 播放期间保持进程存活，本地代理才不会断
        try {
            startService(new Intent(this,
                    com.android.oldbili.retro.net.ProxyStreamService.class));
        } catch (Throwable ignored) {
        }
        Intent view = new Intent(Intent.ACTION_VIEW);
        view.setDataAndType(Uri.parse(url), "video/mp4");
        view.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        java.util.List<android.content.pm.ResolveInfo> list =
                getPackageManager().queryIntentActivities(view, 0);
        if (list == null || list.isEmpty()) {
            showNoPlayer();
            return;
        }
        try {
            startActivity(view);
            finish();
        } catch (Throwable e) {
            showNoPlayer();
        }
    }

    private void showNoPlayer() {
        new AlertDialog.Builder(this)
                .setTitle("没有可用的播放器")
                .setMessage("这台设备（Android " + android.os.Build.VERSION.RELEASE
                        + "）需要安装一个外部播放器才能看视频。\n\n"
                        + "推荐安装支持老系统的 MP4 播放器，装好后回到 OldBiliRetro 再点播放。")
                .setPositiveButton("去官网下载", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent i = new Intent(ExternalPlayActivity.this, UrlOpenActivity.class);
                        i.putExtra(Intent.EXTRA_TEXT, "http://136.suzushikaze274.com/player.php");
                        startActivity(i);
                        finish();
                    }
                })
                .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                })
                .setCancelable(false)
                .show();
    }

    private void fail(final String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(ExternalPlayActivity.this, msg, Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }
}
