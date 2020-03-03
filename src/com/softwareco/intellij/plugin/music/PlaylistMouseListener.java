package com.softwareco.intellij.plugin.music;

import com.softwareco.intellij.plugin.SoftwareCoSessionManager;
import com.softwareco.intellij.plugin.SoftwareCoUtils;
import com.softwareco.intellij.plugin.actions.MusicToolWindow;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class PlaylistMouseListener extends MouseAdapter {
    PlaylistTree playlistTree;

    public PlaylistMouseListener(PlaylistTree playlistTree) {
        this.playlistTree = playlistTree;
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        super.mouseClicked(e);

        PlaylistTreeNode node = (PlaylistTreeNode)
                playlistTree.getLastSelectedPathComponent();

        /* if nothing is selected */
        if (node == null || node.getId() == null) return;

        if(e.getButton() == 3) {
            if(node.getId().length() > 5) {
                JPopupMenu popupMenu;
                if (node.isLeaf()) {
                    PlaylistTreeNode root = (PlaylistTreeNode) node.getRoot();
                    popupMenu = PopupMenuBuilder.buildSongPopupMenu(node.getId(), root.getId());
                } else {
                    popupMenu = PopupMenuBuilder.buildPlaylistPopupMenu(node.getId());
                }

                popupMenu.show(e.getComponent(), e.getX(), e.getY());
            }
        } else if(e.getButton() == 1) {

            boolean activate = false;
            if (MusicControlManager.currentTrackName == null && MusicControlManager.spotifyDeviceIds.size() > 0) {
                activate = MusicControlManager.activateDevice(MusicControlManager.spotifyDeviceIds.get(0));
                SoftwareCoUtils.updatePlayerControls();
            }

            /* retrieve the node that was selected */
            if (node.isLeaf()) {
                PlaylistTreeNode root = (PlaylistTreeNode) node.getRoot();
                if (root.getId().equals(MusicControlManager.currentPlaylistId)
                        && node.getId().equals(MusicControlManager.currentTrackId) && MusicControlManager.currentTrackName != null) {
                    if (!activate) {
                        if (MusicControlManager.defaultbtn.equals("pause"))
                            PlayerControlManager.pauseSpotifyDevices();
                        else if (MusicControlManager.defaultbtn.equals("play"))
                            PlayerControlManager.playSpotifyDevices();
                    }
                } else {
                    if (!activate && MusicControlManager.currentTrackName == null &&
                            (MusicControlManager.playerType.equals("Web Player") || !SoftwareCoUtils.isSpotifyRunning())) {
                        MusicControlManager.launchPlayer();
                        MusicToolWindow.lazilyCheckPlayer(20, root.getId(), node.getId());
                    } else {
                        PlayerControlManager.playSpotifyPlaylist(root.getId(), node.getId());
                    }
                }
            } else {
                if (node.getId().equals(MusicControlManager.currentPlaylistId) && MusicControlManager.currentTrackName != null) {
                    if (!activate) {
                        if (MusicControlManager.defaultbtn.equals("pause"))
                            PlayerControlManager.pauseSpotifyDevices();
                        else if (MusicControlManager.defaultbtn.equals("play"))
                            PlayerControlManager.playSpotifyDevices();
                    }
                } else {
                    PlaylistTreeNode child = (PlaylistTreeNode) node.getFirstChild();

                    if (child.getId() != null) {
                        if (!activate && MusicControlManager.currentTrackName == null &&
                                (MusicControlManager.playerType.equals("Web Player") || !SoftwareCoUtils.isSpotifyRunning())) {
                            MusicControlManager.launchPlayer();
                            MusicToolWindow.lazilyCheckPlayer(20, node.getId(), child.getId());
                        } else {
                            PlayerControlManager.playSpotifyPlaylist(node.getId(), child.getId());
                        }
                    } else {
                        //SoftwareCoUtils.showMsgPrompt("Expand Playlist to load tracks");
                    }
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
}
