package com.sggdev.wcwebcameracontrol;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE;
import static com.sggdev.wcsdk.ChatDatabase.MEDIA_LOC;
import static com.sggdev.wcsdk.ChatDatabase.MSG_STATE_READY_TO_SEND;
import static com.sggdev.wcsdk.ChatDatabase.MSG_STATE_SENDED;
import static com.sggdev.wcsdk.DeviceItem.DEFAULT_DEVICE_COLOR;
import static com.sggdev.wcwebcameracontrol.IntentConsts.EXTRAS_DEVICE_ADDRESS;
import static com.sggdev.wcwebcameracontrol.IntentConsts.EXTRAS_DEVICE_BLE_NAME;
import static com.sggdev.wcwebcameracontrol.IntentConsts.EXTRAS_DEVICE_COLOR;
import static com.sggdev.wcwebcameracontrol.IntentConsts.EXTRAS_DEVICE_HOST_NAME;
import static com.sggdev.wcwebcameracontrol.IntentConsts.EXTRAS_DEVICE_INDEX;
import static com.sggdev.wcwebcameracontrol.IntentConsts.EXTRAS_DEVICE_LST_SYNC;
import static com.sggdev.wcwebcameracontrol.IntentConsts.EXTRAS_DEVICE_WRITE_ID;
import static com.sggdev.wcwebcameracontrol.IntentConsts.EXTRAS_IMAGE_BITMAP;
import static com.sggdev.wcwebcameracontrol.IntentConsts.EXTRAS_TARGET_DEVICE_ID;
import static com.sggdev.wcwebcameracontrol.IntentConsts.EXTRAS_USER_DEVICE_ID;
import static com.sggdev.wcwebcameracontrol.WCPicPreviewHelper.MIN_PIC_SIZE_DPI;
import static com.sggdev.wcsdk.WCRESTProtocol.REST_RESULT_OK;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;

import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Pattern;

import com.sggdev.wcsdk.*;

public class DeviceChatActivity extends Activity {

    private final static int MAX_MSGS_TO_SEND_CHUNCK = 10;
    private final static int MAX_MEDIA_LOADER_JOBS = 3;

    private String mDeviceHostName;
    private long mUserDeviceId;

    private DeviceIconView mDeviceIcon;
    private LinearLayout mPlayStream;
    private RecyclerView mMessageRecycler;
    private MessageListAdapter mMessageAdapter;
    private List<WCChat.ChatMessage> mMessageList;
    private EditText mMsgToSend;
    private TableLayout mOutParamsTable;
    private ImageButton mScrollDown;

    private LinearLayoutManager llm;

    private DeviceItem mTargetDevice;
    private DeviceItem mUserDevice;

    private Timer syntimer;
    private SynchroTask syntask;
    private IdleTask idletask;
    private RollTask rolltask;

    private final BlockingQueue<WCChat.ChatMedia> mediaBlockingQueue = new LinkedBlockingQueue<>();
    private final HashMap<Long, WCChat.ChatMedia> mMediaTable = new LinkedHashMap<>();
    private final WCRollingRIDCache mMediaCache = new WCRollingRIDCache();
    private static final Object SyncMediaTaskObj = new Object();
    private int mediaLoaderTasks = 0;

    private final Pattern pattern = Pattern.compile("-?\\d+(\\.\\d+)?");

    private boolean isNumeric(String strNum) {
        if (strNum == null) {
            return false;
        }
        return pattern.matcher(strNum).matches();
    }

    private boolean tryParseToNumberField(String fldName, String strNum, JSONObject save_to) {
        Number v = isNumber(strNum);
        if (v == null) return false;
        try {
            if (v instanceof Integer) {
                save_to.put(fldName, v.intValue());
                return true;
            } else
            if (v instanceof Long) {
                save_to.put(fldName, v.longValue());
                return true;
            } else
            if (v instanceof Double) {
                save_to.put(fldName, v.doubleValue());
                return true;
            } else
                return false;
        } catch (JSONException e) {
            return false;
        }
    }

    private boolean tryParseToBooleanField(String fldName, String strBool, JSONObject save_to) {
        int v = isBoolean(strBool);
        try {
            switch (v) {
                case 0 : return false;
                case 1 : {
                    save_to.put(fldName, false);
                    return true;
                }
                case 2 : {
                    save_to.put(fldName, true);
                    return true;
                }
            }
        } catch (JSONException e) {
            return false;
        }
        return false;
    }

    private Number isNumber(String strNum) {
        String v = strNum.trim().replace(',', '.');
        if (isNumeric(v)) {
            int loc = 0;

            while (loc < 3) {
                try {
                    switch (loc) {
                        case 0: {
                            return Integer.parseInt(v);
                        }
                        case 1: {
                            return Long.parseLong(v);
                        }
                        case 2: {
                            return Double.parseDouble(v);
                        }
                    }
                } catch (NumberFormatException nfe) {
                    //ignore
                }
                loc++;
            }
        }
        return null;
    }

