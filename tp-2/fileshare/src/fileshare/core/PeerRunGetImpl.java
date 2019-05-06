/* -------------------------------------------------------------------------- */

package fileshare.core;

import fileshare.Util;
import fileshare.transport.ReliableSocket;
import fileshare.transport.ReliableSocketConnection;

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
    )
    {
        final var connections = new ArrayList< ReliableSocketConnection >();
        final var segmentThreads = new ArrayList< Thread >();

        try
        {
            // connect to peers

            for (final var peerEndpoint : state.getJob().getPeerEndpoints())
                connections.add(socket.connect(peerEndpoint));

            // get file size

            final long fileSize = getFileSize(
                connections,
                state.getJob().getRemoteFilePath()
            );

            // partition file into segments

            final int numPeers = state.getJob().getPeerEndpoints().size();

            final long maxSegmentSize = (fileSize + numPeers - 1) / numPeers;

            // open local file

            try (final var localFile = exportedDirectory.openFileForWriting(
                state.getJob().getLocalFilePath()
            ))
            {
                // set local file size

                localFile.setLength(fileSize);

                // update job state

                state.start(fileSize);

                // concurrently receive file segments from peers

                long currentPosition = 0;

                for (final var connection : connections)
                {
                    // compute segment position and size

                    final long thisSegmentPosition = currentPosition;

                    final long thisSegmentSize = Math.min(
                        fileSize - currentPosition,
                        maxSegmentSize
                    );

                    currentPosition += thisSegmentSize;

                    // launch segment thread

                    final var thread = new Thread(() -> readSegment(
                        state,
                        connection,
                        localFile,
                        thisSegmentPosition,
                        thisSegmentSize
                    ));

                    segmentThreads.add(thread);

                    thread.start();
                }

                // wait for segment threads to finish

                segmentThreads.forEach(Util::uninterruptibleJoin);

                // commit changes to file (if no segment thread failed)

                if (state.getPhase() != JobState.Phase.FAILED)
                    localFile.commitAndClose();
            }
        }
        catch (Exception e)
        {
            // update job state (if not previously failed)

            state.fail(e.getMessage());

            // close connections to peers

            connections.forEach(ReliableSocketConnection::close);
        }
        finally
        {
            // wait for segment threads to finish

            segmentThreads.forEach(Util::uninterruptibleJoin);

            // close connections to peers

            connections.forEach(ReliableSocketConnection::close);

            // update job state (if not previously failed)

            state.succeed();
        }
    }

    private static long getFileSize(
        List< ReliableSocketConnection > connections,
        Path remoteFilePath
    ) throws IOException
    {
        long lastFileSize = -1;

        for (final var connection : connections)
        {
            final var input = connection.getInput();
            final var output = connection.getOutput();

            // send job type and remote file path

            output.writeByte(0);
            output.writeUTF(remoteFilePath.toString());
            output.flush();

            // receive file size

            final long fileSize = input.readLong();

            if (fileSize < 0)
                throw new RuntimeException(input.readUTF());

            // check if peer disagrees on file size

            if (lastFileSize < 0)
                lastFileSize = fileSize;
            else if (fileSize != lastFileSize)
                throw new RuntimeException("Peers disagree on file size.");
        }

        // peers agree on file size, return file size

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
            final var input = connection.getInput();
            final var output = connection.getOutput();

            // send segment position and size

            output.writeLong(segmentPosition);
            output.writeLong(segmentSize);
            output.flush();

            // receive and transfer segment content to local file

            Util.transferToFile(
                Channels.newChannel(input),
                localFile.getChannel(),
                segmentPosition,
                segmentSize,
                state::addToTransferredBytes
            );
        }
        catch (Exception e)
        {
            // update job state (if not previously failed)

            state.fail(connection.getRemoteEndpoint(), e.getMessage());
        }
    }
}

/* -------------------------------------------------------------------------- */
