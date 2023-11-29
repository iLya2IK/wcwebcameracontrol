package com.sggdev.wcsdk;

import static com.sggdev.wcsdk.ChatDatabase.MEDIA_LOC;
import static com.sggdev.wcsdk.WCHTTPClient.CS_CONNECTED;
import static com.sggdev.wcsdk.WCHTTPClient.CS_DISCONNECTED_BY_USER;
import static com.sggdev.wcsdk.WCHTTPClient.CS_DISCONNECTED_RETRY_OVER;
import static com.sggdev.wcsdk.WCHTTPClient.CS_USER_CFG_INCORRECT;
import static com.sggdev.wcsdk.WCRESTProtocol.REST_RESULT_OK;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.VectorDrawable;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.os.Build;
import android.os.SystemClock;
import com.sggdev.wcsdk.Log;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.appcompat.view.ContextThemeWrapper;
import androidx.core.app.AlarmManagerCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class WCHTTPResync {
    private final static String TAG = WCHTTPResync.class.getSimpleName() + "_periodic";
    private static final String ACTION =  "com.sggdev.wcsdk.alarm";
    private static final String CHANNEL_ID = TAG;
    public final static int notificationId = 0x00ffda;
    public final static int notificationFailId = 0x00ffdb;

    private static String mNotifySyncLock = "";

    public static void init(Context context) {
        createNotificationChannel(context);
    }

    public static void restartWCHTTPBackgroundWork(Context context) {
        init(context);
        mNotifySyncLock = resyncServiceLastStamp(context);

        launchWCHTTPBackgroundWork(context);
    }

    public static void launchWCHTTPBackgroundWork(Context context) {
        Log.d(TAG, "launchWCHTTPBackgroundWork");

        Data data = new Data.Builder().putString(WCHTTPBackgroundWork.LST_MESSAGE, mNotifySyncLock).build();

        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(WCHTTPBackgroundWork.class).setConstraints(
                new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build())
                .setInitialDelay(((WCAppCommon)context.getApplicationContext()).getBackgroundWorkCleanUpTime(), TimeUnit.MINUTES)
                .addTag(ACTION)
                .setInputData(data)
                .build();

        WorkManager.getInstance(context).enqueueUniqueWork(ACTION, ExistingWorkPolicy.REPLACE,
                workRequest);
    }

    public static void stopWCHTTPBackgroundWork(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(ACTION);
    }

    private static void createNotificationChannel(Context context) {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = context.getString(R.string.channel_name);
            String description = context.getString(R.string.channel_description);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            channel.enableVibration(true);
            channel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), new AudioAttributes.Builder()
                   .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                   .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build());
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    public static String resyncServiceLastStamp(Context context) {
        ChatDatabase db = ChatDatabase.getInstance(context);
        return db.getLastMsgTimeStamp(
                ((WCAppCommon)context.getApplicationContext()).getHttpCfgFullUserName(),
                ((WCAppCommon)context.getApplicationContext()).getHttpCfgDevice());
    }

    public interface OnSyncFinished {
        boolean onNewMessagesSummary(Context context, int totalAmount, String lstSync, List<WCChat.DeviceMsgsCnt> aList);
        void onNewMessages(Context context, List<WCChat.ChatMessage> aList, List<String> aDevices);
        void onNoMessages(Context context);
        void onError(Context context, int state, String aError);
    }

    public static NotificationCompat.Builder genNotifBuilder(Context context) {
        return new NotificationCompat.Builder(context, CHANNEL_ID);
    }

    public static class Builder {
        private final String mLastSync;
        private List<String> mExcludedDevices = null;
        private List<String> mIncludedDevices = null;
        private int mClientState = CS_CONNECTED;
        private final Context mContext;
        private String mErrorMsg = "";
        private final List<OnSyncFinished> mOnFinishListner = new ArrayList<>();
        private final String ALL_DEVICES = "::all::";

        public Builder(Context context, String aLastSync) {
            mContext = context;
            mLastSync = aLastSync;
        }

        public Builder setErrorMsg(String aErrorStr) {
            mErrorMsg = aErrorStr;
            return this;
        }

        public Builder addOnFinishListener(OnSyncFinished aSyncFinished) {
            mOnFinishListner.add(aSyncFinished);
            return this;
        }

        public Builder setClientState(int aClientState) {
            mClientState = aClientState;
            return this;
        }

        public Builder setExcludeDevices(List<String> aDevices) {
            mExcludedDevices = aDevices;
            return this;
        }

        public Builder excludeDevice(String aDevice) {
            if (mExcludedDevices == null) {
                mExcludedDevices = new ArrayList<>();
            }
            mExcludedDevices.add(aDevice);
            return  this;
        }

        public Builder excludeAllDeviceExcept(String aDevice) {
            if (mIncludedDevices == null) {
                mIncludedDevices = new ArrayList<>();
            }
            if (mExcludedDevices == null) {
                mExcludedDevices = new ArrayList<>();
            }
            mExcludedDevices.add(ALL_DEVICES);
            mIncludedDevices.add(aDevice);
            return  this;
        }

        public void doResync() {
            WCAppCommon myApp = (WCAppCommon) (mContext.getApplicationContext());
            switch (mClientState) {
                case CS_CONNECTED: {
                    Log.d(TAG, String.format("reading from db. last checkpoint '%s'.", mLastSync));

                    ChatDatabase db = ChatDatabase.getInstance(mContext);
                    final List<WCChat.DeviceMsgsCnt> aDevCntList = new ArrayList<>();
                    final List<WCChat.ChatMessage> aMessageList = new ArrayList<>();
                    final List<String> devices = new ArrayList<>();
                    int total = db.getNewMessagesCount(
                            myApp.getHttpCfgFullUserName(),
                            myApp.getHttpCfgDevice(),
                            mLastSync, aDevCntList);
                    boolean onmfired = false;
                    if (total > 0) {
                        for (int id = aDevCntList.size() - 1; id >= 0; id--) {
                            String deviceName = aDevCntList.get(id).getName();
                            if (mExcludedDevices != null)
                                for (String dev : mExcludedDevices) {
                                    if (dev.equals(ALL_DEVICES) && (mIncludedDevices != null)) {
                                        boolean is_included = false;
                                        for (String devinc: mIncludedDevices)
                                            if (devinc.equals(deviceName)) {
                                                is_included = true;
                                                break;
                                            }
                                        if (!is_included) {
                                            total -= aDevCntList.get(id).getCnt();
                                            aDevCntList.remove(id);
                                            break;
                                        }
                                    } else
                                    if (dev.equals(deviceName)) {
                                        total -= aDevCntList.get(id).getCnt();
                                        aDevCntList.remove(id);
                                        break;
                                    }
                                }
                        }

                        if (aDevCntList.size() > 0) {
                            boolean messagesWaitToLoad = true;
                            for (OnSyncFinished sync : mOnFinishListner) {
                                if (sync.onNewMessagesSummary(myApp, total, mNotifySyncLock, aDevCntList)) {
                                    if (messagesWaitToLoad) {
                                        messagesWaitToLoad = false;
                                        if (db.checkNewMessages(myApp.getHttpCfgFullUserName(),
                                                myApp.getHttpCfgDevice(),
                                                mLastSync, aMessageList)) {
                                            for (int id = aMessageList.size() - 1; id >= 0; id--) {
                                                WCChat.ChatMessage msg = aMessageList.get(id);
                                                DeviceItem deviceItem = msg.getSender();
                                                String deviceName = deviceItem.getDeviceServerName();
                                                boolean not_found = true;
                                                for (String dev : devices) {
                                                    if (dev.equals(deviceName)) {
                                                        not_found = false;
                                                        break;
                                                    }
                                                }
                                                if (not_found && (mExcludedDevices != null))
                                                    for (String dev : mExcludedDevices) {
                                                        if (dev.equals(deviceName)) {
                                                            not_found = false;
                                                            aMessageList.remove(id);
                                                            break;
                                                        }
                                                    }
                                                if (not_found)
                                                    devices.add(deviceName);
                                            }
                                        }
                                    }

                                    if (devices.size() > 0 && aMessageList.size() > 0) {
                                        sync.onNewMessages(myApp, aMessageList, devices);
                                        onmfired = true;
                                    }
                                }
                            }
                        }
                    }

                    if (!onmfired) {
                        for (OnSyncFinished sync : mOnFinishListner) {
                            sync.onNoMessages(myApp);
                        }
                    }
                    break;
                }

                case CS_DISCONNECTED_RETRY_OVER:
                case CS_DISCONNECTED_BY_USER:
                case CS_USER_CFG_INCORRECT: {

                    for (OnSyncFinished sync : mOnFinishListner)
                        sync.onError(myApp, mClientState, mErrorMsg);

                    break;
                }
                default:
                    break;
            }
        }
    }
}
