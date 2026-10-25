package com.android.oldbili.retro.util;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import com.android.oldbili.retro.net.HttpUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 保存图片到相册的 OldBiliRetro 文件夹。
 * 安卓10+ 走 MediaStore（分区存储合规），老安卓直接写公共 Pictures 目录再广播扫描。
 */
public class SaveImageUtil {

    /** 下载并保存，返回是否成功 */
    public static boolean save(Context context, String url) {
        try {
            byte[] data = HttpUtil.getBytes(url);
            if (data == null || data.length == 0) {
                return false;
            }
            String name = "OldBiliRetro_" + System.currentTimeMillis() + ".jpg";
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                // RELATIVE_PATH 是 API 29 常量，compileSdk 停在 23 直接写字符串
                values.put("relative_path", "Pictures/OldBiliRetro");
                Uri uri = context.getContentResolver().insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri == null) {
                    return false;
                }
                OutputStream out = context.getContentResolver().openOutputStream(uri);
                if (out == null) {
                    return false;
                }
                out.write(data);
                out.flush();
                out.close();
                return true;
            }
            File dir = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES), "OldBiliRetro");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            File file = new File(dir, name);
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(data);
            fos.flush();
            fos.close();
            // 通知系统扫描（老安卓相册才能立刻看到）
            try {
                android.media.MediaScannerConnection.scanFile(context,
                        new String[]{file.getAbsolutePath()}, null, null);
            } catch (Exception ignored) {
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
