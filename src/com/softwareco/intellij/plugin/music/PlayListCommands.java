package com.softwareco.intellij.plugin.music;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.softwareco.intellij.plugin.SoftwareCoSessionManager;
import com.softwareco.intellij.plugin.actions.MusicToolWindow;
import com.softwareco.intellij.plugin.musicjava.PlaylistController;
import com.softwareco.intellij.plugin.musicjava.SoftwareResponse;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PlayListCommands {
    public static final Logger LOG = Logger.getLogger("PlayListCommands");
    public static JsonObject topSpotifyTracks = null;
    public static String topSpotifyPlaylistId = "6jCkTED0V5NEuM8sKbGG1Z"; // Software Top 40 playlist_id
    public static JsonObject likedTracks = null;
    public static String likedPlaylistId = "2";
    public static JsonObject myAITopTracks = null;
    public static String myAIPlaylistId = null;
    public static List<String> userPlaylistIds = new ArrayList<>();
    public static Map<String, String> userPlaylists = new HashMap<>(); // <playlist_id, playlist_name>
    public static Map<String, JsonObject> userTracks = new HashMap<>(); // <playlist_id, tracks>
    public static String sortType = "Latest";
    public static int counter = 0;

    /*
    * type = 0 to get all playlists and sort them
    * type = 1 to update software top 40 playlist
    * type = 2 to update my ai top 40 playlist
    * type = 3 to update liked songs
    * type = 4 to update user playlist with playlist id
    * type = 5 to refresh playlist window
    */
    public static synchronized void updatePlaylists(int type, String playlistId) {
        if(type == 0) {
            PlaylistManager.getUserPlaylists(); // API call
            // Sort Playlists ****************************************************
            if (userPlaylists.size() > 0) {

                Map<String, String> sortedPlaylist = new LinkedHashMap<>();

                if (!sortType.equals("Latest")) {
                    sortedPlaylist = sortHashMapByValues((HashMap<String, String>) userPlaylists);
                    userPlaylistIds.clear();
                } else {
                    sortedPlaylist = userPlaylists;
                }

                Set<String> ids = sortedPlaylist.keySet();
                for (String id : ids) {
                    if (!sortType.equals("Latest")) {
                        userPlaylistIds.add(id);
                    }
                }
            }
            // End Sort Playlists ***************************************************************
        } else if(type == 1) {
            // Software Top 40 Playlist ******************************************
            JsonObject obj = PlaylistManager.getTracksByPlaylistId(topSpotifyPlaylistId); // API call
            if (obj != null && obj.has("tracks"))
                topSpotifyTracks = obj.get("tracks").getAsJsonObject();
            // End Software Top 40 ***************************************************************
        } else if(type == 2) {
            // My AI Top 40 ******************************************************
            myAITopTracks = getAITopTracks(); // API call
            // End My AI Top 40 ***************************************************************
        } else if(type == 3) {
            // Liked Songs Playlist **********************************************
            likedTracks = getLikedSpotifyTracks(); // API call
            // End Liked Songs ***************************************************************
        } else if(type == 4 && playlistId != null) {
            // User Playlists ****************************************************
            userTracks.put(playlistId, PlaylistManager.getTracksByPlaylistId(playlistId)); // API call
            // End User Playlists ***************************************************************
        }

        MusicToolWindow.triggerRefresh();
    }

    public static LinkedHashMap<String, String> sortHashMapByValues(
            HashMap<String, String> passedMap) {
        List<String> mapKeys = new ArrayList<>(passedMap.keySet());
        List<String> mapValues = new ArrayList<>(passedMap.values());
        Collections.sort(mapValues);
        Collections.sort(mapKeys);

        LinkedHashMap<String, String> sortedMap =
                new LinkedHashMap<>();

        Iterator<String> valueIt = mapValues.iterator();
        while (valueIt.hasNext()) {
            String val = valueIt.next();
            Iterator<String> keyIt = mapKeys.iterator();

            while (keyIt.hasNext()) {
                String key = keyIt.next();
                String comp1 = passedMap.get(key);
                String comp2 = val;

                if (comp1.equals(comp2)) {
                    keyIt.remove();
                    sortedMap.put(key, val);
                    break;
                }
            }
        }
        return sortedMap;
    }

    public static void sortAtoZ() {
        sortType = "Sort A-Z";
        updatePlaylists(0, null);
    }

    public static void sortLatest() {
        sortType = "Latest";
        updatePlaylists(0, null);
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
            PlaylistManager.getUserPlaylists();
            String jwt = SoftwareCoSessionManager.getItem("jwt");
            JsonObject obj = new JsonObject();
            obj.addProperty("playlist_id", myAIPlaylistId);
            obj.addProperty("playlistTypeId", 1);
            obj.addProperty("name", "My AI Top 40");

            PlaylistController.sendPlaylistToSoftware(obj.toString(), jwt);

            refreshAIPlaylist();
            return resp.getJsonObj();
        }
        return null;
    }

    public static JsonObject refreshAIPlaylist() {

        if(myAIPlaylistId != null) {
            String jwt = SoftwareCoSessionManager.getItem("jwt");
            SoftwareResponse resp = (SoftwareResponse) PlaylistController.refreshAIPlaylist(myAIPlaylistId, jwt);
            if (resp.isOk()) {
                myAITopTracks = getAITopTracks();
                updatePlaylists(5, null);
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

    public static JsonObject addTracksInPlaylist(String playlist_id, Set<String> tracks) {

        if(playlist_id != null) {
            SoftwareResponse resp = (SoftwareResponse) PlaylistController.addTracksInPlaylist(playlist_id, tracks);
            if (resp.isOk()) {
                return resp.getJsonObj();
            }
        }
        return null;
    }

    public static JsonObject updatePlaylist(String playlist_id, JsonObject tracks) {

        if(playlist_id != null) {
            SoftwareResponse resp = (SoftwareResponse) PlaylistController.updatePlaylist(playlist_id, tracks);
            if (resp.isOk()) {
                return resp.getJsonObj();
            }
        }
        return null;
    }

    public static JsonObject removeTracksInPlaylist(String playlist_id, Set<String> tracks) {

        if(playlist_id != null) {
            SoftwareResponse resp = (SoftwareResponse) PlaylistController.removeTracksInPlaylist(playlist_id, tracks);
            if (resp != null && resp.isOk()) {
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
