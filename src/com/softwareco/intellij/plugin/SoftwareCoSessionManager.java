/**
 * Copyright (c) 2018 by Software.com
 * All rights reserved
 */
package com.softwareco.intellij.plugin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.softwareco.intellij.plugin.music.MusicControlManager;
import com.softwareco.intellij.plugin.music.PlayListCommands;
import com.softwareco.intellij.plugin.music.PlayerControlManager;
import com.softwareco.intellij.plugin.music.PlaylistTreeNode;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;

import java.awt.event.MouseEvent;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Logger;

public class SoftwareCoSessionManager {

    private static SoftwareCoSessionManager instance = null;
    public static final Logger log = Logger.getLogger("SoftwareCoSessionManager");
    private static Map<String, String> sessionMap = new HashMap<>();
    private static Map<String, String> musicData = new HashMap<>();
    private static long lastAppAvailableCheck = 0;

    private static String SERVICE_NOT_AVAIL =
            "Our service is temporarily unavailable.\n\nPlease try again later.\n";

    public static SoftwareCoSessionManager getInstance() {
        if (instance == null) {
            instance = new SoftwareCoSessionManager();
        }
        return instance;
    }

    public static boolean softwareSessionFileExists() {
        // don't auto create the file
        String file = getSoftwareSessionFile(false);
        // check if it exists
        File f = new File(file);
        return f.exists();
    }

    public static boolean musicDataFileExists() {
        // don't auto create the file
        String file = getMusicDataFile(false);
        // check if it exists
        File f = new File(file);
        return f.exists();
    }

    public static boolean jwtExists() {
        String jwt = getItem("jwt");
        return (jwt != null && !jwt.equals("")) ? true : false;
    }

    public static String getSoftwareDir(boolean autoCreate) {
        String softwareDataDir = SoftwareCoUtils.getUserHomeDir();
        if (SoftwareCoUtils.isWindows()) {
            softwareDataDir += "\\.software";
        } else {
            softwareDataDir += "/.software";
        }

        File f = new File(softwareDataDir);
        if (!f.exists()) {
            // make the directory
            f.mkdirs();
        }

        return softwareDataDir;
    }

    public static String getSoftwareSessionFile(boolean autoCreate) {
        String file = getSoftwareDir(autoCreate);
        if (SoftwareCoUtils.isWindows()) {
            file += "\\session.json";
        } else {
            file += "/session.json";
        }
        return file;
    }

    public static String getMusicDataFile(boolean autoCreate) {
        String file = getSoftwareDir(autoCreate);
        if (SoftwareCoUtils.isWindows()) {
            file += "\\musicData.json";
        } else {
            file += "/musicData.json";
        }
        return file;
    }

    public static String getTempDataFile(boolean autoCreate) {
        String file = getSoftwareDir(autoCreate);
        if (SoftwareCoUtils.isWindows()) {
            file += "\\tempData.json";
        } else {
            file += "/tempData.json";
        }
        return file;
    }

    private static String getSongSessionDataFile() {
        String file = getSoftwareDir(true);
        if (SoftwareCoUtils.isWindows()) {
            file += "\\songSessionData.json";
        } else {
            file += "/songSessionData.json";
        }
        return file;
    }

    private static String getSoftwareDataStoreFile() {
        String file = getSoftwareDir(true);
        if (SoftwareCoUtils.isWindows()) {
            file += "\\data.json";
        } else {
            file += "/data.json";
        }
        return file;
    }

    private static String getMusicDashboardFile() {
        String file = getSoftwareDir(true);
        if (SoftwareCoUtils.isWindows()) {
            file += "\\musicTime.html";
        } else {
            file += "/musicTime.html";
        }
        return file;
    }

    public static Project getOpenProject() {
        ProjectManager projMgr = ProjectManager.getInstance();
        Project[] projects = projMgr.getOpenProjects();
        if (projects != null && projects.length > 0) {
            return projects[0];
        }
        return null;
    }

