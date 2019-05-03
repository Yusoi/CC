
    private void runJobGetMultipleRemotes(
        JobState state,
        Consumer< JobState > stateUpdated
        ) throws IOException
    {
        final var connections = new ArrayList< ReliableSocketConnection >();

        try
        {

            // open connections

            for (final var endpoint : state.getJob().getRemoteEndpoints())
                connections.add(this.socket.connect(endpoint));

            final var connInputStreams =
                connections
                    .stream()
                    .map(ReliableSocketConnection::getInputStream)
                    .map(DataInputStream::new)
                    .collect(Collectors.toUnmodifiableList());

            final var connOutputStreams =
                connections
                    .stream()
                    .map(ReliableSocketConnection::getOutputStream)
                    .map(DataOutputStream::new)
                    .collect(Collectors.toUnmodifiableList());

            // check file existence and size in all remotes

            for (final var connInput : connInputStreams)
            {
                connOutput.writeByte(JOB_ID_GET);
                connOutput.writeUTF(state.getJob().getRemoteFilePath().toString());
                connOutput.flush();

                connInput.readLong();
            }

            // partition file

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
                // transfer file data


            }

            // update state with transferred bytes (TODO: update periodically)

            state = state.withTransferredBytes(fileSize);
            stateUpdated.accept(state);
        }
        finally
        {
            // close connections

            for (final var connection : connections)
                connection.close();
        }



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
