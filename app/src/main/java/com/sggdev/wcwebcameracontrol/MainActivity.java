package com.sggdev.wcwebcameracontrol;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.json.*;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.ACCESS_WIFI_STATE;
import static android.Manifest.permission.INTERNET;

public class MainActivity extends AppCompatActivity {

    private Button btn;
    private TextView tv;
    private OkHttpClient client;

    private static int REQUEST_ID_MULTIPLE_PERMISSIONS = 1002;
    private boolean ENABLED_INTERNET_FEATURE = false;

    public static final MediaType JSON
            = MediaType.get("application/json; charset=utf-8");

    private void checkPermissions() {
        String [] permissions = new String [3];
        permissions[0] = ACCESS_WIFI_STATE;
        permissions[1] = ACCESS_FINE_LOCATION;
        permissions[2] = INTERNET;

        List<String> listPermissionsNeeded = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) !=
                    PackageManager.PERMISSION_GRANTED) {
                listPermissionsNeeded.add(permission);
            }
        }
        if (!listPermissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, (String [])
                    listPermissionsNeeded.toArray(), REQUEST_ID_MULTIPLE_PERMISSIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_ID_MULTIPLE_PERMISSIONS) {
            // Forward results to EasyPermissions
            for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    disableInternetFeatures();
                    return;
                }
            }
            enableInternetFeatures();
        }
    }

    private void disableInternetFeatures() {
        ENABLED_INTERNET_FEATURE = false;
    }

    private void enableInternetFeatures() {
        ENABLED_INTERNET_FEATURE = true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        /*
        // for testing

        btn = (Button) findViewById(R.id.button2);
        tv = (TextView)  findViewById(R.id.textView);

        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (ENABLED_INTERNET_FEATURE) {
                    client = new OkHttpClient();
                    try {
                        JSONObject obj = new JSONObject();
                        obj.put("name", "user");
                        obj.put("pass", "psw");
                        obj.put("device", "android");

                        new RetrieveFeedTask().execute("https://loclahost:8080/authorize.json",
                                obj.toString());
                    } catch (JSONException e) {
                        tv.setText(e.getMessage());
                    }
                } else {
                    checkPermissions();
                }
            }
        });  */

    }

    class RetrieveFeedTask extends AsyncTask<String, Void, String> {

        private Exception exception;

        protected String doInBackground(String... params) {
            try {
                RequestBody body = RequestBody.create(params[1], JSON);
                Request request = new Request.Builder()
                        .url(params[0])
                        .post(body)
                        .build();
                try (Response response = client.newCall(request).execute()) {
                    return response.body().string();
                }
            } catch (Exception e) {
                this.exception = e;

                return e.toString();
            } finally {
            }
        }

        protected void onPostExecute(String resp) {
            tv.setText(resp);
        }
    }
}
