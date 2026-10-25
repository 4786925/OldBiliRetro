package com.android.oldbili.retro.widget;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.android.oldbili.retro.R;
import com.android.oldbili.retro.model.Comment;
import com.android.oldbili.retro.model.DynamicItem;
import com.android.oldbili.retro.model.FavoriteFolder;
import com.android.oldbili.retro.model.HistoryItem;
import com.android.oldbili.retro.model.LiveRoom;
import com.android.oldbili.retro.model.MessageSession;
import com.android.oldbili.retro.model.PrivateMessage;
import com.android.oldbili.retro.net.ImageLoader;
import com.android.oldbili.retro.model.UserInfo;
import com.android.oldbili.retro.model.VideoCard;
import com.android.oldbili.retro.net.ImageLoader;
import com.android.oldbili.retro.util.NumberUtil;

/**
 * 全部列表页共用的 Holder 集合。
 *
 * 各页面的 Adapter 只负责建 View、取数据，绑定逻辑全部集中在这个类里：
 * 搜索页、个人空间、历史、收藏、稍后再看用的都是同一个视频卡片 Holder，
 * 样式想改只动这一处。
 */
public class ListHolders {

    // ==================== 视频卡片 ====================

    public static class VideoCardHolder {
        public final View itemView;
        public final ImageView cover;
        public final TextView title;
        public final TextView meta;
        public final TextView extra;
        public final TextView duration;
        public final TextView badge;

        public VideoCardHolder(LayoutInflater inflater, ViewGroup parent) {
            itemView = inflater.inflate(R.layout.widget_item_video_card, parent, false);
            cover = (ImageView) itemView.findViewById(R.id.cover);
            title = (TextView) itemView.findViewById(R.id.title);
            meta = (TextView) itemView.findViewById(R.id.meta);
            extra = (TextView) itemView.findViewById(R.id.extra);
            duration = (TextView) itemView.findViewById(R.id.duration);
            badge = (TextView) itemView.findViewById(R.id.badge);
        }

        public void bind(VideoCard card) {
            if (card == null) {
                return;
            }
            title.setText(card.title);
            meta.setText(card.upName + " · " + NumberUtil.toWan(card.play) + "播放 · "
                    + NumberUtil.toWan(card.danmaku) + "弹幕");
            duration.setText(card.durationDesc());
            ImageLoader.get().load(cover, ImageLoader.coverUrl(card.cover, 320, 200));

            if (card.badge != null && card.badge.length() > 0) {
                badge.setVisibility(View.VISIBLE);
                badge.setText(card.badge);
            } else {
                badge.setVisibility(View.GONE);
            }
            extra.setVisibility(View.GONE);
        }
    }

    // ==================== 用户卡片 ====================

    public static class UserCardHolder {
        public final View itemView;
        public final ImageView avatar;
        public final TextView name;
        public final TextView sign;
        public final TextView right;

        public UserCardHolder(LayoutInflater inflater, ViewGroup parent) {
            itemView = inflater.inflate(R.layout.widget_item_user, parent, false);
            avatar = (ImageView) itemView.findViewById(R.id.avatar);
            name = (TextView) itemView.findViewById(R.id.name);
            sign = (TextView) itemView.findViewById(R.id.sign);
            right = (TextView) itemView.findViewById(R.id.right);
        }

        public void bind(UserInfo user, boolean showRelation) {
            if (user == null) {
                return;
            }
            name.setText(user.name);
            if (user.sign != null && user.sign.length() > 0) {
                sign.setText(user.sign);
            } else {
                sign.setText("粉丝 " + NumberUtil.toWan(user.fans));
            }
            ImageLoader.get().load(avatar, ImageLoader.avatarUrl(user.face, 96));
            if (showRelation && user.relation > 0) {
                right.setVisibility(View.VISIBLE);
                // 统一语义：attribute=2 表示已关注（新编码）；互粉 = 已关注 且 be_relation>0
                boolean followed = com.android.oldbili.retro.api.UserApi.isFollowed(user.relation);
                boolean mutual = followed && user.beRelation > 0;
                if (mutual) {
                    right.setText("已互粉");
                } else if (followed) {
                    right.setText("已关注");
                } else {
                    right.setText("TA关注了你");
                }
            } else {
                right.setVisibility(View.GONE);
            }
        }
    }

