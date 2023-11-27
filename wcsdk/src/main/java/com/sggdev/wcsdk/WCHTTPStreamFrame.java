package com.sggdev.wcsdk;

public class WCHTTPStreamFrame {
    private byte[] data;
    private long len;
    private long id;

    public WCHTTPStreamFrame(long aFrameid) {
        len = 0;
        data = null;
        id = aFrameid;
    }

    public void setData(byte[] aData) {
        data = aData;
    }

    public void setLen(long aLen) {
        len = aLen;
    }

    public byte[] getData() {return data; }
    public long getLen() {return len; }
    public long getFrameId() {return id;}
}
