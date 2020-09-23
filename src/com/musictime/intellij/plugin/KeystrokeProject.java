/**
 * Copyright (c) 2018 by Software.com
 * All rights reserved
 */
package com.musictime.intellij.plugin;

import com.musictime.intellij.plugin.models.ResourceInfo;
import com.musictime.intellij.plugin.repo.GitUtil;

public class KeystrokeProject {

    private String name;
    private String directory;
    private String identifier;
    private ResourceInfo resource = new ResourceInfo();

    public KeystrokeProject(String name, String directory) {
        this.name = name;
        this.directory = directory;
        ResourceInfo resourceInfo = GitUtil.getResourceInfo(directory, false);
        if (resourceInfo != null) {
            this.resource.setIdentifier(resourceInfo.getIdentifier());
            this.resource.setTag(resourceInfo.getTag());
            this.resource.setBranch(resourceInfo.getBranch());
            this.resource.setEmail(resourceInfo.getEmail());
            this.identifier = resourceInfo.getIdentifier();
        }
    }

    public KeystrokeProject cloneProject() {
        KeystrokeProject p = new KeystrokeProject(this.name, this.directory);
        p.setIdentifier(p.getIdentifier());
        if (this.resource != null) {
            p.setResource(this.resource.clone());
        }
        return p;
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

    public ResourceInfo getResource() {
        return resource;
    }

    public void setResource(ResourceInfo resource) {
        this.resource = resource;
    }

    @Override
    public String toString() {
        return "KeystrokeProject{" +
                "name='" + name + '\'' +
                ", directory='" + directory + '\'' +
                '}';
    }
}
