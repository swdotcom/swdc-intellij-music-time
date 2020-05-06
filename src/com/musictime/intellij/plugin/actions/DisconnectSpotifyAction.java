package com.musictime.intellij.plugin.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.musictime.intellij.plugin.SoftwareCoSessionManager;
import com.musictime.intellij.plugin.SoftwareCoUtils;
import com.musictime.intellij.plugin.music.MusicControlManager;
import com.musictime.intellij.plugin.SoftwareCoSessionManager;
import com.musictime.intellij.plugin.SoftwareCoUtils;
import com.musictime.intellij.plugin.music.MusicControlManager;
import org.jetbrains.annotations.NotNull;

public class DisconnectSpotifyAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent anActionEvent) { MusicControlManager.disConnectSpotify(); }

    @Override
    public void update(AnActionEvent event) {
        boolean sessionFileExists = SoftwareCoSessionManager.softwareSessionFileExists();
        boolean hasJwt = SoftwareCoSessionManager.jwtExists();
        boolean hasSpotifyAccess = MusicControlManager.hasSpotifyAccess();
        boolean isLoggedIn = (!sessionFileExists || !hasJwt || !hasSpotifyAccess)
                ? false : true;
        boolean serverOnline = SoftwareCoSessionManager.isServerOnline();

        event.getPresentation().setVisible(isLoggedIn && serverOnline);
        event.getPresentation().setEnabled(true);
    }
}
