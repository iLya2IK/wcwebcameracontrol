package com.sggdev.wcwebcameracontrol;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;

public class WCPicPreviewHelper extends WCMediaPreviewHelper {

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

    public int getHeight() {return mHeight;}
    public int getWidth() {return mWidth;}
    public Bitmap getBitmap() {return mBitmap;}
}
