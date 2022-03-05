package com.sggdev.wcwebcameracontrol;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import static android.content.Context.MODE_PRIVATE;

public class DeviceItem implements Comparable<DeviceItem> {
    public static final int DEFAULT_DEVICE_COLOR = 0xffaaaaaa;

    private static final String DEVICE_ITEM_SERVER_NAME = "server_name";
    private static final String DEVICE_ITEM_BLE_NAME = "ble_name";
    private static final String DEVICE_ITEM_BLE_ADDRESS = "ble_address";
    private static final String DEVICE_ITEM_CHAR = "char";
    private static final String DEVICE_ITEM_COLOR = "color";
    private static final String DEVICE_ITEM_INDEX = "index";
    private static final String DEVICE_ITEM_RATE = "rate";
    private static final String DEVICE_ITEM_TIME_STAMP = "timestamp";

    private Timestamp mTimeStamp;
    private long mRate;

    private Context mContext;
    private BluetoothDevice device = null;
    private boolean mIsBLEConnected = false;
    private boolean mIsOnline = false;
    private String bleName = "";
    private String bleAddress = "";
    private String mServerName = "";
    private String mDeviceWriteChar = "";
    private String mDevicePicture = "";
    private int mDevicePictureID;
    private int mDeviceColor = DEFAULT_DEVICE_COLOR;
    private int mDeviceIndex = 0;    

    DeviceItem(Context aContext) {
        mContext = aContext;
        synchronizeTime();
    }

    DeviceItem(Context aContext, JSONObject obj) {
        mContext = aContext;
        restoreState(obj.toString());
        synchronizeTime();
    }

    boolean isSame(DeviceItem it) {
        return ((device == it.device) &&
                (mServerName.equals(it.mServerName))&&
                (bleAddress.equals(it.bleAddress))&&
                (bleName.equals(it.bleName))&&
                (mRate == it.mRate)&&
                (mDeviceIndex == it.mDeviceIndex)&&
                (mDeviceColor == it.mDeviceColor)&&
                (mDevicePicture.equals(it.mDevicePicture))&&
                (mDevicePictureID == it.mDevicePictureID)&&
                (mDeviceWriteChar.equals(it.mDeviceWriteChar))&&
                (mIsOnline == it.mIsOnline));
    }

    boolean updateFromResult(DeviceItem adevice) {
        if (this == adevice) return false;
        boolean cmpRes = !isSame(adevice);
        if (cmpRes) {
            restoreState(adevice.saveState());

            device = adevice.device;
            mIsOnline = adevice.isOnline();
            synchronizeTime();
        }
        return cmpRes;
    }

    void complete(String deviceServerName,
                  String deviceBLEName,
                  String deviceBLEAddress) {
        if (deviceServerName.length() > 0)
            mServerName = deviceServerName;
        if (deviceBLEAddress.length() > 0)
            bleAddress = deviceBLEAddress;
        if (deviceBLEName.length() > 0)
            bleName = deviceBLEName;
        updateSecondaryValues();
    }

    boolean tryToCompleteFrom(DeviceItem an_device,
                               String deviceServerName,
                               String deviceBLEName,
                               String deviceBLEAddress) {
        if ((an_device.bleAddress.equals(deviceBLEAddress) && (deviceBLEAddress.length() > 0)) ||
                (an_device.mServerName.equals(deviceServerName) && (deviceServerName.length() > 0))) {
            if (deviceServerName.length() > 0)
                mServerName = deviceServerName;
            if (deviceBLEAddress.length() > 0)
                bleAddress = deviceBLEAddress;
            if (deviceBLEName.length() > 0)
                bleName = deviceBLEName;
            if (this != an_device) {
                mDeviceWriteChar = an_device.mDeviceWriteChar;
                mDeviceColor = an_device.mDeviceColor;
                mDeviceIndex = an_device.mDeviceIndex;
                mRate = an_device.mRate;
                device = an_device.device;
                mIsOnline = an_device.isOnline();
                if (deviceServerName.length() == 0)
                    mServerName = an_device.mServerName;
                if (deviceBLEAddress.length() == 0)
                    bleAddress = an_device.bleAddress;
                if (deviceBLEName.length() == 0)
                    bleName = an_device.bleName;
                updateSecondaryValues();
            }
            return true;
        }
        return false;
    }

