package com.musictime.intellij.plugin.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.musictime.intellij.plugin.SoftwareCoSessionManager;
import com.musictime.intellij.plugin.SoftwareCoUtils;
import com.musictime.intellij.plugin.SoftwareCoSessionManager;
import com.musictime.intellij.plugin.SoftwareCoUtils;
import org.jetbrains.annotations.NotNull;

public class MusicDashboardAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
        SoftwareCoSessionManager.launchMusicTimeMetricsDashboard();
    }

    @Override
    public void update(AnActionEvent event) {
        boolean sessionFileExists = SoftwareCoSessionManager.softwareSessionFileExists();
        boolean hasJwt = SoftwareCoSessionManager.jwtExists();
        boolean isLoggedIn = (!sessionFileExists || !hasJwt || !SoftwareCoUtils.isSpotifyConncted())
                ? false : true;
        boolean serverOnline = SoftwareCoSessionManager.isServerOnline();

        event.getPresentation().setVisible(isLoggedIn && serverOnline);
        event.getPresentation().setEnabled(true);
    }
}
