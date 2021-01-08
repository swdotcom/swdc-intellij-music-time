package com.musictime.intellij.plugin.tree;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.components.JBList;
import com.intellij.uiDesigner.core.GridConstraints;
import com.musictime.intellij.plugin.SoftwareCoSessionManager;
import com.musictime.intellij.plugin.SoftwareCoUtils;
import com.musictime.intellij.plugin.models.DeviceInfo;
import com.musictime.intellij.plugin.music.*;
import com.musictime.intellij.plugin.musicjava.Apis;
import com.musictime.intellij.plugin.musicjava.DeviceManager;
import org.apache.commons.lang.StringUtils;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.logging.Logger;

public class MusicToolWindow {

    public static final Logger LOG = Logger.getLogger("MusicToolWindow");

    private JPanel playlistWindowContent;
    private JScrollPane scrollPane;
    private JPanel dataPanel;
    private JLabel refresh;
    private JLabel menu;
    private JPanel recommendPanel;
    private JLabel spotifyState;
    private JLabel spotifyConnect;
    private JScrollPane recommendScroll;
    private JLabel category;
    private JLabel genre;
    private JLabel songSearch;
    private JLabel recommendRefresh;
    private JLabel recommendationHeader;

    private static MusicToolWindow win;
    private static boolean refreshing = false;
    private static int listIndex = 0;
    private static int refreshButtonState = 0;
    private static int recommendRefreshState = 0;
    private static int refreshAIButtonState = 0;
    private static int createPlaylistButtonState = 0;
    private static int counter = 0;
    public static String[] rec_categories = {"Familiar", "Happy", "Energetic", "Danceable", "Instrumental", "Quiet music"};
    public static String[] rec_genres;

