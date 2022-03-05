package com.sggdev.wcwebcameracontrol;

import org.json.JSONException;
import org.json.JSONObject;

public class BabaikaNotification extends BabaikaItem {
    private static final String BLE_STATE_UUID = "uuid";

    private String uuid;
    protected String value;
    protected String formatted_value;
    private boolean connected;
    private boolean waiting;
    private OnValueChangedListener mOnValueChangedListener = null;

    String saveState() {
        String str = super.saveState();
        try {
            JSONObject ble_item = new JSONObject(str);
            ble_item.put(BLE_STATE_UUID, uuid);
            return ble_item.toString();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return str;
    }

    void restoreState(String state) {
        super.restoreState(state);
        try {
            JSONObject ble_item = new JSONObject(state);
            uuid =  ble_item.getString(BLE_STATE_UUID);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    BabaikaNotification() {
        super();
        uuid = "";
        value = "";
        formatted_value = "";
        connected = false;
        waiting = false;
    }

    BabaikaNotification(String aUUID, String aPicture) {
        super(aPicture);
        uuid = aUUID;
        value = "";
        formatted_value = "";
        connected = false;
        waiting = false;
    }

    void setOnValueChangedListener(OnValueChangedListener listener) {
        mOnValueChangedListener = listener;
    }

    String getUUID() {
        return uuid;
    }
    void setUUID(String aUUID) {
        uuid = aUUID;
    }

    void formatValue() { formatted_value = value; }

    String getValue() {
        return value;
    }

    boolean getConnected() {return  connected; }
    void setConnected(boolean avalue) {connected = avalue;}

    boolean getWaiting() {return  waiting; }
    void setWaiting(boolean avalue) {waiting = avalue;}

    void setValue(byte[] avalue) {
        value = new String(avalue);
        formatValue();
        if (mOnValueChangedListener != null) {
            mOnValueChangedListener.onChange();
        }
    }

    public String toString() {
        return formatted_value;
    }
}
