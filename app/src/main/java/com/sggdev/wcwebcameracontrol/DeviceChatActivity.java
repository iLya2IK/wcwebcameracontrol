package com.sggdev.wcwebcameracontrol;

import static com.sggdev.wcwebcameracontrol.DeviceItem.DEFAULT_DEVICE_COLOR;
import static com.sggdev.wcwebcameracontrol.IntentConsts.EXTRAS_DEVICE_ADDRESS;
import static com.sggdev.wcwebcameracontrol.IntentConsts.EXTRAS_DEVICE_BLE_NAME;
import static com.sggdev.wcwebcameracontrol.IntentConsts.EXTRAS_DEVICE_COLOR;
import static com.sggdev.wcwebcameracontrol.IntentConsts.EXTRAS_DEVICE_HOST_NAME;
import static com.sggdev.wcwebcameracontrol.IntentConsts.EXTRAS_DEVICE_INDEX;
import static com.sggdev.wcwebcameracontrol.IntentConsts.EXTRAS_DEVICE_WRITE_ID;
import static com.sggdev.wcwebcameracontrol.IntentConsts.EXTRAS_TARGET_DEVICE_ID;
import static com.sggdev.wcwebcameracontrol.IntentConsts.EXTRAS_USER_DEVICE_ID;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class DeviceChatActivity extends Activity {

    private String mDeviceName;
    private String mDeviceHostName;
    private String mDeviceAddress;
    private int mDeviceColor;
    private int mDeviceIndex;
    private String mDeviceWriteChar = "";
    private long mUserDeviceId;

    private RecyclerView mMessageRecycler;
    private MessageListAdapter mMessageAdapter;
    private List<WCChat.ChatMessage> mMessageList;

    private DeviceItem mTargetDevice;
    private DeviceItem mUserDevice;

    private WCApp myApp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.activity_device_chat);

        myApp = (WCApp) getApplication();

        mMessageList = new ArrayList<>();
        loadMessageList();

        final Intent intent = getIntent();
        mDeviceHostName = "";
        mUserDeviceId = intent.getLongExtra(EXTRAS_USER_DEVICE_ID, -1);
        long mTargetDeviceId = intent.getLongExtra(EXTRAS_TARGET_DEVICE_ID, -1);
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_BLE_NAME);
        mDeviceHostName  = intent.getStringExtra(EXTRAS_DEVICE_HOST_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);
        mDeviceWriteChar = intent.getStringExtra(EXTRAS_DEVICE_WRITE_ID);
        mDeviceColor = intent.getIntExtra(EXTRAS_DEVICE_COLOR, DEFAULT_DEVICE_COLOR);
        mDeviceIndex = intent.getIntExtra(EXTRAS_DEVICE_INDEX, 0);

        if (mTargetDeviceId > 0) {
            mTargetDevice = myApp.mDeviceItems.findItem(mTargetDeviceId);
        } else {
            DeviceItem it = new DeviceItem(myApp);
            it.complete(mDeviceHostName, mDeviceName, mDeviceAddress);
            mTargetDevice = myApp.mDeviceItems.findItem(it);
            if (mTargetDevice == null) {
                mTargetDevice = it;
            }
        }
        mUserDevice = myApp.mDeviceItems.findItem(mUserDeviceId);

        mMessageRecycler = (RecyclerView) findViewById(R.id.recycler_gchat);
        mMessageAdapter = new MessageListAdapter(this, mMessageList);
        mMessageRecycler.setLayoutManager(new LinearLayoutManager(this));
        mMessageRecycler.setAdapter(mMessageAdapter);
    }

    private void loadMessageList() {
        if ((mTargetDevice != null) && (mUserDevice != null)) {
            ChatDatabase db = ChatDatabase.getInstance(this);

            if (db.getAllMessages(mUserDevice, mTargetDevice, mMessageList)) {
                mMessageAdapter.notifyDataSetChanged();
            }
        }
    }

    private void updateMessageList() {
        if ((mTargetDevice != null) && (mUserDevice != null)) {
            ChatDatabase db = ChatDatabase.getInstance(this);
            if (db.getNewMessages(mUserDevice, mTargetDevice, mMessageList)) {
                mMessageAdapter.notifyDataSetChanged();
            }
        }
    }

    public class MessageListAdapter extends RecyclerView.Adapter {
        private static final int VIEW_TYPE_MESSAGE_SENT = 1;
        private static final int VIEW_TYPE_MESSAGE_RECEIVED = 2;

        private Context mContext;
        private List<WCChat.ChatMessage> mMessageList;

        public MessageListAdapter(Context context, List<WCChat.ChatMessage> messageList) {
            mContext = context;
            mMessageList = messageList;
        }

        @Override
        public int getItemCount() {
            return mMessageList.size();
        }

        // Determines the appropriate ViewType according to the sender of the message.
        @Override
        public int getItemViewType(int position) {
            WCChat.ChatMessage message = (WCChat.ChatMessage) mMessageList.get(position);

            if (message.getSender().getDbId() == mUserDeviceId) {
                // If the current user is the sender of the message
                return VIEW_TYPE_MESSAGE_SENT;
            } else {
                // If some other user sent the message
                return VIEW_TYPE_MESSAGE_RECEIVED;
            }
        }

        // Inflates the appropriate layout according to the ViewType.
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;

            if (viewType == VIEW_TYPE_MESSAGE_SENT) {
                view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_chat_outgoing, parent, false);
                return new SentMessageHolder(view);
            } else if (viewType == VIEW_TYPE_MESSAGE_RECEIVED) {
                view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_chat_incoming, parent, false);
                return new ReceivedMessageHolder(view);
            }

            return null;
        }

        // Passes the message object to a ViewHolder so that the contents can be bound to UI.
        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            WCChat.ChatMessage message = (WCChat.ChatMessage) mMessageList.get(position);

            switch (holder.getItemViewType()) {
                case VIEW_TYPE_MESSAGE_SENT:
                    ((SentMessageHolder) holder).bind(message);
                    break;
                case VIEW_TYPE_MESSAGE_RECEIVED:
                    ((ReceivedMessageHolder) holder).bind(message);
            }
        }

        private class SentMessageHolder extends RecyclerView.ViewHolder {
            TextView messageText, timeText;

            SentMessageHolder(View itemView) {
                super(itemView);

                messageText = (TextView) itemView.findViewById(R.id.text_gchat_message);
                timeText = (TextView) itemView.findViewById(R.id.text_gchat_timestamp);
            }

            void bind(WCChat.ChatMessage message) {
                messageText.setText(message.getMessage());

                // Format the stored timestamp into a readable String using method.
                timeText.setText(message.getCreatedAt());
            }
        }

        private class ReceivedMessageHolder extends RecyclerView.ViewHolder {
            TextView messageText, timeText, nameText;
            ImageView profileImage;

            ReceivedMessageHolder(View itemView) {
                super(itemView);

                messageText = (TextView) itemView.findViewById(R.id.text_gchat_message);
                timeText = (TextView) itemView.findViewById(R.id.text_gchat_timestamp);
                nameText = (TextView) itemView.findViewById(R.id.text_gchat_user_incom);
                profileImage = (ImageView) itemView.findViewById(R.id.image_gchat_profile_incom);
            }

            void bind(WCChat.ChatMessage message) {
                messageText.setText(message.getMessage());

                // Format the stored timestamp into a readable String using method.
                timeText.setText(message.getCreatedAt());
                nameText.setText(message.getSender().getDeviceServerName());

                // Insert the profile image from the URL into the ImageView.
                int aPicName = message.getSender().getDevicePictureID();
                if (aPicName > 0) {
                    profileImage.setImageDrawable(ContextCompat.getDrawable(DeviceChatActivity.this, aPicName));
                } else
                    profileImage.setImageDrawable(null);
            }
        }
    }
}