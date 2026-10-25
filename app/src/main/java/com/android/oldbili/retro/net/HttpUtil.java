package com.android.oldbili.retro.net;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.android.oldbili.retro.util.Sp;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

/**
 * 网络请求核心，全部接口都走这里。
 *
 * - HttpURLConnection 直连（当年不用 OkHttp，如今更不需要）
 * - 老设备 TLS1.2 兼容（TlsSocketFactoryCompat）
 * - 浏览器指纹 Cookie 合成 + 登录 Cookie 合并 + Set-Cookie 捕获
 * - 自动重试与手动跟随重定向
 */
public class HttpUtil {

    public static final String TAG = "OldBiliRetro";

    /** 伪装成网页端的 UA，接口都吃这一套 */
    public static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.95 Safari/537.36";

    private static final int CONNECT_TIMEOUT = 15000;
    private static final int READ_TIMEOUT = 20000;
    private static final int MAX_REDIRECT = 5;
    private static final int MAX_RETRY = 1;

    /** 响应体上限，老机器内存小，防止大 JSON 直接 OOM */
    private static final int MAX_RESPONSE_BYTES = 6 * 1024 * 1024;

    private static SSLSocketFactory sSslFactory;
    private static HostnameVerifier sVerifier;

    private static final X509TrustManager TRUST_ALL = new X509TrustManager() {
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
        }

