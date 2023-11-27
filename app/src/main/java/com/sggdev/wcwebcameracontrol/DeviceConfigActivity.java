package com.sggdev.wcwebcameracontrol;

import static com.sggdev.wcsdk.BabaikaCommonConfig.KEY_DEVICE;
import static com.sggdev.wcsdk.BabaikaCommonConfig.KEY_HOST;
import static com.sggdev.wcsdk.BabaikaCommonConfig.KEY_SSID;
import static com.sggdev.wcsdk.BabaikaCommonConfig.KEY_USER;
import static com.sggdev.wcsdk.BabaikaConfigNotif.BabaikaConfigItem.CFG_OPT_PASSWORD;
import static com.sggdev.wcsdk.BabaikaConfigNotif.BabaikaConfigItem.CFG_OPT_READONLY;
import static com.sggdev.wcsdk.DeviceItem.DEFAULT_DEVICE_COLOR;
import static com.sggdev.wcwebcameracontrol.BluetoothService.ACTION_DATA_AVAILABLE;
import static com.sggdev.wcwebcameracontrol.IntentConsts.EXTRAS_DEVICE_ADDRESS;
import static com.sggdev.wcwebcameracontrol.IntentConsts.EXTRAS_DEVICE_BLE_NAME;
import static com.sggdev.wcwebcameracontrol.IntentConsts.EXTRAS_DEVICE_COLOR;
import static com.sggdev.wcwebcameracontrol.IntentConsts.EXTRAS_DEVICE_HOST_NAME;
import static com.sggdev.wcwebcameracontrol.IntentConsts.EXTRAS_DEVICE_INDEX;
import static com.sggdev.wcwebcameracontrol.IntentConsts.EXTRAS_DEVICE_WRITE_ID;

