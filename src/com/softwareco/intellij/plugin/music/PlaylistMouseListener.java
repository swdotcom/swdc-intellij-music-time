package com.softwareco.intellij.plugin.music;

import com.google.gson.JsonObject;
import com.softwareco.intellij.plugin.SoftwareCoSessionManager;
import com.softwareco.intellij.plugin.SoftwareCoUtils;
import com.softwareco.intellij.plugin.actions.MusicToolWindow;
import com.softwareco.intellij.plugin.musicjava.SoftwareResponse;

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

            MusicControlManager.getSpotifyDevices();
            if(MusicControlManager.currentDeviceId == null && MusicControlManager.spotifyDeviceIds.size() > 0) {
                MusicControlManager.currentDeviceId = MusicControlManager.spotifyDeviceIds.get(0);
                MusicControlManager.currentDeviceName = MusicControlManager.spotifyDevices.get(MusicControlManager.currentDeviceId);
                if (MusicControlManager.currentDeviceName.contains("Web Player"))
                    MusicControlManager.playerType = "Web Player";
                else
                    MusicControlManager.playerType = "Desktop Player";
            }
            /* retrieve the node that was selected */
            if (node.isLeaf()) {
                PlaylistTreeNode root = (PlaylistTreeNode) node.getRoot();
                if (root.getId().equals(MusicControlManager.currentPlaylistId)
                        && node.getId().equals(MusicControlManager.currentTrackId) && MusicControlManager.currentTrackName != null) {
                    if (MusicControlManager.defaultbtn.equals("pause"))
                        PlayerControlManager.pauseSpotifyDevices();
                    else if (MusicControlManager.defaultbtn.equals("play"))
                        PlayerControlManager.playSpotifyDevices();
                } else {
                    if (MusicControlManager.currentTrackName == null && MusicControlManager.currentDeviceId == null) {
                        MusicControlManager.deviceActivated = false;
                        boolean launchState = MusicControlManager.launchPlayer(false, true);
                        if(launchState)
                            MusicToolWindow.lazilyCheckPlayer(20, root.getId(), node.getId());
                    } else {
                        SoftwareResponse response = PlayerControlManager.playSpotifyPlaylist(root.getId(), node.getId());
                        if(response.getCode() == 403 && !response.getJsonObj().isJsonNull() && response.getJsonObj().has("error")) {
                            JsonObject error = response.getJsonObj().getAsJsonObject("error");
                            if(error.get("reason").getAsString().equals("PREMIUM_REQUIRED"))
                                SoftwareCoUtils.showMsgPrompt(error.get("message").getAsString());
                            else if(error.get("reason").getAsString().equals("UNKNOWN"))
                                SoftwareCoUtils.showMsgPrompt("We were unable to play the selected track because it is unavailable in your market.");
                        }
                    }
                }
            } else {
                if (node.getId().equals(MusicControlManager.currentPlaylistId) && MusicControlManager.currentTrackName != null) {
                    if (MusicControlManager.defaultbtn.equals("pause"))
                        PlayerControlManager.pauseSpotifyDevices();
                    else if (MusicControlManager.defaultbtn.equals("play"))
                        PlayerControlManager.playSpotifyDevices();
                } else {
                    PlaylistTreeNode child = (PlaylistTreeNode) node.getFirstChild();

                    if (child.getId() != null) {
                        if (MusicControlManager.currentTrackName == null && MusicControlManager.currentDeviceId == null) {
                            MusicControlManager.deviceActivated = false;
                            boolean launchState = MusicControlManager.launchPlayer(false, true);
                            if(launchState)
                                MusicToolWindow.lazilyCheckPlayer(20, node.getId(), child.getId());
                        } else {
                            SoftwareResponse response = PlayerControlManager.playSpotifyPlaylist(node.getId(), child.getId());
                            if(response.getCode() == 403 && !response.getJsonObj().isJsonNull() && response.getJsonObj().has("error")) {
                                JsonObject error = response.getJsonObj().getAsJsonObject("error");
                                if(error.get("reason").getAsString().equals("PREMIUM_REQUIRED"))
                                    SoftwareCoUtils.showMsgPrompt(error.get("message").getAsString());
                                else if(error.get("reason").getAsString().equals("UNKNOWN"))
                                    SoftwareCoUtils.showMsgPrompt("We were unable to play the selected track because it is unavailable in your market.");
                            }
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
