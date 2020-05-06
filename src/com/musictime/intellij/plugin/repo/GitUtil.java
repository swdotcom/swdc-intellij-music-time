package com.musictime.intellij.plugin.repo;


import com.musictime.intellij.plugin.SoftwareCoUtils;
import com.musictime.intellij.plugin.models.CommitChangeStats;
import com.musictime.intellij.plugin.models.CommitInfo;
import com.musictime.intellij.plugin.models.ResourceInfo;
import com.musictime.intellij.plugin.models.TeamMember;
import org.apache.commons.lang.StringUtils;

import java.util.*;

public class GitUtil {

    public static CommitChangeStats accumulateStatChanges(List<String> results, boolean committedChanges) {
        CommitChangeStats changeStats = new CommitChangeStats(committedChanges);

        if (results != null) {
            for (String line : results) {
                line = line.trim();
                if (line.indexOf("changed") != -1 &&
                        (line.indexOf("insertion") != -1 || line.indexOf("deletion") != -1)) {
                    String[] parts = line.split(" ");
                    // the 1st element is the number of files changed
                    int fileCount = Integer.parseInt(parts[0]);
                    changeStats.setFileCount(fileCount);
                    changeStats.setCommitCount(changeStats.getCommitCount() + 1);
                    for (int x = 1; x < parts.length; x++) {
                        String part = parts[x];
                        if (part.indexOf("insertion") != -1) {
                            int insertions = Integer.parseInt(parts[x - 1]);
                            changeStats.setInsertions(changeStats.getInsertions() + insertions);
                        } else if (part.indexOf("deletion") != -1) {
                            int deletions = Integer.parseInt(parts[x - 1]);
                            changeStats.setDeletions(changeStats.getDeletions() + deletions);
                        }
                    }
                }
            }
        }

        return changeStats;
    }

    public static CommitChangeStats getChangeStats(String[] cmdList, String projectDir, boolean committedChanges) {
        CommitChangeStats changeStats = new CommitChangeStats(committedChanges);

        if (projectDir == null || !SoftwareCoUtils.isGitProject(projectDir)) {
            return changeStats;
        }

        /**
         * example:
         * -mbp-2:swdc-vscode xavierluiz$ git diff --stat
         lib/KpmProviderManager.ts | 22 ++++++++++++++++++++--
         1 file changed, 20 insertions(+), 2 deletions(-)

         for multiple files it will look like this...
         7 files changed, 137 insertions(+), 55 deletions(-)
         */
        List<String> resultList = SoftwareCoUtils.getResultsForCommandArgs(cmdList, projectDir);

        if (resultList == null || resultList.size() == 0) {
            // something went wrong, but don't try to parse a null or undefined str
            return changeStats;
        }

        // just look for the line with "insertions" and "deletions"
        changeStats = accumulateStatChanges(resultList, committedChanges);

        return changeStats;
    }

    public static CommitChangeStats getUncommitedChanges(String projectDir) {
        CommitChangeStats changeStats = new CommitChangeStats(false);

        if (projectDir == null || !SoftwareCoUtils.isGitProject(projectDir)) {
            return changeStats;
        }

        String[] cmdList = {"git", "diff", "--stat"};

        return getChangeStats(cmdList, projectDir, false);
    }

    // get the git resource config information
    public static ResourceInfo getResourceInfo(String projectDir) {
        ResourceInfo resourceInfo = new ResourceInfo();

        // is the project dir avail?
        if (projectDir != null &&  SoftwareCoUtils.isGitProject(projectDir)) {
            try {
                String[] branchCmd = { "git", "symbolic-ref", "--short", "HEAD" };
                String branch = SoftwareCoUtils.runCommand(branchCmd, projectDir);

                String[] identifierCmd = { "git", "config", "--get", "remote.origin.url" };
                String identifier = SoftwareCoUtils.runCommand(identifierCmd, projectDir);

                String[] emailCmd = { "git", "config", "user.email" };
                String email = SoftwareCoUtils.runCommand(emailCmd, projectDir);

                String[] tagCmd = { "git", "describe", "--all" };
                String tag = SoftwareCoUtils.runCommand(tagCmd, projectDir);

                if (StringUtils.isNotBlank(branch) && StringUtils.isNotBlank(identifier)) {
                    resourceInfo.setBranch(branch);
                    resourceInfo.setTag(tag);
                    resourceInfo.setEmail(email);
                    resourceInfo.setIdentifier(identifier);
                }

                // get the users
                List<TeamMember> members = new ArrayList<>();
                String[] listUsers = { "git", "log", "--pretty=%an,%ae" };
                List<String> results = SoftwareCoUtils.getResultsForCommandArgs(listUsers, projectDir);
                Set<String> emailSet = new HashSet<>();
                if (results != null && results.size() > 0) {
                    // add them
                    for (int i = 0; i < results.size(); i++) {
                        String[] info = results.get(i).split(",");
                        if (info != null && info.length == 2) {
                            String name = info[0];
                            String teamEmail = info[1];
                            if (!emailSet.contains(teamEmail)) {
                                TeamMember member = new TeamMember();
                                member.setEmail(teamEmail);
                                member.setName(name);
                                member.setIdentifier(identifier);
                                members.add(member);
                                emailSet.add(teamEmail);
                            }
                        }
                    }
                }

                // sort the members in alphabetical order
                members = sortByEmail(members);

                resourceInfo.setMembers(members);
            } catch (Exception e) {
                //
            }
        }

        return resourceInfo;
    }

