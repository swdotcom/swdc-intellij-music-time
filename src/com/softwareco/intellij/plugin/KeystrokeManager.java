/**
 * Copyright (c) 2018 by Software.com
 * All rights reserved
 */
package com.softwareco.intellij.plugin;

import com.intellij.openapi.project.Project;

public class KeystrokeManager {

    private static KeystrokeManager instance = null;

    KeystrokeCountWrapper wrapper = new KeystrokeCountWrapper();

    /**
     * Protected constructor to defeat instantiation
     */
    protected KeystrokeManager() {
        //
    }

    public static KeystrokeManager getInstance() {
        if (instance == null) {
            instance = new KeystrokeManager();
        }
        return instance;
    }

    public void addKeystrokeWrapperIfNoneExists(Project project) {
        if (wrapper == null || wrapper.getProjectName() == null) {
            wrapper = new KeystrokeCountWrapper();
            wrapper.setProjectName(project.getName());
        }
    }

    public KeystrokeCount getKeystrokeCount() {
        if (wrapper != null) {
            return wrapper.getKeystrokeCount();
        }
        return null;
    }

    public void setKeystrokeCount(String projectName, KeystrokeCount keystrokeCount) {
        if (wrapper == null) {
            wrapper = new KeystrokeCountWrapper();
        }
        wrapper.setKeystrokeCount(keystrokeCount);
        wrapper.setProjectName(projectName);
    }

    public KeystrokeCountWrapper getKeystrokeWrapper() {
        return wrapper;
    }

    public class KeystrokeCountWrapper {
        // KeystrokeCount cache metadata
        protected KeystrokeCount keystrokeCount;
        protected String projectName = "";
        protected String currentFileName = "";
        protected int currentTextLength = 0;

        public KeystrokeCount getKeystrokeCount() {
            return keystrokeCount;
        }

        public void setKeystrokeCount(KeystrokeCount keystrokeCount) {
            this.keystrokeCount = keystrokeCount;
        }

        public String getProjectName() {
            return projectName;
        }

        public void setProjectName(String projectName) {
            this.projectName = projectName;
        }

        public void setCurrentFileName(String currentFileName) {
            this.currentFileName = currentFileName;
        }

        public int getCurrentTextLength() {
            return currentTextLength;
        }

        public void setCurrentTextLength(int currentTextLength) {
            this.currentTextLength = currentTextLength;
        }
    }

}
