package com.softwareco.intellij.plugin.music;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.softwareco.intellij.plugin.SoftwareCoSessionManager;
import com.softwareco.intellij.plugin.SoftwareCoUtils;
import com.softwareco.intellij.plugin.musicjava.MusicController;
import com.softwareco.intellij.plugin.musicjava.SoftwareResponse;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

public class PlayerControlManager {

    public static final Logger LOG = Logger.getLogger("PlayerControlManager");

    public static SoftwareResponse playSpotifyPlaylist(String playlistId, String trackId) {

        if(playlistId == null || trackId == null) {
            return new SoftwareResponse();
        }

        if(MusicControlManager.userStatus == null) {
            JsonObject obj = MusicControlManager.getUserProfile();
            if (obj != null)
                MusicControlManager.userStatus = obj.get("product").getAsString();
        }

        if(playlistId.length() < 5 && MusicControlManager.userStatus != null && MusicControlManager.userStatus.equals("premium")) {
            if (MusicControlManager.currentDeviceId != null) {
                JsonObject obj;
                List<String> tracks = new ArrayList<>();
                if(playlistId.equals("2")) {
                    obj = PlayListCommands.likedTracks;
                    if (obj != null && obj.has("items")) {
                        for(JsonElement array : obj.get("items").getAsJsonArray()) {
                            JsonObject track = array.getAsJsonObject().get("track").getAsJsonObject();
                            tracks.add(track.get("id").getAsString());
                        }
                    }
                } else if(playlistId.equals("3")) {
                    PlayListCommands.updateCurrentRecommended();
                    obj = PlayListCommands.currentRecommendedTracks;
                    if (obj != null && obj.has("tracks")) {
                        for(JsonElement array : obj.getAsJsonArray("tracks")) {
                            JsonObject track = array.getAsJsonObject();
                            tracks.add(track.get("id").getAsString());
                        }
                    }
                }
                String accessToken = "Bearer " + SoftwareCoSessionManager.getItem("spotify_access_token");
                SoftwareResponse resp = MusicController.playSpotifyTracks(MusicControlManager.currentDeviceId, trackId, tracks, accessToken);
                if (resp.isOk()) {
                    String finalTrackId = trackId;
                    String finalPlaylistId = playlistId;
                    new Thread(() -> {
                        try {
                            Thread.sleep(1000);
                            MusicControlManager.currentPlaylistId = finalPlaylistId;
                            MusicControlManager.currentTrackId = finalTrackId;
                            SoftwareCoUtils.updatePlayerControls();
                        } catch (Exception e) {
                            System.err.println(e);
                        }
                    }).start();
                }
                return resp;
            }
        } else if(MusicControlManager.playerType.equals("Web Player") || SoftwareCoUtils.isWindows()) {
            if (MusicControlManager.currentDeviceId != null) {
                String accessToken = "Bearer " + SoftwareCoSessionManager.getItem("spotify_access_token");
                SoftwareResponse resp = MusicController.playSpotifyPlaylist(MusicControlManager.currentDeviceId, playlistId, trackId, accessToken);
                if (resp.isOk()) {
                    String finalTrackId = trackId;
                    String finalPlaylistId = playlistId;
                    new Thread(() -> {
                        try {
                            Thread.sleep(1000);
                            MusicControlManager.currentPlaylistId = finalPlaylistId;
                            MusicControlManager.currentTrackId = finalTrackId;
                            SoftwareCoUtils.updatePlayerControls();
                        } catch (Exception e) {
                            System.err.println(e);
                        }
                    }).start();
                }
                return resp;
            }
        } else if(MusicControlManager.spotifyCacheState && SoftwareCoUtils.isSpotifyRunning() &&
                MusicControlManager.currentDeviceId != null) {
            if(playlistId != null && playlistId.length() > 5 && trackId != null)
                MusicController.playDesktopTrackInPlaylist("Spotify", playlistId, trackId);
            else if(playlistId != null && playlistId.length() < 5 && trackId != null)
                MusicController.playDesktopTrack("Spotify", trackId);
//            else if(playlistId != null && playlistId.length() > 5)
//                MusicController.playDesktopPlaylist("Spotify", playlistId);

            MusicControlManager.currentPlaylistId = playlistId;
            MusicControlManager.currentTrackId = trackId;
            SoftwareCoUtils.updatePlayerControls();
            SoftwareResponse resp = new SoftwareResponse();
            resp.setIsOk(true);
            resp.setCode(200);
            return resp;
        }
        return new SoftwareResponse();
    }

