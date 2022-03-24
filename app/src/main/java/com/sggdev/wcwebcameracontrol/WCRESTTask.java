package com.sggdev.wcwebcameracontrol;

import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.JSON_CODE;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.JSON_OK;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.JSON_RESULT;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.REST_ERR_NETWORK;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.REST_ERR_UNSPECIFIED;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.REST_RESULT_OK;

import android.app.Activity;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class WCRESTTask extends Thread {

    public static final MediaType JSONMedia
            = MediaType.get("application/json; charset=utf-8");

    private final WCHTTPClient client;
    private final Activity mActivity;
    private OnJSONRequestFinished onfinish;
    private String errorStr;
    private String mURL;
    private String mMethod;
    private String mContent;

    WCRESTTask(WCHTTPClient aClient, Activity activity) {
        client = aClient;
        mActivity = activity;
    }

    void execute(String... params) {
        mURL = params[0];
        mMethod = params[1];
        mContent = params[2];
        start();
    }

    void setOnJSONResponseListener(OnJSONRequestFinished rf) {
        onfinish = rf;
    }

    protected void onPostExecute(String resp) {
        if (onfinish != null) {
            if (resp == null) {
                try {
                    JSONObject resObj = new JSONObject();
                    resObj.put(JSON_RESULT, errorStr);
                    onfinish.onChange(REST_ERR_NETWORK, resObj);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            } else {
                int code;
                try {
                    JSONObject jsonObj = new JSONObject(resp);
                    String res = jsonObj.optString(JSON_RESULT, "");
                    if (res.length() > 0) {
                        if (res.equals(JSON_OK)) {
                            code = REST_RESULT_OK;
                        } else {
                            code = jsonObj.optInt(JSON_CODE, REST_ERR_UNSPECIFIED);
                        }
                        onfinish.onChange(code, jsonObj);
                    } else {
                        onfinish.onChange(REST_ERR_UNSPECIFIED, null);
                    }
                } catch (JSONException e) {
                    errorStr = e.toString();
                    try {
                        JSONObject resObj = new JSONObject();
                        resObj.put(JSON_RESULT, errorStr);
                        onfinish.onChange(REST_ERR_NETWORK, resObj);
                    } catch (JSONException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }
    }

    @Override
    public void run() {
        String execResult = null;
        try {
            RequestBody body = RequestBody.create(mContent, JSONMedia);
            Request request = new Request.Builder()
                    .url(mURL + mMethod)
                    .post(body)
                    .build();
            try (Response response = client.getClient().newCall(request).execute()) {
                execResult = Objects.requireNonNull(response.body()).string();
            }
        } catch (Exception e) {
            errorStr = e.toString();
        }
        final String passValue = execResult;
        onPostExecute(passValue);
        mActivity.runOnUiThread (new Thread(() -> onPostExecute(passValue)));
    }
}