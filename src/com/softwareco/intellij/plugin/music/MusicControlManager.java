package com.softwareco.intellij.plugin.music;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.softwareco.intellij.plugin.SoftwareCoSessionManager;
import com.softwareco.intellij.plugin.SoftwareCoUtils;
import com.softwareco.intellij.plugin.musicjava.SoftwareResponse;
import com.softwareco.intellij.plugin.actions.MusicToolWindow;
import com.softwareco.intellij.plugin.musicjava.Apis;
import com.softwareco.intellij.plugin.musicjava.MusicStore;
import com.softwareco.intellij.plugin.musicjava.Util;
import com.softwareco.intellij.plugin.slack.SlackControlManager;
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
    private static String CLIENT_ID = null;
    private static String CLIENT_SECRET = null;
    public static String ACCESS_TOKEN = null;
    public static String REFRESH_TOKEN = null;
    public static String userStatus = null; // premium or non-premium
    public static boolean spotifyCacheState = false;
    public static String defaultbtn = "play"; // play or pause
    public static String spotifyUserId = null;

    public static List<String> playlistids = new ArrayList<>();
    public static String currentPlaylistId = null;
    public static List<String> tracksByPlaylistId = new ArrayList<>();
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
        spotifyUserId = null;
        tracksByPlaylistId.clear();
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
        playerState = "End";
        playerType = "Web Player";
        likedTracks.clear();
        topTracks.clear();
        myAITopTracks.clear();
        PlayListCommands.counter = 0;
        MusicToolWindow.reset();
    }

    public static synchronized boolean spotifyState() {
        return spotifyCacheState;
    }

    public static void disConnectSpotify() {
        boolean serverIsOnline = Util.isServerOnline();
        if (!serverIsOnline) {
            SoftwareCoUtils.showOfflinePrompt(false);
        }

        String api = "/auth/spotify/disconnect";
        String jwt = SoftwareCoSessionManager.getItem("jwt");
        com.softwareco.intellij.plugin.SoftwareResponse resp = SoftwareCoUtils.makeApiCall(api, HttpPut.METHOD_NAME, null, jwt);
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
                    MusicStore.resetConfig();
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
            SoftwareCoUtils.showOfflinePrompt(true);
        }
        spotifyUserId = null;
        spotifyDeviceIds.clear();
        currentDeviceId = null;
        currentDeviceName = null;

        JsonObject userObj = SoftwareCoUtils.getClientInfo(serverIsOnline);
        if(userObj != null) {
            CLIENT_ID = userObj.get("clientId").getAsString();
            CLIENT_SECRET = userObj.get("clientSecret").getAsString();
        }

        // Authenticate Spotify
        authSpotify(serverIsOnline);

        // Periodically check that the user has connected
        lazilyFetchSpotifyStatus(20);
    }

    private static void authSpotify(boolean serverIsOnline) {
        if (serverIsOnline) {
            String api = "https://api.software.com/auth/spotify?token=" + SoftwareCoUtils.jwt + "&mac=" + SoftwareCoUtils.isMac();
            BrowserUtil.browse(api);
        }
    }

    protected static void lazilyFetchSpotifyStatus(int retryCount) {
        boolean serverIsOnline = SoftwareCoSessionManager.isServerOnline();
        MusicControlManager.spotifyCacheState = isSpotifyConncted(serverIsOnline);

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

            PlaylistManager.getUserPlaylists(); // API call
            PlayListCommands.updatePlaylists(3, null);
            PlayListCommands.getGenre(); // API call
            PlayListCommands.updateRecommendation("category", "Familiar"); // API call
            MusicControlManager.getSpotifyDevices(); // API call

            lazyUpdatePlayer();
        }
    }

    public static synchronized boolean launchPlayer(boolean skipPopup, boolean activateDevice) {

        if(userStatus == null) {
            JsonObject obj = getUserProfile();
            if (obj != null)
                userStatus = obj.get("product").getAsString();
        }

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
                Icon spotifyIcon = IconLoader.getIcon("/com/softwareco/intellij/plugin/assets/spotify.png");
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
                        activateDevice(deviceId);
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
                        int response = Messages.showDialog(infoMsg, SoftwareCoUtils.pluginName, options, 0, Messages.getInformationIcon());
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

    // Lazily update player controls
    public static void lazyUpdatePlayer() {
        boolean serverIsOnline = SoftwareCoSessionManager.isServerOnline();
        boolean spotifyState = isSpotifyConncted(serverIsOnline);
        if(spotifyState) {
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
        }
        if(CLIENT_ID == null) {
            JsonObject userObj = SoftwareCoUtils.getClientInfo(serverIsOnline);
            if(userObj != null) {
                CLIENT_ID = userObj.get("clientId").getAsString();
                CLIENT_SECRET = userObj.get("clientSecret").getAsString();
            }
        }
        MusicStore.setConfig(CLIENT_ID, CLIENT_SECRET, ACCESS_TOKEN, REFRESH_TOKEN, spotifyCacheState);

        SoftwareCoUtils.updatePlayerControls(true);
    }

    // Lazily update devices
    public static void lazyUpdateDevices(int retryCount, boolean activateDevice, boolean isWeb) {
        if(MusicControlManager.spotifyState() && !deviceActivated && retryCount > 0) {
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
    }

    private static boolean isSpotifyConncted(boolean serverIsOnline) {
        JsonObject userObj = SoftwareCoUtils.getUserDetails(serverIsOnline);
        if (userObj != null && userObj.has("email")) {
            // check if the email is valid
            String email = userObj.get("email").getAsString();
            if (SoftwareCoUtils.validateEmail(email)) {
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
                        MusicControlManager.spotifyCacheState = true;
                        MusicStore.setConfig(CLIENT_ID, CLIENT_SECRET, ACCESS_TOKEN, REFRESH_TOKEN, true);
                    }

                    if(array.getAsJsonObject().get("type").getAsString().equals("slack")) {
                        if(SlackControlManager.ACCESS_TOKEN == null) {
                            SlackControlManager.ACCESS_TOKEN = array.getAsJsonObject().get("access_token").getAsString();
                            SoftwareCoSessionManager.setItem("slack_access_token", SlackControlManager.ACCESS_TOKEN);
                        }
                        SlackControlManager.slackCacheState = true;
                    }
                    SoftwareCoUtils.jwt = userObj.get("plugin_token").getAsString();
                }
            }
        }
        return MusicControlManager.spotifyState();
    }

    public static void refreshAccessToken() {
        if(REFRESH_TOKEN == null)
            REFRESH_TOKEN = SoftwareCoSessionManager.getItem("spotify_refresh_token");

        if(CLIENT_ID == null && CLIENT_SECRET == null) {
            JsonObject userObj = SoftwareCoUtils.getClientInfo(true);
            if(userObj != null) {
                CLIENT_ID = userObj.get("clientId").getAsString();
                CLIENT_SECRET = userObj.get("clientSecret").getAsString();
            }
        }

        SoftwareResponse resp = (SoftwareResponse) Apis.refreshAccessToken(REFRESH_TOKEN, CLIENT_ID, CLIENT_SECRET);
        if (resp.isOk()) {
            JsonObject obj = resp.getJsonObj();
            ACCESS_TOKEN = obj.get("access_token").getAsString();
            SoftwareCoSessionManager.setItem("spotify_access_token", ACCESS_TOKEN);
            LOG.log(Level.INFO, "Music Time: New Access Token: " + ACCESS_TOKEN);
        }
    }

    public static JsonObject getSpotifyDevices() {

        String accessToken = "Bearer " + SoftwareCoSessionManager.getItem("spotify_access_token");
        SoftwareResponse resp = (SoftwareResponse) Apis.getSpotifyDevices(accessToken);
        if (resp.isOk()) {
            JsonObject obj = resp.getJsonObj();
            if (obj != null && obj.has("devices")) {
                spotifyDeviceIds.clear();
                spotifyDevices.clear();
                //currentDeviceName = null;
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
        } else if(resp.getCode() == 401) {
            refreshAccessToken();
        }
        return null;
    }

    public static boolean activateDevice(String deviceId) {
        String accessToken = "Bearer " + SoftwareCoSessionManager.getItem("spotify_access_token");
        return Apis.activateDevice(accessToken, deviceId);
    }

    public static JsonObject getUserProfile() {

        String accessToken = "Bearer " + SoftwareCoSessionManager.getItem("spotify_access_token");
        SoftwareResponse resp = (SoftwareResponse) Apis.getUserProfile(accessToken);
        JsonObject obj = resp.getJsonObj();
        if (resp.isOk()) {
            spotifyUserId = obj.get("id").getAsString();
            return obj;
        } else if (obj != null && obj.has("error")) {
            JsonObject error = obj.get("error").getAsJsonObject();
            String message = error.get("message").getAsString();
            if(message.equals("The access token expired")) {
                refreshAccessToken();
            }
        }
        return null;
    }
}
