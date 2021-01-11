package com.musictime.intellij.plugin.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.musictime.intellij.plugin.SoftwareCoSessionManager;
import com.musictime.intellij.plugin.music.MusicControlManager;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import swdc.java.ops.manager.FileUtilManager;

public class DisconnectSpotifyAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
        MusicControlManager.disConnectSpotify();
    }

    @Override
    public void update(AnActionEvent event) {
        boolean sessionFileExists = SoftwareCoSessionManager.softwareSessionFileExists();
        String name = FileUtilManager.getItem("name");
        boolean hasSpotifyAccess = MusicControlManager.hasSpotifyAccess();
        boolean isLoggedIn = (!sessionFileExists || StringUtils.isBlank(name) || !hasSpotifyAccess)
                ? false : true;

        event.getPresentation().setVisible(isLoggedIn);
        event.getPresentation().setEnabled(true);
    }
}
