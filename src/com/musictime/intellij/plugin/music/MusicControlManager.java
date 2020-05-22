package com.musictime.intellij.plugin.music;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.musictime.intellij.plugin.SoftwareCoMusic;
import com.musictime.intellij.plugin.SoftwareCoSessionManager;
import com.musictime.intellij.plugin.SoftwareCoUtils;
import com.musictime.intellij.plugin.SoftwareResponse;
import com.musictime.intellij.plugin.actions.MusicToolWindow;
import com.musictime.intellij.plugin.fs.FileManager;
import com.musictime.intellij.plugin.musicjava.*;
import org.apache.http.client.methods.HttpPut;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MusicControlManager {

    public static final Logger LOG = Logger.getLogger("MusicControlManager");

    // Spotify variables
    public static String defaultbtn = "play"; // play or pause

    public static List<String> playlistids = new ArrayList<>();
    public static String currentPlaylistId = null;
    public static Map<String, Map<String, String>> tracksByPlaylistId = new HashMap<>();
    public static String currentTrackId = null;
    public static String currentTrackName = null;
    public static List<String> spotifyDeviceIds = new ArrayList<>();
    public static Map<String, String> spotifyDevices = new HashMap<>(); // Device id and name
    public static String currentDeviceId = null;
    public static String currentDeviceName = null;
    public static String cacheDeviceName = null;

    public static int playerCounter = 0;
    public static boolean deviceActivated = false;
    public static boolean desktopDeviceActive = false;
    public static String spotifyStatus = "Not Connected"; // Connected or Not Connected
    public static String playerState = "End"; // End or Resume
    public static String playerType = "Web Player"; // Web Player or Desktop Player

    public static Map<String, String> likedTracks = new HashMap<>();
    public static Map<String, String> topTracks = new HashMap<>();
    public static Map<String, String> myAITopTracks = new HashMap<>();

    public static void resetSpotify() {
        tracksByPlaylistId.clear();
        currentTrackId = null;
        currentTrackName = null;
        playlistids.clear();
        currentPlaylistId = null;
        spotifyDeviceIds.clear();
        currentDeviceId = null;
        currentDeviceName = null;
        playerCounter = 0;
        defaultbtn = "play";
        spotifyStatus = "Not Connected";
        playerState = "End";
        playerType = "Web Player";
        likedTracks.clear();
        topTracks.clear();
        myAITopTracks.clear();
        PlayListCommands.counter = 0;
        MusicToolWindow.reset();
        MusicStore.setSpotifyUserId(null);
        MusicStore.setSpotifyAccountType(null);
    }

    public static void disConnectSpotify() {
        FileManager.setItem("spotify_access_token", null);
        FileManager.setItem("spotify_refresh_token", null);
        MusicStore.resetConfig();

        String api = "/auth/spotify/disconnect";
        SoftwareCoUtils.makeApiCall(api, HttpPut.METHOD_NAME, null);

        resetSpotify();
        String headPhoneIcon = "headphone.png";
        SoftwareCoUtils.setStatusLineMessage(headPhoneIcon, "Reconnect Spotify", "Reconnect Spotify");
        // refresh the tree view
        MusicToolWindow.refresh();
    }

    public static void connectSpotify() {
        spotifyDeviceIds.clear();
        currentDeviceId = null;
        currentDeviceName = null;

        if (MusicStore.SPOTIFY_CLIENT_ID == null) {
            SoftwareCoUtils.getAndUpdateClientInfo();
        }

        // Authenticate Spotify
        authSpotify();

        // Periodically check that the user has connected
        new Thread(() -> {
            try {
                Thread.sleep(10000);
                lazilyFetchSpotifyStatus(30);
            }
            catch (Exception e){
                System.err.println(e);
            }
        }).start();
    }

    public static void seedLikedSongSessions() {
        // get the liked songs
        JsonArray likedTracksList = PlayListCommands.likedTracksList;
        int batch_limit = 30;
        JsonArray batch = new JsonArray();
        if (likedTracksList != null && likedTracksList.size() > 0) {
            for (int i = 0; i < likedTracksList.size(); i++) {
                JsonObject track = likedTracksList.get(i).getAsJsonObject();
                track.addProperty("liked", true);
                track.addProperty("playlistId", "Liked Songs");
                track.addProperty("listened", 0);
                JsonObject songSession = new JsonObject();
                songSession.add("track", track);
                if (batch.size() >= batch_limit) {
                    // send it
                    sendBatchedLikedSongSessions(batch);
                    batch = new JsonArray();
                }
                batch.add(songSession);
            }
        }

        if (batch.size() > 0) {
            // send the rest
            sendBatchedLikedSongSessions(batch);
        }
    }

    private static void sendBatchedLikedSongSessions(JsonArray batch) {
        String api = "/music/session/seed";
        JsonObject payload = new JsonObject();
        payload.add("tracks", batch);
        String jsonPayload = payload.toString();
        String jwt = FileManager.getItem("jwt");

        SoftwareResponse resp = Client.makeApiCall(api, HttpPut.METHOD_NAME, jsonPayload, false);
        if (!resp.isOk()) {
            LOG.info("Error posting seed songs: " + resp.getErrorMessage());
        }
    }

    private static void authSpotify() {
        /**
         * const qryStr = `token=${encodedJwt}&mac=${mac}`;
         *     const endpoint = `${api_endpoint}/auth/spotify?${qryStr}`;
         */
        String jwt = FileManager.getItem("jwt");

        String api = Client.api_endpoint + "/auth/spotify?token=" + jwt + "&mac=" + SoftwareCoUtils.isMac();
        BrowserUtil.browse(api);
    }

    protected static void lazilyFetchSpotifyStatus(int retryCount) {
        boolean connected = SoftwareCoUtils.getMusicTimeUserStatus();

        if (!connected && retryCount > 0) {
            final int newRetryCount = retryCount - 1;
            new Thread(() -> {
                try {
                    Thread.sleep(10000);
                    lazilyFetchSpotifyStatus(newRetryCount);
                }
                catch (Exception e){
                    System.err.println(e);
                }
            }).start();
        } else if (connected) {
            spotifyStatus = "Connected";
            SoftwareCoUtils.showInfoMessage("Successfully connected to Spotify, please wait while we load your playlists.");
            // get the spotify user profile
            Apis.getUserProfile();

            // fetch the user playlists (ai and top 40)
            PlaylistManager.getUserPlaylists(); // API call

            PlayListCommands.updatePlaylists(0, null);

            // fetch the liked songs (type 3 = liked songs)
            PlayListCommands.updatePlaylists(3, null);
            // send the liked songs to the app to seed
            MusicControlManager.seedLikedSongSessions();

            // get genres to show in the options
            PlayListCommands.getGenre(); // API call
            PlayListCommands.updateRecommendation("category", "Familiar"); // API call
            MusicControlManager.getSpotifyDevices(); // API call

            SoftwareCoUtils.updatePlayerControls(false);
        }
    }

    public static synchronized boolean launchPlayer(boolean skipPopup, boolean activateDevice) {

        if (MusicControlManager.spotifyDeviceIds != null && MusicControlManager.spotifyDeviceIds.size() > 0) {
            return true;
        }

        String userStatus = MusicStore.getSpotifyAccountType();
        if(!skipPopup) {
            boolean webPlayer = false;
            boolean desktopPlayer = false;
            int size = 0;
            List<String> devices = new ArrayList<>();
            MusicControlManager.getSpotifyDevices(); // API call
            for(String id : spotifyDeviceIds) {
                String deviceName = spotifyDevices.get(id);
                if(id.equals(currentDeviceId))
                    deviceName += "-(active)";
                else
                    deviceName += "-(inactive)";
                if(deviceName.contains("Web Player")) {
                    webPlayer = true;
                } else {
                    desktopPlayer = true;
                }
                devices.add(deviceName);
            }
            if(!webPlayer && userStatus != null && userStatus.equals("premium")) {
                devices.add(0, "Launch web player");
                size++;
            }
            if(!desktopPlayer) {
                devices.add(0, "Launch Spotify desktop");
                size++;
            }
            if(!activateDevice) {
                String[] deviceList = new String[devices.size()];
                for (int i = 0; i < devices.size(); i++) {
                    deviceList[i] = devices.get(i);
                }
                Icon spotifyIcon = IconLoader.getIcon("/com/musictime/intellij/plugin/assets/spotify.png");
                int index = SoftwareCoUtils.showMsgInputPrompt("Connect to a Spotify device", "Spotify", spotifyIcon, deviceList);
                if (index >= 0) {
                    if (size > 0 && index < size) {
                        if (deviceList[index].equals("Launch web player")) {
                            launchWebPlayer(false);
                            return true;
                        } else if (deviceList[index].equals("Launch Spotify desktop")) {
                            launchDesktopPlayer(false);
                            return true;
                        }
                    } else {
                        String deviceId = spotifyDeviceIds.get(index - size);
                        String deviceName = spotifyDevices.get(deviceId);
                        boolean isActivated = activateDevice(deviceId);
                        if(isActivated) {
                            if (deviceName.contains("Web Player")) {
                                playerType = "Web Player";
                                MusicControlManager.currentDeviceId = deviceId;
                                MusicControlManager.currentDeviceName = deviceName;
                                return true;
                            } else {
                                playerType = "Desktop Player";
                                MusicControlManager.currentDeviceId = deviceId;
                                MusicControlManager.currentDeviceName = deviceName;
                                return true;
                            }
                        } else if(userStatus != null && !userStatus.equals("premium")) {
                            if (deviceName.contains("Web Player")) {
                                SoftwareCoUtils.showMsgPrompt("Unable to switch on " + deviceName + "<br> only desktop app allowed for non-premium.", new Color(120, 23, 50, 100));
                            } else {
                                SoftwareCoUtils.showMsgPrompt("Please close your web player before switching<br> to the desktop player", new Color(120, 23, 50, 100));
                            }
                            return false;
                        }
                    }
                } else {
                    return false;
                }
            } else {
                if(spotifyDeviceIds.size() == 0) {
                    if(userStatus != null && userStatus.equals("premium")) {
                        String infoMsg = "Music Time requires a running Spotify player. \n" +
                                "Choose a player to launch.";
                        String[] options = new String[] {"Web player", "Desktop player"};
                        int response = Messages.showDialog(infoMsg, SoftwareCoMusic.getPluginName(), options, 0, Messages.getInformationIcon());
                        if (response == 0) {
                            launchWebPlayer(true);
                            return true;
                        } else if (response == 1) {
                            launchDesktopPlayer(true);
                            return true;
                        } else {
                            return false;
                        }
                    } else {
                        launchDesktopPlayer(true);
                        return true;
                    }
                }
            }
        }

        if(playerType.equals("Web Player")) {
            launchWebPlayer(activateDevice);
            return true;
        } else if(playerType.equals("Desktop Player")) {
            launchDesktopPlayer(activateDevice);
            return true;
        }
        return false;
    }

    public static void launchWebPlayer(boolean activateDevice) {
        if (currentPlaylistId != null && currentPlaylistId.length() > 5) {
            BrowserUtil.browse("https://open.spotify.com/playlist/" + currentPlaylistId);
        } else if (currentTrackId != null) {
            BrowserUtil.browse("https://open.spotify.com/track/" + currentTrackId);
        } else {
            BrowserUtil.browse("https://open.spotify.com");
        }
        lazyUpdateDevices(5, activateDevice, true);
    }

    public static void launchDesktopPlayer(boolean activateDevice) {
        boolean spotifyState;
        if(SoftwareCoUtils.isWindows()) {
            spotifyState = Apis.isSpotifyInstalled();
        } else {
            spotifyState = true;
        }

        desktopDeviceActive = false;
        if(spotifyState) {
            Apis.startDesktopPlayer("Spotify");
            if(SoftwareCoUtils.isMac())
                lazilyCheckDesktopPlayer(3, activateDevice);
            else
                lazyUpdateDevices(10, activateDevice, false);
        } else {
            lazilyCheckDesktopPlayer(0, activateDevice);
        }
    }

    public static void lazilyCheckDesktopPlayer(int retryCount, boolean activateDevice) {
        if(!desktopDeviceActive && retryCount > 0) {
            final int newRetryCount = retryCount - 1;
            // Check devices for every 3 second
            new Thread(() -> {
                try {
                    Thread.sleep(3000);
                    if(SoftwareCoUtils.isMac() && SoftwareCoUtils.isSpotifyRunning()) {
                        desktopDeviceActive = true;
                        lazyUpdateDevices(3, activateDevice, false);
                    } else if(SoftwareCoUtils.isWindows()) {
                        MusicControlManager.getSpotifyDevices(); // API call
                        boolean desktopPlayer = false;
                        for(String id : spotifyDeviceIds) {
                            String deviceName = spotifyDevices.get(id);
                            if(!deviceName.contains("Web Player")) {
                                desktopPlayer = true;
                            }
                        }
                        if(desktopPlayer) {
                            desktopDeviceActive = true;
                            lazyUpdateDevices(3, activateDevice, false);
                        } else {
                            lazilyCheckDesktopPlayer(newRetryCount, activateDevice);
                        }
                    } else {
                        lazilyCheckDesktopPlayer(newRetryCount, activateDevice);
                    }
                }
                catch (Exception e){
                    System.err.println(e);
                }
            }).start();
        } else if(!desktopDeviceActive) {
            SoftwareCoUtils.showMsgPrompt("Desktop player is not available", new Color(120, 23, 50, 100));
            new Thread(() -> {
                try {
                    Thread.sleep(3000);
                    launchWebPlayer(activateDevice);
                }
                catch (Exception e){
                    System.err.println(e);
                }
            }).start();
        }
    }

    // Lazily update devices
    public static void lazyUpdateDevices(int retryCount, boolean activateDevice, boolean isWeb) {
        if (!hasSpotifyAccess()) {
            return;
        }

        MusicControlManager.getSpotifyDevices(); // API call
        if(activateDevice && !deviceActivated) {
            for(String id : spotifyDeviceIds) {
                String deviceName = spotifyDevices.get(id);
                if(isWeb) {
                    if(deviceName.contains("Web Player")) {
                        currentDeviceId = id;
                        currentDeviceName = deviceName;
                        playerType = "Web Player";
                        deviceActivated = true;
                        break;
                    }
                } else {
                    if(!deviceName.contains("Web Player")) {
                        currentDeviceId = id;
                        currentDeviceName = deviceName;
                        playerType = "Desktop Player";
                        deviceActivated = true;
                        break;
                    }
                }
            }
        }

        if (!deviceActivated && retryCount > 0) {
            final int newRetryCount = retryCount - 1;
            // Update devices for every 3 second
            new Thread(() -> {
                try {
                    Thread.sleep(3000);
                    lazyUpdateDevices(newRetryCount, activateDevice, isWeb);
                }
                catch (Exception e){
                    System.err.println(e);
                }
            }).start();
        }
    }

    public static boolean hasSpotifyAccess() {
        String accessToken = FileManager.getItem("spotify_access_token");
        return accessToken != null ? true : false;
    }

    private static void getMusicTimeUserStatus() {

    }

    public static void refreshAccessToken() {
        SoftwareResponse resp = (SoftwareResponse) Apis.refreshAccessToken();
    }

    public static JsonObject getSpotifyDevices() {
        SoftwareResponse resp = (SoftwareResponse) Apis.getSpotifyDevices();
        if (resp != null && resp.isOk()) {
            JsonObject obj = resp.getJsonObj();
            if (obj != null && obj.has("devices")) {
                spotifyDeviceIds.clear();
                spotifyDevices.clear();
                for(JsonElement array : obj.get("devices").getAsJsonArray()) {
                    JsonObject device = array.getAsJsonObject();
                    if(device.get("type").getAsString().equals("Computer")) {
                        spotifyDeviceIds.add(device.get("id").getAsString());
                        spotifyDevices.put(device.get("id").getAsString(), device.get("name").getAsString());
                        if (device.get("is_active").getAsBoolean()) {
                            currentDeviceId = device.get("id").getAsString();
                            currentDeviceName = device.get("name").getAsString();
                            if (currentDeviceName.contains("Web Player"))
                                playerType = "Web Player";
                            else
                                playerType = "Desktop Player";
                        }
                    }
                }
                if(spotifyDeviceIds.size() == 0) {
                    currentDeviceId = null;
                    currentDeviceName = null;
                    cacheDeviceName = null;
                } else if(spotifyDeviceIds.size() == 1) {
                    currentDeviceId = spotifyDeviceIds.get(0);
                    cacheDeviceName = spotifyDevices.get(currentDeviceId);
                    if (cacheDeviceName.contains("Web Player"))
                        playerType = "Web Player";
                    else
                        playerType = "Desktop Player";
                } else if(!spotifyDeviceIds.contains(currentDeviceId)) {
                    currentDeviceId = null;
                    currentDeviceName = null;
                    cacheDeviceName = null;
                }

                if(currentDeviceName != null && !currentDeviceName.equals(cacheDeviceName)) {
                    cacheDeviceName = currentDeviceName;
                    PlayListCommands.updatePlaylists(5, null);
                }
            } else {
                LOG.log(Level.INFO, "Music Time: No Device Found, null response");
            }
            return obj;
        } else {
            spotifyDeviceIds.clear();
            spotifyDevices.clear();
            currentDeviceId = null;
            currentDeviceName = null;
            cacheDeviceName = null;
        }
        return null;
    }

    public static boolean activateDevice(String deviceId) {
        return Apis.activateDevice(deviceId);
    }

    public static boolean requiresSpotifyAccessTokenRefresh(JsonObject resp) {
        if (resp != null && resp.has("error")) {
            JsonObject error = resp.get("error").getAsJsonObject();
            if (error.has("status")) {
                int statusCode = error.get("status").getAsInt();
                if (statusCode == 401) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void changeCurrentTrack(boolean hasNext) {
        if(currentPlaylistId == null || currentTrackId == null)
            return;
        int currentIndex = 0;
        if(currentPlaylistId.equals(PlayListCommands.topSpotifyPlaylistId)) {
            JsonObject obj = PlayListCommands.topSpotifyTracks;
            if (obj != null && obj.has("items")) {
                JsonArray items = obj.getAsJsonArray("items");
                for (JsonElement array : items) {
                    JsonObject track = array.getAsJsonObject().getAsJsonObject("track");
                    if(track.get("id").getAsString().equals(currentTrackId)) {
                        break;
                    }
                    currentIndex++;
                }
                if(hasNext) {
                    if(currentIndex < items.size()) {
                        JsonObject track = items.get(currentIndex + 1).getAsJsonObject().getAsJsonObject("track");
                        currentTrackId = track.get("id").getAsString();
                        currentTrackName = track.get("name").getAsString();
                    }
                } else if(currentIndex > 0){
                    JsonObject track = items.get(currentIndex - 1).getAsJsonObject().getAsJsonObject("track");
                    currentTrackId = track.get("id").getAsString();
                    currentTrackName = track.get("name").getAsString();
                }
            }
        } else if(currentPlaylistId.equals(PlayListCommands.myAIPlaylistId)) {
            JsonObject obj = PlayListCommands.myAITopTracks;
            if (obj != null && obj.has("tracks")) {
                JsonArray items = obj.getAsJsonObject("tracks").getAsJsonArray("items");
                for (JsonElement array : items) {
                    JsonObject track = array.getAsJsonObject().getAsJsonObject("track");
                    if(track.get("id").getAsString().equals(currentTrackId)) {
                        break;
                    }
                    currentIndex++;
                }
                if(hasNext) {
                    if(currentIndex < items.size()) {
                        JsonObject track = items.get(currentIndex + 1).getAsJsonObject().getAsJsonObject("track");
                        currentTrackId = track.get("id").getAsString();
                        currentTrackName = track.get("name").getAsString();
                    }
                } else if(currentIndex > 0){
                    JsonObject track = items.get(currentIndex - 1).getAsJsonObject().getAsJsonObject("track");
                    currentTrackId = track.get("id").getAsString();
                    currentTrackName = track.get("name").getAsString();
                }
            }
        } else if(currentPlaylistId.equals(PlayListCommands.likedPlaylistId)) {
            JsonObject obj = PlayListCommands.likedTracks;
            if (obj != null && obj.has("items")) {
                JsonArray items = obj.getAsJsonArray("items");
                for (JsonElement array : items) {
                    JsonObject track = array.getAsJsonObject().getAsJsonObject("track");
                    if(track.get("id").getAsString().equals(currentTrackId)) {
                        break;
                    }
                    currentIndex++;
                }
                if(hasNext) {
                    if(currentIndex < items.size()) {
                        JsonObject track = items.get(currentIndex + 1).getAsJsonObject().getAsJsonObject("track");
                        currentTrackId = track.get("id").getAsString();
                        currentTrackName = track.get("name").getAsString();
                    }
                } else if(currentIndex > 0){
                    JsonObject track = items.get(currentIndex - 1).getAsJsonObject().getAsJsonObject("track");
                    currentTrackId = track.get("id").getAsString();
                    currentTrackName = track.get("name").getAsString();
                }
            }
        } else if(currentPlaylistId.equals(PlayListCommands.recommendedPlaylistId)) {
            JsonObject obj = PlayListCommands.currentRecommendedTracks;
            if (obj != null && obj.has("tracks") && obj.getAsJsonArray("tracks").size() > 0
                    && obj.getAsJsonArray("tracks").size() == 50) {
                JsonArray items = obj.getAsJsonArray("tracks");
                for (JsonElement array : items) {
                    JsonObject track = array.getAsJsonObject();
                    if(track.get("id").getAsString().equals(currentTrackId)) {
                        break;
                    }
                    currentIndex++;
                }
                if(hasNext) {
                    if(currentIndex < items.size()) {
                        JsonObject track = items.get(currentIndex + 1).getAsJsonObject();
                        currentTrackId = track.get("id").getAsString();
                        currentTrackName = track.get("name").getAsString();
                    }
                } else if(currentIndex > 0){
                    JsonObject track = items.get(currentIndex - 1).getAsJsonObject();
                    currentTrackId = track.get("id").getAsString();
                    currentTrackName = track.get("name").getAsString();
                }
            }
        } else if(PlayListCommands.userPlaylistIds.contains(currentPlaylistId)) {
            JsonObject obj = PlayListCommands.userTracks.get(currentPlaylistId);
            if (obj != null && obj.has("tracks")) {
                JsonArray items = obj.getAsJsonObject("tracks").getAsJsonArray("items");
                for (JsonElement array : items) {
                    JsonObject track = array.getAsJsonObject().getAsJsonObject("track");
                    if(track.get("id").getAsString().equals(currentTrackId)) {
                        break;
                    }
                    currentIndex++;
                }
                if(hasNext) {
                    if(currentIndex < items.size()) {
                        JsonObject track = items.get(currentIndex + 1).getAsJsonObject().getAsJsonObject("track");
                        currentTrackId = track.get("id").getAsString();
                        currentTrackName = track.get("name").getAsString();
                    }
                } else if(currentIndex > 0){
                    JsonObject track = items.get(currentIndex - 1).getAsJsonObject().getAsJsonObject("track");
                    currentTrackId = track.get("id").getAsString();
                    currentTrackName = track.get("name").getAsString();
                }
            }
        }
    }
}
