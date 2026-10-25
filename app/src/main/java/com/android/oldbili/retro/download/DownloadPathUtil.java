package com.android.oldbili.retro.download;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;

import com.android.oldbili.retro.util.Sp;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.OutputStream;

/**
 * 下载目录适配。
 *
 * 老规矩：默认直接写系统公共"下载"文件夹（老设备 + 授权后的新设备都能写）。
 * 安卓13+ 若系统把直接写入收紧了，就退到用户手动选的系统文件夹（SAF），
 * 再不行就用应用私有目录，保证安卓16甚至更高也能下载。
 */
public class DownloadPathUtil {

    /** 目录抽象：File 或 SAF 文档目录，对外统一 openOutputStream */
    public interface Folder {
        OutputStream openOutputStream(String name) throws FileNotFoundException;

        /** 追加写（断点续传）；FileFolder 支持续传，SafFolder 退回覆盖写 */
        OutputStream openOutputStream(String name, boolean append) throws FileNotFoundException;

        /** 文件大小（FileFolder 有效；SafFolder 恒 0） */
        long length(String name);

        /** 重命名（FileFolder 有效） */
        boolean rename(String from, String to);

        String describe();

        boolean delete(String name);
    }

    /** 普通 File 目录 */
    public static class FileFolder implements Folder {
        private final File mDir;

        public FileFolder(File dir) {
            mDir = dir;
        }

        @Override
        public OutputStream openOutputStream(String name) throws FileNotFoundException {
            return new java.io.FileOutputStream(new File(mDir, name));
        }

        @Override
        public OutputStream openOutputStream(String name, boolean append) throws FileNotFoundException {
            return new java.io.FileOutputStream(new File(mDir, name), append);
        }

        @Override
        public long length(String name) {
            File f = new File(mDir, name);
            return f.exists() ? f.length() : 0;
        }

        @Override
        public boolean rename(String from, String to) {
            File f = new File(mDir, from);
            File t = new File(mDir, to);
            if (!f.exists()) {
                return false;
            }
            if (t.exists()) {
                t.delete();
            }
            return f.renameTo(t);
        }

        @Override
        public String describe() {
            return mDir.getAbsolutePath();
        }

        @Override
        public boolean delete(String name) {
            File f = new File(mDir, name);
            return !f.exists() || f.delete();
        }
    }

    /** SAF 树目录（自写的最小 TreeDocumentFile，兼容老 support 库没有 DocumentFile） */
    public static class SafFolder implements Folder {
        private final ContentResolver mResolver;
        private final Uri mTreeUri;

        public SafFolder(ContentResolver resolver, Uri treeUri) {
            mResolver = resolver;
            mTreeUri = treeUri;
        }

        private Uri buildChildUri(String name) {
            String docId = DocumentsContract.getTreeDocumentId(mTreeUri);
            return DocumentsContract.buildDocumentUriUsingTree(mTreeUri, docId + "/" + name);
        }

        private Uri findOrCreate(String name) {
            String docId = DocumentsContract.getTreeDocumentId(mTreeUri);
            Uri dirUri = DocumentsContract.buildDocumentUriUsingTree(mTreeUri, docId);
            // 先查子文档列表找同名文件（DocumentsContract.getDocument 是隐藏 API，老工程用不上）
            try {
                Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(mTreeUri, docId);
                android.database.Cursor cursor = mResolver.query(childrenUri,
                        new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                                DocumentsContract.Document.COLUMN_DISPLAY_NAME},
                        null, null, null);
                if (cursor != null) {
                    try {
                        while (cursor.moveToNext()) {
                            String displayName = cursor.getString(1);
                            if (name.equals(displayName)) {
                                String childId = cursor.getString(0);
                                cursor.close();
                                return DocumentsContract.buildDocumentUriUsingTree(mTreeUri, childId);
                            }
                        }
                    } finally {
                        cursor.close();
                    }
                }
            } catch (Exception ignored) {
            }
            try {
                return DocumentsContract.createDocument(mResolver, dirUri,
                        "application/octet-stream", name);
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        public OutputStream openOutputStream(String name) throws FileNotFoundException {
            Uri uri = findOrCreate(name);
            if (uri == null) {
                throw new FileNotFoundException("SAF 创建文件失败: " + name);
            }
            try {
                return mResolver.openOutputStream(uri, "rwt");
            } catch (Exception e) {
                throw new FileNotFoundException(e.getMessage());
            }
        }

        @Override
        public OutputStream openOutputStream(String name, boolean append) throws FileNotFoundException {
            // SAF 追加写（"wa" = write+append）；不支持时退回覆盖写
            Uri uri = findOrCreate(name);
            if (uri == null) {
                throw new FileNotFoundException("SAF 创建文件失败: " + name);
            }
            try {
                return mResolver.openOutputStream(uri, append ? "wa" : "rwt");
            } catch (Exception e) {
                try {
                    return mResolver.openOutputStream(uri, "rwt");
                } catch (Exception e2) {
                    throw new FileNotFoundException(e.getMessage());
                }
            }
        }

        @Override
        public long length(String name) {
            return 0;
        }

        @Override
        public boolean rename(String from, String to) {
            return false;
        }

        @Override
        public String describe() {
            return "系统文件夹(SAF)";
        }

        @Override
        public boolean delete(String name) {
            try {
                return DocumentsContract.deleteDocument(mResolver, buildChildUri(name));
            } catch (Exception e) {
                return false;
            }
        }
    }

