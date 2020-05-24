package com.musictime.intellij.plugin;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.messages.MessageBusConnection;
import com.musictime.intellij.plugin.fs.FileManager;
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
    public static JsonParser jsonParser = new JsonParser();
    public static final Logger log = Logger.getLogger("SoftwareCoMusic");
    public static Gson gson;

    public static MessageBusConnection connection;

    private SoftwareCoMusicManager musicMgr = SoftwareCoMusicManager.getInstance();
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
        boolean serverIsOnline = SoftwareCoSessionManager.isServerOnline();
        boolean musicFileExist = SoftwareCoSessionManager.musicDataFileExists();
        if(musicFileExist) {
            String musicFile = FileManager.getMusicDataFile(false);
            SoftwareCoSessionManager.deleteFile(musicFile);
        }
        boolean readmeExist = SoftwareCoSessionManager.readmeFileExists();
        if(readmeExist) {
            String readmeFile = FileManager.getReadmeFile(false);
            SoftwareCoSessionManager.deleteFile(readmeFile);
        }
        boolean sessionFileExists = SoftwareCoSessionManager.softwareSessionFileExists();
        String jwt = FileManager.getItem("jwt");
        if (!sessionFileExists || StringUtils.isBlank(jwt)) {
            if (!serverIsOnline) {
                // server isn't online, check again in 1 min
                if (retry_counter == 0) {
                    SoftwareCoUtils.showOfflinePrompt();
                }
                new Thread(() -> {
                    try {
                        Thread.sleep(check_online_interval_ms);
                        initComponent();
                    } catch (Exception e) {
                        System.err.println(e);
                    }
                }).start();
            } else {
                getPluginName();

                if (StringUtils.isBlank(jwt)) {
                    SoftwareCoUtils.getAppJwt(serverIsOnline);
                    initializePlugin(true);
                } else {
                    initializePlugin(false);
                }
            }
        } else {
            // session json already exists, continue with plugin init
            initializePlugin(false);
        }
    }

    protected void initializePlugin(boolean initializedUser) {
        String plugName = getPluginName();

        log.info(plugName + ": Loaded v" + getVersion());

        gson = new Gson();

        log.info(plugName + ": Finished initializing SoftwareCoMusic plugin");

        // check user status every 45 minutes
        final Runnable userStatusRunner = () -> checkUserStatusIfNotRegistered();
        asyncManager.scheduleService(
                userStatusRunner, "userStatusRunner", 60 * 45, 60 * 45);

        initializeUserInfoWhenProjectsReady(initializedUser);
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

    private void initializeUserInfoWhenProjectsReady(boolean initializedUser) {
        Project p = SoftwareCoUtils.getOpenProject();
        if (p == null) {
            // try again in 4 seconds
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    initializeUserInfoWhenProjectsReady(initializedUser);
                }
            }, 4000);
        } else {
            keystrokeMgr.addKeystrokeWrapperIfNoneExists(p);
            initializeUserInfo(initializedUser);
            setupEventListeners();
            setupFileEditorEventListeners(p);
        }
    }

    private void initializeUserInfo(boolean initializedUser) {
        SoftwareCoUtils.getAndUpdateClientInfo();

        // get the user status (jwt, email, spotify, slack)
        SoftwareCoUtils.getMusicTimeUserStatus();

        // create the 15 minute interval timer to send code time payloads if code time is NOT installed
        if (!SoftwareCoUtils.isCodeTimeInstalled()) {
            // every 15 minutes
            final Runnable sendOfflineDataRunner = () -> this.sendOfflineDataRunner();
            asyncManager.scheduleService(sendOfflineDataRunner, "offlineDataRunner", 2, 60 * 15);
        }

        if (!MusicControlManager.hasSpotifyAccess()) {
            SoftwareCoUtils.setStatusLineMessage();
        }
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

        if (initializedUser) {
            log.log(Level.INFO, "Initial launching README file");
            ApplicationManager.getApplication().invokeLater(new Runnable() {
                public void run() {
                    sessionMgr.openReadmeFile();
                }
            });
        }

        // initiate gather music info
        initiateGatherMusicInfo();

        SoftwareCoUtils.sendHeartbeat("INITIALIZED");
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

    private void initiateGatherMusicInfo() {
        // fetch currently playing track every 20 seconds
        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                PlaylistManager.gatherMusicInfo();
            }
        }, 5000, SoftwareCoUtils.SONG_FETCH_INTERVAL_MILLIS);

        // a timer to check if the song is close to ending and will call gather music info once the song should end
        // every 5 seconds
        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                PlaylistManager.trackEndCheck();
            }
        }, 10000, 5000);
    }

    public static String getRootPath() {
        Project[] projects = ProjectManager.getInstance().getOpenProjects();
        if (projects != null && projects.length > 0) {
            return projects[0].getBasePath();
        }
        return null;
    }

    private void sendOfflineDataRunner() {
        new Thread(() -> {

            try {
                SoftwareCoSessionManager.getInstance().sendOfflineData();
            } catch (Exception e) {
                System.err.println(e);
            }
        }).start();
    }

    // add the document change event listener
    private void setupEventListeners() {
        ApplicationManager.getApplication().invokeLater(() -> {
            // edit document
            EditorFactory.getInstance().getEventMulticaster().addDocumentListener(
                    new SoftwareCoDocumentListener(), this::disposeComponent);
        });
    }

    // add the file selection change event listener
    private void setupFileEditorEventListeners(Project p) {
        ApplicationManager.getApplication().invokeLater(() -> {
            // file open,close,selection listener
            p.getMessageBus().connect().subscribe(
                    FileEditorManagerListener.FILE_EDITOR_MANAGER, new SoftwareCoFileEditorListener());
        });
    }

    public void disposeComponent() {
        try {
            if (connection != null) {
                connection.disconnect();
            }
        } catch(Exception e) {
            log.info("Error disconnecting the software.com plugin, reason: " + e.toString());
        }

        asyncManager.destroyServices();
    }
}
