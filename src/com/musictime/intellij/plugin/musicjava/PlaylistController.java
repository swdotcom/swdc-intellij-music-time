package com.musictime.intellij.plugin.musicjava;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.musictime.intellij.plugin.music.MusicControlManager;
import swdc.java.ops.http.ClientResponse;
import swdc.java.ops.http.OpsHttpClient;
import swdc.java.ops.manager.FileUtilManager;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PlaylistController {
    public static final Logger LOG = Logger.getLogger("PlaylistController");

    public static Map<String, String> likedTracks = new HashMap<>();
    public static Map<String, String> topTracks = new HashMap<>();
    public static Map<String, String> myAITopTracks = new HashMap<>();
    public static List<String> recommendedTracks = new ArrayList<>();

    private static String getSpotifyAccessToken() {
        return FileUtilManager.getItem("spotify_access_token");
    }

    /*
     * Get spotify top tracks
     */
    public static ClientResponse getTopSpotifyTracks() {

        String api = "/v1/me/top/tracks?time_range=medium_term&limit=50";
        ClientResponse resp = OpsHttpClient.spotifyGet(api, getSpotifyAccessToken());
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
            JsonObject jsonResp = resp.getJsonObj();
            if (jsonResp != null && jsonResp.has("error")) {
                if(MusicControlManager.requiresSpotifyAccessTokenRefresh(jsonResp)) {
                    MusicControlManager.refreshAccessToken();
                    resp = OpsHttpClient.spotifyGet(api, getSpotifyAccessToken());
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
                }
            }
        }
        return resp;
    }

    /*
     * Get spotify liked tracks
     * @param
     * accessToken - spotify access token
     */
    public static ClientResponse getLikedSpotifyTracks() {
        String api = "/v1/me/tracks?limit=50&offset=0";
        ClientResponse resp = OpsHttpClient.spotifyGet(api, getSpotifyAccessToken());
        if (resp != null && !resp.isOk() && !resp.getJsonObj().isJsonNull()) {
            JsonObject tracks = resp.getJsonObj();
            if (tracks != null && tracks.has("error")) {
                if(MusicControlManager.requiresSpotifyAccessTokenRefresh(tracks)) {
                    // refresh
                    MusicControlManager.refreshAccessToken();
                    // fetch again
                    resp = OpsHttpClient.spotifyGet(api, getSpotifyAccessToken());
                }
            }
        }
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

    /*
     * Generate software AI playlist
     */
    public static ClientResponse generateAIPlaylist() {
        if(MusicStore.spotifyUserId == null) {
            Apis.getUserProfile();
        }

        JsonObject obj = new JsonObject();
        obj.addProperty("name", "My AI Top 40");
        obj.addProperty("public", "false");

        String api = "/v1/users/" + MusicStore.spotifyUserId + "/playlists";
        ClientResponse resp = OpsHttpClient.spotifyPost(api, getSpotifyAccessToken(), obj);
        if (resp.isOk()) {
            Apis.getUserPlaylists(MusicStore.getSpotifyUserId());
        }
        return resp;
    }

    /*
     * Send generated AI playlist info to software.com
     * @param
     * payload - playlist data
     * jwt - software jwt token
     */
    public static ClientResponse sendPlaylistToSoftware(JsonObject payload) {
        String api = "/music/playlist/generated";
        return OpsHttpClient.spotifyPost(api, getSpotifyAccessToken(), payload);
    }

    /*
     * Get recommended tracks from software.com
     * @param
     * jwt - software jwt token
     */
    public static ClientResponse getRecommendedTracks() {

        String api = "/music/recommendations?limit=40";
        ClientResponse resp = OpsHttpClient.spotifyGet(api, getSpotifyAccessToken());
        if(resp.isOk()) {
            recommendedTracks.clear();
            JsonArray array = (JsonArray) JsonParser.parseString(resp.getJsonStr());
            for(int i=0; i < array.size(); i++) {
                JsonObject obj = array.get(i).getAsJsonObject();
                recommendedTracks.add(obj.get("id").getAsString());
            }
        }
        return resp;
    }


    /*
     * Refresh software AI playlist
     * @param
     * playlistId - spotify playlist id
     * jwt - software jwt token
     */
    public static ClientResponse refreshAIPlaylist(String playlistId) {
        if(playlistId != null) {
            getRecommendedTracks();

            JsonArray arr = new JsonArray();
            for(String id : recommendedTracks) {
                arr.add("spotify:track:" + id);
            }
            JsonObject obj = new JsonObject();
            obj.add("uris", arr);

            String api = "/v1/playlists/" + playlistId + "/tracks";
            ClientResponse resp = OpsHttpClient.spotifyPut(api, getSpotifyAccessToken(), obj);
            if (resp.isOk()) {
                return resp;
            } else if(!resp.getJsonObj().isJsonNull()) {
                JsonObject jsonResp = resp.getJsonObj();
                if (jsonResp != null && jsonResp.has("error")) {
                    if(MusicControlManager.requiresSpotifyAccessTokenRefresh(jsonResp)) {
                        MusicControlManager.refreshAccessToken();
                        resp = OpsHttpClient.spotifyPut(api, getSpotifyAccessToken(), obj);
                        if (resp.isOk()) {
                            return resp;
                        }
                    }
                }
            }
        }
        return new ClientResponse();
    }

    /*
     * Create spotify playlist
     * @param
     * playlistName - spotify playlist name
     */
    public static ClientResponse createPlaylist(String playlistName) {
        if(MusicStore.spotifyUserId == null) {
            Apis.getUserProfile();
        }

        JsonObject obj = new JsonObject();
        obj.addProperty("name", playlistName);
        obj.addProperty("public", "false");

        String api = "/v1/users/" + MusicStore.spotifyUserId + "/playlists";
        ClientResponse resp = OpsHttpClient.spotifyPost(api, getSpotifyAccessToken(), obj);
        if (resp.isOk()) {
            Apis.getUserPlaylists(MusicStore.getSpotifyUserId());
        } else if(!resp.getJsonObj().isJsonNull()) {
            JsonObject jsonResp = resp.getJsonObj();
            if (jsonResp != null && jsonResp.has("error")) {
                if(MusicControlManager.requiresSpotifyAccessTokenRefresh(jsonResp)) {
                    MusicControlManager.refreshAccessToken();
                    resp = OpsHttpClient.spotifyPost(api, getSpotifyAccessToken(), obj);
                    if (resp.isOk()) {
                        return resp;
                    }
                }
            }
        }
        return resp;
    }

    /*
     * Add tracks in spotify playlist at position 0
     * @param
     * playlistId - spotify playlist id
     * tracks - list of tracks to add in playlist
     */
    public static ClientResponse addTracksInPlaylist(String playlistId, Set<String> tracks) {

        ClientResponse resp = null;
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
            resp = OpsHttpClient.spotifyPost(api, getSpotifyAccessToken(), obj);
            if (resp.isOk()) {
                return resp;
            } else if(!resp.getJsonObj().isJsonNull()) {
                JsonObject jsonResp = resp.getJsonObj();
                if (jsonResp != null && jsonResp.has("error")) {
                    if(MusicControlManager.requiresSpotifyAccessTokenRefresh(jsonResp)) {
                        MusicControlManager.refreshAccessToken();
                        resp = OpsHttpClient.spotifyPost(api, getSpotifyAccessToken(), obj);
                        if (resp.isOk()) {
                            return resp;
                        }
                    }
                }
            }
        }
        if (resp == null) {
            return new ClientResponse();
        }
        return resp;
    }

    /*
     * Update spotify playlist tracks with new tracks
     * @param
     * playlistId - spotify playlist id
     * tracks - tracks to be replaced with current tracks in playlist
     */
    public static ClientResponse updatePlaylist(String playlistId, JsonObject tracks) {
        ClientResponse resp = null;
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
            resp = OpsHttpClient.spotifyPost(api, getSpotifyAccessToken(), obj);
            if (resp.isOk()) {
                return resp;
            } else if(!resp.getJsonObj().isJsonNull()) {
                JsonObject jsonResp = resp.getJsonObj();
                if (jsonResp != null && jsonResp.has("error")) {
                    if(MusicControlManager.requiresSpotifyAccessTokenRefresh(jsonResp)) {
                        MusicControlManager.refreshAccessToken();
                        resp = OpsHttpClient.spotifyPost(api, getSpotifyAccessToken(), obj);
                        if (resp.isOk()) {
                            return resp;
                        }
                    }
                }
            }
        }
        if (resp == null) {
            return new ClientResponse();
        }
        return resp;
    }

    /*
     * Remove specific tracks from spotify playlist
     * @param
     * playlistId - spotify playlist id
     * tracks - list of tracks to remove from playlist
     */
    public static ClientResponse removeTracksInPlaylist(String playlistId, Set<String> tracks) {
        ClientResponse resp = null;
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
            resp = OpsHttpClient.spotifyDelete(api, getSpotifyAccessToken(), obj);
            if (resp.isOk()) {
                return resp;
            } else if(!resp.getJsonObj().isJsonNull()) {
                JsonObject jsonResp = resp.getJsonObj();
                if (jsonResp != null && jsonResp.has("error")) {
                    if(MusicControlManager.requiresSpotifyAccessTokenRefresh(jsonResp)) {
                        MusicControlManager.refreshAccessToken();
                        resp = OpsHttpClient.spotifyDelete(api, getSpotifyAccessToken(), obj);
                        if (resp.isOk()) {
                            return resp;
                        }
                    }
                }
            }
        }
        if (resp == null) {
            return new ClientResponse();
        }
        return resp;
    }

    /*
     * Remove playlist from your spotify account
     * @param
     * playlistId - spotify playlist id
     */
    public static boolean removePlaylist(String playlistId) {
        if(playlistId != null) {
            String api = "/v1/playlists/" + playlistId + "/followers";
            ClientResponse resp = OpsHttpClient.spotifyDelete(api, getSpotifyAccessToken(), null);
            if (resp.isOk()) {
                return true;
            } else if(!resp.getJsonObj().isJsonNull()) {
                JsonObject jsonResp = resp.getJsonObj();
                if (jsonResp != null && jsonResp.has("error")) {
                    if(MusicControlManager.requiresSpotifyAccessTokenRefresh(jsonResp)) {
                        MusicControlManager.refreshAccessToken();
                        resp = OpsHttpClient.spotifyDelete(api, getSpotifyAccessToken(), null);
                        if (resp.isOk()) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /*
     * Get AI top tracks from spotify (custom AI playlist of software)
     * @param
     * accessToken - spotify access token
     * playlistId - spotify playlist id
     */
    public static Object getAITopTracks(String playlistId) {
        if(playlistId != null) {
            ClientResponse resp = Apis.getTracksByPlaylistId(playlistId);
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

    /*
     * Get spotify genres
     * @param
     * accessToken - spotify access token
     */
    public static Object getGenre() {
        String api = "/v1/recommendations/available-genre-seeds";
        ClientResponse resp = OpsHttpClient.spotifyGet(api, getSpotifyAccessToken());
        return resp.getJsonObj();
    }

    /*
     * Get spotify recommended tracks
     * @param
     * accessToken - spotify access token
     * queryParameter - parameters to apply on recommendation. Required: (seed_tracks | seed_genres | seed_artists)
     * for more info check: https://developer.spotify.com/documentation/web-api/reference/browse/get-recommendations/
     */
    public static Object getRecommendationForTracks(Map<String, String> queryParameter) {
        String api = "/v1/recommendations";
        if(queryParameter != null && queryParameter.size() > 0) {
            api += "?";
            Set<String> keys = queryParameter.keySet();
            for(String key : keys) {
                api += key + "=" + queryParameter.get(key) + "&";
            }
            api = api.substring(0, api.lastIndexOf("&"));
        }

        ClientResponse resp = OpsHttpClient.spotifyGet(api, getSpotifyAccessToken());
        return resp.getJsonObj();
    }
}
