package com.musictime.intellij.plugin;

import java.util.*;
import java.util.logging.Logger;

public class SoftwareCoRepoManager {

    public static final Logger log = Logger.getLogger("SoftwareCoRepoManager");

    private static SoftwareCoRepoManager instance = null;

    public static SoftwareCoRepoManager getInstance() {
        if (instance == null) {
            instance = new SoftwareCoRepoManager();
        }
        return instance;
    }

    private String buildRepoKey(String identifier, String branch, String tag) {
        return identifier + "_" + branch + "_" + tag;
    }

    public int getFileContributorCount(final String projectDir, String fileName) {
        String[] identifierCmd = { "git", "log", "--pretty=%an", fileName };
        String devOutput = SoftwareCoUtils.runCommand(identifierCmd, projectDir);

        // split the output
        String[] devList = devOutput.split("\n");
        Set<String> contributorList = new HashSet<>(Arrays.asList(devList));
        return contributorList.size();
    }
}
