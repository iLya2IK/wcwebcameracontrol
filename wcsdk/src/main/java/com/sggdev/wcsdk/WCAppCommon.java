package com.sggdev.wcsdk;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.provider.Settings;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;

public class WCAppCommon extends Application implements  androidx.work.Configuration.Provider, LifecycleObserver {

    @NonNull
    @Override
    public androidx.work.Configuration getWorkManagerConfiguration() {
        return new androidx.work.Configuration.Builder()
                .setMinimumLoggingLevel(android.util.Log.INFO)
                .build();
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    public void onAppBackgrounded() {
        WCHTTPResync.restartWCHTTPBackgroundWork(this);
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    public void onAppForegrounded() { WCHTTPResync.stopWCHTTPBackgroundWork(this); }

    public class DevicesHolderList extends ArrayList<DeviceItem> {

        private int mDeviceListUpdate = 0;
        private boolean mDeviceItemsChanged = false;

        private final ReentrantLock lock = new ReentrantLock();

        public void lock() { lock.lock(); }

        public void unlock() {
            lock.unlock();
        }

        DevicesHolderList() {
            loadFromDB();
        }

        void loadFromDB() {
            ChatDatabase db = ChatDatabase.getInstance(WCAppCommon.this);
            List<JSONObject> items = db.getAllDevices(getHttpCfgFullUserName());
            lock();
            try {
                for (JSONObject obj : items)
                    add(new DeviceItem(WCAppCommon.this, obj));
            } finally {
                unlock();
            }
        }

        public void saveToDB() {
            ChatDatabase db = ChatDatabase.getInstance(WCAppCommon.this);
            lock();
            try {
                db.addOrUpdateDevices(this);
            } finally {
                unlock();
            }
        }

        public void completeItem(DeviceItem aItem,
                                String deviceServerName,
                                String deviceBLEName,
                                String deviceBLEAddress) {
            lock();
            try {
                for (int i = 0; i < size(); i++)
                    if (aItem.tryToCompleteFrom(get(i),
                            deviceServerName,
                            deviceBLEName,
                            deviceBLEAddress))
                        return;

                aItem.complete(deviceServerName, deviceBLEName, deviceBLEAddress);
            } finally {
                unlock();
            }
        }

        public void completeItem(DeviceItem aItem,
                          String deviceServerName) {
            lock();
            try {
                for (int i = 0; i < size(); i++)
                    if (aItem.tryToCompleteFrom(get(i),
                            deviceServerName,
                            "", ""))
                        return;

                aItem.complete(deviceServerName);
            } finally {
                unlock();
            }
        }

        public void completeItemWithMeta(DeviceItem aItem,
                          String meta) {
            lock();
            try {
                aItem.completeWithMeta(meta);
            } finally {
                unlock();
            }
        }

        public void completeItem(DeviceItem aItem,
                          String deviceBLEName,
                          String deviceBLEAddress) {
            lock();
            try {
                for (int i = 0; i < size(); i++)
                    if (aItem.tryToCompleteFrom(get(i),
                            "",
                            deviceBLEName,
                            deviceBLEAddress))
                        break;

                aItem.complete("", deviceBLEName, deviceBLEAddress);
            } finally {
                unlock();
            }
        }

        public void setDeviceProps(DeviceItem aItem,
                            int mDeviceColor,
                            int mDeviceIndex) {
            aItem.setProps(mDeviceColor, mDeviceIndex);
            DeviceItem m_obj = findItem(aItem);
            lock();
            try {
                if (m_obj != null) {
                    if (m_obj != aItem)
                        m_obj.setProps(mDeviceColor, mDeviceIndex);
                    mDeviceItemsChanged = true;
                    updateDeviceItems();
                }
            } finally {
                unlock();
            }
        }

        public void setDeviceSyncProps(DeviceItem aItem,
                            String mLstSync,
                            int mCnt) {
            aItem.setSyncProps(mLstSync, mCnt);
            DeviceItem m_obj = findItem(aItem);
            lock();
            try {
                if (m_obj != null) {
                    if (m_obj != aItem)
                        m_obj.setSyncProps(mLstSync, mCnt);
                    mDeviceItemsChanged = true;
                    updateDeviceItems();
                }
            } finally {
                unlock();
            }
        }

        public DeviceItem findItem(DeviceItem aobj) {
            lock();
            try {
                for (DeviceItem obj : mDeviceItems)
                    if (obj.equals(aobj))
                        return obj;

                return null;
            } finally {
                unlock();
            }
        }

        public DeviceItem findItem(long devId) {
            lock();
            try {
                if (devId > 0)
                    for (DeviceItem obj : mDeviceItems)
                        if (obj.getDbId() == devId)
                            return obj;

                return null;
            } finally {
                unlock();
            }
        }

        public DeviceItem findItem(String username, String devHostName) {
            lock();
            try {
                if (devHostName != null && devHostName.length() > 0)
                    for (DeviceItem obj : mDeviceItems)
                        if (obj.getDeviceServerName().equals(devHostName) &&
                            obj.getDeviceUserName().equals(username))
                            return obj;

                return null;
            } finally {
                unlock();
            }
        }

        public void saveItem(DeviceItem n_obj) {
            DeviceItem m_obj = findItem(n_obj);
            lock();
            try {
                if (m_obj != null) {
                    mDeviceItemsChanged |= m_obj.updateFromResult(n_obj);
                } else {
                    mDeviceItems.add(n_obj);
                    mDeviceItemsChanged = true;
                }
            } finally {
                unlock();
            }
            updateDeviceItems();
        }

        void removeItem(DeviceItem n_obj) {
            lock();
            try {
                DeviceItem r_obj = null;
                for (DeviceItem obj : mDeviceItems)
                    if (obj.equals(n_obj)) {
                        r_obj = obj;
                        break;
                    }
                if (r_obj != null) {
                    mDeviceItemsChanged = true;
                    mDeviceItems.remove(r_obj);
                    updateDeviceItems();
                }
            } finally {
                unlock();
            }
        }

        void updateDeviceItems() {
            lock();
            try {
                if (mDeviceListUpdate == 0 && mDeviceItemsChanged) {
                    mDeviceItemsChanged = false;
                    Collections.sort(mDeviceItems);
                    saveToDB();
                }
            } finally {
                unlock();
            }
        }

        public void beginUpdate() {
            lock();
            try {
                mDeviceListUpdate++;
            } finally {
                unlock();
            }
        }
        public void endUpdate() {
            lock();
            try {
                mDeviceListUpdate--;
                updateDeviceItems();
            } finally {
                unlock();
            }
        }

    }

    DevicesHolderList mDeviceItems;

    public DevicesHolderList getDevicesHolderList() {return  mDeviceItems;}

    private static final char[] SSKW = {'s','o','m','e','S','e','c','r','e','t','K','e','y'};
    public static final String PREF_USER_PREFS  = "USER_PREFS";
    public static final String PREF_HTTP_CFG_URL  = "PREF_HTTP_CFG_URL";
    public static final String PREF_HTTP_CFG_USER = "PREF_HTTP_CFG_U";
    public static final String PREF_HTTP_CFG_PSW  = "PREF_HTTP_CFG_K";
    public static final String PREF_HTTP_CFG_DEVICE  = "PREF_HTTP_CFG_DEVICE";
    public static final String PREF_HTTP_CFG_SID  = "PREF_HTTP_CFG_SID";
    public static final String PREF_DB_CFG_LAST_REC = "PREF_DB_CFG_LAST_REC";
    public static final String PREF_DB_CFG_LAST_MSG = "PREF_DB_CFG_LAST_MSG";

    @SuppressLint("HardwareIds")
    protected String encrypt(String value ) {
        try {
            final byte[] bytes = value!=null ? value.getBytes(StandardCharsets.UTF_8) : new byte[0];
            SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("PBEWithMD5AndDES");
            SecretKey key = keyFactory.generateSecret(new PBEKeySpec(SSKW));
            Cipher pbeCipher = Cipher.getInstance("PBEWithMD5AndDES");
            pbeCipher.init(Cipher.ENCRYPT_MODE, key,
                    new PBEParameterSpec(Settings.Secure.getString(getContentResolver(),
                            Settings.Secure.ANDROID_ID).getBytes(StandardCharsets.UTF_8), 16));
            return new String(Base64.encode(pbeCipher.doFinal(bytes), Base64.NO_WRAP),
                    StandardCharsets.UTF_8);

        } catch( Exception e ) {
            throw new RuntimeException(e);
        }
    }

    @SuppressLint("HardwareIds")
    protected String decrypt(String value){
        try {
            final byte[] bytes = value!=null ? Base64.decode(value,Base64.DEFAULT) : new byte[0];
            SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("PBEWithMD5AndDES");
            SecretKey key = keyFactory.generateSecret(new PBEKeySpec(SSKW));
            Cipher pbeCipher = Cipher.getInstance("PBEWithMD5AndDES");
            pbeCipher.init(Cipher.DECRYPT_MODE, key,
                    new PBEParameterSpec(Settings.Secure.getString(getContentResolver(),
                            Settings.Secure.ANDROID_ID).getBytes(StandardCharsets.UTF_8), 16));
            return new String(pbeCipher.doFinal(bytes),StandardCharsets.UTF_8);

        } catch( Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String getSecurePref(String pref) {
        SharedPreferences sp = getSharedPreferences(PREF_USER_PREFS, MODE_PRIVATE);
        String res = sp.getString(pref, null);
        if (res != null) {
            res = decrypt(res);
        }
        return res;
    }

    public void setSecurePref(String pref, String value) {
        SharedPreferences sp = getSharedPreferences(PREF_USER_PREFS, MODE_PRIVATE);
        SharedPreferences.Editor spEd = sp.edit();
        spEd.putString(pref, encrypt(value));
        spEd.apply();
    }

    private String getPref(String pref) {
        SharedPreferences sp = getSharedPreferences(PREF_USER_PREFS, MODE_PRIVATE);
        return sp.getString(pref, null);
    }

    private void setPref(String pref, String value) {
        SharedPreferences sp = getSharedPreferences(PREF_USER_PREFS, MODE_PRIVATE);
        SharedPreferences.Editor spEd = sp.edit();
        spEd.putString(pref, value);
        spEd.apply();
    }

    public String getHttpCfgServerUrl() {
        return getSecurePref(PREF_HTTP_CFG_URL);
    }

    public String getHttpCfgUserName() {
        return getSecurePref(PREF_HTTP_CFG_USER);
    }

    public String getHttpCfgFullUserName() {
        return getSecurePref(PREF_HTTP_CFG_URL) + "@" + getSecurePref(PREF_HTTP_CFG_USER);
    }

    public String getHttpCfgUserPsw() {
        return getSecurePref(PREF_HTTP_CFG_PSW);
    }

    public void setHttpCfgServerUrl(String value) {
        setSecurePref(PREF_HTTP_CFG_URL, value);
    }

    public void setHttpCfgUserName(String value) {
        setSecurePref(PREF_HTTP_CFG_USER, value);
    }

    public void setHttpCfgUserPsw(String value) {
        setSecurePref(PREF_HTTP_CFG_PSW, value);
    }

    public String getHttpCfgDevice() {
        return getPref(PREF_HTTP_CFG_DEVICE);
    }

    public  void setHttpCfgDevice(String value) {
        setPref(PREF_HTTP_CFG_DEVICE, value);
    }

    public String getHttpCfgSID() { return getPref(PREF_HTTP_CFG_SID); }

    public String dbGetLastRecStamp() { return getPref(PREF_DB_CFG_LAST_REC); }

    public void dbSetLastRecStamp(String value) { setPref(PREF_DB_CFG_LAST_REC, value); }

    public String dbGetLastMsgStamp() { return getPref(PREF_DB_CFG_LAST_MSG); }

    public void dbSetLastMsgStamp(String value) { setPref(PREF_DB_CFG_LAST_MSG, value); }

    public  void setHttpCfgSID(String value) {
        setPref(PREF_HTTP_CFG_SID, value);
    }

    public String getCurrentSsid() {
        String ssid = null;
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = cm.getActiveNetworkInfo();
        if (networkInfo == null) {
            return null;
        }

        if (networkInfo.isConnected()) {
            final WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
            final WifiInfo connectionInfo = wifiManager.getConnectionInfo();
            if (connectionInfo != null && (connectionInfo.getSSID().length() > 0)) {
                ssid = connectionInfo.getSSID();
            }
        }

        return ssid;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        WCHTTPResync.init(this);
        androidx.lifecycle.ProcessLifecycleOwner.get().getLifecycle().addObserver(this);

        mDeviceItems = new DevicesHolderList();
        WCHTTPClient wchttpClient = WCHTTPClientHolder.getInstance(this);
        wchttpClient.setConfigInterface(new WCHTTPClient.WCClientConfigInterface() {
            @Override
            public String getUserName() {
                return getHttpCfgUserName();
            }

            @Override
            public String getHostURL() {
                return getHttpCfgServerUrl();
            }

            @Override
            public String getFullUserName() {
                return getHttpCfgFullUserName();
            }

            @Override
            public String getUserPassword() {
                return getHttpCfgUserPsw();
            }

            @Override
            public String getDeviceName() {
                return getHttpCfgDevice();
            }

            @Override
            public String getSID() {
                return getHttpCfgSID();
            }

            @Override
            public String getLastRecStamp() { return dbGetLastRecStamp(); }

            @Override
            public void setLastRecStamp(String aStamp) {dbSetLastRecStamp(aStamp);}

            @Override
            public String getLastMsgStamp() {return dbGetLastMsgStamp();}

            @Override
            public void setLastMsgStamp(String aStamp) {dbSetLastMsgStamp(aStamp);}
        });

        wchttpClient.addStateChangeListener(new WCHTTPClient.OnStateChangeListener() {

            @Override
            public void onConnect(String sid) {
                setHttpCfgSID(sid);
            }

            @Override
            public void onDisconnect(int code) {
                setHttpCfgSID("");
            }

            @Override
            public void onClientStateChanged(int newState) { /* none */ }

            @Override
            public void onLoginError(int errCode, String errStr) { /* none */ }
        });
    }

    public static WCHTTPResync.OnSyncFinished cDummyNotifier = new WCHTTPResync.OnSyncFinished() {
        @Override
        public boolean onNewMessagesSummary(Context context, int totalAmount, String lstSync, List<WCChat.DeviceMsgsCnt> aList) { return false; }
        @Override
        public void onNewMessages(Context context, List<WCChat.ChatMessage> aList, List<String> aDevices) {}
        @Override
        public void onError(Context context, int state, String aError) {}
        @Override
        public void onNoMessages(Context context) {}
    };

    public WCHTTPResync.OnSyncFinished getOnSyncNotify() {
        return cDummyNotifier;
    }

    public void doOnBackgroundSync(WCHTTPClient httpClient) {
        httpClient.recvMsgsSynchro(null, false);
        httpClient.recvSnapsSynchro(null);
        syncTimeWithServer(httpClient);
    }

    public int getBackgroundWorkCleanUpTime() {return 5;}

    public void configBackroundResync(WCHTTPResync.Builder builder) {}

    public void alertWrongUser(Activity act,
                        String errorStr,
                        DialogInterface.OnClickListener onPositive) {
        // dummy method
    }

    public void syncTimeWithServer(WCHTTPClient httpClient) {
        httpClient.getServerTimeSynchro(null, null);
    }
}
