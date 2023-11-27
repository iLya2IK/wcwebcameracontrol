package com.sggdev.wcsdk;

public class BLEDeviceVariant implements Comparable<BLEDeviceVariant> {
    public static final int DEVICE_VARIANT_BLE_ITEM    = 8;
    public static final int DEVICE_VARIANT_SEPARATOR   = 4;
    public static final int DEVICE_VARIANT_DEVICE      = 2;
    public static final int DEVICE_VARIANT_SERVER_ITEM = 1;

    public static final int COMPLETE_BLE_DEVICES = DEVICE_VARIANT_DEVICE | DEVICE_VARIANT_BLE_ITEM;
    public static final int COMPLETE_SERVER_DEVICES = DEVICE_VARIANT_DEVICE | DEVICE_VARIANT_SERVER_ITEM;

    DeviceItem item;
    int variant = DEVICE_VARIANT_SEPARATOR;

    public BLEDeviceVariant() {
        item = null;
        variant = DEVICE_VARIANT_SEPARATOR;
    }

    public BLEDeviceVariant(DeviceItem adevice_item) {
        item = adevice_item;
        update();
    }

    public DeviceItem getItem() {return item;}
    public int getVariant() {return variant;}

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
                return (int) (o.item.compareTo(item));
            } else
                return 0;
        }
    }
}
