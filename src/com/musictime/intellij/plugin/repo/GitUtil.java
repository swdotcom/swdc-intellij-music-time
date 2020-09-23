package com.musictime.intellij.plugin.repo;


import com.musictime.intellij.plugin.SoftwareCoUtils;
import com.musictime.intellij.plugin.models.ResourceInfo;
import com.musictime.intellij.plugin.models.TeamMember;
import org.apache.commons.lang.StringUtils;

import java.util.*;

public class GitUtil {

    // get the git resource config information
    public static ResourceInfo getResourceInfo(String projectDir, boolean buildMembers) {
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

                    // get the ownerId and repoName out of the identifier
                    String[] parts = identifier.split("/");
                    if (parts.length > 2) {
                        // get the last part
                        String repoNamePart = parts[parts.length - 1];
                        int typeIdx = repoNamePart.indexOf(".git");
                        if (typeIdx != -1) {
                            // it's a git identifier AND it has enough parts
                            // to get the repo name and owner id
                            resourceInfo.setRepoName(repoNamePart.substring(0, typeIdx));
                            resourceInfo.setOwnerId(parts[parts.length - 2]);
                        }
                    }
                }

                if (buildMembers) {
                    // get the users
                    List<TeamMember> members = new ArrayList<>();
                    String[] listUsers = {"git", "log", "--pretty=%an,%ae"};
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
                }
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

}
