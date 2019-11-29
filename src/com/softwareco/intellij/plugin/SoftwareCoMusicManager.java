/**
 * Copyright (c) 2018 by Software.com
 * All rights reserved
 */
package com.softwareco.intellij.plugin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.http.client.methods.HttpPost;

import java.time.ZonedDateTime;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Logger;

public class SoftwareCoMusicManager {

    public static final Logger log = Logger.getLogger("SoftwareCoMusicManager");

    private static SoftwareCoMusicManager instance = null;

    private JsonObject currentTrack = new JsonObject();

    public static SoftwareCoMusicManager getInstance() {
        if (instance == null) {
            instance = new SoftwareCoMusicManager();
        }
        return instance;
    }

    protected class MusicSendDataTask implements Callable<SoftwareResponse> {

        @Override
        public SoftwareResponse call() throws Exception {
            //
            // get the music track json string
            //
            JsonObject trackInfo = SoftwareCoUtils.getCurrentMusicTrack();

            SoftwareResponse response = null;
            String existingTrackId = (currentTrack.has("id")) ? currentTrack.get("id").getAsString() : null;
            String trackStr = null;

            Integer offset  = ZonedDateTime.now().getOffset().getTotalSeconds();
            long now = System.currentTimeMillis() / 1000;
            long local_start = now + offset;

            if (trackInfo == null || !trackInfo.has("id") || !trackInfo.has("name")) {
                if (existingTrackId != null) {
                    // update the end time on the previous track and send it as well
                    currentTrack.addProperty("end", now);
                    // send the post to end the previous track
                    trackStr = SoftwareCo.gson.toJson(currentTrack);
                    if (trackStr != null) {
                        response = SoftwareCoUtils.makeApiCall("/data/music", HttpPost.METHOD_NAME, trackStr);
                    }

                    // song has ended, clear out the current track
                    currentTrack = new JsonObject();

                    return response;
                }

                // no existing track or current track, return null
                return null;
            }


            String trackId = (trackInfo != null && trackInfo.has("id")) ? trackInfo.get("id").getAsString() : null;

            if (trackId != null && trackId.indexOf("spotify") == -1 && trackId.indexOf("itunes") == -1) {
                // update it to itunes since spotify uses that in the id
                trackId = "itunes:track:" + trackId;
                trackInfo.addProperty("id", trackId);
            }

            boolean isSpotify = (trackId != null && trackId.indexOf("spotify") != -1) ? true : false;
            if (isSpotify) {
                // convert the duration from milliseconds to seconds
                String durationStr = trackInfo.get("duration").getAsString();
                long duration = Long.parseLong(durationStr);
                int durationInSec = Math.round(duration / 1000);
                trackInfo.addProperty("duration", durationInSec);
            }
            String trackState = (trackInfo.get("state").getAsString());

            boolean isPaused = (trackState.toLowerCase().equals("playing")) ? false : true;

            if (trackId != null) {

                if (existingTrackId != null && (!existingTrackId.equals(trackId) || isPaused)) {
                    // update the end time on the previous track and send it as well
                    currentTrack.addProperty("end", now);
                    // send the post to end the previous track
                    trackStr = SoftwareCo.gson.toJson(currentTrack);
                }


                // if the current track doesn't have an "id" then a song has started
                if (!isPaused && (existingTrackId == null  || !existingTrackId.equals(trackId))) {

                    // send the post to send the new track info
                    trackInfo.addProperty("start", now);
                    trackInfo.addProperty("local_start", local_start);

                    trackStr = SoftwareCo.gson.toJson(trackInfo);

                    // update the current track
                    cloneTrackInfoToCurrent(trackInfo);
                }
            }

            if (trackStr != null) {
                response = SoftwareCoUtils.makeApiCall("/data/music", HttpPost.METHOD_NAME, trackStr);
            }

            return response;
        }

        private void cloneTrackInfoToCurrent(JsonObject trackInfo) {
            currentTrack = new JsonObject();
            currentTrack.addProperty("start", trackInfo.get("start").getAsLong());
            long end = (trackInfo.has("end")) ? trackInfo.get("end").getAsLong() : 0;
            currentTrack.addProperty("end", end);
            currentTrack.addProperty("local_start", trackInfo.get("local_start").getAsLong());
            JsonElement durationElement = (trackInfo.has("duration")) ? trackInfo.get("duration") : null;
            double duration = 0;
            if (durationElement != null) {
                String durationStr = durationElement.getAsString();
                duration = Double.parseDouble(durationStr);
                if (duration > 1000) {
                    duration /= 1000;
                }
            }
            currentTrack.addProperty("duration", duration);
            String genre = (trackInfo.has("genre")) ? trackInfo.get("genre").getAsString() : "";
            currentTrack.addProperty("genre", genre);
            String artist = (trackInfo.has("artist")) ? trackInfo.get("artist").getAsString() : "";
            currentTrack.addProperty("artist", artist);
            currentTrack.addProperty("name", trackInfo.get("name").getAsString());
            String state = (trackInfo.has("state")) ? trackInfo.get("state").getAsString() : "";
            currentTrack.addProperty("state", state);
            currentTrack.addProperty("id", trackInfo.get("id").getAsString());
        }
    }


    public void processMusicTrackInfo() {
        MusicSendDataTask sendTask = new MusicSendDataTask();

        Future<SoftwareResponse> response = SoftwareCoUtils.EXECUTOR_SERVICE.submit(sendTask);

        //
        // Handle the Future if it exist
        //
        if ( response != null ) {
            SoftwareResponse httpResponse = null;
            try {
                httpResponse = response.get();

                if (httpResponse != null && !httpResponse.isOk()) {
                    String errorStr = (httpResponse != null && httpResponse.getErrorMessage() != null) ? httpResponse.getErrorMessage() : "";
                    log.info(SoftwareCoUtils.pluginName + ": Unable to get the music track response from the http request, error: " + errorStr);
                }

            } catch (InterruptedException | ExecutionException e) {
                log.info(SoftwareCoUtils.pluginName + ": Unable to get the music track response from the http request, error: " + e.getMessage());
            }
        }

    }
}