    // ==================== 评论 ====================

    public static class CommentHolder {
        public final View itemView;
        public final ImageView avatar;
        public final TextView name;
        public final TextView like;
        public final TextView content;
        public final TextView time;
        public final ImageView picture;
        public final TextView pictureCount;
        public final View subReplies;

        public CommentHolder(LayoutInflater inflater, ViewGroup parent) {
            itemView = inflater.inflate(R.layout.widget_item_comment, parent, false);
            avatar = (ImageView) itemView.findViewById(R.id.avatar);
            name = (TextView) itemView.findViewById(R.id.name);
            like = (TextView) itemView.findViewById(R.id.like);
            content = (TextView) itemView.findViewById(R.id.content);
            time = (TextView) itemView.findViewById(R.id.time);
            picture = (ImageView) itemView.findViewById(R.id.picture);
            pictureCount = (TextView) itemView.findViewById(R.id.picture_count);
            subReplies = itemView.findViewById(R.id.sub_replies);
        }

        public void bind(final Comment comment) {
            if (comment == null) {
                return;
            }
            name.setText(comment.userName + (comment.isUp ? " (UP)" : ""));
            like.setText("赞 " + comment.like);
            content.setText(comment.message);
            String timeText = (comment.floor > 0 ? comment.floor + "楼 · " : "") + comment.timeDesc();
            if (comment.rcount > 0) {
                timeText += " · " + comment.rcount + "条回复";
            }
            time.setText(timeText);
            ImageLoader.get().load(avatar, ImageLoader.avatarUrl(comment.userFace, 96));
            // 头像点击进个人主页
            avatar.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        android.content.Context ctx = itemView.getContext();
                        if (comment.mid <= 0) {
                            return;
                        }
                        android.content.Intent intent = new android.content.Intent(ctx,
                                com.android.oldbili.retro.UserActivity.class);
                        intent.putExtra("mid", comment.mid);
                        intent.putExtra("name", comment.userName);
                        if (!(ctx instanceof android.app.Activity)) {
                            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                        }
                        ctx.startActivity(intent);
                    } catch (Exception ignored) {
                    }
                }
            });
            // 图文评论：显示第一张配图 + 张数；点开看大图（多图可翻），长按保存
            if (comment.pictures != null && comment.pictures.size() > 0) {
                picture.setVisibility(View.VISIBLE);
                ImageLoader.get().load(picture, ImageLoader.coverUrl(comment.pictures.get(0), 240, 180));
                final java.util.List<String> pics = new java.util.ArrayList<String>(comment.pictures);
                picture.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showPicturesDialog(pics, 0);
                    }
                });
                picture.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        askSavePicture(pics.get(0));
                        return true;
                    }
                });
                if (comment.pictures.size() > 1) {
                    pictureCount.setVisibility(View.VISIBLE);
                    pictureCount.setText("共 " + comment.pictures.size() + " 张图片");
                } else {
                    pictureCount.setVisibility(View.GONE);
                }
            } else {
                picture.setVisibility(View.GONE);
                pictureCount.setVisibility(View.GONE);
            }
            subReplies.setVisibility(View.GONE);
        }

        /** 多图浏览：点击切换下一张，长按保存当前，右侧进度角标 */
        private void showPicturesDialog(final java.util.List<String> pics, final int startIndex) {
            try {
                final android.content.Context ctx = itemView.getContext();
                final ImageView big = new ImageView(ctx);
                big.setBackgroundColor(0xFF000000);
                big.setScaleType(ImageView.ScaleType.FIT_CENTER);
                final int[] index = new int[]{startIndex};
                ImageLoader.get().load(big, pics.get(index[0]));
                // 右上角小字：当前第几张/共几张
                final TextView pageHint = new TextView(ctx);
                pageHint.setTextColor(0xFFFFFFFF);
                pageHint.setTextSize(12);
                pageHint.setBackgroundColor(0x88000000);
                pageHint.setPadding(8, 3, 8, 3);
                pageHint.setText((index[0] + 1) + "/" + pics.size());
                android.widget.FrameLayout frame = new android.widget.FrameLayout(ctx);
                frame.addView(big, new android.widget.FrameLayout.LayoutParams(
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
                frame.addView(pageHint, new android.widget.FrameLayout.LayoutParams(
                        android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                        android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                        android.view.Gravity.TOP | android.view.Gravity.RIGHT));
                final android.app.Dialog dialog = new android.app.Dialog(ctx,
                        android.R.style.Theme_Black_NoTitleBar_Fullscreen);
                dialog.setContentView(frame);
                big.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        askSavePicture(pics.get(index[0]));
                        return true;
                    }
                });
                big.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (pics.size() <= 1) {
                            dialog.dismiss();
                            return;
                        }
                        // 点一下看下一张（循环）
                        index[0] = (index[0] + 1) % pics.size();
                        ImageLoader.get().load(big, pics.get(index[0]));
                        pageHint.setText((index[0] + 1) + "/" + pics.size());
                    }
                });
                dialog.show();
            } catch (Exception ignored) {
            }
        }

        /** 大图查看（单图兼容入口） */
        private void showPictureDialog(final String url) {
            java.util.List<String> one = new java.util.ArrayList<String>();
            one.add(url);
            showPicturesDialog(one, 0);
        }

        private void askSavePicture(final String url) {
            try {
                final android.content.Context ctx = itemView.getContext();
                new android.app.AlertDialog.Builder(ctx)
                        .setMessage("保存图片到相册 OldBiliRetro 文件夹？")
                        .setPositiveButton("保存", new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface d, int which) {
                                new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        final boolean ok = com.android.oldbili.retro.util.SaveImageUtil.save(ctx, url);
                                        if (ctx instanceof android.app.Activity) {
                                            ((android.app.Activity) ctx).runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    com.android.oldbili.retro.util.ToastUtil.show(ctx,
                                                            ok ? "已保存到 Pictures/OldBiliRetro" : "保存失败，请重试");
                                                }
                                            });
                                        }
                                    }
                                }).start();
                            }
                        })
                        .setNegativeButton("取消", null)
                        .show();
            } catch (Exception ignored) {
            }
        }
    }

    // ==================== 直播间 ====================

    public static class LiveCardHolder {
        public final View itemView;
        public final ImageView cover;
        public final TextView title;
        public final TextView owner;
        public final TextView online;

        public LiveCardHolder(LayoutInflater inflater, ViewGroup parent) {
            itemView = inflater.inflate(R.layout.widget_item_live, parent, false);
            cover = (ImageView) itemView.findViewById(R.id.cover);
            title = (TextView) itemView.findViewById(R.id.title);
            owner = (TextView) itemView.findViewById(R.id.owner);
            online = (TextView) itemView.findViewById(R.id.online);
        }

        public void bind(LiveRoom room) {
            if (room == null) {
                return;
            }
            title.setText(room.title);
            owner.setText(room.ownerName);
            online.setText(NumberUtil.toWan(room.online) + "人在看");
            ImageLoader.get().load(cover, ImageLoader.coverUrl(room.cover, 320, 200));
        }
    }

    // ==================== 收藏夹 ====================

    public static class FolderHolder {
        public final View itemView;
        public final ImageView cover;
        public final TextView title;
        public final TextView count;

        public FolderHolder(LayoutInflater inflater, ViewGroup parent) {
            itemView = inflater.inflate(R.layout.widget_item_folder, parent, false);
            cover = (ImageView) itemView.findViewById(R.id.cover);
            title = (TextView) itemView.findViewById(R.id.title);
            count = (TextView) itemView.findViewById(R.id.count);
        }

        public void bind(FavoriteFolder folder) {
            if (folder == null) {
                return;
            }
            title.setText(folder.title);
            count.setText(folder.count + "个内容");
            ImageLoader.get().load(cover, ImageLoader.coverUrl(folder.cover, 160, 100));
        }
    }

    // ==================== 私信会话 ====================

    public static class SessionHolder {
        public final View itemView;
        public final ImageView avatar;
        public final TextView name;
        public final TextView last;
        public final TextView time;
        public final TextView unread;

        public SessionHolder(LayoutInflater inflater, ViewGroup parent) {
            itemView = inflater.inflate(R.layout.widget_item_session, parent, false);
            avatar = (ImageView) itemView.findViewById(R.id.avatar);
            name = (TextView) itemView.findViewById(R.id.name);
            last = (TextView) itemView.findViewById(R.id.last);
            time = (TextView) itemView.findViewById(R.id.time);
            unread = (TextView) itemView.findViewById(R.id.unread);
        }

        public void bind(final MessageSession session) {
            if (session == null) {
                return;
            }
            if (session.kind == MessageSession.KIND_FOLLOW_UP) {
                // 特别关注条目：头像=UP、名字=UP、摘要=视频标题
                name.setText(session.name.length() > 0 ? session.name : "特别关注");
                last.setText(session.videoTitle.length() > 0 ? session.videoTitle : "更新了视频");
                time.setText(session.timeDesc());
                ImageLoader.get().load(avatar, ImageLoader.avatarUrl(session.face, 96));
                unread.setVisibility(View.GONE);
                avatar.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        openUser(session.uid, session.name);
                    }
                });
                return;
            }
            name.setText(session.name.length() > 0 ? session.name : "用户" + session.uid);
            last.setText(session.lastMsg);
            time.setText(session.timeDesc());
            ImageLoader.get().load(avatar, ImageLoader.avatarUrl(session.face, 96));
            if (session.unread > 0) {
                unread.setVisibility(View.VISIBLE);
                unread.setText(String.valueOf(session.unread));
            } else {
                unread.setVisibility(View.GONE);
            }
            // 头像点击进对方主页
            avatar.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openUser(session.uid, session.name);
                }
            });
        }

        private void openUser(long mid, String name) {
            if (mid <= 0) {
                return;
            }
            try {
                android.content.Context ctx = itemView.getContext();
                android.content.Intent intent = new android.content.Intent(ctx,
                        com.android.oldbili.retro.UserActivity.class);
                intent.putExtra("mid", mid);
                intent.putExtra("name", name);
                if (!(ctx instanceof android.app.Activity)) {
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                }
                ctx.startActivity(intent);
            } catch (Exception ignored) {
            }
        }
    }

    // ==================== 私信气泡 ====================

    public static class MsgHolder {
        public final View itemView;
        public final ImageView avatar;
        public final TextView text;
        public final TextView time;

        public MsgHolder(LayoutInflater inflater, ViewGroup parent, boolean self) {
            itemView = inflater.inflate(self ? R.layout.widget_item_msg_right : R.layout.widget_item_msg_left,
                    parent, false);
            avatar = (ImageView) itemView.findViewById(R.id.avatar);
            text = (TextView) itemView.findViewById(R.id.text);
            time = (TextView) itemView.findViewById(R.id.time);
        }

        public void bind(final PrivateMessage msg, String face) {
            if (msg == null) {
                return;
            }
            text.setText(replaceEmoji(msg.text));
            time.setText(msg.timeDesc());
            if (avatar != null) {
                ImageLoader.get().load(avatar, ImageLoader.avatarUrl(face, 96));
                // 聊天页里点对方头像进主页
                avatar.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (msg.fromUid <= 0) {
                            return;
                        }
                        try {
                            android.content.Context ctx = itemView.getContext();
                            android.content.Intent intent = new android.content.Intent(ctx,
                                    com.android.oldbili.retro.UserActivity.class);
                            intent.putExtra("mid", msg.fromUid);
                            if (!(ctx instanceof android.app.Activity)) {
                                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                            }
                            ctx.startActivity(intent);
                        } catch (Exception ignored) {
                        }
                    }
                });
            }
        }
    }

    /** 表情代码 → 近似 emoji（B 站小电视/小黄脸在文本里的形式） */
    private static final String[][] EMOJI_MAP = {
            {"[tv_doge]", "🐶"}, {"[tv_坏笑]", "😏"}, {"[tv_目瞪口呆]", "😳"}, {"[tv_微笑]", "🙂"},
            {"[tv_doge_累]", "😮‍💨"}, {"[tv_流泪]", "😭"}, {"[tv_思考]", "🤔"}, {"[tv_吐血]", "🩸"},
            {"[tv_困]", "😪"}, {"[tv_大佬]", "🕶️"}, {"[tv_抠鼻]", "😒"}, {"[tv_鼓掌]", "👏"},
            {"[doge]", "🐕"}, {"[抱抱]", "🤗"}, {"[笑哭]", "😂"}, {"[脱单doge]", "🐶❤️"},
            {"[大哭]", "😭"}, {"[妙啊]", "👌"}, {"[点赞]", "👍"}, {"[星星眼]", "🤩"},
    };

    static String replaceEmoji(String text) {
        if (text == null) {
            return "";
        }
        // 安卓4.1 之前系统没有 emoji 字形，替换了全是方框；老设备直接显示表情代码原文
        if (android.os.Build.VERSION.SDK_INT < 17) {
            return text;
        }
        for (int i = 0; i < EMOJI_MAP.length; i++) {
            text = text.replace(EMOJI_MAP[i][0], EMOJI_MAP[i][1]);
        }
        return text;
    }

    // ==================== 动态 ====================

    public static class DynamicHolder {
        public final View itemView;
        public final ImageView avatar;
        public final TextView name;
        public final TextView time;
        public final TextView text;
        public final View videoCard;
        public final ImageView videoCover;
        public final TextView videoTitle;
        public final TextView stats;

        public DynamicHolder(LayoutInflater inflater, ViewGroup parent) {
            itemView = inflater.inflate(R.layout.widget_item_dynamic, parent, false);
            avatar = (ImageView) itemView.findViewById(R.id.avatar);
            name = (TextView) itemView.findViewById(R.id.name);
            time = (TextView) itemView.findViewById(R.id.time);
            text = (TextView) itemView.findViewById(R.id.text);
            videoCard = itemView.findViewById(R.id.video_card);
            videoCover = (ImageView) itemView.findViewById(R.id.video_cover);
            videoTitle = (TextView) itemView.findViewById(R.id.video_title);
            stats = (TextView) itemView.findViewById(R.id.stats);
        }

        public void bind(DynamicItem item) {
            if (item == null) {
                return;
            }
            name.setText(item.userName);
            time.setText(item.timeDesc());
            text.setText(item.text);
            ImageLoader.get().load(avatar, ImageLoader.avatarUrl(item.userFace, 96));

            if (item.type == DynamicItem.TYPE_AV && item.aid > 0) {
                videoCard.setVisibility(View.VISIBLE);
                videoTitle.setText(item.videoTitle);
                ImageLoader.get().load(videoCover, ImageLoader.coverUrl(item.videoCover, 320, 200));
                stats.setText(NumberUtil.toWan(item.videoPlay) + "播放");
            } else {
                videoCard.setVisibility(View.GONE);
                if (item.pictures.size() > 0) {
                    stats.setText("[图片] " + item.pictures.size() + "张");
                } else {
                    stats.setText("转发 " + NumberUtil.toWan(item.like));
                }
            }
        }
    }

    // ==================== 下载任务 ====================

    public static class DownloadHolder {
        public final View itemView;
        public final ImageView cover;
        public final TextView title;
        public final TextView status;
        public final TextView quality;
        public final ProgressBar progress;
        public final Button action;

        public DownloadHolder(LayoutInflater inflater, ViewGroup parent) {
            itemView = inflater.inflate(R.layout.widget_item_download, parent, false);
            cover = (ImageView) itemView.findViewById(R.id.cover);
            title = (TextView) itemView.findViewById(R.id.title);
            status = (TextView) itemView.findViewById(R.id.status);
            quality = (TextView) itemView.findViewById(R.id.quality);
            progress = (ProgressBar) itemView.findViewById(R.id.progress);
            action = (Button) itemView.findViewById(R.id.action);
        }
    }
}
