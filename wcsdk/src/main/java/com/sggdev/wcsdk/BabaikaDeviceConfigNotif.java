package com.sggdev.wcsdk;

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
    private final JSONObject mConfig = new JSONObject();

    private void workWithData(StringBuilder sb) {
        consumedStr = sb.toString();

        StringBuilder stringBuilder = new StringBuilder();
        for(int i = 0; i < consumedStr.length(); i++)
        {
            char c = consumedStr.charAt(i);
            if (c == '\r' || c == '\n') {
                String new_record = stringBuilder.toString();
                if (new_record.length() > 0) {
                    try {
                        JSONObject o = new JSONObject(new_record);
                        Iterator<String> keys = o.keys();

                        while(keys.hasNext()) {
                            String key = keys.next();
                            if (o.get(key) instanceof String)
                                mConfig.put(key, o.getString(key));
                        }
                    } catch (JSONException e) {
                        //e.printStackTrace();
                    }
                }
                stringBuilder = new StringBuilder();
            } else {
                stringBuilder.append(c);
            }
        }

        formatted_value =  mConfig.toString();
    }

    @Override
    void formatValue() {
        StringBuilder sb = new StringBuilder(consumedStr);
        for(int i = 0; i < value.length(); i++)
        {
            char c = value.charAt(i);
            if (c == '\t') {
                workWithData(sb);
                consumedStr = "";
                sb = new StringBuilder();
            } else {
                sb.append(c);
            }
        }

        workWithData(sb);
    }
}
