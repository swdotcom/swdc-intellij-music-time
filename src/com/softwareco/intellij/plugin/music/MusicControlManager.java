package com.softwareco.intellij.plugin.music;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.ui.Messages;
import com.softwareco.intellij.plugin.SoftwareCoSessionManager;
import com.softwareco.intellij.plugin.SoftwareCoUtils;
import com.softwareco.intellij.plugin.musicjava.SoftwareResponse;
import com.softwareco.intellij.plugin.actions.MusicToolWindow;
import com.softwareco.intellij.plugin.musicjava.Apis;
import com.softwareco.intellij.plugin.musicjava.MusicStore;
import com.softwareco.intellij.plugin.musicjava.Util;
import org.apache.http.client.methods.HttpPut;

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
    public static List<String> currentPlaylistTracks = new ArrayList<>();
    public static String currentTrackId = null;
    public static String currentTrackName = null;
    public static List<String> spotifyDeviceIds = new ArrayList<>();
    public static String currentDeviceId = null;
    public static String currentDeviceName = null;

    public static int playerCounter = 0;
    public static String spotifyStatus = "Not Connected"; // Connected or Not Connected
    public static String playerState = "End"; // End or Resume
    public static String playerType = "Web Player"; // Web Player or Desktop Player

    public static Map<String, String> likedTracks = new HashMap<>();
    public static Map<String, String> topTracks = new HashMap<>();
    public static Map<String, String> myAITopTracks = new HashMap<>();

    public static void resetSpotify() {
        spotifyUserId = null;
        currentPlaylistTracks.clear();
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

            lazyUpdatePlayer();
            lazyUpdatePlaylist();
        }
    }

    public static void launchPlayer() {
        if(currentTrackId == null)
            PlaylistManager.getSpotifyWebRecentTrack();

        if(currentTrackId == null) {
            PlaylistManager.getPlaylistTracks();
            PlaylistManager.getTrackById();
            currentTrackName = null;
        }
        PlaylistManager.getUserPlaylists();

        if(SoftwareCoUtils.isMac()) {
            String infoMsg = "Spotify is currently not running, would you like to launch the \n" +
                    "desktop instead of the web player?";
            int response = Messages.showOkCancelDialog(infoMsg, SoftwareCoUtils.pluginName, "Not Now", "Yes", Messages.getInformationIcon());
            if(response == 0) {
                playerType = "Web Player";
            } else if(response == 2) {
                playerType = "Desktop Player";
            }
        }

        if(userStatus != null && userStatus.equals("premium") && playerType.equals("Web Player")) {
            if (currentPlaylistId != null && currentPlaylistId.length() > 5) {
                BrowserUtil.browse("https://open.spotify.com/playlist/" + currentPlaylistId);
            } else if (currentTrackId != null) {
                BrowserUtil.browse("https://open.spotify.com/track/" + currentTrackId);
            } else {
                BrowserUtil.browse("https://open.spotify.com/browse");
            }
        } else if(userStatus != null && playerType.equals("Desktop Player")) {
            SoftwareCoUtils.startPlayer("spotify");
            new Thread(() -> {
                try {
                    Thread.sleep(1000);
                    if(!SoftwareCoUtils.isSpotifyRunning())
                        if(userStatus.equals("premium"))
                            SoftwareCoUtils.showMsgPrompt("Spotify Desktop Player is required, Please Install Spotify");
                        else
                            SoftwareCoUtils.showMsgPrompt("Spotify Desktop Player is required for Non-Premium account, Please Install Spotify");
                }
                catch (Exception e){
                    System.err.println(e);
                }
            }).start();
        }
    }

    // Lazily update player controls
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
        }
        if(CLIENT_ID == null) {
            JsonObject userObj = SoftwareCoUtils.getClientInfo(true);
            if(userObj != null) {
                CLIENT_ID = userObj.get("clientId").getAsString();
                CLIENT_SECRET = userObj.get("clientSecret").getAsString();
            }
        }
        MusicStore.setConfig(CLIENT_ID, CLIENT_SECRET, ACCESS_TOKEN, REFRESH_TOKEN, spotifyCacheState);
        SoftwareCoUtils.updatePlayerControles();
        //MusicToolWindow.triggerRefresh();
    }

    // Lazily update playlists
    public static void lazyUpdatePlaylist() {
        if(spotifyCacheState) {
            // Update playlist for every 7 second
            new Thread(() -> {
                try {
                    Thread.sleep(7000);
                    lazyUpdatePlaylist();
                }
                catch (Exception e){
                    System.err.println(e);
                }
            }).start();
            getSpotifyDevices();
            PlayListCommands.updatePlaylists();
        }
        //MusicToolWindow.triggerRefresh();
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
                        SoftwareCoUtils.jwt = userObj.get("plugin_token").getAsString();
                        spotifyCacheState = true;
                        MusicStore.setConfig(CLIENT_ID, CLIENT_SECRET, ACCESS_TOKEN, REFRESH_TOKEN, true);
                        return true;
                    }
                }
            }
        }
        return false;
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
                for(JsonElement array : obj.get("devices").getAsJsonArray()) {
                    JsonObject device = array.getAsJsonObject();
                    spotifyDeviceIds.add(device.get("id").getAsString());
                    if(device.get("is_active").getAsBoolean()) {
                        currentDeviceId = device.get("id").getAsString();
                        currentDeviceName = device.get("name").getAsString();
                        if(currentDeviceName.contains("Web Player"))
                            playerType = "Web Player";
                        else
                            playerType = "Desktop Player";
                    }
                }
            } else {
                LOG.log(Level.INFO, "Music Time: No Device Found, null response");
            }
            return obj;
        }
        return null;
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
