package net.epicforce.jenkinsci.plugins.ahp2jenkins.code;

/**
 * A code block is a chunk of code that's all at the same tab
 * level.
 *
 * A code block may impact the next block's tab level.  That's
 * called a modifier, and it's usually going to be '0', '1', or
 * '-1'.
 *
 * We use this to keep track of the tab level, and also to provide
 * certain exceptions (like the ability to not auto-tab a block)
 *
 * @author sconley (sconley@epicforce.net)
 */
public class CodeBlock
{
    /*****************************************************************
     * PROPERTIES
     ****************************************************************/

    // This code block's tabbing level.  0 means no tabs added,
    // 1 is 1 tab, etc.  Child blocks will usually be their
    // parent's block +1, but the level can be overrided as
    // needed.
    private int tabLevel = 0;

    // This block's tab modifier
    private int tabModifier = 0;

    // This block's contents.  
    private String code;

    /*****************************************************************
     * CONSTRUCTORS
     ****************************************************************/

    /**
     * Creates a code block with the parameters provided.
     *
     * @param code    The source code for this block.
     * @param tabLevel The tab level
     * @param modifier The tab modifier for this block (if any)
     */
    public CodeBlock(String code, int tabLevel, int modifier)
    {
        this.code = code;
        this.tabLevel = tabLevel;
        this.tabModifier = modifier;
    }

    /*****************************************************************
     * ACCESSORS
     ****************************************************************/

    /**
     * @return tabModifier
     */
    public int getTabModifier()
    {
        return tabModifier;
    }

    /**
     * @return code
     */
    public String getCode()
    {
        return code;
    }

    /**
     * @return tabLevel
     */
    public int getTabLevel()
    {
        return tabLevel;
    }

    /**
     * @param val The value to set
     */
    public void setTabModifier(int val)
    {
        tabModifier = val;
    }

    /**
     * @param val The value to set
     */
    public void setCode(String val)
    {
        code = val;
    }

    /**
     * @param val The value to set
     */
    public void setTabLevel(int val)
    {
        tabLevel = val;
    }
}
