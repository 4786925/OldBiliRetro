package com.android.oldbili.retro;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;

import com.android.oldbili.retro.download.DownloadManager;
import com.android.oldbili.retro.download.DownloadPathUtil;
import com.android.oldbili.retro.download.DownloadTask;
import com.android.oldbili.retro.net.ImageLoader;
import com.android.oldbili.retro.widget.ListHolders;
import com.android.oldbili.retro.widget.ListPage;

import java.util.List;

/**
 * 下载管理：任务进度、暂停/继续/删除、选择下载目录、打开下载文件夹。
 */
public class DownloadActivity extends BaseActivity {

    private static final int REQ_PICK_DIR = 2001;

    private ListPage mListPage;
    private DownloadManager mManager;
    private TaskAdapter mAdapter;

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

        mManager.addListener(mListener);

        // 长按删除：先"是/否"确认，再选"删除记录 / 删除记录和文件"
        mListPage.setOnItemLongClick(new android.widget.AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(android.widget.AdapterView<?> parent, View view, int position, long id) {
                int idx = position - mListPage.getListView().getHeaderViewsCount();
                final List<DownloadTask> tasks = mManager.getTasks();
                if (idx < 0 || idx >= tasks.size()) {
                    return true;
                }
                final DownloadTask task = tasks.get(idx);
                new android.app.AlertDialog.Builder(DownloadActivity.this)
                        .setTitle("删除下载")
                        .setMessage("是否删除「" + task.title + "」？")
                        .setPositiveButton("是", new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface dialog, int which) {
                                new android.app.AlertDialog.Builder(DownloadActivity.this)
                                        .setTitle("删除下载")
                                        .setItems(new String[]{"删除记录", "删除记录和文件"},
                                                new android.content.DialogInterface.OnClickListener() {
                                                    @Override
                                                    public void onClick(android.content.DialogInterface d, int w) {
                                                        if (w == 0) {
                                                            mManager.deleteRecord(task);
                                                        } else {
                                                            mManager.delete(task);
                                                        }
                                                        refreshView();
                                                    }
                                                })
                                        .setNegativeButton(R.string.cancel, null)
                                        .show();
                            }
                        })
                        .setNegativeButton("否", null)
                        .show();
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
                openDirectory();
            }
        });

        refreshView();
    }

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

    private void openDirectory() {
        DownloadPathUtil.Folder folder = DownloadPathUtil.resolve(this);
        if (folder instanceof DownloadPathUtil.FileFolder) {
            try {
                java.io.File dir = DownloadPathUtil.getPublicDownloadsDir();
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                Intent intent = new Intent(Intent.ACTION_VIEW);
                if (Build.VERSION.SDK_INT >= 24) {
                    toast("下载目录：" + dir.getAbsolutePath());
                    return;
                }
                intent.setDataAndType(Uri.fromFile(dir), "resource/folder");
                startActivity(intent);
            } catch (Exception e) {
                toast("没有可用的文件管理器");
            }
        } else {
            toast(folder.describe());
        }
    }

    @Override
    protected void onDestroy() {
        mManager.removeListener(mListener);
        super.onDestroy();
    }

    /** 下载任务列表 */
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

            if (task.state == DownloadTask.STATE_DOWNLOADING) {
                holder.progress.setVisibility(View.VISIBLE);
                holder.progress.setProgress(task.percent());
                holder.action.setText(R.string.download_pause);
            } else {
                holder.progress.setVisibility(View.GONE);
                if (task.state == DownloadTask.STATE_DONE) {
                    holder.action.setText(R.string.download_delete);
                } else if (task.state == DownloadTask.STATE_FAILED) {
                    holder.action.setText(R.string.download_resume);
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
                    } else if (task.state == DownloadTask.STATE_DONE) {
                        mManager.delete(task);
                        refreshView();
                    } else {
                        mManager.resume(task);
                    }
                }
            });
            return convertView;
        }
    }
}
