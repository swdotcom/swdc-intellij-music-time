package com.musictime.intellij.plugin.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.musictime.intellij.plugin.tree.MusicToolWindow;
import org.jetbrains.annotations.NotNull;
import swdc.java.ops.manager.SlackManager;

public class ConnectSlackAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
        try {
            SlackManager.connectSlackWorkspace(() -> { MusicToolWindow.refresh();});
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
