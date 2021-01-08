package com.musictime.intellij.plugin.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.musictime.intellij.plugin.tree.MusicToolWindow;
import org.jetbrains.annotations.NotNull;
import swdc.java.ops.manager.SlackManager;

public class DisconnectSlackAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent anActionEvent) { SlackManager.disconnectSlackWorkspace(() -> { MusicToolWindow.refresh();}); }
}
