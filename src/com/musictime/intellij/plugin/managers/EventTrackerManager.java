package com.musictime.intellij.plugin.managers;

import com.intellij.openapi.project.Project;
import com.musictime.intellij.plugin.KeystrokeCount;
import com.musictime.intellij.plugin.SoftwareCoMusic;
import com.musictime.intellij.plugin.SoftwareCoUtils;
import com.musictime.intellij.plugin.fs.FileManager;
import com.musictime.intellij.plugin.models.FileDetails;
import com.musictime.intellij.plugin.models.ResourceInfo;
import com.musictime.intellij.plugin.musicjava.Client;
import com.musictime.intellij.plugin.repo.GitUtil;
import com.swdc.snowplow.tracker.entities.*;
import com.swdc.snowplow.tracker.events.CodetimeEvent;
import com.swdc.snowplow.tracker.events.EditorActionEvent;
import com.swdc.snowplow.tracker.events.UIInteractionEvent;
import com.swdc.snowplow.tracker.events.UIInteractionType;
import com.swdc.snowplow.tracker.manager.TrackerManager;
import org.apache.commons.lang.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Map;
import java.util.logging.Logger;

public class EventTrackerManager {
    public static final Logger log = Logger.getLogger("EventTrackerManager");

    private static EventTrackerManager instance = null;

    private TrackerManager trackerMgr;
    private boolean ready = false;

    public static EventTrackerManager getInstance() {
        if (instance == null) {
            instance = new EventTrackerManager();
        }
        return instance;
    }

    private EventTrackerManager() {}

    public void init() {
        trackerMgr = new TrackerManager(
                Client.api_endpoint, "MusicTime", "swdc-intellij");
        ready = true;
    }

    public void trackCodeTimeEvent(KeystrokeCount payload) {
        if (!this.ready) {
            return;
        }
        ResourceInfo resourceInfo = GitUtil.getResourceInfo(payload.getProject().getDirectory(), false);

        Map<String, KeystrokeCount.FileInfo> fileInfoDataSet = payload.getFileInfos();
        for ( KeystrokeCount.FileInfo fileInfoData : fileInfoDataSet.values() ) {
            CodetimeEvent event = new CodetimeEvent();

            event.characters_added = fileInfoData.characters_added;
            event.characters_deleted = fileInfoData.characters_deleted;
            event.single_adds = fileInfoData.single_adds;
            event.single_deletes = fileInfoData.single_deletes;
            event.multi_deletes = fileInfoData.multi_deletes;
            event.multi_adds = fileInfoData.multi_adds;
            event.auto_indents = fileInfoData.auto_indents;
            event.replacements = fileInfoData.replacements;
            event.is_net_change = fileInfoData.is_net_change;

            event.keystrokes = fileInfoData.keystrokes;
            event.lines_added = fileInfoData.linesAdded;
            event.lines_deleted = fileInfoData.linesRemoved;

            Date startDate = new Date(fileInfoData.start * 1000);
            event.start_time = DateTimeFormatter.ISO_INSTANT.format(startDate.toInstant());
            Date endDate = new Date(fileInfoData.end * 1000);
            event.end_time = DateTimeFormatter.ISO_INSTANT.format(endDate.toInstant());

            // set the entities
            event.fileEntity = this.getFileEntity(fileInfoData);
            event.projectEntity = this.getProjectEntity();
            event.authEntity = this.getAuthEntity();
            event.pluginEntity = this.getPluginEntity();
            event.repoEntity = this.getRepoEntity(resourceInfo);

            // execute async
            trackerMgr.trackCodeTimeEvent(event);
        }
    }

    public void trackUIInteraction(UIInteractionType interaction_type, UIElementEntity elementEntity) {
        if (!this.ready) {
            return;
        }

        UIInteractionEvent event = new UIInteractionEvent();
        event.interaction_type = interaction_type;

        // set the entities
        event.uiElementEntity = elementEntity;
        event.authEntity = this.getAuthEntity();
        event.pluginEntity = this.getPluginEntity();

        // execute async
        log.info("ui interaction event processed");
        trackerMgr.trackUIInteraction(event);
    }

