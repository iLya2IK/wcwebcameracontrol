package com.sggdev.wcwebcameracontrol;

import org.json.JSONException;
import org.json.JSONObject;

public class BabaikaNotiCommand extends BabaikaCommand {
    private static String BLE_STATE_NOTI_UUID = "noti_uuid";

    private BabaikaNotification noti;
    private OnValueChangedListener mOnValueChangedListener = null;

    BabaikaNotiCommand() {
        super();
        noti = null;
    }

    BabaikaNotiCommand(String aKey, String aCommand, String aComment, String aPicture, boolean aRepeatable) {
        super(aKey, aCommand, aComment, aPicture, aRepeatable);
        noti = null;
        mOnValueChangedListener = () -> setPicture(noti.getPicture());
    }

    void setOnValueChangedListener(OnValueChangedListener listener) {
        mOnValueChangedListener = listener;
        if (noti != null) {
            noti.setOnValueChangedListener(mOnValueChangedListener);
        }
    }

    BabaikaNotification getNoti() {
        return noti;
    }

    void setNotification(String aUUID, String aPicture) {
        noti = new BabaikaNotification(aUUID, aPicture);
        noti.setOnValueChangedListener(mOnValueChangedListener);
    }

    void setNotification(BabaikaNotification notification) {
        noti = notification;
        noti.setOnValueChangedListener(mOnValueChangedListener);
    }

    String saveState() {
        String str = super.saveState();
        if (noti != null) {
            try {
                JSONObject ble_item = new JSONObject(str);
                ble_item.put(BLE_STATE_NOTI_UUID, noti.getUUID());
                return ble_item.toString();
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return str;
    }

    void restoreState(String state) {
        super.restoreState(state);
        if (noti != null) {
            try {
                JSONObject ble_item = new JSONObject(state);
                noti.setUUID(ble_item.getString(BLE_STATE_NOTI_UUID));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }
}
