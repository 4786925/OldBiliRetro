package com.android.oldbili.retro.danmaku;

import android.content.Context;
import android.graphics.Color;
import android.view.View;

import com.android.oldbili.retro.net.HttpUtil;
import com.android.oldbili.retro.util.Sp;

import master.flame.danmaku.controller.IDanmakuView;
import master.flame.danmaku.danmaku.model.BaseDanmaku;
import master.flame.danmaku.danmaku.model.DanmakuTimer;
import master.flame.danmaku.danmaku.model.IDanmakus;
import master.flame.danmaku.danmaku.model.IDisplayer;
import master.flame.danmaku.danmaku.model.android.DanmakuGlobalConfig;
import master.flame.danmaku.danmaku.model.android.Danmakus;
import master.flame.danmaku.danmaku.parser.BaseDanmakuParser;
import master.flame.danmaku.danmaku.parser.DanmakuFactory;
import master.flame.danmaku.ui.widget.DanmakuView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 弹幕引擎封装：DanmakuFlameMaster 完整版 + B站 XML 弹幕解析。
 * 弹幕列表接口：comment.bilibili.com/{cid}.xml
 */
public class DanmakuHelper {

    /** 按设置项组装弹幕引擎参数（描边+阴影，完整版功能全开；低配自动降级） */
    public static void applyConfig() {
        DanmakuGlobalConfig config = DanmakuGlobalConfig.DEFAULT;

        // 低配设备自动降级：关描边、限弹幕总数、限行数，保证流畅
        // 安卓 2（SDK<11）强制低端模式：默认 8 行 + 关闭描边
        boolean lowEnd = com.android.oldbili.retro.util.DeviceCompat.isLowEnd()
                || Runtime.getRuntime().maxMemory() < 96L * 1024 * 1024;
        if (lowEnd) {
            config.setDanmakuStyle(DanmakuGlobalConfig.DANMAKU_STYLE_NONE);
            config.setMaximumVisibleSizeInScreen(300);
            config.setDuplicateMergingEnabled(true);
        } else {
            // 描边，弹幕字号固定按 25（DANMAKU_MEDIUM_TEXTSIZE）基准再乘用户倍率
            config.setDanmakuStyle(DanmakuGlobalConfig.DANMAKU_STYLE_STROKEN, 3f);
        }

        float alpha = Sp.getFloat(Sp.SET_DANMAKU_ALPHA, 0.6f);
        if (alpha <= 0f) {
            alpha = 0.6f;
        }
        config.setDanmakuTransparency(alpha);

        float scale = Sp.getFloat(Sp.SET_DANMAKU_SIZE, 1.0f);
        if (scale <= 0f) {
            scale = 1.0f;
        }
        config.setScaleTextSize(scale);

        float speed = Sp.getFloat(Sp.SET_DANMAKU_SPEED, 1.0f);
        if (speed <= 0f) {
            speed = 1.0f;
        }
        config.setScrollSpeedFactor(speed);

        config.setDuplicateMergingEnabled(Sp.getBoolean(Sp.SET_DANMAKU_MERGE, false)
                || lowEnd);

        // 默认弹幕不重叠；设置里开了"允许重叠"才放开
        if (!Sp.getBoolean(Sp.SET_DANMAKU_OVERLAP, false)) {
            config.preventOverlapping(null);
        }

        // 弹幕行数：默认 0 = 不设上限，引擎按屏幕高度自动铺满全部区域
        // （以前默认 10 行，弹幕全部挤在屏幕最上面，观感像"全在顶部"）
        int maxLines = Sp.getInt(Sp.SET_DANMAKU_MAXLINE, 0);
        if (lowEnd && (maxLines == 0 || maxLines > 8)) {
            maxLines = 8;
        }
        if (maxLines > 0) {
            Map<Integer, Integer> lines = new HashMap<Integer, Integer>();
            lines.put(BaseDanmaku.TYPE_SCROLL_RL, maxLines);
            lines.put(BaseDanmaku.TYPE_SCROLL_LR, maxLines);
            lines.put(BaseDanmaku.TYPE_FIX_TOP, maxLines);
            lines.put(BaseDanmaku.TYPE_FIX_BOTTOM, maxLines);
            config.setMaximumLines(lines);
        }
    }

