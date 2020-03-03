package com.softwareco.intellij.plugin.musicjava;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.softwareco.intellij.plugin.SoftwareCoMusic;
import com.softwareco.intellij.plugin.music.MusicControlManager;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PlaylistController {
    public static final Logger LOG = Logger.getLogger("PlaylistController");

    public static Map<String, String> likedTracks = new HashMap<>();
    public static Map<String, String> topTracks = new HashMap<>();
    public static Map<String, String> myAITopTracks = new HashMap<>();
    public static List<String> recommendedTracks = new ArrayList<>();

    public static Object getTopSpotifyTracks() {

        String api = "/v1/me/top/tracks?time_range=medium_term&limit=50";
        SoftwareResponse resp = Client.makeApiCall(api, HttpGet.METHOD_NAME, null, "Bearer " + MusicStore.getSpotifyAccessToken());
        if (resp.isOk()) {
            JsonObject obj = resp.getJsonObj();
            if (obj != null && obj.has("items")) {
                topTracks.clear();
                for(JsonElement array : obj.get("items").getAsJsonArray()) {
                    JsonObject track = array.getAsJsonObject();
                    topTracks.put(track.get("id").getAsString(), track.get("name").getAsString());
                }
            } else {
                LOG.log(Level.INFO, "Music Time: Unable to get Top Tracks, null response");
            }
        } else if(!resp.getJsonObj().isJsonNull()) {
            JsonObject tracks = resp.getJsonObj();
            if (tracks != null && tracks.has("error")) {
                JsonObject error = tracks.get("error").getAsJsonObject();
                String message = error.get("message").getAsString();
                if(message.equals("The access token expired")) {
                    MusicControlManager.refreshAccessToken();
                }
            }
        }
        return resp;
    }

    public static Object getLikedSpotifyTracks(String accessToken) {

        String api = "/v1/me/tracks?limit=50&offset=0";
        SoftwareResponse resp = Client.makeApiCall(api, HttpGet.METHOD_NAME, null, accessToken);
        if (resp.isOk()) {
            JsonObject obj = resp.getJsonObj();
            if (obj != null && obj.has("items")) {
                likedTracks.clear();
                for(JsonElement array : obj.get("items").getAsJsonArray()) {
                    JsonObject track = array.getAsJsonObject().get("track").getAsJsonObject();
                    likedTracks.put(track.get("id").getAsString(), track.get("name").getAsString());
                }

            } else {
                LOG.log(Level.INFO, "Music Time: Unable to get Liked Tracks, null response");
            }
        } else if(!resp.getJsonObj().isJsonNull()) {
            JsonObject tracks = resp.getJsonObj();
            if (tracks != null && tracks.has("error")) {
                JsonObject error = tracks.get("error").getAsJsonObject();
                String message = error.get("message").getAsString();
                if(message.equals("The access token expired")) {
                    MusicControlManager.refreshAccessToken();
                }
            }
        }
        return resp;
    }

    public static Object generateAIPlaylist() {
        if(MusicStore.spotifyUserId == null) {
            Apis.getUserProfile(MusicStore.getSpotifyAccessToken());
        }

        JsonObject obj = new JsonObject();
        obj.addProperty("name", "My AI Top 40");
        obj.addProperty("public", "false");

        String api = "/v1/users/" + MusicStore.spotifyUserId + "/playlists";
        SoftwareResponse resp = Client.makeApiCall(api, HttpPost.METHOD_NAME, obj.toString(), "Bearer " + MusicStore.getSpotifyAccessToken());
        if (resp.isOk()) {
            Apis.getUserPlaylists(MusicStore.getSpotifyUserId(), MusicStore.getSpotifyAccessToken());
        }
        return resp;
    }

    public static Object sendPlaylistToSoftware(String payload, String jwt) {

        String api = "/music/playlist/generated";
        SoftwareResponse resp = Client.makeApiCall(api, HttpPost.METHOD_NAME, payload, jwt);

        return resp;
    }

    public static Object getRecommendedTracks(String jwt) {

        String api = "/music/recommendations?limit=40";
        SoftwareResponse resp = Client.makeApiCall(api, HttpGet.METHOD_NAME, null, jwt);
        if(resp.isOk()) {
            recommendedTracks.clear();
            JsonArray array = (JsonArray) SoftwareCoMusic.jsonParser.parse(resp.getJsonStr());
            for(int i=0; i < array.size(); i++) {
                JsonObject obj = array.get(i).getAsJsonObject();
                recommendedTracks.add(obj.get("id").getAsString());
            }
        }
        return resp;
    }

    public static Object refreshAIPlaylist(String playlistId, String jwt) {

        if(playlistId != null) {
            getRecommendedTracks(jwt);

            JsonArray arr = new JsonArray();
            for(String id : recommendedTracks) {
                arr.add("spotify:track:" + id);
            }
            JsonObject obj = new JsonObject();
            obj.add("uris", arr);

            String api = "/v1/playlists/" + playlistId + "/tracks";
            SoftwareResponse resp = Client.makeApiCall(api, HttpPut.METHOD_NAME, obj.toString(), "Bearer " + MusicStore.getSpotifyAccessToken());
            if (resp.isOk()) {
                return resp;
            }
        }
        return null;
    }

    public static Object createPlaylist(String playlistName) {
        if(MusicStore.spotifyUserId == null) {
            Apis.getUserProfile(MusicStore.getSpotifyAccessToken());
        }

        JsonObject obj = new JsonObject();
        obj.addProperty("name", playlistName);
        obj.addProperty("public", "false");

        String api = "/v1/users/" + MusicStore.spotifyUserId + "/playlists";
        SoftwareResponse resp = Client.makeApiCall(api, HttpPost.METHOD_NAME, obj.toString(), "Bearer " + MusicStore.getSpotifyAccessToken());
        if (resp.isOk()) {
            Apis.getUserPlaylists(MusicStore.getSpotifyUserId(), MusicStore.getSpotifyAccessToken());
        }
        return resp;
    }

    public static Object addTracksInPlaylist(String playlistId, Set<String> tracks) {

        if(playlistId != null) {
            JsonArray arr = new JsonArray();
            Object[] array = tracks.toArray();
            for(Object id : array) {
                arr.add("spotify:track:" + id);
            }
            JsonObject obj = new JsonObject();
            obj.add("uris", arr);
            obj.addProperty("position", 0);

            String api = "/v1/playlists/" + playlistId + "/tracks";
            SoftwareResponse resp = Client.makeApiCall(api, HttpPost.METHOD_NAME, obj.toString(), "Bearer " + MusicStore.getSpotifyAccessToken());
            if (resp.isOk() || resp.getCode() == 403) {
                return resp;
            }
        }
        return null;
    }

    public static Object updatePlaylist(String playlistId, JsonObject tracks) {

        if(playlistId != null) {
            JsonArray arr = new JsonArray();
            if (tracks != null && tracks.has("items")) {

                for(JsonElement array : tracks.get("items").getAsJsonArray()) {
                    JsonObject track = array.getAsJsonObject().get("track").getAsJsonObject();
                    arr.add("spotify:track:" + track.get("id").getAsString());
                }
            }
            JsonObject obj = new JsonObject();
            obj.add("uris", arr);

            String api = "/v1/playlists/" + playlistId + "/tracks";
            SoftwareResponse resp = Client.makeApiCall(api, HttpPost.METHOD_NAME, obj.toString(), "Bearer " + MusicStore.getSpotifyAccessToken());
            if (resp.isOk()) {
                return resp;
            }
        }
        return null;
    }

    public static Object removeTracksInPlaylist(String playlistId, Set<String> tracks) {

        if(playlistId != null) {
            JsonArray arr = new JsonArray();
            Object[] array = tracks.toArray();
            for(Object id : array) {
                JsonObject uri = new JsonObject();
                uri.addProperty("uri", "spotify:track:" + id);
                arr.add(uri);
            }
            JsonObject obj = new JsonObject();
            obj.add("tracks", arr);

            String api = "/v1/playlists/" + playlistId + "/tracks";
            SoftwareResponse resp = Client.makeApiCall(api, HttpDelete.METHOD_NAME, obj.toString(), "Bearer " + MusicStore.getSpotifyAccessToken());
            if (resp.isOk() || resp.getCode() == 403) {
                return resp;
            }
        }
        return null;
    }

    public static boolean removePlaylist(String playlistId) {
        if(playlistId != null) {
            String api = "/v1/playlists/" + playlistId + "/followers";
            SoftwareResponse resp = Client.makeApiCall(api, HttpDelete.METHOD_NAME, null, "Bearer " + MusicStore.getSpotifyAccessToken());
            if (resp.isOk()) {
                return true;
            }
        }
        return false;
    }

    public static Object getAITopTracks(String accessToken, String playlistId) {
        if(playlistId != null) {
            SoftwareResponse resp = (SoftwareResponse) Apis.getTracksByPlaylistId(accessToken, playlistId);
            if(resp != null) {
                JsonObject obj = resp.getJsonObj();
                if (obj != null && obj.has("tracks")) {
                    JsonObject tracks = obj.get("tracks").getAsJsonObject();
                    myAITopTracks.clear();
                    for (JsonElement array : tracks.get("items").getAsJsonArray()) {
                        JsonObject track = array.getAsJsonObject().get("track").getAsJsonObject();
                        myAITopTracks.put(track.get("id").getAsString(), track.get("name").getAsString());
                    }
                }
                return obj;
            }
        }
        return null;
    }

    public static Object getGenre(String accessToken) {
        String api = "/v1/recommendations/available-genre-seeds";
        SoftwareResponse resp = Client.makeApiCall(api, HttpGet.METHOD_NAME, null, accessToken);
        return resp.getJsonObj();
    }

    public static Object getRecommendationForTracks(String accessToken, Map<String, String> queryParameter) {
        String api = "/v1/recommendations";
        if(queryParameter != null && queryParameter.size() > 0) {
            api += "?";
            Set<String> keys = queryParameter.keySet();
            for(String key : keys) {
                api += key + "=" + queryParameter.get(key) + "&";
            }
            api = api.substring(0, api.lastIndexOf("&"));
        }

        SoftwareResponse resp = Client.makeApiCall(api, HttpGet.METHOD_NAME, null, accessToken);
        return resp.getJsonObj();
    }
}
