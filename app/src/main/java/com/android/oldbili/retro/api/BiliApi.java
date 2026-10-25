package com.android.oldbili.retro.api;

import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

/**
 * 接口公共基类：结果检查、WBI 签名、画质名。
 */
public class BiliApi {

    /** 检查接口返回，成功取 data，失败抛带提示的异常 */
    public static JSONObject check(JSONObject resp) throws IOException, org.json.JSONException {
        if (resp == null) {
            throw new IOException("接口返回为空");
        }
        int code = resp.optInt("code", -1);
        if (code != 0) {
            String msg = resp.optString("message", "");
            // -352 风控 / -412 拦截 / -403 权限（多为同族风控）：buvid 失效居多，清掉下次重抓
            if (code == -352 || code == -412 || code == -403) {
                com.android.oldbili.retro.net.HttpUtil.invalidateBuvid3();
            }
            throw new IOException("接口错误(" + code + ") " + msg);
        }
        JSONObject data = resp.optJSONObject("data");
        if (data == null) {
            data = new JSONObject();
        }
        return data;
    }

    /** 检查接口返回，成功取 data 数组 */
    public static org.json.JSONArray checkArray(JSONObject resp) throws IOException, org.json.JSONException {
        if (resp == null) {
            throw new IOException("接口返回为空");
        }
        int code = resp.optInt("code", -1);
        if (code != 0) {
            String msg = resp.optString("message", "");
            throw new IOException("接口错误(" + code + ") " + msg);
        }
        org.json.JSONArray data = resp.optJSONArray("data");
        if (data == null) {
            throw new IOException("接口返回无列表数据");
        }
        return data;
    }

    /** qn 画质值 -> 名字 */
    public static String qualityName(int qn) {
        switch (qn) {
            case 6:
                return "240P 极速";
            case 16:
                return "360P 流畅";
            case 32:
                return "480P 清晰";
            case 64:
                return "720P 高清";
            case 80:
                return "1080P 高清";
            case 112:
                return "1080P 高码率";
            case 116:
                return "1080P60 帧";
            case 74:
                return "720P60 帧";
            case 120:
                return "4K 超清";
            default:
                return qn + "P";
        }
    }

    // ==================== WBI 签名 ====================

    /** nav 接口下发的混排表（公开算法常量） */
    private static final int[] MIXIN_TAB = {
            46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35,
            27, 43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13,
            37, 48, 7, 16, 24, 55, 40, 61, 26, 17, 0, 1, 60, 51, 30, 4,
            22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36, 20, 34, 44, 52
    };

    private static String sMixinKey = "";
    private static long sWbiDate = 0;

