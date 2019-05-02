/* -------------------------------------------------------------------------- */

package fileshare.ui;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParserException;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/* -------------------------------------------------------------------------- */

public class Args
{
    /**
     * TODO: document
     *
     * @param args TODO: document
     * @return TODO: document
     *
     * @throws ArgumentParserException TODO: document
     */
    public static Args parse(String[] args) throws ArgumentParserException
    {
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
     * TODO: document
     *
     * @return TODO: document
     */
    public boolean getAllowAllPeers()
    {
        return this.allowAllPeers;
    }

    /**
     * TODO: document
     *
     * @return TODO: document
     */
    public int getLocalPort()
    {
        return this.localPort;
    }

    /**
     * TODO: document
     *
     * @return TODO: document
     */
    public Path getExportedDirectoryPath()
    {
        return this.exportedDirectoryPath;
    }
}

/* -------------------------------------------------------------------------- */
