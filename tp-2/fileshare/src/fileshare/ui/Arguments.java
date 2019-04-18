/* -------------------------------------------------------------------------- */

package fileshare.ui;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/* -------------------------------------------------------------------------- */

/**
 * TODO: document
 */
public class Arguments
{
    private final Path exportDirPath;
    private final int udpPort;

    private Arguments(Path exportDirPath, int udpPort)
    {
        this.exportDirPath = exportDirPath;
        this.udpPort       = udpPort;
    }

    /**
     * TODO: document
     */
    public static Arguments parse(String[] args)
    {
        // check number of arguments

        if (args.length < 1)
            throw new RuntimeException("Not enough arguments.");

        if (args.length > 2)
            throw new RuntimeException("Too many arguments.");

        // parse export directory path

        final Path exportDirPath;

        try
        {
            exportDirPath = Path.of(args[0]);
        }
        catch (InvalidPathException e)
        {
            throw new RuntimeException("Invalid export path.");
        }

        if (!Files.exists(exportDirPath))
            throw new RuntimeException("Export path does not exist.");

        if (!Files.isDirectory(exportDirPath))
            throw new RuntimeException("Export path does not point to a directory.");

        // parse udp port number

        final int udpPort;

        if (args.length < 2)
        {
            udpPort = 7777;
        }
        else
        {
            try
            {
                udpPort = Integer.parseUnsignedInt(args[1]);
            }
            catch (NumberFormatException e)
            {
                throw new RuntimeException("Invalid UDP port number.");
            }
        }

        // return arguments

        return new Arguments(exportDirPath, udpPort);
    }

    /**
     * TODO: document
     */
    public Path getExportDirPath()
    {
        return this.exportDirPath;
    }

    /**
     * TODO: document
     */
    public int getUdpPort()
    {
        return this.udpPort;
    }
}

/* -------------------------------------------------------------------------- */
