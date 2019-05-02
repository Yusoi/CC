/* -------------------------------------------------------------------------- */

package fileshare.ui;

/* -------------------------------------------------------------------------- */

/**
 * TODO: document
 */
public class Printer
{
    private int numPrintedReplaceableLines = 0;

    /**
     * TODO: document
     */
    public Printer()
    {
    }

    /**
     * TODO: document
     *
     * @param text TODO: document
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
     * TODO: document
     *
     * @param lines TODO: document
     */
    public void printLines(String... lines)
    {
        // print lines

        for (String line : lines)
            System.out.println(line);

        // reset number of printed replaceable lines

        this.numPrintedReplaceableLines = 0;
    }

    /**
     * TODO: document
     *
     * Print but clear and overwrite previous lines that were printed using this
     * method.
     *
     * @param lines TODO: document
     */
    public void printLinesReplace(String... lines)
    {
        // clear lines and adjust cursor

        for (int i = 0; i < this.numPrintedReplaceableLines; ++i)
            System.out.print("\u001b[1A\u001b[999D\u001b[2K");

        System.out.flush();

        // print lines

        for (String line : lines)
            System.out.println(line);

        // store number of printed replaceable lines

        this.numPrintedReplaceableLines = lines.length;
    }
}

/* -------------------------------------------------------------------------- */
