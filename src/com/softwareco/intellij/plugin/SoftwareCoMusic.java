package com.softwareco.intellij.plugin;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import com.softwareco.intellij.plugin.music.MusicControlManager;
import com.softwareco.intellij.plugin.music.PlayListCommands;
import com.softwareco.intellij.plugin.music.PlaylistManager;

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
    private static long check_online_interval_ms = 1000 * 60 * 10;

    public static String rootPath;

    public SoftwareCoMusic() {
    }

    public static String getVersion() {
        if (SoftwareCoUtils.VERSION == null) {
            IdeaPluginDescriptor pluginDescriptor = PluginManager.getPlugin(PluginId.getId("com.softwareco.intellij.plugin.musictime"));

            SoftwareCoUtils.VERSION = pluginDescriptor.getVersion();
            //log.log(Level.INFO, "Root Path: " + pluginDescriptor.getPath().getAbsolutePath());
            SoftwareCoSessionManager.pluginRootPath = pluginDescriptor.getPath().getAbsolutePath();
        }
        return SoftwareCoUtils.VERSION;
    }

    public static String getPluginName() {
        if (SoftwareCoUtils.pluginName == null) {
            IdeaPluginDescriptor pluginDescriptor = PluginManager.getPlugin(PluginId.getId("com.softwareco.intellij.plugin.musictime"));

            SoftwareCoUtils.pluginName = pluginDescriptor.getName();
        }
        return SoftwareCoUtils.pluginName;
    }

    public static boolean getCodeTimePluginState() {
        IdeaPluginDescriptor pluginDescriptor = PluginManager.getPlugin(PluginId.getId("com.softwareco.intellij.plugin"));
        if(pluginDescriptor != null) {
            return true;
        }
        return false;
    }

    public void initComponent() {
        boolean serverIsOnline = SoftwareCoSessionManager.isServerOnline();
        SoftwareCoSessionManager.isServerActive = serverIsOnline;
        boolean musicFileExist = SoftwareCoSessionManager.musicDataFileExists();
        if(musicFileExist) {
            String musicFile = SoftwareCoSessionManager.getMusicDataFile(false);
            SoftwareCoSessionManager.deleteFile(musicFile);
        }
        boolean sessionFileExists = SoftwareCoSessionManager.softwareSessionFileExists();
        boolean jwtExists = SoftwareCoSessionManager.jwtExists();
        if (!sessionFileExists || !jwtExists) {
            if (!serverIsOnline) {
                // server isn't online, check again in 10 min
                if (retry_counter == 0) {
                    SoftwareCoUtils.showOfflinePrompt(true);
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

                String jwt = SoftwareCoUtils.getAppJwt(serverIsOnline);
                SoftwareCoUtils.jwt = jwt;

                if (jwt == null) {
                    // it failed, try again later
                    if (retry_counter == 0) {
                        SoftwareCoUtils.showOfflinePrompt(true);
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
                    initializePlugin(true);
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

        setupEventListeners();

        log.info(plugName + ": Finished initializing SoftwareCoMusic plugin");

        // check user status every 3 minute
        final Runnable userStatusRunner = () -> SoftwareCoUtils.getUserStatus();
        asyncManager.scheduleService(
                userStatusRunner, "userStatusRunner", 60, 60 * 3);

        if(!getCodeTimePluginState()) {
            // every 30 minutes
            final Runnable sendOfflineDataRunner = () -> this.sendOfflineDataRunner();
            asyncManager.scheduleService(sendOfflineDataRunner, "offlineDataRunner", 2, 60 * 30);
        }


        initializeUserInfoWhenProjectsReady(initializedUser);

    }

    private void initializeUserInfoWhenProjectsReady(boolean initializedUser) {
        Project p = SoftwareCoUtils.getOpenProject();
        if (p == null) {
            new Thread(() -> {
                try {
                    Thread.sleep(2000);
                    initializeUserInfoWhenProjectsReady(initializedUser);
                } catch (Exception e) {
                    System.err.println(e);
                }
            }).start();
        } else {
            keystrokeMgr.addKeystrokeWrapperIfNoneExists(p);
            initializeUserInfo(initializedUser);
        }
    }

    private void initializeUserInfo(boolean initializedUser) {
        if(SoftwareCoUtils.jwt != null)
            SoftwareCoUtils.getUserDetails(true);

        SoftwareCoUtils.getUserStatus();

        if(!SoftwareCoUtils.isSpotifyConncted()) {
            String headPhoneIcon = "headphone.png";
            SoftwareCoUtils.setStatusLineMessage(headPhoneIcon, "Connect Spotify", "Connect Spotify");
        } else {
            JsonObject obj = MusicControlManager.getUserProfile();
            if (obj != null)
                MusicControlManager.userStatus = obj.get("product").getAsString();

            PlaylistManager.getUserPlaylists(); // API call
            PlayListCommands.updatePlaylists(3, null);
            PlayListCommands.getGenre(); // API call
            PlayListCommands.updateRecommendation("category", "Familiar"); // API call
            MusicControlManager.getSpotifyDevices(); // API call

            MusicControlManager.lazyUpdatePlayer();
        }

        SoftwareCoUtils.sendHeartbeat("INITIALIZED");
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

    private void setupEventListeners() {
        ApplicationManager.getApplication().invokeLater(() -> {

            // save file
            MessageBus bus = ApplicationManager.getApplication().getMessageBus();
            connection = bus.connect();
            connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new SoftwareCoFileEditorListener());

            // edit document
            EditorFactory.getInstance().getEventMulticaster().addDocumentListener(new SoftwareCoDocumentListener());
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
