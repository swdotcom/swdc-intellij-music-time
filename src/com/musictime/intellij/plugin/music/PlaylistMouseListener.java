package com.musictime.intellij.plugin.music;

import com.google.gson.JsonObject;
import com.musictime.intellij.plugin.SoftwareCoUtils;
import com.musictime.intellij.plugin.SoftwareResponse;
import com.musictime.intellij.plugin.actions.MusicToolWindow;

import javax.swing.*;
import java.awt.*;
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
                        if (launchState) {
                            MusicToolWindow.lazilyCheckPlayer(5, root.getId(), node.getId(), node.getName());
                        }
                    } else {
                        SoftwareResponse response = PlayerControlManager.playSpotifyPlaylist(root.getId(), node.getId(), node.getName());
                        if(response.getCode() == 403 && !response.getJsonObj().isJsonNull() && response.getJsonObj().has("error")) {
                            JsonObject error = response.getJsonObj().getAsJsonObject("error");
                            if(error.get("reason").getAsString().equals("PREMIUM_REQUIRED"))
                                SoftwareCoUtils.showMsgPrompt(error.get("message").getAsString() + "<br>only desktop app allowed, close web player", new Color(120, 23, 50, 100));
                            else if(error.get("reason").getAsString().equals("UNKNOWN"))
                                SoftwareCoUtils.showMsgPrompt("We were unable to play the selected track<br> because it is unavailable in your market.", new Color(120, 23, 50, 100));
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
                            if (launchState) {
                                MusicToolWindow.lazilyCheckPlayer(5, node.getId(), child.getId(), child.getName());
                            }
                        } else {
                            SoftwareResponse response = PlayerControlManager.playSpotifyPlaylist(node.getId(), child.getId(), child.getName());
                            if(response.getCode() == 403 && !response.getJsonObj().isJsonNull() && response.getJsonObj().has("error")) {
                                JsonObject error = response.getJsonObj().getAsJsonObject("error");
                                if(error.get("reason").getAsString().equals("PREMIUM_REQUIRED"))
                                    SoftwareCoUtils.showMsgPrompt(error.get("message").getAsString() + "<br>only desktop app allowed, close web player", new Color(120, 23, 50, 100));
                                else if(error.get("reason").getAsString().equals("UNKNOWN"))
                                    SoftwareCoUtils.showMsgPrompt("We were unable to play the selected track<br> because it is unavailable in your market.", new Color(120, 23, 50, 100));
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
