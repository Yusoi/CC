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

        // get job info

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

            throw e;
        }

        try (localFile)
        {
            // set file size

            localFile.setLength(fileSize);

            // send success indication

            output.writeUTF("");
            output.flush();

            // receive file content

            Util.transferToFile(
                Channels.newChannel(input),
                localFile.getChannel(),
                0,
                fileSize,
                null
            );

            // commit changes

            try
            {
                localFile.commitAndClose();
            }
            catch (Exception e)
            {
                // send error message

                output.writeUTF(e.getMessage());

                throw e;
            }
        }

        // send success indication

        output.writeUTF("");
    }
}

/* -------------------------------------------------------------------------- */
