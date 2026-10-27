package com.android.oldbili.retro;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;

import com.android.oldbili.retro.api.LoginApi;
import com.android.oldbili.retro.download.DownloadPathUtil;
import com.android.oldbili.retro.net.HttpUtil;
import com.android.oldbili.retro.util.ClipboardCompat;
import com.android.oldbili.retro.util.Sp;
import com.android.oldbili.retro.util.CrashHandler;
import com.android.oldbili.retro.util.DeviceInfoUtil;

import java.io.File;

/**
 * 设置页：各个接口的选择开关、播放设置入口、下载目录、账号与关于。
 */
public class SettingsActivity extends PreferenceActivity {

    /** 官网域名暂时留空，以后再加 */
    private static final String UPDATE_URL = "http://136.suzushikaze274.com";

    private static final int REQ_PICK_DIR = 2002;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getPreferenceManager().setSharedPreferencesName("oldbili");
        // 偏好项由 PreferenceActivity 自己 inflate，必须在 addPreferencesFromResource 之前装字体
        com.android.oldbili.retro.util.FontManager.installFactory(this);
        addPreferencesFromResource(R.xml.prefs);

        setTitle(R.string.settings_title);

        // 版本号
        Preference version = findPreference("version");
        if (version != null) {
            version.setSummary("OldBiliRetro " + DeviceInfoUtil.getAppVersion(this));
        }