    public synchronized static boolean isServerOnline() {
        long nowInSec = Math.round(System.currentTimeMillis() / 1000);
        // 5 min threshold
        boolean pastThreshold = (nowInSec - lastAppAvailableCheck > (60 * 5)) ? true : false;
        if (pastThreshold) {
            SoftwareResponse resp = SoftwareCoUtils.makeApiCall("/ping", HttpGet.METHOD_NAME, null);
            SoftwareCoUtils.updateServerStatus(resp.isOk());
            lastAppAvailableCheck = nowInSec;
        }
        return SoftwareCoUtils.isAppAvailable();
    }

    public void storePayload(String payload) {
        if (payload == null || payload.length() == 0) {
            return;
        }
        if (SoftwareCoUtils.isWindows()) {
            payload += "\r\n";
        } else {
            payload += "\n";
        }
        String dataStoreFile = getSoftwareDataStoreFile();
        File f = new File(dataStoreFile);
        try {
            log.info("Music Time: Storing kpm metrics: " + payload);
            Writer output;
            output = new BufferedWriter(new FileWriter(f, true));  //clears file every time
            output.append(payload);
            output.close();
        } catch (Exception e) {
            log.warning("Music Time: Error appending to the Software data store file, error: " + e.getMessage());
        }
    }

    public void sendOfflineData() {
        final String dataStoreFile = getSoftwareDataStoreFile();
        File f = new File(dataStoreFile);

        if (f.exists()) {
            // found a data file, check if there's content
            StringBuffer sb = new StringBuffer();
            try {
                FileInputStream fis = new FileInputStream(f);

                //Construct BufferedReader from InputStreamReader
                BufferedReader br = new BufferedReader(new InputStreamReader(fis));

                String line = null;
                while ((line = br.readLine()) != null) {
                    if (line.length() > 0) {
                        sb.append(line).append(",");
                    }
                }

                br.close();

                if (sb.length() > 0) {
                    // we have data to send
                    String payloads = sb.toString();
                    payloads = payloads.substring(0, payloads.lastIndexOf(","));
                    payloads = "[" + payloads + "]";

                    JsonArray jsonArray = (JsonArray) SoftwareCoMusic.jsonParser.parse(payloads);

                    // delete the file
                    this.deleteFile(dataStoreFile);

                    JsonArray batch = new JsonArray();
                    // go through the array about 50 at a time
                    for (int i = 0; i < jsonArray.size(); i++) {
                        batch.add(jsonArray.get(i));
                        if (i > 0 && i % 50 == 0) {
                            String payloadData = SoftwareCoMusic.gson.toJson(batch);
                            SoftwareResponse resp =
                                    SoftwareCoUtils.makeApiCall("/data/batch", HttpPost.METHOD_NAME, payloadData);
                            if (!resp.isOk()) {
                                // add these back to the offline file
                                log.info("Music Time: Unable to send batch data: " + resp.getErrorMessage());
                            }
                            batch = new JsonArray();
                        }
                    }
                    if (batch.size() > 0) {
                        String payloadData = SoftwareCoMusic.gson.toJson(batch);
                        SoftwareResponse resp =
                                SoftwareCoUtils.makeApiCall("/data/batch", HttpPost.METHOD_NAME, payloadData);
                        if (!resp.isOk()) {
                            // add these back to the offline file
                            log.info("Music Time: Unable to send batch data: " + resp.getErrorMessage());
                        }
                    }

                } else {
                    log.info("Music Time: No offline data to send");
                }
            } catch (Exception e) {
                log.warning("Music Time: Error trying to read and send offline data, error: " + e.getMessage());
            }
        }

        //SoftwareCoOfflineManager.getInstance().clearSessionSummaryData();
        // fetch kpm metrics
//        new Thread(() -> {
//            try {
//                Thread.sleep(10000);
//                fetchDailyKpmSessionInfo();
//            } catch (Exception e) {
//                System.err.println(e);
//            }
//        }).start();
    }

    public void deleteFile(String file) {
        File f = new File(file);
        // if the file exists, delete it
        if (f.exists()) {
            f.delete();
        }
    }

    public static void setItem(String key, String val) {
        sessionMap.put(key, val);
        JsonObject jsonObj = getSoftwareSessionAsJson();
        jsonObj.addProperty(key, val);

        String content = jsonObj.toString();

        String sessionFile = getSoftwareSessionFile(true);

        try {
            Writer output = new BufferedWriter(new FileWriter(sessionFile));
            output.write(content);
            output.close();
        } catch (Exception e) {
            log.warning("Music Time: Failed to write the key value pair (" + key + ", " + val + ") into the session, error: " + e.getMessage());
        }
    }

