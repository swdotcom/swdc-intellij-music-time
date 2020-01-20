package com.softwareco.intellij.plugin.music;

import com.intellij.openapi.util.IconLoader;

import javax.swing.*;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.*;
import java.awt.event.FocusEvent;

public class PlaylistTreeRenderer extends DefaultTreeCellRenderer {
    Icon pauseIcon = IconLoader.getIcon("/com/softwareco/intellij/plugin/assets/pause_new.png");
    Icon playIcon = IconLoader.getIcon("/com/softwareco/intellij/plugin/assets/play_new.png");
    Icon musicIcon = IconLoader.getIcon("/com/softwareco/intellij/plugin/assets/music.png");
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
        tree.requestFocusInWindow();

        super.getTreeCellRendererComponent(
                tree, value, sel,
                expanded, leaf, row,
                true);

        if (leaf) {
            if(isCurrentTrack(value)) {
                if (!MusicControlManager.defaultbtn.equals("play")) {
                    setIcon(pauseIcon);
                    //setBackground(new Color(13, 41, 62, 188));
                } else {
                    setIcon(playIcon);
                    //setBackground(new Color(13, 41, 62, 188));
                }
            } else if(row >= 1) {
                if(sel) {
                    setIcon(playIcon);
                } else {
                    setIcon(musicIcon);
                }
            } else {
                setIcon(playlistIcon);
            }
        } else {
            if(isCurrentPlaylist(value)) {
                if (!MusicControlManager.defaultbtn.equals("play")) {
                    setIcon(pauseIcon);
                } else {
                    setIcon(playIcon);
                }
            } else {
                if(sel) {
                    setIcon(playIcon);
                } else {
                    setIcon(playlistIcon);
                }
            }
        }
        tree.requestFocusInWindow();

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
