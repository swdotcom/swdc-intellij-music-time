/**
 * Copyright (c) 2018 by Software.com
 * All rights reserved
 */
package com.softwareco.intellij.plugin;

import com.google.gson.JsonObject;

public class KeystrokeProject {

    private String name;
    private String directory;
    private String identifier;
    private JsonObject resource = new JsonObject();

    public KeystrokeProject(String name, String directory) {
        this.name = name;
        this.directory = directory;
    }

    public void resetData() {
        // intentional for now
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDirectory() {
        return directory;
    }

    public void setDirectory(String directory) {
        this.directory = directory;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public String getIdentifier() { return identifier; }

    public void updateResource(JsonObject resource) {
        this.resource = resource;
    }

    public boolean hasResource() {
        return this.resource != null && this.resource.has("identifier");
    }

    public String getResource() {
        return SoftwareCo.gson.toJson(resource);
    }

    @Override
    public String toString() {
        return "KeystrokeProject{" +
                "name='" + name + '\'' +
                ", directory='" + directory + '\'' +
                '}';
    }
}
