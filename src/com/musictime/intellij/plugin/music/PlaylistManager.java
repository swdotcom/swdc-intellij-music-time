package com.musictime.intellij.plugin.music;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.musictime.intellij.plugin.SoftwareCoSessionManager;
import com.musictime.intellij.plugin.SoftwareCoUtils;
import com.musictime.intellij.plugin.SoftwareResponse;
import com.musictime.intellij.plugin.actions.MusicToolWindow;
import com.musictime.intellij.plugin.models.DeviceInfo;
import com.musictime.intellij.plugin.musicjava.Apis;
import com.musictime.intellij.plugin.musicjava.DeviceManager;
import com.musictime.intellij.plugin.musicjava.MusicStore;
import org.apache.commons.lang.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PlaylistManager {

    public static final Logger LOG = Logger.getLogger("PlaylistManager");
    public static String previousTrackName = "";
    public static JsonObject currentTrackData = null;

    public static long pauseTriggerTime = 0;
    private static int deviceNullCounter = 0;

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

    private static DeviceInfo deviceCheck() {
        // skip checking for a currently playing device if there's no
        // device and no previously playing track
        DeviceInfo currentDevice = DeviceManager.getBestDeviceOption();
        if (currentDevice == null && MusicControlManager.currentTrackName == null) {
            if (deviceNullCounter % 3 == 0) {
                // check devices
                DeviceManager.refreshDevices();
                deviceNullCounter = 0;
            }
            deviceNullCounter++;
            if (currentDevice == null && MusicControlManager.currentTrackName == null) {
                return null;
            }
        }
        return currentDevice;
    }

    public static void fetchTrack() {

        if (!MusicControlManager.hasSpotifyAccess()) {
            SoftwareCoUtils.setStatusLineMessage();
            return;
        }

        try {
            SoftwareCoUtils.TimesData timesData = SoftwareCoUtils.getTimesData();

            // in case we don't have a device
            DeviceInfo currentDevice = deviceCheck();
            if (currentDevice == null) {
                // no reason to fetch a track if we don't have a device to use
                return;
            }

            SoftwareResponse resp = (SoftwareResponse) Apis.getSpotifyWebCurrentTrack();

            boolean hasPrevTrack = (StringUtils.isNotBlank(PlaylistManager.previousTrackName)) ? true : false;

            if (resp != null && resp.isOk() && resp.getCode() == 200) {

                JsonObject trackInfo = resp.getJsonObj();
                if (trackInfo != null && trackInfo.has("item") && !trackInfo.get("item").isJsonNull()) {
                    JsonObject track = trackInfo.get("item").getAsJsonObject();
                    MusicControlManager.currentTrackPlaying = trackInfo.get("is_playing").getAsBoolean();
                    MusicControlManager.currentTrackId = track.get("id").getAsString();
                    MusicControlManager.currentTrackName = track.get("name").getAsString();

                    if (MusicControlManager.currentTrackPlaying) {
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

                    boolean initializePrevTrack = (hasCurrentTrack && !hasPrevTrack) ? true : false;
                    boolean longPaused = (timesData.now - PlaylistManager.pauseTriggerTime) > 60 ? true : false;

                    boolean prevTrackDone = false;
                    // long paused and has prev track
                    // no current track and has prev track
                    // has current track, has prev track, track names don't match
                    if ((longPaused && hasPrevTrack) || (!hasCurrentTrack && hasPrevTrack) ||
                            (hasCurrentTrack && hasPrevTrack
                                    && !MusicControlManager.currentTrackName.equals(PlaylistManager.previousTrackName))) {
                        prevTrackDone = true;
                    }

                    if (prevTrackDone || initializePrevTrack) {
                        SoftwareCoSessionManager.start = timesData.now;
                        SoftwareCoSessionManager.local_start = timesData.local_now;
                        PlaylistManager.pauseTriggerTime = 0;
                    }

                    // always update the current track data, but after we've checked if
                    // the previous track should be sent, so here is good
                    PlaylistManager.currentTrackData = trackInfo;
                    PlaylistManager.previousTrackName = MusicControlManager.currentTrackName;

                    if (hasCurrentTrack &&
                            !MusicControlManager.currentTrackPlaying &&
                            PlaylistManager.pauseTriggerTime == 0) {
                        // reset to now, now that it's paused and pauseTriggerTime equals zero
                        PlaylistManager.pauseTriggerTime = timesData.now;
                    }
                }

            } else if (resp.getCode() == 204) {

                MusicControlManager.currentTrackPlaying = false;
                PlaylistManager.currentTrackData = null;
                PlaylistManager.previousTrackName = "";
                MusicControlManager.currentTrackName = null;

                if (currentDevice != null) {
                    if (hasPrevTrack) {
                        DeviceManager.refreshDevices();
                    }

                    MusicToolWindow.refresh();
                }
            } else if (!resp.getJsonObj().isJsonNull()) {
                MusicControlManager.currentTrackPlaying = false;
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
            SoftwareCoUtils.setStatusLineMessage();
        }
    }

}
