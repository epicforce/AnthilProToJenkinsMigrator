package net.epicforce.jenkinsci.plugins.ahp2jenkins.ahp;

/**
 * UnknownStep.java
 *
 * At the moment, the AHP library does not have a reasonable default
 * fallback for handling steps that are not supported by the user.
 *
 * Rather than crashing out, this will put comments in the pipline
 * script suggesting we don't know how to handle the step.
 *
 * @author sconley (sconley@epicforce.net)
 */

import net.epicforce.jenkinsci.plugins.ahp2jenkins.context.JenkinsContext;
import net.epicforce.jenkinsci.plugins.ahp2jenkins.context.JenkinsStep;

public class UnknownStep extends JenkinsStep
{
    /**
     * Simple 'run' method to add comments that we don't know how to
     * handle this step.
     *
     * @param context   Our context
     */
    @Override
    public void run(JenkinsContext context)
    {
        context.addCode(
            "// The Anthill Pro to Jenkins module has encountered a step it "
            + " cannot\n" +
            "// handle.  The Anthill step class in question is:\n" +
            "// " + context.getCurrentStep().getClass().getName() + "\n"
        );
    }
}
