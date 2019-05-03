/* -------------------------------------------------------------------------- */

package fileshare.core;

import fileshare.Util;
import fileshare.transport.Endpoint;
import fileshare.transport.ReliableSocket;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.RandomAccessFile;
import java.nio.channels.Channels;
import java.util.ArrayList;
import java.util.Optional;

/* -------------------------------------------------------------------------- */

public class Things
{
    public void runPut(
        JobState state,
        ReliableSocket socket,
        ExportedDirectory exportedDirectory,
        Runnable onStateUpdated
        )
    {
        final var subjobThreads = new ArrayList< Thread >();

        // open local file

        try (final var localFile = exportedDirectory.openFileForReading(
            state.getJob().getLocalFilePath()
            ))
        {
            // update job state with total bytes

            synchronized (state)
            {
                state.setTotalBytes(Optional.of(
                    localFile.length() *
                        state.getJob().getPeerEndpoints().size()
                    ));
            }

            // launch subjobs

            for (final var peerEndpoint : state.getJob().getPeerEndpoints())
            {
                final var thread = new Thread(() -> runSubPut(
                    state,
                    socket,
                    peerEndpoint,
                    localFile,
                    onStateUpdated
                ));

                subjobThreads.add(thread);

                thread.start();
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

            // interrupt subjobs

            subjobThreads.forEach(Thread::interrupt);
        }
        finally
        {
            // await subjobs

            subjobThreads.forEach(Util::uninterruptibleJoin);
        }
    }

    public void runSubPut(
        JobState state,
        ReliableSocket socket,
        Endpoint peerEndpoint,
        RandomAccessFile localFile,
        Runnable onStateUpdated
        )
    {
        // connect to peer

        try (final var connection = socket.connect(peerEndpoint))
        {
            final var input =
                new DataInputStream(connection.getInputStream());

            final var output =
                new DataOutputStream(connection.getOutputStream());

            // send subjob info

            output.writeByte(1);
            output.writeUTF(state.getJob().getRemoteFilePath().toString());
            output.writeLong(localFile.length());
            output.flush();

            // receive error message

            final var initialErrorMessage = input.readUTF();

            if (!initialErrorMessage.isEmpty())
            {
                throw new Exception(
                    String.format("%s: %s", peerEndpoint, initialErrorMessage)
                    );
            }

            // send file data

            final long transferredBytes = Util.transferFromFile(
                localFile.getChannel(),
                0,
                localFile.length(),
                Channels.newChannel(output),
                (size, throughput) -> {
                    synchronized (state)
                    {
                        state.setTransferredBytes(
                            state.getTransferredBytes() + size
                        );

                        state.setThroughput(Optional.of(throughput));
                    }

                    onStateUpdated.run();
                }
            );

            // check transferred bytes

            if (transferredBytes < localFile.length())
            {
                throw new Exception(
                    String.format(
                        "%s: only sent %d of %d bytes",
                        peerEndpoint,
                        transferredBytes,
                        localFile.length()
                    )
                );
            }

            // receive error message

            final var finalErrorMessage = input.readUTF();

            if (!finalErrorMessage.isEmpty())
            {
                throw new Exception(
                    String.format("%s: %s", peerEndpoint, finalErrorMessage)
                );
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
        }
    }

    public void servePut()
    {

    }





















    public void doThings(ReliableSocket socket, JobState jobState)
    {
        // for each peer: open connection and get file size

        for (final var peerEndpoint : jobState.getJob().getPeerEndpoints())
        {
            final var thread = new Thread(() -> {

                final var connection = socket.connect(peerEndpoint);

            });

            thread.start();
        }

        // check if all peers report same file size

        // partition file

        // open local file for writing

        // for each peer: request respective file segment

        // wait for all remotes to finish
    }

    public void doPeerThings1()
    {
        // open connection


    }
}

/* -------------------------------------------------------------------------- */
