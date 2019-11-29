/**
 * Copyright (c) 2018 by Software.com
 * All rights reserved
 */
package com.softwareco.intellij.plugin;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;

import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

public class SoftwareCoSessionManager {

    private static SoftwareCoSessionManager instance = null;
    public static final Logger log = Logger.getLogger("SoftwareCoSessionManager");
    private static Map<String, String> sessionMap = new HashMap<>();
    private static long lastAppAvailableCheck = 0;

    public static SoftwareCoSessionManager getInstance() {
        if (instance == null) {
            instance = new SoftwareCoSessionManager();
        }
        return instance;
    }

    public static boolean softwareSessionFileExists() {
        // don't auto create the file
        String file = getSoftwareSessionFile(false);
        // check if it exists
        File f = new File(file);
        return f.exists();
    }

    public static boolean jwtExists() {
        String jwt = getItem("jwt");
        return (jwt != null && !jwt.equals("")) ? true : false;
    }

    public static String getCodeTimeDashboardFile() {
        String dashboardFile = getSoftwareDir(true);
        if (SoftwareCoUtils.isWindows()) {
            dashboardFile += "\\CodeTime.txt";
        } else {
            dashboardFile += "/CodeTime.txt";
        }
        return dashboardFile;
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

    public static String getSummaryInfoFile(boolean autoCreate) {
        String file = getSoftwareDir(autoCreate);
        if (SoftwareCoUtils.isWindows()) {
            file += "\\SummaryInfo.txt";
        } else {
            file += "/SummaryInfo.txt";
        }
        return file;
    };

    public static String getSoftwareSessionFile(boolean autoCreate) {
        String file = getSoftwareDir(autoCreate);
        if (SoftwareCoUtils.isWindows()) {
            file += "\\session.json";
        } else {
            file += "/session.json";
        }
        return file;
    }

    private String getSoftwareDataStoreFile() {
        String file = getSoftwareDir(true);
        if (SoftwareCoUtils.isWindows()) {
            file += "\\data.json";
        } else {
            file += "/data.json";
        }
        return file;
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

    public void storePayload(String payload) {
        if (payload == null || payload.length() == 0) {
            return;
        }
        if (SoftwareCoUtils.isWindows()) {
            payload += "\r\n";
        } else {
            payload += "\n";
        }
        String dataStoreFile = getSoftwareDataStoreFile();
        File f = new File(dataStoreFile);
        try {
            log.info("Code Time: Storing kpm metrics: " + payload);
            Writer output;
            output = new BufferedWriter(new FileWriter(f, true));  //clears file every time
            output.append(payload);
            output.close();
        } catch (Exception e) {
            log.warning("Code Time: Error appending to the Software data store file, error: " + e.getMessage());
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

                    JsonArray jsonArray = (JsonArray) SoftwareCo.jsonParser.parse(payloads);

                    // delete the file
                    this.deleteFile(dataStoreFile);

                    JsonArray batch = new JsonArray();
                    // go through the array about 50 at a time
                    for (int i = 0; i < jsonArray.size(); i++) {
                        batch.add(jsonArray.get(i));
                        if (i > 0 && i % 50 == 0) {
                            String payloadData = SoftwareCo.gson.toJson(batch);
                            SoftwareResponse resp =
                                    SoftwareCoUtils.makeApiCall("/data/batch", HttpPost.METHOD_NAME, payloadData);
                            if (!resp.isOk()) {
                                // add these back to the offline file
                                log.info("Code Time: Unable to send batch data: " + resp.getErrorMessage());
                            }
                            batch = new JsonArray();
                        }
                    }
                    if (batch.size() > 0) {
                        String payloadData = SoftwareCo.gson.toJson(batch);
                        SoftwareResponse resp =
                                SoftwareCoUtils.makeApiCall("/data/batch", HttpPost.METHOD_NAME, payloadData);
                        if (!resp.isOk()) {
                            // add these back to the offline file
                            log.info("Code Time: Unable to send batch data: " + resp.getErrorMessage());
                        }
                    }

                } else {
                    log.info("Code Time: No offline data to send");
                }
            } catch (Exception e) {
                log.warning("Code Time: Error trying to read and send offline data, error: " + e.getMessage());
            }
        }

        SoftwareCoOfflineManager.getInstance().clearSessionSummaryData();
        // fetch kpm metrics
        new Thread(() -> {
            try {
                Thread.sleep(10000);
                fetchDailyKpmSessionInfo();
            } catch (Exception e) {
                System.err.println(e);
            }
        }).start();
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
            log.warning("Code Time: Failed to write the key value pair (" + key + ", " + val + ") into the session, error: " + e.getMessage());
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
                    data = SoftwareCo.jsonParser.parse(content).getAsJsonObject();
                }
            } catch (Exception e) {
                log.warning("Code Time: Error trying to read and json parse the session file, error: " + e.getMessage());
            }
        }
        return (data == null) ? new JsonObject() : data;
    }

    public void deleteFile(String file) {
        File f = new File(file);
        // if the file exists, delete it
        if (f.exists()) {
            f.delete();
        }
    }

    private Project getCurrentProject() {
        Project project = null;
        Editor[] editors = EditorFactory.getInstance().getAllEditors();
        if (editors != null && editors.length > 0) {
            project = editors[0].getProject();
        }
        return project;
    }

    public void showLoginPrompt() {
        boolean isOnline = isServerOnline();

        if (isOnline) {

            String msg = "To see your coding data in Code Time, please log in your account.";
            Project project = this.getCurrentProject();

            ApplicationManager.getApplication().invokeLater(new Runnable() {
                public void run() {
                    // ask to download the PM
                    int options = Messages.showDialog(
                            project,
                            msg,
                            "Software", new String[]{"Log in", "Not now"},
                            0, Messages.getInformationIcon());
                    if (options == 0) {
                        launchLogin();
                    }
                }
            });
        }
    }

    public static String generateToken() {
        String uuid = UUID.randomUUID().toString();
        return uuid.replace("-", "");
    }

    public void fetchDailyKpmSessionInfo() {
        SoftwareCoOfflineManager offlineMgr = SoftwareCoOfflineManager.getInstance();
        JsonObject sessionSummary = offlineMgr.getSessionSummaryFileAsJson();
        int currentDayMinutes = sessionSummary != null
                ? sessionSummary.get("currentDayMinutes").getAsInt() : 0;
        if (currentDayMinutes == 0) {
            String sessionsApi = "/sessions/summary";

            // make an async call to get the kpm info
            sessionSummary = SoftwareCoUtils.makeApiCall(sessionsApi, HttpGet.METHOD_NAME, null).getJsonObj();
            if (sessionSummary != null) {

                if (sessionSummary.has("currentDayMinutes")) {
                    currentDayMinutes = sessionSummary.get("currentDayMinutes").getAsInt();
                }
                int currentDayKeystrokes = 0;
                if (sessionSummary.has("currentDayKeystrokes")) {
                    currentDayKeystrokes = sessionSummary.get("currentDayKeystrokes").getAsInt();
                }

                int averageDailyMinutes = 0;
                if (sessionSummary.has("averageDailyMinutes")) {
                    averageDailyMinutes = sessionSummary.get("averageDailyMinutes").getAsInt();
                }

                offlineMgr.setSessionSummaryData(currentDayMinutes, currentDayKeystrokes, averageDailyMinutes);

            } else {
                SoftwareCoUtils.setStatusLineMessage(
                        "Code Time", "Click to see more from Code Time");
            }
        }
        offlineMgr.updateStatusBarWithSummaryData(sessionSummary);
    }

    public void statusBarClickHandler(MouseEvent mouseEvent, String id) {
        if (SoftwareCoSessionManager.isServerOnline()) {
            if(SoftwareCoUtils.pluginName.equals("Code Time")) {
                SoftwareCoUtils.launchCodeTimeMetricsDashboard();
            } else if(SoftwareCoUtils.pluginName.equals("Music Time")) {
                String headphoneiconId = SoftwareCoStatusBarKpmIconWidget.KPM_ICON_ID + "_headphoneicon";
                String likeiconId = SoftwareCoStatusBarKpmIconWidget.KPM_ICON_ID + "_likeicon";
                String preiconId = SoftwareCoStatusBarKpmIconWidget.KPM_ICON_ID + "_preicon";
                String stopiconId = SoftwareCoStatusBarKpmIconWidget.KPM_ICON_ID + "_stopicon";
                String pauseiconId = SoftwareCoStatusBarKpmIconWidget.KPM_ICON_ID + "_pauseicon";
                String playiconId = SoftwareCoStatusBarKpmIconWidget.KPM_ICON_ID + "_playicon";
                String nexticonId = SoftwareCoStatusBarKpmIconWidget.KPM_ICON_ID + "_nexticon";
                String songtrackId = SoftwareCoStatusBarKpmTextWidget.KPM_TEXT_ID + "_songtrack";
                String connectspotifyId = SoftwareCoStatusBarKpmTextWidget.KPM_TEXT_ID + "_connectspotify";

                boolean state = false;
                log.warning("Music Time: Button ID: " + id);
                if(id.equals(headphoneiconId) || id.equals(connectspotifyId)) {
                    SoftwareCoUtils.connectSpotify();
                }
                else if(id.equals(playiconId)) {
                    SoftwareCoUtils.playerCounter = 0;
                    SoftwareCoUtils.playSpotifyDevices();
                } else if(id.equals(pauseiconId)) {
                    SoftwareCoUtils.playerCounter = 0;
                    SoftwareCoUtils.pauseSpotifyDevices();
                } else if(id.equals(preiconId)) {
                    SoftwareCoUtils.playerCounter = 0;
                    SoftwareCoUtils.previousSpotifyTrack();
                } else if(id.equals(nexticonId)) {
                    SoftwareCoUtils.playerCounter = 0;
                    SoftwareCoUtils.nextSpotifyTrack();
                } else if(id.equals(songtrackId)) {
                    SoftwareCoUtils.launchPlayer();
                }
            }
        } else {
            SoftwareCoUtils.showOfflinePrompt(false);
        }
    }

    protected static void lazilyFetchUserStatus(int retryCount) {
        SoftwareCoUtils.UserStatus userStatus = SoftwareCoUtils.getUserStatus();

        if (!userStatus.loggedIn && retryCount > 0) {
            final int newRetryCount = retryCount - 1;
            new Thread(() -> {
                try {
                    Thread.sleep(10000);
                    lazilyFetchUserStatus(newRetryCount);
                }
                catch (Exception e){
                    System.err.println(e);
                }
            }).start();
        }
    }

    public static void launchLogin() {
        String url = SoftwareCoUtils.launch_url;
        String jwt = getItem("jwt");

        url += "/onboarding?token=" + jwt;
        BrowserUtil.browse(url);

        new Thread(() -> {
            try {
                Thread.sleep(10000);
                lazilyFetchUserStatus(12);
            }
            catch (Exception e){
                System.err.println(e);
            }
        }).start();
    }

    public static void launchWebDashboard() {
        String url = SoftwareCoUtils.launch_url + "/login";
        BrowserUtil.browse(url);
    }
}
