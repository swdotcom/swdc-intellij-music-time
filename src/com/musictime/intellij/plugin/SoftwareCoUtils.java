/**
 * Copyright (c) 2018 by Software.com
 * All rights reserved
 */
package com.musictime.intellij.plugin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.musictime.intellij.plugin.fs.FileManager;
import com.musictime.intellij.plugin.models.DeviceInfo;
import com.musictime.intellij.plugin.models.FileDetails;
import com.musictime.intellij.plugin.music.MusicControlManager;
import com.musictime.intellij.plugin.music.PlayListCommands;
import com.musictime.intellij.plugin.musicjava.Client;
import com.musictime.intellij.plugin.musicjava.DeviceManager;
import com.musictime.intellij.plugin.musicjava.MusicStore;
import com.musictime.intellij.plugin.slack.SlackControlManager;
import org.apache.commons.codec.binary.Base64;
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
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

public class SoftwareCoUtils {

    public static final Logger LOG = Logger.getLogger("SoftwareCoUtils");

    public static HttpClient httpClient;
    public static HttpClient pingClient;

    // 16 = intellij music time
    public static int pluginId = 16;
    public static String VERSION = null;

    public static KeystrokeCount latestPayload = null;

    public final static ExecutorService EXECUTOR_SERVICE = Executors.newCachedThreadPool();

    private final static int EOF = -1;

    private static boolean fetchingResourceInfo = false;
    private static JsonObject lastResourceInfo = new JsonObject();

    private static boolean appAvailable = true;

    private static int DASHBOARD_LABEL_WIDTH = 25;
    private static int DASHBOARD_VALUE_WIDTH = 25;

    private static long DAYS_IN_SECONDS = 60 * 60 * 24;

    private static ScheduledFuture toggleSongNameFuture = null;

    private static String workspace_name = null;
    private static boolean initiatedCodeTimeInstallCheck = false;
    public static boolean codeTimeInstalled = false;

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

    public static boolean isCodeTimeInstalled() {
        if (!initiatedCodeTimeInstallCheck) {
            initiatedCodeTimeInstallCheck = true;
            getUser();
        }
        return codeTimeInstalled;
    }

    public static KeystrokeCount getLatestPayload() {
        return latestPayload;
    }

    public static void setLatestPayload(KeystrokeCount payload) {
        latestPayload = payload;
    }

    public static String getWorkspaceName() {
        if (workspace_name == null) {
            workspace_name = generateToken();
        }
        return workspace_name;
    }

    public static String generateToken() {
        String uuid = UUID.randomUUID().toString();
        return uuid.replace("-", "");
    }

    public static String getHostname() {
        List<String> cmd = new ArrayList<String>();
        cmd.add("hostname");
        String hostname = getSingleLineResult(cmd, 1);
        return hostname;
    }

