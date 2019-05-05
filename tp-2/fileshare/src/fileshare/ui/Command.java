/* -------------------------------------------------------------------------- */

package fileshare.ui;

import fileshare.core.AddressRange;
import fileshare.core.Job;
import fileshare.core.JobState;
import fileshare.core.JobType;
import fileshare.core.Peer;
import fileshare.transport.Endpoint;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/* -------------------------------------------------------------------------- */

/**
 * Implements the commands supported by the application's interpreter.
 */
public abstract class Command
{
    private final boolean isAllowedInNonConcurrentMode;
    private final boolean isAllowedInConcurrentMode;
    private final Pattern pattern;

    private Command(
        boolean isAllowedInNonConcurrentMode,
        boolean isAllowedInConcurrentMode,
        String patternRegex
    )
    {
        this.isAllowedInNonConcurrentMode = isAllowedInNonConcurrentMode;
        this.isAllowedInConcurrentMode = isAllowedInConcurrentMode;

        this.pattern = Pattern.compile(patternRegex);
    }

    /**
     * Checks whether the command may be used while *not* in "concurrent" mode.
     *
     * @return whether the command may be used while *not* in "concurrent" mode
     */
    public boolean isAllowedInNonConcurrentMode()
    {
        return isAllowedInNonConcurrentMode;
    }

    /**
     * Checks whether the command may be used while in "concurrent" mode.
     *
     * @return whether the command may be used while in "concurrent" mode
     */
    public boolean isAllowedInConcurrentMode()
    {
        return isAllowedInConcurrentMode;
    }

    /**
     * Returns a {@link Pattern} defining the command's syntax.
     *
     * @return a {@link Pattern} defining the command's syntax
     */
    public Pattern getPattern()
    {
        return pattern;
    }

    /**
     * Runs the command.
     *
     * @param interpreter the interpreter whose state the command should use
     * @param matcher the {@link Matcher} object from which the command
     *                arguments should be obtained
     */
    public abstract void run(Interpreter interpreter, Matcher matcher)
        throws Exception;

    /**
     * An array of all commands supported by the application's interpreter.
     */
    public static final Command[] ALL_COMMANDS = new Command[] {
        new CommandExit(),
        new CommandWhitelistAll(),
        new CommandWhitelistAdd(),
        new CommandWhitelistRemove(),
        new CommandWhitelistClear(),
        new CommandGet(),
        new CommandPut(),
        new CommandConcurrent(),
        new CommandRun(),
        new CommandCancel(),
    };

    private static class CommandExit extends Command
    {
        public CommandExit()
        {
            super(true, true, "\\s*exit\\s*");
        }

        @Override
        public void run(Interpreter interpreter, Matcher matcher)
            throws Exception
        {
            interpreter.setShouldExit(true);
        }
    }

    private static class CommandWhitelistAll extends Command
    {
        public CommandWhitelistAll()
        {
            super(true, true, "\\s*whitelist-all\\s*");
        }

        @Override
        public void run(Interpreter interpreter, Matcher matcher)
            throws Exception
        {
            interpreter.getPeer().getPeerWhitelist().add(
                AddressRange.parseCidrNotation("0.0.0.0/0")
            );

            interpreter.getPeer().getPeerWhitelist().add(
                AddressRange.parseCidrNotation("::/0")
            );
        }
    }

    private static class CommandWhitelistAdd extends Command
    {
        public CommandWhitelistAdd()
        {
            super(
                true,
                true,
                "\\s*whitelist-add\\s+(?<cidrs>\\S+(?:\\s+\\S+)*)\\s*"
            );
        }

        @Override
        public void run(Interpreter interpreter, Matcher matcher)
            throws Exception
        {
            final var ranges = new ArrayList< AddressRange >();

            for (final var cidr : matcher.group("cidrs").split("\\s+"))
                ranges.add(AddressRange.parseCidrNotation(cidr));

            ranges.forEach(interpreter.getPeer().getPeerWhitelist()::add);
        }
    }

    private static class CommandWhitelistRemove extends Command
    {
        public CommandWhitelistRemove()
        {
            super(
                true,
                true,
                "\\s*whitelist-remove\\s+(?<cidrs>\\S+(?:\\s+\\S+)*)\\s*"
            );
        }

        @Override
        public void run(Interpreter interpreter, Matcher matcher)
            throws Exception
        {
            final var ranges = new ArrayList< AddressRange >();

            for (final var cidr : matcher.group("cidrs").split("\\s+"))
                ranges.add(AddressRange.parseCidrNotation(cidr));

            ranges.forEach(interpreter.getPeer().getPeerWhitelist()::remove);
        }
    }

    private static class CommandWhitelistClear extends Command
    {
        public CommandWhitelistClear()
        {
            super(true, true, "\\s*whitelist-clear\\s*");
        }

        @Override
        public void run(Interpreter interpreter, Matcher matcher)
            throws Exception
        {
            interpreter.getPeer().getPeerWhitelist().clear();
        }
    }

    private static class CommandGet extends Command
    {
        public CommandGet()
        {
            super(
                true,
                true,
                "\\s*get\\s+(?<get>\\S+)" +
                    "(?:\\s+as\\s+(?<as>\\S+))?" +
                    "\\s+from\\s+(?<from>\\S+(?:\\s+\\S+)*)\\s*"
                );
        }

