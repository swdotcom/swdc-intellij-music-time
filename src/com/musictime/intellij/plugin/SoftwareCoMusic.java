package com.musictime.intellij.plugin;

import com.google.gson.Gson;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.messages.MessageBusConnection;
import com.musictime.intellij.plugin.actions.MusicToolWindowFactory;
import com.musictime.intellij.plugin.fs.FileManager;
import com.musictime.intellij.plugin.managers.EventTrackerManager;
import com.musictime.intellij.plugin.music.MusicControlManager;
import com.musictime.intellij.plugin.music.PlayListCommands;
import com.musictime.intellij.plugin.music.PlaylistManager;
import com.musictime.intellij.plugin.musicjava.Apis;
import com.musictime.intellij.plugin.musicjava.DeviceManager;
import org.apache.commons.lang.StringUtils;

import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SoftwareCoMusic implements ApplicationComponent {
    public static final Logger log = Logger.getLogger("SoftwareCoMusic");
    public static Gson gson;

    public static MessageBusConnection connection;

    private SoftwareCoSessionManager sessionMgr = SoftwareCoSessionManager.getInstance();
    private AsyncManager asyncManager = AsyncManager.getInstance();
    private KeystrokeManager keystrokeMgr = KeystrokeManager.getInstance();

    private static int retry_counter = 0;
    private static String pluginName = null;
    private static long check_online_interval_ms = 1000 * 60;

    public SoftwareCoMusic() {
    }

    private static IdeaPluginDescriptor getIdeaPluginDescriptor() {
        IdeaPluginDescriptor[] desriptors = PluginManager.getPlugins();
        if (desriptors != null && desriptors.length > 0) {
            for (int i = 0; i < desriptors.length; i++) {
                IdeaPluginDescriptor descriptor = desriptors[i];
                if (descriptor.getPluginId().getIdString().equals("com.musictime.intellij.plugin")) {
                    return descriptor;
                }
            }
        }
        return null;
    }

    public static String getVersion() {
        if (SoftwareCoUtils.VERSION == null) {
            IdeaPluginDescriptor pluginDescriptor = getIdeaPluginDescriptor();
            if (pluginDescriptor != null) {
                SoftwareCoUtils.VERSION = pluginDescriptor.getVersion();
            } else {
                return "1.1.0";
            }
        }
        return SoftwareCoUtils.VERSION;
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

    public void initComponent() {
        String jwt = FileManager.getItem("jwt");
        if (StringUtils.isBlank(jwt)) {
            // create an app jwt to support spotify auth
            SoftwareCoUtils.getAppJwt();
        }
        initializePlugin();
    }

    protected void initializePlugin() {
        String plugName = getPluginName();

        log.info(plugName + ": Loaded v" + getVersion());

        gson = new Gson();

        log.info(plugName + ": Finished initializing SoftwareCoMusic plugin");

        // check user status every 45 minutes
        final Runnable userStatusRunner = () -> checkUserStatusIfNotRegistered();
        asyncManager.scheduleService(
                userStatusRunner, "userStatusRunner", 60 * 45, 60 * 45);

        initializeUserInfoWhenProjectsReady();
    }

    private void checkUserStatusIfNotRegistered() {
        new Thread(() -> {
            try {
                String email = FileManager.getItem("name");
                if (StringUtils.isBlank(email)) {
                    SoftwareCoUtils.getMusicTimeUserStatus();
                }
            } catch (Exception e) {
                System.err.println(e);
            }
        }).start();
    }

    private void initializeUserInfoWhenProjectsReady() {
        Project p = SoftwareCoUtils.getOpenProject();
        if (p == null) {
            // try again in 5 seconds
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    initializeUserInfoWhenProjectsReady();
                }
            }, 5000);
        } else {
            keystrokeMgr.addKeystrokeWrapperIfNoneExists(p);
            initializeUserInfo();
            setupEventListeners();
            setupFileEditorEventListeners(p);
        }
    }

    private void initializeUserInfo() {
        SoftwareCoUtils.getAndUpdateClientInfo();

        if (!MusicControlManager.hasSpotifyAccess()) {
            // get the user status (jwt, email, spotify, slack) if no access
            // in case this a different computer or it timed out in a previous session
            SoftwareCoUtils.getMusicTimeUserStatus();
        }

        // initialize the tracker
        EventTrackerManager.getInstance().init();
        // send the 1st event: activate
        EventTrackerManager.getInstance().trackEditorAction("editor", "activate");

        boolean hasAccess = MusicControlManager.hasSpotifyAccess();

        if (!hasAccess) {
            SoftwareCoUtils.setStatusLineMessage();
        } else {
            // check to see if we need to re-authenticate
            if (hasExpiredAccessToken()) {
                // disconnect
                MusicControlManager.disConnectSpotify();

                // show message
                showReconnectPrompt();
            } else {

                Apis.getUserProfile();

                PlaylistManager.getUserPlaylists(); // API call
                PlayListCommands.updatePlaylists(0, null);
                PlayListCommands.updatePlaylists(3, null);
                PlayListCommands.getGenre(); // API call
                PlayListCommands.updateRecommendation("category", "Familiar"); // API call
                DeviceManager.getDevices(); // API call

                SoftwareCoUtils.updatePlayerControls(false);
            }
        }

        boolean initializedIntellijMtPlugin = FileManager.getBooleanItem("intellij_MtInit");

        if (!initializedIntellijMtPlugin) {
            log.log(Level.INFO, "Initial launching README file");
            ApplicationManager.getApplication().invokeLater(new Runnable() {
                public void run() {
                    sessionMgr.openReadmeFile();
                }
            });
            FileManager.setBooleanItem("intellij_MtInit", true);
        }
        AsyncManager.getInstance().executeOnceInSeconds(() -> MusicToolWindowFactory.showWindow(), 1);
        AsyncManager.getInstance().executeOnceInSeconds(() -> PlaylistManager.fetchTrack(), 3);
    }

    public static void showReconnectPrompt() {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
                String email = FileManager.getItem("name");
                String infoMsg = "To continue using Music Time, please reconnect your Spotify account (" + email + ").";
                String[] options = new String[] {"Reconnect", "Cancel"};
                int response = Messages.showDialog(infoMsg, SoftwareCoMusic.getPluginName(), options, 0, Messages.getInformationIcon());
                if (response == 0) {
                    MusicControlManager.connectSpotify();
                }
            }
        });
    }

    public static boolean hasExpiredAccessToken() {
        boolean checkedSpotifyAccess = FileManager.getBooleanItem("intellij_checkedSpotifyAccess");
        String accessToken = FileManager.getItem("spotify_access_token");
        if (!checkedSpotifyAccess && StringUtils.isNotBlank(accessToken)) {
            boolean expired = Apis.accessExpired();
            FileManager.setBooleanItem("intellij_checkedSpotifyAccess", true);
            return expired;
        }
        return false;
    }

    // add the document change event listener
    private void setupEventListeners() {
        // listen to editor events if codetime is not installed
        if (!SoftwareCoUtils.isCodeTimeInstalled()) {
            ApplicationManager.getApplication().invokeLater(() -> {
                Disposable disposable = new Disposable() {
                    @Override
                    public void dispose() {

                        // send the activate event
                        EventTrackerManager.getInstance().trackEditorAction("editor", "deactivate");

                        try {
                            if (connection != null) {
                                connection.disconnect();
                            }
                        } catch (Exception e) {
                            log.info("Error disconnecting the software.com plugin, reason: " + e.toString());
                        }

                        asyncManager.destroyServices();
                    }
                };
                // edit document
                EditorFactory.getInstance().getEventMulticaster().addDocumentListener(
                        new SoftwareCoDocumentListener(), disposable);
            });
        }
    }

    // add the file selection change event listener
    private void setupFileEditorEventListeners(Project p) {
        // listen to editor events if codetime is not installed
        if (!SoftwareCoUtils.isCodeTimeInstalled()) {
            ApplicationManager.getApplication().invokeLater(() -> {
                // file open,close,selection listener
                p.getMessageBus().connect().subscribe(
                        FileEditorManagerListener.FILE_EDITOR_MANAGER, new SoftwareCoFileEditorListener());
            });
        }
    }

}
