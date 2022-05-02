package com.sggdev.wcwebcameracontrol;

import static com.sggdev.wcwebcameracontrol.DeviceItem.DEFAULT_DEVICE_COLOR;
import static com.sggdev.wcwebcameracontrol.DeviceItem.DEVICE_ITEM_BLE_ADDRESS;
import static com.sggdev.wcwebcameracontrol.DeviceItem.DEVICE_ITEM_BLE_NAME;
import static com.sggdev.wcwebcameracontrol.DeviceItem.DEVICE_ITEM_CHAR;
import static com.sggdev.wcwebcameracontrol.DeviceItem.DEVICE_ITEM_COLOR;
import static com.sggdev.wcwebcameracontrol.DeviceItem.DEVICE_ITEM_DB_ID;
import static com.sggdev.wcwebcameracontrol.DeviceItem.DEVICE_ITEM_INDEX;
import static com.sggdev.wcwebcameracontrol.DeviceItem.DEVICE_ITEM_SERVER_NAME;
import static com.sggdev.wcwebcameracontrol.DeviceItem.DEVICE_ITEM_TIME_STAMP;
import static com.sggdev.wcwebcameracontrol.DeviceItem.DEVICE_ITEM_UNREAD_MSGS;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.JSON_DEVICE;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.JSON_MSG;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.JSON_PARAMS;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.JSON_RID;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.JSON_STAMP;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.JSON_TARGET;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ChatDatabase extends SQLiteOpenHelper {
    private static ChatDatabase sInstance;

    public static final int MSG_STATE_UNKNOWN = 0;
    public static final int MSG_STATE_RECIEVED = 1;
    public static final int MSG_STATE_READY_TO_SEND = 2;
    public static final int MSG_STATE_SENDED = 3;

    private static final String TAG = "ChatDatabase";

    // Database Info
    private static final String DATABASE_NAME = "chatDatabase";
    private static final int DATABASE_VERSION = 3;

    // Table Names
    private static final String TABLE_DEVICES = "devices";
    private static final String TABLE_MSGS = "msgs";
    private static final String TABLE_MEDIA = "media";

    // Msg Table Columns
    private static final String KEY_MSG_ID = "mid";
    private static final String KEY_MSG_DEVICE_ID_FK = "devId";
    private static final String KEY_MSG_TARGET = "target";
    private static final String KEY_MSG_TEXT = "text";
    private static final String KEY_MSG_STATE = "state";
    private static final String KEY_MSG_TIMESTAMP = "stamp";

    //Media Table Columns
    private static final String KEY_MEDIA_ID = "medId";
    private static final String KEY_MEDIA_DEVICE_ID_FK = "devId";
    private static final String KEY_MEDIA_RID = "rid";
    private static final String KEY_MEDIA_META = "meta";
    private static final String KEY_MEDIA_LOC = "loc";
    private static final String KEY_MEDIA_PREVIEW = "preview";

    public static synchronized ChatDatabase getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new ChatDatabase(context.getApplicationContext());
        }
        return sInstance;
    }

    private ChatDatabase(@Nullable Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
        // init statements

    }

    private void createDevicesTable(SQLiteDatabase sqLiteDatabase) {
        sqLiteDatabase.execSQL("CREATE TABLE if not exists " + TABLE_DEVICES +
                "("+
                DEVICE_ITEM_DB_ID + " integer PRIMARY KEY AUTOINCREMENT," +
                DEVICE_ITEM_SERVER_NAME + " text default '', "+
                DEVICE_ITEM_BLE_NAME + " text default '', "+
                DEVICE_ITEM_BLE_ADDRESS + " text default '', "+
                DEVICE_ITEM_CHAR + " text default '', "+
                DEVICE_ITEM_INDEX + " integer default 0, "+
                DEVICE_ITEM_COLOR + " integer default " + Integer.valueOf(DEFAULT_DEVICE_COLOR).toString() + ", "+
                DEVICE_ITEM_TIME_STAMP + " text default current_timestamp " +
                ");");
    }

    private void createMsgsTable(SQLiteDatabase sqLiteDatabase) {
        sqLiteDatabase.execSQL("CREATE TABLE if not exists " + TABLE_MSGS +
                "("+
                KEY_MSG_ID + " integer PRIMARY KEY AUTOINCREMENT," +
                KEY_MSG_DEVICE_ID_FK + " integer references " + TABLE_DEVICES + "(" + DEVICE_ITEM_DB_ID + ") on delete cascade, " +
                KEY_MSG_TARGET + " text default ''," +
                KEY_MSG_TEXT + " text default '', "+
                KEY_MSG_STATE + " integer default " + Integer.valueOf(MSG_STATE_UNKNOWN).toString() + ", "+
                KEY_MSG_TIMESTAMP + " text default current_timestamp " +
                ");");
    }

    private void createMediaTable(SQLiteDatabase sqLiteDatabase) {
        sqLiteDatabase.execSQL("CREATE TABLE if not exists " + TABLE_MEDIA +
                "("+
                KEY_MEDIA_ID + " integer PRIMARY KEY AUTOINCREMENT," +
                KEY_MEDIA_DEVICE_ID_FK + " integer references " + TABLE_DEVICES + "(" + DEVICE_ITEM_DB_ID + ") on delete cascade, " +
                KEY_MEDIA_RID + " integer default 0, "+
                KEY_MEDIA_META + " text default '', "+
                KEY_MEDIA_LOC + " text default '', "+
                KEY_MEDIA_PREVIEW + " blob" +
                ");");
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        createDevicesTable(sqLiteDatabase);
        createMsgsTable(sqLiteDatabase);
        createMediaTable(sqLiteDatabase);
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int oldVersion, int newVersion) {
        if (oldVersion != newVersion) {
            if ((oldVersion == 2) && (newVersion == 3)) {
                createMediaTable(sqLiteDatabase);
            } else
            if ((oldVersion == 1) && (newVersion == 2)) {
                sqLiteDatabase.execSQL("ALTER TABLE " + TABLE_MSGS +
                        " ADD COLUMN " + KEY_MSG_STATE + " integer default " +
                        Integer.valueOf(MSG_STATE_UNKNOWN).toString() + ";");
            } else {
                sqLiteDatabase.execSQL("DROP TABLE IF EXISTS " + TABLE_DEVICES);
                sqLiteDatabase.execSQL("DROP TABLE IF EXISTS " + TABLE_MSGS);
                onCreate(sqLiteDatabase);
            }
        }
    }

    public void updateDeviceTimeStamp(DeviceItem device) {
        SQLiteDatabase db = getWritableDatabase();

        db.beginTransaction();
        try {
            ContentValues values = new ContentValues();
            values.put(DEVICE_ITEM_TIME_STAMP, device.getLstSync());

            ArrayList<String> whereArgs = new ArrayList<>();
            StringBuilder whereClause = new StringBuilder();

            if (device.getDbId() > 0) {
                whereClause.append(DEVICE_ITEM_DB_ID);
                whereClause.append("=");
                whereClause.append(device.getDbId());
            } else {
                if (device.getDeviceServerName().length() > 0) {
                    whereClause.append(DEVICE_ITEM_SERVER_NAME);
                    whereClause.append("=?");
                    whereArgs.add(device.getDeviceServerName());
                }
                if (device.getDeviceBLEAddress().length() > 0) {
                    if (whereClause.length() > 0) {
                        whereClause.append(" or ");
                    }
                    whereClause.append(DEVICE_ITEM_BLE_ADDRESS);
                    whereClause.append("=?");
                    whereArgs.add(device.getDeviceBLEAddress());
                }
            }

            db.update(TABLE_DEVICES, values, whereClause.toString(),
                    whereArgs.toArray(new String[]{}));

            db.setTransactionSuccessful();
        } catch (Exception e) {
            Log.d(TAG, "Error while trying to update timestamp for device");
            e.printStackTrace();
        } finally {
            db.endTransaction();
        }
    }

    public void addOrUpdateDevices(List<DeviceItem> items) {
        SQLiteDatabase db = getWritableDatabase();

        for (DeviceItem device : items) {
            long deviceId = -1;
            db.beginTransaction();
            try {
                ContentValues values = new ContentValues();
                values.put(DEVICE_ITEM_SERVER_NAME, device.getDeviceServerName());
                values.put(DEVICE_ITEM_BLE_NAME, device.getDeviceBLEName());
                values.put(DEVICE_ITEM_BLE_ADDRESS, device.getDeviceBLEAddress());
                values.put(DEVICE_ITEM_CHAR, device.getDeviceWriteChar());
                values.put(DEVICE_ITEM_COLOR, device.getDeviceItemColor());
                values.put(DEVICE_ITEM_INDEX, device.getDeviceItemIndex());
                values.put(DEVICE_ITEM_TIME_STAMP, device.getLstSync());

                ArrayList<String> whereArgs = new ArrayList<>();
                StringBuilder whereClause = new StringBuilder();

                if (device.getDbId() > 0) {
                    whereClause.append(DEVICE_ITEM_DB_ID);
                    whereClause.append("=");
                    whereClause.append(device.getDbId());
                } else {
                    if (device.getDeviceServerName().length() > 0) {
                        whereClause.append(DEVICE_ITEM_SERVER_NAME);
                        whereClause.append("=?");
                        whereArgs.add(device.getDeviceServerName());
                    }
                    if (device.getDeviceBLEAddress().length() > 0) {
                        if (whereClause.length() > 0) {
                            whereClause.append(" or ");
                        }
                        whereClause.append(DEVICE_ITEM_BLE_ADDRESS);
                        whereClause.append("=?");
                        whereArgs.add(device.getDeviceBLEAddress());
                    }
                }

                int rows;

                rows = db.update(TABLE_DEVICES, values, whereClause.toString(),
                        whereArgs.toArray(new String[]{}));

                // Check if update succeeded
                if (rows == 1) {
                    String usersSelectQuery = String.format("SELECT %s FROM %s WHERE %s",
                            DEVICE_ITEM_DB_ID, TABLE_DEVICES, whereClause);
                    Cursor cursor = db.rawQuery(usersSelectQuery, whereArgs.toArray(new String[]{}));
                    try {
                        if (cursor.moveToFirst()) {
                            deviceId = cursor.getLong(0);
                            db.setTransactionSuccessful();
                        }
                    } finally {
                        if (cursor != null && !cursor.isClosed()) {
                            cursor.close();
                        }
                    }
                } else {
                    db.insertOrThrow(TABLE_DEVICES, null, values);
                    String usersSelectQuery = String.format("SELECT max(%s) FROM %s",
                            DEVICE_ITEM_DB_ID, TABLE_DEVICES);
                    Cursor cursor = db.rawQuery(usersSelectQuery, null);
                    try {
                        if (cursor.moveToFirst()) {
                            deviceId = cursor.getLong(0);
                            db.setTransactionSuccessful();
                        }
                    } finally {
                        if (cursor != null && !cursor.isClosed()) {
                            cursor.close();
                        }
                    }
                }
            } catch (Exception e) {
                Log.d(TAG, "Error while trying to add or update device");
                e.printStackTrace();
            } finally {
                db.endTransaction();
            }
            if (deviceId > 0) device.setDbId(deviceId);
        }
    }

    List<JSONObject> getAllDevices() {
        List<JSONObject> res = new ArrayList<>();

        @SuppressLint("DefaultLocale") String POSTS_SELECT_QUERY =
                String.format("select * from %s;", TABLE_DEVICES);

        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery(POSTS_SELECT_QUERY, null);
        try {
            if (cursor.moveToFirst()) {
                int [] cInt = {
                        cursor.getColumnIndex(DEVICE_ITEM_DB_ID),
                        cursor.getColumnIndex(DEVICE_ITEM_SERVER_NAME),
                        cursor.getColumnIndex(DEVICE_ITEM_BLE_NAME),
                        cursor.getColumnIndex(DEVICE_ITEM_BLE_ADDRESS),
                        cursor.getColumnIndex(DEVICE_ITEM_CHAR),
                        cursor.getColumnIndex(DEVICE_ITEM_COLOR),
                        cursor.getColumnIndex(DEVICE_ITEM_INDEX),
                        cursor.getColumnIndex(DEVICE_ITEM_TIME_STAMP)
                };
                do {
                    try {
                        JSONObject cached_item = new JSONObject();
                        cached_item.put(DEVICE_ITEM_DB_ID, cursor.getLong(cInt[0]));
                        cached_item.put(DEVICE_ITEM_SERVER_NAME, cursor.getString(cInt[1]));
                        cached_item.put(DEVICE_ITEM_BLE_NAME, cursor.getString(cInt[2]));
                        cached_item.put(DEVICE_ITEM_BLE_ADDRESS, cursor.getString(cInt[3]));
                        cached_item.put(DEVICE_ITEM_CHAR, cursor.getString(cInt[4]));
                        cached_item.put(DEVICE_ITEM_COLOR, cursor.getInt(cInt[5]));
                        cached_item.put(DEVICE_ITEM_INDEX, cursor.getInt(cInt[6]));
                        cached_item.put(DEVICE_ITEM_TIME_STAMP, cursor.getString(cInt[7]));
                        cached_item.put(DEVICE_ITEM_UNREAD_MSGS, 0);
                        res.add(cached_item);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                } while(cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.d(TAG, "Error while trying to get devices from database");
        } finally {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        }

        return res;
    }

    void sendMsg(DeviceItem sender, DeviceItem target, String msg, JSONObject params) {
        try {
            JSONObject obj = new JSONObject();
            obj.put(JSON_DEVICE, sender.getDeviceServerName());
            obj.put(JSON_TARGET, target.getDeviceServerName());
            obj.put(JSON_MSG, msg);
            obj.put(JSON_PARAMS, params);

            addMsgs(new ArrayList<JSONObject>() {{add(obj);}}, MSG_STATE_READY_TO_SEND);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    String addMsgs(List<JSONObject> items, int msgState) {
        String lst = "";

        if (items.size() == 0) return lst;

        SQLiteDatabase db = getWritableDatabase();

        StringBuilder sb = new StringBuilder();
        sb.append("with tn (d, trg, txt, sta, stm) as (values ");
        try {
            boolean nxt = false;
            for (JSONObject msg : items) {
                String device = msg.optString(JSON_DEVICE);
                String target = msg.optString(JSON_TARGET, "");
                String time_stamp = msg.optString(JSON_STAMP, "");
                String msg_text = msg.optString(JSON_MSG, "");
                long rid = msg.optLong(JSON_RID, -1);
                Object params = msg.opt(JSON_PARAMS);

                JSONObject text = new JSONObject();
                if (rid >= 0) text.put(JSON_RID, rid);
                if (msg_text.length() > 0) text.put(JSON_MSG, msg_text);
                text.put(JSON_PARAMS, params);

                if (nxt) sb.append(",");

                sb.append("('"); sb.append(device.replaceAll("'","''")); sb.append("',");
                sb.append("'"); sb.append(target.replaceAll("'","''")); sb.append("',");
                sb.append("'"); sb.append(text.toString().replaceAll("'","''")); sb.append("',");
                sb.append(msgState); sb.append(",");
                if (time_stamp.length() == 0) {
                    sb.append("current_timestamp)");
                } else {
                    sb.append("'");
                    sb.append(time_stamp);
                    sb.append("')");
                }

                lst = time_stamp;
                nxt = true;
            }
            sb.append(String.format(") insert into %s (%s, %s, %s, %s, %s) ",
                    TABLE_MSGS, KEY_MSG_DEVICE_ID_FK, KEY_MSG_TARGET, KEY_MSG_TEXT, KEY_MSG_STATE, KEY_MSG_TIMESTAMP));
            sb.append(String.format("select %s.%s, tn.trg, tn.txt, tn.sta, tn.stm from tn INNER JOIN %s on %s.%s == tn.d;",
                    TABLE_DEVICES, DEVICE_ITEM_DB_ID, TABLE_DEVICES, TABLE_DEVICES, DEVICE_ITEM_SERVER_NAME));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        try {
            db.execSQL(sb.toString());
        } catch (Exception e) {
            lst = "";
            Log.d(TAG, "Error while trying to add messages");
            e.printStackTrace();
        }

        return lst;
    }

    void setMediaPreview(WCChat.ChatMedia media, byte[] ablob) {
        SQLiteDatabase db = getWritableDatabase();

        db.beginTransaction();
        try {
            ArrayList<String> whereArgs = new ArrayList<>();
            whereArgs.add(Long.toString(media.getDbId()));
            ContentValues values = new ContentValues();
            values.put(KEY_MEDIA_PREVIEW, ablob);
            String whereClause = KEY_MEDIA_ID + "=?";
            db.update(TABLE_MEDIA, values, whereClause,
                    whereArgs.toArray(new String[]{}));
            db.setTransactionSuccessful();
        } catch (Exception e) {
            Log.d(TAG, "Error while trying to update media record");
            e.printStackTrace();
        } finally {
            db.endTransaction();
        }
    }

    void setMediaPreview(long rid, byte[] ablob) {
        SQLiteDatabase db = getWritableDatabase();

        db.beginTransaction();
        try {
            ArrayList<String> whereArgs = new ArrayList<>();
            whereArgs.add(Long.toString(rid));
            ContentValues values = new ContentValues();
            values.put(KEY_MEDIA_PREVIEW, ablob);
            String whereClause = KEY_MEDIA_RID + "=?";
            db.update(TABLE_MEDIA, values, whereClause,
                    whereArgs.toArray(new String[]{}));
            db.setTransactionSuccessful();
        } catch (Exception e) {
            Log.d(TAG, "Error while trying to update media record");
            e.printStackTrace();
        } finally {
            db.endTransaction();
        }
    }

    void getMediaPreview(WCChat.ChatMedia media) {
        SQLiteDatabase db = getReadableDatabase();
        String SELECT_QUERY = String.format("select %s from %s where %s == ?;",
                TABLE_MEDIA, KEY_MEDIA_PREVIEW, KEY_MEDIA_ID);
        Cursor cursor = db.rawQuery(SELECT_QUERY, new String[] {Long.toString(media.getDbId())});
        try {
            if (cursor.moveToFirst()) {
                media.setPreview(cursor.getBlob(0));
            }
        } catch (Exception e) {
            Log.d(TAG, "Error while trying to get blob from database");
        } finally {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        }
    }

    public static final int MEDIA_META = 1;
    public static final int MEDIA_LOC = 2;

    @SuppressLint("DefaultLocale")
    void updateMedia(WCChat.ChatMedia item, int updateMask) {
        if (updateMask == 0) return;

        SQLiteDatabase db = getWritableDatabase();

        StringBuilder sb = new StringBuilder();
        sb.append("update ");
        sb.append(TABLE_MEDIA);
        boolean nxt = false;
        if ((updateMask & MEDIA_LOC) != 0) {
            sb.append(" set ");
            sb.append(KEY_MEDIA_LOC);
            sb.append("='");
            sb.append(item.getLocation().replaceAll("'","''"));
            sb.append("' ");
            nxt = true;
        }
        if ((updateMask & MEDIA_META) != 0) {
            if (!nxt)
                sb.append(" set ");
            sb.append(KEY_MEDIA_META);
            sb.append("='");
            sb.append(item.getMetaData().replaceAll("'","''"));
            sb.append("' ");
        }

        sb.append(String.format("where %s == %d;", KEY_MEDIA_RID, item.getServerRID()));

        try {
            db.execSQL(sb.toString());
        } catch (Exception e) {
            Log.d(TAG, "Error while trying to update media");
            e.printStackTrace();
        }
    }

    void addMedia(List<WCChat.ChatMedia> items) {
        if (items.size() == 0) return;

        SQLiteDatabase db = getWritableDatabase();

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("insert into %s (%s, %s, %s, %s) values ",
                TABLE_MEDIA, KEY_MEDIA_DEVICE_ID_FK, KEY_MEDIA_RID, KEY_MEDIA_META, KEY_MEDIA_LOC));
        boolean nxt = false;
        for (WCChat.ChatMedia item : items) {
            if (item.getSender() >= 0) {
                if (nxt) sb.append(",");

                sb.append("(");
                sb.append(item.getSender());
                sb.append(",");
                sb.append(item.getServerRID());
                sb.append(",'");
                sb.append(item.getMetaData().replaceAll("'","''"));
                sb.append("','");
                sb.append(item.getLocation().replaceAll("'","''"));
                sb.append("')");

                nxt = true;
            }
        }

        try {
            db.execSQL(sb.toString());
        } catch (Exception e) {
            Log.d(TAG, "Error while trying to add media records");
            e.printStackTrace();
        }
    }

    @SuppressLint("DefaultLocale")
    List<WCChat.ChatMedia> getMedia(List<Integer> rids) {
        if (rids.size() == 0) return null;

        StringBuilder MEDIA_SELECT_QUERY = new StringBuilder();
        MEDIA_SELECT_QUERY.append("with atable as (values ");
        boolean nxt = false;
        for (Integer id : rids) {
            if (nxt) MEDIA_SELECT_QUERY.append(",");
            MEDIA_SELECT_QUERY.append(String.format("(%d)", id));
            nxt = true;
        }

        MEDIA_SELECT_QUERY.append(String.format(
                        ") select * from %s where %s in atable;", TABLE_MEDIA, KEY_MEDIA_RID));

        SQLiteDatabase db = getReadableDatabase();

        List<WCChat.ChatMedia> aMediaList = new ArrayList<>();
        Cursor cursor = db.rawQuery(MEDIA_SELECT_QUERY.toString(), null);
        try {
            if (cursor.moveToFirst()) {
                int [] cInt = {
                        cursor.getColumnIndex(KEY_MEDIA_ID),
                        cursor.getColumnIndex(KEY_MEDIA_LOC),
                        cursor.getColumnIndex(KEY_MEDIA_META),
                        cursor.getColumnIndex(KEY_MEDIA_PREVIEW),
                        cursor.getColumnIndex(KEY_MEDIA_RID),
                        cursor.getColumnIndex(KEY_MEDIA_DEVICE_ID_FK)
                };
                do {
                    long recID = cursor.getLong(cInt[0]);
                    String loc = cursor.getString(cInt[1]);
                    String meta = cursor.getString(cInt[2]);
                    byte[] preview = cursor.getBlob(cInt[3]);
                    long rid = cursor.getLong(cInt[4]);
                    int devId = cursor.getInt(cInt[5]);

                    WCChat.ChatMedia aMedia = new WCChat.ChatMedia(recID);
                    aMedia.setSender(devId);
                    aMedia.setLocation(loc);
                    aMedia.setServerRID(rid);
                    aMedia.setMetaData(meta);
                    aMedia.setPreview(preview);
                    aMediaList.add(aMedia);
                } while(cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.d(TAG, "Error while trying to get media from database");
            e.printStackTrace();
        } finally {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        }
        return aMediaList;
    }


    boolean getUnsentMsgs(DeviceItem aUser, List<WCChat.ChatMessage> messageList, int limit) {
        int added = 0;
        @SuppressLint("DefaultLocale") String POSTS_SELECT_QUERY =
                String.format("select * from %s where %s == %d and %s == %d limit %d;",
                        TABLE_MSGS, KEY_MSG_DEVICE_ID_FK, aUser.getDbId(),
                        KEY_MSG_STATE, MSG_STATE_READY_TO_SEND, limit
                );

        SQLiteDatabase db = getReadableDatabase();

        Cursor cursor = db.rawQuery(POSTS_SELECT_QUERY, null);
        try {
            if (cursor.moveToFirst()) {
                int [] cInt = {
                        cursor.getColumnIndex(KEY_MSG_ID),
                        cursor.getColumnIndex(KEY_MSG_TEXT),
                        cursor.getColumnIndex(KEY_MSG_TIMESTAMP),
                        cursor.getColumnIndex(KEY_MSG_TARGET),
                        cursor.getColumnIndex(KEY_MSG_STATE)
                };
                do {
                    long msgID = cursor.getLong(cInt[0]);
                    String txt = cursor.getString(cInt[1]);
                    String stamp = cursor.getString(cInt[2]);
                    String msgTrgDevice = cursor.getString(cInt[3]);
                    int state = cursor.getInt(cInt[4]);

                    WCChat.ChatMessage newPost = new WCChat.ChatMessage(msgID);
                    newPost.setSender(aUser);
                    newPost.setState(state);
                    newPost.setRawMessage(txt);
                    newPost.setTimeStamp(stamp);
                    newPost.setTarget(msgTrgDevice);
                    messageList.add(newPost);
                    added++;
                } while(cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.d(TAG, "Error while trying to get unsent messages from database");
            e.printStackTrace();
        } finally {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        }
        return (added > 0);
    }

    void setMsgsState(List<WCChat.ChatMessage> list, int newState) {
        SQLiteDatabase db = getWritableDatabase();

        db.beginTransaction();
        try {
            ArrayList<String> whereArgs = new ArrayList<>();
            StringBuilder whereClause = new StringBuilder();
            whereClause.append(KEY_MSG_ID);
            whereClause.append(" in (");
            int cnt = 0;
            for (WCChat.ChatMessage msg : list) {
                if (msg.getDbId() > 0) {
                    if (cnt > 0)
                        whereClause.append(",?");
                    else
                        whereClause.append("?");
                    cnt++;

                    whereArgs.add(Long.toString(msg.getDbId()));
                }
            }
            if (cnt > 0) {
                whereClause.append(")");
                ContentValues values = new ContentValues();
                values.put(KEY_MSG_STATE, newState);
                db.update(TABLE_MSGS, values, whereClause.toString(),
                                                    whereArgs.toArray(new String[]{}));
                db.setTransactionSuccessful();
            }
        } catch (Exception e) {
            Log.d(TAG, "Error while trying to update messages");
            e.printStackTrace();
        } finally {
            db.endTransaction();
        }
    }

    @SuppressLint("DefaultLocale")
    boolean checkNewMessages(String aUserName, String aLastCheck, List<WCChat.ChatMessage> aList) {
        int added = 0;
        String POSTS_SELECT_QUERY;
        if (aLastCheck != null && aLastCheck.length() > 0)
            POSTS_SELECT_QUERY =
                String.format("with "+
                                "target1 as (values (''), ('%s')) "+
                                "select %s, %s, %s, %s, %s, %s, %s from %s " +
                                "inner join %s on %s.%s == %s.%s " +
                                "where  (%s > '%s') and (%s > %s.%s) and (%s in target1) "+
                                "order by %s",
                        aUserName.replaceAll("'", "''"),
                        KEY_MSG_ID, KEY_MSG_DEVICE_ID_FK, DEVICE_ITEM_SERVER_NAME, KEY_MSG_STATE, KEY_MSG_TARGET, KEY_MSG_TEXT, KEY_MSG_TIMESTAMP, TABLE_MSGS,
                        TABLE_DEVICES, TABLE_DEVICES, DEVICE_ITEM_DB_ID, TABLE_MSGS, KEY_MSG_DEVICE_ID_FK,
                        KEY_MSG_TIMESTAMP, aLastCheck, KEY_MSG_TIMESTAMP, TABLE_DEVICES, DEVICE_ITEM_TIME_STAMP,
                        KEY_MSG_TARGET, KEY_MSG_TIMESTAMP
                );
        else
            POSTS_SELECT_QUERY =
                    String.format("with "+
                                    "target1 as (values (''), ('%s')) "+
                                    "select %s, %s, %s, %s, %s, %s, %s from %s " +
                                    "inner join %s on %s.%s == %s.%s " +
                                    "where  (%s > %s.%s) and (%s in target1) "+
                                    "order by %s",
                            aUserName.replaceAll("'", "''"),
                            KEY_MSG_ID, KEY_MSG_DEVICE_ID_FK, DEVICE_ITEM_SERVER_NAME, KEY_MSG_STATE, KEY_MSG_TARGET, KEY_MSG_TEXT, KEY_MSG_TIMESTAMP, TABLE_MSGS,
                            TABLE_DEVICES, TABLE_DEVICES, DEVICE_ITEM_DB_ID, TABLE_MSGS, KEY_MSG_DEVICE_ID_FK,
                            KEY_MSG_TIMESTAMP, TABLE_DEVICES, DEVICE_ITEM_TIME_STAMP,
                            KEY_MSG_TARGET, KEY_MSG_TIMESTAMP
                    );

        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery(POSTS_SELECT_QUERY, null);
        try {
            if (cursor.moveToFirst()) {
                do {
                    long msgID = cursor.getLong(0);
                    long dbID = cursor.getLong(1);
                    String srcDeviceName = cursor.getString(2);
                    int state = cursor.getInt(3);
                    String msgTrgDevice = cursor.getString(4);
                    String txt = cursor.getString(5);
                    String stamp = cursor.getString(6);

                    WCChat.ChatMessage newPost = new WCChat.ChatMessage(msgID);
                    DeviceItem aSrcDevice = new DeviceItem(null);
                    aSrcDevice.setDbId(dbID);
                    aSrcDevice.complete(srcDeviceName);
                    newPost.setSender(aSrcDevice);
                    newPost.setRawMessage(txt);
                    newPost.setTimeStamp(stamp);
                    newPost.setTarget(msgTrgDevice);
                    newPost.setState(state);
                    aList.add(newPost);

                    added++;
                } while(cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.d(TAG, "Error while trying to check new messages from database");
            e.printStackTrace();
        } finally {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        }
        return (added > 0);
    }

    public String getLastMsgTimeStamp(String aUserName) {
        @SuppressLint("DefaultLocale") String POSTS_SELECT_QUERY =
                String.format("with target1 as (values (''), ('%s')) "+
                                "select max(%s) from %s " +
                                "inner join %s on %s.%s == %s.%s " +
                                "where (%s in target1)",
                        aUserName.replaceAll("'", "''"),
                        KEY_MSG_TIMESTAMP, TABLE_MSGS,
                        TABLE_DEVICES, TABLE_DEVICES, DEVICE_ITEM_DB_ID, TABLE_MSGS, KEY_MSG_DEVICE_ID_FK,
                        KEY_MSG_TARGET
                );

        String lstStamp = "";
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery(POSTS_SELECT_QUERY, null);
        try {
            if (cursor.moveToFirst()) {
                lstStamp = cursor.getString(0);
            }
        } catch (Exception e) {
            Log.d(TAG, "Error while trying to get last timestamp from database");
            e.printStackTrace();
        } finally {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        }
        return lstStamp;
    }

    @SuppressLint("DefaultLocale")
    public int getNewMessagesCount(String aUserName, String aLastSync, List<WCChat.DeviceMsgsCnt> aList) {
        int added = 0;
        String POSTS_SELECT_QUERY;
        if (aLastSync != null && aLastSync.length() > 0)
           POSTS_SELECT_QUERY =
                    String.format("with "+
                                    "target1 as (values (''), ('%s')) "+
                                    "select %s, %s, count(*) from %s " +
                                    "inner join %s on %s.%s == %s.%s " +
                                    "where (%s > '%s') and (%s > %s.%s) and "+
                                    "(%s in target1) group by %s",
                            aUserName.replaceAll("'", "''"),
                            DEVICE_ITEM_DB_ID, DEVICE_ITEM_SERVER_NAME, TABLE_MSGS,
                            TABLE_DEVICES, TABLE_DEVICES, DEVICE_ITEM_DB_ID, TABLE_MSGS, KEY_MSG_DEVICE_ID_FK,
                            KEY_MSG_TIMESTAMP, aLastSync, KEY_MSG_TIMESTAMP, TABLE_DEVICES, DEVICE_ITEM_TIME_STAMP,
                            KEY_MSG_TARGET, DEVICE_ITEM_DB_ID
                    );
        else
            POSTS_SELECT_QUERY =
                    String.format("with "+
                                    "target1 as (values (''), ('%s')) "+
                                    "select %s, %s, count(*), max(%s) from %s " +
                                    "inner join %s on %s.%s == %s.%s " +
                                    "where (%s > %s.%s) and (%s in target1) "+
                                    "group by %s",
                            aUserName.replaceAll("'", "''"),
                            DEVICE_ITEM_DB_ID, DEVICE_ITEM_SERVER_NAME, KEY_MSG_TIMESTAMP, TABLE_MSGS,
                            TABLE_DEVICES, TABLE_DEVICES, DEVICE_ITEM_DB_ID, TABLE_MSGS, KEY_MSG_DEVICE_ID_FK,
                            KEY_MSG_TIMESTAMP, TABLE_DEVICES, DEVICE_ITEM_TIME_STAMP, KEY_MSG_TARGET,
                            DEVICE_ITEM_DB_ID
                    );

        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery(POSTS_SELECT_QUERY, null);
        try {
            if (cursor.moveToFirst()) {
                do {
                    long dbId = cursor.getLong(0);
                    String devName = cursor.getString(1);
                    int cnt = cursor.getInt(2);
                    String devStamp = cursor.getString(3);
                    aList.add(new WCChat.DeviceMsgsCnt(dbId, devName, cnt, devStamp));
                    added += cnt;
                } while(cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.d(TAG, "Error while trying to get count of new messages from database");
            e.printStackTrace();
        } finally {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        }
        return added;
    }

    boolean getNewMessages(DeviceItem aUser,
                           DeviceItem aFrom,
                           List<WCChat.ChatMessage> messageList) {
        int added = 0;
        @SuppressLint("DefaultLocale") String POSTS_SELECT_QUERY =
                String.format("with "+
                                "target1 as (values ('') UNION select %s from %s where %s == %d ), "+
                                "target2 as (values ('') UNION select %s from %s where %s == %d ), "+
                                "total_table as ( "+
                                "select * from %s inner join %s on %s.%s = %s.%s where "+
                                " ((%s.%s == %d) and (%s.%s > '%s') and (%s.%s in target2)) "+
                                "or "+
                                " ((%s.%s == %d) and (%s.%s > '%s') and (%s.%s in target1))) "+
                                "select %s, %s, %s, %s, %s, %s from total_table order by %s",
                        DEVICE_ITEM_SERVER_NAME, TABLE_DEVICES, DEVICE_ITEM_DB_ID, aUser.getDbId(),
                        DEVICE_ITEM_SERVER_NAME, TABLE_DEVICES, DEVICE_ITEM_DB_ID, aFrom.getDbId(),
                        TABLE_MSGS, TABLE_DEVICES, TABLE_MSGS, KEY_MSG_DEVICE_ID_FK,
                        TABLE_DEVICES, DEVICE_ITEM_DB_ID,
                        TABLE_DEVICES, DEVICE_ITEM_DB_ID, aUser.getDbId(),
                        TABLE_MSGS, KEY_MSG_TIMESTAMP, aUser.getLstSync(),
                        TABLE_MSGS, KEY_MSG_TARGET,
                        TABLE_DEVICES, DEVICE_ITEM_DB_ID, aFrom.getDbId(),
                        TABLE_MSGS, KEY_MSG_TIMESTAMP, aFrom.getLstSync(),
                        TABLE_MSGS, KEY_MSG_TARGET,
                        KEY_MSG_ID, KEY_MSG_DEVICE_ID_FK, KEY_MSG_STATE, KEY_MSG_TARGET, KEY_MSG_TEXT, KEY_MSG_TIMESTAMP,
                        KEY_MSG_TIMESTAMP
                );

        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery(POSTS_SELECT_QUERY, null);
        try {
            if (cursor.moveToFirst()) {
                do {
                    long msgID = cursor.getLong(0);
                    long msgSrcDeviceId = cursor.getLong(1);
                    int state = cursor.getInt(2);
                    String msgTrgDevice = cursor.getString(3);
                    String txt = cursor.getString(4);
                    String stamp = cursor.getString(5);

                    DeviceItem dbDevice = null;
                    if (msgSrcDeviceId == aUser.getDbId()) {
                        dbDevice = aUser;
                    } else
                    if (msgSrcDeviceId == aFrom.getDbId()) {
                        dbDevice = aFrom;
                    }
                    if (dbDevice != null) {
                        dbDevice.setLstSync(stamp);

                        WCChat.ChatMessage newPost = new WCChat.ChatMessage(msgID);
                        newPost.setSender(dbDevice);
                        newPost.setRawMessage(txt);
                        newPost.setTimeStamp(stamp);
                        newPost.setTarget(msgTrgDevice);
                        newPost.setState(state);
                        messageList.add(newPost);
                        added++;
                    }
                } while(cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.d(TAG, "Error while trying to get new messages from database");
            e.printStackTrace();
        } finally {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        }
        return (added > 0);
    }


    boolean getAllMessages(DeviceItem aUser,
                           DeviceItem aFrom,
                           List<WCChat.ChatMessage> messageList) {
        messageList.clear();

        @SuppressLint("DefaultLocale") String POSTS_SELECT_QUERY =
                String.format("with "+
                        "target1 as (values ('') UNION select %s from %s where %s == %d ), "+
                        "target2 as (values ('') UNION select %s from %s where %s == %d ), "+
                        "total_table as ( "+
                                "select * from %s inner join %s on %s.%s = %s.%s where "+
                                       " ((%s.%s == %d) and (%s.%s in target2)) "+
                                "or "+
                                       " ((%s.%s == %d) and (%s.%s in target1))) "+
                        "select %s, %s, %s, %s, %s, %s from total_table order by %s",
                        DEVICE_ITEM_SERVER_NAME, TABLE_DEVICES, DEVICE_ITEM_DB_ID, aUser.getDbId(),
                        DEVICE_ITEM_SERVER_NAME, TABLE_DEVICES, DEVICE_ITEM_DB_ID, aFrom.getDbId(),
                        TABLE_MSGS, TABLE_DEVICES, TABLE_MSGS, KEY_MSG_DEVICE_ID_FK,
                                                    TABLE_DEVICES, DEVICE_ITEM_DB_ID,
                        TABLE_DEVICES, DEVICE_ITEM_DB_ID, aUser.getDbId(),
                        TABLE_MSGS, KEY_MSG_TARGET,
                        TABLE_DEVICES, DEVICE_ITEM_DB_ID, aFrom.getDbId(),
                        TABLE_MSGS, KEY_MSG_TARGET,
                        KEY_MSG_ID, KEY_MSG_DEVICE_ID_FK, KEY_MSG_STATE, KEY_MSG_TARGET, KEY_MSG_TEXT, KEY_MSG_TIMESTAMP,
                        KEY_MSG_TIMESTAMP
                        );

        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery(POSTS_SELECT_QUERY, null);
        try {
            if (cursor.moveToFirst()) {
                do {
                    long msgID = cursor.getLong(0);
                    long msgSrcDeviceId = cursor.getLong(1);
                    int state = cursor.getInt(2);
                    String msgTrgDevice = cursor.getString(3);
                    String txt = cursor.getString(4);
                    String stamp = cursor.getString(5);

                    DeviceItem dbDevice = null;
                    if (msgSrcDeviceId == aUser.getDbId()) {
                        dbDevice = aUser;
                    } else
                    if (msgSrcDeviceId == aFrom.getDbId()) {
                        dbDevice = aFrom;
                    }
                    if (dbDevice != null) {
                        dbDevice.setLstSync(stamp);

                        WCChat.ChatMessage newPost = new WCChat.ChatMessage(msgID);
                        newPost.setSender(dbDevice);
                        newPost.setRawMessage(txt);
                        newPost.setTimeStamp(stamp);
                        newPost.setTarget(msgTrgDevice);
                        newPost.setState(state);
                        messageList.add(newPost);
                    }
                } while(cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.d(TAG, "Error while trying to get all msgs from database");
            e.printStackTrace();
        } finally {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        }
        return (messageList.size() > 0);
    }
}
