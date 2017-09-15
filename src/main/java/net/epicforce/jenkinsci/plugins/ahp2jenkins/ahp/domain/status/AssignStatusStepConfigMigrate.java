package net.epicforce.jenkinsci.plugins.ahp2jenkins.ahp.domain.status;

/*
 * AssignStatusStepConfigMigrate.java
 *
 * This is essentially a "no-op" because Jenkins does this its own
 * way.
 *
 * AM-32: Is there any valid reason to implement these steps?  They
 *       are (always?) used to say 'did one of the steps fail?  Yup,
 *       mark it failed'.
 *
 * @author sconley (sconley@epicforce.net)
 */

import net.epicforce.jenkinsci.plugins.ahp2jenkins.ahp.StaticStep;


public class AssignStatusStepConfigMigrate extends StaticStep
{
    /**
     * @return a string explaining why this step was skipped
     */
    @Override
    public String getCode()
    {
        return "// Skipped an AHP Assign Status Step.\n" +
               "// Jenkins' default behavior is usually sufficient.\n";
    }
}
