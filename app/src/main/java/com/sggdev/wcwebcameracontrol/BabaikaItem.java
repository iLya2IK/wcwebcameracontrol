package com.sggdev.wcwebcameracontrol;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

public class BabaikaItem {
    private static final String BLE_STATE_DEVICE_PICTURE = "picture";

    private String picture;

    String saveState() {
        try {
            JSONObject ble_item = new JSONObject();
            ble_item.put(BLE_STATE_DEVICE_PICTURE, picture);
            return ble_item.toString();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return "";
    }

    void restoreState(String state) {
        try {
            JSONObject ble_item = new JSONObject(state);
            setPicture( ble_item.getString(BLE_STATE_DEVICE_PICTURE) );
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    BabaikaItem() {
        picture = "";
    }

    BabaikaItem(String aPicture) {
        picture = aPicture;
    }

    String getPicture() {return picture; }

    protected void setPicture(String avalue) {picture = avalue; }

    @NonNull
    public String toString() {
        return "";
    }
}
