package com.sggdev.wcwebcameracontrol;

import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.JSON_DEVICE;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.JSON_NAME;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.JSON_PASS;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.JSON_RESULT;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.JSON_SHASH;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.REST_DISCONNECT_BY_USER;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.REST_ERR_DATABASE_FAIL;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.REST_ERR_INTERNAL_UNK;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.REST_ERR_JSON_FAIL;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.REST_ERR_NETWORK;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.REST_ERR_NETWORK_ON_LOGIN;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.REST_ERR_NO_SUCH_SESSION;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.REST_ERR_NO_SUCH_USER;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.REST_ERR_UNSPECIFIED;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.REST_RESULT_OK;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.WC_REST_authorize;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.WC_REST_getDevicesOnline;
import static java.util.concurrent.TimeUnit.SECONDS;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;

public class WCHTTPClient {
    interface OnStateChangeListener {
        void onConnect(String sid);
        void onDisconnect(int errCode);
        void onClientStateChanged(int newState);

        void onLoginError(int errCode, String errStr);
    }

    interface WCClientConfigInterface {
        String getUserName();
        String getHostURL();
        String getUserPassword();
        String getDeviceName();
        String getSID();
    }

    public static final int CS_USER_CFG_INCORRECT = -1;
    public static final int CS_DISCONNECTED = 0;
    public static final int CS_DISCONNECTED_BY_USER = 1;
    public static final int CS_CONNECTING = 2;
    public static final int CS_CONNECTED = 3;
    public static final int CS_SERVER_SCANNING = 4;

    private final Context mContext;
    private OkHttpClient client = null;
    private final List<OnStateChangeListener> onStateChangeListeners = new ArrayList<>();
    private WCClientConfigInterface cfg;

    private int httpClientState = CS_DISCONNECTED;

    WCHTTPClient(Context context) {
        mContext = context;
    }

    void addStateChangeListener(OnStateChangeListener aList) {
        onStateChangeListeners.add(aList);
    }

    void setConfigInterface(WCClientConfigInterface aInt) {
        cfg = aInt;
    }

    void removeStateChangeListener(OnStateChangeListener aList) {
        onStateChangeListeners.remove(aList);
    }

    int state() {
        return httpClientState;
    }

    Context getContext() {
        return mContext;
    }

    OkHttpClient getClient() {
        if (client == null) {
            client = getUnsafeOkHttpClient();
        }
        return client;
    }

    void releaseClient() {
        client = null;
        disconnect(REST_ERR_UNSPECIFIED);
    }

    void retryConnect() {
        setState(CS_DISCONNECTED);
    }

    void disconnectByUser() {
        setState(CS_DISCONNECTED_BY_USER);
        for (OnStateChangeListener aList : onStateChangeListeners)
            aList.onDisconnect(REST_DISCONNECT_BY_USER);
    }

    private void setState(int newState) {
        if (newState != httpClientState) {
            httpClientState = newState;
            for (OnStateChangeListener aList : onStateChangeListeners)
                aList.onClientStateChanged(newState);
        }
    }

    private void disconnect(int code) {
        setState(CS_DISCONNECTED);
        for (OnStateChangeListener aList : onStateChangeListeners)
            aList.onDisconnect(code);
    }

    private void setCfgIncorrect() {
        setState(CS_USER_CFG_INCORRECT);
    }

    private void setConnected(String sid) {
        setState(CS_CONNECTED);
        for (OnStateChangeListener aList : onStateChangeListeners)
            aList.onConnect(sid);
    }

    private void doError(int errCode, JSONObject msg) {
        switch (errCode) {
            case REST_ERR_NO_SUCH_SESSION:
            case REST_ERR_DATABASE_FAIL:
            case REST_ERR_JSON_FAIL:
            case REST_ERR_INTERNAL_UNK:
            case REST_ERR_NETWORK:
                disconnect(errCode);
                break;
            case REST_ERR_NO_SUCH_USER:
            case REST_ERR_NETWORK_ON_LOGIN: {
                final String errStr = msg.optString(JSON_RESULT, mContext.getString(R.string.no_details));
                disconnect(errCode);
                setCfgIncorrect();
                for (OnStateChangeListener aList : onStateChangeListeners)
                    aList.onLoginError(errCode, errStr);
                break;
            }
        }
    }

    void startScanning(Activity caller, OnJSONRequestFinished onSuccess) {
        try {
            httpClientState = CS_SERVER_SCANNING;

            JSONObject obj = new JSONObject();
            obj.put(JSON_SHASH, cfg.getSID());

            WCRESTTask wc_task = new WCRESTTask(this, caller);
            wc_task.setOnJSONResponseListener((resultCode, resultMsg) -> {
                httpClientState = CS_CONNECTED;
                if (resultCode == REST_RESULT_OK) {
                    onSuccess.onChange(resultCode, resultMsg);
                } else {
                    doError(resultCode, resultMsg);
                }
            });
            wc_task.execute(cfg.getHostURL(),
                    WC_REST_getDevicesOnline, obj.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    void startConnect(Activity caller) {
        try {
            httpClientState = CS_CONNECTING;

            JSONObject obj = new JSONObject();
            obj.put(JSON_NAME, cfg.getUserName());
            obj.put(JSON_PASS, cfg.getUserPassword());
            obj.put(JSON_DEVICE, cfg.getDeviceName());

            WCRESTTask wc_task = new WCRESTTask(this, caller);
            wc_task.setOnJSONResponseListener((resultCode, resultMsg) -> {
                if (resultCode == REST_RESULT_OK)
                    setConnected(resultMsg.optString(JSON_SHASH));
                else {
                    if (resultCode == REST_ERR_NETWORK)
                        resultCode = REST_ERR_NETWORK_ON_LOGIN;
                    doError(resultCode, resultMsg);
                }
            });
            wc_task.execute(cfg.getHostURL(), WC_REST_authorize, obj.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private static OkHttpClient getUnsafeOkHttpClient() {
        try {
            // Create a trust manager that does not validate certificate chains
            final TrustManager[] trustAllCerts = new TrustManager[] {
                    new X509TrustManager() {
                        @SuppressLint("TrustAllX509TrustManager")
                        @Override
                        public void checkClientTrusted(java.security.cert.X509Certificate[] chain,
                                                       String authType) {
                        }

                        @SuppressLint("TrustAllX509TrustManager")
                        @Override
                        public void checkServerTrusted(java.security.cert.X509Certificate[] chain,
                                                       String authType) {
                        }

                        @Override
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return new java.security.cert.X509Certificate[]{};
                        }
                    }
            };

            // Install the all-trusting trust manager
            final SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            // Create an ssl socket factory with our all-trusting manager
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            OkHttpClient.Builder builder = new OkHttpClient.Builder();
            builder.sslSocketFactory(sslSocketFactory, (X509TrustManager)trustAllCerts[0]);
            builder.hostnameVerifier((hostname, session) -> true);
            builder.readTimeout(20, SECONDS);
            builder.writeTimeout(20, SECONDS);

            return builder.build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
