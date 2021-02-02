/**
 * Copyright (c) 2018 by Software.com
 * All rights reserved
 */
package com.musictime.intellij.plugin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.util.PlatformUtils;
import com.musictime.intellij.plugin.models.DeviceInfo;
import com.musictime.intellij.plugin.models.FileDetails;
import com.musictime.intellij.plugin.music.MusicControlManager;
import com.musictime.intellij.plugin.music.PlayListCommands;
import com.musictime.intellij.plugin.musicjava.DeviceManager;
import com.musictime.intellij.plugin.musicjava.MusicStore;
import com.musictime.intellij.plugin.tree.MusicToolWindow;
import com.musictime.intellij.plugin.tree.PlaylistAction;
import org.apache.commons.lang.StringUtils;
import swdc.java.ops.http.ClientResponse;
import swdc.java.ops.http.OpsHttpClient;
import swdc.java.ops.manager.AccountManager;
import swdc.java.ops.manager.AsyncManager;
import swdc.java.ops.manager.FileUtilManager;
import swdc.java.ops.manager.UtilManager;
import swdc.java.ops.model.Integration;
import swdc.java.ops.model.User;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.*;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

public class SoftwareCoUtils {

    public static final Logger LOG = Logger.getLogger("SoftwareCoUtils");

    // 16 = intellij music time
    public static int pluginId = 16;
    public static String api_endpoint = "https://api.software.com";
    public static String launch_url = "https://app.software.com";

    public static String IDE_NAME = "";
    public static String IDE_VERSION = "";

    private static String VERSION = null;
    private static String pluginName = null;

    private static ScheduledFuture toggleSongNameFuture = null;

    private static String workspace_name = null;
    private static boolean initiatedCodeTimeInstallCheck = false;
    public static boolean codeTimeInstalled = false;

    static {
        try {
            IDE_NAME = PlatformUtils.getPlatformPrefix();
            IDE_VERSION = ApplicationInfo.getInstance().getFullVersion();
        } catch (Exception e) {
            System.out.println("Unable to retrieve IDE name and version info: " + e.getMessage());
        }
    }

    public static String getVersion() {
        if (VERSION == null) {
            IdeaPluginDescriptor pluginDescriptor = getIdeaPluginDescriptor();
            if (pluginDescriptor != null) {
                VERSION = pluginDescriptor.getVersion();
            } else {
                return "1.1.0";
            }
        }
        return VERSION;
    }

    public static String getPluginName() {
        if (pluginName == null) {
            IdeaPluginDescriptor pluginDescriptor = getIdeaPluginDescriptor();
            if (pluginDescriptor != null) {
                pluginName = pluginDescriptor.getName();
            } else {
                pluginName = "Music Time";
            }
        }
        return pluginName;
    }

    private static IdeaPluginDescriptor getIdeaPluginDescriptor() {
        IdeaPluginDescriptor[] descriptors = PluginManager.getPlugins();
        if (descriptors != null && descriptors.length > 0) {
            for (IdeaPluginDescriptor descriptor : descriptors) {
                if (descriptor.getPluginId().getIdString().equals("com.softwareco.intellij.plugin")) {
                    return descriptor;
                }
            }
        }
        return null;
    }

    public static boolean isCodeTimeInstalled() {
        if (!initiatedCodeTimeInstallCheck) {
            initiatedCodeTimeInstallCheck = true;
            getUser();
        }
        return codeTimeInstalled;
    }

    public static String getWorkspaceName() {
        if (workspace_name == null) {
            workspace_name = generateToken();
        }
        return workspace_name;
    }

    public static String generateToken() {
        String uuid = UUID.randomUUID().toString();
        return uuid.replace("-", "");
    }

    public static boolean isMusicTime() {
        if (getPluginName().equals("Music Time")) {
            return true;
        }
        return false;
    }

    public static Project getFirstActiveProject() {
        Project[] projects = ProjectManager.getInstance().getOpenProjects();
        if (projects != null && projects.length > 0) {
            return projects[0];
        }
        Editor[] editors = EditorFactory.getInstance().getAllEditors();
        if (editors != null && editors.length > 0) {
            return editors[0].getProject();
        }
        return null;
    }

