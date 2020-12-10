package com.musictime.intellij.plugin;

import com.musictime.intellij.plugin.fs.FileManager;
import com.musictime.intellij.plugin.musicjava.Client;
import org.apache.commons.lang.StringUtils;
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
    private String overridingJwt = null;

    public SoftwareHttpManager(String api, String httpMethodName, String payload, String overridingJwt, HttpClient httpClient) {
        this.payload = payload;
        this.api = api;
        this.httpMethodName = httpMethodName;
        this.httpClient = httpClient;
        this.overridingJwt = overridingJwt;
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


            if (!StringUtils.isNotBlank(overridingJwt)) {
                req.addHeader("Authorization", overridingJwt);
            } else {
                req.addHeader("Authorization", FileManager.getItem("jwt"));
            }
            req.addHeader("Content-type", "application/json");

            SoftwareCoUtils.TimesData timesData = SoftwareCoUtils.getTimesData();
            req.addHeader("X-SWDC-Plugin-Id", String.valueOf(SoftwareCoUtils.pluginId));
            req.addHeader("X-SWDC-Plugin-Name", SoftwareCoMusic.getPluginName());
            req.addHeader("X-SWDC-Plugin-Version", SoftwareCoMusic.getVersion());
            req.addHeader("X-SWDC-Plugin-OS", SoftwareCoUtils.getOs());
            req.addHeader("X-SWDC-Plugin-TZ", timesData.timezone);
            req.addHeader("X-SWDC-Plugin-Offset", String.valueOf(timesData.offset));

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
