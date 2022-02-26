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

public class CachedItem implements Comparable<CachedItem> {
    private static String BLE_PREF_CACHED_DB = "db";
    private static String BLE_PREF_CACHED_LIST = "CachedList";
    private static String CACHE_ITEM_DEVICE_NAME = "device_name";
    private static String CACHE_ITEM_DEVICE_ADDRESS = "device_address";
    private static String CACHE_ITEM_RATE = "rate";
    private static String CACHE_ITEM_TIME_STAMP = "timestamp";
    private static String CACHE_ITEMS = "items";

    private BLEDevice fDevice;
    private Context mContext;
    private Timestamp mTimeStamp;
    private long mRate;

    CachedItem(Context aContext, BluetoothDevice device, BabaikaCommand cmd, long aRate) {
        mContext = aContext;
        fDevice = new BLEDevice(aContext, device);
        mRate = aRate;
        synchronizeTime();
    }

    CachedItem(Context aContext, String aDeviceName, String aDeviceAddress, BabaikaCommand cmd, long aRate) {
        mContext = aContext;
        fDevice = new BLEDevice(aContext, aDeviceName, aDeviceAddress);
        mRate = aRate;
        synchronizeTime();
    }

    CachedItem(Context aContext, JSONObject cached_item) {
        mContext = aContext;
        restoreState(cached_item);
        synchronizeTime();
    }

    CachedItem(Context aContext, String obj) {
        mContext = aContext;
        restoreState(obj);
        synchronizeTime();
    }

    String saveState() {
        try {
            JSONObject cached_item = new JSONObject();
            cached_item.put(CACHE_ITEM_DEVICE_NAME, fDevice.getName());
            cached_item.put(CACHE_ITEM_DEVICE_ADDRESS, fDevice.getAddress());
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
            String aDeviceName = cached_item.getString(CACHE_ITEM_DEVICE_NAME);
            String aDeviceAddress = cached_item.getString(CACHE_ITEM_DEVICE_ADDRESS);
            mRate = cached_item.getLong(CACHE_ITEM_RATE);
            mTimeStamp = Timestamp.valueOf(cached_item.getString(CACHE_ITEM_TIME_STAMP));

            fDevice = new BLEDevice(mContext, aDeviceName, aDeviceAddress);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public boolean equals(CachedItem other) {
        return other.getDeviceAddress().equals(fDevice.getAddress());
    }

    static ArrayList<CachedItem> loadFromDB(Context aContext) {
        ArrayList<CachedItem> resList = new ArrayList<>();
        SharedPreferences sp = aContext.getSharedPreferences(BLE_PREF_CACHED_DB, MODE_PRIVATE);
        String res = sp.getString(BLE_PREF_CACHED_LIST, null);
        if (res != null) {
            try {
                JSONObject cached_list = new JSONObject(res);
                JSONArray items = cached_list.getJSONArray(CACHE_ITEMS);
                for (int i = 0; i < items.length();i++) {
                    JSONObject obj = items.getJSONObject(i);
                    resList.add(new CachedItem(aContext, obj));
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return resList;
    }

    static void saveToDB(Context aContext, ArrayList<CachedItem> cachedList) {
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


    String getDeviceAddress() {return fDevice.getAddress();}
    String getDeviceWriteChar() {return fDevice.getDeviceWriteChar();}
    BLEDevice getDevice() {return  fDevice;}
    String getDeviceName() {return  fDevice.getName();}
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
    public int compareTo(CachedItem o) {
        return (int) (o.getRate() - mRate);
    }

}
