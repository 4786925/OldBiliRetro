package com.android.oldbili.retro.util;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * 内置字体管理：系统 / 楷体（GB2312 子集）。
 * 通过把 LayoutInflater.Factory 装到 Activity 的 base context 上，
 * 使所有 cloneInContext 出来的 inflater（含适配器）都套上字体。
 */
public class FontManager {

    /** 字体切换功能暂时关闭（预留，后续可重新开启） */
    public static final boolean ENABLED = false;

    public static final String FONT_SYSTEM = "system";
    public static final String FONT_KAI = "kai";

    private static final ThreadLocal<Boolean> IN_FACTORY = new ThreadLocal<Boolean>();
    private static String sCachedName;
    private static Typeface sCachedTypeface;

    public static String currentName() {
        if (!ENABLED) {
            return FONT_SYSTEM;
        }
        String v = Sp.getString(Sp.SET_FONT, FONT_SYSTEM);
        return v == null ? FONT_SYSTEM : v;
    }

    public static Typeface get(Context context) {
        String name = currentName();
        if (FONT_SYSTEM.equals(name) || context == null) {
            return null;
        }
        if (name.equals(sCachedName) && sCachedTypeface != null) {
            return sCachedTypeface;
        }
        try {
            Typeface tf = Typeface.createFromAsset(context.getAssets(), "fonts/" + name + ".otf");
            sCachedName = name;
            sCachedTypeface = tf;
            return tf;
        } catch (Throwable e) {
            return null;
        }
    }

    /** 给某个 Activity 安装字体工厂（覆盖它自己的 inflater 与所有 clone） */
    public static void installFactory(final Activity activity) {
        if (activity == null || FONT_SYSTEM.equals(currentName())) {
            return;
        }
        try {
            final LayoutInflater base = (LayoutInflater) activity.getBaseContext()
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            if (base == null) {
                return;
            }
            final LayoutInflater delegate = activity.getLayoutInflater();
            base.setFactory(new LayoutInflater.Factory() {
                @Override
                public View onCreateView(String name, Context context,
                                         android.util.AttributeSet attrs) {
                    return create(delegate, name, context, attrs);
                }
            });
        } catch (Throwable ignored) {
            // 已经有 factory 的 inflater 会被忽略
        }
    }

    private static View create(LayoutInflater delegate, String name, Context context,
                               android.util.AttributeSet attrs) {
        if (Boolean.TRUE.equals(IN_FACTORY.get())) {
            return null;
        }
        IN_FACTORY.set(Boolean.TRUE);
        try {
            View v = delegate.createView(name, null, attrs);
            if (v instanceof TextView) {
                Typeface tf = get(context);
                if (tf != null) {
                    ((TextView) v).setTypeface(tf);
                }
            }
            return v;
        } catch (Throwable e) {
            return null;
        } finally {
            IN_FACTORY.remove();
        }
    }

    public static void applyTo(View root, Context context) {
        Typeface tf = get(context);
        if (tf == null || root == null) {
            return;
        }
        applyTo(root, tf);
    }

    private static void applyTo(View view, Typeface tf) {
        if (view instanceof TextView) {
            TextView tv = (TextView) view;
            Typeface old = tv.getTypeface();
            int style = old == null ? Typeface.NORMAL : old.getStyle();
            tv.setTypeface(tf, style);
        }
        if (view instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) view;
            for (int i = 0; i < g.getChildCount(); i++) {
                applyTo(g.getChildAt(i), tf);
            }
        }
    }

    public static void apply(TextView tv, Context context) {
        Typeface tf = get(context);
        if (tf != null && tv != null) {
            Typeface old = tv.getTypeface();
            int style = old == null ? Typeface.NORMAL : old.getStyle();
            tv.setTypeface(tf, style);
        }
    }

    /** 给指定 inflater（例如适配器的 LayoutInflater）装字体工厂，inflate 出来即带字体 */
    public static void setFactory(final LayoutInflater inflater, final Context context) {
        if (inflater == null || FONT_SYSTEM.equals(currentName())) {
            return;
        }
        try {
            final LayoutInflater delegate = inflater.cloneInContext(context);
            inflater.setFactory(new LayoutInflater.Factory() {
                @Override
                public View onCreateView(String name, Context ctx,
                                         android.util.AttributeSet attrs) {
                    return create(delegate, name, ctx, attrs);
                }
            });
        } catch (Throwable ignored) {
        }
    }
}
