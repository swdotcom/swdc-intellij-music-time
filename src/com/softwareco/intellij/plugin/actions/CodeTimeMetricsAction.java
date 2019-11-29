/**
 * Copyright (c) 2019 by Software.com
 * All rights reserved
 */
package com.softwareco.intellij.plugin.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.softwareco.intellij.plugin.SoftwareCoUtils;

/**
 * This is the code time metrics action for the menu items
 */
public class CodeTimeMetricsAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent anActionEvent) {
        SoftwareCoUtils.launchCodeTimeMetricsDashboard();
    }

    @Override
    public void update(AnActionEvent event) {
        event.getPresentation().setVisible(true);
        event.getPresentation().setEnabled(true);
    }
}
