/* -------------------------------------------------------------------------- */

package fileshare.ui;

import fileshare.core.Job;
import fileshare.core.Peer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/* -------------------------------------------------------------------------- */

/**
 * Implements the application's interactive prompt.
 */
public class Interpreter
{
    private final Peer peer;
    private final Printer printer;

    private List< Job > concurrentJobs;
    private boolean shouldExit;

    /**
     * Creates a {@code Interpreter}.
     *
     * @param peer the peer on which commands should be performed
     */
    public Interpreter(Peer peer)
    {
        this.peer = peer;
        this.printer = new Printer();

        this.concurrentJobs = null;
        this.shouldExit = false;
    }

    /**
     *
     * @return
     */
    public Peer getPeer()
    {
        return this.peer;
    }

    /**
     *
     * @return
     */
    public Printer getPrinter()
    {
        return this.printer;
    }

    /**
     *
     * @return
     */
    public boolean isInConcurrentMode()
    {
        return this.concurrentJobs != null;
    }

    /**
     *
     */
    public void enterConcurrentMode()
    {
        this.concurrentJobs = new ArrayList<>();
    }

    /**
     *
     */
    public void leaveConcurrentMode()
    {
        this.concurrentJobs = null;
    }

    /**
     *
     * @return
     */
    public List< Job > getConcurrentJobs()
    {
        return this.concurrentJobs;
    }

    /**
     *
     * @return
     */
    public boolean shouldExit()
    {
        return this.shouldExit;
    }

    /**
     *
     * @param shouldExit
     */
    public void setShouldExit(boolean shouldExit)
    {
        this.shouldExit = shouldExit;
    }

    /**
     * Runs the input loop, thus capturing and processing user-given commands.
     *
     * @throws IOException if an error occurs reading from or writing to the
     *         standard streams
     */
    public void runInputLoop() throws IOException
    {
        final var input = new BufferedReader(new InputStreamReader(System.in));

        while (true)
        {
            // print prompt

            if (this.isInConcurrentMode())
            {
                this.printer.print("  ");
                this.printer.setLinePrefix("  ");
            }
            else
            {
                this.printer.setLinePrefix("");
            }

            this.printer.print("> ");

            // read command

            final String line = input.readLine();

            if (line == null)
            {
                // end-of-file on stdin, exit input loop
                printer.printLines("");
                break;
            }

            // process line

            try
            {
                this.processLine(line);
            }
            catch (Exception e)
            {
                printer.printLines(Color.RED.apply(e.getMessage()));
            }

            // check if exit was requested

            if (this.shouldExit())
            {
                // command requested exit
                break;
            }
        }

        // reset state

        this.leaveConcurrentMode();
        this.setShouldExit(false);
    }

    private void processLine(String line) throws Exception
    {
        // check if line is empty

        if (line.isBlank())
            return;

        // find command that matches

        for (final var command : Command.ALL_COMMANDS)
        {
            final var matcher = command.getPattern().matcher(line);

            if (matcher.matches())
            {
                if (!this.isInConcurrentMode() &&
                        !command.isAllowedInNonConcurrentMode())
                    throw new RuntimeException("Command not allowed here.");

                if (this.isInConcurrentMode() &&
                        !command.isAllowedInConcurrentMode())
                    throw new RuntimeException("Command not allowed here.");

                command.run(this, matcher);

                return;
            }
        }

        // invalid command

        throw new RuntimeException("Invalid command.");
    }
}

/* -------------------------------------------------------------------------- */
