package com.musictime.intellij.plugin.models;

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
public class DeviceInfo {
    public String id = "";
    public boolean is_active = false;
    public boolean is_private_session = false;
    public boolean is_restricted = false;
    public String name = "";
    public String type = "";
    public int volume_percent = 0;
    public String playerType = "";
    public String playerDescription = "";
}
