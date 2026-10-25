package com.android.oldbili.retro.net;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;

/**
 * 本地流代理：给 MX Player / VLC / MoboPlayer 这类带不了 Referer 的第三方播放器用。
 *
 * 思路和当年老客户端一样：本机起个 127.0.0.1 的小服务，
 * 播放器播 http://127.0.0.1:端口/?url=<目标地址>，
 * 代理再带着 B 站要的 Referer 去上游把流搬回来。
 */
public class ProxyStreamService extends Service {

    private static int sPort = -1;
    private volatile boolean mStopped;

    /** 拿到代理地址；没起服务就先返回 null（调用方自行 startService 后重试） */
    public static String proxyUrlFor(ContextHolder holder) {
        if (sPort > 0) {
            return "http://127.0.0.1:" + sPort + "/?url=";
        }
        return null;
    }

    /** 简化：调用方直接拼。这里保留占位接口，见 PlayerActivity */
    public static class ContextHolder {
    }

    public static int getPort() {
        return sPort;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (sPort > 0) {
            return START_STICKY;
        }
        startProxy();
        return START_STICKY;
    }

    private void startProxy() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                ServerSocket server = null;
                try {
                    server = new ServerSocket(0, 8, java.net.InetAddress.getByName("127.0.0.1"));
                    sPort = server.getLocalPort();
                    while (!mStopped) {
                        final Socket socket = server.accept();
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                handle(socket);
                            }
                        }).start();
                    }
                } catch (Exception e) {
                    sPort = -1;
                } finally {
                    if (server != null) {
                        try {
                            server.close();
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        }).start();
    }

    private void handle(Socket socket) {
        try {
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();

            // 读请求行：GET /?url=xxx HTTP/1.1
            StringBuilder line = new StringBuilder();
            int c;
            while ((c = in.read()) != -1) {
                if (c == '\n') {
                    break;
                }
                if (c != '\r') {
                    line.append((char) c);
                }
            }
            String target = null;
            String[] parts = line.toString().split(" ");
            if (parts.length >= 2) {
                String path = parts[1];
                int q = path.indexOf("url=");
                if (q >= 0) {
                    target = URLDecoder.decode(path.substring(q + 4), "UTF-8");
                }
            }
            // 把剩余请求头吃掉
            while (true) {
                StringBuilder h = new StringBuilder();
                while ((c = in.read()) != -1 && c != '\n') {
                    if (c != '\r') {
                        h.append((char) c);
                    }
                }
                if (h.length() == 0) {
                    break;
                }
            }

            if (target == null || target.length() == 0) {
                writeBadRequest(out);
                return;
            }

            // 去上游拉流
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new URL(target).openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(60000);
            conn.setRequestProperty("User-Agent", HttpUtil.USER_AGENT);
            conn.setRequestProperty("Referer", "https://www.bilibili.com/");
            int code = conn.getResponseCode();
            InputStream body = code == 200 ? conn.getInputStream() : conn.getErrorStream();
            if (body == null) {
                writeBadRequest(out);
                return;
            }
            String reason = "OK";
            out.write(("HTTP/1.1 200 " + reason + "\r\n").getBytes("ISO-8859-1"));
            String contentType = conn.getHeaderField("Content-Type");
            if (contentType != null) {
                out.write(("Content-Type: " + contentType + "\r\n").getBytes("ISO-8859-1"));
            }
            long length = conn.getContentLength();
            if (length > 0) {
                out.write(("Content-Length: " + length + "\r\n").getBytes("ISO-8859-1"));
            }
            out.write("Connection: close\r\n\r\n".getBytes("ISO-8859-1"));
            byte[] buf = new byte[16384];
            int n;
            while ((n = body.read(buf)) != -1) {
                out.write(buf, 0, n);
                out.flush();
            }
            body.close();
            conn.disconnect();
        } catch (IOException e) {
            // 播放器主动断开很正常
        } finally {
            try {
                socket.close();
            } catch (Exception ignored) {
            }
        }
    }

    private void writeBadRequest(OutputStream out) throws IOException {
        out.write("HTTP/1.1 400 Bad Request\r\nConnection: close\r\n\r\n".getBytes("ISO-8859-1"));
        out.flush();
    }

    @Override
    public void onDestroy() {
        mStopped = true;
        sPort = -1;
        super.onDestroy();
    }
}
