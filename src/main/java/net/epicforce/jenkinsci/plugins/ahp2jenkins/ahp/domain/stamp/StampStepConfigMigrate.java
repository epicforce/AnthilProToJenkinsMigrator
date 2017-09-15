package net.epicforce.jenkinsci.plugins.ahp2jenkins.ahp.domain.stamp;

/*
 * StampStepConfigMigrate.java
 *
 * Jenkins doesn't really have an analog to the AHP notion of stamping.
 * At least not as far as I know.
 *
 * AM-31: Is there a Jenkins analog I don't know about?  If so, we should
 *       probably implement this.
 *
 * @author sconley (sconley@epicforce.net)
 */

import net.epicforce.jenkinsci.plugins.ahp2jenkins.ahp.StaticStep;


public class StampStepConfigMigrate extends StaticStep
{
    /**
     * @return a string explaining why this step was skipped
     */
    @Override
    public String getCode()
    {
        return "// Skipped an AHP Stamp Step\n" +
               "// Jenkins does not really have an equivalent.\n";
    }
}