        @Override
        public void run(Interpreter interpreter, Matcher matcher)
            throws Exception
        {
            final var remoteEndpoints = new ArrayList< Endpoint >();

            for (final var endpoint : matcher.group("from").split("\\s+"))
                remoteEndpoints.add(Endpoint.parse(endpoint, Peer.DEFAULT_PORT));

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

            if (!interpreter.isInConcurrentMode())
                runJobs(interpreter, List.of(job)); // not in concurrent
            else
                interpreter.getConcurrentJobs().add(job); // in concurrent
        }
    }

    private static class CommandPut extends Command
    {
        public CommandPut()
        {
            super(
                true,
                true,
                "\\s*put\\s+(?<put>\\S+)" +
                    "(?:\\s+as\\s+(?<as>\\S+))?" +
                    "\\s+to\\s+(?<to>\\S+(?:\\s+\\S+)*)\\s*"
            );
        }

        @Override
        public void run(Interpreter interpreter, Matcher matcher)
            throws Exception
        {
            final var remoteEndpoints = new ArrayList< Endpoint >();

            for (final var endpoint : matcher.group("to").split("\\s+"))
                remoteEndpoints.add(Endpoint.parse(endpoint, Peer.DEFAULT_PORT));

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

            if (!interpreter.isInConcurrentMode())
                runJobs(interpreter, List.of(job)); // not in concurrent
            else
                interpreter.getConcurrentJobs().add(job); // in concurrent
        }
    }

    private static class CommandConcurrent extends Command
    {
        public CommandConcurrent()
        {
            super(true, false, "\\s*concurrent\\s*");
        }

        @Override
        public void run(Interpreter interpreter, Matcher matcher)
            throws Exception
        {
            interpreter.enterConcurrentMode();
        }
    }

    private static class CommandRun extends Command
    {
        public CommandRun()
        {
            super(false, true, "\\s*run\\s*");
        }

        @Override
        public void run(Interpreter interpreter, Matcher matcher)
            throws Exception
        {
            if (!interpreter.getConcurrentJobs().isEmpty())
                runJobs(interpreter, interpreter.getConcurrentJobs());

            interpreter.leaveConcurrentMode();
        }
    }

    private static class CommandCancel extends Command
    {
        public CommandCancel()
        {
            super(false, true, "\\s*cancel\\s*");
        }

        @Override
        public void run(Interpreter interpreter, Matcher matcher)
            throws Exception
        {
            interpreter.leaveConcurrentMode();
        }
    }

    private static void runJobs(Interpreter interpreter, List< Job > jobs)
    {
        interpreter.getPeer().runJobs(
            jobs,
            jobStates ->
            {
                final var lines =
                    jobStates
                        .stream()
                        .map(Command::jobStateToString)
                        .toArray(String[]::new);

                interpreter.getPrinter().printLinesReplace(lines);
            }
        );
    }

    private static String jobStateToString(JobState state)
    {
        final var numPeers = state.getJob().getPeerEndpoints().size();
        final var peerPlural = (numPeers == 1) ? "" : "s";

        switch (state.getState())
        {
            case STARTING:
            case RUNNING:

                final var progress = Color.YELLOW.apply(
                    String.format("[%3d%%]", state.getProgressPercentage())
                );

                switch (state.getJob().getType())
                {
                    case GET:
                        return String.format(
                            "%s Getting %s as %s from %d peer%s... (%s)",
                            progress,
                            state.getJob().getRemoteFilePath(),
                            state.getJob().getLocalFilePath(),
                            numPeers,
                            peerPlural,
                            throughputToString(state.getImmediateThroughput())
                        );

                    case PUT:
                        return String.format(
                            "%s Putting %s as %s to %d peer%s... (%s)",
                            progress,
                            state.getJob().getLocalFilePath(),
                            state.getJob().getRemoteFilePath(),
                            numPeers,
                            peerPlural,
                            throughputToString(state.getImmediateThroughput())
                        );
                }

            case SUCCEEDED:

                switch (state.getJob().getType())
                {
                    case GET:
                        return String.format(
                            "%s Got %s as %s from %d peer%s. (%s)",
                            Color.GREEN.apply("[100%]"),
                            state.getJob().getRemoteFilePath(),
                            state.getJob().getLocalFilePath(),
                            numPeers,
                            peerPlural,
                            throughputToString(state.getOverallThroughput())
                        );

                    case PUT:
                        return String.format(
                            "%s Put %s as %s to %d peer%s. (%s)",
                            Color.GREEN.apply("[100%]"),
                            state.getJob().getLocalFilePath(),
                            state.getJob().getRemoteFilePath(),
                            numPeers,
                            peerPlural,
                            throughputToString(state.getOverallThroughput())
                        );
                }

            case FAILED:

                return String.format(
                    "%s %s",
                    Color.RED.apply("ERROR!"),
                    state.getErrorMessage()
                );
        }

        throw new RuntimeException();
    }

    private static String throughputToString(long bytesPerSecond)
    {
        if (bytesPerSecond < 10 * (1 << 10))
            return String.format(" %d B/s", bytesPerSecond);
        else if (bytesPerSecond < 10 * (1 << 20))
            return String.format(" %.2f KiB/s", bytesPerSecond / 1024d);
        else
            return String.format(" %.2f MiB/s", bytesPerSecond / 1024d / 1024d);
    }
}

/* -------------------------------------------------------------------------- */
