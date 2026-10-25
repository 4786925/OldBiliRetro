package com.android.oldbili.retro.api;

import com.android.oldbili.retro.model.UserInfo;
import com.android.oldbili.retro.net.HttpUtil;

import org.json.JSONObject;

import java.io.IOException;

/**
 * 登录：扫码登录（二维码 + 轮询）、登录状态检查。
 */
public class LoginApi {

    public static final int POLL_WAIT = 86101;     // 未扫码
    public static final int POLL_SCANNED = 86090;  // 已扫码未确认
    public static final int POLL_EXPIRED = 86038;  // 已过期

    /** 生成登录二维码，返回 [二维码内容URL, qrcode_key] */
    public static String[] generateQr() throws IOException, org.json.JSONException {
        JSONObject data = BiliApi.check(HttpUtil.getJson(
                "https://passport.bilibili.com/x/passport-login/web/qrcode/generate"
                        + "?source=main-fe-header&go_url=https%3A%2F%2Fwww.bilibili.com%2F"));
        String url = data.optString("url", "");
        String key = data.optString("qrcode_key", "");
        if (key.length() == 0) {
            throw new IOException("二维码生成失败");
        }
        return new String[]{url, key};
    }

    /**
     * 轮询扫码状态。
     * 返回数组 [code, message]，code=0 表示登录成功（Cookie 已由网络层自动保存）。
     */
    public static int[] pollQr(String key) throws IOException, org.json.JSONException {
        JSONObject data = BiliApi.check(HttpUtil.getJson(
                "https://passport.bilibili.com/x/passport-login/web/qrcode/poll"
                        + "?source=main-fe-header&qrcode_key=" + key));
        int code = data.optInt("code", -1);
        String message = data.optString("message", "");
        return new int[]{code, message.hashCode()};
    }

    /** 登录状态检查：返回已登录的用户信息，失败返回 null */
    public static UserInfo checkLogin() {
        try {
            JSONObject data = BiliApi.check(HttpUtil.getJsonWithLogin(
                    "https://api.bilibili.com/x/web-interface/nav"));
            UserInfo user = new UserInfo();
            user.mid = data.optLong("mid", 0);
            user.name = data.optString("uname", "");
            user.face = data.optString("face", "");
            user.sign = data.optString("sign", "");
            JSONObject level = data.optJSONObject("level_info");
            if (level != null) {
                user.level = level.optInt("current_level", 0);
            }
            if (data.optBoolean("isLogin", false) || user.mid > 0) {
                return user;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /** 退出登录：清空本地登录信息 */
    public static void logout() {
        com.android.oldbili.retro.util.Sp.putString(com.android.oldbili.retro.util.Sp.COOKIES, "");
        com.android.oldbili.retro.util.Sp.putString(com.android.oldbili.retro.util.Sp.CSRF, "");
        com.android.oldbili.retro.util.Sp.putLong(com.android.oldbili.retro.util.Sp.MID, 0);
    }
}
