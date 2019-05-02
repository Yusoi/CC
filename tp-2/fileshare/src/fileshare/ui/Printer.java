/* -------------------------------------------------------------------------- */

package fileshare.ui;

/* -------------------------------------------------------------------------- */

import java.util.Objects;

/**
 * Utility class for line-based output, with support for text coloring and
 * replacement of already printed lines.
 */
public class Printer
{
    private String linePrefix = "";
    private int numPrintedReplaceableLines = 0;

    /**
     * Creates a {@code Printer}.
     */
    public Printer()
    {
    }

    /**
     * Returns the current line prefix.
     *
     * Lines printed with {@link #printLines(String...)} and {@link
     * #printLinesReplace(String...)} are prefixed with this value.
     *
     * @return the current line prefix
     */
    public String getLinePrefix()
    {
        return this.linePrefix;
    }

    /**
     * Sets the current line prefix.
     *
     * Lines printed with {@link #printLines(String...)} and {@link
     * #printLinesReplace(String...)} are prefixed with this value.
     *
     * @param linePrefix the new current line prefix
     */
    public void setLinePrefix(String linePrefix)
    {
        this.linePrefix = Objects.requireNonNull(linePrefix);
    }

    /**
     * Prints and flushes a string without advancing the cursor to a new line.
     *
     * This method does not replace lines previously printed with {@link
     * #printLinesReplace(String...)}.
     *
     * @param text the string to be printed
     */
    public void print(String text)
    {
        // print and flush text

        System.out.print(text);
        System.out.flush();

        // reset number of printed replaceable lines

        this.numPrintedReplaceableLines = 0;
    }

    /**
     * Prints one or more strings, each on a separate line.
     *
     * This method does not replace lines previously printed with {@link
     * #printLinesReplace(String...)}.
     *
     * @param lines the strings to be printed
     */
    public void printLines(String... lines)
    {
        // print lines

        for (String line : lines)
            System.out.println(this.linePrefix + line);

        // reset number of printed replaceable lines

        this.numPrintedReplaceableLines = 0;
    }

    /**
     * Prints one or more strings, each on a separate line, possibly overwriting
     * previously printed lines.
     *
     * If {@code printLinesReplace} was the last method to be invoked on this
     * instance, the lines printed by that invocation are first erased and then
     * overwritten by the specified strings.
     *
     * @param lines the strings to be printed
     */
    public void printLinesReplace(String... lines)
    {
        // clear lines and adjust cursor

        for (int i = 0; i < this.numPrintedReplaceableLines; ++i)
            System.out.print("\u001b[1A\u001b[999D\u001b[2K");

        System.out.flush();

        // print lines

        for (String line : lines)
            System.out.println(this.linePrefix + line);

        // store number of printed replaceable lines

        this.numPrintedReplaceableLines = lines.length;
    }
}

/* -------------------------------------------------------------------------- */