    public static String getItem(String key) {
        String val = sessionMap.get(key);
        if (val != null) {
            return val;
        }
        JsonObject jsonObj = getSoftwareSessionAsJson();
        if (jsonObj != null && jsonObj.has(key) && !jsonObj.get(key).isJsonNull()) {
            return jsonObj.get(key).getAsString();
        }
        return null;
    }

    public static void setMusicData(String key, String val) {
        musicData.put(key, val);
        JsonObject jsonObj = getMusicDataAsJson();
        jsonObj.addProperty(key, val);

        String content = jsonObj.toString();

        String sessionFile = getMusicDataFile(true);

        try {
            Writer output = new BufferedWriter(new FileWriter(sessionFile));
            output.write(content);
            output.close();
        } catch (Exception e) {
            log.warning("Music Time: Failed to write the key value pair (" + key + ", " + val + ") into the music data, error: " + e.getMessage());
        }
    }

    public static String getMusicData(String key) {
        String val = musicData.get(key);
        if (val != null) {
            return val;
        }
        JsonObject jsonObj = getMusicDataAsJson();
        if (jsonObj != null && jsonObj.has(key) && !jsonObj.get(key).isJsonNull()) {
            return jsonObj.get(key).getAsString();
        }
        return null;
    }

    public static void setTempData(String key, String val) {
        musicData.put(key, val);
        JsonObject jsonObj = getTempDataAsJson();
        jsonObj.addProperty(key, val);

        String content = jsonObj.toString();

        String sessionFile = getTempDataFile(true);

        try {
            Writer output = new BufferedWriter(new FileWriter(sessionFile));
            output.write(content);
            output.close();
        } catch (Exception e) {
            log.warning("Music Time: Failed to write the key value pair (" + key + ", " + val + ") into the music data, error: " + e.getMessage());
        }
    }

    public static String getTempData(String key) {
        String val = musicData.get(key);
        if (val != null) {
            return val;
        }
        JsonObject jsonObj = getTempDataAsJson();
        if (jsonObj != null && jsonObj.has(key) && !jsonObj.get(key).isJsonNull()) {
            return jsonObj.get(key).getAsString();
        }
        return null;
    }

    private static JsonObject getSoftwareSessionAsJson() {
        JsonObject data = null;

        String sessionFile = getSoftwareSessionFile(true);
        File f = new File(sessionFile);
        if (f.exists()) {
            try {
                byte[] encoded = Files.readAllBytes(Paths.get(sessionFile));
                String content = new String(encoded, Charset.defaultCharset());
                if (content != null) {
                    // json parse it
                    data = SoftwareCoMusic.jsonParser.parse(content).getAsJsonObject();
                }
            } catch (Exception e) {
                log.warning("Music Time: Error trying to read and json parse the session file, error: " + e.getMessage());
            }
        }
        return (data == null) ? new JsonObject() : data;
    }

    private static JsonObject getMusicDataAsJson() {
        JsonObject data = null;

        String sessionFile = getMusicDataFile(true);
        File f = new File(sessionFile);
        if (f.exists()) {
            try {
                byte[] encoded = Files.readAllBytes(Paths.get(sessionFile));
                String content = new String(encoded, Charset.defaultCharset());
                if (content != null) {
                    // json parse it
                    data = SoftwareCoMusic.jsonParser.parse(content).getAsJsonObject();
                }
            } catch (Exception e) {
                log.warning("Music Time: Error trying to read and json parse the music data file, error: " + e.getMessage());
            }
        }
        return (data == null) ? new JsonObject() : data;
    }

    private static JsonObject getTempDataAsJson() {
        JsonObject data = null;

        String sessionFile = getTempDataFile(true);
        File f = new File(sessionFile);
        if (f.exists()) {
            try {
                byte[] encoded = Files.readAllBytes(Paths.get(sessionFile));
                String content = new String(encoded, Charset.defaultCharset());
                if (content != null) {
                    // json parse it
                    data = SoftwareCoMusic.jsonParser.parse(content).getAsJsonObject();
                }
            } catch (Exception e) {
                log.warning("Music Time: Error trying to read and json parse the music data file, error: " + e.getMessage());
            }
        }
        return (data == null) ? new JsonObject() : data;
    }

