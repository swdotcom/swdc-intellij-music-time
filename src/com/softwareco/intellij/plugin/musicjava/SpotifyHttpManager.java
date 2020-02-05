package com.softwareco.intellij.plugin.musicjava;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.*;
import org.apache.http.entity.StringEntity;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SpotifyHttpManager implements Callable<HttpResponse> {

    public static final Logger LOG = Logger.getLogger("SpotifyHttpManager");

    private String payload;
    private String api;
    private String httpMethodName;
    private HttpClient httpClient;
    private String overridingJwt;

    public SpotifyHttpManager(String api, String httpMethodName, String payload, String overridingJwt, HttpClient httpClient) {
        this.payload = payload;
        this.api = api;
        this.httpMethodName = httpMethodName;
        this.overridingJwt = overridingJwt;
        this.httpClient = httpClient;
    }

    @Override
    public HttpResponse call() throws Exception {
        HttpUriRequest req = null;
        try {
            HttpResponse response = null;

            switch (httpMethodName) {
                case HttpPost.METHOD_NAME:
                    if(this.overridingJwt != null && this.overridingJwt.contains("Basic"))
                        req = new HttpPost("https://accounts.spotify.com" + this.api);
                    else
                        req = new HttpPost("" + Client.spotify_endpoint + this.api);
                    if (payload != null) {
                        //
                        // add the json payload
                        //
                        StringEntity params = new StringEntity(payload);
                        ((HttpPost)req).setEntity(params);
                    }
                    break;
                case HttpDelete.METHOD_NAME:
                    if (payload != null) {
                        req = new HttpDeleteWithBody(Client.spotify_endpoint + "" + this.api);
                        //
                        // add the json payload
                        //
                        StringEntity params = new StringEntity(payload);
                        ((HttpDeleteWithBody)req).setEntity(params);
                    } else {
                        req = new HttpDelete(Client.spotify_endpoint + "" + this.api);
                    }
                    break;
                case HttpPut.METHOD_NAME:
                    req = new HttpPut(Client.spotify_endpoint + "" + this.api);
                    if (payload != null) {
                        //
                        // add the json payload
                        //
                        StringEntity params = new StringEntity(payload);
                        ((HttpPut)req).setEntity(params);
                    }
                    break;
                default:
                    req = new HttpGet(Client.spotify_endpoint + "" + this.api);
                    break;
            }


            String accessToken = this.overridingJwt;
            // obtain the jwt session token if we have it
            if (accessToken != null) {
                req.addHeader("Authorization", accessToken);
            }

            if(accessToken.contains("Basic"))
                req.addHeader("Content-Type", "application/x-www-form-urlencoded");
            else
                req.addHeader("Content-Type", "application/json");

            if (payload != null) {
                LOG.log(Level.INFO, Client.pluginName + ": Sending API request: {0}, payload: {1}", new Object[]{api, payload});
            }

            // execute the request
            response = httpClient.execute(req);

            //
            // Return the response
            //
            return response;
        } catch (IOException e) {
            LOG.log(Level.WARNING, Client.pluginName + ": Unable to make api request.{0}", e.getMessage());
            LOG.log(Level.INFO, Client.pluginName + ": Sending API request: " + this.api);
        }

        return null;
    }
}
