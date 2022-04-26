package com.sggdev.wcwebcameracontrol;

import static com.sggdev.wcwebcameracontrol.BabaikaConfigNotif.BabaikaConfigItem.CFG_OPT_PASSWORD;
import static com.sggdev.wcwebcameracontrol.BabaikaConfigNotif.BabaikaConfigItem.CFG_OPT_READONLY;

public class BabaikaWebCamConfig extends BabaikaConfigNotif {

    final static String KEY_USER = "u";
    final static String KEY_PASS = "p";
    final static String KEY_HOST = "h";
    final static String KEY_DEVICE = "d";
    final static String KEY_SSID = "s";
    final static String KEY_SSID_PASS = "k";

    BabaikaWebCamConfig() {
        this("");
    }

    BabaikaWebCamConfig(String chuuid) {
        super(chuuid);
        addField(KEY_USER, "ic_user", "username_title", "");
        BabaikaConfigItem it = addField(KEY_PASS, "dev_cfg_password", "password_title", "***");
        it.setOption(CFG_OPT_PASSWORD);
        addField(KEY_HOST, "ic_host", "hostname_title", "https://");
        it = addField(KEY_DEVICE, "ic_default_device", "device_name_title", "");
        it.setOption(CFG_OPT_READONLY);
        it = addField(KEY_SSID, "ic_cfg_wifi_ssid", "ssid_title", "");
        it = addField(KEY_SSID_PASS, "dev_cfg_password", "ssid_password_title", "***");
        it.setOption(CFG_OPT_PASSWORD);
    }
}
