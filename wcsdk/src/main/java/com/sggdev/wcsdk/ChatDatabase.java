package com.sggdev.wcsdk;

import static com.sggdev.wcsdk.DeviceItem.DEFAULT_DEVICE_COLOR;
import static com.sggdev.wcsdk.DeviceItem.DEVICE_ITEM_BLE_ADDRESS;
import static com.sggdev.wcsdk.DeviceItem.DEVICE_ITEM_BLE_NAME;
import static com.sggdev.wcsdk.DeviceItem.DEVICE_ITEM_CHAR;
import static com.sggdev.wcsdk.DeviceItem.DEVICE_ITEM_COLOR;
import static com.sggdev.wcsdk.DeviceItem.DEVICE_ITEM_DB_ID;
import static com.sggdev.wcsdk.DeviceItem.DEVICE_ITEM_INDEX;
import static com.sggdev.wcsdk.DeviceItem.DEVICE_ITEM_META;
import static com.sggdev.wcsdk.DeviceItem.DEVICE_ITEM_SERVER_NAME;
import static com.sggdev.wcsdk.DeviceItem.DEVICE_ITEM_TIME_STAMP;
import static com.sggdev.wcsdk.DeviceItem.DEVICE_ITEM_UNREAD_MSGS;
import static com.sggdev.wcsdk.DeviceItem.DEVICE_ITEM_USER_NAME;
import static com.sggdev.wcsdk.WCRESTProtocol.JSON_DEVICE;
import static com.sggdev.wcsdk.WCRESTProtocol.JSON_MSG;
import static com.sggdev.wcsdk.WCRESTProtocol.JSON_PARAMS;
import static com.sggdev.wcsdk.WCRESTProtocol.JSON_RID;
import static com.sggdev.wcsdk.WCRESTProtocol.JSON_STAMP;
import static com.sggdev.wcsdk.WCRESTProtocol.JSON_TARGET;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import com.sggdev.wcsdk.Log;
import android.util.Pair;

