/* -------------------------------------------------------------------------- */

package fileshare.core;

import fileshare.Util;
import fileshare.transport.Endpoint;
import fileshare.transport.ReliableSocket;

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
        final var subjobThreads = new ArrayList< Thread >();

        // open local file

        try (final var localFile = exportedDirectory.openFileForReading(
            state.getJob().getLocalFilePath()
        ))
        {
            // update job state

            state.start(
                localFile.length() *
                    state.getJob().getPeerEndpoints().size()
            );

            // concurrently put file to peers
            
            for (final var peerEndpoint : state.getJob().getPeerEndpoints())
            {
                final var thread = new Thread(() -> runSub(
                    state,
                    socket,
                    peerEndpoint,
                    localFile
                ));

                subjobThreads.add(thread);

                thread.start();
            }
        }
        catch (Exception e)
        {
            // update job state

            state.fail(e.getMessage());

            // interrupt subjob threads

            subjobThreads.forEach(Thread::interrupt);
        }
        finally
        {
            // wait for subjob threads to die

            subjobThreads.forEach(Util::uninterruptibleJoin);

            // update job state

            state.succeed();
        }
    }

    private static void runSub(
        JobState state,
        ReliableSocket socket,
        Endpoint peerEndpoint,
        RandomAccessFile localFile
    )
    {
        // connect to peer

        try (final var connection = socket.connect(peerEndpoint))
        {
            final var input = connection.getInput();
            final var output = connection.getOutput();

            // send subjob info

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
                state::increaseTransferredBytes
            );

            output.flush();

            // receive error message

            Util.throwIfNotEmpty(input.readUTF());
        }
        catch (Exception e)
        {
            // update job state

            state.fail(peerEndpoint, e.getMessage());
        }
    }
}

/* -------------------------------------------------------------------------- */
