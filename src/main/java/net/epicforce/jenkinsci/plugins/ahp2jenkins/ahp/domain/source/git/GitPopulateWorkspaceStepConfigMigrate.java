package net.epicforce.jenkinsci.plugins.ahp2jenkins.ahp.domain.source.git;

/**
 * GitPopulateWorkspaceStepConfigMigrate.java
 *
 * Does the migration of a Git Populate Workspace step to its Jenkins
 * equivalent.
 *
 * This is the built-in GIT that lacks many features.  There's a plugin
 * GIT that is way more comprehensive.
 *
 * @author sconley (sconley@epicforce.net)
 */

import net.epicforce.jenkinsci.plugins.ahp2jenkins.context.JenkinsContext;
import net.epicforce.jenkinsci.plugins.ahp2jenkins.context.JenkinsStep;
import net.epicforce.migrate.ahp.exception.MigrateException;

import com.urbancode.anthill3.domain.profile.BuildProfile;
import com.urbancode.anthill3.domain.source.git.GitPopulateWorkspaceStepConfig;
import com.urbancode.anthill3.domain.source.git.GitSourceConfig;


public class GitPopulateWorkspaceStepConfigMigrate extends JenkinsStep
{
    /**
     * Implementation
     *
     * @param context           The migration context
     */
    @Override
    public void run(JenkinsContext context)
           throws MigrateException
    {
        // Jenkins has about a billion more config options than
        // AHP does when it comes to GIT.  AHP's support is actually
        // quite rudimentary.

        // BuildProfile has all the repository config info for the
        // workflow.
        BuildProfile bp = context.getWorkflow().getBuildProfile();

        // Get the source configuration
        GitSourceConfig gitConfig = (GitSourceConfig)bp.getSourceConfig();
        
        // Normally, user and password are stored in a Repository
        // object (i.e. for SVN) but GIT doesn't work that way.
        // The repository object can be completely ignored, its got
        // nothing useful in it.

        // Some SCM's support multiple checkout (such as SVN's), but
        // GIT does not.  The multiple checkouts would be in
        // modules usually, but for GIT we have no modules.

        StringBuilder sb = new StringBuilder(2048);

        // Get our actual step
        GitPopulateWorkspaceStepConfig step =
            (GitPopulateWorkspaceStepConfig)context.getCurrentStep();

        // Put some notes in
        sb.append("// GIT Populate Workspace Step\n")
          .append("// Note that Jenkins ignores the Workspace Date\n")
          .append("// AHP would have used: ")
          .append(step.getWorkspaceDateScript())
          .append("\n");

        // We may not care about these
        if((gitConfig.getFilepathExcludeString() != null) &&
           (gitConfig.getFilepathExcludeString().length() != 0)) {
            sb.append("/* Jenkins does not support the changelog file path\n")
              .append(" * exclude.  The following are ignored by AHP:\n")
              .append(" * ")
              .append(gitConfig.getFilepathExcludeString())
              .append("\n */\n");
        }

        if((gitConfig.getUserExcludeString() != null) &&
           (gitConfig.getUserExcludeString().length() != 0)) {
            sb.append("/* Jenkins does not support the changelog user\n")
              .append(" * exclude.  The following are ignored by AHP:\n")
              .append(" * ")
              .append(gitConfig.getUserExcludeString())
              .append("\n */\n");
        }

        // Set our working dir.
        sb.append("dir(a2j('");

        // For defaults, just use the CWD
        if(gitConfig.getWorkDirScript().getId() <= 0) {
            sb.append(".");
        } else {
            // For whatever reason, working dir scripts can have
            // newlines in them even though they are paths with
            // props and beanshell in them.
            String workDirScript = gitConfig.getWorkDirScript()
                                            .getPathScript()
                                            .replace("\n", "");

            // Process properties in it
            context.scanProperties(workDirScript);

            // add it to our stringbuffer.
            sb.append(workDirScript);
        }

        sb.append("')) {\n");


        // Scan properties as needed
        String branch = gitConfig.getRevision();

        // This can be null
        if((branch == null) || (branch.length() == 0)){
            // Per AHP documentation, master is default for blank
            branch = "master";
        } else {
            context.scanProperties(branch);
        }

        context.scanProperties(gitConfig.getRepositoryUrl());
        context.scanProperties(gitConfig.getRepositoryName());    

        // Set up our checkout
        sb.append("    checkout(\n")
          .append("        [\n")
          .append("            $class: 'GitSCM',\n")
          .append("            branches: [[name: a2j('")
          .append(branch)
          .append("')]],\n")
          .append("            doGenerateSubmoduleConfigurations: false,\n")
          .append("            extensions: [\n")
          .append("                [\n")
          .append("                    $class: 'RelativeTargetDirectory',\n")
          .append("                    relativeTargetDir: a2j('")
          .append(gitConfig.getRepositoryName())
          .append("')\n")
          .append("                ]");

        // Do we have more extensions to add?  Only one we care about
        // right now.
        if(step.getCleanWorkspace()) {
            sb.append(",\n")
              .append("                [\n")
              .append("                    $class: 'CleanBeforeCheckout'\n")
              .append("                ]");
        }

        sb.append("\n")
          .append("            ],\n")
          .append("            submoduleCfg: [],\n")
          .append("            userRemoteConfigs: [[url: a2j('")
          .append(gitConfig.getRepositoryUrl())
          .append("')]]\n")
          .append("        ]\n")
          .append("    )\n")
          .append("}\n");

        // Ta-da!
        context.addCode(sb.toString());

        // The built-in step forces a directory change into the new repo.
        // This confused the crap out of me :P
        sb.setLength(0);
        sb.append("dir(a2j('").append(gitConfig.getRepositoryName())
          .append("')) {\n");

        context.addCode(sb.toString(), 1);
    }
}
