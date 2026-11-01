package com.android.oldbili.retro.widget;

import android.content.SharedPreferences;
import android.preference.ListPreference;

/**
 * 容错的 ListPreference：历史版本可能把同一个 key 存成了 int/boolean，
 * 原版 ListPreference 读到非 String 会直接抛 ClassCastException 导致设置页崩溃。
 * 这里读到旧类型时自动转成字符串并写回，避免崩溃。
 */
public class SafeListPreference extends ListPreference {

    public SafeListPreference(android.content.Context context,
                              android.util.AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected String getPersistedString(String defaultReturnValue) {
        try {
            SharedPreferences sp = getSharedPreferences();
            if (sp != null) {
                Object v = sp.getAll().get(getKey());
                if (v instanceof Integer || v instanceof Long || v instanceof Boolean) {
                    String s;
                    if (v instanceof Boolean) {
                        s = ((Boolean) v) ? "1" : "0";
                    } else {
                        s = String.valueOf(v);
                    }
                    sp.edit().putString(getKey(), s).apply();
                    return s;
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            return super.getPersistedString(defaultReturnValue);
        } catch (Throwable e) {
            try {
                getSharedPreferences().edit().remove(getKey()).apply();
            } catch (Throwable ignored) {
            }
            return defaultReturnValue;
        }
    }
}
