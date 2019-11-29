package com.softwareco.intellij.plugin;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.MessageBusConnection;

import java.util.logging.Logger;

public class SoftwareCoMusic implements ApplicationComponent {
    public static JsonParser jsonParser = new JsonParser();
    public static final Logger log = Logger.getLogger("SoftwareCoMusic");
    public static Gson gson;

    public static MessageBusConnection connection;


    private SoftwareCoMusicManager musicMgr = SoftwareCoMusicManager.getInstance();
    private SoftwareCoSessionManager sessionMgr = SoftwareCoSessionManager.getInstance();
    private SoftwareCoEventManager eventMgr = SoftwareCoEventManager.getInstance();
    private AsyncManager asyncManager = AsyncManager.getInstance();

    private static int retry_counter = 0;
    private static long check_online_interval_ms = 1000 * 60 * 10;

    public static String rootPath;

    public SoftwareCoMusic() {
    }

    public static String getVersion() {
        if (SoftwareCoUtils.VERSION == null) {
            IdeaPluginDescriptor pluginDescriptor = PluginManager.getPlugin(PluginId.getId("com.softwareco.intellij.plugin.musictime"));

            SoftwareCoUtils.VERSION = pluginDescriptor.getVersion();
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

    public void initComponent() {
        boolean serverIsOnline = SoftwareCoSessionManager.isServerOnline();
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
                boolean music = SoftwareCoUtils.isMusicTime();
                String jwt = null;
                if(!music) {
                    // create the anon user
                    jwt = SoftwareCoUtils.createAnonymousUser(serverIsOnline);
                } else {
                    jwt = SoftwareCoUtils.getAppJwt(serverIsOnline);
                    SoftwareCoUtils.jwt = jwt;
                }
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

        log.info(plugName + ": Finished initializing SoftwareCoMusic plugin");

//        final Runnable hourlyRunner = () -> this.processHourlyJobs();
//        asyncManager.scheduleService(
//                hourlyRunner, "hourlyJobsRunner", 45, 60 * 60);

        // run the music manager task every 15 seconds
        final Runnable musicTrackRunner = () -> musicMgr.processMusicTrackInfo();
        asyncManager.scheduleService(
                musicTrackRunner, "musicTrackRunner", 30, 15);

        // check user status every 3 minute
        final Runnable userStatusRunner = () -> SoftwareCoUtils.getUserStatus();
        asyncManager.scheduleService(
                userStatusRunner, "userStatusRunner", 60, 60 * 3);

        eventMgr.setAppIsReady(true);

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
            initializeUserInfo(initializedUser);
        }
    }

    private void initializeUserInfo(boolean initializedUser) {

        SoftwareCoUtils.getUserStatus();
        String userStatus = null;

        if(!SoftwareCoUtils.isSpotifyConncted()) {
            String headPhoneIcon = "headphone.png";
            SoftwareCoUtils.setStatusLineMessage(headPhoneIcon, "Connect Spotify", "Connect Spotify");
        } else {
            SoftwareCoUtils.getUserProfile();
            //SoftwareCoUtils.launchPlayer();
            SoftwareCoUtils.lazyUpdatePlayer();
        }

        SoftwareCoUtils.sendHeartbeat("INITIALIZED");
    }

    protected void sendInstallPayload() {
        KeystrokeManager keystrokeManager = KeystrokeManager.getInstance();
        String fileName = "Untitled";
        eventMgr.initializeKeystrokeObjectGraph(fileName, "Unnamed", "");
        KeystrokeCount.FileInfo fileInfo = keystrokeManager.getKeystrokeCount().getSourceByFileName(fileName);
        fileInfo.setAdd(fileInfo.getAdd() + 1);
        fileInfo.setNetkeys(fileInfo.getAdd() - fileInfo.getDelete());
        keystrokeManager.getKeystrokeCount().setKeystrokes(String.valueOf(1));
        keystrokeManager.getKeystrokeCount().processKeystrokes();
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
