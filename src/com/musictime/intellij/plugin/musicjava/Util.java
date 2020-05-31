package com.musictime.intellij.plugin.musicjava;

import com.google.gson.JsonObject;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfo;
import com.musictime.intellij.plugin.SoftwareCoMusic;
import com.musictime.intellij.plugin.SoftwareCoUtils;
import com.musictime.intellij.plugin.music.MusicControlManager;
import com.musictime.intellij.plugin.music.PlayListCommands;
import com.musictime.intellij.plugin.music.PlaylistManager;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
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

    public static String getUserHomeDir() {
        return System.getProperty("user.home");
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
        String result = null;
        if(isWindows()) {
            String spotify = getUserHomeDir() + "\\AppData\\Roaming\\Spotify\\Spotify.exe";
            File file = new File(spotify);
            if (file.exists()) {
                result = "true";
            }
        } else if(isMac()) {
            String[] args = {"osascript", "-e", "exists application \"Spotify\""};
            result = runCommand(args, null);
        } else if (isLinux()) {
            String[] args = {"which", "spotify"};
            result = runCommand(args, null);
        }
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

    private static boolean isCommandResultOk(String result) {
        if (result != null && result.indexOf("ERROR:") == 0) {
            return false;
        }
        return true;
    }

    public static boolean startPlayer() {
        String result = null;
        if (isMac()) {
            result = runCmd("open -a Spotify.app");
            if (!isCommandResultOk(result)) {
                result = runCmd("open -a Spotify");
            }
        } else if (isWindows()) {
            String home = getUserHomeDir();
            result = runCmd(home + "\\AppData\\Roaming\\Spotify\\Spotify.exe");
            if (!isCommandResultOk(result)) {
                // try with a different command
                result = runCmd("cmd /c spotify.exe");
                if (!isCommandResultOk(result)) {
                    result = runCmd("START SPOTIFY");
                    if (!isCommandResultOk(result)) {
                        result = runCmd("spotify");
                        if (!isCommandResultOk(result)) {
                            result = runCmd("spotify.exe");
                            if (!isCommandResultOk(result)) {
                                result = runCmd("%APPDATA%\\Spotify\\Spotify.exe");
                            }
                        }
                    }
                }
            }
        } else if (isLinux()) {
            new Thread(() -> {
                try {
                    String cmdResult = runCmdWithArgs(new String[]{"/bin/sh", "-c", "nohup /snap/bin/spotify > /tmp/spotify.out 2>&1 &"});
                    if (!isCommandResultOk(cmdResult)) {
                        // try it with spotify
                        cmdResult = runCmdWithArgs(new String[]{"/bin/sh", "-c", "nohup spotify > /tmp/spotify.out 2>&1 &"});
                        if (!isCommandResultOk(cmdResult)) {
                            cmdResult = runCmdWithArgs(new String[]{"/bin/sh", "-c", "nohup /usr/bin/spotify > /tmp/spotify.out 2>&1 &"});
                            if (!isCommandResultOk(cmdResult)) {
                                cmdResult = runCmdWithArgs(new String[]{"/bin/sh", "-c", "flatpak run com.spotify.Client > /tmp/spotify.out 2>&1 &"});
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println(e);
                }
            }).start();
        }
        return (!isCommandResultOk(result)) ? false : true;
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

        if (playlist == null ||
                (playlist.equals(PlayListCommands.likedPlaylistId) ||
                playlist.equals(PlayListCommands.recommendedPlaylistId))) {
            return playTrack(playerName, track);
        }

        if(!playlist.contains("playlist")) {
            playlist = "spotify:playlist:" + playlist;
        }
        if(!track.contains("track")) {
            track = "spotify:track:" + track;
        }
        // 'tell application "{0}" to play track "{1}" {2} "{3}"'
        String[] args = { "osascript", "-e", "tell application \""+ playerName + "\" to play track \""+ track +"\" in context \""+ playlist +"\"" };
        String result = runCommand(args, null);
        return result;
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
            return "ERROR: " + e.getMessage();
        }
    }

    public static String runCmd(String cmd) {
        String result = "";
        try {
            Process p = Runtime.getRuntime().exec(cmd);

            BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));

            BufferedReader stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()));

            while ((result = stdInput.readLine()) != null) {
                return result;
            }
            while ((result = stdError.readLine()) != null) {
                return "ERROR: " + result;
            }
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }

        return result;
    }

    public static String runCmdWithArgs(String[] args) {
        String result = "";
        try {
            Process p = Runtime.getRuntime().exec(args);

            BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));

            BufferedReader stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()));

            while ((result = stdInput.readLine()) != null) {
                return result;
            }
            while ((result = stdError.readLine()) != null) {
                return "ERROR: " + result;
            }
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }

        return result;
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
