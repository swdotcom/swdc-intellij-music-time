package com.musictime.intellij.plugin.slack;

import com.musictime.intellij.plugin.*;
import com.musictime.intellij.plugin.SoftwareCoSessionManager;
import com.musictime.intellij.plugin.SoftwareCoUtils;
import com.musictime.intellij.plugin.fs.FileManager;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.*;
import org.apache.http.entity.StringEntity;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SlackHttpManager implements Callable<HttpResponse> {

    public static final Logger LOG = Logger.getLogger("SlackHttpManager");

    private String payload;
    private String api;
    private String httpMethodName;
    private HttpClient httpClient;
    private String overridingJwt;

    public SlackHttpManager(String api, String httpMethodName, String payload, String overridingJwt, HttpClient httpClient) {
        this.payload = payload;
        this.api = api;
        this.httpMethodName = httpMethodName;
        this.httpClient = httpClient;
        this.overridingJwt = overridingJwt;
    }

    @Override
    public HttpResponse call() throws Exception {
        HttpUriRequest req = null;
        try {
            HttpResponse response = null;

            switch (httpMethodName) {
                case HttpPost.METHOD_NAME:
                    req = new HttpPost("" + SlackControlManager.api_endpoint + this.api);
                    if (payload != null) {
                        //
                        // add the json payload
                        //
                        StringEntity params = new StringEntity(payload);
                        ((HttpPost)req).setEntity(params);
                    }
                    break;
                case HttpDelete.METHOD_NAME:
                    req = new HttpDelete(SlackControlManager.api_endpoint + "" + this.api);
                    break;
                case HttpPut.METHOD_NAME:
                    req = new HttpPut(SlackControlManager.api_endpoint + "" + this.api);
                    if (payload != null) {
                        //
                        // add the json payload
                        //
                        StringEntity params = new StringEntity(payload);
                        ((HttpPut)req).setEntity(params);
                    }
                    break;
                default:
                    req = new HttpGet(SlackControlManager.api_endpoint + "" + this.api);
                    break;
            }
            if(httpMethodName.equals("POST")) {
                String accessToken = (this.overridingJwt != null) ? this.overridingJwt : FileManager.getItem("slack_access_token");
                // obtain the access token if we have it
                if (accessToken != null) {
                    req.addHeader("Authorization", "Bearer " + accessToken);
                }

                req.addHeader("Content-type", "application/json");
            } else {
                req.addHeader("Content-type", "application/x-www-form-urlencoded");
            }

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
