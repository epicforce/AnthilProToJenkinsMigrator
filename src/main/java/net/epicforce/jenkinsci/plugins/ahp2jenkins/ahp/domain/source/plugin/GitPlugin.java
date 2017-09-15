package net.epicforce.jenkinsci.plugins.ahp2jenkins.ahp.domain.source.plugin;

/**
 * GitPlugin.java
 *
 * Git Source Control Plugin from Anthill.  Note that this is not
 * directly loaded by the Loader; instead, its proxied through
 * PluginPopulateWorkspaceStepConfigMigrate
 *
 * @author sconley (sconley@epicforce.net)
 */

import java.lang.StringBuilder;
import java.util.HashMap;
import java.util.Map;

import net.epicforce.jenkinsci.plugins.ahp2jenkins.context.JenkinsContext;
import net.epicforce.jenkinsci.plugins.ahp2jenkins.context.JenkinsStep;
import net.epicforce.migrate.ahp.exception.MigrateException;

import com.urbancode.anthill3.domain.property.PropertyValueGroup;
import com.urbancode.anthill3.domain.repository.plugin.PluginRepository;
import com.urbancode.anthill3.domain.source.plugin.PluginPopulateWorkspaceStepConfig;
import com.urbancode.anthill3.domain.source.plugin.PluginSourceConfig;


public class GitPlugin extends JenkinsStep
{
    /**
     * Implementation of the run step -- always throws exception, this
     * method should not be used.
     *
     * @param context           The context
     * @throws MigrateException always
     */
    @Override
    public void run(JenkinsContext context)
           throws MigrateException
    {
        throw new MigrateException("GitPlugin::run called directly");
    }

