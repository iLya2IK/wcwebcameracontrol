package com.sggdev.wcwebcameracontrol;

import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.JSON_DEVICE;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.JSON_DEVICES;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.JSON_MSG;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.JSON_MSGS;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.JSON_NAME;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.JSON_PARAMS;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.JSON_PASS;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.JSON_RECORDS;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.JSON_RESULT;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.JSON_RID;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.JSON_SHASH;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.JSON_STAMP;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.JSON_SYNC;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.JSON_TARGET;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.REST_DISCONNECT_BY_USER;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.REST_ERR_DATABASE_FAIL;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.REST_ERR_INTERNAL_UNK;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.REST_ERR_JSON_FAIL;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.REST_ERR_NETWORK_UNK;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.REST_ERR_NETWORK_HOST;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.REST_ERR_NETWORK_ILLEGAL;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.REST_ERR_NETWORK_IO;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.REST_ERR_NETWORK_ON_LOGIN;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.REST_ERR_NETWORK_SHUT;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.REST_ERR_NETWORK_TIMEOUT;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.REST_ERR_NO_SUCH_SESSION;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.REST_ERR_NO_SUCH_USER;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.REST_ERR_UNSPECIFIED;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.REST_RESULT_OK;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.WC_REST_addMsgs;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.WC_REST_authorize;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.WC_REST_getDevicesOnline;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.WC_REST_getMsgs;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.WC_REST_getMsgsAndSync;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.WC_REST_getRecordCount;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.WC_REST_getRecordData;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.WC_REST_heartBit;
import static java.util.concurrent.TimeUnit.SECONDS;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;

