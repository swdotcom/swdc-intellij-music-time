package com.musictime.intellij.plugin.music;

import com.google.gson.JsonObject;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.util.IconLoader;
import com.musictime.intellij.plugin.SoftwareCoUtils;
import com.musictime.intellij.plugin.tree.MusicToolWindow;
import com.musictime.intellij.plugin.tree.PlaylistAction;
import org.apache.commons.lang.StringUtils;
import swdc.java.ops.manager.SlackManager;
import swdc.java.ops.model.SlackChannel;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;
import java.util.logging.Logger;

public class PopupMenuBuilder {
    public static final Logger LOG = Logger.getLogger("PopupMenuBuilder");

    public static JPopupMenu popupMenu;
    public static JComboBox genres;
    public static String selectedType = "category";
    public static String selectedValue = "Familiar";
    public static Icon slackIcon = IconLoader.getIcon("/com/musictime/intellij/plugin/assets/slack.png");
    public static Icon spotifyIcon = IconLoader.getIcon("/com/musictime/intellij/plugin/assets/spotify.png");

    public static JPopupMenu buildWorkspaceMenu(String authId) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem removeWorkspaceItem = new JMenuItem("Remove workspace");
        removeWorkspaceItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                SlackManager.disconnectSlackAuth(authId, () -> { MusicToolWindow.refresh();});
            }
        });
        menu.add(removeWorkspaceItem);
        return menu;
    }

    public static JPopupMenu buildSongPopupMenu(String trackId, String playlistId) {
        popupMenu = new JPopupMenu();
        JMenu sharePopup = new JMenu("Share");
        String uri = "https://open.spotify.com/track/" + trackId;

        if(MusicControlManager.likedTracks.containsKey(trackId)) {
            /* remove from Liked Songs */
            JMenuItem removeLiked = new JMenuItem("Remove from your Liked Songs");
            removeLiked.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    boolean status = PlayerControlManager.likeSpotifyTrack(false, trackId);
                    if(status) {
                        PlayListCommands.updatePlaylists(PlaylistAction.UPDATE_LIKED_SONGS, null);
                        SoftwareCoUtils.showMsgPrompt("Removed from your Liked Songs", new Color(55, 108, 137, 100));
                    } else {
                        SoftwareCoUtils.showMsgPrompt("Failed to remove from Liked Songs", new Color(120, 23, 50, 100));
                    }
                }
            });
            popupMenu.add(removeLiked);
        } else {
            /* save to Liked Songs */
            JMenuItem addLiked = new JMenuItem("Save to your Liked Songs");
            addLiked.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    boolean status = PlayerControlManager.likeSpotifyTrack(true, trackId);
                    if(status) {
                        PlayListCommands.updatePlaylists(PlaylistAction.UPDATE_LIKED_SONGS, null);
                        SoftwareCoUtils.showMsgPrompt("Added to your Liked Songs", new Color(55, 108, 137, 100));
                    } else {
                        SoftwareCoUtils.showMsgPrompt("Failed to add in Liked Songs", new Color(120, 23, 50, 100));
                    }
                }
            });
            popupMenu.add(addLiked);
        }

        /* add to playlist */
        JMenuItem addPlaylist = new JMenuItem("Add to Playlist");
        addPlaylist.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String createPlaylistVal = "Create a new playlist";
                String[] playlists;
                if(PlayListCommands.userPlaylistIds.size() > 0) {
                    playlists = new String[PlayListCommands.userPlaylistIds.size() + 1];
                    int counter = 0;
                    playlists[counter] = createPlaylistVal;
                    counter++;
                    for (String id : PlayListCommands.userPlaylistIds) {
                        playlists[counter] = PlayListCommands.userPlaylists.get(id);
                        counter++;
                    }
                } else {
                    playlists = new String[] {createPlaylistVal};
                }
                String value = SoftwareCoUtils.showMsgInputPrompt("Select playlist", "Spotify", spotifyIcon, playlists);
                if(StringUtils.isNotBlank(value)) {
                    String playlistName = null;
                    String error = null;
                    Set<String> tracks = new HashSet<>();
                    tracks.add(trackId);
                    if(value.equals(createPlaylistVal)) {
                        playlistName = SoftwareCoUtils.showInputPrompt("Enter playlist name", "Spotify", spotifyIcon);
                        if (playlistName != null) {
                            JsonObject status = PlayListCommands.createPlaylist(playlistName);
                            if (status != null) {
                                JsonObject resp = PlayListCommands.addTracksInPlaylist(status.get("id").getAsString(), tracks);
                                if (resp != null) {
                                    if (resp.has("error")) {
                                        JsonObject err = resp.get("error").getAsJsonObject();
                                        error = err.get("message").getAsString();
                                    } else {
                                        PlayListCommands.updatePlaylists(PlaylistAction.UPDATE_PLAYLIST_BY_ID, status.get("id").getAsString());
                                    }
                                }
                            } else {
                                playlistName = null;
                                error = "Unable to create playlist, try again";
                            }
                        } else {
                            error = "Try again";
                        }
                    } else {
                        String playlist_id = PlayListCommands.getPlaylistIdByPlaylistName(value);
                        JsonObject resp = PlayListCommands.addTracksInPlaylist(playlist_id, tracks);
                        if (resp != null) {
                            if (resp.has("error")) {
                                JsonObject err = resp.get("error").getAsJsonObject();
                                error = err.get("message").getAsString();
                            } else {
                                PlayListCommands.updatePlaylists(PlaylistAction.UPDATE_PLAYLIST_BY_ID, playlist_id);
                                playlistName = value;
                            }
                        }
                    }

                    if (playlistName != null)
                        SoftwareCoUtils.showMsgPrompt("Added to '" + playlistName + "'", new Color(55, 108, 137, 100));
                    else
                        SoftwareCoUtils.showMsgPrompt("Failed to add: " + error, new Color(120, 23, 50, 100));
                }
            }
        });
        popupMenu.add(addPlaylist);

        if(PlayListCommands.userPlaylists.containsKey(playlistId)) {
            /* remove from playlist */
            JMenuItem removePlaylist = new JMenuItem("Remove from this Playlist");
            removePlaylist.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    String playlistName = null;
                    String error = null;
                    Set<String> tracks = new HashSet<>();
                    tracks.add(trackId);
                    JsonObject resp = PlayListCommands.removeTracksInPlaylist(playlistId, tracks);
                    if (resp != null) {
                        if(resp.has("error")) {
                            JsonObject err = resp.get("error").getAsJsonObject();
                            error = err.get("message").getAsString();
                        } else {
                            PlayListCommands.updatePlaylists(PlaylistAction.UPDATE_PLAYLIST_BY_ID, playlistId);
                            playlistName = PlayListCommands.userPlaylists.get(playlistId);
                        }
                    }

                    if (playlistName != null)
                        SoftwareCoUtils.showMsgPrompt("Removed from '" + playlistName + "'", new Color(55, 108, 137, 100));
                    else
                        SoftwareCoUtils.showMsgPrompt("Failed to remove: " + error, new Color(120, 23, 50, 100));
                }
            });
            popupMenu.add(removePlaylist);
        }

        /* copy song link */
        JMenuItem copySongLink = new JMenuItem("Copy Song Link");
        copySongLink.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                StringSelection stringSelection = new StringSelection(uri);
                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                clipboard.setContents(stringSelection, null);
                SoftwareCoUtils.showMsgPrompt("Song link Copied to Clipboard", new Color(55, 108, 137, 100));
            }
        });

        /* share menu items */
        /* share on facebook */
        JMenuItem facebook = new JMenuItem("Facebook");
        facebook.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String api = "https://www.facebook.com/sharer/sharer.php?u=" + uri + "&hashtag=#MusicTime";
                BrowserUtil.browse(api);
            }
        });

        /* share on tumbler */
        JMenuItem tumblr = new JMenuItem("Tumblr");
        tumblr.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String api = "http://tumblr.com/widgets/share/tool?canonicalUrl=" + uri + "&content=" + uri
                        + "&posttype=link&title=Check+out+this+song&caption=Software+Audio+Share&tags=MusicTime";
                BrowserUtil.browse(api);
            }
        });

        /* share on twitter */
        JMenuItem twitter = new JMenuItem("Twitter");
        twitter.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String api = "https://twitter.com/intent/tweet?text=Check+out+this+song&url=" + uri
                        + "&hashtags=MusicTime&via=software_hq";
                BrowserUtil.browse(api);
            }
        });

        /* share on whatsApp */
        JMenuItem whatsApp = new JMenuItem("WhatsApp");
        whatsApp.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String api = "https://api.whatsapp.com/send?text=Check+out+this+song:" + uri;
                BrowserUtil.browse(api);
            }
        });

        /* add to share option */
        sharePopup.add(facebook);
        sharePopup.add(tumblr);
        sharePopup.add(twitter);
        sharePopup.add(whatsApp);

        /* share on slack */
        JMenuItem slack = new JMenuItem("Slack");
        slack.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                List<SlackChannel> slackChannels = SlackManager.getSlackChannels(null);
                if (slackChannels != null) {
                    String[] finalChannels = getSlackChannelNames(slackChannels);

                    String channel = SoftwareCoUtils.showMsgInputPrompt("Select channel", "Slack", slackIcon, finalChannels);

                    if (StringUtils.isNotBlank(channel)) {
                        SlackChannel selectedChannel = getSelectedChannelByName(channel, slackChannels);
                        String message = "Check out this song \n" + uri;
                        SlackManager.postMessageToChannel(selectedChannel, selectedChannel.workspace_access_token, message);
                    }
                }
            }
        });
        sharePopup.add(slack);

        popupMenu.add(copySongLink);
        popupMenu.add(new JSeparator());
        popupMenu.add(sharePopup);

        return popupMenu;
    }

    private static String[] getSlackChannelNames(List<SlackChannel> channels) {
        String[] options = new String[channels.size()];
        for (int i = 0; i < channels.size(); i++) {
            options[i] = channels.get(i).name;
        }
        return options;
    }

    private static SlackChannel getSelectedChannelByName(String name, List<SlackChannel> channels) {
        for (SlackChannel channel : channels) {
            if (channel.name.equals(name)) {
                return channel;
            }
        }
        return null;
    }

    public static JPopupMenu buildPlaylistPopupMenu(String id) {
        popupMenu = new JPopupMenu();
        JMenu sharePopup = new JMenu("Share");
        String uri = "https://open.spotify.com/playlist/" + id;

        /* delete playlist option */
        if(!id.equals(PlayListCommands.topSpotifyPlaylistId)) {
            JMenuItem deletePlaylist = new JMenuItem("Delete Playlist");
            deletePlaylist.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    boolean status = PlayListCommands.removePlaylist(id);
                    if (status)
                        SoftwareCoUtils.showMsgPrompt("Playlist deleted successfully",new Color(55, 108, 137, 100));
                    else
                        SoftwareCoUtils.showMsgPrompt("Playlist deletion failed", new Color(120, 23, 50, 100));
                }
            });
            popupMenu.add(deletePlaylist);
        }

        /* copy playlist link option */
        JMenuItem copyPlaylistLink = new JMenuItem("Copy Playlist Link");
        copyPlaylistLink.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                StringSelection stringSelection = new StringSelection(uri);
                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                clipboard.setContents(stringSelection, null);
                SoftwareCoUtils.showMsgPrompt("Playlist link Copied to Clipboard", new Color(55, 108, 137, 100));
            }
        });

        /* share menu items */
        /* share on facebook */
        JMenuItem facebook = new JMenuItem("Facebook");
        facebook.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String api = "https://www.facebook.com/sharer/sharer.php?u=" + uri + "&hashtag=#MusicTime";
                BrowserUtil.browse(api);
            }
        });

        /* share on tumbler */
        JMenuItem tumblr = new JMenuItem("Tumblr");
        tumblr.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String api = "http://tumblr.com/widgets/share/tool?canonicalUrl=" + uri + "&content=" + uri
                        + "&posttype=link&title=Check+out+this+playlist&caption=Software+Audio+Share&tags=MusicTime";
                BrowserUtil.browse(api);
            }
        });

        /* share on twitter */
        JMenuItem twitter = new JMenuItem("Twitter");
        twitter.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String api = "https://twitter.com/intent/tweet?text=Check+out+this+playlist&url=" + uri
                        + "&hashtags=MusicTime&via=software_hq";
                BrowserUtil.browse(api);
            }
        });

        /* share on whatsApp */
        JMenuItem whatsApp = new JMenuItem("WhatsApp");
        whatsApp.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String api = "https://api.whatsapp.com/send?text=Check+out+this+playlist:" + uri;
                BrowserUtil.browse(api);
            }
        });

        /* add to share option */
        sharePopup.add(facebook);
        sharePopup.add(tumblr);
        sharePopup.add(twitter);
        sharePopup.add(whatsApp);

        /* share on slack */
        JMenuItem slack = new JMenuItem("Slack");
        slack.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                List<SlackChannel> slackChannels = SlackManager.getSlackChannels(null);
                if (slackChannels != null) {
                    String[] finalChannels = getSlackChannelNames(slackChannels);

                    String channel = SoftwareCoUtils.showMsgInputPrompt("Select channel", "Slack", slackIcon, finalChannels);

                    if (StringUtils.isNotBlank(channel)) {
                        SlackChannel selectedChannel = getSelectedChannelByName(channel, slackChannels);
                        String message = "Check out this playlist \n" + uri;
                        SlackManager.postMessageToChannel(selectedChannel, selectedChannel.workspace_access_token, message);
                    }
                }
            }
        });
        sharePopup.add(slack);

        popupMenu.add(copyPlaylistLink);
        popupMenu.add(new JSeparator());
        popupMenu.add(sharePopup);

        return popupMenu;
    }

    public static JPopupMenu buildCategoryFilter(String[] categories) {
        popupMenu = new JPopupMenu();
        for(String category: categories) {
            JMenuItem item = new JMenuItem(category);
            item.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    PlayListCommands.updateRecommendation("category", category);
                    selectedType = "category";
                    selectedValue = category;
                }
            });
            popupMenu.add(item);
        }

        return popupMenu;
    }
}
