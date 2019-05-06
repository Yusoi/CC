/* -------------------------------------------------------------------------- */

package fileshare.core;

import fileshare.Util;
import fileshare.transport.ReliableSocketConnection;

import java.nio.channels.Channels;
import java.nio.file.Path;

/* -------------------------------------------------------------------------- */

class PeerServePutImpl
{
    public static void serve(
        ReliableSocketConnection connection,
        ExportedDirectory exportedDirectory
    ) throws Exception
    {
        final var input = connection.getInput();
        final var output = connection.getOutput();

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
