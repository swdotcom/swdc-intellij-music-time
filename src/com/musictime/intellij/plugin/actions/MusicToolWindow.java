package com.musictime.intellij.plugin.actions;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.uiDesigner.core.GridConstraints;
import com.musictime.intellij.plugin.SoftwareCoSessionManager;
import com.musictime.intellij.plugin.SoftwareCoUtils;
import com.musictime.intellij.plugin.SoftwareResponse;
import com.musictime.intellij.plugin.music.*;
import com.musictime.intellij.plugin.musicjava.Apis;
import org.apache.commons.lang.StringUtils;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

public class MusicToolWindow {

    public static final Logger LOG = Logger.getLogger("MusicToolWindow");

    private JPanel playlistWindowContent;
    private JScrollPane scrollPane;
    private JPanel dataPanel;
    private JLabel refresh;
    private JLabel spotifyState;
    private JLabel menu;
    private JPanel recommendPanel;
    private JLabel spotifyConnect;
    private JScrollPane recommendScroll;
    private JLabel category;
    private JLabel genre;
    private JLabel songSearch;
    private JLabel recommendRefresh;

    private static MusicToolWindow win;
    private static Map<String, PlaylistTree> playlists = new HashMap<>();
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
                        Set<String> keys = playlists.keySet();
                        for (String key : keys) {
                            if (!key.equals(PlayListCommands.recommendedPlaylistId)) {
                                PlaylistTree tree = playlists.get(key);
                                tree.setExpandedState(tree.getPathForRow(0), false);
                            }
                        }
                        MusicControlManager.getSpotifyDevices(); // API call
                        PlayListCommands.updatePlaylists(0, null);
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
                    int index = SoftwareCoUtils.showMsgInputPrompt("Select genre", "Spotify", filterIcon, rec_genres);
                    if (index >= 0) {
                        PopupMenuBuilder.selectedType = "genre";
                        PopupMenuBuilder.selectedValue = rec_genres[index];
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
        recommendScroll.getVerticalScrollBar().setUnitIncrement(16);
        recommendScroll.getHorizontalScrollBar().setUnitIncrement(16);
        recommendScroll.repaint();

        playlistWindowContent.setBackground((Color) null);

        scrollPane.updateUI();
        scrollPane.setVisible(true);
        scrollPane.revalidate();
        //scrollRect = scrollPane.getBounds();
        recommendScroll.updateUI();
        recommendScroll.setVisible(true);
        recommendScroll.revalidate();
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
        playlists.clear();
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

            Icon icon = IconLoader.getIcon("/com/musictime/intellij/plugin/assets/spotify.png");
            JLabel connectedState = new JLabel();
            connectedState.setText("Connect Spotify");
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
                    if (label.getText().equals("Connect Spotify")) {
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
            openDashboard.setText("Open dashboard");
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
            if (MusicControlManager.spotifyDeviceIds.size() > 0) {
                if (MusicControlManager.currentDeviceId != null) {
                    if (MusicControlManager.currentDeviceName != null) {
                        deviceState.setText("Listening on " + MusicControlManager.currentDeviceName);
                        deviceState.setToolTipText("Listening on a Spotify device");
                    } else if (MusicControlManager.cacheDeviceName != null) {
                        deviceState.setText("Available on " + MusicControlManager.cacheDeviceName);
                        deviceState.setToolTipText("Available on a Spotify device");
                    } else {
                        deviceState.setText("Available Spotify devices");
                        deviceState.setToolTipText("Available Spotify devices");
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

            JList<JLabel> actionList = new JList<>(listModel);
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
                    if (label.getText().equals("See web analytics")) {
                        //Code to call web analytics
                        SoftwareCoUtils.launchMusicWebDashboard();
                    } else if (label.getText().equals("Open dashboard")) {
                        //Code to open web dashboard
                        SoftwareCoSessionManager.launchMusicTimeMetricsDashboard();
                    } else if (label.getText().equals("Learn more")) {
                        SoftwareCoSessionManager.getInstance().openReadmeFile();
                    } else if (label.getText().contains("Listening on") || label.getText().contains("Connect to") || label.getText().contains("Available")) {
                        MusicControlManager.launchPlayer(false, false);
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

            JList<JLabel> refreshAIList = new JList<>(refreshAIModel);
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

                    if (label.getText().equals("Create Playlist")) {
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
            PlaylistTreeNode softwarePlaylist = new PlaylistTreeNode("Software Top 40", PlayListCommands.topSpotifyPlaylistId);
            DefaultTreeModel softwarePlaylistModel = new DefaultTreeModel(softwarePlaylist);
            softwarePlaylist.setModel(softwarePlaylistModel);
            JsonObject obj = PlayListCommands.topSpotifyTracks;
            if (obj != null && obj.has("items")) {
                JsonArray items = obj.get("items").getAsJsonArray();
                if (items.size() == 0) {
                    PlaylistTreeNode node = new PlaylistTreeNode("Your tracks will appear here", null);
                    softwarePlaylist.add(node);
                } else {
                    for (JsonElement array : items) {
                        JsonObject track = array.getAsJsonObject().get("track").getAsJsonObject();
                        JsonArray artists = track.getAsJsonArray("artists");
                        String artistNames = "";
                        if (artists.size() > 0) {
                            for (JsonElement artistArray : artists) {
                                artistNames += artistArray.getAsJsonObject().get("name").getAsString() + ", ";
                            }
                            artistNames = artistNames.substring(0, artistNames.lastIndexOf(","));
                        }
                        String trackName = track.get("name").getAsString();
                        if (trackName.length() > 40) {
                            trackName = trackName.substring(0, 36) + "...";
                            if (artistNames.length() > 0)
                                trackName += " (" + artistNames + ")";
                        } else if (artistNames.length() > 0) {
                            trackName += " (" + artistNames + ")";
                        }

                        PlaylistTreeNode node = new PlaylistTreeNode(trackName, track.get("id").getAsString());
                        softwarePlaylist.add(node);
                    }
                }
            } else {
                PlaylistTreeNode node = new PlaylistTreeNode("Fetching playlist…", null);
                softwarePlaylist.add(node);
            }

            PlaylistTree softwarePlaylistTree;
            if (playlists != null && playlists.containsKey(PlayListCommands.topSpotifyPlaylistId)) {
                softwarePlaylistTree = playlists.get(PlayListCommands.topSpotifyPlaylistId);
                softwarePlaylistTree.setModel(softwarePlaylistModel);
            } else {
                softwarePlaylistTree = new PlaylistTree(softwarePlaylistModel);
                softwarePlaylistTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
                softwarePlaylistTree.setCellRenderer(new PlaylistTreeRenderer(pawIcon));

                softwarePlaylistTree.addMouseListener(new PlaylistMouseListener(softwarePlaylistTree));

                softwarePlaylistTree.addTreeExpansionListener(new TreeExpansionListener() {
                    @Override
                    public void treeExpanded(TreeExpansionEvent event) {
                        PlayListCommands.updatePlaylists(1, null);
                    }

                    @Override
                    public void treeCollapsed(TreeExpansionEvent event) {

                    }
                });

                softwarePlaylistTree.addMouseMotionListener(new TreeScanner());

                playlists.put(PlayListCommands.topSpotifyPlaylistId, softwarePlaylistTree);
            }

            PlaylistTreeRenderer softwarePlaylistRenderer = (PlaylistTreeRenderer) softwarePlaylistTree.getCellRenderer();
            softwarePlaylistRenderer.setBackgroundNonSelectionColor(new Color(0, 0, 0, 0));
            softwarePlaylistRenderer.setBorderSelectionColor(new Color(0, 0, 0, 0));
            softwarePlaylistTree.setBackground((Color) null);

            softwarePlaylistTree.setExpandedState(new TreePath(softwarePlaylistModel.getPathToRoot(softwarePlaylist)), softwarePlaylistTree.expandState);

            dataPanel.add(softwarePlaylistTree, gridConstraints(dataPanel.getComponentCount(), 1, 6, 0, 3, 0));

//*********************************************************************************************************************************************
            // My AI Top 40 Playlist
            if (PlayListCommands.myAIPlaylistId != null) {
                PlaylistTreeNode myAIPlaylist = new PlaylistTreeNode("My AI Top 40", PlayListCommands.myAIPlaylistId);
                DefaultTreeModel myAIPlaylistModel = new DefaultTreeModel(myAIPlaylist);
                myAIPlaylist.setModel(myAIPlaylistModel);
                JsonObject obj1 = PlayListCommands.myAITopTracks;
                if (obj1 != null && obj1.has("tracks")) {
                    JsonObject tracks = obj1.get("tracks").getAsJsonObject();
                    JsonArray items = tracks.get("items").getAsJsonArray();
                    if (items.size() == 0) {
                        PlaylistTreeNode node = new PlaylistTreeNode("Your tracks will appear here", null);
                        myAIPlaylist.add(node);
                    } else {
                        for (JsonElement array : items) {
                            JsonObject track = array.getAsJsonObject().get("track").getAsJsonObject();
                            JsonArray artists = track.getAsJsonArray("artists");
                            String artistNames = "";
                            if (artists.size() > 0) {
                                for (JsonElement artistArray : artists) {
                                    artistNames += artistArray.getAsJsonObject().get("name").getAsString() + ", ";
                                }
                                artistNames = artistNames.substring(0, artistNames.lastIndexOf(","));
                            }
                            String trackName = track.get("name").getAsString();
                            if (trackName.length() > 40) {
                                trackName = trackName.substring(0, 36) + "...";
                                if (artistNames.length() > 0)
                                    trackName += " (" + artistNames + ")";
                            } else if (artistNames.length() > 0) {
                                trackName += " (" + artistNames + ")";
                            }

                            PlaylistTreeNode node = new PlaylistTreeNode(trackName, track.get("id").getAsString());
                            myAIPlaylist.add(node);
                        }
                    }
                } else {
                    PlaylistTreeNode node = new PlaylistTreeNode("Fetching playlist…", null);
                    myAIPlaylist.add(node);
                }

                PlaylistTree myAIPlaylistTree;
                if (playlists != null && playlists.containsKey(PlayListCommands.myAIPlaylistId)) {
                    myAIPlaylistTree = playlists.get(PlayListCommands.myAIPlaylistId);
                    myAIPlaylistTree.setModel(myAIPlaylistModel);
                } else {
                    myAIPlaylistTree = new PlaylistTree(myAIPlaylistModel);
                    myAIPlaylistTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
                    myAIPlaylistTree.setCellRenderer(new PlaylistTreeRenderer(pawIcon));

                    myAIPlaylistTree.addMouseListener(new PlaylistMouseListener(myAIPlaylistTree));

                    myAIPlaylistTree.addTreeExpansionListener(new TreeExpansionListener() {
                        @Override
                        public void treeExpanded(TreeExpansionEvent event) {
                            PlayListCommands.updatePlaylists(2, null);
                        }

                        @Override
                        public void treeCollapsed(TreeExpansionEvent event) {

                        }
                    });

                    myAIPlaylistTree.addMouseMotionListener(new TreeScanner());

                    playlists.put(PlayListCommands.myAIPlaylistId, myAIPlaylistTree);
                }
                PlaylistTreeRenderer myAIPlaylistRenderer = (PlaylistTreeRenderer) myAIPlaylistTree.getCellRenderer();
                myAIPlaylistRenderer.setBackgroundNonSelectionColor(new Color(0, 0, 0, 0));
                myAIPlaylistRenderer.setBorderSelectionColor(new Color(0, 0, 0, 0));
                myAIPlaylistTree.setBackground((Color) null);

                myAIPlaylistTree.setExpandedState(new TreePath(myAIPlaylistModel.getPathToRoot(myAIPlaylist)), myAIPlaylistTree.expandState);

                dataPanel.add(myAIPlaylistTree, gridConstraints(dataPanel.getComponentCount(), 1, 6, 0, 3, 0));
            }

//*********************************************************************************************************************************************
            // Liked Songs Playlist
            if (MusicControlManager.likedTracks.size() > 0) {
                Icon likeIcon = IconLoader.getIcon("/com/musictime/intellij/plugin/assets/heart-filled.png");
                PlaylistTreeNode likedPlaylist = new PlaylistTreeNode("Liked Songs", PlayListCommands.likedPlaylistId);
                DefaultTreeModel likedPlaylistModel = new DefaultTreeModel(likedPlaylist);
                likedPlaylist.setModel(likedPlaylistModel);
                JsonObject obj2 = PlayListCommands.likedTracks;
                if (obj2 != null && obj2.has("items")) {
                    for (JsonElement array : obj2.get("items").getAsJsonArray()) {
                        JsonObject track = array.getAsJsonObject().getAsJsonObject("track");
                        JsonArray artists = track.getAsJsonArray("artists");
                        String artistNames = "";
                        if (artists.size() > 0) {
                            for (JsonElement artistArray : artists) {
                                artistNames += artistArray.getAsJsonObject().get("name").getAsString() + ", ";
                            }
                            artistNames = artistNames.substring(0, artistNames.lastIndexOf(","));
                        }
                        String trackName = track.get("name").getAsString();
                        if (trackName.length() > 40) {
                            trackName = trackName.substring(0, 36) + "...";
                            if (artistNames.length() > 0)
                                trackName += " (" + artistNames + ")";
                        } else if (artistNames.length() > 0) {
                            trackName += " (" + artistNames + ")";
                        }

                        PlaylistTreeNode node = new PlaylistTreeNode(trackName, track.get("id").getAsString());
                        likedPlaylist.add(node);
                    }
                } else {
                    PlaylistTreeNode node = new PlaylistTreeNode("Fetching playlist…", null);
                    likedPlaylist.add(node);
                }

                PlaylistTree likedPlaylistTree;
                if (playlists != null && playlists.containsKey(PlayListCommands.likedPlaylistId)) {
                    likedPlaylistTree = playlists.get(PlayListCommands.likedPlaylistId);
                    likedPlaylistTree.setModel(likedPlaylistModel);
                } else {
                    likedPlaylistTree = new PlaylistTree(likedPlaylistModel);
                    likedPlaylistTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
                    likedPlaylistTree.setCellRenderer(new PlaylistTreeRenderer(likeIcon));

                    likedPlaylistTree.addMouseListener(new PlaylistMouseListener(likedPlaylistTree));

                    likedPlaylistTree.addTreeExpansionListener(new TreeExpansionListener() {
                        @Override
                        public void treeExpanded(TreeExpansionEvent event) {
                            PlayListCommands.updatePlaylists(3, null);
                        }

                        @Override
                        public void treeCollapsed(TreeExpansionEvent event) {

                        }
                    });

                    likedPlaylistTree.addMouseMotionListener(new TreeScanner());

                    playlists.put(PlayListCommands.likedPlaylistId, likedPlaylistTree);
                }
                PlaylistTreeRenderer likedPlaylistRenderer = (PlaylistTreeRenderer) likedPlaylistTree.getCellRenderer();
                likedPlaylistRenderer.setBackgroundNonSelectionColor(new Color(0, 0, 0, 0));
                likedPlaylistRenderer.setBorderSelectionColor(new Color(0, 0, 0, 0));
                likedPlaylistTree.setBackground((Color) null);

                likedPlaylistTree.setExpandedState(new TreePath(likedPlaylistModel.getPathToRoot(likedPlaylist)), likedPlaylistTree.expandState);

                dataPanel.add(likedPlaylistTree, gridConstraints(dataPanel.getComponentCount(), 1, 6, 0, 3, 0));
            } else if (counter == 0) {
                PlayListCommands.likedTracks = PlayListCommands.getLikedSpotifyTracks(); // API call
                counter++;
            }

            //*********************************************************************************************************************
            // Add User Playlists
            if (PlayListCommands.userPlaylistIds.size() > 0) {
                //*****************************************************************************************************************************
                JSeparator userPlaylistSeparator = new JSeparator();
                userPlaylistSeparator.setAlignmentY(0.0f);
                userPlaylistSeparator.setForeground(new Color(255, 255, 255, 63));
                dataPanel.add(userPlaylistSeparator, gridConstraints(dataPanel.getComponentCount(), 1, 6, 0, 1, 0));
                //*****************************************************************************************************************************
                Icon playlistIcon = IconLoader.getIcon("/com/musictime/intellij/plugin/assets/playlist-16x16.png");
                for (String playlistId : PlayListCommands.userPlaylistIds) {
                    PlaylistTreeNode userPlaylist = new PlaylistTreeNode(PlayListCommands.userPlaylists.get(playlistId), playlistId);
                    DefaultTreeModel userPlaylistModel = new DefaultTreeModel(userPlaylist);
                    userPlaylist.setModel(userPlaylistModel);
                    JsonObject obj1 = PlayListCommands.userTracks.get(playlistId);
                    if (obj1 != null && obj1.has("tracks")) {
                        JsonObject tracks = obj1.get("tracks").getAsJsonObject();
                        JsonArray items = tracks.get("items").getAsJsonArray();
                        if (items.size() == 0) {
                            PlaylistTreeNode node = new PlaylistTreeNode("Your tracks will appear here", null);
                            userPlaylist.add(node);
                        } else {
                            for (JsonElement array : items) {
                                JsonObject track = array.getAsJsonObject().get("track").getAsJsonObject();
                                JsonArray artists = track.getAsJsonArray("artists");
                                String artistNames = "";
                                if (artists.size() > 0) {
                                    for (JsonElement artistArray : artists) {
                                        artistNames += artistArray.getAsJsonObject().get("name").getAsString() + ", ";
                                    }
                                    artistNames = artistNames.substring(0, artistNames.lastIndexOf(","));
                                }
                                String trackName = track.get("name").getAsString(); // inside track we can check "available_markets": []
                                if (trackName.length() > 40) {
                                    trackName = trackName.substring(0, 36) + "...";
                                    if (artistNames.length() > 0)
                                        trackName += " (" + artistNames + ")";
                                } else if (artistNames.length() > 0) {
                                    trackName += " (" + artistNames + ")";
                                }

                                PlaylistTreeNode node = new PlaylistTreeNode(trackName, track.get("id").getAsString());
                                userPlaylist.add(node);
                            }
                        }
                    } else {
                        PlaylistTreeNode node = new PlaylistTreeNode("Fetching playlist…", null);
                        userPlaylist.add(node);
                    }

                    PlaylistTree userPlaylistTree;
                    if (playlists != null && playlists.containsKey(playlistId)) {
                        userPlaylistTree = playlists.get(playlistId);
                        userPlaylistTree.setModel(userPlaylistModel);
                    } else {
                        userPlaylistTree = new PlaylistTree(userPlaylistModel);
                        userPlaylistTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
                        userPlaylistTree.setCellRenderer(new PlaylistTreeRenderer(playlistIcon));

                        userPlaylistTree.addMouseListener(new PlaylistMouseListener(userPlaylistTree));

                        userPlaylistTree.addTreeExpansionListener(new TreeExpansionListener() {
                            @Override
                            public void treeExpanded(TreeExpansionEvent event) {
                                PlaylistTreeNode node = (PlaylistTreeNode) event.getPath().getPathComponent(0);
                                PlayListCommands.updatePlaylists(4, node.getId());
                            }

                            @Override
                            public void treeCollapsed(TreeExpansionEvent event) {
                            }
                        });

                        userPlaylistTree.addMouseMotionListener(new TreeScanner());

                        playlists.put(playlistId, userPlaylistTree);
                    }
                    PlaylistTreeRenderer userPlaylistRenderer = (PlaylistTreeRenderer) userPlaylistTree.getCellRenderer();
                    userPlaylistRenderer.setBackgroundNonSelectionColor(new Color(0, 0, 0, 0));
                    userPlaylistRenderer.setBorderSelectionColor(new Color(0, 0, 0, 0));
                    userPlaylistTree.setBackground((Color) null);

                    userPlaylistTree.setExpandedState(new TreePath(userPlaylistModel.getPathToRoot(userPlaylist)), userPlaylistTree.expandState);

                    dataPanel.add(userPlaylistTree, gridConstraints(dataPanel.getComponentCount(), 1, 6, 0, 3, 0));
                }
            }

//*********************************************************************************************************************************************
            // Add VSpacer at last
            dataPanel.add(component, gridConstraints(dataPanel.getComponentCount(), 6, 1, 0, 2, 0));

            //dataPanel.setBounds(rect);
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

            Icon icon = IconLoader.getIcon("/com/musictime/intellij/plugin/assets/spotify.png");
            JLabel connectedState = new JLabel();
            connectedState.setText("Connect Spotify to see recommendations");
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
                    if (label.getText().equals("Connect Spotify to see recommendations")) {
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

            recommendPanel.updateUI();
            recommendPanel.setVisible(true);

            recommendScroll.repaint();
            recommendScroll.updateUI();
            recommendScroll.revalidate();

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
                        JsonArray artists = track.getAsJsonArray("artists");
                        String artistNames = "";
                        if (artists.size() > 0) {
                            for (JsonElement artistArray : artists) {
                                artistNames += artistArray.getAsJsonObject().get("name").getAsString() + ", ";
                            }
                            artistNames = artistNames.substring(0, artistNames.lastIndexOf(","));
                        }
                        String trackName = track.get("name").getAsString();
                        if (trackName.length() > 40) {
                            trackName = trackName.substring(0, 36) + "...";
                            if (artistNames.length() > 0)
                                trackName += " (" + artistNames + ")";
                        } else if (artistNames.length() > 0) {
                            trackName += " (" + artistNames + ")";
                        }

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
            if (playlists != null && playlists.containsKey(PlayListCommands.recommendedPlaylistId)) {
                recommendedPlaylistTree = playlists.get(PlayListCommands.recommendedPlaylistId);
                recommendedPlaylistTree.setModel(recommendedPlaylistModel);
            } else {
                recommendedPlaylistTree = new PlaylistTree(recommendedPlaylistModel);
                recommendedPlaylistTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
                recommendedPlaylistTree.setCellRenderer(new PlaylistTreeRenderer(pawIcon));

                recommendedPlaylistTree.addMouseListener(new PlaylistMouseListener(recommendedPlaylistTree));
                recommendedPlaylistTree.addTreeExpansionListener(new TreeExpansionListener() {
                    @Override
                    public void treeExpanded(TreeExpansionEvent event) {
                        refresh();
                    }

                    @Override
                    public void treeCollapsed(TreeExpansionEvent event) {
                        refresh();
                    }
                });

                recommendedPlaylistTree.addMouseMotionListener(new TreeScanner());
                recommendedPlaylistTree.setExpandedState(new TreePath(recommendedPlaylistModel.getPathToRoot(recommendedPlaylist)), true);

                playlists.put(PlayListCommands.recommendedPlaylistId, recommendedPlaylistTree);
            }
            PlaylistTreeRenderer recommendedPlaylistRenderer = (PlaylistTreeRenderer) recommendedPlaylistTree.getCellRenderer();
            recommendedPlaylistRenderer.setBackgroundNonSelectionColor(new Color(0, 0, 0, 0));
            recommendedPlaylistRenderer.setBorderSelectionColor(new Color(0, 0, 0, 0));
            recommendedPlaylistTree.setBackground((Color) null);

            recommendedPlaylistTree.setExpandedState(new TreePath(recommendedPlaylistModel.getPathToRoot(recommendedPlaylist)), recommendedPlaylistTree.expandState);

            recommendPanel.add(recommendedPlaylistTree, gridConstraints(recommendPanel.getComponentCount(), 1, 6, 0, 3, 0));

//*********************************************************************************************************************************************

            recommendPanel.updateUI();
            recommendPanel.setVisible(true);

            recommendScroll.repaint();
            recommendScroll.updateUI();
            recommendScroll.revalidate();

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

    public static void lazilyCheckPlayer(int retryCount, String playlist, String track, String trackName) {
        if (MusicControlManager.currentTrackName == null) {
            if (!MusicControlManager.deviceActivated && retryCount > 0) {
                final int newRetryCount = retryCount - 1;
                new Thread(() -> {
                    try {
                        Thread.sleep(4000);
                        lazilyCheckPlayer(newRetryCount, playlist, track, trackName);
                    } catch (Exception ex) {
                        System.err.println(ex);
                    }
                }).start();
            } else if (MusicControlManager.deviceActivated) {
                SoftwareResponse response = PlayerControlManager.playSpotifyPlaylist(playlist, track, trackName);
                if (response.getCode() == 403 && !response.getJsonObj().isJsonNull() && response.getJsonObj().has("error")) {
                    JsonObject error = response.getJsonObj().getAsJsonObject("error");
                    if (error.get("reason").getAsString().equals("PREMIUM_REQUIRED")) {
                        SoftwareCoUtils.showMsgPrompt(error.get("message").getAsString(), new Color(120, 23, 50, 100));
                    } else if (error.get("reason").getAsString().equals("UNKNOWN")) {
                        SoftwareCoUtils.showMsgPrompt("We were unable to play the selected track<br> because it is unavailable in your market.", new Color(120, 23, 50, 100));
                    }
                }
                MusicControlManager.deviceActivated = false;
            }
        } else {
            SoftwareResponse response = PlayerControlManager.playSpotifyPlaylist(playlist, track, trackName);
        }
    }
}
