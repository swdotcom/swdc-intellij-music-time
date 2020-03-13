/**
 * Copyright (c) 2018 by Software.com
 * All rights reserved
 */
package com.softwareco.intellij.plugin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.softwareco.intellij.plugin.music.*;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;

import java.awt.event.MouseEvent;
import java.io.*;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SoftwareCoSessionManager {

    private static SoftwareCoSessionManager instance = null;
    public static final Logger log = Logger.getLogger("SoftwareCoSessionManager");
    private static Map<String, String> sessionMap = new HashMap<>();
    public static String pluginRootPath = null;

    private static JsonArray keystrokeData = new JsonArray();
    public static int playerState = 0; // 0 = inactive & 1 = active
    public static boolean isServerActive = false;

    /* cache data*/
    private static int keyStrokes = 0;
    private static int add = 0;
    private static int paste = 0;
    private static int delete = 0;
    private static int netkeys = 0;
    private static int linesAdded = 0;
    private static int linesRemoved = 0;
    private static int open = 0;
    private static int close = 0;
    private static Map<String, JsonObject> musicData = new HashMap<>();

    public static long start = 0L;
    public static long local_start = 0L;
    private static long lastAppAvailableCheck = 0;
    private static long lastServerCheck = 0;

    private static String SERVICE_NOT_AVAIL =
            "Our service is temporarily unavailable.\n\nPlease try again later.\n";

    public static SoftwareCoSessionManager getInstance() {
        if (instance == null) {
            instance = new SoftwareCoSessionManager();
        }
        return instance;
    }

    public static void resetCacheData() {
        add = 0;
        paste = 0;
        delete = 0;
        netkeys = 0;
        linesAdded = 0;
        linesRemoved = 0;
        open = 0;
        close = 0;
        keyStrokes = 0;
        musicData = new HashMap<>();
    }

    public static boolean softwareSessionFileExists() {
        // don't auto create the file
        String file = getSoftwareSessionFile(false);
        // check if it exists
        File f = new File(file);
        return f.exists();
    }

    public static boolean musicDataFileExists() {
        // don't auto create the file
        String file = getMusicDataFile(false);
        // check if it exists
        File f = new File(file);
        return f.exists();
    }

    public static boolean jwtExists() {
        String jwt = getItem("jwt");
        return (jwt != null && !jwt.equals("")) ? true : false;
    }

    public static String getSoftwareDir(boolean autoCreate) {
        String softwareDataDir = SoftwareCoUtils.getUserHomeDir();
        if (SoftwareCoUtils.isWindows()) {
            softwareDataDir += "\\.software";
        } else {
            softwareDataDir += "/.software";
        }

        File f = new File(softwareDataDir);
        if (!f.exists()) {
            // make the directory
            f.mkdirs();
        }

        return softwareDataDir;
    }

    public static String getSoftwareSessionFile(boolean autoCreate) {
        String file = getSoftwareDir(autoCreate);
        if (SoftwareCoUtils.isWindows()) {
            file += "\\session.json";
        } else {
            file += "/session.json";
        }
        return file;
    }

    public static String getMusicDataFile(boolean autoCreate) {
        String file = getSoftwareDir(autoCreate);
        if (SoftwareCoUtils.isWindows()) {
            file += "\\musicData.json";
        } else {
            file += "/musicData.json";
        }
        return file;
    }

    private static String getSongSessionDataFile(boolean autoCreate) {
        String file = getSoftwareDir(autoCreate);
        if (SoftwareCoUtils.isWindows()) {
            file += "\\songSessionData.json";
        } else {
            file += "/songSessionData.json";
        }
        return file;
    }

    private static String getSoftwareDataStoreFile() {
        String file = getSoftwareDir(true);
        if (SoftwareCoUtils.isWindows()) {
            file += "\\data.json";
        } else {
            file += "/data.json";
        }
        return file;
    }

    private static String getMusicDashboardFile() {
        String path = new File(".").getAbsolutePath();
        log.log(Level.INFO, "Root Path: " + path);
        String file = getSoftwareDir(true);
        if (SoftwareCoUtils.isWindows()) {
            file += "\\musicTime.txt";
        } else {
            file += "/musicTime.txt";
        }
        return file;
    }

    private static String getLocalREADMEFile() {
        //VirtualFile root = ModuleRootManager.getInstance(ModuleManager.getInstance(getOpenProject()).getModules()[0]).getContentRoots()[0];
        String file = pluginRootPath;
        if (SoftwareCoUtils.isWindows()) {
            file += "\\README.txt";
        } else {
            file += "/README.txt";
        }
        return file;
    }

    public static Project getOpenProject() {
        ProjectManager projMgr = ProjectManager.getInstance();
        Project[] projects = projMgr.getOpenProjects();
        if (projects != null && projects.length > 0) {
            return projects[0];
        }
        return null;
    }

    public synchronized static boolean isServerOnline() {
        long nowInSec = Math.round(System.currentTimeMillis() / 1000);
        // 5 min threshold
        boolean pastThreshold = (nowInSec - lastAppAvailableCheck > (60 * 5)) ? true : false;
        if (pastThreshold) {
            SoftwareResponse resp = SoftwareCoUtils.makeApiCall("/ping", HttpGet.METHOD_NAME, null);
            SoftwareCoUtils.updateServerStatus(resp.isOk());
            lastAppAvailableCheck = nowInSec;
        }
        return SoftwareCoUtils.isAppAvailable();
    }

    public synchronized static boolean serverCheck() {
        long nowInSec = Math.round(System.currentTimeMillis() / 1000);
        // 10 second threshold
        boolean pastThreshold = (nowInSec - lastServerCheck > (10)) ? true : false;
        if (pastThreshold) {
            SoftwareResponse resp = SoftwareCoUtils.makeApiCall("/ping", HttpGet.METHOD_NAME, null);
            SoftwareCoUtils.updateServerStatus(resp.isOk());
            lastServerCheck = nowInSec;
        }
        return SoftwareCoUtils.isAppAvailable();
    }

    public synchronized static boolean directServerOnlineCheck() {
        SoftwareResponse resp = SoftwareCoUtils.makeApiCall("/ping", HttpGet.METHOD_NAME, null);
        SoftwareCoUtils.updateServerStatus(resp.isOk());
        return SoftwareCoUtils.isAppAvailable();
    }

    public void storePayload(String payload) {
        if (payload == null || payload.length() == 0) {
            return;
        }
        if (SoftwareCoUtils.isWindows()) {
            payload += "\r\n";
        } else {
            payload += "\n";
        }
        // Storing kpm data in data.json
        if(!SoftwareCoMusic.getCodeTimePluginState()) {
            String dataStoreFile = getSoftwareDataStoreFile();
            File f = new File(dataStoreFile);
            try {
                log.info("Music Time: Storing kpm metrics in data.json: " + payload);
                Writer output;
                output = new BufferedWriter(new FileWriter(f, true));  //clears file every time
                output.append(payload);
                output.close();
            } catch (Exception e) {
                log.warning("Music Time: Error appending to the Software data store file, error: " + e.getMessage());
            }
        }

        // Storing kmp data in musicData.json
        if(playerState == 1) {
            String dataStoreFile = getMusicDataFile(true);
            File f = new File(dataStoreFile);
            try {
                log.info("Music Time: Storing kpm metrics in musicData.json: " + payload);
                Writer output;
                output = new BufferedWriter(new FileWriter(f, true));  //clears file every time
                output.append(payload);
                output.close();
            } catch (Exception e) {
                log.warning("Music Time: Error appending to the Software data store file, error: " + e.getMessage());
            }
        } else if(playerState == 0) {
            String dataStoreFile = getMusicDataFile(false);
            deleteFile(dataStoreFile);
            keystrokeData = new JsonArray();
        }
    }

    public void sendOfflineData() {
        final String dataStoreFile = getSoftwareDataStoreFile();
        File f = new File(dataStoreFile);

        if (f.exists()) {
            // found a data file, check if there's content
            StringBuffer sb = new StringBuffer();
            try {
                FileInputStream fis = new FileInputStream(f);

                //Construct BufferedReader from InputStreamReader
                BufferedReader br = new BufferedReader(new InputStreamReader(fis));

                String line = null;
                while ((line = br.readLine()) != null) {
                    if (line.length() > 0) {
                        sb.append(line).append(",");
                    }
                }

                br.close();

                if (sb.length() > 0) {
                    // we have data to send
                    String payloads = sb.toString();
                    payloads = payloads.substring(0, payloads.lastIndexOf(","));
                    payloads = "[" + payloads + "]";

                    JsonArray jsonArray = (JsonArray) SoftwareCoMusic.jsonParser.parse(payloads);

                    // delete the file
                    deleteFile(dataStoreFile);

                    JsonArray batch = new JsonArray();
                    // go through the array about 50 at a time
                    for (int i = 0; i < jsonArray.size(); i++) {
                        batch.add(jsonArray.get(i));
                        if (i > 0 && i % 50 == 0) {
                            String payloadData = SoftwareCoMusic.gson.toJson(batch);
                            SoftwareResponse resp =
                                    SoftwareCoUtils.makeApiCall("/data/batch", HttpPost.METHOD_NAME, payloadData);
                            if (!resp.isOk()) {
                                // add these back to the offline file
                                log.info("Music Time: Unable to send batch data: " + resp.getErrorMessage());
                            }
                            batch = new JsonArray();
                        }
                    }
                    if (batch.size() > 0) {
                        String payloadData = SoftwareCoMusic.gson.toJson(batch);
                        SoftwareResponse resp =
                                SoftwareCoUtils.makeApiCall("/data/batch", HttpPost.METHOD_NAME, payloadData);
                        if (!resp.isOk()) {
                            // add these back to the offline file
                            log.info("Music Time: Unable to send batch data: " + resp.getErrorMessage());
                        }
                    }

                } else {
                    log.info("Music Time: No offline data to send");
                }
            } catch (Exception e) {
                log.warning("Music Time: Error trying to read and send offline data, error: " + e.getMessage());
            }
        }
    }

    public static void processMusicPayload(JsonObject object) {

        TrackInfo track = TrackInfoManager.getTrackInfo();

        if(object.has("item")) {
            JsonObject item = object.get("item").getAsJsonObject();
            if (item.has("album")) {
                JsonObject album = item.get("album").getAsJsonObject();
                if (album.has("available_markets"))
                    album.remove("available_markets");

                if (album.has("external_urls"))
                    album.remove("external_urls");

                track.setAlbum(album);
            }

            if (item.has("artists")) {
                JsonArray artistNames = track.getArtist_names();
                JsonArray genre = track.getGenre();
                String artist = "";
                for (JsonElement arr : item.get("artists").getAsJsonArray()) {
                    artist += arr.getAsJsonObject().get("name").getAsString() + ",";
                    artistNames.add(arr.getAsJsonObject().get("name").getAsString());
                    if (arr.getAsJsonObject().has("genres")) {
                        for (JsonElement gen : arr.getAsJsonObject().get("genres").getAsJsonArray())
                            genre.add(gen.getAsString());
                    }
                }
                artist = artist.substring(0, artist.lastIndexOf(","));
                track.setArtists(item.get("artists").getAsJsonArray());
                track.setArtist_names(artistNames);
                track.setArtist(artist);
                track.setGenre(genre);
            }

            if (item.has("disc_number"))
                track.setDisc_number(item.get("disc_number").getAsInt());

            if (item.has("duration_ms")) {
                track.setDuration_ms(item.get("duration_ms").getAsLong());
                track.setDuration(item.get("duration_ms").getAsLong());
            }

            if (item.has("explicit"))
                track.setExplicit(item.get("explicit").getAsBoolean());

            if (item.has("id"))
                track.setId(item.get("id").getAsString());

            if (item.has("is_local"))
                track.setIs_local(item.get("is_local").getAsBoolean());

            if (item.has("name"))
                track.setName(item.get("name").getAsString());

            if (item.has("popularity"))
                track.setPopularity(item.get("popularity").getAsInt());

            if (item.has("preview_url") && !item.get("preview_url").isJsonNull())
                track.setPreview_url(item.get("preview_url").getAsString());

            if (item.has("track_number"))
                track.setTrack_number(item.get("track_number").getAsInt());

            if (item.has("type"))
                track.setType(item.get("type").getAsString());

            if (item.has("uri"))
                track.setUri(item.get("uri").getAsString());

            if (object.has("progress_ms"))
                track.setProgress_ms(object.get("progress_ms").getAsLong());

            if (object.get("is_playing").getAsBoolean())
                track.setState("playing");
            else
                track.setState("paused");
        } else {
            if(object.has("album")) {
                JsonObject obj = new JsonObject();
                obj.addProperty("album_type", "album");
                obj.addProperty("name", object.get("album").getAsString());
                track.setAlbum(obj);
            }

            if(object.has("artist")) {
                JsonArray arr = new JsonArray();
                JsonObject obj = new JsonObject();
                obj.addProperty("name", object.get("artist").getAsString());
                arr.add(obj);
                track.setArtists(arr);
                track.setArtist(object.get("artist").getAsString());
            }

            if(object.has("name"))
                track.setName(object.get("name").getAsString());

            if(object.has("id")) {
                String track_Id = object.get("id").getAsString();
                if(track_Id.contains("track")) {
                    String[] paramParts = track_Id.split(":");
                    track_Id = paramParts[paramParts.length-1].trim();
                }
                track.setId(track_Id);
            }

            if(object.has("duration")) {
                track.setDuration_ms(object.get("duration").getAsLong());
                track.setDuration(object.get("duration").getAsLong());
            }

            if(object.has("discNumber"))
                track.setDisc_number(object.get("discNumber").getAsInt());

            if(object.has("popularity"))
                track.setPopularity(object.get("popularity").getAsInt());

            if(object.has("state"))
                track.setState(object.get("state").getAsString());

        }

        track.setPlayerType(MusicControlManager.playerType);

        if(MusicControlManager.likedTracks.containsKey(track.getId()))
            track.setLoved(true);
        else
            track.setLoved(false);

        KeystrokeCount kc = KeystrokeManager.getInstance().getKeystrokeCount();
        if(kc != null) {
            kc = kc.clone();
            processMusicData();
            track.setVersion(kc.getVersion());
            track.setPluginId(kc.getPluginId());
            track.setAdd(add);
            track.setPaste(paste);
            track.setDelete(delete);
            track.setNetkeys(netkeys);
            track.setLinesAdded(linesAdded);
            track.setLinesRemoved(linesRemoved);
            track.setOpen(open);
            track.setClose(close);
            track.setKeystrokes(keyStrokes);
            SoftwareCoUtils.TimesData timesData = SoftwareCoUtils.getTimesData();
            long end = timesData.now;
            long end_local = timesData.local_now;
            if(start == 0) {
                track.setStart(end - track.getDuration());
                track.setLocal_start(end_local - track.getDuration());
            } else {
                track.setStart(start);
                track.setLocal_start(local_start);
            }
            track.setEnd(end);
            track.setLocal_end(end_local);
            track.setOffset(timesData.offset);
            track.setTimezone(timesData.timezone);
            track.setOs(kc.getOs());
            track.setSource(musicData);
        }

        final String payload = SoftwareCoMusic.gson.toJson(track);

        if(isServerActive) {
            sendMusicOfflineData();
            SoftwareCoUtils.sendSongSessionPayload(payload);
        } else if(SoftwareCoUtils.isMac() && MusicControlManager.playerType.equals("Desktop Player")) {
            storeSongSessionPayload(payload);
        }
        // Reset song session payload
        TrackInfoManager.resetTrackInfo();
        resetCacheData();
    }

    public static void storeSongSessionPayload(String payload) {
        if (payload == null || payload.length() == 0) {
            return;
        }
        if (SoftwareCoUtils.isWindows()) {
            payload += "\r\n";
        } else {
            payload += "\n";
        }
        String dataStoreFile = getSongSessionDataFile(true);
        File f = new File(dataStoreFile);
        try {
            log.info("Music Time: Storing song session payload: " + payload);
            Writer output;
            output = new BufferedWriter(new FileWriter(f, true));  //clears file every time
            output.append(payload);
            output.close();
        } catch (Exception e) {
            log.warning("Music Time: Error appending to the Software song session file, error: " + e.getMessage());
        }
    }

    public static void processMusicData() {
        final String dataStoreFile = getMusicDataFile(false);
        File f = new File(dataStoreFile);

        if (f.exists()) {
            // found a data file, check if there's content
            StringBuffer sb = new StringBuffer();
            try {
                FileInputStream fis = new FileInputStream(f);

                //Construct BufferedReader from InputStreamReader
                BufferedReader br = new BufferedReader(new InputStreamReader(fis));

                String line = null;
                while ((line = br.readLine()) != null) {
                    if (line.length() > 0) {
                        sb.append(line).append(",");
                    }
                }

                br.close();

                if (sb.length() > 0) {
                    // we have data to send
                    String payloads = sb.toString();
                    payloads = payloads.substring(0, payloads.lastIndexOf(","));
                    payloads = "[" + payloads + "]";

                    JsonArray jsonArray = (JsonArray) SoftwareCoMusic.jsonParser.parse(payloads);

                    // delete the file
                    deleteFile(dataStoreFile);

                    isServerActive = directServerOnlineCheck();
                    /* process keystroke data */
                    processKeystrokes(jsonArray);

                } else {
                    isServerActive = directServerOnlineCheck();
                    log.info("Music Time: No keystroke data to send");
                }
            } catch (Exception e) {
                log.warning("Music Time: Error trying to read music data file, error: " + e.getMessage());
            }
        } else {
            if(keystrokeData.size() > 0) {
                /* process keystroke data */
                processKeystrokes(keystrokeData);
                keystrokeData = new JsonArray();
                isServerActive = directServerOnlineCheck();
            } else {
                isServerActive = directServerOnlineCheck();
                log.info("Music Time: No keystroke data to send");
            }
        }
    }

    private static void processKeystrokes(JsonArray jsonArray) {
        // go through the array
        for (int i = 0; i < jsonArray.size(); i++) {
            JsonObject obj = jsonArray.get(i).getAsJsonObject();
            add += obj.get("add").getAsInt();
            paste += obj.get("paste").getAsInt();
            delete += obj.get("delete").getAsInt();
            netkeys += obj.get("netkeys").getAsInt();
            linesAdded += obj.get("linesAdded").getAsInt();
            linesRemoved += obj.get("linesRemoved").getAsInt();
            open += obj.get("open").getAsInt();
            close += obj.get("close").getAsInt();
            keyStrokes += obj.get("keystrokes").getAsInt();

            /* source data */
            JsonObject source = obj.get("source").getAsJsonObject();
            Set<String> keys = source.keySet();
            for(String key : keys) {
                JsonObject data = musicData.get(key);
                if(data == null) {
                    data = source.get(key).getAsJsonObject();
                    musicData.put(key, data);
                } else {
                    JsonObject val = source.get(key).getAsJsonObject();
                    int add = data.get("add").getAsInt() + val.get("add").getAsInt();
                    data.addProperty("add", add);
                    int paste = data.get("paste").getAsInt() + val.get("paste").getAsInt();
                    data.addProperty("paste", paste);
                    int open = data.get("open").getAsInt() + val.get("open").getAsInt();
                    data.addProperty("open", open);
                    int close = data.get("close").getAsInt() + val.get("close").getAsInt();
                    data.addProperty("close", close);
                    int delete = data.get("delete").getAsInt() + val.get("delete").getAsInt();
                    data.addProperty("delete", delete);
                    data.addProperty("length", val.get("length").getAsInt());
                    int netkeys = data.get("netkeys").getAsInt() + val.get("netkeys").getAsInt();
                    data.addProperty("netkeys", netkeys);
                    data.addProperty("lines", val.get("lines").getAsInt());
                    int linesAdded = data.get("linesAdded").getAsInt() + val.get("linesAdded").getAsInt();
                    data.addProperty("linesAdded", linesAdded);
                    int linesRemoved = data.get("linesRemoved").getAsInt() + val.get("linesRemoved").getAsInt();
                    data.addProperty("linesRemoved", linesRemoved);
                    data.addProperty("end", val.get("end").getAsLong());
                    data.addProperty("local_end", val.get("local_end").getAsLong());
                    musicData.put(key, data);
                }
            }
        }
    }

    public static void sendMusicOfflineData() {
        final String dataStoreFile = getSongSessionDataFile(false);
        File f = new File(dataStoreFile);

        if (f.exists()) {
            // found a data file, check if there's content
            StringBuffer sb = new StringBuffer();
            try {
                FileInputStream fis = new FileInputStream(f);

                //Construct BufferedReader from InputStreamReader
                BufferedReader br = new BufferedReader(new InputStreamReader(fis));

                String line = null;
                while ((line = br.readLine()) != null) {
                    if (line.length() > 0) {
                        sb.append(line).append(",");
                    }
                }

                br.close();

                if (sb.length() > 0) {
                    // we have data to send
                    String payloads = sb.toString();
                    payloads = payloads.substring(0, payloads.lastIndexOf(","));
                    payloads = "[" + payloads + "]";

                    JsonArray jsonArray = (JsonArray) SoftwareCoMusic.jsonParser.parse(payloads);

                    // delete the file
                    deleteFile(dataStoreFile);

                    JsonArray batch = new JsonArray();
                    // go through the array about 50 at a time
                    for (int i = 0; i < jsonArray.size(); i++) {
                        batch.add(jsonArray.get(i));
                        if (i > 0 && i % 50 == 0) {
                            String payloadData = SoftwareCoMusic.gson.toJson(batch);
                            boolean resp = SoftwareCoUtils.sendSongSessionPayload(payloadData);
                            if (!resp) {
                                // add these back to the offline file
                                storeSongSessionPayload(jsonArray.get(i).getAsString());
                                log.info("Music Time: Unable to send batch data");
                            }
                            batch = new JsonArray();
                        }
                    }
                    if (batch.size() > 0) {
                        String payloadData = SoftwareCoMusic.gson.toJson(batch);
                        boolean resp = SoftwareCoUtils.sendSongSessionPayload(payloadData);
                        if (!resp) {
                            // add these back to the offline file
                            for (int i = 0; i < jsonArray.size(); i++) {
                                storeSongSessionPayload(jsonArray.get(i).getAsString());
                            }
                            log.info("Music Time: Unable to send batch data");
                        }
                    }

                } else {
                    log.info("Music Time: No offline data to send");
                }
            } catch (Exception e) {
                log.warning("Music Time: Error trying to read and send offline data, error: " + e.getMessage());
            }
        }
    }

    public static void deleteFile(String file) {
        File f = new File(file);
        // if the file exists, delete it
        if (f.exists()) {
            f.delete();
        }
    }

    public static void setItem(String key, String val) {
        sessionMap.put(key, val);
        JsonObject jsonObj = getSoftwareSessionAsJson();
        jsonObj.addProperty(key, val);

        String content = jsonObj.toString();

        String sessionFile = getSoftwareSessionFile(true);

        try {
            Writer output = new BufferedWriter(new FileWriter(sessionFile));
            output.write(content);
            output.close();
        } catch (Exception e) {
            log.warning("Music Time: Failed to write the key value pair (" + key + ", " + val + ") into the session, error: " + e.getMessage());
        }
    }

    public static String getItem(String key) {
        String val = sessionMap.get(key);
        if (val != null) {
            return val;
        }
        JsonObject jsonObj = getSoftwareSessionAsJson();
        if (jsonObj != null && jsonObj.has(key) && !jsonObj.get(key).isJsonNull()) {
            return jsonObj.get(key).getAsString();
        }
        return null;
    }

    private static JsonObject getSoftwareSessionAsJson() {
        JsonObject data = null;

        String sessionFile = getSoftwareSessionFile(true);
        File f = new File(sessionFile);
        if (f.exists()) {
            try {
                byte[] encoded = Files.readAllBytes(Paths.get(sessionFile));
                String content = new String(encoded, Charset.defaultCharset());
                if (content != null) {
                    // json parse it
                    data = SoftwareCoMusic.jsonParser.parse(content).getAsJsonObject();
                }
            } catch (Exception e) {
                log.warning("Music Time: Error trying to read and json parse the session file, error: " + e.getMessage());
            }
        }
        return (data == null) ? new JsonObject() : data;
    }

    public static void fetchMusicTimeMetricsDashboard(String plugin, boolean isHtml) {
        boolean isOnline = isServerOnline();
        String dashboardFile = SoftwareCoSessionManager.getMusicDashboardFile();
        String jwt = SoftwareCoSessionManager.getItem("jwt");

        Writer writer = null;

        if (isOnline) {
            //String api = "/dashboard?plugin=" + plugin + "&linux=" + SoftwareCoUtils.isLinux() + "&html=" + isHtml;
            String api = "/dashboard/music";
            SoftwareResponse response = SoftwareCoUtils.makeApiCall(api, HttpGet.METHOD_NAME, null, jwt);
            if(response.isOk()) {
                String dashboardSummary = response.getJsonStr();
                if (dashboardSummary == null || dashboardSummary.trim().isEmpty()) {
                    dashboardSummary = SERVICE_NOT_AVAIL;
                }

                // write the dashboard summary content
                try {
                    writer = new BufferedWriter(new OutputStreamWriter(
                            new FileOutputStream(dashboardFile), StandardCharsets.UTF_8));
                    writer.write(dashboardSummary);
                } catch (IOException ex) {
                    // Report
                } finally {
                    try {
                        writer.close();
                    } catch (Exception ex) {/*ignore*/}
                }
            }
        }
    }

    public static void launchMusicTimeMetricsDashboard() {
        boolean isOnline = isServerOnline();
        if (!isOnline) {
            SoftwareCoUtils.showOfflinePrompt(false);
        }
        Project p = getOpenProject();
        if (p == null) {
            return;
        }

        fetchMusicTimeMetricsDashboard("music-time", false);

        String musicTimeFile = SoftwareCoSessionManager.getMusicDashboardFile();
        File f = new File(musicTimeFile);

        VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(f);
        OpenFileDescriptor descriptor = new OpenFileDescriptor(p, vFile);
        FileEditorManager.getInstance(p).openTextEditor(descriptor, true);
    }

    public void launchReadMeFile() {
        Project p = getOpenProject();
        if (p == null) {
            return;
        }

        // Getting Resource as file object
        URL url = getClass().getResource("/com/softwareco/intellij/plugin/assets/README.txt");
        File f = new File(url.getFile());

        VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(f);
        OpenFileDescriptor descriptor = new OpenFileDescriptor(p, vFile);
        FileEditorManager.getInstance(p).openTextEditor(descriptor, true);
    }

    private Project getCurrentProject() {
        Project project = null;
        Editor[] editors = EditorFactory.getInstance().getAllEditors();
        if (editors != null && editors.length > 0) {
            project = editors[0].getProject();
        }
        return project;
    }

    public static String generateToken() {
        String uuid = UUID.randomUUID().toString();
        return uuid.replace("-", "");
    }

    public void statusBarClickHandler(MouseEvent mouseEvent, String id) {
        String headphoneiconId = SoftwareCoStatusBarIconWidget.ICON_ID + "_headphoneicon";
        String likeiconId = SoftwareCoStatusBarIconWidget.ICON_ID + "_likeicon";
        String unlikeiconId = SoftwareCoStatusBarIconWidget.ICON_ID + "_unlikeicon";
        String preiconId = SoftwareCoStatusBarIconWidget.ICON_ID + "_preicon";
        String stopiconId = SoftwareCoStatusBarIconWidget.ICON_ID + "_stopicon";
        String pauseiconId = SoftwareCoStatusBarIconWidget.ICON_ID + "_pauseicon";
        String playiconId = SoftwareCoStatusBarIconWidget.ICON_ID + "_playicon";
        String nexticonId = SoftwareCoStatusBarIconWidget.ICON_ID + "_nexticon";
        String songtrackId = SoftwareCoStatusBarTextWidget.TEXT_ID + "_songtrack";
        String connectspotifyId = SoftwareCoStatusBarTextWidget.TEXT_ID + "_connectspotify";
        
        if(id.equals(headphoneiconId) || id.equals(connectspotifyId)) {
            MusicControlManager.connectSpotify();
        } else if(id.equals(playiconId)) {
            MusicControlManager.playerCounter = 0;
            PlayerControlManager.playSpotifyDevices();
        } else if(id.equals(pauseiconId)) {
            MusicControlManager.playerCounter = 0;
            PlayerControlManager.pauseSpotifyDevices();
        } else if(id.equals(preiconId)) {
            MusicControlManager.playerCounter = 0;
            PlayerControlManager.previousSpotifyTrack();
        } else if(id.equals(nexticonId)) {
            MusicControlManager.playerCounter = 0;
            PlayerControlManager.nextSpotifyTrack();
        } else if(id.equals(songtrackId)) {
            MusicControlManager.launchPlayer(true, false);
        } else if(id.equals(unlikeiconId)) {
            PlayerControlManager.likeSpotifyTrack(true, MusicControlManager.currentTrackId);
            PlayListCommands.updatePlaylists(3, null);
        } else if(id.equals(likeiconId)) {
            PlayerControlManager.likeSpotifyTrack(false, MusicControlManager.currentTrackId);
            PlayListCommands.updatePlaylists(3, null);
        }
    }
}
