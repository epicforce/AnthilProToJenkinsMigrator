package net.epicforce.jenkinsci.plugins.ahp2jenkins.code;

/**
 * Code is a series of code blocks that can be exported as a string.
 *
 * The Code class handles the tab interaction between CodeBlocks.
 *
 * @author sconley (sconley@epicforce.net)
 */

import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Code
{
    /*****************************************************************
     * PROPERTIES
     ****************************************************************/

    // Our blocks of code
    private LinkedList<CodeBlock> code = new LinkedList<CodeBlock>();

    // Estimated size of our contained code block.
    private int sizeEstimate = 0;

    /*****************************************************************
     * METHODS
     ****************************************************************/

    /**
     * Get current tab level.  That is to say, what tab level the
     * next item will get if it doesn't override it otherwise.
     *
     * @return integer tab level
     */
    public int getCurrentTabLevel()
    {
        // Because we have to catch the exception, we may as well
        // use it instead of pre-checking
        try {
            CodeBlock block = code.getLast();

            return (block.getTabLevel() + block.getTabModifier());
        } catch(NoSuchElementException e) {
            return 0;
        }
    }

    /**
     * Add a new code block.
     *
     * Note that if 'tabModifier' is a number, it will be set as
     * the modifier for the NEXT block that is put in.  It does not
     * impact the current block's tab level.
     *
     * If forceLevel is a number, we will force that tab level and
     * ignore the previous block's level/modifier.  If its null, we
     * will use the previous block's level/modifier to determine
     * this block's level.
     *
     * If forceLevel is -1, we will decrement the tab level for this
     * row and that will be the new tab level.  Great for putting a
     * } on a line by itself.
     *
     * There are a few aliases to this for ease of use.
     *
     * @param code          The code block to add.
     * @param tabModifier   A number, or null to get the default (0)
     * @param forceLevel    A number, null to calcuate it, or -1 to decrement
     */
    public void add(String code, Integer tabModifier, Integer forceLevel)
    {
        // Calcuate defaults
        if(tabModifier == null) {
            tabModifier = 0;
        }

        if(forceLevel == null) {
            forceLevel = getCurrentTabLevel();
        } else if(forceLevel == -1) {
            forceLevel = getCurrentTabLevel()-1;

            // This shouldn't happen
            if(forceLevel < 0) {
                forceLevel = 0;
            }
        }

        // Create code block and add it.
        CodeBlock newBlock = new CodeBlock(code, forceLevel, tabModifier);

        // Update our size.
        sizeEstimate += code.length();

        this.code.add(newBlock);
    }

    /**
     * Same as above, but calculates tab level.
     *
     * @param code          The code block to add.
     * @param tabModifier   A number, or null to get the default (0)
     */
    public void add(String code, Integer tabModifier)
    {
        add(code, tabModifier, null);
    }

    /**
     * Same as above, but calculates tab level and has no modifier.
     *
     * @param code          The code block to add.
     */
    public void add(String code)
    {
        add(code, 0, null);
    }

    /**
     * Returns our code as a neatly formatted string.
     *
     * @return The code from the code blocks
     */
    public String get()
    {
        // Get a string builder with an estimated size
        // We multiply it to factor in additional new lines.
        StringBuilder sb = new StringBuilder(sizeEstimate*2);

        // Create a string builder just for making tabs
        StringBuilder tabbing = new StringBuilder(32);

        // Keep track of our allocated tab level
        int tabLevel = 0;

        // Match start of line
        Pattern startOfLine = Pattern.compile("^", Pattern.MULTILINE);

        for(CodeBlock block : code) {
            // Create tabbing object if we need to
            if(tabLevel != block.getTabLevel()) {
                tabLevel = block.getTabLevel();
                int tabCount = tabLevel*4;

                // Truncate or grow
                if(tabCount < tabbing.length()) {
                    tabbing.setLength(tabCount);
                } else {
                    while(tabbing.length() < tabCount) {
                        tabbing.append(" ");
                    }
                }
            }

            // Add to string and add tabs.
            sb.append(startOfLine.matcher(block.getCode())
                                 .replaceAll(tabbing.toString())
            ).append("\n");
        }

        return sb.toString();
    }
}
