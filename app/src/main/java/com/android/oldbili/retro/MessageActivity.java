package com.android.oldbili.retro;

import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;

import com.android.oldbili.retro.api.MessageApi;
import com.android.oldbili.retro.model.PrivateMessage;
import com.android.oldbili.retro.net.HttpUtil;
import com.android.oldbili.retro.util.Sp;
import com.android.oldbili.retro.widget.ListAdapters;

import java.util.ArrayList;
import java.util.List;

/**
 * 私信聊天页：看历史消息、发消息、回复。
 */
public class MessageActivity extends BaseActivity {

    private long mUid;
    private String mName = "";
    private String mFace = "";
    private long mMyUid;
    private long mMinSeqno;     // 已加载最早消息的序号（翻更早记录用）
    private boolean mLoadingEarlier;

    private ListView mList;
    private EditText mInput;
    private Button mSend;
    private final List<PrivateMessage> mMessages = new ArrayList<PrivateMessage>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_message);

        mUid = getIntent().getLongExtra("uid", 0);
        mName = getIntent().getStringExtra("name");
        mFace = getIntent().getStringExtra("face");
        mMyUid = Sp.getLong(Sp.MID, 0);
        setTitle(mName != null && mName.length() > 0 ? mName : "私信");

        mList = (ListView) findViewById(R.id.msg_list);
        mInput = (EditText) findViewById(R.id.msg_input);
        mSend = (Button) findViewById(R.id.msg_send);

        // 滚到顶部时加载更早的历史消息（几千条也能往上翻）
        mList.setOnScrollListener(new android.widget.AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(android.widget.AbsListView view, int scrollState) {
            }

            @Override
            public void onScroll(android.widget.AbsListView view, int firstVisibleItem,
                                 int visibleItemCount, int totalItemCount) {
                if (!mLoadingEarlier && firstVisibleItem == 0 && totalItemCount > 0 && mMinSeqno > 0) {
                    loadEarlier();
                }
            }
        });

        mSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendMessage();
            }
        });

        // 表情面板：小电视/小黄脸常用表情，点一下插到输入框
        findViewById(R.id.msg_emoji).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showEmojiPanel();
            }
        });

        if (!HttpUtil.isLoggedIn()) {
            mSend.setEnabled(false);
            toast(R.string.need_login);
        }

        loadMessages();
    }

    /** B 站表情代码 → 本地近似 emoji（显示用） */
    private static final String[][] EMOJI_MAP = {
            {"[tv_doge]", "🐶"}, {"[tv_坏笑]", "😏"}, {"[tv_目瞪口呆]", "😳"}, {"[tv_微笑]", "🙂"},
            {"[tv_doge_累]", "😮‍💨"}, {"[tv_流泪]", "😭"}, {"[tv_思考]", "🤔"}, {"[tv_吐血]", "🩸"},
            {"[tv_困]", "😪"}, {"[tv_大佬]", "🕶️"}, {"[tv_抠鼻]", "😒"}, {"[tv_鼓掌]", "👏"},
            {"[doge]", "🐕"}, {"[抱抱]", "🤗"}, {"[笑哭]", "😂"}, {"[脱单doge]", "🐶❤️"},
            {"[大哭]", "😭"}, {"[妙啊]", "👌"}, {"[点赞]", "👍"}, {"[星星眼]", "🤩"},
    };

    /** 表情面板 */
    private void showEmojiPanel() {
        final String[] codes = new String[EMOJI_MAP.length];
        for (int i = 0; i < EMOJI_MAP.length; i++) {
            codes[i] = EMOJI_MAP[i][1] + " " + EMOJI_MAP[i][0];
        }
        final android.widget.GridView grid = new android.widget.GridView(this);
        grid.setNumColumns(5);
        grid.setPadding(6, 6, 6, 6);
        grid.setAdapter(new android.widget.BaseAdapter() {
            @Override
            public int getCount() {
                return codes.length;
            }

            @Override
            public Object getItem(int position) {
                return codes[position];
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                android.widget.TextView tv = (android.widget.TextView) convertView;
                if (tv == null) {
                    tv = new android.widget.TextView(MessageActivity.this);
                    tv.setGravity(android.view.Gravity.CENTER);
                    tv.setTextSize(16);
                    tv.setPadding(4, 8, 4, 8);
                }
                tv.setText(EMOJI_MAP[position][1]);
                return tv;
            }
        });
        grid.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(android.widget.AdapterView<?> parent, View view, int position, long id) {
                // 插入表情代码到光标处
                int start = mInput.getSelectionStart();
                int end = mInput.getSelectionEnd();
                String code = EMOJI_MAP[position][0];
                String text = mInput.getText().toString();
                String next = text.substring(0, Math.max(0, start)) + code + text.substring(Math.max(0, end));
                mInput.setText(next);
                mInput.setSelection(Math.max(0, start) + code.length());
            }
        });
        new android.app.AlertDialog.Builder(this)
                .setTitle("表情")
                .setView(grid)
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void loadMessages() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<PrivateMessage> messages = MessageApi.getMessages(mUid, 0);
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            mMessages.clear();
                            mMessages.addAll(messages);
                            if (mMessages.size() > 0) {
                                mMinSeqno = mMessages.get(0).seqno;
                            }
                            mList.setAdapter(new ListAdapters.MsgList(MessageActivity.this,
                                    mMessages, mMyUid, mFace));
                            mList.setSelection(mMessages.size() - 1);
                        }
                    });
                } catch (final Exception e) {
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            toast("消息加载失败");
                        }
                    });
                }
            }
        }).start();
    }

    /** 加载更早消息，插入顶部并保持视口位置 */
    private void loadEarlier() {
        if (mLoadingEarlier || mMinSeqno <= 0) {
            return;
        }
        mLoadingEarlier = true;
        final long endSeqno = mMinSeqno;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<PrivateMessage> earlier = MessageApi.getMessages(mUid, endSeqno);
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            mLoadingEarlier = false;
                            if (earlier.size() > 0) {
                                int added = earlier.size();
                                mMessages.addAll(0, earlier);
                                mMinSeqno = earlier.get(0).seqno > 0 ? earlier.get(0).seqno : endSeqno;
                                mList.setAdapter(new ListAdapters.MsgList(MessageActivity.this,
                                        mMessages, mMyUid, mFace));
                                // 保持原来视口：新条目插在顶部，偏移 added 条
                                mList.setSelection(added);
                            } else {
                                mMinSeqno = 0;   // 没有更早的了
                            }
                        }
                    });
                } catch (final Exception e) {
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            mLoadingEarlier = false;
                        }
                    });
                }
            }
        }).start();
    }

    private void sendMessage() {
        final String text = mInput.getText().toString().trim();
        if (text.length() == 0) {
            return;
        }
        mInput.setText("");
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(mInput.getWindowToken(), 0);
        }
        // 立即上屏自己发的消息（不再等慢接口刷新）
        final PrivateMessage selfMsg = new PrivateMessage();
        selfMsg.fromUid = mMyUid;
        selfMsg.time = System.currentTimeMillis() / 1000;
        selfMsg.text = text;
        runOnUi(new Runnable() {
            @Override
            public void run() {
                mMessages.add(selfMsg);
                // 适配器是快照副本，追加后直接重建
                mList.setAdapter(new ListAdapters.MsgList(MessageActivity.this, mMessages, mMyUid, mFace));
                mList.setSelection(mMessages.size() - 1);
            }
        });
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final int code = MessageApi.sendMessage(mMyUid, mUid, text);
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            if (code != 0) {
                                toast(R.string.message_send_failed);
                            }
                            // 后台静默刷新一次（带防抖，不阻塞界面）
                            loadMessages();
                        }
                    });
                } catch (final Exception e) {
                    runOnUi(new Runnable() {
                        @Override
                        public void run() {
                            toast(R.string.message_send_failed);
                        }
                    });
                }
            }
        }).start();
    }
}
