package com.appmea.restclient;

import okhttp3.Call;
import okhttp3.Response;

public abstract class RestResponse<T>
{
    public abstract void onSuccess(RestCall restCall, Response response, T data);
    public abstract void onFailure(RestCall restCall, Response response, Error error);
}
