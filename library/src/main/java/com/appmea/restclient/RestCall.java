package com.appmea.restclient;

import okhttp3.Call;
import okhttp3.Request;

public class RestCall<T>
{
    public Request request;
    public Call call;
    public boolean callGlobalErrorListener;
    public JsonParserWorker jsonParserWorker;
    public RestResponse<T> callback;

    public RestCall(Request request, Call call, boolean callGlobalErrorListener, JsonParserWorker jsonParserWorker, RestResponse<T> callback)
    {
        this.request = request;
        this.call = call;
        this.callGlobalErrorListener = callGlobalErrorListener;
        this.jsonParserWorker = jsonParserWorker;
        this.callback = callback;
    }

}
