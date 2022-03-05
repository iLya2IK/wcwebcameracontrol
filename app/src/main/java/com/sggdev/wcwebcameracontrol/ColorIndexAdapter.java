package com.sggdev.wcwebcameracontrol;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ColorIndexAdapter extends RecyclerView.Adapter<ColorIndexAdapter.CIViewHolder> {
    private List<DeviceIconID> iconList;
    private OnItemChangedListener mOnItemChangedListener;
    private int selection = 0;

    public void setOnItemChangedListener(OnItemChangedListener listener) {
        mOnItemChangedListener = listener;
    }

    void doItemChanged(int position) {
        if (mOnItemChangedListener != null) {
            mOnItemChangedListener.onChange(position);
        }
    }

    class CIViewHolder extends RecyclerView.ViewHolder {
        ImageView iv;
        CIViewHolder(View view) {
            super(view);
            iv = view.findViewById(R.id.icv_icon);
        }

        void bind(final DeviceIconID ble_id) {
            iv.setImageDrawable(ble_id);

            if (selection == getAdapterPosition()) {
                iv.setAlpha(1f);
            } else {
                iv.setAlpha(0.5f);
            }

            iv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    iv.setAlpha(1f);
                    if (selection != getAdapterPosition()) {
                        notifyItemChanged(selection);
                        selection = getAdapterPosition();
                        doItemChanged(selection);
                    }
                }
            });
        }
    }

    public ColorIndexAdapter(List<DeviceIconID> aIconList, int mSelColor, int mSelDigit) {
        this.iconList = aIconList;
    }

    public void setSelColorDigit(int mSelColor, int mSelDigit) {
        for (int i = 0; i < iconList.size(); i++) {
            if ((iconList.get(i).getBleColor() == mSelColor) &&
                    (iconList.get(i).getBleDigit() == mSelDigit)) {
                selection = i;
            }
        }
    }

    @NonNull
    @Override
    public CIViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.icv_item, parent, false);
        return new CIViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull CIViewHolder holder, int position) {
        holder.bind(iconList.get(position));
    }

    @Override
    public int getItemCount() {
        if (iconList == null) return 0;
        return iconList.size();
    }

    public int getSelectedIndex() {
        if (selection >= 0) {
            return iconList.get(selection).getBleDigit();
        }
        return -1;
    }

    public int getSelectedColor() {
        if (selection >= 0) {
            return iconList.get(selection).getBleColor();
        }
        return -1;
    }
}
