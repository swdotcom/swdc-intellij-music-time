/**
 * Copyright (c) 2018 by Software.com
 * All rights reserved
 */
package com.softwareco.intellij.plugin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.SystemUtils;
import org.apache.commons.net.util.Base64;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.HttpClientBuilder;

import javax.swing.*;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class SoftwareCoUtils {

    public static final Logger LOG = Logger.getLogger("SoftwareCoUtils");

    // set the api endpoint to use
    public final static String api_endpoint = "https://api.software.com";
    // set the launch url to use
    public final static String launch_url = "https://app.software.com";
    // set the api endpoint for spotify
    public final static String spotify_endpoint = "https://api.spotify.com";

    public static HttpClient httpClient;
    public static HttpClient pingClient;

    public static JsonParser jsonParser = new JsonParser();

    // sublime = 1, vs code = 2, eclipse = 3, intellij = 4, visual studio = 6, atom = 7
    public static int pluginId = 4;
    public static String VERSION = null;
    public static String pluginName = null;

    public final static ExecutorService EXECUTOR_SERVICE = Executors.newCachedThreadPool();

    private final static int EOF = -1;

    private static boolean fetchingResourceInfo = false;
    private static JsonObject lastResourceInfo = new JsonObject();
    private static boolean loggedInCacheState = false;

    private static boolean appAvailable = true;
    private static boolean showStatusText = true;
    private static String lastMsg = "";
    private static String lastTooltip = "";

    private static int lastDayOfMonth = 0;

    private static int DASHBOARD_LABEL_WIDTH = 25;
    private static int DASHBOARD_VALUE_WIDTH = 25;
    private static int MARKER_WIDTH = 4;

    private static String SERVICE_NOT_AVAIL =
            "Our service is temporarily unavailable.\n\nPlease try again later.\n";

    // jwt_from_apptoken_call
    public static String jwt = null;

    // Spotify variables
    private static String CLIENT_ID = null;
    private static String CLIENT_SECRET = null;
    private static String ACCESS_TOKEN = null;
    private static String REFRESH_TOKEN = null;
    private static String userStatus = null;
    private static boolean spotifyCacheState = false;
    public static String defaultbtn = "play";
    public static String spotifyUserId = null;
    public static List<String> playlistids = new ArrayList<>();
    public static String currentPlaylistId = null;
    public static List<String> playlistTracks = new ArrayList<>();
    public static String currentTrackId = null;
    public static String currentTrackName = null;
    public static List<String> spotifyDeviceIds = new ArrayList<>();
    public static String currentDeviceId = null;
    public static String currentDeviceName = null;
    public static int playerCounter = 0;
    public static String spotifyStatus = "Not Connected";

    // Slack variables
    private static boolean slackCacheState = false;

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

    public static void resetSpotify() {
        spotifyUserId = null;
        playlistTracks.clear();
        currentTrackId = null;
        currentTrackName = null;
        playlistids.clear();
        currentPlaylistId = null;
        spotifyDeviceIds.clear();
        currentDeviceId = null;
        currentDeviceName = null;
        ACCESS_TOKEN = null;
        REFRESH_TOKEN = null;
        userStatus = null;
        playerCounter = 0;
        defaultbtn = "play";
        spotifyStatus = "Not Connected";
    }

    public static boolean isLoggedIn() {
        return loggedInCacheState;
    }

    public static boolean isSpotifyConncted() { return spotifyCacheState; }

    public static boolean isSlackConncted() { return slackCacheState; }

    public static class UserStatus {
        public boolean loggedIn;
    }

    public static String getHostname() {
        List<String> cmd = new ArrayList<String>();
        cmd.add("hostname");
        String hostname = getSingleLineResult(cmd, 1);
        return hostname;
    }

    public static boolean isMusicTime() {
        if(pluginName.equals("Music Time")) {
            return true;
        }
        return false;
    }

    public static String getUserHomeDir() {
        return System.getProperty("user.home");
    }

    public static String getOs() {
        String osName = SystemUtils.OS_NAME;
        String osVersion = SystemUtils.OS_VERSION;
        String osArch = SystemUtils.OS_ARCH;

        String osInfo = "";
        if (osArch != null) {
            osInfo += osArch;
        }
        if (osInfo.length() > 0) {
            osInfo += "_";
        }
        if (osVersion != null) {
            osInfo += osVersion;
        }
        if (osInfo.length() > 0) {
            osInfo += "_";
        }
        if (osName != null) {
            osInfo += osName;
        }

        return osInfo;
    }

    public static boolean isLinux() {
        return (isWindows() || isMac()) ? false : true;
    }

    public static boolean isWindows() {
        return SystemInfo.isWindows;
    }

    public static boolean isMac() {
        return SystemInfo.isMac;
    }

    public static void updateServerStatus(boolean isOnlineStatus) {
        appAvailable = isOnlineStatus;
    }

    public static boolean isAppAvailable() {
        return appAvailable;
    }

    public static SoftwareResponse makeApiCall(String api, String httpMethodName, String payload) {
        return makeApiCall(api, httpMethodName, payload, null);
    }

    public static SoftwareResponse makeApiCall(String api, String httpMethodName, String payload, String overridingJwt) {

        SoftwareResponse softwareResponse = new SoftwareResponse();

        SoftwareHttpManager httpTask = null;
        SpotifyHttpManager spotifyTask = null;
        if (api.contains("/ping") || api.contains("/sessions") || api.contains("/dashboard") || api.contains("/users/plugin/accounts")) {
            // if the server is having issues, we'll timeout within 5 seconds for these calls
            httpTask = new SoftwareHttpManager(api, httpMethodName, payload, overridingJwt, httpClient);
        } else if (api.contains("/v1") || api.contains("/api/token")) {
            // if the server is having issues, we'll timeout within 5 seconds for these calls
            spotifyTask = new SpotifyHttpManager(api, httpMethodName, payload, overridingJwt, httpClient);
        } else {
            if (httpMethodName.equals(HttpPost.METHOD_NAME)) {
                // continue, POSTS encapsulated "invokeLater" with a timeout of 5 seconds
                httpTask = new SoftwareHttpManager(api, httpMethodName, payload, overridingJwt, pingClient);
            } else {
                if (!appAvailable) {
                    // bail out
                    softwareResponse.setIsOk(false);
                    return softwareResponse;
                }
                httpTask = new SoftwareHttpManager(api, httpMethodName, payload, overridingJwt, httpClient);
            }
        }
        Future<HttpResponse> response = null;
        if (api.contains("/v1") || api.contains("/api/token")) {
            response = EXECUTOR_SERVICE.submit(spotifyTask);
        } else {
            response = EXECUTOR_SERVICE.submit(httpTask);
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
                            String errorMessage = pluginName + ": Unable to get the response from the http request, error: " + e.getMessage();
                            softwareResponse.setErrorMessage(errorMessage);
                            LOG.log(Level.WARNING, errorMessage);
                        }
                    }

                    if (statusCode >= 400 && statusCode < 500 && jsonObj != null) {
                        if (jsonObj.has("code")) {
                            String code = jsonObj.get("code").getAsString();
                            if (code != null && code.equals("DEACTIVATED")) {
                                SoftwareCoUtils.setStatusLineMessage(
                                        "warning.png", pluginName, "To see your coding data in Code Time, please reactivate your account.");
                                softwareResponse.setDeactivated(true);
                            }
                        }
                    }
                }
            } catch (InterruptedException | ExecutionException e) {
                String errorMessage = pluginName + ": Unable to get the response from the http request, error: " + e.getMessage();
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

    public static void toggleStatusBar() {
        showStatusText = !showStatusText;

        if(pluginName.equals("Code Time")) {
            if (showStatusText) {
                SoftwareCoUtils.setStatusLineMessage(lastMsg, lastTooltip);
            } else {
                SoftwareCoUtils.setStatusLineMessage("clock.png", "", lastMsg + " | " + lastTooltip);
            }
        }
    }

    public static synchronized void setStatusLineMessage(
            final String singleMsg, final String tooltip) {
        setStatusLineMessage(null, singleMsg, null, null, tooltip);
    }

    public static synchronized void setStatusLineMessage(
            final String singleIcon, final String singleMsg,
            final String tooltip) {
        setStatusLineMessage(singleIcon, singleMsg, null, null, tooltip);
    }

    public static Project getOpenProject() {
        ProjectManager projMgr = ProjectManager.getInstance();
        Project[] projects = projMgr.getOpenProjects();
        if (projects != null && projects.length > 0) {
            return projects[0];
        }
        return null;
    }

    public static synchronized void setStatusLineMessage(
            final String kpmIcon, final String kpmMsg,
            final String timeIcon, final String timeMsg,
            final String tooltip) {
        try {
            Project p = getOpenProject();
            if (p == null) {
                return;
            }
            final StatusBar statusBar = WindowManager.getInstance().getStatusBar(p);

            if (statusBar != null) {
                updateStatusBar(kpmIcon, kpmMsg, timeIcon, timeMsg, tooltip);
            }
        } catch (Exception e) {
            //
        }
    }

    private static void updateStatusBar(final String kpmIcon, final String kpmMsg,
                                        final String timeIcon, final String timeMsg,
                                        final String tooltip) {
        if ( showStatusText ) {
            lastMsg = kpmMsg;
            lastTooltip = tooltip;
        }

        ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
                ProjectManager pm = ProjectManager.getInstance();
                if (pm != null && pm.getOpenProjects() != null && pm.getOpenProjects().length > 0) {
                    try {
                        Project p = pm.getOpenProjects()[0];
                        final StatusBar statusBar = WindowManager.getInstance().getStatusBar(p);

                        if (statusBar != null && pluginName.equals("Code Time")) {
                            String kpmmsgId = SoftwareCoStatusBarKpmTextWidget.KPM_TEXT_ID + "_kpmmsg";
                            String timemsgId = SoftwareCoStatusBarKpmIconWidget.KPM_ICON_ID + "_timemsg";
                            String kpmiconId = SoftwareCoStatusBarKpmIconWidget.KPM_ICON_ID + "_kpmicon";
                            String timeiconId = SoftwareCoStatusBarKpmIconWidget.KPM_ICON_ID + "_timeicon";

                            final String kpmMsgVal = kpmMsg != null ? kpmMsg : pluginName;

                            if (statusBar.getWidget(kpmmsgId) != null) {
                                statusBar.removeWidget(kpmmsgId);
                            }
                            if (statusBar.getWidget(timemsgId) != null) {
                                statusBar.removeWidget(timemsgId);
                            }
                            if (statusBar.getWidget(kpmiconId) != null) {
                                statusBar.removeWidget(kpmiconId);
                            }
                            if (statusBar.getWidget(timeiconId) != null) {
                                statusBar.removeWidget(timeiconId);
                            }

                            String kpmIconVal = kpmIcon;
                            if (!showStatusText && kpmIconVal == null) {
                                kpmIconVal = "clock.png";
                            }

                            if (kpmIconVal != null) {
                                SoftwareCoStatusBarKpmIconWidget kpmIconWidget = buildStatusBarIconWidget(
                                        kpmIconVal, tooltip, kpmiconId);
                                statusBar.addWidget(kpmIconWidget, kpmiconId);
                                statusBar.updateWidget(kpmiconId);
                            }

                            if (showStatusText) {
                                SoftwareCoStatusBarKpmTextWidget kpmWidget = buildStatusBarTextWidget(
                                        kpmMsgVal, tooltip, kpmmsgId);
                                statusBar.addWidget(kpmWidget, kpmmsgId);
                                statusBar.updateWidget(kpmmsgId);
                            }

                            if (showStatusText && timeIcon != null) {
                                SoftwareCoStatusBarKpmIconWidget timeIconWidget = buildStatusBarIconWidget(
                                        timeIcon, tooltip, timeiconId);
                                statusBar.addWidget(timeIconWidget, timeiconId);
                                statusBar.updateWidget(timeiconId);
                            }

                            if (showStatusText && timeMsg != null) {
                                SoftwareCoStatusBarKpmTextWidget timeWidget = buildStatusBarTextWidget(
                                        timeMsg, tooltip, timemsgId);
                                statusBar.addWidget(timeWidget, timemsgId);
                                statusBar.updateWidget(timemsgId);
                            }
                        }
                        else if (statusBar != null && pluginName.equals("Music Time")) {
                            String headphoneiconId = SoftwareCoStatusBarKpmIconWidget.KPM_ICON_ID + "_headphoneicon";
                            String likeiconId = SoftwareCoStatusBarKpmIconWidget.KPM_ICON_ID + "_likeicon";
                            String unlikeiconId = SoftwareCoStatusBarKpmIconWidget.KPM_ICON_ID + "_unlikeicon";
                            String preiconId = SoftwareCoStatusBarKpmIconWidget.KPM_ICON_ID + "_preicon";
                            String stopiconId = SoftwareCoStatusBarKpmIconWidget.KPM_ICON_ID + "_stopicon";
                            String pauseiconId = SoftwareCoStatusBarKpmIconWidget.KPM_ICON_ID + "_pauseicon";
                            String playiconId = SoftwareCoStatusBarKpmIconWidget.KPM_ICON_ID + "_playicon";
                            String nexticonId = SoftwareCoStatusBarKpmIconWidget.KPM_ICON_ID + "_nexticon";
                            String songtrackId = SoftwareCoStatusBarKpmTextWidget.KPM_TEXT_ID + "_songtrack";
                            String connectspotifyId = SoftwareCoStatusBarKpmTextWidget.KPM_TEXT_ID + "_connectspotify";

                            if (statusBar.getWidget(headphoneiconId) != null) {
                                statusBar.removeWidget(headphoneiconId);
                            }
                            if (statusBar.getWidget(likeiconId) != null) {
                                statusBar.removeWidget(likeiconId);
                            }
                            if (statusBar.getWidget(unlikeiconId) != null) {
                                statusBar.removeWidget(unlikeiconId);
                            }
                            if (statusBar.getWidget(preiconId) != null) {
                                statusBar.removeWidget(preiconId);
                            }
                            if (statusBar.getWidget(stopiconId) != null) {
                                statusBar.removeWidget(stopiconId);
                            }
                            if (statusBar.getWidget(pauseiconId) != null) {
                                statusBar.removeWidget(pauseiconId);
                            }
                            if (statusBar.getWidget(playiconId) != null) {
                                statusBar.removeWidget(playiconId);
                            }
                            if (statusBar.getWidget(nexticonId) != null) {
                                statusBar.removeWidget(nexticonId);
                            }
                            if (statusBar.getWidget(songtrackId) != null) {
                                statusBar.removeWidget(songtrackId);
                            }
                            if (statusBar.getWidget(connectspotifyId) != null) {
                                statusBar.removeWidget(connectspotifyId);
                            }

                            if(tooltip.equals("Connect Spotify")) {
                                String headphoneIconVal = kpmIcon;
                                final String headphoneMsgVal = kpmMsg != null ? kpmMsg : pluginName;
                                if (headphoneIconVal != null) {
                                    SoftwareCoStatusBarKpmIconWidget headphoneIconWidget = buildStatusBarIconWidget(
                                            headphoneIconVal, tooltip, headphoneiconId);
                                    statusBar.addWidget(headphoneIconWidget, headphoneiconId);
                                    statusBar.updateWidget(headphoneiconId);


                                    SoftwareCoStatusBarKpmTextWidget kpmWidget = buildStatusBarTextWidget(
                                            headphoneMsgVal, tooltip, connectspotifyId);
                                    statusBar.addWidget(kpmWidget, connectspotifyId);
                                    statusBar.updateWidget(connectspotifyId);

                                }
                            } else {
                                String headphoneIconVal = kpmIcon;
                                String likeIcon = "like.png";
                                String unlikeIcon = "unlike.png";
                                String preIcon = "previous.png";
                                String stopIcon = "stop.png";
                                String pauseIcon = "pause.png";
                                String playIcon = "play.png";
                                String nextIcon = "next.png";
                                final String connectPremiumMsg = kpmMsg != null ? kpmMsg : pluginName;
                                final String musicMsgVal = currentTrackName != null ? currentTrackName : "Current Track";
                                if (headphoneIconVal != null) {
                                    if (connectPremiumMsg.equals("Connect Premium")) {
                                        SoftwareCoStatusBarKpmTextWidget kpmWidget = buildStatusBarTextWidget(
                                                connectPremiumMsg, connectPremiumMsg, connectspotifyId);
                                        statusBar.addWidget(kpmWidget, connectspotifyId);
                                        statusBar.updateWidget(connectspotifyId);
                                    }

                                    if(currentTrackId != null) {
                                        SoftwareCoStatusBarKpmIconWidget unlikeIconWidget = buildStatusBarIconWidget(
                                                unlikeIcon, "like", unlikeiconId);
                                        statusBar.addWidget(unlikeIconWidget, unlikeiconId);
                                        statusBar.updateWidget(unlikeiconId);

                                        SoftwareCoStatusBarKpmIconWidget preIconWidget = buildStatusBarIconWidget(
                                                preIcon, "previous", preiconId);
                                        statusBar.addWidget(preIconWidget, preiconId);
                                        statusBar.updateWidget(preiconId);

//                                    SoftwareCoStatusBarKpmIconWidget stopIconWidget = buildStatusBarIconWidget(
//                                            stopIcon, "stop", stopiconId);
//                                    statusBar.addWidget(stopIconWidget, stopiconId);
//                                    statusBar.updateWidget(stopiconId);

                                        if (!defaultbtn.equals("play")) {
                                            SoftwareCoStatusBarKpmIconWidget pauseIconWidget = buildStatusBarIconWidget(
                                                    pauseIcon, "pause", pauseiconId);
                                            statusBar.addWidget(pauseIconWidget, pauseiconId);
                                            statusBar.updateWidget(pauseiconId);
                                        } else {
                                            SoftwareCoStatusBarKpmIconWidget playIconWidget = buildStatusBarIconWidget(
                                                    playIcon, "play", playiconId);
                                            statusBar.addWidget(playIconWidget, playiconId);
                                            statusBar.updateWidget(playiconId);
                                        }

                                        SoftwareCoStatusBarKpmIconWidget nextIconWidget = buildStatusBarIconWidget(
                                                nextIcon, "next", nexticonId);
                                        statusBar.addWidget(nextIconWidget, nexticonId);
                                        statusBar.updateWidget(nexticonId);
                                    }


                                    SoftwareCoStatusBarKpmTextWidget kpmWidget = buildStatusBarTextWidget(
                                            musicMsgVal, tooltip, songtrackId);
                                    statusBar.addWidget(kpmWidget, songtrackId);
                                    statusBar.updateWidget(songtrackId);

                                }
                            }
                        }
                    } catch(Exception e){
                        //
                    }
                }
            }
        });
    }

    public static SoftwareCoStatusBarKpmTextWidget buildStatusBarTextWidget(String msg, String tooltip, String id) {
        SoftwareCoStatusBarKpmTextWidget textWidget =
                new SoftwareCoStatusBarKpmTextWidget(id);
        textWidget.setText(msg);
        textWidget.setTooltip(tooltip);
        return textWidget;
    }

    public static SoftwareCoStatusBarKpmIconWidget buildStatusBarIconWidget(String iconName, String tooltip, String id) {
        Icon icon = IconLoader.findIcon("/com/softwareco/intellij/plugin/assets/" + iconName);

        SoftwareCoStatusBarKpmIconWidget iconWidget =
                new SoftwareCoStatusBarKpmIconWidget(id);
        iconWidget.setIcon(icon);
        iconWidget.setTooltip(tooltip);
        return iconWidget;
    }

    public static String humanizeMinutes(long minutes) {
        String str = "";
        if (minutes == 60) {
            str = "1 hr";
        } else if (minutes > 60) {
            float hours = (float)minutes / 60;
            try {
                if (hours % 1 == 0) {
                    // don't return a number with 2 decimal place precision
                    str = String.format("%.0f", hours) + " hrs";
                } else {
                    // hours = Math.round(hours * 10) / 10;
                    str = String.format("%.1f", hours) + " hrs";
                }
            } catch (Exception e) {
                str = String.format("%s hrs", String.valueOf(Math.round(hours)));
            }
        } else if (minutes == 1) {
            str = "1 min";
        } else {
            str = minutes + " min";
        }
        return str;
    }

    protected static boolean isItunesRunning() {
        // get running of application "iTunes"
        String[] args = { "osascript", "-e", "get running of application \"iTunes\"" };
        String result = runCommand(args, null);
        return (result != null) ? Boolean.valueOf(result) : false;
    }

    protected static String itunesTrackScript = "tell application \"iTunes\"\n" +
            "set track_artist to artist of current track\n" +
            "set track_album to album of current track\n" +
            "set track_name to name of current track\n" +
            "set track_duration to duration of current track\n" +
            "set track_id to id of current track\n" +
            "set track_genre to genre of current track\n" +
            "set track_state to player state\n" +
            "set json to \"type='itunes';album='\" & track_album & \"';genre='\" & track_genre & \"';artist='\" & track_artist & \"';id='\" & track_id & \"';name='\" & track_name & \"';state='\" & track_state & \"';duration='\" & track_duration & \"'\"\n" +
            "end tell\n" +
            "return json\n";

    protected static String getItunesTrack() {
        String[] args = { "osascript", "-e", itunesTrackScript };
        return runCommand(args, null);
    }

    protected static boolean isSpotifyRunning() {
        String[] args = { "osascript", "-e", "get running of application \"Spotify\"" };
        String result = runCommand(args, null);
        return (result != null) ? Boolean.valueOf(result) : false;
    }

    protected static boolean isSpotifyInstalled() {
        String[] args = { "osascript", "-e", "exists application \"Spotify\"" };
        String result = runCommand(args, null);
        return (result != null) ? Boolean.valueOf(result) : false;
    }

    protected static String spotifyTrackScript = "tell application \"Spotify\"\n" +
            "set track_artist to artist of current track\n" +
            "set track_album to album of current track\n" +
            "set track_name to name of current track\n" +
            "set track_duration to duration of current track\n" +
            "set track_id to id of current track\n" +
            "set track_state to player state\n" +
            "set json to \"type='spotify';album='\" & track_album & \"';genre='';artist='\" & track_artist & \"';id='\" & track_id & \"';name='\" & track_name & \"';state='\" & track_state & \"';duration='\" & track_duration & \"'\"\n" +
            "end tell\n" +
            "return json\n";

    protected static String getSpotifyTrack() {
        String[] args = { "osascript", "-e", spotifyTrackScript };
        return runCommand(args, null);
    }

    protected static String startPlayer(String playerName) {
        String[] args = { "open", "-a", playerName + ".app" };
        return runCommand(args, null);
    }

    protected static String playPlayer(String playerName) {
        String[] args = { "osascript", "-e", "tell application \""+ playerName + "\" to play" };
        return runCommand(args, null);
    }

    protected static String pausePlayer(String playerName) {
        String[] args = { "osascript", "-e", "tell application \""+ playerName + "\" to pause" };
        return runCommand(args, null);
    }

    protected static String previousTrack(String playerName) {
        String[] args = { "osascript", "-e", "tell application \""+ playerName + "\" to play (previous track)" };
        return runCommand(args, null);
    }

    protected static String nextTrack(String playerName) {
        String[] args = { "osascript", "-e", "tell application \""+ playerName + "\" to play (next track)" };
        return runCommand(args, null);
    }

    protected static String stopPlayer(String playerName) {
        // `ps -ef | grep "${appName}" | grep -v grep | awk '{print $2}' | xargs kill`;
        String[] args = { "ps", "-ef", "|", "grep", "\"" + playerName + ".app\"", "|", "grep", "-v", "grep", "|", "awk", "'{print $2}'", "|", "xargs", "kill" };
        return runCommand(args, null);
    }

    public static JsonObject getCurrentMusicTrack() {
        JsonObject jsonObj = new JsonObject();
        if (!SoftwareCoUtils.isMac()) {
            return jsonObj;
        }

        boolean spotifyRunning = isSpotifyRunning();
        boolean itunesRunning = isItunesRunning();

        String trackInfo = "";
        // Vintage Trouble, My Whole World Stopped Without You, spotify:track:7awBL5Pu8LD6Fl7iTrJotx, My Whole World Stopped Without You, 244080
        if (spotifyRunning) {
            trackInfo = getSpotifyTrack();
        } else if (itunesRunning) {
            trackInfo = getItunesTrack();
        }

        if (trackInfo != null && !trackInfo.equals("")) {
            // trim and replace things
            trackInfo = trackInfo.trim();
            trackInfo = trackInfo.replace("\"", "");
            trackInfo = trackInfo.replace("'", "");
            String[] paramParts = trackInfo.split(";");
            for (String paramPart : paramParts) {
                paramPart = paramPart.trim();
                String[] params = paramPart.split("=");
                if (params != null && params.length == 2) {
                    jsonObj.addProperty(params[0], params[1]);
                }
            }

        }
        return jsonObj;
    }

    /**
     * Execute the args
     * @param args
     * @return
     */
    public static String runCommand(String[] args, String dir) {
        // use process builder as it allows to run the command from a specified dir
        ProcessBuilder builder = new ProcessBuilder();

        try {
            builder.command(args);
            if (dir != null) {
                // change to the directory to run the command
                builder.directory(new File(dir));
            }
            Process process = builder.start();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            InputStream is = process.getInputStream();
            copyLarge(is, baos, new byte[4096]);
            return baos.toString().trim();

        } catch (Exception e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private static long copyLarge(InputStream input, OutputStream output, byte[] buffer) throws IOException {

        long count = 0;
        int n;
        while (EOF != (n = input.read(buffer))) {
            output.write(buffer, 0, n);
            count += n;
        }
        return count;
    }

    // get the git resource config information
    public static JsonObject getResourceInfo(String projectDir) {
        if (fetchingResourceInfo) {
            return null;
        }

        fetchingResourceInfo = true;
        lastResourceInfo = new JsonObject();

        // is the project dir avail?
        if (projectDir != null && !projectDir.equals("")) {
            try {
                String[] branchCmd = { "git", "symbolic-ref", "--short", "HEAD" };
                String branch = runCommand(branchCmd, projectDir);

                String[] identifierCmd = { "git", "config", "--get", "remote.origin.url" };
                String identifier = runCommand(identifierCmd, projectDir);

                String[] emailCmd = { "git", "config", "user.email" };
                String email = runCommand(emailCmd, projectDir);

                String[] tagCmd = { "git", "describe", "--all" };
                String tag = runCommand(tagCmd, projectDir);

                if (StringUtils.isNotBlank(branch) && StringUtils.isNotBlank(identifier)) {
                    lastResourceInfo.addProperty("identifier", identifier);
                    lastResourceInfo.addProperty("branch", branch);
                    lastResourceInfo.addProperty("email", email);
                    lastResourceInfo.addProperty("tag", tag);
                }
            } catch (Exception e) {
                //
            }
        }

        fetchingResourceInfo = false;

        return lastResourceInfo;
    }

    public static void launchSoftwareTopForty() {
        BrowserUtil.browse("http://api.software.com/music/top40");
    }

    public static void submitGitIssue() {
        BrowserUtil.browse("https://github.com/swdotcom/swdc-intellij/issues");
    }

    public static void submitFeedback() {
        BrowserUtil.browse("mailto:cody@software.com");
    }

    public static void fetchCodeTimeMetricsDashboard(JsonObject summary) {
        String summaryInfoFile = SoftwareCoSessionManager.getSummaryInfoFile(true);
        String dashboardFile = SoftwareCoSessionManager.getCodeTimeDashboardFile();

        Calendar cal = Calendar.getInstance();
        int dayOfMonth = cal.get(Calendar.DAY_OF_MONTH);
        Writer writer = null;

        if (lastDayOfMonth == 0 || lastDayOfMonth != dayOfMonth) {
            lastDayOfMonth = dayOfMonth;
            String api = "/dashboard?linux=" + SoftwareCoUtils.isLinux() + "&showToday=false";
            String dashboardSummary = SoftwareCoUtils.makeApiCall(api, HttpGet.METHOD_NAME, null).getJsonStr();
            if (dashboardSummary == null || dashboardSummary.trim().isEmpty()) {
                dashboardSummary = SERVICE_NOT_AVAIL;
            }

            // write the summary content
            try {
                writer = new BufferedWriter(new OutputStreamWriter(
                        new FileOutputStream(summaryInfoFile), StandardCharsets.UTF_8));
                writer.write(dashboardSummary);
            } catch (IOException ex) {
                // Report
            } finally {
                try {writer.close();} catch (Exception ex) {/*ignore*/}
            }
        }

        // concat summary info with the dashboard file
        String dashboardContent = "";
        SimpleDateFormat formatDayTime = new SimpleDateFormat("EEE, MMM d h:mma");
        SimpleDateFormat formatDay = new SimpleDateFormat("EEE, MMM d");
        String lastUpdatedStr = formatDayTime.format(new Date());
        dashboardContent += "Code Time          (Last updated on " + lastUpdatedStr + ")";
        dashboardContent += "\n\n";
        String todayStr = formatDay.format(new Date());
        dashboardContent += getSectionHeader("Today (" + todayStr + ")");


        if (summary != null) {
            long currentDayMinutes = 0;
            if (summary.has("currentDayMinutes")) {
                currentDayMinutes = summary.get("currentDayMinutes").getAsLong();
            }
            long averageDailyMinutes = 0;
            if (summary.has("averageDailyMinutes")) {
                averageDailyMinutes = summary.get("averageDailyMinutes").getAsLong();
            }

            String currentDayTimeStr = SoftwareCoUtils.humanizeMinutes(currentDayMinutes);
            String averageDailyMinutesTimeStr = SoftwareCoUtils.humanizeMinutes(averageDailyMinutes);

            dashboardContent += getDashboardRow("Hours coded today", currentDayTimeStr);
            dashboardContent += getDashboardRow("90-day avg", averageDailyMinutesTimeStr);
            dashboardContent += "\n";
        }

        // append the summary content
        String summaryInfoContent = SoftwareCoOfflineManager.getInstance().getSessionSummaryInfoFileContent();
        if (summaryInfoContent != null) {
            dashboardContent += summaryInfoContent;
        }

        // write the dashboard content to the dashboard file
        SoftwareCoOfflineManager.getInstance().saveFileContent(dashboardContent, dashboardFile);

    }

    public static void launchCodeTimeMetricsDashboard() {
        if (!SoftwareCoSessionManager.isServerOnline()) {
            SoftwareCoUtils.showOfflinePrompt(false);
        }
        Project p = getOpenProject();
        if (p == null) {
            return;
        }

        SoftwareCoSessionManager.getInstance().fetchDailyKpmSessionInfo();
        JsonObject sessionSummary = SoftwareCoOfflineManager.getInstance().getSessionSummaryFileAsJson();
        fetchCodeTimeMetricsDashboard(sessionSummary);

        String codeTimeFile = SoftwareCoSessionManager.getCodeTimeDashboardFile();
        File f = new File(codeTimeFile);

        VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(f);
        OpenFileDescriptor descriptor = new OpenFileDescriptor(p, vFile);
        FileEditorManager.getInstance(p).openTextEditor(descriptor, true);
    }

    public static void disConnectSpotify() {
        boolean serverIsOnline = SoftwareCoSessionManager.isServerOnline();
        if (!serverIsOnline) {
            SoftwareCoUtils.showOfflinePrompt(false);
        }

        String api = "/auth/spotify/disconnect";
        String jwt = SoftwareCoSessionManager.getItem("jwt");
        SoftwareResponse resp = SoftwareCoUtils.makeApiCall(api, HttpPut.METHOD_NAME, null, jwt);
        if (resp.isOk()) {
            boolean exist = false;
            JsonObject obj = resp.getJsonObj();
            if (obj != null && obj.has("auths")) {
                for(JsonElement array : obj.get("auths").getAsJsonArray()) {
                    if(array.getAsJsonObject().get("type").getAsString().equals("spotify")) {
                        exist = true;
                    }
                }
                if(!exist) {
                    spotifyCacheState = exist;
                    SoftwareCoSessionManager.setItem("spotify_access_token", null);
                    SoftwareCoSessionManager.setItem("spotify_refresh_token", null);
                }
            } else {
                LOG.log(Level.INFO, "Music Time: Unable to Disconnect Spotify null response");
            }

            if(!spotifyCacheState) {
                resetSpotify();
                String headPhoneIcon = "headphone.png";
                SoftwareCoUtils.setStatusLineMessage(headPhoneIcon, "Connect Spotify", "Connect Spotify");
            } else {
                String headPhoneIcon = "headphone.png";
                SoftwareCoUtils.setStatusLineMessage(headPhoneIcon, "Current Track", "Current Track");
            }
        }
    }

    public static void connectSpotify() {
        boolean serverIsOnline = SoftwareCoSessionManager.isServerOnline();
        if (!serverIsOnline) {
            SoftwareCoUtils.showOfflinePrompt(false);
        }
        spotifyUserId = null;
        spotifyDeviceIds.clear();
        currentDeviceId = null;
        currentDeviceName = null;

        JsonObject userObj = getClientInfo(serverIsOnline);
        if(userObj != null) {
            LOG.log(Level.INFO, "Music Time: clientId: " + userObj.get("clientId") + " & clientSecret: " + userObj.get("clientSecret"));
            CLIENT_ID = userObj.get("clientId").getAsString();
            CLIENT_SECRET = userObj.get("clientSecret").getAsString();
        }

        // Authenticate Spotify
        SoftwareCoUtils.authSpotify(serverIsOnline);

        // Periodically check that the user has connected
        SoftwareCoUtils.lazilyFetchSpotifyStatus(20);
    }

    protected static void refreshAccessToken() {
        if(REFRESH_TOKEN == null)
            REFRESH_TOKEN = SoftwareCoSessionManager.getItem("spotify_refresh_token");

        if(CLIENT_ID == null && CLIENT_SECRET == null) {
            JsonObject userObj = getClientInfo(true);
            if(userObj != null) {
                CLIENT_ID = userObj.get("clientId").getAsString();
                CLIENT_SECRET = userObj.get("clientSecret").getAsString();
            }
        }

        String api = "/api/token?grant_type=refresh_token&refresh_token=" + REFRESH_TOKEN;
        String authPayload = CLIENT_ID + ":" + CLIENT_SECRET;
        byte[] bytesEncoded = Base64.encodeBase64(authPayload.getBytes());
        String encodedAuthPayload = "Basic " + new String(bytesEncoded);
        LOG.log(Level.INFO, "Music Time: Encoded Payload: " + encodedAuthPayload);
        SoftwareResponse resp = SoftwareCoUtils.makeApiCall(api, HttpPost.METHOD_NAME, null, encodedAuthPayload);
        if (resp.isOk()) {
            JsonObject obj = resp.getJsonObj();
            ACCESS_TOKEN = obj.get("access_token").getAsString();
            SoftwareCoSessionManager.setItem("spotify_access_token", ACCESS_TOKEN);
            LOG.log(Level.INFO, "Music Time: New Access Token: " + ACCESS_TOKEN);
        }
    }

    protected static void lazilyFetchSpotifyStatus(int retryCount) {
        boolean serverIsOnline = SoftwareCoSessionManager.isServerOnline();
        if (!serverIsOnline) {
            SoftwareCoUtils.showOfflinePrompt(false);
        }
        spotifyCacheState = isSpotifyConncted(serverIsOnline);

        if (!spotifyCacheState && retryCount > 0) {
            final int newRetryCount = retryCount - 1;
            new Thread(() -> {
                try {
                    Thread.sleep(5000);
                    lazilyFetchSpotifyStatus(newRetryCount);
                }
                catch (Exception e){
                    System.err.println(e);
                }
            }).start();
        } else if(spotifyCacheState && spotifyStatus.equals("Not Connected")) {
            spotifyStatus = "Connected";
            JsonObject obj = getUserProfile();
            if (obj != null)
                userStatus = obj.get("product").getAsString();
            launchPlayer();
            lazyUpdatePlayer();
        }
    }

    public static void lazyUpdatePlayer() {
        if(spotifyCacheState) {
            // Update player controls for every 5 second
            new Thread(() -> {
                try {
                    Thread.sleep(5000);
                    lazyUpdatePlayer();
                }
                catch (Exception e){
                    System.err.println(e);
                }
            }).start();
            updatePlayerControles();
        } else {
            String headPhoneIcon = "headphone.png";
            SoftwareCoUtils.setStatusLineMessage(headPhoneIcon, "Connect Spotify", "Connect Spotify");
        }
    }

    public static void updatePlayerControles() {
        if(spotifyCacheState) {
            if(currentDeviceId == null || userStatus == null) {
                initialSetup();
            } else if(userStatus.equals("premium")){
                getSpotifyWebCurrentTrack();  // get current track to update status bar
            } else {
                getSpotifyDesktopCurrentTrack();  // get current track to update status bar
            }

            if(userStatus != null && !userStatus.equals("premium")) {
                String headPhoneIcon = "headphone.png";
                SoftwareCoUtils.setStatusLineMessage(headPhoneIcon, "Connect Premium", "Current Track");
            } else {
                String headPhoneIcon = "headphone.png";
                SoftwareCoUtils.setStatusLineMessage(headPhoneIcon, "Current Track", "Current Track");
            }
        } else {
            String headPhoneIcon = "headphone.png";
            SoftwareCoUtils.setStatusLineMessage(headPhoneIcon, "Connect Spotify", "Connect Spotify");
        }
    }

    public static void initialSetup() {
        if(userStatus == null) {
            JsonObject obj = getUserProfile();
            if (obj != null)
                userStatus = obj.get("product").getAsString();
        }

        if(currentDeviceId == null) {
            getSpotifyDevices();
            if(currentDeviceId == null && spotifyDeviceIds.size() > 0) {
                if(spotifyDeviceIds.size() == 1)
                    currentDeviceId = spotifyDeviceIds.get(0);
                playSpotifyPlaylist(); // play current playlist
            } else if(currentDeviceId != null) {
                playSpotifyPlaylist(); // play current playlist
            }
        }
    }

    public static void launchPlayer() {
        if(currentTrackId == null)
            getSpotifyWebRecentTrack();

        if(currentTrackId == null) {
            getUserPlaylists();
            getPlaylistTracks();
            getTrackById();
            currentTrackName = null;
        }

        if(userStatus != null && userStatus.equals("premium")) {
//            if (currentPlaylistId != null) {
//                BrowserUtil.browse("https://open.spotify.com/playlist/" + currentPlaylistId);
//            }
            if (currentTrackId != null) {
                BrowserUtil.browse("https://open.spotify.com/track/" + currentTrackId);
            } else {
                BrowserUtil.browse("https://open.spotify.com/browse");
            }
        } else if(userStatus != null) {
            startPlayer("spotify");
            new Thread(() -> {
                try {
                    Thread.sleep(1000);
                    if(!isSpotifyRunning())
                        showMsgPrompt("Spotify Desktop Player is required for Non-Premium account, Please Install Spotify");
                }
                catch (Exception e){
                    System.err.println(e);
                }
            }).start();
        }
    }

    // Music Time Api's
    public static JsonObject getUserProfile() {
        boolean serverIsOnline = SoftwareCoSessionManager.isServerOnline();
        if (!serverIsOnline) {
            SoftwareCoUtils.showOfflinePrompt(false);
        }

        String api = "/v1/me";
        String accessToken = "Bearer " + SoftwareCoSessionManager.getItem("spotify_access_token");
        SoftwareResponse resp = SoftwareCoUtils.makeApiCall(api, HttpGet.METHOD_NAME, null, accessToken);
        JsonObject obj = resp.getJsonObj();
        if (resp.isOk()) {
            //JsonObject obj = resp.getJsonObj();
            spotifyUserId = obj.get("id").getAsString();
            return resp.getJsonObj();
        } else if (obj != null && obj.has("error")) {
            JsonObject error = obj.get("error").getAsJsonObject();
            String message = error.get("message").getAsString();
            if(message.equals("The access token expired")) {
                refreshAccessToken();
            }
        }
        return null;
    }

    public static JsonObject getUserPlaylists() {
        boolean serverIsOnline = SoftwareCoSessionManager.isServerOnline();
        if (!serverIsOnline) {
            SoftwareCoUtils.showOfflinePrompt(false);
        }

        if(spotifyUserId == null) {
            getUserProfile();
        }
        LOG.log(Level.INFO, "Music Time: Spotify User ID: " + spotifyUserId);

        String api = "/v1/users/" + spotifyUserId + "/playlists";
        String accessToken = "Bearer " + SoftwareCoSessionManager.getItem("spotify_access_token");
        SoftwareResponse resp = SoftwareCoUtils.makeApiCall(api, HttpGet.METHOD_NAME, null, accessToken);
        if (resp.isOk()) {
            JsonObject obj = resp.getJsonObj();
            if (obj != null && obj.has("items")) {
                playlistids.clear();
                for(JsonElement array : obj.get("items").getAsJsonArray()) {
                    if(array.getAsJsonObject().get("type").getAsString().equals("playlist")) {
                        playlistids.add(array.getAsJsonObject().get("id").getAsString());
                    }
                }
            } else {
                LOG.log(Level.INFO, "Music Time: Unable to get Playlists, null response");
            }
            return resp.getJsonObj();
        }
        return null;
    }

    public static JsonObject getSpotifyPlaylist() {
        boolean serverIsOnline = SoftwareCoSessionManager.isServerOnline();
        if (!serverIsOnline) {
            SoftwareCoUtils.showOfflinePrompt(false);
        }

        if(currentPlaylistId == null) {
            currentPlaylistId = playlistids.get(0);
        }

        String api = "/v1/playlists/" + currentPlaylistId;
        String accessToken = "Bearer " + SoftwareCoSessionManager.getItem("spotify_access_token");
        SoftwareResponse resp = SoftwareCoUtils.makeApiCall(api, HttpGet.METHOD_NAME, null, accessToken);
        if (resp.isOk()) {
            JsonObject obj = resp.getJsonObj();
            if (obj != null && obj.has("tracks")) {
                JsonObject tracks = obj.get("tracks").getAsJsonObject();
                playlistTracks.clear();
                for(JsonElement array : tracks.get("items").getAsJsonArray()) {
                    JsonObject track = array.getAsJsonObject().get("track").getAsJsonObject();
                    playlistTracks.add(track.get("id").getAsString());
                }
            } else {
                LOG.log(Level.INFO, "Music Time: Unable to get Playlist Tracks, null response");
            }
            return resp.getJsonObj();
        }
        return null;
    }

    public static JsonObject getPlaylistTracks() {
        boolean serverIsOnline = SoftwareCoSessionManager.isServerOnline();
        if (!serverIsOnline) {
            SoftwareCoUtils.showOfflinePrompt(false);
        }

        if(currentPlaylistId == null) {
            currentPlaylistId = playlistids.get(0);
        }
        LOG.log(Level.INFO, "Music Time: Playlist ID: " + currentPlaylistId);

        String api = "/v1/playlists/" + currentPlaylistId + "/tracks";
        String accessToken = "Bearer " + SoftwareCoSessionManager.getItem("spotify_access_token");
        SoftwareResponse resp = SoftwareCoUtils.makeApiCall(api, HttpGet.METHOD_NAME, null, accessToken);
        if (resp.isOk()) {
            JsonObject tracks = resp.getJsonObj();
            if (tracks != null && tracks.has("items")) {
                playlistTracks.clear();
                for(JsonElement array : tracks.get("items").getAsJsonArray()) {
                    JsonObject track = array.getAsJsonObject().get("track").getAsJsonObject();
                    playlistTracks.add(track.get("id").getAsString());
                }
            } else {
                LOG.log(Level.INFO, "Music Time: Unable to get Playlist Tracks, null response");
            }
            return resp.getJsonObj();
        }
        return null;
    }

    public static JsonObject getTrackById() {
        boolean serverIsOnline = SoftwareCoSessionManager.isServerOnline();
        if (!serverIsOnline) {
            SoftwareCoUtils.showOfflinePrompt(false);
        }

        if(currentTrackId == null) {
            currentTrackId = playlistTracks.get(0);
        }
        LOG.log(Level.INFO, "Music Time: Track ID: " + currentTrackId);

        String api = "/v1/tracks/" + currentTrackId;
        String accessToken = "Bearer " + SoftwareCoSessionManager.getItem("spotify_access_token");
        SoftwareResponse resp = SoftwareCoUtils.makeApiCall(api, HttpGet.METHOD_NAME, null, accessToken);
        if (resp.isOk()) {
            JsonObject tracks = resp.getJsonObj();
            if (tracks != null && tracks.has("name")) {
                currentTrackName = tracks.get("name").getAsString();
            } else {
                LOG.log(Level.INFO, "Music Time: Unable to get Playlist Tracks, null response");
            }
            return resp.getJsonObj();
        }
        return null;
    }

    public static JsonObject getSpotifyWebRecentTrack() {
        boolean serverIsOnline = SoftwareCoSessionManager.isServerOnline();
        if (!serverIsOnline) {
            SoftwareCoUtils.showOfflinePrompt(false);
        }

        String api = "/v1/me/player/recently-played?limit=1";
        String accessToken = "Bearer " + SoftwareCoSessionManager.getItem("spotify_access_token");
        SoftwareResponse resp = SoftwareCoUtils.makeApiCall(api, HttpGet.METHOD_NAME, null, accessToken);
        if (resp.isOk()) {
            JsonObject tracks = resp.getJsonObj();
            if (tracks != null && tracks.has("items")) {
                for(JsonElement array : tracks.get("items").getAsJsonArray()) {
                    JsonObject track = array.getAsJsonObject().get("track").getAsJsonObject();
                    currentTrackId = track.get("id").getAsString();
//                    if(!array.getAsJsonObject().get("context").isJsonNull()) {
//                        JsonObject context = array.getAsJsonObject().get("context").getAsJsonObject();
//                        String[] uri = context.get("uri").getAsString().split(":");
//                        currentPlaylistId = uri[uri.length - 1];
//                    }
                }

            }
            return resp.getJsonObj();
        } else {
            JsonObject tracks = resp.getJsonObj();
            if (tracks != null && tracks.has("error")) {
                JsonObject error = tracks.get("error").getAsJsonObject();
                String message = error.get("message").getAsString();
                if(message.equals("The access token expired")) {
                    refreshAccessToken();
                }
            }
        }
        return null;
    }

    public static JsonObject getSpotifyWebCurrentTrack() {
        boolean serverIsOnline = SoftwareCoSessionManager.isServerOnline();
        if (!serverIsOnline) {
            SoftwareCoUtils.showOfflinePrompt(false);
        }

        String api = "/v1/me/player/currently-playing";
        String accessToken = "Bearer " + SoftwareCoSessionManager.getItem("spotify_access_token");
        SoftwareResponse resp = SoftwareCoUtils.makeApiCall(api, HttpGet.METHOD_NAME, null, accessToken);
        if (resp.isOk() && !resp.getJsonObj().isJsonNull()) {
            JsonObject tracks = resp.getJsonObj();
            if (tracks != null && tracks.has("item")) {
                JsonObject track = tracks.get("item").getAsJsonObject();
                currentTrackId = track.get("id").getAsString();
                currentTrackName = track.get("name").getAsString();
                if(!tracks.get("context").isJsonNull()) {
                    JsonObject context = tracks.get("context").getAsJsonObject();
                    String[] uri = context.get("uri").getAsString().split(":");
                    currentPlaylistId = uri[uri.length - 1];
                }
                if(tracks.get("is_playing").getAsBoolean()) {
                    defaultbtn = "pause";
                } else {
                    defaultbtn = "play";
                }
            } else {
                defaultbtn = "play";
                getSpotifyDevices();
            }
            return resp.getJsonObj();
        } else if(!resp.getJsonObj().isJsonNull()) {
            JsonObject tracks = resp.getJsonObj();
            if (tracks != null && tracks.has("error")) {
                JsonObject error = tracks.get("error").getAsJsonObject();
                String message = error.get("message").getAsString();
                if(message.equals("The access token expired")) {
                    refreshAccessToken();
                }
            }
        }
        return null;
    }

    public static JsonObject getSpotifyDesktopCurrentTrack() {
        try {
            JsonObject obj = getCurrentMusicTrack();
            if(!obj.isJsonNull()) {
                currentTrackId = obj.get("id").getAsString();
                currentTrackName = obj.get("name").getAsString();
                if(obj.get("state").getAsString().equals("playing"))
                    defaultbtn = "pause";
                else
                    defaultbtn = "play";
                return obj;
            }
        }catch (Exception e){
            LOG.warning("Music Time: Error trying to read and json parse the current track, error: " + e.getMessage());
        }
        return null;
    }

    public static JsonObject getSpotifyDevices() {
        boolean serverIsOnline = SoftwareCoSessionManager.isServerOnline();
        if (!serverIsOnline) {
            SoftwareCoUtils.showOfflinePrompt(false);
        }

        String api = "/v1/me/player/devices";
        String accessToken = "Bearer " + SoftwareCoSessionManager.getItem("spotify_access_token");
        SoftwareResponse resp = SoftwareCoUtils.makeApiCall(api, HttpGet.METHOD_NAME, null, accessToken);
        if (resp.isOk()) {
            JsonObject tracks = resp.getJsonObj();
            if (tracks != null && tracks.has("devices")) {
                spotifyDeviceIds.clear();
                for(JsonElement array : tracks.get("devices").getAsJsonArray()) {
                    JsonObject device = array.getAsJsonObject();
                    spotifyDeviceIds.add(device.get("id").getAsString());
                    if(device.get("is_active").getAsBoolean()) {
                        currentDeviceId = device.get("id").getAsString();
                        currentDeviceName = device.get("name").getAsString();
                    }
                }
            } else {
                LOG.log(Level.INFO, "Music Time: No Device Found, null response");
            }
            return resp.getJsonObj();
        }
        return null;
    }

    public static boolean playSpotifyPlaylist() {
        boolean serverIsOnline = SoftwareCoSessionManager.isServerOnline();
        if (!serverIsOnline) {
            SoftwareCoUtils.showOfflinePrompt(false);
        }

        if(currentDeviceId == null) {
            getSpotifyDevices();
        }
        LOG.log(Level.INFO, "Music Time: Device ID: " + currentDeviceId);

        JsonObject obj = new JsonObject();
        if(currentPlaylistId != null) {
            obj.addProperty("context_uri", "spotify:playlist:" + currentPlaylistId);
        }

        if(currentTrackId != null) {
            JsonObject offset = new JsonObject();
            offset.addProperty("uri", "spotify:track:" + currentTrackId);

            obj.add("offset", offset);
        }

        if(currentDeviceId != null) {
            String api = "/v1/me/player/play?device_id=" + currentDeviceId;
            String accessToken = "Bearer " + SoftwareCoSessionManager.getItem("spotify_access_token");
            SoftwareResponse resp = SoftwareCoUtils.makeApiCall(api, HttpPut.METHOD_NAME, obj.toString(), accessToken);
            if (resp.isOk()) {
                updatePlayerControles();
                return true;
            }
        }
        return false;
    }

    public static boolean playSpotifyDevices() {
        boolean serverIsOnline = SoftwareCoSessionManager.isServerOnline();
        if (!serverIsOnline) {
            SoftwareCoUtils.showOfflinePrompt(false);
        }

        if(userStatus.equals("premium")) {
            if(currentDeviceId != null) {

                LOG.log(Level.INFO, "Music Time: Device ID: " + currentDeviceId);

                String api = "/v1/me/player/play?device_id=" + currentDeviceId;
                String accessToken = "Bearer " + SoftwareCoSessionManager.getItem("spotify_access_token");
                SoftwareResponse resp = SoftwareCoUtils.makeApiCall(api, HttpPut.METHOD_NAME, null, accessToken);
                if (resp.isOk()) {
                    playerCounter = 0;
                    new Thread(() -> {
                        try {
                            Thread.sleep(1000);
                            updatePlayerControles();
                        } catch (Exception e) {
                            System.err.println(e);
                        }
                    }).start();
                    return true;
                } else if (playerCounter < 1 && resp.getCode() == 404) {
                    getSpotifyDevices();
                    if (spotifyDeviceIds.size() > 0) {
                        if (spotifyDeviceIds.size() == 1)
                            currentDeviceId = spotifyDeviceIds.get(0);
                        playerCounter++;
                        playSpotifyDevices();
                    }
                }
            } else {
                launchPlayer();
            }
        } else if(spotifyCacheState && isSpotifyRunning()) {
            playPlayer("Spotify");
            updatePlayerControles();
            return true;
        }
        return false;
    }

    public static boolean pauseSpotifyDevices() {
        boolean serverIsOnline = SoftwareCoSessionManager.isServerOnline();
        if (!serverIsOnline) {
            SoftwareCoUtils.showOfflinePrompt(false);
        }

        if(userStatus.equals("premium")) {
            if(currentDeviceId != null) {
                LOG.log(Level.INFO, "Music Time: Device ID: " + currentDeviceId);

                String api = "/v1/me/player/pause?device_id=" + currentDeviceId;
                String accessToken = "Bearer " + SoftwareCoSessionManager.getItem("spotify_access_token");
                SoftwareResponse resp = SoftwareCoUtils.makeApiCall(api, HttpPut.METHOD_NAME, null, accessToken);
                if (resp.isOk()) {
                    playerCounter = 0;
                    new Thread(() -> {
                        try {
                            Thread.sleep(1000);
                            updatePlayerControles();
                        } catch (Exception e) {
                            System.err.println(e);
                        }
                    }).start();
                    return true;
                } else if (playerCounter < 1 && resp.getCode() == 404) {
                    getSpotifyDevices();
                    if (spotifyDeviceIds.size() > 0) {
                        if (spotifyDeviceIds.size() == 1)
                            currentDeviceId = spotifyDeviceIds.get(0);
                        playerCounter++;
                        pauseSpotifyDevices();
                    }
                }
            } else {
                launchPlayer();
            }
        } else if(spotifyCacheState && isSpotifyRunning()) {
            pausePlayer("Spotify");
            updatePlayerControles();
            return true;
        }
        return false;
    }

    public static boolean previousSpotifyTrack() {
        boolean serverIsOnline = SoftwareCoSessionManager.isServerOnline();
        if (!serverIsOnline) {
            SoftwareCoUtils.showOfflinePrompt(false);
        }

        if(userStatus.equals("premium")) {
            if(currentDeviceId != null) {
                LOG.log(Level.INFO, "Music Time: Device ID: " + currentDeviceId);

                String api = "/v1/me/player/previous?device_id=" + currentDeviceId;
                String accessToken = "Bearer " + SoftwareCoSessionManager.getItem("spotify_access_token");
                SoftwareResponse resp = SoftwareCoUtils.makeApiCall(api, HttpPost.METHOD_NAME, null, accessToken);
                if (resp.isOk()) {
                    playerCounter = 0;
                    new Thread(() -> {
                        try {
                            Thread.sleep(1000);
                            updatePlayerControles();
                        } catch (Exception e) {
                            System.err.println(e);
                        }
                    }).start();
                    return true;
                } else if (playerCounter < 1 && resp.getCode() == 404) {
                    getSpotifyDevices();
                    if (spotifyDeviceIds.size() > 0) {
                        if (spotifyDeviceIds.size() == 1)
                            currentDeviceId = spotifyDeviceIds.get(0);
                        playerCounter++;
                        previousSpotifyTrack();
                    }
                }
            } else {
                launchPlayer();
            }
        } else if(spotifyCacheState && isSpotifyRunning()) {
            previousTrack("Spotify");
            updatePlayerControles();
            return true;
        }
        return false;
    }

    public static boolean nextSpotifyTrack() {
        boolean serverIsOnline = SoftwareCoSessionManager.isServerOnline();
        if (!serverIsOnline) {
            SoftwareCoUtils.showOfflinePrompt(false);
        }

        if(userStatus.equals("premium")) {
            if(currentDeviceId != null) {
                LOG.log(Level.INFO, "Music Time: Device ID: " + currentDeviceId);

                String api = "/v1/me/player/next?device_id=" + currentDeviceId;
                String accessToken = "Bearer " + SoftwareCoSessionManager.getItem("spotify_access_token");
                SoftwareResponse resp = SoftwareCoUtils.makeApiCall(api, HttpPost.METHOD_NAME, null, accessToken);
                if (resp.isOk()) {
                    playerCounter = 0;
                    new Thread(() -> {
                        try {
                            Thread.sleep(1000);
                            updatePlayerControles();
                        } catch (Exception e) {
                            System.err.println(e);
                        }
                    }).start();
                    return true;
                } else if (playerCounter < 1 && resp.getCode() == 404) {
                    getSpotifyDevices();
                    if (spotifyDeviceIds.size() > 0) {
                        if (spotifyDeviceIds.size() == 1)
                            currentDeviceId = spotifyDeviceIds.get(0);
                        playerCounter++;
                        nextSpotifyTrack();
                    }
                }
            } else {
                launchPlayer();
            }
        } else if(spotifyCacheState && isSpotifyRunning()) {
            nextTrack("Spotify");
            updatePlayerControles();
            return true;
        }
        return false;
    }

    private static String getSingleLineResult(List<String> cmd, int maxLen) {
        String result = null;
        String[] cmdArgs = Arrays.copyOf(cmd.toArray(), cmd.size(), String[].class);
        String content = SoftwareCoUtils.runCommand(cmdArgs, null);

        // for now just get the 1st one found
        if (content != null) {
            String[] contentList = content.split("\n");
            if (contentList != null && contentList.length > 0) {
                int len = (maxLen != -1) ? Math.min(maxLen, contentList.length) : contentList.length;
                for (int i = 0; i < len; i++) {
                    String line = contentList[i];
                    if (line != null && line.trim().length() > 0) {
                        result = line.trim();
                        break;
                    }
                }
            }
        }
        return result;
    }

    public static String getOsUsername() {
        String username = System.getProperty("user.name");
        if (username == null || username.trim().equals("")) {
            try {
                List<String> cmd = new ArrayList<String>();
                if (SoftwareCoUtils.isWindows()) {
                    cmd.add("cmd");
                    cmd.add("/c");
                    cmd.add("whoami");
                } else {
                    cmd.add("/bin/sh");
                    cmd.add("-c");
                    cmd.add("whoami");
                }
                username = getSingleLineResult(cmd, -1);
            } catch (Exception e) {
                //
            }
        }
        return username;
    }

    public static String getAppJwt(boolean serverIsOnline) {
        if (serverIsOnline) {
            long now = Math.round(System.currentTimeMillis() / 1000);
            String api = "/data/apptoken?token=" + now;
            SoftwareResponse resp = SoftwareCoUtils.makeApiCall(api, HttpGet.METHOD_NAME, null);
            if (resp.isOk()) {
                JsonObject obj = resp.getJsonObj();
                return obj.get("jwt").getAsString();
            }
        }
        return null;
    }

    public static String createAnonymousUser(boolean serverIsOnline) {
        // make sure we've fetched the app jwt
        String appJwt = getAppJwt(serverIsOnline);

        if (serverIsOnline && appJwt != null) {
            String timezone = TimeZone.getDefault().getID();

            JsonObject payload = new JsonObject();
            payload.addProperty("username", getOsUsername());
            payload.addProperty("timezone", timezone);
            payload.addProperty("hostname", getHostname());
            payload.addProperty("creation_annotation", "NO_SESSION_FILE");

            String api = "/data/onboard";
            SoftwareResponse resp = SoftwareCoUtils.makeApiCall(api, HttpPost.METHOD_NAME, payload.toString(), appJwt);
            if (resp.isOk()) {
                // check if we have the data and jwt
                // resp.data.jwt and resp.data.user
                // then update the session.json for the jwt
                JsonObject data = resp.getJsonObj();
                // check if we have any data
                if (data != null && data.has("jwt")) {
                    String dataJwt = data.get("jwt").getAsString();
                    SoftwareCoSessionManager.setItem("jwt", dataJwt);
                    return dataJwt;
                }
            }
        }
        return null;
    }

    private static JsonObject getUser(boolean serverIsOnline) {
        String jwt = SoftwareCoSessionManager.getItem("jwt");
        if (serverIsOnline) {
            String api = "/users/me";
            SoftwareResponse resp = SoftwareCoUtils.makeApiCall(api, HttpGet.METHOD_NAME, null, jwt);
            if (resp.isOk()) {
                // check if we have the data and jwt
                // resp.data.jwt and resp.data.user
                // then update the session.json for the jwt
                JsonObject obj = resp.getJsonObj();
                if (obj != null && obj.has("data")) {
                    return obj.get("data").getAsJsonObject();
                }
            }
        }
        return null;
    }

    private static JsonObject getClientInfo(boolean serverIsOnline) {
        if (serverIsOnline) {
            // To find client info
            jwt = getAppJwt(serverIsOnline);
            LOG.log(Level.INFO, pluginName + ": JWT: " + jwt);
            String api = "/auth/spotify/clientInfo";
            SoftwareResponse resp = SoftwareCoUtils.makeApiCall(api, HttpGet.METHOD_NAME, null, jwt);
            if (resp.isOk()) {
                return resp.getJsonObj();
            }
        }
        return null;
    }

    private static void authSpotify(boolean serverIsOnline) {
        if (serverIsOnline) {
            String api = "https://api.software.com/auth/spotify?token=" + jwt + "&mac=" + isMac();
            BrowserUtil.browse(api);
        }
    }

    private static JsonObject getUserDetails(boolean serverIsOnline) {
        if (serverIsOnline) {
            if(jwt != null) {
                // To find user Details
                LOG.log(Level.INFO, pluginName + ": JWT: " + jwt);
                String api = "/auth/spotify/user";
                SoftwareResponse resp = SoftwareCoUtils.makeApiCall(api, HttpGet.METHOD_NAME, null, jwt);
                if (resp.isOk()) {
                    JsonObject obj = resp.getJsonObj();
                    if (obj != null && obj.has("auths")) {
                        for(JsonElement array : obj.get("auths").getAsJsonArray()) {
                            if(array.getAsJsonObject().get("type").getAsString().equals("spotify")) {
                                String email = obj.get("email").getAsString();
                                if (validateEmail(email)) {
                                    SoftwareCoSessionManager.setItem("jwt", obj.get("plugin_jwt").getAsString());
                                    SoftwareCoSessionManager.setItem("name", email);
                                }
                            }
                        }
                    }
                    return resp.getJsonObj();
                }
            }
        }
        return null;
    }

    private static String regex = "^\\S+@\\S+\\.\\S+$";
    private static Pattern pattern = Pattern.compile(regex);

    private static boolean validateEmail(String email) {
        return pattern.matcher(email).matches();
    }

    private static boolean isLoggedOn(boolean serverIsOnline) {
        String pluginjwt = SoftwareCoSessionManager.getItem("jwt");
        if (serverIsOnline) {
            JsonObject userObj = getUser(serverIsOnline);
            if (userObj != null && userObj.has("email")) {
                // check if the email is valid
                String email = userObj.get("email").getAsString();
                if (validateEmail(email)) {
                    SoftwareCoSessionManager.setItem("jwt", userObj.get("plugin_jwt").getAsString());
                    SoftwareCoSessionManager.setItem("name", email);
                    for(JsonElement array : userObj.get("auths").getAsJsonArray()) {
                        if(array.getAsJsonObject().get("type").getAsString().equals("spotify")) {
                            if(ACCESS_TOKEN == null && REFRESH_TOKEN == null) {
                                ACCESS_TOKEN = array.getAsJsonObject().get("access_token").getAsString();
                                REFRESH_TOKEN = array.getAsJsonObject().get("refresh_token").getAsString();
                                SoftwareCoSessionManager.setItem("spotify_access_token", ACCESS_TOKEN);
                                SoftwareCoSessionManager.setItem("spotify_refresh_token", REFRESH_TOKEN);
                            }
                            jwt = userObj.get("plugin_token").getAsString();
                            spotifyCacheState = true;
                        }
                    }
                    return true;
                }
            }

            String api = "/users/plugin/state";
            SoftwareResponse resp = SoftwareCoUtils.makeApiCall(api, HttpGet.METHOD_NAME, null, pluginjwt);
            if (resp.isOk()) {
                // check if we have the data and jwt
                // resp.data.jwt and resp.data.user
                // then update the session.json for the jwt
                JsonObject data = resp.getJsonObj();
                String state = (data != null && data.has("state")) ? data.get("state").getAsString() : "UNKNOWN";
                // check if we have any data
                if (state.equals("OK")) {
                    String dataJwt = data.get("jwt").getAsString();
                    SoftwareCoSessionManager.setItem("jwt", dataJwt);
                    String dataEmail = data.get("email").getAsString();
                    if (dataEmail != null) {
                        SoftwareCoSessionManager.setItem("name", dataEmail);
                    }
                    return true;
                } else if (state.equals("NOT_FOUND")) {
                    SoftwareCoSessionManager.setItem("jwt", null);
                }
            }
        }
        SoftwareCoSessionManager.setItem("name", null);
        return false;
    }

    private static boolean isSpotifyConncted(boolean serverIsOnline) {
        JsonObject userObj = getUserDetails(serverIsOnline);
        if (userObj != null && userObj.has("email")) {
            // check if the email is valid
            String email = userObj.get("email").getAsString();
            if (validateEmail(email)) {
                SoftwareCoSessionManager.setItem("jwt", userObj.get("plugin_jwt").getAsString());
                SoftwareCoSessionManager.setItem("name", email);
                for(JsonElement array : userObj.get("auths").getAsJsonArray()) {
                    if(array.getAsJsonObject().get("type").getAsString().equals("spotify")) {
                        if(ACCESS_TOKEN == null && REFRESH_TOKEN == null) {
                            ACCESS_TOKEN = array.getAsJsonObject().get("access_token").getAsString();
                            REFRESH_TOKEN = array.getAsJsonObject().get("refresh_token").getAsString();
                            SoftwareCoSessionManager.setItem("spotify_access_token", ACCESS_TOKEN);
                            SoftwareCoSessionManager.setItem("spotify_refresh_token", REFRESH_TOKEN);
                        }
                        jwt = userObj.get("plugin_token").getAsString();
                        spotifyCacheState = true;
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static synchronized UserStatus getUserStatus() {
        UserStatus currentUserStatus = new UserStatus();
        if (loggedInCacheState && pluginName.equals("Code Time")) {
            currentUserStatus.loggedIn = loggedInCacheState;
            return currentUserStatus;
        }

        boolean serverIsOnline = SoftwareCoSessionManager.isServerOnline();

        boolean loggedIn = isLoggedOn(serverIsOnline);

        if(pluginName.equals("Music Time"))
            spotifyCacheState =  isSpotifyConncted(serverIsOnline);

        currentUserStatus.loggedIn = loggedIn;

        if (loggedInCacheState != loggedIn && pluginName.equals("Code Time")) {
            sendHeartbeat("STATE_CHANGE:LOGGED_IN:" + loggedIn);
            // refetch kpm
            final Runnable kpmStatusRunner = () -> SoftwareCoSessionManager.getInstance().fetchDailyKpmSessionInfo();
            kpmStatusRunner.run();
        }

        loggedInCacheState = loggedIn;

        return currentUserStatus;
    }

    public static void sendHeartbeat(String reason) {
        boolean serverIsOnline = SoftwareCoSessionManager.isServerOnline();
        String jwt = SoftwareCoSessionManager.getItem("jwt");
        if (serverIsOnline && jwt != null) {

            long start = Math.round(System.currentTimeMillis() / 1000);

            JsonObject payload = new JsonObject();
            payload.addProperty("pluginId", pluginId);
            payload.addProperty("os", getOs());
            payload.addProperty("start", start);
            payload.addProperty("version", VERSION);
            payload.addProperty("hostname", getHostname());
            payload.addProperty("trigger_annotation", reason);

            String api = "/data/heartbeat";
            SoftwareResponse resp = SoftwareCoUtils.makeApiCall(api, HttpPost.METHOD_NAME, payload.toString(), jwt);
            if (!resp.isOk()) {
                LOG.log(Level.WARNING, pluginName + ": unable to send heartbeat ping");
            }
        }
    }

    public static void showOfflinePrompt(boolean isTenMinuteReconnect) {
        final String reconnectMsg = (isTenMinuteReconnect) ? "in ten minutes. " : "soon. ";
        ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
                String infoMsg = "Our service is temporarily unavailable. " +
                        "We will try to reconnect again " + reconnectMsg +
                        "Your status bar will not update at this time.";
                // ask to download the PM
                Messages.showInfoMessage(infoMsg, pluginName);
            }
        });
    }

    public static void showMsgPrompt(String infoMsg) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
                Messages.showInfoMessage(infoMsg, pluginName);
            }
        });
    }

    public static class TimesData {
        public Integer offset = ZonedDateTime.now().getOffset().getTotalSeconds();
        public long now = System.currentTimeMillis() / 1000;
        public long local_now = now + offset;
        public String timezone = TimeZone.getDefault().getID();
    }

    public static TimesData getTimesData() {
        TimesData timesData = new TimesData();
        return timesData;
    }

    public static String getDashboardRow(String label, String value) {
        String content = getDashboardLabel(label) + " : " + getDashboardValue(value) + "\n";
        return content;
    }

    public static String getSectionHeader(String label) {
        String content = label + "\n";
        // add 3 to account for the " : " between the columns
        int dashLen = DASHBOARD_LABEL_WIDTH + DASHBOARD_VALUE_WIDTH + 15;
        for (int i = 0; i < dashLen; i++) {
            content += "-";
        }
        content += "\n";
        return content;
    }

    public static String getDashboardLabel(String label) {
        return getDashboardDataDisplay(DASHBOARD_LABEL_WIDTH, label);
    }

    public static String getDashboardValue(String value) {
        String valueContent = getDashboardDataDisplay(DASHBOARD_VALUE_WIDTH, value);
        String paddedContent = "";
        for (int i = 0; i < 11; i++) {
            paddedContent += " ";
        }
        paddedContent += valueContent;
        return paddedContent;
    }

    public static String getDashboardDataDisplay(int widthLen, String data) {
        int len = widthLen - data.length();
        String content = "";
        for (int i = 0; i < len; i++) {
            content += " ";
        }
        return content + "" + data;
    }

}
