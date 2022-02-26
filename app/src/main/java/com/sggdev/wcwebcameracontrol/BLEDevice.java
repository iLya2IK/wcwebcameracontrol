package com.sggdev.wcwebcameracontrol;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import static android.content.Context.MODE_PRIVATE;

class BLEDevice {
    public static final int DEFAULT_DEVICE_COLOR = 0xffaaaaaa;
    private static String BLE_PREF_DEVICES_DB = "db";
    private static String BLE_PREF_DEVICE_CHAR = "mDeviceChar";
    private static String BLE_PREF_DEVICE_COLOR = "mDeviceColor";
    private static String BLE_PREF_DEVICE_INDEX = "mDeviceIndex";

    private String name;
    private String address;
    private BluetoothDevice device;
    private Context mContext;
    private boolean state;
    private String mDeviceWriteChar;
    private String mDevicePicture;
    private int mDevicePictureID;
    private int mDeviceColor = DEFAULT_DEVICE_COLOR;
    private int mDeviceIndex = 0;


    BLEDevice(Context aContext, BluetoothDevice device) {
        this(aContext);
        updateFromResult(device);
    }

    BLEDevice(Context aContext, String deviceName, String deviceAddress) {
        this(aContext);
        updateFromResult(deviceName, deviceAddress);
    }

    BLEDevice(Context aContext) {
        device = null;
        state = false;
        mContext = aContext;
        mDevicePicture = "";
        mDeviceWriteChar = "";
    }

    static String addressNameToKey(String address) {
        return"device_" + address.replace(':', '_');
    }

    void updateFromResult(BluetoothDevice adevice) {
        device = adevice;
        name = device.getName();
        address = device.getAddress();
        updateFromResult(name, address);
    }

    void refresh() {
        updateFromResult(name, address);
    }

    void updateFromResult(String deviceName, String deviceAddress) {
        name = deviceName;
        address = deviceAddress;
        SharedPreferences sp = mContext.getSharedPreferences(BLE_PREF_DEVICES_DB, MODE_PRIVATE);
        String res = sp.getString(addressNameToKey(address), null);
        if (res != null) {
            try {
                JSONObject ble_device = new JSONObject(res);
                mDeviceWriteChar  = ble_device.getString(BLE_PREF_DEVICE_CHAR);
                mDeviceColor = ble_device.getInt(BLE_PREF_DEVICE_COLOR);
                mDeviceIndex = ble_device.getInt(BLE_PREF_DEVICE_INDEX);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        updateSecondaryValues();
    }

    void updateSecondaryValues() {
        mDevicePicture = SampleGattAttributes.getCharPicture(mDeviceWriteChar);
        mDevicePictureID = defineDevicePictureID(mContext, mDevicePicture);
    }

    int getDevicePictureID() {
        return mDevicePictureID;
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

    static void rebuildBLEDevicePreferences(Context aContext, String address, String aChar, int aColor, int aIndex) throws JSONException {
        SharedPreferences sp = aContext.getSharedPreferences(BLE_PREF_DEVICES_DB, MODE_PRIVATE);
        SharedPreferences.Editor spEd = sp.edit();
        String key_v = addressNameToKey(address);
        spEd.remove(key_v);
        JSONObject ble_device = new JSONObject();
        ble_device.put(BLE_PREF_DEVICE_CHAR, aChar);
        ble_device.put(BLE_PREF_DEVICE_COLOR, aColor);
        ble_device.put(BLE_PREF_DEVICE_INDEX, aIndex);
        spEd.putString(key_v, ble_device.toString());
        spEd.commit();
    }

    String getName() {
        return name;
    }

    String getDeviceWriteChar() {return mDeviceWriteChar;}

    int getDeviceColor() {return mDeviceColor;}

    int getDeviceIndex() {return mDeviceIndex;}

    String getAddress(){
        return address;
    }

    BluetoothDevice getDevice() {
        return device;
    }

    boolean isConnected() {return state;}

    void Connect() {state = true;}
}
