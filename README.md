The Anthill Pro to Jenkins Migration Tool uses the Anthill Pro Remoting API to process Anthill Originating Workflows and convert them into 
Jenkins Pipeline jobs.  The produced Jenkins jobs have parameters based on the Anthill Pro properties used by the jobs, and are neatly 
formatted with comments explaining all the decisions made by the plugin during the migration process.

This Migration can run in batches, allowing you to queue up a set of Anthill Pro jobs that you want to migrate and then performing the 
migration in parallel with a thread pool.  This is handy in particular for cases where your Anthill Pro server and destination Jenkins
server are in separate datacenters, when testing over the wire, or when migrating hundreds or thousands of workflows.

Key features of the migration plugin include:

Simple interface — pick your Anthill Pro project and workflows, and go!  There’s not much user input required to do a migration.
Very clean, commented, easy-to-read generated pipeline scripts that map one-to-one with your Anthill Pro workflow job configurations.
Migration of properties, including secure properties, from Anthill Pro to Jenkins parameters.
The plugin enables Jenkins Pipeline scripts to process inline beanshell scripts which are commonly used in AHP workflows (properties of
the format ${bsh:…}).
Also, the plugin enables Jenkins Pipeline scripts to have parameters that refer to other parameters.  This is necessary because Anthill 
Pro properties often reference each-other which is not supported by Pipeline out of the box.
Use of the Jenkins credential store for migrated credentials (such as source repository credentials).
Support for preconditions and other Anthill Pro scripting mechanisms that are difficult to deal with by hand.
The ability to just export properties without creating Jenkins jobs in case you would rather use your own Pipeline template.  Use our 
generated Pipeline scripts as a base to make your own template — we’ll make sure you didn’t miss anything.
Very easy to test produced pipeline jobs, with clear error messages if there’s a problem with the migration.
