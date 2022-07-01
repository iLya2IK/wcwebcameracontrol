package com.sggdev.wcwebcameracontrol;

import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.JSON_DEVICE;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.JSON_SHASH;
import static com.sggdev.wcwebcameracontrol.WCRESTProtocol.WC_REST_strOutput;

import androidx.annotation.NonNull;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class WCStreamTask extends WCTask {
    private String mHash;
    private String mDevice;

    WCStreamTask(WCHTTPClient aClient) {
        super(aClient);
    }

    @Override
    void setParams(String... params) {
        super.setParams(params);
        mHash = params[1];
        mDevice = params[2];
    }

    @Override
    void internalExecute(boolean sync) {
        HttpUrl.Builder builder = HttpUrl.parse(getURL()).newBuilder();
        builder.addPathSegment(WC_REST_strOutput);
        builder.addQueryParameter(JSON_SHASH, mHash);
        builder.addQueryParameter(JSON_DEVICE, mDevice);
        HttpUrl aUrl = builder.build();
        Request request = new Request.Builder()
                .url(aUrl)
                .get()
                .tag(WC_REST_strOutput)
                .build();

        Call call =  getClient().getClient().newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                ResponseBody rb =  response.body();
                if (rb != null) rb.close();
            }

            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                consumeException(e);
            }
        });
    }
}
