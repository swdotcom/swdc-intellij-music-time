package com.musictime.intellij.plugin.music;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.util.IconLoader;
import com.musictime.intellij.plugin.SoftwareCoUtils;
import com.musictime.intellij.plugin.tree.MusicToolWindow;
import com.musictime.intellij.plugin.models.DeviceInfo;
import com.musictime.intellij.plugin.musicjava.*;
import com.musictime.intellij.plugin.tree.PlaylistAction;
import org.apache.commons.lang.StringUtils;
import swdc.java.ops.http.OpsHttpClient;
import swdc.java.ops.manager.AccountManager;
import swdc.java.ops.manager.AsyncManager;
import swdc.java.ops.manager.FileUtilManager;
import swdc.java.ops.manager.UtilManager;
import swdc.java.ops.model.Integration;
import swdc.java.ops.model.User;
import swdc.java.ops.model.UserState;

import javax.swing.*;
import java.util.*;
import java.util.List;
import java.util.logging.Logger;

public class MusicControlManager {

    public static final Logger LOG = Logger.getLogger("MusicControlManager");

    public static List<String> playlistids = new ArrayList<>();
    public static String currentPlaylistId = null;
    public static Map<String, Map<String, String>> tracksByPlaylistId = new HashMap<>();
    public static String currentTrackId = null;
    public static String currentTrackName = null;
    public static boolean currentTrackPlaying = false;

