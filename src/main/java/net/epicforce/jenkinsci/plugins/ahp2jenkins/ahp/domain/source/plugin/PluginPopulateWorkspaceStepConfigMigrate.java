package net.epicforce.jenkinsci.plugins.ahp2jenkins.ahp.domain.source.plugin;

/**
 * PluginPopulateWorkspaceStepConfigMigrate.java
 *
 * Any plugin based populate workspace step will route here.  We
 * must "sub-route" it to the proper handler as they will ultimately
 * go to different Jenkins code blocks.
 *
 * @author sconley (sconley@epicforce.net)
 */

import net.epicforce.jenkinsci.plugins.ahp2jenkins.context.JenkinsContext;
import net.epicforce.jenkinsci.plugins.ahp2jenkins.context.JenkinsStep;
import net.epicforce.migrate.ahp.exception.MigrateException;

import com.urbancode.anthill3.domain.profile.BuildProfile;
import com.urbancode.anthill3.domain.repository.plugin.PluginRepository;
import com.urbancode.anthill3.domain.source.plugin.PluginSourceConfig;


public class PluginPopulateWorkspaceStepConfigMigrate extends JenkinsStep
{
    /**
     * The run step should figure out the routing based on the plugin
     * type.
     *
     * It seems pretty roundabout.how we have to figure out the plugin,
     * but that's how it rolls.
     *
     * @param context           The context of the process
     * @throws MigrateException on any kind of failure
     */
    @Override
    public void run(JenkinsContext context)
           throws MigrateException
    {
        // Grab a build profile and source config.
        BuildProfile bp = context.getWorkflow().getBuildProfile();

        // This should never happen
        if(bp == null) {
            throw new MigrateException("Null build profile for workflow");
        }

        // Get our plugin configuration
        PluginSourceConfig config = (PluginSourceConfig)bp.getSourceConfig();

        // This should also never happen
        if(config == null) {
            throw new MigrateException("Null source configuration");
        }

        // And we need repos
        PluginRepository[] repos = config.getRepositoryArray();

        if(repos.length == 0) {
            throw new MigrateException("No repos associated with workflow.");
        }

        // Figure out our plugin type from the repo config
        // Route accordingly.
        switch(repos[0].getPlugin().getPluginId()) {
            case "com.urbancode.anthill3.plugin.Git":
                GitPlugin gp = new GitPlugin();
                gp.run(context, config, repos);
                return;
            default:
                throw new MigrateException(
                    "Unknown AHP Source Control Plugin: " +
                    repos[0].getPlugin().getPluginId()
                );
        }
    }
}
