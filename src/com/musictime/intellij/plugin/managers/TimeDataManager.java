package com.musictime.intellij.plugin.managers;

import com.google.gson.JsonArray;
import com.google.gson.reflect.TypeToken;
import com.intellij.openapi.project.Project;
import com.musictime.intellij.plugin.KeystrokeProject;
import com.musictime.intellij.plugin.SoftwareCoMusic;
import com.musictime.intellij.plugin.SoftwareCoUtils;
import com.musictime.intellij.plugin.fs.FileManager;
import com.musictime.intellij.plugin.models.CodeTimeSummary;
import com.musictime.intellij.plugin.models.TimeData;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Time data is saved on disk as a list of time data objects
 */
public class TimeDataManager {

    private static String getTimeDataSummaryFile() {
        String file = FileManager.getSoftwareDir(true);
        if (SoftwareCoUtils.isWindows()) {
            file += "\\projectTimeData.json";
        } else {
            file += "/projectTimeData.json";
        }
        return file;
    }

    public static void clearTimeDataSummary() {
        FileManager.writeData(getTimeDataSummaryFile(), new JsonArray());
    }

    public static void incrementEditorSeconds(long editorSeconds) {
        SoftwareCoUtils.TimesData timesData = SoftwareCoUtils.getTimesData();
        Project activeProject = SoftwareCoUtils.getFirstActiveProject();
        if (activeProject != null) {
            KeystrokeProject project = new KeystrokeProject(activeProject.getName(), activeProject.getBasePath());
            TimeData td = getTodayTimeDataSummary(project);
            if (td != null) {
                // increment the editor seconds
                td.setEditor_seconds(td.getEditor_seconds() + editorSeconds);
                td.setTimestamp_local(timesData.local_now);

                td.setEditor_seconds(Math.max(
                        td.getEditor_seconds(),
                        td.getSession_seconds()));

                saveTimeDataSummaryToDisk(td);
            }
        }
    }

    public static TimeData incrementSessionAndFileSeconds(KeystrokeProject project, long sessionSeconds) {

        TimeData td = getTodayTimeDataSummary(project);
        if (td != null) {
            // increment the session and file seconds
            td.setSession_seconds(td.getSession_seconds() + sessionSeconds);
            td.setFile_seconds(td.getFile_seconds() + 60);

            td.setEditor_seconds(Math.max(
                    td.getEditor_seconds(),
                    td.getSession_seconds()));
            td.setFile_seconds(Math.min(
                    td.getFile_seconds(),
                    td.getSession_seconds()));

            saveTimeDataSummaryToDisk(td);
        }

        return td;
    }

    public static void updateSessionFromSummaryApi(long currentDayMinutes) {
        SoftwareCoUtils.TimesData timesData = SoftwareCoUtils.getTimesData();
        String day = SoftwareCoUtils.getTodayInStandardFormat();

        CodeTimeSummary ctSummary = getCodeTimeSummary();
        // find out if there's a diff
        long diffActiveCodeMinutesToAdd = ctSummary.activeCodeTimeMinutes < currentDayMinutes ?
                currentDayMinutes - ctSummary.activeCodeTimeMinutes : 0;

        Project activeProject = SoftwareCoUtils.getFirstActiveProject();
        TimeData td = null;
        if (activeProject != null) {
            KeystrokeProject project = new KeystrokeProject(
                    activeProject.getName(), activeProject.getBasePath());
            td = getTodayTimeDataSummary(project);
        } else {
            // find the 1st one
            List<TimeData> timeDataList = getTimeDataList();
            if (timeDataList != null && timeDataList.size() > 0) {
                for (TimeData timeData : timeDataList) {
                    if (timeData.getDay().equals(day)) {
                        // use this one
                        td = timeData;
                        break;
                    }
                }
            }
        }

        if (td == null) {
            KeystrokeProject project = new KeystrokeProject(
                    "Unnamed", "Untitled");
            td = new TimeData();
            td.setDay(day);
            td.setTimestamp_local(timesData.local_now);
            td.setTimestamp(timesData.now);
            td.setProject(project);
        }

        long secondsToAdd = diffActiveCodeMinutesToAdd * 60;
        td.setSession_seconds(td.getSession_seconds() + secondsToAdd);
        td.setEditor_seconds(td.getEditor_seconds() + secondsToAdd);

        // save the info to disk
        // make sure editor seconds isn't less
        saveTimeDataSummaryToDisk(td);
    }

    private static List<TimeData> getTimeDataList() {
        JsonArray jsonArr = FileManager.getFileContentAsJsonArray(getTimeDataSummaryFile());
        Type listType = new TypeToken<List<TimeData>>() {}.getType();
        List<TimeData> timeDataList = SoftwareCoMusic.gson.fromJson(jsonArr, listType);
        if (timeDataList == null) {
            timeDataList = new ArrayList<>();
        }
        return timeDataList;
    }

    /**
     * Get the current time data info that is saved on disk. If not found create an empty one.
     * @return
     */
    public static TimeData getTodayTimeDataSummary(KeystrokeProject p) {
        if (p == null || p.getDirectory() == null) {
            return null;
        }
        String day = SoftwareCoUtils.getTodayInStandardFormat();

        List<TimeData> timeDataList = getTimeDataList();

        if (timeDataList != null && timeDataList.size() > 0) {
            for (TimeData timeData : timeDataList) {
                if (timeData.getDay().equals(day) && timeData.getProject().getDirectory().equals(p.getDirectory())) {
                    // return it
                    return timeData;
                }
            }
        }

        SoftwareCoUtils.TimesData timesData = SoftwareCoUtils.getTimesData();

        TimeData td = new TimeData();
        td.setDay(day);
        td.setTimestamp_local(timesData.local_now);
        td.setTimestamp(timesData.now);
        td.setProject(p.cloneProject());

        if (timeDataList == null) {
            timeDataList = new ArrayList<>();
        }

        timeDataList.add(td);
        // write it then return it
        FileManager.writeData(getTimeDataSummaryFile(), timeDataList);
        return td;
    }

    public static CodeTimeSummary getCodeTimeSummary() {
        CodeTimeSummary summary = new CodeTimeSummary();

        String day = SoftwareCoUtils.getTodayInStandardFormat();

        List<TimeData> timeDataList = getTimeDataList();

        if (timeDataList != null && timeDataList.size() > 0) {
            for (TimeData timeData : timeDataList) {
                if (timeData.getDay().equals(day)) {
                    summary.activeCodeTimeMinutes += (timeData.getSession_seconds() / 60);
                    summary.codeTimeMinutes += (timeData.getEditor_seconds() / 60);
                    summary.fileTimeMinutes += (timeData.getFile_seconds() / 60);
                }
            }
        }

        return summary;
    }

    public static void sendOfflineTimeData() {
        FileManager.sendJsonArrayData(getTimeDataSummaryFile(), "/data/time");
    }

    private static void saveTimeDataSummaryToDisk(TimeData timeData) {
        if (timeData == null) {
            return;
        }
        String dir = timeData.getProject().getDirectory();
        String day = timeData.getDay();

        // get the existing list
        List<TimeData> timeDataList = getTimeDataList();

        if (timeDataList != null && timeDataList.size() > 0) {
            for (int i = timeDataList.size() - 1; i >= 0; i--) {
                TimeData td = timeDataList.get(i);
                if (td.getDay().equals(day) && td.getProject().getDirectory().equals(dir)) {
                    timeDataList.remove(i);
                    break;
                }
            }
        }
        timeDataList.add(timeData);

        // write it all
        FileManager.writeData(getTimeDataSummaryFile(), timeDataList);
    }
}
