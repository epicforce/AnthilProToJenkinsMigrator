package net.epicforce.jenkinsci.plugins.ahp2jenkins.context;

/*
 * JenkinsContext.java
 *
 * Jenkins-oriented implementation of AbstractContext, required for the
 * migration process.
 *
 * The context is passed into every step of the migrate process and is
 * used to actually build our pipeline script.
 *
 * @author sconley (sconley@epicforce.net)
 */

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.logging.Logger;
import java.util.logging.Level;

import net.epicforce.jenkinsci.plugins.ahp2jenkins.code.Code;
import net.epicforce.migrate.ahp.context.AbstractContext;
import net.epicforce.migrate.ahp.exception.MigrateException;

import com.urbancode.anthill3.domain.persistent.PersistenceException;
import com.urbancode.anthill3.domain.project.envprops.ProjectEnvironmentProperty;
import com.urbancode.anthill3.domain.property.IProperty;
import com.urbancode.anthill3.domain.property.Property;
import com.urbancode.anthill3.domain.servergroup.ServerGroup;
import com.urbancode.anthill3.domain.singleton.serversettings.ServerSettings;
import com.urbancode.anthill3.domain.singleton.serversettings.ServerSettingsFactory;
import com.urbancode.anthill3.domain.workflow.Workflow;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;

import hudson.model.Item;
import hudson.security.ACL;

import jenkins.model.Jenkins;


public class JenkinsContext extends AbstractContext
{
    private static final Logger LOG =
                            Logger.getLogger(JenkinsContext.class.getName());

    /*****************************************************************
     * PROPERTIES
     ****************************************************************/

    // What's our Jenkins name?
    protected String    jenkinsJobName = null;

    // Our pipeline code
    protected Code      pipeline = new Code();

    // Cache known properties
    protected Map<String, IProperty>        systemProperties =
                                            new HashMap<String, IProperty>();
    protected Map<String, IProperty>        projectProperties =
                                            new HashMap<String, IProperty>();
    protected Map<String, Map<String, IProperty>>
                                            projectEnvProperties =
                                new HashMap<String, Map<String, IProperty>>();
    protected Map<String, IProperty>        workflowProperties =
                                            new HashMap<String, IProperty>();

    // Keep the server settings, why not!
    ServerSettings                          serverSettings;

    // Make a map of known property name to property information,
    // and a map of unknown propertie.
    protected Map<String, IProperty>        neededProps =
                                            new HashMap<String, IProperty>();

    // And properties we don't know
    protected Set<String>                   unknownProps =
                                            new HashSet<String>();

    /*****************************************************************
     * ACCESSORS
     ****************************************************************/

    /**
     * Return known props that we need.
     *
     * @return a collection of IProperty's
     */
    public Collection<IProperty> getKnownProperties()
    {
        return neededProps.values();
    }

    /**
     * Return list of props we need but don't have info on.
     *
     * @return a set of String's
     */
    public Set<String> getUnknownProperties()
    {
        return unknownProps;
    }

    /**
     * @return Jenkins job name
     */
    public final String getJenkinsJobName()
    {
        return jenkinsJobName;
    }

    /**
     * @param name a Jenkins job name
     */
    public void setJenkinsJobName(String name)
    {
        jenkinsJobName = name;
    }

    /**
     * @return the pipeline code generated so far
     *
     * This can theoretically be called multiple times.  Not sure
     * why you'd do it, but, it's a party!
     */
    public String getPipelineCode()
    {
        return pipeline.get();
    }

    /**
     * Get our current tab level.  This is useful for jobs, which
     * will want to close braces down to whatever tab level they
     * started at.
     *
     * @return the current tab level
     */
    public int getPipelineCodeTabLevel()
    {
        return pipeline.getCurrentTabLevel();
    }