    public static boolean playSpotifyDevices() {

        if(MusicControlManager.playerType.equals("Web Player") || SoftwareCoUtils.isWindows()) {
            if(MusicControlManager.currentDeviceId != null) {

                String accessToken = "Bearer " + SoftwareCoSessionManager.getItem("spotify_access_token");
                SoftwareResponse resp = (SoftwareResponse) MusicController.playSpotifyWeb(MusicControlManager.currentDeviceId, accessToken);
                if (resp.isOk()) {
                    MusicControlManager.playerCounter = 0;
                    new Thread(() -> {
                        try {
                            Thread.sleep(1000);
                            SoftwareCoUtils.updatePlayerControls();
                        } catch (Exception e) {
                            System.err.println(e);
                        }
                    }).start();
                    return true;
                } else if (MusicControlManager.playerCounter < 1 && resp.getCode() == 404) {
                    MusicControlManager.getSpotifyDevices();
                    if (MusicControlManager.spotifyDeviceIds.size() > 0) {
                        if (MusicControlManager.spotifyDeviceIds.size() == 1)
                            MusicControlManager.currentDeviceId = MusicControlManager.spotifyDeviceIds.get(0);
                        MusicControlManager.playerCounter++;
                        playSpotifyDevices();
                    }
                } else if(resp.getCode() == 403 && !resp.getJsonObj().isJsonNull() && resp.getJsonObj().has("error")) {
                    JsonObject error = resp.getJsonObj().getAsJsonObject("error");
                    SoftwareCoUtils.showMsgPrompt(error.get("message").getAsString(), new Color(120, 23, 50, 100));
                }
            } else {
                MusicControlManager.getSpotifyDevices();
            }
        } else if(MusicControlManager.spotifyCacheState && SoftwareCoUtils.isMac() && SoftwareCoUtils.isSpotifyRunning()) {
            MusicController.playSpotifyDesktop();
            SoftwareCoUtils.updatePlayerControls();
            return true;
        }
        return false;
    }

    public static boolean pauseSpotifyDevices() {

        if(MusicControlManager.playerType.equals("Web Player") || SoftwareCoUtils.isWindows()) {
            if(MusicControlManager.currentDeviceId != null) {

                String accessToken = "Bearer " + SoftwareCoSessionManager.getItem("spotify_access_token");
                SoftwareResponse resp = (SoftwareResponse) MusicController.pauseSpotifyWeb(MusicControlManager.currentDeviceId, accessToken);
                if (resp.isOk()) {
                    MusicControlManager.playerCounter = 0;
                    new Thread(() -> {
                        try {
                            Thread.sleep(1000);
                            SoftwareCoUtils.updatePlayerControls();
                        } catch (Exception e) {
                            System.err.println(e);
                        }
                    }).start();
                    return true;
                } else if (MusicControlManager.playerCounter < 1 && resp.getCode() == 404) {
                    MusicControlManager.getSpotifyDevices();
                    if (MusicControlManager.spotifyDeviceIds.size() > 0) {
                        if (MusicControlManager.spotifyDeviceIds.size() == 1)
                            MusicControlManager.currentDeviceId = MusicControlManager.spotifyDeviceIds.get(0);
                        MusicControlManager.playerCounter++;
                        pauseSpotifyDevices();
                    }
                } else if(resp.getCode() == 403 && !resp.getJsonObj().isJsonNull() && resp.getJsonObj().has("error")) {
                    JsonObject error = resp.getJsonObj().getAsJsonObject("error");
                    SoftwareCoUtils.showMsgPrompt(error.get("message").getAsString(), new Color(120, 23, 50, 100));
                }
            } else {
                MusicControlManager.getSpotifyDevices();
            }
        } else if(MusicControlManager.spotifyCacheState && SoftwareCoUtils.isMac() && SoftwareCoUtils.isSpotifyRunning()) {
            MusicController.pauseSpotifyDesktop();
            SoftwareCoUtils.updatePlayerControls();
            return true;
        }
        return false;
    }

