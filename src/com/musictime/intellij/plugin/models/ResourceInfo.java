package com.musictime.intellij.plugin.models;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

public class ResourceInfo {
    private String identifier = "";
    private String branch = "";
    private String tag = "";
    private String email = "";
    private List<TeamMember> members = new ArrayList<>();

    public ResourceInfo clone() {
        ResourceInfo info = new ResourceInfo();
        info.setIdentifier(this.identifier);
        info.setBranch(this.branch);
        info.setTag(this.tag);
        info.setEmail(this.email);
        info.setMembers(this.members);
        return info;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public List<TeamMember> getMembers() {
        return members;
    }

    public void setMembers(List<TeamMember> members) {
        this.members = members;
    }

    public JsonArray getJsonMembers() {
        JsonArray jsonMembers = new JsonArray();
        for (TeamMember member : members) {
            JsonObject json = new JsonObject();
            json.addProperty("email", member.getEmail());
            json.addProperty("name", member.getName());
            jsonMembers.add(json);
        }
        return jsonMembers;
    }
}
