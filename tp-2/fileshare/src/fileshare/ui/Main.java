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

        final var argParser = ArgumentParsers.newFor("prog").build();

        argParser.description("Runs a FileShare peer.");

        argParser
            .addArgument("export_dir")
            .help("a path to the directory to be exported by the peer");

        argParser
            .addArgument("-p", "--port")
            .type(Integer.class)
            .setDefault(7777)
            .help("the local UDP port to be used by the peer");

        final Path exportedDirPath;
        final int localPort;

        try
        {
            final var argNamespace = argParser.parseArgs(args);

            exportedDirPath = Path.of(argNamespace.getString("export_dir"));
            localPort       = argNamespace.getInt("port");
        }
        catch (ArgumentParserException e)
        {
            try (final var errWriter = new PrintWriter(System.err))
            {
                argParser.printUsage(errWriter);
                errWriter.println("error: " + e.getMessage());
            }

            System.exit(2);
            return;
        }








        // parse arguments

        final Argumentss arguments;

        try
        {
            arguments = Argumentss.parse(args);
        }
        catch (RuntimeException e)
        {
            System.err.println("Usage: fileshare <export_dir> [<udp_port>]");
            System.err.println("Error: " + e.getMessage());
            System.exit(2);
            return; // to avoid errors about arguments not being initialized
        }

        // start peer

        final var peer = new Peer(
            arguments.getUdpPort(),
            arguments.getExportDirPath()
            );

        try (peer)
        {
            peer.start();

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

                    if (!processCommand(peer, printer, line))
                        printer.printLines(Color.RED.apply("Invalid command."));
                }
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
}

/* -------------------------------------------------------------------------- */
