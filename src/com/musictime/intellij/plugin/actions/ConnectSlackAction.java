package com.musictime.intellij.plugin.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.musictime.intellij.plugin.SoftwareCoSessionManager;
import com.musictime.intellij.plugin.SoftwareCoUtils;
import com.musictime.intellij.plugin.SoftwareCoSessionManager;
import com.musictime.intellij.plugin.SoftwareCoUtils;
import com.musictime.intellij.plugin.slack.SlackControlManager;
import org.jetbrains.annotations.NotNull;

import java.io.UnsupportedEncodingException;

public class ConnectSlackAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
        try {
            SlackControlManager.connectSlack();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void update(AnActionEvent event) {
        boolean sessionFileExists = SoftwareCoSessionManager.softwareSessionFileExists();
        boolean hasJwt = SoftwareCoSessionManager.jwtExists();
        boolean isLoggedIn = (!sessionFileExists || !hasJwt || !SlackControlManager.isSlackConncted())
                ? false : true;
        boolean spotifyConnected = SoftwareCoUtils.isSpotifyConncted();
        boolean serverOnline = SoftwareCoSessionManager.isServerOnline();

        event.getPresentation().setVisible(spotifyConnected && !isLoggedIn && serverOnline);
        event.getPresentation().setEnabled(true);
    }
}
