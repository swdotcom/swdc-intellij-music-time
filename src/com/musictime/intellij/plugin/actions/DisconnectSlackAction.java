package com.musictime.intellij.plugin.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.musictime.intellij.plugin.SoftwareCoSessionManager;
import com.musictime.intellij.plugin.SoftwareCoUtils;
import com.musictime.intellij.plugin.SoftwareCoSessionManager;
import com.musictime.intellij.plugin.SoftwareCoUtils;
import com.musictime.intellij.plugin.fs.FileManager;
import com.musictime.intellij.plugin.music.MusicControlManager;
import com.musictime.intellij.plugin.music.PlayerControlManager;
import com.musictime.intellij.plugin.slack.SlackControlManager;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;

public class DisconnectSlackAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent anActionEvent) { SlackControlManager.disconnectSlack(); }

    @Override
    public void update(AnActionEvent event) {
        boolean sessionFileExists = SoftwareCoSessionManager.softwareSessionFileExists();
        String name = FileManager.getItem("name");
        boolean isLoggedIn = (!sessionFileExists || StringUtils.isBlank(name) || !SlackControlManager.isSlackConncted())
                ? false : true;
        boolean hasSpotifyAccess = MusicControlManager.hasSpotifyAccess();

        event.getPresentation().setVisible(hasSpotifyAccess && isLoggedIn);
        event.getPresentation().setEnabled(true);
    }
}
