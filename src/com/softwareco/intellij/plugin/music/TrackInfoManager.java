package com.softwareco.intellij.plugin.music;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.HashMap;

public class TrackInfoManager {

    private static TrackInfo instance = null;

    public static TrackInfo getTrackInfo() {
        if(instance == null) {
            instance = new TrackInfo();
        }

        return instance;
    }

    public static void setTrackInfo(TrackInfo info) {
        instance = info;
    }

    public static void resetTrackInfo() {
        instance.setAlbum(new JsonObject());
        instance.setDisc_number(0);
        instance.setDuration_ms(0L);
        instance.setExplicit(false);
        instance.setId("");
        instance.setIs_local(false);
        instance.setName("");
        instance.setPopularity(0);
        instance.setPreview_url("");
        instance.setTrack_number(0);
        instance.setType("");
        instance.setUri("");
        instance.setArtists(new JsonArray());
        instance.setArtist("");
        instance.setArtist_names(new JsonArray());
        instance.setDuration(0L);
        instance.setPlayerType("");
        instance.setProgress_ms(0L);
        instance.setState("");
        instance.setLoved(false);
        instance.setFeatures(new JsonObject());
        instance.setGenre(new JsonArray());

        instance.setVersion("");
        instance.setPluginId(0);
        instance.setKeystrokes("0");
        instance.setStart(0L);
        instance.setLocal_start(0L);
        instance.setEnd(0L);
        instance.setLocal_end(0L);
        instance.setOs("");
        instance.setTimezone("");
        instance.setSource(new HashMap<>());
    }
}
