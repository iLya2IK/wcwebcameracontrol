package com.sggdev.wcwebcameracontrol;

import android.app.Activity;

import java.util.TimerTask;

public class TimerJob extends TimerTask {

    public interface OnJobExecute{
        public void doExecute();
    }

    private final Activity mActivity;
    private OnValueChangedListener onFinish;
    private OnJobExecute onJobExecute;

    TimerJob(Activity activity) {
        mActivity = activity;
    }
    TimerJob(Activity activity, TimerJob.OnJobExecute aOnExecute,
             OnValueChangedListener aOnFinish) {
        mActivity = activity;
        setOnJobExecute(aOnExecute);
        setOnFinishListener(aOnFinish);
    }

    Activity getActivity() {
        return mActivity;
    }

    void setOnJobExecute(OnJobExecute rf) {
        onJobExecute = rf;
    }
    void setOnFinishListener(OnValueChangedListener rf) {
        onFinish = rf;
    }

    private void onPostExecute() {
        if (onFinish != null) {
            onFinish.onChange();
        }
    }

    @Override
    public void run() {
        onJobExecute.doExecute();
        mActivity.runOnUiThread(new Thread(this::onPostExecute));
    }
}
