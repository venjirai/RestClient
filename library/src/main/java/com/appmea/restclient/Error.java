package com.appmea.restclient;

public class Error
{
    private int statusCode;
    private String code;
    private String message;
    private Exception exception;

    public Error()
    {

    }

    public Error(int statusCode, String code, String message, Exception e)
    {
        this.statusCode = statusCode;
        this.code = code;
        this.message = message;
        this.exception = e;
    }

    public int getStatusCode()
    {
        return statusCode;
    }

    public String getMessage()
    {
        return message;
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
