/* -------------------------------------------------------------------------- */

package fileshare.ui;

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
public class Prompt
{
    private final Peer peer;

    private final BufferedReader input;
    private final Printer printer;

    private List< Job > concurrentJobs;

    /**
     * Creates a {@code Prompt}.
     *
     * @param peer the peer on which commands should be performed
     */
    public Prompt(Peer peer)
    {
        this.peer = peer;

        this.input   = new BufferedReader(new InputStreamReader(System.in));
        this.printer = new Printer();

        this.concurrentJobs = null;
    }

    /**
     * Runs the input loop, thus capturing and processing user-given commands.
     *
     * @throws IOException if an error occurs reading from or writing to the
     *         standard streams
     */
    public void runInputLoop() throws IOException
    {
        while (true)
        {
            // print prompt

            if (this.concurrentJobs != null)
                this.printer.print("  ");

            this.printer.print("> ");

            // read command

            final String line = this.input.readLine();

            if (line == null)
            {
                // end-of-file on stdin, exit input loop
                printer.printLines("");
                break;
            }

            // process command

            try
            {
                if (!this.processCommand(line))
                {
                    // command requested exit
                    this.concurrentJobs = null;
                    break;
                }
            }
            catch (Exception e)
            {
                printer.printLines(Color.RED.apply(e.getMessage()));
            }
        }
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

    private void runJobs(List< Job > jobs)
    {
        this.peer.runJobs(
            jobs,
            jobStates ->
            {
                final var lines =
                    jobStates
                    .stream()
                    .map(Prompt::jobStateToString)
                    .toArray(String[]::new);

                this.printer.printLinesReplace(lines);
            }
        );
    }

    private static String jobStateToString(JobState state)
    {
        final var numRemotes   = state.getJob().getRemoteEndpoints().size();
        final var remotePlural = (numRemotes == 1) ? "" : "s";

        if (!state.hasFinished())
        {
            final var progress = Color.GREEN.apply("[100%]");

            switch (state.getJob().getType())
            {
                case GET:
                    return String.format(
                        "%s Got %s as %s from %d peer%s.",
                        progress,
                        state.getJob().getRemoteFilePath(),
                        state.getJob().getLocalFilePath(),
                        numRemotes,
                        remotePlural
                    );

                case PUT:
                    return String.format(
                        "%s Put %s as %s to %d peer%s.",
                        progress,
                        state.getJob().getRemoteFilePath(),
                        state.getJob().getLocalFilePath(),
                        numRemotes,
                        remotePlural
                    );

                default:
                    throw new IllegalArgumentException();
            }
        }
        else if (state.getErrorMessage().isEmpty())
        {
            final var progress = Color.GREEN.apply(
                String.format("[%3d%%]", state.getTransferredPercentage())
            );

            switch (state.getJob().getType())
            {
                case GET:
                    return String.format(
                        "%s Getting %s as %s from %d peer%s...",
                        progress,
                        state.getJob().getRemoteFilePath(),
                        state.getJob().getLocalFilePath(),
                        numRemotes,
                        remotePlural
                    );

                case PUT:
                    return String.format(
                        "%s Putting %s as %s to %d peer%s...",
                        progress,
                        state.getJob().getRemoteFilePath(),
                        state.getJob().getLocalFilePath(),
                        numRemotes,
                        remotePlural
                    );

                default:
                    throw new IllegalArgumentException();
            }
        }
        else
        {
            return String.format(
                "%s %s",
                Color.RED.apply("ERROR!"),
                state.getErrorMessage().get()
            );
        }
    }

    private static abstract class Command
    {
        private final boolean allowedOutsideConcurrent;
        private final boolean allowedInsideConcurrent;

        private final Pattern pattern;

        public Command(
            boolean allowedOutsideConcurrent,
            boolean allowedInsideConcurrent,
            String regex
        )
        {
            this.allowedOutsideConcurrent = allowedOutsideConcurrent;
            this.allowedInsideConcurrent  = allowedInsideConcurrent;

            this.pattern = Pattern.compile(regex);
        }

        public boolean isAllowedOutsideConcurrent()
        {
            return allowedOutsideConcurrent;
        }

        public boolean isAllowedInsideConcurrent()
        {
            return allowedInsideConcurrent;
        }

        public Pattern getPattern()
        {
            return pattern;
        }

        public abstract boolean process(
            Prompt prompt,
            Matcher matcher
        ) throws Exception;
    }

    private static final Command[] COMMANDS = new Command[] {

        new Command(true, true, "\\s*exit\\s*") {
            @Override
            public boolean process(
                Prompt prompt,
                Matcher matcher
                ) throws Exception
            {
                return false;
            }
        },

        new Command(
            true,
            true,
            "\\s*get\\s+(?<get>\\S+)" +
            "(?:\\s+as\\s+(?<as>\\S+))?" +
            "\\s+from\\s+(?<from>\\S+(?:\\s+\\S+)*)\\s*"
        ) {
            @Override
            public boolean process(
                Prompt prompt,
                Matcher matcher
                ) throws Exception
            {
                final var remoteEndpoints = new ArrayList< Endpoint >();

                for (final var endpoint : matcher.group("from").split("\\s+"))
                    remoteEndpoints.add(Endpoint.parse(endpoint, 7777));

                final var remoteFilePath = Path.of(matcher.group("get"));

                final var localFilePath =
                    (matcher.group("as") != null) ?
                        Path.of(matcher.group("as")) :
                        remoteFilePath;

                final var job = new Job(
                    JobType.GET,
                    remoteEndpoints,
                    localFilePath,
                    remoteFilePath
                );

                if (prompt.concurrentJobs == null)
                    prompt.runJobs(List.of(job)); // not in concurrent
                else
                    prompt.concurrentJobs.add(job); // in concurrent

                return true;
            }
        },

        new Command(
            true,
            true,
            "\\s*put\\s+(?<put>\\S+)" +
            "(?:\\s+as\\s+(?<as>\\S+))?" +
            "\\s+to\\s+(?<to>\\S+(?:\\s+\\S+)*)\\s*"
        ) {
            @Override
            public boolean process(
                Prompt prompt,
                Matcher matcher
                ) throws Exception
            {
                final var remoteEndpoints = new ArrayList< Endpoint >();

                for (final var endpoint : matcher.group("to").split("\\s+"))
                    remoteEndpoints.add(Endpoint.parse(endpoint, 7777));

                final var localFilePath = Path.of(matcher.group("put"));

                final var remoteFilePath =
                    (matcher.group("as") != null) ?
                        Path.of(matcher.group("as")) :
                        localFilePath;

                final var job = new Job(
                    JobType.PUT,
                    remoteEndpoints,
                    localFilePath,
                    remoteFilePath
                );

                if (prompt.concurrentJobs == null)
                    prompt.runJobs(List.of(job)); // not in concurrent
                else
                    prompt.concurrentJobs.add(job); // in concurrent

                return true;
            }
        },

    };
}

/* -------------------------------------------------------------------------- */
