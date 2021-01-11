package com.musictime.intellij.plugin.music;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.musictime.intellij.plugin.musicjava.PlaylistController;
import com.musictime.intellij.plugin.tree.MusicToolWindow;
import com.musictime.intellij.plugin.tree.PlaylistAction;
import swdc.java.ops.http.ClientResponse;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PlayListCommands {
    public static final Logger LOG = Logger.getLogger("PlayListCommands");
    public static JsonObject topSpotifyTracks = null;
    public static String topSpotifyPlaylistId = "6jCkTED0V5NEuM8sKbGG1Z"; // Software Top 40 playlist_id
    public static JsonObject likedTracks = null;
    public static JsonArray likedTracksList = new JsonArray();
    public static String likedPlaylistId = "Liked Songs";
    public static List<String> genres = new ArrayList<>();
    public static String selectedGenre = null;
    public static JsonObject recommendedTracks = new JsonObject(); // All recommended tracks (limit 100)
    public static String recommendationTitle = "";
    public static JsonObject currentRecommendedTracks = new JsonObject(); // 50 recommended tracks to play
    public static int currentBatch = 1; // range 1-10 for 100 tracks (max 10 batches)
    public static String recommendedPlaylistId = "__musictime-recs__";
    public static String slackWorkspacesId = "__slack-workspaces__";
    public static String addSlackWorkspaceId = "__add-slack-workspace__";
    public static JsonObject myAITopTracks = null;
    public static String myAIPlaylistId = null;
    public static List<String> userPlaylistIds = new ArrayList<>();
    public static Map<String, String> userPlaylists = new HashMap<>(); // <playlist_id, playlist_name>
    public static Map<String, JsonObject> userTracks = new HashMap<>(); // <playlist_id, tracks>
    public static String sortType = "Latest";
    public static int counter = 0;

    public static String getPlaylistIdByPlaylistName(String playlistName) {
        for (String key : userPlaylists.keySet()) {
            String playlist_name = userPlaylists.get(key);
            if (playlist_name != null && playlist_name.equals(playlistName)) {
                return key;
            }
        }
        return null;
    }

    /*
    * type = 0 (GET_ALL_PLAYLISTS)to get all playlists and sort them
    * type = 1 (UPDATE_SOFTWARE_TOP_FORTY)to update software top 40 playlist
    * type = 2 (UPDATE_MY_AI_PLAYLIST)to update my ai top 40 playlist
    * type = 3 (UPDATE_LIKED_SONGS)to update liked songs
    * type = 4 (UPDATE_PLAYLIST_BY_ID)to update user playlist with playlist id
    * type = 5 (REFRESH_PLAYLIST_WINDOW)to refresh playlist window
    */
    public static synchronized void updatePlaylists(PlaylistAction type, String playlistId) {
        switch (type) {
            case GET_ALL_PLAYLISTS:
                PlaylistManager.getUserPlaylists(); // API call
                // Sort Playlists ****************************************************
                if (userPlaylists.size() > 0 && sortType.equals("Sort A-Z")) {
                    sortHashMapByValues((HashMap<String, String>) userPlaylists);
                }
                break;
            case UPDATE_SOFTWARE_TOP_FORTY:
                JsonObject obj = PlaylistManager.getTracksByPlaylistId(topSpotifyPlaylistId); // API call
                if (obj != null && obj.has("tracks"))
                    topSpotifyTracks = obj.get("tracks").getAsJsonObject();
                break;
            case UPDATE_MY_AI_PLAYLIST:
                myAITopTracks = getAITopTracks(); // API call
                break;
            case UPDATE_LIKED_SONGS:
                JsonObject tracks = getLikedSpotifyTracks(); // API call
                if(tracks != null) {
                    // this is just the top level (the tracks are found in the items)
                    likedTracks = tracks;
                    likedTracksList = new JsonArray();
                    // set the raw liked tracks
                    if (likedTracks != null && likedTracks.get("items") != null) {
                        JsonArray items = likedTracks.getAsJsonArray("items");
                        if (items != null && items.size() > 0) {
                            for (JsonElement el : items) {
                                likedTracksList.add(el.getAsJsonObject().getAsJsonObject("track"));
                            }
                        }
                    }
                }
                break;
            case UPDATE_PLAYLIST_BY_ID:
                userTracks.put(playlistId, PlaylistManager.getTracksByPlaylistId(playlistId)); // API call
                break;
            case REFRESH_PLAYLIST_WINDOW:
                break;
        }

        if(genres.size() == 0) {
            getGenre();
        }

        MusicToolWindow.refresh();
    }

    public static LinkedHashMap<String, String> sortHashMapByValues(
            HashMap<String, String> passedMap) {
        List<String> mapKeys = new ArrayList<>(passedMap.keySet());
        List<String> mapValues = new ArrayList<>(passedMap.values());
        List<String> values = new ArrayList<>();
        for(String value : mapValues) {
            values.add(value.toLowerCase());
        }
        Collections.sort(values);
        Collections.sort(mapKeys);

        LinkedHashMap<String, String> sortedMap =
                new LinkedHashMap<>();
        userPlaylistIds.clear();

        Iterator<String> valueIt = values.iterator();
        while (valueIt.hasNext()) {
            String val = valueIt.next().toLowerCase();
            Iterator<String> keyIt = mapKeys.iterator();

            while (keyIt.hasNext()) {
                String key = keyIt.next();
                String comp1 = passedMap.get(key).toLowerCase();
                String comp2 = val;

                if (comp1.equals(comp2)) {
                    keyIt.remove();
                    sortedMap.put(key, val);
                    userPlaylistIds.add(key);
                    break;
                }
            }
        }
        return sortedMap;
    }

    public static void sortAtoZ() {
        sortType = "Sort A-Z";
        updatePlaylists(PlaylistAction.GET_ALL_PLAYLISTS, null);
    }

    public static void sortLatest() {
        sortType = "Latest";
        updatePlaylists(PlaylistAction.GET_ALL_PLAYLISTS, null);
    }

    public static void updateRecommendation(String type, String selected) {
        recommendationTitle = selected;
        if(type.equals("genre")) {
            selectedGenre = selected;
            Map<String, String> queryParams = new HashMap<>();
            queryParams.put("limit", "100");
            queryParams.put("min_popularity", "20");
            queryParams.put("target_popularity", "90");

            queryParams.put("seed_genres", selected);
            PlayListCommands.getRecommendationForTracks(queryParams);
        } else if(type.equals("category")) {
            Map<String, String> queryParams = new HashMap<>();
            queryParams.put("limit", "100");
            queryParams.put("min_popularity", "30");
            queryParams.put("target_popularity", "100");

            String trackIds = "";
            int categoryCounter = 0;
            if(MusicControlManager.likedTracks.size() > 0) {
                Set<String> ids = MusicControlManager.likedTracks.keySet();
                for(String id : ids) {
                    if(categoryCounter < 5)
                        trackIds += id + ",";
                    else
                        break;

                    categoryCounter++;
                }
                trackIds = trackIds.substring(0, trackIds.lastIndexOf(","));
            } else if(userPlaylistIds.size() > 0){
                /* if no liked tracks */
                JsonObject obj = null;
                for(String id : userPlaylistIds) {
                    if(userTracks.containsKey(id)) {
                        obj = userTracks.get(id);
                        break;
                    }
                }
                if(obj == null) {
                    obj = PlaylistManager.getTracksByPlaylistId(userPlaylistIds.get(0));
                }
                if(obj != null) {
                    JsonObject tracks = obj.get("tracks").getAsJsonObject();
                    JsonArray items = tracks.get("items").getAsJsonArray();
                    for (JsonElement array : items) {
                        JsonObject track = array.getAsJsonObject().get("track").getAsJsonObject();
                        if(categoryCounter < 5)
                            trackIds += track.get("id").getAsString() + ",";
                        else
                            break;

                        categoryCounter++;
                    }
                    trackIds = trackIds.substring(0, trackIds.lastIndexOf(","));
                }
            } else {
                updatePlaylists(PlaylistAction.UPDATE_SOFTWARE_TOP_FORTY, null); // Update software playlist
                JsonObject obj = topSpotifyTracks;
                if (obj != null && obj.has("items")) {
                    JsonArray items = obj.get("items").getAsJsonArray();
                    for (JsonElement array : items) {
                        JsonObject track = array.getAsJsonObject().get("track").getAsJsonObject();
                        if(categoryCounter < 5)
                            trackIds += track.get("id").getAsString() + ",";
                        else
                            break;

                        categoryCounter++;
                    }
                    trackIds = trackIds.substring(0, trackIds.lastIndexOf(","));
                }
            }
            if(trackIds.length() > 0) {
                queryParams.put("seed_tracks", trackIds);
            }

            switch (selected) {
                case "Happy":
                    queryParams.put("min_valence", "0.5");
                    queryParams.put("target_valence", "1");
                    break;
                case "Energetic":
                    queryParams.put("min_energy", "0.5");
                    queryParams.put("target_energy", "1");
                    break;
                case "Danceable":
                    queryParams.put("min_danceability", "0.6");
                    queryParams.put("target_danceability", "1");
                    break;
                case "Instrumental":
                    queryParams.put("min_instrumentalness", "0.5");
                    queryParams.put("target_instrumentalness", "1");
                    break;
                case "Quiet music":
                    queryParams.put("max_loudness", "-10");
                    queryParams.put("target_loudness", "-50");
                    break;
            }

            PlayListCommands.getRecommendationForTracks(queryParams);
        }
        currentBatch = 1;
        updateCurrentRecommended();
        MusicToolWindow.refresh();
    }

    public static void updateSearchedSongsRecommendations() {
        if (recommendedTracks != null) {
            JsonArray recommendArray = recommendedTracks.getAsJsonArray("tracks");
            JsonArray array = new JsonArray();
            int maxSize = Math.min(50, recommendArray.size());
            for (int i = 0; i < maxSize; i++) {
                array.add(recommendArray.get(i));
            }
            currentRecommendedTracks.add("tracks", array);
        }
    }

    public static void updateCurrentRecommended() {
        if(recommendedTracks != null) {
            JsonArray recommendArray = recommendedTracks.getAsJsonArray("tracks");
            if(recommendArray != null && recommendArray.size() > 0) {
                if (currentBatch < 6) {
                    currentRecommendedTracks = new JsonObject();
                    JsonArray array = new JsonArray();
                    for (int i = 0; i < 50; i++) {
                        array.add(recommendArray.get(i));
                    }
                    currentRecommendedTracks.add("tracks", array);
                } else {
                    currentRecommendedTracks = new JsonObject();
                    JsonArray array = new JsonArray();
                    for (int i = 50; i < recommendArray.size(); i++) {
                        array.add(recommendArray.get(i));
                    }
                    currentRecommendedTracks.add("tracks", array);
                }
            }
        }
    }

    public static JsonObject getTopSpotifyTracks() {

        ClientResponse resp = PlaylistController.getTopSpotifyTracks();
        if (resp != null && resp.isOk()) {
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
        ClientResponse resp = PlaylistController.getLikedSpotifyTracks();
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

        ClientResponse resp = PlaylistController.generateAIPlaylist();
        if (resp.isOk()) {
            PlaylistManager.getUserPlaylists();
            refreshAIPlaylist();

            JsonObject obj = new JsonObject();
            obj.addProperty("playlist_id", myAIPlaylistId);
            obj.addProperty("playlistTypeId", 1);
            obj.addProperty("name", "My AI Top 40");

            PlaylistController.sendPlaylistToSoftware(obj);
            return resp.getJsonObj();
        }
        return null;
    }

    public static JsonObject refreshAIPlaylist() {

        if(myAIPlaylistId != null) {
            ClientResponse resp = PlaylistController.refreshAIPlaylist(myAIPlaylistId);
            if (resp.isOk()) {
                myAITopTracks = getAITopTracks();
                updatePlaylists(PlaylistAction.REFRESH_PLAYLIST_WINDOW, null);
                return resp.getJsonObj();
            }
        }
        return null;
    }

    public static JsonObject createPlaylist(String playlistName) {
        ClientResponse resp = PlaylistController.createPlaylist(playlistName);
        if (resp.isOk()) {
            updatePlaylists(PlaylistAction.GET_ALL_PLAYLISTS, null);
            return resp.getJsonObj();
        }
        return null;
    }

    public static JsonObject addTracksInPlaylist(String playlist_id, Set<String> tracks) {

        if(playlist_id != null) {
            ClientResponse resp = PlaylistController.addTracksInPlaylist(playlist_id, tracks);
            if (resp.isOk()) {
                return resp.getJsonObj();
            } else if(resp.getCode() == 403) {
                return resp.getJsonObj();
            }
        }
        return null;
    }

    public static JsonObject updatePlaylist(String playlist_id, JsonObject tracks) {

        if(playlist_id != null) {
            ClientResponse resp = PlaylistController.updatePlaylist(playlist_id, tracks);
            if (resp.isOk()) {
                return resp.getJsonObj();
            }
        }
        return null;
    }

    public static JsonObject removeTracksInPlaylist(String playlist_id, Set<String> tracks) {

        if(playlist_id != null) {
            ClientResponse resp = PlaylistController.removeTracksInPlaylist(playlist_id, tracks);
            if (resp.isOk()) {
                return resp.getJsonObj();
            } else if(resp.getCode() == 403) {
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


    public static boolean removePlaylist(String playlistId) {
        boolean state = PlaylistController.removePlaylist(playlistId);
        if(state)
            updatePlaylists(PlaylistAction.GET_ALL_PLAYLISTS, null);

        return state;
    }

    public static JsonObject getGenre() {
        JsonObject obj = (JsonObject) PlaylistController.getGenre();
        if(obj != null && obj.has("genres")) {
            JsonArray genre = obj.getAsJsonArray("genres");
            PlayListCommands.genres.clear();
            for(JsonElement element : genre) {
                PlayListCommands.genres.add(element.getAsString());
            }
        }
        return obj;
    }

    public static JsonObject getRecommendationForTracks(Map<String, String> queryParameter) {
        JsonObject obj = (JsonObject) PlaylistController.getRecommendationForTracks(queryParameter);
        if(obj != null && obj.has("tracks")) {
            PlayListCommands.recommendedTracks = obj;
        }
        return obj;
    }
}
