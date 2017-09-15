package net.epicforce.jenkinsci.plugins.ahp2jenkins.ahp;

/**
 * CleanupStep.java
 *
 * Many (all?) of AHP's cleanup steps do the exact same thing, so
 * may as well centralize the code.
 *
 * Note that this will blow away the working directory, so its a
 * little "dangerous".  That's how AHP works, though.  The job
 * configuration would be what determines the working directory.
 *
 * @author sconley (sconley@epicforce.net)
 */

import net.epicforce.jenkinsci.plugins.ahp2jenkins.context.JenkinsContext;
import net.epicforce.jenkinsci.plugins.ahp2jenkins.context.JenkinsStep;


public class CleanupStep extends JenkinsStep
{
    /**
     * This is basically static code, but we want the step name
     * so we don't use the static step.
     */
    @Override
    public void run(JenkinsContext context)
    {
        context.addCode(
                "// Cleanup Step: " + context.getCurrentStep().getName() +
                "\ndeleteDir();\n"
        );
    }
}
