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
import com.musictime.intellij.plugin.tree.MusicToolWindow;
import com.musictime.intellij.plugin.tree.MusicToolWindowFactory;
import com.musictime.intellij.plugin.managers.EventTrackerManager;
import com.musictime.intellij.plugin.music.MusicControlManager;
import com.musictime.intellij.plugin.music.PlayListCommands;
import com.musictime.intellij.plugin.music.PlaylistManager;
import com.musictime.intellij.plugin.musicjava.Apis;
import com.musictime.intellij.plugin.musicjava.DeviceManager;
import com.musictime.intellij.plugin.tree.PlaylistAction;
import org.apache.commons.lang.StringUtils;
import swdc.java.ops.event.SlackStateChangeModel;
import swdc.java.ops.event.SlackStateChangeObserver;
import swdc.java.ops.event.UserStateChangeModel;
import swdc.java.ops.event.UserStateChangeObserver;
import swdc.java.ops.manager.AccountManager;
import swdc.java.ops.manager.AsyncManager;
import swdc.java.ops.manager.ConfigManager;
import swdc.java.ops.manager.FileUtilManager;

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

    private UserStateChangeObserver userStateChangeObserver;
    private SlackStateChangeObserver slackStateChangeObserver;

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

    public void initComponent() {
        ConfigManager.init(
                SoftwareCoUtils.api_endpoint,
                SoftwareCoUtils.launch_url,
                SoftwareCoUtils.pluginId,
                SoftwareCoUtils.getPluginName(),
                SoftwareCoUtils.getVersion(),
                SoftwareCoUtils.IDE_NAME,
                SoftwareCoUtils.IDE_VERSION);

        String jwt = FileUtilManager.getItem("jwt");
        if (StringUtils.isBlank(jwt)) {
            // create an anon user to allow our spotify strategy to update the anon
            // user to registered once the user has connected via spotify
            AccountManager.createAnonymousUser(false);
        }
        initializePlugin();
    }

    protected void initializePlugin() {
        String plugName = SoftwareCoUtils.getPluginName();

        log.info(plugName + ": Loaded v" + SoftwareCoUtils.getVersion());

        gson = new Gson();

        log.info(plugName + ": Finished initializing SoftwareCoMusic plugin");

        initializeUserInfoWhenProjectsReady();
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
            setupOpsListeners();
            setupFileEditorEventListeners(p);
        }
    }

    private void initializeUserInfo() {
        SoftwareCoUtils.getAndUpdateClientInfo();

        // initialize the tracker
        EventTrackerManager.getInstance().init();
        // send the 1st event: activate
        EventTrackerManager.getInstance().trackEditorAction("editor", "activate");

        MusicControlManager.migrateSpotifyAccessInfo();

        boolean hasAccess = MusicControlManager.hasSpotifyAccess();

        if (!hasAccess) {
            SoftwareCoUtils.setStatusLineMessage();
        } else {
            Apis.getUserProfile();

            PlaylistManager.getUserPlaylists(); // API call
            PlayListCommands.updatePlaylists(PlaylistAction.GET_ALL_PLAYLISTS, null);
            PlayListCommands.updatePlaylists(PlaylistAction.UPDATE_LIKED_SONGS, null);
            PlayListCommands.getGenre(); // API call
            PlayListCommands.updateRecommendation("category", "Familiar"); // API call
            DeviceManager.getDevices(); // API call

            SoftwareCoUtils.updatePlayerControls(false);
        }

        boolean initializedIntellijMtPlugin = FileUtilManager.getBooleanItem("intellij_MtInit");

        if (!initializedIntellijMtPlugin) {
            log.log(Level.INFO, "Initial launching README file");
            ApplicationManager.getApplication().invokeLater(new Runnable() {
                public void run() {
                    sessionMgr.openReadmeFile();
                }
            });
            FileUtilManager.setBooleanItem("intellij_MtInit", true);
        }
        AsyncManager.getInstance().executeOnceInSeconds(() -> MusicToolWindowFactory.showWindow(), 1);
        AsyncManager.getInstance().executeOnceInSeconds(() -> PlaylistManager.fetchTrack(), 3);
    }

    public static void showReconnectPrompt() {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
                long one_day_millis = 1000 * 60 * 60 * 24;
                long lastReconnectPromptTime = FileUtilManager.getNumericItem("lastMtReconnectPromptTime", 0);
                if (lastReconnectPromptTime == 0 || System.currentTimeMillis() - lastReconnectPromptTime > one_day_millis) {
                    String email = FileUtilManager.getItem("name");
                    String infoMsg = "To continue using Music Time, please reconnect your Spotify account (" + email + ").";
                    String[] options = new String[]{"Reconnect", "Cancel"};
                    int response = Messages.showDialog(infoMsg, SoftwareCoUtils.getPluginName(), options, 0, Messages.getInformationIcon());
                    if (response == 0) {
                        MusicControlManager.connectSpotify();
                    }
                }
            }
        });
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

    private void setupOpsListeners() {
        if (userStateChangeObserver == null) {
            userStateChangeObserver = new UserStateChangeObserver(new UserStateChangeModel(), () -> {
                MusicToolWindow.refresh();
            });
        }
        if (slackStateChangeObserver == null) {
            slackStateChangeObserver = new SlackStateChangeObserver(new SlackStateChangeModel(), () -> {
                MusicToolWindow.refresh();
            });
        }
    }

}
