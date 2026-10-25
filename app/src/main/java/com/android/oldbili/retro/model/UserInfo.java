package com.android.oldbili.retro.model;

/**
 * 用户信息。
 */
public class UserInfo {

    public long mid;
    public String name = "";
    public String face = "";
    public String sign = "";
    public long fans;
    public long follows;
    public long likes;      // 获赞数
    public int level;

    /** 关注关系 attribute：2=已关注（新编码）1/6=已关注（旧编码）0=未关注 */
    public int relation;

    /** be_relation.attribute：对方是否关注我（>0 表示对方也关注我，即互粉） */
    public int beRelation;

    /** 关注按钮文案 */
    public String relationDesc() {
        return com.android.oldbili.retro.api.UserApi.isMutual(this)
                ? "已互粉"
                : com.android.oldbili.retro.api.UserApi.isFollowed(relation) ? "已关注" : "关注";
    }
}
