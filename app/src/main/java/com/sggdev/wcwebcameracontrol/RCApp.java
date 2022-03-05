package com.sggdev.wcwebcameracontrol;

import android.annotation.SuppressLint;
import android.app.Application;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;

public class RCApp extends Application {

    class DevicesHolderList extends ArrayList<DeviceItem> {

        private static final String BLE_PREF_DEVICE_DB = "db";
        private static final String BLE_PREF_DEVICE_LIST = "DeviceList";

        private int mDeviceListUpdate = 0;
        private boolean mDeviceItemsChanged = false;

        DevicesHolderList() {
            loadFromDB();
        }

        void loadFromDB() {
            SharedPreferences sp = getSharedPreferences(BLE_PREF_DEVICE_DB, MODE_PRIVATE);
            String res = sp.getString(BLE_PREF_DEVICE_LIST, null);
            if (res != null) {
                try {
                    JSONArray items = new JSONArray(res);
                    for (int i = 0; i < items.length();i++) {
                        JSONObject obj = items.getJSONObject(i);
                        add(new DeviceItem(RCApp.this, obj));
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }

        void saveToDB() {
            SharedPreferences sp = getSharedPreferences(BLE_PREF_DEVICE_DB, MODE_PRIVATE);
            SharedPreferences.Editor spEd = sp.edit();
            spEd.remove(BLE_PREF_DEVICE_LIST);
            JSONArray items = new JSONArray();
            try {
                for (int i = 0; i < size(); i++) {
                    JSONObject obj = new JSONObject(get(i).saveState());
                    items.put(obj);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
            spEd.putString(BLE_PREF_DEVICE_LIST, items.toString());
            spEd.apply();
        }

        void completeItem(DeviceItem aItem,
                                String deviceServerName,
                                String deviceBLEName,
                                String deviceBLEAddress) {
            for (int i = 0; i < size(); i++)
                if (aItem.tryToCompleteFrom(get(i),
                        deviceServerName,
                        deviceBLEName,
                        deviceBLEAddress))
                    return;

            aItem.complete(deviceServerName, deviceBLEName, deviceBLEAddress);
        }

        void completeItem(DeviceItem aItem,
                          String deviceServerName) {
            for (int i = 0; i < size(); i++)
                if (aItem.tryToCompleteFrom(get(i),
                        deviceServerName,
                        "",""))
                    return;

            aItem.complete(deviceServerName, "", "");
        }

        void completeItem(DeviceItem aItem,
                          String deviceBLEName,
                          String deviceBLEAddress) {
            for (int i = 0; i < size(); i++)
                if (aItem.tryToCompleteFrom(get(i),
                        "",
                        deviceBLEName,
                        deviceBLEAddress))
                    break;

            aItem.complete("", deviceBLEName, deviceBLEAddress);
        }

        void incItemRate(DeviceItem n_obj) {
            DeviceItem m_obj = findItem(n_obj);
            if (m_obj != null) {
                m_obj.incRate();
                mDeviceItemsChanged = true;
                updateDeviceItems();
            }
        }

        DeviceItem findItem(DeviceItem aobj) {
            for (DeviceItem obj : mDeviceItems) {
                if (obj.equals(aobj)) {
                    return obj;
                }
            }
            return null;
        }

        void saveItem(DeviceItem n_obj) {
            DeviceItem m_obj = findItem(n_obj);
            if (m_obj != null) {
                mDeviceItemsChanged |= m_obj.updateFromResult(n_obj);
            } else {
                mDeviceItems.add(n_obj);
                mDeviceItemsChanged = true;
            }
            updateDeviceItems();
        }

        void removeItem(DeviceItem n_obj) {
            DeviceItem r_obj = null;
            for (DeviceItem obj : mDeviceItems) {
                if (obj.equals(n_obj)) {
                    r_obj = obj;
                    break;
                }
            }
            if (r_obj != null) {
                mDeviceItemsChanged = true;
                mDeviceItems.remove(r_obj);
                updateDeviceItems();
            }
        }

        void updateDeviceItems() {
            if (mDeviceListUpdate == 0 && mDeviceItemsChanged) {
                mDeviceItemsChanged = false;
                Collections.sort(mDeviceItems);
                saveToDB();
            }
        }

        void beginUpdate() {
            mDeviceListUpdate++;
        }
        void endUpdate() {
            mDeviceListUpdate--;
            updateDeviceItems();
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

    public String getHttpCfgSID() {
        return getPref(PREF_HTTP_CFG_SID);
    }

    public  void setHttpCfgSID(String value) {
        setPref(PREF_HTTP_CFG_SID, value);
    }


    @Override
    public void onCreate() {
        super.onCreate();

        mDeviceItems = new DevicesHolderList();
    }
}
