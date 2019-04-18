/* -------------------------------------------------------------------------- */

package fileshare.ui;

/* -------------------------------------------------------------------------- */

import java.io.*;
import java.util.Scanner;

/**
 * TODO: document
 */
public class Main
{
    /**
     * TODO: document
     *
     * @param args TODO: document
     */
    public static void main(String[] args) throws IOException
    {
        // parse arguments

        final Arguments arguments;

        try
        {
            arguments = Arguments.parse(args);
        }
        catch (RuntimeException e)
        {
            System.err.println("Usage: fileshare <export_dir> [<udp_port>]");
            System.err.println("Error: " + e.getMessage());
            System.exit(2);
        }

        // input loop

        final var printer = new Printer();

        try (final var is = new InputStreamReader(System.in);
             final var br = new BufferedReader(is))
        {
            while (true)
            {
                printer.print("> ");

                final String line = br.readLine();

                if (line == null)
                    break;

                printer.printLinesReplace(
                        Color.YELLOW.apply("[ 42%]") +
                                " Getting FILE from 2 remotes..."
                );

                printer.printLinesReplace(
                        Color.GREEN.apply("[100%]") +
                                " Got FILE from 2 remotes."
                );
            }

            printer.printLines("");
        }
    }

}

/* -------------------------------------------------------------------------- */
