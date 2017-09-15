package net.epicforce.jenkinsci.plugins.ahp2jenkins;

/*
 * PipelineStep.java
 *
 * This is a Jenkins Pipeline script for doing recursive variable
 * replacements (i.e. nested variables... if z=2, y=${z}, then x=${y}
 * it will parse out to 2).  Also, beanshell is procssed by this,
 * which is properties like ${bsh:whatever}
 *
 * The structure of pipeline steps is kind twisty; we have a
 * wrapper class that basically acts as a data transport layer
 * to move the value we're processing.
 *
 * Then we've got the descriptor class which is standard for
 * plugins.  That's a subclass, as is standard.
 *
 * And finally we have the step code itself.  Pipeline runs
 * the steps as threads, so basically that's like a Runnable
 * which receives a copy of the parent class as the data transport.
 *
 * Hopefully that kind fo explains how this works.  You won't find a
 * lot of 'how to make a pipeline step' docs out there (at least not
 * at the time of this writing).
 *
 * @author sconley (sconley@epicforce.net)
 */

import java.io.IOException;
import java.lang.InterruptedException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import hudson.EnvVars;
import hudson.Extension;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousStepExecution;

import net.epicforce.jenkinsci.plugins.ahp2jenkins.beanshell.*;
import net.epicforce.migrate.ahp.exception.MigrateException;

import bsh.EvalError;
import bsh.Interpreter;

public class PipelineStep extends Step
{
    /*****************************************************************
     * PROPERTIES
     ****************************************************************/

    // Keep track of what we're going to process
    private String value;

    /*****************************************************************
     * CONSTRUCTOR
     ****************************************************************/

    /**
     * Data bound constructor for initializing the step.
     *
     * @param value         The value to process
     */
    @DataBoundConstructor
    public PipelineStep(final String value)
    {
        this.value = value;
    }

    /*****************************************************************
     * ACCESSORS
     ****************************************************************/

    /**
     * @param value
     *
     * Value to set
     */
    @DataBoundSetter
    public void setValue(String value)
    {
        this.value = value;
    }

    /**
     * @return the value to process
     */
    public String getValue()
    {
        return this.value;
    }

    /*****************************************************************
     * METHODS
     ****************************************************************/

    /**
     * This method is required to trigger the 'job' of running
     * the pipeline step.
     *
     * Context is a pipeline context, not a JenkinsContext.  It serves
     * the same purpose, though; it keeps running track of the current
     * state of execution.  Each block level has its own context which
     * inherits from the higher level but is a way of handling scope.
     *
     * In our case, we don't care too much about the scope, as we're
     * just processing a string and returning it.  However, the
     * different contexts are how block steps like dir(...) and env(...)
     * work.
     *
     * @param context           The context, which is as described above.
     * @return a StepExecution which is the subclass here.
     */
    @Override
    public StepExecution start(StepContext context)
    {
        return new PipelineExecution(value, context);
    }

    /*
     * Descriptor for the boilerplate required for a Jenkins plugin.
     */
    @Extension
    public static final class PipelineDescriptor extends StepDescriptor
    {
        /**
         * This is the method name used in the pipeline step.
         *
         * @return static string with the method name
         */
        @Override
        public String getFunctionName()
        {
            return "a2j";
        }

        /**
         * This is the "human readable name".  Used in the pipeline function
         * list I think.
         *
         * @return static string with the human descriabable name
         */
        @Override
        public String getDisplayName()
        {
            return "Anthill Pro ot Jenkins to process variables and Beanshell";
        }

        /*
         * AM-41: Pipeline has this neat form builder thing.  I'm not sure
         * what is necessary to support it, but right the builder will
         * list this method but selecting it will provide a rather
         * unhelpful message.  We can do better, but we don't have to do
         * better right now.
         */

        /**
         * Get required context.  Basically, this is a way to handle
         * dependencies.  These classes must exist in the context for
         * us to run.
         *
         * I'm not sure if there's a parameters class we can use
         * instead, but EnvCars.class is pretty close so we can
         * use that.
         *
         * @return set of classes required by this step
         */
        @Override
        public Set<Class<?>> getRequiredContext()
        {
            return Collections.<Class<?>>singleton(EnvVars.class);
        }
    }

