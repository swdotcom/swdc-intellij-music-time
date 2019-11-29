/**
 * Copyright (c) 2019 by Software.com
 * All rights reserved
 */
package com.softwareco.intellij.plugin.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.softwareco.intellij.plugin.SoftwareCoSessionManager;
import com.softwareco.intellij.plugin.SoftwareCoUtils;

public class SoftwareLoginAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent anActionEvent) {
        SoftwareCoSessionManager.launchLogin();
    }

    @Override
    public void update(AnActionEvent event) {
        boolean sessionFileExists = SoftwareCoSessionManager.softwareSessionFileExists();
        boolean hasJwt = SoftwareCoSessionManager.jwtExists();
        boolean isLoggedIn = (!sessionFileExists || !hasJwt || !SoftwareCoUtils.isLoggedIn())
                ? false : true;
        boolean serverOnline = SoftwareCoSessionManager.isServerOnline();
        // only show the login menu item if the server is online
        // and they're not logged on
        event.getPresentation().setVisible(!isLoggedIn && serverOnline);
        event.getPresentation().setEnabled(true);
    }
}
