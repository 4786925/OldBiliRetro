package com.android.oldbili.retro.widget;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.oldbili.retro.R;
import com.android.oldbili.retro.model.Comment;
import com.android.oldbili.retro.model.DynamicItem;
import com.android.oldbili.retro.model.FavoriteFolder;
import com.android.oldbili.retro.model.LiveRoom;
import com.android.oldbili.retro.model.MessageSession;
import com.android.oldbili.retro.model.PrivateMessage;
import com.android.oldbili.retro.model.UserInfo;
import com.android.oldbili.retro.model.VideoCard;

import java.util.ArrayList;
import java.util.List;

/**
 * 全部列表页共用的 Adapter 集合。
 *
 * 按当年的惯例，同一个数据源只写一个 Adapter：视频卡片一个、用户一个、
 * 评论一个……搜索页、个人空间、历史、收藏、稍后再看各页直接用同一个类，
 * 不搞每个页面一份的重复代码。
 */
public class ListAdapters {

    /** 番剧/影视卡片列表（搜索/追番共用） */
    public static class BangumiList extends BaseAdapter {
        private final List<com.android.oldbili.retro.model.BangumiCard> mData;
        private final LayoutInflater mInflater;

        public BangumiList(Activity activity, List<com.android.oldbili.retro.model.BangumiCard> data) {
            mData = data == null
                    ? new ArrayList<com.android.oldbili.retro.model.BangumiCard>()
                    : new ArrayList<com.android.oldbili.retro.model.BangumiCard>(data);
            mInflater = LayoutInflater.from(activity);
        }

        @Override
        public int getCount() {
            return mData.size();
        }

