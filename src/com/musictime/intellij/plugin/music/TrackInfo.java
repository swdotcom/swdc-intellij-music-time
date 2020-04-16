package com.musictime.intellij.plugin.music;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.musictime.intellij.plugin.KeystrokeCount;

import java.util.HashMap;
import java.util.Map;

public class TrackInfo {

    // non-hardcoded attributes
    // Music data
    private JsonObject album = new JsonObject();
    private int disc_number = 0;
    private long duration_ms = 0L;
    private boolean explicit = false;
    private String id = "";
    private boolean is_local = false;
    private String name = "";
    private int popularity = 0;
    private String preview_url = "";
    private int track_number = 0;
    private String type = "";
    private String uri = "";
    private JsonArray artists = new JsonArray();
    private String artist = "";
    private JsonArray artist_names = new JsonArray();
    private long duration = 0L;
    private String playerType = "";
    private long progress_ms = 0L;
    private String state = "";
    private boolean loved = false;
    private JsonObject features = new JsonObject();
    private JsonArray genre = new JsonArray();

    // Coding data
    private int add = 0;
    private int paste = 0;
    private int delete = 0;
    private int netkeys = 0;
    private int linesAdded = 0;
    private int linesRemoved = 0;
    private int open = 0;
    private int close = 0;
    private int keystrokes = 0; // keystroke count
    private int pluginId = 0;
    private String version = "";
    // start and end are in seconds
    private long start = 0L;
    private long local_start = 0L;
    private long end = 0L;
    private long local_end = 0L;
    private int offset = 0;
    private String timezone = "";
    private String os = "";
    private Map<String, JsonObject> source = new HashMap<>();

    public JsonObject getAlbum() {
        return album;
    }

    public void setAlbum(JsonObject album) {
        this.album = album;
    }

    public int getDisc_number() {
        return disc_number;
    }

    public void setDisc_number(int disc_number) {
        this.disc_number = disc_number;
    }

    public long getDuration_ms() {
        return duration_ms;
    }

    public void setDuration_ms(long duration_ms) {
        this.duration_ms = duration_ms;
    }

    public boolean isExplicit() {
        return explicit;
    }

    public void setExplicit(boolean explicit) {
        this.explicit = explicit;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public boolean isIs_local() {
        return is_local;
    }

    public void setIs_local(boolean is_local) {
        this.is_local = is_local;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getPopularity() {
        return popularity;
    }

    public void setPopularity(int popularity) {
        this.popularity = popularity;
    }

    public String getPreview_url() {
        return preview_url;
    }

    public void setPreview_url(String preview_url) {
        this.preview_url = preview_url;
    }

    public int getTrack_number() {
        return track_number;
    }

    public void setTrack_number(int track_number) {
        this.track_number = track_number;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public JsonArray getArtists() {
        return artists;
    }

    public void setArtists(JsonArray artists) {
        this.artists = artists;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public JsonArray getArtist_names() {
        return artist_names;
    }

    public void setArtist_names(JsonArray artist_names) {
        this.artist_names = artist_names;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public String getPlayerType() {
        return playerType;
    }

    public void setPlayerType(String playerType) {
        this.playerType = playerType;
    }

    public long getProgress_ms() {
        return progress_ms;
    }

    public void setProgress_ms(long progress_ms) {
        this.progress_ms = progress_ms;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public boolean isLoved() {
        return loved;
    }

    public void setLoved(boolean loved) {
        this.loved = loved;
    }

    public JsonObject getFeatures() {
        return features;
    }

    public void setFeatures(JsonObject features) {
        this.features = features;
    }

    public JsonArray getGenre() {
        return genre;
    }

    public void setGenre(JsonArray genre) {
        this.genre = genre;
    }

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

    public int getKeystrokes() {
        return keystrokes;
    }

    public void setKeystrokes(int keystrokes) {
        this.keystrokes = keystrokes;
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

    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public Map<String, JsonObject> getSource() {
        return source;
    }

    public void setSource(Map<String, JsonObject> source) {
        this.source = source;
    }
}
