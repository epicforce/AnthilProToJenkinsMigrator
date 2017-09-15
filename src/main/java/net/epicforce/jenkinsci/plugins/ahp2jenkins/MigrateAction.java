package net.epicforce.jenkinsci.plugins.ahp2jenkins;

/*
 * MigrateAction.Java
 *
 * This is the source file for handling the plugin user interface.
 *
 * @author sconley (sconley@epicforce.net)
 * Copyright 2017 Epic Force
 */

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.logging.Level;

import org.apache.commons.lang3.StringUtils;

import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.RootAction;
import hudson.model.listeners.ItemListener;
import hudson.util.DaemonThreadFactory;
import hudson.util.ListBoxModel;
import hudson.util.NamingThreadFactory;

import jenkins.model.Jenkins;

import net.epicforce.jenkinsci.plugins.ahp2jenkins.config.AhpGlobalConfiguration;
import net.epicforce.jenkinsci.plugins.ahp2jenkins.config.AhpInstance;
import net.epicforce.jenkinsci.plugins.ahp2jenkins.context.JenkinsContext;
import net.epicforce.migrate.ahp.Migration;
import net.epicforce.migrate.ahp.exception.MigrateException;

import org.kohsuke.stapler.bind.JavaScriptMethod;


/**
 * This will:
 *
 * - Add a link to the top level (root action) sidebar
 * - Produce a page with controls for doing migration stuff
 * - Actually trigger migrations as needed.
 */