        public void checkServerTrusted(X509Certificate[] chain, String authType) {
        }

        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    };

    static {
        try {
            sSslFactory = new TlsSocketFactoryCompat(TRUST_ALL);
            sVerifier = new HostnameVerifier() {
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            };
        } catch (Exception e) {
            Log.e(TAG, "TLS 兼容层初始化失败: " + e.getMessage());
        }
    }

    // ==================== Cookie 管理 ====================

    /** 登录 Cookie（SESSDATA 等），存 SharedPreferences */
    public static synchronized String loginCookie() {
        String cookie = Sp.getString(Sp.COOKIES, "");
        return cookie == null ? "" : cookie;
    }

    public static synchronized void saveLoginCookie(String cookie) {
        if (cookie == null || cookie.length() == 0) {
            return;
        }
        Sp.putString(Sp.COOKIES, cookie);
        // 顺手同步 csrf 与 mid，登录后点赞收藏评论都要用
        String csrf = cookieValue(cookie, "bili_jct");
        if (csrf.length() > 0) {
            Sp.putString(Sp.CSRF, csrf);
        }
        String mid = cookieValue(cookie, "DedeUserID");
        if (mid.length() > 0) {
            try {
                Sp.putLong(Sp.MID, Long.parseLong(mid));
            } catch (NumberFormatException ignored) {
            }
        }
    }

    /** 从 cookie 串里按名字取值（自动 URL 解码） */
    public static String cookieValue(String cookie, String name) {
        if (cookie == null || name == null) {
            return "";
        }
        int idx = cookie.indexOf(name + "=");
        while (idx >= 0) {
            int start = idx + name.length() + 1;
            int end = cookie.indexOf(';', start);
            if (end < 0) {
                end = cookie.length();
            }
            String value = cookie.substring(start, end).trim();
            try {
                return URLDecoder.decode(value, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                return value;
            }
        }
        return "";
    }

    /** csrf（bili_jct），优先用缓存值 */
    public static synchronized String csrf() {
        String saved = Sp.getString(Sp.CSRF, "");
        if (saved != null && saved.length() > 0) {
            return saved;
        }
        return cookieValue(loginCookie(), "bili_jct");
    }

    /** 是否已登录 */
    public static boolean isLoggedIn() {
        return loginCookie().contains("SESSDATA");
    }

    /**
     * 浏览器指纹 Cookie：buvid3 等。
     * 老设备上接口不带指纹容易被风控，这里在首次联网时合成一套存下来。
     */
    private static String sBrowserCookie;

    private static synchronized String browserCookie() {
        if (sBrowserCookie == null) {
            Map<String, String> map = new HashMap<String, String>();
            Random rnd = new Random();
            String hex = "0123456789ABCDEF";

            // buvid3/buvid4 会过期/被风控判定失效：空、超 7 天、或格式可疑时都重新抓
            String buvid3 = Sp.getString("cookie_buvid3", "");
            String buvid4 = Sp.getString("cookie_buvid4", "");
            long buvidTime = Sp.getLong("cookie_buvid3_time", 0);
            boolean stale = buvidTime == 0
                    || System.currentTimeMillis() - buvidTime > 7L * 24 * 3600 * 1000;
            if (buvid3.length() == 0 || buvid3.length() > 64 || stale) {
                String[] fresh = fetchBuvid();
                if (fresh != null && fresh[0].length() > 0) {
                    buvid3 = fresh[0];
                    if (fresh[1].length() > 0) {
                        buvid4 = fresh[1];
                    }
                    Sp.putString("cookie_buvid3", buvid3);
                    Sp.putString("cookie_buvid4", buvid4);
                    Sp.putLong("cookie_buvid3_time", System.currentTimeMillis());
                }
            }
            if (buvid3.length() > 0) {
                map.put("buvid3", buvid3);
            }
            if (buvid4.length() > 0) {
                map.put("buvid4", buvid4);
            }

            map.put("b_nut", String.valueOf(System.currentTimeMillis() / 1000));

            StringBuilder lsid = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                lsid.append(hex.charAt(rnd.nextInt(16)));
            }
            lsid.append('_').append(Long.toHexString(System.currentTimeMillis()).toUpperCase());
            map.put("b_lsid", lsid.toString());

            StringBuilder uuid = new StringBuilder();
            int[] groups = {8, 4, 4, 4, 12};
            for (int g = 0; g < groups.length; g++) {
                for (int i = 0; i < groups[g]; i++) {
                    uuid.append(hex.charAt(rnd.nextInt(16)));
                }
                if (g < groups.length - 1) {
                    uuid.append('-');
                }
            }
            uuid.append(String.format("%05d", System.currentTimeMillis() % 100000)).append("infoc");
            map.put("_uuid", uuid.toString());

            StringBuilder fp = new StringBuilder();
            String lower = "0123456789abcdef";
            for (int i = 0; i < 32; i++) {
                fp.append(lower.charAt(rnd.nextInt(16)));
            }
            map.put("buvid_fp", fp.toString());

            map.put("PVID", "1");
            map.put("CURRENT_FNVAL", "4048");
            map.put("browser_resolution", "1280-720");

            StringBuilder sb = new StringBuilder();
            for (Iterator<String> it = map.keySet().iterator(); it.hasNext(); ) {
                String key = it.next();
                if (sb.length() > 0) {
                    sb.append("; ");
                }
                sb.append(key).append('=').append(map.get(key));
            }
            sBrowserCookie = sb.toString();
        }
        return sBrowserCookie;
    }

    /** 组装本次请求要带的完整 Cookie */
    private static String buildCookie(boolean forceLogin) {
        String base = browserCookie();
        boolean incognito = Sp.getBoolean(Sp.SET_INCOGNITO, false);
        if (forceLogin) {
            incognito = false;
        }
        if (!incognito) {
            String login = loginCookie();
            if (login.length() > 0) {
                base = mergeCookie(base, login);
            }
        }
        return base;
    }

    /** CDN 防盗链用的 Cookie（登录态 + 设备指纹），播放代理/音频/下载都要带 */
    public static String cdnCookie() {
        try {
            return buildCookie(true);
        } catch (Exception e) {
            return "";
        }
    }

    /** 按名字合并 cookie，后面的覆盖前面的 */
    private static String mergeCookie(String a, String b) {
        if (b == null || b.length() == 0) {
            return a == null ? "" : a;
        }
        if (a == null || a.length() == 0) {
            return b;
        }
        Map<String, String> map = new HashMap<String, String>();
        putAll(map, a);
        putAll(map, b);
        StringBuilder sb = new StringBuilder();
        for (Iterator<String> it = map.keySet().iterator(); it.hasNext(); ) {
            String key = it.next();
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(key).append('=').append(map.get(key));
        }
        return sb.toString();
    }

    private static void putAll(Map<String, String> map, String cookie) {
        if (cookie == null) {
            return;
        }
        String[] pairs = cookie.split(";");
        for (int i = 0; i < pairs.length; i++) {
            String pair = pairs[i].trim();
            int eq = pair.indexOf('=');
            if (eq > 0) {
                map.put(pair.substring(0, eq), pair.substring(eq + 1));
            }
        }
    }

    /** buvid3 被风控判定失效时清掉（-352/-412 后调用），下次请求自动重抓 */
    public static void invalidateBuvid3() {
        Sp.putString("cookie_buvid3", "");
        Sp.putString("cookie_buvid4", "");
        Sp.putLong("cookie_buvid3_time", 0);
        sBrowserCookie = null;
        Log.w(TAG, "buvid 已作废，下次请求重新获取");
    }

    /** 抓 buvid3/buvid4：官方指纹接口优先，主站 Set-Cookie 兜底。返回 [b3, b4] */
    private static String[] fetchBuvid() {
        HttpURLConnection conn = null;
        try {
            conn = buildConnection("https://api.bilibili.com/x/frontend/finger/spi", "GET");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            int code = conn.getResponseCode();
            if (code >= 200 && code < 400) {
                String body = readBody(conn, code);
                org.json.JSONObject root = new org.json.JSONObject(new org.json.JSONTokener(body));
                if (root.optInt("code", -1) == 0) {
                    org.json.JSONObject data = root.optJSONObject("data");
                    if (data != null) {
                        String b3 = data.optString("b_3", "");
                        String b4 = data.optString("b_4", "");
                        if (b3.length() > 0) {
                            return new String[]{b3, b4};
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "spi 抓 buvid 失败: " + e.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        // 兜底：主站首页 Set-Cookie
        try {
            conn = buildConnection("https://www.bilibili.com/", "GET");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            int code = conn.getResponseCode();
            if (code >= 200 && code < 400) {
                String setCookie = collectSetCookie(conn);
                String b3 = cookieValue(setCookie, "buvid3");
                if (b3.length() > 0) {
                    return new String[]{b3, ""};
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "主站抓 buvid3 失败: " + e.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        return new String[]{"", ""};
    }

    // ==================== 基础请求 ====================

    private static HttpURLConnection buildConnection(String url, String method) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        if (url.startsWith("https") && conn instanceof HttpsURLConnection && sSslFactory != null) {
            ((HttpsURLConnection) conn).setSSLSocketFactory(sSslFactory);
            ((HttpsURLConnection) conn).setHostnameVerifier(sVerifier);
        }
        conn.setRequestMethod(method);
        conn.setConnectTimeout(CONNECT_TIMEOUT);
        conn.setReadTimeout(READ_TIMEOUT);
        conn.setUseCaches(false);
        conn.setDoInput(true);
        conn.setInstanceFollowRedirects(false);
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestProperty("Accept", "application/json, text/plain, */*");
        conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        // 不显式设置 Accept-Encoding：
        // 显式写 "gzip" 会让 Android 5+ 的 OkHttp 后端跳过自动解压，直接吐出 gzip 原始字节，
        // 所有 JSON 解析失败（老安卓透明解压所以没事——这正是"2.3 正常、新机全挂"的原因）。
        // 不设置时系统自动协商 gzip 并透明解压，新旧设备都正确且新设备仍享受压缩提速。
        conn.setRequestProperty("Referer", "https://www.bilibili.com/");
        conn.setRequestProperty("Origin", "https://www.bilibili.com");
        return conn;
    }

    /** GET 返回字符串（带 Cookie、重试、重定向） */
    public static String get(String url) throws IOException {
        return get(url, false, 0);
    }

    /** 强制携带登录 Cookie 的 GET（历史记录等必须登录的接口） */
    public static String getWithLogin(String url) throws IOException {
        return get(url, true, 0);
    }

    private static String get(String url, boolean forceLogin, int redirectCount) throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt <= MAX_RETRY; attempt++) {
            HttpURLConnection conn = null;
            try {
                conn = buildConnection(url, "GET");
                conn.setRequestProperty("Cookie", buildCookie(forceLogin));
                int code = conn.getResponseCode();
                if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                    String location = conn.getHeaderField("Location");
                    saveSetCookie(conn);
                    conn.disconnect();
                    if (location == null || redirectCount >= MAX_REDIRECT) {
                        throw new IOException("重定向次数过多: " + url);
                    }
                    if (!location.startsWith("http")) {
                        location = new URL(new URL(url), location).toString();
                    }
                    return get(location, forceLogin, redirectCount + 1);
                }
                String result = readBody(conn, code);
                saveSetCookie(conn);
                if (result != null && result.length() > 0) {
                    return result;
                }
                if (attempt < MAX_RETRY) {
                    continue;
                }
                return result == null ? "" : result;
            } catch (IOException e) {
                last = e;
                Log.w(TAG, "GET 失败(" + attempt + ") " + e.getMessage() + " : " + url);
                if (attempt >= MAX_RETRY) {
                    throw e;
                }
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }
        throw last != null ? last : new IOException("请求失败");
    }

    /** GET 直接解析成 JSONObject */
    public static JSONObject getJson(String url) throws IOException, JSONException {
        String text = get(url);
        if (text == null || text.length() == 0) {
            throw new JSONException("空响应: " + url);
        }
        return new JSONObject(new JSONTokener(sanitize(text)));
    }

    public static JSONObject getJsonWithLogin(String url) throws IOException, JSONException {
        String text = getWithLogin(url);
        if (text == null || text.length() == 0) {
            throw new JSONException("空响应: " + url);
        }
        return new JSONObject(new JSONTokener(sanitize(text)));
    }

    /** POST 表单（application/x-www-form-urlencoded） */
    public static String post(String url, String body) throws IOException {
        return post(url, body, false, 0);
    }

    public static String postWithLogin(String url, String body) throws IOException {
        return post(url, body, true, 0);
    }

    /** POST 二进制体（multipart 图片上传等），带登录 Cookie */
    public static String postBytesWithLogin(String url, byte[] body, String contentType) throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt <= MAX_RETRY; attempt++) {
            HttpURLConnection conn = null;
            OutputStream os = null;
            try {
                conn = buildConnection(url, "POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", contentType);
                conn.setRequestProperty("Cookie", buildCookie(true));
                if (body != null) {
                    os = conn.getOutputStream();
                    os.write(body);
                    os.flush();
                }
                int code = conn.getResponseCode();
                String result = readBody(conn, code);
                saveSetCookie(conn);
                return result == null ? "" : result;
            } catch (IOException e) {
                last = e;
                Log.w(TAG, "POST 二进制失败(" + attempt + ") " + e.getMessage() + " : " + url);
                if (attempt >= MAX_RETRY) {
                    throw e;
                }
            } finally {
                if (os != null) {
                    try {
                        os.close();
                    } catch (Exception ignored) {
                    }
                }
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }
        throw last != null ? last : new IOException("请求失败");
    }

    private static String post(String url, String body, boolean forceLogin, int redirectCount) throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt <= MAX_RETRY; attempt++) {
            HttpURLConnection conn = null;
            OutputStream os = null;
            try {
                conn = buildConnection(url, "POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8");
                conn.setRequestProperty("Cookie", buildCookie(forceLogin));
                if (body != null) {
                    os = conn.getOutputStream();
                    os.write(body.getBytes("UTF-8"));
                    os.flush();
                }
                int code = conn.getResponseCode();
                if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                    String location = conn.getHeaderField("Location");
                    saveSetCookie(conn);
                    conn.disconnect();
                    if (location == null || redirectCount >= MAX_REDIRECT) {
                        throw new IOException("重定向次数过多: " + url);
                    }
                    if (!location.startsWith("http")) {
                        location = new URL(new URL(url), location).toString();
                    }
                    return post(location, body, forceLogin, redirectCount + 1);
                }
                String result = readBody(conn, code);
                saveSetCookie(conn);
                return result == null ? "" : result;
            } catch (IOException e) {
                last = e;
                Log.w(TAG, "POST 失败(" + attempt + ") " + e.getMessage() + " : " + url);
                if (attempt >= MAX_RETRY) {
                    throw e;
                }
            } finally {
                if (os != null) {
                    try {
                        os.close();
                    } catch (Exception ignored) {
                    }
                }
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }
        throw last != null ? last : new IOException("请求失败");
    }

    public static JSONObject postJson(String url, String body) throws IOException, JSONException {
        String text = post(url, body);
        if (text == null || text.length() == 0) {
            throw new JSONException("空响应: " + url);
        }
        return new JSONObject(new JSONTokener(sanitize(text)));
    }

    /** 下载二进制到输出流（视频、图片），带 Referer 与重试 */
    public static boolean downloadTo(String url, OutputStream out, DownloadListener listener)
            throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt <= MAX_RETRY; attempt++) {
            HttpURLConnection conn = null;
            InputStream in = null;
            try {
                conn = buildConnection(url, "GET");
                conn.setRequestProperty("Cookie", buildCookie(false));
                conn.setRequestProperty("Connection", "close");
                int code = conn.getResponseCode();
                if (code == 301 || code == 302 || code == 307 || code == 308) {
                    String location = conn.getHeaderField("Location");
                    conn.disconnect();
                    if (location == null) {
                        throw new IOException("重定向无 Location");
                    }
                    if (!location.startsWith("http")) {
                        location = new URL(new URL(url), location).toString();
                    }
                    return downloadTo(location, out, listener);
                }
                if (code != 200) {
                    throw new IOException("HTTP " + code);
                }
                long total = conn.getContentLength();
                in = conn.getInputStream();
                byte[] buf = new byte[16384];
                long done = 0;
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                    done += n;
                    if (listener != null && total > 0) {
                        listener.onProgress(done, total);
                    }
                }
                out.flush();
                return true;
            } catch (IOException e) {
                last = e;
                Log.w(TAG, "下载失败(" + attempt + ") " + e.getMessage() + " : " + url);
                if (attempt >= MAX_RETRY) {
                    throw e;
                }
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (Exception ignored) {
                    }
                }
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }
        throw last != null ? last : new IOException("下载失败");
    }

    public interface DownloadListener {
        void onProgress(long done, long total);
    }

    /** 解析短链最终地址 */
    public static String resolveFinalUrl(String url) throws IOException {
        String current = url;
        for (int i = 0; i < MAX_REDIRECT; i++) {
            HttpURLConnection conn = buildConnection(current, "GET");
            try {
                conn.setRequestProperty("Cookie", buildCookie(false));
                int code = conn.getResponseCode();
                if (code != 301 && code != 302 && code != 307 && code != 308) {
                    break;
                }
                String location = conn.getHeaderField("Location");
                if (location == null) {
                    break;
                }
                if (!location.startsWith("http")) {
                    location = new URL(new URL(current), location).toString();
                }
                current = location;
            } finally {
                conn.disconnect();
            }
        }
        return current;
    }

    // ==================== 内部工具 ====================

    private static String readBody(HttpURLConnection conn, int code) throws IOException {
        InputStream is;
        if (code >= 400) {
            is = conn.getErrorStream();
        } else {
            is = conn.getInputStream();
        }
        if (is == null) {
            return "";
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream(32768);
        byte[] buf = new byte[8192];
        int total = 0;
        int n;
        while ((n = is.read(buf)) != -1) {
            total += n;
            if (total > MAX_RESPONSE_BYTES) {
                throw new IOException("响应过大: " + total + " 字节");
            }
            bos.write(buf, 0, n);
        }
        is.close();
        return new String(bos.toByteArray(), "UTF-8");
    }

    /** 清掉 BOM 与前置杂字符，老 org.json 解析严格 */
    private static String sanitize(String s) {
        if (s == null) {
            return "";
        }
        if (s.length() > 0 && s.charAt(0) == '\uFEFF') {
            s = s.substring(1);
        }
        s = s.trim();
        int start = -1;
        int obj = s.indexOf('{');
        int arr = s.indexOf('[');
        if (obj < 0) {
            start = arr;
        } else if (arr < 0) {
            start = obj;
        } else {
            start = obj < arr ? obj : arr;
        }
        if (start > 0) {
            s = s.substring(start);
        }
        int end = s.lastIndexOf('}');
        int endArr = s.lastIndexOf(']');
        if (endArr > end) {
            end = endArr;
        }
        if (end >= 0 && end < s.length() - 1) {
            s = s.substring(0, end + 1);
        }
        return s;
    }

    /** 收集响应里的 Set-Cookie（可能有多条） */
    private static String collectSetCookie(HttpURLConnection conn) {
        Map<String, List<String>> headers = conn.getHeaderFields();
        if (headers == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Iterator<String> it = headers.keySet().iterator(); it.hasNext(); ) {
            String key = it.next();
            if (key != null && key.equalsIgnoreCase("Set-Cookie")) {
                List<String> values = headers.get(key);
                for (int i = 0; i < values.size(); i++) {
                    String pair = extractPair(values.get(i));
                    if (pair != null && pair.length() > 0) {
                        if (sb.length() > 0) {
                            sb.append("; ");
                        }
                        sb.append(pair);
                    }
                }
            }
        }
        return sb.toString();
    }

    /** 从一条 Set-Cookie 里只留 name=value */
    private static String extractPair(String setCookie) {
        if (setCookie == null) {
            return null;
        }
        String[] parts = setCookie.split(";");
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            int eq = part.indexOf('=');
            if (eq > 0) {
                String name = part.substring(0, eq).toLowerCase();
                if ("path".equals(name) || "domain".equals(name) || "expires".equals(name)
                        || "secure".equals(name) || "httponly".equals(name)
                        || "samesite".equals(name) || "max-age".equals(name)) {
                    continue;
                }
                return part;
            }
        }
        return null;
    }

    /** 把响应 Set-Cookie 合并进登录 Cookie 存储 */
    private static synchronized void saveSetCookie(HttpURLConnection conn) {
        try {
            String setCookie = collectSetCookie(conn);
            if (setCookie == null || setCookie.length() == 0) {
                return;
            }
            String current = loginCookie();
            String merged = mergeCookie(current, setCookie);
            if (!merged.equals(current)) {
                saveLoginCookie(merged);
                Log.d(TAG, "Cookie 已更新");
            }
            // buvid3 也存到指纹区
            String buvid3 = cookieValue(setCookie, "buvid3");
            if (buvid3.length() > 0 && Sp.getString("cookie_buvid3", "").length() == 0) {
                Sp.putString("cookie_buvid3", buvid3);
            }
        } catch (Exception ignored) {
        }
    }

    /** URL 编码（UTF-8） */
    public static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return value;
        }
    }

    /** 下载二进制（图片等），失败返回 null */
    public static byte[] getBytes(String url) {
        HttpURLConnection conn = null;
        try {
            conn = buildConnection(url, "GET");
            int code = conn.getResponseCode();
            if (code < 200 || code >= 400) {
                return null;
            }
            InputStream in = conn.getInputStream();
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                bos.write(buf, 0, n);
            }
            in.close();
            return bos.toByteArray();
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    // ==================== api.vc 老设备网关隧道 ====================
    //
    // 背景：api.vc.bilibili.com（私信接口域名）服务端已禁用 TLS1.0/1.1，只收 TLS1.2+。
    // 安卓 2.3/4.0 的 OpenSSL 最高只有 TLS1.0，协议层不可能握手成功 → 私信永远转圈。
    // 实测 api.bilibili.com 的网关按 Host 头路由：用主域 IP 建 TLS 连接，
    // 请求里写 Host: api.vc.bilibili.com，网关照样转发到私信后端（主域允许老 TLS）。
    // 因此老设备上的 vc 请求全部改走这个隧道。

    /** vc 域 GET：老设备走主域网关隧道，新设备直连 */
    public static String vcGet(String url, boolean withLogin) throws IOException {
        if (android.os.Build.VERSION.SDK_INT >= 17) {
            return withLogin ? getWithLogin(url) : get(url);
        }
        return gatewayRequest("GET", url, null, withLogin);
    }

    /** vc 域 POST（表单）：老设备走主域网关隧道，新设备直连 */
    public static String vcPost(String url, String body, boolean withLogin) throws IOException {
        if (android.os.Build.VERSION.SDK_INT >= 17) {
            return withLogin ? postWithLogin(url, body) : post(url, body);
        }
        return gatewayRequest("POST", url, body, withLogin);
    }

    /** 主域网关隧道：SSLSocket 连 api.bilibili.com:443，Host 头写 vc 域名 */
    private static String gatewayRequest(String method, String url, String body, boolean withLogin)
            throws IOException {
        javax.net.ssl.SSLSocket sock = null;
        try {
            // 拆 URL：拿路径与查询串
            int schemeEnd = url.indexOf("://");
            String rest = url.substring(schemeEnd + 3);
            String path = rest.substring(rest.indexOf('/'));

            String host = "api.bilibili.com";
            if (sSslFactory == null) {
                throw new IOException("SSL 工厂未初始化");
            }
            sock = (javax.net.ssl.SSLSocket) sSslFactory.createSocket(host, 443);
            sock.setSoTimeout(30000);
            sock.startHandshake();

            StringBuilder req = new StringBuilder();
            req.append(method).append(" ").append(path).append(" HTTP/1.1\r\n");
            req.append("Host: api.vc.bilibili.com\r\n");
            req.append("User-Agent: ").append(USER_AGENT).append("\r\n");
            req.append("Accept: application/json, text/plain, */*\r\n");
            req.append("Accept-Language: zh-CN,zh;q=0.9\r\n");
            req.append("Referer: https://www.bilibili.com/\r\n");
            req.append("Origin: https://www.bilibili.com\r\n");
            String cookie = buildCookie(withLogin);
            if (cookie.length() > 0) {
                req.append("Cookie: ").append(cookie).append("\r\n");
            }
            if ("POST".equals(method)) {
                req.append("Content-Type: application/x-www-form-urlencoded\r\n");
                byte[] bodyBytes = body == null ? new byte[0] : body.getBytes("UTF-8");
                req.append("Content-Length: ").append(bodyBytes.length).append("\r\n");
                req.append("Connection: close\r\n\r\n");
                java.io.OutputStream out = sock.getOutputStream();
                out.write(req.toString().getBytes("UTF-8"));
                out.write(bodyBytes);
                out.flush();
            } else {
                req.append("Connection: close\r\n\r\n");
                sock.getOutputStream().write(req.toString().getBytes("UTF-8"));
                sock.getOutputStream().flush();
            }

            // 读响应：优先按 Content-Length，否则读到 EOF（Connection: close）
            // 2.3 的 SSL 层逐字节读极慢（每次 read(1) 都触发 SSL_Read），必须用缓冲流
            java.io.BufferedInputStream in = new java.io.BufferedInputStream(sock.getInputStream(), 8192);
            String firstLine = readHeaderLine(in);
            if (firstLine == null) {
                throw new IOException("网关响应头为空");
            }
            android.util.Log.d("HttpUtil", "网关状态行: " + firstLine);
            int contentLength = -1;
            boolean chunked = false;
            while (true) {
                String line = readHeaderLine(in);
                if (line == null || line.length() == 0) {
                    break;
                }
                String lower = line.toLowerCase();
                if (lower.startsWith("content-length:")) {
                    try {
                        contentLength = Integer.parseInt(line.substring(15).trim());
                    } catch (Exception ignored) {
                    }
                } else if (lower.startsWith("transfer-encoding:")
                        && lower.contains("chunked")) {
                    chunked = true;
                }
            }
            if (!firstLine.contains(" 200")) {
                throw new IOException("网关 HTTP 状态异常: " + firstLine);
            }
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            if (contentLength > 0 && !chunked) {
                int remain = contentLength;
                while (remain > 0) {
                    int n = in.read(buf, 0, Math.min(buf.length, remain));
                    if (n <= 0) {
                        break;
                    }
                    bos.write(buf, 0, n);
                    remain -= n;
                }
            } else if (chunked) {
                // 按 chunk 协议逐块读：size 行 + 数据 + CRLF，读到 0 块即停，
                // 不依赖服务器关闭连接（否则 keep-alive 时会读到超时）
                while (true) {
                    String sizeLine = readHeaderLine(in);
                    if (sizeLine == null) {
                        break;
                    }
                    sizeLine = sizeLine.trim();
                    if (sizeLine.length() == 0) {
                        continue;
                    }
                    int chunkSize;
                    try {
                        chunkSize = Integer.parseInt(sizeLine.split(";")[0], 16);
                    } catch (NumberFormatException e) {
                        break;
                    }
                    if (chunkSize <= 0) {
                        readHeaderLine(in); // 终止块后的空行
                        break;
                    }
                    int remain = chunkSize;
                    while (remain > 0) {
                        int n = in.read(buf, 0, Math.min(buf.length, remain));
                        if (n <= 0) {
                            break;
                        }
                        bos.write(buf, 0, n);
                        remain -= n;
                    }
                    readHeaderLine(in); // 块尾 CRLF
                }
            } else {
                int n;
                while ((n = in.read(buf)) != -1) {
                    bos.write(buf, 0, n);
                }
            }
            final byte[] bodyBytes = bos.toByteArray();
            String bodyRaw = new String(bodyBytes, "UTF-8");
            android.util.Log.d("HttpUtil", "网关响应: len=" + contentLength
                    + " chunked=" + chunked + " bodyLen=" + bodyRaw.length());
            return bodyRaw;
        } finally {
            if (sock != null) {
                try {
                    sock.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /** 从缓冲流读一行 HTTP 头（\r\n 或 \n 结尾），ISO-8859-1 避免中文头问题 */
    private static String readHeaderLine(java.io.BufferedInputStream in) throws IOException {
        java.io.ByteArrayOutputStream line = new java.io.ByteArrayOutputStream(64);
        boolean any = false;
        while (true) {
            int b = in.read();
            if (b < 0) {
                return any ? line.toString("ISO-8859-1") : null;
            }
            any = true;
            if (b == '\n') {
                String s = line.toString("ISO-8859-1");
                if (s.endsWith("\r")) {
                    s = s.substring(0, s.length() - 1);
                }
                return s;
            }
            line.write(b);
        }
    }
}
