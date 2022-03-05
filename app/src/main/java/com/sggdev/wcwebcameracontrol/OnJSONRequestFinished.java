package com.sggdev.wcwebcameracontrol;

import org.json.JSONObject;

public interface OnJSONRequestFinished {
    public void onChange(int resultCode, JSONObject resultMsg);
}