    public MusicToolWindow(ToolWindow toolWindow) {
        playlistWindowContent.setFocusable(true);

        refresh.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                boolean hasSpotifyAccess = MusicControlManager.hasSpotifyAccess();
                if (refreshButtonState == 0) {
                    refreshButtonState = 1;
                    if (hasSpotifyAccess) {
                        Set<String> keys = TreeHelper.playlistTreeMap.keySet();
                        for (String key : keys) {
                            if (!key.equals(PlayListCommands.recommendedPlaylistId)) {
                                PlaylistTree tree = TreeHelper.playlistTreeMap.get(key);
                                tree.setExpandedState(tree.getPathForRow(0), false);
                            }
                        }
                        DeviceManager.getDevices(); // API call
                        PlayListCommands.updatePlaylists(PlaylistAction.GET_ALL_PLAYLISTS, null);
                    } else {
                        refreshButton();
                    }
                    SoftwareCoUtils.showMsgPrompt("Your playlists were refreshed successfully", new Color(55, 108, 137, 100));
                    new Thread(() -> {
                        try {
                            Thread.sleep(1000);
                            refreshButtonState = 0;
                        } catch (Exception ex) {
                            System.err.println(ex);
                        }
                    }).start();
                }
            }
        });
        Icon refreshIcon = IconLoader.getIcon("/com/musictime/intellij/plugin/assets/refresh.png");
        refresh.setIcon(refreshIcon);

        // Sorting menu ********************************************************
        Icon menuIcon = IconLoader.getIcon("/com/musictime/intellij/plugin/assets/sort.png");
        menu.setIcon(menuIcon);


        menu.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                JMenuItem sort1 = new JMenuItem("Sort A-Z");
                sort1.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        PlayListCommands.sortAtoZ();
                    }
                });
                JMenuItem sort2 = new JMenuItem("Sort Latest");
                sort2.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        PlayListCommands.sortLatest();
                    }
                });
                JPopupMenu popupMenu = new JPopupMenu();
                popupMenu.add(sort1);
                popupMenu.add(sort2);
                super.mouseClicked(e);
                popupMenu.show(e.getComponent(), e.getX(), e.getY());
            }
        });
        // End ******************************************************************

        this.rebuildPlaylistTreeView();

        scrollPane.setMinimumSize(new Dimension(-1, 235));
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(16);
        scrollPane.repaint();

        //**********************************************************************************************************************
        /* Recommended Tool Window */
        /* Add Categories */
        category.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                JPopupMenu menu = PopupMenuBuilder.buildCategoryFilter(rec_categories);
                menu.show(e.getComponent(), e.getX(), e.getY());
            }
        });

        /* Add Genres */
        Icon filterIcon = IconLoader.getIcon("/com/musictime/intellij/plugin/assets/filter.png");
        updateGenres();
        genre.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                if (rec_genres.length == 0) {
                    updateGenres();
                }
                if (rec_genres.length > 0) {
                    String value = SoftwareCoUtils.showMsgInputPrompt("Select genre", "Spotify", filterIcon, rec_genres);
                    if (StringUtils.isNotBlank(value)) {
                        PopupMenuBuilder.selectedType = "genre";
                        PopupMenuBuilder.selectedValue = value;
                        PlayListCommands.updateRecommendation(PopupMenuBuilder.selectedType, PopupMenuBuilder.selectedValue);
                    }
                }
            }
        });

        songSearch.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                Icon spotifyIcon = IconLoader.getIcon("/com/musictime/intellij/plugin/assets/spotify.png");
                String keywords = SoftwareCoUtils.showInputPrompt("Search for songs", "Spotify", spotifyIcon);
                if (StringUtils.isNotBlank(keywords)) {
                    keywords = keywords.trim();
                    JsonArray result = Apis.searchSpotify(keywords);
                    if (result != null && result.size() > 0) {
                        // add these to the recommendation list
                        JsonObject obj = new JsonObject();
                        obj.add("tracks", result);
                        PlayListCommands.recommendedTracks = obj;
                        PlayListCommands.recommendationTitle = "Top results";
                        PlayListCommands.updateSearchedSongsRecommendations();
                        MusicToolWindow.refresh();
                    }
                }
            }
        });

        /* Refresh */
        recommendRefresh.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                boolean hasSpotifyAccess = MusicControlManager.hasSpotifyAccess();
                super.mouseClicked(e);
                if (recommendRefreshState == 0) {
                    recommendRefreshState = 1;
                    if (hasSpotifyAccess) {
                        if (PlayListCommands.currentBatch < 10) {
                            PlayListCommands.currentBatch += 1;
                        } else {
                            PlayListCommands.currentBatch = 1;
                        }
                    }
                    refresh();
                    new Thread(() -> {
                        try {
                            Thread.sleep(1000);
                            recommendRefreshState = 0;
                        } catch (Exception ex) {
                            System.err.println(ex);
                        }
                    }).start();
                }
            }
        });
        recommendRefresh.setIcon(refreshIcon);

        this.rebuildRecommendedTreeView();

        playlistWindowContent.setBackground((Color) null);

        scrollPane.updateUI();
        scrollPane.setVisible(true);
        scrollPane.revalidate();

        updateRecommendVisibility();

        playlistWindowContent.updateUI();
        playlistWindowContent.setVisible(true);
        playlistWindowContent.revalidate();

        win = this;
    }

    public static void updateGenres() {
        int index = 0;
        rec_genres = new String[PlayListCommands.genres.size()];
        for (String gen : PlayListCommands.genres) {
            rec_genres[index] = gen;
            index++;
        }
    }

    public static void refresh() {
        if (refreshing) {
            return;
        }
        refreshing = true;

        if (win != null) {
            ApplicationManager.getApplication().invokeLater(new Runnable() {
                public void run() {
                    win.rebuildRecommendedTreeView();
                    win.rebuildPlaylistTreeView();
                }
            });
        }
        refreshing = false;
    }

    public static void reset() {
        TreeHelper.playlistTreeMap.clear();
    }

    public synchronized void rebuildPlaylistTreeView() {
        // Get VSpacer component
        Component component = dataPanel.getComponent(dataPanel.getComponentCount() - 1);
        boolean hasSpotifyAccess = MusicControlManager.hasSpotifyAccess();
        if (!hasSpotifyAccess) {
            dataPanel.removeAll();
            menu.setVisible(false);
            refresh.setVisible(false);
            DefaultListModel listModel = new DefaultListModel();

            boolean requiresReAuth = MusicControlManager.requiresReAuthentication();
            String connectLabel = requiresReAuth ? "Reconnect Spotify" : "Connect Spotify";
            Icon icon = IconLoader.getIcon("/com/musictime/intellij/plugin/assets/spotify.png");
            JLabel connectedState = new JLabel();
            connectedState.setText(connectLabel);
            connectedState.setIcon(icon);
            connectedState.setOpaque(true);

            listModel.add(0, connectedState);
            JList<JLabel> actionList = new JList<>(listModel);
            actionList.setVisibleRowCount(1);
            actionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            actionList.setCellRenderer(new ListRenderer());
            actionList.addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    super.mouseMoved(e);
                    int row = actionList.locationToIndex(e.getPoint());
                    actionList.setSelectedIndex(row);
                }
            });
            actionList.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    super.mouseClicked(e);

                    JList list = (JList) e.getSource();
                    JLabel label = (JLabel) list.getSelectedValue();
                    if (label != null && label.getText() != null && label.getText().equals(connectLabel)) {
                        MusicControlManager.connectSpotify();
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    super.mouseExited(e);
                    JList list = (JList) e.getSource();
                    list.clearSelection();
                }
            });
            actionList.updateUI();
            actionList.setBackground((Color) null);
            dataPanel.add(actionList, gridConstraints(dataPanel.getComponentCount(), 1, 2, 0, 3, 0));

            // Add VSpacer at last
            dataPanel.add(component, gridConstraints(dataPanel.getComponentCount(), 6, 1, 0, 2, 0));

            dataPanel.updateUI();
            dataPanel.setVisible(true);

            playlistWindowContent.updateUI();
            playlistWindowContent.setVisible(true);
            playlistWindowContent.revalidate();

        } else {
            //Rectangle rect = dataPanel.getBounds();
            dataPanel.removeAll();
            dataPanel.setBackground((Color) null);
            dataPanel.setFocusable(true);
            menu.setVisible(true);
            refresh.setVisible(true);
            listIndex = 0;

            DefaultListModel listModel = new DefaultListModel();

            /* Open dashboard */
            Icon dashboardIcon = IconLoader.getIcon("/com/musictime/intellij/plugin/assets/dashboard.png");
            JLabel openDashboard = new JLabel();
            openDashboard.setIcon(dashboardIcon);
            openDashboard.setText("Dashboard");
            openDashboard.setToolTipText("View your latest music matrix right here in your editor");
            listModel.add(listIndex, openDashboard);
            listIndex++;

            /* Web analytics */
            Icon pawIcon = IconLoader.getIcon("/com/musictime/intellij/plugin/assets/paw.png");
            JLabel webAnalytics = new JLabel();
            webAnalytics.setIcon(pawIcon);
            webAnalytics.setText("See web analytics");
            webAnalytics.setToolTipText("See music analytics in the web app");
            listModel.add(listIndex, webAnalytics);
            listIndex++;

            /* Learn more */
            Icon readmeIcon = IconLoader.getIcon("/com/musictime/intellij/plugin/assets/readme.png");
            JLabel learnMore = new JLabel();
            learnMore.setIcon(readmeIcon);
            learnMore.setText("Learn more");
            learnMore.setToolTipText("View the Music Time Readme to learn more");
            listModel.add(listIndex, learnMore);
            listIndex++;

            /* Device section */
            Icon spotifyIcon = IconLoader.getIcon("/com/musictime/intellij/plugin/assets/spotify.png");
            JLabel deviceState = new JLabel();
            deviceState.setIcon(spotifyIcon);
            DeviceInfo currentDevice = DeviceManager.getBestDeviceOption();
            List<DeviceInfo> deviceInfoList = DeviceManager.getDevices();
            if (deviceInfoList != null && deviceInfoList.size() > 0) {
                if (currentDevice != null) {
                    if (currentDevice.is_active) {
                        deviceState.setText("Listening on " + currentDevice.name);
                        deviceState.setToolTipText("Listening on a Spotify device");
                    } else {
                        deviceState.setText("Available on " + currentDevice.name);
                        deviceState.setToolTipText("Available on a Spotify device");
                    }
                } else {
                    deviceState.setText("Available Spotify devices");
                    deviceState.setToolTipText("Available Spotify devices");
                }

            } else {
                deviceState.setText("Connect to a Spotify device");
                deviceState.setToolTipText("Connect to a Spotify device");
            }
            listModel.add(listIndex, deviceState);
            listIndex++;

            JList<JLabel> actionList = new JBList<>(listModel);
            actionList.setVisibleRowCount(listIndex);
            actionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            actionList.setCellRenderer(new ListRenderer());
            actionList.addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    super.mouseMoved(e);
                    int row = actionList.locationToIndex(e.getPoint());
                    actionList.setSelectedIndex(row);
                }
            });
            actionList.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    super.mouseClicked(e);

                    JList list = (JList) e.getSource();
                    JLabel label = (JLabel) list.getSelectedValue();
                    if (label != null && label.getText() != null) {
                        if (label.getText().equals("See web analytics")) {
                            //Code to call web analytics
                            SoftwareCoUtils.launchMusicWebDashboard();
                        } else if (label.getText().equals("Dashboard")) {
                            //Code to open web dashboard
                            SoftwareCoSessionManager.launchMusicTimeMetricsDashboard();
                        } else if (label.getText().equals("Learn more")) {
                            SoftwareCoSessionManager.getInstance().openReadmeFile();
                        } else if (label.getText().contains("Listening on") ||
                                label.getText().contains("Connect to") ||
                                label.getText().contains("Available")) {
                            MusicControlManager.displayDeviceSelection();
                        }
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    super.mouseExited(e);
                    JList list = (JList) e.getSource();
                    list.clearSelection();
                }
            });
            actionList.updateUI();
            actionList.setBackground((Color) null);

            dataPanel.add(actionList, gridConstraints(dataPanel.getComponentCount(), 1, 6, 0, 3, 0));

            PlaylistTree slackWorkspaceTree = TreeHelper.buildSlackWorkspacesNode();
            dataPanel.add(slackWorkspaceTree, gridConstraints(dataPanel.getComponentCount(), 1, 6, 0, 3, 0));
