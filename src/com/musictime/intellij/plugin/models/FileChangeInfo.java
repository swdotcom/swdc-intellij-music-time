package com.musictime.intellij.plugin.models;

import com.musictime.intellij.plugin.KeystrokeCount;

public class FileChangeInfo {
    public String name = "";
    public String fsPath = "";
    public String projectDir = "";
    public long kpm = 0L;
    public long keystrokes = 0L;
    public long add = 0L;
    public long netkeys = 0L;
    public long paste = 0L;
    public long open = 0L;
    public long close = 0L;
    public long delete = 0L;
    public long length = 0L;
    public long lines = 0L;
    public long linesAdded = 0L;
    public long linesRemoved = 0L;
    public String syntax = "";
    public long fileAgeDays = 0L;
    public long repoFileContributorCount = 0L;
    public long start = 0L;
    public long end = 0L;
    public long local_start = 0L;
    public long local_end = 0L;
    public long update_count = 0L;
    public long duration_seconds = 0L;

    public void aggregate(KeystrokeCount.FileInfo fileInfo) {
        this.add += fileInfo.add;
        this.keystrokes += fileInfo.keystrokes;
        this.netkeys += fileInfo.netkeys;
        this.paste += fileInfo.paste;
        this.open += fileInfo.open;
        this.close += fileInfo.close;
        this.delete += fileInfo.delete;
        this.linesAdded += fileInfo.linesAdded;
        this.linesRemoved += fileInfo.linesRemoved;
        this.duration_seconds += fileInfo.duration_seconds;
        this.update_count += 1;

        // just set
        this.syntax = fileInfo.syntax;
        this.name = fileInfo.name;
        this.fsPath = fileInfo.fsPath;
        this.lines = fileInfo.lines;
        this.length = fileInfo.length;
    }

}
