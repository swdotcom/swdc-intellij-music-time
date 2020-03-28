package com.softwareco.intellij.plugin.music;

import com.google.gson.JsonObject;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.util.IconLoader;
import com.softwareco.intellij.plugin.SoftwareCoUtils;
import com.softwareco.intellij.plugin.slack.SlackControlManager;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.UnsupportedEncodingException;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

public class PopupMenuBuilder {
    public static final Logger LOG = Logger.getLogger("PlaylistManager");

    public static JPopupMenu popupMenu;
    public static JComboBox genres;
    public static String selectedType = "category";
    public static String selectedValue = "Familiar";
    public static Icon slackIcon = IconLoader.getIcon("/com/softwareco/intellij/plugin/assets/slack.png");
    public static Icon spotifyIcon = IconLoader.getIcon("/com/softwareco/intellij/plugin/assets/spotify.png");

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
                        PlayListCommands.updatePlaylists(3, null);
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
                        PlayListCommands.updatePlaylists(3, null);
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
                String[] playlists;
                if(PlayListCommands.userPlaylistIds.size() > 0) {
                    playlists = new String[PlayListCommands.userPlaylistIds.size() + 1];
                    int counter = 0;
                    playlists[counter] = "Create a new playlist";
                    counter++;
                    for (String id : PlayListCommands.userPlaylistIds) {
                        playlists[counter] = PlayListCommands.userPlaylists.get(id);
                        counter++;
                    }
                } else {
                    playlists = new String[] {"Create a new playlist"};
                }
                int index = SoftwareCoUtils.showMsgInputPrompt("Select playlist", "Spotify", spotifyIcon, playlists);
                if(index >= 0) {
                    String playlistName = null;
                    String error = null;
                    Set<String> tracks = new HashSet<>();
                    tracks.add(trackId);
                    if(index == 0) {
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
                                        PlayListCommands.updatePlaylists(4, status.get("id").getAsString());
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
                        JsonObject resp = PlayListCommands.addTracksInPlaylist(PlayListCommands.userPlaylistIds.get(index - 1), tracks);
                        if (resp != null) {
                            if (resp.has("error")) {
                                JsonObject err = resp.get("error").getAsJsonObject();
                                error = err.get("message").getAsString();
                            } else {
                                PlayListCommands.updatePlaylists(4, PlayListCommands.userPlaylistIds.get(index - 1));
                                playlistName = playlists[index];
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
                            PlayListCommands.updatePlaylists(4, playlistId);
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
        String[] channels;
        if(SlackControlManager.slackCacheState) {
            SlackControlManager.getSlackChannels();
            if(SlackControlManager.slackChannels.size() > 0) {
                channels = new String[SlackControlManager.slackChannels.size()];
                Set<String> ids = SlackControlManager.slackChannels.keySet();
                int counter = 0;
                for(String channel : ids) {
                    channels[counter] = channel;
                    counter++;
                }
            } else {
                channels = new String[1];
                channels[0] = "No Slack Channel Found";
            }
            JMenuItem slack = new JMenuItem("Slack");
            String[] finalChannels = channels;
            slack.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    int index = SoftwareCoUtils.showMsgInputPrompt("Select channel", "Slack", slackIcon, finalChannels);
                    String channel = null;
                    if(index >= 0)
                        channel = finalChannels[index];

                    if(channel != null) {
                        String slackId = SlackControlManager.slackChannels.get(channel);
                        JsonObject obj = new JsonObject();
                        obj.addProperty("channel", slackId);
                        obj.addProperty("text", "Check out this song \n" + uri);

                        boolean status = SlackControlManager.postMessage(obj.toString());
                        if (status)
                            SoftwareCoUtils.showMsgPrompt("Song shared successfully", new Color(55, 108, 137, 100));
                        else
                            SoftwareCoUtils.showMsgPrompt("Song sharing failed", new Color(120, 23, 50, 100));
                    }
                }
            });
            sharePopup.add(slack); // add to share menu
        } else {
            JMenuItem slack = new JMenuItem("Connect Slack");
            slack.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    try {
                        SlackControlManager.connectSlack();
                    } catch (UnsupportedEncodingException ex) {
                        ex.printStackTrace();
                    }
                }
            });
            sharePopup.add(slack); // add to share menu
        }

        popupMenu.add(copySongLink);
        popupMenu.add(new JSeparator());
        popupMenu.add(sharePopup);

        return popupMenu;
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
        String[] channels;
        if(SlackControlManager.slackCacheState) {
            SlackControlManager.getSlackChannels();
            if(SlackControlManager.slackChannels.size() > 0) {
                channels = new String[SlackControlManager.slackChannels.size()];
                Set<String> ids = SlackControlManager.slackChannels.keySet();
                int counter = 0;
                for(String channel : ids) {
                    channels[counter] = channel;
                    counter++;
                }
            } else {
                channels = new String[1];
                channels[0] = "No Slack Channel Found";
            }
            JMenuItem slack = new JMenuItem("Slack");
            String[] finalChannels = channels;
            slack.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    int index = SoftwareCoUtils.showMsgInputPrompt("Select channel", "Slack", slackIcon, finalChannels);
                    String channel = null;
                    if(index >= 0)
                        channel = finalChannels[index];

                    if(channel != null) {
                        String slackId = SlackControlManager.slackChannels.get(channel);
                        JsonObject obj = new JsonObject();
                        obj.addProperty("channel", slackId);
                        obj.addProperty("text", "Check out this playlist \n" + uri);

                        boolean status = SlackControlManager.postMessage(obj.toString());
                        if (status)
                            SoftwareCoUtils.showMsgPrompt("Playlist shared successfully", new Color(55, 108, 137, 100));
                        else
                            SoftwareCoUtils.showMsgPrompt("Playlist sharing failed", new Color(120, 23, 50, 100));
                    }
                }
            });
            sharePopup.add(slack); // add to share menu
        } else {
            JMenuItem slack = new JMenuItem("Connect Slack");
            slack.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    try {
                        SlackControlManager.connectSlack();
                    } catch (UnsupportedEncodingException ex) {
                        ex.printStackTrace();
                    }
                }
            });
            sharePopup.add(slack); // add to share menu
        }

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
