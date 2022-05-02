package com.sggdev.wcwebcameracontrol;

import static com.sggdev.wcwebcameracontrol.ChatDatabase.MSG_STATE_UNKNOWN;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.JSON_MSG;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.JSON_PARAMS;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.JSON_RID;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.locks.ReentrantLock;

public class WCChat {

    public final static String MEDIA_DIR = "media";
    public final static String MEDIA_PREFIX = "record_";


    public static class ChatMedia {
        private final static int MAX_RETRY_MEDIA_LOAD_COUNT = 5;
        private final ReentrantLock lock = new ReentrantLock();

        private final long dbId;
        private long sender = -1;
        private long rid = 0;
        private String location = "";
        private String meta = "";
        private boolean loading = false;
        private boolean complete = false;
        private byte[] blob;
        private int retry_cnt = 0;

        ChatMedia(long aDbId) {
            dbId = aDbId;
        }

        boolean isLoading() {
            lock();
            try {
                return loading;
            } finally {
                unlock();
            }
        }
        boolean isComplete() {
            lock();
            try {
                return complete;
            } finally {
                unlock();
            }
        }
        String getLocation() {
            lock();
            try {
                return location;
            } finally {
                unlock();
            }
        }
        String getMetaData() { return meta; }
        long getServerRID() { return rid; }
        long getSender() { return sender; }
        long getDbId() { return dbId; }
        byte[] getPreview() {
            lock();
            try {
                return blob;
            } finally {
                unlock();
            }
        }
        boolean hasPreview() {
            if (blob == null) return false;
            return blob.length != 0;
        }

        void setServerRID(long arid) { rid = arid; }
        void setLocation(String aloc) {
            lock();
            try {
                location = aloc;
            } finally {
                unlock();
            }
        }
        void setMetaData(String ameta) { meta = ameta; }
        void setSender(long asender) { sender = asender; }
        void setPreview(byte[] ablob) {
            lock();
            try {
                blob = ablob;
            } finally {
                unlock();
            }
        }
        boolean startLoading() {
            lock();
            try {
                if (loading) return false;
                if (retry_cnt < MAX_RETRY_MEDIA_LOAD_COUNT) {
                    retry_cnt++;
                    loading = true;
                }
                return loading;
            } finally {
                unlock();
            }
        }
        void finishLoading() {
            lock();
            try {
                loading = false;
            } finally {
                unlock();
            }
        }
        void setComplete() {
            lock();
            try {
                complete = true;
                retry_cnt = 0;
            } finally {
                unlock();
            }
        }

        void lock() {
            lock.lock();
        }

        void unlock() {
            lock.unlock();
        }

        boolean saveMedia(Context context, byte [] raw) {
            WCApp myApp = (WCApp) context.getApplicationContext();
            File mediaDir = myApp.getDir(MEDIA_DIR, Context.MODE_PRIVATE);
            File userMediaDir = new File(mediaDir, myApp.getHttpCfgUserName());
            if (!userMediaDir.exists()) {
                if (!userMediaDir.mkdir()) return false;
            }
            File mediaData = new File(userMediaDir, WCChat.MEDIA_PREFIX + getServerRID());
            try {
                if (!mediaData.exists()) {
                    if (!mediaData.createNewFile()) return false;
                }
                String loc = mediaData.getCanonicalPath();
                FileOutputStream fos = new FileOutputStream(mediaData, false);
                fos.write(raw);
                fos.close();

                setLocation(loc);
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }

        boolean isMediaExists(Context context) {
            WCApp myApp = (WCApp) context.getApplicationContext();
            File mediaDir = myApp.getDir(MEDIA_DIR , Context.MODE_PRIVATE);
            File userMediaDir = new File(mediaDir, myApp.getHttpCfgUserName());
            if (!userMediaDir.exists()) {
                if (!userMediaDir.mkdir()) return false;
            }
            File mediaData = new File(userMediaDir, MEDIA_PREFIX + getServerRID());
            return mediaData.exists();
        }
    }

    public static class ChatMessage {
        private final long dbId;
        private String message = "";
        private DeviceItem sender = null;
        private int state = MSG_STATE_UNKNOWN;
        private String target = "";
        private String createdAt = "";
        private String createdAtTime = "";
        private String createdAtDate = "";
        private String msg = "";
        private int rid = -1;
        private JSONObject jsonObject = null;
        private JSONObject jsonParams = null;

        ChatMessage(long aDbId) {
            dbId = aDbId;
        }

        String getRawMessage() { return message; }
        String getMessage() { return msg; }
        String getCreatedAt() { return createdAt; }
        DeviceItem getSender() { return sender; }
        long getDbId() { return dbId; }
        String getTarget() { return target; }
        String getTimeStamp() { return createdAt; }
        String getTime() { return createdAtTime; }
        String getDate() { return createdAtDate; }
        int getState() {return state;}
        JSONObject getJsonObject() {return jsonObject;}
        JSONObject getJsonParams() {return jsonParams;}
        int getRid() {return rid;}

        boolean hasMedia() {
            return (rid >= 0);
        }
        boolean jsonComplete() {
            return (jsonObject != null);
        }
        boolean hasJSONParams() {return (jsonParams != null); }

        void setTarget(String atarget) { target = atarget; }
        void setRawMessage(String amsg) {
            message = amsg;
            try {
                jsonObject = new JSONObject(amsg);
                rid = jsonObject.optInt(JSON_RID, -1);
                msg = jsonObject.optString(JSON_MSG, "");
                Object params = jsonObject.opt(JSON_PARAMS);
                if (params instanceof JSONObject) jsonParams = (JSONObject)params;
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        void setSender(DeviceItem asender) { sender = asender; }
        void setState(int aState) { state = aState; }
        void setTimeStamp(String astamp) {
            createdAt = astamp;
            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH);
            df.setTimeZone(TimeZone.getTimeZone("UTC"));

            SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMMM", Locale.getDefault());
            dateFormat.setTimeZone(TimeZone.getDefault());

            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
            timeFormat.setTimeZone(TimeZone.getDefault());

            try {
                Date datetime = df.parse(astamp);
                if (datetime != null) {
                    createdAtDate = dateFormat.format(datetime);
                    createdAtTime = timeFormat.format(datetime);
                }
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }
    }

    static class DeviceMsgsCnt {
        private final int mCnt;
        private final String mName;
        private final long mDbId;
        private final String mLstMsgTimeStamp;

        DeviceMsgsCnt(long dbId, String aName, int cnt, String aLstMsgTimeStamp) {
            mCnt = cnt;
            mName = aName;
            mDbId = dbId;
            mLstMsgTimeStamp = aLstMsgTimeStamp;
        }

        String getName() {return mName;}
        int getCnt() {return mCnt;}
        long getDbId() {return mDbId;}
        String getLstMsgTimeStamp() {return mLstMsgTimeStamp;}
    }
}
