package com.android.oldbili.retro.widget;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.preference.ListPreference;
import android.util.AttributeSet;

import com.android.oldbili.retro.util.DialogUtil;

/**
 * 深色主题版 ListPreference：框架自带的选择对话框在安卓 2.x 上是黑字，
 * 深色背景下看不清。这里换成白字深底的复古单选对话框（●/○ 标记）。
 */
public class RetroListPreference extends ListPreference {

    public RetroListPreference(Context context) {
        super(context);
    }

    public RetroListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void showDialog(Bundle state) {
        final CharSequence[] entries = getEntries();
        final CharSequence[] values = getEntryValues();
        if (entries == null || values == null || entries.length == 0) {
            return;
        }
        String[] entryStrs = new String[entries.length];
        for (int i = 0; i < entries.length; i++) {
            entryStrs[i] = String.valueOf(entries[i]);
        }
        int checked = Math.max(0, findIndexOfValue(getValue()));
        final RetroListPreference self = this;
        AlertDialog dialog = DialogUtil.choice(getContext(), getTitle() == null ? "" : getTitle().toString(),
                entryStrs, checked, new DialogUtil.OnPickListener() {
                    @Override
                    public void onPick(int index) {
                        if (index >= 0 && index < values.length) {
                            self.setValue(String.valueOf(values[index]));
                            // setValue 会走 persist；再同步一下 summary 显示
                            notifyChanged();
                        }
                    }
                });
        dialog.show();
    }
}
