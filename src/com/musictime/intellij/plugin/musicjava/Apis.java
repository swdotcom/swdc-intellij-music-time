package com.musictime.intellij.plugin.musicjava;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.musictime.intellij.plugin.SoftwareCoUtils;
import com.musictime.intellij.plugin.SoftwareResponse;
import com.musictime.intellij.plugin.fs.FileManager;
import com.musictime.intellij.plugin.music.MusicControlManager;
import com.musictime.intellij.plugin.music.PlaylistManager;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.net.util.Base64;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Timer;
import java.util.TimerTask;
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

    public static boolean validateEmail(String email) {
        return pattern.matcher(email).matches();
    }

    /*
     * Refresh spotify access token
     * @param
     * refreshToken - spotify refresh token
     * clientId - spotify client id
     * clientSecret - spotify client secret
     */
    public static Object refreshAccessToken() {
        if (MusicStore.SPOTIFY_CLIENT_ID == null) {
            SoftwareCoUtils.getAndUpdateClientInfo();
        }

        String refreshToken = FileManager.getItem("spotify_refresh_token");
        if (refreshToken == null) {
            SoftwareCoUtils.getMusicTimeUserStatus();
        }

        String clientId = MusicStore.getSpotifyClientId();
        String clientSecret = MusicStore.getSpotifyClientSecret();

        String api = "/api/token?grant_type=refresh_token&refresh_token=" + refreshToken;
        String authPayload = clientId + ":" + clientSecret;
        byte[] bytesEncoded = Base64.encodeBase64(authPayload.getBytes());
        String encodedAuthPayload = "Basic " + new String(bytesEncoded);

        SoftwareResponse resp = Client.makeSpotifyApiCallWithAuth(api, HttpPost.METHOD_NAME, null, encodedAuthPayload);

        if (resp.isOk()) {
            JsonObject obj = resp.getJsonObj();
            FileManager.setItem("spotify_access_token", obj.get("access_token").getAsString());
        } else {
            checkIfAccessExpired(resp);
        }
        return resp;
    }

    /*
     * Get spotify devices
     * @param
     * accessToken - spotify access token
     */
    public static Object getSpotifyDevices() {
        String api = "/v1/me/player/devices";
        SoftwareResponse resp = Client.makeSpotifyApiCall(api, HttpGet.METHOD_NAME, null);
        if (resp != null && resp.getCode() == 401) {
            refreshAccessToken();
            // try again
            resp = Client.makeSpotifyApiCall(api, HttpGet.METHOD_NAME, null);
            if (resp != null && resp.getCode() == 401) {
                checkIfAccessExpired(resp);
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
        SoftwareResponse resp = Client.makeSpotifyApiCall(api, HttpPut.METHOD_NAME, obj.toString());
        if (resp.getCode() == 204) {
            getSpotifyDevices();
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    PlaylistManager.gatherMusicInfo();
                }
            }, 1000);
            return true;
        }
        return false;
    }

    /*
     * Get spotify current user profile
     * @param
     * accessToken - spotify access token
     */
    public static SoftwareResponse getUserProfile() {
        String api = "/v1/me";
        SoftwareResponse resp = Client.makeSpotifyApiCall(api, HttpGet.METHOD_NAME, null);
        if (resp != null && resp.getCode() == 401) {
            // refresh the token
            refreshAccessToken();
            resp = Client.makeSpotifyApiCall(api, HttpGet.METHOD_NAME, null);
            if (resp != null && resp.getCode() == 401) {
                // refresh the token
                refreshAccessToken();
                resp = Client.makeSpotifyApiCall(api, HttpGet.METHOD_NAME, null);
            }
        }

        JsonObject obj = resp.getJsonObj();

        if (resp.isOk()) {
            MusicStore.setSpotifyUserId(obj.get("id").getAsString());
            MusicStore.setSpotifyAccountType(obj.get("product").getAsString());
            return resp;
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
    public static Object getUserPlaylists(String spotifyUserId) {
        if (spotifyUserId == null) {
            getUserProfile();
            spotifyUserId = MusicStore.getSpotifyUserId();
        }
        int initial = 0;
        boolean recursiveCall = true;
        SoftwareResponse response = new SoftwareResponse();

        while(recursiveCall) {
            SoftwareResponse resp = (SoftwareResponse) getPlaylists(spotifyUserId);
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
    public static Object getPlaylists(String spotifyUserId) {
        String api = "/v1/users/" + spotifyUserId + "/playlists?limit=50&offset=" + offset;
        SoftwareResponse resp = Client.makeSpotifyApiCall(api, HttpGet.METHOD_NAME, null);
        if(resp.isOk()) {
            return resp;
        } else if(!resp.getJsonObj().isJsonNull()) {
            JsonObject tracks = resp.getJsonObj();
            if (tracks != null && tracks.has("error")) {
                if (MusicControlManager.requiresSpotifyAccessTokenRefresh(tracks)) {
                    refreshAccessToken();
                }
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
            SoftwareResponse resp = Client.makeSpotifyApiCall(api, HttpGet.METHOD_NAME, null);
            if (resp.isOk()) {
                JsonObject data = resp.getJsonObj();
                if (data.has("tracks") && data.get("tracks").getAsJsonObject().has("items")) {
                    return data.get("tracks").getAsJsonObject().get("items").getAsJsonArray();
                }
            } else if(!resp.getJsonObj().isJsonNull()) {
                JsonObject data = resp.getJsonObj();
                if (MusicControlManager.requiresSpotifyAccessTokenRefresh(data)) {
                    refreshAccessToken();
                    resp = Client.makeSpotifyApiCall(api, HttpGet.METHOD_NAME, null);
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
    public static Object getTracksByPlaylistId(String playlistId) {
        if (playlistId != null) {
            String api = "/v1/playlists/" + playlistId;
            SoftwareResponse resp = Client.makeSpotifyApiCall(api, HttpGet.METHOD_NAME, null);
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
            } else if(!resp.getJsonObj().isJsonNull()) {
                JsonObject tracks = resp.getJsonObj();
                if (tracks != null && tracks.has("error")) {
                    if(MusicControlManager.requiresSpotifyAccessTokenRefresh(tracks)) {
                        refreshAccessToken();
                    }
                }
            }
            return resp;
        }
        return new SoftwareResponse();
    }

    /*
     * Get spotify track by id
     * @param
     * trackId - spotify track id
     */
    public static Object getTrackById(String trackId) {
        if (trackId != null) {
            String api = "/v1/tracks/" + trackId;
            SoftwareResponse resp = Client.makeSpotifyApiCall(api, HttpGet.METHOD_NAME, null);

            return resp;
        }
        return new SoftwareResponse();
    }

    /*
     * Get spotify currently playing track
     * @param
     * accessToken - spotify access token
     */
    public static Object getSpotifyWebCurrentTrack() {
        String api = "/v1/me/player/currently-playing";
        SoftwareResponse resp = Client.makeSpotifyApiCall(api, HttpGet.METHOD_NAME, null);
        if (resp.isOk() && resp.getCode() == 200) {
            JsonObject tracks = resp.getJsonObj();
            if (tracks != null && tracks.has("item") && !tracks.get("item").isJsonNull()) {
                JsonObject track = tracks.get("item").getAsJsonObject();
                MusicStore.setCurrentTrackId(track.get("id").getAsString());
                MusicStore.setCurrentTrackName(track.get("name").getAsString());
                if(!tracks.get("context").isJsonNull()) {
                    JsonObject context = tracks.get("context").getAsJsonObject();
                    String[] uri = context.get("uri").getAsString().split(":");
                    MusicStore.setCurrentPlaylistId(uri[uri.length - 1]);
                }
            }
        } else if(resp.getCode() == 204) {
            MusicStore.currentDeviceName = null;
            MusicStore.currentTrackName = null;
        } else if(!resp.getJsonObj().isJsonNull()) {
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

    public static void startDesktopPlayer(String playerName) {
        Util.startPlayer(playerName);
    }

    public static boolean accessExpired() {
        SoftwareResponse resp = getUserProfile();
        if (resp != null && resp.getCode() == 401) {
            resp = getUserProfile();
            if (resp != null && resp.getCode() == 401) {
                return true;
            }
        }
        return false;
    }

    private static void checkIfAccessExpired(SoftwareResponse resp) {
        if (resp != null && resp.getCode() == 401) {
            String spotifyAccessToken = FileManager.getItem("spotify_access_token");
            if (StringUtils.isNotBlank(spotifyAccessToken)) {
                if (accessExpired()) {
                    // disconnect spotify
                    MusicControlManager.disConnectSpotify();
                }
            }
        }
    }
}
