package com.sggdev.wcsdk;

import static com.sggdev.wcsdk.SampleGattAttributes.BT_RELAY_NOTI_CHAR1;

public class BabaikaRelay extends BabaikaBLEDevice {
    static String uuid = "7ae23150-8218-11ee-b962-0242ac120002";
    static String BT_WEBCAM_ICO = "ic_webcam_device";

    BabaikaRelay() {
        putCommNotification(new BabaikaCommonConfig(BT_RELAY_NOTI_CHAR1));
    }

    String getPictureName() {
        return BT_WEBCAM_ICO;
    }
}