package com.softwareco.intellij.plugin.musicjava;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PlaylistController {
    public static final Logger LOG = Logger.getLogger("PlaylistController");

    public static Map<String, String> likedTracks = new HashMap<>();
    public static Map<String, String> topTracks = new HashMap<>();
    public static Map<String, String> myAITopTracks = new HashMap<>();

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
        }
        return resp;
    }

    public static Object getLikedSpotifyTracks() {

        String api = "/v1/me/tracks";
        SoftwareResponse resp = Client.makeApiCall(api, HttpGet.METHOD_NAME, null, "Bearer " + MusicStore.getSpotifyAccessToken());
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
            refreshAIPlaylist(MusicStore.myAIPlaylistId);
        }
        return resp;
    }

    public static Object refreshAIPlaylist(String playlistId) {

        if(playlistId != null) {
            Set<String> tracks = topTracks.keySet();
            JsonArray arr = new JsonArray();
            Object[] array = tracks.toArray();
            for(Object id : array) {
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
                arr.add("spotify:track:" + id);
            }
            JsonObject obj = new JsonObject();
            obj.add("tracks", arr);

            String api = "/v1/playlists/" + playlistId + "/tracks";
            SoftwareResponse resp = Client.makeApiCall(api, HttpDelete.METHOD_NAME, obj.toString(), "Bearer " + MusicStore.getSpotifyAccessToken());
            if (resp.isOk()) {
                return resp;
            }
        }
        return null;
    }

    public static Object getAITopTracks(String playlistId) {
        if(playlistId != null) {
            SoftwareResponse resp = (SoftwareResponse) Apis.getTracksByPlaylistId(playlistId);
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
}
