package com.sggdev.wcwebcameracontrol;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.sql.Timestamp;
import java.util.ArrayList;

import static android.content.Context.MODE_PRIVATE;

public class DeviceItem implements Comparable<DeviceItem> {
    private static final String BLE_PREF_CACHED_DB = "db";
    private static final String BLE_PREF_CACHED_LIST = "CachedList";
    private static final String CACHE_ITEM_DEVICE_SERVER_NAME = "device_server_name";
    private static final String CACHE_ITEM_DEVICE_BLE_NAME = "device_name";
    private static final String CACHE_ITEM_DEVICE_BLE_ADDRESS = "device_address";
    private static final String CACHE_ITEM_RATE = "rate";
    private static final String CACHE_ITEM_TIME_STAMP = "timestamp";
    private static final String CACHE_ITEMS = "items";

    private BLEDevice fDevice;
    private Context mContext;
    private Timestamp mTimeStamp;
    private long mRate;

    DeviceItem(Context aContext, BluetoothDevice device, long aRate) {
        mContext = aContext;
        fDevice = new BLEDevice(aContext, device);
        mRate = aRate;
        synchronizeTime();
    }

    DeviceItem(Context aContext, String aDeviceName, String aDeviceAddress, long aRate) {
        mContext = aContext;
        fDevice = new BLEDevice(aContext, aDeviceName, aDeviceAddress);
        mRate = aRate;
        synchronizeTime();
    }

    DeviceItem(Context aContext, JSONObject cached_item) {
        mContext = aContext;
        restoreState(cached_item);
        synchronizeTime();
    }

    DeviceItem(Context aContext, String obj) {
        mContext = aContext;
        restoreState(obj);
        synchronizeTime();
    }

    String saveState() {
        try {
            JSONObject cached_item = new JSONObject();
            cached_item.put(CACHE_ITEM_DEVICE_SERVER_NAME, fDevice.getServerName());
            cached_item.put(CACHE_ITEM_DEVICE_BLE_NAME, fDevice.getBLEName());
            cached_item.put(CACHE_ITEM_DEVICE_BLE_ADDRESS, fDevice.getBLEAddress());
            cached_item.put(CACHE_ITEM_RATE, mRate);
            cached_item.put(CACHE_ITEM_TIME_STAMP, mTimeStamp.toString());
            return cached_item.toString();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return "";
    }

    void restoreState(String obj) {
        try {
            JSONObject cached_item = new JSONObject(obj);
            restoreState(cached_item);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    void restoreState(JSONObject cached_item) {
        try {
            String aDeviceName = cached_item.getString(CACHE_ITEM_DEVICE_BLE_NAME);
            String aDeviceAddress = cached_item.getString(CACHE_ITEM_DEVICE_BLE_ADDRESS);
            String aServerName = cached_item.getString(CACHE_ITEM_DEVICE_SERVER_NAME);
            mRate = cached_item.getLong(CACHE_ITEM_RATE);
            mTimeStamp = Timestamp.valueOf(cached_item.getString(CACHE_ITEM_TIME_STAMP));

            fDevice = new BLEDevice(mContext, aServerName, aDeviceName, aDeviceAddress);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public boolean equals(DeviceItem other) {
        return other.getDeviceBLEAddress().equals(fDevice.getBLEAddress());
    }

    static ArrayList<DeviceItem> loadFromDB(Context aContext) {
        ArrayList<DeviceItem> resList = new ArrayList<>();
        SharedPreferences sp = aContext.getSharedPreferences(BLE_PREF_CACHED_DB, MODE_PRIVATE);
        String res = sp.getString(BLE_PREF_CACHED_LIST, null);
        if (res != null) {
            try {
                JSONObject cached_list = new JSONObject(res);
                JSONArray items = cached_list.getJSONArray(CACHE_ITEMS);
                for (int i = 0; i < items.length();i++) {
                    JSONObject obj = items.getJSONObject(i);
                    resList.add(new DeviceItem(aContext, obj));
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return resList;
    }

    static void saveToDB(Context aContext, ArrayList<DeviceItem> cachedList) {
        SharedPreferences sp = aContext.getSharedPreferences(BLE_PREF_CACHED_DB, MODE_PRIVATE);
        SharedPreferences.Editor spEd = sp.edit();
        spEd.remove(BLE_PREF_CACHED_LIST);
        JSONObject cached_list = new JSONObject();
        JSONArray items = new JSONArray();
        try {
            for (int i = 0; i < cachedList.size(); i++) {
                JSONObject obj = new JSONObject(cachedList.get(i).saveState());
                items.put(obj);
            }
            cached_list.put(CACHE_ITEMS, items);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        spEd.putString(BLE_PREF_CACHED_LIST, cached_list.toString());
        spEd.apply();
    }


    String getDeviceBLEAddress() {return fDevice.getBLEAddress();}
    String getDeviceServerName() {return fDevice.getServerName();}
    String getDeviceWriteChar() {return fDevice.getDeviceWriteChar();}
    BLEDevice getDevice() {return  fDevice;}
    String getDeviceBLEName() {return  fDevice.getBLEName();}
    Timestamp getTimeStamp() { return mTimeStamp; }
    long getRate() {return mRate;}
    void incRate() {
        long time = System.currentTimeMillis();
        long last = mTimeStamp.getTime();
        if ((time - last) > 5000) {
            mRate++;
            synchronizeTime();
        }
    }
    void decRate() {
        if (mRate > 0) mRate--;
        synchronizeTime();
    }
    void synchronizeTime() {
        mTimeStamp = new Timestamp(System.currentTimeMillis());
    }

    @Override
    public int compareTo(DeviceItem o) {
        return (int) (o.getRate() - mRate);
    }

}
