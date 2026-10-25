package com.android.oldbili.retro.net;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * B 站网页接口的风控参数（dm_img_*）。
 * 现在不少 WBI 接口不带这组参数会直接回 -352 风控校验失败 / -403 访问权限不足，
 * 老客户端照搬网页端的这组"指纹"即可。纯 Java 实现，安卓2 上无压力。
 */
public class RiskParams {

    /** 生成参数表，调用方自行拼 URL 或参与签名 */
    public static Map<String, String> build() {
        Map<String, String> map = new HashMap<String, String>();
        map.put("dm_img_str", "V2ViR0wgMS4wIChPcGVuR0wgRVMgMi4wIENocm9taXVtKQ");
        map.put("dm_cover_img_str",
                "QU5HTEUgKEludGVsLCBJbnRlbChSKSBVSEQgR3JhcGhpY3MgNjMwIERpcmVjdDNEMTEgdnNfNV8wIHBzXzVfMCwgRDNEMTEpR29vZ2xlIEluYy4gKEludGVsKQ");
        map.put("dm_img_list", buildImgList());
        map.put("dm_img_inter", buildImgInter());
        return map;
    }

    /** 把风控参数追加到 URL（WBI 签名之前调用） */
    public static String append(String url) {
        Map<String, String> params = build();
        StringBuilder sb = new StringBuilder(url);
        sb.append(url.indexOf('?') >= 0 ? '&' : '?');
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!first) {
                sb.append('&');
            }
            first = false;
            sb.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return sb.toString();
    }

    // ---- 与网页端同款的伪随机轨迹 ----

    private static final int[] TIMESTAMPS = {2943, 3046, 3152, 3252, 3354, 3454, 3558, 3665, 3767,
            5566, 5676, 5778, 5881, 7296, 7573, 135289};

    private static String buildImgList() {
        try {
            Random random = new Random();
            JSONArray list = new JSONArray();
            list.put(new JSONObject()
                    .put("x", 2355)
                    .put("y", 725)
                    .put("z", 0)
                    .put("timestamp", 2145)
                    .put("k", random.nextInt(68) + 60)
                    .put("type", 0));
            int num = random.nextInt(TIMESTAMPS.length - 6) + 6;
            for (int i = 0; i <= num; i++) {
                int[] xyz = f114i(random.nextInt(200), random.nextInt(200), i + 1);
                list.put(new JSONObject()
                        .put("x", xyz[0])
                        .put("y", xyz[1])
                        .put("z", xyz[2])
                        .put("timestamp", TIMESTAMPS[i])
                        .put("k", random.nextInt(68) + 60)
                        .put("type", i == num ? 1 : 0));
            }
            return list.toString();
        } catch (Exception e) {
            return "[]";
        }
    }

    private static final String[] CLASS_NAMES = {"clearfix", "g-search", "section", "loading",
            "full-rows", "n-btn", "router-link-exact-active", "router-link-active", "active"};

    private static String buildImgInter() {
        try {
            Random random = new Random();
            int y = randBetween(random, -1500, -300);
            int x = randBetween(random, 100, 700);
            int width = randBetween(random, 100, 8000);
            int height = randBetween(random, 20, 500);
            int[] xyz1 = f114(y, x);
            int[] xyz2 = f514(width, height);

            JSONArray ds = new JSONArray();
            ds.put(new JSONObject()
                    .put("t", random.nextInt(6))
                    .put("c", base64((CLASS_NAMES[random.nextInt(CLASS_NAMES.length)] + " "
                            + CLASS_NAMES[random.nextInt(CLASS_NAMES.length)]).getBytes("UTF-8")))
                    .put("p", new JSONArray().put(xyz1[0]).put(xyz1[2]).put(xyz1[1]))
                    .put("s", new JSONArray().put(xyz2[2]).put(xyz2[0]).put(xyz2[1])));

            JSONObject result = new JSONObject();
            result.put("ds", ds);
            result.put("wh", new JSONArray().put(f114(width, height)));
            result.put("of", new JSONArray().put(f514(y, x)));
            return result.toString();
        } catch (Exception e) {
            return "{\"ds\":[],\"wh\":[0,0,0],\"of\":[0,0,0]}";
        }
    }

    private static int[] f114i(int a, int b, int i) {
        Random random = new Random();
        int t = (int) (random.nextDouble() * (114 * i));
        return new int[]{3 * a + 2 * b + t, 4 * a - 5 * b + t, t};
    }

    private static int[] f114(int a, int b) {
        Random random = new Random();
        int t = (int) (random.nextDouble() * 114);
        return new int[]{2 * a + 2 * b + 3 * t, 4 * a - b + t, t};
    }

    private static int[] f514(int a, int b) {
        Random random = new Random();
        int t = (int) (random.nextDouble() * 514);
        return new int[]{3 * a + 2 * b + t, 4 * a - 4 * b + 2 * t, t};
    }

    private static int randBetween(Random random, int min, int max) {
        return random.nextInt(max - min + 1) + min;
    }

    /** RFC 4648 Base64（老 Android 上没有 android.util.Base64，自己写） */
    private static final char[] BASE64_CHARS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();

    private static String base64(byte[] data) {
        if (data == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder((data.length + 2) / 3 * 4);
        int i = 0;
        while (i < data.length) {
            int b0 = data[i++] & 0xFF;
            int b1 = i < data.length ? data[i++] & 0xFF : -1;
            int b2 = i < data.length ? data[i++] & 0xFF : -1;
            sb.append(BASE64_CHARS[b0 >> 2]);
            sb.append(BASE64_CHARS[((b0 & 0x03) << 4) | (b1 == -1 ? 0 : (b1 >> 4))]);
            sb.append(b1 == -1 ? '=' : BASE64_CHARS[((b1 & 0x0F) << 2) | (b2 == -1 ? 0 : (b2 >> 6))]);
            sb.append(b2 == -1 ? '=' : BASE64_CHARS[b2 & 0x3F]);
        }
        return sb.toString();
    }
}
