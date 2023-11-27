package com.sggdev.wcsdk;

import static com.sggdev.wcsdk.WCRESTProtocol.JSON_CODE;
import static com.sggdev.wcsdk.WCRESTProtocol.JSON_OK;
import static com.sggdev.wcsdk.WCRESTProtocol.JSON_RESULT;
import static com.sggdev.wcsdk.WCRESTProtocol.REST_ERR_UNSPECIFIED;
import static com.sggdev.wcsdk.WCRESTProtocol.REST_RESULT_OK;

import android.app.Activity;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class WCRESTTask extends WCTask {

    public static final MediaType JSONMedia
            = MediaType.get("application/json; charset=utf-8");

    public static final int WC_RESULT_JSON_OBJ = 0;
    public static final int WC_RESULT_RAW_DATA = 1;

    private final Activity mActivity;
    private OnJSONRequestFinished onJSONFinish = null;
    private OnRawRequestFinished onRawFinish = null;
    private String mMethod;
    private String mContent;
    private final int mWaitingResult;

    WCRESTTask(WCHTTPClient aClient, Activity activity) {
        super(aClient);
        mActivity = activity;
        mWaitingResult = WC_RESULT_JSON_OBJ;
    }

    WCRESTTask(WCHTTPClient aClient, Activity activity, int resultKind) {
        super(aClient);
        mActivity = activity;
        mWaitingResult = resultKind;
    }

    void setParams(String... params) {
        super.setParams(params);
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
                    resObj.put(JSON_RESULT, getErrorStr());
                    onJSONFinish.onChange(getErrorCode(), resObj);
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
                        setErrorStr(e.toString());
                        try {
                            JSONObject resObj = new JSONObject();
                            resObj.put(JSON_RESULT, getErrorStr());
                            onJSONFinish.onChange(getErrorCode(), resObj);
                        } catch (JSONException ex) {
                            ex.printStackTrace();
                        }
                    }
                }
            } else
            if (mWaitingResult == WC_RESULT_RAW_DATA) {
                boolean isRawData = true;
                if ((resp.length < 30) && (onJSONFinish != null)) {
                    JSONObject jsonObj;
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
    void internalExecute(boolean sync) {
        byte[] execResult = null;
        try {
            RequestBody body = RequestBody.create(mContent, JSONMedia);
            Request request = new Request.Builder()
                    .url(getURL() + mMethod)
                    .post(body)
                    .build();
            if (sync) {
                try (Response response = getClient().getClient().newCall(request).execute()) {
                    execResult = Objects.requireNonNull(response.body()).bytes();
                }
            } else {
                Call call =  getClient().getClient().newCall(request);
                call.enqueue(new Callback() {
                    private void finish(byte[] execInternalResult) {
                        final byte[] passValue = execInternalResult;
                        if (mActivity != null)
                            mActivity.runOnUiThread(new Thread(() -> onPostExecute(passValue)));
                        else
                            onPostExecute(passValue);
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        byte[] execInternalResult = Objects.requireNonNull(response.body()).bytes();
                        finish(execInternalResult);
                    }

                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        consumeException(e);
                        finish(null);
                    }
                });
            }
        } catch (Exception e) {
            if (e instanceof IOException) {
                consumeException((IOException)e);
            } else {
                setErrorStr(e.toString());
                if (e instanceof IllegalStateException) setErrorCode(WCRESTProtocol.REST_ERR_NETWORK_ILLEGAL);
                else setErrorCode(WCRESTProtocol.REST_ERR_NETWORK_UNK);
            }
        }
        if (sync) {
            final byte[] passValue = execResult;
            if (mActivity != null)
                mActivity.runOnUiThread(new Thread(() -> onPostExecute(passValue)));
            else
                onPostExecute(passValue);
        }
    }
}