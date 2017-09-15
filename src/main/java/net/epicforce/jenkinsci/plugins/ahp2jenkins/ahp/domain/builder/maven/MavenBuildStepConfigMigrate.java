package net.epicforce.jenkinsci.plugins.ahp2jenkins.ahp.domain.builder.maven;

/**
 * MavenBuildStepConfigMigrate.java
 *
 * This migrates a Maven Build Step.  Now, there are at least 3
 * was that I know if to run Maven in pipeline.
 *
 * * Build the command line ourselves
 * * Use Artifactory.newMavenBuild() if using Artifactory
 * * Use the Maven plugin (withMaven(...))
 *
 * In the case of the artifactory Maven, that's important for some
 * customers, so I think we should support it.  We should probably
 * support all 3 in the long run and maybe make some kind of toggle
 * to pick which one.  Perhaps make it a pipeline library with some
 * sort of variable or detection process to determine which method
 * is available?
 *
 * AM-27 : Improve the support here
 *
 * @author sconley (sconley@epicforce.net)
 */

import java.lang.StringBuffer;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import net.epicforce.jenkinsci.plugins.ahp2jenkins.context.JenkinsContext;
import net.epicforce.jenkinsci.plugins.ahp2jenkins.context.JenkinsStep;
import net.epicforce.migrate.ahp.exception.MigrateException;

import com.urbancode.anthill3.domain.builder.maven.MavenBuilder;
import com.urbancode.anthill3.domain.builder.maven.MavenBuildStepConfig;
import com.urbancode.anthill3.domain.builder.NameValuePair;


public class MavenBuildStepConfigMigrate extends JenkinsStep
{
    /**
     * Build the pipeline code based on the Jenkins step.
     *
     * @param context       The Jenkins context
     */
    @Override
    public void run(JenkinsContext context)
           throws MigrateException
    {
        MavenBuilder mb = ((MavenBuildStepConfig)context.getCurrentStep())
                          .getBuilder();

        // We're going to build a shell step for this.
        // AM-27: Detect what's available in Jenkins and take action
        //       based on installed plugins

        // Pipeline splits shell steps into 'bat' vs. 'sh'.  We will
        // build our command line, then use an if isUnix block.
        StringBuilder cmd = new StringBuilder(1024);

        cmd.append("mvn");

        // These are inline parameters
        if((mb.getMavenParams() != null) &&
           (mb.getMavenParams().length() > 0)) {
            context.scanProperties(mb.getMavenParams());
            cmd.append(" ")
               .append("' + a2j('")
               .append(mb.getMavenParams())
               .append("') + '");
        }

        // Do we need to add a -f ?
        if((mb.getBuildFilePath() != null) && (mb.getBuildFilePath().length() > 0)) {
            context.scanProperties(mb.getBuildFilePath());
            cmd.append(" -f ' + a2j('")
               .append(mb.getBuildFilePath())
               .append("') + '");
        }

        // The other build parameters that are in a list for some reason.
        for(String param : mb.getBuildParamArray()) {
            if(param.length() > 0) {
                context.scanProperties(param);
                cmd.append(" ' + aj2('").append(param).append("') + '");
            }
        }

        // And finally our goals
        cmd.append(" ").append(mb.getGoal());

        // String builder for our overall command
        StringBuilder sb = new StringBuilder(1024);

        // A dir block is required for working directory
        // An env block is requierd for JVM Properties

        sb.append("// Maven Build Step: ")
          .append(context.getCurrentStep().getName())
          .append("\n")
          .append("dir(a2j('");

        if((mb.getWorkDirOffset() != null) &&
           (mb.getWorkDirOffset().length() > 0)) {
            context.scanProperties(mb.getWorkDirOffset());
            sb.append(mb.getWorkDirOffset());
        } else {
            sb.append(".");
        }

        sb.append("')) {\n")
          .append("    withEnv([");

        // Do we need to append environment vars?
        List<String> envVars = new ArrayList<String>(3);

        // See if Java home is set and if its different from
        // the default, which I think is safe to ignore.
        if((mb.getJavaHomeVar() != null) &&
           (mb.getJavaHomeVar().length() > 0) &&
           (!mb.getJavaHomeVar().equals("${env/JAVA_HOME}"))) {
            // This isn't default, so let's set it.
            context.scanProperties(mb.getJavaHomeVar());
            envVars.add("'JAVA_HOME=' + a2j('" +
                        mb.getJavaHomeVar() +
                        "')"
            );
        }

        // See if JVM properties are set
        if((mb.getJvmParams() != null) && (mb.getJvmParams().length() > 0)) {
            context.scanProperties(mb.getJvmParams());
            envVars.add("'MAVEN_OPTS=' + a2j('" + mb.getJvmParams() +
                        "')"
            );
        }

        // Maven home
        if((mb.getMavenHomeVar() != null) &&
           (mb.getMavenHomeVar().length() > 0)) {
            context.scanProperties(mb.getMavenHomeVar());
            envVars.add("'PATH+MAVEN=' + a2j('" +
                        mb.getMavenHomeVar() +
                        "'), 'MAVEN_HOME=' + a2j('" +
                        mb.getMavenHomeVar() +
                        "')"
            );
        }

        // And finally, Sometimes there's also an environment array to add in.
        for(NameValuePair nvp : mb.getEnvironmentVariableArray()) {
            context.scanProperties(nvp.getName());
            context.scanProperties(nvp.getValue());

            envVars.add("a2j('" + nvp.getName() + "') + '=' + a2j('" +
                        nvp.getValue() + "')");
        }

        // If we have environment variables, let's add them.
        sb.append(
            StringUtils.join(envVars, ",")
        ).append("]) {\n");

        // Finally, our shell commands.
        sb.append("        if(isUnix()) {\n")
          .append("            sh '")
          .append(cmd.toString())
          .append("'\n        } else {\n")
          .append("            bat '")
          .append(cmd.toString())
          .append("'\n")
          .append("        }\n")
          .append("    }\n")
          .append("}\n");

        context.addCode(sb.toString());
    }
}
