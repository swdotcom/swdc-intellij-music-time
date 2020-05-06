package com.musictime.intellij.plugin.musicjava;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.musictime.intellij.plugin.SoftwareCoSessionManager;
import com.musictime.intellij.plugin.SoftwareCoUtils;
import com.musictime.intellij.plugin.SoftwareResponse;
import com.musictime.intellij.plugin.music.MusicControlManager;
import com.musictime.intellij.plugin.music.PlayerControlManager;
import com.musictime.intellij.plugin.music.PlaylistManager;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MusicController {

    public static final Logger LOG = Logger.getLogger("Controller");

    /*
     * Play spotify track with playlist id
     * @param
     * deviceId - spotify device id
     * playlistId - spotify playlist id
     * trackId - spotify track id
     * accessToken - spotify access token
     */
    public static SoftwareResponse playSpotifyPlaylist(String deviceId, String playlistId, String trackId, String accessToken) {

        if(deviceId == null) {
            Apis.getSpotifyDevices(accessToken);
            deviceId = MusicStore.getCurrentDeviceId();
        }

        SoftwareResponse resp = null;

        if(playlistId != null && playlistId.length() > 5) {
            JsonObject obj = new JsonObject();
            obj.addProperty("context_uri", "spotify:playlist:" + playlistId);

            if (trackId != null) {
                JsonObject offset = new JsonObject();
                offset.addProperty("uri", "spotify:track:" + trackId);

                obj.add("offset", offset);
            }

            if (deviceId != null) {
                String api = "/v1/me/player/play?device_id=" + deviceId;
                resp = Client.makeSpotifyApiCall(api, HttpPut.METHOD_NAME, obj.toString(), accessToken);
            }
        } else {
            resp = playSpotifyWebTrack(deviceId, trackId, accessToken);
        }

        if (resp == null) {
            return new SoftwareResponse();
        }
        // check to see if the song is playing
        playSongIfNotPlaying(deviceId, accessToken);
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
    public static SoftwareResponse playSpotifyWebTrack(String deviceId, String trackId, String accessToken) {

        if(deviceId == null) {
            Apis.getSpotifyDevices(accessToken);
            deviceId = MusicStore.getCurrentDeviceId();
        }

        JsonObject obj = new JsonObject();
        if(trackId != null) {
            JsonArray array = new JsonArray();
            array.add("spotify:track:" + trackId);
            obj.add("uris", array);
        }

        SoftwareResponse resp = null;
        if(deviceId != null) {
            String api = "/v1/me/player/play?device_id=" + deviceId;
            resp = Client.makeSpotifyApiCall(api, HttpPut.METHOD_NAME, obj.toString(), accessToken);
        }

        if (resp == null) {
            return new SoftwareResponse();
        }

        // check to see if the song is playing
        playSongIfNotPlaying(deviceId, accessToken);

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
    public static SoftwareResponse playSpotifyTracks(String deviceId, String trackId, List<String> tracks, String accessToken) {

        if(deviceId == null) {
            Apis.getSpotifyDevices(accessToken);
            deviceId = MusicStore.getCurrentDeviceId();
        }

        JsonObject obj = new JsonObject();
        if(tracks != null && tracks.size() > 0) {
            if(trackId != null) {
                JsonArray arr = new JsonArray();

                for(String track : tracks) {
                    arr.add("spotify:track:" + track);
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

        SoftwareResponse resp = null;
        if(deviceId != null) {
            String api = "/v1/me/player/play?device_id=" + deviceId;
            resp = Client.makeSpotifyApiCall(api, HttpPut.METHOD_NAME, obj.toString(), accessToken);
        }

        if (resp == null) {
            return new SoftwareResponse();
        }

        // check to see if the song is playing
        playSongIfNotPlaying(deviceId, accessToken);

        return resp;
    }

    private static void playSongIfNotPlaying(String deviceId, String accessToken) {
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                // check to see if the song is playing
                JsonObject jsonObj = PlaylistManager.getSpotifyWebCurrentTrack();

                if (jsonObj != null && jsonObj.has("is_playing")) {
                    if (!jsonObj.get("is_playing").getAsBoolean()) {
                        PlayerControlManager.playSpotifyDevices();
                    }
                }
            }
        }, 3000);
    }

    //***** Player controls ****************
    public static Object playSpotifyWeb(String deviceId, String accessToken) {
        if(deviceId != null) {

            String api = "/v1/me/player/play?device_id=" + deviceId;
            return Client.makeSpotifyApiCall(api, HttpPut.METHOD_NAME, null, accessToken);
        }
        return false;
    }

    public static boolean playSpotifyDesktop() {
        return Util.playPlayer("Spotify").equals("");
    }

    public static Object pauseSpotifyWeb(String deviceId, String accessToken) {
        if(deviceId != null) {

            String api = "/v1/me/player/pause?device_id=" + deviceId;
            return Client.makeSpotifyApiCall(api, HttpPut.METHOD_NAME, null, accessToken);
        }
        return null;
    }

    public static boolean pauseSpotifyDesktop() {
        return Util.pausePlayer("Spotify").equals("");
    }

    public static Object previousSpotifyWeb(String deviceId, String accessToken) {
        if(deviceId != null) {

            String api = "/v1/me/player/previous?device_id=" + deviceId;
            return Client.makeSpotifyApiCall(api, HttpPost.METHOD_NAME, null, accessToken);
        }
        return null;
    }

    public static boolean previousSpotifyDesktop() {
        return Util.previousTrack("Spotify").equals("");
    }

    public static Object nextSpotifyWeb(String deviceId, String accessToken) {
        if(deviceId != null) {

            String api = "/v1/me/player/next?device_id=" + deviceId;
            return Client.makeSpotifyApiCall(api, HttpPost.METHOD_NAME, null, accessToken);
        }
        return null;
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
    public static Object likeSpotifyWeb(boolean like, String trackId, String accessToken) {
        if(like) {
            // Add to liked playlist
            JsonObject obj = new JsonObject();
            if(trackId != null) {
                JsonArray array = new JsonArray();
                array.add(trackId);
                obj.add("ids", array);
            }

            String api = "/v1/me/tracks";
            return Client.makeSpotifyApiCall(api, HttpPut.METHOD_NAME, obj.toString(), accessToken);
        } else {
            // remove from liked playlist
            String api = "/v1/me/tracks?ids=" + trackId;
            return Client.makeSpotifyApiCall(api, HttpDelete.METHOD_NAME, null, accessToken);
        }
    }
    //const api = `/music/liked/track/${track.id}?type=${type}`; type = spotify

    /*
     * Turn on/off repeat
     * @param
     * repeatOn - true/false
     * accessToken - spotify access token
     *
     * response success code 204
     */
    public static Object updateRepeatMode(boolean repeatOn, String accessToken) {
        String state = repeatOn ? "track" : "off";
        String api = "/v1/me/player/repeat?state=" + state;
        SoftwareResponse resp = Client.makeSpotifyApiCall(api, HttpPut.METHOD_NAME, null, accessToken);
        if(!resp.getJsonObj().isJsonNull()) {
            JsonObject tracks = resp.getJsonObj();
            if (tracks != null && tracks.has("error")) {
                if(MusicControlManager.requiresSpotifyAccessTokenRefresh(tracks)) {
                    Apis.refreshAccessToken(null, null, null);
                    resp = Client.makeSpotifyApiCall(api, HttpPut.METHOD_NAME, null, accessToken);
                }
            }
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
    public static Object updateShuffleMode(boolean shuffleOn, String accessToken) {
        String api = "/v1/me/player/shuffle?state=" + shuffleOn;
        SoftwareResponse resp = Client.makeSpotifyApiCall(api, HttpPut.METHOD_NAME, null, accessToken);
        if(!resp.getJsonObj().isJsonNull()) {
            JsonObject tracks = resp.getJsonObj();
            if (tracks != null && tracks.has("error")) {
                if(MusicControlManager.requiresSpotifyAccessTokenRefresh(tracks)) {
                    Apis.refreshAccessToken(null, null, null);
                    resp = Client.makeSpotifyApiCall(api, HttpPut.METHOD_NAME, null, accessToken);
                }
            }
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
    public static Object updateVolumeLevel(int volumePercent, String accessToken) {
        String api = "/v1/me/player/volume?volume_percent=" + volumePercent;
        SoftwareResponse resp = Client.makeSpotifyApiCall(api, HttpPut.METHOD_NAME, null, accessToken);
        if(!resp.getJsonObj().isJsonNull()) {
            JsonObject tracks = resp.getJsonObj();
            if (tracks != null && tracks.has("error")) {
                if(MusicControlManager.requiresSpotifyAccessTokenRefresh(tracks)) {
                    Apis.refreshAccessToken(null, null, null);
                    resp = Client.makeSpotifyApiCall(api, HttpPut.METHOD_NAME, null, accessToken);
                }
            }
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
    public static Object seekPlayback(long seekMillis, String accessToken) {
        String api = "/v1/me/player/seek?position_ms=" + seekMillis;
        SoftwareResponse resp = Client.makeSpotifyApiCall(api, HttpPut.METHOD_NAME, null, accessToken);
        if(!resp.getJsonObj().isJsonNull()) {
            JsonObject tracks = resp.getJsonObj();
            if (tracks != null && tracks.has("error")) {
                if(MusicControlManager.requiresSpotifyAccessTokenRefresh(tracks)) {
                    Apis.refreshAccessToken(null, null, null);
                    resp = Client.makeSpotifyApiCall(api, HttpPut.METHOD_NAME, null, accessToken);
                }
            }
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
    public static Object addToPlaybackQueue(String track, String accessToken) {
        if(!track.contains("track")) {
            track = "spotify:track:" + track;
        }
        String api = "/v1/me/player/queue?uri=" + track;
        SoftwareResponse resp = Client.makeSpotifyApiCall(api, HttpPost.METHOD_NAME, null, accessToken);
        if(!resp.getJsonObj().isJsonNull()) {
            JsonObject tracks = resp.getJsonObj();
            if (tracks != null && tracks.has("error")) {
                if(MusicControlManager.requiresSpotifyAccessTokenRefresh(tracks)) {
                    Apis.refreshAccessToken(null, null, null);
                    resp = Client.makeSpotifyApiCall(api, HttpPost.METHOD_NAME, null, accessToken);
                }
            }
        }
        return resp;
    }


}
