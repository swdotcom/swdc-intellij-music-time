package com.musictime.intellij.plugin.models;

import com.musictime.intellij.plugin.KeystrokeProject;

public class TimeData {

    private long timestamp = 0L;
    private long timestamp_local = 0L;
    private long editor_seconds = 0L;
    private long session_seconds = 0L;
    private long file_seconds = 0L;
    private String day = "";
    private KeystrokeProject project = null;

    public void clone(TimeData td) {
        this.timestamp = td.timestamp;
        this.timestamp_local = td.timestamp_local;
        this.editor_seconds = td.editor_seconds;
        this.session_seconds = td.session_seconds;
        this.file_seconds = td.file_seconds;
        this.day = td.day;
        if (td.getProject() != null) {
            this.project = new KeystrokeProject(td.getProject().getName(), td.getProject().getDirectory());
        } else {
            this.project = new KeystrokeProject("Unnamed", "Untitled");
        }
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public long getTimestamp_local() {
        return timestamp_local;
    }

    public void setTimestamp_local(long timestamp_local) {
        this.timestamp_local = timestamp_local;
    }

    public long getEditor_seconds() {
        return editor_seconds;
    }

    public void setEditor_seconds(long editor_seconds) {
        this.editor_seconds = editor_seconds;
    }

    public long getSession_seconds() {
        return session_seconds;
    }

    public void setSession_seconds(long session_seconds) {
        this.session_seconds = session_seconds;
    }

    public long getFile_seconds() {
        return file_seconds;
    }

    public void setFile_seconds(long file_seconds) {
        this.file_seconds = file_seconds;
    }

    public String getDay() {
        return day;
    }

    public void setDay(String day) {
        this.day = day;
    }

    public KeystrokeProject getProject() {
        return project;
    }

    public void setProject(KeystrokeProject project) {
        this.project = project;
    }
}

