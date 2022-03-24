package com.sggdev.wcwebcameracontrol;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;

public class BabaikaDeviceConfigNotif extends BabaikaNotification {
    BabaikaDeviceConfigNotif() {
        super();
    }

    BabaikaDeviceConfigNotif(String uuid) {
        super(uuid, "");
    }

    private String consumedStr = "";

    @Override
    void formatValue() {
        StringBuilder sb = new StringBuilder(consumedStr);
        for(int i = 0; i < value.length(); i++)
        {
            char c = value.charAt(i);
            if (c == '\t') {
                consumedStr = "";
                sb = new StringBuilder();
            } else {
                sb.append(c);
            }
        }
        consumedStr = sb.toString();

        JSONObject concatCfg = new JSONObject();
        sb = new StringBuilder();
        for(int i = 0; i < consumedStr.length(); i++)
        {
            char c = consumedStr.charAt(i);
            if (c == '\r' || c == '\n') {
                String new_record = sb.toString();
                if (new_record.length() > 0) {
                    try {
                        JSONObject o = new JSONObject(new_record);
                        Iterator<String> keys = o.keys();

                        while(keys.hasNext()) {
                            String key = keys.next();
                            if (o.get(key) instanceof String)
                                concatCfg.put(key, o.getString(key));
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
                sb = new StringBuilder();
            } else {
                sb.append(c);
            }
        }

        formatted_value =  concatCfg.toString();
    }
}
