package com.android.oldbili.retro.download;

import android.content.Context;

import com.android.oldbili.retro.api.VideoApi;
import com.android.oldbili.retro.net.HttpUtil;
import com.android.oldbili.retro.util.Sp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 下载管理器：队列、并发、进度回调、断点、封面保存、任务持久化。
 * 单例，进程内所有下载页面共用。
 */
public class DownloadManager {

    private static DownloadManager sInstance;

    private final Context mContext;
    private final List<DownloadTask> mTasks = new ArrayList<DownloadTask>();
    private final List<Listener> mListeners = new ArrayList<Listener>();
    private final ExecutorService mPool = Executors.newFixedThreadPool(2);
    private boolean mRunning;

    public interface Listener {
        void onTaskChanged(DownloadTask task);

        void onTaskRemoved(DownloadTask task);
    }

    public static DownloadManager get(Context context) {
        if (sInstance == null) {
            sInstance = new DownloadManager(context.getApplicationContext());
        }
        return sInstance;
    }

    private DownloadManager(Context context) {
        mContext = context;
        loadTasks();
    }

    // ==================== 任务管理 ====================

    public synchronized List<DownloadTask> getTasks() {
        return new ArrayList<DownloadTask>(mTasks);
    }

    public void addListener(Listener listener) {
        if (listener != null && !mListeners.contains(listener)) {
            mListeners.add(listener);
        }
    }

    public void removeListener(Listener listener) {
        mListeners.remove(listener);
    }

    /** 新建下载任务（一个分P）并立即开始排队 */
    public synchronized DownloadTask addTask(long aid, String bvid, long cid, String title,
                                             String part, int partIndex, String cover, int qn,
                                             String qualityName, boolean saveCover, String coverLevel) {
        DownloadTask task = new DownloadTask();
        task.aid = aid;
        task.bvid = bvid;
        task.cid = cid;
        task.title = title;
        task.part = part;
        task.cover = cover;
        task.qn = qn;
        task.qualityName = qualityName;
        task.saveCover = saveCover;
        task.coverLevel = coverLevel;
        task.fileName = "av" + aid
                + (partIndex > 0 ? "_P" + (partIndex + 1) : "")
                + "_" + qualityName.replace(" ", "") + ".mp4";
        mTasks.add(0, task);
        persist();
        notifyChanged(task);
        startQueue();
        return task;
    }

    /** 暂停 */
    public synchronized void pause(DownloadTask task) {
        if (task.state == DownloadTask.STATE_DOWNLOADING || task.state == DownloadTask.STATE_WAITING) {
            task.state = DownloadTask.STATE_PAUSED;
            persist();
            notifyChanged(task);
        }
    }

    /** 继续 */
    public synchronized void resume(DownloadTask task) {
        if (task.state == DownloadTask.STATE_PAUSED || task.state == DownloadTask.STATE_FAILED) {
            task.state = DownloadTask.STATE_WAITING;
            persist();
            notifyChanged(task);
            startQueue();
        }
    }

    /** 删除（连同文件） */
    public synchronized void delete(DownloadTask task) {
        mTasks.remove(task);
        persist();
        try {
            DownloadPathUtil.Folder folder = DownloadPathUtil.resolve(mContext);
            folder.delete(task.fileName);
            folder.delete(task.fileName + ".part");
            if (task.saveCover && task.coverName.length() > 0) {
                folder.delete(task.coverName);
            }
        } catch (Exception ignored) {
        }
        for (int i = mListeners.size() - 1; i >= 0; i--) {
            mListeners.get(i).onTaskRemoved(task);
        }
    }

    /** 只删除下载记录（保留文件） */
    public synchronized void deleteRecord(DownloadTask task) {
        mTasks.remove(task);
        persist();
        for (int i = mListeners.size() - 1; i >= 0; i--) {
            mListeners.get(i).onTaskRemoved(task);
        }
    }

    // ==================== 队列执行 ====================

