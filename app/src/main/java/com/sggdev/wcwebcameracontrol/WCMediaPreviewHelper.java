package com.sggdev.wcwebcameracontrol;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class WCMediaPreviewHelper {
    private final byte[] mBlob;
    private final ByteArrayOutputStream oStream;
    private final DataOutputStream oDataStream;
    private final ByteArrayInputStream iStream;
    private final DataInputStream iDataStream;

    public WCMediaPreviewHelper() {
        iStream = null;
        oStream = new ByteArrayOutputStream();
        oDataStream = new DataOutputStream(oStream);
        mBlob = null;
        iDataStream = null;
    }

    public WCMediaPreviewHelper(byte[] blob) {
        mBlob = blob;
        iStream = new ByteArrayInputStream(mBlob);
        iDataStream = new DataInputStream(iStream);
        oStream = null;
        oDataStream = null;
    }

    private void flush() {
        if (oDataStream != null) {
            try {
                oDataStream.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void writeInt32(int i) {
        if (oDataStream != null) {
            try {
                oDataStream.writeInt(i);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void writeInt64(long l) {
        if (oDataStream != null) {
            try {
                oDataStream.writeLong(l);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void writeBlob(byte[] b, int sz) {
        if (oStream != null)
            oStream.write(b, 0, sz);
    }

    public int readInt32() {
        if (iDataStream != null) {
            try {
                return iDataStream.readInt();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return 0;
    }

    public long readInt64() {
        if (iDataStream != null) {
            try {
                return iDataStream.readLong();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return 0;
    }

    public int readBlob(byte[] b, int sz) {
        if (iStream != null) {
            return iStream.read(b, 0, sz);
        }
        return 0;
    }

    public ByteArrayInputStream getByteInputStream() {
        return iStream;
    }

    public ByteArrayOutputStream getByteOutputStream() {
        flush();
        return oStream;
    }

    public byte[] getBlob() {
        if (oStream != null) {
            flush();
            return oStream.toByteArray();
        }
        else
            return mBlob;
    }
}
