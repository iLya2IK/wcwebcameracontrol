package com.sggdev.wcwebcameracontrol;

import static com.sggdev.wcwebcameracontrol.IntentConsts.EXTRAS_IMAGE_BITMAP;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.graphics.drawable.Drawable;
import android.os.Bundle;

import com.jsibbold.zoomage.ZoomageView;
import com.sggdev.wcwebcameracontrol.databinding.ActivityImageViewFullscreenBinding;

public class ImageViewFullscreenActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActivityImageViewFullscreenBinding binding =
                ActivityImageViewFullscreenBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ZoomageView mContentView = binding.fullscreenContent;

        String imgLoc = getIntent().getStringExtra(EXTRAS_IMAGE_BITMAP);

        Drawable d = null;
        try {
            d = Drawable.createFromPath(imgLoc);
        }
        catch (Exception e) {
            e.printStackTrace();
            d = null;
        }
        finally {
            if (d == null)
                d = ContextCompat.getDrawable(getApplicationContext(),
                        android.R.drawable.ic_menu_report_image);
            mContentView.setImageDrawable(d);
        }
    }
}