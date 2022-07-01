package com.sggdev.wcwebcameracontrol;

import static com.sggdev.wcwebcameracontrol.IntentConsts.EXTRAS_DEVICE_HOST_NAME;
import static com.sggdev.wcwebcameracontrol.IntentConsts.EXTRAS_IMAGE_BITMAP;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.jsibbold.zoomage.ZoomageView;
import com.sggdev.wcwebcameracontrol.databinding.ActivityImageViewFullscreenBinding;
import com.sggdev.wcwebcameracontrol.databinding.ActivityStreamFullscreenBinding;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.util.Timer;
import java.util.TimerTask;

public class StreamFullscreenActivity extends AppCompatActivity {

    private String mDeviceName;
    private ZoomageView mContentView;
    private CustomBitmapDrawable mDrawable = null;
    private Timer strmTimer = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActivityStreamFullscreenBinding binding =
                ActivityStreamFullscreenBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mContentView = binding.fullscreenContent;

        mDeviceName = getIntent().getStringExtra(EXTRAS_DEVICE_HOST_NAME);
    }

    void startReadStream() {
        WCHTTPClient httpClient = WCHTTPClientHolder.getInstance(StreamFullscreenActivity.this);
        httpClient.launchStream(mDeviceName);
        if (strmTimer == null) {
            strmTimer = new Timer();
            strmTimer.schedule(new strmTask(), 500, 200);
        }
    }

    void stopReadStream() {
        if (strmTimer != null) {
            strmTimer.cancel();
            strmTimer.purge();
            strmTimer = null;
        }
        WCHTTPClient httpClient = WCHTTPClientHolder.getInstance(StreamFullscreenActivity.this);
        httpClient.haltStream();
    }

    public class CustomBitmapDrawable extends Drawable implements Drawable.Callback{

        private Bitmap frame;
        private int mBitmapWidth, mBitmapHeight;

        CustomBitmapDrawable(Bitmap b) {
            mBitmapHeight = 0;
            mBitmapWidth = 0;
            invalidateBitmap(b);
        }

        public void invalidateDrawable(Drawable drawable){
            super.invalidateSelf(); //This was done for my specific example. I wouldn't use it otherwise
        }

        public void scheduleDrawable(Drawable drawable, Runnable runnable, long l){
            invalidateDrawable(drawable);
        }

        public void unscheduleDrawable(Drawable drawable,Runnable runnable){
            super.unscheduleSelf(runnable);
        }

        private void invalidateBitmap(Bitmap b) {
            if (frame != null &&
                    b.getHeight() == mBitmapHeight &&
                    b.getWidth() == mBitmapWidth) {
                Canvas canvas = new Canvas(frame);
                canvas.drawBitmap(b, 0, 0, null);
            } else {
                frame = b.copy(Bitmap.Config.ARGB_8888, true);
                mBitmapWidth = b.getWidth();
                mBitmapHeight = b.getHeight();
            }
        }

        @Override
        public void draw(Canvas canvas){
            canvas.drawBitmap(frame, null, getBounds(), null);
        }

        @Override
        public void setAlpha(int alpha) {
            // Has no effect
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
            // Has no effect
        }

        @Override
        public int getOpacity() {
            // Not Implemented
            return PixelFormat.OPAQUE;
        }

        @Override
        public int getIntrinsicWidth() {
            return mBitmapWidth;
        }
        @Override
        public int getIntrinsicHeight() {
            return mBitmapHeight;
        }

    }

    void refreshDrawable(Bitmap b) {
        if (mDrawable == null) {
            mDrawable = new CustomBitmapDrawable(b);//getResources(), b);
            mContentView.setImageDrawable(mDrawable);
        } else {
            mDrawable.invalidateBitmap(b);
            mContentView.postInvalidate();
        }
    }

    void updateDrawable(byte[] aDrawable) {
        ByteArrayInputStream iStream = new ByteArrayInputStream(aDrawable);

        Bitmap b = null;
        try {
            b = BitmapFactory.decodeStream(iStream);
        }
        catch (Exception e) {
            e.printStackTrace();
            b = null;
        }
        finally {
            refreshDrawable(b);
        }
    }

    class strmTask extends TimerTask {

        @Override
        public void run() {
            StreamFullscreenActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    WCHTTPClient httpClient = WCHTTPClientHolder.getInstance(StreamFullscreenActivity.this);
                    WCHTTPStreamFrame fr = httpClient.popFrame();
                    if (fr != null) {
                        StreamFullscreenActivity.this.updateDrawable(fr.getData());
                    }
                }
            });
        }
    };

    @Override
    protected void onResume() {
        super.onResume();

        startReadStream();
    }

    @Override
    public void onPause() {
        super.onPause();

        stopReadStream();
    }
}