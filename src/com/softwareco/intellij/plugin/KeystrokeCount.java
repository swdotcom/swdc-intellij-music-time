/**
 * Copyright (c) 2018 by Software.com
 * All rights reserved...
 */
package com.softwareco.intellij.plugin;

import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.extensions.PluginId;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class KeystrokeCount {

    private static final Logger LOG = Logger.getLogger("KeystrokeCount");
    // TODO: backend driven, we should look at getting a list of types at some point
    private String type = "Events";

    // non-hardcoded attributes
    private Map<String, FileInfo> source = new HashMap<>();
    private String version;
    private int pluginId;
    private String keystrokes = "0"; // keystroke count
    // start and end are in seconds
    private long start;
    private long local_start;
    private String os;
    private String timezone;
    private KeystrokeProject project;

    public KeystrokeCount() {
        String appVersion = SoftwareCo.getVersion();
        if (appVersion != null) {
            this.version = appVersion;
        } else {
            this.version = SoftwareCoUtils.VERSION;
        }
        this.pluginId = SoftwareCoUtils.pluginId;
        this.os = SoftwareCoUtils.getOs();
    }

    public KeystrokeCount clone() {
        KeystrokeCount kc = new KeystrokeCount();
        kc.keystrokes = this.keystrokes;
        kc.start = this.start;
        kc.local_start = this.local_start;
        kc.version = this.version;
        kc.pluginId = this.pluginId;
        kc.project = this.project;
        kc.type = this.type;
        kc.source = this.source;
        kc.timezone = this.timezone;

        return kc;
    }

    public void resetData() {
        this.keystrokes = "0";
        this.source = new HashMap<>();
        if (this.project != null) {
            this.project.resetData();
        }

        this.start = 0L;
        this.local_start = 0L;
        this.timezone = "";
    }

    private boolean hasOpenOrCloseMetrics() {
        Map<String, FileInfo> fileInfoDataSet = this.source;
        for ( FileInfo fileInfoData : fileInfoDataSet.values() ) {
            int openVal = fileInfoData.getOpen();
            if (openVal > 0) {
                return true;
            }
            int closeVal = fileInfoData.getClose();
            if (closeVal > 0) {
                return true;
            }
        }
        return false;
    }

    public static class FileInfo {
        public Integer add = 0;
        public Integer paste = 0;
        public Integer open = 0;
        public Integer close = 0;
        public Integer delete = 0;
        public Integer length = 0;
        public Integer netkeys = 0;
        public Integer lines = 0;
        public Integer linesAdded = 0;
        public Integer linesRemoved = 0;
        public String syntax = "";
        public long start = 0;
        public long end = 0;
        public long local_start = 0;
        public long local_end = 0;

        public FileInfo() { }

        public Integer getAdd() {
            return add;
        }

        public void setAdd(Integer add) {
            this.add = add;
        }

        public Integer getPaste() {
            return paste;
        }

        public void setPaste(Integer paste) {
            this.paste = paste;
        }

        public Integer getOpen() {
            return open;
        }

        public void setOpen(Integer open) {
            this.open = open;
        }

        public Integer getClose() {
            return close;
        }

        public void setClose(Integer close) {
            this.close = close;
        }

        public Integer getDelete() {
            return delete;
        }

        public void setDelete(Integer delete) {
            this.delete = delete;
        }

        public Integer getLength() {
            return length;
        }

        public void setLength(Integer length) {
            this.length = length;
        }

        public Integer getNetkeys() {
            return netkeys;
        }

        public void setNetkeys(Integer netkeys) {
            this.netkeys = netkeys;
        }

        public Integer getLines() {
            return lines;
        }

        public void setLines(Integer lines) {
            this.lines = lines;
        }

        public Integer getLinesAdded() {
            return linesAdded;
        }

        public void setLinesAdded(Integer linesAdded) {
            this.linesAdded = linesAdded;
        }

        public Integer getLinesRemoved() {
            return linesRemoved;
        }

        public void setLinesRemoved(Integer linesRemoved) {
            this.linesRemoved = linesRemoved;
        }

        public String getSyntax() {
            return syntax;
        }

        public void setSyntax(String syntax) {
            this.syntax = syntax;
        }

        public long getStart() {
            return start;
        }

        public void setStart(long start) {
            this.start = start;
        }

        public long getEnd() {
            return end;
        }

        public void setEnd(long end) {
            this.end = end;
        }

        public long getLocal_start() {
            return local_start;
        }

        public void setLocal_start(long local_start) {
            this.local_start = local_start;
        }

        public long getLocal_end() {
            return local_end;
        }

        public void setLocal_end(long local_end) {
            this.local_end = local_end;
        }
    }

    public FileInfo getSourceByFileName(String fileName) {
        if (source.get(fileName) != null) {
            return source.get(fileName);
        }

        SoftwareCoUtils.TimesData timesData = SoftwareCoUtils.getTimesData();

        if (this.start == 0) {
            this.start = timesData.now;
            this.local_start = timesData.local_now;
            this.timezone = timesData.timezone;

            // start the keystroke processor 1 minute timer
            new Thread(() -> {
                try {
                    Thread.sleep(1000 * 60);
                    this.processKeystrokes();
                } catch (Exception e) {
                    System.err.println(e);
                }
            }).start();
        }

        // create one and return the one just created
        FileInfo fileInfoData = new FileInfo();
        fileInfoData.setStart(timesData.now);
        fileInfoData.setLocal_start(timesData.local_now);
        source.put(fileName, fileInfoData);

        return fileInfoData;
    }

    public String getSource() {
        return SoftwareCo.gson.toJson(source);
    }

    public void endPreviousModifiedFiles(String currentFileName) {
        SoftwareCoUtils.TimesData timesData = SoftwareCoUtils.getTimesData();
        Map<String, FileInfo> fileInfoDataSet = this.source;

        for (FileInfo fileInfoData : fileInfoDataSet.values()) {
            if (fileInfoData.getEnd() == 0) {
                fileInfoData.setEnd(timesData.now);
                fileInfoData.setLocal_end(timesData.local_now);
            }
        }
        if(fileInfoDataSet.get(currentFileName) != null) {
            FileInfo fileInfoData = fileInfoDataSet.get(currentFileName);
            fileInfoData.setEnd(0);
            fileInfoData.setLocal_end(0);
        }
    }

    public void endUnendedFiles() {
        SoftwareCoUtils.TimesData timesData = SoftwareCoUtils.getTimesData();
        Map<String, FileInfo> fileInfoDataSet = this.source;
        for ( FileInfo fileInfoData : fileInfoDataSet.values() ) {
            // end the ones that don't have an end time
            if (fileInfoData.getEnd() == 0) {
                // set the end time for this file
                fileInfoData.setEnd(timesData.now);
                fileInfoData.setLocal_end(timesData.local_now);
            }
        }
    }

    public boolean hasData() {
        if (Integer.parseInt(this.getKeystrokes()) > 0 || this.hasOpenOrCloseMetrics()) {
            return true;
        }

        return false;
    }

    public void processKeystrokes() {

        if (this.hasData()) {

            SoftwareCoSessionManager sessionMgr = SoftwareCoSessionManager.getInstance();

            // end the file end times
            this.endUnendedFiles();

            SoftwareCoUtils.TimesData timesData = SoftwareCoUtils.getTimesData();

            final String payload = SoftwareCo.gson.toJson(this);

            SoftwareCoOfflineManager.getInstance().incrementSessionSummaryData(1, Integer.parseInt(keystrokes));

            // store to send later
            sessionMgr.storePayload(payload);

            new Thread(() -> {
                try {
                    Thread.sleep(10000);
                    sessionMgr.fetchDailyKpmSessionInfo();
                } catch (Exception e) {
                    System.err.println(e);
                }
            }).start();

        }

        this.resetData();

    }

    public String getKeystrokes() {
        return keystrokes;
    }

    public void setKeystrokes(String keystrokes) {
        this.keystrokes = keystrokes;
    }

    public KeystrokeProject getProject() {
        return project;
    }

    public void setProject(KeystrokeProject project) {
        this.project = project;
    }

    @Override
    public String toString() {
        return "KeystrokeCount{" +
                "type='" + type + '\'' +
                ", pluginId=" + pluginId +
                ", source=" + source +
                ", keystrokes='" + keystrokes + '\'' +
                ", start=" + start +
                ", local_start=" + local_start +
                ", timezone='" + timezone + '\'' +
                ", project=" + project +
                '}';
    }
}
