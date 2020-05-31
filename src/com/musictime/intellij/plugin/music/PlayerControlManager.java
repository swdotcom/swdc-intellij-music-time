package com.musictime.intellij.plugin.music;

import com.google.gson.JsonObject;
import com.google.protobuf.Api;
import com.musictime.intellij.plugin.SoftwareCoUtils;
import com.musictime.intellij.plugin.SoftwareResponse;
import com.musictime.intellij.plugin.models.DeviceInfo;
import com.musictime.intellij.plugin.musicjava.*;
import org.apache.commons.lang.StringUtils;
import org.apache.http.client.methods.HttpGet;

import java.awt.*;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Logger;

public class PlayerControlManager {

    public static final Logger LOG = Logger.getLogger("PlayerControlManager");

    public static SoftwareResponse playSpotifyPlaylist() {

        DeviceInfo currentDevice = DeviceManager.getBestDeviceOption();

        if (currentDevice == null || MusicControlManager.currentPlaylistId == null) {
            return null;
        }

        // if it's a liked or recommended song then play it with offset context
        SoftwareResponse resp = null;
        if (MusicControlManager.currentPlaylistId.equals(PlayListCommands.likedPlaylistId) ||
                MusicControlManager.currentPlaylistId.equals(PlayListCommands.recommendedPlaylistId)) {
            // liked or recommended playlist
            resp = MusicController.playLikedOrRecommendedTracks(currentDevice.id, MusicControlManager.currentPlaylistId, MusicControlManager.currentTrackId);
        } else {
            // normal playlist
            resp = MusicController.playSpotifyPlaylist(currentDevice.id, MusicControlManager.currentPlaylistId, MusicControlManager.currentTrackId);
        }

        if (resp != null &&
                resp.getCode() == 403 &&
                !resp.getJsonObj().isJsonNull() &&
                resp.getJsonObj().has("error")) {
            JsonObject error = resp.getJsonObj().getAsJsonObject("error");
            if (error.get("reason").getAsString().equals("PREMIUM_REQUIRED")) {
                // try playing playSongInPlaylist
                if (Util.isMac()) {
                    String result = Util.playSongInPlaylist("spotify", MusicControlManager.currentPlaylistId, MusicControlManager.currentTrackId);
                    if (StringUtils.isBlank(result)) {
                        PlaylistManager.gatherMusicInfoRequest();
                    }
                } else {
                    SoftwareCoUtils.showMsgPrompt(error.get("message").getAsString(), new Color(120, 23, 50, 100));
                }
            } else if (error.get("reason").getAsString().equals("UNKNOWN")) {
                SoftwareCoUtils.showMsgPrompt("We were unable to play the selected track<br> because it is unavailable in your market.", new Color(120, 23, 50, 100));
            }
        }

        PlaylistManager.gatherMusicInfoRequest();

        return resp;
    }

    public static void playIt() {
        DeviceInfo currentDevice = DeviceManager.getBestDeviceOption();
        if (currentDevice == null) {
            return;
        }
        SoftwareResponse resp = MusicController.playSpotifyWeb(currentDevice.id);
        if (resp.getCode() == 403 && !resp.getJsonObj().isJsonNull() && resp.getJsonObj().has("error")) {
            JsonObject error = resp.getJsonObj().getAsJsonObject("error");
            if (error.get("reason").getAsString().equals("PREMIUM_REQUIRED")) {
                // try playing playSongInPlaylist
                if (Util.isMac()) {
                    Util.playPlayer("spotify");
                } else {
                    SoftwareCoUtils.showMsgPrompt(error.get("message").getAsString(), new Color(120, 23, 50, 100));
                }
            } else if (error.get("reason").getAsString().equals("UNKNOWN")) {
                SoftwareCoUtils.showMsgPrompt("We were unable to play the selected track<br> because it is unavailable in your market.", new Color(120, 23, 50, 100));
            }
        }

        PlaylistManager.gatherMusicInfoRequest();
    }

