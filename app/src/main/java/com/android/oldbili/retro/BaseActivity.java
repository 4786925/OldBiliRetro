package com.android.oldbili.retro;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.oldbili.retro.util.DensityUtil;
import com.android.oldbili.retro.util.ToastUtil;

/**
 * 所有页面的基类。
 *
 * 安卓3.0+ 用系统原生 Holo 深色 ActionBar；
 * 安卓2.x 没有 ActionBar，这里自动包一层自绘的黑色标题栏，风格尽量贴近。
 */
public class BaseActivity extends Activity {

    private TextView mCompatTitle;
    private boolean mShowBack = true;

    /** 日系按键手机：上下键在列表中移动选中项，确认键点击 */
    private int mKeyIndex = -1;

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        if (event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
            int code = event.getKeyCode();
            android.widget.ListView lv = findKeyListView();
            if (code == android.view.KeyEvent.KEYCODE_DPAD_DOWN
                    || code == android.view.KeyEvent.KEYCODE_DPAD_UP) {
                if (lv != null && lv.getAdapter() != null && lv.getAdapter().getCount() > 0) {
                    int count = lv.getAdapter().getCount();
                    if (mKeyIndex < 0) {
                        mKeyIndex = 0;
                    } else {
                        mKeyIndex += (code == android.view.KeyEvent.KEYCODE_DPAD_DOWN ? 1 : -1);
                    }
                    if (mKeyIndex < 0) {
                        mKeyIndex = 0;
                    }
                    if (mKeyIndex >= count) {
                        mKeyIndex = count - 1;
                    }
                    lv.setSelection(mKeyIndex);
                    lv.requestFocus();
                    return true;
                }
            } else if (code == android.view.KeyEvent.KEYCODE_DPAD_CENTER
                    || code == android.view.KeyEvent.KEYCODE_ENTER) {
                if (lv != null && lv.getAdapter() != null
                        && mKeyIndex >= 0 && mKeyIndex < lv.getAdapter().getCount()) {
                    int visible = mKeyIndex - lv.getFirstVisiblePosition();
                    View child = lv.getChildAt(visible);
                    lv.performItemClick(child, mKeyIndex, lv.getAdapter().getItemId(mKeyIndex));
                    return true;
                }
                View focus = getCurrentFocus();
                if (focus != null) {
                    focus.performClick();
                    return true;
                }
            }
            if (onKeypadKey(code)) {
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    /** 子类可覆盖：额外的按键快捷键（左右键等） */
    protected boolean onKeypadKey(int keyCode) {
        return false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 兜底：已创建好的视图（含列表项）也套上当前字体
        try {
            com.android.oldbili.retro.util.FontManager.applyTo(getWindow().getDecorView(), this);
        } catch (Exception ignored) {
        }
        mKeyIndex = -1;
    }

    private android.widget.ListView findKeyListView() {
        View v = findViewById(R.id.list_view);
        if (v instanceof android.widget.ListView) {
            return (android.widget.ListView) v;
        }
        return null;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        installFontFactory();
        super.onCreate(savedInstanceState);
        com.android.oldbili.retro.util.CrashHandler.setCurrentPage(getClass().getSimpleName());
    }

    @Override
    public void setContentView(int layoutResID) {
        View content = LayoutInflater.from(this).inflate(layoutResID, null);
        setContentView(content);
    }

    @Override
    public void setContentView(View view) {
        if (Build.VERSION.SDK_INT >= 11) {
            super.setContentView(view);
            setupActionBar();
            com.android.oldbili.retro.util.FontManager.applyTo(view, this);
        } else {
            super.setContentView(buildCompatRoot(view));
        }
    }

    @Override
    public void setContentView(View view, ViewGroup.LayoutParams params) {
        if (Build.VERSION.SDK_INT >= 11) {
            super.setContentView(view, params);
            setupActionBar();
        } else {
            super.setContentView(buildCompatRoot(view), params);
        }
    }

    // 安卓 2（SDK<11）天然是软件渲染：系统没有硬件加速管线，
    // Manifest 的 hardwareAccelerated 属性在 2.x 上被直接忽略——无需也无法"关闭"。
    // 注意：不能调用 View.setLayerType/LAYER_TYPE_SOFTWARE（API 11+），
    // 老 dalvik 运行到该引用会 NoSuchFieldError 直接崩溃（历史踩坑）。

    /** 3.0+ 的原生 ActionBar：显示返回箭头 */
    private void setupActionBar() {
        android.app.ActionBar bar = getActionBar();
        if (bar != null && mShowBack && !isTaskRoot()) {
            bar.setDisplayHomeAsUpEnabled(true);
        }
        applyCompatTitle();
    }

    /** 2.x 的兼容标题栏：黑色条 + 返回键 + 标题 */
    private View buildCompatRoot(View content) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setBackgroundColor(0xFF111111);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, DensityUtil.dp2px(this, 4), 0);
        LinearLayout.LayoutParams headerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, DensityUtil.dp2px(this, 44));
        root.addView(header, headerLp);

