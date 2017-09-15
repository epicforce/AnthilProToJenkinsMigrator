package net.epicforce.jenkinsci.plugins.ahp2jenkins.ahp.domain.publisher.artifact;

/**
 * ArtifactDeliverStepConfigMigrate.java
 *
 * Deliver artifacts to Jenkins
 *
 * @author sconley (sconley@epicforce.net)
 */

import java.lang.StringBuilder;

import net.epicforce.jenkinsci.plugins.ahp2jenkins.context.JenkinsContext;
import net.epicforce.jenkinsci.plugins.ahp2jenkins.context.JenkinsStep;
import net.epicforce.migrate.ahp.exception.MigrateException;

import com.urbancode.anthill3.domain.artifacts.ArtifactSet;
import com.urbancode.anthill3.domain.profile.ArtifactDeliverPatterns;
import com.urbancode.anthill3.domain.publisher.artifact.ArtifactDeliverStepConfig;


public class ArtifactDeliverStepConfigMigrate extends JenkinsStep
{
    /**
     * Implementation of Run which will add pipeline code for artifact
     * collection.
     *
     * @param context       The context of the migration
     * @throws MigrateException on any form of error
     */
    @Override
    public void run(JenkinsContext context)
           throws MigrateException
    {
        // Get our artifact confiuguration
        ArtifactDeliverPatterns[] patterns = context.getWorkflow()
                                                    .getBuildProfile()
                                                    .getArtifactConfigArray();

        // Get our artifact set
        ArtifactSet art = ((ArtifactDeliverStepConfig)context.getCurrentStep())
                          .getArtifactDeliver()
                          .getArtifactSet();

        // Set up our code
        StringBuilder sb = new StringBuilder(1024);

        // Find the patterns that goes with this artifact set.
        // There can be more than one!
        for(ArtifactDeliverPatterns pat : patterns) {
            if(pat.getArtifactSet().getId() == art.getId()) {
                // We found it!
                sb.append("dir(a2j('");

                if((pat.getBaseDirectory() != null) &&
                   (pat.getBaseDirectory().length() > 0)) {
                    context.scanProperties(pat.getBaseDirectory());
                    sb.append(pat.getBaseDirectory());
                }

                sb.append("')) {\n")
                  .append("    archiveArtifacts artifacts: a2j('");

                // This could be null
                String artTemp = pat.getArtifactPatternsString();

                if((artTemp == null) || (artTemp.length() == 0)) {
                    sb.append("**/*"); // default, per anthil docs
                } else {
                    context.scanProperties(artTemp);
                    sb.append(artTemp.replace("\n", ","));
                }

                sb.append("')");

                // Excludes?
                artTemp = pat.getArtifactExcludePatternsString();

                if((artTemp != null) && (artTemp.length() > 0)) {
                    context.scanProperties(artTemp);
                    sb.append(", excludes: a2j('")
                      .append(artTemp.replace("\n", ","))
                      .append("')");
                }

                sb.append(", onlyIfSuccessful: true\n")
                  .append("}\n");
            }
        }

        if(sb.length() > 0) {
            context.addCode(sb.toString());
        }
    }
}
