package com.musictime.intellij.plugin.tree;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.openapi.util.IconLoader;
import com.musictime.intellij.plugin.music.*;
import org.apache.commons.lang.StringUtils;
import swdc.java.ops.manager.SlackManager;
import swdc.java.ops.model.Integration;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TreeHelper {

    public static Map<String, PlaylistTree> playlistTreeMap = new HashMap<>();

    public static Icon pawIcon = IconLoader.getIcon("/com/musictime/intellij/plugin/assets/paw.png");
    public static Icon addIcon = IconLoader.getIcon("/com/musictime/intellij/plugin/assets/add.png");
    public static Icon slackIcon = IconLoader.getIcon("/com/musictime/intellij/plugin/assets/slack.png");
    public static Icon likeIcon = IconLoader.getIcon("/com/musictime/intellij/plugin/assets/heart-filled.png");
    public static Icon playlistIcon = IconLoader.getIcon("/com/musictime/intellij/plugin/assets/playlist-16x16.png");

    private static Map<String, String> trackNameMap = new HashMap<>();

    // Software Top 40 Playlist
    public static PlaylistTree buildSoftwarePlaylistTree() {
        JsonArray items = new JsonArray();
        JsonObject obj = PlayListCommands.topSpotifyTracks;
        if (obj != null && obj.has("items")) {
            items = obj.get("items").getAsJsonArray();
        }
        PlaylistTree pTree = TreeHelper.buildTreeNode("Software Top 40",
                PlayListCommands.topSpotifyPlaylistId,
                pawIcon,
                PlaylistAction.UPDATE_SOFTWARE_TOP_FORTY,
                TreeHelper.getNodeLabelsJsonTracks(items),
                "Your tracks will appear here");
        return pTree;
    }

    // liked songs
    public static PlaylistTree buildLikedSongsPlaylistTree() {
        JsonArray items = new JsonArray();
        JsonObject obj = PlayListCommands.likedTracks;
        if (obj != null && obj.has("items")) {
            items = obj.get("items").getAsJsonArray();
        }
        PlaylistTree pTree = TreeHelper.buildTreeNode("Liked Songs",
                PlayListCommands.likedPlaylistId,
                likeIcon,
                PlaylistAction.UPDATE_LIKED_SONGS,
                TreeHelper.getNodeLabelsJsonTracks(items),
                "Your tracks will appear here");
        return pTree;
    }

    // my ai playlist
    public static PlaylistTree buildAIPlaylistTree() {
        JsonArray items = new JsonArray();
        JsonObject obj = PlayListCommands.myAITopTracks;
        if (obj != null && obj.has("items")) {
            items = obj.get("items").getAsJsonArray();
        }
        PlaylistTree pTree = TreeHelper.buildTreeNode("My AI Top 40",
                PlayListCommands.myAIPlaylistId,
                pawIcon,
                PlaylistAction.UPDATE_MY_AI_PLAYLIST,
                TreeHelper.getNodeLabelsJsonTracks(items),
                "Your tracks will appear here");
        return pTree;
    }

    // non-liked playlist
    public static PlaylistTree buildNormalPlaylistTree(String playlistId) {
        JsonArray items = new JsonArray();
        JsonObject obj = PlayListCommands.userTracks.get(playlistId);
        if (obj != null && obj.has("tracks")) {
            JsonObject tracks = obj.get("tracks").getAsJsonObject();
            items = tracks.get("items").getAsJsonArray();
        }

        PlaylistTree pTree = TreeHelper.buildTreeNode(PlayListCommands.userPlaylists.get(playlistId),
                playlistId,
                playlistIcon,
                PlaylistAction.UPDATE_PLAYLIST_BY_ID,
                TreeHelper.getNodeLabelsJsonTracks(items),
                "Your tracks will appear here");
        return pTree;
    }

    public static PlaylistTree buildSlackWorkspacesNode() {
        List<Integration> workspaces = SlackManager.getSlackWorkspaces();
        List<NodeLabel> nodeLabels = new ArrayList<>();
        if (workspaces != null && workspaces.size() > 0) {
            for (Integration workspace : workspaces) {
                NodeLabel nodeLabel = new NodeLabel();
                nodeLabel.label = workspace.team_domain;
                nodeLabel.id = workspace.authId;
                nodeLabels.add(nodeLabel);
            }
        }

        // add the "Add workspace" node
        NodeLabel nodeLabel = new NodeLabel();
        nodeLabel.label = "Add workspace";
        nodeLabel.id = PlayListCommands.addSlackWorkspaceId;
        nodeLabel.icon = addIcon;
        nodeLabels.add(nodeLabel);

        PlaylistTree pTree = TreeHelper.buildTreeNode("Slack workspaces",
                PlayListCommands.slackWorkspacesId,
                null,
                null,
                nodeLabels,
                null);
        return pTree;
    }

    public static PlaylistTree buildTreeNode(String parentLabel, String parentId, Icon parentIcon, PlaylistAction playlistAction, List<NodeLabel> treeItems, String noItemsLabel) {
        PlaylistTreeNode playlistTreeNode = new PlaylistTreeNode(parentLabel, parentId);
        DefaultTreeModel defaultTreeModel = new DefaultTreeModel(playlistTreeNode);
        playlistTreeNode.setModel(defaultTreeModel);

        if (treeItems != null && treeItems.size() > 0) {
            for (NodeLabel item : treeItems) {
                PlaylistTreeNode node = new PlaylistTreeNode(item.label, item.id);
                node.setIcon(item.icon);
                playlistTreeNode.add(node);
            }
        } else if (StringUtils.isNotBlank(noItemsLabel)){
            // no items
            PlaylistTreeNode node = new PlaylistTreeNode(noItemsLabel, null);
            playlistTreeNode.add(node);
        }

        PlaylistTree pTree;
        if (playlistTreeMap != null && playlistTreeMap.containsKey(parentId)) {
            pTree = TreeHelper.playlistTreeMap.get(parentId);
            pTree.setModel(defaultTreeModel);
        } else {
            pTree = new PlaylistTree(defaultTreeModel);
            pTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
            pTree.setCellRenderer(new PlaylistTreeRenderer(parentIcon));
            pTree.addMouseListener(new PlaylistMouseListener(pTree));

            pTree.addTreeExpansionListener(new TreeExpansionListener() {
                @Override
                public void treeExpanded(TreeExpansionEvent event) {
                    if (playlistAction != null) {
                        if (playlistAction.equals(PlaylistAction.UPDATE_PLAYLIST_BY_ID)) {
                            PlaylistTreeNode node = (PlaylistTreeNode) event.getPath().getPathComponent(0);
                            PlayListCommands.updatePlaylists(PlaylistAction.UPDATE_PLAYLIST_BY_ID, node.getId());
                        } else {
                            PlayListCommands.updatePlaylists(playlistAction, null);
                        }
                    }
                }

                @Override
                public void treeCollapsed(TreeExpansionEvent event) {
                    //
                }
            });

            pTree.addMouseMotionListener(new TreeScanner());
            playlistTreeMap.put(parentId, pTree);
        }

        PlaylistTreeRenderer renderer = (PlaylistTreeRenderer) pTree.getCellRenderer();
        renderer.setBackgroundNonSelectionColor(new Color(0, 0, 0, 0));
        renderer.setBorderSelectionColor(new Color(0, 0, 0, 0));
        pTree.setBackground((Color) null);

        pTree.setExpandedState(new TreePath(defaultTreeModel.getPathToRoot(playlistTreeNode)), pTree.expandState);

        return pTree;
    }

    public static List<NodeLabel> getNodeLabelsJsonTracks(JsonArray items) {
        List<NodeLabel> nodeLabels = new ArrayList<>();
        if (items != null) {
            for (JsonElement array : items) {
                JsonObject track = array.getAsJsonObject().get("track").getAsJsonObject();
                String label = getTrackLabelSnippet(track);
                NodeLabel nodeLabel = new NodeLabel();
                nodeLabel.id = track.get("id").getAsString();
                nodeLabel.label = label;
                nodeLabels.add(nodeLabel);
            }
        }
        return nodeLabels;
    }

    public static String getTrackLabelSnippet(JsonObject track) {
        String id = track.get("id").getAsString();
        String trackName = trackNameMap.get(id);
        if (StringUtils.isEmpty(trackName)) {
            JsonArray artists = track.getAsJsonArray("artists");
            String artistNames = "";
            if (artists.size() > 0) {
                for (JsonElement artistArray : artists) {
                    artistNames += artistArray.getAsJsonObject().get("name").getAsString() + ", ";
                }
                artistNames = artistNames.substring(0, artistNames.lastIndexOf(","));
            }
            trackName = track.get("name").getAsString();
            if (trackName.length() > 40) {
                trackName = trackName.substring(0, 36) + "...";
                if (artistNames.length() > 0)
                    trackName += " (" + artistNames + ")";
            } else if (artistNames.length() > 0) {
                trackName += " (" + artistNames + ")";
            }
            trackNameMap.put(id, trackName);
        }
        return trackName;
    }
}
