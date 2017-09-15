package net.epicforce.jenkinsci.plugins.ahp2jenkins.beanshell;

/*
 * PropertyLookup.java
 *
 * This is a class used by the beanshell processor, and it is an
 * analog to one of the AHP helpers.
 *
 * This particular class is for looking up properties.
 *
 * @author sconley (sconley@epicforce.net)
 */

import net.epicforce.jenkinsci.plugins.ahp2jenkins.PipelineStep;

import hudson.EnvVars;

import java.io.IOException;
import java.lang.InterruptedException;
import java.util.Map;

import net.epicforce.migrate.ahp.exception.MigrateException;


public class PropertyLookup extends AbstractHelper
{
    /**
     * Method to look up a property based on a name.
     *
     * Returns empty string if property not found (this is
     * Anthill Pro's behavior)
     *
     * @param val          Property name
     * @return the value of the property or empty string
     * @throws MigrateException on unlikely event of any kind of error
     */
    public String getValue(Object val)
           throws MigrateException
    {
        try {
            Map<String, String> env = (EnvVars)getContext().get(EnvVars.class);
            String property = String.valueOf(env);

            // See if we have a property
            if(env.containsKey(property)) {
                // Run the resolver on it
                PipelineStep.PipelineExecution exec =
                            new PipelineStep.PipelineExecution(property,
                                                               getContext()
                );

                return exec.run();
            }
        } catch(IOException | InterruptedException e) {
            throw new MigrateException(
                "Got an error getting beanshell properties", e
            );
        }

        return "";
    }
}
