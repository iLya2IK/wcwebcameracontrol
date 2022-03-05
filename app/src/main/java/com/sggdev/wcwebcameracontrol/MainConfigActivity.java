package com.sggdev.wcwebcameracontrol;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceFragmentCompat;

public class MainConfigActivity extends AppCompatActivity {

    final static String EXTRAS_USR_CFG_CHANGED = "EXTRAS_USR_CFG_CHANGED";
    MainSettingsFragment fragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);
        fragment = new MainSettingsFragment((RCApp) getApplication());
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings, fragment)
                    .commit();
        }
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    private void toResult() {
        Intent intent = new Intent();
        intent.putExtra(EXTRAS_USR_CFG_CHANGED, fragment.usrCfgChanged());

        setResult(RESULT_OK, intent);
        finish();
    }

    @Override
    protected void onPause() {
        super.onPause();

        toResult();
    }

    @Override
    public void onBackPressed()
    {
        super.onBackPressed();

        toResult();
    }
}