package com.android.oldbili.retro.model;

/**
 * 一条私信。
 */
public class PrivateMessage {

    public long fromUid;
    public String text = "";
    public long time;
    public long seqno;      // 消息序号（翻更早记录用）

    public String timeDesc() {
        return com.android.oldbili.retro.util.TimeUtil.toRelative(time);
    }
}
