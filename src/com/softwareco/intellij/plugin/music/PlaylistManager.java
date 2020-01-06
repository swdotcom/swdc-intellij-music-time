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
                PlayListCommands.userPlaylistIds.clear();
                PlayListCommands.myAIPlaylistId = null;
                for(JsonElement array : obj.get("items").getAsJsonArray()) {
                    if(array.getAsJsonObject().get("type").getAsString().equals("playlist")) {
                        MusicControlManager.playlistids.add(array.getAsJsonObject().get("id").getAsString());
                        if(array.getAsJsonObject().get("name").getAsString().equals("My AI Top 40")) {
                            PlayListCommands.myAIPlaylistId = array.getAsJsonObject().get("id").getAsString();
                        } else {
                            PlayListCommands.userPlaylistIds.add(array.getAsJsonObject().get("id").getAsString());
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
                    MusicControlManager.currentPlaylistTracks.clear();
                    for (JsonElement array : tracks.get("items").getAsJsonArray()) {
                        JsonObject track = array.getAsJsonObject().get("track").getAsJsonObject();
                        MusicControlManager.currentPlaylistTracks.add(track.get("id").getAsString());
                    }
                } else {
                    LOG.log(Level.INFO, "Music Time: Unable to get Playlist Tracks, null response");
                }
                return resp.getJsonObj();
            }
        }
        return null;
    }

    public static JsonObject getPlaylistTracks() {

        if(MusicControlManager.currentPlaylistId == null) {
            MusicControlManager.currentPlaylistId = MusicControlManager.playlistids.get(0);
        }

        SoftwareResponse resp = (SoftwareResponse) Apis.getCurrentPlaylistTracks();
        if (resp.isOk()) {
            JsonObject tracks = resp.getJsonObj();
            if (tracks != null && tracks.has("items")) {
                MusicControlManager.currentPlaylistTracks.clear();
                for(JsonElement array : tracks.get("items").getAsJsonArray()) {
                    JsonObject track = array.getAsJsonObject().get("track").getAsJsonObject();
                    MusicControlManager.currentPlaylistTracks.add(track.get("id").getAsString());
                }
            } else {
                LOG.log(Level.INFO, "Music Time: Unable to get Playlist Tracks, null response");
            }
            return resp.getJsonObj();
        }
        return null;
    }

    public static JsonObject getTrackById() {

        if(MusicControlManager.currentTrackId == null) {
            MusicControlManager.currentTrackId = MusicControlManager.currentPlaylistTracks.get(0);
        }

        SoftwareResponse resp = (SoftwareResponse) Apis.getTrackById();
        if (resp.isOk()) {
            JsonObject tracks = resp.getJsonObj();
            if (tracks != null && tracks.has("name")) {
                MusicControlManager.currentTrackName = tracks.get("name").getAsString();
            } else {
                LOG.log(Level.INFO, "Music Time: Unable to get Playlist Tracks, null response");
            }
            return resp.getJsonObj();
        }
        return null;
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
                SoftwareCoSessionManager.setMusicData("playlist_id", MusicControlManager.currentPlaylistId);
                SoftwareCoSessionManager.setMusicData("track_id", MusicControlManager.currentTrackId);
                if(!tracks.get("context").isJsonNull()) {
                    JsonObject context = tracks.get("context").getAsJsonObject();
                    String[] uri = context.get("uri").getAsString().split(":");
                    MusicControlManager.currentPlaylistId = uri[uri.length - 1];
                }
                if(tracks.get("is_playing").getAsBoolean()) {
                    MusicControlManager.defaultbtn = "pause";
                } else {
                    MusicControlManager.defaultbtn = "play";
                    if(MusicControlManager.currentPlaylistId != null && MusicControlManager.currentPlaylistId.length() < 5
                            && MusicControlManager.playerState.equals("End")) {
                        List<String> list = new ArrayList<>();
                        if(MusicControlManager.currentPlaylistId.equals("1")) {
                            JsonObject obj = PlayListCommands.topSpotifyTracks;
                            if (obj != null && obj.has("items")) {
                                for(JsonElement array : obj.get("items").getAsJsonArray()) {
                                    JsonObject trk = array.getAsJsonObject();
                                    list.add(trk.get("id").getAsString());
                                }
                            }
                        } else if(MusicControlManager.currentPlaylistId.equals("2")) {
                            JsonObject obj = PlayListCommands.likedTracks;
                            if (obj != null && obj.has("items")) {
                                for(JsonElement array : obj.get("items").getAsJsonArray()) {
                                    JsonObject trk = array.getAsJsonObject().getAsJsonObject("track");
                                    list.add(trk.get("id").getAsString());
                                }
                            }
                        }
                        int index = list.indexOf(MusicControlManager.currentTrackId);
                        if(index < (list.size() - 1)) {
                            MusicControlManager.currentTrackId = list.get(index + 1);
                            String trackId = list.get(index + 1);
                            PlayerControlManager.playSpotifyPlaylist(MusicControlManager.currentPlaylistId, trackId);
                        } else {
                            MusicControlManager.currentTrackId = list.get(0);
                            String trackId = list.get(0);
                            PlayerControlManager.playSpotifyPlaylist(MusicControlManager.currentPlaylistId, trackId);
                        }
                    }
                }
            } else {
                MusicControlManager.defaultbtn = "play";
                MusicControlManager.getSpotifyDevices();
            }
            return resp.getJsonObj();
        } else if(resp.getCode() == 204) {
            MusicControlManager.currentDeviceName = null;
            MusicControlManager.currentTrackName = null;
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
                    MusicControlManager.currentTrackId = obj.get("id").getAsString();
                    MusicControlManager.currentTrackName = obj.get("name").getAsString();
                    SoftwareCoSessionManager.setMusicData("playlist_id", MusicControlManager.currentPlaylistId);
                    SoftwareCoSessionManager.setMusicData("track_id", MusicControlManager.currentTrackId);
                    if (obj.get("state").getAsString().equals("playing")) {
                        MusicControlManager.defaultbtn = "pause";
                    } else {
                        MusicControlManager.defaultbtn = "play";
                        if(MusicControlManager.currentPlaylistId != null && MusicControlManager.currentPlaylistId.length() < 5
                                && MusicControlManager.playerState.equals("End")) {
                            List<String> list = new ArrayList<>();
                            if(MusicControlManager.currentPlaylistId.equals("1")) {
                                JsonObject obj1 = PlayListCommands.topSpotifyTracks;
                                if (obj1 != null && obj1.has("items")) {
                                    for(JsonElement array : obj1.get("items").getAsJsonArray()) {
                                        JsonObject trk = array.getAsJsonObject();
                                        list.add(trk.get("id").getAsString());
                                    }
                                }
                            } else if(MusicControlManager.currentPlaylistId.equals("2")) {
                                JsonObject obj2 = PlayListCommands.likedTracks;
                                if (obj2 != null && obj2.has("items")) {
                                    for(JsonElement array : obj2.get("items").getAsJsonArray()) {
                                        JsonObject trk = array.getAsJsonObject().getAsJsonObject("track");
                                        list.add(trk.get("id").getAsString());
                                    }
                                }
                            }
                            int index = list.indexOf(MusicControlManager.currentTrackId);
                            if(index < (list.size() - 1)) {
                                MusicControlManager.currentTrackId = list.get(index + 1);
                                String trackId = list.get(index + 1);
                                PlayerControlManager.playSpotifyPlaylist(MusicControlManager.currentPlaylistId, trackId);
                            } else {
                                MusicControlManager.currentTrackId = list.get(0);
                                String trackId = list.get(0);
                                PlayerControlManager.playSpotifyPlaylist(MusicControlManager.currentPlaylistId, trackId);
                            }
                        }
                    }
                    return obj;
                }
            } catch (Exception e) {
                LOG.warning("Music Time: Error trying to read and json parse the current track, error: " + e.getMessage());
            }
        } else {
            MusicControlManager.currentDeviceName = null;
            MusicControlManager.currentTrackName = null;
        }
        return null;
    }
}
