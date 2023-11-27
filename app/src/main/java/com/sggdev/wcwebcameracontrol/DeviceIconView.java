package com.sggdev.wcwebcameracontrol;

import static android.view.Gravity.CENTER;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.sggdev.wcsdk.DeviceItem;
import com.sggdev.wcsdk.SampleGattAttributes;

import java.util.ArrayList;
import java.util.List;

public class DeviceIconView extends LinearLayout {

    private int mDeviceColor, mDeviceIndex;
    private String mDeviceWriteChar;

    private ImageView mDeviceImage, mDeviceImageId, mConnectionState;
    private LinearLayout mColorIndexChoosePanel;
    private ColorIndexAdapter mIndexAdapter, mColorAdapter;
    private final List<DeviceIconID> colorList = new ArrayList<>();
    private final List<DeviceIconID> indexList = new ArrayList<>();

    public DeviceIconView(Context context) {
        super(context);
        initLayout(context);
    }

    public DeviceIconView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initLayout(context);
    }

    public DeviceIconView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initLayout(context);
    }

    private void initLayout(Context context) {
        setOrientation(VERTICAL);
        setGravity(CENTER);
        LayoutInflater inflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.device_icon_view, this);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        Context context = getContext();

        RecyclerView ch_Color = findViewById(R.id.ch_color);
        RecyclerView ch_Index = findViewById(R.id.ch_index);

        mDeviceImage = findViewById(R.id.icon_device);
        mDeviceImageId = findViewById(R.id.icon_device_id);
        mConnectionState = findViewById(R.id.icon_connection);

        mColorIndexChoosePanel = (LinearLayout) ch_Color.getParent();
        mColorIndexChoosePanel.setVisibility(View.GONE);

        mIndexAdapter = new ColorIndexAdapter(indexList);
        mColorAdapter = new ColorIndexAdapter(colorList);

        LinearLayoutManager mLayoutManager1 = new LinearLayoutManager(context.getApplicationContext());
        mLayoutManager1.setOrientation(LinearLayoutManager.HORIZONTAL);
        LinearLayoutManager mLayoutManager2 = new LinearLayoutManager(context.getApplicationContext());
        mLayoutManager2.setOrientation(LinearLayoutManager.HORIZONTAL);
        ch_Index.setLayoutManager(mLayoutManager1);
        ch_Color.setLayoutManager(mLayoutManager2);
        ch_Color.setItemAnimator(new DefaultItemAnimator());
        ch_Index.setItemAnimator(new DefaultItemAnimator());
        ch_Index.setAdapter(mIndexAdapter);
        ch_Color.setAdapter(mColorAdapter);

        mColorAdapter.setOnItemChangedListener(position -> {
            if (mDeviceColor != mColorAdapter.getSelectedColor()) {
                mDeviceColor = mColorAdapter.getSelectedColor();
                updateIndexList();
            }
        });

        mIndexAdapter.setOnItemChangedListener(position -> {
            if (mDeviceIndex != mIndexAdapter.getSelectedIndex()) {
                mDeviceIndex = mIndexAdapter.getSelectedIndex();
                updateColorList();
            }
        });


        // Sets up UI references.
        mConnectionState.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.disconnected));

        mDeviceImageId.setOnClickListener(v -> {
            if (mColorIndexChoosePanel.getVisibility() == View.VISIBLE) {
                mColorIndexChoosePanel.setVisibility(View.GONE);
            } else
                mColorIndexChoosePanel.setVisibility(View.VISIBLE);
        });
    }

    private static final int[] stat_Colors = {
            0xffaaaaaa, 0xff00ffff, 0xffff00ff, 0xffffff00,
            0xffff5555, 0xff55ff55, 0xff5555ff, 0xffffffff};

    @SuppressLint("NotifyDataSetChanged")
    void updateColorList() {
        colorList.clear();
        for (int stat_color : stat_Colors) {
            DeviceIconID ble_i = new DeviceIconID(stat_color, mDeviceIndex);
            colorList.add(ble_i);
        }
        mColorAdapter.setSelColorDigit(mDeviceColor, mDeviceIndex);
        mColorAdapter.notifyDataSetChanged();
        updateDeviceInfo();
    }

    @SuppressLint("NotifyDataSetChanged")
    void updateIndexList() {
        indexList.clear();
        for (int i = 0; i < 10; i++) {
            DeviceIconID ble_i = new DeviceIconID(mDeviceColor, i);
            indexList.add(ble_i);
        }
        mIndexAdapter.setSelColorDigit(mDeviceColor, mDeviceIndex);
        mIndexAdapter.notifyDataSetChanged();
        updateDeviceInfo();
    }

    private void updateDeviceInfo() {
        String picName = SampleGattAttributes.getCharPicture(mDeviceWriteChar);
        int resID = DeviceItem.defineDevicePictureID(getContext().getApplicationContext(), picName);
        mDeviceImage.setImageDrawable(ContextCompat.getDrawable(getContext(), resID));

        mDeviceImageId.setImageDrawable(new DeviceIconID(mDeviceColor, mDeviceIndex));
    }

    public void setDeviceColor(int aDeviceColor) {
        mDeviceColor = aDeviceColor;
        updateIndexList();
        updateDeviceInfo();
    }

    public int getDeviceColor() {return mDeviceColor;}

    public void setDeviceIndex(int aDeviceIndex) {
        mDeviceIndex = aDeviceIndex;
        updateColorList();
        updateDeviceInfo();
    }

    public int getDeviceIndex() {return mDeviceIndex;}

    public void setDeviceWriteChar(String aDeviceWriteChar) {
        mDeviceWriteChar = aDeviceWriteChar;
        updateDeviceInfo();
    }

    public String getDeviceWriteChar() {return mDeviceWriteChar;}

    public void setDeviceConfig(int aDeviceColor, int aDeviceIndex, String aDeviceWriteChar) {
        mDeviceColor = aDeviceColor;
        mDeviceIndex = aDeviceIndex;
        mDeviceWriteChar = aDeviceWriteChar;
        updateIndexList();
        updateColorList();
        updateDeviceInfo();
    }

    public void updateConnectionState(final int resourceId) {
        mConnectionState.setImageDrawable(ContextCompat.getDrawable(getContext(), resourceId));
    }
}
