/**
 * Copyright (c) 2018 by Software.com
 * All rights reserved
 */
package com.softwareco.intellij.plugin.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.softwareco.intellij.plugin.SoftwareCoUtils;

/**
 */
public class CustomDefaultActionGroup extends DefaultActionGroup {
    @Override
    public void update(AnActionEvent event) {
        boolean music = SoftwareCoUtils.isMusicTime();

        event.getPresentation().setVisible(!music);
        event.getPresentation().setIcon(AllIcons.General.Web);
    }
}