    /**
     * When workflow is set, let's cache properties.
     *
     * This can fail for a number of AHP related reasons, but probably
     * won't.  If it does fail, properties won't load.
     *
     * @param wf      the AHP workflow
     */
    @Override
    public void setWorkflow(Workflow wf)
    {
        super.setWorkflow(wf);

        try {
            // Load system properties
            serverSettings = ServerSettingsFactory.getInstance().restore();

            for(IProperty prop : serverSettings.getPropertyList()) {
                systemProperties.put(prop.getName(), prop);
            }

            // Load project properties
            for(IProperty prop : wf.getProject().getPropertyList()) {
                projectProperties.put(prop.getName(), prop);
            }

            // Figure out environment properties.
            for(ServerGroup sg : wf.getProject().getEnvironmentGroup()
                                                .getServerGroupArray()) {
                IProperty[] props = wf.getProject()
                                      .getEnvironmentProperties(sg);

                Map<String, IProperty> subProps =
                                            new HashMap<String, IProperty>();

                for(IProperty prop : props) {
                    subProps.put(prop.getName(), prop);
                }

                projectEnvProperties.put(sg.getName(), subProps);
            }

            // Workflow properties
            for(IProperty prop : wf.getPropertyArray()) {
                workflowProperties.put(prop.getName(), prop);
            }
        } catch(PersistenceException e) {
            LOG.log(Level.WARNING, 
                    "Failed to load properties due to AHP exception.  " +
                    "This may cause properties to not load.",
                    e
            );
        }
    }

    /*****************************************************************
     * CODE GENERATION METHODS
     ****************************************************************/

    /**
     * Add a block of code to the pipeline script.
     *
     * Optionally add a tab modifier (meaning, a + or - factor that
     * will apply to the next line -- think open { ) or force
     * a tab level (i.e. force it to 0 if you want to force a left
     * justification)
     *
     * @param code      The code to add
     * @param modifier  The tab modifier or 0 / null
     * @param forceTab  Force a tab level
     */
    public void addCode(String code, Integer modifier, Integer forceTab)
    {
        pipeline.add(code, modifier, forceTab);
    }

    /**
     * With force tab level default to null
     *
     * @param code      The code to add
     * @param modifier  The tab modifier or 0 / null
     */
    public void addCode(String code, Integer modifier)
    {
        pipeline.add(code, modifier);
    }

    /**
     * With force tab level default to null and no modifier
     *
     * @param code      The code to add
     */
    public void addCode(String code)
    {
        pipeline.add(code);
    }

    /*****************************************************************
     * PROPERTY HANDLING METHODS
     ****************************************************************/

    /**
     * Locate and add a property name to either the known list or the
     * unknown list in the most efficient way we can manage.
     *
     * @param name          The property name to search for
     * @throws MigrateException if there was any problem (such as an
     *         unknown property type)
     */
    public void locateAndAddProperty(final String name)
           throws MigrateException
    {
        // Check if we've already got it
        if(neededProps.containsKey(name) || unknownProps.contains(name)) {
            return;
        }

        // AM-48: Some of these props need some special handling.
        // Workflow properties and project properties are entirely
        // different objects and one of them has scripting support.
        // That's something we need to dig into more, but for now,
        // this is all kept generic.

        // Find it.  Workflow, project environment, project, system.
        // AM-48: Search agents in some logical fashion, add agent
        // props with a variable element to make them selectable
        // based on agent.
        if(workflowProperties.containsKey(name)) {
            neededProps.put(name, workflowProperties.get(name));

            // Check sub-properties
            scanProperties(workflowProperties.get(name).getValue());
            return;
        }

        // AM-48: Project environment properties.  These will need
        // to have some sort of combination of environment name
        // so they can be selectable based on environment, kind of
        // like agents.

        if(projectProperties.containsKey(name)) {
            neededProps.put(name, projectProperties.get(name));

            // Check sub-properties
            scanProperties(projectProperties.get(name).getValue());

            return;
        }

        if(systemProperties.containsKey(name)) {
            neededProps.put(name, systemProperties.get(name));

            // Check sub-properties
            scanProperties(systemProperties.get(name).getValue());

            return;
        }

        // don't have it.  Note we've found it as not needed
        unknownProps.add(name);
    }

