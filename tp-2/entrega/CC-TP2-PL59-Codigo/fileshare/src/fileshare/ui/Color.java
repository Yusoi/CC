/* -------------------------------------------------------------------------- */

package fileshare.ui;

/* -------------------------------------------------------------------------- */

/**
 * Defines colors that may be applied to strings for outputting to a terminal.
 */
public enum Color
{
    /**
     * Green.
     */
    GREEN("\u001b[32m"),

    /**
     * Yellow.
     */
    YELLOW("\u001b[33m"),

    /**
     * Red.
     */
    RED("\u001b[31m");

    private final String code;

    Color(String code)
    {
        this.code = code;
    }

    /**
     * Applies this color the specified string.
     *
     * @param text the string to which this color should be applied
     * @return the same string with this color applied to it
     */
    public String apply(String text)
    {
        return this.code + text + "\u001b[39m";
    }
}

/* -------------------------------------------------------------------------- */
