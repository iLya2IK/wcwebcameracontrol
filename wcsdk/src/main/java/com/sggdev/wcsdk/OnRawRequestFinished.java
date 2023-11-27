package com.sggdev.wcsdk;

import org.json.JSONObject;

public interface OnRawRequestFinished {
        public void onChange(int resultCode, byte[] result);
}