//*********************************************************************************************************************************************
            JSeparator softwarePlaylistSeparator = new JSeparator();
            softwarePlaylistSeparator.setAlignmentY(0.0f);
            softwarePlaylistSeparator.setForeground(new Color(255, 255, 255, 63));
            dataPanel.add(softwarePlaylistSeparator, gridConstraints(dataPanel.getComponentCount(), 1, 6, 0, 1, 0));
//*********************************************************************************************************************************************
            DefaultListModel refreshAIModel = new DefaultListModel();

            /* Generate or Refresh AI playlist */
            Icon gearIcon = IconLoader.getIcon("/com/musictime/intellij/plugin/assets/generate.png");
            JLabel aiPlaylist = new JLabel();
            aiPlaylist.setIcon(gearIcon);
            if (PlayListCommands.myAIPlaylistId != null) {
                aiPlaylist.setText("Refresh my AI playlist");
                aiPlaylist.setToolTipText("Refresh your personalized playlist (My AI Top 40)");
            } else {
                aiPlaylist.setText("Generate my AI playlist");
                aiPlaylist.setToolTipText("Generate your personalized playlist (My AI Top 40)");
            }
            refreshAIModel.add(0, aiPlaylist);

            /* Create playlist */
            Icon addIcon = IconLoader.getIcon("/com/musictime/intellij/plugin/assets/add.png");
            JLabel createPlaylist = new JLabel();
            createPlaylist.setIcon(addIcon);
            createPlaylist.setText("Create Playlist");
            createPlaylist.setToolTipText("Create your personalized playlist");
            refreshAIModel.add(1, createPlaylist);

            JList<JLabel> refreshAIList = new JBList<>(refreshAIModel);
            refreshAIList.setVisibleRowCount(1);
            refreshAIList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            refreshAIList.setCellRenderer(new ListRenderer());
            refreshAIList.addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    super.mouseMoved(e);
                    int row = refreshAIList.locationToIndex(e.getPoint());
                    refreshAIList.setSelectedIndex(row);
                }
            });
            refreshAIList.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    super.mouseClicked(e);

                    JList list = (JList) e.getSource();
                    JLabel label = (JLabel) list.getSelectedValue();

                    if (label != null && label.getText() != null && label.getText().equals("Create Playlist")) {
                        if (createPlaylistButtonState == 0) {
                            createPlaylistButtonState = 1;
                            String playlistName = SoftwareCoUtils.showInputPrompt("Enter playlist name", "Spotify", spotifyIcon);
                            if (playlistName != null) {
                                JsonObject status = PlayListCommands.createPlaylist(playlistName);
                                if (status != null)
                                    SoftwareCoUtils.showMsgPrompt("Your playlist was created successfully", new Color(55, 108, 137, 100));
                                else
                                    SoftwareCoUtils.showMsgPrompt("Unable to create playlist, try again", new Color(120, 23, 50, 100));
                            }

                            new Thread(() -> {
                                try {
                                    Thread.sleep(1000);
                                    createPlaylistButtonState = 0;
                                } catch (Exception ex) {
                                    System.err.println(ex);
                                }
                            }).start();
                        }
                    } else if (refreshAIButtonState == 0) {
                        refreshAIButtonState = 1;

                        if (label != null && label.getText() != null) {
                            if (label.getText().equals("Refresh my AI playlist")) {
                                PlayListCommands.refreshAIPlaylist();
                                SoftwareCoUtils.showMsgPrompt("Your AI Top 40 playlist was refreshed successfully", new Color(55, 108, 137, 100));
                            } else if (label.getText().equals("Generate my AI playlist")) {
                                PlayListCommands.generateAIPlaylist();
                                SoftwareCoUtils.showMsgPrompt("Your AI Top 40 playlist was generated successfully", new Color(55, 108, 137, 100));
                            }
                            new Thread(() -> {
                                try {
                                    Thread.sleep(1000);
                                    refreshAIButtonState = 0;
                                } catch (Exception ex) {
                                    System.err.println(ex);
                                }
                            }).start();
                        }
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    super.mouseExited(e);
                    JList list = (JList) e.getSource();
                    list.clearSelection();
                }
            });
            refreshAIList.updateUI();
            refreshAIList.setBackground((Color) null);
            dataPanel.add(refreshAIList, gridConstraints(dataPanel.getComponentCount(), 1, 0, 8, 1, 0));