    public static void fetchMusicTimeMetricsDashboard(String plugin, boolean isHtml) {
        boolean isOnline = isServerOnline();
        String dashboardFile = SoftwareCoSessionManager.getMusicDashboardFile();
        String jwt = SoftwareCoSessionManager.getItem("jwt");

        Writer writer = null;

        if (isOnline) {
            String api = "/dashboard?plugin=" + plugin + "&linux=" + SoftwareCoUtils.isLinux() + "&html=" + isHtml;
            SoftwareResponse response = SoftwareCoUtils.makeApiCall(api, HttpGet.METHOD_NAME, null, jwt);
            if(response.isOk()) {
                String dashboardSummary = response.getJsonStr();
                if (dashboardSummary == null || dashboardSummary.trim().isEmpty()) {
                    dashboardSummary = SERVICE_NOT_AVAIL;
                }

                // write the dashboard summary content
                try {
                    writer = new BufferedWriter(new OutputStreamWriter(
                            new FileOutputStream(dashboardFile), StandardCharsets.UTF_8));
                    writer.write(dashboardSummary);
                } catch (IOException ex) {
                    // Report
                } finally {
                    try {
                        writer.close();
                    } catch (Exception ex) {/*ignore*/}
                }
            }
        }
    }

    public static void launchMusicTimeMetricsDashboard() {
        if (!SoftwareCoSessionManager.isServerOnline()) {
            SoftwareCoUtils.showOfflinePrompt(false);
        }
        Project p = getOpenProject();
        if (p == null) {
            return;
        }

        fetchMusicTimeMetricsDashboard("music-time", true);

        String musicTimeFile = SoftwareCoSessionManager.getMusicDashboardFile();
        File f = new File(musicTimeFile);

        VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(f);
        OpenFileDescriptor descriptor = new OpenFileDescriptor(p, vFile);
        FileEditorManager.getInstance(p).openTextEditor(descriptor, true);
    }

    private Project getCurrentProject() {
        Project project = null;
        Editor[] editors = EditorFactory.getInstance().getAllEditors();
        if (editors != null && editors.length > 0) {
            project = editors[0].getProject();
        }
        return project;
    }

    public void showLoginPrompt() {
        boolean isOnline = isServerOnline();

        if (isOnline) {

            String msg = "To see your coding data in Code Time, please log in your account.";
            Project project = this.getCurrentProject();

            ApplicationManager.getApplication().invokeLater(new Runnable() {
                public void run() {
                    // ask to download the PM
                    int options = Messages.showDialog(
                            project,
                            msg,
                            "Software", new String[]{"Log in", "Not now"},
                            0, Messages.getInformationIcon());
                    if (options == 0) {
                        launchLogin();
                    }
                }
            });
        }
    }

    public static String generateToken() {
        String uuid = UUID.randomUUID().toString();
        return uuid.replace("-", "");
    }

