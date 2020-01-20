package com.softwareco.intellij.plugin.music;

import com.softwareco.intellij.plugin.SoftwareCoSessionManager;
import com.softwareco.intellij.plugin.SoftwareCoUtils;
import com.softwareco.intellij.plugin.actions.MusicToolWindow;
import com.softwareco.intellij.plugin.musicjava.MusicController;
import com.softwareco.intellij.plugin.musicjava.SoftwareResponse;
import com.softwareco.intellij.plugin.musicjava.Util;

import java.util.logging.Logger;

public class PlayerControlManager {

    public static final Logger LOG = Logger.getLogger("PlayerControlManager");

    public static boolean playSpotifyPlaylist(String playlistId, String trackId) {

        if(playlistId == null || trackId == null) {
            playlistId = MusicControlManager.currentPlaylistId;
            trackId = MusicControlManager.currentTrackId;
        }

        if(MusicControlManager.playerType.equals("Web Player")) {
            if (MusicControlManager.currentDeviceId != null) {
                String accessToken = "Bearer " + SoftwareCoSessionManager.getItem("spotify_access_token");
                boolean resp = MusicController.playSpotifyPlaylist(MusicControlManager.currentDeviceId, playlistId, trackId, accessToken);
                if (resp) {
                    String finalTrackId = trackId;
                    String finalPlaylistId = playlistId;
                    new Thread(() -> {
                        try {
                            Thread.sleep(1000);
                            MusicControlManager.currentPlaylistId = finalPlaylistId;
                            MusicControlManager.currentTrackId = finalTrackId;
                            MusicControlManager.playerState = "End";
                            SoftwareCoUtils.updatePlayerControles();
                            PlayListCommands.updatePlaylists();
                        } catch (Exception e) {
                            System.err.println(e);
                        }
                    }).start();
                    return true;
                }
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
            SoftwareCoUtils.updatePlayerControles();
            PlayListCommands.updatePlaylists();
            return true;
        }
        return false;
    }

    public static boolean playSpotifyDevices() {
        boolean serverIsOnline = SoftwareCoSessionManager.isServerOnline();
        if (!serverIsOnline) {
            SoftwareCoUtils.showOfflinePrompt(false);
        }

        if(MusicControlManager.playerType.equals("Web Player")) {
            if(MusicControlManager.currentDeviceId != null) {

                String accessToken = "Bearer " + SoftwareCoSessionManager.getItem("spotify_access_token");
                SoftwareResponse resp = (SoftwareResponse) MusicController.playSpotifyWeb(MusicControlManager.currentDeviceId, accessToken);
                if (resp.isOk()) {
                    MusicControlManager.playerCounter = 0;
                    MusicControlManager.playerState = "End";
                    new Thread(() -> {
                        try {
                            Thread.sleep(1000);
                            SoftwareCoUtils.updatePlayerControles();
                            PlayListCommands.updatePlaylists();
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
                }
            } else {
                MusicControlManager.launchPlayer();
            }
        } else if(MusicControlManager.spotifyCacheState && SoftwareCoUtils.isSpotifyRunning()) {
            MusicController.playSpotifyDesktop();
            MusicControlManager.playerState = "End";
            SoftwareCoUtils.updatePlayerControles();
            PlayListCommands.updatePlaylists();
            return true;
        }
        return false;
    }

    public static boolean pauseSpotifyDevices() {
        boolean serverIsOnline = SoftwareCoSessionManager.isServerOnline();
        if (!serverIsOnline) {
            SoftwareCoUtils.showOfflinePrompt(false);
        }

        if(MusicControlManager.playerType.equals("Web Player")) {
            if(MusicControlManager.currentDeviceId != null) {

                String accessToken = "Bearer " + SoftwareCoSessionManager.getItem("spotify_access_token");
                SoftwareResponse resp = (SoftwareResponse) MusicController.pauseSpotifyWeb(MusicControlManager.currentDeviceId, accessToken);
                if (resp.isOk()) {
                    MusicControlManager.playerCounter = 0;
                    MusicControlManager.playerState = "Resume";
                    new Thread(() -> {
                        try {
                            Thread.sleep(1000);
                            SoftwareCoUtils.updatePlayerControles();
                            PlayListCommands.updatePlaylists();
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
                }
            } else {
                MusicControlManager.launchPlayer();
            }
        } else if(MusicControlManager.spotifyCacheState && SoftwareCoUtils.isSpotifyRunning()) {
            MusicController.pauseSpotifyDesktop();
            MusicControlManager.playerState = "Resume";
            SoftwareCoUtils.updatePlayerControles();
            PlayListCommands.updatePlaylists();
            return true;
        }
        return false;
    }

    public static boolean previousSpotifyTrack() {
        boolean serverIsOnline = SoftwareCoSessionManager.isServerOnline();
        if (!serverIsOnline) {
            SoftwareCoUtils.showOfflinePrompt(false);
        }

        if(MusicControlManager.playerType.equals("Web Player")) {
            if(MusicControlManager.currentDeviceId != null) {

                String accessToken = "Bearer " + SoftwareCoSessionManager.getItem("spotify_access_token");
                SoftwareResponse resp = (SoftwareResponse) MusicController.previousSpotifyWeb(MusicControlManager.currentDeviceId, accessToken);
                if (resp.isOk()) {
                    MusicControlManager.playerCounter = 0;
                    new Thread(() -> {
                        try {
                            Thread.sleep(1000);
                            SoftwareCoUtils.updatePlayerControles();
                            PlayListCommands.updatePlaylists();
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
                }
            } else {
                MusicControlManager.launchPlayer();
            }
        } else if(MusicControlManager.spotifyCacheState && SoftwareCoUtils.isSpotifyRunning()) {
            MusicController.previousSpotifyDesktop();
            SoftwareCoUtils.updatePlayerControles();
            PlayListCommands.updatePlaylists();
            return true;
        }
        return false;
    }

    public static boolean nextSpotifyTrack() {
        boolean serverIsOnline = SoftwareCoSessionManager.isServerOnline();
        if (!serverIsOnline) {
            SoftwareCoUtils.showOfflinePrompt(false);
        }

        if(MusicControlManager.playerType.equals("Web Player")) {
            if(MusicControlManager.currentDeviceId != null) {

                String accessToken = "Bearer " + SoftwareCoSessionManager.getItem("spotify_access_token");
                SoftwareResponse resp = (SoftwareResponse) MusicController.nextSpotifyWeb(MusicControlManager.currentDeviceId, accessToken);
                if (resp.isOk()) {
                    MusicControlManager.playerCounter = 0;
                    new Thread(() -> {
                        try {
                            Thread.sleep(1000);
                            SoftwareCoUtils.updatePlayerControles();
                            PlayListCommands.updatePlaylists();
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
                }
            } else {
                MusicControlManager.launchPlayer();
            }
        } else if(MusicControlManager.spotifyCacheState && SoftwareCoUtils.isSpotifyRunning()) {
            MusicController.nextSpotifyDesktop();
            SoftwareCoUtils.updatePlayerControles();
            PlayListCommands.updatePlaylists();
            return true;
        }
        return false;
    }

    public static boolean likeSpotifyTrack(boolean like, String trackId) {
        boolean serverIsOnline = SoftwareCoSessionManager.isServerOnline();
        if (!serverIsOnline) {
            SoftwareCoUtils.showOfflinePrompt(false);
        }

        if(trackId != null) {

            String accessToken = "Bearer " + SoftwareCoSessionManager.getItem("spotify_access_token");
            SoftwareResponse resp = (SoftwareResponse) MusicController.likeSpotifyWeb(like, trackId, accessToken);
            if (resp.isOk()) {
                MusicControlManager.playerCounter = 0;
                new Thread(() -> {
                    try {
                        Thread.sleep(1000);
                        SoftwareCoUtils.updatePlayerControles();
                        PlayListCommands.updatePlaylists();
                    } catch (Exception e) {
                        System.err.println(e);
                    }
                }).start();
                return true;
            }
        }
        return false;
    }
}
