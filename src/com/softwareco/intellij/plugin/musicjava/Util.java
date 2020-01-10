package com.softwareco.intellij.plugin.musicjava;

import com.google.gson.JsonObject;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfo;
import org.apache.http.client.methods.HttpGet;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class Util {
    public static final Logger log = Logger.getLogger("Util");
    private static long lastAppAvailableCheck = 0;
    private static boolean appAvailable = true;
    private static Map<String, String> sessionMap = new HashMap<>();

    private final static int EOF = -1;

    public static boolean isLinux() {
        return (isWindows() || isMac()) ? false : true;
    }

    public static boolean isWindows() {
        return SystemInfo.isWindows;
    }

    public static boolean isMac() {
        return SystemInfo.isMac;
    }

    public static void updateServerStatus(boolean isOnlineStatus) {
        appAvailable = isOnlineStatus;
    }

    public static boolean isAppAvailable() {
        return appAvailable;
    }

    public synchronized static boolean isServerOnline() {
        long nowInSec = Math.round(System.currentTimeMillis() / 1000);
        // 5 min threshold
        boolean pastThreshold = (nowInSec - lastAppAvailableCheck > (60 * 5)) ? true : false;
        if (pastThreshold) {
            SoftwareResponse resp = Client.makeApiCall("/ping", HttpGet.METHOD_NAME, null);
            updateServerStatus(resp.isOk());
            lastAppAvailableCheck = nowInSec;
        }
        return isAppAvailable();
    }

    public static String getItem(String key) {
        String val = sessionMap.get(key);
        if (val != null) {
            return val;
        }
        return null;
    }

    public static void setItem(String key, String val) { sessionMap.put(key, val); }

    public static void showOfflinePrompt(boolean isTenMinuteReconnect) {
        final String reconnectMsg = (isTenMinuteReconnect) ? "in ten minutes. " : "soon. ";
        ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
                String infoMsg = "Our service is temporarily unavailable. " +
                        "We will try to reconnect again " + reconnectMsg +
                        "Your status bar will not update at this time.";
                // ask to download the PM
                Messages.showInfoMessage(infoMsg, Client.pluginName);
            }
        });
    }

    public static void showMsgPrompt(String infoMsg) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
                Messages.showInfoMessage(infoMsg, Client.pluginName);
            }
        });
    }

    // Apple scripts *************************************************************************

    protected static boolean isItunesRunning() {
        // get running of application "iTunes"
        String[] args = { "osascript", "-e", "get running of application \"iTunes\"" };
        String result = runCommand(args, null);
        return (result != null) ? Boolean.valueOf(result) : false;
    }

    protected static String itunesTrackScript = "tell application \"iTunes\"\n" +
            "set track_artist to artist of current track\n" +
            "set track_album to album of current track\n" +
            "set track_name to name of current track\n" +
            "set track_duration to duration of current track\n" +
            "set track_id to id of current track\n" +
            "set track_genre to genre of current track\n" +
            "set track_state to player state\n" +
            "set json to \"type='itunes';album='\" & track_album & \"';genre='\" & track_genre & \"';artist='\" & track_artist & \"';id='\" & track_id & \"';name='\" & track_name & \"';state='\" & track_state & \"';duration='\" & track_duration & \"'\"\n" +
            "end tell\n" +
            "return json\n";

    protected static String getItunesTrack() {
        String[] args = { "osascript", "-e", itunesTrackScript };
        return runCommand(args, null);
    }

    public static boolean isSpotifyRunning() {
        String[] args = { "osascript", "-e", "get running of application \"Spotify\"" };
        String result = runCommand(args, null);
        return (result != null) ? Boolean.valueOf(result) : false;
    }

    protected static boolean isSpotifyInstalled() {
        String[] args = { "osascript", "-e", "exists application \"Spotify\"" };
        String result = runCommand(args, null);
        return (result != null) ? Boolean.valueOf(result) : false;
    }

    protected static String spotifyTrackScript = "tell application \"Spotify\"\n" +
            "set track_artist to artist of current track\n" +
            "set track_album to album of current track\n" +
            "set track_name to name of current track\n" +
            "set track_duration to duration of current track\n" +
            "set track_id to id of current track\n" +
            "set track_state to player state\n" +
            "set json to \"type='spotify';album='\" & track_album & \"';genre='';artist='\" & track_artist & \"';id='\" & track_id & \"';name='\" & track_name & \"';state='\" & track_state & \"';duration='\" & track_duration & \"'\"\n" +
            "end tell\n" +
            "return json\n";

    protected static String getSpotifyTrack() {
        String[] args = { "osascript", "-e", spotifyTrackScript };
        return runCommand(args, null);
    }

    public static String startPlayer(String playerName) {
        String[] args = { "open", "-a", playerName + ".app" };
        return runCommand(args, null);
    }

    public static String playPlayer(String playerName) {
        String[] args = { "osascript", "-e", "tell application \""+ playerName + "\" to play" };
        return runCommand(args, null);
    }

    public static String pausePlayer(String playerName) {
        String[] args = { "osascript", "-e", "tell application \""+ playerName + "\" to pause" };
        return runCommand(args, null);
    }

    public static String previousTrack(String playerName) {
        String[] args = { "osascript", "-e", "tell application \""+ playerName + "\" to play (previous track)" };
        return runCommand(args, null);
    }

    public static String nextTrack(String playerName) {
        String[] args = { "osascript", "-e", "tell application \""+ playerName + "\" to play (next track)" };
        return runCommand(args, null);
    }

    protected static String stopPlayer(String playerName) {
        // `ps -ef | grep "${appName}" | grep -v grep | awk '{print $2}' | xargs kill`;
        String[] args = { "ps", "-ef", "|", "grep", "\"" + playerName + ".app\"", "|", "grep", "-v", "grep", "|", "awk", "'{print $2}'", "|", "xargs", "kill" };
        return runCommand(args, null);
    }

    public static String playPlaylist(String playerName, String playlist) {
        if(!playlist.contains("playlist")) {
            playlist = "spotify:playlist:" + playlist;
        }
        String[] args = { "osascript", "-e", "tell application \""+ playerName + "\" to play of playlist \""+ playlist +"\"" };
        return runCommand(args, null);
    }

    public static String playSongInPlaylist(String playerName, String playlist, String track) {
        if(!playlist.contains("playlist")) {
            playlist = "spotify:playlist:" + playlist;
        }
        if(!track.contains("track")) {
            track = "spotify:track:" + track;
        }
        String[] args = { "osascript", "-e", "tell application \""+ playerName + "\" to play track \""+ track +"\" in context \""+ playlist +"\"" };
        return runCommand(args, null);
    }

    public static String playTrack(String playerName, String track) {
        if(!track.contains("track")) {
            track = "spotify:track:" + track;
        }
        String[] args = { "osascript", "-e", "tell application \""+ playerName + "\" to play track \""+ track +"\"" };
        return runCommand(args, null);
    }

    public static JsonObject getCurrentMusicTrack() {
        JsonObject jsonObj = new JsonObject();
        if (!isMac()) {
            return jsonObj;
        }

        boolean spotifyRunning = isSpotifyRunning();
        boolean itunesRunning = isItunesRunning();

        String trackInfo = "";
        // Vintage Trouble, My Whole World Stopped Without You, spotify:track:7awBL5Pu8LD6Fl7iTrJotx, My Whole World Stopped Without You, 244080
        if (spotifyRunning) {
            trackInfo = getSpotifyTrack();
        } else if (itunesRunning) {
            trackInfo = getItunesTrack();
        }

        if (trackInfo != null && !trackInfo.equals("")) {
            // trim and replace things
            trackInfo = trackInfo.trim();
            trackInfo = trackInfo.replace("\"", "");
            trackInfo = trackInfo.replace("'", "");
            String[] paramParts = trackInfo.split(";");
            for (String paramPart : paramParts) {
                paramPart = paramPart.trim();
                String[] params = paramPart.split("=");
                if (params != null && params.length == 2) {
                    jsonObj.addProperty(params[0], params[1]);
                }
            }

        }
        return jsonObj;
    }

    /**
     * Execute the args
     * @param args
     * @return
     */
    public static String runCommand(String[] args, String dir) {
        // use process builder as it allows to run the command from a specified dir
        ProcessBuilder builder = new ProcessBuilder();

        try {
            builder.command(args);
            if (dir != null) {
                // change to the directory to run the command
                builder.directory(new File(dir));
            }
            Process process = builder.start();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            InputStream is = process.getInputStream();
            copyLarge(is, baos, new byte[4096]);
            return baos.toString().trim();

        } catch (Exception e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private static long copyLarge(InputStream input, OutputStream output, byte[] buffer) throws IOException {

        long count = 0;
        int n;
        while (EOF != (n = input.read(buffer))) {
            output.write(buffer, 0, n);
            count += n;
        }
        return count;
    }
}
