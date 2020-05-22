package com.musictime.intellij.plugin.music;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.musictime.intellij.plugin.SoftwareCoSessionManager;
import com.musictime.intellij.plugin.SoftwareCoUtils;
import com.musictime.intellij.plugin.SoftwareResponse;
import com.musictime.intellij.plugin.models.DeviceInfo;
import com.musictime.intellij.plugin.musicjava.Apis;
import com.musictime.intellij.plugin.musicjava.DeviceManager;
import com.musictime.intellij.plugin.musicjava.MusicController;
import com.musictime.intellij.plugin.musicjava.MusicStore;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class PlayerControlManager {

    public static final Logger LOG = Logger.getLogger("PlayerControlManager");

    public static SoftwareResponse playSpotifyPlaylist(String playlistId, String trackId, String trackName) {

        if(playlistId == null || trackId == null) {
            return new SoftwareResponse();
        }

        try {
            if (MusicStore.getSpotifyAccountType() == null) {
                Apis.getUserProfile();
            }

            boolean hasPremiumUserStatus = MusicStore.getSpotifyAccountType() != null && MusicStore.getSpotifyAccountType().equals("premium") ? true : false;
            boolean hasSpotifyAccess = MusicControlManager.hasSpotifyAccess();
            boolean isNonNamedPlaylist = (playlistId.equals(PlayListCommands.recommendedPlaylistId) || playlistId.equals(PlayListCommands.likedPlaylistId)) ? true : false;

            DeviceInfo currentDevice = DeviceManager.getBestDeviceOption();
            if (isNonNamedPlaylist && hasPremiumUserStatus) {
                if (currentDevice != null) {
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
                    SoftwareResponse resp = MusicController.playSpotifyTracks(currentDevice.id, trackId, tracks);
                    if (resp.isOk()) {
                        MusicControlManager.currentTrackId = trackId;
                        MusicControlManager.currentPlaylistId = playlistId;

                        if (trackName != null)
                            MusicControlManager.currentTrackName = trackName.substring(0, trackName.lastIndexOf("(")).trim();

                        SoftwareCoUtils.updatePlayerControls(false);
                    }
                    return resp;
                }
            } else if (MusicControlManager.playerType.equals("Web Player") || SoftwareCoUtils.isWindows()) {
                if (currentDevice != null) {

                    SoftwareResponse resp = MusicController.playSpotifyPlaylist(currentDevice.id, playlistId, trackId);
                    if (resp.isOk()) {
                        MusicControlManager.currentTrackId = trackId;
                        MusicControlManager.currentPlaylistId = playlistId;

                        if (trackName != null)
                            MusicControlManager.currentTrackName = trackName.substring(0, trackName.lastIndexOf("(")).trim();

                        SoftwareCoUtils.updatePlayerControls(false);
                    }
                    return resp;
                }
            } else if (hasSpotifyAccess && SoftwareCoUtils.isSpotifyRunning() && currentDevice != null) {
                if (playlistId != null && playlistId.length() > 5 && trackId != null) {
                    MusicController.playDesktopTrackInPlaylist("Spotify", playlistId, trackId);
                } else if (playlistId != null && playlistId.length() < 5 && trackId != null) {
                    MusicController.playDesktopTrack("Spotify", trackId);
                }

                MusicControlManager.currentPlaylistId = playlistId;
                MusicControlManager.currentTrackId = trackId;

                if (trackName != null)
                    MusicControlManager.currentTrackName = trackName.substring(0, trackName.lastIndexOf("(")).trim();

                SoftwareCoUtils.updatePlayerControls(false);
                SoftwareResponse resp = new SoftwareResponse();
                resp.setIsOk(true);
                resp.setCode(200);
                return resp;
            }
        } catch (Exception e) {
            //
        } finally {
            PlaylistManager.gatherTrackTimer();
        }

        return new SoftwareResponse();
    }

    public static boolean playSpotifyDevices() {
        boolean hasSpotifyAccess = MusicControlManager.hasSpotifyAccess();
        DeviceInfo currentDevice = DeviceManager.getBestDeviceOption();
        try {
            if (MusicControlManager.playerType.equals("Web Player") || SoftwareCoUtils.isWindows()) {
                if (currentDevice != null) {

                    SoftwareResponse resp = (SoftwareResponse) MusicController.playSpotifyWeb(currentDevice.id);
                    if (resp.isOk()) {
                        MusicControlManager.playerCounter = 0;
                        MusicControlManager.defaultbtn = "pause";
                        SoftwareCoUtils.updatePlayerControls(false);
                        return true;
                    } else if (MusicControlManager.playerCounter < 1 && resp.getCode() == 404) {
                        DeviceManager.getDevices(true);
                        if (DeviceManager.getDevices().size() > 0) {
                            MusicControlManager.playerCounter++;
                            playSpotifyDevices();
                        }
                    } else if (resp.getCode() == 403 && !resp.getJsonObj().isJsonNull() && resp.getJsonObj().has("error")) {
                        JsonObject error = resp.getJsonObj().getAsJsonObject("error");
                        SoftwareCoUtils.showMsgPrompt(error.get("message").getAsString(), new Color(120, 23, 50, 100));
                    }
                } else {
                    DeviceManager.getDevices();
                }
            } else if (hasSpotifyAccess && SoftwareCoUtils.isMac() && SoftwareCoUtils.isSpotifyRunning()) {
                boolean state = MusicController.playSpotifyDesktop();
                if (state) {
                    MusicControlManager.defaultbtn = "pause";
                }
                SoftwareCoUtils.updatePlayerControls(false);
                return true;
            }
        } catch (Exception e) {
            //
        } finally {
            PlaylistManager.gatherTrackTimer();
        }
        return false;
    }

    public static boolean pauseSpotifyDevices() {
        boolean hasSpotifyAccess = MusicControlManager.hasSpotifyAccess();
        DeviceInfo currentDevice = DeviceManager.getBestDeviceOption();
        try {
            if (MusicControlManager.playerType.equals("Web Player") || SoftwareCoUtils.isWindows()) {
                if (currentDevice != null) {

                    SoftwareResponse resp = (SoftwareResponse) MusicController.pauseSpotifyWeb(currentDevice.id);
                    if (resp.isOk()) {
                        MusicControlManager.playerCounter = 0;
                        MusicControlManager.defaultbtn = "play";
                        SoftwareCoUtils.updatePlayerControls(false);
                        return true;
                    } else if (MusicControlManager.playerCounter < 1 && resp.getCode() == 404) {
                        DeviceManager.getDevices(true);
                        if (DeviceManager.getDevices().size() > 0) {
                            MusicControlManager.playerCounter++;
                            pauseSpotifyDevices();
                        }
                    } else if (resp.getCode() == 403 && !resp.getJsonObj().isJsonNull() && resp.getJsonObj().has("error")) {
                        JsonObject error = resp.getJsonObj().getAsJsonObject("error");
                        SoftwareCoUtils.showMsgPrompt(error.get("message").getAsString(), new Color(120, 23, 50, 100));
                    }
                } else {
                    DeviceManager.getDevices();
                }
            } else if (hasSpotifyAccess && SoftwareCoUtils.isMac() && SoftwareCoUtils.isSpotifyRunning()) {
                boolean state = MusicController.pauseSpotifyDesktop();
                if (state) {
                    MusicControlManager.defaultbtn = "play";
                }
                SoftwareCoUtils.updatePlayerControls(false);

                return true;
            }
        } catch (Exception e) {
            //
        } finally {
            PlaylistManager.gatherTrackTimer();
        }
        return false;
    }

    public static boolean previousSpotifyTrack() {
        boolean hasSpotifyAccess = MusicControlManager.hasSpotifyAccess();
        DeviceInfo currentDevice = DeviceManager.getBestDeviceOption();
        try {
            if (MusicControlManager.playerType.equals("Web Player") || SoftwareCoUtils.isWindows()) {
                if (currentDevice != null) {

                    SoftwareResponse resp = (SoftwareResponse) MusicController.previousSpotifyWeb(currentDevice.id);
                    if (resp.isOk()) {
                        MusicControlManager.playerCounter = 0;
                        new Thread(() -> {
                            try {
                                MusicControlManager.changeCurrentTrack(false);
                                SoftwareCoUtils.updatePlayerControls(false);
                            } catch (Exception e) {
                                System.err.println(e);
                            }
                        }).start();
                        return true;
                    } else if (MusicControlManager.playerCounter < 1 && resp.getCode() == 204) {
                        MusicControlManager.playerCounter++;
                        playSpotifyPlaylist(null, null, null);
                        previousSpotifyTrack();
                    } else if (MusicControlManager.playerCounter < 1 && resp.getCode() == 404) {
                        DeviceManager.getDevices(true);
                        if (DeviceManager.getDevices().size() > 0) {
                            MusicControlManager.playerCounter++;
                            previousSpotifyTrack();
                        }
                    } else if (resp.getCode() == 403 && !resp.getJsonObj().isJsonNull() && resp.getJsonObj().has("error")) {
                        JsonObject error = resp.getJsonObj().getAsJsonObject("error");
                        SoftwareCoUtils.showMsgPrompt(error.get("message").getAsString(), new Color(120, 23, 50, 100));
                    }
                } else {
                    DeviceManager.getDevices();
                }
            } else if (hasSpotifyAccess && SoftwareCoUtils.isMac() && SoftwareCoUtils.isSpotifyRunning()) {
                MusicController.previousSpotifyDesktop();
                MusicControlManager.changeCurrentTrack(false);
                SoftwareCoUtils.updatePlayerControls(false);
                return true;
            }
        } catch (Exception e) {
            //
        } finally {
            PlaylistManager.gatherTrackTimer();
        }
        return false;
    }

    public static boolean nextSpotifyTrack() {
        boolean hasSpotifyAccess = MusicControlManager.hasSpotifyAccess();
        DeviceInfo currentDevice = DeviceManager.getBestDeviceOption();
        try {
            if (MusicControlManager.playerType.equals("Web Player") || SoftwareCoUtils.isWindows()) {
                if (currentDevice != null) {

                    SoftwareResponse resp = (SoftwareResponse) MusicController.nextSpotifyWeb(currentDevice.id);
                    if (resp.isOk()) {
                        MusicControlManager.playerCounter = 0;
                        new Thread(() -> {
                            try {
                                MusicControlManager.changeCurrentTrack(true);
                                SoftwareCoUtils.updatePlayerControls(false);
                            } catch (Exception e) {
                                System.err.println(e);
                            }
                        }).start();
                        return true;
                    } else if (MusicControlManager.playerCounter < 1 && resp.getCode() == 204) {
                        MusicControlManager.playerCounter++;
                        playSpotifyPlaylist(null, null, null);
                        nextSpotifyTrack();
                    } else if (MusicControlManager.playerCounter < 1 && resp.getCode() == 404) {
                        DeviceManager.getDevices(true);
                        if (DeviceManager.getDevices().size() > 0) {
                            MusicControlManager.playerCounter++;
                            nextSpotifyTrack();
                        }
                    } else if (resp.getCode() == 403 && !resp.getJsonObj().isJsonNull() && resp.getJsonObj().has("error")) {
                        JsonObject error = resp.getJsonObj().getAsJsonObject("error");
                        SoftwareCoUtils.showMsgPrompt(error.get("message").getAsString(), new Color(120, 23, 50, 100));
                    }
                } else {
                    DeviceManager.getDevices();
                }
            } else if (hasSpotifyAccess && SoftwareCoUtils.isMac() && SoftwareCoUtils.isSpotifyRunning()) {
                MusicController.nextSpotifyDesktop();
                MusicControlManager.changeCurrentTrack(true);
                SoftwareCoUtils.updatePlayerControls(false);
                return true;
            }
        } catch (Exception e) {
            //
        } finally {
            PlaylistManager.gatherTrackTimer();
        }
        return false;
    }

    public static boolean likeSpotifyTrack(boolean like, String trackId) {

        try {
            if (trackId != null) {
                SoftwareResponse resp = (SoftwareResponse) MusicController.likeSpotifyWeb(like, trackId);
                if (resp.isOk()) {
                    MusicControlManager.playerCounter = 0;
                    new Thread(() -> {
                        try {
                            Thread.sleep(1000);
                            PlayListCommands.updatePlaylists(3, null);
                            SoftwareCoUtils.updatePlayerControls(true);
                            SoftwareCoUtils.sendLikedTrack(like, trackId, "spotify");
                        } catch (Exception e) {
                            System.err.println(e);
                        }
                    }).start();
                    return true;
                } else if (resp.getCode() == 403 && !resp.getJsonObj().isJsonNull() && resp.getJsonObj().has("error")) {
                    JsonObject error = resp.getJsonObj().getAsJsonObject("error");
                    SoftwareCoUtils.showMsgPrompt(error.get("message").getAsString(), new Color(120, 23, 50, 100));
                }
            }
        } catch (Exception e) {
            //
        } finally {
            PlaylistManager.gatherTrackTimer();
        }
        return false;
    }
}