    /** 应用私有目录（最后兜底） */
    public static class AppFolder extends FileFolder {
        public AppFolder(File dir) {
            super(dir);
        }

        @Override
        public String describe() {
            return super.describe() + " (应用私有)";
        }
    }

    /** 用户通过系统文件选择器选过目录（安卓13+） */
    public static boolean hasSafDir() {
        String saved = Sp.getString(Sp.SET_DOWNLOAD_DIR, "");
        return saved != null && saved.startsWith("saf:");
    }

    public static void saveSafDir(Uri treeUri) {
        Sp.putString(Sp.SET_DOWNLOAD_DIR, "saf:" + treeUri.toString());
    }

    /** 解析当前下载目录 */
    public static Folder resolve(Context context) {
        String saved = Sp.getString(Sp.SET_DOWNLOAD_DIR, "");
        if (saved != null && saved.startsWith("saf:") && Build.VERSION.SDK_INT >= 21) {
            try {
                Uri tree = Uri.parse(saved.substring(4));
                context.getContentResolver().takePersistableUriPermission(tree,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                return new SafFolder(context.getContentResolver(), tree);
            } catch (Exception e) {
                // URI 失效就退回普通目录
                Sp.putString(Sp.SET_DOWNLOAD_DIR, "");
            }
        }
        return new FileFolder(getPublicDownloadsDir());
    }

    /** 公共下载文件夹里的子目录：Download/OldBiliRetro */
    public static File getPublicDownloadsDir() {
        // getExternalStoragePublicDirectory 是安卓2.2+的接口，更老的设备直接拼路径
        if (Build.VERSION.SDK_INT >= 8) {
            return new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS), "OldBiliRetro");
        }
        return new File("/mnt/sdcard/Download/OldBiliRetro");
    }

    /** 应用私有下载目录（兜底） */
    public static AppFolder getAppFallbackFolder(Context context) {
        File base;
        if (Build.VERSION.SDK_INT >= 8) {
            base = context.getExternalFilesDir(null);
        } else {
            base = context.getFilesDir();
        }
        return new AppFolder(new File(base, "download"));
    }

    /** 测试目标目录是否真的可写（安卓13+ 需要 SAF 的场合会失败） */
    public static boolean isDirectDirWritable() {
        File dir = getPublicDownloadsDir();
        if (!dir.exists() && !dir.mkdirs()) {
            return false;
        }
        File test = new File(dir, ".write_test");
        try {
            java.io.FileOutputStream fos = new java.io.FileOutputStream(test);
            fos.write(1);
            fos.close();
            return test.delete();
        } catch (Exception e) {
            return false;
        }
    }

    /** 导出崩溃日志等小文件到下载文件夹（尽力而为） */
    public static File exportToDownloads(Context context, File srcFile, String outName) {
        try {
            Folder folder = resolve(context);
            FileFolder direct = folder instanceof FileFolder ? (FileFolder) folder : null;
            File out = null;
            if (direct != null) {
                File dir = getPublicDownloadsDir();
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                out = new File(dir, outName);
                copyFile(srcFile, out);
            } else {
                // SAF：复制后无 File 可给，直接写流
                OutputStream os = folder.openOutputStream(outName);
                java.io.FileInputStream fis = new java.io.FileInputStream(srcFile);
                byte[] buf = new byte[8192];
                int n;
                while ((n = fis.read(buf)) != -1) {
                    os.write(buf, 0, n);
                }
                fis.close();
                os.close();
            }
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    private static void copyFile(File src, File dst) throws Exception {
        java.io.FileInputStream fis = new java.io.FileInputStream(src);
        java.io.FileOutputStream fos = new java.io.FileOutputStream(dst);
        byte[] buf = new byte[8192];
        int n;
        while ((n = fis.read(buf)) != -1) {
            fos.write(buf, 0, n);
        }
        fis.close();
        fos.close();
    }
}
