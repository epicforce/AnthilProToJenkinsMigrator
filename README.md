# Anthill Pro to Jenkins Migrator

The Anthill Pro to Jenkins Migration Tool uses the Anthill Pro Remoting API to process Anthill Originating Workflows and convert them into Jenkins Pipeline jobs.  The produced Jenkins jobs have parameters based on the Anthill Pro properties used by the jobs, and are neatly formatted with comments explaining all the decisions made by the plugin during the migration process.

This Migration can run in batches, allowing you to queue up a set of Anthill Pro jobs that you want to migrate and then performing the migration in parallel with a thread pool.  This is handy in particular for cases where your Anthill Pro server and destination Jenkins server are in separate datacenters, when testing over the wire, or when migrating hundreds or thousands of workflows.

Key features:

* Simple interface; pick your Anthill Pro project and workflows, and go!  There is not much user input required to do a migration.
* Very clean, commented, easy-to-read generated pipeline scripts that map one-to-one with your Anthill Pro workflow job configurations.
* Migration of properties, including secure properties, from Anthill Pro to Jenkins parameters.
* The plugin enables Jenkins Pipeline scripts to process inline beanshell scripts which are commonly used in AHP workflows (properties of the format ${bsh:}}).
* Also, the plugin enables Jenkins Pipeline scripts to have parameters that refer to other parameters.  This is necessary because Anthill  Pro properties often reference each-other which is not supported by Pipeline out of the box.
* Use of the Jenkins credential store for migrated credentials (such as source repository credentials).
* Very easy to test produced pipeline jobs, with clear error messages if there's a problem with the migration.

Key planned features of the migration plugin include:

* Support for more Anthill steps (there's a pretty limited set implemented at the moment)
* Support for preconditions and other Anthill Pro scripting mechanisms that are difficult to deal with by hand.
* The ability to just export properties without creating Jenkins jobs in case you would rather use your own Pipeline template.  Use our generated Pipeline scripts as a base to make your own template and we'll make sure you didn't miss anything.


STATUS OF PROJECT
=================
Epic Force started this project with the thought there might be a demand for migration from Anthill Pro to Jenkins.  So far, we haven't gotten much demand for this, so the project is a bit stalled.  As such, this doesn't migrate a whole lot of stuff other than basic Maven jobs.

If you are interested in this project, we're interested in partners willing to help us finish this, either by sponsoring further work or by providing aide.  We'd be happy to finish the work, but with no clear users yet, we're back-burnering this and have chosen to open source it to see if there's any interest.

Feel free to reach out to : engineering@epicforce.net if you have any questions or interest in this project.


BUILDING
========
In order to build this project, you must first have a net.epicforce.migrate.ahp in your packages.  This can be done (at the moment) by doing a 'mvn clean install' to make a local cache of the AHP library.  It will automatically bake in all the IBM UrbanCode stuff needed to run the migration.

You can get the library here: https://github.com/epicforce/AnthillProMigratorLibrary

PLEASE NOTE: Different versions of AHP have different remoting API's.  They are NOT compatible with each-other.  Therefore, you must build this plugin for whatever version of AHP you anticipate interfacing with.  You may get errors about serial numbers not matching if you try to use mis-matched remoting API's.

Then you can do a typical:

```
mvn clean package
```

to build your plugin (.hpi) file.
