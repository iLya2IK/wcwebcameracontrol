package com.sggdev.wcwebcameracontrol;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.json.*;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.BLUETOOTH;
import static android.Manifest.permission.BLUETOOTH_ADMIN;
import static android.Manifest.permission.INTERNET;
import static android.view.View.MeasureSpec.AT_MOST;
import static android.view.View.MeasureSpec.makeMeasureSpec;

import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.*;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class MainActivity extends AppCompatActivity {
    private static final int STATE_DEVICE_LISTING = 0;
    private static final int STATE_DEVICE_CONFIG = 1;
    private static final int STATE_DEVICE_CHAT = 2;
    private static final int STATE_MAIN_CONFIG = 3;
    private RCApp myApp;
    private RCApp.DevicesHolderList DevList() { return  myApp.mDeviceItems; }

    private DeviceVariantList mAvailableDevices;
    private DeviceAdapter mAvailableDevicesAdapter;
    private ListView mListView;
    private BluetoothAdapter bluetoothAdapter;
    private boolean READY_TO_BLE_SCAN = false;
    private boolean BLE_SCANNING = false;

    private static final int CS_USER_CFG_INCORRECT = -1;
    private static final int CS_DISCONNECTED = 0;
    private static final int CS_CONNECTING = 1;
    private static final int CS_CONNECTED = 2;
    private static final int CS_SERVER_SCANNING = 3;
    private int httpClientState = CS_DISCONNECTED;
    private int httpServerCooldown = 0;

    private int ACTIVITY_STATE = STATE_DEVICE_LISTING;
    private Timer syntimer;
    private TimerTask syntask;
    private AnimatorSet blinkanimation;
    private ImageView mBLEIcon;
    private ImageView mOpenConfig;
    private ImageView mUserConnect;
    private TextView mBLEText;
    private Handler handlerBLEScan;
    private SwipeRefreshLayout mSRL;

    private OkHttpClient httpClient;

    private static final int REQUEST_ID_MULTIPLE_PERMISSIONS = 1002;
    private boolean ENABLED_INTERNET_FEATURE = false;
    private boolean ENABLED_BLE_FEATURE = false;

    public static final MediaType JSONMedia
            = MediaType.get("application/json; charset=utf-8");

    private final ScanCallback leScanCallback =
            new ScanCallback () {
                @Override
                public void onScanResult(final int callbackType, final ScanResult result) {
                    super.onScanResult(callbackType, result);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            BluetoothDevice bledevice = result.getDevice();
                            if (bledevice != null) {
                                String dname = bledevice.getName();
                                if (dname != null && dname.startsWith(SampleGattAttributes.BLE_NAME_PREFIX)) {
                                    mAvailableDevices.add(bledevice);
                                    refreshAvailableDevicesAdapter();
                                }
                            }
                        }
                    });
                }

                @Override
                public void onBatchScanResults(final List<ScanResult> results) {
                    Log.d("MainActivity", "results : " + Integer.valueOf(results.size()).toString());
                }

                @Override
                public void onScanFailed(final int errorCode) {
                    Log.d("MainActivity", "error : " + Integer.valueOf(errorCode).toString());
                }
            };

    private class SynchroTask extends TimerTask {

        @TargetApi(23)
        void startBLEScaning() {
            if(Build.VERSION.SDK_INT >= 23) {
                ScanSettings scanSettings = new ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                        .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                        .build();
                bluetoothAdapter.getBluetoothLeScanner().startScan(null, scanSettings, leScanCallback);
            } else {
                bluetoothAdapter.getBluetoothLeScanner().startScan(leScanCallback);
            }
        }

        public void run() {
            DevList().beginUpdate();
            for (BLEDeviceVariant bd : mAvailableDevices)
                if (bd.isCompleteDevice())
                    DevList().saveItem(bd.item);
            DevList().endUpdate();

            /*mAvailableDevices.clear();
            for (DeviceItem ci : DevList()) {
                BLEDeviceVariant ad = new BLEDeviceVariant(ci);
                mAvailableDevices.add(ad);
            }*/
            refreshAvailableDevicesAdapter();

            httpServerCooldown -= 1000;
            if (httpServerCooldown < 0) httpServerCooldown = 0;

            if (ENABLED_INTERNET_FEATURE &&
                    (httpClientState == CS_DISCONNECTED) && (httpServerCooldown <= 0)) {
                try {
                    httpClientState = CS_CONNECTING;

                    JSONObject obj = new JSONObject();
                    obj.put(JSON_NAME, myApp.getHttpCfgUserName());
                    obj.put(JSON_PASS, myApp.getHttpCfgUserPsw());
                    obj.put(JSON_DEVICE, myApp.getHttpCfgDevice());

                    WCRESTTask wc_task = new WCRESTTask(httpClient);
                    wc_task.setOnJSONResponseListener(new OnJSONRequestFinished() {
                        @Override
                        public void onChange(int resultCode, JSONObject resultMsg) {
                            if (resultCode == REST_RESULT_OK)
                                connectUser(resultMsg.optString(JSON_SHASH));
                            else
                                consumeHTTPError(resultCode);
                        }
                    });
                    wc_task.execute(myApp.getHttpCfgServerUrl(),
                            WC_REST_authorize, obj.toString());
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            if (ENABLED_INTERNET_FEATURE && (httpClientState == CS_CONNECTED)) {
                try {
                    httpClientState = CS_SERVER_SCANNING;

                    JSONObject obj = new JSONObject();
                    obj.put(JSON_SHASH, myApp.getHttpCfgSID());

                    WCRESTTask wc_task = new WCRESTTask(httpClient);
                    wc_task.setOnJSONResponseListener(new OnJSONRequestFinished() {
                        @Override
                        public void onChange(int resultCode, JSONObject resultMsg) {
                            httpClientState = CS_CONNECTED;
                            if (resultCode == REST_RESULT_OK) {
                                Object res = resultMsg.opt(JSON_DEVICES);
                                if (res instanceof JSONArray)
                                    for (int i = 0; i < ((JSONArray) res).length(); i++) {
                                        Object aDevice = ((JSONArray) res).opt(i);
                                        if (aDevice instanceof String)
                                            mAvailableDevices.add((String) aDevice);
                                    }
                                refreshAvailableDevicesAdapter();
                            } else
                                consumeHTTPError(resultCode);
                        }
                    });
                    wc_task.execute(myApp.getHttpCfgServerUrl(),
                            WC_REST_getDevicesOnline, obj.toString());
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            if (ENABLED_BLE_FEATURE && READY_TO_BLE_SCAN && !BLE_SCANNING) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        //
                        mSRL.setRefreshing(false);
                        READY_TO_BLE_SCAN = false;

                        handlerBLEScan.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                cancelSearchBLE();
                            }
                        }, 10000);

                        BLE_SCANNING = true;

                        mBLEIcon.setVisibility(View.VISIBLE);
                        mBLEText.setVisibility(View.VISIBLE);
                        mBLEIcon.setAlpha(0f);
                        mBLEText.setText(R.string.searching_ble);
                        blinkanimation.start();

                        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);

                        for (BluetoothDevice bluetoothDevice : bluetoothManager.getConnectedDevices(BluetoothProfile.GATT))
                            if (bluetoothDevice.getName().startsWith(SampleGattAttributes.BLE_NAME_PREFIX))
                                mAvailableDevices.add(bluetoothDevice);

                        startBLEScaning();
                        refreshAvailableDevicesAdapter();
                    }

                });
            }
        }
    }

    private void refreshAvailableDevicesAdapter() {
        Collections.sort(mAvailableDevices);
        runOnUiThread (new Thread(new Runnable() {
            public void run() {
                mAvailableDevicesAdapter.notifyDataSetChanged();
            }
        }));
    }

    private static class WCRESTTask extends AsyncTask<String, Void, String> {

        private final OkHttpClient client;
        private OnJSONRequestFinished onfinish;

        WCRESTTask(OkHttpClient aClient) {
            client = aClient;
        }

        protected String doInBackground(String... params) {
            try {
                RequestBody body = RequestBody.create(params[2], JSONMedia);
                Request request = new Request.Builder()
                        .url(params[0] + params[1])
                        .post(body)
                        .build();
                try (Response response = client.newCall(request).execute()) {
                    return response.body().string();
                }
            } catch (Exception e) {
                return e.toString();
            }
        }

        void setOnJSONResponseListener(OnJSONRequestFinished rf) {
            onfinish = rf;
        }

        protected void onPostExecute(String resp) {
            if (onfinish != null) {
                int code;
                try {
                    JSONObject jsonObj = new JSONObject(resp);
                    String res = jsonObj.optString(JSON_RESULT, "");
                    if (res.length() > 0) {
                        if (res.equals(JSON_OK)) {
                            code = REST_RESULT_OK;
                        } else {
                            code = jsonObj.optInt(JSON_CODE, REST_ERR_UNSPECIFIED);
                        }
                        onfinish.onChange(code, jsonObj);
                    } else {
                        onfinish.onChange(REST_ERR_UNSPECIFIED, null);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                    onfinish.onChange(REST_ERR_NETWORK, null);
                }
            }
        }
    }

    private void restartAsyncTimer() {
        syntask  = new SynchroTask();
        syntimer = new Timer();
    }

    private void cancelSearchBLE() {
        if (BLE_SCANNING) {
            bluetoothAdapter.getBluetoothLeScanner().stopScan(leScanCallback);
            BLE_SCANNING = false;
            mBLEIcon.setVisibility(View.GONE);
            int cnt = mAvailableDevicesAdapter.getCount();
            if (cnt == 0) {
                mBLEText.setText(R.string.ble_not_found);
            } else {
                mBLEText.setVisibility(View.GONE);
            }
        }
    }

    private boolean mListMod = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        myApp = (RCApp) getApplication();

        myApp.setHttpCfgDevice("android_" + Build.MODEL);

        this.requestWindowFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.activity_main);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        handlerBLEScan = new Handler();

        mAvailableDevices = new DeviceVariantList();
        mAvailableDevicesAdapter = new DeviceAdapter(this);

        mListView = findViewById(R.id.list_view);
        mListView.setAdapter(mAvailableDevicesAdapter);

        mOpenConfig = findViewById(R.id.img_open_config);
        mUserConnect = findViewById(R.id.img_con_indicator);

        mSRL = findViewById(R.id.drawer_layout);

        mBLEIcon = findViewById(R.id.image_ble);
        mBLEText = findViewById(R.id.text_ble);
        mBLEText.setVisibility(View.GONE);

        blinkanimation = (AnimatorSet) AnimatorInflater.loadAnimator(this, R.animator.blinker);
        blinkanimation.setTarget(mBLEIcon);
        blinkanimation.addListener(new AnimatorListenerAdapter() {

            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                blinkanimation.start();
            }

        });

        mSRL.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                if (!BLE_SCANNING) {
                    allowToBLEScan();
                } else {
                    mSRL.setRefreshing(false);
                }
            }
        });

        mListView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                mListMod = false;
                Object o = mListView.getItemAtPosition(position);
                BLEDeviceVariant aitem = (BLEDeviceVariant) o;
                if (aitem.isBLEDevice()) {
                    mAvailableDevices.remove(position);
                    refreshAvailableDevicesAdapter();
                    mListMod = true;
                }
                return false;
            }
        });

        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (mListMod) {
                    mListMod = false;
                    return;
                }
                Object o = mListView.getItemAtPosition(position);
                BLEDeviceVariant aitem = (BLEDeviceVariant) o;
                switch (aitem.variant) {
                    case BLEDeviceVariant.DEVICE_VARIANT_DEVICE:
                    case BLEDeviceVariant.DEVICE_VARIANT_SERVER_ITEM: {
                        DeviceItem adevice = aitem.item;
                        launchDeviceChat(adevice);
                        break;
                    }
                    case BLEDeviceVariant.DEVICE_VARIANT_BLE_ITEM: {
                        final DeviceItem adevice = aitem.item;
                        if (!adevice.isBLEConnected())
                            launchDeviceConfig(adevice);
                        break;
                    }
                }
            }
        });


        mListView.setOnScrollListener(new AbsListView.OnScrollListener() {
            private int curState = SCROLL_STATE_IDLE;

            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                curState = scrollState;
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                if (curState != SCROLL_STATE_IDLE) {
                    mListView.invalidateViews();
                }
            }
        });

        mOpenConfig.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launchMainConfig();
            }
        });
    }

    private void launchMainConfig() {
        final Intent intent = new Intent(MainActivity.this, MainConfigActivity.class);
        cancelSearchBLE();
        ACTIVITY_STATE = STATE_MAIN_CONFIG;
        stopTimer();
        startActivityForResult(intent, REQUEST_MAIN_CONFIG_MODE);
    }

    private void launchDeviceConfig(DeviceItem adevice) {
        /*final Intent intent = new Intent(MainActivity.this, DeviceConfigActivity.class);
        intent.putExtra(DeviceConfigActivity.EXTRAS_DEVICE_NAME, adevice.getDeviceBLEName());
        intent.putExtra(DeviceConfigActivity.EXTRAS_DEVICE_ADDRESS, adevice.getDeviceBLEAddress());
        intent.putExtra(DeviceConfigActivity.EXTRAS_DEVICE_WRITE_ID, adevice.getDeviceWriteChar());
        intent.putExtra(DeviceConfigActivity.EXTRAS_DEVICE_COLOR, adevice.getDeviceColor());
        intent.putExtra(DeviceConfigActivity.EXTRAS_DEVICE_INDEX, adevice.getDeviceIndex());*/
        cancelSearchBLE();
        ACTIVITY_STATE = STATE_DEVICE_CONFIG;
        adevice.BLEConnect();
        stopTimer();
        //startActivityForResult(intent, REQUEST_DEVICE_CONFIG_MODE);
    }

    private void launchDeviceChat(DeviceItem adevice) {
        /*final Intent intent = new Intent(MainActivity.this, DeviceControlActivity.class);
        intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_NAME, device.getName());
        intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_ADDRESS, device.getAddress());
        intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_WRITE_ID, adevice.getDeviceWriteChar());
        intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_COLOR, adevice.getDeviceColor());
        intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_INDEX, adevice.getDeviceIndex());*/
        cancelSearchBLE();
        ACTIVITY_STATE = STATE_DEVICE_CHAT;
        adevice.BLEConnect();
        stopTimer();
        //startActivityForResult(intent, REQUEST_DEVICE_CHAT_MODE);
    }

    final static int REQUEST_ENABLE_BT = 0x009333;
    final static int REQUEST_DEVICE_CONFIG_MODE = 0x009334;
    final static int REQUEST_DEVICE_CHAT_MODE = 0x009335;
    final static int REQUEST_MAIN_CONFIG_MODE = 0x009336;

    protected void onResume() {
        super.onResume();

        checkPermissions();
        // Ensures Bluetooth is available on the device and it is enabled. If not,
        // displays a dialog requesting user permission to enable Bluetooth.
        if (ACTIVITY_STATE == STATE_DEVICE_LISTING) {
            restartScanMode();
        }
    }

    private void restartScanMode() {
        ACTIVITY_STATE = STATE_DEVICE_LISTING;

        if (ENABLED_BLE_FEATURE) {
            if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            } else {
                allowToBLEScan();
            }
        }

        //This will be called after onCreate or your activity is resumed
        if (syntimer == null) {
            restartAsyncTimer();
            syntimer.schedule(syntask, 500, 1000);
        }
    }

    private void stopTimer() {
        handlerBLEScan.removeCallbacksAndMessages(null);
        cancelSearchBLE();

        if (syntask != null)
            syntask.cancel();
        if (syntimer != null) {
            syntimer.cancel();
            syntimer.purge();
        }

        syntimer = null;
        syntask = null;
    }

    @Override
    protected void onPause() {
        super.onPause();

        DevList().saveToDB();

        //This will be called if the app is sent to background or the phone is locked
        //Also this prevent you from duplicating the instance of your timer
        stopTimer();
    }

    @Override
    protected void onStop() {
        //thread stop here
        stopTimer();
        super.onStop();
    }

    private void allowToBLEScan() {
        READY_TO_BLE_SCAN = true;
    }

    private void denyToBLEScan() {
        READY_TO_BLE_SCAN = false;
    }

    private void connectUser(String sid) {
        httpClientState = CS_CONNECTED;
        myApp.setHttpCfgSID(sid);
        runOnUiThread (new Thread(new Runnable() {
            public void run() {
                mUserConnect.setImageDrawable(getDrawable(R.drawable.connected));
            }
        }));
    }

    private void consumeHTTPError(int code) {
        switch (code) {
            case REST_ERR_NO_SUCH_SESSION:
            case REST_ERR_DATABASE_FAIL:
            case REST_ERR_JSON_FAIL:
            case REST_ERR_INTERNAL_UNK:
                disconnectUser(code);
            case REST_ERR_NO_SUCH_USER:
            case REST_ERR_NETWORK:
                alertWrongUser(code);
        }
    }

    private void disconnectUser(int code) {
        httpClientState = CS_DISCONNECTED;
        httpServerCooldown = 3500;
        myApp.setHttpCfgSID("");
        runOnUiThread (new Thread(new Runnable() {
            public void run() {
                mUserConnect.setImageDrawable(getDrawable(R.drawable.disconnected));
                Toast toast = Toast.makeText(getApplicationContext(),
                        String.format(getString(R.string.rest_error), code, REST_RESPONSE_ERRORS[code]),
                        Toast.LENGTH_LONG);
                toast.show();
            }
        }));
    }

    private void alertWrongUser(int code) {
        disconnectUser(code);
        httpClientState = CS_USER_CFG_INCORRECT;
        runOnUiThread (new Thread(new Runnable() {
            public void run() {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(R.string.alert_refused_connection)
                        .setMessage(R.string.alert_edit_config)
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                launchMainConfig();
                            }
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .show();
            }
        }));
    }

    protected void onActivityResult (int requestCode,
                                     int resultCode,
                                     Intent data) {
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                //do ok
                allowToBLEScan();
            } else {
                //something wrong
                denyToBLEScan();
            }
        } else
        if (requestCode == REQUEST_DEVICE_CONFIG_MODE) {
            restartScanMode();

            if (data != null) {
                    /*String deviceAddress = data.getStringExtra(DeviceControlActivity.EXTRAS_DEVICE_ADDRESS);
                    final BluetoothManager bluetoothManager =
                            (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
                    List<BluetoothDevice> bles = bluetoothManager.getConnectedDevices(BluetoothProfile.GATT);
                    for (BluetoothDevice bluetoothDevice : bles) {
                        if (bluetoothDevice.getAddress().compareTo(deviceAddress) == 0) {
                            Log.d("MainActivity", deviceAddress + " still connected ");
                        }
                    }*/
            }

        } else
        if (requestCode == REQUEST_DEVICE_CHAT_MODE) {
            restartScanMode();

            if (data != null) {
                    /*String deviceAddress = data.getStringExtra(DeviceControlActivity.EXTRAS_DEVICE_ADDRESS);
                    final BluetoothManager bluetoothManager =
                            (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
                    List<BluetoothDevice> bles = bluetoothManager.getConnectedDevices(BluetoothProfile.GATT);
                    for (BluetoothDevice bluetoothDevice : bles) {
                        if (bluetoothDevice.getAddress().compareTo(deviceAddress) == 0) {
                            Log.d("MainActivity", deviceAddress + " still connected ");
                        }
                    }*/
            }

        } else
        if (requestCode == REQUEST_MAIN_CONFIG_MODE) {
            boolean connectDataChanged = false;
            if (data != null) {
               connectDataChanged = data.getBooleanExtra(MainConfigActivity.EXTRAS_USR_CFG_CHANGED, false);
            }
            // trying to reset the scan mode. hope the user gave the correct config
            if (connectDataChanged)
                httpClientState = CS_DISCONNECTED;
            restartScanMode();
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private class DeviceAdapter extends ArrayAdapter<BLEDeviceVariant> {

        public DeviceAdapter(Context context) {
            super(context, R.layout.two_line_listitem_ico, mAvailableDevices);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            BLEDeviceVariant ble;
            try {
                ble = getItem(position);
            } catch (IndexOutOfBoundsException e) {
                if (convertView == null) {
                    convertView = LayoutInflater.from(getContext())
                            .inflate(R.layout.separator_item, null);
                }
                return convertView;
            }

            if (ble.variant == BLEDeviceVariant.DEVICE_VARIANT_SEPARATOR) {
                if (convertView == null) {
                    convertView = LayoutInflater.from(getContext())
                            .inflate(R.layout.separator_item, null);
                } else {
                    TextView name_tv_ = convertView.findViewById(R.id.text1);
                    if (name_tv_ != null) {
                        convertView = LayoutInflater.from(getContext())
                                .inflate(R.layout.separator_item, null);
                    }
                }
                return convertView;
            }

            boolean new_View = false;
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext())
                        .inflate(R.layout.two_line_listitem_ico, null);
                new_View = true;
            } else {
                TextView name_tv_ = convertView.findViewById(R.id.text1);
                if (name_tv_ == null)
                    convertView = LayoutInflater.from(getContext())
                            .inflate(R.layout.two_line_listitem_ico, null);
            }
            String aName = "", aText2 = "";
            Drawable aDevicePic = null, aSecondaryPic = null, aPresencePic = null;

            switch (ble.variant) {
                case BLEDeviceVariant.DEVICE_VARIANT_BLE_ITEM: {
                    aName = ble.item.getDeviceBLEName();
                    aText2 = ble.item.getDeviceBLEAddress();
                    aDevicePic = getDrawable(ble.item.getDevicePictureID());
                    aSecondaryPic = new DeviceIconID(ble.item.getDeviceItemColor(), ble.item.getDeviceItemIndex());
                    break;
                }
                case BLEDeviceVariant.DEVICE_VARIANT_DEVICE:
                case BLEDeviceVariant.DEVICE_VARIANT_SERVER_ITEM: {
                    int resID = 0;

                    aName = ble.item.getDeviceServerName();
                    if (aName == null || aName.length() == 0)
                        aName = ble.item.getDeviceBLEAddress();
                    if (aName == null) aName = "";
                    aText2 = ble.item.getDeviceBLEName();
                    if (aText2 == null) aText2 = "";
                    if (aText2.length() > 0)
                        resID = getApplicationContext().getResources().
                                getIdentifier(aText2, "string",
                                        getApplicationContext().getPackageName());

                    if (resID != 0)
                        aText2 = getString(resID);
                    else
                        aText2 = "";

                    resID =  ble.item.getDevicePictureID();
                    if (resID != 0) {
                        aDevicePic = getDrawable(resID);
                        aSecondaryPic = new DeviceIconID(ble.item.getDeviceItemColor(),
                                ble.item.getDeviceItemIndex());
                    }

                    if (ble.item.isOnline())
                        aPresencePic = getDrawable(android.R.drawable.presence_online);
                    else
                        aPresencePic = getDrawable(android.R.drawable.presence_offline);

                    break;
                }
            }

            TextView name_tv = (TextView) convertView.findViewById(R.id.text1);
            TextView mac_tv = (TextView) convertView.findViewById(R.id.text2);

            ImageView img = convertView.findViewById(R.id.icon);
            ImageView img_id = convertView.findViewById(R.id.icon_id);
            ImageView img_presence = convertView.findViewById(R.id.icon_presence);
            ImageView img_cfg = convertView.findViewById(R.id.icon_cfg);

            if (new_View) {
                int pW = parent.getWidth();
                name_tv.measure(makeMeasureSpec(pW, AT_MOST), makeMeasureSpec(0xffff, AT_MOST));
                mac_tv.measure(makeMeasureSpec(pW, AT_MOST), makeMeasureSpec(0xffff, AT_MOST));
                int tw = name_tv.getMeasuredWidth();
                int mw = mac_tv.getMeasuredWidth();
                int iw = img.getLayoutParams().width;
                int icw = img_cfg.getLayoutParams().width;
                pW -= (int) ((float) (iw + icw) * 1.24f);

                if (tw > pW) {
                    name_tv.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                            name_tv.getTextSize() * (float) pW / (float) tw);
                }
                if (mw > pW) {
                    mac_tv.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                            mac_tv.getTextSize() * (float) pW / (float) mw);
                }
            }

            name_tv.setText(aName);
            mac_tv.setText(aText2);

            img.setImageDrawable(aDevicePic);
            img_id.setImageDrawable(aSecondaryPic);

            if (aPresencePic != null && img_presence != null)
                img_presence.setImageDrawable(aPresencePic);

            if (!ble.isSeparator() && img_cfg != null) {
                if (ble.item.isBLEAvaible())
                    img_cfg.setVisibility(View.VISIBLE);
                else
                    img_cfg.setVisibility(View.GONE);
            }

            Display disp = ((WindowManager)getSystemService(WINDOW_SERVICE)).getDefaultDisplay();
            int flags = disp.getFlags();
            DisplayMetrics dmetr = new DisplayMetrics();
            disp.getMetrics(dmetr);
            float ratio = (float)dmetr.heightPixels / (float)dmetr.widthPixels;
            if (((flags & Display.FLAG_ROUND) != 0) || (Math.abs(ratio - 1f) < 0.1f)) {
                float centerOffset = ((float) convertView.getHeight() / 2.0f) / (float) parent.getHeight();
                float yRelativeToCenterOffset = (convertView.getY() / parent.getHeight()) + centerOffset;

                float progressToCenter;
                // Normalize for center
                progressToCenter = Math.abs(0.5f - yRelativeToCenterOffset);
                // Adjust to the maximum scale
                progressToCenter = Math.min(progressToCenter, 0.65f);

                convertView.setScaleX(1f - progressToCenter);
                convertView.setScaleY(1f - progressToCenter);
            }

            return convertView;
        }
    }

    private void checkPermissions() {
        String [] permissions = new String [4];
        permissions[0] = ACCESS_FINE_LOCATION;
        permissions[1] = INTERNET;
        permissions[2] = BLUETOOTH;
        permissions[3] = BLUETOOTH_ADMIN;

        List<String> listPermissionsNeeded = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) !=
                    PackageManager.PERMISSION_GRANTED) {
                listPermissionsNeeded.add(permission);
            }
        }
        if (!listPermissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, (String [])
                    listPermissionsNeeded.toArray(new String[listPermissionsNeeded.size()]),
                    REQUEST_ID_MULTIPLE_PERMISSIONS);
        } else {
            setInternetFeatures(true);
            setBLEFeatures(true);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_ID_MULTIPLE_PERMISSIONS) {
            boolean inetEnable = true;
            boolean bleEnable = true;
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    switch (permissions[i]) {
                        case ACCESS_FINE_LOCATION:
                            bleEnable = false;
                            inetEnable = false;
                            break;
                        case INTERNET:
                            inetEnable = false;
                            break;
                        case BLUETOOTH:
                        case BLUETOOTH_ADMIN:
                            bleEnable = false;
                            break;
                    }
                }
            }
            setInternetFeatures(inetEnable);
            setBLEFeatures(bleEnable);
        }
    }

    private static OkHttpClient getUnsafeOkHttpClient() {
        try {
            // Create a trust manager that does not validate certificate chains
            final TrustManager[] trustAllCerts = new TrustManager[] {
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
                        }

                        @Override
                        public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
                        }

                        @Override
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return new java.security.cert.X509Certificate[]{};
                        }
                    }
            };

            // Install the all-trusting trust manager
            final SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            // Create an ssl socket factory with our all-trusting manager
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            OkHttpClient.Builder builder = new OkHttpClient.Builder();
            builder.sslSocketFactory(sslSocketFactory, (X509TrustManager)trustAllCerts[0]);
            builder.hostnameVerifier(new HostnameVerifier() {
                @Override
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            });

            OkHttpClient okHttpClient = builder.build();
            return okHttpClient;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setInternetFeatures(boolean value) {
        ENABLED_INTERNET_FEATURE = value;
        if (value) {
            if (httpClient == null) httpClient = getUnsafeOkHttpClient();
        } else {
            httpClient = null;
            httpClientState = CS_DISCONNECTED;
        }
    }

    private void setBLEFeatures(boolean value) {
        ENABLED_BLE_FEATURE = value;
        if (!value)
            READY_TO_BLE_SCAN = false;
    }


    class DeviceVariantList extends ArrayList<BLEDeviceVariant> {

        private boolean hasServerDevices = false;
        private boolean hasBLEDevices = false;

        @Override
        public void clear() {
            hasServerDevices = false;
            hasBLEDevices = false;
            super.clear();
        }

        @Override
        public boolean add(BLEDeviceVariant item) {
            if (item.isServerCompleteDevice())
                hasServerDevices = true;
            else
            if (item.isBLEDevice())
            {
                if (!hasBLEDevices && hasServerDevices)
                    add(new BLEDeviceVariant());
                hasBLEDevices = true;
            }
            return super.add(item);
        }

        public boolean add(String item) {
            for (BLEDeviceVariant bd : mAvailableDevices)
                if (bd.isServerCompleteDevice())
                    if (bd.item.getDeviceServerName().equals(item)) {
                        bd.item.setOnline(true);
                        return true;
                    }
            DeviceItem di = new DeviceItem(getApplicationContext());
            di.setOnline(true);
            DevList().completeItem(di, item);
            return add(new BLEDeviceVariant(di));
        }


        public boolean add(BluetoothDevice bleDevice) {
            String bleaddress = bleDevice.getAddress();
            for (BLEDeviceVariant bd : mAvailableDevices)
                if (bd.isBLECompleteDevice())
                    if (bd.item.getDeviceBLEAddress().equals(bleaddress)) {
                        bd.item.setBLEDevice(bleDevice);
                        bd.update();
                        return true;
                    }
            DeviceItem di = new DeviceItem(getApplicationContext());
            di.setBLEDevice(bleDevice);
            DevList().completeItem(di, bleDevice.getName(), bleDevice.getAddress());
            return add(new BLEDeviceVariant(di));
        }

        public boolean add(String srvName, String bleAddr, String bleName) {
            for (BLEDeviceVariant bd : mAvailableDevices)
            {
                switch (bd.variant) {
                    case BLEDeviceVariant.DEVICE_VARIANT_BLE_ITEM:{
                        if (bd.item.getDeviceBLEAddress().equals(bleAddr)) {
                            DevList().completeItem(bd.item, srvName);
                            bd.update();
                            return true;
                        }
                    }
                    case BLEDeviceVariant.DEVICE_VARIANT_DEVICE:{
                        if (bd.item.getDeviceServerName().equals(srvName)) {
                            DevList().completeItem(bd.item, bleName, bleAddr);
                            bd.update();
                            return true;
                        }
                    }
                }
            }
            DeviceItem di = new DeviceItem(getApplicationContext());
            DevList().completeItem(di, srvName, bleName, bleAddr);
            return add(new BLEDeviceVariant(di));
        }
    }
}
