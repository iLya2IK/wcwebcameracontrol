package com.sggdev.wcwebcameracontrol;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class WCNotificationDismissedReceiver extends BroadcastReceiver {
    public final static String WC_SKIP = "com.sggdev.wcwebcameracontrol.ACTION_SKIP";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(WC_SKIP)) {
            WCHTTPResync.restartWCHTTPBackgroundWork(context);
        }
    }
}