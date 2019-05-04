/* -------------------------------------------------------------------------- */

package fileshare.core;

import fileshare.Util;
import fileshare.transport.ReliableSocketConnection;

import java.nio.channels.Channels;
import java.nio.file.Path;
import java.util.Optional;

/* -------------------------------------------------------------------------- */

class PeerServeGetImpl
{
    public static void serve(
        ReliableSocketConnection connection,
        ExportedDirectory exportedDirectory
    ) throws Exception
    {
        // get job info

        final var localFilePath = Path.of(input.readUTF());

        // open local file

        final ExportedDirectory.TemporaryRandomAccessFile localFile;

        try
        {
            final var localFile = exportedDirectory.openFileForReading(
                localFilePath
            );
        }
        catch (Exception e)
        {
            // write error

            output.writeLong(-1);
            output.writeUTF(e.getMessage());

            throw e;
        }

        try (localFile)
        {
            // write file size

            output.writeLong(localFile.length());
            output.flush();

            // get segment info

            final long segmentOffset = input.readLong();
            final long segmentSize = input.readLong();

            // update state with total bytes

            synchronized (state)
            {
                state.setTotalBytes(Optional.of(segmentSize));
            }

            onStateUpdated.run();

            // send file content

            Util.transferFromFile(
                localFile.getChannel(),
                segmentOffset,
                segmentSize,
                Channels.newChannel(output),
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
        }
    }
}

/* -------------------------------------------------------------------------- */
