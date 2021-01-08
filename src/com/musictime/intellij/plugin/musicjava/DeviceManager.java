package com.musictime.intellij.plugin.musicjava;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.musictime.intellij.plugin.models.DeviceInfo;
import org.apache.commons.lang.StringUtils;
import swdc.java.ops.http.ClientResponse;
import swdc.java.ops.manager.FileUtilManager;

import java.util.ArrayList;
import java.util.List;

public class DeviceManager {

    private static List<DeviceInfo> devices = new ArrayList<>();

    public static void clearDevices() {
        devices = new ArrayList<>();
    }

    public static List<DeviceInfo> refreshDevices() {
        populateDevices();
        return devices;
    }

    public static List<DeviceInfo> getDevices() {
        if (devices == null || devices.size() == 0) {
            populateDevices();
        }
        return devices;
    }

    public static DeviceInfo getActiveDevice() {
        DeviceInfo info = getBestDeviceOption();
        if (info == null) {
            // nothing, fetch devices
            populateDevices();
        }
        return getBestDeviceOption();
    }

    public static boolean hasDesktopOrWebDevice() {
        return (hasDesktopDevice() || hasWebDevice()) ? true : false;
    }

    public static DeviceInfo getBestDeviceOption() {
        if (devices == null || devices.size() == 0) {
            return null;
        }

        for (DeviceInfo info : devices) {
            if (info.is_active) {
                return info;
            }
        }

        // none are active, return the best option
        // desktop, then web
        DeviceInfo desktop = null;
        DeviceInfo webplayer = null;

        for (DeviceInfo info : devices) {
            if (info.playerType.equals("desktop")) {
                // DESKTOP
                desktop = info;
            } else if (info.playerType.equals("webplayer")) {
                webplayer = info;
            }
        }

        if (desktop != null) {
            return desktop;
        } else if (webplayer != null) {
            return webplayer;
        }
        return null;
    }

    public static boolean hasActiveWebOrDesktopDevice() {
        DeviceInfo desktop = null;
        DeviceInfo webplayer = null;

        for (DeviceInfo info : devices) {
            if (info.playerType.equals("desktop")) {
                // DESKTOP
                desktop = info;
            } else if (info.playerType.equals("webplayer")) {
                webplayer = info;
            }
        }

        if ((desktop != null && desktop.is_active) || (webplayer != null && webplayer.is_active)) {
            return true;
        }
        return false;
    }

    public static boolean hasWebDevice() {
        for (DeviceInfo info : devices) {
            if (info.playerType.equals("webplayer")) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasDesktopDevice() {
        for (DeviceInfo info : devices) {
            if (info.playerType.equals("desktop")) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasActiveDesktopDevice() {
        for (DeviceInfo info : devices) {
            if (info.is_active && info.playerType.equals("desktop")) {
                return true;
            }
        }
        return false;
    }

    private static void populateDevices() {
        devices = new ArrayList<>();

        String spotifyAccessToken = FileUtilManager.getItem("spotify_access_token");
        if (StringUtils.isBlank(spotifyAccessToken)) {
            return;
        }

        ClientResponse resp = Apis.getSpotifyDevices();
        if (resp != null && resp.isOk()) {
            JsonObject obj = resp.getJsonObj();
            if (obj != null && obj.has("devices")) {

                /**
                 *  obj has the following:
                 *  { devices: [ {id, is_active, is_private_session, is_restricted, name, type, volume_percent},...]}
                 * "id" -> {JsonPrimitive@23883} ""55a2706bb28411173ac21e4c794704e88e818108""
                 * "is_active" -> {JsonPrimitive@23885} "true"
                 * "is_private_session" -> {JsonPrimitive@23887} "false"
                 * "is_restricted" -> {JsonPrimitive@23889} "false"
                 * "name" -> {JsonPrimitive@23891} ""Xavier’s MacBook Pro""
                 * "type" -> {JsonPrimitive@23893} ""Computer""
                 * "volume_percent" -> {JsonPrimitive@23895} "100"
                 */
                for(JsonElement array : obj.get("devices").getAsJsonArray()) {
                    JsonObject jsonObj = array.getAsJsonObject();
                    DeviceInfo deviceInfo = buildDeviceInfo(jsonObj);
                    devices.add(deviceInfo);
                }
            }
        }
    }

    private static DeviceInfo buildDeviceInfo(JsonObject obj) {
        DeviceInfo info = new DeviceInfo();
        info.id = obj.has("id") ? obj.get("id").getAsString() : "";
        info.is_active = obj.has("is_active") ? obj.get("is_active").getAsBoolean() : false;
        info.is_private_session = obj.has("is_private_session") ? obj.get("is_private_session").getAsBoolean() : false;
        info.is_restricted = obj.has("is_restricted") ? obj.get("is_restricted").getAsBoolean() : false;
        info.name = obj.has("name") ? obj.get("name").getAsString() : "";
        info.type = obj.has("type") ? obj.get("type").getAsString() : "";
        info.volume_percent = obj.has("volume_percent") ? obj.get("volume_percent").getAsInt() : 0;
        if (info.type.toLowerCase().equals("computer") && info.name.toLowerCase().indexOf("web player") == -1) {
            // DESKTOP
            info.playerType = "desktop";
            info.playerDescription = "Desktop Player";
        } else if (info.type.toLowerCase().equals("computer") && info.name.toLowerCase().indexOf("web player") != -1) {
            info.playerType = "webplayer";
            info.playerDescription = "Web Player";
        }
        return info;
    }
}