import org.json.JSONArray;
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
        String getLastRecStamp();
        void setLastRecStamp(String aStamp);
        String getLastMsgStamp();
        void setLastMsgStamp(String aStamp);
    }

    public static final int CS_USER_CFG_INCORRECT = -1;
    public static final int CS_DISCONNECTED = 0;
    public static final int CS_DISCONNECTED_BY_USER = 1;
    public static final int CS_DISCONNECTED_RETRY_OVER = 2;
    public static final int CS_CONNECTING = 3;
    public static final int CS_CONNECTED = 4;
    public static final int CS_SERVER_SCANNING = 5;

    private static final int MAX_CONNECTED_RETRY_AMOUNT = 5;

    private final Context mContext;
    private OkHttpClient client = null;
    private final List<OnStateChangeListener> onStateChangeListeners = new ArrayList<>();
    private WCClientConfigInterface cfg;

    private int httpClientState = CS_DISCONNECTED;
    private int failedConnection = 0;
    private boolean httpClientSendingMsgs = false;

    WCHTTPClient(Context context) {
        mContext = context;
    }

    void addStateChangeListener(OnStateChangeListener aList) {
        for (OnStateChangeListener aInList : onStateChangeListeners)
            if (aInList == aList) return;

        onStateChangeListeners.add(aList);
        if (httpClientState == CS_CONNECTED)
            aList.onConnect(((WCApp)(mContext.getApplicationContext())).getHttpCfgSID());
        aList.onClientStateChanged(httpClientState);
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
        failedConnection = 0;
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
        failedConnection++;
        if (failedConnection > MAX_CONNECTED_RETRY_AMOUNT) {
            setState(CS_DISCONNECTED_RETRY_OVER);
        } else {
            setState(CS_DISCONNECTED);
        }
        for (OnStateChangeListener aList : onStateChangeListeners)
            aList.onDisconnect(code);
    }

    private void setCfgIncorrect() {
        setState(CS_USER_CFG_INCORRECT);
    }

    private void setConnected(String sid) {
        failedConnection = 0;
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
            case REST_ERR_NETWORK_TIMEOUT:
            case REST_ERR_NETWORK_IO:
            case REST_ERR_NETWORK_ILLEGAL:
            case REST_ERR_NETWORK_SHUT:
                disconnect(errCode);
                break;
            case REST_ERR_NO_SUCH_USER:
            case REST_ERR_NETWORK_ON_LOGIN:
            case REST_ERR_NETWORK_HOST: {
                final String errStr = msg.optString(JSON_RESULT, mContext.getString(R.string.no_details));
                disconnect(errCode);
                setCfgIncorrect();
                for (OnStateChangeListener aList : onStateChangeListeners)
                    aList.onLoginError(errCode, errStr);
                break;
            }
        }
    }

    void checkDeviceConnected(Activity caller, final String aDeviceToFind,
                              OnBooleanRequestFinished onResult) {

        if (httpClientState != CS_CONNECTED)
            if (onResult != null) onResult.onFail();

        try {
            JSONObject obj = new JSONObject();
            obj.put(JSON_SHASH, cfg.getSID());

            WCRESTTask wc_task = new WCRESTTask(this, caller);
            wc_task.setOnJSONResponseListener((resultCode, resultMsg) -> {
                if (resultCode == REST_RESULT_OK) {
                    Object res = resultMsg.opt(JSON_DEVICES);

                    if (res instanceof JSONArray)
                        for (int i = 0; i < ((JSONArray) res).length(); i++) {
                            Object aDevice = ((JSONArray) res).opt(i);
                            if (aDevice instanceof String)
                                if (((String) aDevice).equals(aDeviceToFind)) {
                                    onResult.onSuccess();
                                    return;
                                }
                        }
                    onResult.onFail();
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

    private WCRESTTask heartBitPrepare(Activity caller) {
        if ((httpClientState != CS_CONNECTED) || httpClientSendingMsgs) return null;
        try {
            JSONObject obj = new JSONObject();
            obj.put(JSON_SHASH, cfg.getSID());

            WCRESTTask wc_task = new WCRESTTask(this, caller);
            wc_task.setOnJSONResponseListener((resultCode, resultMsg) -> {
                if (resultCode != REST_RESULT_OK)
                    doError(resultCode, resultMsg);
            });
            wc_task.setParams(cfg.getHostURL(), WC_REST_heartBit, obj.toString());
            return wc_task;
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void heartBit(Activity caller) {
        WCRESTTask wc_task = heartBitPrepare(caller);
        if (wc_task != null) wc_task.start();
    }

    public void heartBitSynchro(Activity caller) {
        WCRESTTask wc_task = heartBitPrepare(caller);
        if (wc_task != null) wc_task.run();
    }

    private WCRESTTask startConnectPrepare(Activity caller) {
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
                    if (resultCode == REST_ERR_NETWORK_UNK)
                        resultCode = REST_ERR_NETWORK_ON_LOGIN;
                    doError(resultCode, resultMsg);
                }
            });
            wc_task.setParams(cfg.getHostURL(), WC_REST_authorize, obj.toString());
            return wc_task;
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void startConnect(Activity caller) {
        WCRESTTask wc_task = startConnectPrepare(caller);
        if (wc_task != null) wc_task.start();
    }

    public void startConnectSynchro(Activity caller) {
        WCRESTTask wc_task = startConnectPrepare(caller);
        if (wc_task != null) wc_task.run();
    }

    private WCRESTTask sendMsgsPrepare(Activity caller, DeviceItem aUser, int limit,
                                      OnItemChangedListener onResult) {
        if ((httpClientState != CS_CONNECTED) || httpClientSendingMsgs) return null;

        try {
            httpClientSendingMsgs = true;
            ChatDatabase db = ChatDatabase.getInstance(caller);
            final List<WCChat.ChatMessage> msgs = new ArrayList<>();
            if (db.getUnsentMsgs(aUser, msgs, limit)) {
                JSONObject obj;
                if (msgs.size() == 1) {
                    WCChat.ChatMessage msg_tosend = msgs.get(0);
                    obj = new JSONObject( msg_tosend.getRawMessage() );
                    if (msg_tosend.getTarget().length() > 0)
                        obj.put(JSON_TARGET, msg_tosend.getTarget());
                } else {
                    obj = new JSONObject();
                    JSONArray jmsgs = new JSONArray();
                    for (WCChat.ChatMessage msg_tosend : msgs) {
                        JSONObject nobj = new JSONObject( msg_tosend.getRawMessage() );
                        if (msg_tosend.getTarget().length() > 0)
                            nobj.put(JSON_TARGET, msg_tosend.getTarget());
                        jmsgs.put(nobj);
                    }
                    obj.put(JSON_MSGS, jmsgs);
                }
                obj.put(JSON_SHASH, cfg.getSID());

                WCRESTTask wc_task = new WCRESTTask(this, caller);
                wc_task.setOnJSONResponseListener((resultCode, resultMsg) -> {
                    if (resultCode == REST_RESULT_OK) {
                        ChatDatabase db0 = ChatDatabase.getInstance(caller);
                        db0.setMsgsState(msgs, ChatDatabase.MSG_STATE_SENDED);
                        if (onResult != null) {
                            for (WCChat.ChatMessage msg : msgs) {
                                onResult.onChange(msg.getDbId());
                            }
                        }
                    } else {
                        doError(resultCode, resultMsg);
                    }
                    httpClientSendingMsgs = false;
                });
                wc_task.setParams(cfg.getHostURL(),
                        WC_REST_addMsgs, obj.toString());
                return wc_task;
            } else {
                httpClientSendingMsgs = false;
                return null;
            }
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void sendMsgs(Activity caller, DeviceItem aUser, int limit,
                                      OnItemChangedListener onResult) {
        WCRESTTask wc_task = sendMsgsPrepare(caller, aUser, limit,
                onResult);
        if (wc_task != null) wc_task.start();
    }

    public void sendMsgsSynchro(Activity caller, DeviceItem aUser, int limit,
                         OnItemChangedListener onResult) {
        WCRESTTask wc_task = sendMsgsPrepare(caller, aUser, limit,
                onResult);
        if (wc_task != null) wc_task.run();
    }

    public void sendSync(Activity caller) {
        if ((httpClientState != CS_CONNECTED) || httpClientSendingMsgs) return;

        try {
            httpClientSendingMsgs = true;
            JSONObject obj = new JSONObject();
            obj.put(JSON_MSG, JSON_SYNC);
            obj.put(JSON_SHASH, cfg.getSID());

            WCRESTTask wc_task = new WCRESTTask(this, caller);
            wc_task.setOnJSONResponseListener((resultCode, resultMsg) -> {
                if (resultCode != REST_RESULT_OK) {
                    doError(resultCode, resultMsg);
                }
                httpClientSendingMsgs = false;
            });
            wc_task.execute(cfg.getHostURL(),
                    WC_REST_addMsgs, obj.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private WCRESTTask recvSnapsPrepare(Activity caller) {
        if (httpClientState != CS_CONNECTED) return null;

        try {
            JSONObject obj = new JSONObject();
            obj.put(JSON_SHASH, cfg.getSID());
            obj.put(JSON_STAMP, cfg.getLastRecStamp());

            WCRESTTask wc_task = new WCRESTTask(this, caller);
            wc_task.setOnJSONResponseListener((resultCode, resultMsg) -> {
                if (resultCode == REST_RESULT_OK) {
                    Object res = resultMsg.opt(JSON_RECORDS);

                    if (res instanceof JSONArray &&
                            (((JSONArray) res).length() > 0)) {
                        WCApp capp = (WCApp) mContext.getApplicationContext();

                        ChatDatabase db = ChatDatabase.getInstance(capp);
                        List<WCChat.ChatMedia> mediaList = new ArrayList<>();
                        List<JSONObject> list = new ArrayList<>();

                        capp.mDeviceItems.beginUpdate();
                        try {
                            try {
                                for (int i = 0; i < ((JSONArray) res).length(); i++) {
                                    JSONObject rec = (JSONObject) ((JSONArray) res).get(i);

                                    Object dev = rec.opt(JSON_DEVICE);
                                    if (dev instanceof String) {
                                        DeviceItem d = capp.mDeviceItems.findItem((String) dev);
                                        if (d == null) {
                                            d = new DeviceItem(capp);
                                            d.complete((String) dev);
                                            capp.mDeviceItems.saveItem(d);
                                        }
                                        int rid = rec.optInt(JSON_RID, -1);
                                        if (rid >= 0) {
                                            WCChat.ChatMedia media = new WCChat.ChatMedia(-1);
                                            media.setServerRID(rid);
                                            media.setSender(d.getDbId());
                                            mediaList.add(media);
                                        }
                                    }
                                    list.add(rec);
                                }
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        } finally {
                            capp.mDeviceItems.endUpdate();
                        }

                        db.addMedia(mediaList);
                        String lst = db.addMsgs(list, ChatDatabase.MSG_STATE_RECIEVED);
                        if (lst != null && lst.length() > 0)
                            cfg.setLastRecStamp(lst);
                    }
                } else {
                    doError(resultCode, resultMsg);
                }
            });
            wc_task.setParams(cfg.getHostURL(),
                    WC_REST_getRecordCount, obj.toString());
            return wc_task;
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void recvSnaps(Activity caller) {
        WCRESTTask wc_task = recvSnapsPrepare(caller);
        if (wc_task != null) wc_task.start();
    }

    public void recvSnapsSynchro(Activity caller) {
        WCRESTTask wc_task = recvSnapsPrepare(caller);
        if (wc_task != null) wc_task.run();
    }

    public WCRESTTask recvMsgsPrepare(Activity caller, boolean doSync) {
        if (httpClientState != CS_CONNECTED) return null;

        try {
            JSONObject obj = new JSONObject();
            obj.put(JSON_SHASH, cfg.getSID());
            obj.put(JSON_STAMP, cfg.getLastMsgStamp());

            WCRESTTask wc_task = new WCRESTTask(this, caller);
            wc_task.setOnJSONResponseListener((resultCode, resultMsg) -> {
                if (resultCode == REST_RESULT_OK) {
                    Object res = resultMsg.opt(JSON_MSGS);

                    if (res instanceof JSONArray &&
                            (((JSONArray) res).length() > 0)) {
                        WCApp capp = (WCApp) mContext.getApplicationContext();

                        ChatDatabase db = ChatDatabase.getInstance(capp);
                        List<JSONObject> list = new ArrayList<>();

                        capp.mDeviceItems.beginUpdate();
                        try {
                            try {
                                for (int i = 0; i < ((JSONArray) res).length(); i++) {
                                    JSONObject rec = (JSONObject) ((JSONArray) res).get(i);
                                    Object dev = rec.opt(JSON_DEVICE);
                                    if (dev instanceof String) {
                                        DeviceItem d = capp.mDeviceItems.findItem((String) dev);
                                        if (d == null) {
                                            d = new DeviceItem(capp);
                                            d.complete((String) dev);
                                            capp.mDeviceItems.saveItem(d);
                                        }
                                    }
                                    list.add(rec);
                                }
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        } finally {
                            capp.mDeviceItems.endUpdate();
                        }

                        String lst = db.addMsgs(list, ChatDatabase.MSG_STATE_RECIEVED);
                        if (lst != null && lst.length() > 0)
                            cfg.setLastMsgStamp(lst);
                    }
                } else {
                    doError(resultCode, resultMsg);
                }
            });
            if (doSync) {
                wc_task.setParams(cfg.getHostURL(),
                        WC_REST_getMsgsAndSync, obj.toString());
            } else {
                wc_task.setParams(cfg.getHostURL(),
                        WC_REST_getMsgs, obj.toString());
            }
            return wc_task;
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void recvMsgs(Activity caller, boolean doSync) {
        WCRESTTask wc_task = recvMsgsPrepare(caller, doSync);
        if (wc_task != null) wc_task.start();
    }

    public void recvMsgsSynchro(Activity caller, boolean doSync) {
        WCRESTTask wc_task = recvMsgsPrepare(caller, doSync);
        if (wc_task != null) wc_task.run();
    }

    private WCRESTTask getRecordDataPrepare(Activity caller, WCChat.ChatMedia media,
                                      OnRawRequestFinished onFinish) {
        if (httpClientState != CS_CONNECTED) return null;

        try {
            JSONObject obj = new JSONObject();
            obj.put(JSON_SHASH, cfg.getSID());
            obj.put(JSON_RID, media.getServerRID());

            WCRESTTask wc_task = new WCRESTTask(this, caller, WCRESTTask.WC_RESULT_RAW_DATA);
            wc_task.setOnRawResponseListener(onFinish);
            wc_task.setOnJSONResponseListener((resultCode, resultMsg) -> {
                onFinish.onChange(resultCode, null);
                if (resultCode != REST_RESULT_OK) {
                    doError(resultCode, resultMsg);
                }
            });
            wc_task.setParams(cfg.getHostURL(),
                    WC_REST_getRecordData, obj.toString());
            return wc_task;
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void getRecordData(Activity caller, WCChat.ChatMedia media,
                              OnRawRequestFinished onFinish) {
        WCRESTTask wc_task = getRecordDataPrepare(caller, media, onFinish);
        if (wc_task != null) {
            wc_task.start();
        }
    }

    public void getRecordDataSynchro(Activity caller, WCChat.ChatMedia media,
                              OnRawRequestFinished onFinish) {
        WCRESTTask wc_task = getRecordDataPrepare(caller, media, onFinish);
        if (wc_task != null)
            wc_task.run();
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
            builder.readTimeout(30, SECONDS);
            builder.writeTimeout(20, SECONDS);
            builder.connectTimeout(20, SECONDS);

            return builder.build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