    /**
     * This is the version we should use.
     *
     * @param context           The context
     * @param config            The plugin source config object
     * @param repos             Our array of repositories
     * @throws MigrateException always
     */
    public void run(JenkinsContext context, PluginSourceConfig config,
                    PluginRepository[] repos)
           throws MigrateException
    {
        StringBuffer sb = new StringBuffer(2048);

        // AM-29: Handle workspace date ?
        // Put some notes in
        sb.append("// GIT Plugin Populate Workspace Step\n")
          .append("// Note that Jenkins ignores the Workspace Date\n")
          .append("// AHP would have used: ")
          .append(((PluginPopulateWorkspaceStepConfig)context.getCurrentStep())
                   .getWorkspaceDateScript())
          .append("\n");

        // We may not care about these.  Some plugins (like SVN) do support
        // this though. So maybe onesy-twosy is okay in this case.
        if((config.getFilepathExcludeString() != null) &&
           (config.getFilepathExcludeString().length() != 0)) {
            sb.append("/* Jenkins does not support the changelog file path\n")
              .append(" * exclude.  The following are ignored by AHP:\n")
              .append(" * ")
              .append(config.getFilepathExcludeString())
              .append("\n */\n");
        }

        if((config.getUserExcludeString() != null) &&
           (config.getUserExcludeString().length() != 0)) {
            sb.append("/* Jenkins does not support the changelog user\n")
              .append(" * exclude.  The following are ignored by AHP:\n")
              .append(" * ")
              .append(config.getUserExcludeString())
              .append("\n */\n");
        }

        // Turn the repo's into a map, because the map is what we will
        // be referring to.  The linkage is to property value groups.
        Map<String, PropertyValueGroup> repoMap
                = new HashMap<String, PropertyValueGroup>(repos.length);

        // Map of credential ID's
        Map<String, String> repoCreds
                                = new HashMap<String, String>(repos.length);

        for(PluginRepository repo : repos) {
            for(PropertyValueGroup pvg : repo.getPropertyValueGroups()) {
                // WE use this a lot
                String id = String.valueOf(pvg.getId());

                // Make a mapping
                repoMap.put(id, pvg);

                // Create credentials if we need them
                if((pvg.getPropertyValue("username") == null) ||
                   (pvg.getPropertyValue("username")
                       .getValue()
                       .length() == 0)) {
                    repoCreds.put(id, "");
                } else {
                    // Create credentials based on this repo.
                    String credId = "ahp-repo-pvg-" + id;

                    context.addJenkinsCredential(
                                pvg.getPropertyValue("username").getValue(),
                                pvg.getPropertyValue("password").getValue(),
                                credId
                    );

                    // add it
                    repoCreds.put(id, credId);
                }
            }
        }

        // Now, time to set up our checkouts.
        // Set our working dir.  AM-30 : Centralize this code ?
        sb.append("dir(a2j('");

        // For defaults, just use the CWD
        if(config.getWorkDirScript().getId() <= 0) {
            sb.append(".");
        } else {
            // For whatever reason, working dir scripts can have
            // newlines in them even though they are paths with
            // props and beanshell in them.
            String workDirScript = config.getWorkDirScript()
                                         .getPathScript()
                                         .replace("\n", "");

            // Process properties in it
            context.scanProperties(workDirScript);

            // add it to our stringbuffer.
            sb.append(workDirScript);
        }

        sb.append("')) {\n");

        for(PropertyValueGroup pvg : config.getPropertyValueGroups()) {
            // Each one of these is a checkout.
            // Keys we care about: repo, dirOffset (default .), remoteUrl,
            // branch (default master), remoteName (default origin),
            // revision (default HEAD)

            // Process all our strings.  This is kind of a clunky way
            // to do it, but its clunky now or clunky later :P  May as
            // we be clunky up front

            // This MUST be set and will never have properties
            String repoId = pvg.getPropertyValue("repo").getValue();

            // this also MUST be set and may have properties.
            String remoteUrl = pvg.getPropertyValue("remoteUrl").getValue();

            // We may need to suffix this on our repo.
            if((!remoteUrl.contains("://")) && (remoteUrl.charAt(0) != '/')) {
                remoteUrl = repoMap.get(repoId)
                                   .getPropertyValue("repoBaseUrl")
                                   .getValue() + "/" + remoteUrl;
            }

            context.scanProperties(remoteUrl);

            // Everything else is optional.  In my experience, these
            // are never null; but they COULD be, so we have to check.
            String dirOffset = "."; // Default per AHP spec

            if((pvg.getPropertyValue("dirOffset") != null) &&
               (pvg.getPropertyValue("dirOffset").getValue().length() > 0)) {
                dirOffset = pvg.getPropertyValue("dirOffset").getValue();
                context.scanProperties(dirOffset);
            }

            // AHP default
            String branch = "master";

            if((pvg.getPropertyValue("branch") != null) &&
               (pvg.getPropertyValue("branch").getValue().length() > 0)) {
                branch = pvg.getPropertyValue("branch").getValue();
                context.scanProperties(branch);
            }

            // AHP default
            String remoteName = "origin";

            if((pvg.getPropertyValue("remoteName") != null) &&
               (pvg.getPropertyValue("remoteName").getValue().length() > 0)) {
                remoteName = pvg.getPropertyValue("remoteName").getValue();
                context.scanProperties(remoteName);
            }

            // AHP Default is HEAD.  Or blank really
            String revision = "";

            if((pvg.getPropertyValue("revision") != null) &&
               (pvg.getPropertyValue("revision").getValue().length() > 0)) {
                revision = " " + pvg.getPropertyValue("revision").getValue();
                context.scanProperties(revision);
            }

            sb.append("    checkout(\n")
              .append("        [\n")
              .append("            $class: 'GitSCM',\n")
              .append("            doGenerateSubmoduleConfigurations: false,\n")
              .append("            submoduleCfg: [],\n")
              .append("            extensions: [\n")
              .append("                [$class: 'RelativeTargetDirectory', relativeTargetDir: a2j('")
              .append(dirOffset)
              .append("')]\n")
              .append("            ],\n")
              .append("            branches: [[name: a2j('")
              .append(branch)
              .append(revision) // This behavior seems to be what AHP does
              .append("')]],\n")
              .append("            userRemoteConfigs: [\n")
              .append("                [name: a2j('")
              .append(remoteName)
              .append("'), url: a2j('")
              .append(remoteUrl)
              .append("')");

            // Do we have credentials?
            if(repoCreds.get(repoId).length() > 0) {
                sb.append(", credentialsId: '")
                  .append(repoCreds.get(repoId))
                  .append("'");
            }

            sb.append("]\n")
              .append("            ]\n")
              .append("        ]\n")
              .append("    )\n");
        }

        // Close our dir
        sb.append("}\n");

        // Done!
        context.addCode(sb.toString());
    }
}
