package com.sggdev.wcsdk;

import static com.sggdev.wcsdk.SampleGattAttributes.BT_RADIO_NOTI_CHAR1;

public class BabaikaRadio extends BabaikaBLEDevice {
    static String uuid = "42ca7fb6-8218-11ee-b962-0242ac120002";
    static String BT_WEBCAM_ICO = "ic_webcam_device";

    BabaikaRadio() {
        putCommNotification(new BabaikaCommonConfig(BT_RADIO_NOTI_CHAR1));
    }

    String getPictureName() {
        return BT_WEBCAM_ICO;
    }
}