import android.app.Activity;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.sggdev.wcsdk.BabaikaCommand;
import com.sggdev.wcsdk.BabaikaConfigNotif;
import com.sggdev.wcsdk.BabaikaItem;
import com.sggdev.wcsdk.BabaikaNotiCommand;
import com.sggdev.wcsdk.BabaikaNotification;
import com.sggdev.wcsdk.SampleGattAttributes;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class DeviceConfigActivity extends Activity {
    private final static String TAG = DeviceConfigActivity.class.getSimpleName();

    private WCApp myApp;

    private String mDeviceName;
    private String mDeviceHostName;
    private String mDeviceAddress;
    private ListView mConfigList;
    private BluetoothService mBluetoothLeService;
    private ArrayList<BabaikaItem>  mGattCharacteristics =
            new ArrayList<> ();

    private final ArrayList<BabaikaConfigNotif.BabaikaModConfigItem> mDeviceConfig = new ArrayList<>();

    private final Queue<String> mSendingValues = new LinkedList<>();
    private boolean mConnected = false;
    private boolean mStopped = true;
    private boolean mWriteCharacteristicDiscovered = false;

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            if (!mConnected) {
                mBluetoothLeService.connect(mDeviceAddress);
            }
            mStopped = false;
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mStopped = true;
            mBluetoothLeService = null;
        }
    };

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                updateConnectionState(R.drawable.connected);
            } else if (BluetoothService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                updateConnectionState(R.drawable.disconnected);
                clearUI();
            } else if (BluetoothService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                displayGattServices(mBluetoothLeService.getSupportedGattServices());
            } else if (BluetoothService.ACTION_NOTIFICATION_GRANTED.equals(action)) {
                String uuid = intent.getStringExtra(BluetoothService.EXTRA_DATA_CHAR);
                for (BabaikaItem notification : mGattCharacteristics) {
                    BabaikaNotification bnoti = null;

                    if (notification instanceof BabaikaNotification) {
                        bnoti = (BabaikaNotification) notification;
                    } else if (notification instanceof BabaikaNotiCommand) {
                        bnoti = ((BabaikaNotiCommand) notification).getNoti();
                    }
                    if (bnoti != null) {
                        if (bnoti.getUUID().equals(uuid)) {
                            bnoti.setWaiting(false);
                            bnoti.setConnected(true);
                            break;
                        }
                    }
                }
            } else if (BluetoothService.ACTION_NOTIFICATION_CANCELED.equals(action)) {
                String uuid = intent.getStringExtra(BluetoothService.EXTRA_DATA_CHAR);
                for (BabaikaItem notification : mGattCharacteristics) {
                    BabaikaNotification bnoti = null;

                    if (notification instanceof BabaikaNotification) {
                        bnoti = (BabaikaNotification) notification;
                    } else if (notification instanceof BabaikaNotiCommand) {
                        bnoti = ((BabaikaNotiCommand) notification).getNoti();
                    }

                    if (bnoti != null) {
                        if (bnoti.getUUID().equals(uuid)) {
                            bnoti.setWaiting(false);
                            bnoti.setConnected(false);
                            for (BluetoothGattCharacteristic characteristic : mBluetoothLeService.getNotifyCharacteristics()) {
                                if (characteristic.getUuid().toString().equals(uuid)) {
                                    sendNotifySetting(characteristic, true);
                                }
                            }
                            break;
                        }
                    }
                }
            } else if (ACTION_DATA_AVAILABLE.equals(action)) {
                String uuid = intent.getStringExtra(BluetoothService.EXTRA_DATA_CHAR);
                if (uuid != null) {
                    if (uuid.length() > 8) uuid = uuid.substring(0, 8);
                    for (BabaikaItem notification : mGattCharacteristics) {
                        BabaikaNotification bnoti = null;

                        if (notification instanceof BabaikaNotification) {
                            bnoti = (BabaikaNotification) notification;
                        } else if (notification instanceof BabaikaNotiCommand) {
                            bnoti = ((BabaikaNotiCommand) notification).getNoti();
                        }

                        if (bnoti != null) {
                            if (bnoti.getUUID().equals(uuid)) {
                                bnoti.setConnected(true);
                                break;
                            }
                        }
                    }
                    displayData(uuid,
                            intent.getByteArrayExtra(BluetoothService.EXTRA_DATA));
                }
            } else if (BluetoothService.ACTION_INPUT_CHARACTERISTIC_DISCOVERED.equals(action)) {
                BluetoothGattCharacteristic characteristic =  mBluetoothLeService.getWriteCharacteristic();
                mWriteCharacteristicDiscovered = true;
                setDeviceWriteChar(characteristic.getUuid().toString());
            }
        }
    };

    private void clearUI() {
        mConfigList.setAdapter(null);
    }

    private void sendNotifySetting(BluetoothGattCharacteristic characteristic, boolean notifyEnable) {
        if (mBluetoothLeService.setCharacteristicNotification(
                characteristic, notifyEnable)) {
            BluetoothGattDescriptor clientConfig = characteristic.getDescriptor(SampleGattAttributes.CLIENT_CHARACTERISTIC_CONFIG);
            if (clientConfig != null) {
                if (notifyEnable) {
                    clientConfig.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                } else {
                    clientConfig.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
                }
                mBluetoothLeService.writeDescriptor(clientConfig);
            }
        }
    }

    private DeviceIconView mDeviceIcon;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.gatt_services_characteristics);

        myApp = (WCApp) getApplication();

        final Intent intent = getIntent();
        mDeviceHostName = "";
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_BLE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);
        String mDeviceWriteChar = intent.getStringExtra(EXTRAS_DEVICE_WRITE_ID);
        int mDeviceColor = intent.getIntExtra(EXTRAS_DEVICE_COLOR, DEFAULT_DEVICE_COLOR);
        int mDeviceIndex = intent.getIntExtra(EXTRAS_DEVICE_INDEX, 0);

        mConfigList = findViewById(R.id.gatt_services_list);

        mDeviceIcon = findViewById(R.id.device_icon_view);
        mDeviceIcon.setDeviceConfig(mDeviceColor, mDeviceIndex, mDeviceWriteChar);

        Intent gattServiceIntent = new Intent(this, BluetoothService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        ArrayList<BabaikaCommand> res = SampleGattAttributes.getCommandSet(mDeviceWriteChar);
        if (res != null) {
            mGattCharacteristics.addAll(res);
        }

        mHandler = new Handler();
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                //Do something after 2000ms
                if (!mStopped) {
                    if (!mConnected) {
                        if (mBluetoothLeService != null) {
                            Log.d("DEBUG", "try to connect");
                            mBluetoothLeService.connect(mDeviceAddress);
                        }
                    } else {
                        for (BabaikaItem notification : mGattCharacteristics) {
                            BabaikaNotification bnoti = null;

                            if (notification instanceof BabaikaNotification) {
                                bnoti = (BabaikaNotification) notification;
                            } else if (notification instanceof BabaikaNotiCommand) {
                                bnoti = ((BabaikaNotiCommand) notification).getNoti();
                            }

                            if (bnoti != null) {
                                if ((!bnoti.getConnected()) &&
                                        bnoti.getWaiting()) {
                                    bnoti.setWaiting(false);
                                    for (BluetoothGattCharacteristic characteristic : mBluetoothLeService.getNotifyCharacteristics()) {
                                        if (characteristic.getUuid().toString().equals(bnoti.getUUID())) {
                                            sendNotifySetting(characteristic, true);
                                        }
                                    }
                                } else
                                if ((!bnoti.getConnected()) &&
                                        (!bnoti.getWaiting())) {
                                    bnoti.setWaiting(true);
                                }
                            }
                        }
                    }
                }

                mHandler.postDelayed(this, 3000);
            }
        }, 3000);

        mQueueHandler = new Handler();
        mQueueHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mConnected && (mWriteCharacteristicDiscovered)) {
                    String c = mSendingValues.poll();
                    if (c != null) {
                        mBluetoothLeService.sendData(c);
                        Log.d("DEBUG", "sending " + c);
                    }
                }
                mQueueHandler.postDelayed(this, 500);
            }
        }, 500);

        mConfigList.setOnItemClickListener((adapterView, view, position, id) -> {
            final BabaikaConfigNotif.BabaikaModConfigItem it =
                    (BabaikaConfigNotif.BabaikaModConfigItem) adapterView.getItemAtPosition(position);

            if ((it.getOptions() & CFG_OPT_READONLY) == 0) {
                String title = it.Comment();
                int resID = 0;
                if (title != null) {
                    resID = getApplicationContext().getResources().
                            getIdentifier(title, "string",
                                    getApplicationContext().getPackageName());
                }
                if (resID != 0)
                    title = getApplicationContext().getString(resID);

                AlertDialog.Builder builder = new AlertDialog.Builder(DeviceConfigActivity.this);
                builder.setTitle(title);


                EditText an_input;
                if (it.Key().equals(KEY_USER) || it.Key().equals(KEY_HOST)) {
                    View cView = LayoutInflater.from(DeviceConfigActivity.this)
                            .inflate(R.layout.edit_request_with_set_def, null);

                    Button btn = cView.findViewById(R.id.set_default_button);
                    final EditText et = cView.findViewById(R.id.editText);
                    an_input = et;
                    if (it.Key().equals(KEY_USER))
                        btn.setOnClickListener(view1 -> et.setText(myApp.getHttpCfgUserName()));
                    else
                    if (it.Key().equals(KEY_HOST))
                        btn.setOnClickListener(view1 -> et.setText(myApp.getHttpCfgServerUrl()));
                    builder.setView(cView);
                } else
                if (it.Key().equals(KEY_SSID)) {
                    View cView = LayoutInflater.from(DeviceConfigActivity.this)
                            .inflate(R.layout.edit_request_with_set_def_choose, null);

                    Button btn = cView.findViewById(R.id.set_default_button);
                    final EditText et = cView.findViewById(R.id.editText);
                    an_input = et;
                    btn.setOnClickListener(view1 -> et.setText(myApp.getCurrentSsid()));
                    btn = cView.findViewById(R.id.choose_button);
                    btn.setOnClickListener(view1 -> {
                        // todo : create alertdialog to choose wifi net
                    });
                    builder.setView(cView);
                } else {
                    an_input = new EditText(DeviceConfigActivity.this);
                    builder.setView(an_input);
                }
                final EditText input = an_input;
                // Specify the type of input expected; this, for example, sets the input as a password, and will mask the text
                int itp = InputType.TYPE_CLASS_TEXT;
                if ((it.getOptions() & CFG_OPT_PASSWORD) > 0)
                    itp |= InputType.TYPE_TEXT_VARIATION_PASSWORD;
                input.setInputType(itp);
                input.setText(it.getValue());

                // Set up the buttons
                builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String cValue = input.getText().toString();
                    if (!it.getValue().equals(cValue)) {
                        try {
                            JSONObject r = new JSONObject();

                            r.put(it.Key(), cValue);

                            cValue = "*set* ".concat(r.toString()).concat("\r\n");
                            boolean founded = false;
                            for (String node : mSendingValues) {
                                if (node.equals(cValue)) {
                                    founded = true;
                                    mSendingValues.remove(node);
                                    mSendingValues.offer(node);
                                    break;
                                }
                            }
                            if (!founded) mSendingValues.offer(cValue);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                });
                builder.setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.cancel());

                builder.show();
            }
        });
    }

    void setDeviceWriteChar(String achar) {
        mDeviceIcon.setDeviceWriteChar(achar);
    }

    private Handler mHandler;
    private Handler mQueueHandler;

    @Override
    protected void onResume() {
        super.onResume();

        doConnect();
    }

    private void doConnect() {
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final int result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
            mStopped = false;
        }
    }

    private void disconnectAllNotifications() {
        for (BluetoothGattCharacteristic characteristic: mBluetoothLeService.getNotifyCharacteristics()) {
            sendNotifySetting(characteristic, false);
        }
    }

    private void doDisconnect() {
        if (mConnected && mWriteCharacteristicDiscovered) {
            mBluetoothLeService.sendData("*cmd* exit\r\n");
        }

        mStopped = true;
        disconnectAllNotifications();
        mWriteCharacteristicDiscovered = false;
        unregisterReceiver(mGattUpdateReceiver);
        mBluetoothLeService.disconnect();

        Log.d(TAG, "disconnected");
    }

    @Override
    protected void onPause() {
        super.onPause();

        mHandler.removeCallbacksAndMessages(null);
        mQueueHandler.removeCallbacksAndMessages(null);
        doDisconnect();
    }

    @Override
    public void onBackPressed() {
        Intent intent = new Intent();
        intent.putExtra(EXTRAS_DEVICE_BLE_NAME, mDeviceName);
        intent.putExtra(EXTRAS_DEVICE_ADDRESS, mDeviceAddress);
        intent.putExtra(EXTRAS_DEVICE_WRITE_ID, mDeviceIcon.getDeviceWriteChar());
        intent.putExtra(EXTRAS_DEVICE_COLOR, mDeviceIcon.getDeviceColor());
        intent.putExtra(EXTRAS_DEVICE_INDEX, mDeviceIcon.getDeviceIndex());
        intent.putExtra(EXTRAS_DEVICE_HOST_NAME, mDeviceHostName);

        setResult(RESULT_OK, intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        unbindService(mServiceConnection);

        super.onDestroy();
        mBluetoothLeService = null;
    }

    private void updateConnectionState(final int resourceId) {
        runOnUiThread(() -> mDeviceIcon.updateConnectionState(resourceId));
    }

    private void displayData(String charid, byte[] data) {
        if ((data != null) && (charid != null)) {
            for (BabaikaItem notification : mGattCharacteristics) {

                BabaikaNotification bnoti = null;

                if (notification instanceof BabaikaNotification) {
                    bnoti = (BabaikaNotification) notification;
                } else if (notification instanceof BabaikaNotiCommand) {
                    bnoti = ((BabaikaNotiCommand) notification).getNoti();
                    if (notification instanceof BabaikaConfigNotif)
                        ((BabaikaConfigNotif)notification).setFieldValueChanged((field, new_value) -> {
                            boolean found = false;
                            boolean need_upd = false;
                            for (BabaikaConfigNotif.BabaikaModConfigItem ci: mDeviceConfig) {
                                if (ci.Key().equals(field)) {
                                    found = true;
                                    if (!ci.getValue().equals(new_value)) {
                                        ci.setValue(new_value);
                                        if (field.equals(KEY_DEVICE))
                                            mDeviceHostName = new_value;
                                        need_upd = true;
                                    }
                                    break;
                                }
                            }
                            if (!found) {
                                BabaikaConfigNotif.BabaikaModConfigItem nci =
                                        ((BabaikaConfigNotif)notification).genNewInstance(field);
                                nci.setValue(new_value);
                                mDeviceConfig.add(nci);
                                need_upd = true;
                            }
                            if (need_upd)
                                mConfigList.setAdapter(new BabaikaConfigAdapter(this));
                        });
                }

                if (bnoti != null) {
                    if (bnoti.getUUID().equals(charid)) {
                        bnoti.setValue(data);
                        return;
                    }
                }
            }
        }
    }

    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        String uuid;
        mGattCharacteristics = new ArrayList<> ();

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            uuid = gattService.getUuid().toString();

            if (SampleGattAttributes.isMainService(uuid)) {
                List<BluetoothGattCharacteristic> gattCharacteristics =
                        gattService.getCharacteristics();

                // Loops through available Characteristics.
                for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                    uuid = gattCharacteristic.getUuid().toString();
                    ArrayList<BabaikaCommand> res = SampleGattAttributes.getCommandSet(uuid);
                    if (res != null) {
                        mGattCharacteristics.addAll(res);
                    }
                    BabaikaItem notification = SampleGattAttributes.getNotification(uuid);
                    if (notification != null) {
                        final int charaProp = gattCharacteristic.getProperties();
                        final int charaPerm = gattCharacteristic.getPermissions();
                        if ((charaProp & BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                            mGattCharacteristics.add(notification);
                            sendNotifySetting(gattCharacteristic, true);

                            if (((charaProp & BluetoothGattCharacteristic.PROPERTY_READ) > 0) ||
                                    ((charaPerm & BluetoothGattCharacteristic.PERMISSION_READ) > 0)) {
                                try {
                                    Thread.sleep(500);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                                mBluetoothLeService.readCharacteristic(gattCharacteristic);
                            }

                        }
                    }
                }

            }
        }
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(ACTION_DATA_AVAILABLE);
        intentFilter.addAction(BluetoothService.ACTION_NOTIFICATION_CANCELED);
        intentFilter.addAction(BluetoothService.ACTION_NOTIFICATION_GRANTED);
        intentFilter.addAction(BluetoothService.ACTION_INPUT_CHARACTERISTIC_DISCOVERED);
        return intentFilter;
    }

    private class BabaikaConfigAdapter extends ArrayAdapter<BabaikaConfigNotif.BabaikaModConfigItem> {

        public BabaikaConfigAdapter(Context context) {
            super(context, R.layout.list_item, mDeviceConfig);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            BabaikaConfigNotif.BabaikaModConfigItem bcmd = getItem(position);

            if (convertView == null) {
                convertView = LayoutInflater.from(getContext())
                        .inflate(R.layout.list_item, null);
            }
            TextView tv_title =  convertView.findViewById(R.id.text1);
            TextView tv_value =  convertView.findViewById(R.id.text2);

            String text = bcmd.Comment();
            int resID = 0;
            if (text != null) {
                resID = getApplicationContext().getResources().
                        getIdentifier(text, "string",
                                getApplicationContext().getPackageName());
            }
            if (resID != 0) {
                tv_title.setText(resID);
            } else tv_title.setText(text);

            if ((bcmd.getOptions() & CFG_OPT_PASSWORD) > 0)
                tv_value.setText(bcmd.defValue());
            else
                tv_value.setText(bcmd.getValue());

            ImageView img = convertView.findViewById(R.id.image);
            String aPicName = bcmd.getPicture();

            resID = 0;
            if (aPicName != null) {
                resID = getApplicationContext().getResources().
                        getIdentifier(aPicName, "drawable",
                                getApplicationContext().getPackageName());
            }
            if (resID != 0) {
                img.setImageDrawable(ContextCompat.getDrawable(DeviceConfigActivity.this, resID));
            } else img.setImageDrawable(null);

            return  convertView;
        }
    }
}
