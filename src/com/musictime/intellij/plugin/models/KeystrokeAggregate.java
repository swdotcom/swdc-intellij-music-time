package com.musictime.intellij.plugin.models;

import com.musictime.intellij.plugin.KeystrokeCount;

public class KeystrokeAggregate {
    public int add = 0;
    public int close = 0;
    public int delete = 0;
    public int linesAdded = 0;
    public int linesRemoved = 0;
    public int open = 0;
    public int paste = 0;
    public int keystrokes = 0;
    public String directory = "";

    public void aggregate(KeystrokeCount.FileInfo fileInfo) {
        this.add += fileInfo.add;
        this.keystrokes += fileInfo.keystrokes;
        this.paste += fileInfo.paste;
        this.open += fileInfo.open;
        this.close += fileInfo.close;
        this.delete += fileInfo.delete;
        this.linesAdded += fileInfo.linesAdded;
        this.linesRemoved += fileInfo.linesRemoved;
    }
}
