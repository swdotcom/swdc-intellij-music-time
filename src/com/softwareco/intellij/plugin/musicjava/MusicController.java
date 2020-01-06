package com.softwareco.intellij.plugin.musicjava;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.softwareco.intellij.plugin.SoftwareCoSessionManager;
import com.softwareco.intellij.plugin.SoftwareCoUtils;
import com.softwareco.intellij.plugin.music.MusicControlManager;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;

import java.util.logging.Level;
import java.util.logging.Logger;

public class MusicController {

    public static final Logger LOG = Logger.getLogger("Controller");

    public static boolean playSpotifyPlaylist(String deviceId, String playlistId, String trackId, String accessToken) {

        if(deviceId == null) {
            Apis.getSpotifyDevices(accessToken);
            deviceId = MusicStore.getCurrentDeviceId();
        }

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
                SoftwareResponse resp = Client.makeApiCall(api, HttpPut.METHOD_NAME, obj.toString(), accessToken);
                if (resp.isOk()) {
                    return true;
                }
            }
        } else {
            return playSpotifyWebTrack(deviceId, trackId, accessToken);
        }
        return false;
    }

    public static void playDesktopPlaylist(String playerName, String playlist) { Util.playPlaylist(playerName, playlist); }

    public static void playDesktopTrackInPlaylist(String playerName, String playlist, String track) { Util.playSongInPlaylist(playerName, playlist, track); }

    public static void playDesktopTrack(String playerName, String track) { Util.playTrack(playerName, track); }

    public static boolean playSpotifyWebTrack(String deviceId, String trackId, String accessToken) {

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

        if(deviceId != null) {
            String api = "/v1/me/player/play?device_id=" + deviceId;
            SoftwareResponse resp = Client.makeApiCall(api, HttpPut.METHOD_NAME, obj.toString(), accessToken);
            if (resp.isOk()) {
                return true;
            }
        }
        return false;
    }

    public static Object playSpotifyWeb(String deviceId, String accessToken) {
        if(deviceId != null) {

            String api = "/v1/me/player/play?device_id=" + deviceId;
            SoftwareResponse resp = Client.makeApiCall(api, HttpPut.METHOD_NAME, null, accessToken);
            if (resp.isOk()) {
                return resp;
            }
            return resp;
        }
        return false;
    }

    public static void playSpotifyDesktop() {
        Util.playPlayer("Spotify");
    }

    public static Object pauseSpotifyWeb(String deviceId, String accessToken) {
        if(deviceId != null) {

            String api = "/v1/me/player/pause?device_id=" + deviceId;
            SoftwareResponse resp = Client.makeApiCall(api, HttpPut.METHOD_NAME, null, accessToken);
            if (resp.isOk()) {
                return resp;
            }
            return resp;
        }
        return null;
    }

    public static void pauseSpotifyDesktop() {
        Util.pausePlayer("Spotify");
    }

    public static Object previousSpotifyWeb(String deviceId, String accessToken) {
        if(deviceId != null) {

            String api = "/v1/me/player/previous?device_id=" + deviceId;
            SoftwareResponse resp = Client.makeApiCall(api, HttpPost.METHOD_NAME, null, accessToken);
            if (resp.isOk()) {
                return resp;
            }
            return resp;
        }
        return null;
    }

    public static void previousSpotifyDesktop() {
        Util.previousTrack("Spotify");
    }

    public static Object nextSpotifyWeb(String deviceId, String accessToken) {
        if(deviceId != null) {

            String api = "/v1/me/player/next?device_id=" + deviceId;
            SoftwareResponse resp = Client.makeApiCall(api, HttpPost.METHOD_NAME, null, accessToken);
            if (resp.isOk()) {
                return resp;
            }
            return resp;
        }
        return null;
    }

    public static void nextSpotifyDesktop() {
        Util.nextTrack("Spotify");
    }
}
