/* -------------------------------------------------------------------------- */

package fileshare.core;

import fileshare.Util;
import fileshare.transport.ReliableSocketConnection;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.channels.Channels;
import java.nio.file.Path;
import java.util.Optional;

/* -------------------------------------------------------------------------- */

class PeerServePutImpl
{
    public static void serve(
        ReliableSocketConnection connection,
        ExportedDirectory exportedDirectory
    ) throws Exception
    {
        // get job info

        final var localFilePath = Path.of(input.readUTF());
        final long fileSize = input.readLong();

        // update state with total bytes

        synchronized (state)
        {
            state.setTotalBytes(Optional.of(fileSize));
        }

        onStateUpdated.run();

        // open local file

        final ExportedDirectory.RandomAccessFileForWriting localFile;

        try
        {
            final var localFile = exportedDirectory.openFileForWriting(
                localFilePath, fileSize
            );
        }
        catch (Exception e)
        {
            // write error

            output.writeUTF(e.getMessage());

            throw e;
        }

        try (localFile)
        {
            // write success

            output.writeUTF("");
            output.flush();

            // receive file content

            try
            {
                Util.transferToFile(
                    Channels.newChannel(input),
                    localFile.getChannel(),
                    0,
                    localFile.length(),
                    (deltaTransferred, throughput) ->
                    {
                        synchronized (state)
                        {
                            state.setTransferredBytes(
                                state.getTransferredBytes() + deltaTransferred
                            );

                            state.setThroughput(Optional.of(throughput));
                        }

                        onStateUpdated.run();
                    }
                );

                // commit changes

                localFile.commitAndClose();
            }
            catch (Exception e)
            {
                // write error

                output.writeUTF(e.getMessage());

                throw e;
            }

            // write success

            output.writeUTF("");
        }
    }
}

/* -------------------------------------------------------------------------- */