//*********************************************************************************************************************************************
            // Software Top 40 Playlist
            PlaylistTree softwarePlaylistTree = TreeHelper.buildSoftwarePlaylistTree();
            dataPanel.add(softwarePlaylistTree, gridConstraints(dataPanel.getComponentCount(), 1, 6, 0, 3, 0));

//*********************************************************************************************************************************************
            // My AI Top 40 Playlist
            if (PlayListCommands.myAIPlaylistId != null) {
                PlaylistTree myAIPlaylistTree = TreeHelper.buildAIPlaylistTree();
                dataPanel.add(myAIPlaylistTree, gridConstraints(dataPanel.getComponentCount(), 1, 6, 0, 3, 0));
            }
//*********************************************************************************************************************************************
            // Liked Songs Playlist
            if (MusicControlManager.likedTracks.size() > 0) {
                PlaylistTree likedPlaylistTree = TreeHelper.buildLikedSongsPlaylistTree();

                dataPanel.add(likedPlaylistTree, gridConstraints(dataPanel.getComponentCount(), 1, 6, 0, 3, 0));
            } else if (counter == 0) {
                PlayListCommands.likedTracks = PlayListCommands.getLikedSpotifyTracks(); // API call
                counter++;
            }

            //*********************************************************************************************************************
            // Add User Playlists
            List<String> playlistIds = new ArrayList<>(PlayListCommands.userPlaylistIds);
            if (playlistIds.size() > 0) {
                //*****************************************************************************************************************************
                JSeparator userPlaylistSeparator = new JSeparator();
                userPlaylistSeparator.setAlignmentY(0.0f);
                userPlaylistSeparator.setForeground(new Color(255, 255, 255, 63));
                dataPanel.add(userPlaylistSeparator, gridConstraints(dataPanel.getComponentCount(), 1, 6, 0, 1, 0));
                //*****************************************************************************************************************************

                for (String playlistId : playlistIds) {
                    PlaylistTree userPlaylistTree = TreeHelper.buildNormalPlaylistTree(playlistId);
                    dataPanel.add(userPlaylistTree, gridConstraints(dataPanel.getComponentCount(), 1, 6, 0, 3, 0));
                }
            }

