package com.sggdev.wcwebcameracontrol;

public class WCHTTPStreamFrame {
    private byte[] data;
    private long len;
    private long id;

    WCHTTPStreamFrame(long aFrameid) {
        len = 0;
        data = null;
        id = aFrameid;
    }

    void setData(byte[] aData) {
        data = aData;
    }

    void setLen(long aLen) {
        len = aLen;
    }

    byte[] getData() {return data; }
    long getLen() {return len; }
    long getFrameId() {return id;}
}
