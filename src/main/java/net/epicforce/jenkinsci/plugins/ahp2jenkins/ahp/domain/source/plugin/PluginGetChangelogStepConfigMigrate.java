package net.epicforce.jenkinsci.plugins.ahp2jenkins.ahp.domain.source.plugin;

/**
 * PluginGetChangelogStepConfigMigrate.java
 *
 * Jenkins does this automatically; there's no need to do anything
 * with this step.
 *
 * @author sconley (sconley@epicforce.net)
 */

import net.epicforce.jenkinsci.plugins.ahp2jenkins.ahp.StaticStep;


public class PluginGetChangelogStepConfigMigrate extends StaticStep
{
    /**
     * @return a string explaining why this step was skipped
     */
    @Override
    public String getCode()
    {
        return "// Skipped an AHP Get Changelog Step\n" +
               "// Jenkins does this automatically.\n";
    }
}
