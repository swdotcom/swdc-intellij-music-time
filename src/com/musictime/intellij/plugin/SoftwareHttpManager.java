package com.musictime.intellij.plugin;

import com.musictime.intellij.plugin.musicjava.Client;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.*;
import org.apache.http.entity.StringEntity;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SoftwareHttpManager implements Callable<HttpResponse> {

    public static final Logger LOG = Logger.getLogger("Software");

    private String payload;
    private String api;
    private String httpMethodName;
    private HttpClient httpClient;
    private String overridingJwt;

    public SoftwareHttpManager(String api, String httpMethodName, String payload, String overridingJwt, HttpClient httpClient) {
        this.payload = payload;
        this.api = api;
        this.httpMethodName = httpMethodName;
        this.overridingJwt = overridingJwt;
        this.httpClient = httpClient;
    }

    @Override
    public HttpResponse call() {
        HttpUriRequest req = null;
        try {
            HttpResponse response = null;

            switch (httpMethodName) {
                case HttpPost.METHOD_NAME:
                    req = new HttpPost("" + Client.api_endpoint + this.api);
                    if (payload != null) {
                        //
                        // add the json payload
                        //
                        StringEntity params = new StringEntity(payload);
                        ((HttpPost)req).setEntity(params);
                    }
                    break;
                case HttpDelete.METHOD_NAME:
                    req = new HttpDelete(Client.api_endpoint + "" + this.api);
                    break;
                case HttpPut.METHOD_NAME:
                    req = new HttpPut(Client.api_endpoint + "" + this.api);
                    if (payload != null) {
                        //
                        // add the json payload
                        //
                        StringEntity params = new StringEntity(payload);
                        ((HttpPut)req).setEntity(params);
                    }
                    break;
                default:
                    req = new HttpGet(Client.api_endpoint + "" + this.api);
                    break;
            }


            String jwtToken = (this.overridingJwt != null) ? this.overridingJwt : SoftwareCoSessionManager.getItem("jwt");
            // obtain the jwt session token if we have it
            if (jwtToken != null) {
                req.addHeader("Authorization", jwtToken);
            }

            req.addHeader("Content-type", "application/json");

            if (payload != null) {
                LOG.log(Level.INFO, SoftwareCoMusic.getPluginName() + ": Sending API request: {0}, payload: {1}", new Object[]{api, payload});
            }

            // execute the request
            response = httpClient.execute(req);

            //
            // Return the response
            //
            return response;
        } catch (IOException e) {
            LOG.log(Level.WARNING, SoftwareCoMusic.getPluginName() + ": Unable to make api request.{0}", e.getMessage());
            LOG.log(Level.INFO, SoftwareCoMusic.getPluginName() + ": Sending API request: " + this.api);
        }

        return null;
    }
}
