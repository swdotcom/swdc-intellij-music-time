package com.softwareco.intellij.plugin.slack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.ide.BrowserUtil;
import com.softwareco.intellij.plugin.SoftwareCoSessionManager;
import com.softwareco.intellij.plugin.SoftwareCoUtils;
import com.softwareco.intellij.plugin.SoftwareHttpManager;
import com.softwareco.intellij.plugin.SoftwareResponse;
import com.softwareco.intellij.plugin.musicjava.Util;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ContentType;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SlackControlManager {
    public static final Logger LOG = Logger.getLogger("SlackControlManager");

    public static String ACCESS_TOKEN = null;
    public static boolean slackCacheState = false;
    public static Map<String, String> slackChannels = new HashMap<>(); // <id, name>
    public final static String api_endpoint = "https://slack.com/api/";

    public static SoftwareResponse makeApiCall(String api, String httpMethodName, String payload) {
        return makeApiCall(api, httpMethodName, payload, null);
    }

    public static SoftwareResponse makeApiCall(String api, String httpMethodName, String payload, String overridingJwt) {

        SoftwareResponse softwareResponse = new SoftwareResponse();

        SlackHttpManager slackHttpTask = null;

        slackHttpTask = new SlackHttpManager(api, httpMethodName, payload, overridingJwt, SoftwareCoUtils.pingClient);

        Future<HttpResponse> response = SoftwareCoUtils.EXECUTOR_SERVICE.submit(slackHttpTask);

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
                            String jsonStr = SoftwareCoUtils.getStringRepresentation(entity);
                            softwareResponse.setJsonStr(jsonStr);
                            if (jsonStr != null && mimeType.indexOf("text/plain") == -1) {
                                Object jsonEl = null;
                                try {
                                    jsonEl = SoftwareCoUtils.jsonParser.parse(jsonStr);
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
                            String errorMessage = "Music Time: Unable to get the response from the http request, error: " + e.getMessage();
                            softwareResponse.setErrorMessage(errorMessage);
                            LOG.log(Level.WARNING, errorMessage);
                        }
                    }

                    if (statusCode >= 400 && statusCode < 500 && jsonObj != null) {
                        if (jsonObj.has("code")) {
                            String code = jsonObj.get("code").getAsString();
                            if (code != null && code.equals("DEACTIVATED")) {
                                SoftwareCoUtils.setStatusLineMessage(
                                        "warning.png", "Music Time", "To see your music data, please reactivate your account.");
                                softwareResponse.setDeactivated(true);
                            }
                        }
                    }
                }
            } catch (InterruptedException | ExecutionException e) {
                String errorMessage = "Music Time: Unable to get the response from the http request, error: " + e.getMessage();
                softwareResponse.setErrorMessage(errorMessage);
                LOG.log(Level.WARNING, errorMessage);
            }
        }

        return softwareResponse;
    }

    public static boolean isSlackConncted() { return slackCacheState; }

    public static void connectSlack() throws UnsupportedEncodingException {
        boolean serverIsOnline = SoftwareCoSessionManager.isServerOnline();
        if (!serverIsOnline) {
            SoftwareCoUtils.showOfflinePrompt(true);
        }

        String jwt = SoftwareCoSessionManager.getItem("jwt");
        String encodedJwt = URLEncoder.encode(jwt, "UTF-8");
        String api = SoftwareCoUtils.api_endpoint + "/auth/slack?integrate=slack&plugin=musictime&token=" + encodedJwt;
        BrowserUtil.browse(api);

        // Periodically check that the user has connected
        lazilyFetchSlackStatus(20);
    }

    public static void disconnectSlack() {
        boolean serverIsOnline = Util.isServerOnline();
        if (!serverIsOnline) {
            SoftwareCoUtils.showOfflinePrompt(false);
        }

        String api = "/auth/slack/disconnect";
        String jwt = SoftwareCoSessionManager.getItem("jwt");
        SoftwareResponse resp = SoftwareCoUtils.makeApiCall(api, HttpPut.METHOD_NAME, null, jwt);
        if (resp.isOk()) {
            boolean exist = false;
            JsonObject obj = resp.getJsonObj();
            if (obj != null && obj.has("auths")) {
                for(JsonElement array : obj.get("auths").getAsJsonArray()) {
                    if(array.getAsJsonObject().get("type").getAsString().equals("slack")) {
                        exist = true;
                    }
                }
                if(!exist) {
                    slackCacheState = exist;
                    ACCESS_TOKEN = null;
                    SoftwareCoSessionManager.setItem("slack_access_token", null);
                    SoftwareCoUtils.showMsgPrompt("Slack disconnected successfully!!!");
                }
            } else {
                LOG.log(Level.INFO, "Music Time: Unable to Disconnect Slack null response");
            }
        }
    }

    protected static void lazilyFetchSlackStatus(int retryCount) {
        boolean serverIsOnline = SoftwareCoSessionManager.isServerOnline();
        slackCacheState = isSlackConnected(serverIsOnline);

        if (!slackCacheState && retryCount > 0) {
            final int newRetryCount = retryCount - 1;
            new Thread(() -> {
                try {
                    Thread.sleep(5000);
                    lazilyFetchSlackStatus(newRetryCount);
                }
                catch (Exception e){
                    System.err.println(e);
                }
            }).start();
        } else {
            SoftwareCoUtils.showMsgPrompt("Slack connected successfully!!!");
        }
    }

    private static boolean isSlackConnected(boolean serverIsOnline) {
        JsonObject userObj = SoftwareCoUtils.getUser(serverIsOnline);
        if (userObj != null && userObj.has("email")) {
            // check if the email is valid
            String email = userObj.get("email").getAsString();
            if (SoftwareCoUtils.validateEmail(email)) {
                SoftwareCoSessionManager.setItem("jwt", userObj.get("plugin_jwt").getAsString());
                SoftwareCoSessionManager.setItem("name", email);
                for(JsonElement array : userObj.get("auths").getAsJsonArray()) {
                    if(array.getAsJsonObject().get("type").getAsString().equals("slack")) {
                        if(ACCESS_TOKEN == null) {
                            ACCESS_TOKEN = array.getAsJsonObject().get("access_token").getAsString();
                            SoftwareCoSessionManager.setItem("slack_access_token", ACCESS_TOKEN);
                        }
                        SoftwareCoUtils.jwt = userObj.get("plugin_token").getAsString();
                        slackCacheState = true;

                        return true;
                    }
                }
                return false;
            }
        }
        return slackCacheState;
    }

    public static void getSlackChannels() {
        boolean serverIsOnline = SoftwareCoSessionManager.isServerOnline();
        if (!serverIsOnline) {
            SoftwareCoUtils.showOfflinePrompt(false);
        }

        String accessToken = SoftwareCoSessionManager.getItem("slack_access_token");
        String api = "channels.list?token=" + accessToken + "&exclude_archived=true&exclude_members=true&pretty=1";
        SoftwareResponse resp = makeApiCall(api, HttpGet.METHOD_NAME, null);
        if (resp.isOk()) {
            JsonObject obj = resp.getJsonObj();
            if (obj != null && obj.has("channels")) {
                slackChannels.clear();
                for(JsonElement array : obj.get("channels").getAsJsonArray()) {
                    slackChannels.put(array.getAsJsonObject().get("name").getAsString(), array.getAsJsonObject().get("id").getAsString());
                }
            } else {
                LOG.log(Level.INFO, "Music Time: Unable to Get Slack Channels null response");
            }
        }

    }

    public static boolean postMessage(String payload) {
        boolean serverIsOnline = SoftwareCoSessionManager.isServerOnline();
        if (!serverIsOnline) {
            SoftwareCoUtils.showOfflinePrompt(false);
        }

        String accessToken = SoftwareCoSessionManager.getItem("slack_access_token");
        String api = "chat.postMessage";
        SoftwareResponse resp = makeApiCall(api, HttpPost.METHOD_NAME, payload, accessToken);
        if (resp.isOk()) {
            JsonObject obj = resp.getJsonObj();
            if (obj != null && obj.has("ok")) {
                return obj.get("ok").getAsBoolean();
            } else {
                LOG.log(Level.INFO, "Music Time: Unable to Post message on Slack Channel");
            }
        }
        return false;
    }
}
