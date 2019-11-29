package com.softwareco.intellij.plugin;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginInstaller;
import com.intellij.ide.plugins.PluginStateListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.apache.http.client.methods.HttpDelete;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Logger;

public class PluginPostStartupActivity implements StartupActivity {

    public static final Logger log = Logger.getLogger("PluginPostStartupActivity");

    @Override
    public void runActivity(@NotNull Project project) {

        PluginInstaller.addStateListener(new PluginStateListener() {
            @Override
            public void install(@NotNull IdeaPluginDescriptor ideaPluginDescriptor) {

            }

            @Override
            public void uninstall(@NotNull IdeaPluginDescriptor ideaPluginDescriptor) {
                // send a quick update to the app to delete the integration
                SoftwareResponse response =
                        SoftwareCoUtils.makeApiCall(
                                "/integrations/" + SoftwareCoUtils.pluginId, HttpDelete.METHOD_NAME, null);
                if (response.isOk()) {
                    log.info("Code Time: Uninstalled plugin.");
                } else {
                    log.warning("Code Time: Failed to update Code Time about the uninstall event.");
                }
            }
        });
    }
}
