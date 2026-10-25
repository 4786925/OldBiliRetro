package com.android.oldbili.retro.api;

import com.android.oldbili.retro.net.HttpUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * 弹幕数据源。
 * 老 XML 接口 comment.bilibili.com/{cid}.xml 已下线（404），
 * 现行接口是 protobuf 分段的 x/v2/dm/web/seg.so（游客可访问，每段 6 分钟）。
 * 这里手写最小 protobuf 解析（DmSegMobileReply + DmElem），不引入 protobuf 依赖。
 *
 * DmElem 字段：
 *   1 id(int64) 2 progress(int32,毫秒) 3 mode(int32) 4 fontsize(int32)
 *   5 color(uint32) 6 midHash(string) 7 content(string) 8 ctime(int64) ...
 */
public class DanmakuApi {

    /** 每段覆盖时长：6 分钟 */
    private static final long SEG_MS = 6 * 60 * 1000L;

    /**
     * 拉取整集弹幕。返回字符串数组列表，每项：
     * [秒, 模式, 字号, 颜色, 用户hash, 内容]（与旧 XML 解析结果格式一致）
     */
    public static List<String[]> fetchDanmaku(long cid, long durationMs) throws java.io.IOException {
        return fetchDanmaku(cid, durationMs, 0);
    }

    /** maxCount>0 时到量即停（低端机防 OOM） */
    public static List<String[]> fetchDanmaku(long cid, long durationMs, int maxCount)
            throws java.io.IOException {
        List<String[]> out = new ArrayList<String[]>();
        int segCount = (int) Math.max(1, (durationMs + SEG_MS - 1) / SEG_MS);
        // 时长未知时多拉几段兜底（很多视频 metadata 无时长也能拿弹幕）
        if (durationMs <= 0) {
            segCount = 3;
        }
        for (int seg = 1; seg <= segCount; seg++) {
            byte[] data = getSeg(cid, seg);
            if (data == null || data.length == 0) {
                continue;
            }
            parseSeg(data, out);
            if (maxCount > 0 && out.size() >= maxCount) {
                break;
            }
            // 时长未知且当前段为空：后面一般也没有了
            if (durationMs <= 0 && data.length < 16) {
                break;
            }
        }
        return out;
    }

    private static byte[] getSeg(long cid, int index) {
        String url = "https://api.bilibili.com/x/v2/dm/web/seg.so?type=1&oid=" + cid
                + "&segment_index=" + index;
        byte[] data = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            data = HttpUtil.getBytes(url);
            if (data != null) {
                break;
            }
        }
        return data;
    }

    // ==================== 手写 protobuf ====================

    /** 解析 DmSegMobileReply：repeated DmElem elems = 1 */
    private static void parseSeg(byte[] data, List<String[]> out) {
        int pos = 0;
        try {
            while (pos < data.length) {
                int[] tag = readVarint(data, pos);
                pos = tag[1];
                int fieldNum = tag[0] >>> 3;
                int wireType = tag[0] & 7;
                if (fieldNum == 1 && wireType == 2) {
                    int[] len = readVarint(data, pos);
                    pos = len[1];
                    int size = len[0];
                    parseElem(data, pos, size, out);
                    pos += size;
                } else if (wireType == 0) {
                    int[] v = readVarint(data, pos);
                    pos = v[1];
                } else if (wireType == 2) {
                    int[] len = readVarint(data, pos);
                    pos = len[1] + len[0];
                } else if (wireType == 1) {
                    pos += 8;
                } else if (wireType == 5) {
                    pos += 4;
                } else {
                    break;
                }
            }
        } catch (Exception ignored) {
        }
    }

    /** 解析 DmElem */
    private static void parseElem(byte[] data, int start, int size, List<String[]> out) {
        int pos = start;
        int end = start + size;
        long progressMs = 0;
        int mode = 1;
        int fontSize = 25;
        int color = 0xFFFFFF;
        String midHash = "";
        String content = "";
        try {
            while (pos < end) {
                int[] tag = readVarint(data, pos);
                pos = tag[1];
                int fieldNum = tag[0] >>> 3;
                int wireType = tag[0] & 7;
                if (wireType == 0) {
                    int[] v = readVarint(data, pos);
                    pos = v[1];
                    switch (fieldNum) {
                        case 2:
                            progressMs = v[0];
                            break;
                        case 3:
                            mode = v[0];
                            break;
                        case 4:
                            fontSize = v[0];
                            break;
                        case 5:
                            color = v[0];
                            break;
                        default:
                            break;
                    }
                } else if (wireType == 2) {
                    int[] len = readVarint(data, pos);
                    pos = len[1];
                    int n = len[0];
                    String s = new String(data, pos, Math.min(n, end - pos), "UTF-8");
                    pos += n;
                    if (fieldNum == 6) {
                        midHash = s;
                    } else if (fieldNum == 7) {
                        content = s;
                    }
                } else if (wireType == 1) {
                    pos += 8;
                } else if (wireType == 5) {
                    pos += 4;
                } else {
                    break;
                }
            }
        } catch (Exception ignored) {
        }
        if (content.length() == 0) {
            return;
        }
        // 兼容旧解析器格式：秒, 模式, 字号, 颜色, 用户hash, 内容
        out.add(new String[]{
                String.valueOf(progressMs / 1000f),
                String.valueOf(mode),
                String.valueOf(fontSize),
                String.valueOf(color),
                midHash,
                content
        });
    }

    /** 读 varint，返回 [值, 新位置] */
    private static int[] readVarint(byte[] data, int pos) {
        long value = 0;
        int shift = 0;
        while (pos < data.length) {
            int b = data[pos++] & 0xFF;
            value |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                break;
            }
            shift += 7;
            if (shift > 63) {
                break;
            }
        }
        return new int[]{(int) value, pos};
    }
}
