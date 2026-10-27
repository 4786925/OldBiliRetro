package com.android.oldbili.retro;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import com.android.oldbili.retro.net.HttpUtil;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 外部链接入口：任何 B 站相关链接都可以选择用 OldBiliRetro 打开。
 * 解析后转发到视频详情 / 个人空间 / 直播 / 内置浏览器，自身不显示界面。
 */
public class UrlOpenActivity extends Activity {

    private static final Pattern P_VIDEO_BV = Pattern.compile("/(?:video|s)/(BV[0-9A-Za-z]+)");
    private static final Pattern P_VIDEO_AV = Pattern.compile("/(?:video|s)/av(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_UID = Pattern.compile("^/(\\d+)");
    private static final Pattern P_LIVE = Pattern.compile("^/(?:h5/)?(\\d+)");
    private static final Pattern P_B23 = Pattern.compile("^https?://b23\\.tv/([0-9A-Za-z]+)");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Uri uri = getIntent() == null ? null : getIntent().getData();
        if (uri == null) {
            // 有些启动器用 EXTRA_TEXT 传链接
            String text = getIntent() == null ? null : getIntent().getStringExtra(Intent.EXTRA_TEXT);
            if (text != null && text.startsWith("http")) {
                uri = Uri.parse(text);
            }
        }
        if (uri == null) {
            finish();
            return;
        }
        try {
            route(uri);
        } catch (Exception e) {
            toast("链接无法打开：" + e.getMessage());
        }
        finish();
    }

    private void route(Uri uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
        String path = uri.getPath() == null ? "" : uri.getPath();
        String full = uri.toString();

        // bilibili://video/123 或 bilibili://space/123
        if ("bilibili".equals(scheme)) {
            String p = path.isEmpty() ? uri.getSchemeSpecificPart() : path;
            Matcher m = Pattern.compile("(\\d+)").matcher(p == null ? "" : p);
            if (m.find()) {
                long id = Long.parseLong(m.group(1));
                if (full.contains("space")) {
                    openSpace(id);
                } else {
                    openVideo(null, id);
                }
                return;
            }
        }

        // b23.tv 短链：解析跳转后再路由
        if (host.endsWith("b23.tv")) {
            String real = resolveShortLink(full);
            if (real != null) {
                Uri r = Uri.parse(real);
                if (r.getHost() != null && !r.getHost().equals(host)) {
                    route(r);
                    return;
                }
            }
            openWeb(full);
            return;
        }

        // 个人空间
        if (host.startsWith("space.bilibili.com")) {
            Matcher m = P_UID.matcher(path);
            if (m.find()) {
                openSpace(Long.parseLong(m.group(1)));
                return;
            }
        }

        // 直播间
        if (host.startsWith("live.bilibili.com")) {
            Matcher m = P_LIVE.matcher(path);
            if (m.find()) {
                Intent i = new Intent(this, LiveRoomActivity.class);
                i.putExtra("room_id", Long.parseLong(m.group(1)));
                i.putExtra("title", "直播间");
                startActivity(i);
                return;
            }
        }

        // 视频
        Matcher bv = P_VIDEO_BV.matcher(path);
        if (bv.find()) {
            openVideo(bv.group(1), 0);
            return;
        }
        Matcher av = P_VIDEO_AV.matcher(path);
        if (av.find()) {
            openVideo(null, Long.parseLong(av.group(1)));
            return;
        }

        // 其他（专栏、番剧、动态等）先用内置浏览器
        openWeb(full);
    }

    private void openVideo(String bvid, long aid) {
        Intent i = new Intent(this, VideoDetailActivity.class);
        if (bvid != null && bvid.length() > 0) {
            i.putExtra("bvid", bvid);
        } else {
            i.putExtra("aid", aid);
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
    }

    private void openSpace(long mid) {
        Intent i = new Intent(this, UserActivity.class);
        i.putExtra("mid", mid);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
    }

    private void openWeb(String url) {
        Intent i = new Intent(this, WebActivity.class);
        i.putExtra("url", url);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
    }

    /** 解析 b23.tv 短链（跟随重定向，取最终 URL） */
    private static String resolveShortLink(String url) {
        java.net.HttpURLConnection conn = null;
        try {
            java.net.URL u = new java.net.URL(url);
            conn = (java.net.HttpURLConnection) u.openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", HttpUtil.USER_AGENT);
            conn.getResponseCode();
            return conn.getURL().toString();
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private void toast(String text) {
        try {
            android.widget.Toast.makeText(this, text, android.widget.Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) {
        }
    }
}
