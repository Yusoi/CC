/* -------------------------------------------------------------------------- */

package fileshare.core;

import fileshare.Util;
import fileshare.transport.ReliableSocketConnection;

import java.io.RandomAccessFile;
import java.nio.channels.Channels;
import java.nio.file.Path;

/* -------------------------------------------------------------------------- */

class PeerServeGetImpl
{
    public static void serve(
        ReliableSocketConnection connection,
        ExportedDirectory exportedDirectory
    ) throws Exception
    {
        final var input = connection.getInput();
        final var output = connection.getOutput();

        // get local file path

        final var localFilePath = Path.of(input.readUTF());

        // open local file

        final RandomAccessFile localFile;

        try
        {
            localFile = exportedDirectory.openFileForReading(localFilePath);
        }
        catch (Exception e)
        {
            // send error message

            output.writeLong(-1);
            output.writeUTF(e.getMessage());

            throw e;
        }

        try (localFile)
        {
            // send file size

            output.writeLong(localFile.length());
            output.flush();

            // receive segment position and size

            final long segmentPosition = input.readLong();
            final long segmentSize = input.readLong();

            // send segment content

            Util.transferFromFile(
                localFile.getChannel(),
                segmentPosition,
                segmentSize,
                Channels.newChannel(output),
                null
            );
        }
    }
}

/* -------------------------------------------------------------------------- */
