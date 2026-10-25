package com.android.oldbili.retro.widget;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;

import com.android.oldbili.retro.model.VideoCard;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 批量选择对话框：列表里点条目切换选中（深蓝底色标记），底部确认删除所选。
 * 用自带列表（白字深底），老设备上不会出现黑字问题。
 */
public class BatchSelectDialog {

    public interface OnConfirmListener {
        void onConfirm(List<Long> aids);
    }

    public interface OnConfirmStringsListener {
        void onConfirm(List<String> values);
    }

    /**
     * 历史列表批量选择：每行显示标题，返回选中的 kid 列表。
     */
    public static void showHistory(final Activity activity, String title,
                                   final List<com.android.oldbili.retro.model.HistoryItem> items,
                                   final OnConfirmStringsListener listener) {
        final Set<Integer> selected = new HashSet<Integer>();

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);

        final ListView listView = new ListView(activity);
        listView.setDivider(null);
        listView.setCacheColorHint(0x00000000);
        listView.setBackgroundColor(0xFF101010);
        listView.setAdapter(new ListAdapters.HistoryList(activity, items));

        final Button btn = new Button(activity);
        btn.setBackgroundColor(0xFF333333);
        btn.setTextColor(Color.WHITE);
        btn.setText("删除所选 (0)");

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (selected.contains(position)) {
                    selected.remove(position);
                    view.setBackgroundColor(0xFF101010);
                } else {
                    selected.add(position);
                    view.setBackgroundColor(0xFF1E3A4A);
                }
                btn.setText("删除所选 (" + selected.size() + ")");
            }
        });

        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                List<String> kids = new ArrayList<String>();
                for (Integer idx : selected) {
                    if (idx >= 0 && idx < items.size()) {
                        kids.add(items.get(idx).kid);
                    }
                }
                if (listener != null) {
                    listener.onConfirm(kids);
                }
            }
        });

        root.addView(listView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        root.addView(btn, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (int) (48 * activity.getResources().getDisplayMetrics().density)));

        new AlertDialog.Builder(activity)
                .setTitle(title)
                .setView(root)
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 用户列表批量选择（关注/粉丝等）：每行显示名字，返回选中的 mid 列表。
     */
    public static void showUsers(final Activity activity, String title,
                                 final List<com.android.oldbili.retro.model.UserInfo> items,
                                 final OnConfirmListener listener) {
        final Set<Integer> selected = new HashSet<Integer>();

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);

        final ListView listView = new ListView(activity);
        listView.setDivider(null);
        listView.setCacheColorHint(0x00000000);
        listView.setBackgroundColor(0xFF101010);
        listView.setAdapter(new ListAdapters.UserList(activity, items, false));

        final Button btn = new Button(activity);
        btn.setBackgroundColor(0xFF333333);
        btn.setTextColor(Color.WHITE);
        btn.setText("删除所选 (0)");

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (selected.contains(position)) {
                    selected.remove(position);
                    view.setBackgroundColor(0xFF101010);
                } else {
                    selected.add(position);
                    view.setBackgroundColor(0xFF1E3A4A);
                }
                btn.setText("删除所选 (" + selected.size() + ")");
            }
        });

        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                List<Long> mids = new ArrayList<Long>();
                for (Integer idx : selected) {
                    if (idx >= 0 && idx < items.size()) {
                        mids.add(items.get(idx).mid);
                    }
                }
                if (listener != null) {
                    listener.onConfirm(mids);
                }
            }
        });

        root.addView(listView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        root.addView(btn, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (int) (48 * activity.getResources().getDisplayMetrics().density)));

        new AlertDialog.Builder(activity)
                .setTitle(title)
                .setView(root)
                .setNegativeButton("取消", null)
                .show();
    }

    public static void show(final Activity activity, String title,
                            final List<VideoCard> items, final OnConfirmListener listener) {
        final Set<Integer> selected = new HashSet<Integer>();

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);

        final ListView listView = new ListView(activity);
        listView.setDivider(null);
        listView.setCacheColorHint(0x00000000);
        listView.setBackgroundColor(0xFF101010);
        listView.setAdapter(new ListAdapters.VideoList(activity, items));

        final Button btn = new Button(activity);
        btn.setBackgroundColor(0xFF333333);
        btn.setTextColor(Color.WHITE);
        btn.setText("删除所选 (0)");

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (selected.contains(position)) {
                    selected.remove(position);
                    view.setBackgroundColor(0xFF101010);
                } else {
                    selected.add(position);
                    view.setBackgroundColor(0xFF1E3A4A);
                }
                btn.setText("删除所选 (" + selected.size() + ")");
            }
        });

        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                List<Long> aids = new ArrayList<Long>();
                for (Integer idx : selected) {
                    if (idx >= 0 && idx < items.size()) {
                        aids.add(items.get(idx).aid);
                    }
                }
                if (listener != null) {
                    listener.onConfirm(aids);
                }
            }
        });

        root.addView(listView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        root.addView(btn, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (int) (48 * activity.getResources().getDisplayMetrics().density)));

        new AlertDialog.Builder(activity)
                .setTitle(title)
                .setView(root)
                .setNegativeButton("取消", null)
                .show();
    }
}
