package com.musictime.intellij.plugin.musicjava;

import com.google.gson.JsonObject;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfo;
import com.musictime.intellij.plugin.SoftwareCoMusic;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
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

    public static String startPlayer(String playerName) {
        if(isMac()) {
            String[] args = {"open", "-a", playerName + ".app"};
            return runCommand(args, null);
        } else if(isWindows()) {
            String home = getUserHomeDir();
            String[] args = {home + "\\AppData\\Roaming\\Spotify\\Spotify.exe"};
            return runCommand(args, null);
        } else if (isLinux()) {
            // String result = runCmd(new String[]{"nohup", "/snap/bin/spotify", "2>&1"});
            ApplicationManager.getApplication().invokeLater(new Runnable() {
                public void run() {
                    String infoMsg = "Launching the Spotify desktop is currently not supported on linux.";
                    // ask to download the PM
                    Messages.showInfoMessage(infoMsg, SoftwareCoMusic.getPluginName());
                }
            });
        }
        return null;
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

    public static String RunLinuxCommand(String cmd) {

        String linuxCommandResult = "";
        try {
            Process p = Runtime.getRuntime().exec(cmd);

            BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));

            BufferedReader stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()));

            while ((linuxCommandResult = stdInput.readLine()) != null) {
                return linuxCommandResult;
            }
            while ((linuxCommandResult = stdError.readLine()) != null) {
                return null;
            }
        } catch (Exception e) {
            System.out.println("Error running command '" + cmd + "': " + e.getMessage());
            return null;
        }

        return linuxCommandResult;
    }

    public static String runCmd(String[] args) {
        Runtime run = Runtime.getRuntime();
        Process p = null;
        String command = String.join(" ", args);
        try
        {
            // Running the above command
            p = run.exec(args);

            StringBuilder output = new StringBuilder();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(p.getInputStream()));

            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line + "\n");
            }
            if (StringUtils.isBlank(line)) {
                line = "ok";
            }
            return line;

        } catch (IOException e) {
            System.out.println("Error running command '" + command + "': " + e.getMessage());
            return null;
        } finally {
            if (p != null) {
                p.destroy();
            }
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
