package com.musictime.intellij.plugin.music;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.musictime.intellij.plugin.SoftwareCoSessionManager;
import com.musictime.intellij.plugin.SoftwareCoUtils;
import com.musictime.intellij.plugin.SoftwareResponse;
import com.musictime.intellij.plugin.actions.MusicToolWindow;
import com.musictime.intellij.plugin.musicjava.Apis;
import com.musictime.intellij.plugin.musicjava.Util;
import org.apache.commons.lang.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PlaylistManager {

    public static final Logger LOG = Logger.getLogger("PlaylistManager");
    public static String previousTrack = "";
    public static JsonObject previousTrackData = null;
    public static boolean skipPrevious = false;

    public static boolean pauseTrigger = false;
    public static long pauseTriggerTime = 0;
    private static boolean gatheringTrack = false;

    public static JsonObject getUserPlaylists() {

        if (MusicControlManager.spotifyUserId == null) {
            MusicControlManager.getUserProfile();
        }

        String accessToken = "Bearer " + SoftwareCoSessionManager.getItem("spotify_access_token");
        SoftwareResponse resp = (SoftwareResponse) Apis.getUserPlaylists(MusicControlManager.spotifyUserId, accessToken);
        if (resp.isOk()) {
            JsonObject obj = resp.getJsonObj();
            if (obj != null && obj.has("items")) {
                MusicControlManager.playlistids.clear();
                PlayListCommands.userPlaylists.clear();
                PlayListCommands.myAIPlaylistId = null;
                PlayListCommands.userPlaylistIds.clear();
                for(JsonElement array : obj.get("items").getAsJsonArray()) {
                    if(array.getAsJsonObject().get("type").getAsString().equals("playlist")
                            && !array.getAsJsonObject().get("id").getAsString().equals("6jCkTED0V5NEuM8sKbGG1Z")) {
                        MusicControlManager.playlistids.add(array.getAsJsonObject().get("id").getAsString());
                        if(array.getAsJsonObject().get("name").getAsString().equals("My AI Top 40")) {
                            PlayListCommands.myAIPlaylistId = array.getAsJsonObject().get("id").getAsString();
                        } else {
                            PlayListCommands.userPlaylistIds.add(array.getAsJsonObject().get("id").getAsString());
                            PlayListCommands.userPlaylists.put(array.getAsJsonObject().get("id").getAsString(),
                                    array.getAsJsonObject().get("name").getAsString().trim());
                        }
                    }
                }
            } else {
                LOG.log(Level.INFO, "Music Time: Unable to get Playlists, null response");
            }
            return resp.getJsonObj();
        }
        return null;
    }

    public static JsonObject getTracksByPlaylistId(String playlistId) {

        if(playlistId != null) {
            String accessToken = "Bearer " + SoftwareCoSessionManager.getItem("spotify_access_token");
            SoftwareResponse resp = (SoftwareResponse) Apis.getTracksByPlaylistId(accessToken, playlistId);
            if (resp.isOk()) {
                JsonObject obj = resp.getJsonObj();
                if (obj != null && obj.has("tracks")) {
                    JsonObject tracks = obj.get("tracks").getAsJsonObject();
                    Map<String, String> trks = new HashMap<>();
                    for (JsonElement array : tracks.get("items").getAsJsonArray()) {
                        JsonObject track = array.getAsJsonObject().get("track").getAsJsonObject();
                        trks.put(track.get("id").getAsString(), track.get("name").getAsString());
                    }
                    MusicControlManager.tracksByPlaylistId.put(playlistId, trks);
                } else {
                    LOG.log(Level.INFO, "Music Time: Unable to get Playlist Tracks, null response");
                }
                return resp.getJsonObj();
            }
        }
        return new JsonObject();
    }

    public static JsonObject getTrackById(String trackId) {

        SoftwareResponse resp = (SoftwareResponse) Apis.getTrackById(trackId);
        if (resp.isOk()) {
            return resp.getJsonObj();
        }
        return new JsonObject();
    }

    public static JsonObject getSpotifyWebRecentTrack() {

        String accessToken = "Bearer " + SoftwareCoSessionManager.getItem("spotify_access_token");
        SoftwareResponse resp = (SoftwareResponse) Apis.getSpotifyWebRecentTrack(accessToken);
        if (resp.isOk()) {
            JsonObject tracks = resp.getJsonObj();
            if (tracks != null && tracks.has("items")) {
                for(JsonElement array : tracks.get("items").getAsJsonArray()) {
                    JsonObject track = array.getAsJsonObject().get("track").getAsJsonObject();
                    MusicControlManager.currentTrackId = track.get("id").getAsString();
                }
            }
            return resp.getJsonObj();
        }
        return null;
    }

    public static void gatherMusicInfo() {

        if (!MusicControlManager.hasSpotifyAccess() || gatheringTrack) {
            return;
        }

        // skip checking for a currently playing device if there's no
        // device and no previously playing track
        if (MusicControlManager.currentDeviceName == null && MusicControlManager.currentTrackName == null) {
            return;
        }

        gatheringTrack = true;
        SoftwareCoUtils.TimesData timesData = SoftwareCoUtils.getTimesData();
        String accessToken = "Bearer " + SoftwareCoSessionManager.getItem("spotify_access_token");
        SoftwareResponse resp = (SoftwareResponse) Apis.getSpotifyWebCurrentTrack(accessToken);

        if (resp.isOk() && resp.getCode() == 200) {

            if (MusicControlManager.currentDeviceName == null) {
                MusicControlManager.getSpotifyDevices(); // API call
            }

            JsonObject tracks = resp.getJsonObj();
            if (tracks != null && tracks.has("item") && !tracks.get("item").isJsonNull()) {
                JsonObject track = tracks.get("item").getAsJsonObject();
                boolean isPlaying = tracks.get("is_playing").getAsBoolean();
                MusicControlManager.currentTrackId = track.get("id").getAsString();
                MusicControlManager.currentTrackName = track.get("name").getAsString();

                if (isPlaying) {
                    // reset to zero every time we know it's playing
                    PlaylistManager.pauseTriggerTime = 0;
                }

                // set context
                if (tracks.has("context") && !tracks.get("context").isJsonNull()) {
                    JsonObject context = tracks.get("context").getAsJsonObject();
                    String[] uri = context.get("uri").getAsString().split(":");
                    if (uri[uri.length - 2].equals("playlist")) {
                        MusicControlManager.currentPlaylistId = uri[uri.length - 1];
                    }
                } else if (MusicControlManager.currentPlaylistId != null && MusicControlManager.currentPlaylistId.length() > 5) {
                    MusicControlManager.currentPlaylistId = null;
                }

                boolean hasCurrentTrack = (StringUtils.isNotBlank(MusicControlManager.currentTrackName)) ? true : false;
                boolean hasPrevTrack = (StringUtils.isNotBlank(PlaylistManager.previousTrack)) ? true : false;
                boolean initializePrevTrack = (hasCurrentTrack && !hasPrevTrack) ? true : false;
                boolean longPaused = (timesData.now - PlaylistManager.pauseTriggerTime) > 60 ? true : false;

                boolean sendPreviousTrack = false;
                // long paused and has prev track
                // no current track and has prev track
                // has current track, has prev track, track names don't match
                if ((longPaused && hasPrevTrack) || (!hasCurrentTrack && hasPrevTrack) ||
                        (hasCurrentTrack && hasPrevTrack
                                && !MusicControlManager.currentTrackName.equals(PlaylistManager.previousTrack))) {
                    sendPreviousTrack = true;
                }

                if (sendPreviousTrack && PlaylistManager.previousTrackData != null) {
                    // process music payload
                    SoftwareCoSessionManager.processMusicPayload(PlaylistManager.previousTrackData);
                }

                if (sendPreviousTrack || initializePrevTrack) {
                    SoftwareCoSessionManager.start = timesData.now;
                    SoftwareCoSessionManager.local_start = timesData.local_now;
                    PlaylistManager.previousTrack = MusicControlManager.currentTrackName;
                    PlaylistManager.previousTrackData = tracks;
                    PlaylistManager.pauseTriggerTime = 0;
                }

                if (hasCurrentTrack) {
                    if (isPlaying) {
                        MusicControlManager.defaultbtn = "pause";
                        PlayListCommands.updatePlaylists(5, null);
                        SoftwareCoSessionManager.playerState = 1;
                    } else {
                        MusicControlManager.defaultbtn = "play";
                        PlayListCommands.updatePlaylists(5, null);
                        SoftwareCoSessionManager.playerState = 0;
                        if (PlaylistManager.pauseTriggerTime == 0) {
                            PlaylistManager.pauseTriggerTime = timesData.now;
                        }
                    }
                } else {
                    MusicControlManager.defaultbtn = "play";
                    MusicControlManager.currentTrackName = null;
                    SoftwareCoSessionManager.playerState = 0;
                }

                if(tracks.has("actions")) {
                    JsonObject obj = tracks.getAsJsonObject("actions");
                    JsonObject actions = obj.getAsJsonObject("disallows");
                    if(actions.has("skipping_prev")) {
                        skipPrevious = actions.get("skipping_prev").getAsBoolean();
                    } else {
                        skipPrevious = false;
                    }
                }
            }

            SoftwareCoUtils.updatePlayerControls(false);

        } else if(resp.getCode() == 204) {
            if (MusicControlManager.currentDeviceName != null) {
                MusicControlManager.getSpotifyDevices();
                MusicControlManager.currentDeviceName = null;
                MusicControlManager.currentTrackName = null;
                SoftwareCoSessionManager.playerState = 0;
                MusicControlManager.defaultbtn = "play";
                MusicToolWindow.refresh();
            }
        } else if(!resp.getJsonObj().isJsonNull()) {
            JsonObject tracks = resp.getJsonObj();
            if (tracks != null && tracks.has("error")) {
                if(MusicControlManager.requiresSpotifyAccessTokenRefresh(tracks)) {
                    MusicControlManager.refreshAccessToken();
                }
            }
        }

        gatheringTrack = false;
    }

}
