/* -------------------------------------------------------------------------- */

package fileshare.ui;

/* -------------------------------------------------------------------------- */

/**
 * TODO: document
 */
public enum Color
{
    /**
     * TODO: document
     */
    DEFAULT("\u001b[39m"),

    /**
     * TODO: document
     */
    GREEN("\u001b[32m"),

    /**
     * TODO: document
     */
    YELLOW("\u001b[33m"),

    /**
     * TODO: document
     */
    RED("\u001b[31m");

    private final String code;

    Color(String code)
    {
        this.code = code;
    }

    /**
     * TODO: document
     */
    public String apply(String text)
    {
        if (this != DEFAULT)
            text = this.code + text + DEFAULT.code;

        return text;
    }
}

/* -------------------------------------------------------------------------- */
