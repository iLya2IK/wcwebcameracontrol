package com.sggdev.wcwebcameracontrol;

import static com.sggdev.wcsdk.ChatDatabase.MEDIA_LOC;
import static com.sggdev.wcsdk.WCHTTPClient.CS_DISCONNECTED_BY_USER;
import static com.sggdev.wcsdk.WCHTTPClient.CS_DISCONNECTED_RETRY_OVER;
import static com.sggdev.wcsdk.WCHTTPClient.CS_USER_CFG_INCORRECT;
import static com.sggdev.wcsdk.WCRESTProtocol.REST_RESULT_OK;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.ContextThemeWrapper;
import android.widget.RemoteViews;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.sggdev.wcsdk.ChatDatabase;
import com.sggdev.wcsdk.DeviceItem;
import com.sggdev.wcsdk.Log;
import com.sggdev.wcsdk.WCAppCommon;
import com.sggdev.wcsdk.WCChat;
import com.sggdev.wcsdk.WCHTTPClient;
import com.sggdev.wcsdk.WCHTTPClientHolder;
import com.sggdev.wcsdk.WCHTTPResync;
import com.sggdev.wcsdk.WCNotificationDismissedReceiver;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class WCApp extends WCAppCommon {

    private static final String TAG = "WCApp";

    public void alertWrongUser(Activity act,
                               String errorStr,
                               DialogInterface.OnClickListener onPositive) {
        String msg = getString(com.sggdev.wcsdk.R.string.alert_edit_config);
        if (errorStr != null)
            msg = msg.concat(getString(com.sggdev.wcsdk.R.string.details_prefix)).concat(errorStr);
        final String msgTxt = msg;
        act.runOnUiThread (new Thread(() -> new AlertDialog.Builder(
                new ContextThemeWrapper(act, com.sggdev.wcsdk.R.style.AlertDialogCustom))
                .setTitle(com.sggdev.wcsdk.R.string.alert_refused_connection)
                .setMessage(msgTxt)
                .setPositiveButton(R.string.yes, onPositive)
                .setNegativeButton(android.R.string.cancel, null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show()));
    }

    public static WCHTTPResync.OnSyncFinished cStandardNotifier = new WCHTTPResync.OnSyncFinished() {

        private PendingIntent createOnDismissedIntent(Context context, int aNotificationId) {
            Intent skipIntent = new Intent(context, WCNotificationDismissedReceiver.class);
            skipIntent.setAction(WCNotificationDismissedReceiver.WC_SKIP);
            skipIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            skipIntent.putExtra(WCHTTPResync.EXTRA_NOTIFICATION_ID, aNotificationId);
            return PendingIntent.getBroadcast(context, 0, skipIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        }

        private NotificationCompat.Builder initNotification(Context context, int notId, Intent intent) {
            PendingIntent skipPendingIntent = createOnDismissedIntent(context, notId);

            NotificationCompat.Builder builder = WCHTTPResync.genNotifBuilder(context);

            Drawable myLogo = ContextCompat.getDrawable(context, com.sggdev.wcsdk.R.mipmap.ic_launcher);

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
            NotificationCompat.Action action =
                    new NotificationCompat.Action.Builder(com.sggdev.wcsdk.R.drawable.ic_skip,
                            context.getString(com.sggdev.wcsdk.R.string.skip),
                                            skipPendingIntent)
                            .build();
            builder.setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setDeleteIntent(skipPendingIntent)
                    .setDefaults(NotificationCompat.DEFAULT_ALL)
                    .setAutoCancel(true)
                    .addAction(action);
            if (intent != null) {
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP |
                        Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
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
                                            String lstSync, List<WCChat.DeviceMsgsCnt> aList) {
            List<WCChat.DeviceMsgsCnt> locList = new ArrayList<>();

            if (lstSync == null ) return false;

            for (WCChat.DeviceMsgsCnt item : aList) {
                String stamp = item.getLstMsgTimeStamp();
                if ((stamp == null) || (stamp.compareTo(lstSync) <= 0)) {
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
                            ((WCApp)context.getApplicationContext()).
                                    getDevicesHolderList().
                                    findItem(((WCApp)context.getApplicationContext()).getHttpCfgFullUserName(),
                                            devName);

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

                NotificationCompat.Builder builder = initNotification(context, WCHTTPResync.notificationId, intent);
                builder.setSmallIcon(devIcon)
                        .setContentTitle(textTitle)
                        .setContentText(textContent);

                startNotification(context, WCHTTPResync.notificationId, builder);

                return false;
            }
        }

        @Override
        public void onNoMessages(Context context) {
            //
        }

        @Override
        @SuppressLint("DefaultLocale")
        public void onNewMessages(Context context, List<WCChat.ChatMessage> aList, List<String> aDevices) {

            if (aList.size() > 1) return;

            WCChat.ChatMedia media = null;
            WCChat.ChatMessage msg = aList.get(0);
            if (msg.hasMedia()) {
                Log.d(TAG, "there is media incoming");
                List<Long> mediaId = new ArrayList<>();
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
            NotificationCompat.Builder builder = initNotification(context, WCHTTPResync.notificationId, intent);

            DeviceItem dev =
                    ((WCApp)context.getApplicationContext()).
                            getDevicesHolderList().
                            findItem(((WCApp)context.getApplicationContext()).getHttpCfgFullUserName(),
                                    devName);

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

            startNotification(context, WCHTTPResync.notificationId, builder);

        }

        @Override
        public void onError(Context context, int state, String aError) {

            Intent intent = new Intent(context, MainActivity.class);
            intent.putExtra(MainActivity.ACTION_LAUNCH_CONFIG, MainActivity.ACTION_LAUNCH_CONFIG);
            NotificationCompat.Builder builder = initNotification(context, WCHTTPResync.notificationFailId, intent);
            builder.setSmallIcon(android.R.drawable.ic_dialog_alert);

            switch (state) {
                case CS_DISCONNECTED_RETRY_OVER:
                case CS_DISCONNECTED_BY_USER: {
                    //
                    builder.setContentTitle(context.getString(R.string.err_connect_to_server))
                            .setContentText(String.format(context.getString(R.string.err_connect_message),
                                    aError));

                    startNotification(context, WCHTTPResync.notificationFailId, builder);

                    break;
                }

                case CS_USER_CFG_INCORRECT: {
                    //
                    builder.setContentTitle(context.getString(R.string.err_connect_to_server))
                            .setContentText(String.format(context.getString(R.string.err_wrong_config_message),
                                    aError));

                    startNotification(context, WCHTTPResync.notificationFailId, builder);

                    break;
                }
            }

        }
    };

    public WCHTTPResync.OnSyncFinished getOnSyncNotify() {
        return cStandardNotifier;
    }
}
