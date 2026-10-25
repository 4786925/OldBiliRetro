package com.android.oldbili.retro.net;

import android.util.Base64;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLDecoder;

/**
 * 本地 HTTPS 转发代理：解决"IJK 的 ffmpeg 没有 TLS、而 B 站 CDN 又关闭了 http 明文口"的问题。
 * 原理：IJK 请求 http://127.0.0.1:PORT/<base64(https目标url)>，
 * 代理用系统的 HttpsURLConnection（支持 TLS1.2）拉流后原样转发。
 * 单例常驻（端口极小内存），127.0.0.1 仅本机可达，安全。
 */
public class HttpProxyServer {

    private static HttpProxyServer sInstance;
    private ServerSocket mServer;
    private int mPort;
    private Thread mThread;

    public static synchronized HttpProxyServer get() {
        if (sInstance == null) {
            sInstance = new HttpProxyServer();
        }
        return sInstance;
    }

    private HttpProxyServer() {
        start();
    }

    private void start() {
        try {
            try {
                mServer = new ServerSocket(24680, 8);
            } catch (Exception e) {
                mServer = new ServerSocket(0, 8);
            }
            mPort = mServer.getLocalPort();
            mThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    while (mServer != null) {
                        try {
                            final Socket client = mServer.accept();
                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    handle(client);
                                }
                            }).start();
                        } catch (Exception e) {
                            if (mServer == null) {
                                return;
                            }
                        }
                    }
                }
            }, "HttpProxy");
            mThread.start();
            android.util.Log.w("HttpProxy", "本地代理已启动 端口=" + mPort);
        } catch (Exception e) {
            android.util.Log.w("HttpProxy", "代理启动失败: " + e.getMessage());
        }
    }

    /** 把 https 流地址包成本地代理地址（http 地址原样返回） */
    public String wrap(String url) {
        if (url == null || !url.startsWith("https://") || mServer == null) {
            return url;
        }
        try {
            String enc = Base64.encodeToString(url.getBytes("UTF-8"),
                    Base64.NO_WRAP | Base64.URL_SAFE);
            return "http://127.0.0.1:" + mPort + "/" + enc;
        } catch (Exception e) {
            return url;
        }
    }

    private void handle(Socket client) {
        try {
            client.setSoTimeout(30000);
            InputStream in = client.getInputStream();
            OutputStream out = client.getOutputStream();
            // 读请求行：GET /<base64> HTTP/1.1
            StringBuilder lineBuf = new StringBuilder();
            int c;
            while ((c = in.read()) != -1) {
                if (c == '\n') {
                    break;
                }
                lineBuf.append((char) c);
            }
            String line = lineBuf.toString().trim();
            int sp1 = line.indexOf(' ');
            int sp2 = line.indexOf(' ', sp1 + 1);
            if (sp1 < 0 || sp2 < 0) {
                client.close();
                return;
            }
            String path = line.substring(sp1 + 1, sp2);
            // 读其余请求头（保留 Range，IJK 拖动进度条/续播依赖断点）
            String range = null;
            while (true) {
                StringBuilder h = new StringBuilder();
                while ((c = in.read()) != -1 && c != '\n') {
                    h.append((char) c);
                }
                String header = h.toString().trim();
                if (header.length() == 0) {
                    break;
                }
                if (header.toLowerCase().startsWith("range:")) {
                    range = header.substring(6).trim();
                }
            }
            // 解析目标 URL（base64，可带查询参数后缀 ?... 由 IJK 拼接）
            String target;
            try {
                String enc = path.startsWith("/") ? path.substring(1) : path;
                int q = enc.indexOf('?');
                String extra = "";
                if (q > 0) {
                    extra = enc.substring(q);
                    enc = enc.substring(0, q);
                }
                byte[] bytes = Base64.decode(enc, Base64.NO_WRAP | Base64.URL_SAFE);
                target = new String(bytes, "UTF-8") + extra;
            } catch (Exception e) {
                client.close();
                return;
            }
            if (!target.startsWith("https://")) {
                client.close();
                return;
            }
            // 用系统 TLS 拉流
            HttpURLConnection conn = (HttpURLConnection) new URL(target).openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("User-Agent", HttpUtil.USER_AGENT);
            conn.setRequestProperty("Referer", "https://www.bilibili.com/");
            // CDN 防盗链：必须带登录态 + 设备指纹 Cookie，否则 403
            String cookie = HttpUtil.cdnCookie();
            if (cookie != null && cookie.length() > 0) {
                conn.setRequestProperty("Cookie", cookie);
            }
            if (range != null) {
                conn.setRequestProperty("Range", range);
            }
            int code = conn.getResponseCode();
            android.util.Log.w("HttpProxy", "转发 " + code
                    + " " + (target.length() > 80 ? target.substring(0, 80) : target));
            InputStream body = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
            if (body == null) {
                client.close();
                conn.disconnect();
                return;
            }
            // 响应头
            String status = "HTTP/1.1 " + code + " " + conn.getResponseMessage() + "\r\n";
            out.write(status.getBytes("UTF-8"));
            String ct = conn.getContentType();
            if (ct != null) {
                out.write(("Content-Type: " + ct + "\r\n").getBytes("UTF-8"));
            }
            long len = conn.getContentLength();
            if (len > 0) {
                out.write(("Content-Length: " + len + "\r\n").getBytes("UTF-8"));
            }
            out.write("Accept-Ranges: bytes\r\nConnection: close\r\n\r\n".getBytes("UTF-8"));
            out.flush();
            // 流式转发
            byte[] buf = new byte[16384];
            int n;
            while ((n = body.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            out.flush();
            body.close();
            conn.disconnect();
            client.close();
        } catch (Exception e) {
            try {
                client.close();
            } catch (Exception ignored) {
            }
        }
    }
}
