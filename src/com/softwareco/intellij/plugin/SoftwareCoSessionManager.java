/**
 * Copyright (c) 2018 by Software.com
 * All rights reserved
 */
package com.softwareco.intellij.plugin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.softwareco.intellij.plugin.music.MusicControlManager;
import com.softwareco.intellij.plugin.music.PlayListCommands;
import com.softwareco.intellij.plugin.music.PlayerControlManager;
import com.softwareco.intellij.plugin.music.PlaylistTreeNode;
import org.apache.http.client.methods.HttpGet;

import java.awt.event.MouseEvent;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;

public class SoftwareCoSessionManager {

    private static SoftwareCoSessionManager instance = null;
    public static final Logger log = Logger.getLogger("SoftwareCoSessionManager");
    private static Map<String, String> sessionMap = new HashMap<>();
    private static Map<String, String> musicData = new HashMap<>();
    private static long lastAppAvailableCheck = 0;

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
