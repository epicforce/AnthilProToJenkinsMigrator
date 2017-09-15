package net.epicforce.jenkinsci.plugins.ahp2jenkins.context;

/**
 * JenkinsStep.java
 *
 * This is an ease of use base class for Jenkins steps that
 * works with JenkinsContext instead of AbstractContext
 *
 * @author sconley (sconley@epicforce.net)
 */

import net.epicforce.migrate.ahp.context.AbstractContext;
import net.epicforce.migrate.ahp.exception.MigrateException;
import net.epicforce.migrate.ahp.migrate.AbstractStep;

public abstract class JenkinsStep extends AbstractStep
{
    /**
     * Implement this to avoid having to do the type casting
     * to AbstractStep
     *
     * @param context   Our context
     * @throws MigrateException on any error
     */
    public abstract void run(JenkinsContext context)
           throws MigrateException;

    /**
     * Wrapper to handle the type casting and to handle preconditions
     * and other common stuff.
     *
     * @param context       Our inbound context
     * @throws MigrateException on any error
     */
    @Override
    public void run(AbstractContext context)
           throws MigrateException
    {
        // AM-47 : Support precondition
        // AM-47 : Support ignore failures
        // AM-47 : support post process
        // AM-47 : Support timeout

        // Skip preflight only
        if(((JenkinsContext)context).getCurrentStep().isRunInPreflightOnly()) {
            ((JenkinsContext)context).addCode(
                "// Skipped an Anthill step that runs in preflight only:\n"
                + "// " + ((JenkinsContext)context).getCurrentStep().getName()
                + "\n"
            );

            return;
        }

        run((JenkinsContext)context);
    }
}
