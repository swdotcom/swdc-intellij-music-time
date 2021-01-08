package com.musictime.intellij.plugin.musicjava;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.musictime.intellij.plugin.music.MusicControlManager;
import com.musictime.intellij.plugin.music.PlayListCommands;
import swdc.java.ops.http.ClientResponse;
import swdc.java.ops.http.OpsHttpClient;
import swdc.java.ops.manager.FileUtilManager;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class MusicController {

    public static final Logger LOG = Logger.getLogger("Controller");

    private static String getSpotifyAccessToken() {
        return FileUtilManager.getItem("spotify_access_token");
    }

    public static ClientResponse playLikedOrRecommendedTracks(String deviceId, String playlistId, String trackId) {
        JsonObject obj;
        List<String> tracks = new ArrayList<>();
        if (playlistId.equals(PlayListCommands.likedPlaylistId)) {
            obj = PlayListCommands.likedTracks;
            if (obj != null && obj.has("items")) {
                for (JsonElement array : obj.get("items").getAsJsonArray()) {
                    JsonObject track = array.getAsJsonObject().get("track").getAsJsonObject();
                    tracks.add(track.get("id").getAsString());
                }
            }
        } else if (playlistId.equals(PlayListCommands.recommendedPlaylistId)) {
            PlayListCommands.updateCurrentRecommended();
            obj = PlayListCommands.currentRecommendedTracks;
            if (obj != null && obj.has("tracks")) {
                for (JsonElement array : obj.getAsJsonArray("tracks")) {
                    JsonObject track = array.getAsJsonObject();
                    tracks.add(track.get("id").getAsString());
                }
            }
        }
        return MusicController.playSpotifyTracks(deviceId, trackId, tracks);
    }

    /*
     * Play spotify track with playlist id
     * @param
     * deviceId - spotify device id
     * playlistId - spotify playlist id
     * trackId - spotify track id
     * accessToken - spotify access token
     */
    public static ClientResponse playSpotifyPlaylist(String deviceId, String playlistId, String trackId) {

        if (deviceId == null) {
            return new ClientResponse();
        }

        ClientResponse resp = null;

        if(playlistId != null && playlistId.length() > 5) {
            JsonObject obj = new JsonObject();
            obj.addProperty("context_uri", "spotify:playlist:" + playlistId);

            if (trackId != null) {
                JsonObject offset = new JsonObject();
                offset.addProperty("uri", "spotify:track:" + trackId);

                obj.add("offset", offset);
            }

            String api = "/v1/me/player/play?device_id=" + deviceId;
            resp = OpsHttpClient.spotifyPut(api, getSpotifyAccessToken(), obj);
            if (Apis.refreshAccessTokenIfExpired(resp.getJsonObj())) {
                OpsHttpClient.spotifyPut(api, getSpotifyAccessToken(), obj);
            }
        } else {
            resp = playSpotifyWebTrack(deviceId, trackId);
        }

        if (resp == null) {
            return new ClientResponse();
        }

        return resp;
    }

    public static void playDesktopPlaylist(String playerName, String playlist) { Util.playPlaylist(playerName, playlist); }

    public static void playDesktopTrackInPlaylist(String playerName, String playlist, String track) { Util.playSongInPlaylist(playerName, playlist, track); }

    public static void playDesktopTrack(String playerName, String track) { Util.playTrack(playerName, track); }

    /*
     * Play spotify track without any context
     * @param
     * deviceId - spotify device id
     * trackId - spotify track id
     * accessToken - spotify access token
     */
    public static ClientResponse playSpotifyWebTrack(String deviceId, String trackId) {

        if (deviceId == null) {
            return new ClientResponse();
        }

        JsonObject obj = new JsonObject();
        if(trackId != null) {
            JsonArray array = new JsonArray();
            array.add("spotify:track:" + trackId);
            obj.add("uris", array);
        }

        ClientResponse resp = null;
        if(deviceId != null) {
            String api = "/v1/me/player/play?device_id=" + deviceId;
            resp = OpsHttpClient.spotifyPut(api, getSpotifyAccessToken(), obj);
            if (Apis.refreshAccessTokenIfExpired(resp.getJsonObj())) {
                resp = OpsHttpClient.spotifyPut(api, getSpotifyAccessToken(), obj);
            }
        }

        if (resp == null) {
            return new ClientResponse();
        }

        return resp;
    }

    /*
     * Play spotify tracks without playlist id
     * @param
     * deviceId - spotify device id
     * trackId - spotify track id
     * tracks - list of tracks to play as context (max 50)
     * accessToken - spotify access token
     */
    public static ClientResponse playSpotifyTracks(String deviceId, String trackId, List<String> tracks) {

        if (deviceId == null) {
            return new ClientResponse();
        }

        JsonObject obj = new JsonObject();
        if(tracks != null && tracks.size() > 0) {
            if(trackId != null) {
                JsonArray arr = new JsonArray();

                for(String track : tracks) {
                    if (track.indexOf("spotify:track") == -1) {
                        arr.add("spotify:track:" + track);
                    } else {
                        arr.add(track);
                    }
                }

                obj.add("uris", arr);
                int index = tracks.indexOf(trackId);
                if(index >= 0) {
                    JsonObject pos = new JsonObject();
                    pos.addProperty("position", index);
                    obj.add("offset", pos);
                }
            }
        }

        ClientResponse resp = null;
        if(deviceId != null) {
            String api = "/v1/me/player/play?device_id=" + deviceId;
            resp = OpsHttpClient.spotifyPut(api, getSpotifyAccessToken(), obj);
            if (Apis.refreshAccessTokenIfExpired(resp.getJsonObj())) {
                resp = OpsHttpClient.spotifyPut(api, getSpotifyAccessToken(), obj);
            }
        }

        if (resp == null) {
            return new ClientResponse();
        }

        return resp;
    }

    //***** Player controls ****************
    public static ClientResponse playSpotifyWeb(String deviceId) {
        ClientResponse resp = null;
        if(deviceId != null) {
            String api = "/v1/me/player/play?device_id=" + deviceId;
            resp = OpsHttpClient.spotifyPut(api, getSpotifyAccessToken(), null);
            if (Apis.refreshAccessTokenIfExpired(resp.getJsonObj())) {
                resp = OpsHttpClient.spotifyPut(api, getSpotifyAccessToken(), null);
            }
        }
        if (resp == null) {
            return new ClientResponse();
        }
        return resp;
    }

    public static boolean playSpotifyDesktop() {
        return Util.playPlayer("Spotify").equals("");
    }

    public static ClientResponse pauseSpotifyWeb(String deviceId) {
        ClientResponse resp = null;
        if (deviceId != null) {
            String api = "/v1/me/player/pause?device_id=" + deviceId;
            resp = OpsHttpClient.spotifyPut(api, getSpotifyAccessToken(), null);
            if (Apis.refreshAccessTokenIfExpired(resp.getJsonObj())) {
                resp = OpsHttpClient.spotifyPut(api, getSpotifyAccessToken(), null);
            }
        }
        if (resp == null) {
            return new ClientResponse();
        }
        return resp;
    }

    public static boolean pauseSpotifyDesktop() {
        return Util.pausePlayer("Spotify").equals("");
    }

    public static ClientResponse previousSpotifyWeb(String deviceId) {
        ClientResponse resp = null;
        if(deviceId != null) {
            String api = "/v1/me/player/previous?device_id=" + deviceId;
            resp = OpsHttpClient.spotifyPost(api, getSpotifyAccessToken(), null);
            if (Apis.refreshAccessTokenIfExpired(resp.getJsonObj())) {
                resp = OpsHttpClient.spotifyPost(api, getSpotifyAccessToken(), null);
            }
        }
        if (resp == null) {
            return new ClientResponse();
        }
        return resp;
    }

    public static boolean previousSpotifyDesktop() {
        return Util.previousTrack("Spotify").equals("");
    }

    public static ClientResponse nextSpotifyWeb(String deviceId) {
        ClientResponse resp = null;
        if(deviceId != null) {
            String api = "/v1/me/player/next?device_id=" + deviceId;
            resp = OpsHttpClient.spotifyPost(api, getSpotifyAccessToken(), null);
            if (Apis.refreshAccessTokenIfExpired(resp.getJsonObj())) {
                resp = OpsHttpClient.spotifyPost(api, getSpotifyAccessToken(), null);
            }
        }
        if (resp == null) {
            return new ClientResponse();
        }
        return resp;
    }

    public static boolean nextSpotifyDesktop() {
        return Util.nextTrack("Spotify").equals("");
    }
    //********* End Player controls ******************

    /*
     * Like/Unlike song
     * @param
     * like - true/false
     * trackId - only track id not uri
     * accessToken - spotify access token
     */
    public static ClientResponse likeSpotifyWeb(boolean like, String trackId) {
        ClientResponse resp;
        if (like) {
            // Add to liked playlist
            JsonObject obj = new JsonObject();
            if(trackId != null) {
                JsonArray array = new JsonArray();
                array.add(trackId);
                obj.add("ids", array);
            }

            String api = "/v1/me/tracks";
            resp = OpsHttpClient.spotifyPut(api, getSpotifyAccessToken(), obj);
            if (Apis.refreshAccessTokenIfExpired(resp.getJsonObj())) {
                resp = OpsHttpClient.spotifyPut(api, getSpotifyAccessToken(), obj);
            }
        }
        // remove from liked playlist
        String api = "/v1/me/tracks?ids=" + trackId;
        resp = OpsHttpClient.spotifyDelete(api, getSpotifyAccessToken(), null);
        if (Apis.refreshAccessTokenIfExpired(resp.getJsonObj())) {
            resp = OpsHttpClient.spotifyDelete(api, getSpotifyAccessToken(), null);
        }
        return resp;
    }

    /*
     * Turn on/off repeat
     * @param
     * repeatOn - true/false
     * accessToken - spotify access token
     *
     * response success code 204
     */
    public static ClientResponse updateRepeatMode(boolean repeatOn) {
        String state = repeatOn ? "track" : "off";
        String api = "/v1/me/player/repeat?state=" + state;
        ClientResponse resp = OpsHttpClient.spotifyPut(api, getSpotifyAccessToken(), null);
        if (Apis.refreshAccessTokenIfExpired(resp.getJsonObj())) {
            resp = OpsHttpClient.spotifyPut(api, getSpotifyAccessToken(), null);
        }
        return resp;
    }

    /*
     * Turn on/off shuffle
     * @param
     * shuffleOn - true/false
     * accessToken - spotify access token
     *
     * response success code 204
     */
    public static ClientResponse updateShuffleMode(boolean shuffleOn) {
        String api = "/v1/me/player/shuffle?state=" + shuffleOn;
        ClientResponse resp = OpsHttpClient.spotifyPut(api, getSpotifyAccessToken(), null);
        if (Apis.refreshAccessTokenIfExpired(resp.getJsonObj())) {
            resp = OpsHttpClient.spotifyPut(api, getSpotifyAccessToken(), null);
        }
        return resp;
    }

    /*
     * Set volume level
     * @param
     * volumePercent - percent value of volume range (0 - 100)
     * accessToken - spotify access token
     *
     * response success code 204
     */
    public static ClientResponse updateVolumeLevel(int volumePercent) {
        String api = "/v1/me/player/volume?volume_percent=" + volumePercent;
        ClientResponse resp = OpsHttpClient.spotifyPut(api, getSpotifyAccessToken(), null);
        if (Apis.refreshAccessTokenIfExpired(resp.getJsonObj())) {
            resp = OpsHttpClient.spotifyPut(api, getSpotifyAccessToken(), null);
        }
        return resp;
    }

    /*
     * Seek playback to specific point
     * @param
     * seekMillis - milliseconds to seek
     * accessToken - spotify access token
     *
     * response success code 204
     */
    public static ClientResponse seekPlayback(long seekMillis) {
        String api = "/v1/me/player/seek?position_ms=" + seekMillis;
        ClientResponse resp = OpsHttpClient.spotifyPut(api, getSpotifyAccessToken(), null);
        if (Apis.refreshAccessTokenIfExpired(resp.getJsonObj())) {
            resp = OpsHttpClient.spotifyPut(api, getSpotifyAccessToken(), null);
        }
        return resp;
    }

    /*
    * Add track to the playback queue
    * @param
    * track - allows track_id or uri (spotify:track:<track_id>)
    * accessToken - spotify access token
    *
    * response success code 204
    */
    public static ClientResponse addToPlaybackQueue(String track) {
        if(!track.contains("track")) {
            track = "spotify:track:" + track;
        }
        String api = "/v1/me/player/queue?uri=" + track;
        ClientResponse resp = OpsHttpClient.spotifyPost(api, getSpotifyAccessToken(), null);
        if (Apis.refreshAccessTokenIfExpired(resp.getJsonObj())) {
            resp = OpsHttpClient.spotifyPost(api, getSpotifyAccessToken(), null);
        }
        return resp;
    }


}
