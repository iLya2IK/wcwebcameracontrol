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

    private static final String TAG = "ChatDatabase";

    // Database Info
    private static final String DATABASE_NAME = "chatDatabase";
    private static final int DATABASE_VERSION = 1;

    // Table Names
    private static final String TABLE_DEVICES = "devices";
    private static final String TABLE_MSGS = "msgs";

    // Msg Table Columns
    private static final String KEY_MSG_ID = "mid";
    private static final String KEY_MSG_DEVICE_ID_FK = "devId";
    private static final String KEY_MSG_TARGET = "target";
    private static final String KEY_MSG_TEXT = "text";
    private static final String KEY_MSG_TIMESTAMP = "stamp";

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
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        sqLiteDatabase.execSQL("CREATE TABLE if not exists " + TABLE_DEVICES +
                "("+
                DEVICE_ITEM_DB_ID + " integer PRIMARY KEY AUTOINCREMENT," +
                DEVICE_ITEM_SERVER_NAME + " text default \"\", "+
                DEVICE_ITEM_BLE_NAME + " text default \"\", "+
                DEVICE_ITEM_BLE_ADDRESS + " text default \"\", "+
                DEVICE_ITEM_CHAR + " text default \"\", "+
                DEVICE_ITEM_COLOR + " integer default 0, "+
                DEVICE_ITEM_INDEX + " integer default " + Integer.valueOf(DEFAULT_DEVICE_COLOR).toString() + ", "+
                DEVICE_ITEM_TIME_STAMP + " text default current_timestamp " +
                ");");
        sqLiteDatabase.execSQL("CREATE TABLE if not exists " + TABLE_MSGS +
                "("+
                KEY_MSG_ID + " integer PRIMARY KEY AUTOINCREMENT," +
                KEY_MSG_DEVICE_ID_FK + " integer references " + TABLE_DEVICES + "(" + DEVICE_ITEM_DB_ID + "), " +
                KEY_MSG_TARGET + " text default \"\"," +
                KEY_MSG_TEXT + " text default \"\", "+
                KEY_MSG_TIMESTAMP + " text default current_timestamp " +
                ");");
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int oldVersion, int newVersion) {
        if (oldVersion != newVersion) {
            sqLiteDatabase.execSQL("DROP TABLE IF EXISTS " + TABLE_DEVICES);
            sqLiteDatabase.execSQL("DROP TABLE IF EXISTS " + TABLE_MSGS);
            onCreate(sqLiteDatabase);
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
                            deviceId = cursor.getInt(0);
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
                            deviceId = cursor.getInt(0);
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
                String.format("select * from %s", TABLE_DEVICES);

        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery(POSTS_SELECT_QUERY, null);
        try {
            if (cursor.moveToFirst()) {
                do {
                    try {
                        JSONObject cached_item = new JSONObject();
                        cached_item.put(DEVICE_ITEM_DB_ID, cursor.getInt(cursor.getColumnIndex(DEVICE_ITEM_DB_ID)));
                        cached_item.put(DEVICE_ITEM_SERVER_NAME, cursor.getString(cursor.getColumnIndex(DEVICE_ITEM_SERVER_NAME)));
                        cached_item.put(DEVICE_ITEM_BLE_NAME, cursor.getString(cursor.getColumnIndex(DEVICE_ITEM_BLE_NAME)));
                        cached_item.put(DEVICE_ITEM_BLE_ADDRESS, cursor.getString(cursor.getColumnIndex(DEVICE_ITEM_BLE_ADDRESS)));
                        cached_item.put(DEVICE_ITEM_CHAR, cursor.getString(cursor.getColumnIndex(DEVICE_ITEM_CHAR)));
                        cached_item.put(DEVICE_ITEM_COLOR, cursor.getInt(cursor.getColumnIndex(DEVICE_ITEM_COLOR)));
                        cached_item.put(DEVICE_ITEM_INDEX, cursor.getInt(cursor.getColumnIndex(DEVICE_ITEM_INDEX)));
                        cached_item.put(DEVICE_ITEM_TIME_STAMP, cursor.getString(cursor.getColumnIndex(DEVICE_ITEM_TIME_STAMP)));
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

    boolean getNewMessages(DeviceItem aUser,
                           DeviceItem aFrom,
                           List<WCChat.ChatMessage> messageList) {
        int added = 0;
        @SuppressLint("DefaultLocale") String POSTS_SELECT_QUERY =
                String.format("with "+
                                "target1 as (values (\"\") UNION select %s from %s where %s == %d ), "+
                                "target2 as (values (\"\") UNION select %s from %s where %s == %d ), "+
                                "total_table as ( "+
                                "select * from %s inner join %s on %s.%s = %s.%s where "+
                                " ((%s.%s == %d) and (%s.%s > \"%s\") and (%s.%s in target2)) "+
                                "or "+
                                " ((%s.%s == %d) and (%s.%s > \"%s\") and (%s.%s in target1))) "+
                                "select %s, %s, %s, %s, %s from total_table order by %s",
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
                        KEY_MSG_ID, KEY_MSG_TEXT, KEY_MSG_TIMESTAMP, KEY_MSG_TARGET, KEY_MSG_DEVICE_ID_FK,
                        KEY_MSG_TIMESTAMP
                );

        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery(POSTS_SELECT_QUERY, null);
        try {
            if (cursor.moveToFirst()) {
                do {
                    int msgID = cursor.getInt(cursor.getColumnIndex(KEY_MSG_ID));
                    String txt = cursor.getString(cursor.getColumnIndex(KEY_MSG_TEXT));
                    String stamp = cursor.getString(cursor.getColumnIndex(KEY_MSG_TIMESTAMP));
                    String msgTrgDevice = cursor.getString(cursor.getColumnIndex(KEY_MSG_TARGET));
                    int msgSrcDeviceId = cursor.getInt(cursor.getColumnIndex(KEY_MSG_DEVICE_ID_FK));

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
                        newPost.setMessage(txt);
                        newPost.setTimeStamp(stamp);
                        newPost.setTarget(msgTrgDevice);
                        messageList.add(newPost);
                        added++;
                    }
                } while(cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.d(TAG, "Error while trying to get posts from database");
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
                        "target1 as (values (\"\") UNION select %s from %s where %s == %d ), "+
                        "target2 as (values (\"\") UNION select %s from %s where %s == %d ), "+
                        "total_table as ( "+
                                "select * from %s inner join %s on %s.%s = %s.%s where "+
                                       " ((%s.%s == %d) and (%s.%s in target2)) "+
                                "or "+
                                       " ((%s.%s == %d) and (%s.%s in target1))) "+
                        "select %s, %s, %s, %s, %s from total_table order by %s",
                        DEVICE_ITEM_SERVER_NAME, TABLE_DEVICES, DEVICE_ITEM_DB_ID, aUser.getDbId(),
                        DEVICE_ITEM_SERVER_NAME, TABLE_DEVICES, DEVICE_ITEM_DB_ID, aFrom.getDbId(),
                        TABLE_MSGS, TABLE_DEVICES, TABLE_MSGS, KEY_MSG_DEVICE_ID_FK,
                                                    TABLE_DEVICES, DEVICE_ITEM_DB_ID,
                        TABLE_DEVICES, DEVICE_ITEM_DB_ID, aUser.getDbId(),
                        TABLE_MSGS, KEY_MSG_TARGET,
                        TABLE_DEVICES, DEVICE_ITEM_DB_ID, aFrom.getDbId(),
                        TABLE_MSGS, KEY_MSG_TARGET,
                        KEY_MSG_ID, KEY_MSG_TEXT, KEY_MSG_TIMESTAMP, KEY_MSG_TARGET, KEY_MSG_DEVICE_ID_FK,
                        KEY_MSG_TIMESTAMP
                        );

        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery(POSTS_SELECT_QUERY, null);
        try {
            if (cursor.moveToFirst()) {
                do {
                    int msgID = cursor.getInt(cursor.getColumnIndex(KEY_MSG_ID));
                    String txt = cursor.getString(cursor.getColumnIndex(KEY_MSG_TEXT));
                    String stamp = cursor.getString(cursor.getColumnIndex(KEY_MSG_TIMESTAMP));
                    String msgTrgDevice = cursor.getString(cursor.getColumnIndex(KEY_MSG_TARGET));
                    int msgSrcDeviceId = cursor.getInt(cursor.getColumnIndex(KEY_MSG_DEVICE_ID_FK));

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
                        newPost.setMessage(txt);
                        newPost.setTimeStamp(stamp);
                        newPost.setTarget(msgTrgDevice);
                        messageList.add(newPost);
                    }
                } while(cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.d(TAG, "Error while trying to get posts from database");
        } finally {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        }
        return (messageList.size() > 0);
    }
}