    /**
     * 下载并解析弹幕，返回 DanmakuView 用的解析器。
     * 优先走现行 protobuf 接口 seg.so（游客可用），失败回退老 XML（已下线，兜底）。
     */
    public static BaseDanmakuParser fetchParser(final long cid, long durationMs) throws IOException {
        // 低端机：拉取阶段就限量（解析阶段的峰值内存同样会 OOM）
        boolean lowEnd = com.android.oldbili.retro.util.DeviceCompat.isLowEnd()
                || Runtime.getRuntime().maxMemory() < 96L * 1024 * 1024;
        List<String[]> fields = com.android.oldbili.retro.api.DanmakuApi.fetchDanmaku(
                cid, durationMs, lowEnd ? 2000 : 0);
        if (fields.size() == 0) {
            // 兜底：老 XML 接口（2026 已下线，仅做后备）
            String xml = HttpUtil.get("https://comment.bilibili.com/" + cid + ".xml");
            android.util.Log.i("Danmaku", "seg.so 无弹幕，走 XML 兜底 cid=" + cid);
            return parseXml(xml);
        }
        android.util.Log.i("Danmaku", "弹幕加载成功 cid=" + cid + " 条数=" + fields.size());
        return new ListParser(fields);
    }

    /**
     * 低端机（安卓 2/内存 <96MB）弹幕裁剪：
     * 老机 dalvik 堆只有 48~64MB，几万条弹幕对象直接 OOM 被杀。
     * 均匀抽样保留最多 2000 条，观感损失小、内存安全。
     */
    private static List<String[]> trimForLowEnd(List<String[]> fields) {
        boolean lowEnd = com.android.oldbili.retro.util.DeviceCompat.isLowEnd()
                || Runtime.getRuntime().maxMemory() < 96L * 1024 * 1024;
        if (!lowEnd || fields.size() <= 2000) {
            return fields;
        }
        List<String[]> out = new ArrayList<String[]>();
        int step = fields.size() / 2000;
        for (int i = 0; i < fields.size(); i += step) {
            out.add(fields.get(i));
            if (out.size() >= 2000) {
                break;
            }
        }
        android.util.Log.i("Danmaku", "低端机弹幕裁剪 " + fields.size() + " -> " + out.size());
        return out;
    }

    /**
     * 解析 comment.bilibili.com 的 XML 弹幕：
     * <d p="时间,模式,字号,颜色,时间戳,弹幕池,用户ID,行ID">弹幕内容</d>
     * 真正的弹幕对象等播放器 prepare 时（拿到显示尺寸后）才创建，保证字号密度正确。
     */
    public static BaseDanmakuParser parseXml(String xml) {
        List<String[]> fields = new ArrayList<String[]>();
        if (xml == null || xml.length() == 0) {
            return new ListParser(fields);
        }
        int start = 0;
        while (true) {
            int tagStart = xml.indexOf("<d ", start);
            if (tagStart < 0) {
                break;
            }
            int tagEnd = xml.indexOf(">", tagStart);
            if (tagEnd < 0) {
                break;
            }
            int closeStart = xml.indexOf("</d>", tagEnd);
            if (closeStart < 0) {
                break;
            }
            String attrs = xml.substring(tagStart + 3, tagEnd);
            String text = xml.substring(tagEnd + 1, closeStart);
            start = closeStart + 4;

            String p = null;
            String[] attrParts = attrs.split(" ");
            for (int i = 0; i < attrParts.length; i++) {
                if (attrParts[i].startsWith("p=\"")) {
                    p = attrParts[i].substring(3, attrParts[i].length() - 1);
                    break;
                }
            }
            if (p == null || text.length() == 0) {
                continue;
            }
            String[] f = p.split(",");
            if (f.length < 7) {
                continue;
            }
            // 只保留解析弹幕需要的字段
            fields.add(new String[]{f[0], f[1], f[2], f[3], f[6], text});
        }
        return new ListParser(fields);
    }

    /** 预解析列表的解析器：prepare 时按显示尺寸创建弹幕对象 */
    static class ListParser extends BaseDanmakuParser {
        private final List<String[]> mFields;

        ListParser(List<String[]> fields) {
            mFields = fields;
        }

        int size() {
            return mFields == null ? 0 : mFields.size();
        }

