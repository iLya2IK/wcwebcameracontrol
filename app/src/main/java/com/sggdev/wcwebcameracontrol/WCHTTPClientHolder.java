package com.sggdev.wcwebcameracontrol;

import android.content.Context;

public class WCHTTPClientHolder {
    private static WCHTTPClientHolder sInstance;

    private final WCHTTPClient wchttpClient;

    public static synchronized WCHTTPClient getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new WCHTTPClientHolder(context.getApplicationContext());
        }
        return sInstance.wchttpClient;
    }

    private WCHTTPClientHolder(Context context) {
        wchttpClient = new WCHTTPClient(context);
    }
}
