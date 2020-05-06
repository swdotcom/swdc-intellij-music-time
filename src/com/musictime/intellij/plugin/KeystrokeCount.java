/**
 * Copyright (c) 2018 by Software.com
 * All rights reserved...
 */
package com.musictime.intellij.plugin;

import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.musictime.intellij.plugin.fs.FileManager;
import com.musictime.intellij.plugin.music.MusicControlManager;
import com.musictime.intellij.plugin.music.MusicControlManager;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class KeystrokeCount {

    // TODO: backend driven, we should look at getting a list of types at some point
    private String type = "Events";

    // non-hardcoded attributes
    // Coding data
    public int add = 0;
    public int paste = 0;
    public int delete = 0;
    public int netkeys = 0;
    public int linesAdded = 0;
    public int linesRemoved = 0;
    public int open = 0;
    public int close = 0;
    private String version;
    private int pluginId;
    private int keystrokes = 0; // keystroke count
    // start and end are in seconds
    private long start;
    private long local_start;
    private long end;
    private long local_end;
    private String os;
    private String timezone;
    private KeystrokeProject project;
    private Map<String, FileInfo> source = new HashMap<>();

    public int getAdd() {
        return add;
    }

    public void setAdd(int add) {
        this.add = add;
    }

    public int getPaste() {
        return paste;
    }

    public void setPaste(int paste) {
        this.paste = paste;
    }

    public int getDelete() {
        return delete;
    }

    public void setDelete(int delete) {
        this.delete = delete;
    }

    public int getNetkeys() {
        return netkeys;
    }

    public void setNetkeys(int netkeys) {
        this.netkeys = netkeys;
    }

    public int getLinesAdded() {
        return linesAdded;
    }

    public void setLinesAdded(int linesAdded) {
        this.linesAdded = linesAdded;
    }

    public int getLinesRemoved() {
        return linesRemoved;
    }

    public void setLinesRemoved(int linesRemoved) {
        this.linesRemoved = linesRemoved;
    }

    public int getOpen() {
        return open;
    }

    public void setOpen(int open) {
        this.open = open;
    }

    public int getClose() {
        return close;
    }

    public void setClose(int close) {
        this.close = close;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public int getPluginId() {
        return pluginId;
    }

    public void setPluginId(int pluginId) {
        this.pluginId = pluginId;
    }

    public long getStart() {
        return start;
    }

    public void setStart(long start) {
        this.start = start;
    }

    public long getLocal_start() {
        return local_start;
    }

    public void setLocal_start(long local_start) {
        this.local_start = local_start;
    }

    public long getEnd() {
        return end;
    }

    public void setEnd(long end) {
        this.end = end;
    }

    public long getLocal_end() {
        return local_end;
    }

    public void setLocal_end(long local_end) {
        this.local_end = local_end;
    }

    public String getOs() {
        return os;
    }

    public void setOs(String os) {
        this.os = os;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public void setSource(Map<String, FileInfo> source) {
        this.source = source;
    }

    public Map<String, FileInfo> getSourceMap() {
        return source;
    }

    public KeystrokeCount() {
        String appVersion = SoftwareCoMusic.getVersion();
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
        kc.end = this.end;
        kc.local_end = this.local_end;
        kc.version = this.version;
        kc.pluginId = this.pluginId;
        kc.project = this.project;
        kc.type = this.type;
        kc.source = this.source;
        kc.timezone = this.timezone;
        kc.os = this.os;

        return kc;
    }

    public void resetData() {
        this.add = 0;
        this.paste = 0;
        this.delete = 0;
        this.netkeys = 0;
        this.linesAdded = 0;
        this.linesRemoved = 0;
        this.open = 0;
        this.close = 0;
        this.keystrokes = 0;
        this.source = new HashMap<>();

        if (this.project != null) {
            this.project = null;
        }

        this.start = 0L;
        this.local_start = 0L;
        this.timezone = "";
    }

    public void updateLatestPayloadLazily() {
        String payload = SoftwareCoMusic.gson.toJson(this);
        FileManager.storeLatestPayloadLazily(payload);
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
        public Integer repoFileContributorCount = 0;
        public long fileAgeDays = 0;

        public FileInfo() { }

        public void reduceOtherFileInfo(JsonObject jsonObj) {
            try {
                Type type = new TypeToken<FileInfo>() { }.getType();
                FileInfo otherFileInfo = SoftwareCoMusic.gson.fromJson(jsonObj, type);
                if (otherFileInfo != null) {
                    this.add += otherFileInfo.add;
                    this.paste += otherFileInfo.paste;
                    this.open += otherFileInfo.open;
                    this.close += otherFileInfo.close;
                    this.delete += otherFileInfo.delete;
                    this.netkeys += otherFileInfo.netkeys;
                    this.linesAdded += otherFileInfo.linesAdded;
                    this.linesRemoved += otherFileInfo.linesRemoved;
                    if (this.end < otherFileInfo.end) {
                        this.end = otherFileInfo.end;
                    }
                    if (this.start > otherFileInfo.start) {
                        this.start = otherFileInfo.start;
                    }
                    if (this.local_end < otherFileInfo.local_end) {
                        this.local_end = otherFileInfo.local_end;
                    }
                    if (this.local_start > otherFileInfo.local_start) {
                        this.local_start = otherFileInfo.local_start;
                    }
                }
            } catch (Exception e) {
                //
            }
        }

        public JsonObject getAsJson() {
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("add", this.add);
            jsonObject.addProperty("start", this.start);
            jsonObject.addProperty("local_start", this.local_start);
            jsonObject.addProperty("end", this.end);
            jsonObject.addProperty("local_end", this.local_end);
            jsonObject.addProperty("paste", this.paste);
            jsonObject.addProperty("open", this.open);
            jsonObject.addProperty("close", this.close);
            jsonObject.addProperty("delete", this.delete);
            jsonObject.addProperty("length", this.length);
            jsonObject.addProperty("netkeys", this.netkeys);
            jsonObject.addProperty("lines", this.lines);
            jsonObject.addProperty("linesAdded", this.linesAdded);
            jsonObject.addProperty("linesRemoved", this.linesRemoved);
            jsonObject.addProperty("syntax", this.syntax);
            jsonObject.addProperty("keystrokes", this.add + this.paste + this.delete + this.linesAdded + this.linesRemoved);
            return jsonObject;
        }

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

        public Integer getRepoFileContributorCount() {
            return repoFileContributorCount;
        }

        public void setRepoFileContributorCount(Integer repoFileContributorCount) {
            this.repoFileContributorCount = repoFileContributorCount;
        }

        public long getFileAgeDays() {
            return fileAgeDays;
        }

        public void setFileAgeDays(long fileAgeDays) {
            this.fileAgeDays = fileAgeDays;
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
        return SoftwareCoMusic.gson.toJson(source);
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
        if (this.getKeystrokes() > 0 || this.hasOpenOrCloseMetrics()) {
            return true;
        }

        return false;
    }

    public void processKeystrokes() {

        boolean hasSpotifyAccess = MusicControlManager.hasSpotifyAccess();
        if (this.hasData() && hasSpotifyAccess) {

            SoftwareCoSessionManager sessionMgr = SoftwareCoSessionManager.getInstance();

            // end the file end times
            this.endUnendedFiles();

            for(String key : this.source.keySet()) {
                FileInfo fileInfo = this.source.get(key);
                this.add += fileInfo.getAdd();
                this.paste += fileInfo.getPaste();
                this.delete += fileInfo.getDelete();
                this.netkeys += fileInfo.getNetkeys();
                this.linesAdded += fileInfo.getLinesAdded();
                this.linesRemoved += fileInfo.getLinesRemoved();
                this.open += fileInfo.getOpen();
                this.close += fileInfo.getClose();

                int fileContributorCount = SoftwareCoRepoManager.getInstance().getFileContributorCount(SoftwareCoMusic.getRootPath(), key);
                fileInfo.setRepoFileContributorCount(fileContributorCount);

                long fileAgeDays = 0;
                BasicFileAttributes attributes = null;
                try {
                    attributes = Files.readAttributes(Paths.get(key), BasicFileAttributes.class);
                } catch (IOException ex) {}
                if(attributes != null) {
                    Instant fileInstant = attributes.creationTime().toInstant();
                    Instant now = Clock.systemUTC().instant();
                    Duration difference = Duration.between(fileInstant, now);
                    fileAgeDays = difference.toDays();
                }

                fileInfo.setFileAgeDays(fileAgeDays);
            }

            SoftwareCoUtils.TimesData timesData = SoftwareCoUtils.getTimesData();
            this.end = timesData.now;
            this.local_end = timesData.local_now;

            final String payload = SoftwareCoMusic.gson.toJson(this);

            // store to send later
            sessionMgr.storePayload(payload);

        }

        this.resetData();
    }

    public int getKeystrokes() {
        return keystrokes;
    }

    public void setKeystrokes(int keystrokes) {
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
