package com.softwareco.intellij.plugin.actions;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.uiDesigner.core.GridConstraints;
import com.softwareco.intellij.plugin.SoftwareCoUtils;
import com.softwareco.intellij.plugin.music.*;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
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
    private static JButton reload;
    private Map<String, PlaylistTree> playlists = new HashMap<>();
    private JLabel spotifyState;

    private static int state = 0;

    public MusicToolWindow(ToolWindow toolWindow) {
        playlistWindowContent.setFocusable(true);

        refresh.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if(MusicControlManager.spotifyCacheState)
                    PlayListCommands.updatePlaylists();
                refreshButton();
                SoftwareCoUtils.showMsgPrompt("Playlist Refreshed Successfully !!!");
            }
        });
        Icon refreshIcon = IconLoader.getIcon("/com/softwareco/intellij/plugin/assets/refresh.png");
        refresh.setIcon(refreshIcon);

        reload = new JButton();
        reload.addActionListener(e -> {
            try {
                refreshButton();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        this.currentPlayLists();

        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
//        scrollPane.addMouseListener(new MouseAdapter() {
//            @Override
//            public void mouseClicked(MouseEvent e) {
//                super.mouseClicked(e);
//            }
//
//            @Override
//            public void mouseEntered(MouseEvent e) {
//                super.mouseEntered(e);
//            }
//
//            @Override
//            public void mouseExited(MouseEvent e) {
//                super.mouseExited(e);
//            }
//        });
//        scrollPane.addMouseMotionListener(new MouseMotionAdapter() {
//            @Override
//            public void mouseMoved(MouseEvent e) {
//                super.mouseMoved(e);
//            }
//        });
//
//        dataPanel.addMouseListener(new MouseAdapter() {
//            @Override
//            public void mouseClicked(MouseEvent e) {
//                super.mouseClicked(e);
//            }
//
//            @Override
//            public void mouseEntered(MouseEvent e) {
//                super.mouseEntered(e);
//            }
//
//            @Override
//            public void mouseExited(MouseEvent e) {
//                super.mouseExited(e);
//            }
//        });
//
//        dataPanel.addMouseMotionListener(new MouseMotionAdapter() {
//            @Override
//            public void mouseMoved(MouseEvent e) {
//                super.mouseMoved(e);
//            }
//        });
        playlistWindowContent.setBackground((Color) null);
    }

    public static void triggerRefresh() {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
                reload.doClick();
            }
        });
    }

    public synchronized void currentPlayLists() {
        // Get VSpacer component
        Component component = dataPanel.getComponent(dataPanel.getComponentCount() - 1);

        if(!SoftwareCoUtils.isSpotifyConncted()) {
            dataPanel.removeAll();
            DefaultListModel listModel = new DefaultListModel();

            Icon icon = IconLoader.getIcon("/com/softwareco/intellij/plugin/assets/spotify.png");
            JLabel connectedState = new JLabel();
            connectedState.setText("Connect Spotify");
            connectedState.setIcon(icon);
            connectedState.setOpaque(true);

            listModel.add(0, connectedState);
            JList<JLabel> list1 = new JList<>(listModel);
            list1.setVisibleRowCount(1);
            list1.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            list1.setCellRenderer(new ListRenderer());
            list1.addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    super.mouseMoved(e);
                    int row = list1.locationToIndex(e.getPoint());
                    list1.setSelectedIndex(row);
                }
            });
            list1.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    super.mouseClicked(e);

                    JList lst = (JList) e.getSource();
                    JLabel lbl = (JLabel) lst.getSelectedValue();
                    if(lbl.getText().equals("Connect Spotify")) {
                        MusicControlManager.connectSpotify();
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    super.mouseExited(e);
                    JList lst = (JList) e.getSource();
                    lst.clearSelection();
                }
            });
            list1.updateUI();
            dataPanel.add(list1, gridConstraints(dataPanel.getComponentCount(), 1, 2, 0, 3, 0));

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

            DefaultListModel listModel = new DefaultListModel();
            Icon towerIcon = IconLoader.getIcon("/com/softwareco/intellij/plugin/assets/tower.png");
            JLabel connectedState = new JLabel();
            connectedState.setText("Spotify Connected");
            connectedState.setIcon(towerIcon);
            connectedState.setOpaque(true);

            listModel.add(0, connectedState);
            //dataPanel.add(connectedState, gridConstraints(dataPanel.getComponentCount(), 1, 0, 8, 1, 1));

            Icon spotifyIcon = IconLoader.getIcon("/com/softwareco/intellij/plugin/assets/spotify.png");
            JLabel player = new JLabel();
            player.setIcon(spotifyIcon);
            player.setText(MusicControlManager.currentDeviceName == null ? "No Devices Detected" : "Listening on " + MusicControlManager.currentDeviceName);
            listModel.add(1, player);
            //dataPanel.add(player, gridConstraints(dataPanel.getComponentCount(), 1, 0, 8, 1, 1));

            Icon pawIcon = IconLoader.getIcon("/com/softwareco/intellij/plugin/assets/paw.png");
            JLabel web = new JLabel();
            web.setIcon(pawIcon);
            web.setText("See Web Analytics");
            listModel.add(2, web);

            JList<JLabel> labellist1 = new JList<>(listModel);
            labellist1.setVisibleRowCount(3);
            labellist1.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            labellist1.setCellRenderer(new ListRenderer());
            labellist1.addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    super.mouseMoved(e);
                    int row = labellist1.locationToIndex(e.getPoint());
                    labellist1.setSelectedIndex(row);
                }
            });
            labellist1.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    super.mouseClicked(e);

                    JList lst = (JList) e.getSource();
                    JLabel lbl = (JLabel) lst.getSelectedValue();
                    if(lbl.getText().equals("See Web Analytics")) {
                        //Code to call web analytics
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    super.mouseExited(e);
                    JList lst = (JList) e.getSource();
                    lst.clearSelection();
                }
            });
            labellist1.updateUI();

            dataPanel.add(labellist1, gridConstraints(dataPanel.getComponentCount(), 1, 6, 0, 3, 0));

            JSeparator seperator = new JSeparator();
            seperator.setAlignmentY(0.0f);
            seperator.setForeground(new Color(58, 86, 187));
            dataPanel.add(seperator, gridConstraints(dataPanel.getComponentCount(), 1, 6, 0, 1, 0));

            DefaultListModel listModel1 = new DefaultListModel();
            Icon gearIcon = IconLoader.getIcon("/com/softwareco/intellij/plugin/assets/settings.png");
            JLabel aiPlaylist = new JLabel();
            aiPlaylist.setIcon(gearIcon);
            if(PlayListCommands.myAIPlaylistId != null) {
                aiPlaylist.setText("Refresh My AI Playlist");
            } else {
                aiPlaylist.setText("Generate My AI Playlist");
            }
            listModel1.add(0, aiPlaylist);

            JList<JLabel> labellist2 = new JList<>(listModel1);
            labellist2.setVisibleRowCount(1);
            labellist2.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            labellist2.setCellRenderer(new ListRenderer());
            labellist2.addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    super.mouseMoved(e);
                    int row = labellist2.locationToIndex(e.getPoint());
                    labellist2.setSelectedIndex(row);
                }
            });
            labellist2.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    super.mouseClicked(e);

                    JList lst = (JList) e.getSource();
                    JLabel lbl = (JLabel) lst.getSelectedValue();
                    if(lbl.getText().equals("Refresh My AI Playlist")) {
                        PlayListCommands.refreshAIPlaylist();
                        SoftwareCoUtils.showMsgPrompt("My AI Playlist Refreshed Successfully !!!");
                    } else if(lbl.getText().equals("Generate My AI Playlist")) {
                        PlayListCommands.generateAIPlaylist();
                        SoftwareCoUtils.showMsgPrompt("My AI Playlist Generated Successfully !!!");
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    super.mouseExited(e);
                    JList lst = (JList) e.getSource();
                    lst.clearSelection();
                }
            });
            labellist2.updateUI();
            dataPanel.add(labellist2, gridConstraints(dataPanel.getComponentCount(), 1, 0, 8, 1, 0));


            // Software Top 40 Playlist ************************************************************
            PlaylistTreeNode list = new PlaylistTreeNode("Software Top 40", PlayListCommands.topSpotifyPlaylistId);
            DefaultTreeModel model = new DefaultTreeModel(list);
            list.setModel(model);
            JsonObject obj = PlayListCommands.topSpotifyTracks;
            if (obj != null && obj.has("items")) {
                for(JsonElement array : obj.get("items").getAsJsonArray()) {
                    JsonObject track = array.getAsJsonObject();
                    PlaylistTreeNode node = new PlaylistTreeNode(track.get("name").getAsString(), track.get("id").getAsString());
                    list.add(node);
                }
            } else {
                LOG.log(Level.INFO, "Music Time: Unable to get Top Tracks, null response");
            }

            PlaylistTree tree;
            if(playlists != null && playlists.containsKey(PlayListCommands.topSpotifyPlaylistId)) {
                tree = playlists.get(PlayListCommands.topSpotifyPlaylistId);
                tree.setModel(model);
            } else {
                tree = new PlaylistTree(model);
                tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
                tree.setCellRenderer(new PlaylistTreeRenderer(pawIcon));

                tree.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        super.mouseClicked(e);

                        PlaylistTreeNode node = (PlaylistTreeNode)
                                tree.getLastSelectedPathComponent();

                        /* if nothing is selected */
                        if (node == null) return;

                        /* retrieve the node that was selected */
                        if(node.isLeaf()) {
                            PlaylistTreeNode root = (PlaylistTreeNode) node.getRoot();
                            if(root.getId().equals(MusicControlManager.currentPlaylistId)
                                    && node.getId().equals(MusicControlManager.currentTrackId) && MusicControlManager.currentTrackName != null) {
                                if(MusicControlManager.defaultbtn.equals("pause"))
                                    PlayerControlManager.pauseSpotifyDevices();
                                else if(MusicControlManager.defaultbtn.equals("play"))
                                    PlayerControlManager.playSpotifyDevices();
                            } else {
                                MusicControlManager.currentPlaylistId = root.getId();
                                MusicControlManager.currentTrackId = node.getId();
                                if(MusicControlManager.currentTrackName == null) {
                                    MusicControlManager.launchPlayer();
                                    lazilyCheckPlayer(20, root.getId(), node.getId());
                                } else {
                                    PlayerControlManager.playSpotifyPlaylist(root.getId(), node.getId());
                                }
                            }
                        } else {
                            if(node.getId().equals(MusicControlManager.currentPlaylistId) && MusicControlManager.currentTrackName != null) {
                                if(MusicControlManager.defaultbtn.equals("pause"))
                                    PlayerControlManager.pauseSpotifyDevices();
                                else if(MusicControlManager.defaultbtn.equals("play"))
                                    PlayerControlManager.playSpotifyDevices();
                            } else {
                                PlaylistTreeNode child = (PlaylistTreeNode) node.getFirstChild();
                                MusicControlManager.currentPlaylistId = node.getId();
                                MusicControlManager.currentTrackId = child.getId();
                                if(MusicControlManager.currentTrackName == null) {
                                    MusicControlManager.launchPlayer();
                                    lazilyCheckPlayer(20, node.getId(), child.getId());
                                } else {
                                    PlayerControlManager.playSpotifyPlaylist(node.getId(), child.getId());
                                }
                            }
                        }
                    }

                    @Override
                    public void mouseEntered(MouseEvent e) {
                        super.mouseEntered(e);
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        super.mouseExited(e);
                        JTree tree=(JTree) e.getSource();
                        tree.clearSelection();
                    }
                });

                tree.addMouseMotionListener(new TreeScanner());

                playlists.put(PlayListCommands.topSpotifyPlaylistId, tree);
            }
            tree.setBackground((Color) null);
            //tree.setRequestFocusEnabled(true);
            tree.requestFocusInWindow();
            PlaylistTreeRenderer renderer = (PlaylistTreeRenderer) tree.getCellRenderer();
            renderer.setBackgroundNonSelectionColor(new Color(0,0,0,0));
            renderer.setBorderSelectionColor(new Color(0,0,0,0));
            //renderer.setBackgroundSelectionColor(new Color(13, 41, 62, 188));

            tree.setExpandedState(new TreePath(model.getPathToRoot(list)), tree.expandState);

            dataPanel.add(tree, gridConstraints(dataPanel.getComponentCount(), 1, 6, 0, 3, 0));

            // My AI Top 40 Playlist ********************************************************
            if(PlayListCommands.myAIPlaylistId != null) {
                PlaylistTreeNode list1 = new PlaylistTreeNode("My AI Top 40", PlayListCommands.myAIPlaylistId);
                DefaultTreeModel model1 = new DefaultTreeModel(list1);
                list1.setModel(model1);
                JsonObject obj1 = PlayListCommands.myAITopTracks;
                if (obj1 != null && obj1.has("tracks")) {
                    JsonObject tracks = obj1.get("tracks").getAsJsonObject();
                    for (JsonElement array : tracks.get("items").getAsJsonArray()) {
                        JsonObject track = array.getAsJsonObject().get("track").getAsJsonObject();
                        PlaylistTreeNode node = new PlaylistTreeNode(track.get("name").getAsString(), track.get("id").getAsString());
                        list1.add(node);
                    }
                } else {
                    LOG.log(Level.INFO, "Music Time: Unable to get AI Top 40 Tracks, null response");
                }

                PlaylistTree tree1;
                if(playlists != null && playlists.containsKey(PlayListCommands.myAIPlaylistId)) {
                    tree1 = playlists.get(PlayListCommands.myAIPlaylistId);
                    tree1.setModel(model1);
                } else {
                    tree1 = new PlaylistTree(model1);
                    tree1.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
                    tree1.setCellRenderer(new PlaylistTreeRenderer(pawIcon));

                    tree1.addMouseListener(new MouseAdapter() {
                        @Override
                        public void mouseClicked(MouseEvent e) {
                            super.mouseClicked(e);

                            PlaylistTreeNode node = (PlaylistTreeNode)
                                    tree1.getLastSelectedPathComponent();

                            /* if nothing is selected */
                            if (node == null) return;

                            /* retrieve the node that was selected */
                            if(node.isLeaf()) {
                                PlaylistTreeNode root = (PlaylistTreeNode) node.getRoot();
                                if(root.getId().equals(MusicControlManager.currentPlaylistId)
                                        && node.getId().equals(MusicControlManager.currentTrackId) && MusicControlManager.currentTrackName != null) {
                                    if(MusicControlManager.defaultbtn.equals("pause"))
                                        PlayerControlManager.pauseSpotifyDevices();
                                    else if(MusicControlManager.defaultbtn.equals("play"))
                                        PlayerControlManager.playSpotifyDevices();
                                } else {
                                    MusicControlManager.currentPlaylistId = root.getId();
                                    MusicControlManager.currentTrackId = node.getId();

                                    if(MusicControlManager.currentTrackName == null) {
                                        MusicControlManager.launchPlayer();
                                        lazilyCheckPlayer(20, root.getId(), node.getId());
                                    } else {
                                        PlayerControlManager.playSpotifyPlaylist(root.getId(), node.getId());
                                    }
                                }
                            } else {
                                if(node.getId().equals(MusicControlManager.currentPlaylistId) && MusicControlManager.currentTrackName != null) {
                                    if(MusicControlManager.defaultbtn.equals("pause"))
                                        PlayerControlManager.pauseSpotifyDevices();
                                    else if(MusicControlManager.defaultbtn.equals("play"))
                                        PlayerControlManager.playSpotifyDevices();
                                } else {
                                    PlaylistTreeNode child = (PlaylistTreeNode) node.getFirstChild();
                                    MusicControlManager.currentPlaylistId = node.getId();
                                    MusicControlManager.currentTrackId = child.getId();

                                    if(MusicControlManager.currentTrackName == null) {
                                        MusicControlManager.launchPlayer();
                                        lazilyCheckPlayer(20, node.getId(), child.getId());
                                    } else {
                                        PlayerControlManager.playSpotifyPlaylist(node.getId(), child.getId());
                                    }
                                }
                            }
                        }

                        @Override
                        public void mouseEntered(MouseEvent e) {
                            super.mouseEntered(e);
                        }

                        @Override
                        public void mouseExited(MouseEvent e) {
                            super.mouseExited(e);
                            JTree tree=(JTree) e.getSource();
                            tree.clearSelection();
                        }
                    });

                    tree1.addMouseMotionListener(new TreeScanner());

                    playlists.put(PlayListCommands.myAIPlaylistId, tree1);
                }
                PlaylistTreeRenderer renderer1 = (PlaylistTreeRenderer) tree1.getCellRenderer();
                renderer1.setBackgroundNonSelectionColor(new Color(0,0,0,0));
                renderer1.setBorderSelectionColor(new Color(0,0,0,0));
                tree1.setBackground((Color)null);

                tree1.setExpandedState(new TreePath(model1.getPathToRoot(list1)), tree1.expandState);

                dataPanel.add(tree1, gridConstraints(dataPanel.getComponentCount(), 1, 6, 0, 3, 0));
            }

            // Liked Songs Playlist *******************************************************
            if(MusicControlManager.likedTracks.size() > 0) {
                PlaylistTreeNode list2 = new PlaylistTreeNode("Liked Songs", PlayListCommands.likedPlaylistId);
                DefaultTreeModel model2 = new DefaultTreeModel(list2);
                list2.setModel(model2);
                JsonObject obj2 = PlayListCommands.likedTracks;
                if (obj2 != null && obj2.has("items")) {
                    for (JsonElement array : obj2.get("items").getAsJsonArray()) {
                        JsonObject track = array.getAsJsonObject().getAsJsonObject("track");
                        PlaylistTreeNode node = new PlaylistTreeNode(track.get("name").getAsString(), track.get("id").getAsString());
                        list2.add(node);
                    }
                } else {
                    LOG.log(Level.INFO, "Music Time: Unable to get Liked Tracks, null response");
                }

                PlaylistTree tree2;
                if (playlists != null && playlists.containsKey(PlayListCommands.likedPlaylistId)) {
                    tree2 = playlists.get(PlayListCommands.likedPlaylistId);
                    tree2.setModel(model2);
                } else {
                    tree2 = new PlaylistTree(model2);
                    tree2.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
                    tree2.setCellRenderer(new PlaylistTreeRenderer(spotifyIcon));

                    tree2.addMouseListener(new MouseAdapter() {
                        @Override
                        public void mouseClicked(MouseEvent e) {
                            super.mouseClicked(e);

                            PlaylistTreeNode node = (PlaylistTreeNode)
                                    tree2.getLastSelectedPathComponent();

                            /* if nothing is selected */
                            if (node == null) return;

                            /* retrieve the node that was selected */
                            if(node.isLeaf()) {
                                PlaylistTreeNode root = (PlaylistTreeNode) node.getRoot();
                                if(root.getId().equals(MusicControlManager.currentPlaylistId)
                                        && node.getId().equals(MusicControlManager.currentTrackId) && MusicControlManager.currentTrackName != null) {
                                    if(MusicControlManager.defaultbtn.equals("pause"))
                                        PlayerControlManager.pauseSpotifyDevices();
                                    else if(MusicControlManager.defaultbtn.equals("play"))
                                        PlayerControlManager.playSpotifyDevices();
                                } else {
                                    MusicControlManager.currentPlaylistId = root.getId();
                                    MusicControlManager.currentTrackId = node.getId();

                                    if(MusicControlManager.currentTrackName == null) {
                                        MusicControlManager.launchPlayer();
                                        lazilyCheckPlayer(20, root.getId(), node.getId());
                                    } else {
                                        PlayerControlManager.playSpotifyPlaylist(root.getId(), node.getId());
                                    }
                                }
                            } else {
                                if(node.getId().equals(MusicControlManager.currentPlaylistId) && MusicControlManager.currentTrackName != null) {
                                    if(MusicControlManager.defaultbtn.equals("pause"))
                                        PlayerControlManager.pauseSpotifyDevices();
                                    else if(MusicControlManager.defaultbtn.equals("play"))
                                        PlayerControlManager.playSpotifyDevices();
                                } else {
                                    PlaylistTreeNode child = (PlaylistTreeNode) node.getFirstChild();
                                    MusicControlManager.currentPlaylistId = node.getId();
                                    MusicControlManager.currentTrackId = child.getId();

                                    if(MusicControlManager.currentTrackName == null) {
                                        MusicControlManager.launchPlayer();
                                        lazilyCheckPlayer(20, node.getId(), child.getId());
                                    } else {
                                        PlayerControlManager.playSpotifyPlaylist(node.getId(), child.getId());
                                    }
                                }
                            }
                        }

                        @Override
                        public void mouseEntered(MouseEvent e) {
                            super.mouseEntered(e);
                        }

                        @Override
                        public void mouseExited(MouseEvent e) {
                            super.mouseExited(e);
                            JTree tree=(JTree) e.getSource();
                            tree.clearSelection();
                        }
                    });

                    tree2.addMouseMotionListener(new TreeScanner());

                    playlists.put(PlayListCommands.likedPlaylistId, tree2);
                }
                PlaylistTreeRenderer renderer2 = (PlaylistTreeRenderer) tree2.getCellRenderer();
                renderer2.setBackgroundNonSelectionColor(new Color(0,0,0,0));
                renderer2.setBorderSelectionColor(new Color(0,0,0,0));
                tree2.setBackground((Color)null);

                tree2.setExpandedState(new TreePath(model2.getPathToRoot(list2)), tree2.expandState);

                dataPanel.add(tree2, gridConstraints(dataPanel.getComponentCount(), 1, 6, 0, 3, 0));
            }

            // Get User Playlists *********************************************************
            if(PlayListCommands.userPlaylistIds.size() > 0) {
                JSeparator seperator1 = new JSeparator();
                seperator1.setAlignmentY(0.0f);
                seperator1.setForeground(new Color(58, 86, 187));
                dataPanel.add(seperator1, gridConstraints(dataPanel.getComponentCount(), 1, 6, 0, 1, 0));

                for(String playlistId : PlayListCommands.userPlaylistIds) {
                    JsonObject obj1 = PlayListCommands.userTracks.get(playlistId);
                    if (obj1 != null && obj1.has("tracks")) {
                        PlaylistTreeNode list3 = new PlaylistTreeNode(obj1.get("name").getAsString(), playlistId);
                        DefaultTreeModel model3 = new DefaultTreeModel(list3);
                        list3.setModel(model3);
                        JsonObject tracks = obj1.get("tracks").getAsJsonObject();
                        int count = 0;
                        for(JsonElement array : tracks.get("items").getAsJsonArray()) {
                            JsonObject track = array.getAsJsonObject().get("track").getAsJsonObject();
                            PlaylistTreeNode node = new PlaylistTreeNode(track.get("name").getAsString(), track.get("id").getAsString());
                            list3.add(node);
                            count++;
                        }

                        PlaylistTree tree3;
                        if(playlists != null && playlists.containsKey(playlistId)) {
                            tree3 = playlists.get(playlistId);
                            tree3.setModel(model3);
                        } else {
                            tree3 = new PlaylistTree(model3);
                            tree3.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
                            tree3.setCellRenderer(new PlaylistTreeRenderer(spotifyIcon));

                            tree3.addMouseListener(new MouseAdapter() {
                                @Override
                                public void mouseClicked(MouseEvent e) {
                                    super.mouseClicked(e);

                                    PlaylistTreeNode node = (PlaylistTreeNode)
                                            tree3.getLastSelectedPathComponent();

                                    /* if nothing is selected */
                                    if (node == null) return;

                                    /* retrieve the node that was selected */
                                    if(node.isLeaf()) {
                                        PlaylistTreeNode root = (PlaylistTreeNode) node.getRoot();
                                        if(root.getId().equals(MusicControlManager.currentPlaylistId)
                                                && node.getId().equals(MusicControlManager.currentTrackId) && MusicControlManager.currentTrackName != null) {
                                            if(MusicControlManager.defaultbtn.equals("pause"))
                                                PlayerControlManager.pauseSpotifyDevices();
                                            else if(MusicControlManager.defaultbtn.equals("play"))
                                                PlayerControlManager.playSpotifyDevices();
                                        } else {
                                            MusicControlManager.currentPlaylistId = root.getId();
                                            MusicControlManager.currentTrackId = node.getId();

                                            if(MusicControlManager.currentTrackName == null) {
                                                MusicControlManager.launchPlayer();
                                                lazilyCheckPlayer(20, root.getId(), node.getId());
                                            } else {
                                                PlayerControlManager.playSpotifyPlaylist(root.getId(), node.getId());
                                            }
                                        }
                                    } else {
                                        if(node.getId().equals(MusicControlManager.currentPlaylistId) && MusicControlManager.currentTrackName != null) {
                                            if(MusicControlManager.defaultbtn.equals("pause"))
                                                PlayerControlManager.pauseSpotifyDevices();
                                            else if(MusicControlManager.defaultbtn.equals("play"))
                                                PlayerControlManager.playSpotifyDevices();
                                        } else {
                                            PlaylistTreeNode child = (PlaylistTreeNode) node.getFirstChild();
                                            MusicControlManager.currentPlaylistId = node.getId();
                                            MusicControlManager.currentTrackId = child.getId();

                                            if(MusicControlManager.currentTrackName == null) {
                                                MusicControlManager.launchPlayer();
                                                lazilyCheckPlayer(20, node.getId(), child.getId());
                                            } else {
                                                PlayerControlManager.playSpotifyPlaylist(node.getId(), child.getId());
                                            }
                                        }
                                    }
                                }

                                @Override
                                public void mouseEntered(MouseEvent e) {
                                    super.mouseEntered(e);
                                }

                                @Override
                                public void mouseExited(MouseEvent e) {
                                    super.mouseExited(e);
                                    JTree tree=(JTree) e.getSource();
                                    tree.clearSelection();
                                }
                            });

                            tree3.addMouseMotionListener(new TreeScanner());

                            playlists.put(playlistId, tree3);
                        }
                        PlaylistTreeRenderer renderer3 = (PlaylistTreeRenderer) tree3.getCellRenderer();
                        renderer3.setBackgroundNonSelectionColor(new Color(0,0,0,0));
                        renderer3.setBorderSelectionColor(new Color(0,0,0,0));
                        tree3.setBackground((Color)null);

                        tree3.setExpandedState(new TreePath(model3.getPathToRoot(list3)), tree3.expandState);

                        dataPanel.add(tree3, gridConstraints(dataPanel.getComponentCount(), 1, 6, 0, 3, 0));

                    }
                }
            }

            // Add VSpacer at last
            dataPanel.add(component, gridConstraints(dataPanel.getComponentCount(), 6, 1, 0, 2, 0));

            //dataPanel.setFocusable(true);
            dataPanel.updateUI();
            //dataPanel.setVisible(true);
            //scrollPane.setFocusable(true);
            //scrollPane.updateUI();
            //scrollPane.setVisible(true);
            //playlistWindowContent.setFocusable(true);
            //playlistWindowContent.updateUI();
            //playlistWindowContent.setVisible(true);
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

    private void refreshButton() {
        this.currentPlayLists();
    }

    public JPanel getContent() {
        return playlistWindowContent;
    }

    protected void lazilyCheckPlayer(int retryCount, String playlist, String track) {
        if(MusicControlManager.playerType.equals("Desktop Player") && !SoftwareCoUtils.isSpotifyRunning() && retryCount > 0) {
            final int newRetryCount = retryCount - 1;
            new Thread(() -> {
                try {
                    Thread.sleep(3000);
                    lazilyCheckPlayer(newRetryCount, playlist, track);
                }
                catch (Exception ex){
                    System.err.println(ex);
                }
            }).start();
        } else if(MusicControlManager.playerType.equals("Web Player") || SoftwareCoUtils.isSpotifyRunning()) {
            MusicControlManager.currentPlaylistId = playlist;
            MusicControlManager.currentTrackId = track;
            PlayerControlManager.playSpotifyPlaylist(playlist, track);
        }
    }
}
