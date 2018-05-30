package com.appmea.restclient;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import okhttp3.Call;
import okhttp3.Response;

public class Resource<T>
{
    public enum Status
    {
        SUCCESS,
        ERROR
    }

    @NonNull public Status status;
    @Nullable public T data;
    @Nullable public Call call;
    @Nullable public Response response;
    @Nullable public Error error;

    private Resource()
    {

    }

    private Resource(@NonNull Status status, @Nullable T data, @Nullable Call call, @Nullable Response response, @Nullable Error error)
    {
        this.status = status;
        this.data = data;
        this.call = call;
        this.response = response;
        this.error = error;
    }

    public void setCall(@Nullable Call call)
    {
        this.call = call;
    }

    public static <T> Resource<T> success(T data, Call call, Response response)
    {
        return new Resource<T>(Status.SUCCESS, data, call, response, null);
    }

    public static <T> Resource<T> error(Call call, Response response, Error error)
    {
        return new Resource<T>(Status.ERROR, null, call, response, error);
    }
}
