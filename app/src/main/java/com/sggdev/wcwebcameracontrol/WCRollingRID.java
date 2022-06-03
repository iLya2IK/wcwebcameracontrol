package com.sggdev.wcwebcameracontrol;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;

import androidx.core.content.ContextCompat;

import java.util.NoSuchElementException;
import java.util.concurrent.locks.ReentrantLock;

public class WCRollingRID {
    private final ReentrantLock lock = new ReentrantLock();

    public interface OnLoadingFinished {
        public void finished(WCRollingRID obj);
    }

    private final Context mContext;
    private final long mRid;
    private final String mLocation;
    private boolean mIsLoaded = false;
    private boolean mIsLoading = false;
    private boolean mRefreshPreview;
    private byte[] newPreview = null;
    private OnLoadingFinished onFinished = null;
    private BitmapDrawable mBitmap = null;
    private int mWidth = 0;
    private int mHeight = 0;

    WCRollingRID(Context context, long aRid,  String aLoc, boolean refreshPreview) {
        mContext = context;
        mRid = aRid;
        mLocation = aLoc;
        mRefreshPreview = refreshPreview;
    }

    public long rid() {return mRid;}
    public String location() {return mLocation;}
    public boolean needToLoad() {
        lock();
        try {
            return !(mIsLoaded || mIsLoading);
        } finally {
            unlock();
        }
    }
    public boolean isLoaded() {
        lock();
        try {
            return mIsLoaded;
        } finally {
            unlock();
        }
    }
    public BitmapDrawable getBitmap() {
        lock();
        try {
            if (mIsLoaded)
                return mBitmap;
            return null;
        } finally {
            unlock();
        }
    }
    public int getHeight() {
        lock();
        try {
            return mHeight;
        } finally {
            unlock();
        }
    }
    public int getWidth() {
        lock();
        try {
            return mWidth;
        } finally {
            unlock();
        }
    }
    public void setFinishedListener(OnLoadingFinished list) {
        lock();
        try {
            onFinished = list;
        } finally {
            unlock();
        }
    }
    public void load() {
        lock();
        try {
            mIsLoaded = false;
            mIsLoading = true;
        } finally {
            unlock();
        }

        Drawable drawing = Drawable.createFromPath(mLocation);

        if (drawing == null)
            drawing = ContextCompat.getDrawable(mContext.getApplicationContext(),
                    android.R.drawable.ic_menu_report_image);
        else {
            if (mRefreshPreview) {
                WCPicPreviewHelper previewHelper = new WCPicPreviewHelper();
                previewHelper.save((BitmapDrawable) drawing);
                newPreview = previewHelper.getBlob();
                ChatDatabase db = ChatDatabase.getInstance(mContext);
                db.setMediaPreview(rid(), newPreview);
            }
        }


        if (drawing != null) {
            Bitmap bitmap = ((BitmapDrawable) drawing).getBitmap();

            int width;
            try {
                width = bitmap.getWidth();
            } catch (NullPointerException e) {
                throw new NoSuchElementException("Can't find bitmap on given view/drawable");
            }

            int height = bitmap.getHeight();

            float scale = WCPicPreviewHelper.getDisplayScale(mContext, width, height);

            Matrix matrix = new Matrix();
            matrix.postScale(scale, scale);

            Bitmap scaledBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
            lock();
            try {
                mWidth = scaledBitmap.getWidth();
                mHeight = scaledBitmap.getHeight();
                mBitmap = new BitmapDrawable(mContext.getResources(), scaledBitmap);
            } finally {
                unlock();
            }
        }

        lock();
        try {
            mIsLoaded = true;
            mIsLoading = false;
            //send event
            if (onFinished != null) {
                onFinished.finished(this);
            }
        } finally {
            unlock();
        }
    }

    public boolean needRefreshPreview() { return mRefreshPreview; }

    public byte[] getPreview() { return newPreview;}

    public void lock() {
        lock.lock();
    }

    public void unlock() {
        lock.unlock();
    }
}