    private static List<TeamMember> sortByEmail(List<TeamMember> members) {
        List<TeamMember> entryList = new ArrayList<TeamMember>(members);
        // natural ASC order
        Collections.sort(
                entryList, new Comparator<TeamMember>() {
                    @Override
                    public int compare(TeamMember entryA, TeamMember entryB) {
                        return entryA.getName().toLowerCase().compareTo(entryB.getName().toLowerCase());
                    }
                }
        );
        return entryList;
    }

    public static String getUsersEmail(String projectDir) {
        if (projectDir == null || !SoftwareCoUtils.isGitProject(projectDir)) {
            return "";
        }
        String[] emailCmd = { "git", "config", "user.email" };
        String email = SoftwareCoUtils.runCommand(emailCmd, projectDir);
        return email;
    }

    public static CommitChangeStats getTodaysCommits(String projectDir, String email) {
        CommitChangeStats changeStats = new CommitChangeStats(true);

        if (projectDir == null || !SoftwareCoUtils.isGitProject(projectDir)) {
            return changeStats;
        }

        return getCommitsForRange("today", projectDir, email);
    }

    public static CommitChangeStats getYesterdaysCommits(String projectDir, String email) {
        CommitChangeStats changeStats = new CommitChangeStats(true);

        if (projectDir == null || !SoftwareCoUtils.isGitProject(projectDir)) {
            return changeStats;
        }

        return getCommitsForRange("yesterday", projectDir, email);
    }

    public static CommitChangeStats getThisWeeksCommits(String projectDir, String email) {
        CommitChangeStats changeStats = new CommitChangeStats(true);

        if (projectDir == null || !SoftwareCoUtils.isGitProject(projectDir)) {
            return changeStats;
        }

        return getCommitsForRange("thisWeek", projectDir, email);
    }

    public static CommitChangeStats getCommitsForRange(String rangeType, String projectDir, String email) {
        if (projectDir == null || !SoftwareCoUtils.isGitProject(projectDir)) {
            return new CommitChangeStats(true);
        }
        SoftwareCoUtils.TimesData timesData = SoftwareCoUtils.getTimesData();
        long startOfRange = 0l;
        if (rangeType == "today") {
            startOfRange = timesData.local_start_day;
        } else if (rangeType == "yesterday") {
            startOfRange = timesData.local_start_yesterday;
        } else if (rangeType == "thisWeek") {
            startOfRange = timesData.local_start_of_week;
        }

        String authorArg = "";
        if (email == null || email.equals("")) {
            ResourceInfo resourceInfo = getResourceInfo(projectDir);
            if (resourceInfo != null && resourceInfo.getEmail() != null && !resourceInfo.getEmail().isEmpty()) {
                authorArg = "--author=" + resourceInfo.getEmail();
            }
        } else {
            authorArg = "--author=" + email;
        }

        // set the until to now
        String untilArg = "--until=" + timesData.now;

        String[] cmdList = {"git", "log", "--stat", "--pretty=COMMIT:%H,%ct,%cI,%s", "--since=" + startOfRange, untilArg, authorArg};

        return getChangeStats(cmdList, projectDir, true);
    }

    public static String getRepoUrlLink(String projectDir) {
        if (projectDir == null || !SoftwareCoUtils.isGitProject(projectDir)) {
            return "";
        }
        String[] cmdList = { "git", "config", "--get", "remote.origin.url" };

        // should only be a result of 1
        List<String> resultList = SoftwareCoUtils.getResultsForCommandArgs(cmdList, projectDir);
        String url = resultList != null && resultList.size() > 0 ? resultList.get(0) : null;
        if (url != null && !url.equals("") && url.indexOf(".git") != -1) {
            url = url.substring(0, url.lastIndexOf(".git"));
        }
        return url;
    }

    public static CommitInfo getLastCommitInfo(String projectDir, String email) {
        if (projectDir == null || !SoftwareCoUtils.isGitProject(projectDir)) {
            return null;
        }
        if (email == null) {
            ResourceInfo resourceInfo = getResourceInfo(projectDir);
            email = resourceInfo != null ? resourceInfo.getEmail() : null;
        }
        CommitInfo commitInfo = new CommitInfo();

        String authorArg = (email != null) ? "--author=" + email : "";

        String[] cmdList = { "git", "log", "--pretty=%H,%s", authorArg, "--max-count=1" };

        // should only be a result of 1
        List<String> resultList = SoftwareCoUtils.getResultsForCommandArgs(cmdList, projectDir);
        if (resultList != null && resultList.size() > 0) {
            String[] parts = resultList.get(0).split(",");
            if (parts != null && parts.length == 2) {
                commitInfo.setCommitId(parts[0]);
                commitInfo.setComment(parts[1]);
                commitInfo.setEmail(email);
            }
        }

        return commitInfo;
    }

}