    public static void pauseIt() {
        DeviceInfo currentDevice = DeviceManager.getBestDeviceOption();
        if (currentDevice == null) {
            return;
        }
        SoftwareResponse resp = MusicController.pauseSpotifyWeb(currentDevice.id);
        if (resp.getCode() == 403 && !resp.getJsonObj().isJsonNull() && resp.getJsonObj().has("error")) {
            JsonObject error = resp.getJsonObj().getAsJsonObject("error");
            if (error.get("reason").getAsString().equals("PREMIUM_REQUIRED")) {
                // try playing playSongInPlaylist
                if (Util.isMac()) {
                    Util.pausePlayer("spotify");
                } else {
                    SoftwareCoUtils.showMsgPrompt(error.get("message").getAsString(), new Color(120, 23, 50, 100));
                }
            } else if (error.get("reason").getAsString().equals("UNKNOWN")) {
                SoftwareCoUtils.showMsgPrompt("We were unable to pause the selected track<br> because it is unavailable in your market.", new Color(120, 23, 50, 100));
            }
        }

        PlaylistManager.gatherMusicInfoRequest();
    }

    public static void previousIt() {
        DeviceInfo currentDevice = DeviceManager.getBestDeviceOption();
        if (currentDevice == null) {
            return;
        }
        SoftwareResponse resp = MusicController.previousSpotifyWeb(currentDevice.id);
        if (resp.getCode() == 403 && !resp.getJsonObj().isJsonNull() && resp.getJsonObj().has("error")) {
            JsonObject error = resp.getJsonObj().getAsJsonObject("error");
            if (error.get("reason").getAsString().equals("PREMIUM_REQUIRED")) {
                // try playing playSongInPlaylist
                if (Util.isMac()) {
                    Util.previousTrack("spotify");
                } else {
                    SoftwareCoUtils.showMsgPrompt(error.get("message").getAsString(), new Color(120, 23, 50, 100));
                }
            } else if (error.get("reason").getAsString().equals("UNKNOWN")) {
                SoftwareCoUtils.showMsgPrompt("We were unable to go to the previous track<br> because it is unavailable in your market.", new Color(120, 23, 50, 100));
            }
        }

        PlaylistManager.gatherMusicInfoRequest();
    }

    public static void nextIt() {
        DeviceInfo currentDevice = DeviceManager.getBestDeviceOption();
        if (currentDevice == null) {
            return;
        }
        SoftwareResponse resp = MusicController.nextSpotifyWeb(currentDevice.id);
        if (resp.getCode() == 403 && !resp.getJsonObj().isJsonNull() && resp.getJsonObj().has("error")) {
            JsonObject error = resp.getJsonObj().getAsJsonObject("error");
            if (error.get("reason").getAsString().equals("PREMIUM_REQUIRED")) {
                // try playing playSongInPlaylist
                if (Util.isMac()) {
                    Util.nextTrack("spotify");
                } else {
                    SoftwareCoUtils.showMsgPrompt(error.get("message").getAsString(), new Color(120, 23, 50, 100));
                }
            } else if (error.get("reason").getAsString().equals("UNKNOWN")) {
                SoftwareCoUtils.showMsgPrompt("We were unable to go to the next track<br> because it is unavailable in your market.", new Color(120, 23, 50, 100));
            }
        }

        PlaylistManager.gatherMusicInfoRequest();
    }

    /**
     * Set the track to Liked or remove from the Liked playlist
     * @param like
     * @param trackId
     * @return
     */
    public static boolean likeSpotifyTrack(boolean like, String trackId) {
        try {
            if (trackId != null) {
                SoftwareResponse resp = MusicController.likeSpotifyWeb(like, trackId);
                if (resp != null && resp.getCode() == 401) {
                    Apis.refreshAccessToken();
                    resp = MusicController.likeSpotifyWeb(like, trackId);
                }
                if (resp.isOk()) {
                    new Timer().schedule(new TimerTask() {
                        @Override
                        public void run() {
                            PlayListCommands.updatePlaylists(3, null);
                            SoftwareCoUtils.sendLikedTrack(like, trackId, "spotify");
                            PlaylistManager.gatherMusicInfoRequest();
                        }
                    }, 1000);
                    return true;
                } else if (resp.getCode() == 403 && !resp.getJsonObj().isJsonNull() && resp.getJsonObj().has("error")) {
                    JsonObject error = resp.getJsonObj().getAsJsonObject("error");
                    SoftwareCoUtils.showMsgPrompt(error.get("message").getAsString(), new Color(120, 23, 50, 100));
                }
            }
        } catch (Exception e) {
            //
        }
        return false;
    }
}
