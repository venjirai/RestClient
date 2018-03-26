package com.appmea.restclient;

import okhttp3.Call;
import okhttp3.Response;

public abstract class RestResponse<T>
{
    public abstract void onSuccess(Call call, Response response, T data);
    public abstract void onFailure(Call call, Error error);
}
