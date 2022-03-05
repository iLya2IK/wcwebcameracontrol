package com.sggdev.wcwebcameracontrol;

import java.util.HashSet;
import java.util.Set;

public class BLEDeviceVariant implements Comparable<BLEDeviceVariant> {
    static final int DEVICE_VARIANT_BLE_ITEM    = 8;
    static final int DEVICE_VARIANT_SEPARATOR   = 4;
    static final int DEVICE_VARIANT_DEVICE      = 2;
    static final int DEVICE_VARIANT_SERVER_ITEM = 1;

    static final int COMPLETE_BLE_DEVICES = DEVICE_VARIANT_DEVICE | DEVICE_VARIANT_BLE_ITEM;
    static final int COMPLETE_SERVER_DEVICES = DEVICE_VARIANT_DEVICE | DEVICE_VARIANT_SERVER_ITEM;

    DeviceItem item;
    int variant = DEVICE_VARIANT_SEPARATOR;

    BLEDeviceVariant() {
        item = null;
        variant = DEVICE_VARIANT_SEPARATOR;
    }

    BLEDeviceVariant(DeviceItem adevice_item) {
        item = adevice_item;
        update();
    }

    public boolean isCompleteDevice() {
        return (variant == DEVICE_VARIANT_DEVICE);
    }

    public boolean isBLEDevice() {
        return (variant == DEVICE_VARIANT_BLE_ITEM);
    }

    public boolean isServerDevice() {
        return (variant == COMPLETE_SERVER_DEVICES);
    }

    public boolean isBLECompleteDevice() {
        return ((variant & COMPLETE_BLE_DEVICES) > 0);
    }

    public boolean isServerCompleteDevice() {
        return ((variant & COMPLETE_SERVER_DEVICES) > 0);
    }

    public boolean isSeparator() {
        return (variant == DEVICE_VARIANT_SEPARATOR);
    }

    public void update() {
        if (item.hasServerName()) {
            if (item.hasBLEAddress()) {
                variant = DEVICE_VARIANT_DEVICE;
            } else
                variant = DEVICE_VARIANT_SERVER_ITEM;
        } else {
            variant = DEVICE_VARIANT_BLE_ITEM;
        }
    }

    @Override
    public int compareTo(BLEDeviceVariant o) {
        if (variant != o.variant) {
            return (int) (variant - o.variant);
        } else {
            if ((item != null) && (o.item != null)) {
                return (int) (o.item.getRate() - item.getRate());
            } else
                return 0;
        }
    }
}
