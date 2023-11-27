package com.sggdev.wcsdk;

import android.bluetooth.BluetoothDevice;
import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

public class DeviceItem implements Comparable<DeviceItem> {
    public static final int DEFAULT_DEVICE_COLOR = 0xffaaaaaa;

    public static final String DEVICE_ITEM_DB_ID = "did";
    public static final String DEVICE_ITEM_USER_NAME = "user_name";
    public static final String DEVICE_ITEM_SERVER_NAME = "server_name";
    public static final String DEVICE_ITEM_BLE_NAME = "ble_name";
    public static final String DEVICE_ITEM_BLE_ADDRESS = "ble_address";
    public static final String DEVICE_ITEM_CHAR = "ble_char";
    public static final String DEVICE_ITEM_COLOR = "color";
    public static final String DEVICE_ITEM_META = "meta";
    public static final String DEVICE_ITEM_INDEX = "ind";
    public static final String DEVICE_ITEM_TIME_STAMP = "timestamp";
    public static final String DEVICE_ITEM_UNREAD_MSGS = "unread";

    private final Context mContext;
    private BluetoothDevice device = null;
    private boolean mIsBLEConnected = false;
    private boolean mIsOnline = false;
    private String bleName = "";
    private String bleAddress = "";
    private String mUserName = "";
    private String mServerName = "";
    private String mDeviceWriteChar = "";
    private String mDevicePicture = "";
    private int mDevicePictureID = 0;
    private int mDeviceColor = DEFAULT_DEVICE_COLOR;
    private int mDeviceIndex = 0;
    private long mDBID = -1;
    private JSONObject mMeta = new JSONObject();
    private String mLastSync = "";
    private int mUnReadedMsgs = 0;

    public DeviceItem(Context aContext, String aUserName) {
        mContext = aContext;
        mUserName = aUserName;
    }

    public DeviceItem(Context aContext, JSONObject obj) {
        mContext = aContext;
        restoreState(obj.toString());
    }

    public DeviceItem(Context aContext, DeviceItem obj) {
        mContext = aContext;
        restoreState(obj.saveState());
    }

    boolean isSame(DeviceItem it) {
        return ((device == it.device) &&
                (mUserName.equals(it.mUserName)) &&
                (mServerName.equals(it.mServerName))&&
                (bleAddress.equals(it.bleAddress))&&
                (bleName.equals(it.bleName))&&
                (mDeviceIndex == it.mDeviceIndex)&&
                (mDeviceColor == it.mDeviceColor)&&
                (mDevicePicture.equals(it.mDevicePicture))&&
                (mDevicePictureID == it.mDevicePictureID)&&
                (mDeviceWriteChar.equals(it.mDeviceWriteChar))&&
                (mUnReadedMsgs == it.mUnReadedMsgs)&&
                (mIsOnline == it.mIsOnline));
    }

    boolean updateFromResult(DeviceItem adevice) {
        if (this == adevice) return false;
        boolean cmpRes = !isSame(adevice);
        if (cmpRes) {
            restoreState(adevice.saveState());

            device = adevice.device;
            mIsOnline = adevice.isOnline();
        }
        return cmpRes;
    }

