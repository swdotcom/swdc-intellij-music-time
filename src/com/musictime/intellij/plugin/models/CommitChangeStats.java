package com.musictime.intellij.plugin.models;

public class CommitChangeStats {
    private int insertions = 0;
    private int deletions = 0;
    private int fileCount = 0;
    private int commitCount = 0;
    private boolean committed = false;

    public CommitChangeStats(boolean committed) {
        this.committed = committed;
    }

    public boolean isCommitted() {
        return committed;
    }

    public int getInsertions() {
        return insertions;
    }

    public void setInsertions(int insertions) {
        this.insertions = insertions;
    }

    public int getDeletions() {
        return deletions;
    }

    public void setDeletions(int deletions) {
        this.deletions = deletions;
    }

    public int getFileCount() {
        return fileCount;
    }

    public void setFileCount(int fileCount) {
        this.fileCount = fileCount;
    }

    public int getCommitCount() {
        return commitCount;
    }

    public void setCommitCount(int commitCount) {
        this.commitCount = commitCount;
    }
}
