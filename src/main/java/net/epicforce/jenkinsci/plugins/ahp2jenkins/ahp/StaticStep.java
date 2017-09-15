package net.epicforce.jenkinsci.plugins.ahp2jenkins.ahp;

/*
 * StaticStep.java
 *
 * There are many classes in Anthill which we can safely ignore.
 * Rather than implement each one individually, we'll let this
 * step do the "heavy" lifting.
 *
 * Basically, it allows the injection of a comment block or any
 * other static thing you want, and is for steps that are either
 * ignored or resolve to a 'constant' on the Jenkins side.
 *
 * @author sconley@epicforce.net
 */

import net.epicforce.jenkinsci.plugins.ahp2jenkins.context.JenkinsContext;
import net.epicforce.jenkinsci.plugins.ahp2jenkins.context.JenkinsStep;

public abstract class StaticStep extends JenkinsStep
{
    /**
     * Implement this to return whatever the comment will be.
     *
     * I suppose it doesn't have to be just comments -- it
     * can be any static string.  It will be injected in the
     * Pipeline workflow as-is, so you are responsible for putting
     * any comment tags you want.
     *
     * Note a lack of context injection.  This is part of the deal
     * and helps avoid an extra import on the child classes.
     *
     * @return a string to inject
     */
    public abstract String getCode();

    /**
     * Implement run
     *
     * @param context    Our migration context
     */
    @Override
    public void run(JenkinsContext context)
    {
        context.addCode(getCode());
    }
}
