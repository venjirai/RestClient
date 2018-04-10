package com.appmea.restclient;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

public class Error
{
    private int statusCode;
    @NonNull
    private String code;
    @NonNull
    private String message;
    @Nullable
    private Exception exception;
    @Nullable
    private String responseBody;

    public Error()
    {
        code = "";
        message = "";
    }

    public Error(int statusCode, @NonNull String code, @NonNull String message, @Nullable Exception e, @Nullable String responseBody)
    {
        this.statusCode = statusCode;
        this.code = code;
        this.message = message;
        this.exception = e;
        this.responseBody = responseBody;
    }

    public int getStatusCode()
    {
        return statusCode;
    }

    public String getMessage()
    {
        return message;
    }

    @Nullable
    public String getResponseBody()
    {
        return responseBody;
    }

    public void setResponseBody(@Nullable String responseBody)
    {
        this.responseBody = responseBody;
    }

    public Exception getException()
    {
        return exception;
    }

    public void setException(Exception exception)
    {
        this.exception = exception;
    }

    public void setStatusCode(int statusCode)
    {
        this.statusCode = statusCode;
    }

    public void setMessage(String message)
    {
        this.message = message;
    }

    public String getCode()
    {
        return code;
    }

    public void setCode(String code)
    {
        this.code = code;
    }
}
