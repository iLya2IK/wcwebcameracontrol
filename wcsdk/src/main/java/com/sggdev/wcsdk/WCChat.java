package com.sggdev.wcsdk;

import static com.sggdev.wcsdk.ChatDatabase.MSG_STATE_UNKNOWN;
import static com.sggdev.wcsdk.WCRESTProtocol.JSON_MSG;
import static com.sggdev.wcsdk.WCRESTProtocol.JSON_PARAMS;
import static com.sggdev.wcsdk.WCRESTProtocol.JSON_RID;

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

        public boolean isLoading() {
            lock();
            try {
                return loading;
            } finally {
                unlock();
            }
        }
        public boolean isComplete() {
            lock();
            try {
                return complete;
            } finally {
                unlock();
            }
        }
        public String getLocation() {
            lock();
            try {
                return location;
            } finally {
                unlock();
            }
        }
        public String getMetaData() { return meta; }
        public long getServerRID() { return rid; }
        public long getSender() { return sender; }
        public long getDbId() { return dbId; }
        public byte[] getPreview() {
            lock();
            try {
                return blob;
            } finally {
                unlock();
            }
        }
        public boolean hasPreview() {
            if (blob == null) return false;
            return blob.length != 0;
        }

        public void setServerRID(long arid) { rid = arid; }
        public void setLocation(String aloc) {
            lock();
            try {
                location = aloc;
            } finally {
                unlock();
            }
        }
        public void setMetaData(String ameta) { meta = ameta; }
        public void setSender(long asender) { sender = asender; }
        public void setPreview(byte[] ablob) {
            lock();
            try {
                blob = ablob;
            } finally {
                unlock();
            }
        }
        public boolean startLoading() {
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
        public void finishLoading() {
            lock();
            try {
                loading = false;
            } finally {
                unlock();
            }
        }
        public void setComplete() {
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

        public boolean saveMedia(Context context, byte [] raw) {
            WCAppCommon myApp = (WCAppCommon) context.getApplicationContext();
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

        public boolean isMediaExists(Context context) {
            WCAppCommon myApp = (WCAppCommon) context.getApplicationContext();
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
        private String mServerStamp = "";
        private String msg = "";
        private long rid = -1;
        private JSONObject jsonObject = null;
        private JSONObject jsonParams = null;

        public ChatMessage(long aDbId) {
            dbId = aDbId;
        }

        public String getRawMessage() { return message; }
        public String getMessage() { return msg; }
        public String getCreatedAt() { return createdAt; }
        public DeviceItem getSender() { return sender; }
        public long getDbId() { return dbId; }
        public String getTarget() { return target; }
        public String getTimeStamp() { return createdAt; }
        public String getServerTimeStamp() { return mServerStamp; }
        public String getTime() { return createdAtTime; }
        public String getDate() { return createdAtDate; }
        public int getState() {return state;}
        public JSONObject getJsonObject() {return jsonObject;}
        public JSONObject getJsonParams() {return jsonParams;}
        public long getRid() {return rid;}

        public boolean hasMedia() {
            return (rid >= 0);
        }
        public boolean jsonComplete() {
            return (jsonObject != null);
        }
        public boolean hasJSONParams() {return (jsonParams != null); }

        public void setTarget(String atarget) { target = atarget; }
        public void setRawMessage(String amsg) {
            message = amsg;
            try {
                jsonObject = new JSONObject(amsg);
                rid = jsonObject.optLong(JSON_RID, -1);
                msg = jsonObject.optString(JSON_MSG, "");
                Object params = jsonObject.opt(JSON_PARAMS);
                if (params instanceof JSONObject) jsonParams = (JSONObject)params;
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        public void setSender(DeviceItem asender) { sender = asender; }
        public void setState(int aState) { state = aState; }
        public void setServerTimeStamp(String stamp) {
            mServerStamp = stamp;
        }
        public void setTimeStamp(String astamp) {
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

    public static class DeviceMsgsCnt {
        private final int mCnt;
        private final String mName;
        private final long mDbId;
        private final String mLstMsgTimeStamp;

        public DeviceMsgsCnt(long dbId, String aName, int cnt, String aLstMsgTimeStamp) {
            mCnt = cnt;
            mName = aName;
            mDbId = dbId;
            mLstMsgTimeStamp = aLstMsgTimeStamp;
        }

        public String getName() {return mName;}
        public int getCnt() {return mCnt;}
        public long getDbId() {return mDbId;}
        public String getLstMsgTimeStamp() {return mLstMsgTimeStamp;}
    }
}
