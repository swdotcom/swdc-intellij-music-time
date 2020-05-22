package com.musictime.intellij.plugin.music;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.musictime.intellij.plugin.SoftwareCoSessionManager;
import com.musictime.intellij.plugin.SoftwareCoUtils;
import com.musictime.intellij.plugin.SoftwareResponse;
import com.musictime.intellij.plugin.actions.MusicToolWindow;
import com.musictime.intellij.plugin.musicjava.Apis;
import com.musictime.intellij.plugin.musicjava.MusicStore;
import org.apache.commons.lang.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PlaylistManager {

    public static final Logger LOG = Logger.getLogger("PlaylistManager");
    public static String previousTrackName = "";
    public static JsonObject currentTrackData = null;
    public static boolean skipPrevious = false;

    public static boolean pauseTrigger = false;
    public static long pauseTriggerTime = 0;
    private static boolean gatheringTrack = false;
    private static long endCheckThresholdMillis = 1000 * 19;
    private static Timer endCheckTimer = null;

    public static JsonObject getUserPlaylists() {

        if (MusicStore.getSpotifyUserId() == null) {
            Apis.getUserProfile();
        }

        SoftwareResponse resp = (SoftwareResponse) Apis.getUserPlaylists(MusicStore.getSpotifyUserId());
        if (resp != null && resp.isOk()) {
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
            SoftwareResponse resp = (SoftwareResponse) Apis.getTracksByPlaylistId(playlistId);
            if (resp != null && resp.isOk()) {
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
        if (resp != null && resp.isOk()) {
            return resp.getJsonObj();
        }
        return new JsonObject();
    }

    public static void gatherTrackTimer() {
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                gatherMusicInfo();
            }
        }, 1000);
    }

    public static void trackEndCheck() {
        // PlaylistManager.currentTrackData
        // get progress_ms from the top level track data and duration_ms from the track data "item"
        if (PlaylistManager.currentTrackData != null && PlaylistManager.currentTrackData.has("item")) {
            JsonObject track = PlaylistManager.currentTrackData.get("item").getAsJsonObject();
            long duration_ms = track.get("duration_ms").getAsLong();
            long progress_ms = PlaylistManager.currentTrackData.has("progress_ms") ?
                    PlaylistManager.currentTrackData.get("progress_ms").getAsLong() : 0;
            long diff = duration_ms - progress_ms;

            // the diff has to be less than the threshold (19 seconds)
            // and a previous timer should be null before sending another fetch
            if (diff > 0 && diff <= endCheckThresholdMillis && endCheckTimer == null) {
                diff += 1000;
                scheduleGatherMusicInfo(diff);
            }
        }
    }

    private static void scheduleGatherMusicInfo(long timeout) {
        endCheckTimer = new Timer();
        endCheckTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                System.out.println("GATHERING MUSIC INFO");
                gatherMusicInfo();
                endCheckTimer = null;
            }
        }, timeout);
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
        System.out.println("gathering music");
        try {
            SoftwareCoUtils.TimesData timesData = SoftwareCoUtils.getTimesData();
            SoftwareResponse resp = (SoftwareResponse) Apis.getSpotifyWebCurrentTrack();

            if (resp != null && resp.isOk() && resp.getCode() == 200) {

                if (MusicControlManager.currentDeviceName == null) {
                    MusicControlManager.getSpotifyDevices(); // API call
                }

                JsonObject trackInfo = resp.getJsonObj();
                if (trackInfo != null && trackInfo.has("item") && !trackInfo.get("item").isJsonNull()) {
                    JsonObject track = trackInfo.get("item").getAsJsonObject();
                    boolean isPlaying = trackInfo.get("is_playing").getAsBoolean();
                    MusicControlManager.currentTrackId = track.get("id").getAsString();
                    MusicControlManager.currentTrackName = track.get("name").getAsString();

                    if (isPlaying) {
                        // reset to zero every time we know it's playing
                        PlaylistManager.pauseTriggerTime = 0;
                    }

                    // set context
                    if (trackInfo.has("context") && !trackInfo.get("context").isJsonNull()) {
                        JsonObject context = trackInfo.get("context").getAsJsonObject();
                        String[] uri = context.get("uri").getAsString().split(":");
                        if (uri[uri.length - 2].equals("playlist")) {
                            MusicControlManager.currentPlaylistId = uri[uri.length - 1];
                        }
                    } else if (MusicControlManager.currentPlaylistId != null && MusicControlManager.currentPlaylistId.length() > 5) {
                        MusicControlManager.currentPlaylistId = null;
                    }

                    boolean hasCurrentTrack = (StringUtils.isNotBlank(MusicControlManager.currentTrackName)) ? true : false;
                    boolean hasPrevTrack = (StringUtils.isNotBlank(PlaylistManager.previousTrackName)) ? true : false;
                    boolean initializePrevTrack = (hasCurrentTrack && !hasPrevTrack) ? true : false;
                    boolean longPaused = (timesData.now - PlaylistManager.pauseTriggerTime) > 60 ? true : false;

                    boolean sendPreviousTrack = false;
                    // long paused and has prev track
                    // no current track and has prev track
                    // has current track, has prev track, track names don't match
                    if ((longPaused && hasPrevTrack) || (!hasCurrentTrack && hasPrevTrack) ||
                            (hasCurrentTrack && hasPrevTrack
                                    && !MusicControlManager.currentTrackName.equals(PlaylistManager.previousTrackName))) {
                        sendPreviousTrack = true;
                    }

                    if (sendPreviousTrack && PlaylistManager.currentTrackData != null) {
                        // process music payload
                        SoftwareCoSessionManager.processMusicPayload(PlaylistManager.currentTrackData);
                    }

                    if (sendPreviousTrack || initializePrevTrack) {
                        SoftwareCoSessionManager.start = timesData.now;
                        SoftwareCoSessionManager.local_start = timesData.local_now;
                        PlaylistManager.previousTrackName = MusicControlManager.currentTrackName;
                        PlaylistManager.pauseTriggerTime = 0;
                    }

                    // always update the current track data, but after we've checked if
                    // the previous track should be sent, so here is good
                    PlaylistManager.currentTrackData = trackInfo;

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
                                // reset to now, now that it's paused and pauseTriggerTime equals zero
                                PlaylistManager.pauseTriggerTime = timesData.now;
                            }
                        }
                    } else {
                        MusicControlManager.defaultbtn = "play";
                        MusicControlManager.currentTrackName = null;
                        SoftwareCoSessionManager.playerState = 0;
                    }

                    if (trackInfo.has("actions")) {
                        JsonObject obj = trackInfo.getAsJsonObject("actions");
                        JsonObject actions = obj.getAsJsonObject("disallows");
                        if (actions.has("skipping_prev")) {
                            skipPrevious = actions.get("skipping_prev").getAsBoolean();
                        } else {
                            skipPrevious = false;
                        }
                    }
                }

                SoftwareCoUtils.updatePlayerControls(false);

            } else if (resp.getCode() == 204) {
                if (MusicControlManager.currentDeviceName != null) {
                    MusicControlManager.getSpotifyDevices();
                    MusicControlManager.currentDeviceName = null;
                    MusicControlManager.currentTrackName = null;
                    SoftwareCoSessionManager.playerState = 0;
                    MusicControlManager.defaultbtn = "play";
                    MusicToolWindow.refresh();
                }
            } else if (!resp.getJsonObj().isJsonNull()) {
                JsonObject tracks = resp.getJsonObj();
                if (tracks != null && tracks.has("error")) {
                    if (MusicControlManager.requiresSpotifyAccessTokenRefresh(tracks)) {
                        MusicControlManager.refreshAccessToken();
                    }
                }
            }
        } catch (Exception e) {
            //
        } finally {
            gatheringTrack = false;
        }
    }

}
