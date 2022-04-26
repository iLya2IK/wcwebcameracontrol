package com.sggdev.wcwebcameracontrol;

import android.os.Bundle;
import android.text.InputType;

import androidx.preference.EditTextPreference;
import androidx.preference.PreferenceFragmentCompat;

public class MainSettingsFragment extends PreferenceFragmentCompat {

    private final WCApp myApp;

    private boolean mUsrCfgChanged = false;

    MainSettingsFragment(WCApp aApp) {
        myApp = aApp;
    }

    boolean usrCfgChanged() {
        return mUsrCfgChanged;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey);

        mUsrCfgChanged = false;

        final EditTextPreference preference1 = findPreference(WCApp.PREF_HTTP_CFG_URL);

        if (preference1 != null) {
            preference1.setSummaryProvider(preference -> {

                String getValue = myApp.getHttpCfgServerUrl();
                if (getValue == null) getValue = "";
                //return "not set" else return password with asterisks
                if (getValue.length() == 0) {
                    return "not set";
                } else
                    return getValue;
            });

            preference1.setOnPreferenceChangeListener((preference, newValue) -> {
                if (newValue != null && newValue.toString().length() > 0) {
                    myApp.setHttpCfgServerUrl(newValue.toString());
                    mUsrCfgChanged = true;
                    return true;
                } else
                    return false;
            });
        }

        final EditTextPreference preference2 = findPreference(WCApp.PREF_HTTP_CFG_USER);

        if (preference2 != null) {
            preference2.setSummaryProvider(preference -> {

                String getValue = myApp.getHttpCfgUserName();
                if (getValue == null) getValue = "";
                //return "not set" else return password with asterisks
                if (getValue.length() == 0) {
                    return "not set";
                } else
                    return getValue;
            });

            preference2.setOnPreferenceChangeListener((preference, newValue) -> {
                if (newValue != null && newValue.toString().length() > 0) {
                    myApp.setHttpCfgUserName(newValue.toString());
                    mUsrCfgChanged = true;
                    return true;
                } else
                    return false;
            });
        }

        final EditTextPreference preference3 = findPreference(WCApp.PREF_HTTP_CFG_PSW);

        if (preference3 != null) {
            preference3.setSummaryProvider(preference -> {

                String getPassword = myApp.getHttpCfgUserPsw();
                if (getPassword == null) getPassword = "";

                //return "not set" else return password with asterisks
                if (getPassword.length() == 0) {
                    return "not set";
                } else
                    return (setAsterisks(getPassword.length()));
            });

            preference3.setOnPreferenceChangeListener((preference, newValue) -> {
                if (newValue != null && newValue.toString().length() > 0) {
                    myApp.setHttpCfgUserPsw(newValue.toString());
                    mUsrCfgChanged = true;
                    return true;
                } else
                    return false;
            });

            //set input type as password and set summary with asterisks the new password
            preference3.setOnBindEditTextListener(
                    editText -> {
                        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                        preference3.setSummaryProvider(preference -> setAsterisks(editText.getText().toString().length()));
                    });
        }
    }

    //return the password in asterisks
    private String setAsterisks(int length) {
        StringBuilder sb = new StringBuilder();
        for (int s = 0; s < length; s++) {
            sb.append("*");
        }
        return sb.toString();
    }
}