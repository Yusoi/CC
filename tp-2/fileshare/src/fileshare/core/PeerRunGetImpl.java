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
        final var subjobThreads = new ArrayList< Thread >();

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
                // set file size

                localFile.setLength(fileSize);

                // update job state

                state.start(fileSize);

                // concurrently get file segments from peers

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

                    // launch subjob thread

                    final var thread = new Thread(() -> readSegment(
                        state,
                        connection,
                        localFile,
                        thisSegmentPosition,
                        thisSegmentSize
                    ));

                    subjobThreads.add(thread);

                    thread.start();
                }
            }
        }
        catch (Exception e)
        {
            // update job state

            state.fail(e.toString());

            state.fail(e.getMessage());

            // interrupt subjob threads

            subjobThreads.forEach(Thread::interrupt);
        }
        finally
        {
            // wait for subjob threads to die

            subjobThreads.forEach(Util::uninterruptibleJoin);

            // close connections to peers

            connections.forEach(ReliableSocketConnection::close);

            // update job state

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

            // send job info

            output.writeByte(0);
            output.writeUTF(remoteFilePath.toString());
            output.flush();

            // receive file size

            final long fileSize = input.readLong();

            if (fileSize < 0)
                throw new RuntimeException(input.readUTF());

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
            final var input = connection.getInput();
            final var output = connection.getOutput();

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
                state::addToTransferredBytes
            );
        }
        catch (Exception e)
        {
            // update job state

            state.fail(connection.getRemoteEndpoint(), e.getMessage());
        }
    }
}

/* -------------------------------------------------------------------------- */
