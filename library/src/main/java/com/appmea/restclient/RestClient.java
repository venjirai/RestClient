package com.appmea.restclient;


import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONException;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import okhttp3.Cache;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.ConnectionSpec;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.TlsVersion;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class RestClient
{
    private static RestClient instance;
    private final OkHttpClient client;
    private final boolean loggingEnabled;
    private JsonParserWorker errorParser;
    private final String failureMessage;
    private OnGlobalErrorListener globalErrorListener;

    public static RestClient getInstance()
    {
        if (instance == null)
            throw new RuntimeException("Rest client needs to be initialized. Call initialize once first!");

        return instance;
    }

    public static void initialize(@NonNull OkHttpClient.Builder builder, @NonNull JsonParserWorker errorParser, @NonNull String networkFailureMessage,
                                  boolean loggingEnabled, @Nullable OnGlobalErrorListener globalErrorListener)
    {
        if (instance != null)
            throw new RuntimeException("Rest Client can only be initialized once!");

        instance = new RestClient(builder, errorParser, networkFailureMessage, loggingEnabled, globalErrorListener);
    }


    private RestClient(@NonNull OkHttpClient.Builder builder, @NonNull JsonParserWorker errorParser, @NonNull String networkFailureMessage,
                       boolean loggingEnabled, @Nullable OnGlobalErrorListener globalErrorListener)
    {
        this.errorParser = errorParser;
        this.loggingEnabled = loggingEnabled;
        this.failureMessage = networkFailureMessage;
        this.globalErrorListener = globalErrorListener;

        // add Accept-Language Header Interceptor
        builder.addInterceptor(new Interceptor()
        {
            @Override
            public Response intercept(Chain chain) throws IOException
            {
                Request originalRequest = chain.request();
                Request requestWithHeaders = originalRequest.newBuilder()
                        .header("Accept-Language", localeToBcp47Language(Locale.getDefault()))
                        .build();
                return chain.proceed(requestWithHeaders);
            }
        });

        client = builder.build();
    }

    public OkHttpClient getClient()
    {
        return client;
    }

    public boolean isLoggingEnabled()
    {
        return loggingEnabled;
    }

    public JsonParserWorker getErrorParser()
    {
        return errorParser;
    }

    public String getFailureMessage()
    {
        return failureMessage;
    }

    public OnGlobalErrorListener getGlobalErrorListener()
    {
        return globalErrorListener;
    }


    @SuppressWarnings("unchecked")
    public <T> RestCall makeCall(final Request request, final boolean callGlobalErrorListener, final JsonParserWorker jsonParserWorker, final RestResponse<T> callback)
    {
        return new RestCall(request, client.newCall(request), callGlobalErrorListener, jsonParserWorker, callback);
    }

    public WebSocket makeWebSocket(@NonNull final Request request, WebSocketListener listener)
    {
        return client.newWebSocket(request, listener);
    }

    public <T> RestCall makeAndExecuteCall(final Request request, final boolean callGlobalErrorListener, final JsonParserWorker jsonParserWorker, final RestResponse<T> callback)
    {
        RestCall restCall = new RestCall<T>(request, client.newCall(request), callGlobalErrorListener, jsonParserWorker, callback);
        executeCall(restCall);
        return restCall;
    }

    public <T> void executeCalls(final List<RestCall<T>> restCalls)
    {
        for (RestCall restCall : restCalls)
            executeCall(restCall);
    }

    public <T> void executeCall(final RestCall<T> restCall)
    {
        restCall.call.enqueue(new Callback()
        {
            Handler handler = new Handler(Looper.getMainLooper());

            @Override
            public void onFailure(final Call call, final IOException e)
            {
                handler.post(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        if (loggingEnabled)
                            Log.w("RestClient", "onFailure[" + restCall.request.tag() + "]: " + e.getMessage());

                        Error error = new Error(0, e.getMessage(), failureMessage, e, null);
                        try
                        {
                            restCall.callback.onFailure(restCall, null, error);
                        }
                        catch (Exception e)
                        {
                            e.printStackTrace();
                        }
                        if (restCall.callGlobalErrorListener && globalErrorListener != null)
                            globalErrorListener.onError(restCall, null, error);
                    }
                });
            }

            @Override
            public void onResponse(final Call call, final Response response) throws IOException
            {
                final String responseBody = response.body().string();

                if (response.isSuccessful())
                {
                    try
                    {
                        T data = null;
                        if (restCall.jsonParserWorker != null)
                            data = restCall.jsonParserWorker.run(responseBody);

                        if (loggingEnabled)
                            Log.w("RestClient", "onSuccess[" + restCall.request.tag() + "]: " + responseBody);

                        final T finalData = data;
                        handler.post(new Runnable()
                        {
                            @Override
                            public void run()
                            {
                                try
                                {
                                    restCall.callback.onSuccess(restCall, response, finalData);
                                }
                                catch (Exception e)
                                {
                                    e.printStackTrace();
                                }
                            }
                        });

                    }
                    // parsing error
                    catch (final Exception e)
                    {
                        e.printStackTrace();
                        handler.post(new Runnable()
                        {
                            @Override
                            public void run()
                            {
                                Error error = new Error(response.code(), "server error", "server error", e, responseBody);
                                try
                                {
                                    restCall.callback.onFailure(restCall, response, error);
                                }
                                catch (Exception e)
                                {
                                    e.printStackTrace();
                                }

                                if (restCall.callGlobalErrorListener && globalErrorListener != null)
                                    globalErrorListener.onError(restCall, response, error);
                            }
                        });

                    }

                }
                else
                {
                    // parse error message
                    Error error = new Error();

                    if (!responseBody.isEmpty())
                    {
                        try
                        {
                            Error parsedError = errorParser.run(responseBody);
                            error.setCode(parsedError.getCode());
                            error.setMessage(parsedError.getMessage());
                            error.setResponseBody(responseBody);
                        }
                        catch (JSONException e)
                        {
                            error.setCode(response.message());
                            error.setMessage(response.message());
                            error.setResponseBody(responseBody);
                            error.setException(e);
                            e.printStackTrace();
                        }
                    }
                    else
                    {
                        error.setCode(response.message());
                        error.setMessage(response.message());
                        error.setResponseBody(null);
                        error.setException(new Exception("response body is null"));
                    }

                    error.setStatusCode(response.code());

                    if (loggingEnabled)
                        Log.w("RestClient", "onFailure[" + restCall.request.tag() + "]: " + error.getStatusCode() + " " + error.getCode() + " " + error.getMessage());

                    final Error finalError = error;
                    handler.post(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            try
                            {
                                restCall.callback.onFailure(restCall, response, finalError);
                            }
                            catch (Exception e)
                            {
                                e.printStackTrace();
                            }

                            if (restCall.callGlobalErrorListener && globalErrorListener != null)
                                globalErrorListener.onError(restCall, response, finalError);
                        }
                    });
                }
            }
        });
    }

    /*
     * From https://github.com/apache/cordova-plugin-globalization/blob/master/src/android/Globalization.java#L140
     * @Description: Returns a well-formed ITEF BCP 47 language tag representing
     * the locale identifier for the client's current locale
     *
     * @Return: String: The BCP 47 language tag for the current locale
     */
    public static String localeToBcp47Language(Locale loc)
    {
        final char SEP = '-';       // we will use a dash as per BCP 47
        String language = loc.getLanguage();
        String region = loc.getCountry();
        String variant = loc.getVariant();

        // special case for Norwegian Nynorsk since "NY" cannot be a variant as per BCP 47
        // this goes before the string matching since "NY" wont pass the variant checks
        if (language.equals("no") && region.equals("NO") && variant.equals("NY"))
        {
            language = "nn";
            region = "NO";
            variant = "";
        }

        if (language.isEmpty() || !language.matches("\\p{Alpha}{2,8}"))
        {
            language = "und";       // Follow the Locale#toLanguageTag() implementation
            // which says to return "und" for Undetermined
        }
        else if (language.equals("iw"))
            language = "he";        // correct deprecated "Hebrew"
        else if (language.equals("in"))
            language = "id";        // correct deprecated "Indonesian"
        else if (language.equals("ji"))
            language = "yi";        // correct deprecated "Yiddish"

        // ensure valid country code, if not well formed, it's omitted
        if (!region.matches("\\p{Alpha}{2}|\\p{Digit}{3}"))
            region = "";

        // variant subtags that begin with a letter must be at least 5 characters long
        if (!variant.matches("\\p{Alnum}{5,8}|\\p{Digit}\\p{Alnum}{3}"))
            variant = "";

        StringBuilder bcp47Tag = new StringBuilder(language);
        if (!region.isEmpty())
            bcp47Tag.append(SEP).append(region);
        if (!variant.isEmpty())
            bcp47Tag.append(SEP).append(variant);

        return bcp47Tag.toString();
    }

}