    public static boolean previousSpotifyTrack() {

        if(MusicControlManager.playerType.equals("Web Player") || SoftwareCoUtils.isWindows()) {
            if(MusicControlManager.currentDeviceId != null) {

                String accessToken = "Bearer " + SoftwareCoSessionManager.getItem("spotify_access_token");
                SoftwareResponse resp = (SoftwareResponse) MusicController.previousSpotifyWeb(MusicControlManager.currentDeviceId, accessToken);
                if (resp.isOk()) {
                    MusicControlManager.playerCounter = 0;
                    new Thread(() -> {
                        try {
                            Thread.sleep(1000);
                            SoftwareCoUtils.updatePlayerControls();
                        } catch (Exception e) {
                            System.err.println(e);
                        }
                    }).start();
                    return true;
                } else if (MusicControlManager.playerCounter < 1 && resp.getCode() == 204) {
                    MusicControlManager.playerCounter++;
                    playSpotifyPlaylist(null, null);
                    previousSpotifyTrack();
                } else if (MusicControlManager.playerCounter < 1 && resp.getCode() == 404) {
                    MusicControlManager.getSpotifyDevices();
                    if (MusicControlManager.spotifyDeviceIds.size() > 0) {
                        if (MusicControlManager.spotifyDeviceIds.size() == 1)
                            MusicControlManager.currentDeviceId = MusicControlManager.spotifyDeviceIds.get(0);
                        MusicControlManager.playerCounter++;
                        previousSpotifyTrack();
                    }
                } else if(resp.getCode() == 403 && !resp.getJsonObj().isJsonNull() && resp.getJsonObj().has("error")) {
                    JsonObject error = resp.getJsonObj().getAsJsonObject("error");
                    SoftwareCoUtils.showMsgPrompt(error.get("message").getAsString(), new Color(120, 23, 50, 100));
                }
            } else {
                MusicControlManager.getSpotifyDevices();
            }
        } else if(MusicControlManager.spotifyCacheState && SoftwareCoUtils.isMac() && SoftwareCoUtils.isSpotifyRunning()) {
            MusicController.previousSpotifyDesktop();
            SoftwareCoUtils.updatePlayerControls();
            return true;
        }
        return false;
    }

    public static boolean nextSpotifyTrack() {

        if(MusicControlManager.playerType.equals("Web Player") || SoftwareCoUtils.isWindows()) {
            if(MusicControlManager.currentDeviceId != null) {

                String accessToken = "Bearer " + SoftwareCoSessionManager.getItem("spotify_access_token");
                SoftwareResponse resp = (SoftwareResponse) MusicController.nextSpotifyWeb(MusicControlManager.currentDeviceId, accessToken);
                if (resp.isOk()) {
                    MusicControlManager.playerCounter = 0;
                    new Thread(() -> {
                        try {
                            Thread.sleep(1000);
                            SoftwareCoUtils.updatePlayerControls();
                        } catch (Exception e) {
                            System.err.println(e);
                        }
                    }).start();
                    return true;
                } else if (MusicControlManager.playerCounter < 1 && resp.getCode() == 204) {
                    MusicControlManager.playerCounter++;
                    playSpotifyPlaylist(null, null);
                    nextSpotifyTrack();
                } else if (MusicControlManager.playerCounter < 1 && resp.getCode() == 404) {
                    MusicControlManager.getSpotifyDevices();
                    if (MusicControlManager.spotifyDeviceIds.size() > 0) {
                        if (MusicControlManager.spotifyDeviceIds.size() == 1)
                            MusicControlManager.currentDeviceId = MusicControlManager.spotifyDeviceIds.get(0);
                        MusicControlManager.playerCounter++;
                        nextSpotifyTrack();
                    }
                } else if(resp.getCode() == 403 && !resp.getJsonObj().isJsonNull() && resp.getJsonObj().has("error")) {
                    JsonObject error = resp.getJsonObj().getAsJsonObject("error");
                    SoftwareCoUtils.showMsgPrompt(error.get("message").getAsString(), new Color(120, 23, 50, 100));
                }
            } else {
                MusicControlManager.getSpotifyDevices();
            }
        } else if(MusicControlManager.spotifyCacheState && SoftwareCoUtils.isMac() && SoftwareCoUtils.isSpotifyRunning()) {
            MusicController.nextSpotifyDesktop();
            SoftwareCoUtils.updatePlayerControls();
            return true;
        }
        return false;
    }

    public static boolean likeSpotifyTrack(boolean like, String trackId) {

        if(trackId != null) {
            String accessToken = "Bearer " + SoftwareCoSessionManager.getItem("spotify_access_token");
            SoftwareResponse resp = (SoftwareResponse) MusicController.likeSpotifyWeb(like, trackId, accessToken);
            if (resp.isOk()) {
                MusicControlManager.playerCounter = 0;
                new Thread(() -> {
                    try {
                        Thread.sleep(1000);
                        PlayListCommands.updatePlaylists(3, null);
                        SoftwareCoUtils.updatePlayerControls();
                        SoftwareCoUtils.sendLikedTrack(like, trackId, "spotify");
                    } catch (Exception e) {
                        System.err.println(e);
                    }
                }).start();
                return true;
            } else if(resp.getCode() == 403 && !resp.getJsonObj().isJsonNull() && resp.getJsonObj().has("error")) {
                JsonObject error = resp.getJsonObj().getAsJsonObject("error");
                SoftwareCoUtils.showMsgPrompt(error.get("message").getAsString(), new Color(120, 23, 50, 100));
            }
        }
        return false;
    }
}
