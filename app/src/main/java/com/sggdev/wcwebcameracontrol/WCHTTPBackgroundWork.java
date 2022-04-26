package com.sggdev.wcwebcameracontrol;

import static com.sggdev.wcwebcameracontrol.WCHTTPClient.CS_CONNECTED;
import static com.sggdev.wcwebcameracontrol.WCHTTPClient.CS_CONNECTING;
import static com.sggdev.wcwebcameracontrol.WCHTTPClient.CS_DISCONNECTED;
import static com.sggdev.wcwebcameracontrol.WCHTTPClient.CS_DISCONNECTED_BY_USER;
import static com.sggdev.wcwebcameracontrol.WCHTTPClient.CS_DISCONNECTED_RETRY_OVER;
import static com.sggdev.wcwebcameracontrol.WCHTTPClient.CS_USER_CFG_INCORRECT;

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

public class WCHTTPBackgroundWork extends Worker {
    private final static String TAG = WCHTTPBackgroundWork.class.getSimpleName() + "_periodic";
    private final static String WAKE_TAG = "wcwebcamapp:wcwakelocktag";

    public final static String LST_MESSAGE = "LST_MESSAGE";

    public WCHTTPBackgroundWork(Context appContext, WorkerParameters workerParams) {
        super(appContext, workerParams);
    }

    private String errorStr = "";

    @SuppressLint("DefaultLocale")
    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "WCHTTPService on doWork");

        WCApp myApp = (WCApp) getApplicationContext();

        final String aLstMsg = getInputData().getString(LST_MESSAGE);

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
                    errorStr = WCRESTProtocol.REST_RESPONSE_ERRORS[errCode];
                    Log.d(TAG, errorStr);
                }

                @Override
                public void onClientStateChanged(int newState) {}

                @Override
                public void onLoginError(int errCode, String errStr) {
                    Log.d(TAG, errStr);
                    errorStr = errStr;
                }
            };

            httpClient.addStateChangeListener(listnr);

            try {
                if (httpClient.state() == CS_CONNECTED) {
                    Log.d(TAG, "sending heart bit (ping)...");
                    httpClient.heartBitSynchro(null);
                }

                if (httpClient.state() == CS_DISCONNECTED) {
                    Log.d(TAG, "connecting...");
                    httpClient.startConnectSynchro(null);
                }

                if (httpClient.state() == CS_CONNECTED) {
                    Log.d(TAG, "sending requests...");
                    httpClient.recvMsgsSynchro(null, false);
                    httpClient.recvSnapsSynchro(null);
                }

                new WCHTTPResync.Builder(myApp, aLstMsg)
                        .addNotifyListener()
                        .setClientState(httpClient.state())
                        .setErrorMsg(errorStr)
                        .doResync();

            } finally {
                httpClient.removeStateChangeListener(listnr);
            }

        } finally {
            mWakeLock.release();
        }

        Log.d(TAG, "restarting sync");

        WCHTTPResync.launchWCHTTPBackgroundWork(getApplicationContext());

        Log.d(TAG, "WCHTTPService on destroy");

        return Result.success();
    }


}