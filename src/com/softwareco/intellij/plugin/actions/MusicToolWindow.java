package com.softwareco.intellij.plugin.actions;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.uiDesigner.core.GridConstraints;
import com.softwareco.intellij.plugin.SoftwareCoUtils;
import com.softwareco.intellij.plugin.music.*;

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
import java.util.logging.Level;
import java.util.logging.Logger;

public class MusicToolWindow {

    public static final Logger LOG = Logger.getLogger("MusicToolWindow");

    private JPanel playlistWindowContent;
    private JScrollPane scrollPane;
    private JPanel dataPanel;
    private JLabel refresh;
    private static JButton reload = new JButton();
    private static Map<String, PlaylistTree> playlists = new HashMap<>();
    private JLabel spotifyState;
    private JLabel menu;
    private JPopupMenu popupMenu = new JPopupMenu();

    private static int listIndex = 0;
    private static int refreshButtonState = 0;
    private static int refreshAIButtonState = 0;
    private static int counter = 0;

    public MusicToolWindow(ToolWindow toolWindow) {
        playlistWindowContent.setFocusable(true);

        refresh.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if(refreshButtonState == 0) {
                    refreshButtonState = 1;
                    if (MusicControlManager.spotifyCacheState)
                        PlayListCommands.updatePlaylists(0, null);
                    refreshButton();
                    SoftwareCoUtils.showMsgPrompt("Playlist Refreshed Successfully !!!");
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
        Icon refreshIcon = IconLoader.getIcon("/com/softwareco/intellij/plugin/assets/refresh.png");
        refresh.setIcon(refreshIcon);

        // Sorting menu ********************************************************
        Icon menuIcon = IconLoader.getIcon("/com/softwareco/intellij/plugin/assets/menu.png");
        menu.setIcon(menuIcon);

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
        popupMenu.add(sort1);
        popupMenu.add(sort2);
        menu.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                popupMenu.show(e.getComponent(), e.getX(), e.getY());
            }
        });
        // End ******************************************************************

        reload.addActionListener(e -> {
            try {
                refreshButton();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        this.currentPlayLists();

        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        playlistWindowContent.setBackground((Color) null);
    }

    public static void triggerRefresh() {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
                reload.doClick();
            }
        });
    }

    public static void reset() {
        playlists.clear();
    }

    public synchronized void currentPlayLists() {
        // Get VSpacer component
        Component component = dataPanel.getComponent(dataPanel.getComponentCount() - 1);

        if(!SoftwareCoUtils.isSpotifyConncted()) {
            dataPanel.removeAll();
            menu.setEnabled(false);
            DefaultListModel listModel = new DefaultListModel();

            Icon icon = IconLoader.getIcon("/com/softwareco/intellij/plugin/assets/spotify.png");
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
                    if(label.getText().equals("Connect Spotify")) {
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
            dataPanel.add(actionList, gridConstraints(dataPanel.getComponentCount(), 1, 2, 0, 3, 0));

            // Add VSpacer at last
            dataPanel.add(component, gridConstraints(dataPanel.getComponentCount(), 6, 1, 0, 2, 0));
            //dataPanel.revalidate();
            dataPanel.updateUI();
            dataPanel.setVisible(true);
            scrollPane.updateUI();
            scrollPane.setVisible(true);
            playlistWindowContent.updateUI();
            playlistWindowContent.setVisible(true);
        } else {
            dataPanel.removeAll();
            dataPanel.setBackground((Color) null);
            dataPanel.setFocusable(true);
            menu.setEnabled(true);
            listIndex = 0;

            DefaultListModel listModel = new DefaultListModel();
            Icon towerIcon = IconLoader.getIcon("/com/softwareco/intellij/plugin/assets/tower.png");
            JLabel connectedState = new JLabel();
            connectedState.setText("Spotify Connected");
            connectedState.setIcon(towerIcon);
            connectedState.setOpaque(true);
            listModel.add(listIndex, connectedState);
            listIndex ++;

            Icon spotifyIcon = IconLoader.getIcon("/com/softwareco/intellij/plugin/assets/spotify.png");
            if(MusicControlManager.spotifyDeviceIds.size() > 0) {
                JLabel deviceState = new JLabel();
                deviceState.setIcon(spotifyIcon);
                if(MusicControlManager.currentDeviceName != null) {
                    deviceState.setText("Listening on " + MusicControlManager.currentDeviceName);
                    deviceState.setToolTipText("Listening on a Spotify device");
                } else {
                    String devices = "Connected on ";
                    String toolTip = "";
                    for(String id : MusicControlManager.spotifyDeviceIds) {
                        devices += MusicControlManager.spotifyDevices.get(id) + ",";
                    }
                    devices = devices.substring(0, devices.lastIndexOf(","));
                    if(MusicControlManager.spotifyDeviceIds.size() == 1)
                        toolTip = "Spotify devices connected";
                    else
                        toolTip = "Multiple Spotify devices connected";

                    deviceState.setText(devices);
                    deviceState.setToolTipText(toolTip);
                }
                listModel.add(listIndex, deviceState);
                listIndex ++;
            }

            Icon pawIcon = IconLoader.getIcon("/com/softwareco/intellij/plugin/assets/paw.png");
            JLabel webAnalytics = new JLabel();
            webAnalytics.setIcon(pawIcon);
            webAnalytics.setText("See Web Analytics");
            listModel.add(listIndex, webAnalytics);
            listIndex ++;

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
                    if(label.getText().equals("See Web Analytics")) {
                        //Code to call web analytics
                        SoftwareCoUtils.launchMusicWebDashboard();
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

            dataPanel.add(actionList, gridConstraints(dataPanel.getComponentCount(), 1, 6, 0, 3, 0));
//*********************************************************************************************************************************************
            JSeparator softwarePlaylistSeparator = new JSeparator();
            softwarePlaylistSeparator.setAlignmentY(0.0f);
            softwarePlaylistSeparator.setForeground(new Color(58, 86, 187));
            dataPanel.add(softwarePlaylistSeparator, gridConstraints(dataPanel.getComponentCount(), 1, 6, 0, 1, 0));
//*********************************************************************************************************************************************
            DefaultListModel refreshAIModel = new DefaultListModel();
            Icon gearIcon = IconLoader.getIcon("/com/softwareco/intellij/plugin/assets/settings.png");
            JLabel aiPlaylist = new JLabel();
            aiPlaylist.setIcon(gearIcon);
            if(PlayListCommands.myAIPlaylistId != null) {
                aiPlaylist.setText("Refresh My AI Playlist");
            } else {
                aiPlaylist.setText("Generate My AI Playlist");
            }
            refreshAIModel.add(0, aiPlaylist);

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

                    if(refreshAIButtonState == 0) {
                        refreshAIButtonState = 1;
                        JList list = (JList) e.getSource();
                        JLabel label = (JLabel) list.getSelectedValue();
                        if (label.getText().equals("Refresh My AI Playlist")) {
                            PlayListCommands.refreshAIPlaylist();
                            SoftwareCoUtils.showMsgPrompt("My AI Playlist Refreshed Successfully !!!");
                        } else if (label.getText().equals("Generate My AI Playlist")) {
                            PlayListCommands.generateAIPlaylist();
                            SoftwareCoUtils.showMsgPrompt("My AI Playlist Generated Successfully !!!");
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
            dataPanel.add(refreshAIList, gridConstraints(dataPanel.getComponentCount(), 1, 0, 8, 1, 0));

//*********************************************************************************************************************************************
            // Software Top 40 Playlist
            PlaylistTreeNode softwarePlaylist = new PlaylistTreeNode("Software Top 40", PlayListCommands.topSpotifyPlaylistId);
            DefaultTreeModel softwarePlaylistModel = new DefaultTreeModel(softwarePlaylist);
            softwarePlaylist.setModel(softwarePlaylistModel);
            JsonObject obj = PlayListCommands.topSpotifyTracks;
            if (obj != null && obj.has("items")) {
                JsonArray items = obj.get("items").getAsJsonArray();
                if(items.size() == 0) {
                    PlaylistTreeNode node = new PlaylistTreeNode("No songs have been added to this playlist yet", null);
                    softwarePlaylist.add(node);
                } else {
                    for (JsonElement array : items) {
                        JsonObject track = array.getAsJsonObject().get("track").getAsJsonObject();
                        PlaylistTreeNode node = new PlaylistTreeNode(track.get("name").getAsString(), track.get("id").getAsString());
                        softwarePlaylist.add(node);
                    }
                }
            } else {
                PlaylistTreeNode node = new PlaylistTreeNode("Loading...", null);
                softwarePlaylist.add(node);
            }

            PlaylistTree softwarePlaylistTree;
            if(playlists != null && playlists.containsKey(PlayListCommands.topSpotifyPlaylistId)) {
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
            softwarePlaylistRenderer.setBackgroundNonSelectionColor(new Color(0,0,0,0));
            softwarePlaylistRenderer.setBorderSelectionColor(new Color(0,0,0,0));
            softwarePlaylistTree.setBackground((Color) null);

            softwarePlaylistTree.setExpandedState(new TreePath(softwarePlaylistModel.getPathToRoot(softwarePlaylist)), softwarePlaylistTree.expandState);

            dataPanel.add(softwarePlaylistTree, gridConstraints(dataPanel.getComponentCount(), 1, 6, 0, 3, 0));

//*********************************************************************************************************************************************
            // My AI Top 40 Playlist
            if(PlayListCommands.myAIPlaylistId != null) {
                PlaylistTreeNode myAIPlaylist = new PlaylistTreeNode("My AI Top 40", PlayListCommands.myAIPlaylistId);
                DefaultTreeModel myAIPlaylistModel = new DefaultTreeModel(myAIPlaylist);
                myAIPlaylist.setModel(myAIPlaylistModel);
                JsonObject obj1 = PlayListCommands.myAITopTracks;
                if (obj1 != null && obj1.has("tracks")) {
                    JsonObject tracks = obj1.get("tracks").getAsJsonObject();
                    JsonArray items = tracks.get("items").getAsJsonArray();
                    if(items.size() == 0) {
                        PlaylistTreeNode node = new PlaylistTreeNode("No songs have been added to this playlist yet, Refresh AI playlist", null);
                        myAIPlaylist.add(node);
                    } else {
                        for (JsonElement array : items) {
                            JsonObject track = array.getAsJsonObject().get("track").getAsJsonObject();
                            PlaylistTreeNode node = new PlaylistTreeNode(track.get("name").getAsString(), track.get("id").getAsString());
                            myAIPlaylist.add(node);
                        }
                    }
                } else {
                    PlaylistTreeNode node = new PlaylistTreeNode("Loading...", null);
                    myAIPlaylist.add(node);
                }

                PlaylistTree myAIPlaylistTree;
                if(playlists != null && playlists.containsKey(PlayListCommands.myAIPlaylistId)) {
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
                myAIPlaylistRenderer.setBackgroundNonSelectionColor(new Color(0,0,0,0));
                myAIPlaylistRenderer.setBorderSelectionColor(new Color(0,0,0,0));
                myAIPlaylistTree.setBackground((Color)null);

                myAIPlaylistTree.setExpandedState(new TreePath(myAIPlaylistModel.getPathToRoot(myAIPlaylist)), myAIPlaylistTree.expandState);

                dataPanel.add(myAIPlaylistTree, gridConstraints(dataPanel.getComponentCount(), 1, 6, 0, 3, 0));
            }

//*********************************************************************************************************************************************
            // Liked Songs Playlist
            if(MusicControlManager.likedTracks.size() > 0) {
                PlaylistTreeNode likedPlaylist = new PlaylistTreeNode("Liked Songs", PlayListCommands.likedPlaylistId);
                DefaultTreeModel likedPlaylistModel = new DefaultTreeModel(likedPlaylist);
                likedPlaylist.setModel(likedPlaylistModel);
                JsonObject obj2 = PlayListCommands.likedTracks;
                if (obj2 != null && obj2.has("items")) {
                    for (JsonElement array : obj2.get("items").getAsJsonArray()) {
                        JsonObject track = array.getAsJsonObject().getAsJsonObject("track");
                        PlaylistTreeNode node = new PlaylistTreeNode(track.get("name").getAsString(), track.get("id").getAsString());
                        likedPlaylist.add(node);
                    }
                }

                PlaylistTree likedPlaylistTree;
                if (playlists != null && playlists.containsKey(PlayListCommands.likedPlaylistId)) {
                    likedPlaylistTree = playlists.get(PlayListCommands.likedPlaylistId);
                    likedPlaylistTree.setModel(likedPlaylistModel);
                } else {
                    likedPlaylistTree = new PlaylistTree(likedPlaylistModel);
                    likedPlaylistTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
                    likedPlaylistTree.setCellRenderer(new PlaylistTreeRenderer(spotifyIcon));

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
                likedPlaylistRenderer.setBackgroundNonSelectionColor(new Color(0,0,0,0));
                likedPlaylistRenderer.setBorderSelectionColor(new Color(0,0,0,0));
                likedPlaylistTree.setBackground((Color)null);

                likedPlaylistTree.setExpandedState(new TreePath(likedPlaylistModel.getPathToRoot(likedPlaylist)), likedPlaylistTree.expandState);

                dataPanel.add(likedPlaylistTree, gridConstraints(dataPanel.getComponentCount(), 1, 6, 0, 3, 0));
            } else if(counter == 0) {
                PlayListCommands.likedTracks = PlayListCommands.getLikedSpotifyTracks(); // API call
                counter++;
            }

//*********************************************************************************************************************************************
            // Get User Playlists
            if(PlayListCommands.userPlaylistIds.size() > 0) {
                //*****************************************************************************************************************************
                JSeparator userPlaylistSeparator = new JSeparator();
                userPlaylistSeparator.setAlignmentY(0.0f);
                userPlaylistSeparator.setForeground(new Color(58, 86, 187));
                dataPanel.add(userPlaylistSeparator, gridConstraints(dataPanel.getComponentCount(), 1, 6, 0, 1, 0));
                //*****************************************************************************************************************************

                for(String playlistId : PlayListCommands.userPlaylistIds) {
                    PlaylistTreeNode userPlaylist = new PlaylistTreeNode(PlayListCommands.userPlaylists.get(playlistId), playlistId);
                    DefaultTreeModel userPlaylistModel = new DefaultTreeModel(userPlaylist);
                    userPlaylist.setModel(userPlaylistModel);
                    JsonObject obj1 = PlayListCommands.userTracks.get(playlistId);
                    if (obj1 != null && obj1.has("tracks")) {
                        JsonObject tracks = obj1.get("tracks").getAsJsonObject();
                        JsonArray items = tracks.get("items").getAsJsonArray();
                        if (items.size() == 0) {
                            PlaylistTreeNode node = new PlaylistTreeNode("No songs have been added to this playlist yet", null);
                            userPlaylist.add(node);
                        } else {
                            for (JsonElement array : items) {
                                JsonObject track = array.getAsJsonObject().get("track").getAsJsonObject();
                                PlaylistTreeNode node = new PlaylistTreeNode(track.get("name").getAsString(), track.get("id").getAsString());
                                userPlaylist.add(node);
                            }
                        }
                    } else {
                        PlaylistTreeNode node = new PlaylistTreeNode("Loading...", null);
                        userPlaylist.add(node);
                    }

                    PlaylistTree userPlaylistTree;
                    if(playlists != null && playlists.containsKey(playlistId)) {
                        userPlaylistTree = playlists.get(playlistId);
                        userPlaylistTree.setModel(userPlaylistModel);
                    } else {
                        userPlaylistTree = new PlaylistTree(userPlaylistModel);
                        userPlaylistTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
                        userPlaylistTree.setCellRenderer(new PlaylistTreeRenderer(spotifyIcon));

                        userPlaylistTree.addMouseListener(new PlaylistMouseListener(userPlaylistTree));

                        userPlaylistTree.addTreeExpansionListener(new TreeExpansionListener() {
                            @Override
                            public void treeExpanded(TreeExpansionEvent event) {
                                PlaylistTreeNode node = (PlaylistTreeNode) event.getPath().getPathComponent(0);
                                PlayListCommands.updatePlaylists(4, node.getId());
                            }

                            @Override
                            public void treeCollapsed(TreeExpansionEvent event) { }
                        });

                        userPlaylistTree.addMouseMotionListener(new TreeScanner());

                        playlists.put(playlistId, userPlaylistTree);
                    }
                    PlaylistTreeRenderer userPlaylistRenderer = (PlaylistTreeRenderer) userPlaylistTree.getCellRenderer();
                    userPlaylistRenderer.setBackgroundNonSelectionColor(new Color(0,0,0,0));
                    userPlaylistRenderer.setBorderSelectionColor(new Color(0,0,0,0));
                    userPlaylistTree.setBackground((Color)null);

                    userPlaylistTree.setExpandedState(new TreePath(userPlaylistModel.getPathToRoot(userPlaylist)), userPlaylistTree.expandState);

                    dataPanel.add(userPlaylistTree, gridConstraints(dataPanel.getComponentCount(), 1, 6, 0, 3, 0));
                }
            }

//*********************************************************************************************************************************************
            // Add VSpacer at last
            dataPanel.add(component, gridConstraints(dataPanel.getComponentCount(), 6, 1, 0, 2, 0));

            dataPanel.updateUI();
            dataPanel.setVisible(true);
            scrollPane.setFocusable(true);
            scrollPane.setVisible(true);
            playlistWindowContent.updateUI();
            playlistWindowContent.setFocusable(true);
            playlistWindowContent.setVisible(true);
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
        this.currentPlayLists();
    }

    public JPanel getContent() {
        return playlistWindowContent;
    }

    public static void lazilyCheckPlayer(int retryCount, String playlist, String track) {
        if(MusicControlManager.currentTrackName == null) {
            if (MusicControlManager.playerType.equals("Desktop Player") && !SoftwareCoUtils.isSpotifyRunning() && retryCount > 0) {
                final int newRetryCount = retryCount - 1;
                new Thread(() -> {
                    try {
                        Thread.sleep(3000);
                        lazilyCheckPlayer(newRetryCount, playlist, track);
                    } catch (Exception ex) {
                        System.err.println(ex);
                    }
                }).start();
            } else if (MusicControlManager.playerType.equals("Web Player") || SoftwareCoUtils.isSpotifyRunning()) {
                if (MusicControlManager.currentDeviceId == null) {
                    final int newRetryCount = retryCount - 1;
                    new Thread(() -> {
                        try {
                            Thread.sleep(3000);
                            lazilyCheckPlayer(newRetryCount, playlist, track);
                        } catch (Exception ex) {
                            System.err.println(ex);
                        }
                    }).start();

                    MusicControlManager.getSpotifyDevices();
                } else {
                    PlayerControlManager.playSpotifyPlaylist(playlist, track);
                }
            }
        }
    }
}
