package com.musictime.intellij.plugin.tree;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

public class MusicToolWindowFactory implements ToolWindowFactory {

    private static ToolWindow musicTimeWindow;
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        musicTimeWindow = toolWindow;
        MusicToolWindow mtToolWindow = new MusicToolWindow(toolWindow);
        ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();
        Content content = contentFactory.createContent(mtToolWindow.getContent(), "", false);
        toolWindow.getContentManager().addContent(content);
    }

    public static void showWindow() {
        if (musicTimeWindow != null) {
            ApplicationManager.getApplication().invokeLater(new Runnable() {
                public void run() {
                    if (!musicTimeWindow.isVisible()) {
                        musicTimeWindow.show(null);
                    }
                }
            });
        }
    }

    public static void toggleWindow() {
        if (musicTimeWindow != null) {
            ApplicationManager.getApplication().invokeLater(new Runnable() {
                public void run() {
                    if (!musicTimeWindow.isVisible()) {
                        musicTimeWindow.show(null);
                    } else {
                        musicTimeWindow.hide(null);
                    }
                }
            });
        }
    }

}
