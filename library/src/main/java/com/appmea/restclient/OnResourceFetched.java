package com.appmea.restclient;

public interface OnResourceFetched<T>
{
    void onFetched(Resource<T> resource);
}
