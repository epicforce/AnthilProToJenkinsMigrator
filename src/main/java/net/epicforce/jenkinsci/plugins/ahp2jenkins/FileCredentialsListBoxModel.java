package net.epicforce.jenkinsci.plugins.ahp2jenkins;

import com.cloudbees.plugins.credentials.common.AbstractIdCredentialsListBoxModel;
import com.cloudbees.plugins.credentials.CredentialsNameProvider;

import edu.umd.cs.findbugs.annotations.NonNull;

import org.jenkinsci.plugins.plaincredentials.FileCredentials;

/**
 * Box model to provide a list of file credentials.
 *
 * Its hard to believe this doesn't exist, but as far as I can tell it does
 * not.  So I keep having to make it!
 *
 * @author sconley (sconley@epicforce.net)
 */
public class FileCredentialsListBoxModel
       extends AbstractIdCredentialsListBoxModel<FileCredentialsListBoxModel,
                                                 FileCredentials>
{
    /**
     * (@inheritDoc)
     */
    @NonNull
    @Override
    protected String describe(@NonNull FileCredentials fileCred)
    {
        return CredentialsNameProvider.name(fileCred);
    }
}
