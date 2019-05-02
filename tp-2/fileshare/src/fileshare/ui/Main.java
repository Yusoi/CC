/* -------------------------------------------------------------------------- */

package fileshare.ui;

import fileshare.core.Peer;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParserException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.regex.Pattern;

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
            arguments = new Args(args);
        }
        catch (ArgumentParserException e)
        {
            System.exit(2);
            return;
        }

        // start peer

        final var peer = new Peer(
            arguments.localPort,
            arguments.exportedDirectoryPath
            );

        try (peer)
        {
            peer.start();

            // run input loop

            runInputLoop(peer);
        }
    }

    private static class Args
    {
        public final int localPort;
        public final Path exportedDirectoryPath;

        public Args(String[] args) throws ArgumentParserException
        {
            // create parser

            final var argParser = ArgumentParsers.newFor("prog").build();

            argParser.description("Runs a FileShare peer.");

            argParser
                .addArgument("-p", "--port")
                .type(Integer.class)
                .setDefault(7777)
                .help("the local UDP port to be used by the peer");

            argParser
                .addArgument("export_dir")
                .help("a path to the directory to be exported by the peer");

            // parse arguments

            try
            {
                final var argNamespace = argParser.parseArgs(args);

                this.localPort = argNamespace.getInt("port");

                if (this.localPort < 1 || this.localPort > 65535)
                {
                    throw new IllegalArgumentException(
                        "port must be between 1 and 65535, inclusive"
                    );
                }

                this.exportedDirectoryPath = Path.of(
                    argNamespace.getString("export_dir")
                    );
            }
            catch (ArgumentParserException e)
            {
                try (final var errWriter = new PrintWriter(System.err))
                {
                    argParser.printUsage(errWriter);
                    errWriter.println("error: " + e.getMessage());
                }

                throw e;
            }
        }
    }

    private static void runInputLoop(Peer peer) throws IOException
    {
        final var stdin = new BufferedReader(new InputStreamReader(System.in));

        final var printer = new Printer();

        boolean insideConcurrent = false;

        try (final var is = new InputStreamReader(System.in);
            final var br = new BufferedReader(is))
        {
            while (true)
            {
                // print prompt

                if (insideConcurrent)
                    printer.print("  ");

                printer.print("> ");

                // read command

                final String line = stdin.readLine();

                if (line == null)
                    break; // stdin eof, exit

                // process command

                if (!processCommand(peer, printer, line))
                    printer.printLines(Color.RED.apply("Invalid command."));
            }
        }
    }

    private static boolean processCommand(
        Peer peer,
        Printer printer,
        String command
        )
    {
        if (command.matches("\\s*"))
            return true;

        final var pattern = Pattern.compile(
            "\\s*get\\s+(?<get>\\S+)" +
            "(?:\\s+as\\s+(?<as>\\S+))" +
            "\\s+from\\s+(?<from>\\S+)\\s*"
            );

        final var matcher = pattern.matcher(command);

        if (!matcher.matches())
            return false;

        printer.printLines(
            matcher.group("get"),
            matcher.group("as"),
            matcher.group("from")
            );

        return true;
    }

    // No point in ever instantiating this class.
    private Main()
    {
    }
}

/* -------------------------------------------------------------------------- */
