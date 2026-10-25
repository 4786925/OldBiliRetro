package com.android.oldbili.retro.util;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.android.oldbili.retro.R;

/**
 * 深色单选对话框：白字深底，自带"●/○"单选标记。
 * 框架自带的 setItems/setSingleChoiceItems 在安卓 2.x 上列表行是黑字，
 * 深色主题下根本看不清——所有"点开选一项"的对话框统一走这里。
 */
public class DialogUtil {

    public interface OnPickListener {
        /** 用户选中了第几项（从 0 开始） */
        void onPick(int index);
    }

    /** 单选对话框，checked 传当前选中项（-1 表示没有） */
    public static AlertDialog choice(final Context context, String title,
                                     final String[] entries, int checked,
                                     final OnPickListener listener) {
        final int checkedIndex = checked;
        BaseAdapter adapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return entries == null ? 0 : entries.length;
            }

            @Override
            public Object getItem(int position) {
                return entries[position];
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView view;
                if (convertView instanceof TextView) {
                    view = (TextView) convertView;
                } else {
                    view = (TextView) View.inflate(context,
                            R.layout.widget_choice_item, null);
                }
                boolean selected = position == checkedIndex;
                view.setText((selected ? "● " : "○ ") + entries[position]);
                view.setTextColor(selected ? 0xFF33B5E5 : 0xFFD9D9D9);
                return view;
            }
        };

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(title)
                .setAdapter(adapter, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (listener != null) {
                            listener.onPick(which);
                        }
                    }
                })
                .create();
        try {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_bg);
        } catch (Exception ignored) {
        }
        // 2.x 上 AlertDialog 标题默认继承活动主题，这里再强制一次
        try {
            int titleId = context.getResources().getIdentifier("alertTitle", "id", "android");
            if (titleId > 0 && dialog.findViewById(titleId) instanceof TextView) {
                ((TextView) dialog.findViewById(titleId)).setTextColor(Color.WHITE);
            }
        } catch (Exception ignored) {
        }
        return dialog;
    }

    /** Activity 快捷入口 */
    public static AlertDialog choice(Activity activity, String title,
                                     String[] entries, int checked,
                                     OnPickListener listener) {
        return choice((Context) activity, title, entries, checked, listener);
    }
}
