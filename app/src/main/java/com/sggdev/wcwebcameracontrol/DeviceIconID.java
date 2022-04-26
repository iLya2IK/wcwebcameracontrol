package com.sggdev.wcwebcameracontrol;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

public class DeviceIconID extends Drawable {
    private final Paint mPaint;

    private final int mColor;
    private final int mDigit;

    private final Path mPath;
    private final Path mPathGlance;

    private float mX;
    private float mY;

    public int getBleDigit() {return mDigit;}
    public int getBleColor() {return mColor;}

    public DeviceIconID(int color, int digit) {
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setAntiAlias(true);
        mPaint.setTypeface(Typeface.MONOSPACE);
        mPaint.setTextAlign(Paint.Align.CENTER);
        mPaint.setTextSize(35f);

        mPath = new Path();
        mPath.setFillType(Path.FillType.EVEN_ODD);

        mPathGlance = new Path();
        mPathGlance.setFillType(Path.FillType.EVEN_ODD);

        mColor = color;
        mDigit = digit;
    }

    private final Rect textBounds = new Rect();

    @Override
    public void draw(Canvas canvas) {
        mPaint.setColor(mColor);
        canvas.drawPath(mPath, mPaint);
        mPaint.setColor(Color.parseColor("#000000"));
        mPaint.getTextBounds(Integer.toString(mDigit), 0, Integer.toString(mDigit).length(), textBounds);
        canvas.drawText(Integer.toString(mDigit), mX, mY - textBounds.exactCenterY(), mPaint);
        int a = mPaint.getAlpha();
        mPaint.setColor(Color.parseColor("#ffffff"));
        mPaint.setAlpha(a / 2);
        canvas.drawPath(mPathGlance, mPaint);
    }

    @Override
    public void setAlpha(int alpha) {
        mPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        mPaint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        mPath.reset();
        mPathGlance.reset();

        mX = (float)(bounds.left + bounds.right) * 0.5f;
        mY = (float)(bounds.top + bounds.bottom) * 0.5f;
        float radius = Math.min((float)(bounds.right - bounds.left),
                (float)(bounds.bottom - bounds.top)) * 0.5f;

        mPaint.setTextSize(radius * 1.25f);

        mPath.addCircle(mX, mY, radius, Path.Direction.CW);
        mPathGlance.addCircle(mX, mY, radius * 0.9f, Path.Direction.CW);

        Path p1 = new Path();
        p1.addCircle(mX + radius * 0.5f, mY + radius * 0.5f, radius * 1.25f, Path.Direction.CW);

        mPathGlance.op(mPathGlance, p1, Path.Op.DIFFERENCE);
    }
}
