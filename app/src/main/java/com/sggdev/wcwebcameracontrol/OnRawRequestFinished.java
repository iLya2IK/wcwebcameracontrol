package com.sggdev.wcwebcameracontrol;

import org.json.JSONObject;

public interface OnRawRequestFinished {
        public void onChange(int resultCode, byte[] result);
}