        // 解码设置（参照哔哩终端安卓4.0修复版）
        findPreference("decoder_settings").setOnPreferenceClickListener(
                new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        startActivity(new Intent(SettingsActivity.this, DecoderSettingsActivity.class));
                        return true;
                    }
                });

        // 弹幕设置（独立页）
        findPreference("danmaku_settings").setOnPreferenceClickListener(
                new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        startActivity(new Intent(SettingsActivity.this, DanmakuSettingsActivity.class));
                        return true;
                    }
                });

        // 下载目录
        findPreference("set_download_dir").setOnPreferenceClickListener(
                new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        pickDownloadDir();
                        return true;
                    }
                });

        // 清除缓存
        findPreference("clear_cache").setOnPreferenceClickListener(
                new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        confirmClearCache();
                        return true;
                    }
                });

        // 安卓 2：默认清晰度只给到 1080P（隐藏 1080P60/2K）
        if (Build.VERSION.SDK_INT < 11) {
            try {
                android.preference.ListPreference q =
                        (android.preference.ListPreference) findPreference("set_default_quality");
                if (q != null) {
                    q.setEntries(R.array.quality_entries_old);
                    q.setEntryValues(R.array.quality_values_old);
                }
            } catch (Exception ignored) {
            }
        }

        // 登录/退出登录
        findPreference("login_logout").setOnPreferenceClickListener(
                new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        onLoginLogout();
                        return true;
                    }
                });

        // 导出 Cookies（导出后清除登录状态）
        findPreference("export_cookies").setOnPreferenceClickListener(
                new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        confirmExportCookies();
                        return true;
                    }
                });

        // 检查更新：跳转官网（域名留空，以后再加）
        findPreference("check_update").setOnPreferenceClickListener(
                new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        checkUpdate();
                        return true;
                    }
                });

        // 设备信息检测
        findPreference("device_info").setOnPreferenceClickListener(
                new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        startActivity(new Intent(SettingsActivity.this, DeviceInfoActivity.class));
                        return true;
                    }
                });

        // 崩溃日志收集：导出TXT到下载文件夹
        findPreference("crash_log").setOnPreferenceClickListener(
                new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        exportCrashLogs();
                        return true;
                    }
                });

        // 界面字体：二次确认后保存，并结束进程重启应用
        try {
            final android.preference.ListPreference fontPref =
                    (android.preference.ListPreference) findPreference("set_font");
            if (fontPref != null) {
                fontPref.setOnPreferenceChangeListener(
                        new android.preference.Preference.OnPreferenceChangeListener() {
                            @Override
                            public boolean onPreferenceChange(android.preference.Preference p,
                                                              Object newValue) {
                                final String value = String.valueOf(newValue);
                                new android.app.AlertDialog.Builder(SettingsActivity.this)
                                        .setTitle("切换字体")
                                        .setMessage("您真的要切换字体吗？将结束进程并重启APP")
                                        .setPositiveButton("确定",
                                                new android.content.DialogInterface.OnClickListener() {
                                                    @Override
                                                    public void onClick(android.content.DialogInterface d,
                                                                        int w) {
                                                        Sp.putString(Sp.SET_FONT, value);
                                                        restartApp();
                                                    }
                                                })
                                        .setNegativeButton(R.string.cancel, null)
                                        .show();
                                // 由确认框负责保存，这里不自动写入
                                return false;
                            }
                        });
            }
        } catch (Exception ignored) {
        }

        // 兼容性列表（放在利用规约上面）
        findPreference("compat").setOnPreferenceClickListener(
                new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        startActivity(new Intent(SettingsActivity.this, CompatActivity.class));
                        return true;
                    }
                });

        // 利用规约
        findPreference("eula").setOnPreferenceClickListener(
                new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        startActivity(new Intent(SettingsActivity.this, EulaActivity.class));
                        return true;
                    }
                });

        // 关于
        findPreference("about").setOnPreferenceClickListener(
                new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        startActivity(new Intent(SettingsActivity.this, AboutActivity.class));
                        return true;
                    }
                });
    }

    private void onLoginLogout() {
        if (HttpUtil.isLoggedIn()) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.set_logout)
                    .setMessage("确定退出登录吗？\n退出后会清除消息、动态、收藏等本地缓存，防止账号信息残留。")
                    .setPositiveButton(R.string.set_logout, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // 先清缓存再清登录态，避免残留上一个账号的内容
                            com.android.oldbili.retro.util.CacheUtil.clearUserCache();
                            com.android.oldbili.retro.net.ImageLoader.clearAll();
                            LoginApi.logout();
                            toast(R.string.logout_ok);
                        }
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        } else {
            startActivity(new Intent(this, LoginActivity.class));
        }
    }

    private void confirmClearCache() {
        new AlertDialog.Builder(this)
                .setTitle("清除缓存")
                .setMessage("将清理图片缓存、消息/动态缓存和临时文件，确定继续吗？")
                .setPositiveButton("清除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        com.android.oldbili.retro.util.CacheUtil.clearAll(SettingsActivity.this);
                        toast("缓存已清除");
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** 导出 Cookies：确认 → 写 TXT 到 Download/OldBiliRetro → 清登录 → 提示位置并可打开文件夹 */
    private void confirmExportCookies() {
        new AlertDialog.Builder(this)
                .setTitle("导出Cookies")
                .setMessage("您是否要导出Cookies？导出会清除登陆状态")
                .setPositiveButton("是", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        doExportCookies();
                    }
                })
                .setNegativeButton("否", null)
                .show();
    }

    private void doExportCookies() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                String result = null;
                File out = null;
                try {
                    // 文件名：UID+编辑时间（本地时间）
                    long uid = com.android.oldbili.retro.util.Sp.getLong(com.android.oldbili.retro.util.Sp.MID, 0);
                    java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat(
                            "yyyyMMdd_HHmmss", java.util.Locale.getDefault());
                    String name = (uid > 0 ? String.valueOf(uid) : "guest")
                            + "_" + fmt.format(new java.util.Date()) + ".txt";

                    StringBuilder sb = new StringBuilder();
                    sb.append("# OldBiliRetro Cookies 导出\n");
                    sb.append("# 导出时间: ").append(new java.util.Date().toString()).append("\n\n");
                    sb.append(com.android.oldbili.retro.util.Sp.getString(
                            com.android.oldbili.retro.util.Sp.COOKIES, "")).append("\n");
                    sb.append("DedeUserID=").append(uid).append("\n");
                    sb.append("buvid3=").append(com.android.oldbili.retro.util.Sp.getString("cookie_buvid3", "")).append("\n");
                    sb.append("buvid4=").append(com.android.oldbili.retro.util.Sp.getString("cookie_buvid4", "")).append("\n");

                    File tmp = new File(getFilesDir(), "cookies_export.txt");
                    java.io.FileOutputStream fos = new java.io.FileOutputStream(tmp);
                    fos.write(sb.toString().getBytes("UTF-8"));
                    fos.close();
                    out = DownloadPathUtil.exportToDownloads(SettingsActivity.this, tmp, name);
                    tmp.delete();
                    if (out != null) {
                        result = out.getAbsolutePath();
                    }
                } catch (Exception e) {
                    android.util.Log.w("Settings", "导出Cookies失败: " + e.getMessage());
                }
                final String path = result;
                final File exported = out;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // 无论成功失败都清除登录状态（按需求：导出会清除登陆状态）
                        LoginApi.logout();
                        if (path == null) {
                            toast("导出失败，请检查存储权限");
                            return;
                        }
                        // 显示确切保存位置：左边"知道了"、右边"关闭"，点哪个都打开文件夹
                        DialogInterface.OnClickListener openFolder = new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                openFolder(exported);
                            }
                        };
                        new AlertDialog.Builder(SettingsActivity.this)
                                .setTitle("导出成功")
                                .setMessage("Cookies 已保存到：\n" + path)
                                .setPositiveButton("知道了", openFolder)
                                .setNegativeButton("关闭", openFolder)
                                .show();
                    }
                });
            }
        }).start();
    }

    /** 打开文件管理器并定位到导出文件夹 */
    private void openFolder(File exported) {
        try {
            File dir = exported != null && exported.getParentFile() != null
                    ? exported.getParentFile() : DownloadPathUtil.getPublicDownloadsDir();
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(android.net.Uri.fromFile(dir), "resource/folder");
            startActivity(intent);
            return;
        } catch (Exception ignored) {
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(android.net.Uri.fromFile(DownloadPathUtil.getPublicDownloadsDir()),
                    "vnd.android.document/directory");
            startActivity(intent);
        } catch (Exception e2) {
            toast("请手动打开：Download/OldBiliRetro 文件夹");
        }
    }

    private void checkUpdate() {
        // 每次更新提示都带上这一行，提醒用户用新版本
        new android.app.AlertDialog.Builder(this)
                .setTitle("检查更新")
                .setMessage(UPDATE_URL + "\n\n" + getString(R.string.update_tip))
                .setPositiveButton("应用内打开", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        startActivity(new Intent(SettingsActivity.this, WebActivity.class)
                                .putExtra("url", UPDATE_URL)
                                .putExtra("title", "官网"));
                    }
                })
                .setNeutralButton("系统浏览器", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        try {
                            startActivity(new Intent(Intent.ACTION_VIEW,
                                    android.net.Uri.parse(UPDATE_URL)));
                        } catch (Exception e) {
                            toast(R.string.update_no_url);
                        }
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void pickDownloadDir() {
        if (Build.VERSION.SDK_INT >= 21) {
            try {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                startActivityForResult(intent, REQ_PICK_DIR);
                return;
            } catch (Exception ignored) {
            }
        }
        toast(R.string.download_folder_tip);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_DIR && resultCode == RESULT_OK && data != null && data.getData() != null) {
            try {
                getContentResolver().takePersistableUriPermission(data.getData(),
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                DownloadPathUtil.saveSafDir(data.getData());
                toast(R.string.download_dir_picked);
            } catch (Exception e) {
                toast("目录授权失败");
            }
        }
    }

    private void exportCrashLogs() {
        File dir = new File(getFilesDir(), "crash");
        File[] logs = dir.listFiles();
        if (logs == null || logs.length == 0) {
            toast(R.string.crash_none);
            return;
        }
        // 导出最新的那个
        File newest = null;
        for (int i = 0; i < logs.length; i++) {
            if (newest == null || logs[i].lastModified() > newest.lastModified()) {
                newest = logs[i];
            }
        }
        File out = DownloadPathUtil.exportToDownloads(this, newest, newest.getName());
        if (out != null) {
            toast(R.string.crash_exported);
        } else {
            toast("导出失败，请检查下载目录");
        }
    }

    private void toast(int resId) {
        com.android.oldbili.retro.util.ToastUtil.show(this, resId);
    }

    private void toast(String text) {
        com.android.oldbili.retro.util.ToastUtil.show(this, text);
    }

    /** 结束当前进程并重新启动应用（切换字体后使用） */
    private void restartApp() {
        try {
            android.content.Intent intent = new android.content.Intent(
                    SettingsActivity.this, SplashActivity.class);
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
            int flags = android.app.PendingIntent.FLAG_CANCEL_CURRENT;
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                flags |= 0x04000000; // FLAG_IMMUTABLE
            }
            android.app.PendingIntent pi = android.app.PendingIntent.getActivity(
                    SettingsActivity.this, 0, intent, flags);
            android.app.AlarmManager am = (android.app.AlarmManager)
                    getSystemService(android.content.Context.ALARM_SERVICE);
            if (am != null) {
                am.set(android.app.AlarmManager.ELAPSED_REALTIME,
                        android.os.SystemClock.elapsedRealtime() + 500, pi);
            }
        } catch (Throwable ignored) {
        }
        try {
            android.os.Process.killProcess(android.os.Process.myPid());
        } catch (Throwable ignored) {
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
        // 偏好列表的行是异步布局出来的，延迟再套一次字体
        applyFontToSettings();
    }

    private void applyFontToSettings() {
        try {
            final android.view.View decor = getWindow().getDecorView();
            com.android.oldbili.retro.util.FontManager.applyTo(decor, this);
            decor.postDelayed(new Runnable() {
                @Override
                public void run() {
                    com.android.oldbili.retro.util.FontManager.applyTo(decor,
                            SettingsActivity.this);
                }
            }, 250);
        } catch (Throwable ignored) {
        }
    }

}
