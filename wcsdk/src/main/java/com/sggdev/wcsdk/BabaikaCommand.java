package com.sggdev.wcsdk;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

public class BabaikaCommand extends BabaikaItem {
    private static final String BLE_STATE_KEY = "key";
    private static final String BLE_STATE_COMMENT = "comment";
    private static final String BLE_STATE_COMMAND = "command";
    private static final String BLE_STATE_REPEATABLE = "repeatable";

    private String key;
    private String command;
    private String comment;
    private boolean repeatable;

    BabaikaCommand() {
        super();
        key= "";
        command="";
        comment="";
        repeatable=false;
    }

    BabaikaCommand(String aKey, String aCommand, String aComment,
                   String aPicture, boolean aRepeatable) {
        super(aPicture);
        key = aKey;
        command = aCommand;
        comment = aComment;
        repeatable = aRepeatable;
    }

    String saveState() {
        String str = super.saveState();
        try {
            JSONObject ble_item = new JSONObject(str);
            ble_item.put(BLE_STATE_KEY, key);
            ble_item.put(BLE_STATE_COMMAND, command);
            ble_item.put(BLE_STATE_COMMENT, comment);
            ble_item.put(BLE_STATE_REPEATABLE, repeatable);
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
            key =  ble_item.getString(BLE_STATE_KEY);
            command =  ble_item.getString(BLE_STATE_COMMAND);
            comment =  ble_item.getString(BLE_STATE_COMMENT);
            repeatable =  ble_item.getBoolean(BLE_STATE_REPEATABLE);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    String getCommand() {
        return command;
    }
    String getKey() {
        return key;
    }
    String getComment() {
        return comment;
    }
    @NonNull
    public String toString() {
        return key;
    }
}