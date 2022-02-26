package com.sggdev.wcwebcameracontrol;

public class BabaikaWebCamNotiComm extends BabaikaNotiCommand {

    BabaikaWebCamNotiComm() {
        this("");
    }

    BabaikaWebCamNotiComm(String chuuid) {
        super("", "", "","", false);
        setNotification(new BabaikaWebCamNotif(chuuid));
        setOnValueChangedListener(new OnValueChangedListener() {
            @Override
            public void onChange() {
                String v = getNoti().getValue().trim();
                if (v.equals("value")) {
                    /* do something */
                }
            }
        });
    }

}
