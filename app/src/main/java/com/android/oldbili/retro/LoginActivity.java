package com.android.oldbili.retro;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.oldbili.retro.api.LoginApi;
import com.android.oldbili.retro.net.HttpUtil;
import com.android.oldbili.retro.util.ClipboardCompat;
import com.android.oldbili.retro.util.ToastUtil;

/**
 * 登录页：扫码登录（SwetakeQRCode 魔改版生成二维码）+ Cookie 导入/导出。
 * 部分老设备没有 WebView，导入 Cookie 就是登录的备用通道。
 */
public class LoginActivity extends BaseActivity {

    private ImageView mQrImage;
    private TextView mQrStatus;
    private String mQrKey = "";
    private Handler mHandler = new Handler();
    private boolean mPolling;

    private final Runnable mPollRunnable = new Runnable() {
        @Override
        public void run() {
            poll();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.login_title);
        setContentView(R.layout.activity_login);

        mQrImage = (ImageView) findViewById(R.id.qr_image);
        mQrStatus = (TextView) findViewById(R.id.qr_status);

        findViewById(R.id.btn_refresh_qr).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                generateQr();
            }
        });
        findViewById(R.id.btn_import_cookie).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showImportCookie();
            }
        });
        // 导出 Cookies 按钮已移除（导出功能在 设置-账号 里）

        generateQr();
    }

    // ==================== 扫码登录 ====================

    private void generateQr() {
        mQrStatus.setText(R.string.login_qr_hint);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final String[] result = LoginApi.generateQr();
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            mQrKey = result[1];
                            Bitmap qr = renderQr(result[0]);
                            mQrImage.setImageBitmap(qr);
                            android.util.Log.i("Login", "二维码已生成 size="
                                    + (qr != null ? qr.getWidth() + "x" + qr.getHeight() : "null")
                                    + " len=" + result[0].length());
                            startPolling();
                        }
                    });
                } catch (final Exception e) {
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            mQrStatus.setText("二维码生成失败，点击刷新重试");
                        }
                    });
                }
            }
        }).start();
    }

    /** ZXing 生成二维码位图（不依赖任何资源文件，长 URL 也不会越界空白） */
    private Bitmap renderQr(String content) {
        try {
            int size = 400;
            java.util.Map<com.google.zxing.EncodeHintType, Object> hints =
                    new java.util.HashMap<com.google.zxing.EncodeHintType, Object>();
            hints.put(com.google.zxing.EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(com.google.zxing.EncodeHintType.MARGIN, 1);
            com.google.zxing.common.BitMatrix matrix =
                    new com.google.zxing.qrcode.QRCodeWriter().encode(content,
                            com.google.zxing.BarcodeFormat.QR_CODE, size, size, hints);
            int[] pixels = new int[size * size];
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    pixels[y * size + x] = matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF;
                }
            }
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);
            bitmap.setPixels(pixels, 0, size, 0, 0, size, size);
            return bitmap;
        } catch (Exception e) {
            android.util.Log.e("Login", "二维码生成失败: " + e.getMessage());
            return null;
        }
    }

    private void startPolling() {
        if (mPolling) {
            return;
        }
        mPolling = true;
        mHandler.postDelayed(mPollRunnable, 3000);
    }

    private void poll() {
        if (mQrKey.length() == 0) {
            mPolling = false;
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final int[] result = LoginApi.pollQr(mQrKey);
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            switch (result[0]) {
                                case 0:
                                    // 登录成功，Cookie 已由网络层保存
                                    mPolling = false;
                                    mQrStatus.setText(R.string.login_ok);
                                    toast(R.string.login_ok);
                                    mHandler.postDelayed(new Runnable() {
                                        @Override
                                        public void run() {
                                            finish();
                                        }
                                    }, 800);
                                    break;
                                case LoginApi.POLL_WAIT:
                                    mHandler.postDelayed(mPollRunnable, 3000);
                                    break;
                                case LoginApi.POLL_SCANNED:
                                    mQrStatus.setText(R.string.login_qr_scanned);
                                    mHandler.postDelayed(mPollRunnable, 2000);
                                    break;
                                case LoginApi.POLL_EXPIRED:
                                    mPolling = false;
                                    mQrStatus.setText(R.string.login_qr_expired);
                                    break;
                                default:
                                    mPolling = false;
                                    mQrStatus.setText("登录失败，点击刷新重试");
                                    break;
                            }
                        }
                    });
                } catch (final Exception e) {
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            mHandler.postDelayed(mPollRunnable, 3000);
                        }
                    });
                }
            }
        }).start();
    }

    // ==================== Cookie 导入/导出 ====================

    private void showImportCookie() {
        final EditText input = new EditText(this);
        input.setHint(R.string.cookie_import_hint);
        input.setMinLines(3);
        new AlertDialog.Builder(this)
                .setTitle(R.string.cookie_import)
                .setView(input)
                .setPositiveButton(R.string.cookie_import, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        final String cookie = input.getText().toString().trim();
                        if (cookie.length() == 0) {
                            return;
                        }
                        // 有效性检查：必须含 SESSDATA 与 bili_jct
                        if (!cookie.contains("SESSDATA") || !cookie.contains("bili_jct")) {
                            toast(R.string.cookie_import_failed);
                            return;
                        }
                        HttpUtil.saveLoginCookie(cookie);
                        toast(R.string.cookie_import_ok);
                        finish();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void exportCookie() {
        String cookie = HttpUtil.loginCookie();
        if (cookie == null || cookie.length() == 0) {
            toast(R.string.not_login);
            return;
        }
        ClipboardCompat.copyOrShow(this, cookie, getString(R.string.cookie_copied));
        ToastUtil.show(this, R.string.cookie_export_hint);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mPolling = false;
        mHandler.removeCallbacksAndMessages(null);
    }
}