import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ChatDatabase extends SQLiteOpenHelper {
    public interface OnNewInstance {
        public ChatDatabase onNew(Context context);
    }

    private static OnNewInstance sNewInstInterf;
    private static ChatDatabase sInstance;

    public static void setNewInstInterf(OnNewInstance sNewInstInterf) {
        ChatDatabase.sNewInstInterf = sNewInstInterf;
    }

    public static final int MSG_STATE_UNKNOWN = 0;
    public static final int MSG_STATE_RECIEVED = 1;
    public static final int MSG_STATE_READY_TO_SEND = 2;
    public static final int MSG_STATE_SENDED = 3;

    private static final String TAG = "ChatDatabase";

    // Database Info
    private static final String DATABASE_NAME = "chatDatabase";
    private static final int DATABASE_VERSION = 2;

    // Table Names
    public static final String TABLE_DEVICES = "devices";
    public static final String TABLE_MSGS = "msgs";
    public static final String TABLE_MEDIA = "media";

    // Msg Table Columns
    public static final String KEY_MSG_ID = "mid";
    public static final String KEY_MSG_DEVICE_ID_FK = "devId";
    public static final String KEY_MSG_TARGET = "target";
    public static final String KEY_MSG_TEXT = "text";
    public static final String KEY_MSG_STATE = "state";
    public static final String KEY_MSG_TIMESTAMP = "stamp";
    public static final String KEY_MSG_LOCAL_TIMESTAMP = "lstamp";

    //Media Table Columns
    public static final String KEY_MEDIA_ID = "medId";
    public static final String KEY_MEDIA_DEVICE_ID_FK = "devId";
    public static final String KEY_MEDIA_RID = "rid";
    public static final String KEY_MEDIA_META = "meta";
    public static final String KEY_MEDIA_LOC = "loc";
    public static final String KEY_MEDIA_PREVIEW = "preview";

    public static ChatDatabase newInstance(Context context) {
        if (sNewInstInterf != null) {
            return sNewInstInterf.onNew(context);
        }
        return new ChatDatabase(context.getApplicationContext());
    }

    public static synchronized ChatDatabase getInstance(Context context) {
        if (sInstance == null) {
            sInstance = newInstance(context);
        }
        return sInstance;
    }

    private ChatDatabase(@Nullable Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    public ChatDatabase(@Nullable Context context, String dbName, int dbVersion) {
        super(context, dbName, null, dbVersion);
    }

    SQLiteStatement stmt_insert_media,
            stmt_find_device,
            stmt_get_last_timestamp,
            stmt_get_last_device;

    String query_get_all_devices,
           query_add_msgs,
           query_get_media_preview,
           query_get_media,
           query_get_unsent,
           query_get_check_new_msgs_1,
           query_get_check_new_msgs_2,
           query_get_check_new_msgs_count_1,
           query_get_check_new_msgs_count_2,
           query_get_new_msgs,
           query_get_all_msgs;

    @Override
    @SuppressLint("DefaultLocale")
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    @SuppressLint("DefaultLocale")
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        // init queries
        query_get_all_devices =
                String.format("select * from %s where %s == ?;",
                        TABLE_DEVICES, DEVICE_ITEM_USER_NAME);
        query_add_msgs = "with tn (u, d, trg, txt, sta, stm) as (values (?, ?, ?, ?, ?, ?)" +
                String.format(") insert into %s (%s, %s, %s, %s, %s) ",
                        TABLE_MSGS, KEY_MSG_DEVICE_ID_FK, KEY_MSG_TARGET, KEY_MSG_TEXT, KEY_MSG_STATE, KEY_MSG_TIMESTAMP) +
                String.format("select %s.%s, tn.trg, tn.txt, tn.sta, tn.stm from tn INNER JOIN %s on (%s.%s == tn.d) and (%s.%s == tn.u);",
                        TABLE_DEVICES, DEVICE_ITEM_DB_ID, TABLE_DEVICES, TABLE_DEVICES, DEVICE_ITEM_SERVER_NAME, TABLE_DEVICES, DEVICE_ITEM_USER_NAME);
        query_get_media_preview = String.format("select %s from %s where %s == ?;",
                TABLE_MEDIA, KEY_MEDIA_PREVIEW, KEY_MEDIA_ID);
        query_get_media = String.format(
                "select * from %s where %s == ?;", TABLE_MEDIA, KEY_MEDIA_RID);
        query_get_unsent = String.format("select * from %s where %s == ? and %s == %d limit ?;",
                TABLE_MSGS, KEY_MSG_DEVICE_ID_FK,
                KEY_MSG_STATE, MSG_STATE_READY_TO_SEND);
        query_get_check_new_msgs_1 =
                String.format("with " +
                                "target1 as (values (''), (?)) " +
                                "select %s, %s, %s, %s, %s, %s, %s, %s from %s " +
                                "inner join %s on %s.%s == %s.%s " +
                                "where  (%s||printf('.%%04d', %s %% 10000) > ?) and (%s||printf('.%%04d', %s %% 10000) > %s.%s) and (%s in target1) and (%s.%s == ?) " +
                                "order by (%s||printf('.%%04d', %s %% 10000))",
                        KEY_MSG_ID, KEY_MSG_DEVICE_ID_FK, DEVICE_ITEM_SERVER_NAME, KEY_MSG_STATE, KEY_MSG_TARGET, KEY_MSG_TEXT, KEY_MSG_TIMESTAMP, KEY_MSG_LOCAL_TIMESTAMP, TABLE_MSGS,
                        TABLE_DEVICES, TABLE_DEVICES, DEVICE_ITEM_DB_ID, TABLE_MSGS, KEY_MSG_DEVICE_ID_FK,
                        KEY_MSG_LOCAL_TIMESTAMP, KEY_MSG_ID, KEY_MSG_LOCAL_TIMESTAMP, KEY_MSG_ID, TABLE_DEVICES, DEVICE_ITEM_TIME_STAMP,
                        KEY_MSG_TARGET,
                        TABLE_DEVICES, DEVICE_ITEM_USER_NAME,
                        KEY_MSG_LOCAL_TIMESTAMP, KEY_MSG_ID
                );
        query_get_check_new_msgs_2 =
                String.format("with " +
                                "target1 as (values (''), (?)) " +
                                "select %s, %s, %s, %s, %s, %s, %s, %s from %s " +
                                "inner join %s on %s.%s == %s.%s " +
                                "where  (%s||printf('.%%04d', %s %% 10000) > %s.%s) and (%s in target1) and (%s.%s == ?) " +
                                "order by (%s||printf('.%%04d', %s %% 10000))",
                        KEY_MSG_ID, KEY_MSG_DEVICE_ID_FK, DEVICE_ITEM_SERVER_NAME, KEY_MSG_STATE, KEY_MSG_TARGET, KEY_MSG_TEXT, KEY_MSG_TIMESTAMP, KEY_MSG_LOCAL_TIMESTAMP, TABLE_MSGS,
                        TABLE_DEVICES, TABLE_DEVICES, DEVICE_ITEM_DB_ID, TABLE_MSGS, KEY_MSG_DEVICE_ID_FK,
                        KEY_MSG_LOCAL_TIMESTAMP, KEY_MSG_ID, TABLE_DEVICES, DEVICE_ITEM_TIME_STAMP,
                        KEY_MSG_TARGET,
                        TABLE_DEVICES, DEVICE_ITEM_USER_NAME,
                        KEY_MSG_LOCAL_TIMESTAMP, KEY_MSG_ID
                );

        query_get_check_new_msgs_count_1 =
                String.format("with "+
                                "target1 as (values (''), (?)) "+
                                "select %s, %s, count(*), max(%s||printf('.%%04d', %s %% 10000)) from %s " +
                                "inner join %s on %s.%s == %s.%s " +
                                "where (%s||printf('.%%04d', %s %% 10000) > ?) and (%s||printf('.%%04d', %s %% 10000) > %s.%s) and "+
                                "(%s in target1) and (%s.%s == ?) group by %s",
                        DEVICE_ITEM_DB_ID, DEVICE_ITEM_SERVER_NAME, KEY_MSG_LOCAL_TIMESTAMP, KEY_MSG_ID, TABLE_MSGS,
                        TABLE_DEVICES, TABLE_DEVICES, DEVICE_ITEM_DB_ID, TABLE_MSGS, KEY_MSG_DEVICE_ID_FK,
                        KEY_MSG_LOCAL_TIMESTAMP, KEY_MSG_ID, KEY_MSG_LOCAL_TIMESTAMP, KEY_MSG_ID, TABLE_DEVICES, DEVICE_ITEM_TIME_STAMP,
                        KEY_MSG_TARGET, TABLE_DEVICES, DEVICE_ITEM_USER_NAME,
                        DEVICE_ITEM_DB_ID
                );
        query_get_check_new_msgs_count_2 =
                String.format("with " +
                              "target1 as (values (''), (?)) " +
                              "select %s, %s, count(*), max(%s||printf('.%%04d', %s %% 10000)) from %s " +
                              "inner join %s on %s.%s == %s.%s " +
                              "where (%s||printf('.%%04d', %s %% 10000) > %s.%s) and (%s in target1) and (%s.%s == ?) " +
                              "group by %s",
                        DEVICE_ITEM_DB_ID, DEVICE_ITEM_SERVER_NAME, KEY_MSG_LOCAL_TIMESTAMP, KEY_MSG_ID, TABLE_MSGS,
                        TABLE_DEVICES, TABLE_DEVICES, DEVICE_ITEM_DB_ID, TABLE_MSGS, KEY_MSG_DEVICE_ID_FK,
                        KEY_MSG_LOCAL_TIMESTAMP, KEY_MSG_ID, TABLE_DEVICES, DEVICE_ITEM_TIME_STAMP, KEY_MSG_TARGET,
                        TABLE_DEVICES, DEVICE_ITEM_USER_NAME,
                        DEVICE_ITEM_DB_ID
                );

        query_get_new_msgs =
                String.format("with "+
                              "target1 as (values ('') UNION select %s from %s where %s == ?1 ), "+
                              "target2 as (values ('') UNION select %s from %s where %s == ?2 ), "+
                              "total_table as ( "+
                              "select * from %s inner join %s on %s.%s = %s.%s where "+
                              " ((%s.%s == ?1) and (%s.%s||printf('.%%04d', %s %% 10000) > ?3) and (%s.%s in target2) and (%s.%s == ?4)) "+
                              "or "+
                              " ((%s.%s == ?2) and (%s.%s||printf('.%%04d', %s %% 10000) > ?5) and (%s.%s in target1) and (%s.%s == ?4))) "+
                              "select %s, %s, %s, %s, %s, %s, %s, (%s||printf('.%%04d', %s %% 10000)) as ctid from total_table order by ctid",
                        DEVICE_ITEM_SERVER_NAME, TABLE_DEVICES, DEVICE_ITEM_DB_ID,
                        DEVICE_ITEM_SERVER_NAME, TABLE_DEVICES, DEVICE_ITEM_DB_ID,
                        TABLE_MSGS, TABLE_DEVICES, TABLE_MSGS, KEY_MSG_DEVICE_ID_FK,
                        TABLE_DEVICES, DEVICE_ITEM_DB_ID,
                        TABLE_DEVICES, DEVICE_ITEM_DB_ID,
                        TABLE_MSGS, KEY_MSG_LOCAL_TIMESTAMP, KEY_MSG_ID,
                        TABLE_MSGS, KEY_MSG_TARGET,
                        TABLE_DEVICES, DEVICE_ITEM_USER_NAME,
                        TABLE_DEVICES, DEVICE_ITEM_DB_ID,
                        TABLE_MSGS, KEY_MSG_LOCAL_TIMESTAMP, KEY_MSG_ID,
                        TABLE_MSGS, KEY_MSG_TARGET,
                        TABLE_DEVICES, DEVICE_ITEM_USER_NAME,
                        KEY_MSG_ID, KEY_MSG_DEVICE_ID_FK, KEY_MSG_STATE, KEY_MSG_TARGET, KEY_MSG_TEXT, KEY_MSG_TIMESTAMP, KEY_MSG_LOCAL_TIMESTAMP,
                        KEY_MSG_LOCAL_TIMESTAMP, KEY_MSG_ID
                );

        query_get_all_msgs =
                String.format("with "+
                              "target1 as (values ('') UNION select %s from %s where %s == ?1 ), "+
                              "target2 as (values ('') UNION select %s from %s where %s == ?2 ), "+
                              "total_table as ( "+
                              "select * from %s inner join %s on %s.%s = %s.%s where "+
                              " ((%s.%s == ?1) and (%s.%s in target2) and (%s.%s == ?3)) "+
                              "or "+
                              " ((%s.%s == ?2) and (%s.%s in target1) and (%s.%s == ?3))) "+
                              "select %s, %s, %s, %s, %s, %s, %s, (%s||printf('.%%04d', %s %% 10000)) as ctid from total_table order by ctid",
                        DEVICE_ITEM_SERVER_NAME, TABLE_DEVICES, DEVICE_ITEM_DB_ID,
                        DEVICE_ITEM_SERVER_NAME, TABLE_DEVICES, DEVICE_ITEM_DB_ID,
                        TABLE_MSGS, TABLE_DEVICES, TABLE_MSGS, KEY_MSG_DEVICE_ID_FK,
                        TABLE_DEVICES, DEVICE_ITEM_DB_ID,
                        TABLE_DEVICES, DEVICE_ITEM_DB_ID,
                        TABLE_MSGS, KEY_MSG_TARGET,
                        TABLE_DEVICES, DEVICE_ITEM_USER_NAME,
                        TABLE_DEVICES, DEVICE_ITEM_DB_ID,
                        TABLE_MSGS, KEY_MSG_TARGET,
                        TABLE_DEVICES, DEVICE_ITEM_USER_NAME,
                        KEY_MSG_ID, KEY_MSG_DEVICE_ID_FK, KEY_MSG_STATE, KEY_MSG_TARGET, KEY_MSG_TEXT, KEY_MSG_TIMESTAMP, KEY_MSG_LOCAL_TIMESTAMP,
                        KEY_MSG_LOCAL_TIMESTAMP, KEY_MSG_ID
                );

        // init statements
        stmt_get_last_device = db.compileStatement(
                String.format("SELECT max(%s) FROM %s",
                        DEVICE_ITEM_DB_ID, TABLE_DEVICES));

        stmt_insert_media = db.compileStatement(
                String.format("insert into %s (%s, %s, %s, %s) values (?,?,?,?)",
                        TABLE_MEDIA, KEY_MEDIA_DEVICE_ID_FK, KEY_MEDIA_RID, KEY_MEDIA_META, KEY_MEDIA_LOC));
        stmt_find_device = db.compileStatement(
                String.format("select ifnull(%s, 0) from %s where %s == ? and %s == ?",
                        DEVICE_ITEM_DB_ID, TABLE_DEVICES, DEVICE_ITEM_USER_NAME, DEVICE_ITEM_SERVER_NAME));
        stmt_get_last_timestamp = db.compileStatement(
                String.format("with target1 as (values (''), (?)) "+
                                "select max((%s||printf('%%d',%s))) from %s " +
                                "inner join %s on %s.%s == %s.%s " +
                                "where (%s in target1) and (%s == ?)",
                        KEY_MSG_LOCAL_TIMESTAMP, KEY_MSG_ID, TABLE_MSGS,
                        TABLE_DEVICES, TABLE_DEVICES, DEVICE_ITEM_DB_ID, TABLE_MSGS, KEY_MSG_DEVICE_ID_FK,
                        KEY_MSG_TARGET, DEVICE_ITEM_USER_NAME
                ));
    }

    private void createDevicesTable(SQLiteDatabase sqLiteDatabase) {
        sqLiteDatabase.execSQL("CREATE TABLE if not exists " + TABLE_DEVICES +
                "("+
                DEVICE_ITEM_DB_ID + " integer PRIMARY KEY AUTOINCREMENT," +
                DEVICE_ITEM_USER_NAME + " text default '', "+
                DEVICE_ITEM_SERVER_NAME + " text default '', "+
                DEVICE_ITEM_BLE_NAME + " text default '', "+
                DEVICE_ITEM_BLE_ADDRESS + " text default '', "+
                DEVICE_ITEM_CHAR + " text default '', "+
                DEVICE_ITEM_INDEX + " integer default 0, "+
                DEVICE_ITEM_COLOR + " integer default " + Integer.valueOf(DEFAULT_DEVICE_COLOR).toString() + ", "+
                DEVICE_ITEM_META + " text default '{}', "+
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
                KEY_MSG_TIMESTAMP + " text default current_timestamp, " +
                KEY_MSG_LOCAL_TIMESTAMP + " text default current_timestamp " +
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
            sqLiteDatabase.execSQL("DROP TABLE IF EXISTS " + TABLE_DEVICES);
            sqLiteDatabase.execSQL("DROP TABLE IF EXISTS " + TABLE_MSGS);
            onCreate(sqLiteDatabase);
        }
    }

    public long findDevice(String userName, String devName) {
        if (userName == null) return 0;
        if (devName == null) return 0;

        SQLiteDatabase db = getReadableDatabase();
        try {
            db.beginTransaction();

            stmt_find_device.clearBindings();
            stmt_find_device.bindString(1, userName);
            stmt_find_device.bindString(2, devName);
            long res = stmt_find_device.simpleQueryForLong();

            db.setTransactionSuccessful();

            return res;
        } catch (Exception e) {
            Log.e(TAG, "Error while trying to get media from database");
            e.printStackTrace();
        } finally {
            db.endTransaction();
        }
        return 0;
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
            Log.e(TAG, "Error while trying to update timestamp for device");
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

                values.put(DEVICE_ITEM_USER_NAME, device.getDeviceUserName());
                values.put(DEVICE_ITEM_SERVER_NAME, device.getDeviceServerName());
                values.put(DEVICE_ITEM_BLE_NAME, device.getDeviceBLEName());
                values.put(DEVICE_ITEM_BLE_ADDRESS, device.getDeviceBLEAddress());
                values.put(DEVICE_ITEM_CHAR, device.getDeviceWriteChar());
                values.put(DEVICE_ITEM_COLOR, device.getDeviceItemColor());
                values.put(DEVICE_ITEM_INDEX, device.getDeviceItemIndex());
                values.put(DEVICE_ITEM_META, device.getMeta().toString());
                values.put(DEVICE_ITEM_TIME_STAMP, device.getLstSync());

                ArrayList<String> whereArgs = new ArrayList<>();
                StringBuilder whereClause = new StringBuilder();

                if (device.getDeviceUserName().length() > 0) {
                    whereClause.append(DEVICE_ITEM_USER_NAME);
                    whereClause.append("=? and (");
                    whereArgs.add(device.getDeviceUserName());
                }

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
                whereClause.append(")");

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

                    deviceId = stmt_get_last_device.simpleQueryForLong();
                    db.setTransactionSuccessful();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error while trying to add or update device");
                e.printStackTrace();
            } finally {
                db.endTransaction();
            }
            if (deviceId > 0) device.setDbId(deviceId);
        }
    }

    public List<JSONObject> getAllDevices(String uname) {
        List<JSONObject> res = new ArrayList<>();

        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery(query_get_all_devices, new String[]{uname});
        try {
            if (cursor.moveToFirst()) {
                int [] cInt = {
                        cursor.getColumnIndex(DEVICE_ITEM_DB_ID),
                        cursor.getColumnIndex(DEVICE_ITEM_USER_NAME),
                        cursor.getColumnIndex(DEVICE_ITEM_SERVER_NAME),
                        cursor.getColumnIndex(DEVICE_ITEM_BLE_NAME),
                        cursor.getColumnIndex(DEVICE_ITEM_BLE_ADDRESS),
                        cursor.getColumnIndex(DEVICE_ITEM_CHAR),
                        cursor.getColumnIndex(DEVICE_ITEM_COLOR),
                        cursor.getColumnIndex(DEVICE_ITEM_INDEX),
                        cursor.getColumnIndex(DEVICE_ITEM_META),
                        cursor.getColumnIndex(DEVICE_ITEM_TIME_STAMP)
                };
                do {
                    try {
                        JSONObject cached_item = new JSONObject();
                        cached_item.put(DEVICE_ITEM_DB_ID, cursor.getLong(cInt[0]));
                        cached_item.put(DEVICE_ITEM_USER_NAME, cursor.getString(cInt[1]));
                        cached_item.put(DEVICE_ITEM_SERVER_NAME, cursor.getString(cInt[2]));
                        cached_item.put(DEVICE_ITEM_BLE_NAME, cursor.getString(cInt[3]));
                        cached_item.put(DEVICE_ITEM_BLE_ADDRESS, cursor.getString(cInt[4]));
                        cached_item.put(DEVICE_ITEM_CHAR, cursor.getString(cInt[5]));
                        cached_item.put(DEVICE_ITEM_COLOR, cursor.getInt(cInt[6]));
                        cached_item.put(DEVICE_ITEM_INDEX, cursor.getInt(cInt[7]));
                        cached_item.put(DEVICE_ITEM_META, cursor.getString(cInt[8]));
                        cached_item.put(DEVICE_ITEM_TIME_STAMP, cursor.getString(cInt[9]));
                        cached_item.put(DEVICE_ITEM_UNREAD_MSGS, 0);
                        res.add(cached_item);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                } while(cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error while trying to get devices from database");
            e.printStackTrace();
        } finally {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        }

        return res;
    }

    private JSONObject generateMsg(String sender, String target, String msg, JSONObject params) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put(JSON_DEVICE, sender);
        obj.put(JSON_TARGET, target);
        obj.put(JSON_MSG, msg);
        if (params == null)
            obj.put(JSON_PARAMS, new JSONObject());
        else
            obj.put(JSON_PARAMS, params);

        return obj;
    }

    public void sendMsgsNoParams(String user, String sender, String target, List<String> msgs, final String server_time) {
        try {
            List<JSONObject> jmsgs = new ArrayList<>();
            for (String str : msgs) {
                JSONObject msg = generateMsg(sender, target, str, null);
                jmsgs.add(msg);
            }

            addMsgs(user, jmsgs, MSG_STATE_READY_TO_SEND, server_time);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void sendMsgsWithParams(String user, String sender, String target, List<Pair<String, JSONObject>> msgs, final String server_time) {
        try {
            List<JSONObject> jmsgs = new ArrayList<>();
            for (Pair<String, JSONObject> str : msgs) {
                JSONObject msg = generateMsg(sender, target, str.first, str.second);
                jmsgs.add(msg);
            }

            addMsgs(user, jmsgs, MSG_STATE_READY_TO_SEND, server_time);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void sendMsg(String user, String sender, String target, String msg, JSONObject params, final String server_time) {
        try {
            JSONObject obj = generateMsg(sender, target, msg, params);

            addMsgs(user, new ArrayList<JSONObject>() {{add(obj);}}, MSG_STATE_READY_TO_SEND, server_time);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void sendMsg(DeviceItem sender, DeviceItem target, String msg, JSONObject params, final String server_time) {
        sendMsg(sender.getDeviceUserName(), sender.getDeviceServerName(), target.getDeviceServerName(), msg, params, server_time);
    }

    public String addMsgs(String aUserName, List<JSONObject> items, int msgState, final String server_time) {
        String lst = "";

        if (items.size() == 0) return lst;

        SQLiteDatabase db = getWritableDatabase();


        try {
            db.beginTransaction();

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

                ArrayList<String> alist = new ArrayList<>();

                alist.add(aUserName);
                alist.add(device);
                alist.add(target);
                alist.add(text.toString());
                alist.add(Integer.toString(msgState));
                if (time_stamp.length() == 0) {
                    alist.add(server_time);
                } else {
                    alist.add(time_stamp);
                }

                lst = time_stamp;

                db.execSQL(query_add_msgs, alist.toArray());
            }

            db.setTransactionSuccessful();
        } catch (Exception e) {
            lst = "";
            Log.e(TAG, "Error while trying to add messages");
            e.printStackTrace();
        } finally {
            db.endTransaction();
        }

        return lst;
    }

    public void setMediaPreview(WCChat.ChatMedia media, byte[] ablob) {
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
            Log.e(TAG, "Error while trying to update media record");
            e.printStackTrace();
        } finally {
            db.endTransaction();
        }
    }

    public void setMediaPreview(long rid, byte[] ablob) {
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
            Log.e(TAG, "Error while trying to update media record");
            e.printStackTrace();
        } finally {
            db.endTransaction();
        }
    }

    public void getMediaPreview(WCChat.ChatMedia media) {
        SQLiteDatabase db = getReadableDatabase();

        Cursor cursor = db.rawQuery(query_get_media_preview, new String[] {Long.toString(media.getDbId())});
        try {
            if (cursor.moveToFirst()) {
                media.setPreview(cursor.getBlob(0));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error while trying to get blob from database");
            e.printStackTrace();
        } finally {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        }
    }

    public static final int MEDIA_META = 1;
    public static final int MEDIA_LOC = 2;

    @SuppressLint("DefaultLocale")
    public void updateMedia(WCChat.ChatMedia item, int updateMask) {
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
            Log.e(TAG, "Error while trying to update media");
            e.printStackTrace();
        }
    }

    public void addMedia(List<WCChat.ChatMedia> items) {
        if (items.size() == 0) return;

        SQLiteDatabase db = getWritableDatabase();

        try {
            db.beginTransaction();

            for (WCChat.ChatMedia item : items) {
                if (item.getSender() >= 0) {
                    stmt_insert_media.clearBindings();
                    stmt_insert_media.bindLong(1, item.getSender());
                    stmt_insert_media.bindLong(2, item.getServerRID());
                    stmt_insert_media.bindString(3, item.getMetaData());
                    stmt_insert_media.bindString(4, item.getLocation());
                    stmt_insert_media.executeInsert();
                }
            }

            db.setTransactionSuccessful(); // This commits the transaction if there were no exceptions

        } catch (Exception e) {
            Log.e(TAG, "Error while trying to add media records");
            e.printStackTrace();
        } finally {
            db.endTransaction();
        }
    }

    @SuppressLint("DefaultLocale")
    public List<WCChat.ChatMedia> getMedia(List<Long> rids) {
        if (rids.size() == 0) return null;

        SQLiteDatabase db = getReadableDatabase();

        try {
            List<WCChat.ChatMedia> aMediaList = new ArrayList<>();

            ArrayList<Integer> cInt = new ArrayList<>();

            db.beginTransaction();

            for (Long id : rids) {
                Cursor cursor = db.rawQuery(query_get_media, new String[]{Long.toString(id)});
                try {
                    if (cursor.moveToFirst()) {
                        if (cInt.size() == 0) {
                           cInt.add(cursor.getColumnIndex(KEY_MEDIA_ID));
                           cInt.add(cursor.getColumnIndex(KEY_MEDIA_LOC));
                           cInt.add(cursor.getColumnIndex(KEY_MEDIA_META));
                           cInt.add(cursor.getColumnIndex(KEY_MEDIA_PREVIEW));
                           cInt.add(cursor.getColumnIndex(KEY_MEDIA_RID));
                           cInt.add(cursor.getColumnIndex(KEY_MEDIA_DEVICE_ID_FK));
                        }
                        do {
                            long recID = cursor.getLong(cInt.get(0));
                            String loc = cursor.getString(cInt.get(1));
                            String meta = cursor.getString(cInt.get(2));
                            byte[] preview = cursor.getBlob(cInt.get(3));
                            long rid = cursor.getLong(cInt.get(4));
                            int devId = cursor.getInt(cInt.get(5));

                            WCChat.ChatMedia aMedia = new WCChat.ChatMedia(recID);
                            aMedia.setSender(devId);
                            aMedia.setLocation(loc);
                            aMedia.setServerRID(rid);
                            aMedia.setMetaData(meta);
                            aMedia.setPreview(preview);
                            aMediaList.add(aMedia);
                        } while (cursor.moveToNext());
                    }
                } finally {
                    if (cursor != null && !cursor.isClosed()) {
                        cursor.close();
                    }
                }
            }
            db.setTransactionSuccessful();
            return aMediaList;
        } catch (Exception e) {
            Log.e(TAG, "Error while trying to get media from database");
            e.printStackTrace();
        } finally {
            db.endTransaction();
        }
        return null;
    }


    boolean getUnsentMsgs(DeviceItem aUser, List<WCChat.ChatMessage> messageList, int limit) {
        int added = 0;

        SQLiteDatabase db = getReadableDatabase();

        Cursor cursor = db.rawQuery(query_get_unsent, new String[] {
                Long.toString(aUser.getDbId()),
                Integer.toString(limit)
        } );
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
            Log.e(TAG, "Error while trying to get unsent messages from database");
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
            StringBuilder whereClause = new StringBuilder();
            whereClause.append(KEY_MSG_ID);
            whereClause.append("=?");

            for (WCChat.ChatMessage msg : list) {
                if (msg.getDbId() > 0) {
                    ContentValues values = new ContentValues();
                    values.put(KEY_MSG_STATE, newState);
                    db.update(TABLE_MSGS, values, whereClause.toString(),
                            new String[]{Long.toString(msg.getDbId())});
                }
            }
            db.setTransactionSuccessful();
        } catch (Exception e) {
            Log.e(TAG, "Error while trying to update messages");
            e.printStackTrace();
        } finally {
            db.endTransaction();
        }
    }

    @SuppressLint("DefaultLocale")
    public boolean checkNewMessages(String aUserName,
                             String aDeviceName,
                             String aLastCheck, List<WCChat.ChatMessage> aList) {
        int added = 0;
        String POSTS_SELECT_QUERY;
        ArrayList<String> aParams = new ArrayList<>();
        if (aLastCheck != null && aLastCheck.length() > 0) {
            POSTS_SELECT_QUERY = query_get_check_new_msgs_1;
            aParams.add(aDeviceName);
            aParams.add(aLastCheck);
            aParams.add(aUserName);
        }
        else {
            POSTS_SELECT_QUERY = query_get_check_new_msgs_2;
            aParams.add(aDeviceName);
            aParams.add(aUserName);
        }

        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery(POSTS_SELECT_QUERY, aParams.toArray(new String[]{}));
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
                    String slsttamp = cursor.getString(7);

                    WCChat.ChatMessage newPost = new WCChat.ChatMessage(msgID);
                    DeviceItem aSrcDevice = new DeviceItem(null, aUserName);
                    aSrcDevice.setDbId(dbID);
                    aSrcDevice.complete(srcDeviceName);
                    newPost.setSender(aSrcDevice);
                    newPost.setRawMessage(txt);
                    newPost.setServerTimeStamp(stamp);
                    newPost.setTimeStamp(slsttamp);
                    newPost.setTarget(msgTrgDevice);
                    newPost.setState(state);
                    aList.add(newPost);

                    added++;
                } while(cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error while trying to check new messages from database");
            e.printStackTrace();
        } finally {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        }
        return (added > 0);
    }

    public String getLastMsgTimeStamp(String aUserName, String aDeviceName) {
        SQLiteDatabase db = getReadableDatabase();

        String lstStamp;
        try {
            db.beginTransaction();

            stmt_get_last_timestamp.bindString(1, aUserName);
            stmt_get_last_timestamp.bindString(2, aDeviceName);
            lstStamp = stmt_get_last_timestamp.simpleQueryForString();
            if (lstStamp == null) lstStamp = "";

            db.setTransactionSuccessful(); // This commits the transaction if there were no exceptions
        } catch (Exception e) {
            Log.e(TAG, "Error while trying to get last timestamp from database");
            e.printStackTrace();
            lstStamp = "";
        } finally {
            db.endTransaction();
        }

        return lstStamp;
    }

    @SuppressLint("DefaultLocale")
    public int getNewMessagesCount(String aUserName,
                                   String aDeviceName,
                                   String aLastSync, List<WCChat.DeviceMsgsCnt> aList) {
        int added = 0;
        String POSTS_SELECT_QUERY;
        ArrayList<String> aParams = new ArrayList<>();
        if (aLastSync != null && aLastSync.length() > 0) {
            POSTS_SELECT_QUERY = query_get_check_new_msgs_count_1;
            aParams.add(aDeviceName);
            aParams.add(aLastSync);
            aParams.add(aUserName);
        }
        else {
            POSTS_SELECT_QUERY = query_get_check_new_msgs_count_2;
            aParams.add(aDeviceName);
            aParams.add(aUserName);
        }

        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery(POSTS_SELECT_QUERY, aParams.toArray(new String[]{}));
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
            Log.e(TAG, "Error while trying to get count of new messages from database");
            e.printStackTrace();
        } finally {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        }
        return added;
    }

    public boolean getNewMessages(DeviceItem aUser,
                           DeviceItem aFrom,
                           List<WCChat.ChatMessage> messageList) {
        int added = 0;

        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery(query_get_new_msgs, new String[] {
                Long.toString(aUser.getDbId()),
                Long.toString(aFrom.getDbId()),
                aUser.getLstSync(),
                aUser.getDeviceUserName(),
                aFrom.getLstSync()
        });
        try {
            if (cursor.moveToFirst()) {
                do {
                    long msgID = cursor.getLong(0);
                    long msgSrcDeviceId = cursor.getLong(1);
                    int state = cursor.getInt(2);
                    String msgTrgDevice = cursor.getString(3);
                    String txt = cursor.getString(4);
                    String stamp = cursor.getString(5);
                    String lstamp = cursor.getString(6);
                    String lstamp_id = cursor.getString(7);

                    DeviceItem dbDevice = null;
                    if (msgSrcDeviceId == aUser.getDbId()) {
                        dbDevice = aUser;
                    } else
                    if (msgSrcDeviceId == aFrom.getDbId()) {
                        dbDevice = aFrom;
                    }
                    if (dbDevice != null) {
                        dbDevice.setLstSync(lstamp_id);

                        WCChat.ChatMessage newPost = new WCChat.ChatMessage(msgID);
                        newPost.setSender(dbDevice);
                        newPost.setRawMessage(txt);
                        newPost.setServerTimeStamp(stamp);
                        newPost.setTimeStamp(lstamp);
                        newPost.setTarget(msgTrgDevice);
                        newPost.setState(state);
                        messageList.add(newPost);
                        added++;
                    }
                } while(cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error while trying to get new messages from database");
            e.printStackTrace();
        } finally {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        }
        return (added > 0);
    }


    public boolean getAllMessages(DeviceItem aUser,
                           DeviceItem aFrom,
                           List<WCChat.ChatMessage> messageList) {
        messageList.clear();

        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery(query_get_all_msgs, new String[] {
                Long.toString(aUser.getDbId()),
                Long.toString(aFrom.getDbId()),
                aUser.getDeviceUserName()
        });
        try {
            if (cursor.moveToFirst()) {
                do {
                    long msgID = cursor.getLong(0);
                    long msgSrcDeviceId = cursor.getLong(1);
                    int state = cursor.getInt(2);
                    String msgTrgDevice = cursor.getString(3);
                    String txt = cursor.getString(4);
                    String stamp = cursor.getString(5);
                    String lstamp = cursor.getString(6);
                    String lstamp_id = cursor.getString(7);

                    DeviceItem dbDevice = null;
                    if (msgSrcDeviceId == aUser.getDbId()) {
                        dbDevice = aUser;
                    } else
                    if (msgSrcDeviceId == aFrom.getDbId()) {
                        dbDevice = aFrom;
                    }
                    if (dbDevice != null) {
                        dbDevice.setLstSync(lstamp_id);

                        WCChat.ChatMessage newPost = new WCChat.ChatMessage(msgID);
                        newPost.setSender(dbDevice);
                        newPost.setRawMessage(txt);
                        newPost.setServerTimeStamp(stamp);
                        newPost.setTimeStamp(lstamp);
                        newPost.setTarget(msgTrgDevice);
                        newPost.setState(state);
                        messageList.add(newPost);
                    }
                } while(cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error while trying to get all msgs from database");
            e.printStackTrace();
        } finally {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        }
        return (messageList.size() > 0);
    }
}
