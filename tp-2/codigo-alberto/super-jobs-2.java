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
import java.util.InputMismatchException;
import java.util.Optional;

/* -------------------------------------------------------------------------- */

public class Things
{
    // !! DONE !!
    private void runPut(
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

            final long totalBytes =
                localFile.length() *
                    state.getJob().getPeerEndpoints().size();

            synchronized (state)
            {
                state.setTotalBytes(Optional.of(totalBytes))
            }

            onStateUpdated.run();

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

    // !! DONE !!
    private void runSubPut(
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

            // send file content

            Util.transferFromFile(
                localFile.getChannel(),
                0,
                localFile.length(),
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

            output.flush();

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

    public void servePut(
        ExportedDirectory exportedDirectory,
        DataInputStream input,
        DataOutputStream output
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