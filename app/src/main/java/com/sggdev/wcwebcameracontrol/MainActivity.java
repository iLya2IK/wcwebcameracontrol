package com.sggdev.wcwebcameracontrol;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.ReentrantLock;

import org.json.*;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.BLUETOOTH;
import static android.Manifest.permission.BLUETOOTH_ADMIN;
import static android.Manifest.permission.INTERNET;
import static android.view.View.MeasureSpec.AT_MOST;
import static android.view.View.MeasureSpec.makeMeasureSpec;

import static com.sggdev.wcwebcameracontrol.BLEDeviceVariant.DEVICE_VARIANT_BLE_ITEM;
import static com.sggdev.wcwebcameracontrol.BLEDeviceVariant.DEVICE_VARIANT_DEVICE;
import static com.sggdev.wcwebcameracontrol.BLEDeviceVariant.DEVICE_VARIANT_SERVER_ITEM;
import static com.sggdev.wcwebcameracontrol.DeviceItem.DEFAULT_DEVICE_COLOR;
import static com.sggdev.wcwebcameracontrol.IntentConsts.EXTRAS_DEVICE_ADDRESS;
import static com.sggdev.wcwebcameracontrol.IntentConsts.EXTRAS_DEVICE_BLE_NAME;
import static com.sggdev.wcwebcameracontrol.IntentConsts.EXTRAS_DEVICE_COLOR;
import static com.sggdev.wcwebcameracontrol.IntentConsts.EXTRAS_DEVICE_HOST_NAME;
import static com.sggdev.wcwebcameracontrol.IntentConsts.EXTRAS_DEVICE_INDEX;
import static com.sggdev.wcwebcameracontrol.IntentConsts.EXTRAS_DEVICE_LST_SYNC;
import static com.sggdev.wcwebcameracontrol.IntentConsts.EXTRAS_DEVICE_WRITE_ID;
import static com.sggdev.wcwebcameracontrol.IntentConsts.EXTRAS_TARGET_DEVICE_ID;
import static com.sggdev.wcwebcameracontrol.IntentConsts.EXTRAS_USER_DEVICE_ID;
import static com.sggdev.wcwebcameracontrol.WCHTTPClient.CS_CONNECTED;
import static com.sggdev.wcwebcameracontrol.WCHTTPClient.CS_DISCONNECTED;
import static com.sggdev.wcwebcameracontrol.WCHTTPClient.CS_DISCONNECTED_BY_USER;
import static com.sggdev.wcwebcameracontrol.WCHTTPClient.CS_DISCONNECTED_RETRY_OVER;
import static com.sggdev.wcwebcameracontrol.WCHTTPClient.CS_USER_CFG_INCORRECT;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.*;

public class MainActivity extends AppCompatActivity {
    private static final int STATE_DEVICE_LISTING = 0;
    private static final int STATE_DEVICE_CONFIG = 1;
    private static final int STATE_DEVICE_CHAT = 2;
    private static final int STATE_MAIN_CONFIG = 3;
    private WCApp myApp;
    private WCApp.DevicesHolderList DevList() { return  myApp.mDeviceItems; }

    public static final String ACTION_LAUNCH_CHAT = "ACTION_LAUNCH_CHAT";
    public static final String ACTION_LAUNCH_CONFIG = "ACTION_LAUNCH_CONFIG";

    private static final int DEVICE_SYNC_PERIOD = 500;
    private static final int DEVICE_LIST_REFRESH_PERIOD = 2000;
    private static final int DEVICE_SERVER_REQUEST_PERIOD = 5000;
    private static final int DEVICE_SERVER_CONNECT_COOLDOWN = 3500;
    private static final int DEVICE_BLE_SCAN_TIMEOUT = 10000;
    private static final int DEVICE_DB_REFRESH_TIMEOUT = 10000;

    private DeviceVariantList mAvailableDevices;
    private DeviceAdapter mAvailableDevicesAdapter;
    private ListView mListView;
    private BluetoothAdapter bluetoothAdapter;
    private static final int BS_SHUTDOWN = 0;
    private static final int BS_SLEEP = 1;
    private static final int BS_READY = 2;
    private static final int BS_SCANNING = 3;
    private int bleScannerState = BS_SHUTDOWN;

