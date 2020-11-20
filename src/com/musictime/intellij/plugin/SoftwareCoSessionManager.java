/**
 * Copyright (c) 2018 by Software.com
 * All rights reserved
 */
package com.musictime.intellij.plugin;

import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.musictime.intellij.plugin.actions.MusicToolWindowFactory;
import com.musictime.intellij.plugin.fs.FileManager;
import com.musictime.intellij.plugin.music.*;
import org.apache.http.client.methods.HttpGet;

import java.awt.event.MouseEvent;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Logger;

public class SoftwareCoSessionManager {

    private static SoftwareCoSessionManager instance = null;
    public static final Logger log = Logger.getLogger("SoftwareCoSessionManager");

    public static long start = 0L;
    public static long local_start = 0L;
    private static long lastAppAvailableCheck = 0;

    public static SoftwareCoSessionManager getInstance() {
        if (instance == null) {
            instance = new SoftwareCoSessionManager();
        }
        return instance;
    }

    public static boolean softwareSessionFileExists() {
        // don't auto create the file
        String file = FileManager.getSoftwareSessionFile(false);
        // check if it exists
        File f = new File(file);
        return f.exists();
    }

    public static boolean readmeFileExists() {
        // don't auto create the file
        String file = FileManager.getReadmeFile(false);
        // check if it exists
        File f = new File(file);
        return f.exists();
    }

    public static Project getOpenProject() {
        ProjectManager projMgr = ProjectManager.getInstance();
        Project[] projects = projMgr.getOpenProjects();
        if (projects != null && projects.length > 0) {
            return projects[0];
        }
        return null;
    }

    public static void fetchMusicTimeMetricsDashboard(String plugin, boolean isHtml) {
        String dashboardFile = FileManager.getMusicDashboardFile();

        Writer writer = null;

        String api = "/dashboard/music";
        SoftwareResponse response = SoftwareCoUtils.makeApiCall(api, HttpGet.METHOD_NAME, null);
        if(response.isOk()) {
            String dashboardSummary = response.getJsonStr();

            // write the dashboard summary content
            try {
                writer = new BufferedWriter(new OutputStreamWriter(
                        new FileOutputStream(dashboardFile), StandardCharsets.UTF_8));
                writer.write(dashboardSummary);
            } catch (IOException ex) {
                // Report
            } finally {
                try {
                    writer.close();
                } catch (Exception ex) {/*ignore*/}
            }
        }
    }

    public static void launchMusicTimeMetricsDashboard() {
        Project p = getOpenProject();
        if (p == null) {
            return;
        }

        fetchMusicTimeMetricsDashboard("music-time", false);

        String musicTimeFile = FileManager.getMusicDashboardFile();
        File f = new File(musicTimeFile);

        VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(f);
        OpenFileDescriptor descriptor = new OpenFileDescriptor(p, vFile);
        FileEditorManager.getInstance(p).openTextEditor(descriptor, true);
    }

    public void openReadmeFile() {
        Project p = getOpenProject();
        if (p == null) {
            return;
        }

        // Getting Resource as file object
//        URL url = getClass().getResource("/com/softwareco/intellij/plugin/assets/README.md");
//        String fileContent = getFileContent(url.getFile());

        String readmeFile = FileManager.getReadmeFile(true);
        File f = new File(readmeFile);
        if (!f.exists()) {
            String fileContent = FileManager.getReadmeMdContent();
            Writer writer = null;
            // write the summary content
            try {
                writer = new BufferedWriter(new OutputStreamWriter(
                        new FileOutputStream(new File(readmeFile)), Charset.forName("UTF-8")));
                writer.write(fileContent);
            } catch (IOException ex) {
                // Report
            } finally {
                try {
                    writer.close();
                } catch (Exception ex) {/*ignore*/}
            }
        }
        try {
            VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(f);
            if (vFile != null) {
                OpenFileDescriptor descriptor = new OpenFileDescriptor(p, vFile);
                FileEditorManager.getInstance(p).openTextEditor(descriptor, true);
                FileManager.setItem("intellij_MtReadme", "true");
            }
        } catch (Exception e) {
            System.out.println("error opening file: " + e.getMessage());
        }
    }

    public void statusBarClickHandler(MouseEvent mouseEvent, String id) {
        String headphoneiconId = SoftwareCoStatusBarIconWidget.ICON_ID + "_headphoneicon";
        String likeiconId = SoftwareCoStatusBarIconWidget.ICON_ID + "_likeicon";
        String unlikeiconId = SoftwareCoStatusBarIconWidget.ICON_ID + "_unlikeicon";
        String preiconId = SoftwareCoStatusBarIconWidget.ICON_ID + "_preicon";
        String pauseiconId = SoftwareCoStatusBarIconWidget.ICON_ID + "_pauseicon";
        String playiconId = SoftwareCoStatusBarIconWidget.ICON_ID + "_playicon";
        String nexticonId = SoftwareCoStatusBarIconWidget.ICON_ID + "_nexticon";
        String songtrackId = SoftwareCoStatusBarTextWidget.TEXT_ID + "_songtrack";
        String pulseiconId = SoftwareCoStatusBarTextWidget.TEXT_ID + "_pulseicon";
        String connectspotifyId = SoftwareCoStatusBarTextWidget.TEXT_ID + "_connectspotify";

        if (id.equals(headphoneiconId)) {
            // show the tree view
            MusicToolWindowFactory.toggleWindow();
        } else if(id.equals(connectspotifyId)) {
            MusicControlManager.connectSpotify();
        } else if(id.equals(playiconId)) {
            PlayerControlManager.playIt();
        } else if(id.equals(pauseiconId)) {
            PlayerControlManager.pauseIt();
        } else if(id.equals(preiconId)) {
            PlayerControlManager.previousIt();
        } else if(id.equals(nexticonId)) {
            PlayerControlManager.nextIt();
        } else if(id.equals(songtrackId)) {
            MusicControlManager.launchPlayer();
        } else if (id.equals(pulseiconId)) {
            PlaylistManager.fetchTrack();
        } else if(id.equals(unlikeiconId)) {
            PlayerControlManager.likeSpotifyTrack(true, MusicControlManager.currentTrackId);
            PlayListCommands.updatePlaylists(3, null);
        } else if(id.equals(likeiconId)) {
            PlayerControlManager.likeSpotifyTrack(false, MusicControlManager.currentTrackId);
            PlayListCommands.updatePlaylists(3, null);
        }
    }
}