    public static boolean isMusicTime() {
        if(SoftwareCoMusic.getPluginName().equals("Music Time")) {
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

        SoftwareResponse softwareResponse = new SoftwareResponse();

        SoftwareHttpManager httpTask = null;
        if (api.contains("/ping") || api.contains("/sessions") || api.contains("/dashboard")
                || api.contains("/users/plugin/accounts")) {
            // if the server is having issues, we'll timeout within 5 seconds for these calls
            httpTask = new SoftwareHttpManager(api, httpMethodName, payload, httpClient);
        } else {
            if (httpMethodName.equals(HttpPost.METHOD_NAME)) {
                // continue, POSTS encapsulated "invokeLater" with a timeout of 5 seconds
                httpTask = new SoftwareHttpManager(api, httpMethodName, payload, pingClient);
            } else {
                if (!appAvailable) {
                    // bail out
                    softwareResponse.setIsOk(false);
                    return softwareResponse;
                }
                httpTask = new SoftwareHttpManager(api, httpMethodName, payload, httpClient);
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
                                    jsonEl = JsonParser.parseString(jsonStr);
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
                            String errorMessage = SoftwareCoMusic.getPluginName() + ": Unable to get the response from the http request for api " + api + ", error: " + e.getMessage();
                            softwareResponse.setErrorMessage(errorMessage);
                            LOG.log(Level.WARNING, errorMessage);
                        }
                    }

                }
            } catch (InterruptedException | ExecutionException e) {
                String errorMessage = SoftwareCoMusic.getPluginName() + ": Unable to get the response from the http request for api " + api + ", error: " + e.getMessage();
                softwareResponse.setErrorMessage(errorMessage);
                LOG.log(Level.WARNING, errorMessage);
            }
        }

        return softwareResponse;
    }

    public static Project getFirstActiveProject() {
        Project[] projects = ProjectManager.getInstance().getOpenProjects();
        if (projects != null && projects.length > 0) {
            return projects[0];
        }
        Editor[] editors = EditorFactory.getInstance().getAllEditors();
        if (editors != null && editors.length > 0) {
            return editors[0].getProject();
        }
        return null;
    }

    public static Project getProjectForPath(String path) {
        Editor[] editors = EditorFactory.getInstance().getAllEditors();
        if (editors != null && editors.length > 0) {
            for (Editor editor : editors) {
                if (editor != null && editor.getProject() != null) {
                    String basePath = editor.getProject().getBasePath();
                    if (path.indexOf(basePath) != -1) {
                        return editor.getProject();
                    }
                }
            }
        } else {
            Project[] projects = ProjectManager.getInstance().getOpenProjects();
            if (projects != null && projects.length > 0) {
                return projects[0];
            }
        }
        return null;
    }

    public static FileDetails getFileDetails(String fullFileName) {
        FileDetails fileDetails = new FileDetails();
        if (StringUtils.isNotBlank(fullFileName)) {
            fileDetails.full_file_name = fullFileName;
            Project p = getProjectForPath(fullFileName);
            if (p != null) {
                fileDetails.project_directory = p.getBasePath();
                fileDetails.project_name = p.getName();
            }

            File f = new File(fullFileName);

            if (f.exists()) {
                fileDetails.character_count = f.length();
                fileDetails.file_name = f.getName();
                if (StringUtils.isNotBlank(fileDetails.project_directory) && fullFileName.indexOf(fileDetails.project_directory) != -1) {
                    // strip out the project_file_name
                    String[] parts = fullFileName.split(fileDetails.project_directory);
                    if (parts.length > 1) {
                        fileDetails.project_file_name = parts[1];
                    } else {
                        fileDetails.project_file_name = fullFileName;
                    }
                } else {
                    fileDetails.project_file_name = fullFileName;
                }
                fileDetails.line_count = SoftwareCoUtils.getLineCount(fullFileName);

                VirtualFile vFile = LocalFileSystem.getInstance().findFileByIoFile(f);
                if (vFile != null) {
                    fileDetails.syntax = vFile.getFileType().getName();
                }
            }
        }

        return fileDetails;
    }

    public static int getLineCount(String fileName) {
        Stream<String> stream = null;
        try {
            Path path = Paths.get(fileName);
            stream = Files.lines(path);
            return (int) stream.count();
        } catch (Exception e) {
            return 0;
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (Exception e) {
                    //
                }
            }
        }
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

    public static Project getOpenProject() {
        ProjectManager projMgr = ProjectManager.getInstance();
        Project[] projects = projMgr.getOpenProjects();
        if (projects != null && projects.length > 0) {
            return projects[0];
        }
        return null;
    }

    public static synchronized void setStatusLineMessage() {
        try {
            Project p = getOpenProject();
            if (p == null) {
                return;
            }
            final StatusBar statusBar = WindowManager.getInstance().getStatusBar(p);

            if (statusBar != null) {
                updateStatusBar(false);
            }
        } catch (Exception e) {
            //
        }
    }

    private static void updateStatusBar(boolean hideTrack) {

        ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
                ProjectManager pm = ProjectManager.getInstance();
                if (pm != null && pm.getOpenProjects() != null && pm.getOpenProjects().length > 0) {

                    try {
                        Project p = pm.getOpenProjects()[0];
                        StatusBar statusBar = WindowManager.getInstance().getStatusBar(p);

                        String headphoneiconId = SoftwareCoStatusBarIconWidget.ICON_ID + "_headphoneicon";
                        String likeiconId = SoftwareCoStatusBarIconWidget.ICON_ID + "_likeicon";
                        String unlikeiconId = SoftwareCoStatusBarIconWidget.ICON_ID + "_unlikeicon";
                        String preiconId = SoftwareCoStatusBarIconWidget.ICON_ID + "_preicon";
                        String pauseiconId = SoftwareCoStatusBarIconWidget.ICON_ID + "_pauseicon";
                        String playiconId = SoftwareCoStatusBarIconWidget.ICON_ID + "_playicon";
                        String nexticonId = SoftwareCoStatusBarIconWidget.ICON_ID + "_nexticon";
                        String pulseiconId = SoftwareCoStatusBarTextWidget.TEXT_ID + "_pulseicon";
                        String songtrackId = SoftwareCoStatusBarTextWidget.TEXT_ID + "_songtrack";
                        String connectspotifyId = SoftwareCoStatusBarTextWidget.TEXT_ID + "_connectspotify";

                        Disposable disposable = new Disposable() {
                            @Override
                            public void dispose() {
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
                                if (statusBar.getWidget(pauseiconId) != null) {
                                    statusBar.removeWidget(pauseiconId);
                                }
                                if (statusBar.getWidget(playiconId) != null) {
                                    statusBar.removeWidget(playiconId);
                                }
                                if (statusBar.getWidget(nexticonId) != null) {
                                    statusBar.removeWidget(nexticonId);
                                }
                                if (statusBar.getWidget(pulseiconId) != null) {
                                    statusBar.removeWidget(pulseiconId);
                                }
                                if (statusBar.getWidget(songtrackId) != null) {
                                    statusBar.removeWidget(songtrackId);
                                }
                                if (statusBar.getWidget(connectspotifyId) != null) {
                                    statusBar.removeWidget(connectspotifyId);
                                }
                            }
                        };

                        disposable.dispose();

                        boolean requiresReAuth = MusicControlManager.requiresReAuthentication();
                        boolean hasSpotifyAccess = MusicControlManager.hasSpotifyAccess();
                        boolean requiresAccess = requiresReAuth || !hasSpotifyAccess ? true : false;

                        String connectLabel = null;
                        if (requiresAccess) {
                            if (requiresReAuth) {
                                connectLabel = "Reconnect Spotify";
                            } else if (!hasSpotifyAccess) {
                                connectLabel = "Connect Spotify";
                            }
                        }

                        String email = FileManager.getItem("name");
                        String headphoneIconVal = "headphones.png";
                        String headphoneTooltip = "Click to see more from Music Time.";
                        if (StringUtils.isNotBlank(email)) {
                            headphoneTooltip += " (" + email + ")";
                        }

                        DeviceInfo deviceInfo = DeviceManager.getBestDeviceOption();
                        SoftwareCoStatusBarIconWidget headphoneIconWidget = buildStatusBarIconWidget(
                                headphoneIconVal, headphoneTooltip, headphoneiconId);
                        statusBar.addWidget(headphoneIconWidget, headphoneiconId, disposable);
                        statusBar.updateWidget(headphoneiconId);

                        if (StringUtils.isNotBlank(connectLabel)) {
                            SoftwareCoStatusBarTextWidget kpmWidget = buildStatusBarTextWidget(
                                    connectLabel, connectLabel, connectspotifyId);
                            statusBar.addWidget(kpmWidget, connectspotifyId, disposable);
                            statusBar.updateWidget(connectspotifyId);
                        }

                        String likeIcon = "like.png";
                        String unlikeIcon = "unlike.png";
                        String preIcon = "previous.png";
                        String pauseIcon = "pause.png";
                        String playIcon = "play.png";
                        String nextIcon = "next.png";
                        String pulseIcon = "pulse.png";

                        String trackName = MusicControlManager.currentTrackName;
                        final String musicToolTipVal = trackName != null ? trackName : "";
                        if (trackName != null && trackName.length() > 19) {
                            trackName = trackName.substring(0, 18) + "...";
                        }

                        boolean isPremiumUser = MusicStore.isSpotifyPremiumUser();
                        boolean isMacUser = SoftwareCoUtils.isMac();

                        if (deviceInfo != null && connectLabel == null) {

                            if(isPremiumUser || isMacUser) {

                                SoftwareCoStatusBarIconWidget preIconWidget = buildStatusBarIconWidget(
                                        preIcon, "previous", preiconId);
                                statusBar.addWidget(preIconWidget, preiconId, disposable);
                                statusBar.updateWidget(preiconId);

                                if (MusicControlManager.currentTrackPlaying) {
                                    SoftwareCoStatusBarIconWidget pauseIconWidget = buildStatusBarIconWidget(
                                            pauseIcon, "pause", pauseiconId);
                                    statusBar.addWidget(pauseIconWidget, pauseiconId, disposable);
                                    statusBar.updateWidget(pauseiconId);
                                } else {
                                    SoftwareCoStatusBarIconWidget playIconWidget = buildStatusBarIconWidget(
                                            playIcon, "play", playiconId);
                                    statusBar.addWidget(playIconWidget, playiconId, disposable);
                                    statusBar.updateWidget(playiconId);
                                }

                                SoftwareCoStatusBarIconWidget nextIconWidget = buildStatusBarIconWidget(
                                        nextIcon, "next", nexticonId);
                                statusBar.addWidget(nextIconWidget, nexticonId, disposable);
                                statusBar.updateWidget(nexticonId);

                            }
                        }

                        if(MusicControlManager.currentTrackId != null) {
                            if (MusicControlManager.likedTracks.containsKey(MusicControlManager.currentTrackId)) {
                                SoftwareCoStatusBarIconWidget likeIconWidget = buildStatusBarIconWidget(
                                        likeIcon, "unlike", likeiconId);
                                statusBar.addWidget(likeIconWidget, likeiconId, disposable);
                                statusBar.updateWidget(likeiconId);
                            } else {
                                SoftwareCoStatusBarIconWidget unlikeIconWidget = buildStatusBarIconWidget(
                                        unlikeIcon, "like", unlikeiconId);
                                statusBar.addWidget(unlikeIconWidget, unlikeiconId, disposable);
                                statusBar.updateWidget(unlikeiconId);
                            }
                        }

                        if (StringUtils.isBlank(trackName) || hideTrack) {
                            SoftwareCoStatusBarIconWidget pulseIconWidget = buildStatusBarIconWidget(
                                    pulseIcon, "Display song info", pulseiconId);
                            statusBar.addWidget(pulseIconWidget, pulseiconId, disposable);
                            statusBar.updateWidget(pulseiconId);
                        }

                        if (!hideTrack) {
                            SoftwareCoStatusBarTextWidget kpmWidget = buildStatusBarTextWidget(
                                    trackName, musicToolTipVal, songtrackId);
                            statusBar.addWidget(kpmWidget, songtrackId, disposable);
                            statusBar.updateWidget(songtrackId);
                        }

                        // hide the song
                        if (!hideTrack) {
                            AsyncManager.getInstance().executeOnceInSeconds(() -> toggleSongName(), 1);
                        }

                    } catch(Exception e){
                        //
                    }
                }
            }
        });
    }

    private static void toggleSongName() {
        if (toggleSongNameFuture != null) {
            toggleSongNameFuture.cancel(false);
            toggleSongNameFuture = null;
        }
        toggleSongNameFuture = AsyncManager.getInstance().executeOnceInSeconds(() -> hideSongName(), 5);
    }

    private static void hideSongName() {
        updateStatusBar(true);
        toggleSongNameFuture = null;
    }

    public static SoftwareCoStatusBarTextWidget buildStatusBarTextWidget(String msg, String tooltip, String id) {
        SoftwareCoStatusBarTextWidget textWidget =
                new SoftwareCoStatusBarTextWidget(id);
        textWidget.setText(msg);
        textWidget.setTooltip(tooltip);
        return textWidget;
    }

    public static SoftwareCoStatusBarIconWidget buildStatusBarIconWidget(String iconName, String tooltip, String id) {
        Icon icon = IconLoader.getIcon("/com/musictime/intellij/plugin/assets/" + iconName);

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

    public static List<String> getResultsForCommandArgs(String[] args, String dir) {
        List<String> results = new ArrayList<>();
        try {
            String result = runCommand(args, dir);
            if (result == null || result.trim().length() == 0) {
                return results;
            }
            String[] contentList = result.split("\n");
            results = Arrays.asList(contentList);
        } catch (Exception e) {
            if (results == null) {
                results = new ArrayList<>();
            }
        }
        return results;
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

    public static synchronized void updatePlayerControls(boolean recursiveCall) {
        PlayListCommands.updatePlaylists(5, null); // API call

        SoftwareCoUtils.setStatusLineMessage();
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

    public static void getAppJwt() {
        long now = Math.round(System.currentTimeMillis() / 1000);
        String api = "/data/apptoken?token=" + now;
        SoftwareResponse resp = SoftwareCoUtils.makeApiCall(api, HttpGet.METHOD_NAME, null);
        if (resp.isOk()) {
            JsonObject obj = resp.getJsonObj();
            FileManager.setItem("jwt", obj.get("jwt").getAsString());
        }
    }

    public static JsonObject getUser() {
        String api = "/users/me";
        SoftwareResponse resp = SoftwareCoUtils.makeApiCall(api, HttpGet.METHOD_NAME, null);
        if (resp.isOk()) {
            // check if we have the data and jwt
            // resp.data.jwt and resp.data.user
            // then update the session.json for the jwt
            JsonObject obj = resp.getJsonObj();

            if (obj != null && obj.has("data")) {
                JsonObject userData = obj.get("data").getAsJsonObject();

                if (userData != null && userData.get("integrations") != null) {
                    JsonArray jsonArray = userData.get("integrations").getAsJsonArray();
                    if (jsonArray != null && jsonArray.size() > 0) {
                        // check if Intellij Code Time is installed
                        for (JsonElement el : jsonArray) {
                            if (el.getAsJsonObject().get("pluginId").getAsInt() == 4) {
                                codeTimeInstalled = true;
                                break;
                            }
                        }
                    }
                }

                if (!codeTimeInstalled) {
                    // check to see if latestPayloadTimestampEndUtc is found
                    String val = FileManager.getItem("latestPayloadTimestampEndUtc");
                    if (StringUtils.isNotBlank(val)) {
                        codeTimeInstalled = true;
                    }
                }

                return userData;
            }
        }

        if (!codeTimeInstalled) {
            // check to see if latestPayloadTimestampEndUtc is found
            String val = FileManager.getItem("latestPayloadTimestampEndUtc");
            if (StringUtils.isNotBlank(val)) {
                codeTimeInstalled = true;
            }
        }

        return null;
    }

    public static void getAndUpdateClientInfo() {
        // To find client info
        String api = "/auth/spotify/clientInfo";
        SoftwareResponse resp = SoftwareCoUtils.makeApiCall(api, HttpGet.METHOD_NAME, null);
        if (resp.isOk()) {
            JsonObject jsonObj = resp.getJsonObj();
            if (jsonObj != null) {
                MusicStore.SPOTIFY_CLIENT_ID = jsonObj.get("clientId").getAsString();
                MusicStore.SPOTIFY_CLIENT_SECRET = jsonObj.get("clientSecret").getAsString();
            }
        }
    }

    private static String regex = "^\\S+@\\S+\\.\\S+$";
    private static Pattern pattern = Pattern.compile(regex);

    public static boolean validateEmail(String email) {
        return pattern.matcher(email).matches();
    }

    public static boolean getMusicTimeUserStatus() {
        String api = "/users/plugin/state";
        SoftwareResponse resp = SoftwareCoUtils.makeApiCall(api, HttpGet.METHOD_NAME, null);
        if (resp.isOk()) {
            JsonObject data = resp.getJsonObj();

            // set the email and jwt if the state === "OK"
            String state = (data != null && data.has("state")) ? data.get("state").getAsString() : "UNKNOWN";
            if (state.toLowerCase().equals("ok")) {
                if (data.has("user") ) {
                    // get the user object
                    JsonObject userData = data.get("user").getAsJsonObject();

                    // set the spotify or slack access token
                    if (userData != null && userData.has("auths")) {
                        // it does
                        JsonArray auths = userData.getAsJsonArray("auths");
                        for (int i = 0; i < auths.size(); i++) {
                            JsonObject auth = auths.get(i).getAsJsonObject();
                            if (auth.has("type")) {
                                if (auth.get("type").getAsString().equals("spotify")) {
                                    FileManager.setItem("spotify_access_token", auth.get("access_token").getAsString());
                                    FileManager.setItem("spotify_refresh_token", auth.get("refresh_token").getAsString());
                                } else if (auth.get("type").getAsString().equals("slack")) {
                                    SlackControlManager.ACCESS_TOKEN = auth.get("access_token").getAsString();
                                    FileManager.setItem("slack_access_token", SlackControlManager.ACCESS_TOKEN);
                                    SlackControlManager.slackCacheState = true;
                                }
                            }
                        }
                    }
                }

                // set the jwt and name
                FileManager.setItem("name", data.get("email").getAsString());
                FileManager.setItem("jwt", data.get("jwt").getAsString());
                // authorized, return true
                return true;
            }


        }

        return false;
    }

    public static void showOfflinePrompt() {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
                String infoMsg = "Our service is temporarily unavailable or you are currently offline. " +
                        "We will try to reconnect again soon. Your status bar will not update at this time.";
                // ask to download the PM
                Messages.showInfoMessage(infoMsg, SoftwareCoMusic.getPluginName());
            }
        });
    }

    public static void showInfoMessage(String msg) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
                // ask to download the PM
                Messages.showInfoMessage(msg, SoftwareCoMusic.getPluginName());
            }
        });
    }

    public static void showMsgPrompt(String infoMsg, Color color) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
                ProjectManager pm = ProjectManager.getInstance();
                if (pm != null && pm.getOpenProjects() != null && pm.getOpenProjects().length > 0) {
                    try {
                        Project p = pm.getOpenProjects()[0];
                        final StatusBar statusBar = WindowManager.getInstance().getStatusBar(p);

                        JLabel msg = new JLabel("<html><strong>Music Time</strong><br>" + infoMsg + "</html>");
                        msg.setBorder(new EmptyBorder(2, 10, 2, 0));

                        statusBar.fireNotificationPopup(msg, color);
                    } catch(Exception e) {}
                }
                //Messages.showInfoMessage(infoMsg, pluginName);
            }
        });
    }

    public static String showMsgInputPrompt(String message, String title, Icon icon, String[] options) {
        int idx = Messages.showChooseDialog(message, title, options, options[0], icon);
        if (idx >= 0) {
            return options[idx];
        }
        return null;

        // return Messages.showEditableChooseDialog(message, title, icon, options, options[0], null);
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

    public static boolean isGitProject(String projectDir) {
        if (projectDir == null || projectDir.equals("")) {
            return false;
        }

        String gitFile = projectDir + File.separator + ".git";
        File f = new File(gitFile);
        return f.exists();
    }

    public static Date atStartOfWeek(long local_now) {
        // find out how many days to go back
        int daysBack = 0;
        Calendar cal = Calendar.getInstance();
        if (cal.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) {
            int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
            while (dayOfWeek != Calendar.SUNDAY) {
                daysBack++;
                dayOfWeek -= 1;
            }
        } else {
            daysBack = 7;
        }

        long startOfDayInSec = atStartOfDay(new Date(local_now * 1000)).toInstant().getEpochSecond();
        long startOfWeekInSec = startOfDayInSec - (DAYS_IN_SECONDS * daysBack);

        return new Date(startOfWeekInSec * 1000);
    }

    public static Date atStartOfDay(Date date) {
        LocalDateTime localDateTime = dateToLocalDateTime(date);
        LocalDateTime startOfDay = localDateTime.with(LocalTime.MIN);
        return localDateTimeToDate(startOfDay);
    }

    public static Date atEndOfDay(Date date) {
        LocalDateTime localDateTime = dateToLocalDateTime(date);
        LocalDateTime endOfDay = localDateTime.with(LocalTime.MAX);
        return localDateTimeToDate(endOfDay);
    }

    private static LocalDateTime dateToLocalDateTime(Date date) {
        return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }

    private static Date localDateTimeToDate(LocalDateTime localDateTime) {
        return Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
    }

    // the timestamps are all in seconds
    public static class TimesData {
        public Integer offset;
        public long now;
        public long local_now;
        public String timezone;
        public long local_start_day;
        public long local_start_yesterday;
        public Date local_start_of_week_date;
        public Date local_start_of_yesterday_date;
        public Date local_start_today_date;
        public long local_start_of_week;
        public long local_end_day;
        public long utc_end_day;

        public TimesData() {
            offset = ZonedDateTime.now().getOffset().getTotalSeconds();
            now = System.currentTimeMillis() / 1000;
            local_now = now + offset;
            timezone = TimeZone.getDefault().getID();
            local_start_day = atStartOfDay(new Date(local_now * 1000)).toInstant().getEpochSecond();
            local_start_yesterday = local_start_day - DAYS_IN_SECONDS;
            local_start_of_week_date = atStartOfWeek(local_now);
            local_start_of_yesterday_date = new Date(local_start_yesterday * 1000);
            local_start_today_date = new Date(local_start_day * 1000);
            local_start_of_week = local_start_of_week_date.toInstant().getEpochSecond();
            local_end_day = atEndOfDay(new Date(local_now * 1000)).toInstant().getEpochSecond();
            utc_end_day = atEndOfDay(new Date(now * 1000)).toInstant().getEpochSecond();
        }
    }

    public static String getTodayInStandardFormat() {
        SimpleDateFormat formatDay = new SimpleDateFormat("YYYY-MM-dd");
        String day = formatDay.format(new Date());
        return day;
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
        String url = Client.launch_url + "/music";
        BrowserUtil.browse(url);
    }

    public static boolean isNewDay() {
        String currentDay = FileManager.getItem("currentDay", "");
        String day = SoftwareCoUtils.getTodayInStandardFormat();
        return !day.equals(currentDay);
    }

    public static String createAnonymousUser() {
        getAppJwt();
        // make sure we've fetched the app jwt
        String jwt = FileManager.getItem("jwt");

        if (jwt != null) {
            String timezone = TimeZone.getDefault().getID();

            JsonObject payload = new JsonObject();
            payload.addProperty("username", getOsUsername());
            payload.addProperty("timezone", timezone);
            payload.addProperty("hostname", getHostname());
            payload.addProperty("creation_annotation", "NO_SESSION_FILE");

            String api = "/data/onboard";
            SoftwareResponse resp = SoftwareCoUtils.makeApiCall(api, HttpPost.METHOD_NAME, payload.toString());
            if (resp.isOk()) {
                // check if we have the data and jwt
                // resp.data.jwt and resp.data.user
                // then update the session.json for the jwt
                JsonObject data = resp.getJsonObj();
                // check if we have any data
                if (data != null && data.has("jwt")) {
                    String dataJwt = data.get("jwt").getAsString();
                    FileManager.setItem("jwt", dataJwt);
                    return dataJwt;
                }
            }
        }
        return null;
    }

}
