package net.epicforce.jenkinsci.plugins.ahp2jenkins.config;

/*
 * AhpInstance.java
 *
 * Implementation of a single AHP Instance.  This is to store
 * global configuration.
 *
 * @author sconley
 */

import java.io.IOException;
import java.io.File;
import java.io.Serializable;
import java.lang.StringBuilder;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import edu.umd.cs.findbugs.annotations.NonNull;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;

import org.jenkinsci.plugins.plaincredentials.FileCredentials;

import net.epicforce.migrate.ahp.Migration;
import net.epicforce.migrate.ahp.exception.MigrateException;
import net.epicforce.jenkinsci.plugins.ahp2jenkins.FileCredentialsListBoxModel;


// AM-33: Support keystore passwords other than the default
// ... I have never seen anyone use anything other than the
// default but hey, we should support it because we can!

public class AhpInstance extends AbstractDescribableImpl<AhpInstance>
{
    private static final Logger LOG =
                                Logger.getLogger(AhpInstance.class.getName());

    /*****************************************************************
     * PROPERTIES
     ****************************************************************/

    private final String hostname;
    private final String port;
    private final String userCredentialId;
    private final String keystoreCredentialId;

    /*****************************************************************
     * CONSTRUCTORS
     ****************************************************************/

    /**
     * Constructor to make an AhpInstance, which is a data object
     * representing an AHP connection.
     *
     * @param hostname              The host
     * @param port                  A numeric port as a string
     * @param userCredentialId      Credential ID string
     * @param keystoreCredentialId  Credential ID string
     */
    @DataBoundConstructor
    public AhpInstance(String hostname, String port, String userCredentialId,
                       String keystoreCredentialId)
    {
        this.hostname = Util.fixEmptyAndTrim(hostname);
        this.port = Util.fixEmptyAndTrim(port);
        this.userCredentialId = Util.fixEmpty(userCredentialId);
        this.keystoreCredentialId = Util.fixEmpty(keystoreCredentialId);
    }

    /**
     * There is problem a considerably less dumb way to do this, but
     * I don't know it and I don't want to spend a lot of time figuring
     * it out right now.
     *
     * AM-34: Improve this.
     *
     * @param serializedString          Constructed from toString
     */
    static private Pattern ahpInstancePattern = Pattern.compile(
        "AhpInstance\\{hostname='(.+)', port='(.+)', userCredentialId='(.+)', keystoreCredentialId='(.*)'\\}"
    );
    public AhpInstance(String serializedString)
    {
        Matcher m = ahpInstancePattern.matcher(serializedString);

        if(m.find()) {
            this.hostname = Util.fixEmptyAndTrim(m.group(1));
            this.port = Util.fixEmptyAndTrim(m.group(2));
            this.userCredentialId = Util.fixEmptyAndTrim(m.group(3));
            this.keystoreCredentialId = Util.fixEmptyAndTrim(m.group(4));
        } else {
            this.hostname = null;
            this.port = null;
            this.userCredentialId = null;
            this.keystoreCredentialId = null;
        }
    }

    /*****************************************************************
     * ACCESSORS
     ****************************************************************/

    /**
     * @return host name
     */
    public String getHostname()
    {
        return hostname;
    }

    /**
     * @return port as string
     */
    public String getPort()
    {
        return port;
    }

    /**
     * @return user credential ID string
     */
    public String getUserCredentialId()
    {
        return userCredentialId;
    }

    /**
     * @return keystore credential ID string
     */
    public String getKeystoreCredentialId()
    {
        return keystoreCredentialId;
    }

    /*****************************************************************
     * METHODS
     ****************************************************************/