    private synchronized void startQueue() {
        if (mRunning) {
            return;
        }
        mRunning = true;
        mPool.execute(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    DownloadTask next = pickNext();
                    if (next == null) {
                        break;
                    }
                    runTask(next);
                }
                mRunning = false;
            }
        });
    }

    private synchronized DownloadTask pickNext() {
        for (int i = 0; i < mTasks.size(); i++) {
            DownloadTask task = mTasks.get(i);
            if (task.state == DownloadTask.STATE_WAITING) {
                task.state = DownloadTask.STATE_DOWNLOADING;
                notifyChanged(task);
                return task;
            }
        }
        return null;
    }

    /**
     * 把 B 站 CDN 的明文 http 地址规范化为 https。
     * CDN 已关闭 http 口（返回 403/959），下载走系统 TLS 必须用 https。
     */
    private static String toHttps(String url) {
        if (url == null || !url.startsWith("http://")) {
            return url;
        }
        String host = url.substring(7);
        int slash = host.indexOf('/');
        String hostPart = slash > 0 ? host.substring(0, slash) : host;
        if (hostPart.contains("bilivideo") || hostPart.contains("upos")
                || hostPart.contains("hdslb") || hostPart.contains("mcdn")
                || hostPart.contains("akamaized") || hostPart.contains("bilivideo.cn")) {
            return "https://" + host;
        }
        return url;
    }

    private void runTask(final DownloadTask task) {
        try {
            DownloadPathUtil.Folder folder = DownloadPathUtil.resolve(mContext);
            if (folder instanceof DownloadPathUtil.FileFolder) {
                File dir = DownloadPathUtil.getPublicDownloadsDir();
                if (!dir.exists()) {
                    dir.mkdirs();
                }
            }

            // 拿播放地址（单流 mp4，方便直接下）：取全部候选线路
            String idType = task.bvid != null && task.bvid.length() > 0 ? "bvid" : "aid";
            String id = idType.equals("bvid") ? task.bvid : String.valueOf(task.aid);
            Object[] play = VideoApi.getPlayUrlCandidates(idType, id, task.cid, task.qn);
            @SuppressWarnings("unchecked")
            java.util.List<String> rawCandidates = (java.util.List<String>) play[0];
            if (rawCandidates == null || rawCandidates.size() == 0) {
                throw new java.io.IOException("未取到播放地址");
            }
            // 下载走系统 TLS：https 候选优先，http 明文节点仅作兜底
            // （B 站已关闭明文 CDN 口，http 化地址会返回 959，直接放弃）
            java.util.List<String> ordered = new java.util.ArrayList<String>();
            // 先把所有候选规范化为 https 形式（CDN 已拒绝明文 http，会 403）
            for (int i = 0; i < rawCandidates.size(); i++) {
                String u = toHttps(rawCandidates.get(i));
                if (u != null && !ordered.contains(u)) {
                    ordered.add(u);
                }
            }
            // 再补上原样的 http 地址作为最后的兜底
            for (int i = 0; i < rawCandidates.size(); i++) {
                String u = rawCandidates.get(i);
                if (u != null && !ordered.contains(u)) {
                    ordered.add(u);
                }
            }

            // 断点续传：写入 .part，完成后改名正式文件
            final String partName = task.fileName + ".part";
            long startByte = folder.length(partName);
            boolean done = false;
            Exception lastError = null;
            for (int ci = 0; ci < ordered.size(); ci++) {
                String videoUrl = ordered.get(ci);
                OutputStream out = null;
                try {
                    out = folder.openOutputStream(partName, startByte > 0);
                    done = downloadWithPause(task, videoUrl, out, startByte);
                    if (done) {
                        break;
                    }
                    if (task.state == DownloadTask.STATE_PAUSED) {
                        break;
                    }
                } catch (Exception e) {
                    lastError = e;
                    android.util.Log.w("Download", "线路[" + ci + "]失败: " + e.getMessage()
                            + " -> " + videoUrl.substring(0, Math.min(70, videoUrl.length())));
                    // 换线路时丢弃半截文件，重新从 0 开始
                    try {
                        if (out != null) {
                            out.close();
                        }
                    } catch (Exception ignored) {
                    }
                    out = null;
                    try {
                        folder.delete(partName);
                    } catch (Exception ignored) {
                    }
                    startByte = 0;
                } finally {
                    try {
                        if (out != null) {
                            out.close();
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
            if (!done) {
                if (task.state == DownloadTask.STATE_PAUSED) {
                    // 被暂停：保留 .part 进度，状态保持暂停
                    return;
                }
                throw lastError != null ? lastError : new java.io.IOException("所有线路均下载失败");
            }

            // 完成：改名 + 封面
            folder.rename(partName, task.fileName);

            if (task.saveCover && task.cover != null && task.cover.length() > 0) {
                try {
                    saveCover(folder, task);
                } catch (Exception e) {
                    android.util.Log.w("Download", "封面保存失败: " + e.getMessage());
                }
            }

            task.state = DownloadTask.STATE_DONE;
            persist();
            notifyChanged(task);
        } catch (Exception e) {
            android.util.Log.w("Download", "下载失败: " + e.getMessage());
            try {
                DownloadPathUtil.Folder folder = DownloadPathUtil.resolve(mContext);
                folder.delete(task.fileName + ".part");
            } catch (Exception ignored) {
            }
            // 暂停状态不要覆盖成失败（用户主动暂停不算失败）
            if (task.state != DownloadTask.STATE_PAUSED) {
                task.state = DownloadTask.STATE_FAILED;
                persist();
                notifyChanged(task);
            }
        }
    }

    /**
     * 手写下载循环：支持 Range 断点续传 + 每块检查暂停。
     * @return true=完整下载完成 false=中途被暂停
     */
    private boolean downloadWithPause(DownloadTask task, String url, OutputStream out, long startByte)
            throws Exception {
        java.net.HttpURLConnection conn =
                (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
        try {
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);
            // UA 必须与播放器一致（CDN 会按 UA 校验，自制 UA 会 403）
            conn.setRequestProperty("User-Agent",
                    com.android.oldbili.retro.net.HttpUtil.USER_AGENT);
            conn.setRequestProperty("Referer", "https://www.bilibili.com/");
            conn.setRequestProperty("Origin", "https://www.bilibili.com");
            conn.setRequestProperty("Accept", "*/*");
            // CDN 防盗链 Cookie（不带会 403）
            String dCookie = com.android.oldbili.retro.net.HttpUtil.cdnCookie();
            if (dCookie != null && dCookie.length() > 0) {
                conn.setRequestProperty("Cookie", dCookie);
            }
            if (startByte > 0) {
                conn.setRequestProperty("Range", "bytes=" + startByte + "-");
            }
            int code = conn.getResponseCode();
            if (code != 200 && code != 206) {
                throw new java.io.IOException("HTTP " + code);
            }
            long total = conn.getContentLength();
            if (total > 0 && startByte > 0) {
                total += startByte;
            }
            task.total = total;
            java.io.InputStream in = conn.getInputStream();
            byte[] buf = new byte[16384];
            long done = startByte;
            int n;
            while ((n = in.read(buf)) != -1) {
                if (task.state != DownloadTask.STATE_DOWNLOADING) {
                    // 被暂停：留好进度直接退出
                    in.close();
                    task.progress = done;
                    task.state = DownloadTask.STATE_PAUSED;
                    persist();
                    notifyChanged(task);
                    return false;
                }
                out.write(buf, 0, n);
                done += n;
                task.progress = done;
                long now = System.currentTimeMillis();
                if (now - task.lastNotifyTime >= 500) {
                    task.lastNotifyTime = now;
                    notifyChanged(task);
                }
            }
            in.close();
            task.progress = done;
            notifyChanged(task);
            return true;
        } finally {
            conn.disconnect();
        }
    }

    private void saveCover(DownloadPathUtil.Folder folder, DownloadTask task) throws Exception {
        int w, h;
        if ("高".equals(task.coverLevel)) {
            w = 1920;
            h = 1200;
        } else if ("低".equals(task.coverLevel)) {
            w = 480;
            h = 300;
        } else {
            w = 960;
            h = 600;
        }
        String coverUrl = com.android.oldbili.retro.net.ImageLoader.coverUrl(task.cover, w, h);
        task.coverName = task.fileName.replace(".mp4", "_cover.jpg");
        OutputStream out = folder.openOutputStream(task.coverName);
        try {
            HttpUtil.downloadTo(coverUrl, out, null);
        } finally {
            try {
                out.close();
            } catch (Exception ignored) {
            }
        }
    }

    // ==================== 持久化与通知 ====================

    private void notifyChanged(DownloadTask task) {
        for (int i = mListeners.size() - 1; i >= 0; i--) {
            mListeners.get(i).onTaskChanged(task);
        }
    }

    private synchronized void persist() {
        try {
            JSONArray arr = new JSONArray();
            for (int i = 0; i < mTasks.size(); i++) {
                DownloadTask t = mTasks.get(i);
                JSONObject o = new JSONObject();
                o.put("aid", t.aid);
                o.put("bvid", t.bvid);
                o.put("cid", t.cid);
                o.put("title", t.title);
                o.put("part", t.part);
                o.put("cover", t.cover);
                o.put("qn", t.qn);
                o.put("qualityName", t.qualityName);
                o.put("saveCover", t.saveCover);
                o.put("coverLevel", t.coverLevel);
                o.put("state", t.state);
                o.put("progress", t.progress);
                o.put("total", t.total);
                o.put("fileName", t.fileName);
                o.put("coverName", t.coverName);
                arr.put(o);
            }
            Sp.putString("download_tasks", arr.toString());
        } catch (Exception ignored) {
        }
    }

    private synchronized void loadTasks() {
        String saved = Sp.getString("download_tasks", "");
        if (saved == null || saved.length() == 0) {
            return;
        }
        try {
            JSONArray arr = new JSONArray(new org.json.JSONTokener(saved));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) {
                    continue;
                }
                DownloadTask t = new DownloadTask();
                t.aid = o.optLong("aid", 0);
                t.bvid = o.optString("bvid", "");
                t.cid = o.optLong("cid", 0);
                t.title = o.optString("title", "");
                t.part = o.optString("part", "");
                t.cover = o.optString("cover", "");
                t.qn = o.optInt("qn", 16);
                t.qualityName = o.optString("qualityName", "");
                t.saveCover = o.optBoolean("saveCover", false);
                t.coverLevel = o.optString("coverLevel", "中");
                t.progress = o.optLong("progress", 0);
                t.total = o.optLong("total", 0);
                t.fileName = o.optString("fileName", "");
                t.coverName = o.optString("coverName", "");
                int state = o.optInt("state", DownloadTask.STATE_PAUSED);
                if (state == DownloadTask.STATE_DOWNLOADING || state == DownloadTask.STATE_WAITING) {
                    state = DownloadTask.STATE_PAUSED; // 进程重启后不自动续下
                }
                t.state = state;
                mTasks.add(t);
            }
        } catch (Exception ignored) {
        }
    }
}