        if (mShowBack) {
            ImageButton back = new ImageButton(this);
            back.setImageResource(R.drawable.ic_back);
            back.setBackgroundDrawable(getResources().getDrawable(R.drawable.btn_transparent));
            back.setPadding(DensityUtil.dp2px(this, 6), 0, DensityUtil.dp2px(this, 6), 0);
            back.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
            LinearLayout.LayoutParams backLp = new LinearLayout.LayoutParams(
                    DensityUtil.dp2px(this, 44), ViewGroup.LayoutParams.MATCH_PARENT);
            header.addView(back, backLp);
        }

        mCompatTitle = new TextView(this);
        mCompatTitle.setTextColor(0xFFFFFFFF);
        mCompatTitle.setTextSize(18);
        mCompatTitle.setGravity(android.view.Gravity.CENTER_VERTICAL);
        mCompatTitle.setPadding(DensityUtil.dp2px(this, 10), 0, 0, 0);
        header.addView(mCompatTitle, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        applyCompatTitle();

        FrameLayout container = new FrameLayout(this);
        root.addView(container, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        container.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return root;
    }

    private void applyCompatTitle() {
        if (mCompatTitle != null) {
            mCompatTitle.setText(getTitle());
        }
    }

    @Override
    public void setTitle(CharSequence title) {
        super.setTitle(title);
        applyCompatTitle();
    }

    @Override
    public void setTitle(int titleId) {
        super.setTitle(titleId);
        applyCompatTitle();
    }

    /** 3.0+ ActionBar 顶部返回箭头：与物理返回键行为一致 */
    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /** 子类用：隐藏兼容标题栏的返回键（主页用） */
    protected void setShowBack(boolean show) {
        mShowBack = show;
    }

    /** 主线程执行 */
    protected void runOnUi(Runnable action) {
        runOnUiThread(action);
    }

    protected void toast(String text) {
        ToastUtil.show(this, text);
    }

    protected void toast(int resId) {
        ToastUtil.show(this, resId);
    }

    /** 是否已登录（Cookie 里有 SESSDATA） */
    protected boolean isLoggedIn() {
        return com.android.oldbili.retro.net.HttpUtil.isLoggedIn();
    }

    /** 写存储权限：安卓6+需要运行时申请，老系统直接就有 */
    protected boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= 23) {
            return checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    protected void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= 23) {
            requestPermissions(new String[]{
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    android.Manifest.permission.READ_EXTERNAL_STORAGE
            }, 100);
        }
    }

    // ==================== 自定义字体（LayoutInflater.Factory） ====================

    private void installFontFactory() {
        try {
            // 关键：把 Factory 装到底层 Context 的 inflater 上。
            // Activity/适配器用 LayoutInflater.from(ctx) 时是 cloneInContext，
            // clone 会复制 factory，所以从这里设置才能覆盖所有界面。
            final android.view.LayoutInflater base =
                    (android.view.LayoutInflater) getBaseContext()
                            .getSystemService(android.content.Context.LAYOUT_INFLATER_SERVICE);
            final android.view.LayoutInflater delegate = base.cloneInContext(this);
            android.view.LayoutInflater.Factory factory =
                    new android.view.LayoutInflater.Factory() {
                        @Override
                        public View onCreateView(String name, android.content.Context context,
                                                 android.util.AttributeSet attrs) {
                            return createFontViewStatic(delegate, name, context, attrs);
                        }
                    };
            base.setFactory(factory);
        } catch (Exception ignored) {
        }
    }

    private static final ThreadLocal<Boolean> sInFontFactory = new ThreadLocal<Boolean>();

    private static View createFontViewStatic(android.view.LayoutInflater delegate, String name,
                                             android.content.Context context,
                                             android.util.AttributeSet attrs) {
        // 防止 delegate 继承了本 factory 造成无限递归（表现为白屏后闪退）
        if (Boolean.TRUE.equals(sInFontFactory.get())) {
            return null;
        }
        sInFontFactory.set(Boolean.TRUE);
        try {
            View v = delegate.createView(name, null, attrs);
            if (v instanceof android.widget.TextView) {
                android.graphics.Typeface tf =
                        com.android.oldbili.retro.util.FontManager.get(context);
                if (tf != null) {
                    ((android.widget.TextView) v).setTypeface(tf);
                }
            }
            return v;
        } catch (Throwable e) {
            return null;
        } finally {
            sInFontFactory.remove();
        }
    }

}
