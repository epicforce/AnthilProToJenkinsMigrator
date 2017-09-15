package net.epicforce.jenkinsci.plugins.ahp2jenkins;

/*
 * MigrateEngine.java
 *
 * The MigrateEngine does the 'heavy lifting' of actually running a
 * migration of different parts of Anthill to Jenkins.
 *
 * It manages the thread pool and provides status accessors and
 * controls.  It also handles the Jenkins-specific thread ACL management.
 *
 * Its designed to run in a thread itself so it doesn't block the UI
 * in any particular way.
 *
 * @author sconley (sconley@epicforce.net)
 */
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.logging.Level;

import hudson.security.ACL;
import hudson.security.ACLContext;

import net.epicforce.migrate.ahp.Migration;
import net.epicforce.migrate.ahp.exception.MigrateException;

import net.epicforce.jenkinsci.plugins.ahp2jenkins.config.AhpInstance;
import net.epicforce.jenkinsci.plugins.ahp2jenkins.context.JenkinsContext;
import net.epicforce.jenkinsci.plugins.ahp2jenkins.loader.JenkinsLoader;

public class MigrateEngine implements Runnable
{
    private static final Logger LOG =
                                Logger.getLogger(MigrateEngine.class.getName());

    /*****************************************************************
     * PROPERTIES
     ****************************************************************/

    // Keep track of our shutdown state.
    private boolean             doShutdown = false;
    private volatile boolean    isShutdown = false;

    // Keep track of our list of jobs.
    private static ConcurrentLinkedQueue<Migration> ahpJobs =
                                    new ConcurrentLinkedQueue<Migration>();

    // Keep track of our queued up jobs (futures)
    private List<Pair> futures = new LinkedList<Pair>();

    // Our thread pool
    private ExecutorService         threadService = null;

    // This can be shared by everyone
    private JenkinsLoader           loader = new JenkinsLoader();

    /*****************************************************************
     * METHODS
     ****************************************************************/

    /**
     * Get a list of our migration jobs.  Copy it out of
     * the queue so its more or less static.
     *
     * @return array of Migration objects
     */
    public static Migration[] getJobs()
    {
        return (Migration[])ahpJobs.toArray(new Migration[0]);
    }

    /**
     * Tell the thread to shutdown
     */
    public void shutdown()
    {
        doShutdown = true;
    }

    /**
     * Check if its shut down
     *
     * @return true if shutdown is complete, false if not
     */
    public boolean checkShutdown()
    {
        return isShutdown;
    }

    /**
     * Queue new jobs into the migrate engine.
     *
     * Jobs are always accepted; this will never error or otherwise abort.
     * If there's a problem, it will show up as a migration error.
     *
     * @param ahp            The instance to run against.
     * @param workflowIds    Our list of workflow ID's in string format
     * @param threadCount    A suggested thread count.  Suggestion may be ignored.
     *                       In practice, only the first call will pay attention
     *                       to this right now.
     */
    public void migrateWorkflows(final AhpInstance ahp,
                                 final List<String> workflowIds,
                                 final int threadCount)
    {
        try {
            // Initialize if we need to
            if(threadService == null) {
                threadService = Executors.newFixedThreadPool(threadCount);
            }

            for(String id : workflowIds) {
                // Create a context
                JenkinsContext context = new JenkinsContext();

                // Create a migrate -- if this crashes, skip it
                Migration migrate;

                try {
                    migrate = ahp.getMigration();
                } catch(MigrateException e) {
                    LOG.log(Level.WARNING,
                            "Got exception during migrate attempt", e
                    );

                    continue;
                }

                // Configure it
                try {
                    migrate.setWorkflowId(Long.parseLong(id));
                } catch(NumberFormatException e) {
                    LOG.log(Level.WARNING,
                            "The UI allowed an invalid workflow id: " + id
                    );

                    // Set it to something invalid.
                    // The UI shouldn't allow this
                    migrate.setWorkflowId(-1L);
                }

                migrate.setContext(context);
                migrate.setLoader(loader);

                // Make a shell for it
                JenkinsThread shell = new JenkinsThread(migrate);

                // queue it up
                Future<Migration> future = threadService.submit(shell,
                                                                migrate
                );

                Pair entry = new Pair(future, migrate);

                futures.add(entry);
                ahpJobs.add(migrate);
            }
        } catch(RejectedExecutionException | IllegalArgumentException e) {
            // Neither of these should happen.
            LOG.log(Level.SEVERE, "Could not queue a migration thread", e);
        }
    }

