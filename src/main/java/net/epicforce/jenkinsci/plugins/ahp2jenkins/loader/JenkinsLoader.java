package net.epicforce.jenkinsci.plugins.ahp2jenkins.loader;

/*
 * JenkinsLoader.java
 *
 * Class loader for Jenkins processing steps.  As required by the AHP library.
 *
 * @author sconley (sconley@epicforce.net)
 */

import net.epicforce.jenkinsci.plugins.ahp2jenkins.ahp.UnknownStep;
import net.epicforce.jenkinsci.plugins.ahp2jenkins.context.JenkinsJob;
import net.epicforce.jenkinsci.plugins.ahp2jenkins.context.JenkinsWorkflow;
import net.epicforce.migrate.ahp.exception.UnsupportedClassException;
import net.epicforce.migrate.ahp.loader.DefaultLoader;
import net.epicforce.migrate.ahp.migrate.AbstractJob;
import net.epicforce.migrate.ahp.migrate.AbstractStep;
import net.epicforce.migrate.ahp.migrate.AbstractWorkflow;

import java.util.logging.Level;
import java.util.logging.Logger;


public class JenkinsLoader extends DefaultLoader
{
    private final static Logger LOG =
                            Logger.getLogger(JenkinsLoader.class.getName());

    /**
     * Override default workflow loader, providing actual workflow
     *
     * @return a JenkinsWorkflow
     */
    public AbstractWorkflow loadWorkflowClass()
    {
        return new JenkinsWorkflow();
    }

    /**
     * Override default job loader, providing actual job
     *
     * @return a JenkinsJob
     */
    public AbstractJob loadJobClass()
    {
        return new JenkinsJob();
    }

    /**
     * Override default loader step, but fallback to default.
     *
     * @param stepName      The step we're trying to load
     * @return AbstractStep to process
     * @throws UnsupportedClassException if we can't load it.
     */
    @Override
    public AbstractStep loadStepClass(final String stepName)
           throws UnsupportedClassException
    {
        try {
            return (AbstractStep)loadClass(
                stepName.replace(
                    "com.urbancode.anthill3.",
                    "net.epicforce.jenkinsci.plugins.ahp2jenkins.ahp."
                ) + "Migrate"
            );
        } catch(UnsupportedClassException e) {

            LOG.log(Level.WARNING, "Deferring to default due to error", e);

            /* This would run the default library code.
             * However, for now, we're going to use UnknownStep
            return super.loadStepClass(stepName);
             */

            return new UnknownStep();
        }
    }
}
