package net.epicforce.jenkinsci.plugins.ahp2jenkins.context;

/*
 * JenkinsWorkflow.java
 *
 * This implemenets the AbstractWorkflow for the loader and gives
 * us hooks to actually set up our Jenkins job after the data is
 * pulled out of AHP.
 *
 * @author sconley (sconley@epicforce.net)
 */

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;

import net.epicforce.migrate.ahp.context.AbstractContext;
import net.epicforce.migrate.ahp.exception.MigrateException;
import net.epicforce.migrate.ahp.migrate.AbstractWorkflow;

import hudson.model.Failure;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.PasswordParameterDefinition;
import hudson.model.StringParameterDefinition;
import hudson.model.TextParameterDefinition;

import jenkins.model.Jenkins;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;

import com.urbancode.anthill3.domain.project.envprops.ProjectEnvironmentProperty;
import com.urbancode.anthill3.domain.project.prop.ProjectProperty;
import com.urbancode.anthill3.domain.property.IProperty;
import com.urbancode.anthill3.domain.property.Property;
import com.urbancode.anthill3.domain.workflow.WorkflowProperty;


public class JenkinsWorkflow extends AbstractWorkflow
{
    private static final Logger LOG =
                            Logger.getLogger(JenkinsWorkflow.class.getName());
    /**
     * preRun
     *
     * Add our node(...) step
     *
     * AM-43: Handle parallel/complex workflows
     * AM-44: try/catch boilerplate for notifications
     *
     * @param context  Our migration context
     */
    public void preRun(AbstractContext context)
    {
        // This property (A2J_NODE) is added in teh postRun
        ((JenkinsContext)context).addCode("node(params.A2J_NODE)\n{", 1);
    }

    /**
     * postRun
     *
     * Close our node(...) step and then create our Jenkins job.
     *
     * AM-35: add Jenkins path support
     */
    public void postRun(AbstractContext context)
           throws MigrateException
    {
        JenkinsContext ctx = (JenkinsContext)context;

        ctx.addCode("}", 0, -1);

        // Try to create a Jenkins job
        String jobName =
            "A2J-" + ctx.getWorkflow().getProject().getName() +
            "-" + ctx.getWorkflow().getName();

        // Convert space to dash
        jobName = jobName.replace(" ", "-");

        // Our Jenkins instance
        Jenkins jenkins = Jenkins.getActiveInstance();

        // A workflow job
        WorkflowJob job;

        // Keep going til we find a valid one.
        for(int i = 0; true; i++) {
            String testName;

            // Construct a name, taking into account possible
            // duplicate jobs.
            if(i == 0) {
                testName = jobName;
            } else {
                testName = jobName + "-" + String.valueOf(i);
            }

            try {
                // This throws exception on failure.
                // I think any job/workflow you can make in AHP is
                // probably valid in Jenkins too.
                jenkins.checkGoodName(testName);

                // Try to create our job
                job = jenkins.createProject(WorkflowJob.class, testName);

                // push the final name back into our job name
                jobName = testName;
                break;
            } catch(Failure e) {
                // This is never going to get better
                LOG.log(Level.WARNING, "We'll never be able to migrate name ",
                        e
                );

                throw new MigrateException(
                    "Jenkins can't handle the project/workflow name: "
                    + testName, e
                );
            } catch(IllegalArgumentException e) {
                // this means we already have a job with this name
                // Ignore it!
            } catch(IOException e) {
                // This is a pretty low level error
                LOG.log(Level.SEVERE, "IO Error while running ahp2jenkins", e);
                throw new MigrateException("IO Error while migrating", e);
            }
        }

        // Update context with the new job name
        ctx.setJenkinsJobName(jobName);

        // Set our pipeline script
        // The boolean is for sandbox or no -- we'll try sandbox true
        // which causes less problems for now.
        CpsFlowDefinition fd = new CpsFlowDefinition(ctx.getPipelineCode(),
                                                     true
        );

        // Set the project definition
        job.setDefinition(fd);

        // Add our properties
        List<ParameterDefinition> parameterDefs = new LinkedList<ParameterDefinition>();

        // AM-45: Determine Jenkins agent mapping in a more computed way
        parameterDefs.add(
            new StringParameterDefinition("A2J_NODE", "",
                                          "Which Jenkins label to use to run this job."
            )
        );

        // Add the needful
        ahpToJenkinsParameters(ctx.getKnownProperties(), parameterDefs);

        // Add the needed
        for(String prop : ctx.getUnknownProperties()) {
            parameterDefs.add(
                new StringParameterDefinition(prop, "", "Provide Value")
            );
        }

        try {
            // add to our job
            job.addProperty(
                new ParametersDefinitionProperty(parameterDefs)
            );

            // Save it
            job.save();
        } catch(IOException e) {
            LOG.log(Level.SEVERE, "IO Error while saving new job", e);
            throw new MigrateException("IO Error while migrating", e);
        }
    }

