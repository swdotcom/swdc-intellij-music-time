package com.softwareco.intellij.plugin.music;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.softwareco.intellij.plugin.SoftwareCoSessionManager;
import com.softwareco.intellij.plugin.SoftwareCoUtils;
import com.softwareco.intellij.plugin.musicjava.SoftwareResponse;
import com.softwareco.intellij.plugin.musicjava.Apis;
import com.softwareco.intellij.plugin.musicjava.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PlaylistManager {

    public static final Logger LOG = Logger.getLogger("PlaylistManager");
    public static String previousTrack = "";
    public static JsonObject previousTrackData = null;

    public static boolean pauseTrigger = false;
    public static long pauseTriggerTime = 0;

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
                //PlayListCommands.likedPlaylistId = "2";
                PlayListCommands.userPlaylistIds.clear();
                for(JsonElement array : obj.get("items").getAsJsonArray()) {
                    if(array.getAsJsonObject().get("type").getAsString().equals("playlist")) {
                        MusicControlManager.playlistids.add(array.getAsJsonObject().get("id").getAsString());
                        if(array.getAsJsonObject().get("name").getAsString().equals("My AI Top 40")) {
                            PlayListCommands.myAIPlaylistId = array.getAsJsonObject().get("id").getAsString();
                        } else {
                            PlayListCommands.userPlaylistIds.add(array.getAsJsonObject().get("id").getAsString());
                            PlayListCommands.userPlaylists.put(array.getAsJsonObject().get("id").getAsString(),
                                    array.getAsJsonObject().get("name").getAsString().toLowerCase().trim());
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
            if (resp.isOk()) {
                JsonObject obj = resp.getJsonObj();
                if (obj != null && obj.has("tracks")) {
                    JsonObject tracks = obj.get("tracks").getAsJsonObject();
                    MusicControlManager.tracksByPlaylistId.clear();
                    for (JsonElement array : tracks.get("items").getAsJsonArray()) {
                        JsonObject track = array.getAsJsonObject().get("track").getAsJsonObject();
                        MusicControlManager.tracksByPlaylistId.add(track.get("id").getAsString());
                    }
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
//                    if(!array.getAsJsonObject().get("context").isJsonNull()) {
//                        JsonObject context = array.getAsJsonObject().get("context").getAsJsonObject();
//                        String[] uri = context.get("uri").getAsString().split(":");
//                        currentPlaylistId = uri[uri.length - 1];
//                    }
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
            JsonObject tracks = resp.getJsonObj();
            if (tracks != null && tracks.has("item")) {
                JsonObject track = tracks.get("item").getAsJsonObject();
                MusicControlManager.currentTrackId = track.get("id").getAsString();
                MusicControlManager.currentTrackName = track.get("name").getAsString();

                // set context
                if(!tracks.get("context").isJsonNull()) {
                    JsonObject context = tracks.get("context").getAsJsonObject();
                    String[] uri = context.get("uri").getAsString().split(":");
                    if(uri[uri.length - 2].equals("playlist"))
                        MusicControlManager.currentPlaylistId = uri[uri.length - 1];
                } else {
                    MusicControlManager.currentPlaylistId = PlayListCommands.likedPlaylistId;
                }

                // set player state
                if(tracks.get("is_playing").getAsBoolean()) {
                    if(MusicControlManager.defaultbtn.equals("play")) {
                        MusicControlManager.defaultbtn = "pause";
                        PlayListCommands.updatePlaylists(5, null);
                    }
                    pauseTriggerTime = 0;
                    if(pauseTrigger) {
                        pauseTrigger = false;
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
                    if(pauseTriggerTime == 0) {
                        pauseTriggerTime = timesData.now;
                    } else if(!pauseTrigger && (timesData.now - pauseTriggerTime) > 60) {
                        pauseTriggerTime = 0;
                        pauseTrigger = true;
                        if(previousTrackData != null) {
                            // process music payload
                            SoftwareCoSessionManager.processMusicPayload(previousTrackData);
                        }
                        SoftwareCoSessionManager.playerState = 0;
                    }
                }

                if(!MusicControlManager.currentTrackName.equals(previousTrack)) {
                    previousTrack = MusicControlManager.currentTrackName;

                    if(previousTrackData != null) {
                        // process music payload
                        SoftwareCoSessionManager.processMusicPayload(previousTrackData);
                    }
                    SoftwareCoUtils.TimesData timesData = SoftwareCoUtils.getTimesData();
                    SoftwareCoSessionManager.start = timesData.now;
                    SoftwareCoSessionManager.local_start = timesData.local_now;
                }
                previousTrackData = tracks;
            } else {
                MusicControlManager.defaultbtn = "play";
                MusicControlManager.getSpotifyDevices();
            }
            return resp.getJsonObj();
        } else if(resp.getCode() == 204) {
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
        if(SoftwareCoUtils.isSpotifyRunning()) {
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
                        pauseTriggerTime = 0;
                        pauseTrigger = false;
                        SoftwareCoSessionManager.playerState = 1;
                    } else {
                        if(MusicControlManager.defaultbtn.equals("pause")) {
                            MusicControlManager.defaultbtn = "play";
                            PlayListCommands.updatePlaylists(5, null);
                        }
//                        if(MusicControlManager.currentPlaylistId != null && MusicControlManager.currentPlaylistId.length() < 5
//                                && MusicControlManager.playerState.equals("End")) {
//                            List<String> list = new ArrayList<>();
//                            if(MusicControlManager.currentPlaylistId.equals("1")) {
//                                JsonObject obj1 = PlayListCommands.topSpotifyTracks;
//                                if (obj1 != null && obj1.has("items")) {
//                                    for(JsonElement array : obj1.get("items").getAsJsonArray()) {
//                                        JsonObject trk = array.getAsJsonObject();
//                                        list.add(trk.get("id").getAsString());
//                                    }
//                                }
//                            } else if(MusicControlManager.currentPlaylistId.equals("2")) {
//                                JsonObject obj2 = PlayListCommands.likedTracks;
//                                if (obj2 != null && obj2.has("items")) {
//                                    for(JsonElement array : obj2.get("items").getAsJsonArray()) {
//                                        JsonObject trk = array.getAsJsonObject().getAsJsonObject("track");
//                                        list.add(trk.get("id").getAsString());
//                                    }
//                                }
//                            }
//                            int index = list.indexOf(MusicControlManager.currentTrackId);
//                            if(index < (list.size() - 1)) {
//                                MusicControlManager.currentTrackId = list.get(index + 1);
//                                String trackId = list.get(index + 1);
//                                PlayerControlManager.playSpotifyPlaylist(MusicControlManager.currentPlaylistId, trackId);
//                            } else {
//                                MusicControlManager.currentTrackId = list.get(0);
//                                String trackId = list.get(0);
//                                PlayerControlManager.playSpotifyPlaylist(MusicControlManager.currentPlaylistId, trackId);
//                            }
//                        }

                        SoftwareCoUtils.TimesData timesData = SoftwareCoUtils.getTimesData();
                        if(pauseTriggerTime == 0) {
                            pauseTriggerTime = timesData.now;
                        } else if(!pauseTrigger && (timesData.now - pauseTriggerTime) > 60) {
                            pauseTriggerTime = 0;
                            pauseTrigger = true;
                            if(previousTrackData != null) {
                                // process music payload
                                SoftwareCoSessionManager.processMusicPayload(previousTrackData);
                            }
                            SoftwareCoSessionManager.playerState = 0;
                            previousTrackData = null;
                            previousTrack = "";
                        }
                    }

                    if(!MusicControlManager.currentTrackName.equals(previousTrack)) {
                        previousTrack = MusicControlManager.currentTrackName;

                        if(previousTrackData != null) {
                            // process music payload
                            SoftwareCoSessionManager.processMusicPayload(previousTrackData);
                        }
                    }
                    previousTrackData = obj;
                    return obj;
                }
            } catch (Exception e) {
                LOG.warning("Music Time: Error trying to read and json parse the current track, error: " + e.getMessage());
            }
        } else {
            MusicControlManager.currentDeviceName = null;
            MusicControlManager.currentTrackName = null;
            SoftwareCoSessionManager.playerState = 0;
        }
        return null;
    }
}