@Extension
public class MigrateAction extends ItemListener
                           implements RootAction,
                                      Describable<MigrateAction>
{
    private static final Logger LOG =
                                Logger.getLogger(MigrateAction.class.getName());

    /*****************************************************************
     * PROPERTIES
     ****************************************************************/

    private MigrateEngine   engine = null;
    private Thread          engineThread = null;

    /*****************************************************************
     * WEB ACTION METHODS
     ****************************************************************/

    /**
     * Check to see if we have any AHP instances configured.  This
     * used by the UI.
     *
     * @return boolean true if instances configured.
     */
    public boolean getInstancesConfigured()
    {
        return (AhpGlobalConfiguration.get().getInstances().size() > 0);
    }

    /**
     * Get a list of AhpInstance objects for display.
     *
     * @return List of AhpInstance objects
     */
    public List<AhpInstance> getAhpInstances()
    {
        return AhpGlobalConfiguration.get().getInstances();
    }

    /**
     * RPC call to do project search
     *
     * @param instance The instance to use.
     * @param query    Search query.
     * @return Map of project names to a map of workflow names to
     *         ID's
     */
    @JavaScriptMethod
    public Map<String, Map<String, String>> doSearch(final String instance,
                                                     final String query)
    {
        // Make an instance out of our instance string.
        AhpInstance ahp = new AhpInstance(instance);

        // Our migrate object
        Migration migrate = null;

        try {
            migrate = ahp.getMigration();

            // Do a search
            Map<String, Map<String, Long>> ahpResults =
                                migrate.fetchWorkflowsForProjectName(query, 0);

            // Note: Jenkins javascript methods can't handle Long's,
            // we have to convert what we get from the Anthill library
            // into something friendly.
            Map<String, Map<String, String>> ret =
                new HashMap<String, Map<String, String>>(ahpResults.size());

            for(Map.Entry<String, Map<String, Long>> e: ahpResults.entrySet()) {
                // Get the workflow map
                Map<String, String> subMap = 
                        new HashMap<String, String>(e.getValue().size());

                // Move it over
                for(Map.Entry<String, Long> se : e.getValue().entrySet()) {
                    subMap.put(se.getKey(), String.valueOf(se.getValue()));
                }

                ret.put(e.getKey(), subMap);
            }

            return ret;
        } catch(MigrateException e) {
            LOG.log(Level.WARNING, "Got exception while doing project search",
                    e
            );

            return null;
        } finally {
            if(migrate != null) {
                migrate.close();
                migrate = null;
            }
        }
    }

    /**
     * RPC method to return migration progress.  Returns a list of
     * string arrays, representing values to display on the migration
     * progress table.
     *
     * The array columns are [0] Workflow, [1] Jenkins Job, [2] results
     *
     * @return as described
     */
    @JavaScriptMethod
    public List<String[]> doStatus()
    {
        List<String[]> ret = new ArrayList<String[]>();

        // See if we're doing anything
        if(engine == null) {
            // Nothing!
            return ret;
        }

        StringBuilder sb = new StringBuilder(128);

        // Get workflow list
        for(Migration m : engine.getJobs()) {
            String[] cols = new String[3];
            sb.setLength(0); // reset

            // Name column
            if(m.getWorkflowName() != null) {
                sb.append(m.getWorkflowName())
                  .append(" (")
                  .append(m.getWorkflowId())
                  .append(")");
            } else {
                sb.append("Workflow Not Loaded (")
                  .append(m.getWorkflowId())
                  .append(")");
            }

            cols[0] = sb.toString();
            sb.setLength(0);

            JenkinsContext jc = (JenkinsContext)m.getContext();

            // Jenkins job column
            if((jc == null) || (jc.getJenkinsJobName() == null)) {
                cols[1] = "Jenkins job not created yet";
            } else {
                cols[1] = jc.getJenkinsJobName();
            }

            // Status
            switch(m.getStatus()) {
                case Migration.NEED_SETUP:
                case Migration.READY:
                    cols[2] = "Initializing...";
                    break;
                case Migration.RUNNING:
                    sb.append("Running: ")
                      .append(m.getProgress())
                      .append("%");
                    cols[2] = sb.toString();
                    break;
                case Migration.SUCCESS:
                case Migration.ERROR:
                case Migration.CLOSED:
                    // Success can't be reliably used since it is
                    // superceded by close status.  Use the error
                    // presence to determine success instead.
                    if(m.getError() != null) {
                        sb.append("Error: ");
                        sb.append(m.getError().getMessage());
                        cols[2] = sb.toString();
                    } else {
                        cols[2] = "Success";
                    }
                    break;
                default:
                    cols[2] = "Entered unknown status";
                    LOG.log(Level.SEVERE, "Unknown status: " +
                            String.valueOf(m.getStatus())
                    );
            }

            ret.add(cols);
        }

        return ret;
    }

    /**
     * RPC method to fire off a migration batch.  This is fire and
     * forget; the migration batch will always be accepted, and any
     * errors will show up during a subsequent 'doStatus' call.
     *
     * @param instance      A string representation of our AHP instance
     * @param workflows     A list of String workflow ID's
     */
    @JavaScriptMethod
    public void doSubmitWorkflows(final String instance,
                                  final List<String> workflows)
    {
        // Make an instance out of our instance string.
        AhpInstance ahp = new AhpInstance(instance);

        // Start our engine if we need to
        if(engineThread == null) {
            if(engine != null) {
                // This shouldn't happen
                LOG.log(Level.SEVERE,
                        "engineThread was null, but engine was not null."
                );
            }

            engine = new MigrateEngine();
            engineThread = (new NamingThreadFactory(
                                new DaemonThreadFactory(),
                                "AHP2Jenkins.MigrateEngine"
                            )
            ).newThread(engine);
            engineThread.start();
        }

        // AM-37: Make thread count configurable
        engine.migrateWorkflows(ahp, workflows, 5);
    }

    /*****************************************************************
     * BOILER PLATE
     *
     * Methods required for proper Jenkins display
     * (RootAction Implementation)
     ****************************************************************/

    /**
     * Icon file name.  /plugin/ahp2jenkins basically maps to
     * src/main/webapp
     *
     * @return String containing web path.
     */
    @Override
    public String getIconFileName()
    {
        return "/plugin/ahp2jenkins/img/ahp.png";
    }

    /**
     * Display name is used on the side bar menu and a couple other
     * places that are links to the plugin (I think breadcrumbs uses
     * this too, maybe even page title).
     *
     * @return String containing plugin link name
     */
    @Override
    public String getDisplayName()
    {
        return "Migrate from AHP";
    }

    /**
     * URL name is the URL path that will be used to access the plugin.
     *
     * @return String containing URL path for plugin.
     */
    @Override
    public String getUrlName()
    {
        return "ahp2jenkins";
    }

    /**
     * Get the descriptor required by Jenkins
     *
     * @return Descriptor object
     */
    @Override
    public Descriptor<MigrateAction> getDescriptor()
    {
        return Jenkins.getActiveInstance().getDescriptorOrDie(getClass());
    }

    /**
     * Properly / cleanly shutdown
     *
     */
    @Override
    public void onBeforeShutdown()
    {
        // Cleanly shutdown our threads
        if(engine != null) {
            engine.shutdown();

            if(engineThread != null) {
                while(engineThread.isAlive()) {
                    try {
                        engineThread.join();
                    } catch(InterruptedException e) {
                        // try to force kill it
                        engineThread.interrupt();
                    }
                }
            }
        }
    }

    /**
     * The descriptor is boilerplate that Jenkins requires in order for the
     * plugin to load.  It's basically plugin configuration.
     */
    @Extension
    public static final class MigrateActionDescriptor
                        extends Descriptor<MigrateAction>
    {
        /**
         * Get the display name (what this shows up as in the plugin list)
         *
         * @return string constant
         */
        @Override
        public String getDisplayName()
        {
            return "AnthillPro Migration Plugin";
        }

        /*************************************************************
         * FORM DRIVER METHODS
         *
         * These methods are all related to driving form behavior.
         ************************************************************/

    }
}