    /**
     * Constructs a Migration object based on the data in this AhpInstance.
     *
     * Handles all the finnicky details of unpacking the keystore in a
     * relatively secure way (since it has to be on the filesystem for AHP
     * to pick it up) and then making sure it gets cleaned up.
     *
     * @return a Migration object ready for use
     * @throws MigrateException if we could not connect to AHP or some other
     *         similar error from Migration.
     */
    public Migration getMigration()
           throws MigrateException
    {
        // If we don't have a required item, that's a paddling.
        if((hostname == null) || (port == null) || (userCredentialId == null)) {
            throw new MigrateException("Hostname, port, and user credential ID "
                                       + "are all required parameters."
            );
        }

        // Keep this around for cleanup purposes.  It will still be null if
        // the keystore was never used.
        File tempKeyfile = null;

        try {
            // Get our user credential
            StandardUsernamePasswordCredentials user =
                CredentialsMatchers.firstOrNull(
                    CredentialsProvider.lookupCredentials(
                                    StandardUsernamePasswordCredentials.class,
                                    (Item) null,
                                    ACL.SYSTEM,
                                    Collections.<DomainRequirement>emptyList()
                    ),
                    CredentialsMatchers.withId(userCredentialId)
            );

            // This is a paddlin'
            if(user == null) {
                throw new MigrateException("Could not load user credentials for"
                                           + " selected AHP instance."
                );
            }

            // Keystore path
            String keystorePath = null;

            // Handle keystore if we need to
            if(Util.fixEmptyAndTrim(keystoreCredentialId) != null) {
                FileCredentials keystore =
                    CredentialsMatchers.firstOrNull(
                        CredentialsProvider.lookupCredentials(
                                    FileCredentials.class,
                                    (Item) null,
                                    ACL.SYSTEM,
                                    Collections.<DomainRequirement>emptyList()
                        ),
                        CredentialsMatchers.withId(userCredentialId)
                );

                // This is a paddlin'
                if(keystore == null) {
                    throw new MigrateException("Could not load keystore for " +
                                               "selected AHP instance."
                    );
                }

                // Set up a temporary key file
                tempKeyfile = File.createTempFile("a2j", ".tmp");

                // Copy it
                Files.copy(keystore.getContent(),
                           tempKeyfile.getCanonicalFile().toPath(),
                           StandardCopyOption.REPLACE_EXISTING
                );

                keystorePath = tempKeyfile.getCanonicalPath();
            }

            // Try to construct
            // AM-33: support passwords other than the default for
            // keystore.
            return new Migration(hostname, Integer.parseInt(port),
                                 user.getUsername(),
                                 user.getPassword().getPlainText(),
                                 keystorePath, "changeit"
            );
        } catch(IOException e) {
            throw new MigrateException("IO Exception while connecting to AHP",
                                       e
            );
        } finally {
            // Delete temp file.
            if((tempKeyfile != null) && (!tempKeyfile.delete())) {
                try {
                    LOG.log(Level.WARNING, "Could not delete temp file: {}",
                            tempKeyfile.getCanonicalPath()
                    );
                } catch(IOException e) {
                    LOG.log(Level.SEVERE, "Could not delete temp file OR " +
                                          "determine its path name!", e
                    );
                }
            }
        }
    }

    /*****************************************************************
     * COMMON ASSISTIVE METHODS
     ****************************************************************/

    /**
     * @return some logical display of the object contents.
     */
    @Override
    public String toString()
    {
        final StringBuilder sb = new StringBuilder("AhpInstance{");
        sb.append("hostname='").append(hostname).append("', port='")
          .append(port).append("', userCredentialId='")
          .append(userCredentialId).append("', keystoreCredentialId='")
          .append("'}");

        return sb.toString();
    }

    /**
     * @return boolean if this object equals another of the same type.
     */
    @Override
    public boolean equals(Object o)
    {
        // 'easy' checks of reference and type
        if(this == o) {
            return true;
        }

        if(!(o instanceof AhpInstance)) {
            return false;
        }

        // check values
        AhpInstance ahp = (AhpInstance)o;

        // Do null checks first
        if(
            (((hostname == null) || (ahp.hostname == null)) &&
              (hostname != ahp.hostname)) ||
            (((port == null) || (ahp.port == null)) &&
              (port != ahp.port)) ||
            (((userCredentialId == null) || (ahp.userCredentialId == null))
              && (userCredentialId != ahp.userCredentialId)) ||
            (((keystoreCredentialId == null) ||
              (ahp.keystoreCredentialId == null)) &&
              (keystoreCredentialId != ahp.keystoreCredentialId))
          ) {
            return false;
        }

        // Do equality check -- if there are nulls, both should
        // be null at this point, so only have to check null on
        // one.
        return ((hostname == null) || hostname.equals(ahp.hostname)) &&
               ((port == null) || port.equals(ahp.port)) &&
               ((userCredentialId == null) ||
                userCredentialId.equals(ahp.userCredentialId)) &&
               ((keystoreCredentialId == null) ||
                keystoreCredentialId.equals(ahp.keystoreCredentialId));
    }

