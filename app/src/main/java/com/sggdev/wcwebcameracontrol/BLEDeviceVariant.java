package com.sggdev.wcwebcameracontrol;

public class BLEDeviceVariant {
    static final int BLE_DEVICE_VARIANT_DEVICE = 0;
    static final int BLE_DEVICE_VARIANT_CACHE_ITEM = 1;
    static final int BLE_DEVICE_VARIANT_SEPARATOR = 2;

    BLEDevice device;
    CachedItem item;
    int variant = BLE_DEVICE_VARIANT_SEPARATOR;

    BLEDeviceVariant() {
        device = null;
        item = null;
        variant = BLE_DEVICE_VARIANT_SEPARATOR;
    }

    BLEDeviceVariant(BLEDevice adevice) {
        device = adevice;
        item = null;
        variant = BLE_DEVICE_VARIANT_DEVICE;
    }

    BLEDeviceVariant(CachedItem acache_item) {
        device = null;
        item = acache_item;
        variant = BLE_DEVICE_VARIANT_CACHE_ITEM;
    }
}
