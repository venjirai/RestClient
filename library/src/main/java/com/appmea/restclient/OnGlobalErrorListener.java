package com.appmea.restclient;

import android.support.annotation.Nullable;

import okhttp3.Call;
import okhttp3.Request;
import okhttp3.Response;

public interface OnGlobalErrorListener
{
    void onError(RestCall restCall, @Nullable Response response, Error error);
}