    public static Project getProjectForPath(String path) {
        Editor[] editors = EditorFactory.getInstance().getAllEditors();
        if (editors != null && editors.length > 0) {
            for (Editor editor : editors) {
                if (editor != null && editor.getProject() != null) {
                    String basePath = editor.getProject().getBasePath();
                    if (path.indexOf(basePath) != -1) {
                        return editor.getProject();
                    }
                }
            }
        } else {
            Project[] projects = ProjectManager.getInstance().getOpenProjects();
            if (projects != null && projects.length > 0) {
                return projects[0];
            }
        }
        return null;
    }

    public static FileDetails getFileDetails(String fullFileName) {
        FileDetails fileDetails = new FileDetails();
        if (StringUtils.isNotBlank(fullFileName)) {
            fileDetails.full_file_name = fullFileName;
            Project p = getProjectForPath(fullFileName);
            if (p != null) {
                fileDetails.project_directory = p.getBasePath();
                fileDetails.project_name = p.getName();
            }

            File f = new File(fullFileName);

            if (f.exists()) {
                fileDetails.character_count = f.length();
                fileDetails.file_name = f.getName();
                if (StringUtils.isNotBlank(fileDetails.project_directory) && fullFileName.indexOf(fileDetails.project_directory) != -1) {
                    // strip out the project_file_name
                    String[] parts = fullFileName.split(fileDetails.project_directory);
                    if (parts.length > 1) {
                        fileDetails.project_file_name = parts[1];
                    } else {
                        fileDetails.project_file_name = fullFileName;
                    }
                } else {
                    fileDetails.project_file_name = fullFileName;
                }
                fileDetails.line_count = SoftwareCoUtils.getLineCount(fullFileName);

                VirtualFile vFile = LocalFileSystem.getInstance().findFileByIoFile(f);
                if (vFile != null) {
                    fileDetails.syntax = vFile.getFileType().getName();
                }
            }
        }

        return fileDetails;
    }

