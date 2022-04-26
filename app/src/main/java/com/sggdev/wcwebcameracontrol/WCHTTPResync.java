package com.sggdev.wcwebcameracontrol;

import static com.sggdev.wcwebcameracontrol.ChatDatabase.MEDIA_LOC;
import static com.sggdev.wcwebcameracontrol.WCHTTPClient.CS_CONNECTED;
import static com.sggdev.wcwebcameracontrol.WCHTTPClient.CS_DISCONNECTED_BY_USER;
import static com.sggdev.wcwebcameracontrol.WCHTTPClient.CS_DISCONNECTED_RETRY_OVER;
import static com.sggdev.wcwebcameracontrol.WCHTTPClient.CS_USER_CFG_INCORRECT;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.REST_RESULT_OK;

import android.annotation.SuppressLint;
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
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.appcompat.view.ContextThemeWrapper;
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
    private static final String ACTION = "com.sggdev.wcwebcameracontrol.alarm";
    private static final String CHANNEL_ID = TAG;
    private final static int notificationId = 0x00ffda;
    private final static int notificationFailId = 0x00ffdb;

    private static final int CLEANUP_INTERVAL = 5;

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
        Data data = new Data.Builder().putString(WCHTTPBackgroundWork.LST_MESSAGE, mNotifySyncLock).build();

        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(WCHTTPBackgroundWork.class).setConstraints(
                new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build())
                .setInitialDelay(CLEANUP_INTERVAL, TimeUnit.MINUTES)
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
        return db.getLastMsgTimeStamp(((WCApp)context.getApplicationContext()).getHttpCfgUserName());
    }

    public interface OnSyncFinished {
        boolean onNewMessagesSummary(Context context, int totalAmount, List<WCChat.DeviceMsgsCnt> aList);
        void onNewMessages(Context context, List<WCChat.ChatMessage> aList, List<String> aDevices);
        void onError(Context context, int state, String aError);
    }

    public static OnSyncFinished cStandardNotifier = new OnSyncFinished() {
        @SuppressLint("UnspecifiedImmutableFlag")
        private PendingIntent createOnDismissedIntent(Context context, int aNotificationId) {
            Intent skipIntent = new Intent(context, WCNotificationDismissedReceiver.class);
            skipIntent.setAction(WCNotificationDismissedReceiver.WC_SKIP);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                return PendingIntent.getBroadcast(context, 0, skipIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            } else {
                return PendingIntent.getBroadcast(context, 0, skipIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT);
            }
        }

        private NotificationCompat.Builder initNotification(Context context, int notId, Intent intent) {
            PendingIntent skipPendingIntent = createOnDismissedIntent(context, notId);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID);

            Drawable myLogo = ContextCompat.getDrawable(context, R.mipmap.ic_launcher);

            if (myLogo != null) {
                Bitmap bitmap = Bitmap.createBitmap(myLogo.getIntrinsicWidth(),
                        myLogo.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                myLogo.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                Path clipPath = new Path();
                RectF rect = new RectF(0, 0, canvas.getWidth(), canvas.getHeight());
                float radius = ((float)canvas.getWidth() / 4.0f);
                clipPath.addRoundRect(rect, radius, radius, Path.Direction.CW);
                canvas.clipPath(clipPath);
                myLogo.draw(canvas);
                builder.setLargeIcon(bitmap);
            }
            builder.setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setDeleteIntent(skipPendingIntent)
                    .setDefaults(NotificationCompat.DEFAULT_ALL)
                    .setAutoCancel(true)
                    .addAction(R.drawable.ic_skip, context.getString(R.string.skip),
                            skipPendingIntent);
            if (intent != null) {
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP |
                        Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT);
                builder.setContentIntent(pendingIntent);
            }
            return builder;
        }

        private void startNotification(Context context, int notId, NotificationCompat.Builder builder) {
            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.notify(notId, builder.build());
        }

        @Override
        @SuppressLint("DefaultLocale")
        public boolean onNewMessagesSummary(Context context, int totalAmount,
                                            List<WCChat.DeviceMsgsCnt> aList) {
            List<WCChat.DeviceMsgsCnt> locList = new ArrayList<>();

            for (WCChat.DeviceMsgsCnt item : aList) {
                String stamp = item.getLstMsgTimeStamp();
                if (stamp.compareTo(mNotifySyncLock) <= 0) {
                    totalAmount -= item.getCnt();
                } else
                    locList.add(item);
            }
            if (totalAmount <= 0) return false;

            if (locList.size() == 1 && totalAmount <= 1) {
                return true;
            } else {
                String textContent;
                String textTitle;
                int devIcon;

                Intent intent = new Intent(context, MainActivity.class);

                if (locList.size() == 1) {
                    String devName = locList.get(0).getName();

                    DeviceItem dev =
                            ((WCApp)context.getApplicationContext()).mDeviceItems.findItem(devName);

                    if (dev != null)
                        devIcon = dev.getDevicePictureID();
                    else
                        devIcon = R.drawable.ic_default_device;

                    textTitle = context.getString(R.string.new_messages);
                    textContent = String.format(context.getString(R.string.messages_from),
                            locList.get(0).getCnt(), devName);

                    intent.putExtra(MainActivity.ACTION_LAUNCH_CHAT, devName);
                } else {
                    devIcon = R.drawable.ic_default_device;
                    textTitle = context.getString(R.string.new_messages);
                    textContent = String.format(context.getString(R.string.messages_from_devices),
                            totalAmount, locList.size());

                    intent.putExtra(MainActivity.ACTION_LAUNCH_CHAT, "");
                }

                Log.d(TAG, "do notification");
                Log.d(TAG, textContent);

                NotificationCompat.Builder builder = initNotification(context, notificationId, intent);
                builder.setSmallIcon(devIcon)
                        .setContentTitle(textTitle)
                        .setContentText(textContent);

                startNotification(context, notificationId, builder);

                return false;
            }
        }

        @Override
        @SuppressLint("DefaultLocale")
        public void onNewMessages(Context context, List<WCChat.ChatMessage> aList, List<String> aDevices) {
            if (aList.size() > 1) return;

            WCChat.ChatMedia media = null;
            WCChat.ChatMessage msg = aList.get(0);
            if (msg.hasMedia()) {
                Log.d(TAG, "there is media incoming");
                List<Integer> mediaId = new ArrayList<>();
                mediaId.add(msg.getRid());
                ChatDatabase db = ChatDatabase.getInstance(context);
                final List<WCChat.ChatMedia> new_media = db.getMedia(mediaId);

                if (new_media != null && new_media.size() > 0) {
                    media = new_media.get(0);
                    if (media.isMediaExists(context)) {
                        media.setComplete();
                    } else {
                        media.startLoading();
                        WCHTTPClient httpClient = WCHTTPClientHolder.getInstance(context);
                        final WCChat.ChatMedia waiting_media = media;
                        httpClient.getRecordDataSynchro(null, waiting_media,
                                (resultCode, result) -> {
                                    waiting_media.finishLoading();
                                    if (resultCode == REST_RESULT_OK) {
                                        if (waiting_media.saveMedia(context, result)) {
                                            ChatDatabase adb = ChatDatabase.getInstance(context);
                                            adb.updateMedia(waiting_media, MEDIA_LOC);

                                            waiting_media.setComplete();
                                        }
                                    }
                                });
                    }
                }
            }

            String textContent;
            int devIcon;

            String devName = aDevices.get(0);
            Intent intent = new Intent(context, MainActivity.class);
            intent.putExtra(MainActivity.ACTION_LAUNCH_CHAT, devName);
            NotificationCompat.Builder builder = initNotification(context, notificationId, intent);

            DeviceItem dev =
                    ((WCApp)context.getApplicationContext()).mDeviceItems.findItem(devName);

            if (dev != null)
                devIcon = dev.getDevicePictureID();
            else
                devIcon = R.drawable.ic_default_device;

            if (media != null && media.isComplete()) {
                textContent = context.getString(R.string.media_captured);
                Bitmap bitmap =
                        ((BitmapDrawable)Drawable.createFromPath(media.getLocation()))
                                .getBitmap();
                builder.setLargeIcon(bitmap)
                        .setStyle(new NotificationCompat.BigPictureStyle()
                                .bigPicture(bitmap)
                                .bigLargeIcon(null));
                builder.setContentText(textContent);
            } else {
                textContent = msg.getMessage();
                if (msg.hasJSONParams()) {
                    RemoteViews notificationLayoutExpanded = new RemoteViews(context.getPackageName(),
                            R.layout.incoming_notification);
                    notificationLayoutExpanded.setTextViewText(R.id.textTitle, devName);
                    notificationLayoutExpanded.setTextViewText(R.id.text, textContent);

                    if (msg.hasJSONParams()) {
                        Iterator<String> keys = msg.getJsonParams().keys();

                        while (keys.hasNext()) {
                            String key = keys.next();
                            Object obj = msg.getJsonParams().opt(key);

                            if (obj != null) {
                                RemoteViews newView = new RemoteViews(context.getPackageName(), R.layout.notification_param_key_layout);
                                newView.setTextViewText(R.id.text, key);
                                notificationLayoutExpanded.addView(R.id.table_params, newView);

                                newView = new RemoteViews(context.getPackageName(), R.layout.notification_param_layout);
                                newView.setTextViewText(R.id.text, obj.toString());
                                notificationLayoutExpanded.addView(R.id.table_params, newView);
                            }
                        }
                    }

                    // Apply the layouts to the notification
                    builder.setStyle(new NotificationCompat.DecoratedCustomViewStyle())
                            .setCustomBigContentView(notificationLayoutExpanded);
                } else {
                    builder.setStyle(new NotificationCompat.BigTextStyle().bigText(textContent));
                }
            }

            Log.d(TAG, "do notification");
            Log.d(TAG, textContent);

            builder.setSmallIcon(devIcon)
                   .setContentTitle(devName);

            startNotification(context, notificationId, builder);
        }

        @Override
        public void onError(Context context, int state, String aError) {
            Intent intent = new Intent(context, MainActivity.class);
            intent.putExtra(MainActivity.ACTION_LAUNCH_CONFIG, MainActivity.ACTION_LAUNCH_CONFIG);
            NotificationCompat.Builder builder = initNotification(context, notificationFailId, intent);
            builder.setSmallIcon(android.R.drawable.ic_dialog_alert);

            switch (state) {
                case CS_DISCONNECTED_RETRY_OVER:
                case CS_DISCONNECTED_BY_USER: {
                    //
                    builder.setContentTitle(context.getString(R.string.err_connect_to_server))
                            .setContentText(String.format(context.getString(R.string.err_connect_message),
                                    aError));

                    startNotification(context, notificationFailId, builder);

                    break;
                }

                case CS_USER_CFG_INCORRECT: {
                    //
                    builder.setContentTitle(context.getString(R.string.err_connect_to_server))
                            .setContentText(String.format(context.getString(R.string.err_wrong_config_message),
                                    aError));

                    startNotification(context, notificationFailId, builder);

                    break;
                }
            }
        }
    };

    public static class Builder {
        private final String mLastSync;
        private List<String> mExcludedDevices = null;
        private int mClientState = CS_CONNECTED;
        private final Context mContext;
        private String mErrorMsg = "";
        private final List<OnSyncFinished> mOnFinishListner = new ArrayList<>();

        Builder(Context context, String aLastSync) {
            mContext = context;
            mLastSync = aLastSync;
        }

        Builder setErrorMsg(String aErrorStr) {
            mErrorMsg = aErrorStr;
            return this;
        }

        Builder addOnFinishListener(OnSyncFinished aSyncFinished) {
            mOnFinishListner.add(aSyncFinished);
            return this;
        }

        Builder setClientState(int aClientState) {
            mClientState = aClientState;
            return this;
        }

        Builder setExcludeDevices(List<String> aDevices) {
            mExcludedDevices = aDevices;
            return this;
        }

        Builder excludeDevice(String aDevice) {
            if (mExcludedDevices == null) {
                mExcludedDevices = new ArrayList<>();
            }
            mExcludedDevices.add(aDevice);
            return  this;
        }

        Builder addNotifyListener() {
            return addOnFinishListener(cStandardNotifier);
        }

        public void doResync() {
            WCApp myApp = (WCApp) (mContext.getApplicationContext());
            switch (mClientState) {
                case CS_CONNECTED: {
                    Log.d(TAG, String.format("reading from db. last checkpoint '%s'.", mLastSync));

                    ChatDatabase db = ChatDatabase.getInstance(mContext);
                    final List<WCChat.DeviceMsgsCnt> aDevCntList = new ArrayList<>();
                    final List<WCChat.ChatMessage> aMessageList = new ArrayList<>();
                    final List<String> devices = new ArrayList<>();
                    int total = db.getNewMessagesCount(myApp.getHttpCfgUserName(),
                            mLastSync, aDevCntList);
                    if (total > 0) {
                        for (int id = aDevCntList.size() - 1; id >= 0; id--) {
                            String deviceName = aDevCntList.get(id).getName();
                            if (mExcludedDevices != null)
                                for (String dev : mExcludedDevices)
                                    if (dev.equals(deviceName)) {
                                        total -= aDevCntList.get(id).getCnt();
                                        aDevCntList.remove(id);
                                        break;
                                    }
                        }

                        if (aDevCntList.size() > 0) {
                            boolean messagesWaitToLoad = true;
                            for (OnSyncFinished sync : mOnFinishListner) {
                                if (sync.onNewMessagesSummary(myApp, total, aDevCntList)) {
                                    if (messagesWaitToLoad) {
                                        messagesWaitToLoad = false;
                                        if (db.checkNewMessages(myApp.getHttpCfgUserName(),
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

                                    if (devices.size() > 0 && aMessageList.size() > 0)
                                        sync.onNewMessages(myApp, aMessageList, devices);
                                }
                            }
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
