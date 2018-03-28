package com.appmea.restclient;

import org.json.JSONException;

public interface JsonParserWorker
{
    <T> T run(String jsonString) throws JSONException;
}