    private int isBoolean(String aValue) {
        String v = aValue.trim();
        if (Boolean.toString(true).equals(v)) {
            return 2;
        } else
        if (Boolean.toString(false).equals(v)) {
            return 1;
        } else {
            return 0;
        }
    }

    private int getTextColorByValue(String aValue) {
        Number v = isNumber(aValue);
        int aColorRID;
        if (v != null) {
            if (v instanceof Integer || v instanceof Long) {
                aColorRID = R.color.colorValueInt;
            } else {
                aColorRID = R.color.colorValueFloat;
            }
        } else
        if (isBoolean(aValue) > 0) {
            aColorRID = R.color.colorValueBool;
        } else {
            aColorRID = R.color.colorValueString;
        }
        return aColorRID;
    }

    private void scrollDown() {
        if (mMessageList.size() > 0)
            mMessageRecycler.smoothScrollToPosition(mMessageList.size() - 1);
        mScrollDown.setVisibility(View.GONE);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.activity_device_chat);

        WCApp myApp = (WCApp) getApplication();

        final Intent intent = getIntent();
        mDeviceHostName = "";
        mUserDeviceId = intent.getLongExtra(EXTRAS_USER_DEVICE_ID, -1);
        long mTargetDeviceId = intent.getLongExtra(EXTRAS_TARGET_DEVICE_ID, -1);
        String mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_BLE_NAME);
        mDeviceHostName  = intent.getStringExtra(EXTRAS_DEVICE_HOST_NAME);
        String mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);
        String mDeviceWriteChar = intent.getStringExtra(EXTRAS_DEVICE_WRITE_ID);
        int mDeviceColor = intent.getIntExtra(EXTRAS_DEVICE_COLOR, DEFAULT_DEVICE_COLOR);
        int mDeviceIndex = intent.getIntExtra(EXTRAS_DEVICE_INDEX, 0);

        if (mTargetDeviceId > 0) {
            mTargetDevice = myApp.getDevicesHolderList().findItem(mTargetDeviceId);
        } else {
            DeviceItem it = new DeviceItem(myApp, myApp.getHttpCfgFullUserName());
            it.complete(mDeviceHostName, mDeviceName, mDeviceAddress);
            mTargetDevice = myApp.getDevicesHolderList().findItem(it);
            if (mTargetDevice == null) mTargetDevice = it;
        }
        mUserDevice = myApp.getDevicesHolderList().findItem(mUserDeviceId);

        mMessageList = new ArrayList<>();

        mMessageRecycler = findViewById(R.id.recycler_gchat);
        mMessageAdapter = new MessageListAdapter(mMessageList);
        mMessageRecycler.setLayoutManager(new LinearLayoutManager(this));
        mMessageRecycler.setAdapter(mMessageAdapter);
        mMessageRecycler.addOnLayoutChangeListener(
                (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if ( bottom < oldBottom) {
                mMessageRecycler.postDelayed(() -> {
                            if (mMessageList.size() > 0)
                                mMessageRecycler.smoothScrollToPosition(mMessageList.size() - 1);
                        },
                        100);
            }
        });
        llm = (LinearLayoutManager) mMessageRecycler.getLayoutManager();

        mDeviceIcon = findViewById(R.id.device_icon_view);
        mDeviceIcon.setDeviceConfig(mDeviceColor, mDeviceIndex, mDeviceWriteChar);

        mMsgToSend = findViewById(R.id.edit_gchat_message);

        mOutParamsTable = findViewById(R.id.table_gchat_send);
        mOutParamsTable.setColumnStretchable(1, true);

        mPlayStream = findViewById(R.id.device_online);

        final TextView sDate = findViewById(R.id.stamp_cur_date);

        mScrollDown = findViewById(R.id.stamp_scroll_down);
        mScrollDown.setOnClickListener(view -> {
            scrollDown();
        });

        mMessageRecycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);

                //boolean hasStarted = newState == SCROLL_STATE_DRAGGING;
                boolean hasEnded = newState == SCROLL_STATE_IDLE;

                if (hasEnded) {
                    sDate.animate()
                            .alpha(0.0f)
                            .setDuration(1000)
                            .setListener(new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationEnd(Animator animation) {
                                    super.onAnimationEnd(animation);
                                    sDate.setVisibility(View.GONE);
                                }
                            });
                }

                if (hasEnded) {
                    mScrollDown.clearAnimation();
                    if (llm != null) {
                        int visiblePosition = llm.findLastCompletelyVisibleItemPosition();
                        if (visiblePosition < (mMessageList.size() - 1)) {
                            mScrollDown.setVisibility(View.VISIBLE);
                            mScrollDown.animate()
                                    .alpha(1.0f)
                                    .setDuration(300)
                                    .setListener(new AnimatorListenerAdapter() {
                                        @Override
                                        public void onAnimationEnd(Animator animation) {
                                            super.onAnimationEnd(animation);
                                            mScrollDown.setAlpha(1.0f);
                                        }
                                    });
                        } else {
                            mScrollDown.animate()
                                    .alpha(0.0f)
                                    .setDuration(300)
                                    .setListener(new AnimatorListenerAdapter() {
                                        @Override
                                        public void onAnimationEnd(Animator animation) {
                                            super.onAnimationEnd(animation);
                                            mScrollDown.setVisibility(View.GONE);
                                        }
                                    });
                        }
                    }
                }
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                sDate.clearAnimation();
                sDate.setVisibility(View.VISIBLE);
                sDate.setAlpha(1.0f);

                if (llm != null) {
                    int visiblePosition = llm.findFirstVisibleItemPosition();
                    if (visiblePosition >= 0) {
                        WCChat.ChatMessage message = mMessageList.get(visiblePosition);
                        sDate.setText(message.getDate());
                    }
                }
            }
        });

        Button send = findViewById(R.id.button_gchat_send);
        send.setOnClickListener(view -> {
            String msgToSend = mMsgToSend.getText().toString();

            JSONObject jsonParams = new JSONObject();

            for (int i = 0; i < mOutParamsTable.getChildCount(); i++) {
                final View row = mOutParamsTable.getChildAt(i);
                if (row instanceof TableRow) {
                    TextView label = (TextView) ((TableRow)row).getChildAt(0);
                    TextView value = (TextView) ((TableRow)row).getChildAt(1);

                    String fldLabel = label.getText().toString().trim();
                    String fldValue = value.getText().toString();

                    if ((fldLabel.length() > 0) && (fldValue.length() > 0)) {
                        if (tryParseToNumberField(fldLabel, fldValue, jsonParams)) {
                            continue;
                        } else
                        if (tryParseToBooleanField(fldLabel, fldValue, jsonParams)) {
                            continue;
                        } else {
                            try {
                                jsonParams.put(fldLabel, fldValue);
                            } catch (JSONException e) {
                                //ignore
                            }
                        }
                    }
                }
            }


            if (msgToSend.length() > 0) {
                ChatDatabase db = ChatDatabase.getInstance(DeviceChatActivity.this);

                db.sendMsg(mUserDevice, mTargetDevice, mMsgToSend.getText().toString(), jsonParams, WCUtils.serverTimeStamp());

                mMsgToSend.setText("");

                updateMessageList();
            }
        });

        ImageButton add_param = findViewById(R.id.button_gchat_add_param);
        add_param.setOnClickListener(view -> {
            DisplayMetrics displayMetrics = getApplicationContext().getResources().getDisplayMetrics();
            int sz_x =  Math.round(48 * (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT));
            int sz_y =  Math.round(48 * (displayMetrics.ydpi / DisplayMetrics.DENSITY_DEFAULT));

            final TableRow tr = new TableRow(DeviceChatActivity.this);
            tr.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT,
                    TableRow.LayoutParams.WRAP_CONTENT));

            EditText fieldName = new EditText(new ContextThemeWrapper(DeviceChatActivity.this,R.style.table_field));
            fieldName.setText(String.format(Locale.getDefault(), "param_%d", mOutParamsTable.getChildCount()));
            fieldName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            fieldName.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, sz_y));
            fieldName.setCursorVisible(false);
            fieldName.setFocusableInTouchMode(false);
            fieldName.setInputType(InputType.TYPE_NULL);
            fieldName.setBackground(ContextCompat.getDrawable(getApplicationContext(),
                    R.drawable.table_border));
            tr.addView(fieldName);

            final EditText fieldValue = new EditText(new ContextThemeWrapper(DeviceChatActivity.this, R.style.table_value));
            fieldValue.setLayoutParams(new TableRow.LayoutParams(0, sz_y));
            fieldValue.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            fieldValue.setCursorVisible(false);
            fieldValue.setFocusableInTouchMode(false);
            fieldValue.setInputType(InputType.TYPE_NULL);
            fieldValue.setBackground(ContextCompat.getDrawable(getApplicationContext(),
                    R.drawable.table_border));
            tr.addView(fieldValue);

            ImageButton removeParam = new ImageButton(DeviceChatActivity.this);

            removeParam.setImageDrawable(ContextCompat.getDrawable(getApplicationContext(),
                    R.drawable.ic_baseline_delete_outline_24));
            removeParam.setBackgroundResource(R.drawable.round_button);
            removeParam.setLayoutParams(new TableRow.LayoutParams(sz_x, sz_y));
            removeParam.setOnClickListener(view13 -> runOnUiThread(() -> mOutParamsTable.removeView(tr)));
            tr.addView(removeParam);

            mOutParamsTable.addView(tr, new TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT,
                    TableLayout.LayoutParams.WRAP_CONTENT));

            View.OnFocusChangeListener focusCh = (view1, b) -> {
                if (!b) {
                    view1.setBackground(ContextCompat.getDrawable(getApplicationContext(),
                            R.drawable.table_border));
                    view1.setFocusableInTouchMode(false);
                    ((EditText) view1).setCursorVisible(false);
                    ((EditText) view1).setInputType(InputType.TYPE_NULL);
                }
            };

            View.OnFocusChangeListener focusCh2 = (view1, b) -> {
                if (!b) {
                    view1.setBackground(ContextCompat.getDrawable(getApplicationContext(),
                            R.drawable.table_border));

                    String strValue = ((EditText) view1).getText().toString();
                    int aColorRID = getTextColorByValue(strValue);
                    ((EditText) view1).setTextColor(ContextCompat.getColor(getApplicationContext(), aColorRID));
                    view1.setFocusableInTouchMode(false);
                    ((EditText) view1).setCursorVisible(false);
                    ((EditText) view1).setInputType(InputType.TYPE_NULL);
                }
            };

            View.OnClickListener click = view12 -> {
                if (((EditText) view12).getInputType() == InputType.TYPE_NULL) {
                    view12.setBackground(mMsgToSend.getBackground().getConstantState().newDrawable());
                    view12.setFocusable(true);
                    view12.setFocusableInTouchMode(true);
                    ((TextView) view12).setCursorVisible(true);
                    ((TextView) view12).setInputType(InputType.TYPE_CLASS_TEXT);
                    view12.requestFocus();
                }
            };
            fieldName.setOnFocusChangeListener(focusCh);
            fieldValue.setOnFocusChangeListener(focusCh2);
            fieldValue.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

                }

                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

                }

                @Override
                public void afterTextChanged(Editable editable) {
                    String strValue = editable.toString();
                    int aColorRID = getTextColorByValue(strValue);
                    fieldValue.setTextColor(ContextCompat.getColor(getApplicationContext(), aColorRID));
                }
            });
            fieldName.setOnClickListener(click);
            fieldValue.setOnClickListener(click);
        });

        mPlayStream.setVisibility(GONE);

        mPlayStream.setOnClickListener(view -> {
            final Intent data = new Intent(DeviceChatActivity.this,
                    StreamFullscreenActivity.class);

            data.putExtra(EXTRAS_DEVICE_HOST_NAME, mDeviceHostName);
            startActivity(data);
        });

        loadMessageList();
    }

    @Override
    protected void onResume() {
        super.onResume();

        restartSyncMode();
    }

    @Override
    protected void onPause() {
        super.onPause();

        stopTimer();
    }

    @Override
    protected void onStop() {
        //thread stop here
        stopTimer();
        super.onStop();
    }

    @Override
    public void onBackPressed() {
        Intent intent = new Intent();
        intent.putExtra(EXTRAS_DEVICE_HOST_NAME, mDeviceHostName);
        intent.putExtra(EXTRAS_DEVICE_LST_SYNC, mTargetDevice.getLstSync());
        intent.putExtra(EXTRAS_DEVICE_COLOR, mDeviceIcon.getDeviceColor());
        intent.putExtra(EXTRAS_DEVICE_INDEX, mDeviceIcon.getDeviceIndex());

        setResult(RESULT_OK, intent);
        finish();
    }

    void restartIdleTimer() {
        rolltask = new RollTask(this);
        syntask  = new SynchroTask(this);
        idletask = new IdleTask(this);
        syntimer = new Timer();
    }

    private void restartSyncMode() {
        //This will be called after onCreate or your activity is resumed
        if (syntimer == null) {
            restartIdleTimer();
            syntimer.schedule(rolltask, 500, 500);
            syntimer.schedule(syntask, 500, 5000);
            syntimer.schedule(idletask, 10000, 10000);
        }
    }

    private void stopTimer() {
        if (syntask != null)
            syntask.cancel();
        if (idletask != null)
            idletask.cancel();
        if (rolltask != null)
            rolltask.cancel();
        if (syntimer != null) {
            syntimer.cancel();
            syntimer.purge();
        }

        syntimer = null;
        syntask = null;
        idletask = null;
        rolltask = null;
    }

    private static class SynchroTask extends TimerJob {

        SynchroTask(Activity activity) {
            super(activity);
            setOnJobExecute(() -> ((DeviceChatActivity) getActivity()).startUpdateAll());
            setOnFinishListener(() -> ((DeviceChatActivity) getActivity()).updateMessageList());
        }
    }

    private static class IdleTask extends TimerJob {

        IdleTask(Activity activity) {
            super(activity);
            setOnJobExecute(() -> ((DeviceChatActivity) getActivity()).doIdle());
        }
    }

    private static class RollTask extends TimerJob {

        RollTask(Activity activity) {
            super(activity);
            setOnJobExecute(() -> ((DeviceChatActivity) getActivity()).doRoll());
        }
    }

    private void startUpdateMsgs() {
        WCHTTPClient httpClient = WCHTTPClientHolder.getInstance(DeviceChatActivity.this);
        httpClient.recvMsgs(DeviceChatActivity.this, true);
    }

    private void startUpdateSnaps() {
        WCHTTPClient httpClient = WCHTTPClientHolder.getInstance(DeviceChatActivity.this);
        httpClient.recvSnaps(DeviceChatActivity.this);
    }

    private void startUpdateStatus() {
        WCHTTPClient httpClient = WCHTTPClientHolder.getInstance(DeviceChatActivity.this);
        httpClient.checkDeviceConnected(DeviceChatActivity.this, mDeviceHostName,
                new OnBooleanRequestFinished() {
                    @Override
                    public void onSuccess() {
                        updateConnectionState(R.drawable.connected);
                    }

                    @Override
                    public void onFail() {
                        updateConnectionState(R.drawable.disconnected);
                    }
                });
        httpClient.checkDeviceStreaming(DeviceChatActivity.this, mDeviceHostName,
                new OnBooleanRequestFinished() {
                    @Override
                    public void onSuccess() {
                        updateStreamingState(VISIBLE);
                    }

                    @Override
                    public void onFail() {
                        updateStreamingState(GONE);
                    }
                });
    }

    private void startSendUnsentMsgs() {
        WCHTTPClient httpClient = WCHTTPClientHolder.getInstance(DeviceChatActivity.this);
        httpClient.getServerTime(DeviceChatActivity.this, null);
        httpClient.sendMsgs(DeviceChatActivity.this,
                mUserDevice,
                MAX_MSGS_TO_SEND_CHUNCK,
                position -> DeviceChatActivity.this.runOnUiThread(() -> {
                    for (int i = mMessageList.size()-1; i >= 0; i--) {
                        if (mMessageList.get(i).getDbId() == position) {
                            mMessageList.get(i).setState(MSG_STATE_SENDED);
                            mMessageAdapter.notifyItemChanged(i);
                            return;
                        }
                    }
                }));
    }

    private void updateConnectionState(final int resourceId) {
        runOnUiThread(() -> mDeviceIcon.updateConnectionState(resourceId));
    }

    private void updateStreamingState(final int state) {
        runOnUiThread(() -> {
            mPlayStream.setVisibility(state);
        });
    }

    private void startUpdateAll() {
        startUpdateMsgs();
        startUpdateSnaps();
        startUpdateStatus();
        startSendUnsentMsgs();
        while (loadNextMedia());
    }

    private void doIdle() {
        WCHTTPResync.Builder sync = new WCHTTPResync.Builder(this, null);
        sync.addOnFinishListener(((WCApp) getApplication()).getOnSyncNotify())
                .excludeDevice(mTargetDevice.getDeviceServerName()).doResync();
    }

    private void doRoll() {
        int cnt = 0;
        for (WCRollingRID rid : mMediaCache) {
            if (rid.needToLoad()) {
                rid.load();
                cnt++;
                if (cnt > 1) break;
            }
        }
    }

    private void loadMessageList() {
        if ((mTargetDevice != null) && (mUserDevice != null)) {
            ChatDatabase db = ChatDatabase.getInstance(this);

            if (db.getAllMessages(mUserDevice, mTargetDevice, mMessageList)) {
                updateMediaContentFromTo(0, mMessageList.size());
                mMessageAdapter.notifyItemRangeInserted(0, mMessageList.size());
                mMessageRecycler.scrollToPosition(mMessageList.size() - 1);
            }
        }
    }

    private void updateMessageList() {
        if ((mTargetDevice != null) && (mUserDevice != null)) {
            ChatDatabase db = ChatDatabase.getInstance(this);
            int cnt1 = mMessageList.size();
            if (db.getNewMessages(mUserDevice, mTargetDevice, mMessageList)) {
                int cnt2 = mMessageList.size();
                updateMediaContentFromTo(cnt1, cnt2 - cnt1);
                mMessageAdapter.notifyItemRangeInserted(cnt1, cnt2 - cnt1);
                mMessageRecycler.scrollToPosition(mMessageList.size() - 1);
            }
        }
    }

    private void doCompleteMedia(WCChat.ChatMedia media) {
        media.setComplete();
        final long rid = media.getServerRID();
        runOnUiThread(() -> {
            for (int i = mMessageList.size() - 1; i >= 0; i--) {
                if (mMessageList.get(i).getRid() == rid)
                    mMessageAdapter.notifyItemChanged(i);
            }
        });
    }

    private void updateMediaContentFromTo(int start, int count) {
        final List<WCChat.ChatMedia> list = new ArrayList<>();
        List<Long> ridsToAdd = new ArrayList<>();

        for (int i = start; i < (start + count); i++) {
            if (mMessageList.get(i).hasMedia()) {
                final long rid = mMessageList.get(i).getRid();
                WCChat.ChatMedia media;
                synchronized (mMediaTable) {
                    media = mMediaTable.get(rid);
                }
                if (media != null) {
                    if (!media.isComplete()) {
                        if (media.isMediaExists(this))
                            media.setComplete();
                        else
                            list.add(media);
                    }
                } else
                    ridsToAdd.add(rid);
            }
        }
        updateMediaContentListed(list);

        if (ridsToAdd.size() > 0) {
            ChatDatabase db = ChatDatabase.getInstance(DeviceChatActivity.this);
            List<WCChat.ChatMedia> new_media = db.getMedia(ridsToAdd);

            if (new_media != null && new_media.size() > 0) {
                final List<WCChat.ChatMedia> nlist = new ArrayList<>();
                for (WCChat.ChatMedia media : new_media) {
                    synchronized (mMediaTable) {
                        mMediaTable.put(media.getServerRID(), media);
                    }
                    if (media.isMediaExists(this))
                        media.setComplete();
                    else
                        nlist.add(media);
                }
                updateMediaContentListed(nlist);
            }
        }
    }

    private void updateMediaContentListed(List<WCChat.ChatMedia> list) {
        new Thread(() -> {
            for (WCChat.ChatMedia media : list) {
                try {
                    if (media.startLoading()) {
                        mediaBlockingQueue.put(media);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            while (loadNextMedia());
        }).start();
    }

    private boolean loadNextMedia() {
        if (mediaBlockingQueue.size() == 0) return false;
        synchronized (SyncMediaTaskObj) {
            if (mediaLoaderTasks >= MAX_MEDIA_LOADER_JOBS) return false;
        }

        WCChat.ChatMedia media = null;
        try {
            media = mediaBlockingQueue.take();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (media != null) {

            if (media.isMediaExists(this)) {
                doCompleteMedia(media);
                return true;
            }

            synchronized (SyncMediaTaskObj) {
                mediaLoaderTasks++;
            }

            WCHTTPClient httpClient = WCHTTPClientHolder.getInstance(DeviceChatActivity.this);
            final WCChat.ChatMedia waiting_media = media;
            httpClient.getRecordData(DeviceChatActivity.this, waiting_media,
                    (resultCode, result) -> {
                        synchronized (SyncMediaTaskObj) {
                            mediaLoaderTasks--;
                        }
                        waiting_media.finishLoading();
                        if (resultCode == REST_RESULT_OK) {
                            if (waiting_media.saveMedia(this, result)) {
                                ChatDatabase db = ChatDatabase.getInstance(DeviceChatActivity.this);
                                db.updateMedia(waiting_media, MEDIA_LOC);

                                doCompleteMedia(waiting_media);
                            }
                        }
                        if (!waiting_media.isComplete()) {
                            try {
                                if (waiting_media.startLoading()) {
                                    mediaBlockingQueue.put(waiting_media);
                                }
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
            });
            return true;
        }
        return false;
    }

    private void populateMsgParams(WCChat.ChatMessage message, TableLayout paramsTable) {
        if (message.hasJSONParams()) {
            paramsTable.setVisibility(View.VISIBLE);
            paramsTable.removeAllViews();

            Iterator<String> keys = message.getJsonParams().keys();

            while (keys.hasNext()) {
                String key = keys.next();
                Object obj = message.getJsonParams().opt(key);

                if (obj != null) {
                    TableRow tr = new TableRow(DeviceChatActivity.this);
                    tr.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT));

                    TextView fieldName = new TextView(new ContextThemeWrapper(DeviceChatActivity.this, R.style.table_field));
                    fieldName.setText(key);
                    fieldName.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT));

                    tr.addView(fieldName);
                    TextView fieldValue;
                    int aStyle;
                    if (obj instanceof String)
                        aStyle = R.style.table_string_field;
                    else if (obj instanceof Integer || obj instanceof Long)
                        aStyle = R.style.table_int_field;
                    else if (obj instanceof Number)
                        aStyle = R.style.table_float_field;
                    else if (obj instanceof Boolean)
                        aStyle = R.style.table_bool_field;
                    else
                        aStyle = R.style.table_value;

                    fieldValue = new TextView(new ContextThemeWrapper(DeviceChatActivity.this, aStyle));
                    fieldValue.setText(obj.toString());
                    fieldValue.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT));
                    tr.addView(fieldValue);
                    paramsTable.addView(tr, new TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, TableLayout.LayoutParams.WRAP_CONTENT));
                }
            }
        } else
            paramsTable.setVisibility(View.GONE);
    }

    public class MessageListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int VIEW_TYPE_MESSAGE_SENT = 1;
        private static final int VIEW_TYPE_MESSAGE_RECEIVED = 2;
        private static final int VIEW_TYPE_MEDIA_RECEIVED = 3;

        private final List<WCChat.ChatMessage> mMessageList;

        public MessageListAdapter(List<WCChat.ChatMessage> messageList) {
            mMessageList = messageList;
        }

        @Override
        public int getItemCount() {
            return mMessageList.size();
        }

        // Determines the appropriate ViewType according to the sender of the message.
        @Override
        public int getItemViewType(int position) {
            WCChat.ChatMessage message = mMessageList.get(position);

            if (message.getSender().getDbId() == mUserDeviceId) {
                // If the current user is the sender of the message
                return VIEW_TYPE_MESSAGE_SENT;
            } else {
                // If some other user sent the message
                if (message.hasMedia()) {
                    return VIEW_TYPE_MEDIA_RECEIVED;
                } else {
                    return VIEW_TYPE_MESSAGE_RECEIVED;
                }
            }
        }

        // Inflates the appropriate layout according to the ViewType.
        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;

            switch (viewType)
            {
                case VIEW_TYPE_MESSAGE_SENT:
                {
                    view = LayoutInflater.from(parent.getContext())
                            .inflate(R.layout.item_chat_outgoing, parent, false);
                    return new SentMessageHolder(view);
                }

                case VIEW_TYPE_MESSAGE_RECEIVED:
                {
                    view = LayoutInflater.from(parent.getContext())
                            .inflate(R.layout.item_chat_incoming, parent, false);
                    return new ReceivedMessageHolder(view);
                }

                case VIEW_TYPE_MEDIA_RECEIVED:
                {
                    view = LayoutInflater.from(parent.getContext())
                            .inflate(R.layout.item_chat_media_incoming, parent, false);
                    return new ReceivedMediaHolder(view);
                }

                default:
                    return null;
            }
        }

        // Passes the message object to a ViewHolder so that the contents can be bound to UI.
        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            WCChat.ChatMessage message = mMessageList.get(position);

            boolean showDate = (position == 0);
            if (!showDate) {
                WCChat.ChatMessage prev_message = mMessageList.get(position-1);
                showDate = !(prev_message.getDate().equals(message.getDate()));
            }

            switch (holder.getItemViewType()) {
                case VIEW_TYPE_MESSAGE_SENT:
                    ((SentMessageHolder) holder).bind(message, showDate);
                    break;
                case VIEW_TYPE_MESSAGE_RECEIVED:
                    ((ReceivedMessageHolder) holder).bind(message, showDate);
                    break;
                case VIEW_TYPE_MEDIA_RECEIVED:
                    ((ReceivedMediaHolder) holder).bind(message, showDate);
                    break;
            }
        }

        private class SentMessageHolder extends RecyclerView.ViewHolder {
            TextView messageText, timeText, dateText;
            ImageView messageState;
            TableLayout paramsTable;

            SentMessageHolder(View itemView) {
                super(itemView);

                messageText = itemView.findViewById(R.id.text_gchat_message);
                timeText = itemView.findViewById(R.id.text_gchat_timestamp);
                dateText = itemView.findViewById(R.id.text_gchat_date);
                paramsTable = itemView.findViewById(R.id.table_gchat_message);
                messageState = itemView.findViewById(R.id.text_gchat_send_label);
            }

            void bind(WCChat.ChatMessage message, boolean showDate) {
                if (showDate) {
                    dateText.setText(message.getDate());
                    dateText.setVisibility(View.VISIBLE);
                } else {
                    dateText.setVisibility(View.GONE);
                }

                messageText.setText(message.getMessage());

                populateMsgParams(message, paramsTable);

                timeText.setText(message.getTime());

                int imgId = R.drawable.ic_ok_sym;

                if (message.getState() ==  MSG_STATE_READY_TO_SEND)
                    imgId = R.drawable.ic_wait_sym;

                messageState.setImageDrawable(ContextCompat.getDrawable(DeviceChatActivity.this, imgId));
            }
        }

        private class ReceivedMessageHolder extends RecyclerView.ViewHolder {
            TextView messageText, timeText, dateText;
            TableLayout paramsTable;

            ReceivedMessageHolder(View itemView) {
                super(itemView);

                messageText = itemView.findViewById(R.id.text_gchat_message);
                timeText = itemView.findViewById(R.id.text_gchat_timestamp);
                dateText = itemView.findViewById(R.id.text_gchat_date);
                paramsTable = itemView.findViewById(R.id.table_gchat_message);
            }

            void bind(WCChat.ChatMessage message, boolean showDate) {

                if (showDate) {
                    dateText.setText(message.getDate());
                    dateText.setVisibility(View.VISIBLE);
                } else {
                    dateText.setVisibility(View.GONE);
                }

                messageText.setText(message.getMessage());

                populateMsgParams(message, paramsTable);

                timeText.setText(message.getTime());
            }
        }

        private class ReceivedMediaHolder extends RecyclerView.ViewHolder {
            TextView messageText, timeText, dateText;
            ImageView recImage;
            TableLayout paramsTable;

            ReceivedMediaHolder(View itemView) {
                super(itemView);

                recImage = itemView.findViewById(R.id.image_gchat_message);
                messageText = itemView.findViewById(R.id.text_gchat_message);
                timeText = itemView.findViewById(R.id.text_gchat_timestamp);
                dateText = itemView.findViewById(R.id.text_gchat_date);
                paramsTable = itemView.findViewById(R.id.table_gchat_message);
            }

            class OnMediaLoaded implements WCRollingRID.OnLoadingFinished {

                @Override
                public void finished(WCRollingRID obj) {
                    if (obj.needRefreshPreview()) {
                        synchronized (mMediaTable) {
                           WCChat.ChatMedia media = mMediaTable.get(obj.rid());
                           if (media != null) {
                               media.setPreview(obj.getPreview());
                           }
                        }
                    }
                    DeviceChatActivity.this.runOnUiThread(() -> {
                        if (llm != null) {
                            int firstVisiblePosition = llm.findFirstVisibleItemPosition() - 16;
                            int lastVisiblePosition = llm.findLastVisibleItemPosition() + 16;
                            if (firstVisiblePosition < 0) firstVisiblePosition = 0;
                            if (lastVisiblePosition >= mMessageList.size()) lastVisiblePosition = mMessageList.size()-1;
                            for (int i = firstVisiblePosition; i <= lastVisiblePosition; i++) {
                                if (mMessageList.get(i).getRid() == obj.rid())
                                    mMessageAdapter.notifyItemChanged(i);
                            }
                        }
                    });
                }
            }

            void bind(WCChat.ChatMessage message, boolean showDate) {

                if (showDate) {
                    dateText.setText(message.getDate());
                    dateText.setVisibility(View.VISIBLE);
                } else {
                    dateText.setVisibility(View.GONE);
                }

                WCChat.ChatMedia media;
                synchronized (mMediaTable) {
                    media = mMediaTable.get(message.getRid());
                }

                boolean imgEmpty = true;

                if (media != null && media.isComplete()) {
                    long rid = media.getServerRID();
                    WCRollingRID rollingRID = mMediaCache.findByRID(rid);
                    if (rollingRID != null) {
                        if (rollingRID.isLoaded()) {
                            recImage.setImageDrawable(rollingRID.getBitmap());
                            recImage.setClipToOutline(true);
                            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) recImage.getLayoutParams();
                            params.width = rollingRID.getWidth();
                            params.height = rollingRID.getHeight();
                            recImage.setLayoutParams(params);
                            imgEmpty = false;
                        }
                    } else {
                        String loadFrom = media.getLocation();
                        WCRollingRID rollid = new WCRollingRID(DeviceChatActivity.this,
                                rid, loadFrom, !media.hasPreview());
                        rollid.setFinishedListener(new OnMediaLoaded());
                        try {
                            mMediaCache.putFirst(rollid);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    final String imgLoc = media.getLocation();
                    recImage.setOnClickListener(view -> {
                        final Intent data = new Intent(DeviceChatActivity.this,
                                ImageViewFullscreenActivity.class);

                        data.putExtra(EXTRAS_IMAGE_BITMAP, imgLoc);
                        startActivity(data);
                    });
                }

                if (imgEmpty) {
                    Drawable drawing = null;
                    int width = 2;
                    int height = 2;
                    if ((media != null) && media.hasPreview()) {
                        WCPicPreviewHelper previewHelper = new WCPicPreviewHelper(media.getPreview());
                        if (previewHelper.load()) {
                            drawing = new BitmapDrawable(getApplicationContext().getResources(),
                                                            previewHelper.getBitmap());
                            height = previewHelper.getHeight();
                            width = previewHelper.getWidth();
                            float scale = WCPicPreviewHelper.getDisplayScale(DeviceChatActivity.this,
                                    width, height);
                            height *= scale;
                            width *= scale;
                        }
                    }
                    if (drawing == null) {
                        drawing = ContextCompat.getDrawable(getApplicationContext(),
                                android.R.drawable.ic_menu_report_image);
                        DisplayMetrics displayMetrics = getApplicationContext().getResources().getDisplayMetrics();
                        width = Math.round(MIN_PIC_SIZE_DPI * (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT));
                        height = width;
                    }
                    recImage.setImageDrawable(drawing);
                    recImage.setClipToOutline(true);
                    LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) recImage.getLayoutParams();
                    params.width = width;
                    params.height = height;
                    recImage.setLayoutParams(params);
                    recImage.setOnClickListener(null);
                }

                String msg = message.getMessage();
                if (msg.length() > 0)
                    messageText.setText(msg);
                else
                    messageText.setVisibility(View.GONE);

                populateMsgParams(message, paramsTable);

                // Format the stored timestamp into a readable String using method.
                timeText.setText(message.getTime());
            }
        }
    }

}