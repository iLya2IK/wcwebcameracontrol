package com.sggdev.wcwebcameracontrol;

import static com.sggdev.wcwebcameracontrol.BabaikaConfigNotif.BabaikaConfigItem.CFG_OPT_PASSWORD;
import static com.sggdev.wcwebcameracontrol.BabaikaConfigNotif.BabaikaConfigItem.CFG_OPT_READONLY;

public class BabaikaWebCamConfig extends BabaikaConfigNotif {

    final static String KEY_USER = "u";
    final static String KEY_PASS = "p";
    final static String KEY_HOST = "h";
    final static String KEY_DEVICE = "d";

    BabaikaWebCamConfig() {
        this("");
    }

    BabaikaWebCamConfig(String chuuid) {
        super(chuuid);
        addField(KEY_USER, "dev_cfg_username", "username_title", "");
        BabaikaConfigItem it = addField(KEY_PASS, "dev_cfg_password", "password_title", "***");
        it.setOption(CFG_OPT_PASSWORD);
        addField(KEY_HOST, "dev_cfg_host", "hostname_title", "https://");
        it = addField(KEY_DEVICE, "dev_cfg_device_name", "device_name_title", "");
        it.setOption(CFG_OPT_READONLY);
    }
}
