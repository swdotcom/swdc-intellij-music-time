package com.softwareco.intellij.plugin.musicjava;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.commons.net.util.Base64;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;

import java.util.List;
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
    public static Object refreshAccessToken(String refreshToken, String clientId, String clientSecret) {
        if(refreshToken == null)
            refreshToken = MusicStore.getSpotifyRefreshToken();
        else
            MusicStore.setSpotifyRefreshToken(refreshToken);

        if(clientId == null && clientSecret == null) {
            clientId = MusicStore.getSpotifyClientId();
            clientSecret = MusicStore.getSpotifyClientSecret();
        } else {
            MusicStore.setSpotifyClientId(clientId);
            MusicStore.setSpotifyClientSecret(clientSecret);
        }

        String api = "/api/token?grant_type=refresh_token&refresh_token=" + refreshToken;
        String authPayload = clientId + ":" + clientSecret;
        byte[] bytesEncoded = Base64.encodeBase64(authPayload.getBytes());
        String encodedAuthPayload = "Basic " + new String(bytesEncoded);

        SoftwareResponse resp = Client.makeApiCall(api, HttpPost.METHOD_NAME, null, encodedAuthPayload);
        if (resp.isOk()) {
            JsonObject obj = resp.getJsonObj();
            MusicStore.setSpotifyAccessToken(obj.get("access_token").getAsString());
        }
        return resp;
    }

    /*
     * Get spotify devices
     * @param
     * accessToken - spotify access token
     */
    public static Object getSpotifyDevices(String accessToken) {

        String api = "/v1/me/player/devices";
        SoftwareResponse resp = Client.makeApiCall(api, HttpGet.METHOD_NAME, null, accessToken);
        if (resp.isOk()) {
            JsonObject tracks = resp.getJsonObj();
            if (tracks != null && tracks.has("devices")) {
                List<String> spotifyDeviceIds = MusicStore.getSpotifyDeviceIds();
                spotifyDeviceIds.clear();
                for(JsonElement array : tracks.get("devices").getAsJsonArray()) {
                    JsonObject device = array.getAsJsonObject();
                    if(device.get("type").getAsString().equals("Computer")) {
                        spotifyDeviceIds.add(device.get("id").getAsString());
                        if (device.get("is_active").getAsBoolean()) {
                            MusicStore.setCurrentDeviceId(device.get("id").getAsString());
                            MusicStore.setCurrentDeviceName(device.get("name").getAsString());
                        }
                    }
                }
            } else {
                LOG.log(Level.INFO, "Music Time: No Device Found, null response");
            }
        } else if(!resp.getJsonObj().isJsonNull()) {
            JsonObject tracks = resp.getJsonObj();
            if (tracks != null && tracks.has("error")) {
                JsonObject error = tracks.get("error").getAsJsonObject();
                String message = error.get("message").getAsString();
                if(message.equals("The access token expired")) {
                    refreshAccessToken(null, null, null);
                }
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
    public static boolean activateDevice(String accessToken, String deviceId) {

        JsonObject obj = new JsonObject();
        if(deviceId != null) {
            JsonArray array = new JsonArray();
            array.add(deviceId);
            obj.add("device_ids", array);
        }
        obj.addProperty("play", true);

        String api = "/v1/me/player";
        SoftwareResponse resp = Client.makeApiCall(api, HttpPut.METHOD_NAME, obj.toString(), accessToken);
        if (resp.getCode() == 204) {
            getSpotifyDevices(accessToken);
            return true;
        }
        return false;
    }

    /*
     * Get spotify current user profile
     * @param
     * accessToken - spotify access token
     */
    public static Object getUserProfile(String accessToken) {

        String api = "/v1/me";
        SoftwareResponse resp = Client.makeApiCall(api, HttpGet.METHOD_NAME, null, accessToken);
        JsonObject obj = resp.getJsonObj();
        if (resp.isOk()) {
            MusicStore.setSpotifyUserId(obj.get("id").getAsString());
            MusicStore.setSpotifyAccountType(obj.get("product").getAsString());
            MusicStore.setSpotifyAccessToken(accessToken);
            return resp;
        } else if (obj != null && obj.has("error")) {
            JsonObject error = obj.get("error").getAsJsonObject();
            String message = error.get("message").getAsString();
            if(message.equals("The access token expired")) {
                refreshAccessToken(null, null, null);
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
    public static Object getUserPlaylists(String spotifyUserId, String accessToken) {

        if(spotifyUserId == null) {
            getUserProfile(accessToken);
            spotifyUserId = MusicStore.getSpotifyUserId();
        }
        int initial = 0;
        boolean recursiveCall = true;
        SoftwareResponse response = new SoftwareResponse();

        while(recursiveCall) {
            SoftwareResponse resp = (SoftwareResponse) getPlaylists(spotifyUserId, accessToken);
            if (resp.isOk()) {
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
    public static Object getPlaylists(String spotifyUserId, String accessToken) {
        String api = "/v1/users/" + spotifyUserId + "/playlists?limit=50&offset=" + offset;
        SoftwareResponse resp = Client.makeApiCall(api, HttpGet.METHOD_NAME, null, accessToken);
        if(resp.isOk()) {
            return resp;
        } else if(!resp.getJsonObj().isJsonNull()) {
            JsonObject tracks = resp.getJsonObj();
            if (tracks != null && tracks.has("error")) {
                JsonObject error = tracks.get("error").getAsJsonObject();
                String message = error.get("message").getAsString();
                if(message.equals("The access token expired")) {
                    refreshAccessToken(null, null, null);
                }
            }
        }
        return resp;
    }

    /*
     * Get spotify tracks by playlist id
     * @param
     * accessToken - spotify access token
     * playlistId - spotify playlist id
     */
    public static Object getTracksByPlaylistId(String accessToken, String playlistId) {

        if(playlistId != null) {
            String api = "/v1/playlists/" + playlistId;
            SoftwareResponse resp = Client.makeApiCall(api, HttpGet.METHOD_NAME, null, accessToken);
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
                    JsonObject error = tracks.get("error").getAsJsonObject();
                    String message = error.get("message").getAsString();
                    if(message.equals("The access token expired")) {
                        refreshAccessToken(null, null, null);
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

        if(trackId != null) {
            String api = "/v1/tracks/" + trackId;
            SoftwareResponse resp = Client.makeApiCall(api, HttpGet.METHOD_NAME, null, "Bearer " + MusicStore.getSpotifyAccessToken());

            return resp;
        }
        return new SoftwareResponse();
    }

    /*
     * Get spotify recently played track
     * @param
     * accessToken - spotify access token
     */
    public static Object getSpotifyWebRecentTrack(String accessToken) {

        String api = "/v1/me/player/recently-played?limit=1";
        SoftwareResponse resp = Client.makeApiCall(api, HttpGet.METHOD_NAME, null, accessToken);
        if (resp.isOk()) {
            JsonObject tracks = resp.getJsonObj();
            if (tracks != null && tracks.has("items")) {
                for(JsonElement array : tracks.get("items").getAsJsonArray()) {
                    JsonObject track = array.getAsJsonObject().get("track").getAsJsonObject();
                    MusicStore.setCurrentTrackId(track.get("id").getAsString());
                }

            }
        } else {
            JsonObject tracks = resp.getJsonObj();
            if (tracks != null && tracks.has("error")) {
                JsonObject error = tracks.get("error").getAsJsonObject();
                String message = error.get("message").getAsString();
                if(message.equals("The access token expired")) {
                    refreshAccessToken(null, null, null);
                }
            }
        }
        return resp;
    }

    /*
     * Get spotify currently playing track
     * @param
     * accessToken - spotify access token
     */
    public static Object getSpotifyWebCurrentTrack(String accessToken) {

        String api = "/v1/me/player/currently-playing";
        SoftwareResponse resp = Client.makeApiCall(api, HttpGet.METHOD_NAME, null, accessToken);
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
                JsonObject error = tracks.get("error").getAsJsonObject();
                String message = error.get("message").getAsString();
                if(message.equals("The access token expired")) {
                    refreshAccessToken(null, null, null);
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
}
