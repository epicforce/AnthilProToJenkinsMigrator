package net.epicforce.jenkinsci.plugins.ahp2jenkins.config;

/*
 * GlobalConfiguration.java
 *
 * Implementation for the configuration panel under Jenkins
 * System Configuration.
 *
 * @author sconley@epicforce.net
 */

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.logging.Level;

import hudson.Extension;
import jenkins.model.GlobalConfiguration;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.StaplerRequest;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;


@Extension
public class AhpGlobalConfiguration extends GlobalConfiguration
{
    private static final Logger LOG =
                Logger.getLogger(AhpGlobalConfiguration.class.getName());

    /*****************************************************************
     * PROPERTIES
     ****************************************************************/

    /*
     * Our list of AHP instances.
     *
     * Life would be slightly easier if this was a Set instead, but
     * when looking at other plugins I see some amazing gymnastics
     * to keep this as a List instead of a Set.  Not sure why it
     * matters, but hey, its a party!
     */
    private List<AhpInstance> instances;
     
    /*****************************************************************
     * CONSTRUCTORS
     ****************************************************************/

    /**
     * Load our configuration information.
     */
    public AhpGlobalConfiguration()
    {
        load();
    }

    /*****************************************************************
     * ACCESSORS
     ****************************************************************/

    /**
     * Get our AhpGlobalConfiguration from the system in an eeasy
     * fashion.
     *
     * @return AhpGlobalConfiguration with configs.
     */
    public static AhpGlobalConfiguration get()
    {
        return GlobalConfiguration.all().get(AhpGlobalConfiguration.class);
    }

    /**
     * Get our list of AHP Instances
     *
     * @return read-only list of AhpInstance objects
     */
    @NonNull
    public List<AhpInstance> getInstances()
    {
        LOG.log(Level.FINE, "Get instances called");

        if(instances == null) {
            return Collections.<AhpInstance>emptyList();
        } else {
            return Collections.unmodifiableList(instances);
        }
    }

    /**
     * Set our list of AHP instances.
     *
     * @param instances    Our list of instances
     */
    public void setInstances(@CheckForNull List<AhpInstance> instances)
    {
        LOG.log(Level.FINE, "Set instances called");

        // Short circuit if instances is null.
        if(instances == null) {
            this.instances = new ArrayList<AhpInstance>();
            return;
        }

        // Remove duplicates and empties
        Set<AhpInstance> noDups = new HashSet<AhpInstance>(instances.size());
        noDups.addAll(instances);

        // Remove empties
        for(Iterator<AhpInstance> it = noDups.iterator(); it.hasNext(); ) {
            AhpInstance ahp = it.next();

            if(ahp.getHostname() == null) {
                it.remove();
            }
        }

        // Make it back into a list.
        this.instances = new ArrayList<AhpInstance>(noDups);
        save();
    }

    /*****************************************************************
     * METHODS
     ****************************************************************/

    /**
     * I saw this in another plugin (github-branch-source-plugin) and
     * I don't really know why this is the way it is.
     *
     * According to the docs, returning false will tell the configuration
     * to stay on the same page, and that's the default.  Maybe this
     * is required for configuration panel items.
     *
     * It seems to work and I haven't had a chance to noodle with it,
     * so leaving it as-is for now.  Its possible this method could
     * just be removed.
     *
     * @param req        The web request
     * @param json       What we're procesing
     * @return boolean - false to stay on page.  Guess this is success indicator
     */
    @Override
    public boolean configure(StaplerRequest req, JSONObject json)
           throws FormException
    {
        req.bindJSON(this, json);
        return true; // WHY?  Not sure
    }
}
