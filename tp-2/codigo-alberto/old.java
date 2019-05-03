
import fileshare.core.JobState;

class ReliableSocketConnection
{
    /**
     * Shuts down this side's output stream.
     *
     * Invoking this method first flushes this connection's output stream (thus
     * sending any already written but buffered data; see
     * {@link #getOutputStream()}) and then closes that stream.
     *
     * It is *not* mandatory to invoke this method prior to invoking
     * {@link #close()}.
     *
     * This connection still has to be closed with {@link #close()} even if both
     * sides invoke this method.
     *
     * See {@link #getInputStream()} and {@link #getOutputStream()} for more
     * information on the effects of this method on this connection's streams.
     *
     * If this side's output stream is already shut down, or if this connection
     * is closed, this method has no effect.
     *
     * If this method fails, this connection is left in a closed state (as if by
     * invoking {@link #close()}).
     *
     * This class' API (including this method) is fully thread-safe: all methods
     * may be called concurrently with any method.
     *
     * @throws IOException if an I/O error occurs
     */
    public void shutdownOutput() throws IOException
    {
        this.tcpSocket.shutdownOutput();
    }
}

class Peer
{
    private void runJobGet(
        JobState state,
        Consumer< JobState > stateUpdated,
        DataInputStream connInput,
        DataOutputStream connOutput
    ) throws IOException
    {
        // send request

        connOutput.writeByte(JOB_ID_GET);
        connOutput.writeUTF(state.getJob().getRemoteFilePath().toString());
        connOutput.flush();

        // receive response

        final long fileSize = connInput.readLong();

        if (fileSize < 0)
        {
            throw new IllegalArgumentException(
                "File does not exist in remote."
            );
        }

        // update state with total bytes

        state = state.withTotalBytes(Optional.of(fileSize));
        stateUpdated.accept(state);

        // open local file

        final var fileOutput = this.exportedDirectory.openFileForWriting(
            state.getJob().getLocalFilePath(),
            fileSize
        );

        try (fileOutput)
        {
            // receive file data

            final long transferredBytes;

            if (fileSize == 0)
            {
                transferredBytes = 0;
            }
            else
            {
                try (final var channel = fileOutput.getChannel())
                {
                    transferredBytes = channel.transferFrom(
                        Channels.newChannel(connInput),
                        0,
                        fileSize
                    );
                }
            }

            // check transferred byte count

            if (transferredBytes < fileSize)
            {
                throw new RuntimeException(
                    String.format(
                        "Only transferred %s of %s bytes.",
                        transferredBytes, fileSize
                    )
                );
            }

            // commit file changes

            fileOutput.commitAndClose();
        }

        // update state with transferred bytes (TODO: update periodically)

        state = state.withTransferredBytes(fileSize);
        stateUpdated.accept(state);
    }

    private void runJobPut(
        JobState state,
        Consumer< JobState > stateUpdated,
        DataInputStream connInput,
        DataOutputStream connOutput
    ) throws IOException
    {
        // open local file

        final var fileInput = this.exportedDirectory.openFileForReading(
            state.getJob().getLocalFilePath()
        );

        final long fileSize;

        try (fileInput)
        {
            fileSize = fileInput.length();

            // update state with total bytes

            state = state.withTotalBytes(Optional.of(fileSize));
            stateUpdated.accept(state);

            // send request (no response necessary)

            connOutput.writeByte(JOB_ID_PUT);
            connOutput.writeUTF(state.getJob().getRemoteFilePath().toString());
            connOutput.writeLong(fileSize);

            // send file data

            try (final var fileInputChannel = fileInput.getChannel())
            {
                fileInputChannel.transferTo(
                    0,
                    fileInput.length(),
                    Channels.newChannel(connOutput)
                );
            }
        }

        // update state with transferred bytes (TODO: update periodically)

        state = state.withTransferredBytes(fileSize);
        stateUpdated.accept(state);
    }

    private void serveJobGet(
        Path localFilePath,
        DataInputStream connInput,
        DataOutputStream connOutput
    ) throws IOException
    {
        // open local file

        final RandomAccessFile fileInput;

        try
        {
            fileInput = this.exportedDirectory.openFileForReading(
                localFilePath
            );
        }
        catch (FileNotFoundException e)
        {
            connOutput.writeLong(-1);
            return;
        }

        try (fileInput)
        {
            // send file size

            connOutput.writeLong(fileInput.length());

            // send file data

            try (final var fileInputChannel = fileInput.getChannel())
            {
                fileInputChannel.transferTo(
                    0,
                    fileInput.length(),
                    Channels.newChannel(connOutput)
                );
            }
        }
    }

    private void serveJobPut(
        Path localFilePath,
        DataInputStream connInput,
        DataOutputStream connOutput
    ) throws IOException
    {
        // read file size

        final long fileSize = connInput.readLong();

        // open local file

        final var fileOutput = this.exportedDirectory.openFileForWriting(
            localFilePath,
            fileSize
        );

        try (fileOutput)
        {
            // receive file data

            final long transferredBytes;

            if (fileSize == 0)
            {
                transferredBytes = 0;
            }
            else
            {
                try (final var channel = fileOutput.getChannel())
                {
                    transferredBytes = channel.transferFrom(
                        Channels.newChannel(connInput),
                        0,
                        fileSize
                    );
                }
            }

            // check transferred byte count

            if (transferredBytes < fileSize)
            {
                throw new RuntimeException(
                    String.format(
                        "Only transferred %s of %s bytes.",
                        transferredBytes, fileSize
                    )
                );
            }

            // commit file changes

            fileOutput.commitAndClose();
        }
    }
}
