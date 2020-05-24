package com.musictime.intellij.plugin.musicjava;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.musictime.intellij.plugin.SoftwareResponse;
import com.musictime.intellij.plugin.fs.FileManager;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.HttpClientBuilder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Client {

    public static final Logger LOG = Logger.getLogger("Client");

    // set the api endpoint to use
    public final static String api_endpoint = "https://api.software.com";
    // set the api endpoint for spotify
    public final static String spotify_endpoint = "https://api.spotify.com";
    // set the launch url to use
    public final static String launch_url = "https://app.software.com";

    public static String pluginName = "Music Time";

    public static HttpClient httpClient;
    public static HttpClient pingClient;

    public static JsonParser jsonParser = new JsonParser();

    public final static ExecutorService EXECUTOR_SERVICE = Executors.newCachedThreadPool();

    static {
        // initialize the HttpClient
        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(5000)
                .setConnectionRequestTimeout(5000)
                .setSocketTimeout(5000)
                .build();

        pingClient = HttpClientBuilder.create().setDefaultRequestConfig(config).build();
        httpClient = HttpClientBuilder.create().build();
    }

    public static SoftwareResponse makeSpotifyApiCallWithAuth(String api, String httpMethodName, String payload, String encodedAuth) {
        return apiCall(api, httpMethodName, payload, true, encodedAuth);
    }

    public static SoftwareResponse makeSpotifyApiCall(String api, String httpMethodName, String payload) {
        return apiCall(api, httpMethodName, payload, true, null);
    }

    public static SoftwareResponse makeApiCall(String api, String httpMethodName, String payload) {
        return apiCall(api, httpMethodName, payload, false, null);
    }

    public static SoftwareResponse makeApiCall(String api, String httpMethodName, String payload, boolean isSpotifyApiCall) {
        return apiCall(api, httpMethodName, payload, isSpotifyApiCall, null);
    }

    private static SoftwareResponse apiCall(String api, String httpMethodName, String payload, boolean isSpotifyApiCall, String encodedAuth) {

        SoftwareResponse softwareResponse = new SoftwareResponse();

        SpotifyHttpManager spotifyTask = null;
        SoftwareHttpManager httpTask = null;

        Future<HttpResponse> response = null;

        if (!isSpotifyApiCall) {
            // if the server is having issues, we'll timeout within 5 seconds for these calls
            httpTask = new SoftwareHttpManager(api, httpMethodName, payload, httpClient);
            response = EXECUTOR_SERVICE.submit(httpTask);
        } else {
            String accesstoken = encodedAuth == null ? FileManager.getItem("spotify_access_token") : encodedAuth;
            accesstoken = "Bearer " + accesstoken;
            spotifyTask = new SpotifyHttpManager(api, httpMethodName, payload, accesstoken, httpClient);
            response = EXECUTOR_SERVICE.submit(spotifyTask);
        }

        //
        // Handle the Future if it exist
        //
        if (response != null) {
            try {
                HttpResponse httpResponse = response.get();
                if (httpResponse != null) {
                    int statusCode = httpResponse.getStatusLine().getStatusCode();
                    if (statusCode < 300) {
                        softwareResponse.setIsOk(true);
                    }
                    softwareResponse.setCode(statusCode);
                    HttpEntity entity = httpResponse.getEntity();
                    JsonObject jsonObj = null;
                    if (entity != null) {
                        try {
                            ContentType contentType = ContentType.getOrDefault(entity);
                            String mimeType = contentType.getMimeType();
                            String jsonStr = getStringRepresentation(entity);
                            softwareResponse.setJsonStr(jsonStr);
                            // LOG.log(Level.INFO, "Code Time: API response {0}", jsonStr);
                            if (jsonStr != null && mimeType.indexOf("text/plain") == -1) {
                                Object jsonEl = null;
                                try {
                                    jsonEl = jsonParser.parse(jsonStr);
                                } catch (Exception e) {
                                    //
                                }

                                if (jsonEl != null && jsonEl instanceof JsonElement) {
                                    try {
                                        JsonElement el = (JsonElement)jsonEl;
                                        if (el.isJsonPrimitive()) {
                                            if (statusCode < 300) {
                                                softwareResponse.setDataMessage(el.getAsString());
                                            } else {
                                                softwareResponse.setErrorMessage(el.getAsString());
                                            }
                                        } else {
                                            jsonObj = ((JsonElement) jsonEl).getAsJsonObject();
                                            softwareResponse.setJsonObj(jsonObj);
                                        }
                                    } catch (Exception e) {
                                        LOG.log(Level.WARNING, "Unable to parse response data: {0}", e.getMessage());
                                    }
                                }
                            }
                        } catch (IOException e) {
                            String errorMessage = pluginName + ": Unable to get the response from the http request for api " + api + ", error: " + e.getMessage();
                            softwareResponse.setErrorMessage(errorMessage);
                            LOG.log(Level.WARNING, errorMessage);
                        }
                    }

                    if (statusCode >= 400 && statusCode < 500 && jsonObj != null) {
                        if (jsonObj.has("code")) {
                            String code = jsonObj.get("code").getAsString();
                            if (code != null && code.equals("DEACTIVATED")) {
                                softwareResponse.setDeactivated(true);
                            }
                        }
                    }
                }
            } catch (InterruptedException | ExecutionException e) {
                String errorMessage = pluginName + ": Unable to get the response from the http request for api " + api + ", error: " + e.getMessage();
                softwareResponse.setErrorMessage(errorMessage);
                LOG.log(Level.WARNING, errorMessage);
            }
        }

        return softwareResponse;
    }

    private static String getStringRepresentation(HttpEntity res) throws IOException {
        if (res == null) {
            return null;
        }

        ContentType contentType = ContentType.getOrDefault(res);
        String mimeType = contentType.getMimeType();
        boolean isPlainText = mimeType.indexOf("text/plain") != -1;

        InputStream inputStream = res.getContent();

        // Timing information--- verified that the data is still streaming
        // when we are called (this interval is about 2s for a large response.)
        // So in theory we should be able to do somewhat better by interleaving
        // parsing and reading, but experiments didn't show any improvement.
        //

        StringBuffer sb = new StringBuffer();
        InputStreamReader reader;
        reader = new InputStreamReader(inputStream, Charset.forName("UTF-8"));

        BufferedReader br = new BufferedReader(reader);
        boolean done = false;
        while (!done) {
            String aLine = br.readLine();
            if (aLine != null) {
                sb.append(aLine);
                if (isPlainText) {
                    sb.append("\n");
                }
            } else {
                done = true;
            }
        }
        br.close();

        return sb.toString();
    }
}
