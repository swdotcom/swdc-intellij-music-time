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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.softwareco.intellij.plugin.music.MusicControlManager;
import com.softwareco.intellij.plugin.music.PlayListCommands;
import com.softwareco.intellij.plugin.music.PlaylistManager;
import com.softwareco.intellij.plugin.slack.SlackControlManager;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.SystemUtils;
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
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class SoftwareCoUtils {

    public static final Logger LOG = Logger.getLogger("SoftwareCoUtils");

    // set the api endpoint to use
    public final static String api_endpoint = "https://api.software.com";
    // set the launch url to use
    public final static String launch_url = "https://app.software.com";

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
    private static int deviceCounter = 0;

    private static String SERVICE_NOT_AVAIL =
            "Our service is temporarily unavailable.\n\nPlease try again later.\n";

    // jwt_from_apptoken_call
    public static String jwt = null;

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

    public static boolean isLoggedIn() {
        return loggedInCacheState;
    }

    public static boolean isSpotifyConncted() { return MusicControlManager.spotifyCacheState; }

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
        if (api.contains("/ping") || api.contains("/sessions") || api.contains("/dashboard")
                || api.contains("/users/plugin/accounts")) {
            // if the server is having issues, we'll timeout within 5 seconds for these calls
            httpTask = new SoftwareHttpManager(api, httpMethodName, payload, overridingJwt, httpClient);
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
        Future<HttpResponse> response = EXECUTOR_SERVICE.submit(httpTask);

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

    public static String getStringRepresentation(HttpEntity res) throws IOException {
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

                        String headphoneiconId = SoftwareCoStatusBarIconWidget.ICON_ID + "_headphoneicon";
                        String likeiconId = SoftwareCoStatusBarIconWidget.ICON_ID + "_likeicon";
                        String unlikeiconId = SoftwareCoStatusBarIconWidget.ICON_ID + "_unlikeicon";
                        String preiconId = SoftwareCoStatusBarIconWidget.ICON_ID + "_preicon";
                        String stopiconId = SoftwareCoStatusBarIconWidget.ICON_ID + "_stopicon";
                        String pauseiconId = SoftwareCoStatusBarIconWidget.ICON_ID + "_pauseicon";
                        String playiconId = SoftwareCoStatusBarIconWidget.ICON_ID + "_playicon";
                        String nexticonId = SoftwareCoStatusBarIconWidget.ICON_ID + "_nexticon";
                        String songtrackId = SoftwareCoStatusBarTextWidget.TEXT_ID + "_songtrack";
                        String connectspotifyId = SoftwareCoStatusBarTextWidget.TEXT_ID + "_connectspotify";

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
//                                SoftwareCoStatusBarIconWidget headphoneIconWidget = buildStatusBarIconWidget(
//                                        headphoneIconVal, tooltip, headphoneiconId);
//                                statusBar.addWidget(headphoneIconWidget, headphoneiconId);
//                                statusBar.updateWidget(headphoneiconId);

                                SoftwareCoStatusBarTextWidget kpmWidget = buildStatusBarTextWidget(
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
                            final String musicToolTipVal = MusicControlManager.currentTrackName != null ? MusicControlManager.currentTrackName : "Current Track";
                            if(MusicControlManager.currentTrackName != null && MusicControlManager.currentTrackName.length() > 20) {
                                MusicControlManager.currentTrackName = MusicControlManager.currentTrackName.substring(0, 19) + "...";
                            }
                            final String musicMsgVal = MusicControlManager.currentTrackName != null ? MusicControlManager.currentTrackName : "Current Track";
                            if (headphoneIconVal != null) {
                                if (connectPremiumMsg.equals("Connect Premium")) {
                                    SoftwareCoStatusBarTextWidget kpmWidget = buildStatusBarTextWidget(
                                            connectPremiumMsg, connectPremiumMsg, connectspotifyId);
                                    statusBar.addWidget(kpmWidget, connectspotifyId);
                                    statusBar.updateWidget(connectspotifyId);
                                }

                                if(MusicControlManager.currentTrackName != null) {
                                    if(MusicControlManager.likedTracks.containsKey(MusicControlManager.currentTrackId)) {
                                        SoftwareCoStatusBarIconWidget likeIconWidget = buildStatusBarIconWidget(
                                                likeIcon, "unlike", likeiconId);
                                        statusBar.addWidget(likeIconWidget, likeiconId);
                                        statusBar.updateWidget(likeiconId);
                                    } else {
                                        SoftwareCoStatusBarIconWidget unlikeIconWidget = buildStatusBarIconWidget(
                                                unlikeIcon, "like", unlikeiconId);
                                        statusBar.addWidget(unlikeIconWidget, unlikeiconId);
                                        statusBar.updateWidget(unlikeiconId);
                                    }

                                    SoftwareCoStatusBarIconWidget preIconWidget = buildStatusBarIconWidget(
                                            preIcon, "previous", preiconId);
                                    statusBar.addWidget(preIconWidget, preiconId);
                                    statusBar.updateWidget(preiconId);

//                                    SoftwareCoStatusBarIconWidget stopIconWidget = buildStatusBarIconWidget(
//                                            stopIcon, "stop", stopiconId);
//                                    statusBar.addWidget(stopIconWidget, stopiconId);
//                                    statusBar.updateWidget(stopiconId);

                                    if (!MusicControlManager.defaultbtn.equals("play")) {
                                        SoftwareCoStatusBarIconWidget pauseIconWidget = buildStatusBarIconWidget(
                                                pauseIcon, "pause", pauseiconId);
                                        statusBar.addWidget(pauseIconWidget, pauseiconId);
                                        statusBar.updateWidget(pauseiconId);
                                    } else {
                                        SoftwareCoStatusBarIconWidget playIconWidget = buildStatusBarIconWidget(
                                                playIcon, "play", playiconId);
                                        statusBar.addWidget(playIconWidget, playiconId);
                                        statusBar.updateWidget(playiconId);
                                    }

                                    SoftwareCoStatusBarIconWidget nextIconWidget = buildStatusBarIconWidget(
                                            nextIcon, "next", nexticonId);
                                    statusBar.addWidget(nextIconWidget, nexticonId);
                                    statusBar.updateWidget(nexticonId);
                                }

                                if(!musicMsgVal.equals("Current Track")) {
                                    SoftwareCoStatusBarTextWidget kpmWidget = buildStatusBarTextWidget(
                                            musicMsgVal, musicToolTipVal, songtrackId);
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

    public static SoftwareCoStatusBarTextWidget buildStatusBarTextWidget(String msg, String tooltip, String id) {
        SoftwareCoStatusBarTextWidget textWidget =
                new SoftwareCoStatusBarTextWidget(id);
        textWidget.setText(msg);
        textWidget.setTooltip(tooltip);
        return textWidget;
    }

    public static SoftwareCoStatusBarIconWidget buildStatusBarIconWidget(String iconName, String tooltip, String id) {
        Icon icon = IconLoader.getIcon("/com/softwareco/intellij/plugin/assets/" + iconName);

        SoftwareCoStatusBarIconWidget iconWidget =
                new SoftwareCoStatusBarIconWidget(id);
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

    public static boolean isSpotifyRunning() {
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
        BrowserUtil.browse("https://github.com/swdotcom/swdc-intellij-music-time/issues");
    }

    public static void submitFeedback() {
        BrowserUtil.browse("mailto:cody@software.com");
    }

    public static synchronized void updatePlayerControls() {
        if(MusicControlManager.spotifyCacheState) {
//            if(deviceCounter == 0) {
//                MusicControlManager.getSpotifyDevices(); // API call
//                deviceCounter = 2;
//            } else {
//                deviceCounter--;
//            }
            if(MusicControlManager.playerType.equals("Web Player") || isWindows()){
                PlaylistManager.getSpotifyWebCurrentTrack();  // get current track to update status bar
            } else {
                PlaylistManager.getSpotifyDesktopCurrentTrack();  // get current track to update status bar
            }

            PlayListCommands.updatePlaylists(5, null); // API call

            if(MusicControlManager.userStatus != null && !MusicControlManager.userStatus.equals("premium")) {
                String headPhoneIcon = "headphone.png";
                SoftwareCoUtils.setStatusLineMessage(headPhoneIcon, "Connect Premium", "Current Track");
            } else {
                String headPhoneIcon = "headphone.png";
                SoftwareCoUtils.setStatusLineMessage(headPhoneIcon, "Current Track", "Current Track");
            }
        } else {
            String headPhoneIcon = "headphone.png";
            SoftwareCoUtils.setStatusLineMessage(headPhoneIcon, "Connect Spotify", "Connect Spotify");
            PlayListCommands.updatePlaylists(5, null);
        }
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

    public static JsonObject getUser(boolean serverIsOnline) {
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

    public static JsonObject getClientInfo(boolean serverIsOnline) {
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

    public static JsonObject getUserDetails(boolean serverIsOnline) {
        if (serverIsOnline) {
            if(jwt != null) {
                // To find user Details
                //LOG.log(Level.INFO, pluginName + ": JWT: " + jwt);
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

    public static boolean validateEmail(String email) {
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
                            if(MusicControlManager.ACCESS_TOKEN == null && MusicControlManager.REFRESH_TOKEN == null) {
                                MusicControlManager.ACCESS_TOKEN = array.getAsJsonObject().get("access_token").getAsString();
                                MusicControlManager.REFRESH_TOKEN = array.getAsJsonObject().get("refresh_token").getAsString();
                                SoftwareCoSessionManager.setItem("spotify_access_token", MusicControlManager.ACCESS_TOKEN);
                                SoftwareCoSessionManager.setItem("spotify_refresh_token", MusicControlManager.REFRESH_TOKEN);
                            }
                            MusicControlManager.spotifyCacheState = true;
                        }
                        if(array.getAsJsonObject().get("type").getAsString().equals("slack")) {
                            if(SlackControlManager.ACCESS_TOKEN == null) {
                                SlackControlManager.ACCESS_TOKEN = array.getAsJsonObject().get("access_token").getAsString();
                                SoftwareCoSessionManager.setItem("slack_access_token", SlackControlManager.ACCESS_TOKEN);
                            }
                            SlackControlManager.slackCacheState = true;
                        }

                        if(!userObj.get("plugin_token").isJsonNull())
                            jwt = userObj.get("plugin_token").getAsString();
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

    public static synchronized UserStatus getUserStatus() {
        UserStatus currentUserStatus = new UserStatus();
        if (loggedInCacheState) {
            currentUserStatus.loggedIn = loggedInCacheState;
            return currentUserStatus;
        }

        boolean serverIsOnline = SoftwareCoSessionManager.isServerOnline();

        boolean loggedIn = isLoggedOn(serverIsOnline);

        currentUserStatus.loggedIn = loggedIn;

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

    public static boolean sendSongSessionPayload(String songSession) {
        String jwt = SoftwareCoSessionManager.getItem("jwt");
        if (jwt != null) {
            LOG.info("Music Time: Sending payload: " + songSession);

            String api = "/music/session";
            SoftwareResponse resp = SoftwareCoUtils.makeApiCall(api, HttpPost.METHOD_NAME, songSession, jwt);
            if (!resp.isOk()) {
                LOG.log(Level.WARNING, pluginName + ": Unable to send song session");
                return false;
            }
            return true;
        }
        return false;
    }

    public static void sendBatchedLikedSongSessions(String tracksToSave) {
        boolean serverIsOnline = SoftwareCoSessionManager.isServerOnline();
        String jwt = SoftwareCoSessionManager.getItem("jwt");
        if (serverIsOnline && jwt != null) {

            JsonObject payload = new JsonObject();
            payload.addProperty("tracks", tracksToSave);

            String api = "/music/session/seed";
            SoftwareResponse resp = SoftwareCoUtils.makeApiCall(api, HttpPut.METHOD_NAME, payload.toString(), jwt);
            if (!resp.isOk()) {
                LOG.log(Level.WARNING, pluginName + ": unable to send liked song sessions");
            }
        }
    }

    // sendLikedTrack(like: true|false, trackId, type: spotify|itunes)
    public static void sendLikedTrack(boolean like, String trackId, String type) {
        boolean serverIsOnline = SoftwareCoSessionManager.isServerOnline();
        String jwt = SoftwareCoSessionManager.getItem("jwt");
        if (serverIsOnline && jwt != null) {

            JsonObject payload = new JsonObject();
            payload.addProperty("liked", like);

            String api = "/music/liked/track/" + trackId + "?type=" + type;
            SoftwareResponse resp = SoftwareCoUtils.makeApiCall(api, HttpPut.METHOD_NAME, payload.toString(), jwt);
            if (!resp.isOk()) {
                LOG.log(Level.WARNING, "Music Time: unable to send liked song to software.com");
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

    public static int showMsgInputPrompt(String message, String title, Icon icon, String[] options) {
        return Messages.showChooseDialog(message, title, options, options[0], icon);
    }

    public static String showInputPrompt(String message, String title, Icon icon) {
        return Messages.showInputDialog(message, title, icon, "", getRegexInputValidator());
    }

    private static InputValidator getRegexInputValidator() {
        return new InputValidator() {
            @Override
            public boolean checkInput(String string) {
                try {
                    if (string == null || string.trim().isEmpty()) {
                        //do not allow null or blank entries
                        return false;
                    }
                    Pattern.compile(string);
                    return true;
                } catch (PatternSyntaxException e) {
                    return false;
                }
            }

            @Override
            public boolean canClose(String s) {
                return true;
            }
        };
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

    public static void launchMusicWebDashboard() {
        String url = SoftwareCoUtils.launch_url + "/music";
        BrowserUtil.browse(url);
    }

}