    /**
     * Scan a string for properties and recursively add necessary
     * properties.  There's no need for a return.
     *
     * @param toScan
     */
    static final Pattern scanPattern =
                                Pattern.compile("\\$\\{([^:]+):([^}]+)\\}");
    static final Pattern bshPattern =
        Pattern.compile("PropertyLookup\\.getValue\\(\"([^\"]+)\"\\)");
    public void scanProperties(final String toScan)
           throws MigrateException
    {
        Matcher matches = scanPattern.matcher(toScan);

        // Find the matches, and work on each one.
        while(matches.find()) {
            switch(matches.group(1)) { // our property type
                case "property":
                case "property?":
                case "p":
                case "p?":
                    // AM-49: Handle agent properties smarty, maybe use
                    // a combined property value to emulate how it works in
                    // AHP.
                case "agent":
                case "agent?":
                case "a":
                case "a?":
                    // Add the property name if we don't have it anymore.
                    locateAndAddProperty(matches.group(2));
                    break;
                case "bsh":
                    // Process beanshell properties, make sure they get
                    // added too.
                    Matcher bshMatcher = bshPattern.matcher(matches.group(2));

                    // Check them all
                    while(bshMatcher.find()) {
                        locateAndAddProperty(bshMatcher.group(1));
                    }

                    break;
                default:
                    // Error
                    throw new MigrateException(
                        "Unknown property type: " + matches.group(1) +
                        " in property string: " + toScan
                    );
            }
        }
    }

    /*****************************************************************
     * JENKINS ASSISTIVE METHODS
     ****************************************************************/

    /**
     * Add a credential to Jenkins, or update it if it already exists.
     *
     * The ID passed should be based off something unique on the Anthill
     * side... such as repository ID.  Repository ID is good because then
     * different repos with the same credentials can use the same Jenkins
     * credentials.
     *
     * @param username          The user's name
     * @param password          The password
     * @param id                The credential ID to store.
     *
     * @throws MigrateException on any sort of failure (somewhat unlikely)
     */
    public void addJenkinsCredential(String username,
                                     String password,
                                     String id)
           throws MigrateException
    {
        try {
            // We have to search the credentials store to see if we have
            // this ID in the store yet.  There may be a better way to do
            // this, but my investigation shows this is the only way.
            List<StandardUsernamePasswordCredentials> credentials =
                CredentialsProvider.lookupCredentials(
                    StandardUsernamePasswordCredentials.class,
                    (Item)null,
                    ACL.SYSTEM,
                    Collections.<DomainRequirement>emptyList()
            );

            // I'm not honestly sure what this does -- I need to
            // look at it closer later.  This is some voodoo I found
            // on the net that seems to work :)
            CredentialsStore store = CredentialsProvider.lookupStores(
                                        Jenkins.getInstance()
            ).iterator().next();

            // Whether we update or add, we need a credential object.
            UsernamePasswordCredentialsImpl cred = new
                UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL,
                                                id, "Imported from AHP",
                                                username, password
            );

            // Iterate to see if we need to udpate.  Not a fan of how
            // this works.  AM-50: Is there a more direct way to do this?
            for(StandardUsernamePasswordCredentials c : credentials) {
                if(c.getId().equals(id)) {
                    // In this case we'll do an update.
                    if(!store.updateCredentials(Domain.global(), c, cred)) {
                        throw new MigrateException(
                            "Could not update Jenkins credential with ID: "
                            + id
                        );
                    }

                    return;
                }
            }

            // If we survived to here, then we don't have the credential
            // and we need to add it.
            if(!store.addCredentials(Domain.global(), cred)) {
                throw new MigrateException(
                    "Could not create new credentials in Jenkins."
                );
            }
        } catch(IOException e) {
            throw new MigrateException(
                "Got IO Exception while storing Jenkins credentials", e
            );
        }
    }
}