    public void complete(String deviceServerName,
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

    public void complete(String deviceServerName) {
        if (deviceServerName.length() > 0)
            mServerName = deviceServerName;
        updateSecondaryValues();
    }

    public void completeWithMeta(String meta) {
        if (meta.length() > 0) {
            setMeta(meta);
            if (mMeta.length() > 0) {
                String deviceBLEName = mMeta.optString(DEVICE_ITEM_BLE_NAME, "");
                if (deviceBLEName.length() > 0)
                    bleName = deviceBLEName;
                String writeAttr = mMeta.optString(DEVICE_ITEM_CHAR, "");
                if (writeAttr.length() > 0)
                    mDeviceWriteChar = writeAttr;
            }
            updateSecondaryValues();
        }
    }

    public void setMeta(String meta) {
        try {
            mMeta = new JSONObject(meta);
        } catch (JSONException e) {
            mMeta = new JSONObject();
        }
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
                if (an_device.mMeta.length() > 0) {
                    setMeta(an_device.mMeta.toString());
                }
                mDBID = an_device.mDBID;
                mDeviceWriteChar = an_device.mDeviceWriteChar;
                mDeviceColor = an_device.mDeviceColor;
                mDeviceIndex = an_device.mDeviceIndex;
                mLastSync = an_device.mLastSync;
                mUnReadedMsgs = an_device.mUnReadedMsgs;
                device = an_device.device;
                mIsOnline = an_device.isOnline();
                mUserName = an_device.mUserName;
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

    public String saveState() {
        try {
            JSONObject cached_item = new JSONObject();
            cached_item.put(DEVICE_ITEM_DB_ID, mDBID);
            cached_item.put(DEVICE_ITEM_USER_NAME, mUserName);
            cached_item.put(DEVICE_ITEM_SERVER_NAME, mServerName);
            cached_item.put(DEVICE_ITEM_BLE_NAME, bleName);
            cached_item.put(DEVICE_ITEM_BLE_ADDRESS, bleAddress);
            cached_item.put(DEVICE_ITEM_CHAR, mDeviceWriteChar);
            cached_item.put(DEVICE_ITEM_COLOR, mDeviceColor);
            cached_item.put(DEVICE_ITEM_INDEX, mDeviceIndex);
            cached_item.put(DEVICE_ITEM_TIME_STAMP, mLastSync);
            cached_item.put(DEVICE_ITEM_META, mMeta.toString());
            cached_item.put(DEVICE_ITEM_UNREAD_MSGS, mUnReadedMsgs);
            return cached_item.toString();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return "";
    }

    public void restoreState(String obj) {
        try {
            JSONObject cached_item = new JSONObject(obj);
            restoreState(cached_item);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void restoreState(JSONObject cached_item) {
        try {
            mDBID =  cached_item.getInt(DEVICE_ITEM_DB_ID);
            bleName = cached_item.getString(DEVICE_ITEM_BLE_NAME);
            bleAddress = cached_item.getString(DEVICE_ITEM_BLE_ADDRESS);
            mUserName = cached_item.getString(DEVICE_ITEM_USER_NAME);
            mServerName = cached_item.getString(DEVICE_ITEM_SERVER_NAME);
            mDeviceWriteChar = cached_item.getString(DEVICE_ITEM_CHAR);
            mDeviceIndex = cached_item.getInt(DEVICE_ITEM_INDEX);
            mDeviceColor = cached_item.getInt(DEVICE_ITEM_COLOR);
            mLastSync = cached_item.getString(DEVICE_ITEM_TIME_STAMP);
            mUnReadedMsgs = cached_item.getInt(DEVICE_ITEM_UNREAD_MSGS);
            setMeta(cached_item.getString(DEVICE_ITEM_META));

            updateSecondaryValues();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public boolean equals(DeviceItem other) {
        if (mUserName.length() > 0)
        {
            if (!other.getDeviceUserName().equals(mUserName))
                return false;
        }
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
        if (mContext != null)
            mDevicePictureID = defineDevicePictureID(mContext, mDevicePicture);
    }

    void setProps(int aColor, int aIndex) {
        mDeviceColor = aColor;
        mDeviceIndex = aIndex;
    }

    void setSyncProps(String aLstSync, int aCnt) {
        mLastSync = aLstSync;
        mUnReadedMsgs = aCnt;
    }

    public static int defineDevicePictureID(Context aContext, String aPicName) {
        int resID;
        if (aPicName != null) {
            resID = aContext.getResources().getIdentifier(aPicName, "drawable", aContext.getPackageName());
            if (resID == 0) {
                resID = R.drawable.ic_default_device;
            }
        } else resID = R.drawable.ic_default_device;
        return resID;
    }

    public String getDeviceBLEAddress() {return bleAddress;}
    public String getDeviceServerName() {return mServerName;}
    public String getDeviceUserName() {return mUserName;}
    public String getDeviceWriteChar() {return mDeviceWriteChar;}
    public int getDeviceItemColor() {return mDeviceColor;}
    public int getDeviceItemIndex() {return mDeviceIndex;}
    public String getDeviceBLEName() {return  bleName;}
    public int getDevicePictureID() { return mDevicePictureID;  }
    public long getDbId() { return  mDBID; }
    public String getLstSync() { return mLastSync; }
    public JSONObject getMeta() {return mMeta; }

    public boolean isBLEAvaible() {return (device != null);}
    public boolean isBLEConnected() {return mIsBLEConnected;}
    public boolean isOnline() {return mIsOnline;}
    public void BLEConnect() {mIsBLEConnected = true;}
    public void BLEDisconnect() {mIsBLEConnected = false;}
    public void setOnline(boolean state) {mIsOnline = state;}
    public void setBLEDevice(BluetoothDevice dev) {device = dev;}
    public void setDbId(long dbId) { mDBID = dbId; }
    public void setLstSync(String aLstSync) { mLastSync = aLstSync; }

    public int getUnreadedMsgs() { return mUnReadedMsgs; }
    public void setUnreadedMsgs(int amount) { mUnReadedMsgs = amount; }

    @Override
    public int compareTo(DeviceItem o) {
        return (int) (mLastSync.compareTo(o.mLastSync));
    }

    boolean hasBLEAddress() {
        return (bleAddress.length() > 0);
    }

    boolean hasServerName() {
        return (mServerName.length() > 0);
    }

}
