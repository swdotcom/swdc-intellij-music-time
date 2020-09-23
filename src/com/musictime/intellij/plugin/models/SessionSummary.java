package com.musictime.intellij.plugin.models;

public class SessionSummary {
    private long currentDayMinutes = 0L;
    private long currentDayKeystrokes = 0L;
    private long currentDayKpm = 0L;
    private long currentDayLinesAdded = 0L;
    private long currentDayLinesRemoved = 0L;
    private long averageDailyMinutes = 0L;
    private long averageDailyKeystrokes = 0L;
    private long averageDailyKpm = 0L;
    private long averageLinesAdded = 0L;
    private long averageLinesRemoved = 0L;
    private int timePercent = 0;
    private int volumePercent = 0;
    private int velocityPercent = 0;
    private long liveshareMinutes = 0L;
    private long latestPayloadTimestampEndUtc = 0L;
    private long latestPayloadTimestamp = 0L;
    private boolean lastUpdatedToday = false;
    private int currentSessionGoalPercent = 0;
    private boolean inFlow = false;
    private int dailyMinutesGoal = 0;
    private long globalAverageSeconds = 0L;
    private long globalAverageDailyMinutes = 0L;
    private long globalAverageDailyKeystrokes = 0L;
    private long globalAverageLinesAdded = 0L;
    private long globalAverageLinesRemoved = 0L;

    public void clone(SessionSummary in) {
        this.currentDayMinutes = in.getCurrentDayMinutes();
        this.currentDayKeystrokes = in.getCurrentDayKeystrokes();
        this.currentDayKpm = in.getCurrentDayKpm();
        this.currentDayLinesAdded = in.getCurrentDayLinesAdded();
        this.currentDayLinesRemoved = in.getCurrentDayLinesRemoved();
        this.cloneNonCurrentMetrics(in);
    }

    public void cloneNonCurrentMetrics(SessionSummary in) {
        this.averageDailyMinutes = in.getAverageDailyMinutes();
        this.averageDailyKeystrokes = in.getAverageDailyKeystrokes();
        this.averageDailyKpm = in.getAverageDailyKpm();
        this.averageLinesAdded = in.getAverageLinesAdded();
        this.averageLinesRemoved = in.getAverageLinesAdded();
        this.timePercent = in.getTimePercent();
        this.volumePercent = in.getVolumePercent();
        this.velocityPercent = in.getVelocityPercent();
        this.liveshareMinutes = in.getLiveshareMinutes();
        this.latestPayloadTimestampEndUtc = in.getLatestPayloadTimestampEndUtc();
        this.latestPayloadTimestamp = in.getLatestPayloadTimestamp();
        this.lastUpdatedToday = in.isLastUpdatedToday();
        this.inFlow = in.isInFlow();
        this.dailyMinutesGoal = in.getDailyMinutesGoal();
        this.globalAverageSeconds = in.getGlobalAverageSeconds();
        this.globalAverageDailyMinutes = in.getGlobalAverageDailyMinutes();
        this.globalAverageDailyKeystrokes = in.getGlobalAverageDailyKeystrokes();
        this.globalAverageLinesAdded = in.getGlobalAverageLinesAdded();
        this.globalAverageLinesRemoved = in.getGlobalAverageLinesRemoved();
    }

    public long getCurrentDayMinutes() {
        return currentDayMinutes;
    }

    public void setCurrentDayMinutes(long currentDayMinutes) {
        this.currentDayMinutes = currentDayMinutes;
    }

    public long getCurrentDayKeystrokes() {
        return currentDayKeystrokes;
    }

    public void setCurrentDayKeystrokes(long currentDayKeystrokes) {
        this.currentDayKeystrokes = currentDayKeystrokes;
    }

    public long getCurrentDayKpm() {
        return currentDayKpm;
    }

    public void setCurrentDayKpm(long currentDayKpm) {
        this.currentDayKpm = currentDayKpm;
    }

    public long getCurrentDayLinesAdded() {
        return currentDayLinesAdded;
    }

    public void setCurrentDayLinesAdded(long currentDayLinesAdded) {
        this.currentDayLinesAdded = currentDayLinesAdded;
    }

    public long getCurrentDayLinesRemoved() {
        return currentDayLinesRemoved;
    }

    public void setCurrentDayLinesRemoved(long currentDayLinesRemoved) {
        this.currentDayLinesRemoved = currentDayLinesRemoved;
    }

    public long getAverageDailyMinutes() {
        return averageDailyMinutes;
    }

