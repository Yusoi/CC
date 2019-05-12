/* -------------------------------------------------------------------------- */

package fileshare.ui;

import fileshare.core.AddressRange;
import fileshare.core.Peer;
import net.sourceforge.argparse4j.inf.ArgumentParserException;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.BindException;

/* -------------------------------------------------------------------------- */

/**
 * Provides the {@link #main} method for the application.
 */
public final class Main
{
    /**
     * The application's {@code main} method.
     *
     * @param args the application's arguments
     *
     * @throws IOException if an error occurs reading from or writing to the
     *         standard streams
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

        // create peer

        final Peer peer;

        try
        {
            peer = new Peer(
                arguments.getLocalPort(),
                arguments.getExportedDirectoryPath()
            );
        }
        catch (BindException e)
        {
            System.err.println(Color.RED.apply(e.getMessage()));

            System.exit(1);
            return;
        }

        try (peer)
        {
            // whitelist all peers if requested

            if (arguments.allowAllPeers())
            {
                peer.getPeerWhitelist().add(
                    AddressRange.parseCidrNotation("0.0.0.0/0")
                    );

                peer.getPeerWhitelist().add(
                    AddressRange.parseCidrNotation("::/0")
                    );
            }

            // open peer

            peer.open();

            // run interpreter input loop

            new Interpreter(peer).runInputLoop();
        }
    }

    // No point in ever instantiating this class.
    private Main()
    {
    }
}

/* -------------------------------------------------------------------------- */
