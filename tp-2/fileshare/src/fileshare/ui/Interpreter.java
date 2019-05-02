/* -------------------------------------------------------------------------- */

package fileshare.ui;

import fileshare.core.AddressRange;
import fileshare.core.Job;
import fileshare.core.JobState;
import fileshare.core.JobType;
import fileshare.core.Peer;
import fileshare.transport.Endpoint;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    public void enableConcurrentMode()
    {
        this.concurrentJobs = new ArrayList<>();
    }

    /**
     *
     */
    public void disableConcurrentMode()
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

            if (this.concurrentJobs != null)
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

            // process command

            try
            {
                this.processCommand(line);

            }
            catch (Exception e)
            {
                printer.printLines(Color.RED.apply(e.getMessage()));
            }

            // check if command requested to exit

            if (this.shouldExit())
            {
                // command requested exit
                break;
            }
        }

        // reset state

        this.disableConcurrentMode();
        this.setShouldExit(false);
    }

    private boolean processCommand(String command) throws Exception
    {
        if (command.isBlank())
            return true;

        for (final var cmd : COMMANDS)
        {
            final var matcher = cmd.getPattern().matcher(command);

            if (matcher.matches())
            {
                if (this.concurrentJobs == null &&
                        !cmd.isAllowedOutsideConcurrent())
                    throw new RuntimeException("Command not allowed here.");

                if (this.concurrentJobs != null &&
                        !cmd.isAllowedInsideConcurrent())
                    throw new RuntimeException("Command not allowed here.");

                return cmd.process(this, matcher);
            }
        }

        throw new RuntimeException("Invalid command.");
    }
}

/* -------------------------------------------------------------------------- */