    public static String spotifyStatus = "Not Connected"; // Connected or Not Connected
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
        currentTrackPlaying = false;
        spotifyStatus = "Not Connected";
        playerType = "Web Player";
        likedTracks.clear();
        topTracks.clear();
        myAITopTracks.clear();
        PlayListCommands.counter = 0;
        MusicToolWindow.reset();
        MusicStore.resetConfig();
        DeviceManager.clearDevices();
    }

    public static void disConnectSpotify() {

        String api = "/auth/spotify/disconnect";
        OpsHttpClient.softwarePut(api, FileUtilManager.getItem("jwt"), null);

        List<Integration> integrations = FileUtilManager.getIntegrations();
        int removeIdx = -1;
        for (int i = integrations.size() - 1; i >= 0; i--) {
            Integration n = integrations.get(i);
            if ("spotify".equals(n.name.toLowerCase()) && n.status.toLowerCase().equals("active")) {
                removeIdx = i;
                break;
            }
        }
        if (removeIdx != -1) {
            integrations.remove(removeIdx);
            FileUtilManager.syncIntegrations(integrations);
        }

        FileUtilManager.setBooleanItem("requiresSpotifyReAuth", true);

        resetSpotify();

        SoftwareCoUtils.setStatusLineMessage();

        // refresh the tree view
        MusicToolWindow.refresh();
    }

    public static void connectSpotify() {
        DeviceManager.clearDevices();

        if (MusicStore.SPOTIFY_CLIENT_ID == null) {
            SoftwareCoUtils.getAndUpdateClientInfo();
        }

        // Authenticate Spotify
        authSpotify();
    }

    private static void authSpotify() {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("plugin", SoftwareCoUtils.getPluginName());
        jsonObject.addProperty("plugin_uuid", FileUtilManager.getPluginUuid());
        jsonObject.addProperty("pluginVersion", SoftwareCoUtils.getVersion());
        jsonObject.addProperty("plugin_id", SoftwareCoUtils.pluginId);
        jsonObject.addProperty("mac", UtilManager.isMac());
        jsonObject.addProperty("plugin_token", FileUtilManager.getItem("jwt"));
        jsonObject.addProperty("auth_callback_state", FileUtilManager.getAuthCallbackState(true));

        String qryStr = SoftwareCoUtils.buildQueryString(jsonObject, true);

        String api = SoftwareCoUtils.api_endpoint + "/auth/spotify" + qryStr;
        BrowserUtil.browse(api);

        // Periodically check that the user has connected
        AsyncManager.getInstance().executeOnceInSeconds(() -> lazilyFetchSpotifyStatus(40), 10);
    }

    protected static void lazilyFetchSpotifyStatus(int retryCount) {
        UserState userState = AccountManager.getUserLoginState(true);

        // check to see if there are any active spotify integrations
        boolean foundSpotifyIntegrations = false;
        if (userState.user != null && userState.user.integrations != null) {
            Integration spotifyIntegration = userState.user.integrations.stream()
                    .filter(n -> "spotify".equals(n.name.toLowerCase()) && n.status.toLowerCase().equals("active"))
                    .findAny()
                    .orElse(null);
            foundSpotifyIntegrations = (spotifyIntegration != null) ? true : false;
        }

        if (!foundSpotifyIntegrations) {
            if (retryCount > 0) {
                final int newRetryCount = retryCount - 1;
                final Runnable service = () -> lazilyFetchSpotifyStatus(newRetryCount);
                AsyncManager.getInstance().executeOnceInSeconds(service, 10);
            } else {
                FileUtilManager.setAuthCallbackState(null);
            }
        } else {
            spotifyStatus = "Connected";
            FileUtilManager.setAuthCallbackState(null);
            SoftwareCoUtils.showInfoMessage("Successfully connected to Spotify, please wait while we load your playlists.");

            // update the spotify integration
            updateSpotifyIntegration(userState.user);

            FileUtilManager.setBooleanItem("requiresSpotifyReAuth", false);

            // get the spotify user profile
            Apis.getUserProfile();

            // fetch the user playlists (ai and top 40)
            PlaylistManager.getUserPlaylists(); // API call

            PlayListCommands.updatePlaylists(PlaylistAction.GET_ALL_PLAYLISTS, null);

            // fetch the liked songs (type 3 = liked songs)
            PlayListCommands.updatePlaylists(PlaylistAction.UPDATE_LIKED_SONGS, null);

            // get genres to show in the options
            PlayListCommands.getGenre(); // API call
            PlayListCommands.updateRecommendation("category", "Familiar"); // API call
            DeviceManager.getDevices();

            AsyncManager.getInstance().executeOnceInSeconds(() -> PlaylistManager.fetchTrack(), 3);

            // refresh the status bar
            SoftwareCoUtils.setStatusLineMessage();
        }
    }

    public static void updateSpotifyIntegration(User softwareUser) {
        List<Integration> integrations = FileUtilManager.getIntegrations();
        if (integrations == null) {
            integrations = new ArrayList<>();
        }
        if (softwareUser.integrations != null) {
            for (Integration n : softwareUser.integrations) {
                if ("spotify".equals(n.name.toLowerCase())
                        && n.status.toLowerCase().equals("active")
                        && StringUtils.isNotBlank(n.access_token)) {
                    integrations.add(n);
                    break;
                }
            }
            FileUtilManager.syncIntegrations(integrations);
        }
    }

    public static synchronized void launchPlayer() {
        DeviceInfo currentDevice = DeviceManager.getBestDeviceOption();

        if (currentDevice != null) {
            return;
        }

        displayDeviceSelection();
    }

    public static void displayDeviceSelection() {
        List<DeviceInfo> deviceInfos = DeviceManager.getDevices();
        boolean hasWebDevice = DeviceManager.hasWebDevice();
        boolean hasDesktopDevice = DeviceManager.hasDesktopDevice();

        List<String> devices = new ArrayList<>();
        for (DeviceInfo info : deviceInfos) {
            String name = info.name;
            devices.add(info.name);
        }

        String webPlayer = "Web Player";
        String desktopPlayer = "Desktop Player";
        if (!hasWebDevice) {
            devices.add(0, webPlayer);
        }
        if (!hasDesktopDevice) {
            devices.add(0, desktopPlayer);
        }

        String[] deviceList = new String[devices.size()];
        for (int i = 0; i < devices.size(); i++) {
            deviceList[i] = devices.get(i);
        }
        Icon spotifyIcon = IconLoader.getIcon("/com/musictime/intellij/plugin/assets/spotify.png");
        String deviceName = SoftwareCoUtils.showMsgInputPrompt("Connect to a Spotify device", "Spotify", spotifyIcon, deviceList);
        if (StringUtils.isNotBlank(deviceName)) {
            if (deviceName.equals(webPlayer)) {
                launchWebPlayer();
            } else if (deviceName.equals(desktopPlayer)) {
                launchDesktopPlayer();
            } else {
                // web or desktop was not selected, trying to activate
                // the selected device now

                String deviceId = "";
                String playerDescription = "";
                for (DeviceInfo info : deviceInfos) {
                    if (info.name.equals(deviceName)) {
                        deviceId = info.id;
                        playerDescription = info.playerDescription;
                        break;
                    }
                }

                boolean isActivated = Apis.activateDevice(deviceId);
                if (isActivated) {
                    if (deviceName.contains("Web Player")) {
                        playerType = playerDescription;
                    } else {
                        playerType = playerDescription;
                    }
                }
            }

            DeviceManager.refreshDevices();
        }
    }

    public static void launchWebPlayer() {
        if (currentPlaylistId != null && currentPlaylistId.equals(PlayListCommands.likedPlaylistId)) {
            BrowserUtil.browse("https://open.spotify.com/collection/tracks");
        } else if (currentPlaylistId != null) {
            BrowserUtil.browse("https://open.spotify.com/playlist/" + currentPlaylistId);
        } else if (currentTrackId != null) {
            BrowserUtil.browse("https://open.spotify.com/track/" + currentTrackId);
        } else {
            BrowserUtil.browse("https://open.spotify.com");
        }
        lazilyCheckAvailablePlayer(4);
    }

    public static void launchDesktopPlayer() {
        Util.startPlayer();

        if (UtilManager.isLinux()) {
            // try in 5 seconds
            AsyncManager.getInstance().executeOnceInSeconds(() -> lazilyCheckAvailablePlayer(6), 5);
        } else {
            lazilyCheckAvailablePlayer(4);
        }
    }

    // Lazily update devices
    public static void lazilyCheckAvailablePlayer(int retryCount) {
        if (!hasSpotifyAccess()) {
            return;
        }

        DeviceManager.refreshDevices();

        if (!DeviceManager.hasDesktopOrWebDevice() && retryCount > 0) {
            final int newRetryCount = retryCount - 1;
            // Update devices for every 3 second
            new Thread(() -> {
                try {
                    Thread.sleep(3000);
                    lazilyCheckAvailablePlayer(newRetryCount);
                }
                catch (Exception e) {
                    System.err.println(e);
                }
            }).start();
        }
    }

    public static boolean hasSpotifyAccess() {
        String accessToken = Apis.getSpotifyAccessToken();
        // has a spotify access token if its not null or empty
        return StringUtils.isNotBlank(accessToken);
    }

    public static boolean requiresReAuthentication() {
        boolean requiresReAuth = FileUtilManager.getBooleanItem("requiresSpotifyReAuth");
        if (requiresReAuth) {
            return true;
        }
        return false;
    }

    public static void refreshAccessToken() {
        Apis.refreshAccessToken();
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

    public static void migrateSpotifyAccessInfo() {
        String legacyAccessToken = FileUtilManager.getItem("spotify_access_token", null);
        if (StringUtils.isNotBlank(legacyAccessToken) && Apis.getSpotifyAccessToken() == null) {
            if (StringUtils.isNotBlank(legacyAccessToken)) {
                User softwareUser = AccountManager.getUser();
                if (softwareUser != null && softwareUser.integrations != null) {
                    FileUtilManager.setItem("spotify_access_token", null);
                    FileUtilManager.setItem("spotify_refresh_token", null);
                    // update the spotify integrations
                    Integration spotifyIntegration = softwareUser.integrations.stream()
                            .filter(n -> "spotify".equals(n.name.toLowerCase()) && n.status.toLowerCase().equals("active"))
                            .findAny()
                            .orElse(null);
                    List<Integration> integrations = FileUtilManager.getIntegrations();
                    if (integrations == null) {
                        integrations = new ArrayList<>();
                    }
                    integrations.add(spotifyIntegration);
                    FileUtilManager.syncIntegrations(integrations);
                }
            }
        }
    }
}