        @Override
        protected IDanmakus parse() {
            Danmakus danmakus = new Danmakus();
            if (mFields == null || mFields.isEmpty()) {
                return danmakus;
            }
            boolean forceScroll = Sp.getBoolean(Sp.SET_DANMAKU_FORCE_SCROLL, false);
            for (int i = 0; i < mFields.size(); i++) {
                String[] f = mFields.get(i);
                try {
                    float timeSec = Float.parseFloat(f[0]);
                    int mode = parseInt(f[1], 1);
                    float textSize = Float.parseFloat(f[2]);
                    int color = parseInt(f[3], 0xFFFFFF);

                    int type;
                    switch (mode) {
                        case 5:
                            type = forceScroll ? BaseDanmaku.TYPE_SCROLL_RL : BaseDanmaku.TYPE_FIX_TOP;
                            break;
                        case 4:
                            type = forceScroll ? BaseDanmaku.TYPE_SCROLL_RL : BaseDanmaku.TYPE_FIX_BOTTOM;
                            break;
                        default:
                            type = BaseDanmaku.TYPE_SCROLL_RL;
                            break;
                    }
                    BaseDanmaku item = DanmakuFactory.createDanmaku(type, mDisp);
                    item.text = f[5];
                    item.textSize = textSize * (mDispDensity - 0.6f);
                    item.textColor = color <= 0 ? Color.WHITE : color;
                    item.textShadowColor = Color.BLACK;
                    item.time = (long) (timeSec * 1000);
                    item.setTimer(mTimer);
                    item.userHash = f[4];
                    item.setVisibility(true);
                    danmakus.addItem(item);
                } catch (Exception ignored) {
                }
            }
            return danmakus;
        }
    }

