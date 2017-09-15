package net.epicforce.jenkinsci.plugins.ahp2jenkins.context;

/*
 * JenkinsJob.java
 *
 * This is a callback handler that's called at the top and bottom
 * of every job.
 *
 * A new instance of this class is created for each iteration of
 * the job process loop, which is important to keep in mind.
 *
 * @author sconley (sconley@epicforce.net)
 */

import net.epicforce.migrate.ahp.context.AbstractContext;
import net.epicforce.migrate.ahp.migrate.AbstractJob;

public class JenkinsJob extends AbstractJob
{
    // Keep track of our starting tab level so we can return to it.
    int startingLevel = 0;

    /**
     * preRun
     *
     * This is run at the start of a job.  Let's make a Jenkins
     * stage and increase the tab level.
     *
     * @param context   Our context
     */
    public void preRun(AbstractContext context) {
        JenkinsContext ctx = (JenkinsContext)context;
        startingLevel = ctx.getPipelineCodeTabLevel();

        ctx.addCode(
            "stage(\""
            + ctx.getCurrentJob().getName().replace("\"", "\\\"")
            + "\") {\n",
            1 // Increase tab level by 1
        );

        // AM-36: Job working directory
    }

    /**
     * Post run -- close our stage()
     *
     * @param context       Our context
     */
    public void postRun(AbstractContext context)
    {
        JenkinsContext ctx = (JenkinsContext)context;

        ctx.addCode("}", 0, -1);

        // Lower our tab level if needed
        while(ctx.getPipelineCodeTabLevel() > startingLevel) {
            ctx.addCode("}", 0, -1);
        }
    }
}