    public static int getLineCount(String fileName) {
        Stream<String> stream = null;
        try {
            Path path = Paths.get(fileName);
            stream = Files.lines(path);
            return (int) stream.count();
        } catch (Exception e) {
            return 0;
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (Exception e) {
                    //
                }
            }
        }
    }

    public static Project getOpenProject() {
        ProjectManager projMgr = ProjectManager.getInstance();
        Project[] projects = projMgr.getOpenProjects();
        if (projects != null && projects.length > 0) {
            return projects[0];
        }
        return null;
    }

    public static synchronized void setStatusLineMessage() {
        try {
            Project p = getOpenProject();
            if (p == null) {
                return;
            }
            final StatusBar statusBar = WindowManager.getInstance().getStatusBar(p);

            if (statusBar != null) {
                updateStatusBar(false);
            }
        } catch (Exception e) {
            //
        }
    }

    private static void updateStatusBar(boolean hideTrack) {

        ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
                ProjectManager pm = ProjectManager.getInstance();
                if (pm != null && pm.getOpenProjects() != null && pm.getOpenProjects().length > 0) {

                    try {
                        Project p = pm.getOpenProjects()[0];
                        StatusBar statusBar = WindowManager.getInstance().getStatusBar(p);

                        String headphoneiconId = SoftwareCoStatusBarIconWidget.ICON_ID + "_headphoneicon";
                        String likeiconId = SoftwareCoStatusBarIconWidget.ICON_ID + "_likeicon";
                        String unlikeiconId = SoftwareCoStatusBarIconWidget.ICON_ID + "_unlikeicon";
                        String preiconId = SoftwareCoStatusBarIconWidget.ICON_ID + "_preicon";
                        String pauseiconId = SoftwareCoStatusBarIconWidget.ICON_ID + "_pauseicon";
                        String playiconId = SoftwareCoStatusBarIconWidget.ICON_ID + "_playicon";
                        String nexticonId = SoftwareCoStatusBarIconWidget.ICON_ID + "_nexticon";
                        String pulseiconId = SoftwareCoStatusBarTextWidget.TEXT_ID + "_pulseicon";
                        String songtrackId = SoftwareCoStatusBarTextWidget.TEXT_ID + "_songtrack";
                        String connectspotifyId = SoftwareCoStatusBarTextWidget.TEXT_ID + "_connectspotify";

                        Disposable disposable = new Disposable() {
                            @Override
                            public void dispose() {
                                if (statusBar.getWidget(headphoneiconId) != null) {
                                    statusBar.removeWidget(headphoneiconId);
                                }
                                if (statusBar.getWidget(likeiconId) != null) {
                                    statusBar.removeWidget(likeiconId);
                                }
                                if (statusBar.getWidget(unlikeiconId) != null) {
                                    statusBar.removeWidget(unlikeiconId);
                                }
                                if (statusBar.getWidget(preiconId) != null) {
                                    statusBar.removeWidget(preiconId);
                                }
                                if (statusBar.getWidget(pauseiconId) != null) {
                                    statusBar.removeWidget(pauseiconId);
                                }
                                if (statusBar.getWidget(playiconId) != null) {
                                    statusBar.removeWidget(playiconId);
                                }
                                if (statusBar.getWidget(nexticonId) != null) {
                                    statusBar.removeWidget(nexticonId);
                                }
                                if (statusBar.getWidget(pulseiconId) != null) {
                                    statusBar.removeWidget(pulseiconId);
                                }
                                if (statusBar.getWidget(songtrackId) != null) {
                                    statusBar.removeWidget(songtrackId);
                                }
                                if (statusBar.getWidget(connectspotifyId) != null) {
                                    statusBar.removeWidget(connectspotifyId);
                                }
                            }
                        };

                        disposable.dispose();

                        boolean requiresReAuth = MusicControlManager.requiresReAuthentication();
                        boolean hasSpotifyAccess = MusicControlManager.hasSpotifyAccess();
                        boolean requiresAccess = requiresReAuth || !hasSpotifyAccess ? true : false;

                        String connectLabel = null;
                        String connectTooltip = "Connect Spotify";
                        if (requiresAccess) {
                            if (requiresReAuth) {
                                connectLabel = "Reconnect Spotify";
                                connectTooltip = "We're unable to access Spotify. Reconnect if this issue continues.";
                            } else if (!hasSpotifyAccess) {
                                connectLabel = "Connect Spotify";
                            }
                        }

                        String email = FileUtilManager.getItem("name");
                        String headphoneIconVal = "headphones.png";
                        String headphoneTooltip = "Click to see more from Music Time.";
                        if (StringUtils.isNotBlank(email)) {
                            headphoneTooltip += " (" + email + ")";
                        }

                        DeviceInfo deviceInfo = DeviceManager.getBestDeviceOption();
                        SoftwareCoStatusBarIconWidget headphoneIconWidget = buildStatusBarIconWidget(
                                headphoneIconVal, headphoneTooltip, headphoneiconId);
                        statusBar.addWidget(headphoneIconWidget, headphoneiconId, disposable);
                        statusBar.updateWidget(headphoneiconId);

                        if (StringUtils.isNotBlank(connectLabel)) {
                            SoftwareCoStatusBarTextWidget kpmWidget = buildStatusBarTextWidget(
                                    connectLabel, connectTooltip, connectspotifyId);
                            statusBar.addWidget(kpmWidget, connectspotifyId, disposable);
                            statusBar.updateWidget(connectspotifyId);
                        }

                        String likeIcon = "like.png";
                        String unlikeIcon = "unlike.png";
                        String preIcon = "previous.png";
                        String pauseIcon = "pause.png";
                        String playIcon = "play.png";
                        String nextIcon = "next.png";
                        String pulseIcon = "pulse.png";

                        String trackName = MusicControlManager.currentTrackName;
                        final String musicToolTipVal = trackName != null ? trackName : "";
                        if (trackName != null && trackName.length() > 19) {
                            trackName = trackName.substring(0, 18) + "...";
                        }

                        boolean isPremiumUser = MusicStore.isSpotifyPremiumUser();
                        boolean isMacUser = UtilManager.isMac();

                        if (deviceInfo != null && connectLabel == null) {

                            if(isPremiumUser || isMacUser) {

                                SoftwareCoStatusBarIconWidget preIconWidget = buildStatusBarIconWidget(
                                        preIcon, "previous", preiconId);
                                statusBar.addWidget(preIconWidget, preiconId, disposable);
                                statusBar.updateWidget(preiconId);

                                if (MusicControlManager.currentTrackPlaying) {
                                    SoftwareCoStatusBarIconWidget pauseIconWidget = buildStatusBarIconWidget(
                                            pauseIcon, "pause", pauseiconId);
                                    statusBar.addWidget(pauseIconWidget, pauseiconId, disposable);
                                    statusBar.updateWidget(pauseiconId);
                                } else {
                                    SoftwareCoStatusBarIconWidget playIconWidget = buildStatusBarIconWidget(
                                            playIcon, "play", playiconId);
                                    statusBar.addWidget(playIconWidget, playiconId, disposable);
                                    statusBar.updateWidget(playiconId);
                                }

                                SoftwareCoStatusBarIconWidget nextIconWidget = buildStatusBarIconWidget(
                                        nextIcon, "next", nexticonId);
                                statusBar.addWidget(nextIconWidget, nexticonId, disposable);
                                statusBar.updateWidget(nexticonId);

                            }
                        }

                        if(MusicControlManager.currentTrackId != null) {
                            if (MusicControlManager.likedTracks.containsKey(MusicControlManager.currentTrackId)) {
                                SoftwareCoStatusBarIconWidget likeIconWidget = buildStatusBarIconWidget(
                                        likeIcon, "unlike", likeiconId);
                                statusBar.addWidget(likeIconWidget, likeiconId, disposable);
                                statusBar.updateWidget(likeiconId);
                            } else {
                                SoftwareCoStatusBarIconWidget unlikeIconWidget = buildStatusBarIconWidget(
                                        unlikeIcon, "like", unlikeiconId);
                                statusBar.addWidget(unlikeIconWidget, unlikeiconId, disposable);
                                statusBar.updateWidget(unlikeiconId);
                            }
                        }

                        if (StringUtils.isBlank(trackName) || hideTrack) {
                            SoftwareCoStatusBarIconWidget pulseIconWidget = buildStatusBarIconWidget(
                                    pulseIcon, "Display song info", pulseiconId);
                            statusBar.addWidget(pulseIconWidget, pulseiconId, disposable);
                            statusBar.updateWidget(pulseiconId);
                        }

                        if (!hideTrack) {
                            SoftwareCoStatusBarTextWidget kpmWidget = buildStatusBarTextWidget(
                                    trackName, musicToolTipVal, songtrackId);
                            statusBar.addWidget(kpmWidget, songtrackId, disposable);
                            statusBar.updateWidget(songtrackId);
                        }

                        // hide the song
                        if (!hideTrack) {
                            AsyncManager.getInstance().executeOnceInSeconds(() -> toggleSongName(), 1);
                        }

                    } catch(Exception e){
                        //
                    }
                }
            }
        });
    }

    private static void toggleSongName() {
        if (toggleSongNameFuture != null) {
            toggleSongNameFuture.cancel(false);
            toggleSongNameFuture = null;
        }
        toggleSongNameFuture = AsyncManager.getInstance().executeOnceInSeconds(() -> hideSongName(), 5);
    }

    private static void hideSongName() {
        updateStatusBar(true);
        toggleSongNameFuture = null;
    }

    public static SoftwareCoStatusBarTextWidget buildStatusBarTextWidget(String msg, String tooltip, String id) {
        SoftwareCoStatusBarTextWidget textWidget =
                new SoftwareCoStatusBarTextWidget(id);
        textWidget.setText(msg);
        textWidget.setTooltip(tooltip);
        return textWidget;
    }

    public static SoftwareCoStatusBarIconWidget buildStatusBarIconWidget(String iconName, String tooltip, String id) {
        Icon icon = IconLoader.getIcon("/com/musictime/intellij/plugin/assets/" + iconName);

        SoftwareCoStatusBarIconWidget iconWidget =
                new SoftwareCoStatusBarIconWidget(id);
        iconWidget.setIcon(icon);
        iconWidget.setTooltip(tooltip);
        return iconWidget;
    }

    protected static boolean isItunesRunning() {
        // get running of application "iTunes"
        String[] args = { "osascript", "-e", "get running of application \"iTunes\"" };
        String result = UtilManager.runCommand(args, null);
        return (result != null) ? Boolean.valueOf(result) : false;
    }

    protected static String itunesTrackScript = "tell application \"iTunes\"\n" +
            "set track_artist to artist of current track\n" +
            "set track_album to album of current track\n" +
            "set track_name to name of current track\n" +
            "set track_duration to duration of current track\n" +
            "set track_id to id of current track\n" +
            "set track_genre to genre of current track\n" +
            "set track_state to player state\n" +
            "set json to \"type='itunes';album='\" & track_album & \"';genre='\" & track_genre & \"';artist='\" & track_artist & \"';id='\" & track_id & \"';name='\" & track_name & \"';state='\" & track_state & \"';duration='\" & track_duration & \"'\"\n" +
            "end tell\n" +
            "return json\n";

    protected static String getItunesTrack() {
        String[] args = { "osascript", "-e", itunesTrackScript };
        return UtilManager.runCommand(args, null);
    }

    public static boolean isSpotifyRunning() {
        String[] args = { "osascript", "-e", "get running of application \"Spotify\"" };
        String result = UtilManager.runCommand(args, null);
        return (result != null) ? Boolean.valueOf(result) : false;
    }

    protected static boolean isSpotifyInstalled() {
        String[] args = { "osascript", "-e", "exists application \"Spotify\"" };
        String result = UtilManager.runCommand(args, null);
        return (result != null) ? Boolean.valueOf(result) : false;
    }

    protected static String spotifyTrackScript = "tell application \"Spotify\"\n" +
            "set track_artist to artist of current track\n" +
            "set track_album to album of current track\n" +
            "set track_name to name of current track\n" +
            "set track_duration to duration of current track\n" +
            "set track_id to id of current track\n" +
            "set track_state to player state\n" +
            "set json to \"type='spotify';album='\" & track_album & \"';genre='';artist='\" & track_artist & \"';id='\" & track_id & \"';name='\" & track_name & \"';state='\" & track_state & \"';duration='\" & track_duration & \"'\"\n" +
            "end tell\n" +
            "return json\n";

    protected static String getSpotifyTrack() {
        String[] args = { "osascript", "-e", spotifyTrackScript };
        return UtilManager.runCommand(args, null);
    }

    public static JsonObject getCurrentMusicTrack() {
        JsonObject jsonObj = new JsonObject();
        if (!UtilManager.isMac()) {
            return jsonObj;
        }

        boolean spotifyRunning = isSpotifyRunning();
        boolean itunesRunning = isItunesRunning();

        String trackInfo = "";
        // Vintage Trouble, My Whole World Stopped Without You, spotify:track:7awBL5Pu8LD6Fl7iTrJotx, My Whole World Stopped Without You, 244080
        if (spotifyRunning) {
            trackInfo = getSpotifyTrack();
        } else if (itunesRunning) {
            trackInfo = getItunesTrack();
        }

        if (trackInfo != null && !trackInfo.equals("")) {
            // trim and replace things
            trackInfo = trackInfo.trim();
            trackInfo = trackInfo.replace("\"", "");
            trackInfo = trackInfo.replace("'", "");
            String[] paramParts = trackInfo.split(";");
            for (String paramPart : paramParts) {
                paramPart = paramPart.trim();
                String[] params = paramPart.split("=");
                if (params != null && params.length == 2) {
                    jsonObj.addProperty(params[0], params[1]);
                }
            }

        }
        return jsonObj;
    }

    public static void submitGitIssue() {
        BrowserUtil.browse("https://github.com/swdotcom/swdc-intellij-music-time/issues");
    }

    public static void submitFeedback() {
        BrowserUtil.browse("mailto:cody@software.com");
    }

    public static synchronized void updatePlayerControls(boolean recursiveCall) {
        PlayListCommands.updatePlaylists(PlaylistAction.REFRESH_PLAYLIST_WINDOW, null); // API call

        SoftwareCoUtils.setStatusLineMessage();
    }

    private static String getSingleLineResult(List<String> cmd, int maxLen) {
        String result = null;
        String[] cmdArgs = Arrays.copyOf(cmd.toArray(), cmd.size(), String[].class);
        String content = UtilManager.runCommand(cmdArgs, null);

        // for now just get the 1st one found
        if (content != null) {
            String[] contentList = content.split("\n");
            if (contentList != null && contentList.length > 0) {
                int len = (maxLen != -1) ? Math.min(maxLen, contentList.length) : contentList.length;
                for (int i = 0; i < len; i++) {
                    String line = contentList[i];
                    if (line != null && line.trim().length() > 0) {
                        result = line.trim();
                        break;
                    }
                }
            }
        }
        return result;
    }

    public static User getUser() {
        User softwareUser = AccountManager.getUser();
        if (softwareUser != null && !codeTimeInstalled) {
            // check to see if intellij codetime is installed
            Integration intellijCtPlugin = FileUtilManager.getIntegrations().stream()
                    .filter(n -> n.id == 4 && n.status.toLowerCase().equals("active"))
                    .findAny()
                    .orElse(null);
            if (intellijCtPlugin != null) {
                // it's installed
                codeTimeInstalled = true;
            } else {
                // check to see if latestPayloadTimestampEndUtc is found
                String val = FileUtilManager.getItem("latestPayloadTimestampEndUtc");
                if (StringUtils.isNotBlank(val)) {
                    codeTimeInstalled = true;
                }
            }
        }
        return softwareUser;
    }

    public static void getAndUpdateClientInfo() {
        // To find client info
        String api = "/auth/spotify/clientInfo";
        ClientResponse resp = OpsHttpClient.softwareGet(api, FileUtilManager.getItem("jwt"));
        if (resp.isOk()) {
            JsonObject jsonObj = resp.getJsonObj();
            if (jsonObj != null) {
                MusicStore.SPOTIFY_CLIENT_ID = jsonObj.get("clientId").getAsString();
                MusicStore.SPOTIFY_CLIENT_SECRET = jsonObj.get("clientSecret").getAsString();
            }
        }
    }

    public static void showInfoMessage(String msg) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
                // ask to download the PM
                Messages.showInfoMessage(msg, getPluginName());
            }
        });
    }

    public static void showMsgPrompt(String infoMsg, Color color) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
                ProjectManager pm = ProjectManager.getInstance();
                if (pm != null && pm.getOpenProjects() != null && pm.getOpenProjects().length > 0) {
                    try {
                        Project p = pm.getOpenProjects()[0];
                        final StatusBar statusBar = WindowManager.getInstance().getStatusBar(p);

                        JLabel msg = new JLabel("<html><strong>Music Time</strong><br>" + infoMsg + "</html>");
                        msg.setBorder(new EmptyBorder(2, 10, 2, 0));

                        statusBar.fireNotificationPopup(msg, color);
                    } catch(Exception e) {}
                }
                //Messages.showInfoMessage(infoMsg, pluginName);
            }
        });
    }

    public static String showMsgInputPrompt(String message, String title, Icon icon, String[] options) {
        int idx = Messages.showChooseDialog(message, title, options, options[0], icon);
        if (idx >= 0) {
            return options[idx];
        }
        return null;

        // return Messages.showEditableChooseDialog(message, title, icon, options, options[0], null);
    }

    public static String showInputPrompt(String message, String title, Icon icon) {
        return Messages.showInputDialog(message, title, icon, "", getRegexInputValidator());
    }

    public static boolean checkRegistration() {
        String email = FileUtilManager.getItem("name");
        if (StringUtils.isBlank(email)) {
            String infoMsg = "Sign up or register for a web.com account at Software.com to view your most productive music.";
            String[] options = new String[]{"Sign up", "Cancel"};
            int response = Messages.showDialog(infoMsg, SoftwareCoUtils.getPluginName(), options, 0, Messages.getInformationIcon());
            if (response == 0) {
                AccountManager.showAuthSelectPrompt(true, ()-> {
                    MusicToolWindow.refresh();});
            }
            return false;
        }
        return true;
    }

    private static InputValidator getRegexInputValidator() {
        return new InputValidator() {
            @Override
            public boolean checkInput(String string) {
                try {
                    if (string == null || string.trim().isEmpty()) {
                        //do not allow null or blank entries
                        return false;
                    }
                    Pattern.compile(string);
                    return true;
                } catch (PatternSyntaxException e) {
                    return false;
                }
            }

            @Override
            public boolean canClose(String s) {
                return true;
            }
        };
    }

    public static boolean isGitProject(String projectDir) {
        if (projectDir == null || projectDir.equals("")) {
            return false;
        }

        String gitFile = projectDir + File.separator + ".git";
        File f = new File(gitFile);
        return f.exists();
    }

    public static void launchMusicWebDashboard() {
        if (!SoftwareCoUtils.checkRegistration()) {
            return;
        }
        String url = launch_url + "/music";
        BrowserUtil.browse(url);
    }

    public static String buildQueryString(JsonObject obj, boolean includeQmark) {
        StringBuffer sb = new StringBuffer();
        Iterator<String> keys = obj.keySet().iterator();
        while(keys.hasNext()) {
            if (sb.length() > 0) {
                sb.append("&");
            }
            String key = keys.next();
            String val = obj.get(key).getAsString();
            try {
                val = URLEncoder.encode(val, "UTF-8");
            } catch (Exception e) {
                //
            }
            sb.append(key).append("=").append(val);
        }
        if (includeQmark) {
            return "?" + sb.toString();
        }
        return sb.toString();
    }

}
