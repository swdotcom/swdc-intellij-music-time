package com.softwareco.intellij.plugin;

import com.google.gson.JsonObject;
import java.util.logging.Logger;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class SoftwareCoOfflineManager {

    public static final Logger log = Logger.getLogger("SoftwareCoOfflineManager");

    private static SoftwareCoOfflineManager instance = null;

    private JsonObject currentTrack = new JsonObject();

    public SessionSummaryData sessionSummaryData = new SessionSummaryData();

    public static SoftwareCoOfflineManager getInstance() {
        if (instance == null) {
            instance = new SoftwareCoOfflineManager();
        }
        return instance;
    }

    protected class SessionSummaryData {
        public int currentDayMinutes;
        public int averageDailyMinutes;
        public int averageDailyKeystrokes;
        public int currentDayKeystrokes;
        public int liveshareMinutes;
    }

    public void setSessionSummaryData(int minutes, int keystrokes, int averageDailyMinutes) {
        sessionSummaryData = new SessionSummaryData();
        sessionSummaryData.currentDayKeystrokes = keystrokes;
        sessionSummaryData.currentDayMinutes = minutes;
        sessionSummaryData.averageDailyMinutes = averageDailyMinutes;
        saveSessionSummaryToDisk();
    }

    public void clearSessionSummaryData() {
        sessionSummaryData = new SessionSummaryData();
        saveSessionSummaryToDisk();
    }

    public void setSessionSummaryLiveshareMinutes(int minutes) {
        sessionSummaryData.liveshareMinutes = minutes;
    }

    public void incrementSessionSummaryData(int minutes, int keystrokes) {
        sessionSummaryData.currentDayMinutes += minutes;
        sessionSummaryData.currentDayKeystrokes += keystrokes;
        saveSessionSummaryToDisk();
    }

    public String getSessionSummaryFile() {
        String file = SoftwareCoSessionManager.getSoftwareDir(true);
        if (SoftwareCoUtils.isWindows()) {
            file += "\\sessionSummary.json";
        } else {
            file += "/sessionSummary.json";
        }
        return file;
    }

    public void updateStatusBarWithSummaryData(JsonObject sessionSummary) {

        long currentDayMinutes = 0;
        if (sessionSummary.has("currentDayMinutes")) {
            currentDayMinutes = sessionSummary.get("currentDayMinutes").getAsLong();
        }
        long averageDailyMinutes = 0;
        if (sessionSummary.has("averageDailyMinutes")) {
            averageDailyMinutes = sessionSummary.get("averageDailyMinutes").getAsLong();
        }

        String currentDayTimeStr = SoftwareCoUtils.humanizeMinutes(currentDayMinutes);
        String averageDailyMinutesTimeStr = SoftwareCoUtils.humanizeMinutes(averageDailyMinutes);

        String inFlowIcon = currentDayMinutes > averageDailyMinutes ? "rocket.png" : null;
        String msg = currentDayTimeStr;
        if (averageDailyMinutes > 0) {
            msg += " | " + averageDailyMinutesTimeStr;
        }

        SoftwareCoUtils.setStatusLineMessage(inFlowIcon, msg, "Code time today vs. your daily average. Click to see more from Code Time");

        SoftwareCoUtils.fetchCodeTimeMetricsDashboard(sessionSummary);
    }

    public void saveSessionSummaryToDisk() {
        File f = new File(getSessionSummaryFile());

        final String summaryDataJson = SoftwareCo.gson.toJson(sessionSummaryData);

        Writer writer = null;
        try {
            writer = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(f), StandardCharsets.UTF_8));
            writer.write(summaryDataJson);
        } catch (IOException ex) {
            // Report
        } finally {
            try {writer.close();} catch (Exception ex) {/*ignore*/}
        }
    }

    public JsonObject getSessionSummaryFileAsJson() {
        JsonObject data = null;

        String sessionSummaryFile = getSessionSummaryFile();
        File f = new File(sessionSummaryFile);
        if (f.exists()) {
            try {
                byte[] encoded = Files.readAllBytes(Paths.get(sessionSummaryFile));
                String content = new String(encoded, Charset.forName("UTF-8"));
                if (content != null) {
                    // json parse it
                    data = SoftwareCo.jsonParser.parse(content).getAsJsonObject();
                }
            } catch (Exception e) {
                log.warning("Code Time: Error trying to read and json parse the session file, error: " + e.getMessage());
            }
        }
        return data;
    }

    public String getSessionSummaryInfoFileContent() {
        String content = null;

        String sessionSummaryFile = SoftwareCoSessionManager.getSummaryInfoFile(true);
        File f = new File(sessionSummaryFile);
        if (f.exists()) {
            try {
                byte[] encoded = Files.readAllBytes(Paths.get(sessionSummaryFile));
                content = new String(encoded, Charset.forName("UTF-8"));
            } catch (Exception e) {
                log.warning("Code Time: Error trying to read and json parse the session file, error: " + e.getMessage());
            }
        }
        return content;
    }

    public void saveFileContent(String content, String file) {
        File f = new File(file);

        Writer writer = null;
        try {
            writer = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(f), Charset.forName("UTF-8")));
            writer.write(content);
        } catch (IOException ex) {
            // Report
        } finally {
            try {writer.close();} catch (Exception ex) {/*ignore*/}
        }
    }
}