    private int httpServerCooldown = 0;
    private int dbCooldown = 0;

    private int ACTIVITY_STATE = STATE_DEVICE_LISTING;
    private Timer syntimer;
    private SynchroTask syntask;
    private IdleCheckTask idletask1;
    private IdleDeviceUpdateTask idletask2;
    private AnimatorSet blinkanimation;
    private ImageView mBLEIcon;
    private ImageButton mUserConnect;
    private TextView mUserName;
    private TextView mBLEText;
    private Handler handlerBLEScan;
    private SwipeRefreshLayout mSRL;

    private static final int REQUEST_ID_MULTIPLE_PERMISSIONS = 1002;
    private boolean ENABLED_INTERNET_FEATURE = false;
    private boolean ENABLED_BLE_FEATURE = false;
    private boolean ENABLED_ALARM_FEATURE = false;

    private final ScanCallback leScanCallback =
            new ScanCallback () {
                @Override
                public void onScanResult(final int callbackType, final ScanResult result) {
                    super.onScanResult(callbackType, result);
                    runOnUiThread(() -> {
                        BluetoothDevice bledevice = result.getDevice();
                        if (bledevice != null) {
                            String dname = bledevice.getName();
                            if (dname != null && dname.startsWith(SampleGattAttributes.BLE_NAME_PREFIX)) {
                                mAvailableDevices.add(bledevice);
                                doRefreshAvailableDevices();
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

    private class IdleCheckTask extends TimerTask {
        public void run() {
            if (mAvailableDevices.size() > 0) {
                WCHTTPResync.Builder sync = new WCHTTPResync.Builder(myApp, null);
                sync.addNotifyListener()
                        .addOnFinishListener(new WCHTTPResync.OnSyncFinished() {
                            @Override
                            public boolean onNewMessagesSummary(Context context, int totalAmount, List<WCChat.DeviceMsgsCnt> aList) {
                                mAvailableDevices.lock();
                                try {
                                    for (BLEDeviceVariant deviceVariant : mAvailableDevices)
                                        if (deviceVariant.isServerCompleteDevice()) {
                                            if (totalAmount > 0) {
                                                boolean not_found = true;
                                                for (WCChat.DeviceMsgsCnt devCnt : aList) {
                                                    if (deviceVariant.item.getDbId() == devCnt.getDbId() ||
                                                            deviceVariant.item.getDeviceServerName().equals(devCnt.getName())) {
                                                        deviceVariant.item.setUnreadedMsgs(devCnt.getCnt());
                                                        not_found = false;
                                                        break;
                                                    }
                                                }
                                                if (not_found)
                                                    deviceVariant.item.setUnreadedMsgs(0);
                                            } else
                                                deviceVariant.item.setUnreadedMsgs(0);
                                        }
                                } finally {
                                    mAvailableDevices.unlock();
                                }
                                return false;
                            }

                            @Override
                            public void onNewMessages(Context context, List<WCChat.ChatMessage> aList, List<String> aDevices) {
                            }

                            @Override
                            public void onError(Context context, int state, String aError) {
                            }
                        });
                sync.doResync();
            }
        }
    }

    private class IdleDeviceUpdateTask extends TimerTask {

        public void run() {
            if (mAvailableDevices.size() > 0) {
                DevList().beginUpdate();
                mAvailableDevices.lock();
                try {
                    for (BLEDeviceVariant bd : mAvailableDevices)
                        if (bd.isServerCompleteDevice())
                            DevList().saveItem(bd.item);
                } finally {
                    mAvailableDevices.unlock();
                    DevList().endUpdate();
                }
                refreshAvailableDevicesThreadSafe();
            }
        }

    }

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
            httpServerCooldown -= DEVICE_SYNC_PERIOD;
            if (httpServerCooldown < 0) httpServerCooldown = 0;


            if (ENABLED_INTERNET_FEATURE && (httpServerCooldown <= 0)) {
                WCHTTPClient httpClient = WCHTTPClientHolder.getInstance(MainActivity.this);
                if (httpClient.state() == CS_DISCONNECTED) {
                    httpClient.startConnect(MainActivity.this);
                } else
                if (httpClient.state() == CS_CONNECTED) {
                    httpServerCooldown = DEVICE_SERVER_REQUEST_PERIOD;

                    httpClient.recvMsgs(MainActivity.this, false);
                    httpClient.recvSnaps(MainActivity.this);

                    httpClient.startScanning(MainActivity.this,
                            (resultCode, resultMsg) -> {
                                Object res = resultMsg.opt(JSON_DEVICES);

                                mAvailableDevices.lock();
                                try {
                                    for (BLEDeviceVariant deviceVariant : mAvailableDevices)
                                        if (deviceVariant.isServerCompleteDevice())
                                            deviceVariant.item.setOnline(false);
                                } finally {
                                    mAvailableDevices.unlock();
                                }

                                if (res instanceof JSONArray)
                                    for (int i = 0; i < ((JSONArray) res).length(); i++) {
                                        Object aDevice = ((JSONArray) res).opt(i);
                                        if (aDevice instanceof String) {
                                            mAvailableDevices.add((String) aDevice);
                                        } else
                                        if (aDevice instanceof JSONObject) {
                                            String aName = ((JSONObject) aDevice).optString(JSON_DEVICE, "");
                                            String aMeta = ((JSONObject) aDevice).optString(JSON_META, "{}");
                                            mAvailableDevices.addNameAndMeta(aName, aMeta);
                                        }
                                    }

                                refreshAvailableDevicesThreadSafe();
                            });
                }
            }

            if (ENABLED_BLE_FEATURE && (bleScannerState == BS_READY)) {
                runOnUiThread(() -> {
                    mAvailableDevices.removeMasked(DEVICE_VARIANT_BLE_ITEM);
                    doRefreshAvailableDevices();
                    //
                    mSRL.setRefreshing(false);

                    handlerBLEScan.postDelayed(MainActivity.this::cancelSearchBLE, DEVICE_BLE_SCAN_TIMEOUT);

                    bleScannerState = BS_SCANNING;

                    mBLEIcon.setVisibility(View.VISIBLE);
                    mBLEText.setVisibility(View.VISIBLE);
                    mBLEIcon.setAlpha(0f);
                    mBLEText.setText(R.string.searching_ble);
                    blinkanimation.start();

                    final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);

                    for (BluetoothDevice bluetoothDevice : bluetoothManager.getConnectedDevices(BluetoothProfile.GATT))
                        if ((bluetoothDevice.getName() != null) &&
                             (bluetoothDevice.getName().startsWith(SampleGattAttributes.BLE_NAME_PREFIX)))
                            mAvailableDevices.add(bluetoothDevice);

                    startBLEScaning();
                    doRefreshAvailableDevices();
                });
            }
        }
    }

    private void refreshAvailableDevicesThreadSafe() {
        runOnUiThread (new Thread(this::doRefreshAvailableDevices));
    }

    private void doRefreshAvailableDevices() {
        mAvailableDevices.lock();
        try {
            mAvailableDevices.sortList();
            mAvailableDevicesAdapter.notifyDataSetChanged();
        } finally {
            mAvailableDevices.unlock();
        }
    }

    private void restartAsyncTimer() {
        syntask  = new SynchroTask();
        idletask1 = new IdleCheckTask();
        idletask2 = new IdleDeviceUpdateTask();
        syntimer = new Timer();
    }

    private void cancelSearchBLE() {
        if (bleScannerState == BS_SCANNING) {
            bluetoothAdapter.getBluetoothLeScanner().stopScan(leScanCallback);
            bleScannerState = BS_SLEEP;
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

    private final WCHTTPClient.OnStateChangeListener httpInterface = new WCHTTPClient.OnStateChangeListener() {
        @Override
        public void onConnect(String sid) {
            runOnUiThread (new Thread(() -> afterConnectionChanged(CS_CONNECTED)));
        }

        @Override
        public void onDisconnect(int errCode) {
            httpServerCooldown = DEVICE_SERVER_CONNECT_COOLDOWN;
            runOnUiThread (new Thread(() -> {
                afterConnectionChanged(CS_DISCONNECTED);
                Toast toast = Toast.makeText(getApplicationContext(),
                        String.format(getString(R.string.rest_error), errCode, REST_RESPONSE_ERRORS[errCode]),
                        Toast.LENGTH_LONG);
                toast.show();
            }));
        }

        @Override
        public void onClientStateChanged(int newState) { }

        @Override
        public void onLoginError(int errCode, String errStr) {
            myApp.alertWrongUser(MainActivity.this, errStr,
                    (dialogInterface, i) -> launchMainConfig());
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        myApp = (WCApp) getApplication();

        myApp.setHttpCfgDevice("android_" + Build.MODEL);

        this.requestWindowFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.activity_main);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        handlerBLEScan = new Handler();

        mAvailableDevices = new DeviceVariantList();
        for (DeviceItem di : DevList()) {
            di.setOnline(false);
            mAvailableDevices.add(new BLEDeviceVariant(di));
        }
        mAvailableDevicesAdapter = new DeviceAdapter(this);

        mListView = findViewById(R.id.list_view);
        mListView.setAdapter(mAvailableDevicesAdapter);

        ImageButton mOpenConfig = findViewById(R.id.img_open_config);
        mUserConnect = findViewById(R.id.img_con_indicator);
        mUserName = findViewById(R.id.username_tv);

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

        mSRL.setOnRefreshListener(this::allowToBLEScan);

        mListView.setOnItemLongClickListener((parent, view, position, id) -> {
            mListMod = false;
            Object o = mListView.getItemAtPosition(position);
            BLEDeviceVariant aitem = (BLEDeviceVariant) o;
            if (aitem.isBLEDevice()) {
                mAvailableDevices.remove(position);
                doRefreshAvailableDevices();
                mListMod = true;
            }
            return false;
        });

        mListView.setOnItemClickListener((parent, view, position, id) -> {
            if (mListMod) {
                mListMod = false;
                return;
            }
            Object o = mListView.getItemAtPosition(position);
            BLEDeviceVariant aitem = (BLEDeviceVariant) o;
            switch (aitem.variant) {
                case DEVICE_VARIANT_DEVICE:
                case DEVICE_VARIANT_SERVER_ITEM: {
                    DeviceItem adevice = aitem.item;
                    launchDeviceChat(adevice);
                    break;
                }
                case DEVICE_VARIANT_BLE_ITEM: {
                    final DeviceItem adevice = aitem.item;
                    if (!adevice.isBLEConnected())
                        launchDeviceConfig(adevice);
                    break;
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

        mOpenConfig.setOnClickListener(v -> launchMainConfig());
        mUserConnect.setOnClickListener(view -> {
            if (ENABLED_INTERNET_FEATURE) {
                WCHTTPClient wchttpClient = WCHTTPClientHolder.getInstance(this);
                switch (wchttpClient.state()) {
                    case CS_CONNECTED:
                        new AlertDialog.Builder(
                                new ContextThemeWrapper(MainActivity.this, R.style.AlertDialogCustom))
                                .setTitle(R.string.disconnect_user)
                                .setMessage(R.string.disconnect_request)
                                .setPositiveButton(android.R.string.yes, (dialog, which) -> WCHTTPClientHolder.getInstance(MainActivity.this).disconnectByUser())
                                .setNegativeButton(android.R.string.cancel, null)
                                .setIcon(android.R.drawable.ic_dialog_alert)
                                .show();
                        break;
                    case CS_USER_CFG_INCORRECT:
                    case CS_DISCONNECTED_BY_USER:
                    case CS_DISCONNECTED_RETRY_OVER:
                    {
                        wchttpClient.retryConnect();
                        restartScanMode();
                        break;
                    }
                    case CS_DISCONNECTED: {
                        restartScanMode();
                        break;
                    }
                }
            } else
                checkPermissions();
        });

        final Intent intent = getIntent();
        String chatToLaunch = intent.getStringExtra(ACTION_LAUNCH_CHAT);
        if ((chatToLaunch != null) && (chatToLaunch.length() > 0)) {
            final DeviceItem dev = myApp.mDeviceItems.findItem(chatToLaunch);
            if (dev != null) {
                new Handler().postDelayed(() -> runOnUiThread(() -> launchDeviceChat(dev)), 1000);
            }
        } else {
            String confToLaunch = intent.getStringExtra(ACTION_LAUNCH_CONFIG);
            if ((confToLaunch != null) && (confToLaunch.length() > 0)) {
                new Handler().postDelayed(() -> runOnUiThread(this::launchMainConfig), 1000);
            }
        }
    }

    private void launchMainConfig() {
        final Intent intent = new Intent(MainActivity.this, MainConfigActivity.class);
        cancelSearchBLE();
        ACTIVITY_STATE = STATE_MAIN_CONFIG;
        stopTimer();
        startActivityForResult(intent, REQUEST_MAIN_CONFIG_MODE);
    }

    private void launchDeviceConfig(DeviceItem adevice) {
        final Intent intent = new Intent(MainActivity.this, DeviceConfigActivity.class);
        intent.putExtra(EXTRAS_DEVICE_BLE_NAME, adevice.getDeviceBLEName());
        intent.putExtra(EXTRAS_DEVICE_ADDRESS, adevice.getDeviceBLEAddress());
        intent.putExtra(EXTRAS_DEVICE_WRITE_ID, adevice.getDeviceWriteChar());
        intent.putExtra(EXTRAS_DEVICE_COLOR, adevice.getDeviceItemColor());
        intent.putExtra(EXTRAS_DEVICE_INDEX, adevice.getDeviceItemIndex());
        cancelSearchBLE();
        ACTIVITY_STATE = STATE_DEVICE_CONFIG;
        adevice.BLEConnect();
        stopTimer();
        startActivityForResult(intent, REQUEST_DEVICE_CONFIG_MODE);
    }

    private void launchDeviceChat(DeviceItem adevice) {
        final Intent intent = new Intent(MainActivity.this, DeviceChatActivity.class);
        long userDbId = -1;
        String cfgDev = myApp.getHttpCfgDevice();
        DeviceItem userDevice = DevList().findItem(cfgDev);
        if (userDevice != null) {
            userDbId = userDevice.getDbId();
        }
        intent.putExtra(EXTRAS_USER_DEVICE_ID, userDbId);
        intent.putExtra(EXTRAS_TARGET_DEVICE_ID, adevice.getDbId());
        intent.putExtra(EXTRAS_DEVICE_HOST_NAME, adevice.getDeviceServerName());
        intent.putExtra(EXTRAS_DEVICE_ADDRESS, adevice.getDeviceBLEAddress());
        intent.putExtra(EXTRAS_DEVICE_BLE_NAME, adevice.getDeviceBLEName());
        intent.putExtra(EXTRAS_DEVICE_WRITE_ID, adevice.getDeviceWriteChar());
        intent.putExtra(EXTRAS_DEVICE_COLOR, adevice.getDeviceItemColor());
        intent.putExtra(EXTRAS_DEVICE_INDEX, adevice.getDeviceItemIndex());
        cancelSearchBLE();
        ACTIVITY_STATE = STATE_DEVICE_CHAT;
        stopTimer();
        startActivityForResult(intent, REQUEST_DEVICE_CHAT_MODE);
    }

    final static int REQUEST_ENABLE_BT = 0x009333;
    final static int REQUEST_DEVICE_CONFIG_MODE = 0x009334;
    final static int REQUEST_DEVICE_CHAT_MODE = 0x009335;
    final static int REQUEST_MAIN_CONFIG_MODE = 0x009336;

    protected void onResume() {
        super.onResume();

        checkPermissions();
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
            syntimer.schedule(syntask, 500, DEVICE_SYNC_PERIOD);
            syntimer.schedule(idletask1, 1000, DEVICE_DB_REFRESH_TIMEOUT);
            syntimer.schedule(idletask2, 500, DEVICE_LIST_REFRESH_PERIOD);
        }
    }

    private void stopTimer() {
        handlerBLEScan.removeCallbacksAndMessages(null);
        cancelSearchBLE();

        if (syntask != null)
            syntask.cancel();
        if (idletask1 != null)
            idletask1.cancel();
        if (idletask2 != null)
            idletask2.cancel();
        if (syntimer != null) {
            syntimer.cancel();
            syntimer.purge();
        }

        syntimer = null;
        syntask = null;
        idletask2= null;
        idletask1= null;
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (ENABLED_INTERNET_FEATURE)
            WCHTTPClientHolder.getInstance(this).removeStateChangeListener(httpInterface);

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
        if (bleScannerState != BS_SCANNING)
            bleScannerState = BS_READY;
        else
            mSRL.setRefreshing(false);
    }

    private void denyToBLEScan() {
        if (bleScannerState == BS_SCANNING)
            cancelSearchBLE();
        bleScannerState = BS_SHUTDOWN;
        mSRL.setRefreshing(false);
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

            if (data != null && (resultCode == RESULT_OK)) {
                String deviceAddress = data.getStringExtra(EXTRAS_DEVICE_ADDRESS);
                String deviceName = data.getStringExtra(EXTRAS_DEVICE_BLE_NAME);
                String deviceHostName = data.getStringExtra(EXTRAS_DEVICE_HOST_NAME);
                int deviceColor = data.getIntExtra(EXTRAS_DEVICE_COLOR, DEFAULT_DEVICE_COLOR);
                int deviceIndex = data.getIntExtra(EXTRAS_DEVICE_INDEX, 0);

                DeviceItem di = null;

                if (deviceHostName.length() > 0)
                    di = DevList().findItem(deviceHostName);

                DevList().beginUpdate();
                try {
                    if (di == null) {
                        di = mAvailableDevices.add(deviceHostName, deviceAddress, deviceName);
                        DevList().saveItem(di);
                    } else
                        di.complete(deviceHostName, deviceName, deviceAddress);
                    DevList().setDeviceProps(di, deviceColor, deviceIndex);
                } finally {
                    DevList().endUpdate();
                }
            }

        } else
        if (requestCode == REQUEST_DEVICE_CHAT_MODE) {
            restartScanMode();

            if (data != null && (resultCode == RESULT_OK)) {
                String deviceHostName = data.getStringExtra(EXTRAS_DEVICE_HOST_NAME);
                int deviceColor = data.getIntExtra(EXTRAS_DEVICE_COLOR, DEFAULT_DEVICE_COLOR);
                int deviceIndex = data.getIntExtra(EXTRAS_DEVICE_INDEX, 0);
                String lstSync = data.getStringExtra(EXTRAS_DEVICE_LST_SYNC);

                DeviceItem di = DevList().findItem(deviceHostName);

                DevList().beginUpdate();
                try {
                    if (di == null) {
                        di = mAvailableDevices.add(deviceHostName);
                        DevList().saveItem(di);
                    }
                    DevList().setDeviceProps(di, deviceColor, deviceIndex);
                    DevList().setDeviceSyncProps(di, lstSync, 0);
                } finally {
                    DevList().endUpdate();
                }
            }

        } else
        if (requestCode == REQUEST_MAIN_CONFIG_MODE) {
            boolean connectDataChanged = false;
            if (data != null) {
               connectDataChanged = data.getBooleanExtra(MainConfigActivity.EXTRAS_USR_CFG_CHANGED, false);
            }
            // trying to reset the scan mode. hope the user gave the correct config
            if (connectDataChanged)
                WCHTTPClientHolder.getInstance(this).retryConnect();
            restartScanMode();
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void afterConnectionChanged(int aState) {
        mUserName.setText(myApp.getHttpCfgUserName());

        if (aState == CS_CONNECTED)
            mUserConnect.setImageDrawable(ContextCompat.getDrawable(MainActivity.this, R.drawable.connected));
        else
            mUserConnect.setImageDrawable(ContextCompat.getDrawable(MainActivity.this, R.drawable.disconnected));
    }

    private class DeviceAdapter extends ArrayAdapter<BLEDeviceVariant> {

        public DeviceAdapter(Context context) {
            super(context, R.layout.two_line_listitem_ico, mAvailableDevices);
        }

        @SuppressLint("DefaultLocale")
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
                case DEVICE_VARIANT_BLE_ITEM: {
                    aName = ble.item.getDeviceBLEName();
                    aText2 = ble.item.getDeviceBLEAddress();
                    aDevicePic = ContextCompat.getDrawable(MainActivity.this, ble.item.getDevicePictureID());
                    aSecondaryPic = new DeviceIconID(ble.item.getDeviceItemColor(), ble.item.getDeviceItemIndex());
                    break;
                }
                case DEVICE_VARIANT_DEVICE:
                case DEVICE_VARIANT_SERVER_ITEM: {
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
                        aDevicePic = ContextCompat.getDrawable(MainActivity.this,resID);
                        aSecondaryPic = new DeviceIconID(ble.item.getDeviceItemColor(),
                                ble.item.getDeviceItemIndex());
                    }

                    if (ble.item.isOnline())
                        aPresencePic = ContextCompat.getDrawable(MainActivity.this,android.R.drawable.presence_online);
                    else
                        aPresencePic = ContextCompat.getDrawable(MainActivity.this,android.R.drawable.presence_offline);

                    break;
                }
            }

            TextView name_tv = convertView.findViewById(R.id.text1);
            TextView mac_tv  = convertView.findViewById(R.id.text2);
            TextView msg_cnt = convertView.findViewById(R.id.new_msgs);
            ImageView img    = convertView.findViewById(R.id.icon);
            ImageView img_id = convertView.findViewById(R.id.icon_id);
            ImageView img_presence = convertView.findViewById(R.id.icon_presence);
            ImageView img_cfg = convertView.findViewById(R.id.icon_cfg);

            if (ble.item.getUnreadedMsgs() > 0) {
                msg_cnt.setVisibility(View.VISIBLE);
                msg_cnt.setText(String.format("%d", ble.item.getUnreadedMsgs()));
            } else
                msg_cnt.setVisibility(View.GONE);

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
        List<String> permissions = new ArrayList<>();
        permissions.add(ACCESS_FINE_LOCATION);
        permissions.add(INTERNET);
        permissions.add(BLUETOOTH);
        permissions.add(BLUETOOTH_ADMIN);

        List<String> listPermissionsNeeded = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) !=
                    PackageManager.PERMISSION_GRANTED) {
                listPermissionsNeeded.add(permission);
            }
        }
        if (!listPermissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, listPermissionsNeeded.toArray(new String[0]),
                    REQUEST_ID_MULTIPLE_PERMISSIONS);
        } else {
            setInternetFeatures(true);
            setBLEFeatures(true);
        }

        // Ensures Bluetooth is available on the device and it is enabled. If not,
        // displays a dialog requesting user permission to enable Bluetooth.
        if (ACTIVITY_STATE == STATE_DEVICE_LISTING) {
            restartScanMode();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
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

    private void setInternetFeatures(boolean value) {
        ENABLED_INTERNET_FEATURE = value;
        if (value) {
            WCHTTPClientHolder.getInstance(this).addStateChangeListener(httpInterface);
            WCHTTPClientHolder.getInstance(this).getClient();
        } else {
            WCHTTPClientHolder.getInstance(this).removeStateChangeListener(httpInterface);
            WCHTTPClientHolder.getInstance(this).releaseClient();
            WCHTTPResync.stopWCHTTPBackgroundWork(this);
        }
    }

    private void setBLEFeatures(boolean value) {
        ENABLED_BLE_FEATURE = value;
        if (!value)
            denyToBLEScan();
    }

    class DeviceVariantList extends ArrayList<BLEDeviceVariant> {

        private boolean hasServerDevices = false;
        private boolean hasBLEDevices = false;
        private final ReentrantLock lock = new ReentrantLock();

        @Override
        public void clear() {
            lock();
            try {
                hasServerDevices = false;
                hasBLEDevices = false;
                super.clear();
            } finally {
                unlock();
            }
        }

        public void sortList() {
            Collections.sort(this);
        }

        public void lock() {
            lock.lock();
        }

        public void unlock() {
            lock.unlock();
        }

        public void removeMasked(int mask) {
            lock();
            try {
                for (int i = size()-1; i >=0; i--) {
                    if ((get(i).variant & mask) > 0)
                        remove(i);
                }
            } finally {
                unlock();
            }
        }

        private void setHasServerDevices() {
            if (hasBLEDevices && !hasServerDevices)
                add(new BLEDeviceVariant());
            hasServerDevices = true;
        }

        private void setHasBLEDevices() {
            if (!hasBLEDevices && hasServerDevices)
                add(new BLEDeviceVariant());
            hasBLEDevices = true;
        }

        @Override
        public boolean add(BLEDeviceVariant item) {
            lock();
            try {
                if (item.isServerCompleteDevice())
                    setHasServerDevices();
                else
                if (item.isBLEDevice())
                    setHasBLEDevices();

                return super.add(item);
            } finally {
                unlock();
            }
        }

        public DeviceItem add(String item) {
            lock();
            try {
                setHasServerDevices();

                for (BLEDeviceVariant bd : mAvailableDevices)
                    if (bd.isServerCompleteDevice())
                        if (bd.item.getDeviceServerName().equals(item)) {
                            bd.item.setOnline(true);
                            return bd.item;
                        }
                DeviceItem di = new DeviceItem(getApplicationContext());
                di.setOnline(true);
                DevList().completeItem(di, item);
                add(new BLEDeviceVariant(di));
                return di;
            } finally {
                unlock();
            }
        }

        public DeviceItem addNameAndMeta(String item, String meta) {
            DeviceItem nitem = add(item);
            if (nitem != null) {
                lock();
                try {
                    DevList().completeItemWithMeta(nitem, meta);
                } finally {
                    unlock();
                }
            }
            return nitem;
        }

        public void add(BluetoothDevice bleDevice) {
            lock();
            try {
                setHasBLEDevices();

                String bleaddress = bleDevice.getAddress();
                for (BLEDeviceVariant bd : mAvailableDevices)
                    if (bd.isBLECompleteDevice())
                        if (bd.item.getDeviceBLEAddress().equals(bleaddress)) {
                            bd.item.setBLEDevice(bleDevice);
                            bd.update();
                            return;
                        }
                DeviceItem di = new DeviceItem(getApplicationContext());
                di.setBLEDevice(bleDevice);
                DevList().completeItem(di, bleDevice.getName(), bleDevice.getAddress());
                add(new BLEDeviceVariant(di));
            } finally {
                unlock();
            }
        }

        public DeviceItem add(String srvName, String bleAddr, String bleName) {
            lock();
            try {
                for (BLEDeviceVariant bd : mAvailableDevices)
                {
                    switch (bd.variant) {
                        case DEVICE_VARIANT_DEVICE:
                        case DEVICE_VARIANT_BLE_ITEM:{
                            if (bd.item.getDeviceBLEAddress().equals(bleAddr)) {
                                DevList().completeItem(bd.item, srvName);
                                bd.update();
                                return bd.item;
                            }
                            break;
                        }
                        case DEVICE_VARIANT_SERVER_ITEM:{
                            if (bd.item.getDeviceServerName().equals(srvName)) {
                                DevList().completeItem(bd.item, bleName, bleAddr);
                                bd.update();
                                return bd.item;
                            }
                            break;
                        }
                    }
                }
                setHasServerDevices();
                DeviceItem di = new DeviceItem(getApplicationContext());
                DevList().completeItem(di, srvName, bleName, bleAddr);
                BLEDeviceVariant res = new BLEDeviceVariant(di);
                add(res);
                return di;
            } finally {
                unlock();
            }
        }
    }
}