        @Override
        public Object getItem(int position) {
            return mData.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.widget_item_bangumi, parent, false);
            }
            com.android.oldbili.retro.model.BangumiCard card = mData.get(position);
            ((TextView) convertView.findViewById(R.id.title)).setText(card.title);
            String info = card.progressDesc();
            if (card.badge != null && card.badge.length() > 0) {
                info = (info.length() > 0 ? info + " · " : "") + card.badge;
            }
            if (card.isFinish) {
                info = (info.length() > 0 ? info + " · " : "") + "已完结";
            }
            ((TextView) convertView.findViewById(R.id.info)).setText(info);
            com.android.oldbili.retro.net.ImageLoader.get().load(
                    (ImageView) convertView.findViewById(R.id.cover),
                    com.android.oldbili.retro.net.ImageLoader.coverUrl(card.cover, 240, 150));
            return convertView;
        }
    }

    /** 首页双行紧凑视频卡（左封面右信息） */
    public static class VideoListCompact extends BaseAdapter {
        private final List<VideoCard> mData;
        private final LayoutInflater mInflater;

        public VideoListCompact(Activity activity, List<VideoCard> data) {
            mData = data == null ? new ArrayList<VideoCard>() : new ArrayList<VideoCard>(data);
            mInflater = LayoutInflater.from(activity);
        }

        @Override
        public int getCount() {
            return mData == null ? 0 : mData.size();
        }

        @Override
        public Object getItem(int position) {
            return mData.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView;
            if (row == null) {
                row = mInflater.inflate(R.layout.widget_item_video_compact, parent, false);
            }
            VideoCard card = mData.get(position);
            ImageView cover = (ImageView) row.findViewById(R.id.cover);
            TextView title = (TextView) row.findViewById(R.id.title);
            TextView info = (TextView) row.findViewById(R.id.info);
            title.setText(card.title);
            info.setText(card.upName + " · " + com.android.oldbili.retro.util.NumberUtil.toWan(card.play)
                    + "播放 · " + com.android.oldbili.retro.util.NumberUtil.toWan(card.danmaku) + "弹幕");
            com.android.oldbili.retro.net.ImageLoader.get()
                    .load(cover, com.android.oldbili.retro.net.ImageLoader.coverUrl(card.cover, 240, 150));
            return row;
        }
    }

    /** 视频卡片列表 */
    public static class VideoList extends BaseAdapter {
        private final List<VideoCard> mData;
        private final LayoutInflater mInflater;

        public VideoList(Activity activity, List<VideoCard> data) {
            mData = data == null ? new ArrayList<VideoCard>() : new ArrayList<VideoCard>(data);
            mInflater = LayoutInflater.from(activity);
        }

        @Override
        public int getCount() {
            return mData == null ? 0 : mData.size();
        }

        @Override
        public Object getItem(int position) {
            return mData.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ListHolders.VideoCardHolder holder;
            if (convertView == null) {
                holder = new ListHolders.VideoCardHolder(mInflater, parent);
                convertView = holder.itemView;
                convertView.setTag(holder);
            } else {
                holder = (ListHolders.VideoCardHolder) convertView.getTag();
            }
            holder.bind(mData.get(position));
            return convertView;
        }
    }

    /** 用户列表 */
    public static class UserList extends BaseAdapter {
        private final List<UserInfo> mData;
        private final LayoutInflater mInflater;
        private final boolean mShowRelation;

        public UserList(Activity activity, List<UserInfo> data, boolean showRelation) {
            mData = data == null ? new ArrayList<UserInfo>() : new ArrayList<UserInfo>(data);
            mInflater = LayoutInflater.from(activity);
            mShowRelation = showRelation;
        }

        @Override
        public int getCount() {
            return mData == null ? 0 : mData.size();
        }

        @Override
        public Object getItem(int position) {
            return mData.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ListHolders.UserCardHolder holder;
            if (convertView == null) {
                holder = new ListHolders.UserCardHolder(mInflater, parent);
                convertView = holder.itemView;
                convertView.setTag(holder);
            } else {
                holder = (ListHolders.UserCardHolder) convertView.getTag();
            }
            holder.bind(mData.get(position), mShowRelation);
            return convertView;
        }
    }

    /** 评论列表 */
    public static class CommentList extends BaseAdapter {
        private final List<Comment> mData;
        private final LayoutInflater mInflater;

        public CommentList(Activity activity, List<Comment> data) {
            mData = data == null ? new ArrayList<Comment>() : new ArrayList<Comment>(data);
            mInflater = LayoutInflater.from(activity);
        }

        @Override
        public int getCount() {
            return mData == null ? 0 : mData.size();
        }

        @Override
        public Object getItem(int position) {
            return mData.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ListHolders.CommentHolder holder;
            if (convertView == null) {
                holder = new ListHolders.CommentHolder(mInflater, parent);
                convertView = holder.itemView;
                convertView.setTag(holder);
            } else {
                holder = (ListHolders.CommentHolder) convertView.getTag();
            }
            holder.bind(mData.get(position));
            return convertView;
        }
    }

    /** 直播间列表 */
    public static class LiveList extends BaseAdapter {
        private final List<LiveRoom> mData;
        private final LayoutInflater mInflater;

        public LiveList(Activity activity, List<LiveRoom> data) {
            mData = data == null ? new ArrayList<LiveRoom>() : new ArrayList<LiveRoom>(data);
            mInflater = LayoutInflater.from(activity);
        }

        @Override
        public int getCount() {
            return mData == null ? 0 : mData.size();
        }

        @Override
        public Object getItem(int position) {
            return mData.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ListHolders.LiveCardHolder holder;
            if (convertView == null) {
                holder = new ListHolders.LiveCardHolder(mInflater, parent);
                convertView = holder.itemView;
                convertView.setTag(holder);
            } else {
                holder = (ListHolders.LiveCardHolder) convertView.getTag();
            }
            holder.bind(mData.get(position));
            return convertView;
        }
    }

    /** 收藏夹列表 */
    public static class FolderList extends BaseAdapter {
        private final List<FavoriteFolder> mData;
        private final LayoutInflater mInflater;

        public FolderList(Activity activity, List<FavoriteFolder> data) {
            mData = data == null ? new ArrayList<FavoriteFolder>() : new ArrayList<FavoriteFolder>(data);
            mInflater = LayoutInflater.from(activity);
        }

        @Override
        public int getCount() {
            return mData == null ? 0 : mData.size();
        }

        @Override
        public Object getItem(int position) {
            return mData.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ListHolders.FolderHolder holder;
            if (convertView == null) {
                holder = new ListHolders.FolderHolder(mInflater, parent);
                convertView = holder.itemView;
                convertView.setTag(holder);
            } else {
                holder = (ListHolders.FolderHolder) convertView.getTag();
            }
            holder.bind(mData.get(position));
            return convertView;
        }
    }

    /** 私信会话列表 */
    public static class SessionList extends BaseAdapter {
        private final List<MessageSession> mData;
        private final LayoutInflater mInflater;

        public SessionList(Activity activity, List<MessageSession> data) {
            mData = data == null ? new ArrayList<MessageSession>() : new ArrayList<MessageSession>(data);
            mInflater = LayoutInflater.from(activity);
        }

        @Override
        public int getCount() {
            return mData == null ? 0 : mData.size();
        }

        @Override
        public Object getItem(int position) {
            return mData.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ListHolders.SessionHolder holder;
            if (convertView == null) {
                holder = new ListHolders.SessionHolder(mInflater, parent);
                convertView = holder.itemView;
                convertView.setTag(holder);
            } else {
                holder = (ListHolders.SessionHolder) convertView.getTag();
            }
            holder.bind(mData.get(position));
            return convertView;
        }
    }

    /** 私信聊天记录（气泡） */
    public static class MsgList extends BaseAdapter {
        private final List<PrivateMessage> mData;
        private final LayoutInflater mInflater;
        private final long mMyUid;
        private final String mOtherFace;

        public MsgList(Activity activity, List<PrivateMessage> data, long myUid, String otherFace) {
            mData = data == null ? new ArrayList<PrivateMessage>() : new ArrayList<PrivateMessage>(data);
            mInflater = LayoutInflater.from(activity);
            mMyUid = myUid;
            mOtherFace = otherFace;
        }

        @Override
        public int getCount() {
            return mData == null ? 0 : mData.size();
        }

        @Override
        public Object getItem(int position) {
            return mData.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public int getItemViewType(int position) {
            PrivateMessage msg = mData.get(position);
            return msg.fromUid == mMyUid ? 1 : 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            boolean self = mData.get(position).fromUid == mMyUid;
            ListHolders.MsgHolder holder;
            if (convertView == null) {
                holder = new ListHolders.MsgHolder(mInflater, parent, self);
                convertView = holder.itemView;
                convertView.setTag(holder);
            } else {
                holder = (ListHolders.MsgHolder) convertView.getTag();
            }
            holder.bind(mData.get(position), mOtherFace);
            return convertView;
        }
    }

    /** 动态列表 */
    public static class DynamicList extends BaseAdapter {
        private final List<DynamicItem> mData;
        private final LayoutInflater mInflater;

        public DynamicList(Activity activity, List<DynamicItem> data) {
            mData = data == null ? new ArrayList<DynamicItem>() : new ArrayList<DynamicItem>(data);
            mInflater = LayoutInflater.from(activity);
        }

        @Override
        public int getCount() {
            return mData == null ? 0 : mData.size();
        }

        @Override
        public Object getItem(int position) {
            return mData.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ListHolders.DynamicHolder holder;
            if (convertView == null) {
                holder = new ListHolders.DynamicHolder(mInflater, parent);
                convertView = holder.itemView;
                convertView.setTag(holder);
            } else {
                holder = (ListHolders.DynamicHolder) convertView.getTag();
            }
            holder.bind(mData.get(position));
            return convertView;
        }
    }

    /** 播放历史列表：复用视频卡片 Holder，额外一行显示看到哪儿 */
    public static class HistoryList extends BaseAdapter {
        private final List<com.android.oldbili.retro.model.HistoryItem> mData;
        private final LayoutInflater mInflater;

        public HistoryList(Activity activity, List<com.android.oldbili.retro.model.HistoryItem> data) {
            mData = data == null
                    ? new ArrayList<com.android.oldbili.retro.model.HistoryItem>()
                    : new ArrayList<com.android.oldbili.retro.model.HistoryItem>(data);
            mInflater = LayoutInflater.from(activity);
        }

        @Override
        public int getCount() {
            return mData == null ? 0 : mData.size();
        }

        @Override
        public Object getItem(int position) {
            return mData.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ListHolders.VideoCardHolder holder;
            if (convertView == null) {
                holder = new ListHolders.VideoCardHolder(mInflater, parent);
                convertView = holder.itemView;
                convertView.setTag(holder);
            } else {
                holder = (ListHolders.VideoCardHolder) convertView.getTag();
            }
            com.android.oldbili.retro.model.HistoryItem item = mData.get(position);
            VideoCard card = new VideoCard();
            card.aid = item.aid;
            card.bvid = item.bvid;
            card.cid = item.cid;
            card.title = item.title;
            card.cover = item.cover;
            card.duration = item.duration;
            card.upName = item.upName;
            holder.bind(card);
            holder.extra.setVisibility(View.VISIBLE);
            holder.extra.setText(item.progressDesc() + " · " + item.viewAtDesc());
            return convertView;
        }
    }
}
