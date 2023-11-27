package com.sggdev.wcsdk;

import static com.sggdev.wcsdk.WCHTTPClient.CS_CONNECTED;
import static com.sggdev.wcsdk.WCHTTPClient.CS_CONNECTING;
import static com.sggdev.wcsdk.WCHTTPClient.CS_DISCONNECTED;
import static com.sggdev.wcsdk.WCHTTPClient.CS_DISCONNECTED_BY_USER;
import static com.sggdev.wcsdk.WCHTTPClient.CS_DISCONNECTED_RETRY_OVER;
import static com.sggdev.wcsdk.WCHTTPClient.CS_USER_CFG_INCORRECT;

import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.content.Context;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.ArrayList;
import java.util.List;

public class WCHTTPBackgroundWork extends androidx.work.Worker {
    private final static String TAG = WCHTTPBackgroundWork.class.getSimpleName() + "_periodic";
    private final static String WAKE_TAG = "wcwebcamapp:wcwakelocktag";

    public final static String LST_MESSAGE = "LST_MESSAGE";

    public WCHTTPBackgroundWork(Context appContext, androidx.work.WorkerParameters workerParams) {
        super(appContext, workerParams);
    }

    public static void doSync(WCAppCommon myApp, String aLstMsg) {
        final StringBuilder mErrStr = new StringBuilder();

        PowerManager pm = (PowerManager) myApp.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_TAG);
        mWakeLock.acquire(60000);
        try {
            WCHTTPClient httpClient = WCHTTPClientHolder.getInstance(myApp);
            WCHTTPClient.OnStateChangeListener listnr = new WCHTTPClient.OnStateChangeListener() {
                @Override
                public void onConnect(String sid) { Log.d(TAG,"connected"); }

                @Override
                public void onDisconnect(int errCode) {
                    mErrStr.append( " " );
                    mErrStr.append( WCRESTProtocol.REST_RESPONSE_ERRORS[errCode]);
                    Log.d(TAG, String.format("Client disconnected %s ", mErrStr.toString()));
                }

                @Override
                public void onClientStateChanged(int newState) {}

                @Override
                public void onLoginError(int errCode, String errStr) {
                    Log.d(TAG, errStr);
                    mErrStr.append( errStr );
                }
            };

            httpClient.addStateChangeListener(listnr);

            try {
                if (httpClient.state() == CS_CONNECTED) {
                    Log.d(TAG, "sending heart bit (ping)...");
                    httpClient.heartBitSynchro(null);
                }

                if ((httpClient.state() == CS_DISCONNECTED) ||
                        (httpClient.state() == CS_DISCONNECTED_BY_USER) ||
                        (httpClient.state() == CS_DISCONNECTED_RETRY_OVER)) {
                    Log.d(TAG, "connecting...");
                    httpClient.startConnectSynchro(null);
                }

                if (httpClient.state() == CS_CONNECTED) {
                    Log.d(TAG, "sending requests...");
                    myApp.doOnBackgroundSync(httpClient);
                }

                WCHTTPResync.Builder builder = new WCHTTPResync.Builder(myApp, aLstMsg)
                        .addOnFinishListener(myApp.getOnSyncNotify())
                        .setClientState(httpClient.state())
                        .setErrorMsg(mErrStr.toString());

                myApp.configBackroundResync(builder);

                builder.doResync();

            } finally {
                httpClient.removeStateChangeListener(listnr);
            }

        } finally {
            mWakeLock.release();
        }

        Log.d(TAG, "restarting sync");

        WCHTTPResync.launchWCHTTPBackgroundWork(myApp);

        Log.d(TAG, "WCHTTPService on destroy");
    }

    @SuppressLint("DefaultLocale")
    @NonNull
    @Override
    public androidx.work.ListenableWorker.Result doWork() {
        Log.d(TAG, "WCHTTPService on doWork");

        final WCAppCommon myApp = (WCAppCommon) getApplicationContext();
        final String aLstMsg = getInputData().getString(LST_MESSAGE);

        doSync(myApp, aLstMsg);

        return Result.success();
    }


}