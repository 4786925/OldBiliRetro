package com.android.oldbili.retro;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.android.oldbili.retro.api.VideoApi;
import com.android.oldbili.retro.download.DownloadManager;
import com.android.oldbili.retro.download.DownloadService;
import com.android.oldbili.retro.download.DownloadTask;
import com.android.oldbili.retro.model.VideoDetail;

/**
 * 仅用于调试/自动化：am start -n .../.DevDownloadActivity --es bvid BVxxx --ei qn 16
 * 直接把一个视频加入下载队列，方便在没有触摸输入的旧设备上验证下载链路。
 */
public class DevDownloadActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final String bvid = getIntent().getStringExtra("bvid");
        final long aid = getIntent().getLongExtra("aid", 0);
        final int qn = getIntent().getIntExtra("qn", 16);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    VideoDetail detail = bvid != null
                            ? VideoApi.getDetail(bvid) : VideoApi.getDetail(aid);
                    if (detail == null || detail.pages == null || detail.pages.isEmpty()) {
                        android.util.Log.e("DevDownload", "no detail");
                        finish();
                        return;
                    }
                    long cid = detail.pages.get(0).cid;
                    DownloadTask task = DownloadManager.get(DevDownloadActivity.this).addTask(
                            detail.aid, detail.bvid, cid, detail.title,
                            detail.pages.get(0).title, 0, detail.cover, qn,
                            BiliApiCompat.qualityName(qn), false, "low");
                    startService(new Intent(DevDownloadActivity.this, DownloadService.class));
                    android.util.Log.i("DevDownload", "queued " + task.title + " qn=" + qn);
                } catch (Throwable e) {
                    android.util.Log.e("DevDownload", "failed", e);
                } finally {
                    finish();
                }
            }
        }).start();
    }

    private static class BiliApiCompat {
        static String qualityName(int qn) {
            switch (qn) {
                case 16: return "360P";
                case 32: return "480P";
                case 64: return "720P";
                case 80: return "1080P";
                default: return String.valueOf(qn);
            }
        }
    }
}