    /** 取当日 WBI 混合密钥：一天一取（持久化，冷启动当天免掉一次 nav 请求） */
    private static synchronized String mixinKey() {
        int today = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) * 10000
                + (java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1) * 100
                + java.util.Calendar.getInstance().get(java.util.Calendar.DATE);
        if (sMixinKey.length() == 0) {
            // 先读持久化缓存
            long savedDate = com.android.oldbili.retro.util.Sp.getLong("wbi_date", 0);
            String savedKey = com.android.oldbili.retro.util.Sp.getString("wbi_key", "");
            if (savedDate == today && savedKey.length() == 32) {
                sMixinKey = savedKey;
                sWbiDate = today;
            }
        }
        if (sMixinKey.length() > 0 && sWbiDate == today) {
            return sMixinKey;
        }
        try {
            String nav = com.android.oldbili.retro.net.HttpUtil.get(
                    "https://api.bilibili.com/x/web-interface/nav");
            // nav 对未登录请求现在回 code=-101，但 data.wbi_img 照样下发——不能走 check()
            JSONObject root = new JSONObject(new org.json.JSONTokener(nav));
            JSONObject data = root.optJSONObject("data");
            if (data == null) {
                data = new JSONObject();
            }
            JSONObject wbi = data.optJSONObject("wbi_img");
            if (wbi == null) {
                throw new IOException("nav 无 wbi_img");
            }
            String img = firstName(wbi.optString("img_url", ""));
            String sub = firstName(wbi.optString("sub_url", ""));
            if (img.length() == 0 || sub.length() == 0 || img.length() + sub.length() < 60) {
                throw new IOException("wbi_img 不完整");
            }
            String raw = img + sub;
            StringBuilder key = new StringBuilder();
            for (int i = 0; i < 32; i++) {
                key.append(raw.charAt(MIXIN_TAB[i]));
            }
            sMixinKey = key.toString();
            sWbiDate = today;
            // 持久化，进程重启当天不用再请求 nav
            com.android.oldbili.retro.util.Sp.putString("wbi_key", sMixinKey);
            com.android.oldbili.retro.util.Sp.putLong("wbi_date", today);
        } catch (Exception e) {
            android.util.Log.w("BiliApi", "取 WBI 密钥失败: " + e.getMessage());
            // 拿不到就用兜底，尽量不挡用户
            if (sMixinKey.length() == 0) {
                sMixinKey = "604f662d63f4ee19c94bd8ac0de3f84d";
            }
        }
        return sMixinKey;
    }

    private static String firstName(String url) {
        int slash = url.lastIndexOf('/');
        if (slash >= 0) {
            url = url.substring(slash + 1);
        }
        int dot = url.indexOf('.');
        if (dot >= 0) {
            url = url.substring(0, dot);
        }
        return url;
    }

    /** 对带 query 的 URL 做 WBI 签名，返回可直接请求的完整 URL */
    public static String signWbi(String urlWithQuery) throws IOException, org.json.JSONException {
        String base;
        String query;
        int q = urlWithQuery.indexOf('?');
        if (q >= 0) {
            base = urlWithQuery.substring(0, q + 1);
            query = urlWithQuery.substring(q + 1);
        } else {
            base = urlWithQuery + "?";
            query = "";
        }

        String wts = String.valueOf(System.currentTimeMillis() / 1000);
        StringBuilder paramStr = new StringBuilder(query);
        if (paramStr.length() > 0) {
            paramStr.append('&');
        }
        paramStr.append("wts=").append(wts);

        String sorted = sortParams(filterParam(paramStr.toString()));
        String wRid = com.android.oldbili.retro.util.Md5Util.md5(sorted + mixinKey());
        return base + sorted + "&w_rid=" + wRid;
    }

    /** WBI 要求参数值过滤 !'()* */
    private static String filterParam(String paramStr) {
        if (paramStr.indexOf('!') < 0 && paramStr.indexOf('\'') < 0 && paramStr.indexOf('(') < 0
                && paramStr.indexOf(')') < 0 && paramStr.indexOf('*') < 0) {
            return paramStr;
        }
        StringBuilder sb = new StringBuilder();
        String[] params = paramStr.split("&");
        for (int i = 0; i < params.length; i++) {
            String param = params[i];
            int eq = param.indexOf('=');
            if (eq >= 0) {
                String name = param.substring(0, eq);
                String value = param.substring(eq + 1);
                try {
                    value = java.net.URLDecoder.decode(value, "UTF-8");
                    value = value.replace("!", "").replace("'", "")
                            .replace("(", "").replace(")", "").replace("*", "");
                    value = java.net.URLEncoder.encode(value, "UTF-8");
                } catch (Exception ignored) {
                }
                param = name + "=" + value;
            }
            if (i > 0) {
                sb.append('&');
            }
            sb.append(param);
        }
        return sb.toString();
    }

    /** 参数按 key 字典序排列 */
    private static String sortParams(String query) {
        if (query == null || query.length() == 0) {
            return "";
        }
        Map<String, String> map = new HashMap<String, String>();
        String[] params = query.split("&");
        for (int i = 0; i < params.length; i++) {
            String param = params[i];
            if (param.length() == 0) {
                continue;
            }
            String[] kv = param.split("=", 2);
            map.put(kv[0], kv.length > 1 ? kv[1] : "");
        }
        Map<String, String> sorted = new TreeMap<String, String>(map);
        StringBuilder sb = new StringBuilder();
        for (Iterator<Map.Entry<String, String>> it = sorted.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, String> entry = it.next();
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return sb.toString();
    }
}
