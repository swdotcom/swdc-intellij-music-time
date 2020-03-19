package com.softwareco.intellij.plugin;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;

public class PopupNotifier {
    private static final NotificationGroup NOTIFICATION_GROUP = new NotificationGroup("Groovy DSL errors", NotificationDisplayType.BALLOON, true);

    public static Notification notify(String content) {
        Project[] projects = ProjectManager.getInstance().getOpenProjects();
        final Editor editor = FileEditorManager.getInstance(projects[0]).getSelectedTextEditor();
        HintManager.getInstance().showErrorHint(editor, content);
        return notify(null, content);
    }

    public static Notification notify(Project project, String content) {
        final Notification notification = NOTIFICATION_GROUP.createNotification(content, NotificationType.ERROR);
        notification.notify(project);
        return notification;
    }
}
