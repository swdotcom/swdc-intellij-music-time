package com.musictime.intellij.plugin.music;

import com.intellij.openapi.util.IconLoader;

import javax.swing.*;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.*;

public class PlaylistTreeRenderer extends DefaultTreeCellRenderer {
    Icon pauseIcon = IconLoader.getIcon("/com/musictime/intellij/plugin/assets/pause_new.png");
    Icon playIcon = IconLoader.getIcon("/com/musictime/intellij/plugin/assets/play_new.png");
    Icon musicIcon = IconLoader.getIcon("/com/musictime/intellij/plugin/assets/track.png");
    Icon emptyIcon = IconLoader.getIcon("/com/musictime/intellij/plugin/assets/empty.png");
    Icon playlistIcon;

    public PlaylistTreeRenderer(Icon playlistIcon) {
        this.playlistIcon = playlistIcon;
    }

    public Component getTreeCellRendererComponent(
            JTree tree,
            Object value,
            boolean sel,
            boolean expanded,
            boolean leaf,
            int row,
            boolean hasFocus) {
        if(sel)
            tree.requestFocusInWindow();

        super.getTreeCellRendererComponent(
                tree, value, sel,
                expanded, leaf, row,
                true);

        PlaylistTreeNode node = (PlaylistTreeNode)value;
        String id = node.getId();
        setToolTipText(node.getToolTip() + " (Right click for more options)");

        if (node.getIcon() != null) {
            setIcon(node.getIcon());
        } else if (node.isLeaf() && node.getParent() != null) {
            // it's a child node
            setIcon(musicIcon);
        } else if (!node.isLeaf() && node.getId() != null && node.getChildCount() > 0) {
            // it's a root node
            setIcon(playlistIcon);
        }

        return this;
    }

    protected boolean isCurrentTrack(Object value) {
        PlaylistTreeNode node =
                (PlaylistTreeNode)value;

        String id = node.getId();
        PlaylistTreeNode root = (PlaylistTreeNode) node.getRoot();
        if (MusicControlManager.currentPlaylistId != null && MusicControlManager.currentPlaylistId.equals(root.getId())
                && MusicControlManager.currentTrackId != null && MusicControlManager.currentTrackId.equals(id)) {
            return true;
        }

        return false;
    }

    protected boolean isCurrentPlaylist(Object value) {
        PlaylistTreeNode node =
                (PlaylistTreeNode)value;

        String id = node.getId();
        if (MusicControlManager.currentPlaylistId != null && MusicControlManager.currentPlaylistId.equals(id)) {
            return true;
        }

        return false;
    }
}
