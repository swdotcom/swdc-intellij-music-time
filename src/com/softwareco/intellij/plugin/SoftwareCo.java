/**
 * Copyright (c) 2018 by Software.com
 * All rights reserved
 */
package com.softwareco.intellij.plugin;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;

import java.util.logging.Logger;


/**
 * Intellij Plugin Application
 * ....
 */
public class SoftwareCo implements ApplicationComponent {

    public static JsonParser jsonParser = new JsonParser();
    public static final Logger log = Logger.getLogger("SoftwareCo");
    public static Gson gson;

    public static MessageBusConnection connection;


    private SoftwareCoMusicManager musicMgr = SoftwareCoMusicManager.getInstance();
    private SoftwareCoSessionManager sessionMgr = SoftwareCoSessionManager.getInstance();
    private SoftwareCoEventManager eventMgr = SoftwareCoEventManager.getInstance();
    private AsyncManager asyncManager = AsyncManager.getInstance();

    private static int retry_counter = 0;
    private static long check_online_interval_ms = 1000 * 60 * 10;

    public SoftwareCo() {
    }

    public static String getVersion() {
        if (SoftwareCoUtils.VERSION == null) {
            IdeaPluginDescriptor pluginDescriptor = PluginManager.getPlugin(PluginId.getId("com.softwareco.intellij.plugin"));

            SoftwareCoUtils.VERSION = pluginDescriptor.getVersion();
        }
        return SoftwareCoUtils.VERSION;
    }

    public static String getPluginName() {
        if (SoftwareCoUtils.pluginName == null) {
            IdeaPluginDescriptor pluginDescriptor = PluginManager.getPlugin(PluginId.getId("com.softwareco.intellij.plugin"));

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

        setupEventListeners();

        log.info(plugName + ": Finished initializing SoftwareCo plugin");

        // add the kpm status scheduler
        final Runnable kpmStatusRunner = () -> sessionMgr.fetchDailyKpmSessionInfo();
        asyncManager.scheduleService(
                kpmStatusRunner, "kpmStatusRunner", 15, 60 * 5);

        final Runnable hourlyRunner = () -> this.processHourlyJobs();
        asyncManager.scheduleService(
                hourlyRunner, "hourlyJobsRunner", 45, 60 * 60);

        final Runnable userStatusRunner = () -> SoftwareCoUtils.getUserStatus();
        asyncManager.scheduleService(
                userStatusRunner, "userStatusRunner", 60, 60 * 3);

        // every 30 minutes
        final Runnable sendOfflineDataRunner = () -> this.sendOfflineDataRunner();
        asyncManager.scheduleService(sendOfflineDataRunner, "offlineDataRunner", 2, 60 * 30);

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

    private void sendOfflineDataRunner() {
        new Thread(() -> {

            try {
                SoftwareCoSessionManager.getInstance().sendOfflineData();
            } catch (Exception e) {
                System.err.println(e);
            }
        }).start();
    }

    private void processHourlyJobs() {
        SoftwareCoUtils.sendHeartbeat("HOURLY");

        SoftwareCoRepoManager repoMgr = SoftwareCoRepoManager.getInstance();
        new Thread(() -> {
            try {
                Thread.sleep(60000);
                repoMgr.getHistoricalCommits(getRootPath());
            } catch (Exception e) {
                System.err.println(e);
            }
        }).start();
    }

    private void initializeUserInfo(boolean initializedUser) {

        SoftwareCoUtils.getUserStatus();

        if (initializedUser) {
            // send an initial plugin payload
            this.sendInstallPayload();

            // ask the user to login one time only
            new Thread(() -> {
                try {
                    Thread.sleep(5000);
                    sessionMgr.showLoginPrompt();
                }
                catch (Exception e){
                    System.err.println(e);
                }
            }).start();
        }

        new Thread(() -> {
            try {
                sessionMgr.fetchDailyKpmSessionInfo();
            }
            catch (Exception e){
                System.err.println(e);
            }
        }).start();

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

    protected String getRootPath() {
        Editor[] editors = EditorFactory.getInstance().getAllEditors();
        if (editors != null && editors.length > 0) {
            for (Editor editor : editors) {
                Project project = editor.getProject();
                if (project != null && project.getBasePath() != null) {
                    return project.getBasePath();
                }
            }
        }
        return null;
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

        if(SoftwareCoUtils.pluginName.equals("Code Time"))
            SoftwareCoUtils.setStatusLineMessage(
                "Code Time", "Click to see more from Code Time");
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

        // process one last time
        // this will ensure we process the latest keystroke updates
        KeystrokeManager keystrokeManager = KeystrokeManager.getInstance();
        if (keystrokeManager.getKeystrokeCount() != null) {
            keystrokeManager.getKeystrokeCount().processKeystrokes();
        }
    }


}