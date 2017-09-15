package net.epicforce.jenkinsci.plugins.ahp2jenkins.ahp.domain.publisher.changelog;

/*
 * ChangeLogPublisherStepConfigMigrate.java
 *
 * Wow this is a long path.
 *
 * Ahem.  Jenkins automatically grabs change logs, so I'm not sure
 * there's any point to implementing this.
 *
 * AM-26: Review if there's a need for this step.
 * AM-26: Note there's a Jenkins plugin that does this.  We should use
 * it if hte plugin is installed.  Plugin is:
 *
 * https://github.com/jenkinsci/git-changelog-plugin
 *
 * @author sconley (sconley@epicforce.net)
 */

import net.epicforce.jenkinsci.plugins.ahp2jenkins.ahp.StaticStep;

public class ChangeLogPublisherStepConfigMigrate extends StaticStep
{
    /**
     * @return a string explaining why this step was skipped
     */
    @Override
    public String getCode()
    {
        return "// Skipped an AHP Change Log Publish step.\n" +
               "// Jenkins does this automatically.\n";
    }
}