    public void setAverageDailyMinutes(long averageDailyMinutes) {
        this.averageDailyMinutes = averageDailyMinutes;
    }

    public long getAverageDailyKeystrokes() {
        return averageDailyKeystrokes;
    }

    public void setAverageDailyKeystrokes(long averageDailyKeystrokes) {
        this.averageDailyKeystrokes = averageDailyKeystrokes;
    }

    public long getAverageDailyKpm() {
        return averageDailyKpm;
    }

    public void setAverageDailyKpm(long averageDailyKpm) {
        this.averageDailyKpm = averageDailyKpm;
    }

    public long getAverageLinesAdded() {
        return averageLinesAdded;
    }

    public void setAverageLinesAdded(long averageLinesAdded) {
        this.averageLinesAdded = averageLinesAdded;
    }

    public long getAverageLinesRemoved() {
        return averageLinesRemoved;
    }

    public void setAverageLinesRemoved(long averageLinesRemoved) {
        this.averageLinesRemoved = averageLinesRemoved;
    }

    public int getTimePercent() {
        return timePercent;
    }

    public void setTimePercent(int timePercent) {
        this.timePercent = timePercent;
    }

    public int getVolumePercent() {
        return volumePercent;
    }

    public void setVolumePercent(int volumePercent) {
        this.volumePercent = volumePercent;
    }

    public int getVelocityPercent() {
        return velocityPercent;
    }

    public void setVelocityPercent(int velocityPercent) {
        this.velocityPercent = velocityPercent;
    }

    public long getLiveshareMinutes() {
        return liveshareMinutes;
    }

    public void setLiveshareMinutes(long liveshareMinutes) {
        this.liveshareMinutes = liveshareMinutes;
    }

    public long getLatestPayloadTimestampEndUtc() {
        return latestPayloadTimestampEndUtc;
    }

    public void setLatestPayloadTimestampEndUtc(long latestPayloadTimestampEndUtc) {
        this.latestPayloadTimestampEndUtc = latestPayloadTimestampEndUtc;
    }

    public long getLatestPayloadTimestamp() {
        return latestPayloadTimestamp;
    }

    public void setLatestPayloadTimestamp(long latestPayloadTimestamp) {
        this.latestPayloadTimestamp = latestPayloadTimestamp;
    }

    public boolean isLastUpdatedToday() {
        return lastUpdatedToday;
    }

    public void setLastUpdatedToday(boolean lastUpdatedToday) {
        this.lastUpdatedToday = lastUpdatedToday;
    }

    public int getCurrentSessionGoalPercent() {
        return currentSessionGoalPercent;
    }

    public void setCurrentSessionGoalPercent(int currentSessionGoalPercent) {
        this.currentSessionGoalPercent = currentSessionGoalPercent;
    }

    public boolean isInFlow() {
        return inFlow;
    }

    public void setInFlow(boolean inFlow) {
        this.inFlow = inFlow;
    }

    public int getDailyMinutesGoal() {
        return dailyMinutesGoal;
    }

    public void setDailyMinutesGoal(int dailyMinutesGoal) {
        this.dailyMinutesGoal = dailyMinutesGoal;
    }

    public long getGlobalAverageSeconds() {
        return globalAverageSeconds;
    }

    public void setGlobalAverageSeconds(long globalAverageSeconds) {
        this.globalAverageSeconds = globalAverageSeconds;
    }

    public long getGlobalAverageDailyMinutes() {
        return globalAverageDailyMinutes;
    }

    public void setGlobalAverageDailyMinutes(long globalAverageDailyMinutes) {
        this.globalAverageDailyMinutes = globalAverageDailyMinutes;
    }

    public long getGlobalAverageDailyKeystrokes() {
        return globalAverageDailyKeystrokes;
    }

    public void setGlobalAverageDailyKeystrokes(long globalAverageDailyKeystrokes) {
        this.globalAverageDailyKeystrokes = globalAverageDailyKeystrokes;
    }

    public long getGlobalAverageLinesAdded() {
        return globalAverageLinesAdded;
    }

    public void setGlobalAverageLinesAdded(long globalAverageLinesAdded) {
        this.globalAverageLinesAdded = globalAverageLinesAdded;
    }

    public long getGlobalAverageLinesRemoved() {
        return globalAverageLinesRemoved;
    }

    public void setGlobalAverageLinesRemoved(long globalAverageLinesRemoved) {
        this.globalAverageLinesRemoved = globalAverageLinesRemoved;
    }
}