//*********************************************************************************************************************************************
            // Add VSpacer at last
            dataPanel.add(component, gridConstraints(dataPanel.getComponentCount(), 6, 1, 0, 2, 0));

            dataPanel.updateUI();
            dataPanel.setVisible(true);

            scrollPane.repaint();
            scrollPane.updateUI();
            scrollPane.revalidate();

            playlistWindowContent.updateUI();
            playlistWindowContent.setVisible(true);
            playlistWindowContent.revalidate();
        }

    }

    public synchronized void rebuildRecommendedTreeView() {
        boolean hasSpotifyAccess = MusicControlManager.hasSpotifyAccess();
        if (!hasSpotifyAccess) {
            recommendPanel.removeAll();
            category.setVisible(false);
            genre.setVisible(false);
            recommendRefresh.setVisible(false);
            DefaultListModel listModel = new DefaultListModel();

            boolean requiresReAuth = MusicControlManager.requiresReAuthentication();
            String connectLabel = requiresReAuth ? "Reconnect Spotify to see recommendations" : "Connect Spotify to see recommendations";

            Icon icon = IconLoader.getIcon("/com/musictime/intellij/plugin/assets/spotify.png");
            JLabel connectedState = new JLabel();
            connectedState.setText(connectLabel);
            connectedState.setIcon(icon);
            connectedState.setOpaque(true);

            listModel.add(0, connectedState);
            JList<JLabel> actionList = new JList<>(listModel);
            actionList.setVisibleRowCount(1);
            actionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            actionList.setCellRenderer(new ListRenderer());
            actionList.addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    super.mouseMoved(e);
                    int row = actionList.locationToIndex(e.getPoint());
                    actionList.setSelectedIndex(row);
                }
            });
            actionList.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    super.mouseClicked(e);

                    JList list = (JList) e.getSource();
                    JLabel label = (JLabel) list.getSelectedValue();
                    if (label != null && label.getText() != null && label.getText().equals(connectLabel)) {
                        MusicControlManager.connectSpotify();
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    super.mouseExited(e);
                    JList list = (JList) e.getSource();
                    list.clearSelection();
                }
            });
            actionList.updateUI();
            actionList.setBackground((Color) null);
            recommendPanel.add(actionList, gridConstraints(recommendPanel.getComponentCount(), 1, 2, 0, 3, 0));

            updateRecommendVisibility();

            playlistWindowContent.updateUI();
            playlistWindowContent.setVisible(true);
            playlistWindowContent.revalidate();
        } else {
            recommendPanel.removeAll();
            category.setVisible(true);
            genre.setVisible(true);
            recommendRefresh.setVisible(true);
            recommendPanel.setBackground((Color) null);
            recommendPanel.setFocusable(true);

//*********************************************************************************************************************************************
            // Recommended Songs List
            Icon pawIcon = IconLoader.getIcon("/com/musictime/intellij/plugin/assets/paw.png");
            PlaylistTreeNode recommendedPlaylist = new PlaylistTreeNode(PlayListCommands.recommendationTitle, PlayListCommands.recommendedPlaylistId);
            DefaultTreeModel recommendedPlaylistModel = new DefaultTreeModel(recommendedPlaylist);
            recommendedPlaylist.setModel(recommendedPlaylistModel);
            JsonObject obj = PlayListCommands.recommendedTracks;
            if (obj != null && obj.has("tracks") && obj.getAsJsonArray("tracks").size() > 0) {
                JsonArray tracks = obj.getAsJsonArray("tracks");
                if (tracks != null && tracks.size() > 0) {
                    int index = (PlayListCommands.currentBatch * 10) - 10;

                    for (int i = 0; i < 10; i++) {
                        // start back at the beginning of the tracks list if we've reached the end
                        if (tracks.size() <= index) {
                            PlayListCommands.currentBatch = 1;
                            // start back at the beginning
                            index = 0;
                        }
                        JsonObject track = tracks.get(index).getAsJsonObject();
                        String trackName = TreeHelper.getTrackLabelSnippet(track);

                        PlaylistTreeNode node = new PlaylistTreeNode(trackName, track.get("id").getAsString());
                        recommendedPlaylist.add(node);
                        index++;
                    }
                }
            } else {
                PlaylistTreeNode node = new PlaylistTreeNode("Your tracks will appear here", null);
                recommendedPlaylist.add(node);
            }

            PlaylistTree recommendedPlaylistTree;
            if (TreeHelper.playlistTreeMap != null && TreeHelper.playlistTreeMap.containsKey(PlayListCommands.recommendedPlaylistId)) {
                recommendedPlaylistTree = TreeHelper.playlistTreeMap.get(PlayListCommands.recommendedPlaylistId);
                recommendedPlaylistTree.setModel(recommendedPlaylistModel);
            } else {
                recommendedPlaylistTree = new PlaylistTree(recommendedPlaylistModel);
                recommendedPlaylistTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
                recommendedPlaylistTree.setCellRenderer(new PlaylistTreeRenderer(pawIcon));

                recommendedPlaylistTree.addMouseListener(new PlaylistMouseListener(recommendedPlaylistTree));
                recommendedPlaylistTree.addTreeExpansionListener(new TreeExpansionListener() {
                    @Override
                    public void treeExpanded(TreeExpansionEvent event) {
                        // refresh();
                    }

                    @Override
                    public void treeCollapsed(TreeExpansionEvent event) {
                        // refresh();
                    }
                });

                recommendedPlaylistTree.addMouseMotionListener(new TreeScanner());
                recommendedPlaylistTree.setExpandedState(new TreePath(recommendedPlaylistModel.getPathToRoot(recommendedPlaylist)), true);

                TreeHelper.playlistTreeMap.put(PlayListCommands.recommendedPlaylistId, recommendedPlaylistTree);
            }
            PlaylistTreeRenderer recommendedPlaylistRenderer = (PlaylistTreeRenderer) recommendedPlaylistTree.getCellRenderer();
            recommendedPlaylistRenderer.setBackgroundNonSelectionColor(new Color(0, 0, 0, 0));
            recommendedPlaylistRenderer.setBorderSelectionColor(new Color(0, 0, 0, 0));
            recommendedPlaylistTree.setBackground((Color) null);

            recommendedPlaylistTree.setExpandedState(new TreePath(recommendedPlaylistModel.getPathToRoot(recommendedPlaylist)), recommendedPlaylistTree.expandState);

            recommendPanel.add(recommendedPlaylistTree, gridConstraints(recommendPanel.getComponentCount(), 1, 6, 0, 3, 0));

//*********************************************************************************************************************************************

            updateRecommendVisibility();

            playlistWindowContent.updateUI();
            playlistWindowContent.setVisible(true);
            playlistWindowContent.revalidate();
        }

    }

    private GridConstraints gridConstraints(int row, int vSize, int hSize, int anchor, int fill, int indent) {
        GridConstraints constraints = new GridConstraints();
        constraints.setRow(row);
        constraints.setColumn(0);
        constraints.setRowSpan(1);
        constraints.setColSpan(1);
        constraints.setVSizePolicy(vSize);
        constraints.setHSizePolicy(hSize);
        constraints.setAnchor(anchor);
        constraints.setFill(fill);
        constraints.setIndent(indent);
        constraints.setUseParentLayout(false);

        return constraints;
    }

    private synchronized void refreshButton() {
        win.rebuildPlaylistTreeView();
    }

    public JPanel getContent() {
        return playlistWindowContent;
    }

    public static void lazilyCheckDeviceLaunch(int retryCount, boolean playSelectedTrackOnCompletion) {
        DeviceInfo currentDevice = DeviceManager.getBestDeviceOption();
        if (currentDevice == null && retryCount > 0) {
            final int newRetryCount = retryCount - 1;
            new Thread(() -> {
                try {
                    Thread.sleep(4000);
                    lazilyCheckDeviceLaunch(newRetryCount, playSelectedTrackOnCompletion);
                } catch (Exception ex) {
                    System.err.println(ex);
                }
            }).start();
        } else if (currentDevice != null) {
            // done
            if (playSelectedTrackOnCompletion) {
                PlayerControlManager.playSpotifyPlaylist();
            }
        } else {
            SoftwareCoUtils.showMsgPrompt("Unable to establish a device connection. Please check that you are logged into your Spotify account.", new Color(120, 23, 50, 100));
        }
    }

    private void updateRecommendVisibility() {
        recommendPanel.updateUI();
        if (MusicControlManager.hasSpotifyAccess()) {
            recommendPanel.setVisible(true);
            recommendationHeader.setVisible(true);
            category.setVisible(true);
            genre.setVisible(true);
            recommendRefresh.setVisible(true);
            recommendScroll.setMinimumSize(new Dimension(-1, 75));
        } else {
            recommendPanel.setVisible(false);
            recommendationHeader.setVisible(false);
            category.setVisible(false);
            genre.setVisible(false);
            recommendRefresh.setVisible(false);
            recommendScroll.setMinimumSize(new Dimension(0, 0));
        }
        recommendScroll.getVerticalScrollBar().setUnitIncrement(16);
        recommendScroll.getHorizontalScrollBar().setUnitIncrement(16);
        recommendScroll.repaint();
        recommendScroll.updateUI();
        recommendScroll.revalidate();
    }
}
