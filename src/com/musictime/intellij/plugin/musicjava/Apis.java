package com.musictime.intellij.plugin.musicjava;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.musictime.intellij.plugin.SoftwareCoMusic;
import com.musictime.intellij.plugin.SoftwareCoUtils;
import com.musictime.intellij.plugin.music.MusicControlManager;
import com.musictime.intellij.plugin.music.PlaylistManager;
import org.apache.commons.lang.StringUtils;
import swdc.java.ops.http.ClientResponse;
import swdc.java.ops.http.OpsHttpClient;
import swdc.java.ops.manager.AccountManager;
import swdc.java.ops.manager.AsyncManager;
import swdc.java.ops.manager.FileUtilManager;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class Apis {
    public static final Logger LOG = Logger.getLogger("SoftwareCoUtils");

    private static String regex = "^\\S+@\\S+\\.\\S+$";
    private static Pattern pattern = Pattern.compile(regex);
    private static JsonObject userPlaylists = new JsonObject();
    private static JsonArray userPlaylistArray = new JsonArray();
    private static int offset = 0;

    private static String getSpotifyAccessToken() {
        return FileUtilManager.getItem("spotify_access_token");
    }

    /*
     * Refresh spotify access token
     * @param
     * refreshToken - spotify refresh token
     * clientId - spotify client id
     * clientSecret - spotify client secret
     */
    public static boolean refreshAccessToken() {
        if (MusicStore.SPOTIFY_CLIENT_ID == null) {
            SoftwareCoUtils.getAndUpdateClientInfo();
        }

        String refreshToken = FileUtilManager.getItem("spotify_refresh_token");
        if (StringUtils.isBlank(refreshToken)) {
            AccountManager.getUserLoginState(false);
            refreshToken = FileUtilManager.getItem("spotify_refresh_token");
            if (StringUtils.isBlank(refreshToken)) {
                return false;
            }
        }

        String clientId = MusicStore.getSpotifyClientId();
        String clientSecret = MusicStore.getSpotifyClientSecret();
        String refreshedAccessToken = OpsHttpClient.spotifyTokenRefresh(refreshToken, clientId, clientSecret);

        if (refreshedAccessToken != null) {
            FileUtilManager.setItem("spotify_access_token", refreshedAccessToken);
            return true;
        }
        return false;
    }

    /*
     * Get spotify devices
     * @param
     * accessToken - spotify access token
     */
    public static ClientResponse getSpotifyDevices() {
        String api = "/v1/me/player/devices";
        ClientResponse resp = OpsHttpClient.spotifyGet(api, getSpotifyAccessToken());
        if (resp.isOk()) {
            return resp;
        } else {
            if (refreshAccessTokenIfExpired(resp.getJsonObj())) {
                resp = OpsHttpClient.spotifyGet(api, getSpotifyAccessToken());
            }
        }
        return resp;
    }

    /*
     * Activate spotify device
     * @param
     * accessToken - spotify access token
     * deviceId - spotify device id
     */
    public static boolean activateDevice(String deviceId) {
        JsonObject obj = new JsonObject();
        if(deviceId != null) {
            JsonArray array = new JsonArray();
            array.add(deviceId);
            obj.add("device_ids", array);
        }
        obj.addProperty("play", true);

        String api = "/v1/me/player";
        ClientResponse resp = OpsHttpClient.softwarePut(api, getSpotifyAccessToken(), obj);
        if (resp.getCode() < 300) {
            DeviceManager.refreshDevices();
            AsyncManager.getInstance().executeOnceInSeconds(() -> PlaylistManager.fetchTrack(), 3);
            return true;
        }
        return false;
    }

    /*
     * Get spotify current user profile
     * @param
     * accessToken - spotify access token
     */
    public static ClientResponse getUserProfile() {
        String api = "/v1/me";
        ClientResponse resp = OpsHttpClient.spotifyGet(api, getSpotifyAccessToken());
        if (resp.isOk()) {
            MusicStore.setSpotifyUserId(resp.getJsonObj().get("id").getAsString());
            MusicStore.setSpotifyAccountType(resp.getJsonObj().get("product").getAsString());
        } else {
            if (refreshAccessTokenIfExpired(resp.getJsonObj())) {
                resp = OpsHttpClient.spotifyGet(api, getSpotifyAccessToken());
                if (resp.isOk()) {
                    MusicStore.setSpotifyUserId(resp.getJsonObj().get("id").getAsString());
                    MusicStore.setSpotifyAccountType(resp.getJsonObj().get("product").getAsString());
                }
            }
        }

        return resp;
    }

    // Playlist Manager APIs **********************************************************************************
    //**** Start *******
    /*
     * Get spotify user playlist
     * @param
     * spotifyUserId - spotify user id
     * accessToken - spotify access token
     */
    public static ClientResponse getUserPlaylists(String spotifyUserId) {
        if (spotifyUserId == null) {
            getUserProfile();
            spotifyUserId = MusicStore.getSpotifyUserId();
        }
        int initial = 0;
        boolean recursiveCall = true;
        ClientResponse response = new ClientResponse();

        while(recursiveCall) {
            ClientResponse resp = getPlaylists(spotifyUserId);
            if (resp != null && resp.isOk()) {
                userPlaylists = resp.getJsonObj();
                if (userPlaylists != null && userPlaylists.has("items")) {

                    if (initial == 0) {
                        userPlaylistArray = new JsonArray();
                        MusicStore.playlistIds.clear();
                        MusicStore.userPlaylistIds.clear();
                        MusicStore.setMyAIPlaylistId(null);
                        initial = 1;
                    }
                    int counter = 0;
                    for (JsonElement array : userPlaylists.get("items").getAsJsonArray()) {
                        if (array.getAsJsonObject().get("type").getAsString().equals("playlist")) {
                            userPlaylistArray.add(array);
                            MusicStore.playlistIds.add(array.getAsJsonObject().get("id").getAsString());
                            if (array.getAsJsonObject().get("name").getAsString().equals("My AI Top 40")) {
                                MusicStore.setMyAIPlaylistId(array.getAsJsonObject().get("id").getAsString());
                            } else {
                                MusicStore.userPlaylistIds.add(array.getAsJsonObject().get("id").getAsString());
                            }
                            counter++;
                        }
                    }
                    if (counter == 50) {
                        offset += 50;
                        recursiveCall = true;
                    } else {
                        offset = 0;
                        recursiveCall = false;
                        userPlaylists.add("items", userPlaylistArray);
                        response.setIsOk(resp.isOk());
                        response.setCode(resp.getCode());
                        response.setJsonObj(userPlaylists);
                        userPlaylistArray = new JsonArray();
                        userPlaylists = new JsonObject();
                    }
                } else {
                    LOG.log(Level.INFO, "Music Time: Unable to get Playlists, null response");
                    recursiveCall = false;
                }
            } else {
                recursiveCall = false;
            }
        }
        return response;
    }

    /*
     * Get spotify user playlist
     * @param
     * spotifyUserId - spotify user id
     * accessToken - spotify access token
     */
    public static ClientResponse getPlaylists(String spotifyUserId) {
        String api = "/v1/users/" + spotifyUserId + "/playlists?limit=50&offset=" + offset;
        ClientResponse resp = OpsHttpClient.spotifyGet(api, getSpotifyAccessToken());
        if (resp.isOk()) {
            return resp;
        } else {
            if (refreshAccessTokenIfExpired(resp.getJsonObj())) {
                resp = OpsHttpClient.spotifyGet(api, getSpotifyAccessToken());
            }
        }
        return resp;
    }

    /**
     * type: Valid types are: album, artist, playlist, and track
     * q: can have a filter and keywords, or just keywords. You
     * can have a wildcard as well. The query will search against
     * the name and description if a specific filter isn't specified.
     * examples:
     * 1) search for a track by name "what a time to be alive"
     *    query string: ?q=name:what%20a%20time&type=track
     *    result: this should return tracks matching the track name
     * 2) search for a track using a wildcard in the name
     *    query string: ?q=name:what*&type=track&limit=50
     *    result: will return all tracks with "what" in the name
     * 3) search for an artist in name or description
     *    query string: ?tania%20bowra&type=artist
     *    result: will return all artists where tania bowra is in
     *            the name or description
     * limit: max of 50
     */
    public static JsonArray searchSpotify(String keywords) {
        if (StringUtils.isNotBlank(keywords)) {
            keywords = keywords.trim();
            String encodedKeywords = keywords;
            try {
                encodedKeywords = URLEncoder.encode(keywords, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                encodedKeywords = keywords;
            }

            String api = "/v1/search?type=track&q="+encodedKeywords+"&limit=50";
            ClientResponse resp = OpsHttpClient.spotifyGet(api, getSpotifyAccessToken());
            if (resp.isOk()) {
                JsonObject data = resp.getJsonObj();
                if (data.has("tracks") && data.get("tracks").getAsJsonObject().has("items")) {
                    return data.get("tracks").getAsJsonObject().get("items").getAsJsonArray();
                }
            } else if(resp.getJsonObj() != null) {
                JsonObject data = resp.getJsonObj();
                if (MusicControlManager.requiresSpotifyAccessTokenRefresh(data)) {
                    refreshAccessToken();
                    resp = OpsHttpClient.spotifyGet(api, getSpotifyAccessToken());
                }
            }
            if (resp != null && resp.isOk()) {
                JsonObject data = resp.getJsonObj();
                if (data.has("tracks") && data.get("tracks").getAsJsonObject().has("items")) {
                    return data.get("tracks").getAsJsonObject().get("items").getAsJsonArray();
                }
            }
        }
        return new JsonArray();
    }

    /*
     * Get spotify tracks by playlist id
     * @param
     * accessToken - spotify access token
     * playlistId - spotify playlist id
     */
    public static ClientResponse getTracksByPlaylistId(String playlistId) {
        if (playlistId != null) {
            String api = "/v1/playlists/" + playlistId;
            ClientResponse resp = OpsHttpClient.spotifyGet(api, getSpotifyAccessToken());
            if (resp.isOk()) {
                JsonObject obj = resp.getJsonObj();
                if (obj != null && obj.has("tracks")) {
                    JsonObject tracks = obj.get("tracks").getAsJsonObject();
                    MusicStore.tracksByPlaylistId.clear();
                    for (JsonElement array : tracks.get("items").getAsJsonArray()) {
                        JsonObject track = array.getAsJsonObject().get("track").getAsJsonObject();
                        MusicStore.tracksByPlaylistId.add(track.get("id").getAsString());
                    }
                } else {
                    LOG.log(Level.INFO, "Music Time: Unable to get Playlist Tracks, null response");
                }
            } else if(resp.getJsonObj() != null) {
                JsonObject tracks = resp.getJsonObj();
                if (tracks != null && tracks.has("error")) {
                    if(MusicControlManager.requiresSpotifyAccessTokenRefresh(tracks)) {
                        refreshAccessToken();
                    }
                }
            }
            return resp;
        }
        return new ClientResponse();
    }

    /*
     * Get spotify currently playing track
     * @param
     * accessToken - spotify access token
     */
    public static ClientResponse getSpotifyWebCurrentTrack() {
        String api = "/v1/me/player/currently-playing";
        ClientResponse resp = OpsHttpClient.spotifyGet(api, getSpotifyAccessToken());
        if (resp.isOk() && resp.getCode() == 200) {
            JsonObject tracks = resp.getJsonObj();
            if (tracks != null && tracks.has("item") && !tracks.get("item").isJsonNull()) {
                JsonObject track = tracks.get("item").getAsJsonObject();
                MusicControlManager.currentTrackId = track.get("id").getAsString();
                MusicControlManager.currentTrackName = track.get("name").getAsString();
                if (!tracks.get("context").isJsonNull()) {
                    JsonObject context = tracks.get("context").getAsJsonObject();
                    String[] uri = context.get("uri").getAsString().split(":");
                    MusicControlManager.currentPlaylistId = uri[uri.length - 1];
                }
            }
        } else if(resp.getJsonObj() != null) {
            JsonObject tracks = resp.getJsonObj();
            if (tracks != null && tracks.has("error")) {
                if(MusicControlManager.requiresSpotifyAccessTokenRefresh(tracks)) {
                    refreshAccessToken();
                }
            }
        }
        return resp;
    }
    //***** End ****************

    public static boolean isSpotifyInstalled() {
        return Util.isSpotifyInstalled();
    }

    public static boolean refreshAccessTokenIfExpired(JsonObject obj) {
        if (obj != null && obj.has("error") && !obj.get("error").isJsonNull()) {
            JsonObject error = obj.get("error").getAsJsonObject();
            if (error.has("status") && error.get("status").getAsInt() == 401) {
                if (!refreshAccessToken()) {

                    // disconnect spotify
                    MusicControlManager.disConnectSpotify();

                    // show reconnect prompt
                    SoftwareCoMusic.showReconnectPrompt();
                    return false;
                }

            }
        }
        return true;
    }
}
