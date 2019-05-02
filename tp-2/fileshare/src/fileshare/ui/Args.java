/* -------------------------------------------------------------------------- */

package fileshare.ui;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParserException;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Objects;

/* -------------------------------------------------------------------------- */

/**
 * Parses and holds the application arguments.
 */
public class Args
{
    /**
     * Parses the application arguments.
     *
     * @param args the string arguments given to the application
     * @return an instance of {@link Args} holding the parsed arguments
     *
     * @throws ArgumentParserException if the arguments to be parsed are invalid
     */
    public static Args parse(String[] args) throws ArgumentParserException
    {
        Objects.requireNonNull(args);

        // create parser

        final var argParser = ArgumentParsers.newFor("fileshare").build();

        argParser.description("Runs a FileShare peer.");

        argParser
            .addArgument("-a", "--allow-all")
            .action(Arguments.storeTrue())
            .help("accept connections from any peer");

        argParser
            .addArgument("-p", "--port")
            .type(Integer.class)
            .setDefault(7777)
            .help("the local UDP port to be used by the peer (default: 7777)");

        argParser
            .addArgument("export_dir")
            .help("a path to the directory to be exported by the peer");

        // parse arguments

        try
        {
            final var argNamespace = argParser.parseArgs(args);

            // -a, --allow-all

            final var allowAllPeers = argNamespace.getBoolean("allow_all");

            // -p, --port

            final var localPort = argNamespace.getInt("port");

            if (localPort < 1 || localPort > 65535)
            {
                throw new RuntimeException(
                    String.format("invalid port %s", localPort)
                    );
            }

            // export_dir

            final Path exportedDirectoryPath;

            try
            {
                exportedDirectoryPath = Path.of(
                    argNamespace.getString("export_dir")
                    );
            }
            catch (InvalidPathException e)
            {
                throw new RuntimeException(
                    String.format("invalid path %s", e.getInput())
                    );
            }

            if (!Files.exists(exportedDirectoryPath))
            {
                throw new RuntimeException(
                    String.format(
                        "%s does not exist",
                        exportedDirectoryPath
                    )
                );
            }

            if (!Files.isDirectory(exportedDirectoryPath))
            {
                throw new RuntimeException(
                    String.format(
                        "%s is not a directory",
                        exportedDirectoryPath
                    )
                );
            }

            // return args

            return new Args(allowAllPeers, localPort, exportedDirectoryPath);
        }
        catch (Exception e)
        {
            throw new ArgumentParserException(e.getMessage(), argParser);
        }
    }

    private final boolean allowAllPeers;
    private final int localPort;
    private final Path exportedDirectoryPath;

    private Args(
        boolean allowAllPeers,
        int localPort,
        Path exportedDirectoryPath
        )
    {
        this.allowAllPeers         = allowAllPeers;
        this.localPort             = localPort;
        this.exportedDirectoryPath = exportedDirectoryPath;
    }

    /**
     * Returns whether any peer should be allowed to connect to the local peer.
     *
     * @return whether any peer should be allowed to connect to the local peer
     */
    public boolean allowAllPeers()
    {
        return this.allowAllPeers;
    }

    /**
     * Returns the local UDP port to be used by the local peer.
     *
     * @return the local UDP port to be used by the local peer
     */
    public int getLocalPort()
    {
        return this.localPort;
    }

    /**
     * Returns a path to the local directory to be exported by the local peer.
     *
     * @return a path to the local directory to be exported by the local peer
     */
    public Path getExportedDirectoryPath()
    {
        return this.exportedDirectoryPath;
    }
}

/* -------------------------------------------------------------------------- */
