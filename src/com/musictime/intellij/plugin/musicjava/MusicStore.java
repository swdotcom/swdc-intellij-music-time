package com.musictime.intellij.plugin.musicjava;

import java.util.ArrayList;
import java.util.List;

public class MusicStore {
    public static String SPOTIFY_CLIENT_ID = null;
    public static String SPOTIFY_CLIENT_SECRET = null;
    public static String spotifyAccountType = null; // Premium or Non-Premium
    public static String spotifyUserId = null;

    public static List<String> playlistIds = new ArrayList<>();
    public static String currentPlaylistId = null;
    public static List<String> tracksByPlaylistId = new ArrayList<>();
    public static String currentTrackId = null;
    public static String currentTrackName = null;
    public static List<String> spotifyDeviceIds = new ArrayList<>();
    public static String currentDeviceId = null;
    public static String currentDeviceName = null;

    public static String topSpotifyPlaylistId = "1";
    public static String myAIPlaylistId = null;
    public static List<String> userPlaylistIds = new ArrayList<>();

    public static void resetConfig() {
        spotifyAccountType = null; // Premium or Non-Premium
        spotifyUserId = null;
        playlistIds = new ArrayList<>();
        currentPlaylistId = null;
        currentTrackId = null;
        currentTrackName = null;
        tracksByPlaylistId = new ArrayList<>();
        spotifyDeviceIds = new ArrayList<>();
        currentDeviceName = null;
        currentDeviceId = null;
    }

    public static String getSpotifyClientId() {
        return SPOTIFY_CLIENT_ID;
    }

    public static void setSpotifyClientId(String spotifyClientId) {
        SPOTIFY_CLIENT_ID = spotifyClientId;
    }

    public static String getSpotifyClientSecret() {
        return SPOTIFY_CLIENT_SECRET;
    }

    public static void setSpotifyClientSecret(String spotifyClientSecret) {
        SPOTIFY_CLIENT_SECRET = spotifyClientSecret;
    }

    public static String getSpotifyAccountType() {
        return spotifyAccountType;
    }

    public static void setSpotifyAccountType(String spotifyAccountType) {
        MusicStore.spotifyAccountType = spotifyAccountType;
    }

    public static String getSpotifyUserId() {
        return spotifyUserId;
    }

    public static void setSpotifyUserId(String spotifyUserId) {
        MusicStore.spotifyUserId = spotifyUserId;
    }

    public static List<String> getPlaylistIds() {
        return playlistIds;
    }

    public static void setPlaylistIds(List<String> playlistIds) {
        MusicStore.playlistIds = playlistIds;
    }

    public static String getCurrentPlaylistId() {
        return currentPlaylistId;
    }

    public static void setCurrentPlaylistId(String currentPlaylistId) {
        MusicStore.currentPlaylistId = currentPlaylistId;
    }

    public static List<String> getTracksByPlaylistId() {
        return tracksByPlaylistId;
    }

    public static void setTracksByPlaylistId(List<String> tracksByPlaylistId) {
        MusicStore.tracksByPlaylistId = tracksByPlaylistId;
    }

    public static String getCurrentTrackId() {
        return currentTrackId;
    }

    public static void setCurrentTrackId(String currentTrackId) {
        MusicStore.currentTrackId = currentTrackId;
    }

    public static String getCurrentTrackName() {
        return currentTrackName;
    }

    public static void setCurrentTrackName(String currentTrackName) {
        MusicStore.currentTrackName = currentTrackName;
    }

    public static List<String> getSpotifyDeviceIds() {
        return spotifyDeviceIds;
    }

    public static void setSpotifyDeviceIds(List<String> spotifyDeviceIds) {
        MusicStore.spotifyDeviceIds = spotifyDeviceIds;
    }

    public static String getCurrentDeviceId() {
        return currentDeviceId;
    }

    public static void setCurrentDeviceId(String currentDeviceId) {
        MusicStore.currentDeviceId = currentDeviceId;
    }

    public static String getCurrentDeviceName() {
        return currentDeviceName;
    }

    public static void setCurrentDeviceName(String currentDeviceName) {
        MusicStore.currentDeviceName = currentDeviceName;
    }

    public static String getTopSpotifyPlaylistId() {
        return topSpotifyPlaylistId;
    }

    public static void setTopSpotifyPlaylistId(String topSpotifyPlaylistId) {
        MusicStore.topSpotifyPlaylistId = topSpotifyPlaylistId;
    }

    public static String getMyAIPlaylistId() {
        return myAIPlaylistId;
    }

    public static void setMyAIPlaylistId(String myAIPlaylistId) {
        MusicStore.myAIPlaylistId = myAIPlaylistId;
    }

    public static List<String> getUserPlaylistIds() {
        return userPlaylistIds;
    }

    public static void setUserPlaylistIds(List<String> userPlaylistIds) {
        MusicStore.userPlaylistIds = userPlaylistIds;
    }
}