    /**
     * Takes a list of Anthill properties and converts them to their nearest
     * Jenkins equivalent, adding them to the target list.
     *
     * @param ahp       Anthill properties
     * @param target    Target prop list
     */
    private void ahpToJenkinsParameters(Collection<IProperty> ahp,
                                        List<ParameterDefinition> target)
    {
        for(IProperty prop : ahp) {
            // Process type
            target.add(
                PropertyMap.valueOf(prop.getClass()
                                        .getSimpleName()
                ).translate(prop)
            );
        }
    }

    /**
     * This is an enumeration that is used to map AHP property types to
     * Jenkins property types
     */
    private static enum PropertyMap {
        ProjectEnvironmentProperty
        {
            /**
             * Converts a Project Environment Property to its Jenkins
             * equivalent.
             *
             * @param source source property
             * @return an appropriate parameter definition.
             */
            ParameterDefinition translate(IProperty source)
            {
                ProjectEnvironmentProperty prop =
                                            (ProjectEnvironmentProperty)source;
                String description = scrubDescription(prop.getDescription());
                String value = scrubValue(prop.getValue());

                if(prop.isSecureValue()) {
                    return new PasswordParameterDefinition( prop.getName(),
                                                            value,
                                                            description
                    );
                }

                // Does it have newlines?  If so, it needs a text field.
                return pickDefinition(prop.getName(), value, description);
            }
        },

        ProjectProperty
        {
            /**
             * Converts a Property to its Jenkins equivalent.
             *
             * @param source source property
             * @return an appropriate parameter definition.
             */
            ParameterDefinition translate(IProperty source)
            {
                ProjectProperty prop = (ProjectProperty)source;
                String description = scrubDescription(prop.getDescription());
                String value = scrubValue(prop.getValue());

                if(prop.isSecure()) {
                    return new PasswordParameterDefinition( prop.getName(),
                                                            value,
                                                            description
                    );
                }

                // Does it have newlines?  If so, it needs a text field.
                return pickDefinition(prop.getName(), value, description);
            }
        },

        Property
        {
            /**
             * Converts a Property to its Jenkins equivalent.
             *
             * @param source source property
             * @return an appropriate parameter definition.
             */
            ParameterDefinition translate(IProperty source)
            {
                Property prop = (Property)source;
                String description = scrubDescription(prop.getDescription());
                String value = scrubValue(prop.getValue());

                if(prop.isSecure()) {
                    return new PasswordParameterDefinition( prop.getName(),
                                                            value,
                                                            description
                    );
                }

                // Does it have newlines?  If so, it needs a text field.
                return pickDefinition(prop.getName(), value, description);
            }
        },

        WorkflowProperty
        {
            /**
             * Converts a Workflow Property to its Jenkins equivalent.
             *
             * Workflow props are way more complicated than the others.
             *
             * AM-46: Properly migrate all the workflow types to Jenkins
             *       equiv's.  For tonight, I'm not going into that level
             *       of detail but we have the ability to do so!
             *
             * @param source source property
             * @return an appropriate parameter definition.
             */
            ParameterDefinition translate(IProperty source)
            {
                WorkflowProperty prop = (WorkflowProperty)source;
                String description = scrubDescription(prop.getDescription());
                String value = scrubValue(prop.getValue());

                if(prop.isSecure()) {
                    return new PasswordParameterDefinition( prop.getName(),
                                                            value,
                                                            description
                    );
                }

                // Does it have newlines?  If so, it needs a text field.
                return pickDefinition(prop.getName(), value, description);
            }
        };

        /**
         * Different property types implement this to do the conversions.
         *
         * @param source source property
         * @return an appropriate parameter definition.
         */
        abstract ParameterDefinition translate(IProperty source);


        /**
         * Common description handling
         *
         * @param description which may be null
         * @return standardized text
         */
        protected String scrubDescription(String description)
        {
            // Description is usually unset.
            if((description != null) && (description.length() > 0)) {
                return "From AHP: " + description;
            } else {
                return "From AHP";
            }
        }

        /**
         * Common value handling
         *
         * @param value which may be null
         * @return standardized text
         */
        protected String scrubValue(String value)
        {
            if(value == null) {
                return "";
            }

            return value;
        }

        /**
         * Common handling of text vs. textbox
         *
         * @param name the name of the property
         * @param value which may have newline characters in it
         * @param description which will be the decsription
         * @return the appropriate variant of ParameterDefinition
         */
        protected ParameterDefinition pickDefinition(   String name,
                                                        String value,
                                                        String description)
        {
            // Does it have newlines?  If so, it needs a text field.
            if(value.indexOf('\n') != -1) {
                return new TextParameterDefinition(name, value, description);
            } else {
                return new StringParameterDefinition(name, value, description);
            }
        }
    }
}
