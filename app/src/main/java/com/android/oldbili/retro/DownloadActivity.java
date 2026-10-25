package com.android.oldbili.retro;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import com.android.oldbili.retro.download.DownloadManager;
import com.android.oldbili.retro.download.DownloadPathUtil;
import com.android.oldbili.retro.download.DownloadTask;
import com.android.oldbili.retro.net.ImageLoader;
import com.android.oldbili.retro.util.ClipboardCompat;
import com.android.oldbili.retro.widget.ListHolders;
import com.android.oldbili.retro.widget.ListPage;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 下载管理：支持多选、批量开始、批量删除（二次确认：记录 / 记录+文件）、打开文件位置。
 */
public class DownloadActivity extends BaseActivity {

    private static final int REQ_PICK_DIR = 2001;

    private ListPage mListPage;
    private DownloadManager mManager;
    private TaskAdapter mAdapter;

    /** 多选模式 */
    private boolean mMultiMode;
    /** 已选任务 key（fileName 优先，空则 title+cid） */
    private final LinkedHashSet<String> mSelected = new LinkedHashSet<String>();

    private Button mBtnMulti;
    private Button mBtnSelectAll;
    private TextView mTxtSelected;
    private View mBatchBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.download_title);
        setContentView(R.layout.activity_download);

        mManager = DownloadManager.get(this);
        mListPage = new ListPage(findViewById(R.id.download_list_root));
        mListPage.setEnableLoadMore(false);
        mAdapter = new TaskAdapter(mManager.getTasks());
        mListPage.setAdapter(mAdapter);
        mListPage.showContent();

        mBtnMulti = (Button) findViewById(R.id.btn_multi);
        mBtnSelectAll = (Button) findViewById(R.id.btn_select_all);
        mTxtSelected = (TextView) findViewById(R.id.txt_selected);
        mBatchBar = findViewById(R.id.batch_bar);

        mManager.addListener(mListener);

        // 多选开关
        mBtnMulti.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setMultiMode(!mMultiMode);
            }
        });

        // 全选 / 全不选
        mBtnSelectAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                List<DownloadTask> tasks = mManager.getTasks();
                if (mSelected.size() >= tasks.size() && tasks.size() > 0) {
                    mSelected.clear();
                } else {
                    mSelected.clear();
                    for (int i = 0; i < tasks.size(); i++) {
                        mSelected.add(keyOf(tasks.get(i)));
                    }
                }
                updateSelectionUi();
            }
        });

        // 批量开始
        findViewById(R.id.btn_batch_start).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                batchStart();
            }
        });

        // 批量删除（二次确认）
        findViewById(R.id.btn_batch_delete).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                batchDelete();
            }
        });

        // 列表项点击：多选模式下切换选中
        mListPage.setOnItemClick(new android.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(android.widget.AdapterView<?> parent, View view, int position, long id) {
                int idx = position - mListPage.getListView().getHeaderViewsCount();
                List<DownloadTask> tasks = mManager.getTasks();
                if (idx < 0 || idx >= tasks.size()) {
                    return;
                }
                DownloadTask task = tasks.get(idx);
                if (mMultiMode) {
                    toggleSelect(task);
                } else if (task.state == DownloadTask.STATE_DONE) {
                    showLocationDialog(task);
                }
            }
        });

        // 长按：单条操作菜单
        mListPage.setOnItemLongClick(new android.widget.AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(android.widget.AdapterView<?> parent, View view, int position, long id) {
                int idx = position - mListPage.getListView().getHeaderViewsCount();
                List<DownloadTask> tasks = mManager.getTasks();
                if (idx < 0 || idx >= tasks.size()) {
                    return true;
                }
                showTaskMenu(tasks.get(idx));
                return true;
            }
        });

        findViewById(R.id.btn_pick_dir).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickDirectory();
            }
        });
        findViewById(R.id.btn_open_dir).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openDownloadFolder();
            }
        });

        refreshView();
    }

    // ==================== 多选 ====================

    private String keyOf(DownloadTask task) {
        if (!TextUtils.isEmpty(task.fileName)) {
            return task.fileName;
        }
        return (task.title == null ? "" : task.title) + "#" + task.cid;
    }

    private boolean isSelected(DownloadTask task) {
        return mSelected.contains(keyOf(task));
    }

    private void toggleSelect(DownloadTask task) {
        String key = keyOf(task);
        if (mSelected.contains(key)) {
            mSelected.remove(key);
        } else {
            mSelected.add(key);
        }
        updateSelectionUi();
    }

    private void setMultiMode(boolean on) {
        mMultiMode = on;
        if (!on) {
            mSelected.clear();
        }
        mBtnMulti.setText(on ? R.string.download_multi_exit : R.string.download_multi);
        mBtnSelectAll.setVisibility(on ? View.VISIBLE : View.GONE);
        mTxtSelected.setVisibility(on ? View.VISIBLE : View.GONE);
        mBatchBar.setVisibility(on ? View.VISIBLE : View.GONE);
        updateSelectionUi();
    }

    private void updateSelectionUi() {
        if (mTxtSelected != null) {
            int count = mSelected.size();
            mTxtSelected.setText(count > 0
                    ? getString(R.string.download_selected_count, count) : "");
        }
        mAdapter.notifyDataSetChanged();
    }

    private List<DownloadTask> selectedTasks() {
        List<DownloadTask> all = mManager.getTasks();
        List<DownloadTask> picked = new ArrayList<DownloadTask>();
        for (int i = 0; i < all.size(); i++) {
            if (mSelected.contains(keyOf(all.get(i)))) {
                picked.add(all.get(i));
            }
        }
        return picked;
    }

    private void batchStart() {
        List<DownloadTask> tasks = selectedTasks();
        if (tasks.size() == 0) {
            toast(R.string.download_batch_none);
            return;
        }
        int started = 0;
        for (int i = 0; i < tasks.size(); i++) {
            DownloadTask t = tasks.get(i);
            if (t.state == DownloadTask.STATE_PAUSED
                    || t.state == DownloadTask.STATE_FAILED
                    || t.state == DownloadTask.STATE_WAITING) {
                mManager.resume(t);
                started++;
            }
        }
        toast(getString(R.string.download_batch_started, started));
        if (started == 0) {
            toast("所选任务都已在下载或已完成");
        }
        setMultiMode(false);
        refreshView();
    }

    /** 批量删除：第一次确认数量，第二次选择"仅记录 / 记录+文件" */
    private void batchDelete() {
        final List<DownloadTask> tasks = selectedTasks();
        if (tasks.size() == 0) {
            toast(R.string.download_batch_none);
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.download_delete_confirm_title)
                .setMessage(getString(R.string.download_delete_confirm_msg, tasks.size()))
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        chooseDeleteMode(tasks);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void chooseDeleteMode(final List<DownloadTask> tasks) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.download_delete_choice_title)
                .setItems(new String[]{
                        getString(R.string.download_delete_record),
                        getString(R.string.download_delete_record_file)
                }, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mManager.deleteBatch(tasks, which == 1);
                        toast(R.string.download_deleted);
                        setMultiMode(false);
                        refreshView();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ==================== 单条操作 ====================

    private void showTaskMenu(final DownloadTask task) {
        boolean downloading = task.state == DownloadTask.STATE_DOWNLOADING
                || task.state == DownloadTask.STATE_WAITING;
        List<String> items = new ArrayList<String>();
        items.add(getString(R.string.download_open_location));
        if (task.state == DownloadTask.STATE_DONE) {
            items.add(getString(R.string.download_delete_record));
        } else if (downloading) {
            items.add(getString(R.string.download_pause));
        } else {
            items.add(getString(R.string.download_resume));
        }
        items.add(getString(R.string.download_delete_record));
        items.add(getString(R.string.download_delete_record_file));

        final String[] arr = items.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle(task.title)
                .setItems(arr, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String label = arr[which];
                        if (label.equals(getString(R.string.download_open_location))) {
                            showLocationDialog(task);
                        } else if (label.equals(getString(R.string.download_pause))) {
                            mManager.pause(task);
                            refreshView();
                        } else if (label.equals(getString(R.string.download_resume))) {
                            mManager.resume(task);
                            refreshView();
                        } else if (label.equals(getString(R.string.download_delete_record))) {
                            confirmDelete(task, false);
                        } else {
                            confirmDelete(task, true);
                        }
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void confirmDelete(final DownloadTask task, final boolean withFile) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.download_delete_confirm_title)
                .setMessage("确定删除「" + task.title + "」吗？")
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        List<DownloadTask> one = new ArrayList<DownloadTask>();
                        one.add(task);
                        mManager.deleteBatch(one, withFile);
                        toast(R.string.download_deleted);
                        refreshView();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** 打开文件位置：显示路径，可复制；老系统尝试用文件管理器直接打开 */
    private void showLocationDialog(DownloadTask task) {
        DownloadPathUtil.Folder folder = DownloadPathUtil.resolve(this);
        String path;
        if (folder instanceof DownloadPathUtil.FileFolder) {
            java.io.File dir = DownloadPathUtil.getPublicDownloadsDir();
            path = dir.getAbsolutePath();
        } else {
            path = folder.describe();
        }
        final String fullPath = path + "/" + (TextUtils.isEmpty(task.fileName)
                ? "" : task.fileName);
        new AlertDialog.Builder(this)
                .setTitle(R.string.download_path_title)
                .setMessage(fullPath + "\n\n" + getString(R.string.download_path_tip))
                .setPositiveButton(R.string.download_copy_path, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ClipboardCompat.copyOrShow(DownloadActivity.this, fullPath,
                                getString(R.string.download_path_copied));
                    }
                })
                .setNeutralButton("用文件管理器打开", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        openDownloadFolder();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ==================== 监听 ====================

    private final DownloadManager.Listener mListener = new DownloadManager.Listener() {
        @Override
        public void onTaskChanged(DownloadTask task) {
            runOnUi(new Runnable() {
                @Override
                public void run() {
                    refreshView();
                }
            });
        }

        @Override
        public void onTaskRemoved(DownloadTask task) {
            runOnUi(new Runnable() {
                @Override
                public void run() {
                    refreshView();
                }
            });
        }
    };

    private void refreshView() {
        List<DownloadTask> tasks = mManager.getTasks();
        if (tasks.size() == 0) {
            mListPage.showEmpty("还没有下载任务");
        } else {
            mListPage.showContent();
        }
        mAdapter.setData(tasks);
        updateSelectionUi();
    }

    /** 安卓13+ 或写入被收紧时，用系统文件选择器挑下载目录 */
    private void pickDirectory() {
        if (Build.VERSION.SDK_INT >= 21) {
            try {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                startActivityForResult(intent, REQ_PICK_DIR);
                return;
            } catch (Exception ignored) {
            }
        }
        toast(R.string.download_folder_tip);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_DIR && resultCode == RESULT_OK && data != null && data.getData() != null) {
            try {
                getContentResolver().takePersistableUriPermission(data.getData(),
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                DownloadPathUtil.saveSafDir(data.getData());
                toast(R.string.download_dir_picked);
            } catch (Exception e) {
                toast("目录授权失败");
            }
        }
    }

    /** 打开下载文件夹（老系统直接交给文件管理器；新系统显示路径供复制） */
    private void openDownloadFolder() {
        DownloadPathUtil.Folder folder = DownloadPathUtil.resolve(this);
        if (!(folder instanceof DownloadPathUtil.FileFolder)) {
            toast(folder.describe());
            return;
        }
        java.io.File dir = DownloadPathUtil.getPublicDownloadsDir();
        if (!dir.exists()) {
            dir.mkdirs();
        }
        if (Build.VERSION.SDK_INT < 24) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(Uri.fromFile(dir), "resource/folder");
                startActivity(intent);
                return;
            } catch (Exception ignored) {
            }
        }
        // 新系统 file:// 会被 FileUriExposedException 拦截：给出路径让用户复制
        showFolderPathDialog(dir);
    }

    private void showFolderPathDialog(final java.io.File dir) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.download_path_title)
                .setMessage(dir.getAbsolutePath() + "\n\n" + getString(R.string.download_path_tip))
                .setPositiveButton(R.string.download_copy_path, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ClipboardCompat.copyOrShow(DownloadActivity.this, dir.getAbsolutePath(),
                                getString(R.string.download_path_copied));
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    protected void onDestroy() {
        mManager.removeListener(mListener);
        super.onDestroy();
    }

    // ==================== 列表适配器 ====================

    private class TaskAdapter extends BaseAdapter {

        private List<DownloadTask> mTasks;
        private final LayoutInflater mInflater = LayoutInflater.from(DownloadActivity.this);

        TaskAdapter(List<DownloadTask> tasks) {
            mTasks = tasks;
        }

        void setData(List<DownloadTask> tasks) {
            mTasks = tasks;
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return mTasks == null ? 0 : mTasks.size();
        }

        @Override
        public Object getItem(int position) {
            return mTasks.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ListHolders.DownloadHolder holder;
            if (convertView == null) {
                holder = new ListHolders.DownloadHolder(mInflater, parent);
                convertView = holder.itemView;
                convertView.setTag(holder);
            } else {
                holder = (ListHolders.DownloadHolder) convertView.getTag();
            }
            final DownloadTask task = mTasks.get(position);
            holder.title.setText(task.part != null && task.part.length() > 0
                    ? task.title + " · " + task.part : task.title);
            holder.status.setText(task.stateDesc());
            holder.quality.setText(task.qualityName);
            ImageLoader.get().load(holder.cover, ImageLoader.coverUrl(task.cover, 160, 100));

            // 多选模式下高亮选中项
            if (mMultiMode && isSelected(task)) {
                convertView.setBackgroundColor(0xFF1F3A48);
            } else {
                convertView.setBackgroundColor(0x00000000);
            }

            if (task.state == DownloadTask.STATE_DOWNLOADING) {
                holder.progress.setVisibility(View.VISIBLE);
                holder.progress.setProgress(task.percent());
                holder.action.setText(R.string.download_pause);
            } else {
                holder.progress.setVisibility(View.GONE);
                if (task.state == DownloadTask.STATE_DONE) {
                    holder.action.setText("打开位置");
                } else {
                    holder.action.setText(R.string.download_resume);
                }
            }
            holder.action.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (task.state == DownloadTask.STATE_DOWNLOADING
                            || task.state == DownloadTask.STATE_WAITING) {
                        mManager.pause(task);
                        refreshView();
                    } else if (task.state == DownloadTask.STATE_DONE) {
                        showLocationDialog(task);
                    } else {
                        mManager.resume(task);
                        refreshView();
                    }
                }
            });
            return convertView;
        }
    }
}
