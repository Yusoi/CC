/* -------------------------------------------------------------------------- */

package fileshare.ui;

import fileshare.core.AddressRange;
import fileshare.core.Peer;
import net.sourceforge.argparse4j.inf.ArgumentParserException;

import java.io.IOException;
import java.io.PrintWriter;

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

            new Prompt(peer).runInputLoop();
        }
    }

    // No point in ever instantiating this class.
    private Main()
    {
    }
}

/* -------------------------------------------------------------------------- */
