/* -------------------------------------------------------------------------- */

package fileshare.core;

import fileshare.Util;
import fileshare.transport.ReliableSocketConnection;

import java.nio.channels.Channels;
import java.nio.file.Path;

/* -------------------------------------------------------------------------- */

/**
 * Utility class for {@link Peer} containing the logic for serving jobs of type
 * {@link JobType#PUT}.
 *
 * Note that this class is package-private.
 */
class PeerServePutImpl
{
    /**
     * Serves a job of type {@link JobType#PUT}.
     *
     * @param connection the connection to the remote peer
     * @param exportedDirectory the local peer's exported directory
     */
    static void serve(
        ReliableSocketConnection connection,
        ExportedDirectory exportedDirectory
    ) throws Exception
    {
        final var input = connection.getDataInputStream();
        final var output = connection.getDataOutputStream();

        // get local file path and size

        final var localFilePath = Path.of(input.readUTF());
        final long fileSize = input.readLong();

        // open local file

        final ExportedDirectory.TemporaryRandomAccessFile localFile;

        try
        {
            localFile = exportedDirectory.openFileForWriting(localFilePath);
        }
        catch (Exception e)
        {
            // send error message

            output.writeUTF(e.getMessage());
            output.flush();

            throw e;
        }

        try (localFile)
        {
            // set local file size

            localFile.setLength(fileSize);

            // send success message

            output.writeUTF("");
            output.flush();

            // receive and transfer file content to local file

            Util.transferToFile(
                Channels.newChannel(input),
                localFile.getChannel(),
                0,
                fileSize,
                null
            );

            // commit changes to local file

            try
            {
                localFile.commitAndClose();
            }
            catch (Exception e)
            {
                // send error message

                output.writeUTF(e.getMessage());
                output.flush();

                throw e;
            }
        }

        // send success message

        output.writeUTF("");
        output.flush();
    }
}

/* -------------------------------------------------------------------------- */
