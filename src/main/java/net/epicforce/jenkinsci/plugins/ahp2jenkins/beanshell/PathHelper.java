package net.epicforce.jenkinsci.plugins.ahp2jenkins.beanshell;

/**
 * PathHelper.java
 *
 * This class emulates the like-named helper in Anthill Pro.
 * It has a single "makeSafe" method that simply cleans paths so
 * forbidden characters aren't in it.
 *
 * The way this class operates is 100% like Anthill's, it will always
 * operate teh same.
 *
 * @author sconley (sconley@epicforce.net)
 */

import java.lang.StringBuilder;

public class PathHelper extends AbstractHelper
{
    /**
     * Takes a string and makes it safe for a path, allowing only
     * characters that are java identifiers and replaces the rest
     * with underscores.
     *
     * @param val               The value to replace.
     * @return the cleaned up string
     */
    public String makeSafe(Object val)
    {
        StringBuilder sb = new StringBuilder();
        String path = String.valueOf(val);

        for(int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);

            if(Character.isJavaIdentifierPart(c)) {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }

        return sb.toString();
    }
}
