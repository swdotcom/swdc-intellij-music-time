package com.musictime.intellij.plugin.musicjava;

import com.musictime.intellij.plugin.SoftwareCoMusic;
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

public class SoftwareHttpManager implements Callable<HttpResponse> {

    public static final Logger LOG = Logger.getLogger("SoftwareHttpManager");

    private String payload;
    private String api;
    private String httpMethodName;
    private HttpClient httpClient;

    public SoftwareHttpManager(String api, String httpMethodName, String payload, HttpClient httpClient) {
        this.payload = payload;
        this.api = api;
        this.httpMethodName = httpMethodName;
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
                    break;
                default:
                    req = new HttpGet(Client.api_endpoint + "" + this.api);
                    break;
            }



            req.addHeader("Authorization", FileManager.getItem("jwt"));
            req.addHeader("Content-Type", "application/json");

            SoftwareCoUtils.TimesData timesData = SoftwareCoUtils.getTimesData();
            req.addHeader("X-SWDC-Plugin-Id", String.valueOf(SoftwareCoUtils.pluginId));
            req.addHeader("X-SWDC-Plugin-Name", SoftwareCoMusic.getPluginName());
            req.addHeader("X-SWDC-Plugin-Version", SoftwareCoMusic.getVersion());
            req.addHeader("X-SWDC-Plugin-OS", SoftwareCoUtils.getOs());
            req.addHeader("X-SWDC-Plugin-TZ", timesData.timezone);
            req.addHeader("X-SWDC-Plugin-Offset", String.valueOf(timesData.offset));

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
