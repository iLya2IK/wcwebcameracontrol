package com.sggdev.wcwebcameracontrol;

import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.REST_ERR_NETWORK_UNK;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import okhttp3.internal.http2.ConnectionShutdownException;

public class WCTask extends Thread {
    private final WCHTTPClient client;
    private String errorStr;
    private int errorCode = REST_ERR_NETWORK_UNK;
    private String mURL;

    WCTask(WCHTTPClient aClient) {
        client = aClient;
    }

    void execute(String... params) {
        setParams(params);
        start();
    }

    void setParams(String... params) {
        mURL = params[0];
    }

    String getURL() {
        return mURL;
    }

    String getErrorStr() {
        return errorStr;
    }

    int getErrorCode() {
        return errorCode;
    }

    void setErrorCode(int aErrorCode) {
        errorCode = aErrorCode;
    }

    void setErrorStr(String aErrorString) {
        errorStr = aErrorString;
    }

    WCHTTPClient getClient() {return client;}

    void internalExecute(boolean sync) {  }

    void consumeException(IOException e) {
        errorStr = e.toString();
        if (e instanceof SocketTimeoutException) errorCode = WCRESTProtocol.REST_ERR_NETWORK_TIMEOUT;
        else if (e instanceof UnknownHostException) errorCode = WCRESTProtocol.REST_ERR_NETWORK_HOST;
        else if (e instanceof ConnectionShutdownException) errorCode = WCRESTProtocol.REST_ERR_NETWORK_SHUT;
        else errorCode = WCRESTProtocol.REST_ERR_NETWORK_IO;
    }

    @Override
    public void run() {
        try {
            internalExecute(true);
        } catch (Exception e) {
            errorStr = e.toString();
            if (e instanceof IllegalStateException) errorCode = WCRESTProtocol.REST_ERR_NETWORK_ILLEGAL;
            else errorCode = WCRESTProtocol.REST_ERR_NETWORK_UNK;
        }
    }
}