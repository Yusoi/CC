/* -------------------------------------------------------------------------- */

package fileshare.ui;

import fileshare.core.Job;
import fileshare.core.Peer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/* -------------------------------------------------------------------------- */

/**
 * Implements the application's command interpreter.
 */
public class Interpreter
{
    private final Peer peer;
    private final Printer printer;

    private List< Job > concurrentJobs;
    private boolean shouldExit;

    /**
     * Creates an {@code Interpreter}.
     *
     * @param peer the peer on which commands should be performed
     */
    public Interpreter(Peer peer)
    {
        this.peer = Objects.requireNonNull(peer);
        this.printer = new Printer();

        this.concurrentJobs = null;
        this.shouldExit = false;
    }

    /**
     * Returns the peer on which commands are performed.
     *
     * @return the peer on which commands are performed
     */
    public Peer getPeer()
    {
        return this.peer;
    }

    /**
     * Returns the printer used by the interpreter.
     *
     * @return the printer used by the interpreter
     */
    public Printer getPrinter()
    {
        return this.printer;
    }

    /**
     * Returns whether the interpreter is in "concurrent" mode, i.e., in the
     * context of the "concurrent" command.
     *
     * @return whether the interpreter is in "concurrent" mode
     */
    public boolean isInConcurrentMode()
    {
        return this.concurrentJobs != null;
    }

    /**
     * Enables "concurrent" mode for the interpreter.
     */
    public void enterConcurrentMode()
    {
        this.concurrentJobs = new ArrayList<>();
    }

    /**
     * Disables "concurrent" mode for the interpreter.
     */
    public void leaveConcurrentMode()
    {
        this.concurrentJobs = null;
    }

    /**
     * Returns a modifiable list of jobs specified in the context of a
     * "concurrent" command.
     *
     * Returns {@code null} if the interpreter is not in "concurrent" mode.
     *
     * @return a modifiable list of jobs specified in the context of a
     *         "concurrent" command, or {@code null} if the interpreter is not
     *         in "concurrent" mode
     */
    public List< Job > getConcurrentJobs()
    {
        return this.concurrentJobs;
    }

    /**
     * Checks whether a command instructed the interpreter to exit.
     *
     * @return whether a command instructed the interpreter to exit
     */
    public boolean shouldExit()
    {
        return this.shouldExit;
    }

    /**
     * Sets whether the interpreter should exit when possible.
     *
     * @param shouldExit whether the interpreter should exit
     */
    public void setShouldExit(boolean shouldExit)
    {
        this.shouldExit = shouldExit;
    }

    /**
     * Runs the input loop, capturing and running user-given commands.
     *
     * @throws IOException if an error occurs while reading from or writing to
     *         the standard streams
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
