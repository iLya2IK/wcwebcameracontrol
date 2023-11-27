package com.sggdev.wcsdk;

public class WCRESTProtocol {
    public static final int REST_RESULT_OK             = 0;
    public static final int REST_ERR_UNSPECIFIED       = 1;
    public static final int REST_ERR_INTERNAL_UNK      = 2;
    public static final int REST_ERR_DATABASE_FAIL     = 3;
    public static final int REST_ERR_JSON_PARSER_FAIL  = 4;
    public static final int REST_ERR_JSON_FAIL         = 5;
    public static final int REST_ERR_NO_SUCH_SESSION   = 6;
    public static final int REST_ERR_NO_SUCH_USER      = 7;
    public static final int REST_ERR_NO_DEVICES        = 8;
    public static final int REST_ERR_NO_SUCH_RECORD    = 9;
    public static final int REST_ERR_NO_DATA_RETURNED  = 10;
    public static final int REST_ERR_EMPTY_REQUEST     = 11;
    public static final int REST_ERR_MALFORMED_REQUEST = 12;
    public static final int REST_ERR_NO_CHANNEL        = 13;
    public static final int REST_ERR_ERRORED_STREAM    = 14;
    public static final int REST_ERR_NO_SUCH_DEVICE    = 15;

    public static final int REST_ERR_NETWORK_UNK       = 16;
    public static final int REST_ERR_NETWORK_TIMEOUT   = 17;
    public static final int REST_ERR_NETWORK_HOST      = 18;
    public static final int REST_ERR_NETWORK_SHUT      = 19;
    public static final int REST_ERR_NETWORK_IO        = 20;
    public static final int REST_ERR_NETWORK_ILLEGAL   = 21;
    public static final int REST_ERR_NETWORK_ON_LOGIN  = 22;
    public static final int REST_DISCONNECT_BY_USER    = 23;

    public static final String [] REST_RESPONSE_ERRORS  = {
                                  "NO_ERROR",
                                  "UNSPECIFIED",
                                  "INTERNAL_UNKNOWN_ERROR",
                                  "DATABASE_FAIL",
                                  "JSON_PARSER_FAIL",
                                  "JSON_FAIL",
                                  "NO_SUCH_SESSION",
                                  "NO_SUCH_USER",
                                  "NO_DEVICES_ONLINE",
                                  "NO_SUCH_RECORD",
                                  "NO_DATA_RETURNED",
                                  "EMPTY_REQUEST",
                                  "MALFORMED_REQUEST",
                                  "NO_CHANNEL",
                                  "ERRORED_STREAM",
                                  "NO_SUCH_DEVICE",
                                  "REST_ERR_NETWORK_UNK",
                                  "REST_ERR_NETWORK_TIMEOUT",
                                  "REST_ERR_NETWORK_HOST",
                                  "REST_ERR_NETWORK_SHUT",
                                  "REST_ERR_NETWORK_IO",
                                  "REST_ERR_NETWORK_ILLEGAL",
                                  "REST_ERR_NETWORK_ON_LOGIN",
                                  "REST_DISCONNECT_BY_USER"};


    public static final String JSON_OK        = "OK";
    public static final String JSON_BAD       = "BAD";

    public static final String JSON_MSG       = "msg";
    public static final String JSON_MSGS      = "msgs";
    public static final String JSON_RECORDS   = "records";
    public static final String JSON_RESULT    = "result";
    public static final String JSON_CODE      = "code";
    public static final String JSON_NAME      = "name";
    public static final String JSON_PASS      = "pass";
    public static final String JSON_SHASH     = "shash";
    public static final String JSON_META      = "meta";
    public static final String JSON_REC       = "record";
    public static final String JSON_STAMP     = "stamp";
    public static final String JSON_RID       = "rid";
    public static final String JSON_MID       = "mid";
    public static final String JSON_SYNC      = "sync";
    public static final String JSON_DEVICE    = "device";
    public static final String JSON_DEVICES   = "devices";
    public static final String JSON_TARGET    = "target";
    public static final String JSON_PARAMS    = "params";
    public static final String JSON_CONFIG    = "config";
    public static final String JSON_KIND      = "kind";
    public static final String JSON_DESCR     = "descr";
    public static final String JSON_MIVALUE   = "miv";
    public static final String JSON_MAVALUE   = "mav";
    public static final String JSON_DEFVALUE  = "dv";
    public static final String JSON_FVALUE    = "fv";

    public static final String WC_REST_authorize = "/authorize.json";
    public static final String WC_REST_addRecord = "/addRecord.json";
    public static final String WC_REST_addMsgs = "/addMsgs.json";
    public static final String WC_REST_getRecordMeta = "/getRecordMeta.json";
    public static final String WC_REST_getRecordData = "/getRecordData.json";
    public static final String WC_REST_getRecordCount = "/getRecordCount.json";
    public static final String WC_REST_deleteRecords = "/deleteRecords.json";
    public static final String WC_REST_getMsgs = "/getMsgs.json";
    public static final String WC_REST_getMsgsAndSync = "/getMsgsAndSync.json";
    public static final String WC_REST_getDevicesOnline = "/getDevicesOnline.json";
    public static final String WC_REST_getStreams = "/getStreams.json";
    public static final String WC_REST_getConfig = "/getConfig.json";
    public static final String WC_REST_setConfig = "/setConfig.json";
    public static final String WC_REST_heartBit = "/heartBit.json";
    public static final String WC_REST_getServerTime = "/getServerTime.json";

    public static final String WC_REST_strInput = "input.raw";
    public static final String WC_REST_strOutput = "output.raw";
}
