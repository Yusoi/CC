/* -------------------------------------------------------------------------- */

package fileshare.ui;

import fileshare.core.AddressRange;
import fileshare.core.Job;
import fileshare.core.JobState;
import fileshare.core.JobType;
import fileshare.core.Peer;
import fileshare.transport.Endpoint;
import net.sourceforge.argparse4j.inf.ArgumentParserException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/* -------------------------------------------------------------------------- */

/**
 * TODO: document
 */
public final class Main
{
    /**
     * TODO: document
     *
     * @param args TODO: document
     */
    public static void main(String[] args) throws IOException
    {
        // parse arguments

        final Args arguments;

        try
        {
            arguments = Args.parse(args);
        }
        catch (ArgumentParserException e)
        {
            try (final var errWriter = new PrintWriter(System.err))
            {
                e.getParser().printUsage(errWriter);
                errWriter.println("error: " + e.getMessage());
            }

            System.exit(2);
            return;
        }

        // start peer

        final var peer = new Peer(
            arguments.getLocalPort(),
            arguments.getExportedDirectoryPath()
            );

        try (peer)
        {
            // whitelist all peers if requested

            if (arguments.getAllowAllPeers())
            {
                peer.getPeerWhitelist().add(
                    AddressRange.fromCidrNotation("0.0.0.0/0")
                    );

                peer.getPeerWhitelist().add(
                    AddressRange.fromCidrNotation("::/0")
                    );
            }

            // start peer and run input loop

            peer.start();
            runInputLoop(peer);
        }
    }

    private static void runInputLoop(Peer peer) throws IOException
    {
        final var stdin = new BufferedReader(new InputStreamReader(System.in));
        final var printer = new Printer();

        final List< Job > concurrentJobs = null;

        while (true)
        {
            // print prompt

            if (concurrentJobs != null)
                printer.print("  ");

            printer.print("> ");

            // read command

            final String line = stdin.readLine();

            if (line == null)
                break; // stdin eof, exit

            // process command

            try
            {
                if (!processCommand(peer, printer, concurrentJobs, line))
                    break; // command requested exit
            }
            catch (Exception e)
            {
                printer.printLines(Color.RED.apply(e.getMessage()));
            }
        }
    }

    private static boolean processCommand(
        Peer peer,
        Printer printer,
        List< Job > concurrentJobs,
        String command
        ) throws Exception
    {
        for (final var cmd : commands)
        {
            final var matcher = cmd.getPattern().matcher(command);

            if (matcher.matches())
                return cmd.process(peer, printer, concurrentJobs, matcher);
        }

        throw new RuntimeException("Invalid command.");
    }

    private static abstract class Command
    {
        private final Pattern pattern;

        public Command(String regex)
        {
            this.pattern = Pattern.compile(regex);
        }

        public Pattern getPattern()
        {
            return pattern;
        }

        public abstract boolean process(
            Peer peer,
            Printer printer,
            List< Job > concurrentJobs,
            Matcher matcher
            ) throws Exception;
    }

    private static final Command[] commands = new Command[] {

        new Command(
            "\\s*get\\s+(?<get>\\S+)" +
            "(?:\\s+as\\s+(?<as>\\S+))?" +
            "\\s+from\\s+(?<from>\\S+(?:\\s+\\S+)*)\\s*"
            ) {
            @Override
            public boolean process(
                Peer peer,
                Printer printer,
                List< Job > concurrentJobs,
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

                if (concurrentJobs == null)
                    runJobs(peer, printer, List.of(job)); // not in concurrent
                else
                    concurrentJobs.add(job); // in concurrent

                return false;
            }
        }

    };

    private static void runJobs(
        Peer peer,
        Printer printer,
        List< Job > jobs
        )
    {
        peer.runJobs(
            jobs,
            jobStates -> printer.printLinesReplace(
                jobStates
                    .stream()
                    .map(Main::jobStateToString)
                    .toArray(String[]::new)
                )
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

    // No point in ever instantiating this class.
    private Main()
    {
    }
}

/* -------------------------------------------------------------------------- */
