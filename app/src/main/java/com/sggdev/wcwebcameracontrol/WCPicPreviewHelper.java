package com.sggdev.wcwebcameracontrol;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.util.DisplayMetrics;

public class WCPicPreviewHelper extends WCMediaPreviewHelper {

    public final static int MIN_PIC_SIZE_DPI = 96;
    private final static int MAX_PREVIEW_SIZE = 64;

    private int mWidth = 0;
    private int mHeight = 0;
    private Bitmap mBitmap = null;

    public WCPicPreviewHelper() {
        super();
    }

    public WCPicPreviewHelper(byte[] blob) {
        super(blob);
    }

    public boolean load() {
        mWidth = readInt32();
        mHeight = readInt32();
        mBitmap = BitmapFactory.decodeStream(getByteInputStream());

        return true;
    }

    public boolean save(BitmapDrawable aBitmap) {
        Bitmap unscaledBitmap = aBitmap.getBitmap();
        mHeight = unscaledBitmap.getHeight();
        mWidth = unscaledBitmap.getWidth();

        int bounding;
        float xScale, yScale, scale;
        if (mWidth > MAX_PREVIEW_SIZE || mHeight > MAX_PREVIEW_SIZE) {
            bounding = MAX_PREVIEW_SIZE;
        } else {
            bounding = Math.max(mHeight, mWidth);
        }
        xScale = ((float) bounding) / mWidth;
        yScale = ((float) bounding) / mHeight;
        scale = Math.min(xScale, yScale);
        Matrix matrix = new Matrix();
        matrix.postScale(scale, scale);
        mBitmap = Bitmap.createBitmap(unscaledBitmap, 0, 0, mWidth, mHeight, matrix, true);

        writeInt32(mWidth);
        writeInt32(mHeight);
        mBitmap.compress(Bitmap.CompressFormat.PNG, 50, getByteOutputStream());

        return true;
    }

    static float getDisplayScale(Context context, int phWidth, int phHeight) {
        DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
        //max image width = 90% of width - 30dp
        int max_bounding = (int) Math.round(displayMetrics.widthPixels * 0.9 -
                30.0 * (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT));
        int min_bounding = Math.round(MIN_PIC_SIZE_DPI * (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT));
        int bounding;

        if (phWidth > max_bounding || phHeight > max_bounding) {
            bounding = max_bounding;
        } else if (phWidth < min_bounding || phHeight < min_bounding) {
            bounding = min_bounding;
        } else {
            bounding = Math.max(phWidth, phHeight);
        }
        float xScale = ((float) bounding) / phWidth;
        float yScale = ((float) bounding) / phHeight;
        return Math.min(xScale, yScale);
    }

    public int getHeight() {return mHeight;}
    public int getWidth() {return mWidth;}
    public Bitmap getBitmap() {return mBitmap;}
}
