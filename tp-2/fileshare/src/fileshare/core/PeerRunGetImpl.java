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
import java.util.Optional;

/* -------------------------------------------------------------------------- */

class PeerRunGetImpl
{
    public static void run(
        JobState state,
        Runnable onStateUpdated,
        ReliableSocket socket,
        ExportedDirectory exportedDirectory
    )
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

            // update job state with total bytes

            synchronized (state)
            {
                state.setTotalBytes(Optional.of(fileSize));
            }

            onStateUpdated.run();

            // partition file

            final int numPeers = state.getJob().getPeerEndpoints().size();

            final long maxSegmentSize = (fileSize + numPeers - 1) / numPeers;

            // open local file

            try (final var localFile = exportedDirectory.openFileForWriting(
                state.getJob().getLocalFilePath(), lastFileSize
            ))
            {
                long currentOffset = 0;

                for (final var conn : connections)
                {
                    final long thisSegmentOffset = currentOffset;

                    final long thisSegmentSize = Math.min(
                        lastFileSize - currentOffset,
                        maxSegmentSize
                    );

                    currentOffset += thisSegmentOffset;

                    final var thread = new Thread(() -> runSubGet(
                        state,
                        conn,
                        localFile,
                        thisSegmentOffset,
                        thisSegmentSize,
                        onStateUpdated
                    );

                    threads.add(thread);

                    thread.start();
                }
            }
        }
        catch (Exception e)
        {
            // update state with error message

            synchronized (state)
            {
                if (state.getErrorMessage().isEmpty())
                    state.setErrorMessage(Optional.of(e.getMessage()));
            }

            onStateUpdated.run();

            // close connections

            for (final var conn : connections)
                conn.close();

            // TODO

            threads.forEach(Thread::interrupt);
        }
        finally
        {
            // TODO

            threads.forEach(Util::uninterruptibleJoin);
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
        Runnable onStateUpdated,
        ReliableSocketConnection connection,
        RandomAccessFile localFile,
        long segmentPosition,
        long segmentSize
    )
    {
        final var input =
            new DataInputStream(conn.getInputStream());

        final var output =
            new DataOutputStream(conn.getOutputStream());

        // write segment info

        output.writeLong(fileSegmentPosition);
        output.writeLong(fileSegmentSize);
        output.flush();

        // receive file segment content

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
    }

}

/* -------------------------------------------------------------------------- */
