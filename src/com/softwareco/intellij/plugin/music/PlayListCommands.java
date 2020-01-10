package com.softwareco.intellij.plugin.music;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.softwareco.intellij.plugin.actions.MusicToolWindow;
import com.softwareco.intellij.plugin.musicjava.PlaylistController;
import com.softwareco.intellij.plugin.musicjava.SoftwareResponse;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PlayListCommands {
    public static final Logger LOG = Logger.getLogger("PlayListCommands");
    public static JsonObject topSpotifyTracks = null;
    public static String topSpotifyPlaylistId = "1";
    public static JsonObject likedTracks = null;
    public static String likedPlaylistId = "2";
    public static JsonObject myAITopTracks = null;
    public static String myAIPlaylistId = null;
    public static List<String> userPlaylistIds = new ArrayList<>();
    public static List<String> userPlaylistNames = new ArrayList<>();
    public static Map<String, String> userPlaylists = new HashMap<>(); // <playlist_name, playlist_id>
    public static Map<String, JsonObject> userTracks = new HashMap<>(); // <playlist_id, tracks>
    public static String sortType = "Latest";
    public static int counter = 0;

    public static synchronized void updatePlaylists() {
        topSpotifyTracks = getTopSpotifyTracks();
        likedTracks = getLikedSpotifyTracks();
        PlaylistManager.getUserPlaylists();
        myAITopTracks = getAITopTracks();
        if(userPlaylists.size() > 0) {
            userTracks.clear();

            if(!sortType.equals("Latest")) {
                Collections.sort(userPlaylistNames);
            }
            userPlaylistIds.clear();
            for(String name : userPlaylistNames) {
                String playlistId = userPlaylists.get(name);
                userPlaylistIds.add(playlistId);
                userTracks.put(playlistId, PlaylistManager.getTracksByPlaylistId(playlistId));
            }
        }
        MusicToolWindow.triggerRefresh();
    }

    public static void sortAtoZ() {
        sortType = "Sort A-Z";
        updatePlaylists();
    }

    public static void sortLatest() {
        sortType = "Latest";
        updatePlaylists();
    }

    public static JsonObject getTopSpotifyTracks() {

        SoftwareResponse resp = (SoftwareResponse) PlaylistController.getTopSpotifyTracks();
        if (resp.isOk()) {
            JsonObject obj = resp.getJsonObj();
            if (obj != null && obj.has("items")) {
                MusicControlManager.topTracks.clear();
                for(JsonElement array : obj.get("items").getAsJsonArray()) {
                    JsonObject track = array.getAsJsonObject();
                    MusicControlManager.topTracks.put(track.get("id").getAsString(), track.get("name").getAsString());
                }
            } else {
                LOG.log(Level.INFO, "Music Time: Unable to get Top Tracks, null response");
            }
            return resp.getJsonObj();
        }
        return null;
    }

    public static JsonObject getLikedSpotifyTracks() {

        SoftwareResponse resp = (SoftwareResponse) PlaylistController.getLikedSpotifyTracks();
        if (resp.isOk()) {
            JsonObject obj = resp.getJsonObj();
            if (obj != null && obj.has("items")) {
                MusicControlManager.likedTracks.clear();
                for(JsonElement array : obj.get("items").getAsJsonArray()) {
                    JsonObject track = array.getAsJsonObject().get("track").getAsJsonObject();
                    MusicControlManager.likedTracks.put(track.get("id").getAsString(), track.get("name").getAsString());
                }

            } else {
                LOG.log(Level.INFO, "Music Time: Unable to get Liked Tracks, null response");
            }
            return resp.getJsonObj();
        }
        return null;
    }

    public static JsonObject generateAIPlaylist() {

        SoftwareResponse resp = (SoftwareResponse) PlaylistController.generateAIPlaylist();
        if (resp.isOk()) {
            return resp.getJsonObj();
        }
        return null;
    }

    public static JsonObject refreshAIPlaylist() {

        if(myAIPlaylistId != null) {
            SoftwareResponse resp = (SoftwareResponse) PlaylistController.refreshAIPlaylist(myAIPlaylistId);
            if (resp.isOk()) {
                return resp.getJsonObj();
            }
        }
        return null;
    }

    public static JsonObject createPlaylist(String playlistName) {

        SoftwareResponse resp = (SoftwareResponse) PlaylistController.createPlaylist(playlistName);
        if (resp.isOk()) {
            return resp.getJsonObj();
        }
        return null;
    }

    public static JsonObject addTracksInPlaylist(Set<String> tracks) {

        if(myAIPlaylistId != null) {
            SoftwareResponse resp = (SoftwareResponse) PlaylistController.addTracksInPlaylist(myAIPlaylistId, tracks);
            if (resp.isOk()) {
                return resp.getJsonObj();
            }
        }
        return null;
    }

    public static JsonObject removeTracksInPlaylist(Set<String> tracks) {

        if(myAIPlaylistId != null) {
            SoftwareResponse resp = (SoftwareResponse) PlaylistController.removeTracksInPlaylist(myAIPlaylistId, tracks);
            if (resp.isOk()) {
                return resp.getJsonObj();
            }
        }
        return null;
    }

    public static JsonObject getAITopTracks() {
        if(myAIPlaylistId != null) {
            JsonObject obj = (JsonObject) PlaylistController.getAITopTracks(myAIPlaylistId);
            if (obj != null && obj.has("tracks")) {
                JsonObject tracks = obj.get("tracks").getAsJsonObject();
                MusicControlManager.myAITopTracks.clear();
                for(JsonElement array : tracks.get("items").getAsJsonArray()) {
                    JsonObject track = array.getAsJsonObject().get("track").getAsJsonObject();
                    MusicControlManager.myAITopTracks.put(track.get("id").getAsString(), track.get("name").getAsString());
                }
            }
            return obj;
        }
        return null;
    }


}
