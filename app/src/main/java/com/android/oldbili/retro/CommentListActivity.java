package com.android.oldbili.retro;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.android.oldbili.retro.api.CommentApi;
import com.android.oldbili.retro.model.Comment;
import com.android.oldbili.retro.net.HttpUtil;
import com.android.oldbili.retro.util.Sp;
import com.android.oldbili.retro.widget.ListAdapters;
import com.android.oldbili.retro.widget.ListPage;

import java.util.ArrayList;
import java.util.List;

/**
 * 评论区独立页（老版B站就是单独一页），评论、回复、点赞都在这里。
 * 动态评论复用同一页（type=11，oid=动态id）。
 */
public class CommentListActivity extends BaseActivity {

    private ListPage mListPage;
    private EditText mInput;
    private Button mSend;
    private Button mPicture;

    private long mOid;
    private int mType = 1;
    private int mSort = 3;      // 3=热度 2=时间
    private String mCursor = "";
    private boolean mHasMore = true;
    private boolean mHadPicture;
    private byte[] mPictureBytes;
    private String mPictureName = "";
    private final List<Comment> mComments = new ArrayList<Comment>();

    private static final int REQ_PICK_IMAGE = 241;

    /** 图片发不出去时的统一提示（用户指定的文案） */
    private static final String IMAGE_SEND_FAIL = "可能不是大会员或者发送失败，请重试";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_comment_list);

        mOid = getIntent().getLongExtra("oid", 0);
        if (mOid == 0) {
            String oidStr = getIntent().getStringExtra("oid");
            if (oidStr != null && oidStr.length() > 0) {
                try {
                    mOid = Long.parseLong(oidStr.trim());
                } catch (NumberFormatException ignored) {
                }
            }
        }
        mType = getIntent().getIntExtra("type", 1);
        mSort = Sp.getInt(Sp.SET_COMMENT_SORT, 3);
        updateSortStyle();
        setTitle(mSort == 2 ? R.string.comment_sort_new_title : R.string.comment_sort_hot_title);

        mListPage = new ListPage(findViewById(R.id.comment_list_root));
        mInput = (EditText) findViewById(R.id.comment_input);
        mSend = (Button) findViewById(R.id.btn_send);
        mPicture = (Button) findViewById(R.id.btn_picture);

        // 排序切换：热门 / 最新
        findViewById(R.id.sort_hot).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mSort != 3) {
                    mSort = 3;
                    Sp.putInt(Sp.SET_COMMENT_SORT, 3);
                    updateSortStyle();
                    setTitle(R.string.comment_sort_hot_title);
                    startLoad();
                }
            }
        });
        findViewById(R.id.sort_new).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mSort != 2) {
                    mSort = 2;
                    Sp.putInt(Sp.SET_COMMENT_SORT, 2);
                    updateSortStyle();
                    setTitle(R.string.comment_sort_new_title);
                    startLoad();
                }
            }
        });

        mListPage.setOnLoadMore(new ListPage.OnLoadMoreListener() {
            @Override
            public void onLoadMore(int page) {
                loadMore();
            }
        });
        mListPage.setOnItemClick(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                int idx = position - mListPage.getListView().getHeaderViewsCount();
                if (idx >= 0 && idx < mComments.size()) {
                    showCommentActions(mComments.get(idx));
                }
            }
        });

        // 发评论（带图：先上传图片再发）
        mSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendComment(0, 0);
            }
        });
        // 选图
        mPicture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isLoggedIn()) {
                    toast(R.string.need_login);
                    startActivity(new android.content.Intent(CommentListActivity.this, LoginActivity.class));
                    return;
                }
                try {
                    android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_GET_CONTENT);
                    intent.setType("image/*");
                    intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
                    startActivityForResult(intent, REQ_PICK_IMAGE);
                } catch (Exception e) {
                    toast("无法打开相册");
                }
            }
        });

        if (!HttpUtil.isLoggedIn()) {
            mSend.setEnabled(false);
        }

        startLoad();
    }

    /** 排序样式：选中的高亮 */
    private void updateSortStyle() {
        TextView hot = (TextView) findViewById(R.id.sort_hot);
        TextView fresh = (TextView) findViewById(R.id.sort_new);
        hot.setTextColor(mSort == 3 ? 0xFF33B5E5 : 0xFF9A9A9A);
        fresh.setTextColor(mSort == 2 ? 0xFF33B5E5 : 0xFF9A9A9A);
    }

    private void startLoad() {
        mCursor = "";
        mHasMore = true;
        mComments.clear();
        // 排序切换/重载后清掉旧楼中楼对话框状态，避免"查看回复"打不开（必须退出重进）
        mSubRoot = null;
        mSubs.clear();
        mSubListView = null;
        mSubPage = 1;
        mSubHasMore = true;
        mSubLoading = false;
        loadMore();
    }

    private void loadMore() {
        if (!mHasMore) {
            return;
        }
        final String cursor = mCursor;
        final int sort = mSort;
        // 只有首屏才显示整页加载圈；翻页时列表保持不动，只走底部"加载中"
        if (mComments.isEmpty()) {
            mListPage.showLoading();
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Object[] result = CommentApi.getVideoComments(mOid, sort, cursor, 20, mComments.size());
                    final List<Comment> comments = (List<Comment>) result[0];
                    final String next = (String) result[1];
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            mCursor = next;
                            mHasMore = next != null && next.length() > 0;
                            // 翻页追加：记录当前滚动位置，重建 adapter 后恢复，避免跳到顶部
                            int first = 0;
                            int top = 0;
                            try {
                                android.widget.ListView lv = mListPage.getListView();
                                if (lv.getChildCount() > 0) {
                                    first = lv.getFirstVisiblePosition();
                                    View child = lv.getChildAt(0);
                                    top = child == null ? 0 : child.getTop();
                                }
                            } catch (Exception ignored) {
                            }
                            mComments.addAll(comments);
                            mListPage.setAdapter(new ListAdapters.CommentList(CommentListActivity.this, mComments));
                            if (first > 0) {
                                try {
                                    mListPage.getListView().setSelectionFromTop(first, top);
                                } catch (Exception ignored) {
                                }
                            }
                            mListPage.finishLoadMore(mHasMore);
                            if (mComments.size() == 0) {
                                mListPage.showEmpty("还没有评论，快来抢沙发");
                            } else {
                                mListPage.showContent();
                            }
                        }
                    });
                } catch (final Exception e) {
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            mListPage.showError(e.getMessage(), new Runnable() {
                                @Override
                                public void run() {
                                    startLoad();
                                }
                            });
                        }
                    });
                }
            }
        }).start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK_IMAGE || resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        try {
            android.net.Uri uri = data.getData();
            // 文件名（有些相册不给名字，用默认）
            String name = "image.jpg";
            String display = uri.getLastPathSegment();
            if (display != null && display.contains(".")) {
                name = display;
            }
            java.io.InputStream in = getContentResolver().openInputStream(uri);
            if (in == null) {
                toast("读取图片失败");
                return;
            }
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            int total = 0;
            while ((n = in.read(buf)) != -1) {
                total += n;
                if (total > 5 * 1024 * 1024) {
                    in.close();
                    toast("图片太大（限 5MB）");
                    return;
                }
                bos.write(buf, 0, n);
            }
            in.close();
            mPictureBytes = bos.toByteArray();
            mPictureName = name;
            toast("已选择图片：" + name);
        } catch (Exception e) {
            toast("读取图片失败");
        }
    }

    private void sendComment(final long root, final long parent) {
        if (!isLoggedIn()) {
            toast(R.string.need_login);
            startActivity(new android.content.Intent(this, LoginActivity.class));
            return;
        }
        final String text = mInput.getText().toString().trim();
        if (text.length() == 0) {
            toast("说点什么吧");
            return;
        }
        final boolean hadPicture = mHadPicture || mPictureBytes != null;
        mHadPicture = false;
        mInput.setText("");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // 带图评论：先传图（失败时保留图片和文字，提示后可原样重试）
                    String pictureUrl = "";
                    final byte[] data = mPictureBytes;
                    final String name = mPictureName;
                    if (data != null) {
                        try {
                            pictureUrl = CommentApi.uploadReplyImage(data, name);
                            android.util.Log.i("Comment", "图片上传结果: " + (pictureUrl == null ? "null" : pictureUrl));
                        } catch (Exception uploadEx) {
                            android.util.Log.w("Comment", "图片上传异常: " + uploadEx.getMessage());
                            pictureUrl = null;
                        }
                        if (pictureUrl == null) {
                            runOnUi(new Runnable() {
                                @Override
                                public void run() {
                                    toast(IMAGE_SEND_FAIL);
                                    // 恢复输入与选图，直接重试即可
                                    mInput.setText(text);
                                    mInput.setSelection(text.length());
                                    mPictureBytes = data;
                                    mPictureName = name;
                                }
                            });
                            return;
                        }
                        mPictureBytes = null;
                        mPictureName = "";
                    }
                    final String sentPicture = pictureUrl;
                    final long[] result = CommentApi.addComment(mType, mOid, text, root, parent, pictureUrl);
                    final long code = result[0];
                    final long action = result[1];
                    final long echoRoot = result[2];
                    final long echoParent = result[3];
                    final long echoRpid = result.length > 4 ? result[4] : 0;
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            if (code == 0) {
                                if (action == 2) {
                                    toast("评论已发送，但需要审核，暂时仅自己可见");
                                } else if (action != 0) {
                                    toast("评论已发送，但可能被系统拦截或进入审核");
                                } else {
                                    toast(sentPicture.length() > 0 ? "图文评论已发送" : "评论已发送");
                                }
                                if (echoRoot > 0 && echoParent > 0) {
                                    // 楼中楼回复：立即重新加载楼中楼列表
                                    reloadSubComments(echoRoot);
                                } else if (action != 2) {
                                    // 根评论：立即插入列表顶部显示，稍后静默刷新拿真实数据
                                    insertMyComment(text, echoRpid);
                                }
                                // 3 秒后静默刷新（评论已插在顶部，刷新回来自然合并）
                                mListPage.getListView().postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        startLoad();
                                    }
                                }, 3000);
                            } else if (sentPicture.length() > 0) {
                                toast(IMAGE_SEND_FAIL);
                                mInput.setText(text);
                                mInput.setSelection(text.length());
                            } else {
                                toast("发送失败(" + code + ")");
                            }
                        }
                    });
                } catch (final Exception e) {
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            toast(hadPicture ? IMAGE_SEND_FAIL : "发送失败");
                        }
                    });
                }
            }
        }).start();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(mInput.getWindowToken(), 0);
        }
    }

    private void showCommentActions(final Comment comment) {
        final java.util.List<String> actions = new ArrayList<String>();
        actions.add("回复");
        actions.add(comment.action == 1 ? "取消点赞" : "点赞");
        actions.add("查看回复");
        actions.add("复制");
        // 自己发的评论可以删除
        if (comment.mid > 0 && comment.mid == Sp.getLong(Sp.MID, 0)) {
            actions.add("删除");
        }
        new AlertDialog.Builder(this)
                .setItems(actions.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            showReplyInput(comment);
                        } else if (which == 1) {
                            doLikeComment(comment);
                        } else if (which == 2) {
                            showSubComments(comment);
                        } else if (which == 3) {
                            com.android.oldbili.retro.util.ClipboardCompat.copy(
                                    CommentListActivity.this, comment.message);
                            toast("已复制到剪贴板");
                        } else {
                            confirmDeleteComment(comment);
                        }
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void confirmDeleteComment(final Comment comment) {
        new AlertDialog.Builder(this)
                .setTitle("删除评论")
                .setMessage("确定删除这条评论吗？")
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        deleteComment(comment);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void deleteComment(final Comment comment) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final int code = CommentApi.delComment(mType, mOid, comment.rpid);
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            if (code == 0) {
                                toast("已删除");
                                mComments.remove(comment);
                                refreshCommentList();
                            } else {
                                toast("删除失败(" + code + ")");
                            }
                        }
                    });
                } catch (final Exception e) {
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            toast("删除失败");
                        }
                    });
                }
            }
        }).start();
    }

    private void showReplyInput(final Comment comment) {
        showReplyInput(comment, null);
    }

    /**
     * 回复输入框。
     * root 为 null 时是回复根评论（root=comment.rpid, parent=0）；
     * root 非 null 时是回复楼中楼里的子评论（root=根评论rpid, parent=子评论rpid），
     * 否则 B 站会把内容发成一条新的根评论。
     */
    private void showReplyInput(final Comment comment, final Comment root) {
        final EditText input = new EditText(this);
        input.setHint("回复 " + comment.userName);
        new AlertDialog.Builder(this)
                .setTitle(R.string.message_reply)
                .setView(input)
                .setPositiveButton(R.string.message_reply, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String text = input.getText().toString().trim();
                        if (text.length() == 0) {
                            return;
                        }
                        // B 站规则：回复根评论 root=parent=该评论rpid（parent 不能传 0，否则会被当成新根评论）；
                        // 回复子评论 root=根评论rpid, parent=子评论rpid
                        long rootRpid = root != null ? root.rpid : comment.rpid;
                        long parentRpid = root != null ? comment.rpid : comment.rpid;
                        sendReply(rootRpid, parentRpid, text);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void sendReply(final long rootRpid, final long parentRpid, final String text) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final long[] result = CommentApi.addComment(mType, mOid, text, rootRpid, parentRpid);
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            if (result[0] == 0) {
                                // 校验落点：接口回显的 root 必须等于请求的 root（rpid 是长整型，直接 long 比较）
                                if (rootRpid > 0 && result.length > 2 && result[2] != 0
                                        && result[2] != rootRpid) {
                                    toast("警告：回复落点异常（接口回显 root=" + result[2]
                                            + "，应为 " + rootRpid + "），请把日志发给开发者");
                                } else {
                                    toast(result[1] != 0 ? "回复已发送，但可能进入审核或不可见"
                                            : (parentRpid > 0 ? "回复成功（已进入楼中楼）" : "回复成功"));
                                    // 楼中楼回复成功且对话框还开着：立即重新加载楼中楼
                                    if (parentRpid > 0) {
                                        reloadSubComments(rootRpid);
                                    }
                                }
                            } else {
                                toast("回复失败(" + result[0] + ")");
                            }
                            startLoad();
                        }
                    });
                } catch (final Exception e) {
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            toast("回复失败");
                        }
                    });
                }
            }
        }).start();
    }

    private void doLikeComment(final Comment comment) {
        if (!isLoggedIn()) {
            toast(R.string.need_login);
            startActivity(new android.content.Intent(this, LoginActivity.class));
            return;
        }
        final boolean wantLike = comment.action != 1;   // 已赞再点 = 取消点赞
        // 乐观更新：先加/减 1 并刷新，接口失败再回滚
        comment.like = Math.max(0, comment.like + (wantLike ? 1 : -1));
        comment.action = wantLike ? 1 : 0;
        refreshCommentList();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final int code = CommentApi.likeComment(mType, mOid, comment.rpid, wantLike ? 1 : 0);
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            if (code == 0) {
                                toast(wantLike ? "点赞成功" : "已取消点赞");
                            } else {
                                // 回滚
                                comment.like = Math.max(0, comment.like + (wantLike ? -1 : 1));
                                comment.action = wantLike ? 0 : 1;
                                refreshCommentList();
                                toast(wantLike ? "点赞失败" : "取消点赞失败");
                            }
                        }
                    });
                } catch (final Exception e) {
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            comment.like = Math.max(0, comment.like + (wantLike ? -1 : 1));
                            comment.action = wantLike ? 0 : 1;
                            refreshCommentList();
                            toast("点赞失败");
                        }
                    });
                }
            }
        }).start();
    }

    /** 重建主评论列表（保持当前滚动位置） */
    private void refreshCommentList() {
        try {
            android.widget.ListView lv = mListPage.getListView();
            int first = lv.getFirstVisiblePosition();
            int top = lv.getChildCount() > 0 && lv.getChildAt(0) != null ? lv.getChildAt(0).getTop() : 0;
            mListPage.setAdapter(new ListAdapters.CommentList(CommentListActivity.this, mComments));
            if (first > 0) {
                lv.setSelectionFromTop(first, top);
            }
        } catch (Exception ignored) {
        }
    }

    /** 发送成功后把自己的评论立即插到列表顶部 */
    private void insertMyComment(String text, long rpid) {
        try {
            Comment mine = new Comment();
            mine.rpid = rpid;
            mine.mid = Sp.getLong(Sp.MID, 0);
            mine.userName = "我";
            mine.message = text;
            mine.time = System.currentTimeMillis() / 1000;
            mine.like = 0;
            mComments.add(0, mine);
            mListPage.setAdapter(new ListAdapters.CommentList(CommentListActivity.this, mComments));
            mListPage.showContent();
            mListPage.getListView().setSelection(0);
        } catch (Exception ignored) {
        }
    }

    // ==================== 楼中楼（支持分批加载） ====================

    private Comment mSubRoot;
    private final List<Comment> mSubs = new ArrayList<Comment>();
    private ListView mSubListView;
    private int mSubPage = 1;
    private boolean mSubHasMore = true;
    private boolean mSubLoading;

    private void showSubComments(final Comment comment) {
        mSubRoot = comment;
        mSubs.clear();
        mSubPage = 1;
        mSubHasMore = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<Comment> firstPage = CommentApi.getSubComments(mOid, comment.rpid, 1);
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            mSubs.addAll(firstPage);
                            mSubHasMore = firstPage.size() >= 10;
                            openSubDialog();
                        }
                    });
                } catch (final Exception e) {
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            toast("加载失败");
                        }
                    });
                }
            }
        }).start();
    }

    /** 打开/更新楼中楼对话框 */
    private void openSubDialog() {
        final Comment root = mSubRoot;
        if (root == null) {
            return;
        }
        if (mSubListView == null) {
            mSubListView = new ListView(CommentListActivity.this);
            mSubListView.setBackgroundColor(0xFF101010);
            mSubListView.setDivider(null);
            // 滑到底部分批加载
            mSubListView.setOnScrollListener(new android.widget.AbsListView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(android.widget.AbsListView view, int scrollState) {
                }

                @Override
                public void onScroll(android.widget.AbsListView view, int firstVisibleItem,
                                     int visibleItemCount, int totalItemCount) {
                    if (!mSubLoading && mSubHasMore && totalItemCount > 0
                            && firstVisibleItem + visibleItemCount >= totalItemCount - 1) {
                        loadSubPage();
                    }
                }
            });
            mSubListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    if (position < 0 || position >= mSubs.size()) {
                        return;
                    }
                    showSubActions(mSubs.get(position));
                }
            });
            new AlertDialog.Builder(CommentListActivity.this)
                    .setTitle("回复")
                    .setView(mSubListView)
                    .setPositiveButton(R.string.confirm, null)
                    .show();
        }
        mSubListView.setAdapter(new ListAdapters.CommentList(CommentListActivity.this, mSubs));
    }

    /** 楼中楼子评论操作：回复TA/点赞/查看主页/复制 */
    private void showSubActions(final Comment sub) {
        final Comment root = mSubRoot;
        final java.util.List<String> actions = new ArrayList<String>();
        actions.add("回复TA");
        actions.add(sub.action == 1 ? "取消点赞" : "点赞");
        actions.add("查看TA的主页");
        actions.add("复制");
        new AlertDialog.Builder(CommentListActivity.this)
                .setItems(actions.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            // 回复子评论：带上根评论，落到该条的楼中楼里
                            if (root != null) {
                                showReplyInput(sub, root);
                            }
                        } else if (which == 1) {
                            doLikeComment(sub);
                        } else if (which == 2) {
                            android.content.Intent intent = new android.content.Intent(
                                    CommentListActivity.this, UserActivity.class);
                            intent.putExtra("mid", sub.mid);
                            intent.putExtra("name", sub.userName);
                            startActivity(intent);
                        } else {
                            com.android.oldbili.retro.util.ClipboardCompat.copy(
                                    CommentListActivity.this, sub.message);
                            toast("已复制到剪贴板");
                        }
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** 分批加载楼中楼下一页 */
    private void loadSubPage() {
        final Comment root = mSubRoot;
        if (root == null || mSubLoading || !mSubHasMore) {
            return;
        }
        mSubLoading = true;
        final int page = mSubPage + 1;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<Comment> more = CommentApi.getSubComments(mOid, root.rpid, page);
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            mSubLoading = false;
                            mSubPage = page;
                            mSubHasMore = more.size() >= 10;
                            mSubs.addAll(more);
                            if (mSubListView != null) {
                                mSubListView.setAdapter(new ListAdapters.CommentList(
                                        CommentListActivity.this, mSubs));
                            }
                        }
                    });
                } catch (final Exception e) {
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            mSubLoading = false;
                        }
                    });
                }
            }
        }).start();
    }

    /** 回复楼中楼成功后：立即重新拉第一页刷新（若对话框还开着） */
    private void reloadSubComments(final long root) {
        if (mSubRoot == null || mSubRoot.rpid != root || mSubListView == null) {
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<Comment> firstPage = CommentApi.getSubComments(mOid, mSubRoot.rpid, 1);
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            mSubs.clear();
                            mSubs.addAll(firstPage);
                            mSubPage = 1;
                            mSubHasMore = firstPage.size() >= 10;
                            mSubListView.setAdapter(new ListAdapters.CommentList(
                                    CommentListActivity.this, mSubs));
                        }
                    });
                } catch (Exception ignored) {
                }
            }
        }).start();
    }
}
