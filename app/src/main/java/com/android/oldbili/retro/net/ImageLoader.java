package com.android.oldbili.retro.net;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.support.v4.util.LruCache;
import android.util.Log;
import android.widget.ImageView;

import com.android.oldbili.retro.util.Md5Util;
import com.android.oldbili.retro.util.Sp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 自绘图片加载器（当年 Glide 还没普及，大家都自己写）。
 * 内存 LRU + 磁盘缓存 + 线程池，封面按需裁剪尺寸，老设备自动降级 http。
 */
public class ImageLoader {

    private static ImageLoader sInstance;

    private final Context mContext;
    private final LruCache<String, Bitmap> mMemCache;
    private final ExecutorService mPool;
    private File mDiskDir;

    public static void init(Context context) {
        if (sInstance == null) {
            sInstance = new ImageLoader(context.getApplicationContext());
        }
    }

    public static ImageLoader get() {
        return sInstance;
    }

    private ImageLoader(Context context) {
        mContext = context;
        int maxMem = (int) (Runtime.getRuntime().maxMemory() / 8);
        if (maxMem < 1024 * 1024) {
            maxMem = 1024 * 1024;
        }
        if (maxMem > 8 * 1024 * 1024) {
            maxMem = 8 * 1024 * 1024;
        }
        final int cacheSize = maxMem;
        mMemCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getRowBytes() * value.getHeight();
            }
        };
        mPool = Executors.newFixedThreadPool(3);
        mDiskDir = new File(context.getCacheDir(), "img");
        if (!mDiskDir.exists()) {
            mDiskDir.mkdirs();
        }
    }

    /** 清空图片缓存（内存 + 磁盘） */
    public static void clearAll() {
        if (sInstance == null) {
            return;
        }
        try {
            sInstance.mMemCache.evictAll();
        } catch (Exception ignored) {
        }
        deleteTree(sInstance.mDiskDir);
        if (!sInstance.mDiskDir.exists()) {
            sInstance.mDiskDir.mkdirs();
        }
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (int i = 0; i < children.length; i++) {
                    deleteTree(children[i]);
                }
            }
        }
        file.delete();
    }

    /** 封面 URL 加裁剪参数（居中裁成 w x h；老设备用 jpg，新设备用 webp 省流量） */
    public static String coverUrl(String url, int w, int h) {
        if (url == null || url.length() == 0) {
            return null;
        }
        url = fixProtocolRelative(url);
        boolean hd = Sp.getBoolean(Sp.SET_IMAGE_QUALITY, true);
        if (!hd) {
            w = Math.max(1, w / 2);
            h = Math.max(1, h / 2);
        }
        // 少图模式（安卓 2 自动开）：封面统一小图，省流量省内存
        if (com.android.oldbili.retro.util.DeviceCompat.isLowImageMode()) {
            w = Math.min(w, 240);
            h = Math.min(h, 150);
        }
        // B 站图床规则：参数追加在原地址（含 .jpg）之后，如 xxx.jpg@320w_200h_1c.webp
        // 安卓4.2 及以下解码 WebP 不稳，退回 jpg 输出
        String ext = Build.VERSION.SDK_INT < 18 ? "jpg" : "webp";
        return url + "@" + w + "w_" + h + "h_1c." + ext;
    }

    /** 头像 URL 加尺寸参数 */
    public static String avatarUrl(String url, int size) {
        if (url == null || url.length() == 0) {
            return null;
        }
        url = fixProtocolRelative(url);
        if (url.indexOf('@') > 0) {
            return url;
        }
        String ext = Build.VERSION.SDK_INT < 18 ? "jpg" : "webp";
        return url + "@" + size + "w_" + size + "h_1c." + ext;
    }

    /** 协议相对地址（//i0.hdslb.com/...）补协议头：新设备 https，老设备 http */
    private static String fixProtocolRelative(String url) {
        if (url.startsWith("//")) {
            return (Build.VERSION.SDK_INT < 19 ? "http:" : "https:") + url;
        }
        if (Build.VERSION.SDK_INT >= 28 && url.startsWith("http://")) {
            // Android 9+ 默认禁止明文 http，升级为 https（图床同址支持）
            return "https://" + url.substring(7);
        }
        return url;
    }

    /** 老系统图片走 http，避开 TLS 握手开销与证书问题 */
    private static String adaptScheme(String url) {
        if (url == null) {
            return null;
        }
        url = fixProtocolRelative(url);
        if (Build.VERSION.SDK_INT < 19 && url.startsWith("https://")) {
            return "http://" + url.substring(8);
        }
        return url;
    }

    /** 加载封面到 ImageView */
    public void load(final ImageView view, String url) {
        load(view, url, 0);
    }

    public void load(final ImageView view, String url, int placeholderRes) {
        if (view == null) {
            return;
        }
        if (placeholderRes != 0) {
            view.setImageResource(placeholderRes);
        } else {
            view.setImageBitmap(null);
            view.setBackgroundColor(0xFF222222);
        }
        if (url == null || url.length() == 0) {
            return;
        }
        final String realUrl = adaptScheme(url);
        view.setTag(realUrl);

        Bitmap cached = mMemCache.get(realUrl);
        if (cached != null) {
            view.setImageBitmap(cached);
            return;
        }

        mPool.execute(new Runnable() {
            @Override
            public void run() {
                final Bitmap bitmap = loadBitmap(realUrl);
                if (bitmap == null) {
                    return;
                }
                final Object tag = view.getTag();
                view.post(new Runnable() {
                    @Override
                    public void run() {
                        if (realUrl.equals(view.getTag()) || (tag != null && tag.equals(view.getTag()))) {
                            view.setImageBitmap(bitmap);
                        }
                    }
                });
            }
        });
    }

    private Bitmap loadBitmap(String url) {
        Bitmap cached = mMemCache.get(url);
        if (cached != null) {
            return cached;
        }
        File disk = new File(mDiskDir, Md5Util.md5(url) + ".jpg");
        if (disk.exists() && disk.length() > 0) {
            Bitmap bmp = decode(disk);
            if (bmp != null) {
                mMemCache.put(url, bmp);
                return bmp;
            }
        }
        // 网络下载
        try {
            final File out = disk;
            HttpUtil.downloadTo(url, new OutputStream() {
                private FileOutputStream fos = new FileOutputStream(out);

                @Override
                public void write(int b) throws IOException {
                    fos.write(b);
                }

                @Override
                public void write(byte[] b, int off, int len) throws IOException {
                    fos.write(b, off, len);
                }

                @Override
                public void flush() throws IOException {
                    fos.flush();
                }

                @Override
                public void close() throws IOException {
                    fos.close();
                }
            }, null);
            Bitmap bmp = decode(disk);
            if (bmp != null) {
                mMemCache.put(url, bmp);
                return bmp;
            }
        } catch (Exception e) {
            Log.w("ImageLoader", "图片加载失败: " + e.getMessage() + " " + url);
            if (disk.exists()) {
                disk.delete();
            }
        }
        return null;
    }

    /** 解码时顺手限制尺寸，老机器内存小 */
    private Bitmap decode(File file) {
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
            int sample = 1;
            int maxSide = 640;
            int side = Math.max(opts.outWidth, opts.outHeight);
            while (side / sample > maxSide) {
                sample *= 2;
            }
            opts.inJustDecodeBounds = false;
            opts.inSampleSize = sample;
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
        } catch (Exception e) {
            return null;
        }
    }

    public void clearMemory() {
        mMemCache.evictAll();
    }
}
