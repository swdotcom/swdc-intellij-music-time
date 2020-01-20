package com.softwareco.intellij.plugin.musicjava;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.commons.net.util.Base64;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class Apis {
    public static final Logger LOG = Logger.getLogger("SoftwareCoUtils");

    private static String regex = "^\\S+@\\S+\\.\\S+$";
    private static Pattern pattern = Pattern.compile(regex);

    public static boolean validateEmail(String email) {
        return pattern.matcher(email).matches();
    }

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
                    spotifyDeviceIds.add(device.get("id").getAsString());
                    if(device.get("is_active").getAsBoolean()) {
                        MusicStore.setCurrentDeviceId(device.get("id").getAsString());
                        MusicStore.setCurrentDeviceName(device.get("name").getAsString());
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

    public static Object getUserProfile(String accessToken) {

        String api = "/v1/me";
        SoftwareResponse resp = Client.makeApiCall(api, HttpGet.METHOD_NAME, null, accessToken);
        JsonObject obj = resp.getJsonObj();
        if (resp.isOk()) {
            MusicStore.setSpotifyUserId(obj.get("id").getAsString());
            MusicStore.setSpotifyAccountType(obj.get("product").getAsString());
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
    public static Object getUserPlaylists(String spotifyUserId, String accessToken) {

        if(spotifyUserId == null) {
            getUserProfile(accessToken);
            spotifyUserId = MusicStore.getSpotifyUserId();
        }

        String api = "/v1/users/" + spotifyUserId + "/playlists";
        SoftwareResponse resp = Client.makeApiCall(api, HttpGet.METHOD_NAME, null, accessToken);
        if (resp.isOk()) {
            JsonObject obj = resp.getJsonObj();
            if (obj != null && obj.has("items")) {
                List<String> playlistIds = MusicStore.getPlaylistIds();
                List<String> userPlaylistIds = MusicStore.getUserPlaylistIds();
                playlistIds.clear();
                userPlaylistIds.clear();
                MusicStore.setMyAIPlaylistId(null);
                for(JsonElement array : obj.get("items").getAsJsonArray()) {
                    if(array.getAsJsonObject().get("type").getAsString().equals("playlist")) {
                        playlistIds.add(array.getAsJsonObject().get("id").getAsString());
                        if(array.getAsJsonObject().get("name").getAsString().equals("My AI Top 40")) {
                            MusicStore.setMyAIPlaylistId(array.getAsJsonObject().get("id").getAsString());
                        } else {
                            userPlaylistIds.add(array.getAsJsonObject().get("id").getAsString());
                        }
                    }
                }
            } else {
                LOG.log(Level.INFO, "Music Time: Unable to get Playlists, null response");
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

    public static Object getTracksByPlaylistId(String playlistId) {

        if(playlistId != null) {
            String api = "/v1/playlists/" + playlistId;
            SoftwareResponse resp = Client.makeApiCall(api, HttpGet.METHOD_NAME, null, "Bearer " + MusicStore.getSpotifyAccessToken());
            if (resp.isOk()) {
                JsonObject obj = resp.getJsonObj();
                if (obj != null && obj.has("tracks")) {
                    JsonObject tracks = obj.get("tracks").getAsJsonObject();
                    MusicStore.currentPlaylistTracks.clear();
                    for (JsonElement array : tracks.get("items").getAsJsonArray()) {
                        JsonObject track = array.getAsJsonObject().get("track").getAsJsonObject();
                        MusicStore.currentPlaylistTracks.add(track.get("id").getAsString());
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
        return null;
    }

    public static Object getCurrentPlaylistTracks() {

        if(MusicStore.currentPlaylistId == null) {
            MusicStore.currentPlaylistId = MusicStore.playlistIds.get(0);
        }

        String api = "/v1/playlists/" + MusicStore.currentPlaylistId + "/tracks";
        SoftwareResponse resp = Client.makeApiCall(api, HttpGet.METHOD_NAME, null, "Bearer " + MusicStore.getSpotifyAccessToken());
        if (resp.isOk()) {
            JsonObject tracks = resp.getJsonObj();
            if (tracks != null && tracks.has("items")) {
                MusicStore.currentPlaylistTracks.clear();
                for(JsonElement array : tracks.get("items").getAsJsonArray()) {
                    JsonObject track = array.getAsJsonObject().get("track").getAsJsonObject();
                    MusicStore.currentPlaylistTracks.add(track.get("id").getAsString());
                }
            } else {
                LOG.log(Level.INFO, "Music Time: Unable to get Playlist Tracks, null response");
            }
        }
        return resp;
    }

    public static Object getTrackById() {

        if(MusicStore.currentTrackId == null) {
            MusicStore.currentTrackId = MusicStore.currentPlaylistTracks.get(0);
        }

        String api = "/v1/tracks/" + MusicStore.currentTrackId;
        SoftwareResponse resp = Client.makeApiCall(api, HttpGet.METHOD_NAME, null, "Bearer " + MusicStore.getSpotifyAccessToken());
        if (resp.isOk()) {
            JsonObject tracks = resp.getJsonObj();
            if (tracks != null && tracks.has("name")) {
                MusicStore.currentTrackName = tracks.get("name").getAsString();
            } else {
                LOG.log(Level.INFO, "Music Time: Unable to get Playlist Tracks, null response");
            }
        }
        return resp;
    }

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

    public static Object getSpotifyWebCurrentTrack(String accessToken) {

        String api = "/v1/me/player/currently-playing";
        SoftwareResponse resp = Client.makeApiCall(api, HttpGet.METHOD_NAME, null, accessToken);
        if (resp.isOk() && resp.getCode() == 200) {
            JsonObject tracks = resp.getJsonObj();
            if (tracks != null && tracks.has("item")) {
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

    public static void startDesktopPlayer(String playerName) {
        Util.startPlayer(playerName);
    }
}
