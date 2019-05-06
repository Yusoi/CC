/* -------------------------------------------------------------------------- */

package fileshare.core;

import fileshare.Util;
import fileshare.transport.ReliableSocket;
import fileshare.transport.ReliableSocketConnection;

import java.io.RandomAccessFile;
import java.nio.channels.Channels;
import java.util.ArrayList;

/* -------------------------------------------------------------------------- */

class PeerRunPutImpl
{
    public static void run(
        JobState state,
        ReliableSocket socket,
        ExportedDirectory exportedDirectory
    )
    {
        final var connections = new ArrayList< ReliableSocketConnection >();
        final var peerThreads = new ArrayList< Thread >();

        // open local file

        try (final var localFile = exportedDirectory.openFileForReading(
            state.getJob().getLocalFilePath()
        ))
        {
            // connect to peers

            for (final var peerEndpoint : state.getJob().getPeerEndpoints())
                connections.add(socket.connect(peerEndpoint));

            // update job state

            state.start(
                localFile.length() *
                    state.getJob().getPeerEndpoints().size()
            );

            // concurrently send file to peers

            for (final var connection : connections)
            {
                final var thread = new Thread(() -> runSub(
                    state,
                    connection,
                    localFile
                ));

                peerThreads.add(thread);

                thread.start();
            }

            // wait for peer threads to finish

            peerThreads.forEach(Util::uninterruptibleJoin);
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

            peerThreads.forEach(Util::uninterruptibleJoin);

            // close connections to peers

            connections.forEach(ReliableSocketConnection::close);

            // update job state (if not previously failed)

            state.succeed();
        }
    }

    private static void runSub(
        JobState state,
        ReliableSocketConnection connection,
        RandomAccessFile localFile
    )
    {
        try
        {
            final var input = connection.getInput();
            final var output = connection.getOutput();

            // send job type and remote file path and size

            output.writeByte(1);
            output.writeUTF(state.getJob().getRemoteFilePath().toString());
            output.writeLong(localFile.length());
            output.flush();

            // receive error message

            Util.throwIfNotEmpty(input.readUTF());

            // send file content

            Util.transferFromFile(
                localFile.getChannel(),
                0,
                localFile.length(),
                Channels.newChannel(output),
                state::addToTransferredBytes
            );

            output.flush();

            // receive error message

            Util.throwIfNotEmpty(input.readUTF());
        }
        catch (Exception e)
        {
            e.printStackTrace();
            // update job state (if not previously failed)

            state.fail(connection.getRemoteEndpoint(), e.getMessage());
        }
    }
}

/* -------------------------------------------------------------------------- */
