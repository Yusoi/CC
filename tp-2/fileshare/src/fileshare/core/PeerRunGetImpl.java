/* -------------------------------------------------------------------------- */

package fileshare.core;

import fileshare.Util;
import fileshare.transport.ReliableSocket;
import fileshare.transport.ReliableSocketConnection;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.Channels;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/* -------------------------------------------------------------------------- */

class PeerRunGetImpl
{
    public static void run(
        JobState state,
        ReliableSocket socket,
        ExportedDirectory exportedDirectory
    ) throws IOException
    {
        final var connections = new ArrayList< ReliableSocketConnection >();
        final var threads = new ArrayList< Thread >();

        try
        {
            // connect to peers

            for (final var peerEndpoint : state.getJob().getPeerEndpoints())
                connections.add(socket.connect(peerEndpoint));

            // get file size

            final long fileSize = getFileSize(
                state.getJob().getRemoteFilePath(),
                connections
            );

            // partition file into segments

            final int numPeers = state.getJob().getPeerEndpoints().size();

            final long maxSegmentSize = (fileSize + numPeers - 1) / numPeers;

            // open local file

            try (final var localFile = exportedDirectory.openFileForWriting(
                state.getJob().getLocalFilePath()
            ))
            {
                // set file size

                localFile.setLength(fileSize);

                // update job state

                state.begin(fileSize);

                // request and read segments from peers

                long currentPosition = 0;

                for (final var connection : connections)
                {
                    final long thisSegmentPosition = currentPosition;

                    final long thisSegmentSize = Math.min(
                        fileSize - currentPosition,
                        maxSegmentSize
                    );

                    currentPosition += thisSegmentSize;

                    final var thread = new Thread(() -> readSegment(
                        state,
                        connection,
                        localFile,
                        thisSegmentPosition,
                        thisSegmentSize
                    ));

                    threads.add(thread);
                    thread.start();
                }
            }
        }
        catch (Exception e)
        {
            // update state with error message

            state.fail(e.getMessage());

            // close connections

            for (final var connection : connections)
                connection.close();

            // interrupt peer threads

            threads.forEach(Thread::interrupt);
        }
        finally
        {
            // wait for peer threads to die

            threads.forEach(Util::uninterruptibleJoin);

            // update job state

            state.finish();
        }
    }

    private static long getFileSize(
        Path peerFilePath,
        List< ReliableSocketConnection > connections
    ) throws IOException
    {
        long lastFileSize = -1;

        for (final var connection : connections)
        {
            final var input =
                new DataInputStream(connection.getInputStream());

            final var output =
                new DataOutputStream(connection.getOutputStream());

            // send job info

            output.writeByte(0);
            output.writeUTF(peerFilePath.toString());
            output.flush();

            // receive file size

            final long fileSize = input.readLong();

            if (fileSize < 0)
            {
                final var errorMessage = input.readUTF();

                throw new RuntimeException(
                    String.format(
                        "%s: %s",
                        connection.getRemoteEndpoint(),
                        errorMessage
                    )
                );
            }

            // check file size

            if (lastFileSize < 0)
                lastFileSize = fileSize;
            else if (fileSize != lastFileSize)
                throw new RuntimeException("Peers disagree on file size.");
        }

        return lastFileSize;
    }

    private static void readSegment(
        JobState state,
        ReliableSocketConnection connection,
        RandomAccessFile localFile,
        long segmentPosition,
        long segmentSize
    )
    {
        try
        {
            final var input = new DataInputStream(connection.getInputStream());
            final var output = new DataOutputStream(connection.getOutputStream());

            // write segment info

            output.writeLong(segmentPosition);
            output.writeLong(segmentSize);
            output.flush();

            // receive file segment content

            Util.transferToFile(
                Channels.newChannel(input),
                localFile.getChannel(),
                segmentPosition,
                segmentSize,
                state::increaseTransferredBytes
            );
        }
        catch (Exception e)
        {
            state.fail(connection.getRemoteEndpoint(), e.getMessage());
        }
    }
}

/* -------------------------------------------------------------------------- */
