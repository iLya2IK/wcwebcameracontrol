package com.sggdev.wcwebcameracontrol;

import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.JSON_CODE;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.JSON_OK;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.JSON_RESULT;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.REST_ERR_NETWORK_UNK;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.REST_ERR_UNSPECIFIED;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.REST_RESULT_OK;

import android.app.Activity;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.internal.http2.ConnectionShutdownException;

public class WCRESTTask extends Thread {

    public static final MediaType JSONMedia
            = MediaType.get("application/json; charset=utf-8");

    public static final int WC_RESULT_JSON_OBJ = 0;
    public static final int WC_RESULT_RAW_DATA = 1;

    private final WCHTTPClient client;
    private final Activity mActivity;
    private OnJSONRequestFinished onJSONFinish = null;
    private OnRawRequestFinished onRawFinish = null;
    private String errorStr;
    private int errorCode = REST_ERR_NETWORK_UNK;
    private String mURL;
    private String mMethod;
    private String mContent;
    private final int mWaitingResult;

    WCRESTTask(WCHTTPClient aClient, Activity activity) {
        client = aClient;
        mActivity = activity;
        mWaitingResult = WC_RESULT_JSON_OBJ;
    }

    WCRESTTask(WCHTTPClient aClient, Activity activity, int resultKind) {
        client = aClient;
        mActivity = activity;
        mWaitingResult = resultKind;
    }

    void execute(String... params) {
        setParams(params);
        start();
    }

    void setParams(String... params) {
        mURL = params[0];
        mMethod = params[1];
        mContent = params[2];
    }

    private void workWithJSON(JSONObject jsonObj) {
        int code;
        String res = jsonObj.optString(JSON_RESULT, "");
        if (res.length() > 0) {
            if (res.equals(JSON_OK)) {
                code = REST_RESULT_OK;
            } else {
                code = jsonObj.optInt(JSON_CODE, REST_ERR_UNSPECIFIED);
            }
            onJSONFinish.onChange(code, jsonObj);
        } else {
            onJSONFinish.onChange(REST_ERR_UNSPECIFIED, null);
        }
    }

    void setOnJSONResponseListener(OnJSONRequestFinished rf) {
        onJSONFinish = rf;
    }
    void setOnRawResponseListener(OnRawRequestFinished rf) {
        onRawFinish = rf;
    }

    protected void onPostExecute(byte[] resp) {
        if (resp == null) {
            if (onJSONFinish != null) {
                try {
                    JSONObject resObj = new JSONObject();
                    resObj.put(JSON_RESULT, errorStr);
                    onJSONFinish.onChange(errorCode, resObj);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        } else {
            if (mWaitingResult == WC_RESULT_JSON_OBJ) {
                if (onJSONFinish != null) {
                    try {
                        JSONObject jsonObj = new JSONObject(new String(resp, StandardCharsets.UTF_8));
                        workWithJSON(jsonObj);
                    } catch (JSONException e) {
                        errorStr = e.toString();
                        try {
                            JSONObject resObj = new JSONObject();
                            resObj.put(JSON_RESULT, errorStr);
                            onJSONFinish.onChange(errorCode, resObj);
                        } catch (JSONException ex) {
                            ex.printStackTrace();
                        }
                    }
                }
            } else
            if (mWaitingResult == WC_RESULT_RAW_DATA) {
                boolean isRawData = true;
                if ((resp.length < 30) && (onJSONFinish != null)) {
                    JSONObject jsonObj = null;
                    try {
                        jsonObj = new JSONObject(Arrays.toString(resp));
                        workWithJSON(jsonObj);
                        isRawData = false;
                    } catch (JSONException e) {
                        isRawData = true;
                    }
                }
                if (isRawData && (onRawFinish != null))
                    onRawFinish.onChange(REST_RESULT_OK, resp);
            }
        }
    }

    @Override
    public void run() {
        byte[] execResult = null;
        try {
            RequestBody body = RequestBody.create(mContent, JSONMedia);
            Request request = new Request.Builder()
                    .url(mURL + mMethod)
                    .post(body)
                    .build();
            try (Response response = client.getClient().newCall(request).execute()) {
                execResult = Objects.requireNonNull(response.body()).bytes();
            }
        } catch (Exception e) {
            errorStr = e.toString();
            if (e instanceof SocketTimeoutException) errorCode = WCRESTProtocol.REST_ERR_NETWORK_TIMEOUT;
            else if (e instanceof UnknownHostException) errorCode = WCRESTProtocol.REST_ERR_NETWORK_HOST;
            else if (e instanceof ConnectionShutdownException) errorCode = WCRESTProtocol.REST_ERR_NETWORK_SHUT;
            else if (e instanceof IOException) errorCode = WCRESTProtocol.REST_ERR_NETWORK_IO;
            else if (e instanceof IllegalStateException) errorCode = WCRESTProtocol.REST_ERR_NETWORK_ILLEGAL;
            else errorCode = WCRESTProtocol.REST_ERR_NETWORK_UNK;
        }
        final byte[] passValue = execResult;
        if (mActivity != null)
            mActivity.runOnUiThread (new Thread(() -> onPostExecute(passValue)));
        else
            onPostExecute(passValue);
    }
}