package com.sggdev.wcwebcameracontrol;

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
import android.view.ContextThemeWrapper;

import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;
import androidx.lifecycle.ProcessLifecycleOwner;
import androidx.work.Configuration;

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

public class WCApp extends Application implements Configuration.Provider, LifecycleObserver {

    @Override
    public Configuration getWorkManagerConfiguration() {
        return new Configuration.Builder()
                .setMinimumLoggingLevel(android.util.Log.INFO)
                .build();
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    public void onAppBackgrounded() {
        WCHTTPResync.restartWCHTTPBackgroundWork(this);
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    public void onAppForegrounded() { WCHTTPResync.stopWCHTTPBackgroundWork(this); }

    class DevicesHolderList extends ArrayList<DeviceItem> {

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
            ChatDatabase db = ChatDatabase.getInstance(WCApp.this);
            List<JSONObject> items = db.getAllDevices();
            lock();
            try {
                for (JSONObject obj : items)
                    add(new DeviceItem(WCApp.this, obj));
            } finally {
                unlock();
            }
        }

        void saveToDB() {
            ChatDatabase db = ChatDatabase.getInstance(WCApp.this);
            lock();
            try {
                db.addOrUpdateDevices(this);
            } finally {
                unlock();
            }
        }

        void completeItem(DeviceItem aItem,
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

        void completeItem(DeviceItem aItem,
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

        void completeItem(DeviceItem aItem,
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

        void setDeviceProps(DeviceItem aItem,
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

        void setDeviceSyncProps(DeviceItem aItem,
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

        public DeviceItem findItem(String devHostName) {
            lock();
            try {
                if (devHostName != null && devHostName.length() > 0)
                    for (DeviceItem obj : mDeviceItems)
                        if (obj.getDeviceServerName().equals(devHostName))
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

        void beginUpdate() {
            lock();
            try {
                mDeviceListUpdate++;
            } finally {
                unlock();
            }
        }
        void endUpdate() {
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

    private static final char[] SSKW = {'s','o','m','e','S','e','c','r','e','t','K','e','y'};
    static final String PREF_USER_PREFS  = "USER_PREFS";
    static final String PREF_HTTP_CFG_URL  = "PREF_HTTP_CFG_URL";
    static final String PREF_HTTP_CFG_USER = "PREF_HTTP_CFG_U";
    static final String PREF_HTTP_CFG_PSW  = "PREF_HTTP_CFG_K";
    static final String PREF_HTTP_CFG_DEVICE  = "PREF_HTTP_CFG_DEVICE";
    static final String PREF_HTTP_CFG_SID  = "PREF_HTTP_CFG_SID";
    static final String PREF_DB_CFG_LAST_REC = "PREF_DB_CFG_LAST_REC";
    static final String PREF_DB_CFG_LAST_MSG = "PREF_DB_CFG_LAST_MSG";

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

    private String getSecurePref(String pref) {
        SharedPreferences sp = getSharedPreferences(PREF_USER_PREFS, MODE_PRIVATE);
        String res = sp.getString(pref, null);
        if (res != null) {
            res = decrypt(res);
        }
        return res;
    }

    private void setSecurePref(String pref, String value) {
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
        ProcessLifecycleOwner.get().getLifecycle().addObserver(this);

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

    void alertWrongUser(Activity act,
                        String errorStr,
                        DialogInterface.OnClickListener onPositive) {
        String msg = getString(R.string.alert_edit_config);
        if (errorStr != null)
            msg = msg.concat(getString(R.string.details_prefix)).concat(errorStr);
        final String msgTxt = msg;
        act.runOnUiThread (new Thread(() -> new AlertDialog.Builder(
                new ContextThemeWrapper(act, R.style.AlertDialogCustom))
                .setTitle(R.string.alert_refused_connection)
                .setMessage(msgTxt)
                .setPositiveButton(android.R.string.yes, onPositive)
                .setNegativeButton(android.R.string.cancel, null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show()));
    }
}
