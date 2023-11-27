package com.sggdev.wcsdk;

import static com.sggdev.wcsdk.SampleGattAttributes.BT_WEBCAM_NOTI_CHAR1;

public class BabaikaWebCam extends BabaikaBLEDevice {
    static String uuid = "7d75752d-e135-4d45-9add-155b5f02a9ac";
    static String BT_WEBCAM_ICO = "ic_webcam_device";

    BabaikaWebCam() {
        putCommNotification(new BabaikaWebCamConfig(BT_WEBCAM_NOTI_CHAR1));
    }

    String getPictureName() {
        return BT_WEBCAM_ICO;
    }
}