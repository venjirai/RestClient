package com.appmea.restclient;


import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
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
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

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

public class RestClient
{
    private static RestClient instance;
    private final OkHttpClient client;
    private final boolean loggingEnabled;
    public final MediaType mediaTypeJson;
    private JsonParserWorker errorParser;
    private final String failureMessage;
    private OnGlobalErrorListener globalErrorListener;

    public static RestClient getInstance()
    {
        if (instance == null)
            throw new RuntimeException("Rest client needs to be initialized. Call initialize once first!");

        return instance;
    }

    /**
     * Creates and initializes a new rest client instance
     *
     * @param connectTimeout Connection timeout in seconds
     * @param readTimeout    Read timeout in seconds
     * @param loggingEnabled Set to true to print all requests to logcat
     */
    public static void initialize(Cache cache, JsonParserWorker errorParser, String failureMessage, int connectTimeout, int readTimeout, boolean loggingEnabled,
                                  boolean trustAllCertificates, @Nullable OnGlobalErrorListener globalErrorListener)
    {
        if (instance != null)
            throw new RuntimeException("Rest Client can only be initialized once!");

        instance = new RestClient(cache, errorParser, failureMessage, connectTimeout, readTimeout, loggingEnabled, trustAllCertificates, globalErrorListener);
    }

    private RestClient(Cache cache, JsonParserWorker errorParser, String failureMessage, int connectTimeout, int readTimeout, boolean loggingEnabled,
                       boolean trustAllCertificates, @Nullable OnGlobalErrorListener globalErrorListener)
    {
        this.errorParser = errorParser;
        this.loggingEnabled = loggingEnabled;
        this.failureMessage = failureMessage;
        this.globalErrorListener = globalErrorListener;
        mediaTypeJson = MediaType.parse("application/json; charset=utf-8");

        OkHttpClient.Builder builder;

        // NEVER EVER ENABLE THIS FOR RELEASE BUILD IF YOU DON'T KNOW WHAT YOU ARE DOING
        if (trustAllCertificates)
        {
            final TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager()
                    {
                        @Override
                        public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException
                        {
                        }

                        @Override
                        public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException
                        {
                        }

                        @Override
                        public java.security.cert.X509Certificate[] getAcceptedIssuers()
                        {
                            return new java.security.cert.X509Certificate[]{};
                        }
                    }
            };

            // Install the all-trusting trust manager
            SSLSocketFactory sslSocketFactory = null;
            try
            {
                final SSLContext sslContext = SSLContext.getInstance("SSL");
                sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

                // Create an ssl socket factory with our all-trusting manager
                sslSocketFactory = sslContext.getSocketFactory();
            }

            catch (Exception e)
            {
                e.printStackTrace();
            }

            builder = new OkHttpClient.Builder()
                    .sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0])
                    .hostnameVerifier(new HostnameVerifier()
                    {
                        @Override
                        public boolean verify(String hostname, SSLSession session)
                        {
                            return true;
                        }
                    });
        }
        else
            builder = new OkHttpClient.Builder();


        builder
                .cache(cache)
                .followRedirects(true)
                .followSslRedirects(true)
                .retryOnConnectionFailure(true)
                .connectTimeout(connectTimeout, TimeUnit.SECONDS)
                .readTimeout(readTimeout, TimeUnit.SECONDS)
                .addInterceptor(new Interceptor()
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

        client = enableTls12OnPreLollipop(builder).build();
    }

    private static OkHttpClient.Builder enableTls12OnPreLollipop(OkHttpClient.Builder client)
    {
        if (Build.VERSION.SDK_INT >= 16 && Build.VERSION.SDK_INT <= 21)
        {
            try
            {
                SSLContext sc = SSLContext.getInstance("TLSv1.2");
                sc.init(null, null, null);
                client.sslSocketFactory(new Tls12SocketFactory(sc.getSocketFactory()));

                ConnectionSpec cs = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                        .tlsVersions(TlsVersion.TLS_1_2)
                        .build();

                List<ConnectionSpec> specs = new ArrayList<>();
                specs.add(cs);
                specs.add(ConnectionSpec.COMPATIBLE_TLS);
                specs.add(ConnectionSpec.CLEARTEXT);

                client.connectionSpecs(specs);
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }

        return client;
    }



    public <T> Call newCall(final Request request, final boolean callGlobalErrorListener, final JsonParserWorker jsonParserWorker, final RestResponse<T> callback)
    {
        Call call = client.newCall(request);
        call.enqueue(new Callback()
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
                            Log.w("RestClient", "onFailure[" + request.tag() + "]: " + e.getMessage());

                        try
                        {
                            callback.onFailure(call, new Error(0, e.getMessage(), failureMessage));
                        }
                        catch (Exception e)
                        {
                            e.printStackTrace();
                        }
                        if (callGlobalErrorListener && globalErrorListener != null)
                            globalErrorListener.onError(call, request, null, new Error(0, e.getMessage(), failureMessage));
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
                        if (jsonParserWorker != null)
                            data = jsonParserWorker.run(responseBody);

                        if (loggingEnabled)
                            Log.w("RestClient", "onSuccess[" + request.tag() + "]: " + responseBody);

                        final T finalData = data;
                        handler.post(new Runnable()
                        {
                            @Override
                            public void run()
                            {
                                try
                                {
                                    callback.onSuccess(call, response, finalData);
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
                                try
                                {
                                    callback.onFailure(call, new Error(response.code(), "server error", "server error"));
                                }
                                catch (Exception e)
                                {
                                    e.printStackTrace();
                                }

                                if (callGlobalErrorListener && globalErrorListener != null)
                                    globalErrorListener.onError(call, request, response, new Error(response.code(), "server error", "server error"));
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
                        }
                        catch (JSONException e)
                        {
                            error.setCode(response.message());
                            error.setMessage(response.message());
                            e.printStackTrace();
                        }
                    }
                    else
                    {
                        error.setCode(response.message());
                        error.setMessage(response.message());
                    }

                    error.setStatusCode(response.code());

                    if (loggingEnabled)
                        Log.w("RestClient", "onFailure[" + request.tag() + "]: " + error.getStatusCode() + " " + error.getCode() + " " + error.getMessage());

                    final Error finalError = error;
                    handler.post(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            try
                            {
                                callback.onFailure(call, finalError);
                            }
                            catch (Exception e)
                            {
                                e.printStackTrace();
                            }

                            if (callGlobalErrorListener && globalErrorListener != null)
                                globalErrorListener.onError(call, request, response, finalError);
                        }
                    });
                }
            }
        });

        return call;
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
