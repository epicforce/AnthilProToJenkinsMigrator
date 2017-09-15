package net.epicforce.jenkinsci.plugins.ahp2jenkins.beanshell;

/**
 * AbstractHelper.java
 *
 * This is some common methods for all the beanshell helpers.
 * The beanshell helpers, in turn, support the different
 * helpers used by Anthill Pro beanshell.
 *
 * @author sconley (sconley@epicforce.net)
 */

import org.jenkinsci.plugins.workflow.steps.StepContext;


public abstract class AbstractHelper
{
    /*
     * Many of these will need the StepContext
     */
    protected StepContext       context;

    /**
     * Setter for the context
     *
     * @param context       The context to set
     */
    public void setContext(StepContext context)
    {
        this.context = context;
    }

    /**
     * Getter for the context
     *
     * @return the context
     */
    public StepContext getContext()
    {
        return context;
    }
}