    public void statusBarClickHandler(MouseEvent mouseEvent, String id) {
        if (SoftwareCoSessionManager.isServerOnline()) {
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

            log.warning("Music Time: Button ID: " + id);
            if(id.equals(headphoneiconId) || id.equals(connectspotifyId)) {
                MusicControlManager.connectSpotify();
            } else if(id.equals(playiconId)) {
                MusicControlManager.playerCounter = 0;
                PlayerControlManager.playSpotifyDevices();
            } else if(id.equals(pauseiconId)) {
                MusicControlManager.playerCounter = 0;
                PlayerControlManager.pauseSpotifyDevices();
            } else if(id.equals(preiconId)) {
                MusicControlManager.playerCounter = 0;
                if(MusicControlManager.currentPlaylistId != null && MusicControlManager.currentPlaylistId.length() > 5) {
                    PlayerControlManager.previousSpotifyTrack();
                } else if(MusicControlManager.currentPlaylistId != null) {
                    List<String> tracks = new ArrayList<>();
                    if(MusicControlManager.currentPlaylistId.equals("1")) {
                        JsonObject obj = PlayListCommands.topSpotifyTracks;
                        if (obj != null && obj.has("items")) {
                            for(JsonElement array : obj.get("items").getAsJsonArray()) {
                                JsonObject track = array.getAsJsonObject();
                                tracks.add(track.get("id").getAsString());
                            }
                        }
                    } else if(MusicControlManager.currentPlaylistId.equals("2")) {
                        JsonObject obj = PlayListCommands.likedTracks;
                        if (obj != null && obj.has("items")) {
                            for(JsonElement array : obj.get("items").getAsJsonArray()) {
                                JsonObject track = array.getAsJsonObject().getAsJsonObject("track");
                                tracks.add(track.get("id").getAsString());
                            }
                        }
                    }
                    int index = tracks.indexOf(MusicControlManager.currentTrackId);
                    if(index > 0) {
                        MusicControlManager.currentTrackId = tracks.get(index - 1);
                        PlayerControlManager.playSpotifyPlaylist(null, null);
                    } else {
                        MusicControlManager.currentTrackId = tracks.get(0);
                        PlayerControlManager.playSpotifyPlaylist(null, null);
                    }
                }
            } else if(id.equals(nexticonId)) {
                MusicControlManager.playerCounter = 0;
                if(MusicControlManager.currentPlaylistId != null && MusicControlManager.currentPlaylistId.length() > 5) {
                    PlayerControlManager.nextSpotifyTrack();
                } else if(MusicControlManager.currentPlaylistId != null) {
                    List<String> tracks = new ArrayList<>();
                    if(MusicControlManager.currentPlaylistId.equals("1")) {
                        JsonObject obj = PlayListCommands.topSpotifyTracks;
                        if (obj != null && obj.has("items")) {
                            for(JsonElement array : obj.get("items").getAsJsonArray()) {
                                JsonObject track = array.getAsJsonObject();
                                tracks.add(track.get("id").getAsString());
                            }
                        }
                    } else if(MusicControlManager.currentPlaylistId.equals("2")) {
                        JsonObject obj = PlayListCommands.likedTracks;
                        if (obj != null && obj.has("items")) {
                            for(JsonElement array : obj.get("items").getAsJsonArray()) {
                                JsonObject track = array.getAsJsonObject().getAsJsonObject("track");
                                tracks.add(track.get("id").getAsString());
                            }
                        }
                    }
                    int index = tracks.indexOf(MusicControlManager.currentTrackId);
                    if(index < (tracks.size() - 1)) {
                        MusicControlManager.currentTrackId = tracks.get(index + 1);
                        PlayerControlManager.playSpotifyPlaylist(null, null);
                    } else {
                        MusicControlManager.currentTrackId = tracks.get(0);
                        PlayerControlManager.playSpotifyPlaylist(null, null);
                    }
                }
            } else if(id.equals(songtrackId)) {
                MusicControlManager.launchPlayer();
            } else if(id.equals(unlikeiconId)) {
                PlayerControlManager.likeSpotifyTrack(true, MusicControlManager.currentTrackId);
            } else if(id.equals(likeiconId)) {
                PlayerControlManager.likeSpotifyTrack(false, MusicControlManager.currentTrackId);
            }
        } else {
            SoftwareCoUtils.showOfflinePrompt(false);
        }
    }

    protected static void lazilyFetchUserStatus(int retryCount) {
        SoftwareCoUtils.UserStatus userStatus = SoftwareCoUtils.getUserStatus();

        if (!userStatus.loggedIn && retryCount > 0) {
            final int newRetryCount = retryCount - 1;
            new Thread(() -> {
                try {
                    Thread.sleep(10000);
                    lazilyFetchUserStatus(newRetryCount);
                }
                catch (Exception e){
                    System.err.println(e);
                }
            }).start();
        }
    }

    public static void launchLogin() {
        String url = SoftwareCoUtils.launch_url;
        String jwt = getItem("jwt");

        url += "/onboarding?token=" + jwt;
        BrowserUtil.browse(url);

        new Thread(() -> {
            try {
                Thread.sleep(10000);
                lazilyFetchUserStatus(12);
            }
            catch (Exception e){
                System.err.println(e);
            }
        }).start();
    }
}