    public void trackEditorAction(String entity, String type) {
        trackEditorAction(entity, type, null);
    }

    public void trackEditorAction(String entity, String type, String full_file_name) {
        if (!this.ready) {
            return;
        }

        EditorActionEvent event = new EditorActionEvent();
        event.entity = entity;
        event.type = type;

        // set the entities
        event.authEntity = this.getAuthEntity();
        event.pluginEntity = this.getPluginEntity();
        event.projectEntity = this.getProjectEntity();
        event.fileEntity = this.getFileEntityFromFileName(full_file_name);
        ResourceInfo resourceInfo = GitUtil.getResourceInfo(event.projectEntity.project_directory, false);
        event.repoEntity = this.getRepoEntity(resourceInfo);

        // execute async
        log.info("editor action event processed");
        trackerMgr.trackEditorAction(event);
    }

    private AuthEntity getAuthEntity() {
        AuthEntity authEntity = new AuthEntity();
        String jwt = FileManager.getItem("jwt");
        if (StringUtils.isNotBlank(jwt)) {
            if (jwt.indexOf("JWT") == 0) {
                authEntity.setJwt(jwt.split("JWT ")[1].trim());
            } else {
                authEntity.setJwt(jwt.trim());
            }
        } else {
            authEntity.setJwt("");
        }
        return authEntity;
    }

    private FileEntity getFileEntityFromFileName(String fullFileName) {
        FileDetails fileDetails = SoftwareCoUtils.getFileDetails(fullFileName);
        FileEntity fileEntity = new FileEntity();
        fileEntity.character_count = fileDetails.character_count;
        fileEntity.file_name = fileDetails.project_file_name;
        fileEntity.file_path = fileDetails.full_file_name;
        fileEntity.line_count = fileDetails.line_count;
        fileEntity.syntax = fileDetails.syntax;
        return fileEntity;
    }

    private FileEntity getFileEntity(KeystrokeCount.FileInfo fileInfo) {
        FileDetails fileDetails = SoftwareCoUtils.getFileDetails(fileInfo.fsPath);
        FileEntity fileEntity = new FileEntity();
        fileEntity.character_count = fileDetails.character_count;
        fileEntity.file_name = fileDetails.project_file_name;
        fileEntity.file_path = fileDetails.full_file_name;
        fileEntity.line_count = fileDetails.line_count;
        fileEntity.syntax = fileDetails.syntax;
        return fileEntity;
    }

    private ProjectEntity getProjectEntity() {
        ProjectEntity projectEntity = new ProjectEntity();
        Project activeProject = SoftwareCoUtils.getFirstActiveProject();
        if (activeProject != null) {
            projectEntity.project_directory = activeProject.getBasePath();
            projectEntity.project_name = activeProject.getName();
        }
        return projectEntity;
    }

    private RepoEntity getRepoEntity(ResourceInfo resourceInfo) {
        RepoEntity repoEntity = new RepoEntity();
        if (resourceInfo != null) {
            repoEntity.git_branch = resourceInfo.getBranch();
            repoEntity.git_tag = resourceInfo.getTag();
            repoEntity.repo_identifier = resourceInfo.getIdentifier();
            repoEntity.owner_id = resourceInfo.getOwnerId();
            repoEntity.repo_name = resourceInfo.getRepoName();
        }
        return repoEntity;
    }

    private PluginEntity getPluginEntity() {
        PluginEntity pluginEntity = new PluginEntity();
        pluginEntity.plugin_name = SoftwareCoMusic.getPluginName();
        pluginEntity.plugin_version = SoftwareCoMusic.getVersion();
        pluginEntity.plugin_id = SoftwareCoUtils.pluginId;
        return pluginEntity;
    }
}
