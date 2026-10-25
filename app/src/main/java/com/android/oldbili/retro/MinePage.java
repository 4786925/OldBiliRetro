package com.android.oldbili.retro;

import android.app.Activity;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.oldbili.retro.api.LoginApi;
import com.android.oldbili.retro.model.UserInfo;
import com.android.oldbili.retro.net.HttpUtil;
import com.android.oldbili.retro.net.ImageLoader;
import com.android.oldbili.retro.util.Sp;
import com.android.oldbili.retro.util.ToastUtil;

/**
 * 我的页签。
 * 点头像进个人主页（需登录，未登录提示"未登录"）；头像下面是登录按钮，
 * 点进去是登录页二维码。下方是历史/稍后再看/收藏/关注/粉丝/私信/下载/设置入口。
 */
public class MinePage {

    private final Activity mActivity;
    private View mView;
    private ImageView mAvatar;
    private TextView mName;
    private TextView mSign;
    private Button mLoginButton;
    private LinearLayout mEntryContainer;

    private boolean mLastLogged;
    private UserInfo mUser;

    public MinePage(Activity activity) {
        mActivity = activity;
    }

    public View getView() {
        if (mView == null) {
            mView = LayoutInflater.from(mActivity).inflate(R.layout.activity_mine, null);
            mAvatar = (ImageView) mView.findViewById(R.id.avatar);
            mName = (TextView) mView.findViewById(R.id.name);
            mSign = (TextView) mView.findViewById(R.id.sign);
            mLoginButton = (Button) mView.findViewById(R.id.login_button);
            mEntryContainer = (LinearLayout) mView.findViewById(R.id.entry_container);

            // 点头像进个人主页（需登录）
            mAvatar.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!HttpUtil.isLoggedIn()) {
                        ToastUtil.show(mActivity, R.string.not_login);
                        return;
                    }
                    long mid = Sp.getLong(Sp.MID, 0);
                    Intent intent = new Intent(mActivity, UserActivity.class);
                    intent.putExtra("mid", mid);
                    if (mUser != null) {
                        intent.putExtra("name", mUser.name);
                    }
                    mActivity.startActivity(intent);
                }
            });

            // 头像下面是登录按钮
            mLoginButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mActivity.startActivity(new Intent(mActivity, LoginActivity.class));
                }
            });

            addEntry(R.drawable.ic_history, R.string.history_title, HistoryActivity.class);
            addEntry(R.drawable.ic_watchlater, R.string.watchlater_title, WatchLaterActivity.class);
            addEntry(R.drawable.ic_fav, R.string.favorite_folders, FavoriteFolderActivity.class);
            // 追番 / 追剧（自己的）
            addEntry(R.drawable.ic_fav, R.string.mine_bangumi, BangumiListActivity.class,
                    BangumiListActivity.TYPE_BANGUMI);
            addEntry(R.drawable.ic_follow, R.string.mine_tv, BangumiListActivity.class,
                    BangumiListActivity.TYPE_TV);
            addEntry(R.drawable.ic_follow, R.string.followed_list, FollowListActivity.class, true);
            addEntry(R.drawable.ic_follow, R.string.fans_list, FollowListActivity.class, false);
            addEntry(R.drawable.tab_message, R.string.message_list, MessageListActivity.class);
            addEntry(R.drawable.ic_download, R.string.download_title, DownloadActivity.class);
            addEntry(R.drawable.ic_settings, R.string.settings_title, SettingsActivity.class);
        }
        return mView;
    }

    private void addEntry(int icon, int textRes, final Class<?> target) {
        final View row = LayoutInflater.from(mActivity).inflate(R.layout.widget_item_entry, null);
        ((ImageView) row.findViewById(R.id.entry_icon)).setImageResource(icon);
        ((TextView) row.findViewById(R.id.entry_text)).setText(textRes);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mActivity.startActivity(new Intent(mActivity, target));
            }
        });
        mEntryContainer.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void addEntry(int icon, int textRes, final Class<?> target, final boolean following) {
        final View row = LayoutInflater.from(mActivity).inflate(R.layout.widget_item_entry, null);
        ((ImageView) row.findViewById(R.id.entry_icon)).setImageResource(icon);
        ((TextView) row.findViewById(R.id.entry_text)).setText(textRes);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!HttpUtil.isLoggedIn()) {
                    ToastUtil.show(mActivity, R.string.need_login);
                    return;
                }
                Intent intent = new Intent(mActivity, target);
                intent.putExtra("mid", Sp.getLong(Sp.MID, 0));
                intent.putExtra("following", following);
                mActivity.startActivity(intent);
            }
        });
        mEntryContainer.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /** 追番/追剧入口：跳 BangumiListActivity（自己的追番列表） */
    private void addEntry(int icon, int textRes, final Class<?> target, final int bangumiType) {
        final View row = LayoutInflater.from(mActivity).inflate(R.layout.widget_item_entry, null);
        ((ImageView) row.findViewById(R.id.entry_icon)).setImageResource(icon);
        ((TextView) row.findViewById(R.id.entry_text)).setText(textRes);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!HttpUtil.isLoggedIn()) {
                    ToastUtil.show(mActivity, R.string.need_login);
                    return;
                }
                Intent intent = new Intent(mActivity, target);
                intent.putExtra("vmid", Sp.getLong(Sp.MID, 0));
                intent.putExtra("type", bangumiType);
                mActivity.startActivity(intent);
            }
        });
        mEntryContainer.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    public void onShow() {
        if (mView == null) {
            return;
        }
        boolean logged = HttpUtil.isLoggedIn();
        if (logged == mLastLogged && mUser != null) {
            return;
        }
        mLastLogged = logged;
        updateHeader();
        if (logged) {
            loadUser();
        }
    }

    private void updateHeader() {
        if (HttpUtil.isLoggedIn()) {
            mLoginButton.setVisibility(View.GONE);
        } else {
            mLoginButton.setVisibility(View.VISIBLE);
            mName.setText(R.string.not_login);
            mSign.setVisibility(View.GONE);
            mAvatar.setImageResource(R.drawable.ic_avatar_default);
        }
    }

    private void loadUser() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final UserInfo user = LoginApi.checkLogin();
                mActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (user != null) {
                            mUser = user;
                            mName.setText(user.name + (user.level > 0 ? " Lv" + user.level : ""));
                            if (user.sign != null && user.sign.length() > 0) {
                                mSign.setVisibility(View.VISIBLE);
                                mSign.setText(user.sign);
                            }
                            ImageLoader.get().load(mAvatar, ImageLoader.avatarUrl(user.face, 128));
                        }
                    }
                });
            }
        }).start();
    }
}