    private static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return def;
        }
    }

    /** 弹幕状态回调：把加载/渲染过程显示在屏幕上，便于实机定位问题 */
    public interface StatusListener {
        void onStatus(String status);
    }

    /** 装载弹幕到 DanmakuView */
    public static void loadDanmaku(final IDanmakuView view, final long cid, final long durationMs,
                                   final boolean enable, final StatusListener status) {
        if (view == null) {
            return;
        }
        if (!enable || !Sp.getBoolean(Sp.SET_DANMAKU_ENGINE, true)) {
            view.hide();
            view.removeAllDanmakus();
            report(status, "弹幕已关闭");
            return;
        }
        view.show();
        report(status, "弹幕下载中…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final BaseDanmakuParser parser = fetchParser(cid, durationMs);
                    final List<String[]> countHolder = new java.util.ArrayList<String[]>();
                    report(status, "弹幕就绪 " + (parser instanceof ListParser
                            ? ((ListParser) parser).size() : "?") + " 条，启动引擎…");
                    postUi(view, new Runnable() {
                        @Override
                        public void run() {
                            // 关键：start() 内部会 removeCallbacksAndMessages 清空消息队列，
                            // 若在 prepare 后立即 start，刚发出的 PREPARE 消息会被清掉，
                            // prepared 永远 false、弹幕永远不显示（之前"随缘"的根因）。
                            // 正确做法：在 prepared 回调里 start（与哔哩终端参考实现一致）。
                            view.setCallback(new master.flame.danmaku.controller.DrawHandler.Callback() {
                                @Override
                                public void prepared() {
                                    view.start();
                                    // 首次 prepare 时显示尺寸可能尚未就绪，弹幕行会全部挤在顶部；
                                    // 立即 seek 到 0 触发一次行重排（与"拖回 00:00 才正常"同理）
                                    try {
                                        view.seekTo(0L);
                                    } catch (Exception ignored) {
                                    }
                                    android.util.Log.i("Danmaku", "prepared 回调：引擎已启动 cid=" + cid);
                                }

                                @Override
                                public void updateTimer(master.flame.danmaku.danmaku.model.DanmakuTimer timer) {
                                }
                            });
                            view.prepare(parser);
                            // 不启用 drawing cache：CacheManagingDrawTask 在大内存/新系统上
                            // 存在卡死问题（prepared 永远 false），普通 DrawTask 稳定
                            view.enableDanmakuDrawingCache(false);
                            view.showFPS(false);
                            android.util.Log.i("Danmaku", "弹幕引擎 prepare 已提交 cid=" + cid);
                            // 屏幕状态自检：每 2 秒刷新，共 10 次
                            for (int i = 1; i <= 10; i++) {
                                final int tick = i;
                                postUiDelayed(view, new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            boolean prepared = view.isPrepared();
                                            int vw = view instanceof View ? ((View) view).getWidth() : 0;
                                            int vh = view instanceof View ? ((View) view).getHeight() : 0;
                                            report(status, "弹幕运行 " + (tick * 2) + "s"
                                                    + (prepared ? " prepared=1" : " prepared=0")
                                                    + " shown=" + view.isShown());
                                            android.util.Log.i("Danmaku", "自检 " + (tick * 2)
                                                    + "s prepared=" + prepared
                                                    + " shown=" + view.isShown()
                                                    + " w=" + vw + " h=" + vh);
                                        } catch (Exception e) {
                                            android.util.Log.w("Danmaku", "自检失败: " + e.getMessage());
                                        }
                                    }
                                }, tick * 2000L);
                            }
                        }
                    });
                } catch (Exception e) {
                    android.util.Log.w("Danmaku", "弹幕加载失败: " + e.getMessage());
                    report(status, "弹幕加载失败: " + e.getMessage());
                }
            }
        }).start();
    }

    private static void report(final StatusListener listener, final String status) {
        if (listener != null) {
            listener.onStatus(status);
        }
    }

    /** UI 线程投递（兼容 TextureView / SurfaceView 两种实现） */
    private static boolean postUi(IDanmakuView view, Runnable r) {
        if (view instanceof View) {
            ((View) view).post(r);
            return true;
        }
        return false;
    }

    private static boolean postUiDelayed(IDanmakuView view, Runnable r, long ms) {
        if (view instanceof View) {
            ((View) view).postDelayed(r, ms);
            return true;
        }
        return false;
    }

    /** 开始/恢复滚动 */
    public static void start(IDanmakuView view) {
        if (view != null && view.isPrepared()) {
            view.show();
            view.start();
        }
    }

    /** 暂停 */
    public static void pause(IDanmakuView view) {
        if (view == null) {
            return;
        }
        if (view.isPrepared()) {
            view.pause();
        }
        // 未 prepared 或暂停消息没生效时兜底：直接隐藏，保证"暂停后弹幕一定停"
        view.hide();
    }

    /** 定位到指定时间 */
    public static void seekTo(IDanmakuView view, long ms) {
        if (view != null && view.isPrepared()) {
            view.seekTo(ms);
        }
    }

    /** 释放 */
    public static void release(IDanmakuView view) {
        if (view != null) {
            view.release();
        }
    }

    /** 直播弹幕：空解析器把引擎准备好，弹幕逐条即时注入 */
    public static void prepareLive(final IDanmakuView view) {
        if (view == null) {
            return;
        }
        BaseDanmakuParser parser = new BaseDanmakuParser() {
            @Override
            protected IDanmakus parse() {
                return new Danmakus();
            }
        };
        view.show();
        view.setCallback(new master.flame.danmaku.controller.DrawHandler.Callback() {
            @Override
            public void prepared() {
                view.start();
                try {
                    view.seekTo(0L);
                } catch (Exception ignored) {
                }
            }

            @Override
            public void updateTimer(master.flame.danmaku.danmaku.model.DanmakuTimer timer) {
            }
        });
        view.enableDanmakuDrawingCache(false);
        view.prepare(parser);
    }

    /** 直播：立即显示一条弹幕（不排队、不跟视频时间轴） */
    public static void addLiveDanmaku(IDanmakuView view, String text) {
        if (view == null || !view.isPrepared() || text == null || text.length() == 0) {
            return;
        }
        try {
            BaseDanmaku item = DanmakuFactory.createDanmaku(BaseDanmaku.TYPE_SCROLL_RL);
            if (item == null) {
                return;
            }
            float density = view instanceof View
                    ? ((View) view).getResources().getDisplayMetrics().density : 1f;
            item.text = text;
            item.textSize = DanmakuFactory.DANMAKU_MEDIUM_TEXTSIZE * (density - 0.6f);
            item.textColor = Color.WHITE;
            item.textShadowColor = Color.BLACK;
            item.time = 50;
            item.setTimer(new DanmakuTimer());
            item.userHash = "live";
            item.setVisibility(true);
            view.addDanmaku(item);
        } catch (Exception ignored) {
        }
    }

    /** 立即显示一条自己刚发的弹幕 */
    public static void showSelf(IDanmakuView view, String text, long progressMs) {
        if (view == null || !view.isPrepared()) {
            return;
        }
        try {
            BaseDanmaku item = DanmakuFactory.createDanmaku(BaseDanmaku.TYPE_SCROLL_RL);
            float density = view instanceof View
                    ? ((View) view).getResources().getDisplayMetrics().density : 1f;
            item.text = text;
            item.textSize = DanmakuFactory.DANMAKU_MEDIUM_TEXTSIZE * (density - 0.6f);
            item.textColor = Color.WHITE;
            item.textShadowColor = Color.BLACK;
            item.time = progressMs;
            item.setTimer(new DanmakuTimer());
            item.userHash = "self";
            item.setVisibility(true);
            view.addDanmaku(item);
        } catch (Exception ignored) {
        }
    }
}