    /*
     * This class is essentially a Runnable.  However, in our case, we want
     * something that runs synchronous -- we MUST process properties
     * before moving on.  So we'll use the SynchronousStepExecution.
     *
     * Note this class isn't available in old versions of Pipeline.
     */
    public static final class PipelineExecution extends
                              SynchronousStepExecution
    {
        // All steps must be serializable.
        private static final long serialVersionUID = 1L;

        // The string we're working on
        private final String value;

        /**
         * Get our value string and pass our context up to the parent.
         *
         * @param value     Our value for processing
         * @param context   Our pipeline context
         */
        public PipelineExecution(final String value, StepContext context)
        {
            super(context);
            this.value = value;
        }

        /**
         * Process and replace ${bsh: variables.  Basically, crunch
         * beanshell.  This should be run after processProperties
         *
         * @param val       String to check for beanshell
         * @return string with all beanshell processed.
         *
         * @throws MigrateException, most likely due to beanshell error.
         */
        private final static Pattern bshSearch =
                                        Pattern.compile("\\$\\{bsh:([^}]+)\\}");
        private String processBeanshell(final String val)
                throws MigrateException
        {
            Matcher match = bshSearch.matcher(val);

            if(match.find()) {
                // To catch beanshell stuff
                try {
                    // Set up a Beanshell environment
                    Interpreter bsh = new Interpreter();

                    // Set up initial "imports".  This could probably be done
                    // more efficiently somehow, but this will do for now.
                    // AM-42: Support more features, set up BSH more
                    //       efficiently -- can I set up once and reuse it?

                    // AM-42: I think I can auto-import the helpers somehow,
                    //       but I need to sort out exactly how
                    AbstractHelper  helper;

                    // PROPERTY LOOKUP
                    helper = new PropertyLookup();
                    helper.setContext(getContext());

                    bsh.set("PropertyLookup", helper);

                    // PATH HELPER
                    helper = new PathHelper();
                    helper.setContext(getContext());

                    bsh.set("PathHelper", helper);

                    // Run it
                    // Any properties loaded via ProperyLookup will
                    // be processed so there shouldn't be a need to
                    // re-run this through the prop processor.
                    return val.replace(match.group(0),
                                       String.valueOf(
                                            bsh.eval(match.group(1))
                                       )
                    );
                } catch(EvalError e) {
                    throw new MigrateException(
                        "Error while processing beanshell",
                        e
                    );
                }
            }

            return val;
        }

        /**
         * Process / resolve ${property} and replace them, recursively,
         * as there could be ${property} inside of ${property}.
         *
         * @param val       The value to process.
         * @return  Modified string with replaced properties.
         *
         * @throws MigrateException if it can't resolve a variable
         */
        private final static Pattern propSearch =
                                            Pattern.compile("\\$\\{([^:]+)\\}");
        private String processProperties(final String val)
                throws MigrateException
        {
            Matcher match = propSearch.matcher(val);

            // Recursively replace them until we've got no more.
            if(match.find()) {
                // Grab the environment out of the context.
                // I wish I could get the parameters directly;
                // not sure if that is possible (this may be more
                // flexible anyway?)
                Map<String, String> env;

                try {
                    env = (EnvVars)getContext().get(EnvVars.class);
                } catch(IOException | InterruptedException e) {
                    throw new MigrateException(
                                    "Error while processing properties", e
                    );
                }

                // Try to find the variable in our environment.
                if(!env.containsKey(match.group(1))) {
                    throw new MigrateException(
                        "Could not resolve property: " + match.group(1)
                    );
                }

                return processProperties(
                    val.replace(match.group(0),
                                env.get(match.group(1))
                    )
                );
            }

            return val;
        }

        /**
         * This is the 'main' method that is triggered by
         * SynchronousStepExecution
         *
         * @return the processed string value
         * @throws MigrateException on failure.
         */
        @Override
        public String run()
               throws MigrateException
        {
            return processBeanshell(
                        processProperties(
                            value
                        )
            );
        }
    }
}
