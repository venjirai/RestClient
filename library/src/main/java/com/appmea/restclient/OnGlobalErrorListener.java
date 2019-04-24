package com.appmea.restclient;

import androidx.annotation.Nullable;

import okhttp3.Response;

public interface OnGlobalErrorListener
{
    void onError(RestCall restCall, @Nullable Response response, Error error);
}
