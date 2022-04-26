package com.sggdev.wcwebcameracontrol;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;


public class WCBootBroadcastReceiver extends BroadcastReceiver {
    private final static String WC_BOOT_BROADCST = "android.intent.action.BOOT_COMPLETED";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(WC_BOOT_BROADCST)) {
            WCHTTPResync.restartWCHTTPBackgroundWork(context);
        }

    }
}