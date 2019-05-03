
    private void runJobPutMultipleRemotes(
        JobState state,
        Runnable onStateUpdated
        ) throws IOException
    {
        final var numRemotes = state.getJob().getRemoteEndpoints().size();

        final var connections = new ArrayList< ReliableSocketConnection >();
        final var threads     = new ArrayList< Thread >();

        // open local file

        final var fileInput = this.exportedDirectory.openFileForReading(
            state.getJob().getLocalFilePath()
            );

        try (fileInput)
        {
            // update state with total bytes

            final var fileSize = fileInput.length();

            state.setTotalBytes(Optional.of(numRemotes * fileSize));
            onStateUpdated.run();

            // open connections

            for (final var endpoint : state.getJob().getRemoteEndpoints())
                connections.add(this.socket.connect(endpoint));

            // ...

            for (final var conn : connections)
            {
                final var connInput  = new DataInputStream(conn.getInputStream());
                final var connOutput = new DataOutputStream(conn.getOutputStream());

                final var thread = new Thread(() -> {

                    // send request

                    connOutput.writeByte(2);
                    connOutput.writeUTF(state.getJob().getRemoteFilePath().toString());
                    connOutput.writeLong(fileSize);
                    connOutput.flush();

                    // receive response

                    final var error1 = connInput.readUTF();

                    if (!error1.isEmpty())
                    {
                        // TODO: implement
                    }

                    // send file and shutdown output

                    Util.transfer(
                        fileInput.getChannel(),
                        Channels.newChannel(connOutput),
                        t -> {
                            synchronized (state)
                            {
                                state.setTransferredBytes(
                                    state.getTransferredBytes() + t
                                    );
                            }

                            onStateUpdated.run();
                        }
                        );

                    conn.shutdownOutput();

                    // receive response

                    final var error2 = connInput.readUTF();

                    if (!error2.isEmpty())
                    {
                        // TODO: implement
                    }
                });
            }
        }
        finally
        {
            // interrupt threads and wait for them to die

            threads.forEach(Thread::interrupt);
            threads.forEach(Util::uninterruptibleJoin);

            // close connections

            for (final var connection : connections)
                connection.close();
        }
    }

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