    /**
     * I dug while trying to figure out how to make a logical hashCode method
     * as I have never done it before.  Given the relatively small number of
     * instances we'll be dealing with (likely never more than 1!) this is
     * probably more than sufficient.
     *
     * @return int hash code based on members.
     */
    @Override
    public int hashCode() {
        int res = 0;

        if(hostname != null) {
            res = res * 31 + hostname.hashCode();
        }

        if(port != null) {
            res = res * 31 + port.hashCode();
        }

        if(userCredentialId != null) {
            res = res * 31 + userCredentialId.hashCode();
        }

        if(keystoreCredentialId != null) {
            res = res * 31 + keystoreCredentialId.hashCode();
        }

        return res;
    }

    /**
     * Descriptor for our form on the Jenkins global settings page.
     */
    @Extension
    public static class DescriptorImpl extends Descriptor<AhpInstance>
    {
        /**
         * A display name is not necessary in this case, because this
         * is essentially a child form of something that will display
         * on the configuration screen.
         *
         * @return Empty string
         */
        @Override
        public String getDisplayName()
        {
            return "";
        }

        /*************************************************************
         * VALIDATION CALLBACKS
         *
         * Only usable within the module
         ************************************************************/

        /**
         * Validate hostname
         *
         * @param value    Host name string
         * @return proper FormValidation response.
         */
        @Restricted(NoExternalUse.class)
        public FormValidation doCheckHostname(@QueryParameter String value)
        {
            if(Util.fixEmptyAndTrim(value) == null) {
                return FormValidation.error("You must enter a host.");
            }

            return FormValidation.ok();
        }

        /**
         * Validate port
         *
         * @param value    port string
         * @return proper FormValidation response.
         */
        @Restricted(NoExternalUse.class)
        public FormValidation doCheckPort(@QueryParameter String value)
        {
            if(Util.fixEmptyAndTrim(value) == null) {
                return FormValidation.error("You must enter a port.");
            }

            // Make sure its an int val
            try {
                Integer.parseInt(value);
                return FormValidation.ok();
            } catch(NumberFormatException e) {
                return FormValidation.error("Port must be a port number.");
            }
        }

        /**
         * Validate credentials -- I'm not sure if this works?  I
         * think this is never called by the form callback.
         *
         * @param value    credential ID
         * @return proper FormValidation response.
         */
        @Restricted(NoExternalUse.class)
        public FormValidation
                            doCheckUserCredentialId(@QueryParameter String value)
        {
            if(Util.fixEmptyAndTrim(value) == null) {
                return FormValidation.error(
                    "You must provide an admin user to use for the migration.");
            }

            return FormValidation.ok();
        }

        /**
         * Generate a list box model with list of available user credentials.
         *
         * @return ListBoxModel of user credentials
         */
        public ListBoxModel doFillUserCredentialIdItems()
        {
            List<StandardUsernamePasswordCredentials> creds;

            creds = CredentialsProvider.lookupCredentials(
                                    StandardUsernamePasswordCredentials.class,
                                    (Item) null,
                                    ACL.SYSTEM,
                                    Collections.<DomainRequirement>emptyList()
            );

            return new StandardUsernameListBoxModel().withEmptySelection()
                                                     .withAll(creds);
        }

        /**
         * Generate a list box model with list of available keystore files.
         *
         * @return ListBoxModel of keystore files
         */
        public ListBoxModel doFillKeystoreCredentialIdItems()
        {
            List<FileCredentials> creds;

            creds = CredentialsProvider.lookupCredentials(
                                    FileCredentials.class,
                                    (Item) null,
                                    ACL.SYSTEM,
                                    Collections.<DomainRequirement>emptyList()
            );

            return new FileCredentialsListBoxModel().withEmptySelection()
                                                    .withAll(creds);
        }
    }
}