    String saveState() {
        try {
            JSONObject cached_item = new JSONObject();
            cached_item.put(DEVICE_ITEM_SERVER_NAME, mServerName);
            cached_item.put(DEVICE_ITEM_BLE_NAME, bleName);
            cached_item.put(DEVICE_ITEM_BLE_ADDRESS, bleAddress);
            cached_item.put(DEVICE_ITEM_CHAR, mDeviceWriteChar);
            cached_item.put(DEVICE_ITEM_COLOR, mDeviceColor);
            cached_item.put(DEVICE_ITEM_INDEX, mDeviceIndex);
            cached_item.put(DEVICE_ITEM_RATE, mRate);
            cached_item.put(DEVICE_ITEM_TIME_STAMP, mTimeStamp.toString());
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
            bleName = cached_item.getString(DEVICE_ITEM_BLE_NAME);
            bleAddress = cached_item.getString(DEVICE_ITEM_BLE_ADDRESS);
            mServerName = cached_item.getString(DEVICE_ITEM_SERVER_NAME);
            mDeviceWriteChar = cached_item.getString(DEVICE_ITEM_CHAR);
            mDeviceIndex = cached_item.getInt(DEVICE_ITEM_INDEX);
            mDeviceColor = cached_item.getInt(DEVICE_ITEM_COLOR);
            mRate = cached_item.getLong(DEVICE_ITEM_RATE);

            updateSecondaryValues();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public boolean equals(DeviceItem other) {
        if ((other.getDeviceBLEAddress().length() > 0) &&
            (bleAddress.length() > 0)) {
            return other.getDeviceBLEAddress().equals(bleAddress);
        } else
        if (mServerName.length() > 0)
        {
            return other.getDeviceServerName().equals(mServerName);
        }
        return false;
    }

    void updateSecondaryValues() {
        mDevicePicture = SampleGattAttributes.getCharPicture(mDeviceWriteChar);
        mDevicePictureID = defineDevicePictureID(mContext, mDevicePicture);
    }

    static int defineDevicePictureID(Context aContext, String aPicName) {
        int resID;
        if (aPicName != null) {
            resID = aContext.getResources().getIdentifier(aPicName, "drawable", aContext.getPackageName());
            if (resID == 0) {
                resID = R.drawable.ic_ble_device;
            }
        } else resID = R.drawable.ic_ble_device;
        return resID;
    }

    BluetoothDevice getDevice() {return device;}
    String getDeviceBLEAddress() {return bleAddress;}
    String getDeviceServerName() {return mServerName;}
    String getDeviceWriteChar() {return mDeviceWriteChar;}
    int getDeviceItemColor() {return mDeviceColor;}
    int getDeviceItemIndex() {return mDeviceIndex;}
    String getDeviceBLEName() {return  bleName;}
    Timestamp getTimeStamp() { return mTimeStamp; }
    int getDevicePictureID() {
        return mDevicePictureID;
    }

    boolean isBLEAvaible() {return (device != null);}
    boolean isBLEConnected() {return mIsBLEConnected;}
    boolean isOnline() {return mIsOnline;}
    void BLEConnect() {mIsBLEConnected = true;}
    void BLEDisconnect() {mIsBLEConnected = false;}
    void setOnline(boolean state) {mIsOnline = state;}
    void setBLEDevice(BluetoothDevice dev) {device = dev;}

    
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

    boolean hasBLEAddress() {
        return (bleAddress.length() > 0);
    }

    boolean hasServerName() {
        return (mServerName.length() > 0);
    }

}
