package com.softwareco.intellij.plugin.music;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.softwareco.intellij.plugin.SoftwareCoSessionManager;
import com.softwareco.intellij.plugin.SoftwareCoUtils;
import com.softwareco.intellij.plugin.musicjava.SoftwareResponse;
import com.softwareco.intellij.plugin.musicjava.Apis;
import com.softwareco.intellij.plugin.musicjava.Util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
    public static boolean isDeviceChecked = false;

    public static JsonObject getUserPlaylists() {

        if(MusicControlManager.spotifyUserId == null) {
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

    public static JsonObject getSpotifyWebCurrentTrack() {

        String accessToken = "Bearer " + SoftwareCoSessionManager.getItem("spotify_access_token");
        SoftwareResponse resp = (SoftwareResponse) Apis.getSpotifyWebCurrentTrack(accessToken);
        if (resp.isOk() && resp.getCode() == 200) {
            if(isDeviceChecked) {
                MusicControlManager.getSpotifyDevices(); // API call
                isDeviceChecked = false;
            }
            JsonObject tracks = resp.getJsonObj();
            if (tracks != null && tracks.has("item") && !tracks.get("item").isJsonNull()) {
                JsonObject track = tracks.get("item").getAsJsonObject();
                MusicControlManager.currentTrackId = track.get("id").getAsString();
                MusicControlManager.currentTrackName = track.get("name").getAsString();

                // set context
                if(tracks.has("context") && !tracks.get("context").isJsonNull()) {
                    JsonObject context = tracks.get("context").getAsJsonObject();
                    String[] uri = context.get("uri").getAsString().split(":");
                    if(uri[uri.length - 2].equals("playlist"))
                        MusicControlManager.currentPlaylistId = uri[uri.length - 1];
                } else if(MusicControlManager.currentPlaylistId != null && MusicControlManager.currentPlaylistId.length() > 5) {
                    MusicControlManager.currentPlaylistId = null;
                }

                // set player state
                if(tracks.get("is_playing").getAsBoolean()) {
                    if(MusicControlManager.defaultbtn.equals("play")) {
                        MusicControlManager.defaultbtn = "pause";
                        MusicControlManager.getSpotifyDevices(); // API call
                        PlayListCommands.updatePlaylists(5, null);
                    }
                    PlaylistManager.pauseTriggerTime = 0;
                    if(PlaylistManager.pauseTrigger) {
                        PlaylistManager.pauseTrigger = false;
                        SoftwareCoUtils.TimesData timesData = SoftwareCoUtils.getTimesData();
                        SoftwareCoSessionManager.start = timesData.now;
                        SoftwareCoSessionManager.local_start = timesData.local_now;
                    }
                    SoftwareCoSessionManager.playerState = 1;
                } else {
                    if(MusicControlManager.defaultbtn.equals("pause")) {
                        MusicControlManager.defaultbtn = "play";
                        MusicControlManager.getSpotifyDevices(); // API call
                        PlayListCommands.updatePlaylists(5, null);
                    }
                    SoftwareCoUtils.TimesData timesData = SoftwareCoUtils.getTimesData();
                    if(PlaylistManager.pauseTriggerTime == 0) {
                        PlaylistManager.pauseTriggerTime = timesData.now;
                    } else if(!PlaylistManager.pauseTrigger && (timesData.now - PlaylistManager.pauseTriggerTime) > 60) {
                        PlaylistManager.pauseTriggerTime = 0;
                        PlaylistManager.pauseTrigger = true;
                        if(PlaylistManager.previousTrackData != null) {
                            // process music payload
                            SoftwareCoSessionManager.processMusicPayload(PlaylistManager.previousTrackData);
                        }
                        SoftwareCoSessionManager.playerState = 0;
                    }
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

                if(!MusicControlManager.currentTrackName.equals(PlaylistManager.previousTrack)) {
                    PlaylistManager.previousTrack = MusicControlManager.currentTrackName;

                    if(PlaylistManager.previousTrackData != null) {
                        // process music payload
                        SoftwareCoSessionManager.processMusicPayload(PlaylistManager.previousTrackData);
                    }
                    SoftwareCoUtils.TimesData timesData = SoftwareCoUtils.getTimesData();
                    SoftwareCoSessionManager.start = timesData.now;
                    SoftwareCoSessionManager.local_start = timesData.local_now;
                }
                PlaylistManager.previousTrackData = tracks;
            } else {
                MusicControlManager.defaultbtn = "play";
                MusicControlManager.currentTrackName = null;
                SoftwareCoSessionManager.playerState = 0;
            }
            return resp.getJsonObj();
        } else if(resp.getCode() == 204) {
            if(!isDeviceChecked) {
                new Thread(() -> {
                    try {
                        Thread.sleep(5000);
                        MusicControlManager.getSpotifyDevices(); // API call
                        isDeviceChecked = true;
                    }
                    catch (Exception e){
                        System.err.println(e);
                    }
                }).start();
            }
            MusicControlManager.currentDeviceName = null;
            MusicControlManager.currentTrackName = null;
            SoftwareCoSessionManager.playerState = 0;
            MusicControlManager.defaultbtn = "play";
        } else if(!resp.getJsonObj().isJsonNull()) {
            JsonObject tracks = resp.getJsonObj();
            if (tracks != null && tracks.has("error")) {
                JsonObject error = tracks.get("error").getAsJsonObject();
                String message = error.get("message").getAsString();
                if(message.equals("The access token expired")) {
                    MusicControlManager.refreshAccessToken();
                }
            }
        }
        return null;
    }

    public static JsonObject getSpotifyDesktopCurrentTrack() {
        if(SoftwareCoUtils.isMac() && SoftwareCoUtils.isSpotifyRunning()) {
            try {
                JsonObject obj = Util.getCurrentMusicTrack();
                if (!obj.isJsonNull()) {
                    String track_Id = obj.get("id").getAsString();
                    if(track_Id.contains("track")) {
                        String[] paramParts = track_Id.split(":");
                        track_Id = paramParts[paramParts.length-1].trim();
                    }
                    MusicControlManager.currentTrackId = track_Id;
                    MusicControlManager.currentTrackName = obj.get("name").getAsString();
                    if (obj.get("state").getAsString().equals("playing")) {
                        if(MusicControlManager.defaultbtn.equals("play")) {
                            MusicControlManager.defaultbtn = "pause";
                            PlayListCommands.updatePlaylists(5, null);
                        }
                        PlaylistManager.pauseTriggerTime = 0;
                        if(PlaylistManager.pauseTrigger) {
                            PlaylistManager.pauseTrigger = false;
                            SoftwareCoUtils.TimesData timesData = SoftwareCoUtils.getTimesData();
                            SoftwareCoSessionManager.start = timesData.now;
                            SoftwareCoSessionManager.local_start = timesData.local_now;
                        }
                        SoftwareCoSessionManager.playerState = 1;
                    } else {
                        if(MusicControlManager.defaultbtn.equals("pause")) {
                            MusicControlManager.defaultbtn = "play";
                            PlayListCommands.updatePlaylists(5, null);
                        }

                        SoftwareCoUtils.TimesData timesData = SoftwareCoUtils.getTimesData();
                        if(PlaylistManager.pauseTriggerTime == 0) {
                            PlaylistManager.pauseTriggerTime = timesData.now;
                        } else if(!PlaylistManager.pauseTrigger && (timesData.now - PlaylistManager.pauseTriggerTime) > 60) {
                            PlaylistManager.pauseTriggerTime = 0;
                            PlaylistManager.pauseTrigger = true;
                            if(PlaylistManager.previousTrackData != null) {
                                // process music payload
                                SoftwareCoSessionManager.processMusicPayload(PlaylistManager.previousTrackData);
                            }
                            SoftwareCoSessionManager.playerState = 0;
                        }
                    }

                    if(!MusicControlManager.currentTrackName.equals(PlaylistManager.previousTrack)) {
                        PlaylistManager.previousTrack = MusicControlManager.currentTrackName;

                        if(PlaylistManager.previousTrackData != null) {
                            // process music payload
                            SoftwareCoSessionManager.processMusicPayload(PlaylistManager.previousTrackData);
                        }
                        SoftwareCoUtils.TimesData timesData = SoftwareCoUtils.getTimesData();
                        SoftwareCoSessionManager.start = timesData.now;
                        SoftwareCoSessionManager.local_start = timesData.local_now;
                    }
                    PlaylistManager.previousTrackData = obj;
                    return obj;
                }
            } catch (Exception e) {
                LOG.warning("Music Time: Error trying to read and json parse the current track, error: " + e.getMessage());
            }
        } else {
            //MusicControlManager.currentDeviceName = null;
            MusicControlManager.currentTrackName = null;
            SoftwareCoSessionManager.playerState = 0;
            MusicControlManager.defaultbtn = "play";
        }
        return null;
    }
}