    /**
     * This thread manages jobs and passes status information around.
     *
     * It also does the shutdown of subjobs if necessary
     */
    public void run()
    {
        // Run as a system thread
        try(ACLContext notUsed = ACL.as(ACL.SYSTEM)) {
            while(!doShutdown) {
                if(futures.size() == 0) {
                    // AM-39 : It would be more efficient to let this shut down
                    // when there's no work.  But it makes adding new jobs to
                    // the migration difficult, because you could theoretically
                    // be adding a job while this thread has decided to shut
                    // down, thus leaving the new job in limbo.
                    try {
                        Thread.sleep(5000);
                    } catch(InterruptedException e) {
                        // shutdown
                        doShutdown = true;
                    }
                    continue;
                }

                // Otherwise, let's monitor our futures.
                for(Iterator<Pair> it = futures.iterator(); it.hasNext(); ) {
                    Pair pair = it.next();
                    Future<Migration> future = pair.getKey();

                    try {
                        Migration result = future.get(0, TimeUnit.SECONDS);

                        // This one is done -- we can close it.
                        result.close();
                        it.remove();
                    } catch(TimeoutException e) {
                        // don't care
                    } catch(InterruptedException | CancellationException e) {
                        // We're shutting down in thise case.  Close it
                        // nicely
                        LOG.log(Level.WARNING,
                                "Received early exit of Migration object",
                                e
                        );

                        pair.getValue().close();
                        it.remove();
                    } catch(ExecutionException e) {
                        // Got an error
                        LOG.log(Level.WARNING, "Received migration exception",
                                e
                        );

                        // set the error
                        pair.getValue().setError(
                            new MigrateException("Received exception", e)
                        );

                        pair.getValue().close();

                        // remove it
                        it.remove();
                    }
                }

                // sleep if we've got this far.
                // This is duplicated at the top of the loop by the empty queue
                // handler, but I'm hoping to remediate that into something more
                // tidy later.
                try {
                    Thread.sleep(5000);
                } catch(InterruptedException e) {
                    // shutdown - we got a cancel
                    doShutdown = true;
                }
            }

            // do our shutdown
            if(threadService != null) {
                threadService.shutdown();
            }

            // Wait to shut down
            while(true) {
                try {
                    if(threadService.awaitTermination(1, TimeUnit.HOURS)) {
                        for(Migration m : ahpJobs) {
                            m.close();
                        }

                        ahpJobs.clear();

                        // AM-40 : Is this a thread leak?  Do we need to
                        // iterate over futures and get() each one?
                        futures.clear();

                        isShutdown = true;
                        return;
                    }
                } catch(InterruptedException e) {
                    // In this case, let's upgrade to shutdownNow
                    threadService.shutdownNow();
                }
            }
        }
    }

    /**
     * This is a class to support a pair of Future and Migration.
     *
     * I wish Java had a Pair but it doesn't.  I tried to use
     * Map.Entry but that turned into a stupid-fest.
     *
     * To minimize code flux from my failed attempt, the signature
     * is like a Map.Entry and it uses getKey for the Future and
     * getValue for the Migration.
     */
    public static class Pair
    {
        private Future<Migration> key;
        private Migration value;

        /**
         * @return the future... oooOOOooo
         */
        public Future<Migration> getKey()
        {
            return key;
        }

        /**
         * @return the migration
         */
        public Migration getValue()
        {
            return value;
        }

        /**
         * Simple constructor
         *
         * @param key the future
         * @param value the migration
         */
        public Pair(Future<Migration> key, Migration value)
        {
            this.key = key;
            this.value = value;
        }
    }

    /**
     * This is a thread shell to set Jenkins ACL on a Migration
     */
    public static class JenkinsThread implements Runnable
    {
        // Our Migration object
        private Migration   ahp;

        /**
         * Basic constructor for the shell
         *
         * @param ahp   the migration object to run
         */
        public JenkinsThread(Migration ahp)
        {
            this.ahp = ahp;
        }

        /**
         * Thin wrapper to run a migration using a Jenkins ACL
         */
        public void run()
        {
            try(ACLContext notUsed = ACL.as(ACL.SYSTEM)) {
                ahp.run();
            }
        }
    }
}
